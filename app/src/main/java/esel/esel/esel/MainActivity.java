// ---------- CODICE FINALE SEMPLIFICATO PER minSdk 33 ----------
package esel.esel.esel;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
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

import esel.esel.esel.receivers.WatchdogReceiver;
import esel.esel.esel.services.DataMonitorService;
import esel.esel.esel.util.EselLog;
import esel.esel.esel.util.SP;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

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
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, DataMonitorService.WATCHDOG_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        boolean isAlarmUp = (PendingIntent.getBroadcast(this, DataMonitorService.WATCHDOG_REQUEST_CODE, intent, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE) != null);
        if (isAlarmUp) {
            EselLog.LogI(TAG, "L'allarme Watchdog è già programmato a 15 minuti.");
            return;
        }
        alarmManager.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                AlarmManager.INTERVAL_FIFTEEN_MINUTES,
                AlarmManager.INTERVAL_FIFTEEN_MINUTES,
                pendingIntent
        );
        EselLog.LogI(TAG, "Allarme Watchdog programmato ogni 15 minuti.");
    }

    private boolean areAllPermissionsGranted() {
        return isNotificationPermissionGranted() && isNotificationListenerEnabled() && isBatteryOptimizationIgnored();
    }

    private void requestMissingPermissions() {
        if (!isNotificationPermissionGranted()) {
            requestNotificationPermission();
        }
        if (!isNotificationListenerEnabled()) {
            requestNotificationListenerPermission();
        }
        if (!isBatteryOptimizationIgnored()) {
            requestToIgnoreBatteryOptimizations();
        }
    }

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

    // --- METODO SEMPLIFICATO ---
    // Il permesso per le notifiche è obbligatorio da Android 13 (nostra minSdk), quindi non serve più l'if.
    private boolean isNotificationPermissionGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) { EselLog.LogI(TAG, "Permesso Notifiche CONCESSO."); }
                else {
                    EselLog.LogW(TAG, "Permesso Notifiche NEGATO.");
                    Toast.makeText(this, "Attenzione: senza il permesso notifiche, l'app potrebbe non funzionare correttamente.", Toast.LENGTH_LONG).show();
                }
            });

    // --- METODO SEMPLIFICATO ---
    private void requestNotificationPermission() {
        EselLog.LogI(TAG, "Richiesta permesso POST_NOTIFICATIONS...");
        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
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

    // --- METODO SEMPLIFICATO ---
    // Le ottimizzazioni batteria esistono da Android 6 (molto prima della nostra minSdk), quindi non serve più l'if.
    private boolean isBatteryOptimizationIgnored() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    // --- METODO SEMPLIFICATO ---
    private void requestToIgnoreBatteryOptimizations() {
        if (!isBatteryOptimizationIgnored()){
            EselLog.LogI(TAG, "Richiesta per ignorare ottimizzazione batteria...");
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }
}