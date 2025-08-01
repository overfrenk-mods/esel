// ---------- CODICE PER IL NUOVO FILE: FastPatrolReceiver.java ----------
package esel.esel.esel.receivers;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;

import esel.esel.esel.services.DataMonitorService;
import esel.esel.esel.util.EselLog;
import esel.esel.esel.util.SP;

public class FastPatrolReceiver extends BroadcastReceiver {

    private static final String TAG = "FastPatrolReceiver";
    private static final long PATROL_INTERVAL_MS = 4 * 60 * 1000L; // 4 minuti
    private static final int PATROL_REQUEST_CODE = 902; // Un request code diverso dal Watchdog principale

    @Override
    public void onReceive(Context context, Intent intent) {
        EselLog.LogI(TAG, "Pattuglia Veloce attivata. Controllo servizio...");

        // Se il monitoraggio è stato disabilitato dall'utente, non fare nulla.
        if (!SP.getBoolean("enable_service", true)) {
            EselLog.LogI(TAG, "Servizio disabilitato, la pattuglia non interviene.");
            return;
        }

        // Questo è l'unico compito: avviare il servizio.
        // Se il servizio è già attivo, questa chiamata non fa nulla di dannoso.
        // Se il servizio è stato ucciso, lo riavvia.
        try {
            ContextCompat.startForegroundService(context, new Intent(context, DataMonitorService.class));
            EselLog.LogI(TAG, "Pattuglia Veloce: Comando di avvio per DataMonitorService inviato per sicurezza.");
        } catch (Exception e) {
            Log.e(TAG, "ERRORE: Android ha bloccato l'avvio del servizio dalla Pattuglia Veloce!", e);
        }
    }

    public static void schedule(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            EselLog.LogE(TAG, "Impossibile ottenere AlarmManager per la Pattuglia Veloce.");
            return;
        }

        // Controlliamo il permesso una sola volta qui, per pulizia.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            EselLog.LogE(TAG, "L'app non ha il permesso di pianificare allarmi esatti. La Pattuglia Veloce non può funzionare.");
            return;
        }

        Intent intent = new Intent(context, FastPatrolReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, PATROL_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        long triggerAtMillis = System.currentTimeMillis() + PATROL_INTERVAL_MS;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
        }

        // Questo allarme non si ripete da solo. Verrà ripianificato dal DataMonitorService
        // ogni volta che il servizio dimostra di essere vivo.
        EselLog.LogI(TAG, "Pattuglia Veloce schedulata tra 4 minuti.");
    }

    public static void cancel(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, FastPatrolReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, PATROL_REQUEST_CODE, intent, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);

        if (pendingIntent != null && alarmManager != null) {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
            EselLog.LogW(TAG, "Allarme Pattuglia Veloce CANCELLATO.");
        }
    }
}