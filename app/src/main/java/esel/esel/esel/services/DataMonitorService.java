// ---------- CODICE COMPLETO, CORRETTO E PRONTO ALLA COMPILAZIONE ----------
package esel.esel.esel.services;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import esel.esel.esel.R;
import esel.esel.esel.datareader.SGV;
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
    public static final int WATCHDOG_REQUEST_CODE = 901;

    private ExecutorService executor;

    @Override
    public void onCreate() {
        super.onCreate();
        EselLog.LogI(TAG, "DataMonitorService onCreate.");
        executor = Executors.newSingleThreadExecutor();
        createNotificationChannel();

        SP.putBoolean("service_should_be_running", true);

        Notification notification = buildNotification("Servizio in attesa di dati...");
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
                EselLog.LogW(TAG, "Azione STOP ricevuta dalle impostazioni. Termino volontariamente il servizio.");
                SP.putBoolean("service_should_be_running", false);
                SP.putBoolean("enable_service", false);
                cancelWatchdogAlarm();
                stopForeground(true);
                stopSelf();
                return START_NOT_STICKY;
            }
            if (intent.hasExtra("sgv_data")) {
                final SGV sgv = (SGV) intent.getSerializableExtra("sgv_data");
                if (sgv != null) {
                    executor.execute(() -> processSgv(sgv));
                }
            }
        }
        return START_STICKY;
    }

    private void processSgv(SGV sgv) {
        try {
            int lastSentRawValue = SP.getInt("lastSentRawValue", -1);
            int lastSentFinalValue = SP.getInt("lastSentFinalValue", -1);
            long lastSentTime = SP.getLong("lastSentTime", -1L);

            if (lastSentTime > 0 && sgv.timestamp == lastSentTime) {
                EselLog.LogW(TAG, "SGV scartato: timestamp identico all'ultimo invio. (" + sgv.timestamp + ")");
                return;
            }

            boolean hasTimeGap = (lastSentTime > 0) && (sgv.timestamp - lastSentTime) > 12 * 60 * 1000L;
            int finalValue = sgv.raw;
            boolean smoothing_enabled = SP.getBoolean("smooth_data", false);
            if (smoothing_enabled && lastSentRawValue != -1 && !hasTimeGap) {
                sgv.smooth(lastSentRawValue);
                finalValue = sgv.value;
            }
            double slopeByMinute = 0d;
            if (lastSentTime > 0 && !hasTimeGap) {
                slopeByMinute = (double) (finalValue - lastSentFinalValue) * 60000.0d / (double) (sgv.timestamp - lastSentTime);
                sgv.setDirection(slopeByMinute);
            }
            sgv.value = finalValue;
            DateFormat df = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            EselLog.LogI(TAG, "Pronto per invio: Valore=" + sgv.value + " (Grezzo=" + sgv.raw + ") | Direzione=" + sgv.direction);
            if (SP.getBoolean("send_to_AAPS", true)) {
                AapsSender.sendToAaps(getApplicationContext(), sgv);
            }
            if (SP.getBoolean("send_to_NS", false)) {
                AapsSender.sendToNsClient(getApplicationContext(), sgv);
            }
            SP.putLong("lastSentTime", sgv.timestamp);
            SP.putInt("lastSentRawValue", sgv.raw);
            SP.putInt("lastSentFinalValue", finalValue);

            String trendArrow = getTrendArrow(sgv.direction);
            String notificationText = "Ultimo invio: " + sgv.value + " " + trendArrow + " alle " + df.format(new Date(sgv.timestamp));
            updateNotification(notificationText);

        } catch (Throwable t) {
            // Usiamo android.util.Log.e che accetta un Throwable per stampare l'intero errore.
            android.util.Log.e(TAG, "Errore critico durante il processamento del SGV:", t);
        }
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

    private void cancelWatchdogAlarm() {
        Intent intent = new Intent(this, WatchdogReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, WATCHDOG_REQUEST_CODE, intent, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pendingIntent != null) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null) {
                alarmManager.cancel(pendingIntent);
            }
            EselLog.LogI(TAG, "Allarme Watchdog cancellato.");
        }
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

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