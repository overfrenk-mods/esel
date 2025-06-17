// ---------------- INIZIO CODICE FINALE E CORRETTO (DI NUOVO) PER MainActivity.java ----------------
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
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

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

        checkAndRequestPermissions();
    }

    private void checkAndRequestPermissions() {
        if (!isNotificationPermissionGranted()) { requestNotificationPermission(); }
        // --- CORREZIONE: Rimosso il "Listener" doppio dal nome del metodo ---
        if (!isNotificationListenerEnabled()) { requestNotificationListenerPermission(); }
        if (!isBatteryOptimizationIgnored()) { requestToIgnoreBatteryOptimizations(); }
    }

    // --- 1. GESTIONE PERMESSO NOTIFICHE (POST_NOTIFICATIONS) ---
    private boolean isNotificationPermissionGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) { EselLog.LogI("Permissions", "Permesso Notifiche CONCESSO."); }
                else {
                    EselLog.LogW("Permissions", "Permesso Notifiche NEGATO.");
                    Toast.makeText(this, "Attenzione: senza il permesso notifiche, l'app potrebbe non funzionare correttamente.", Toast.LENGTH_LONG).show();
                }
            });
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            EselLog.LogI("Permissions", "Richiesta permesso POST_NOTIFICATIONS...");
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    // --- 2. GESTIONE ACCESSO ALLE NOTIFICHE (NOTIFICATION LISTENER) ---
    private boolean isNotificationListenerEnabled() {
        String enabledListeners = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (enabledListeners != null) { return enabledListeners.contains(getPackageName()); }
        return false;
    }
    private void requestNotificationListenerPermission() {
        if (!isNotificationListenerEnabled()) {
            EselLog.LogI("Permissions", "Richiesta accesso alle notifiche (Listener)...");
            Toast.makeText(this, "Per favore, abilita Esel nella schermata di Accesso alle Notifiche.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        }
    }

    // --- 3. GESTIONE OTTIMIZZAZIONE BATTERIA ---
    private boolean isBatteryOptimizationIgnored() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            return pm.isIgnoringBatteryOptimizations(getPackageName());
        }
        return true;
    }
    private void requestToIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isBatteryOptimizationIgnored()){
            EselLog.LogI("Permissions", "Richiesta per ignorare ottimizzazione batteria...");
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }
}