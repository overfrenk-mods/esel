// ---------- CODICE COMPLETO E MODIFICATO ----------
package esel.esel.esel.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.content.ContextCompat;

import esel.esel.esel.services.DataMonitorService;
import esel.esel.esel.util.EselLog;
import esel.esel.esel.util.SP;

public class AutostartReceiver extends BroadcastReceiver {

    private static final String TAG = "AutostartReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        // Assicuriamoci che l'azione sia quella corretta (BOOT_COMPLETED)
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }

        EselLog.LogW(TAG, "Riavvio del dispositivo rilevato (BOOT_COMPLETED).");

        // --- CONTROLLO MIGLIORATO ---
        // Verifichiamo entrambe le condizioni:
        // 1. Che l'app sia stata attivata almeno una volta.
        // 2. Che l'utente avesse lasciato il servizio abilitato dalle impostazioni.
        boolean isAppUnlocked = SP.getBoolean("is_app_unlocked", false);
        boolean wasServiceEnabledByUser = SP.getBoolean("enable_service", true); // Leggiamo lo stato dell'interruttore

        if (isAppUnlocked && wasServiceEnabledByUser) {
            EselLog.LogI(TAG, "App sbloccata e servizio abilitato dall'utente. Avvio il DataMonitorService.");

            Intent serviceIntent = new Intent(context, DataMonitorService.class);
            ContextCompat.startForegroundService(context, serviceIntent);

        } else {
            EselLog.LogW(TAG, "Il servizio non verrà avviato. App sbloccata: " + isAppUnlocked + ", Servizio abilitato: " + wasServiceEnabledByUser);
        }
    }
}