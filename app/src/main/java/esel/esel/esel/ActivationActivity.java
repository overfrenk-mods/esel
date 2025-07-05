// ---------- CODICE CON LOGICA DI AVVIO ROBUSTA ----------
package esel.esel.esel;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

import esel.esel.esel.util.EselLog;
import esel.esel.esel.util.SP;

public class ActivationActivity extends AppCompatActivity {

    private static final String TAG = "ActivationActivity";

    // Viste per l'attivazione
    private TextView textViewGeneratedCode;
    private EditText editTextUnlockCode;
    private Button buttonUnlock;
    private View activationGroup; // Un gruppo per nascondere tutta la parte di attivazione

    // Viste per i permessi
    private LinearLayout permissionsLayout;
    private ImageView iconNotificationPermission;
    private Button buttonEnableNotifications;
    private ImageView iconBatteryPermission;
    private Button buttonDisableBattery;
    private Button buttonStartApp;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 1. Imposta SEMPRE la UI come prima cosa. Questo previene crash se la logica sotto fallisce.
        setContentView(R.layout.activity_activation);

        // 2. Inizializza SEMPRE tutte le viste.
        initializeViews();

        // 3. ORA controlla lo stato dell'app.
        if (SP.getBoolean("is_app_unlocked", false)) {
            // L'app è già stata sbloccata, mostra direttamente la checklist dei permessi.
            showPermissionsChecklist();
        } else {
            // L'app non è sbloccata, avvia il flusso di generazione del codice.
            setupActivationFlow();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Controlla lo stato ogni volta che l'activity torna in primo piano.

        if (SP.getBoolean("is_app_unlocked", false)) {
            // Se l'app è sbloccata, aggiorniamo la checklist dei permessi
            updatePermissionsChecklist();

            // Se TUTTI i permessi sono OK, allora possiamo procedere alla MainActivity.
            if (areAllPermissionsGranted()) {
                proceedToMainApp();
            }
        }
    }

    private void initializeViews() {
        // Viste attivazione
        textViewGeneratedCode = findViewById(R.id.textViewGeneratedCode);
        editTextUnlockCode = findViewById(R.id.editTextUnlockCode);
        buttonUnlock = findViewById(R.id.buttonUnlock);
        // Ho raggruppato le viste di attivazione per gestirle più facilmente,
        // ma se non hai un 'activationGroup' nel layout, questo non è un problema.
        // La logica funzionerà comunque disabilitando i singoli elementi.

        // Viste permessi
        permissionsLayout = findViewById(R.id.permissionsLayout);
        iconNotificationPermission = findViewById(R.id.iconNotificationPermission);
        buttonEnableNotifications = findViewById(R.id.buttonEnableNotifications);
        iconBatteryPermission = findViewById(R.id.iconBatteryPermission);
        buttonDisableBattery = findViewById(R.id.buttonDisableBattery);
        buttonStartApp = findViewById(R.id.buttonStartApp);
    }

    private void setupActivationFlow() {
        // Assicurati che la parte dei permessi sia nascosta
        permissionsLayout.setVisibility(View.GONE);
        buttonStartApp.setVisibility(View.GONE);

        String generatedCode = String.format(Locale.US, "%04d", new Random().nextInt(10000));
        textViewGeneratedCode.setText(generatedCode);
        EselLog.LogI(TAG, "Codice di attivazione generato: " + generatedCode);

        buttonUnlock.setOnClickListener(v -> {
            String userInput = editTextUnlockCode.getText().toString().trim();
            if (userInput.isEmpty()) {
                Toast.makeText(this, "Per favore, inserisci un codice.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (isUnlockCodeCorrect(generatedCode, userInput)) {
                EselLog.LogI(TAG, "Codice di sblocco corretto! App attivata.");
                Toast.makeText(this, "App attivata con successo!", Toast.LENGTH_SHORT).show();
                SP.putBoolean("is_app_unlocked", true);
                showPermissionsChecklist();
            } else {
                EselLog.LogW(TAG, "Codice di sblocco errato inserito.");
                Toast.makeText(this, "Codice di sblocco errato!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showPermissionsChecklist() {
        // Nascondi o disabilita la parte di attivazione
        textViewGeneratedCode.setVisibility(View.GONE);
        findViewById(R.id.textViewActivationInstructions).setVisibility(View.GONE);
        editTextUnlockCode.setVisibility(View.GONE);
        buttonUnlock.setVisibility(View.GONE);

        // Mostra la checklist dei permessi
        permissionsLayout.setVisibility(View.VISIBLE);
        buttonStartApp.setVisibility(View.VISIBLE);

        buttonEnableNotifications.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            startActivity(intent);
        });

        buttonDisableBattery.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        });

        buttonStartApp.setOnClickListener(v -> proceedToMainApp());
    }

    private void updatePermissionsChecklist() {
        // Aggiorna lo stato della checklist e abilita/disabilita i pulsanti
        boolean notificationOK = isNotificationListenerEnabled();
        updateChecklistItemUI(iconNotificationPermission, buttonEnableNotifications, notificationOK, "Abilita");

        boolean batteryOK = isBatteryOptimizationIgnored();
        updateChecklistItemUI(iconBatteryPermission, buttonDisableBattery, batteryOK, "Disabilita");

        // Controlla se il pulsante finale "Inizia" può essere abilitato
        if (notificationOK && batteryOK) {
            buttonStartApp.setEnabled(true);
            buttonStartApp.setText("Inizia a usare l'app");
        } else {
            buttonStartApp.setEnabled(false);
            buttonStartApp.setText("Permessi mancanti...");
        }
    }

    private void updateChecklistItemUI(ImageView icon, Button button, boolean isGranted, String buttonText) {
        if (isGranted) {
            icon.setImageResource(R.drawable.ic_status_dot_green);
            button.setEnabled(false);
            button.setText("OK");
        } else {
            icon.setImageResource(R.drawable.ic_status_dot_red);
            button.setEnabled(true);
            button.setText(buttonText);
        }
    }

    private boolean areAllPermissionsGranted() {
        return isNotificationListenerEnabled() && isBatteryOptimizationIgnored();
    }

    private boolean isNotificationListenerEnabled() {
        Set<String> enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(this);
        return enabledListeners.contains(getPackageName());
    }

    private boolean isBatteryOptimizationIgnored() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private boolean isUnlockCodeCorrect(String generatedCode, String userInput) {
        try {
            int numeroProposto = Integer.parseInt(generatedCode);
            int codiceInserito = Integer.parseInt(userInput);
            int risultatoSottrazione = 9999 - numeroProposto;
            int primiDue = Integer.parseInt(generatedCode.substring(0, 2));
            int risultatoIntermedio = primiDue * risultatoSottrazione;
            int codiceCalcolato = risultatoIntermedio % 1000;
            EselLog.LogI(TAG, "Verifica codice: Generato=" + generatedCode + ", Inserito=" + codiceInserito + ", Calcolato=" + codiceCalcolato);
            return codiceInserito == codiceCalcolato;
        } catch (NumberFormatException e) {
            EselLog.LogW(TAG, "Input non valido per il codice di sblocco.");
            return false;
        }
    }

    private void proceedToMainApp() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}