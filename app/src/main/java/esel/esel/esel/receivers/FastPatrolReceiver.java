// ---------- CODICE FAST PATROL 3.1.0 "DEADMAN SWITCH" ----------
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

    // FIX CRITICO: Deve essere maggiore dell'intervallo del DataMonitorService (5 min).
    // Mettiamo 7 minuti. Se il servizio salta un giro, la pattuglia se ne accorge e interviene.
    private static final long PATROL_INTERVAL_MS = 7 * 60 * 1000L;

    private static final int PATROL_REQUEST_CODE = 902;

    @Override
    public void onReceive(Context context, Intent intent) {
        EselLog.LogW(TAG, "🚨 PATTUGLIA VELOCE SCATTATA! Il servizio principale è in ritardo/morto.");

        if (!SP.getBoolean("enable_service", true)) {
            EselLog.LogI(TAG, "Servizio disabilitato dall'utente. Pattuglia a riposo.");
            return;
        }

        // 1. Riavvia il Servizio
        try {
            Intent serviceIntent = new Intent(context, DataMonitorService.class);

            // Fix per Android 12+: Usiamo startForegroundService se possibile
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
            EselLog.LogI(TAG, ">>> Rianimazione DataMonitorService inviata.");

        } catch (Exception e) {
            Log.e(TAG, "ERRORE CRITICO: Rianimazione fallita!", e);
        }

        // 2. AUTO-RESCHEDULE (Fondamentale!)
        // Se siamo qui, vuol dire che il servizio era morto.
        // Dobbiamo ri-armare la pattuglia subito, altrimenti se il servizio crasha di nuovo
        // tra 1 secondo, non ci sarà più nessuno a controllarlo!
        schedule(context);
    }

    /**
     * Questo metodo agisce come un "Reset del Timer".
     * Ogni volta che il DataMonitorService gira, chiama questo metodo.
     * Questo SPOSTA l'allarme in avanti nel futuro, impedendogli di scattare.
     */
    public static void schedule(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        // Verifica permessi Android 12+ (Safety Check)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                // Senza permessi esatti, la pattuglia sarà imprecisa ma funzionerà lo stesso
                // Non blocchiamo l'esecuzione con un return, lasciamo che provi.
                EselLog.LogW(TAG, "Manca permesso Exact Alarm. Pattuglia potrebbe ritardare.");
            }
        }

        Intent intent = new Intent(context, FastPatrolReceiver.class);
        // FLAG_UPDATE_CURRENT è vitale: sovrascrive il vecchio timer con quello nuovo
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, PATROL_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Imposta la "bomba" tra 7 minuti da ADESSO
        long triggerAtMillis = System.currentTimeMillis() + PATROL_INTERVAL_MS;

        // Usiamo setExactAndAllowWhileIdle per garantire che il watchdog funzioni
        // anche se il telefono è in Doze Mode profondo.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
        }

        // Loggare questo riempie troppo il logcat perché succede ogni 5 min. Usiamo LogV (Verbose) o niente.
        // EselLog.LogV(TAG, "Pattuglia posticipata di 7 min.");
    }

    public static void cancel(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, FastPatrolReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, PATROL_REQUEST_CODE, intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);

        if (pendingIntent != null && alarmManager != null) {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
            EselLog.LogI(TAG, "Pattuglia Veloce disattivata (Stop Servizio).");
        }
    }
}