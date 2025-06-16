package esel.esel.esel;

import android.os.Build;
import android.os.Bundle;
import android.Manifest;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.view.MenuItem;
import android.view.View;
import android.view.Menu;
import android.widget.Button;
import android.widget.TextView;
import android.content.Context;
import android.provider.Settings;
import android.net.Uri;
import android.app.AlertDialog;
import android.os.PowerManager; // <-- AGGIUNGI QUESTO IMPORT


import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import com.google.android.material.navigation.NavigationView;

import esel.esel.esel.preferences.Preferences;
import esel.esel.esel.LogActivity;
import esel.esel.esel.receivers.ReadReceiver;
import esel.esel.esel.util.EselLog;
import esel.esel.esel.util.SP;

import java.io.File;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private static final int PERMISSION_REQUEST_CODE_POST_NOTIFICATIONS = 1;

    private Toolbar toolbar;
    private DrawerLayout drawer;
    private NavigationView navigationView;
    private Button exportDataButton;
    private TextView mainTextView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawer = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        navigationView.setNavigationItemSelectedListener(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, PERMISSION_REQUEST_CODE_POST_NOTIFICATIONS);
            }
        }

        requestNotificationAccess();
        requestIgnoreBatteryOptimizations();


        exportDataButton = findViewById(R.id.button_exportdata);
        mainTextView = findViewById(R.id.textview_main);

        exportDataButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                EselLog.LogI("MainActivity", "Export Data button clicked.");
                performExport();
            }
        });
    }

    private void requestNotificationAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            String packageName = getPackageName();
            if (!Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners").contains(packageName)) {
                new AlertDialog.Builder(this)
                        .setTitle("Accesso Notifiche Necessario")
                        .setMessage("Eversense-Reader ha bisogno dell'accesso alle notifiche per leggere i dati dal tuo sensore. Per favore, abilita l'accesso nella schermata successiva.")
                        .setPositiveButton("Abilita", (dialog, which) -> {
                            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                            startActivity(intent);
                        })
                        .setNegativeButton("Annulla", (dialog, which) -> dialog.dismiss())
                        .show();
            }
        }
    }

    private void requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE); // Riga 120
            if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
                new AlertDialog.Builder(this)
                        .setTitle("Ottimizzazione Batteria")
                        .setMessage("Eversense-Reader deve essere escluso dall'ottimizzazione della batteria per funzionare correttamente in background e non perdere dati. Per favore, abilita l'opzione nella schermata successiva.")
                        .setPositiveButton("Abilita", (dialog, which) -> {
                            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                            intent.setData(Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                        })
                        .setNegativeButton("Annulla", (dialog, which) -> dialog.dismiss())
                        .show();
            }
        }
    }


    private void performExport() {
        ReadReceiver.FullExport(this, new File(getExternalFilesDir(null), "esel_data_export.txt"), SP.getInt("max-sync-hours", 24));
        EselLog.LogI("MainActivity", "Data export initiated to: " + new File(getExternalFilesDir(null), "esel_data_export.txt").getAbsolutePath());
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_settings) {
            Intent intent = new Intent(this, Preferences.class);
            startActivity(intent);
        } else if (id == R.id.nav_home) {
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_errors) {
            Intent intent = new Intent(this, LogActivity.class);
            startActivity(intent);
        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public void onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE_POST_NOTIFICATIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                EselLog.LogI("MainActivity", "POST_NOTIFICATIONS permission granted.");
            } else {
                EselLog.LogW("MainActivity", "POST_NOTIFICATIONS permission denied.");
            }
        }
    }
}