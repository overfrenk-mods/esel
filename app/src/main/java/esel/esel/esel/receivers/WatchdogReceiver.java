package esel.esel.esel.receivers;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import esel.esel.esel.R;
import esel.esel.esel.services.DataMonitorService;
import esel.esel.esel.util.EselLog;
import esel.esel.esel.util.SP;
import esel.esel.esel.util.WakeLockHelper;

public class WatchdogReceiver extends BroadcastReceiver {

    private static final String TAG = "WatchdogReceiver";
    private static final long WATCHDOG_INTERVAL_MS = 15 * 60 * 1000L;
    private static final long WAKELOCK_TIMEOUT_MS = 60 * 1000L;

    private static final String LISTENER_ALERT_CHANNEL_ID = "EselListenerAlertChannel";
    private static final int LISTENER_ALERT_NOTIFICATION_ID = 103;

    @Override
    public void onReceive(Context context, Intent intent) {
        EselLog.LogI(TAG, "Watchdog 'Guardiano' attivo. Controllo integrità sistema...");

        WakeLockHelper.acquire(context.getApplicationContext(), WAKELOCK_TIMEOUT_MS);

        final PendingResult pendingResult = goAsync();

        new Thread(() -> {
            try {
                scheduleNextWatchdog(context);

                if (!SP.getBoolean("enable_service", true)) {
                    EselLog.LogI(TAG, "Servizio disabilitato dall'utente. Watchdog in pausa.");
                    return;
                }

                checkNotificationListenerPermission(context);
                keepServiceAlive(context);

            } catch (Exception e) {
                EselLog.LogE(TAG, "Errore durante ciclo Watchdog: " + e.getMessage());
            } finally {
                if (pendingResult != null) {
                    pendingResult.finish();
                }
                WakeLockHelper.release();
            }
        }).start();
    }

    private void keepServiceAlive(Context context) {
        try {
            Intent serviceIntent = new Intent(context, DataMonitorService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
            EselLog.LogI(TAG, "Ping al DataMonitorService inviato.");
        } catch (Exception e) {
            EselLog.LogE(TAG, "Impossibile avviare il servizio: " + e.getMessage());
        }
    }

    private void checkNotificationListenerPermission(Context context) {
        if (!isNotificationServiceEnabled(context)) {
            EselLog.LogE(TAG, "PERICOLO: Permesso lettura notifiche DISATTIVATO!");
            showListenerPermissionWarningNotification(context);
        }
    }

    private boolean isNotificationServiceEnabled(Context context) {
        String pkgName = context.getPackageName();
        final String flat = Settings.Secure.getString(context.getContentResolver(), "enabled_notification_listeners");
        if (!TextUtils.isEmpty(flat)) {
            final String[] names = flat.split(":");
            for (String name : names) {
                final ComponentName cn = ComponentName.unflattenFromString(name);
                if (cn != null && TextUtils.equals(pkgName, cn.getPackageName())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void scheduleNextWatchdog(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                EselLog.LogW(TAG, "Manca permesso SCHEDULE_EXACT_ALARM.");
            }
        }

        Intent intent = new Intent(context, WatchdogReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, DataMonitorService.WATCHDOG_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        long triggerAtMillis = System.currentTimeMillis() + WATCHDOG_INTERVAL_MS;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
        }
    }

    public static void cancelWatchdog(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, WatchdogReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, DataMonitorService.WATCHDOG_REQUEST_CODE, intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);

        if (pendingIntent != null && alarmManager != null) {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
            EselLog.LogI(TAG, "Watchdog disattivato.");
        }
    }

    private void showListenerPermissionWarningNotification(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(LISTENER_ALERT_CHANNEL_ID, "Avvisi Permessi ESEL", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Avvisa se l'app perde il permesso di leggere Eversense");
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }

        Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 1, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // --- MODIFICA MULTILINGUA QUI ---
        // Ora usa le stringhe XML invece del testo fisso
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, LISTENER_ALERT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_esel_sync)
                .setContentTitle(context.getString(R.string.notification_permission_title))
                .setContentText(context.getString(R.string.notification_permission_text))
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(context.getString(R.string.notification_permission_text)))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .setAutoCancel(false);

        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                NotificationManagerCompat.from(context).notify(LISTENER_ALERT_NOTIFICATION_ID, builder.build());
            }
        } catch (Exception ignored) {}
    }
}