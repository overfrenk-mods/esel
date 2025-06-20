package esel.esel.esel.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import esel.esel.esel.R;
import esel.esel.esel.receivers.ReadReceiver;
import esel.esel.esel.util.EselLog;

public class DataMonitorService extends Service {

    private static final String TAG = "DataMonitorService";
    public static final String CHANNEL_ID = "EselMonitorChannel";
    public static final int NOTIFICATION_ID = 101;

    @Override
    public void onCreate() {
        super.onCreate();
        EselLog.LogI(TAG, "DataMonitorService onCreate. Preparazione del servizio in primo piano.");
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
        EselLog.LogI(TAG, "DataMonitorService onStartCommand. Avvio la prima sveglia della catena.");
        Intent broadcastIntent = new Intent(this, ReadReceiver.class);
        sendBroadcast(broadcastIntent);
        return START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy() {
        EselLog.LogI(TAG, "DataMonitorService onDestroy. La catena di sveglie viene interrotta.");
        ReadReceiver.cancelAlarm(this);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            serviceChannel.setDescription(getString(R.string.channel_description));
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) { manager.createNotificationChannel(serviceChannel); }
        }
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_content))
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .build();
    }
}