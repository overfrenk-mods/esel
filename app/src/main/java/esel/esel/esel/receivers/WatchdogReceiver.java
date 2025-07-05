// ---------- CODICE CON FIX ANTI-ANR (goAsync) ----------
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
import android.provider.Settings;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.util.Set;

import esel.esel.esel.MainActivity;
import esel.esel.esel.R;
import esel.esel.esel.services.DataMonitorService;
import esel.esel.esel.util.EselLog;
import esel.esel.esel.util.SP;

public class WatchdogReceiver extends BroadcastReceiver {

    private static final String TAG = "WatchdogReceiver";
    private static final long WATCHDOG_INTERVAL_MS = 15 * 60 * 1000L;

    private static final int RESTART_THRESHOLD = 3;
    private static final long WINDOW_HOUR_MS = 60 * 60 * 1000L;
    private static final String RESTART_ALERT_CHANNEL_ID = "EselRestartAlertChannel";
    private static final int RESTART_ALERT_NOTIFICATION_ID = 102;

    private static final String LISTENER_ALERT_CHANNEL_ID = "EselListenerAlertChannel";
    private static final int LISTENER_ALERT_NOTIFICATION_ID = 103;
    private static final int SHOW_APP_REQUEST_CODE = 902;

    @Override
    public void onReceive(Context context, Intent intent) {
        EselLog.LogW(TAG, "Allarme Watchdog 'L'Origlione' scattato!");

        // --- FIX ANTI-ANR: Usiamo goAsync per spostare il lavoro in background ---
        final PendingResult pendingResult = goAsync();

        new Thread(() -> {
            try {
                // Tutta la logica viene eseguita in un thread separato
                scheduleNextWatchdog(context);

                if (!SP.getBoolean("enable_service", true)) {
                    EselLog.LogI(TAG, "Il servizio è stato fermato volontariamente dall'utente. Il watchdog non interviene.");
                    return; // Esce dal thread, il finish() verrà chiamato nel finally
                }

                checkNotificationListenerPermission(context);
                checkAndRestartService(context);

            } finally {
                // Diciamo al sistema che abbiamo finito il nostro lavoro in background.
                // Questo è FONDAMENTALE.
                if (pendingResult != null) {
                    pendingResult.finish();
                }
                EselLog.LogI(TAG, "Watchdog ha completato il lavoro in background.");
            }
        }).start();
    }

    private void checkAndRestartService(Context context) {
        long now = System.currentTimeMillis();
        int restartCount = SP.getInt("watchdog_restart_count", 0);
        long firstRestartTimestamp = SP.getLong("watchdog_first_restart_timestamp", 0L);

        if (firstRestartTimestamp == 0 || (now - firstRestartTimestamp) > WINDOW_HOUR_MS) {
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
                SP.putInt("watchdog_restart_count", 0);
                SP.putLong("watchdog_first_restart_timestamp", 0L);
            }
        }

        EselLog.LogW(TAG, "Il servizio dovrebbe essere attivo. Avvio preventivo per sicurezza...");
        try {
            ContextCompat.startForegroundService(context, new Intent(context, DataMonitorService.class));
            EselLog.LogI(TAG, "Comando di avvio per DataMonitorService inviato con successo.");
        } catch (Exception e) {
            Log.e(TAG, "ERRORE: Android ha bloccato il tentativo di avvio del servizio dal Watchdog!", e);
        }
    }

    private void checkNotificationListenerPermission(Context context) {
        Set<String> enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(context);
        if (enabledListeners.contains(context.getPackageName())) {
            EselLog.LogI(TAG, "Controllo permesso notifiche: OK.");
        } else {
            EselLog.LogE(TAG, "Controllo permesso notifiche: FALLITO! Il permesso non è più attivo.");
            showListenerPermissionWarningNotification(context);
        }
    }


    public static void scheduleNextWatchdog(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            EselLog.LogE(TAG, "Impossibile ottenere AlarmManager.");
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                EselLog.LogE(TAG, "L'app non ha il permesso di pianificare allarmi esatti. Il Watchdog non può funzionare.");
                return;
            }
        }

        Intent intent = new Intent(context, WatchdogReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, DataMonitorService.WATCHDOG_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent showActivityIntent = new Intent(context, MainActivity.class);
        PendingIntent showActivityPendingIntent = PendingIntent.getActivity(context, SHOW_APP_REQUEST_CODE, showActivityIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        long triggerAtMillis = System.currentTimeMillis() + WATCHDOG_INTERVAL_MS;
        AlarmManager.AlarmClockInfo alarmClockInfo = new AlarmManager.AlarmClockInfo(triggerAtMillis, showActivityPendingIntent);

        alarmManager.setAlarmClock(alarmClockInfo, pendingIntent);

        EselLog.LogI(TAG, "Watchdog 'L'Origlione': Prossimo allarme ad alta priorità pianificato tra 15 minuti.");
    }

    public static void cancelWatchdog(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, WatchdogReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, DataMonitorService.WATCHDOG_REQUEST_CODE, intent, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);

        if (pendingIntent != null && alarmManager != null) {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
            EselLog.LogW(TAG, "Catena di allarmi Watchdog CANCELLATA.");
        }
    }

    private void showRestartWarningNotification(Context context) {
        NotificationChannel channel = new NotificationChannel(RESTART_ALERT_CHANNEL_ID, "Avvisi Riavvio ESEL", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Notifiche per problemi critici di funzionamento dell'app");
        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);

        Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, RESTART_ALERT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_esel_sync)
                .setContentTitle("Attenzione: Riavvi Frequenti")
                .setContentText("Il servizio di monitoraggio si sta riavviando troppo spesso. Clicca per controllare le impostazioni della batteria.")
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("Il servizio di monitoraggio si sta riavviando troppo spesso. Questo può essere causato dalle impostazioni di risparmio energetico. Clicca per controllare."))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context).notify(RESTART_ALERT_NOTIFICATION_ID, builder.build());
        } else {
            EselLog.LogW(TAG, "Permesso notifiche non concesso. Impossibile mostrare l'avviso di riavvio.");
        }
    }

    private void showListenerPermissionWarningNotification(Context context) {
        NotificationChannel channel = new NotificationChannel(LISTENER_ALERT_CHANNEL_ID, "Avvisi Permessi ESEL", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Notifiche per permessi mancanti o disattivati");
        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);

        Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, LISTENER_ALERT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_esel_sync)
                .setContentTitle("ATTENZIONE: Permesso Mancante")
                .setContentText("ESEL non può leggere i dati. Clicca qui per riattivare il permesso di accesso alle notifiche.")
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText("ESEL non può leggere i dati glicemici perché il permesso di accesso alle notifiche è stato disattivato. Clicca qui per andare alle impostazioni e riattivarlo."))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .setAutoCancel(false);

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context).notify(LISTENER_ALERT_NOTIFICATION_ID, builder.build());
        } else {
            EselLog.LogW(TAG, "Permesso notifiche non concesso. Impossibile mostrare l'avviso del listener.");
        }
    }
}