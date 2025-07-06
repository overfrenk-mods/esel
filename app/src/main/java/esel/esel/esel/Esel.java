// ---------- CODICE CON APPLICAZIONE DELLA LINGUA ALL'AVVIO ----------
package esel.esel.esel;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Build;

import androidx.core.content.ContextCompat;

import esel.esel.esel.services.DataMonitorService;
import esel.esel.esel.util.EselLog;
import esel.esel.esel.util.LocaleHelper; // <-- NUOVO IMPORT
import esel.esel.esel.util.SP;

public class Esel extends Application {

    private static Esel sInstance;
    private static Resources sResources;

    public static final String ALERT_CHANNEL_ID = "EselAlertChannel";

    // --- NUOVO METODO DA AGGIUNGERE ---
    // Questo metodo viene chiamato da Android prima di onCreate()
    // ed è il posto perfetto per impostare la lingua.
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(LocaleHelper.onAttach(base));
    }


    @Override
    public void onCreate() {
        super.onCreate();

        CrashCatcher.install(this);
        EselLog.init(this);

        sInstance = this;
        sResources = getResources();

        logSystemInfo();

        EselLog.LogI("EselApp", "Application onCreate");

        createNotificationChannels();

        boolean use_patched_es = SP.getBoolean("use_patched_es", false);
        if (use_patched_es) {
            startDataMonitorService();
        }
    }

    private void logSystemInfo() {
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String versionName = pInfo.versionName;
            int versionCode = pInfo.versionCode;

            EselLog.LogI("SystemInfo", "----------------------------------------------------");
            EselLog.LogI("SystemInfo", "AVVIO APPLICAZIONE");
            EselLog.LogI("SystemInfo", "App Version: " + versionName + " (" + versionCode + ")");
            EselLog.LogI("SystemInfo", "Device: " + Build.MANUFACTURER + " " + Build.MODEL);
            EselLog.LogI("SystemInfo", "Android Version: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
            EselLog.LogI("SystemInfo", "----------------------------------------------------");

        } catch (PackageManager.NameNotFoundException e) {
            EselLog.LogE("SystemInfo", "Impossibile ottenere la versione dell'app: " + e.getMessage());
        }
    }

    private void createNotificationChannels() {
        NotificationChannel alertChannel = new NotificationChannel(
                ALERT_CHANNEL_ID,
                "Avvisi Critici Eversense-Reader",
                NotificationManager.IMPORTANCE_HIGH
        );
        alertChannel.setDescription("Notifiche per problemi critici di funzionamento dell'app");

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