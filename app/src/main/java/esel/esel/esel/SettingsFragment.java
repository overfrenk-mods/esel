// ---------- CODICE CON FIX AL SYNC MANUALE E ALL'ERRORE getSystemService ----------
package esel.esel.esel;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import esel.esel.esel.datareader.EsNotificationListener;
import esel.esel.esel.datareader.SGV;
import esel.esel.esel.services.DataMonitorService;
import esel.esel.esel.util.EselLog;
import esel.esel.esel.util.SP;

public class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {

    private Preference statusServiceState;
    private Preference statusLastReading;
    private Preference statusLastSend;
    private Preference manualSyncButton;
    private Preference batteryOptimizationButton;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey);

        statusServiceState = findPreference("status_service_state");
        statusLastReading = findPreference("status_last_reading");
        statusLastSend = findPreference("status_last_send");
        manualSyncButton = findPreference("manual_sync_button");
        SwitchPreferenceCompat enableServiceSwitch = findPreference("enable_service");
        batteryOptimizationButton = findPreference("battery_optimization_button");

        if (batteryOptimizationButton != null) {
            batteryOptimizationButton.setOnPreferenceClickListener(preference -> {
                if (getActivity() != null) {
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getActivity().getPackageName()));
                    startActivity(intent);
                }
                return true;
            });
        }

        if (manualSyncButton != null) {
            manualSyncButton.setOnPreferenceClickListener(preference -> {
                EselLog.LogW("SettingsFragment", "Click su Sync Manuale rilevato.");
                handleManualSync();
                return true;
            });
        }

        if (enableServiceSwitch != null) {
            enableServiceSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean isEnabled = (Boolean) newValue;

                if (isEnabled) {
                    if (!SP.getBoolean("is_app_unlocked", false)) {
                        EselLog.LogW("SettingsFragment", "Tentativo di avviare il servizio su un'app non attivata.");
                        Toast.makeText(getActivity(), R.string.settings_toast_activation_required, Toast.LENGTH_LONG).show();
                        return false;
                    }
                    EselLog.LogI("SettingsFragment", "Interruttore attivato dall'utente. Avvio servizio...");
                    if (getActivity() != null) {
                        ContextCompat.startForegroundService(getActivity(), new Intent(getActivity(), DataMonitorService.class));
                    }
                } else {
                    EselLog.LogI("SettingsFragment", "Interruttore disattivato dall'utente. Fermo servizio...");
                    if (getActivity() != null) {
                        Intent stopIntent = new Intent(getActivity(), DataMonitorService.class);
                        stopIntent.setAction(DataMonitorService.ACTION_STOP_SERVICE);
                        ContextCompat.startForegroundService(getActivity(), stopIntent);
                    }
                }

                if (getContext() != null) {
                    getContext().getMainExecutor().execute(this::updateStatusSummaries);
                }
                return true;
            });
        }
    }

    private void handleManualSync() {
        if (getActivity() == null) return;

        String lastSeenText = SP.getString(EsNotificationListener.KEY_LAST_SEEN_NOTIFICATION_TEXT, "");

        if (lastSeenText.isEmpty()) {
            Toast.makeText(getActivity(), R.string.settings_toast_no_recent_reading, Toast.LENGTH_SHORT).show();
            EselLog.LogE("SettingsFragment", "Sync Manuale fallito: non ci sono dati in cache.");
            return;
        }

        // --- FIX CRITICO PER IL SYNC MANUALE ---
        // Generiamo l'SGV usando il timestamp attuale (System.currentTimeMillis()) invece del vecchio e inaffidabile 'when'.
        // Questo assicura che il dato venga processato con un orario fresco e non "avveleni" lo stato del DataMonitorService.
        SGV sgvToSend = EsNotificationListener.generateSGVFromText(lastSeenText, System.currentTimeMillis());

        if (sgvToSend == null) {
            Toast.makeText(getActivity(), R.string.settings_toast_processing_error, Toast.LENGTH_SHORT).show();
            EselLog.LogE("SettingsFragment", "Sync Manuale fallito: impossibile generare SGV dal testo: " + lastSeenText);
            return;
        }

        Intent manualSyncIntent = new Intent(getActivity(), DataMonitorService.class);
        manualSyncIntent.setAction(DataMonitorService.ACTION_MANUAL_SYNC);
        manualSyncIntent.putExtra(EsNotificationListener.EXTRA_SGV_DATA, sgvToSend);
        ContextCompat.startForegroundService(getActivity(), manualSyncIntent);

        Toast.makeText(getActivity(), R.string.settings_toast_manual_sync_sent, Toast.LENGTH_SHORT).show();
    }

    private void updateStatusSummaries() {
        if (getContext() == null) return;

        boolean isServiceEnabled = SP.getBoolean("enable_service", true);
        if (isServiceEnabled) {
            statusServiceState.setSummary(R.string.settings_service_status_running);
            statusServiceState.setIcon(R.drawable.ic_status_dot_green);
        } else {
            statusServiceState.setSummary(R.string.settings_service_status_stopped);
            statusServiceState.setIcon(R.drawable.ic_status_dot_red);
        }

        long lastSgvTimestamp = SP.getLong(DataMonitorService.KEY_LAST_SGV_TIMESTAMP, 0L);
        if (lastSgvTimestamp > 0) {
            int lastReadingValue = SP.getInt(DataMonitorService.KEY_LAST_SGV_FINAL_VALUE, 0);
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            String formattedTime = sdf.format(new Date(lastSgvTimestamp));
            statusLastReading.setSummary(getString(R.string.settings_last_reading_summary_placeholder, lastReadingValue, formattedTime));
        } else {
            statusLastReading.setSummary(R.string.settings_last_reading_none);
        }

        long lastSendTime = SP.getLong(DataMonitorService.KEY_LAST_SUCCESSFUL_SEND_MS, 0L);
        if (lastSendTime > 0) {
            long secondsAgo = (System.currentTimeMillis() - lastSendTime) / 1000;
            if (secondsAgo < 60) {
                statusLastSend.setSummary(getString(R.string.settings_last_send_seconds_ago, (int) secondsAgo));
            } else {
                statusLastSend.setSummary(getString(R.string.settings_last_send_minutes_ago, (int) (secondsAgo / 60)));
            }
        } else {
            statusLastSend.setSummary(R.string.settings_last_send_none);
        }

        updateBatteryOptimizationStatus();
    }

    private void updateBatteryOptimizationStatus() {
        if (getContext() == null || batteryOptimizationButton == null) return;

        PowerManager pm = (PowerManager) getContext().getSystemService(Context.POWER_SERVICE);
        if (pm == null) return;

        boolean isIgnoringOptimizations = pm.isIgnoringBatteryOptimizations(getContext().getPackageName());

        if (isIgnoringOptimizations) {
            batteryOptimizationButton.setSummary(R.string.settings_battery_settings_summary_ok);
        } else {
            batteryOptimizationButton.setSummary(R.string.settings_battery_settings_summary_warn);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getPreferenceManager() != null && getPreferenceManager().getSharedPreferences() != null) {
            getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        }
        updateStatusSummaries();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (getPreferenceManager() != null && getPreferenceManager().getSharedPreferences() != null) {
            getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key != null && key.equals("app_language")) {
            if (getActivity() != null) {
                Intent i = getActivity().getBaseContext().getPackageManager()
                        .getLaunchIntentForPackage(getActivity().getBaseContext().getPackageName());
                if (i != null) {
                    i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(i);
                }
            }
            return;
        }

        if (getActivity() != null) {
            getActivity().runOnUiThread(this::updateStatusSummaries);
        }
    }
}