// ---------- CODICE FINALE E COMPLETO PER SettingsFragment.java ----------
package esel.esel.esel;

import android.content.Intent;
import android.os.Bundle;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import esel.esel.esel.services.DataMonitorService;
import esel.esel.esel.util.EselLog;
import esel.esel.esel.util.SP;

public class SettingsFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey);

        // Troviamo il nostro interruttore principale usando la sua "key"
        SwitchPreferenceCompat enableServiceSwitch = findPreference("enable_service");

        if (enableServiceSwitch != null) {
            // Impostiamo un "listener" che si attiva ogni volta che l'utente tocca l'interruttore
            enableServiceSwitch.setOnPreferenceChangeListener((preference, newValue) -> {

                boolean isEnabled = (Boolean) newValue;

                if (isEnabled) {
                    // Se l'utente ATTIVA l'interruttore, avviamo il servizio
                    EselLog.LogI("SettingsFragment", "Interruttore attivato dall'utente. Avvio servizio...");
                    // Usiamo getActivity() per ottenere il contesto e avviare il servizio
                    if (getActivity() != null) {
                        ContextCompat.startForegroundService(getActivity(), new Intent(getActivity(), DataMonitorService.class));
                    }
                } else {
                    // Se l'utente DISATTIVA l'interruttore, fermiamo il servizio
                    EselLog.LogI("SettingsFragment", "Interruttore disattivato dall'utente. Fermo servizio...");
                    // Invia l'intent di STOP al servizio stesso per una chiusura pulita e controllata
                    if (getActivity() != null) {
                        Intent stopIntent = new Intent(getActivity(), DataMonitorService.class);
                        stopIntent.setAction(DataMonitorService.ACTION_STOP_SERVICE);
                        ContextCompat.startForegroundService(getActivity(), stopIntent);
                    }
                }

                return true; // Conferma che accettiamo la modifica dello stato dell'interruttore
            });
        }
    }
}