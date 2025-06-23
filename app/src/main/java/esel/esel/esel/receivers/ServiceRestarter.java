// ---------- CODICE COMPLETO PER ServiceRestarter.java ----------
package esel.esel.esel.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.core.content.ContextCompat;
import esel.esel.esel.services.DataMonitorService;
import esel.esel.esel.util.EselLog;

public class ServiceRestarter extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        EselLog.LogW("ServiceRestarter", "Ricevuto broadcast di riavvio. Rianimo il DataMonitorService!");
        ContextCompat.startForegroundService(context, new Intent(context, DataMonitorService.class));
    }
}