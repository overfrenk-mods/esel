package esel.esel.esel.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.content.ContextCompat;

import esel.esel.esel.services.DataMonitorService;
import esel.esel.esel.util.EselLog;
import esel.esel.esel.util.SP; // <-- IMPORT AGGIUNTO per usare le SharedPreferences

/**
 * Receiver che ascolta l'aggiornamento (sostituzione) dell'app per riavviare il servizio.
 */
public class AppUpdateReceiver extends BroadcastReceiver {

    private static final String TAG = "AppUpdateReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();

        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {

            // --- MODIFICA: Controlliamo la "chiave di accensione" prima di partire ---
            if (SP.getBoolean("is_app_unlocked", false)) {
                EselLog.LogI(TAG, "App aggiornata e già sbloccata. Riavvio il DataMonitorService.");
                ContextCompat.startForegroundService(context, new Intent(context, DataMonitorService.class));
            } else {
                EselLog.LogW(TAG, "App aggiornata, ma è ancora bloccata. Il servizio non verrà avviato.");
            }
        }
    }
}