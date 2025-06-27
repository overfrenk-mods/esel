// ---------- CODICE FINALE SEMPLIFICATO PER minSdk 33 ----------
package esel.esel.esel;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Build;

import androidx.core.content.ContextCompat;

import esel.esel.esel.services.DataMonitorService;
import esel.esel.esel.util.EselLog;
import esel.esel.esel.util.SP;

public class Esel extends Application {

    private static Esel sInstance;
    private static Resources sResources;

    public static final String ALERT_CHANNEL_ID = "EselAlertChannel";

    @Override
    public void onCreate() {
        super.onCreate();

        EselLog.init(this);

        sInstance = this;
        sResources = getResources();
        EselLog.LogI("EselApp", "Application onCreate");

        createNotificationChannels();

        boolean use_patched_es = SP.getBoolean("use_patched_es", false);
        if (use_patched_es) {
            startDataMonitorService();
        }
    }

    // --- METODO SEMPLIFICATO ---
    // Il controllo if(Build.VERSION...) è stato rimosso perché la minSdk è 33 (Android 13)
    private void createNotificationChannels() {
        // Canale per gli avvisi critici del Watchdog (alta priorità)
        NotificationChannel alertChannel = new NotificationChannel(
                ALERT_CHANNEL_ID,
                "Avvisi Critici Eversense-Reader",
                NotificationManager.IMPORTANCE_HIGH
        );
        alertChannel.setDescription("Notifiche per problemi critici di funzionamento dell'app");

        // Registriamo il canale con il sistema
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(alertChannel);
            EselLog.LogI("EselApp", "Canale di notifica per gli avvisi creato.");
        }
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