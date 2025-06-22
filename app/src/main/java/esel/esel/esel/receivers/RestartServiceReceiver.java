package esel.esel.esel.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.core.content.ContextCompat;
import esel.esel.esel.services.DataMonitorService;
import esel.esel.esel.util.EselLog;

public class RestartServiceReceiver extends BroadcastReceiver {
    private static final String TAG = "RestartServiceReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        EselLog.LogW(TAG, "Sveglia di sicurezza (Watchdog) ricevuta. Controllo e avvio DataMonitorService...");
        // Questo comando avvia il servizio solo se non è già in esecuzione, rianimandolo.
        ContextCompat.startForegroundService(context, new Intent(context, DataMonitorService.class));
    }
}