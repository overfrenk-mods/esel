// ---------- CODICE FINALE E SEMPLIFICATO PER WatchdogReceiver.java ----------
package esel.esel.esel.receivers;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import esel.esel.esel.R;
import esel.esel.esel.services.DataMonitorService;
import esel.esel.esel.util.EselLog;
import esel.esel.esel.util.SP;

public class WatchdogReceiver extends BroadcastReceiver {

    private static final String TAG = "WatchdogReceiver";

    private static final int RESTART_THRESHOLD = 3;
    private static final long WINDOW_HOUR_MS = 60 * 60 * 1000L;
    private static final String ALERT_CHANNEL_ID = "EselAlertChannel";
    private static final int ALERT_NOTIFICATION_ID = 102;

    @Override
    public void onReceive(Context context, Intent intent) {
        EselLog.LogI(TAG, "Watchdog ricevuto. Avvio controllo intelligente...");

        if (!SP.getBoolean("service_should_be_running", false)) {
            EselLog.LogI(TAG, "Il servizio è stato fermato volontariamente. Il watchdog non fa nulla.");
            return;
        }

        long now = System.currentTimeMillis();
        int restartCount = SP.getInt("watchdog_restart_count", 0);
        long firstRestartTimestamp = SP.getLong("watchdog_first_restart_timestamp", 0L);

        if (firstRestartTimestamp == 0 || (now - firstRestartTimestamp) > WINDOW_HOUR_MS) {
            EselLog.LogI(TAG, "Finestra di un'ora resettata. Questo è il primo riavvio della serie.");
            restartCount = 1;
            SP.putLong("watchdog_first_restart_timestamp", now);
        } else {
            restartCount++;
        }

        EselLog.LogW(TAG, "Conteggio riavvii nell'ultima ora: " + restartCount);
        SP.putInt("watchdog_restart_count", restartCount);

        if (restartCount >= RESTART_THRESHOLD) {
            long lastWarningTime = SP.getLong("watchdog_last_warning_time", 0L);
            if ((now - lastWarningTime) > WINDOW_HOUR_MS) {
                EselLog.LogE(TAG, "Soglia di riavvio superata! Mostro notifica di avviso all'utente.");
                showRestartWarningNotification(context);
                SP.putLong("watchdog_last_warning_time", now);
                SP.putInt("watchdog_restart_count", 0);
                SP.putLong("watchdog_first_restart_timestamp", 0L);
            }
        }

        EselLog.LogW(TAG, "Il servizio dovrebbe essere attivo. Avvio preventivo per sicurezza...");
        ContextCompat.startForegroundService(context, new Intent(context, DataMonitorService.class));
    }

    // --- METODO SEMPLIFICATO ---
    private void showRestartWarningNotification(Context context) {
        // Il controllo if(Build.VERSION...) è stato rimosso, perché la minSdk è 33 (Android 13)
        // e i canali di notifica esistono da Android 8.
        NotificationChannel channel = new NotificationChannel(ALERT_CHANNEL_ID, "Avvisi Critici Eversense-Reader", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Notifiche per problemi critici di funzionamento dell'app");
        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);

        Intent intent = new Intent("android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS");
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_esel_sync)
                .setContentTitle("Attenzione: Riavvi Frequenti")
                .setContentText("Il servizio di monitoraggio si sta riavviando troppo spesso. Controlla le impostazioni della batteria.")
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("Il servizio di monitoraggio si sta riavviando troppo spesso. Questo può essere causato dalle impostazioni di risparmio energetico del telefono. Clicca qui per controllare."))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(context);

        // Questo controllo rimane perché il permesso può essere concesso o revocato dall'utente in qualsiasi momento.
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            notificationManagerCompat.notify(ALERT_NOTIFICATION_ID, builder.build());
        } else {
            EselLog.LogW(TAG, "Permesso notifiche non concesso. Impossibile mostrare l'avviso di riavvio.");
        }
    }
}