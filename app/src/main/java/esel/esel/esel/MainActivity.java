package esel.esel.esel;

import android.Manifest;
import android.app.AlarmManager;
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
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import esel.esel.esel.receivers.WatchdogWorker;
import esel.esel.esel.services.DataMonitorService;
import esel.esel.esel.util.EselLog;
import esel.esel.esel.util.SP;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String WATCHDOG_WORKER_TAG = "watchdog_worker_tag";
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
        boolean permissionsOk = areRuntimePermissionsGranted() &&
                isNotificationListenerEnabled() &&
                isBatteryOptimizationIgnored() &&
                isExactAlarmPermissionGranted();

        if (permissionsOk && SP.getBoolean("enable_service", true)) {
            EselLog.LogI(TAG, "Permessi OK. Servizio abilitato. Avvio Sistema...");

            Intent serviceIntent = new Intent(this, DataMonitorService.class);
            ContextCompat.startForegroundService(this, serviceIntent);

            scheduleRedundantWatchdog();

        } else if (!permissionsOk) {
            EselLog.LogW(TAG, "Permessi mancanti. Avvio procedura guidata.");
            requestNextMissingPermission();
        } else {
            EselLog.LogI(TAG, "Servizio disabilitato dall'utente.");
        }
    }

    private void scheduleRedundantWatchdog() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest watchdogWorkRequest =
                new PeriodicWorkRequest.Builder(WatchdogWorker.class, 15, TimeUnit.MINUTES)
                        .setConstraints(constraints)
                        .addTag(WATCHDOG_WORKER_TAG)
                        .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                WATCHDOG_WORKER_TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                watchdogWorkRequest);

        EselLog.LogI(TAG, "Watchdog di Riserva (WorkManager) attivo.");
    }

    private String[] getRequiredRuntimePermissions() {
        List<String> permissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        return permissions.toArray(new String[0]);
    }

    private boolean areRuntimePermissionsGranted() {
        for (String permission : getRequiredRuntimePermissions()) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void requestNextMissingPermission() {
        // 1. Permessi Runtime (Notifiche)
        List<String> missingRuntime = new ArrayList<>();
        for (String permission : getRequiredRuntimePermissions()) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                missingRuntime.add(permission);
            }
        }
        if (!missingRuntime.isEmpty()) {
            // Mostra Toast tradotto
            Toast.makeText(this, getString(R.string.toast_permissions_missing), Toast.LENGTH_LONG).show();
            multiplePermissionsLauncher.launch(missingRuntime.toArray(new String[0]));
            return;
        }

        // 2. Accesso Notifiche (Listener)
        if (!isNotificationListenerEnabled()) {
            // Toast Multilingua
            Toast.makeText(this, getString(R.string.toast_enable_notifications), Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            return;
        }

        // 3. Batteria (Doze Mode)
        if (!isBatteryOptimizationIgnored()) {
            // Toast Multilingua
            Toast.makeText(this, getString(R.string.toast_disable_battery_opt), Toast.LENGTH_LONG).show();
            requestToIgnoreBatteryOptimizations();
            return;
        }

        // 4. Allarmi Esatti (Android 14+)
        if (!isExactAlarmPermissionGranted()) {
            // Toast Multilingua
            Toast.makeText(this, getString(R.string.toast_enable_alarms), Toast.LENGTH_LONG).show();
            requestExactAlarmPermission();
            return;
        }
    }

    private final ActivityResultLauncher<String[]> multiplePermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), (Map<String, Boolean> grants) -> {
                checkPermissionsAndStartService();
            });

    private boolean isNotificationListenerEnabled() {
        String pkgName = getPackageName();
        final String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (flat != null) {
            return flat.contains(pkgName);
        }
        return false;
    }

    private boolean isBatteryOptimizationIgnored() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void requestToIgnoreBatteryOptimizations() {
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            EselLog.LogE(TAG, "Errore apertura settings batteria: " + e.getMessage());
        }
    }

    private boolean isExactAlarmPermissionGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null) {
                return alarmManager.canScheduleExactAlarms();
            }
        }
        return true;
    }

    private void requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                startActivity(intent);
            } catch (Exception e) {
                EselLog.LogE(TAG, "Errore apertura settings allarmi: " + e.getMessage());
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

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
}