// ---------- CODICE FINALE CON AVVIO ROBUSTO DELL'ALLARME ----------
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

        // **FIX PER IL CRASH AL RIAVVIO**
        // Avviamo la catena di allarmi con un piccolo ritardo per dare al sistema il tempo di stabilizzarsi.
        handler.postDelayed(() -> {
            EselLog.LogW(TAG, "Nessun allarme Watchdog trovato. AVVIO LA CATENA DI ALLARMI ESATTI con ritardo.");
            WatchdogReceiver.scheduleNextWatchdog(this);
        }, 2000); // Ritardo di 2 secondi
    }

    private String[] getRequiredPermissions() {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.POST_NOTIFICATIONS);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
        }
        // Il permesso per la posizione è richiesto per la scansione bluetooth
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
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

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_log) {
            startActivity(new Intent(this, LogActivity.class));
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