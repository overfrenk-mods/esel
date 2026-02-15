// ---------- CODICE VERSIONE 3.5.0 "TREND STANDARD DEXCOM" ----------
package esel.esel.esel.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import esel.esel.esel.MainActivity;
import esel.esel.esel.R;
import esel.esel.esel.datareader.SGV;
import esel.esel.esel.datareader.EsNotificationListener;
import esel.esel.esel.receivers.FastPatrolReceiver;
import esel.esel.esel.receivers.ServiceRestarter;
import esel.esel.esel.receivers.WatchdogReceiver;
import esel.esel.esel.util.AapsSender;
import esel.esel.esel.util.EselLog;
import esel.esel.esel.util.SP;

public class DataMonitorService extends Service {
    private static final String TAG = "DataMonitorService";
    public static final String CHANNEL_ID = "EselMonitorChannel";
    public static final int NOTIFICATION_ID = 101;

    public static final String ACTION_STOP_SERVICE = "esel.esel.esel.ACTION_STOP_SERVICE";
    public static final String ACTION_MANUAL_SYNC = "esel.esel.esel.ACTION_MANUAL_SYNC";
    public static final String ACTION_REQUEST_SGV_READ = "esel.esel.esel.ACTION_REQUEST_SGV_READ";
    public static final int WATCHDOG_REQUEST_CODE = 901;

    public static final String KEY_LAST_SUCCESSFUL_SEND_MS = "status_last_successful_send_ms";
    public static final String KEY_LAST_SGV_TIMESTAMP = "status_last_sgv_timestamp";
    public static final String KEY_LAST_SGV_RAW_VALUE = "status_last_sgv_raw_value";
    public static final String KEY_LAST_SGV_FINAL_VALUE = "status_last_sgv_final_value";
    public static final String KEY_SGV_HISTORY_JSON = "sgv_history_json";

    // --- CONFIGURAZIONE v3.5.0 ---
    private static final long MIN_INTERVAL_MS = 270000L; // 4m 30s Buffer
    private static final long LONG_PAUSE_THRESHOLD_MS = 9 * 60 * 1000L; // 9m Reset per ricarica

    // ZOMBIE KILLER: Scadenza dato nel buffer (8 minuti)
    private static final long DATA_EXPIRATION_MS = 480000L;

    private static final long INTERVAL_MS = 5 * 60 * 1000L;

    private ExecutorService executor;
    private BroadcastReceiver sgvDataReceiver;
    private PowerManager.WakeLock wakeLock;
    private Gson gson;

    private Handler bufferHandler;
    private Runnable bufferRunnable;
    private SGV pendingSgv = null;

    private Handler syncTriggerHandler;
    private Runnable syncTriggerRunnable;

    private String currentSmoothingStatus = "OFF";

    public static class SgvHistoryPoint {
        public long timestamp;
        public int value;

        public SgvHistoryPoint(long timestamp, int value) {
            this.timestamp = timestamp;
            this.value = value;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        EselLog.LogI(TAG, "DataMonitorService onCreate.");

        executor = Executors.newSingleThreadExecutor();
        gson = new Gson();

        bufferHandler = new Handler(Looper.getMainLooper());
        syncTriggerHandler = new Handler(Looper.getMainLooper());

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EselReader::DataProcessingWakeLock");
        }

        createNotificationChannel();
        setupSgvDataReceiver();

        WatchdogReceiver.scheduleNextWatchdog(this);
        FastPatrolReceiver.schedule(this);

        startSyncTrigger();

        SP.putBoolean("service_should_be_running", true);

