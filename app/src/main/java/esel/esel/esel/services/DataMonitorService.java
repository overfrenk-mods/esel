// ---------- CODICE CON LOGICA DEFINITIVA "SALA D'ATTESA" ----------
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
import java.util.concurrent.atomic.AtomicReference;

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

    private static final long TIMESTAMP_COOLDOWN_MS = (5 * 60 * 1000L) - 30000L; // 4 minuti e 30 secondi
    private static final long LONG_PAUSE_THRESHOLD_MS = 15 * 60 * 1000L;

    private ExecutorService executor;
    private BroadcastReceiver sgvDataReceiver;
    private PowerManager.WakeLock wakeLock;
    private Gson gson;

    // --- LOGICA "SALA D'ATTESA" ---
    private final AtomicReference<SGV> datoInAttesa = new AtomicReference<>(null);
    private Handler delayedSendHandler;
    private Runnable delayedSendRunnable;
    // --- FINE ---


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
        delayedSendHandler = new Handler(Looper.getMainLooper());

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EselReader::DataProcessingWakeLock");

        createNotificationChannel();
        setupSgvDataReceiver();

        WatchdogReceiver.scheduleNextWatchdog(this);

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
            String action = intent.getAction();
            if (ACTION_STOP_SERVICE.equals(action)) {
                EselLog.LogW(TAG, "Azione STOP ricevuta. Termino volontariamente il servizio.");
                stopSelfService();
                return START_NOT_STICKY;
            }
            if (ACTION_MANUAL_SYNC.equals(action)) {
                EselLog.LogW(TAG, "RICEVUTO COMANDO DI SYNC MANUALE!");
                if (intent.hasExtra(EsNotificationListener.EXTRA_SGV_DATA)) {
                    final SGV sgv = (SGV) intent.getSerializableExtra(EsNotificationListener.EXTRA_SGV_DATA);
                    if (sgv != null) {
                        executor.execute(() -> sendSgv(sgv, true));
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
                        executor.execute(() -> processSgv(sgv));
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter(EsNotificationListener.ACTION_NEW_SGV_DATA);
        LocalBroadcastManager.getInstance(this).registerReceiver(sgvDataReceiver, filter);
    }

    private void processSgv(SGV sgv) {
        long lastSuccessfulSendTimestamp = SP.getLong(KEY_LAST_SGV_TIMESTAMP, 0L);
        long timeSinceLastSend = (lastSuccessfulSendTimestamp > 0) ? sgv.timestamp - lastSuccessfulSendTimestamp : Long.MAX_VALUE;

        // Se il cooldown è passato, invia subito
        if (timeSinceLastSend >= TIMESTAMP_COOLDOWN_MS) {
            EselLog.LogI(TAG, "Cooldown superato. Invio immediato del dato: " + sgv.value);
            // Cancella qualsiasi invio programmato e il dato in attesa, perché stiamo inviando uno più fresco
            cancelDelayedSend();
            sendSgv(sgv, false);
        } else {
            // Se il cooldown è attivo, mettiamo il dato nella "sala d'attesa"
            EselLog.LogI(TAG, "Cooldown attivo. Metto il dato " + sgv.value + " in sala d'attesa.");
            datoInAttesa.set(sgv);
            scheduleDelayedSend();
        }
    }

    private void scheduleDelayedSend() {
        // Se c'è già un invio programmato, non fare nulla, aspetterà il suo turno
        if (delayedSendRunnable != null) {
            EselLog.LogI(TAG, "Invio ritardato già programmato. Attendo.");
            return;
        }

        long lastSuccessfulSendTimestamp = SP.getLong(KEY_LAST_SGV_TIMESTAMP, 0L);
        long timeSinceLastSend = System.currentTimeMillis() - lastSuccessfulSendTimestamp;
        long delay = TIMESTAMP_COOLDOWN_MS - timeSinceLastSend;

        if (delay <= 0) {
            // Se il ritardo è negativo, significa che il cooldown è già scaduto, invia subito
            EselLog.LogW(TAG, "Delay calcolato negativo o nullo, invio subito il dato in attesa.");
            sendWaitingSgv();
            return;
        }

        EselLog.LogI(TAG, "Programmo l'invio del dato in attesa tra " + (delay / 1000) + " secondi.");
        delayedSendRunnable = this::sendWaitingSgv;
        delayedSendHandler.postDelayed(delayedSendRunnable, delay);
    }

    private void sendWaitingSgv() {
        SGV sgvToSend = datoInAttesa.getAndSet(null); // Prende il dato e svuota la sala d'attesa
        if (sgvToSend != null) {
            EselLog.LogI(TAG, "Timer scaduto. Invio il dato più fresco dalla sala d'attesa: " + sgvToSend.value);
            sendSgv(sgvToSend, false);
        } else {
            EselLog.LogW(TAG, "Timer scaduto, ma la sala d'attesa era vuota.");
        }
        delayedSendRunnable = null; // Resetta il runnable
    }

    private void cancelDelayedSend() {
        if (delayedSendRunnable != null) {
            EselLog.LogW(TAG, "Annullato invio ritardato perché è arrivato un dato più fresco da inviare subito.");
            delayedSendHandler.removeCallbacks(delayedSendRunnable);
            delayedSendRunnable = null;
        }
        datoInAttesa.set(null); // Svuota comunque la sala d'attesa
    }


    private void sendSgv(SGV sgv, boolean isManualOverride) {
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire(20 * 1000L);
            EselLog.LogW(TAG, "WakeLock acquisito per l'elaborazione dei dati.");
        }
        try {
            if (isManualOverride) {
                EselLog.LogW(TAG, "SYNC MANUALE: Invio forzato.");
            }

            long lastSgvTimestamp = SP.getLong(KEY_LAST_SGV_TIMESTAMP, 0L);
            long timeSinceLastSgv = sgv.timestamp - lastSgvTimestamp;

            if (lastSgvTimestamp > 0 && timeSinceLastSgv > LONG_PAUSE_THRESHOLD_MS) {
                EselLog.LogW(TAG, "Rilevata lunga pausa di " + (timeSinceLastSgv / 60000) + " min. Resetto lo stato per il calcolo della pendenza.");
                SP.putLong(KEY_LAST_SGV_TIMESTAMP, 0L);
                SP.putInt(KEY_LAST_SGV_RAW_VALUE, -1);
                SP.putInt(KEY_LAST_SGV_FINAL_VALUE, -1);
                lastSgvTimestamp = 0;
            }

            int lastSentRawValue = SP.getInt(KEY_LAST_SGV_RAW_VALUE, -1);
            int lastSentFinalValue = SP.getInt(KEY_LAST_SGV_FINAL_VALUE, -1);

            SGV sgvForSlope = new SGV(sgv.raw, sgv.timestamp, 0);
            boolean smoothing_enabled = SP.getBoolean("smooth_data", false);
            if (smoothing_enabled && lastSentRawValue != -1) {
                sgvForSlope.smooth(lastSentRawValue);
            }

            double slopeByMinute = 0d;
            if (lastSgvTimestamp > 0) {
                long timeDiff = sgv.timestamp - lastSgvTimestamp;
                if (timeDiff > 0) {
                    slopeByMinute = (double) (sgvForSlope.value - lastSentFinalValue) * 60000.0d / (double) timeDiff;
                    sgv.setDirection(slopeByMinute);
                }
            }

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
            android.util.Log.e(TAG, "Errore critico durante l'invio del SGV:", t);
        } finally {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
                EselLog.LogW(TAG, "WakeLock rilasciato.");
            }
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
        cancelDelayedSend(); // Annulla eventuali invii programmati prima di chiudere
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