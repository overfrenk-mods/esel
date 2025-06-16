package esel.esel.esel.preferences;

import android.os.Bundle;
import androidx.preference.PreferenceFragmentCompat; // Importa PreferenceFragmentCompat

import esel.esel.esel.R;

/**
 * Created by adrian on 04/08/17.
 */

public class PrefsFragment extends PreferenceFragmentCompat { // Ora estende PreferenceFragmentCompat

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) { // Metodo cambiato per PreferenceFragmentCompat
        // Load the preferences from an XML resource
        setPreferencesFromResource(R.xml.preferences, rootKey); // Usa setPreferencesFromResource
    }
}