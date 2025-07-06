// ---------- CODICE CON STRINGHE CENTRALIZZATE ----------
package esel.esel.esel;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;

import java.util.Locale;
import java.util.Random;
import java.util.Set;

import esel.esel.esel.services.StabilityService;
import esel.esel.esel.util.EselLog;
import esel.esel.esel.util.SP;

public class ActivationActivity extends AppCompatActivity {

    private static final String TAG = "ActivationActivity";

    // Viste per l'attivazione
    private TextView textViewGeneratedCode;
    private EditText editTextUnlockCode;
    private Button buttonUnlock;

    // Viste per i permessi
    private LinearLayout permissionsLayout;
    private ImageView iconNotificationPermission;
    private Button buttonEnableNotifications;
    private ImageView iconBatteryPermission;
    private Button buttonDisableBattery;
    private ImageView iconAccessibilityPermission;
    private Button buttonEnableAccessibility;
    private Button buttonStartApp;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_activation);

        initializeViews();

        if (SP.getBoolean("is_app_unlocked", false)) {
            showPermissionsChecklist();
        } else {
            setupActivationFlow();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (SP.getBoolean("is_app_unlocked", false)) {
            updatePermissionsChecklist();

            if (areAllPermissionsGranted()) {
                proceedToMainApp();
            }
        }
    }

    private void initializeViews() {
        textViewGeneratedCode = findViewById(R.id.textViewGeneratedCode);
        editTextUnlockCode = findViewById(R.id.editTextUnlockCode);
        buttonUnlock = findViewById(R.id.buttonUnlock);

        permissionsLayout = findViewById(R.id.permissionsLayout);
        iconNotificationPermission = findViewById(R.id.iconNotificationPermission);
        buttonEnableNotifications = findViewById(R.id.buttonEnableNotifications);
        iconBatteryPermission = findViewById(R.id.iconBatteryPermission);
        buttonDisableBattery = findViewById(R.id.buttonDisableBattery);
        buttonStartApp = findViewById(R.id.buttonStartApp);

        iconAccessibilityPermission = findViewById(R.id.iconAccessibilityPermission);
        buttonEnableAccessibility = findViewById(R.id.buttonEnableAccessibility);
    }

    private void setupActivationFlow() {
        permissionsLayout.setVisibility(View.GONE);
        buttonStartApp.setVisibility(View.GONE);

        String generatedCode = String.format(Locale.US, "%04d", new Random().nextInt(10000));
        textViewGeneratedCode.setText(generatedCode);
        EselLog.LogI(TAG, "Codice di attivazione generato: " + generatedCode);

        buttonUnlock.setOnClickListener(v -> {
            String userInput = editTextUnlockCode.getText().toString().trim();
            if (userInput.isEmpty()) {
                Toast.makeText(this, R.string.activation_please_enter_code, Toast.LENGTH_SHORT).show();
                return;
            }

            if (isUnlockCodeCorrect(generatedCode, userInput)) {
                EselLog.LogI(TAG, "Codice di sblocco corretto! App attivata.");
                Toast.makeText(this, R.string.activation_success, Toast.LENGTH_SHORT).show();
                SP.putBoolean("is_app_unlocked", true);
                showPermissionsChecklist();
            } else {
                EselLog.LogW(TAG, "Codice di sblocco errato inserito.");
                Toast.makeText(this, R.string.activation_wrong_code, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showPermissionsChecklist() {
        textViewGeneratedCode.setVisibility(View.GONE);
        findViewById(R.id.textViewActivationInstructions).setVisibility(View.GONE);
        editTextUnlockCode.setVisibility(View.GONE);
        buttonUnlock.setVisibility(View.GONE);

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

        buttonEnableAccessibility.setOnClickListener(v -> {
            Toast.makeText(this, R.string.permissions_toast_find_and_enable, Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });

        buttonStartApp.setOnClickListener(v -> proceedToMainApp());
    }

    private void updatePermissionsChecklist() {
        if (permissionsLayout == null) {
            return;
        }

        boolean notificationOK = isNotificationListenerEnabled();
        updateChecklistItemUI(iconNotificationPermission, buttonEnableNotifications, notificationOK, R.string.permissions_enable_button);

        boolean batteryOK = isBatteryOptimizationIgnored();
        updateChecklistItemUI(iconBatteryPermission, buttonDisableBattery, batteryOK, R.string.permissions_disable_button);

        boolean accessibilityOK = isAccessibilityServiceEnabled();
        updateChecklistItemUI(iconAccessibilityPermission, buttonEnableAccessibility, accessibilityOK, R.string.permissions_activate_button);

        if (areAllPermissionsGranted()) {
            buttonStartApp.setEnabled(true);
            buttonStartApp.setText(R.string.permissions_start_app);
        } else {
            buttonStartApp.setEnabled(false);
            buttonStartApp.setText(R.string.permissions_missing);
        }
    }

    private void updateChecklistItemUI(ImageView icon, Button button, boolean isGranted, @StringRes int buttonTextResId) {
        if (isGranted) {
            icon.setImageResource(R.drawable.ic_status_dot_green);
            button.setEnabled(false);
            button.setText(R.string.permissions_ok_button);
        } else {
            icon.setImageResource(R.drawable.ic_status_dot_red);
            button.setEnabled(true);
            button.setText(buttonTextResId);
        }
    }

    private boolean areAllPermissionsGranted() {
        return isNotificationListenerEnabled() && isBatteryOptimizationIgnored() && isAccessibilityServiceEnabled();
    }

    private boolean isNotificationListenerEnabled() {
        Set<String> enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(this);
        return enabledListeners.contains(getPackageName());
    }

    private boolean isBatteryOptimizationIgnored() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private boolean isAccessibilityServiceEnabled() {
        int accessibilityEnabled = 0;
        final String service = getPackageName() + "/" + StabilityService.class.getCanonicalName();
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                    getApplicationContext().getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException e) {
            EselLog.LogE("ActivationActivity", "Errore nel leggere le impostazioni di accessibilità: " + e.getMessage());
        }
        TextUtils.SimpleStringSplitter colonSplitter = new TextUtils.SimpleStringSplitter(':');

        if (accessibilityEnabled == 1) {
            String settingValue = Settings.Secure.getString(
                    getApplicationContext().getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            if (settingValue != null) {
                colonSplitter.setString(settingValue);
                while (colonSplitter.hasNext()) {
                    String accessibilityService = colonSplitter.next();
                    if (accessibilityService.equalsIgnoreCase(service)) {
                        return true;
                    }
                }
            }
        }
        return false;
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