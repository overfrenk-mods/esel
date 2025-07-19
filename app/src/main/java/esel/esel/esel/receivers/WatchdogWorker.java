// ---------- CODICE PER IL NUOVO FILE WatchdogWorker.java ----------
package esel.esel.esel.receivers;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import esel.esel.esel.services.DataMonitorService;
import esel.esel.esel.util.EselLog;
import esel.esel.esel.util.SP;

public class WatchdogWorker extends Worker {

    private static final String TAG = "WatchdogWorker";

    public WatchdogWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        EselLog.LogW(TAG, "Pattuglia di riserva (WorkManager) attivata. Eseguo controllo...");

        // Se l'utente ha disabilitato il servizio, non facciamo nulla.
        if (!SP.getBoolean("enable_service", true)) {
            EselLog.LogI(TAG, "Servizio disabilitato, la pattuglia di riserva non interviene.");
            return Result.success();
        }

        // Controlliamo se il nostro guardiano principale (AlarmManager) è ancora attivo.
        Intent intent = new Intent(getApplicationContext(), WatchdogReceiver.class);
        boolean isAlarmUp = (PendingIntent.getBroadcast(getApplicationContext(), DataMonitorService.WATCHDOG_REQUEST_CODE, intent, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE) != null);

        if (isAlarmUp) {
            EselLog.LogI(TAG, "Controllo OK: Il guardiano principale (AlarmManager) è già attivo.");
        } else {
            // Se l'allarme principale è stato cancellato dal sistema, lo riattiviamo!
            EselLog.LogE(TAG, "EMERGENZA: Il guardiano principale (AlarmManager) è sparito! Lo riattivo ora.");
            WatchdogReceiver.scheduleNextWatchdog(getApplicationContext());
        }

        return Result.success();
    }
}