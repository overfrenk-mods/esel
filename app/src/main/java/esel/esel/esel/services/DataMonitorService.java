// ---------- CODICE CON FIX PER IL BUG DI ELABORAZIONE DATI ----------
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
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

    private static final long COOLDOWN_PERIOD_MS = (5 * 60 * 1000L) - 15000L;
    private static final long LONG_PAUSE_THRESHOLD_MS = 15 * 60 * 1000L;

    private ExecutorService executor;
    private BroadcastReceiver sgvDataReceiver;
    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        EselLog.LogI(TAG, "DataMonitorService onCreate.");
        executor = Executors.newSingleThreadExecutor();

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EselReader::DataProcessingWakeLock");

        createNotificationChannel();
        setupSgvDataReceiver();
        SP.putBoolean("service_should_be_running", true);
        Notification notification = buildNotification("Servizio in attesa di dati...");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC | ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
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
            final long now = System.currentTimeMillis();
            if (!isManualOverride) {
                final long lastSentTime = SP.getLong(KEY_LAST_SUCCESSFUL_SEND_MS, 0L);
                final long timeSinceLastProcess = now - lastSentTime;

                if (lastSentTime > 0 && timeSinceLastProcess > LONG_PAUSE_THRESHOLD_MS) {
                    EselLog.LogW(TAG, "[FILTRO] SCARTATO SGV(" + sgv.value + ") perché è il primo dopo una lunga pausa di " + (timeSinceLastProcess / 60000) + " min. Resetto il timer e lo stato.");
                    SP.putLong(KEY_LAST_SGV_TIMESTAMP, 0L);
                    SP.putInt(KEY_LAST_SGV_RAW_VALUE, -1);
                    SP.putInt(KEY_LAST_SGV_FINAL_VALUE, -1);
                    SP.putLong(KEY_LAST_SUCCESSFUL_SEND_MS, now);
                    return;
                }

                if (lastSentTime > 0 && timeSinceLastProcess < COOLDOWN_PERIOD_MS) {
                    EselLog.LogI(TAG, "[FILTRO] Scartato per cooldown. Ultimo invio troppo recente.");
                    return;
                }
            } else {
                EselLog.LogW(TAG, "SYNC MANUALE: Filtri temporali bypassati.");
            }

            EselLog.LogI(TAG, "SGV(" + sgv.value + ") ha superato i filtri. Inizio elaborazione.");

            int lastSentRawValue = SP.getInt(KEY_LAST_SGV_RAW_VALUE, -1);
            int lastSentFinalValue = SP.getInt(KEY_LAST_SGV_FINAL_VALUE, -1);
            long lastSgvTimestamp = SP.getLong(KEY_LAST_SGV_TIMESTAMP, 0L);
            boolean hasTimeGap = (lastSgvTimestamp > 0) && (sgv.timestamp - lastSgvTimestamp) > 12 * 60 * 1000L;

            // **FIX BUG "DATO INDIETRO"**
            // Lo smoothing viene applicato a una copia del dato, solo per calcolare lo slope.
            // Il valore originale (sgv) non viene mai modificato.
            SGV sgvForSlope = new SGV(sgv.raw, sgv.timestamp, 0);
            boolean smoothing_enabled = SP.getBoolean("smooth_data", false);
            if (smoothing_enabled && lastSentRawValue != -1 && !hasTimeGap) {
                sgvForSlope.smooth(lastSentRawValue);
            }

            double slopeByMinute = 0d;
            if (lastSgvTimestamp > 0 && !hasTimeGap) {
                long timeDiff = sgv.timestamp - lastSgvTimestamp;
                if (timeDiff > 0) {
                    slopeByMinute = (double) (sgvForSlope.value - lastSentFinalValue) * 60000.0d / (double) timeDiff;
                    sgv.setDirection(slopeByMinute);
                }
            }

            sgv.value = sgv.raw;
            DateFormat df = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            EselLog.LogI(TAG, "Pronto per invio: Valore=" + sgv.value + " (Grezzo=" + sgv.raw + ") | Direzione=" + sgv.direction);

            if (SP.getBoolean("send_to_AAPS", true)) { AapsSender.sendToAaps(getApplicationContext(), sgv); }
            if (SP.getBoolean("send_to_NS", false)) { AapsSender.sendToNsClient(getApplicationContext(), sgv); }

            SP.putLong(KEY_LAST_SUCCESSFUL_SEND_MS, System.currentTimeMillis());
            SP.putLong(KEY_LAST_SGV_TIMESTAMP, sgv.timestamp);
            SP.putInt(KEY_LAST_SGV_RAW_VALUE, sgv.raw);
            SP.putInt(KEY_LAST_SGV_FINAL_VALUE, sgv.raw);

            String trendArrow = getTrendArrow(sgv.direction);
            String notificationText = "Ultimo invio: " + sgv.value + " " + trendArrow + " alle " + df.format(new Date(sgv.timestamp));
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

    private void stopSelfService() {
        SP.putBoolean("service_should_be_running", false);
        SP.putBoolean("enable_service", false);
        WatchdogReceiver.cancelWatchdog(this);
        stopForeground(true);
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
        Intent notificationIntent = new Intent(this, esel.esel.esel.MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Eversense-Reader Attivo")
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(CHANNEL_ID, "Eversense-Reader Service", NotificationManager.IMPORTANCE_LOW);
            serviceChannel.setDescription("Notifica persistente per il monitoraggio dati.");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
}