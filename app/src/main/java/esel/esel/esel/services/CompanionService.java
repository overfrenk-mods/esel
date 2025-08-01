// ---------- CODICE PER IL NUOVO FILE: CompanionService.java ----------
package esel.esel.esel.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import esel.esel.esel.R;

/**
 * Questo è un servizio "compagno" per il DataMonitorService.
 * Il suo unico scopo è quello di essere avviato in foreground insieme al servizio principale.
 * Avere due servizi in foreground rende l'intero processo dell'app una priorità molto più alta
 * per il sistema operativo, rendendolo molto meno propenso a essere terminato per risparmio energetico.
 */
public class CompanionService extends Service {

    public static final String CHANNEL_ID = "EselCompanionChannel";
    public static final int NOTIFICATION_ID = 105; // ID Univoco

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.companion_service_title))
                .setContentText(getString(R.string.companion_service_text))
                .setSmallIcon(R.drawable.ic_stat_esel_sync)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build();

        startForeground(NOTIFICATION_ID, notification);

        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "ESEL Companion Service",
                    NotificationManager.IMPORTANCE_MIN
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
}