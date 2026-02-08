// ---------- CODICE APPLICATION 3.1.0 "CENTRALIZED CHANNELS" ----------
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
import android.util.Log;

import androidx.core.content.ContextCompat;

import esel.esel.esel.services.DataMonitorService;
import esel.esel.esel.util.EselLog;
import esel.esel.esel.util.LocaleHelper;
import esel.esel.esel.util.SP;

public class Esel extends Application {

    private static Esel sInstance;
    private static Resources sResources;

    // Definiamo qui gli ID dei canali per averli centralizzati,
    // anche se sono duplicati nelle classi specifiche (non fa male).
    public static final String ALERT_CHANNEL_ID = "EselAlertChannel";
    public static final String SERVICE_CHANNEL_ID = "EselMonitorChannel"; // Deve coincidere con DataMonitorService
    public static final String WATCHDOG_RESTART_CHANNEL_ID = "EselRestartAlertChannel"; // Coincide con Watchdog

    @Override
    protected void attachBaseContext(Context base) {
        // Vitale per il supporto multilingua (IT/EN) persistente
        super.attachBaseContext(LocaleHelper.onAttach(base));
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // 1. Inizializzazione Log e Crash Catcher (Prima di tutto)
        CrashCatcher.install(this);
        EselLog.initialize(this);

        sInstance = this;
        sResources = getResources();

        logSystemInfo();
        EselLog.LogI("EselApp", "Application onCreate: Sistema inizializzato.");

        // 2. CREAZIONE CANALI (Cruciale per Samsung/Android 14+)
        // Creiamo TUTTI i canali subito. Se il Watchdog o il Servizio partono
        // e il canale non esiste, l'app crasha.
        createAllNotificationChannels();

        // 3. Avvio condizionale del servizio
        // Usiamo un try-catch perché su Android 12+ l'avvio da background ha restrizioni severe.
        boolean use_patched_es = SP.getBoolean("use_patched_es", false);
        boolean enable_service = SP.getBoolean("enable_service", true);

        if (use_patched_es && enable_service) {
            try {
                startDataMonitorService();
            } catch (Exception e) {
                EselLog.LogE("EselApp", "Avvio automatico servizio fallito (Restrizione Background?): " + e.getMessage());
            }
        }
    }

    private void logSystemInfo() {
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String versionName = pInfo.versionName;
            int versionCode = pInfo.versionCode;

            EselLog.LogI("SystemInfo", "=== ESEL BOOT SEQUENCE ===");
            EselLog.LogI("SystemInfo", "App Version: " + versionName + " (" + versionCode + ")");
            EselLog.LogI("SystemInfo", "Device: " + Build.MANUFACTURER + " " + Build.MODEL);
            EselLog.LogI("SystemInfo", "Android API: " + Build.VERSION.SDK_INT);
            EselLog.LogI("SystemInfo", "==========================");

        } catch (PackageManager.NameNotFoundException e) {
            EselLog.LogE("SystemInfo", "Errore info pacchetto: " + e.getMessage());
        }
    }

    private void createAllNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager == null) return;

            // --- Canale 1: Avvisi Critici (High) ---
            NotificationChannel alertChannel = new NotificationChannel(
                    ALERT_CHANNEL_ID,
                    "Avvisi Critici Esel",
                    NotificationManager.IMPORTANCE_HIGH
            );
            alertChannel.setDescription("Notifiche importanti per errori o problemi.");
            manager.createNotificationChannel(alertChannel);

            // --- Canale 2: Servizio Persistente (Low - Silenzioso) ---
            // È fondamentale creare questo canale PRIMA che parta il servizio!
            NotificationChannel serviceChannel = new NotificationChannel(
                    SERVICE_CHANNEL_ID,
                    "Esel Monitor Service", // Nome visibile all'utente
                    NotificationManager.IMPORTANCE_LOW
            );
            serviceChannel.setSound(null, null);
            serviceChannel.enableVibration(false);
            serviceChannel.setDescription("Notifica persistente per mantenere l'app attiva.");
            manager.createNotificationChannel(serviceChannel);

            // --- Canale 3: Watchdog (High) ---
            NotificationChannel watchdogChannel = new NotificationChannel(
                    WATCHDOG_RESTART_CHANNEL_ID,
                    "Avvisi Riavvio Esel",
                    NotificationManager.IMPORTANCE_HIGH
            );
            watchdogChannel.setDescription("Avvisi quando l'app viene riavviata automaticamente.");
            manager.createNotificationChannel(watchdogChannel);

            EselLog.LogI("EselApp", "Tutti i canali di notifica (Service, Alert, Watchdog) sono stati registrati.");
        }
    }

    public static Esel getsInstance() {
        return sInstance;
    }

    public static Resources getsResources() {
        return sResources;
    }

    public synchronized void startDataMonitorService() {
        EselLog.LogI("EselApp", "Richiesta avvio DataMonitorService...");
        try {
            Intent intent = new Intent(this, DataMonitorService.class);
            ContextCompat.startForegroundService(this, intent);
        } catch (Exception e) {
            EselLog.LogE("EselApp", "Impossibile avviare DataMonitorService: " + e.getMessage());
        }
    }

    public synchronized void stopDataMonitorService() {
        EselLog.LogI("EselApp", "Richiesta stop DataMonitorService.");
        try {
            stopService(new Intent(this, DataMonitorService.class));
        } catch (Exception e) {
            EselLog.LogE("EselApp", "Errore stop servizio: " + e.getMessage());
        }
    }

    // Metodi legacy mantenuti per compatibilità con il resto del codice
    public synchronized void startReadReceiver() { startDataMonitorService(); }
    public synchronized void stopReadReceiver() { stopDataMonitorService(); }
    public synchronized void startKeepAliveService() { startDataMonitorService(); }
    public synchronized void stopKeepAliveService() { stopDataMonitorService(); }
}