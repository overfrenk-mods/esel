// ---------- CODICE FINALE E OTTIMIZZATO ----------
package esel.esel.esel;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import esel.esel.esel.receivers.WatchdogReceiver;
import esel.esel.esel.services.DataMonitorService;
import esel.esel.esel.util.EselLog;
import esel.esel.esel.util.SP;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.app_name);
            getSupportActionBar().setSubtitle(R.string.app_subtitle);
        }

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings_container, new SettingsFragment())
                    .commit();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkPermissionsAndStartService();
    }

    private void checkPermissionsAndStartService() {
        if (areAllPermissionsGranted() && SP.getBoolean("enable_service", true)) {
            EselLog.LogI(TAG, "Permessi OK e servizio abilitato. Avvio il DataMonitorService...");
            ContextCompat.startForegroundService(this, new Intent(this, DataMonitorService.class));
            scheduleWatchdogAlarm();
        } else if (!areAllPermissionsGranted()) {
            EselLog.LogW(TAG, "Permessi mancanti. Avvio la procedura di richiesta.");
            requestMissingPermissions();
        } else {
            EselLog.LogI(TAG, "Il servizio è disabilitato dalle impostazioni. Nessuna azione.");
        }
    }

    private void scheduleWatchdogAlarm() {
        Intent intent = new Intent(this, WatchdogReceiver.class);
        boolean isAlarmUp = (PendingIntent.getBroadcast(this, DataMonitorService.WATCHDOG_REQUEST_CODE, intent, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE) != null);

        if (isAlarmUp) {
            EselLog.LogI(TAG, "La catena di allarmi Watchdog è già attiva.");
            return;
        }

        handler.postDelayed(() -> {
            EselLog.LogW(TAG, "Nessun allarme Watchdog trovato. AVVIO LA CATENA DI ALLARMI ESATTI con ritardo.");
            WatchdogReceiver.scheduleNextWatchdog(this);
        }, 2000); // Ritardo di 2 secondi
    }

    // --- MODIFICA: Rimossi i permessi non necessari per ESEL ---
    private String[] getRequiredPermissions() {
        List<String> permissions = new ArrayList<>();
        // Da Android 13 in su, è necessario per mostrare la notifica del servizio
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        return permissions.toArray(new String[0]);
    }


    private boolean areAllPermissionsGranted() {
        for (String permission : getRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return isNotificationListenerEnabled() && isBatteryOptimizationIgnored();
    }

    private void requestMissingPermissions() {
        List<String> missingPermissions = new ArrayList<>();
        for (String permission : getRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }

        if (!missingPermissions.isEmpty()) {
            EselLog.LogI(TAG, "Richiesta permessi runtime: " + missingPermissions);
            multiplePermissionsLauncher.launch(missingPermissions.toArray(new String[0]));
        }

        if (!isNotificationListenerEnabled()) {
            requestNotificationListenerPermission();
        }
        if (!isBatteryOptimizationIgnored()) {
            requestToIgnoreBatteryOptimizations();
        }
    }

    private final ActivityResultLauncher<String[]> multiplePermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), (Map<String, Boolean> grants) -> {
                for (Map.Entry<String, Boolean> entry : grants.entrySet()) {
                    if (entry.getValue()) {
                        EselLog.LogI(TAG, "Permesso " + entry.getKey() + " CONCESSO.");
                    } else {
                        EselLog.LogW(TAG, "Permesso " + entry.getKey() + " NEGATO.");
                        Toast.makeText(this, "Attenzione: senza il permesso " + entry.getKey() + ", l'app potrebbe non funzionare.", Toast.LENGTH_LONG).show();
                    }
                }
            });


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    // --- MODIFICA: Aggiunto il listener per la nuova icona del grafico ---
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();

        if (itemId == R.id.action_log) {
            startActivity(new Intent(this, LogActivity.class));
            return true;
        }

        if (itemId == R.id.action_graph) {
            startActivity(new Intent(this, GraphActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private boolean isNotificationListenerEnabled() {
        String enabledListeners = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (enabledListeners != null) { return enabledListeners.contains(getPackageName()); }
        return false;
    }

    private void requestNotificationListenerPermission() {
        EselLog.LogI(TAG, "Richiesta accesso alle notifiche (Listener)...");
        Toast.makeText(this, "Per favore, abilita Eversense-Reader nella schermata di Accesso alle Notifiche.", Toast.LENGTH_LONG).show();
        startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
    }

    private boolean isBatteryOptimizationIgnored() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void requestToIgnoreBatteryOptimizations() {
        if (!isBatteryOptimizationIgnored()){
            EselLog.LogI(TAG, "Richiesta per ignorare ottimizzazione batteria...");
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }
}