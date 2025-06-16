package esel.esel.esel.services; // QUESTA RIGA DEVE ESSERE ESATTA E CORRISPONDERE AL PERCORSO

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import esel.esel.esel.R; // Assicurati che questo import sia corretto per il tuo package principale
import esel.esel.esel.receivers.ReadReceiver;
import esel.esel.esel.util.EselLog; // Assicurati che questo import sia corretto

public class DataMonitorService extends Service {

    private static final String TAG = "DataMonitorService";
    public static final String CHANNEL_ID = "EselMonitorChannel";
    public static final int NOTIFICATION_ID = 101;

    private Handler handler;
    private Runnable runnable;
    private ReadReceiver readReceiverLogic;

    @Override
    public void onCreate() {
        super.onCreate();
        EselLog.LogI(TAG, "DataMonitorService onCreate");

        createNotificationChannel();
        Notification notification = buildNotification();
        startForeground(NOTIFICATION_ID, notification);

        handler = new Handler(Looper.getMainLooper());
        readReceiverLogic = new ReadReceiver();

        runnable = new Runnable() {
            @Override
            public void run() {
                EselLog.LogI(TAG, "Esecuzione periodica di lettura dati...");

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        readReceiverLogic.CallBroadcast(getApplicationContext());
                    }
                }).start();

                handler.postDelayed(this, ReadReceiver.REPEAT_TIME);
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        EselLog.LogI(TAG, "DataMonitorService onStartCommand");
        handler.removeCallbacks(runnable);
        handler.post(runnable);
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        EselLog.LogI(TAG, "DataMonitorService onDestroy");
        handler.removeCallbacks(runnable);
        super.onDestroy();
    }

    private void createNotificationChannel() {
        EselLog.LogI(TAG, "Attempting to create notification channel.");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            serviceChannel.setDescription(getString(R.string.channel_description));
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
                EselLog.LogI(TAG, "Notification channel created successfully.");
            } else {
                EselLog.LogE(TAG, "NotificationManager is null, cannot create channel.");
            }
        } else {
            EselLog.LogI(TAG, "Notification channel not needed for this Android version.");
        }
    }

    private Notification buildNotification() {
        EselLog.LogI(TAG, "Attempting to build notification.");
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_content))
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .build();
        EselLog.LogI(TAG, "Notification built successfully.");
        return notification;
    }
}