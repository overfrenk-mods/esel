// ---------- CODICE FINALE CON BLOCCO DI ATTIVAZIONE SULL'INTERRUTTORE ----------
package esel.esel.esel;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast; // <-- NUOVO IMPORT
import androidx.core.content.ContextCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import esel.esel.esel.services.DataMonitorService;
import esel.esel.esel.util.EselLog;
import esel.esel.esel.util.SP;

public class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {

    private Preference statusServiceState;
    private Preference statusLastReading;
    private Preference statusLastSend;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey);

        statusServiceState = findPreference("status_service_state");
        statusLastReading = findPreference("status_last_reading");
        statusLastSend = findPreference("status_last_send");

        SwitchPreferenceCompat enableServiceSwitch = findPreference("enable_service");

        if (enableServiceSwitch != null) {
            // --- MODIFICA: La logica del listener ora è più intelligente ---
            enableServiceSwitch.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean isEnabled = (Boolean) newValue;

                if (isEnabled) {
                    // --- CONTROLLO DI SICUREZZA AGGIUNTIVO ---
                    // L'utente sta provando ad ATTIVARE il servizio.
                    // Prima controlliamo se l'app è stata sbloccata.
                    if (!SP.getBoolean("is_app_unlocked", false)) {
                        EselLog.LogW("SettingsFragment", "Tentativo di avviare il servizio su un'app non attivata.");
                        Toast.makeText(getActivity(), "Devi prima attivare l'app al primo avvio!", Toast.LENGTH_LONG).show();
                        // Ritornando 'false', impediamo allo switch di cambiare stato. Rimane su OFF.
                        return false;
                    }
                    // Se l'app è sbloccata, procediamo normalmente ad avviare il servizio.
                    EselLog.LogI("SettingsFragment", "Interruttore attivato dall'utente. Avvio servizio...");
                    if (getActivity() != null) {
                        ContextCompat.startForegroundService(getActivity(), new Intent(getActivity(), DataMonitorService.class));
                    }
                } else {
                    // Se l'utente DISATTIVA l'interruttore, fermiamo il servizio come prima.
                    EselLog.LogI("SettingsFragment", "Interruttore disattivato dall'utente. Fermo servizio...");
                    if (getActivity() != null) {
                        Intent stopIntent = new Intent(getActivity(), DataMonitorService.class);
                        stopIntent.setAction(DataMonitorService.ACTION_STOP_SERVICE);
                        ContextCompat.startForegroundService(getActivity(), stopIntent);
                    }
                }

                // Diamo un istante al servizio per cambiare il suo stato prima di aggiornare la UI
                if (getContext() != null) {
                    getContext().getMainExecutor().execute(this::updateStatusSummaries);
                }
                return true; // Conferma che accettiamo la modifica dello stato dell'interruttore
            });
        }
    }

    private void updateStatusSummaries() {
        if (getContext() == null) return;

        // La logica per aggiornare la dashboard rimane identica
        boolean isServiceRunning = SP.getBoolean("service_should_be_running", false) && SP.getBoolean("enable_service", true);
        if (isServiceRunning) {
            statusServiceState.setSummary("Attivo");
            statusServiceState.setIcon(R.drawable.ic_status_dot_green);
        } else {
            statusServiceState.setSummary("Fermato");
            statusServiceState.setIcon(R.drawable.ic_status_dot_red);
        }

        long lastReadingTime = SP.getLong("lastSentTime", 0L);
        if (lastReadingTime > 0) {
            int lastReadingValue = SP.getInt("lastSentFinalValue", 0);
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            statusLastReading.setSummary(lastReadingValue + " mg/dL alle " + sdf.format(new Date(lastReadingTime)));
        } else {
            statusLastReading.setSummary("Nessuna lettura ancora ricevuta");
        }

        long lastSendTime = SP.getLong("lastSuccessfulSendTime", 0L);
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
        SP.sharedPreferences.registerOnSharedPreferenceChangeListener(this);
        updateStatusSummaries();
    }

    @Override
    public void onPause() {
        super.onPause();
        SP.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(this::updateStatusSummaries);
        }
    }
}