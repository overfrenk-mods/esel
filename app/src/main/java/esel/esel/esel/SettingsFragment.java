// ---------- CODICE COMPLETO E NON ABBREVIATO ----------
package esel.esel.esel;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
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

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey);

        statusServiceState = findPreference("status_service_state");
        statusLastReading = findPreference("status_last_reading");
        statusLastSend = findPreference("status_last_send");
        manualSyncButton = findPreference("manual_sync_button");
        SwitchPreferenceCompat enableServiceSwitch = findPreference("enable_service");

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
                        Toast.makeText(getActivity(), "Devi prima attivare l'app al primo avvio!", Toast.LENGTH_LONG).show();
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
        long lastSeenWhen = SP.getLong(EsNotificationListener.KEY_LAST_SEEN_NOTIFICATION_WHEN, 0L);

        if (lastSeenText.isEmpty() || lastSeenWhen == 0L) {
            Toast.makeText(getActivity(), "Nessuna lettura recente in memoria da inviare.", Toast.LENGTH_SHORT).show();
            EselLog.LogE("SettingsFragment", "Sync Manuale fallito: non ci sono dati in cache.");
            return;
        }

        SGV sgvToSend = EsNotificationListener.generateSGVFromText(lastSeenText, lastSeenWhen);
        if (sgvToSend == null) {
            Toast.makeText(getActivity(), "Errore: impossibile processare l'ultima lettura.", Toast.LENGTH_SHORT).show();
            EselLog.LogE("SettingsFragment", "Sync Manuale fallito: impossibile generare SGV dal testo: " + lastSeenText);
            return;
        }

        Intent manualSyncIntent = new Intent(getActivity(), DataMonitorService.class);
        manualSyncIntent.setAction(DataMonitorService.ACTION_MANUAL_SYNC);
        manualSyncIntent.putExtra(EsNotificationListener.EXTRA_SGV_DATA, sgvToSend);
        ContextCompat.startForegroundService(getActivity(), manualSyncIntent);

        Toast.makeText(getActivity(), "Comando di Sync Manuale inviato!", Toast.LENGTH_SHORT).show();
    }

    private void updateStatusSummaries() {
        if (getContext() == null) return;

        boolean isServiceEnabled = SP.getBoolean("enable_service", true);
        if (isServiceEnabled) {
            statusServiceState.setSummary("Attivo");
            statusServiceState.setIcon(R.drawable.ic_status_dot_green);
        } else {
            statusServiceState.setSummary("Fermato dall'utente");
            statusServiceState.setIcon(R.drawable.ic_status_dot_red);
        }

        long lastSgvTimestamp = SP.getLong(DataMonitorService.KEY_LAST_SGV_TIMESTAMP, 0L);
        if (lastSgvTimestamp > 0) {
            int lastReadingValue = SP.getInt(DataMonitorService.KEY_LAST_SGV_FINAL_VALUE, 0);
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            statusLastReading.setSummary(lastReadingValue + " mg/dL alle " + sdf.format(new Date(lastSgvTimestamp)));
        } else {
            statusLastReading.setSummary("Nessuna lettura ancora ricevuta");
        }

        long lastSendTime = SP.getLong(DataMonitorService.KEY_LAST_SUCCESSFUL_SEND_MS, 0L);
        if (lastSendTime > 0) {
            long secondsAgo = (System.currentTimeMillis() - lastSendTime) / 1000;
            if (secondsAgo < 60) {
                statusLastSend.setSummary(secondsAgo + " secondi fa");
            } else {
                statusLastSend.setSummary((secondsAgo / 60) + " minuti fa");
            }
        } else {
            statusLastSend.setSummary("Nessun invio ancora effettuato");
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
        if (getActivity() != null) {
            getActivity().runOnUiThread(this::updateStatusSummaries);
        }
    }
}