package esel.esel.esel.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build; // Necessario per Build.VERSION.SDK_INT
import androidx.core.content.ContextCompat; // Necessario per startForegroundService

import esel.esel.esel.services.DataMonitorService; // Necessario per avviare il servizio
import esel.esel.esel.util.EselLog; // Necessario per i log

/**
 * Receiver che ascolta l'aggiornamento (sostituzione) dell'app per riavviare il servizio.
 */
public class AppUpdateReceiver extends BroadcastReceiver {

    private static final String TAG = "AppUpdateReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        // L'azione Intent.ACTION_MY_PACKAGE_REPLACED viene inviata dal sistema quando l'app viene aggiornata.
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            EselLog.LogI(TAG, "App updated (ACTION_MY_PACKAGE_REPLACED). Attempting to restart DataMonitorService.");

            // Avvia il DataMonitorService come Foreground Service
            // Utilizziamo ContextCompat.startForegroundService per la compatibilità con le API recenti.
            ContextCompat.startForegroundService(context, new Intent(context, DataMonitorService.class));

            // Nota: Se l'app non è in primo piano e il servizio deve partire, è meglio usare startForegroundService.
            // Se l'app è in primo piano, puoi usare startService. ContextCompat gestisce la differenza.
        }
    }
}