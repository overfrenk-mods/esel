// ---------------- INIZIO CODICE COMPLETO E MODIFICATO PER Esel.java ----------------
package esel.esel.esel;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import androidx.core.content.ContextCompat;

import esel.esel.esel.services.DataMonitorService;
import esel.esel.esel.util.EselLog;
import esel.esel.esel.util.SP;

/**
 * Created by adrian on 04/08/17.
 */

public class Esel extends Application {

    private static Esel sInstance;
    private static Resources sResources;


    @Override
    public void onCreate() {
        super.onCreate();

        // ---> QUESTA È LA RIGA CHE ABBIAMO AGGIUNTO <---
        // Inizializza il nostro sistema di logging come primissima cosa.
        EselLog.init(this);

        sInstance = this;
        sResources = getResources();
        EselLog.LogI("EselApp", "Application onCreate");

        boolean use_patched_es = SP.getBoolean("use_patched_es", false); // Default è FALSE
        if (use_patched_es) {
            startDataMonitorService();
        }
        // Nota: Se use_patched_es è FALSE, il servizio non parte da qui.
        // Verrà avviato dall'AutostartReceiver (al boot) o da un'azione UI.
    }

    public static Esel getsInstance() {
        return sInstance;
    }

    public static Resources getsResources() {
        return sResources;
    }

    public synchronized void startDataMonitorService() {
        EselLog.LogI("EselApp", "Attempting to start DataMonitorService");
        ContextCompat.startForegroundService(this, new Intent(this, DataMonitorService.class));
    }

    public synchronized void stopDataMonitorService() {
        EselLog.LogI("EselApp", "Attempting to stop DataMonitorService");
        stopService(new Intent(this, DataMonitorService.class));
    }

    // I seguenti metodi reindirizzano le chiamate al DataMonitorService
    public synchronized void startReadReceiver() {
        startDataMonitorService();
    }

    public synchronized void stopReadReceiver() {
        stopDataMonitorService();
    }

    public synchronized void startKeepAliveService() {
        startDataMonitorService();
    }

    public synchronized void stopKeepAliveService() {
        stopDataMonitorService();
    }
}
// ---------------- FINE CODICE COMPLETO E MODIFICATO PER Esel.java ----------------