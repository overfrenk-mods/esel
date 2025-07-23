// ---------- CODICE CON COOLDOWN A 5 MINUTI PER TEST DERIVA ----------
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
    public static final int WATCHDOG_REQUEST_CODE = 901;

    public static final String KEY_LAST_SUCCESSFUL_SEND_MS = "status_last_successful_send_ms";
    public static final String KEY_LAST_SGV_TIMESTAMP = "status_last_sgv_timestamp";
    public static final String KEY_LAST_SGV_RAW_VALUE = "status_last_sgv_raw_value";
    public static final String KEY_LAST_SGV_FINAL_VALUE = "status_last_sgv_final_value";
    public static final String KEY_SGV_HISTORY_JSON = "sgv_history_json";

    // --- MODIFICA PER TEST: Impostato a 5 minuti esatti ---
    private static final long TIMESTAMP_COOLDOWN_MS = 5 * 60 * 1000L; // 5 minuti
    private static final long LONG_PAUSE_THRESHOLD_MS = 15 * 60 * 1000L;
    private static final long HEARTBEAT_INTERVAL_MS = 2 * 60 * 1000L;

    private ExecutorService executor;
    private BroadcastReceiver sgvDataReceiver;
    private PowerManager.WakeLock wakeLock;
    private Gson gson;

    private Handler heartbeatHandler;
    private Runnable heartbeatRunnable;

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
        heartbeatHandler = new Handler(Looper.getMainLooper());

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EselReader::DataProcessingWakeLock");

        createNotificationChannel();
        setupSgvDataReceiver();
        WatchdogReceiver.scheduleNextWatchdog(this);
        startHeartbeat();

        SP.putBoolean("service_should_be_running", true);
        Notification notification = buildNotification(getString(R.string.notification_persistent_text_waiting));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if (ACTION_STOP_SERVICE.equals(intent.getAction())) {
                EselLog.LogW(TAG, "Azione STOP ricevuta. Termino volontariamente il servizio.");
                stopSelfService();
                return START_NOT_STICKY;
            }
            if (ACTION_MANUAL_SYNC.equals(intent.getAction())) {
                EselLog.LogW(TAG, "RICEVUTO COMANDO DI SYNC MANUALE!");
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
            wakeLock.acquire(20 * 1000L);
            EselLog.LogW(TAG, "WakeLock acquisito per l'elaborazione dei dati.");
        }
        try {
            long lastSgvTimestamp = SP.getLong(KEY_LAST_SGV_TIMESTAMP, 0L);

            if (!isManualOverride) {
                long timeSinceLastSgv = sgv.timestamp - lastSgvTimestamp;

                if (lastSgvTimestamp > 0 && timeSinceLastSgv > LONG_PAUSE_THRESHOLD_MS) {
                    EselLog.LogW(TAG, "Rilevata lunga pausa di " + (timeSinceLastSgv / 60000) + " min. Resetto stato pendenza.");
                    SP.putLong(KEY_LAST_SGV_TIMESTAMP, 0L);
                    SP.putInt(KEY_LAST_SGV_RAW_VALUE, -1);
                    SP.putInt(KEY_LAST_SGV_FINAL_VALUE, -1);
                }
                else if (lastSgvTimestamp > 0 && timeSinceLastSgv < TIMESTAMP_COOLDOWN_MS) {
                    EselLog.LogI(TAG, "[FILTRO] Dato scartato per cooldown. Intervallo: " + (timeSinceLastSgv / 1000) + "s < " + (TIMESTAMP_COOLDOWN_MS / 1000) + "s");
                    return;
                }
            } else {
                EselLog.LogW(TAG, "SYNC MANUALE: Filtri temporali bypassati.");
            }

            EselLog.LogI(TAG, "SGV(" + sgv.value + ") ha superato i filtri. Inizio elaborazione.");

            calculateTrend(sgv);

            sgv.value = sgv.raw;
            EselLog.LogI(TAG, "Pronto per invio: Valore=" + sgv.value + " (Grezzo=" + sgv.raw + ") | Direzione=" + sgv.direction);

            if (SP.getBoolean("send_to_AAPS", true)) { AapsSender.sendToAaps(getApplicationContext(), sgv); }
            if (SP.getBoolean("send_to_NS", false)) { AapsSender.sendToNsClient(getApplicationContext(), sgv); }

            SP.putLong(KEY_LAST_SUCCESSFUL_SEND_MS, System.currentTimeMillis());
            SP.putLong(KEY_LAST_SGV_TIMESTAMP, sgv.timestamp);
            SP.putInt(KEY_LAST_SGV_RAW_VALUE, sgv.raw);
            SP.putInt(KEY_LAST_SGV_FINAL_VALUE, sgv.raw);

            updateSgvHistory(sgv);

            DateFormat df = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            String trendArrow = getTrendArrow(sgv.direction);
            String formattedTime = df.format(new Date(sgv.timestamp));
            String notificationText = getString(R.string.notification_persistent_text_last_send, String.valueOf(sgv.value), trendArrow, formattedTime);
            updateNotification(notificationText);

        } catch (Throwable t) {
            android.util.Log.e(TAG, "Errore critico durante il processamento del SGV:", t);
        } finally {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
                EselLog.LogW(TAG, "WakeLock rilasciato.");
            }
        }
    }

    private void calculateTrend(SGV sgv) {
        long lastSgvTimestamp = SP.getLong(KEY_LAST_SGV_TIMESTAMP, 0L);
        int lastSentRawValue = SP.getInt(KEY_LAST_SGV_RAW_VALUE, -1);
        int lastSentFinalValue = SP.getInt(KEY_LAST_SGV_FINAL_VALUE, -1);

        if (lastSgvTimestamp <= 0 || lastSentFinalValue == -1) {
            sgv.direction = "Flat";
            return;
        }

        long timeDiff = sgv.timestamp - lastSgvTimestamp;
        if (timeDiff <= 0) {
            sgv.direction = "Flat";
            return;
        }

        SGV sgvForSlope = new SGV(sgv.raw, sgv.timestamp, 0);
        boolean smoothing_enabled = SP.getBoolean("smooth_data", false);
        if (smoothing_enabled && lastSentRawValue != -1) {
            sgvForSlope.smooth(lastSentRawValue);
        }

        double slopeByMinute = (double) (sgvForSlope.value - lastSentFinalValue) * 60000.0d / (double) timeDiff;

        if (slopeByMinute <= -3.5) {
            sgv.direction = "DoubleDown";
        } else if (slopeByMinute <= -2.0) {
            sgv.direction = "SingleDown";
        } else if (slopeByMinute <= -1.0) {
            sgv.direction = "FortyFiveDown";
        } else if (slopeByMinute < 1.0) {
            sgv.direction = "Flat";
        } else if (slopeByMinute < 2.0) {
            sgv.direction = "FortyFiveUp";
        } else if (slopeByMinute < 3.5) {
            sgv.direction = "SingleUp";
        } else {
            sgv.direction = "DoubleUp";
        }
    }


    private void startHeartbeat() {
        heartbeatRunnable = new Runnable() {
            @Override
            public void run() {
                EselLog.LogI(TAG, "Heartbeat check... Service is alive.");
                heartbeatHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS);
            }
        };
        heartbeatHandler.post(heartbeatRunnable);
        EselLog.LogW(TAG, "Heartbeat di sopravvivenza avviato. Controllo ogni 2 minuti.");
    }

    private void stopHeartbeat() {
        if (heartbeatHandler != null && heartbeatRunnable != null) {
            heartbeatHandler.removeCallbacks(heartbeatRunnable);
            EselLog.LogW(TAG, "Heartbeat di sopravvivenza fermato.");
        }
    }


    private void updateSgvHistory(SGV newSgv) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            String durationHoursStr = prefs.getString("graph_duration_hours", "3");
            int durationHours = Integer.parseInt(durationHoursStr);
            int historyMaxSize = durationHours * 12;

            String historyJson = SP.getString(KEY_SGV_HISTORY_JSON, "[]");
            Type listType = new TypeToken<ArrayList<SgvHistoryPoint>>() {}.getType();
            List<SgvHistoryPoint> history = gson.fromJson(historyJson, listType);
            if (history == null) {
                history = new ArrayList<>();
            }

            history.add(new SgvHistoryPoint(newSgv.timestamp, newSgv.value));

            while (history.size() > historyMaxSize) {
                history.remove(0);
            }

            String newHistoryJson = gson.toJson(history);
            SP.putString(KEY_SGV_HISTORY_JSON, newHistoryJson);
        } catch (Exception e) {
            android.util.Log.e(TAG, "Errore durante l'aggiornamento della cronologia SGV", e);
        }
    }

    private void stopSelfService() {
        EselLog.LogW(TAG, "Inizio procedura di arresto volontario del servizio.");
        SP.putBoolean("service_should_be_running", false);
        SP.putBoolean("enable_service", false);
        stopHeartbeat();
        WatchdogReceiver.cancelWatchdog(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        stopSelf();
    }

    private String getTrendArrow(String direction) {
        if (direction == null) return "↔";
        switch (direction) {
            case "DoubleUp": return "↑↑";
            case "SingleUp": return "↑";
            case "FortyFiveUp": return "↗";
            case "Flat": return "→";
            case "FortyFiveDown": return "↘";
            case "SingleDown": return "↓";
            case "DoubleDown": return "↓↓";
            default: return "↔";
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EselLog.LogW(TAG, "DataMonitorService onDestroy.");
        stopHeartbeat();
        if (sgvDataReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(sgvDataReceiver);
        }
        if (executor != null) {
            executor.shutdown();
        }
        if (SP.getBoolean("service_should_be_running", false)) {
            EselLog.LogW(TAG, "Distruzione non volontaria. Invio broadcast per il riavvio...");
            Intent broadcastIntent = new Intent(this, ServiceRestarter.class);
            sendBroadcast(broadcastIntent);
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        EselLog.LogW(TAG, "Task rimosso.");
        if (SP.getBoolean("service_should_be_running", false)) {
            EselLog.LogW(TAG, "Distruzione non volontaria. Invio broadcast per il riavvio...");
            Intent broadcastIntent = new Intent(this, ServiceRestarter.class);
            sendBroadcast(broadcastIntent);
        }
    }

    private void updateNotification(String contentText) {
        Notification notification = buildNotification(contentText);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, notification);
        }
    }

    private Notification buildNotification(String contentText) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_persistent_title))
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_stat_esel_sync)
                .setColor(ContextCompat.getColor(this, R.color.green_primary))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW
        );
        serviceChannel.setDescription(getString(R.string.notification_channel_description));

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(serviceChannel);
        }
    }
}