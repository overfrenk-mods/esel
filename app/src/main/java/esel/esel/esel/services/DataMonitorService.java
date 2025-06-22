// Codice COMPLETO e DEFINITIVO da incollare in DataMonitorService.java
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
import esel.esel.esel.R;
import esel.esel.esel.receivers.RestartServiceReceiver;
import esel.esel.esel.util.EselLog;

public class DataMonitorService extends Service {
    private static final String TAG = "DataMonitorService";
    public static final String CHANNEL_ID = "EselMonitorChannel";
    public static final int NOTIFICATION_ID = 101;
    public static final int RESTART_ALARM_REQUEST_CODE = 456;

    @Override
    public void onCreate() {
        super.onCreate();
        EselLog.LogI(TAG, "DataMonitorService onCreate.");
        createNotificationChannel();
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        EselLog.LogI(TAG, "DataMonitorService avviato. Programmo la sveglia di sicurezza (watchdog).");
        scheduleRestartAlarm();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        EselLog.LogW(TAG, "DataMonitorService onDestroy.");
        super.onDestroy();
    }

    private void scheduleRestartAlarm() {
        Intent intent = new Intent(this, RestartServiceReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, RESTART_ALARM_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager manager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        // Usiamo un intervallo standard di 15 minuti, a basso consumo
        long interval = AlarmManager.INTERVAL_FIFTEEN_MINUTES;
        manager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, System.currentTimeMillis() + interval, interval, pendingIntent);
        EselLog.LogI(TAG, "Sveglia di rianimazione programmata ogni 15 minuti.");
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel( CHANNEL_ID, "Esel Monitor Service", NotificationManager.IMPORTANCE_LOW);
            serviceChannel.setDescription("Notifica persistente per mantenere il servizio attivo.");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) { manager.createNotificationChannel(serviceChannel); }
        }
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Esel Service Attivo")
                .setContentText("Monitoraggio dati in corso...")
                .setSmallIcon(R.drawable.ic_stat_esel_sync)
                .setColor(ContextCompat.getColor(this, R.color.green_primary))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .build();
    }
}