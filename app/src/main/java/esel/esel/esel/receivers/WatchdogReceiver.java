// ---------- CODICE GIÀ CORRETTO E VERIFICATO ----------
package esel.esel.esel.receivers;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log; // <-- IMPORT AGGIUNTO

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import esel.esel.esel.R;
import esel.esel.esel.services.DataMonitorService;
import esel.esel.esel.util.EselLog;
import esel.esel.esel.util.SP;

public class WatchdogReceiver extends BroadcastReceiver {

    private static final String TAG = "WatchdogReceiver";
    private static final long WATCHDOG_INTERVAL_MS = 15 * 60 * 1000L;

    private static final int RESTART_THRESHOLD = 3;
    private static final long WINDOW_HOUR_MS = 60 * 60 * 1000L;
    private static final String ALERT_CHANNEL_ID = "EselAlertChannel";
    private static final int ALERT_NOTIFICATION_ID = 102;

    @Override
    public void onReceive(Context context, Intent intent) {
        EselLog.LogW(TAG, "Allarme Watchdog esatto scattato! Eseguo controllo...");

        scheduleNextWatchdog(context);

        if (!SP.getBoolean("enable_service", true)) {
            EselLog.LogI(TAG, "Il servizio è stato fermato volontariamente dall'utente. Il watchdog non interviene.");
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
        try {
            ContextCompat.startForegroundService(context, new Intent(context, DataMonitorService.class));
            EselLog.LogI(TAG, "Comando di avvio per DataMonitorService inviato con successo.");
        } catch (Exception e) {
            // --- MODIFICA: Uso il logger standard di Android che accetta le eccezioni ---
            Log.e(TAG, "ERRORE: Android ha bloccato il tentativo di avvio del servizio dal Watchdog!", e);
        }
    }

    public static void scheduleNextWatchdog(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, WatchdogReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, DataMonitorService.WATCHDOG_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + WATCHDOG_INTERVAL_MS,
                pendingIntent
        );
        EselLog.LogI(TAG, "Watchdog: Prossimo allarme esatto pianificato tra 15 minuti.");
    }

    public static void cancelWatchdog(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, WatchdogReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, DataMonitorService.WATCHDOG_REQUEST_CODE, intent, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
            EselLog.LogW(TAG, "Catena di allarmi Watchdog CANCELLATA.");
        }
    }

    private void showRestartWarningNotification(Context context) {
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

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            notificationManagerCompat.notify(ALERT_NOTIFICATION_ID, builder.build());
        } else {
            EselLog.LogW(TAG, "Permesso notifiche non concesso. Impossibile mostrare l'avviso di riavvio.");
        }
    }
}