        Notification notification = buildNotification(getString(R.string.notification_persistent_text_waiting));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } catch (Exception e) {
                EselLog.LogE(TAG, "Errore startForeground Q+: " + e.getMessage());
                try {
                    startForeground(NOTIFICATION_ID, notification);
                } catch (Exception ignored) {}
            }
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if (ACTION_STOP_SERVICE.equals(intent.getAction())) {
                stopSelfService();
                return START_NOT_STICKY;
            }
            if (ACTION_MANUAL_SYNC.equals(intent.getAction())) {
                if (intent.hasExtra(EsNotificationListener.EXTRA_SGV_DATA)) {
                    final SGV sgv = (SGV) intent.getSerializableExtra(EsNotificationListener.EXTRA_SGV_DATA);
                    if (sgv != null) {
                        executor.execute(() -> processSgv(sgv, true));
                    }
                }
            }
        }
        return START_STICKY;
    }

    private void setupSgvDataReceiver() {
        sgvDataReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null && intent.hasExtra(EsNotificationListener.EXTRA_SGV_DATA)) {
                    final SGV sgv = (SGV) intent.getSerializableExtra(EsNotificationListener.EXTRA_SGV_DATA);
                    if (sgv != null) {
                        executor.execute(() -> processSgv(sgv, false));
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter(EsNotificationListener.ACTION_NEW_SGV_DATA);
        LocalBroadcastManager.getInstance(this).registerReceiver(sgvDataReceiver, filter);
    }

    private void processSgv(SGV sgv, boolean isManualOverride) {
        if (wakeLock != null && !wakeLock.isHeld()) {
            try { wakeLock.acquire(20 * 1000L); } catch (Exception ignored) {}
        }

        try {
            int rawValue = sgv.raw;
            long now = System.currentTimeMillis();
            sgv.timestamp = now;

            if (rawValue < 30 || rawValue > 500) {
                EselLog.LogE(TAG, "⛔ VALORE ANOMALO: " + rawValue + ". Scartato.");
                return;
            }

            long lastSentTime = SP.getLong(KEY_LAST_SUCCESSFUL_SEND_MS, 0L);
            long timeSinceLastSend = now - lastSentTime;

            // RESET per Manuale o Pausa Lunga (Gap > 9 min)
            if (isManualOverride || (lastSentTime > 0 && timeSinceLastSend > LONG_PAUSE_THRESHOLD_MS)) {
                EselLog.LogW(TAG, "⚠️ [RESET/GAP " + (timeSinceLastSend/60000) + "m] Invio immediato (RAW).");
                SP.putInt(KEY_LAST_SGV_FINAL_VALUE, -1);
                flushBufferAndSend(sgv);
                return;
            }

            // BUFFER se troppo presto (< 4m 30s)
            if (timeSinceLastSend < MIN_INTERVAL_MS) {
                EselLog.LogI(TAG, "🅿️ Buffer (+" + (timeSinceLastSend/1000) + "s). Parcheggio " + rawValue + ".");

                pendingSgv = sgv;

                if (bufferRunnable == null) {
                    scheduleBufferFlush(MIN_INTERVAL_MS - timeSinceLastSend);
                }
                return;
            }

            // INVIO DIRETTO se tempo OK
            EselLog.LogI(TAG, "✅ Tempo OK (" + (timeSinceLastSend/1000) + "s). Invio diretto.");
            flushBufferAndSend(sgv);

        } catch (Throwable t) {
            EselLog.LogE(TAG, "Errore processamento: " + Log.getStackTraceString(t));
        } finally {
            if (wakeLock != null && wakeLock.isHeld()) {
                try { wakeLock.release(); } catch (Exception ignored) {}
            }
        }
    }

    private void scheduleBufferFlush(long delayMs) {
        if (delayMs < 100) delayMs = 100;
        EselLog.LogI(TAG, "⏳ Timer impostato tra " + (delayMs/1000) + "s.");

        bufferRunnable = () -> {
            if (pendingSgv != null) {
                long now = System.currentTimeMillis();
                long age = now - pendingSgv.timestamp;

                // CONTROLLO ZOMBIE: Se il dato è vecchio di > 8 min, BUTTALO.
                if (age > DATA_EXPIRATION_MS) {
                    EselLog.LogE(TAG, "💀 Dato ZOMBIE rilevato! È vecchio di " + (age/1000) + "s. SCARTATO.");
                    pendingSgv = null;
                    bufferRunnable = null;
                    return;
                }

                EselLog.LogW(TAG, "⏰ Buffer scaduto. Invio dato parcheggiato: " + pendingSgv.raw);
                SGV toSend = pendingSgv;
                pendingSgv = null;
                bufferRunnable = null;
                executor.execute(() -> performSend(toSend));
            } else {
                bufferRunnable = null;
            }
        };
        bufferHandler.postDelayed(bufferRunnable, delayMs);
    }

    private void flushBufferAndSend(SGV sgv) {
        if (bufferRunnable != null) {
            bufferHandler.removeCallbacks(bufferRunnable);
            bufferRunnable = null;
        }
        pendingSgv = null;
        performSend(sgv);
    }

    private void performSend(SGV sgv) {
        try {
            long now = System.currentTimeMillis();
            sgv.timestamp = now;

            // Smoothing
            sgv.value = applyEasySmoothing(sgv);

            // Calcolo Trend (IMPORTANTE: Fatto DOPO lo smoothing)
            calculateTrend(sgv);

            Context appContext = getApplicationContext();
            if (SP.getBoolean("send_to_AAPS", true)) { AapsSender.sendToAaps(appContext, sgv); }
            if (SP.getBoolean("send_to_NS", false)) { AapsSender.sendToNsClient(appContext, sgv); }

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(appContext);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putLong(KEY_LAST_SUCCESSFUL_SEND_MS, now);
            editor.putInt(KEY_LAST_SGV_RAW_VALUE, sgv.raw);
            editor.putInt(KEY_LAST_SGV_FINAL_VALUE, sgv.value);
            editor.apply();

            updateSgvHistory(sgv);

            DateFormat df = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            String arrow = getTrendArrow(sgv.direction);
            String time = df.format(new Date(sgv.timestamp));
            String notificationDetail = sgv.value + " " + arrow +
                    " (Raw: " + sgv.raw + ") [" + currentSmoothingStatus + "] alle " + time;
            updateNotification(notificationDetail);

        } catch (Exception e) {
            EselLog.LogE(TAG, "Errore performSend: " + e.getMessage());
        }
    }

    private int applyEasySmoothing(SGV currentSgv) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (!prefs.getBoolean("smooth_data", false)) {
            currentSmoothingStatus = "OFF";
            return currentSgv.raw;
        }
        try {
            int lowerLimit = Integer.parseInt(prefs.getString("lower_limit", "65"));
            if (currentSgv.raw < lowerLimit) {
                currentSmoothingStatus = "LIMIT";
                return currentSgv.raw;
            }

            String level = prefs.getString("smoothing_level", "MEDIO").toUpperCase();
            double alpha;
            switch (level) {
                case "SOFT":  alpha = 0.70; break;
                case "ALTO":  alpha = 0.25; break;
                case "FORTE": alpha = 0.15; break;
                case "MEDIO":
                default:      alpha = 0.40; level = "MEDIO"; break;
            }

            int lastFinalValue = SP.getInt(KEY_LAST_SGV_FINAL_VALUE, -1);
            if (lastFinalValue == -1) {
                currentSmoothingStatus = "RAW(Reset)";
                return currentSgv.raw;
            }

            double smoothedValue = (alpha * currentSgv.raw) + ((1.0 - alpha) * lastFinalValue);

            currentSmoothingStatus = level;
            return (int) Math.round(smoothedValue);
        } catch (Exception e) {
            currentSmoothingStatus = "ERR";
            return currentSgv.raw;
        }
    }

    // --- NUOVO CALCOLO TREND STANDARD (mg/dL al minuto) ---
    private void calculateTrend(SGV sgv) {
        long lastSgvTimestamp = SP.getLong(KEY_LAST_SGV_TIMESTAMP, 0L);
        int lastSentFinalValue = SP.getInt(KEY_LAST_SGV_FINAL_VALUE, -1);

        // Se non abbiamo uno storico valido, non possiamo calcolare la tendenza
        if (lastSgvTimestamp <= 0 || lastSentFinalValue <= 0) {
            sgv.direction = "Flat"; return;
        }

        long timeDiff = sgv.timestamp - lastSgvTimestamp;
        // Evitiamo divisioni per zero o tempi assurdi
        if (timeDiff <= 0 || timeDiff > 30 * 60 * 1000) {
            sgv.direction = "Flat"; return;
        }

        // Calcoliamo la variazione
        double valueDiff = (double) sgv.value - lastSentFinalValue;

        // Calcoliamo la pendenza: (Delta / Tempo) * 60000 = mg/dL al minuto
        double slopeByMinute = (valueDiff / timeDiff) * 60000.0;

        // --- SOGLIE STANDARD (Stile Dexcom/Abbott) ---
        // Double Up/Down: > 3.0 mg/dl al minuto
        // Single Up/Down: > 2.0 mg/dl al minuto
        // 45 Up/Down:     > 1.0 mg/dl al minuto
        // Flat:           tra -1.0 e 1.0

        if (slopeByMinute <= -3.0) sgv.direction = "DoubleDown";
        else if (slopeByMinute <= -2.0) sgv.direction = "SingleDown";
        else if (slopeByMinute <= -1.0) sgv.direction = "FortyFiveDown";
        else if (slopeByMinute < 1.0) sgv.direction = "Flat";
        else if (slopeByMinute < 2.0) sgv.direction = "FortyFiveUp";
        else if (slopeByMinute < 3.0) sgv.direction = "SingleUp";
        else sgv.direction = "DoubleUp";
    }

    private void startSyncTrigger() {
        if (syncTriggerHandler != null && syncTriggerRunnable != null) {
            syncTriggerHandler.removeCallbacks(syncTriggerRunnable);
        }
        syncTriggerRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    FastPatrolReceiver.schedule(getApplicationContext());
                } catch (Exception ignored) {}

                if (bufferRunnable == null) {
                    requestSgvFromListener();
                }

                long now = System.currentTimeMillis();
                long nextTarget = now + (INTERVAL_MS - (now % INTERVAL_MS)) + 30000L;
                long delay = nextTarget - now;
                if (delay < 5000) delay += INTERVAL_MS;

                syncTriggerHandler.postDelayed(this, delay);
            }
        };
        syncTriggerHandler.post(syncTriggerRunnable);
    }

    private void stopSyncTrigger() {
        if (syncTriggerHandler != null && syncTriggerRunnable != null) {
            syncTriggerHandler.removeCallbacks(syncTriggerRunnable);
        }
    }

    private void requestSgvFromListener() {
        try {
            Intent requestIntent = new Intent(this, EsNotificationListener.class);
            requestIntent.setAction(ACTION_REQUEST_SGV_READ);
            startService(requestIntent);
        } catch (Exception ignored) {}
    }

    private void updateSgvHistory(SGV newSgv) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            String durationHoursStr = prefs.getString("graph_duration_hours", "3");
            int historyMaxSize = Integer.parseInt(durationHoursStr) * 12;

            String historyJson = SP.getString(KEY_SGV_HISTORY_JSON, "[]");
            Type listType = new TypeToken<ArrayList<SgvHistoryPoint>>() {}.getType();
            List<SgvHistoryPoint> history = gson.fromJson(historyJson, listType);

            if (history == null) history = new ArrayList<>();

            history.add(new SgvHistoryPoint(newSgv.timestamp, newSgv.value));
            while (history.size() > historyMaxSize) {
                history.remove(0);
            }

            SP.putString(KEY_SGV_HISTORY_JSON, gson.toJson(history));
        } catch (Exception e) {
            EselLog.LogE(TAG, "Errore history: " + e.getMessage());
        }
    }

    private void stopSelfService() {
        SP.putBoolean("service_should_be_running", false);
        SP.putBoolean("enable_service", false);
        if (syncTriggerHandler != null) {
            syncTriggerHandler.removeCallbacks(syncTriggerRunnable);
        }
        if (bufferHandler != null && bufferRunnable != null) {
            bufferHandler.removeCallbacks(bufferRunnable);
        }
        WatchdogReceiver.cancelWatchdog(this);
        FastPatrolReceiver.cancel(this);
        stopForeground(true);
        stopSelf();
    }

    private String getTrendArrow(String direction) {
        if (direction == null) return "→";
        switch (direction) {
            case "DoubleUp": return "↑↑";
            case "SingleUp": return "↑";
            case "FortyFiveUp": return "↗";
            case "Flat": return "→";
            case "FortyFiveDown": return "↘";
            case "SingleDown": return "↓";
            case "DoubleDown": return "↓↓";
            default: return "→";
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopSyncTrigger();
        if (sgvDataReceiver != null) {
            try { LocalBroadcastManager.getInstance(this).unregisterReceiver(sgvDataReceiver); } catch (Exception ignored) {}
        }
        if (executor != null) executor.shutdown();
        if (wakeLock != null && wakeLock.isHeld()) { try { wakeLock.release(); } catch (Exception ignored) {} }

        if (SP.getBoolean("service_should_be_running", false)) {
            sendBroadcast(new Intent(this, ServiceRestarter.class));
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (SP.getBoolean("service_should_be_running", false)) {
            sendBroadcast(new Intent(this, ServiceRestarter.class));
        }
        super.onTaskRemoved(rootIntent);
    }

    private void updateNotification(String contentText) {
        try {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.notify(NOTIFICATION_ID, buildNotification(contentText));
            }
        } catch (Exception ignored) {}
    }

    private Notification buildNotification(String contentText) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_persistent_title))
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_stat_esel_sync)
                .setColor(ContextCompat.getColor(this, R.color.green_primary))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setSilent(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            serviceChannel.setSound(null, null);
            serviceChannel.enableVibration(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(serviceChannel);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}