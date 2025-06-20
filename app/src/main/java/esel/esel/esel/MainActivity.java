// ---------------- INIZIO CODICE FINALE E CORRETTO PER MainActivity.java ----------------
package esel.esel.esel;

import android.Manifest;
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

import esel.esel.esel.services.DataMonitorService; // <-- IMPORT AGGIUNTO
import esel.esel.esel.util.EselLog;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.app_name);
            getSupportActionBar().setSubtitle("by Francesco v1");
        }

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings_container, new SettingsFragment())
                    .commit();
        }
    }

    // --- MODIFICA CHIAVE: Usiamo onResume() per controllare i permessi e avviare il servizio ---
    @Override
    protected void onResume() {
        super.onResume();
        // Ogni volta che l'utente torna all'app, controlliamo lo stato.
        checkPermissionsAndStartService();
    }

    private void checkPermissionsAndStartService() {
        // Controlliamo se tutti i permessi critici sono stati concessi
        if (areAllPermissionsGranted()) {
            // Se sì, avviamo il servizio
            EselLog.LogI(TAG, "Tutti i permessi sono concessi. Avvio il DataMonitorService...");
            ContextCompat.startForegroundService(this, new Intent(this, DataMonitorService.class));
        } else {
            // Se no, chiediamo quelli mancanti
            EselLog.LogW(TAG, "Permessi mancanti. Avvio la procedura di richiesta.");
            requestMissingPermissions();
        }
    }

    // --- NUOVO METODO: Controlla tutti i permessi in un colpo solo ---
    private boolean areAllPermissionsGranted() {
        return isNotificationPermissionGranted() && isNotificationListenerEnabled() && isBatteryOptimizationIgnored();
    }

    // --- NUOVO METODO: Chiede solo i permessi che mancano ---
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

    // Il resto della classe (i metodi per i singoli permessi) rimane quasi invariato...
    private static final String TAG = "MainActivity"; // Aggiunto TAG per i log

    private boolean isNotificationPermissionGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) { EselLog.LogI(TAG, "Permesso Notifiche CONCESSO."); }
                else {
                    EselLog.LogW(TAG, "Permesso Notifiche NEGATO.");
                    Toast.makeText(this, "Attenzione: senza il permesso notifiche, l'app potrebbe non funzionare correttamente.", Toast.LENGTH_LONG).show();
                }
            });
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            EselLog.LogI(TAG, "Richiesta permesso POST_NOTIFICATIONS...");
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }
    private boolean isNotificationListenerEnabled() {
        String enabledListeners = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (enabledListeners != null) { return enabledListeners.contains(getPackageName()); }
        return false;
    }
    private void requestNotificationListenerPermission() {
        EselLog.LogI(TAG, "Richiesta accesso alle notifiche (Listener)...");
        Toast.makeText(this, "Per favore, abilita Esel nella schermata di Accesso alle Notifiche.", Toast.LENGTH_LONG).show();
        startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
    }
    private boolean isBatteryOptimizationIgnored() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            return pm.isIgnoringBatteryOptimizations(getPackageName());
        }
        return true;
    }
    private void requestToIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isBatteryOptimizationIgnored()){
            EselLog.LogI(TAG, "Richiesta per ignorare ottimizzazione batteria...");
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }
}