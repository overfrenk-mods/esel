// ---------- CODICE COMPLETO PER WatchdogReceiver.java ----------
package esel.esel.esel.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.core.content.ContextCompat;
import esel.esel.esel.services.DataMonitorService;
import esel.esel.esel.util.EselLog;
import esel.esel.esel.util.SP;

public class WatchdogReceiver extends BroadcastReceiver {

    private static final String TAG = "WatchdogReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        EselLog.LogI(TAG, "Watchdog ricevuto. Controllo lo stato del servizio...");

        boolean shouldBeRunning = SP.getBoolean("service_should_be_running", false);

        if (shouldBeRunning) {
            EselLog.LogW(TAG, "Il servizio dovrebbe essere attivo. Avvio preventivo per sicurezza...");
            ContextCompat.startForegroundService(context, new Intent(context, DataMonitorService.class));
        } else {
            EselLog.LogI(TAG, "Il servizio è stato fermato volontariamente. Il watchdog non fa nulla.");
        }
    }
}