package esel.esel.esel.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.content.ContextCompat;

import esel.esel.esel.services.DataMonitorService;
import esel.esel.esel.util.EselLog;
import esel.esel.esel.util.SP; // <-- IMPORT AGGIUNTO per usare le SharedPreferences

public class AutostartReceiver extends BroadcastReceiver {

    private static final String TAG = "AutostartReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {

            // --- MODIFICA: Controlliamo la "chiave di accensione" prima di partire ---
            if (SP.getBoolean("is_app_unlocked", false)) {
                EselLog.LogI(TAG, "Boot completato e app sbloccata. Avvio il DataMonitorService.");
                ContextCompat.startForegroundService(context, new Intent(context, DataMonitorService.class));
            } else {
                EselLog.LogW(TAG, "Boot completato, ma l'app è bloccata. Il servizio non verrà avviato.");
            }
        }
    }
}