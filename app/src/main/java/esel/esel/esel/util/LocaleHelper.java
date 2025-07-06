// File: esel/esel/esel/util/LocaleHelper.java
package esel.esel.esel.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import androidx.preference.PreferenceManager;
import java.util.Locale;

public class LocaleHelper {

    private static final String SELECTED_LANGUAGE_KEY = "app_language";

    /**
     * Questo metodo viene chiamato all'avvio di ogni Activity per assicurarsi
     * che il contesto abbia la lingua corretta impostata.
     */
    public static Context onAttach(Context context) {
        String lang = getPersistedLanguage(context);
        return setLocale(context, lang);
    }

    /**
     * Legge la lingua salvata nelle SharedPreferences.
     * Il valore di default è "default", che indica di usare la lingua di sistema.
     */
    public static String getPersistedLanguage(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return preferences.getString(SELECTED_LANGUAGE_KEY, "default");
    }

    /**
     * Crea un nuovo contesto con la lingua specificata e lo restituisce.
     */
    public static Context setLocale(Context context, String language) {
        // Se la lingua scelta è "default", non facciamo nulla.
        // Android userà automaticamente la lingua del telefono.
        if (language.equals("default")) {
            return context;
        }

        // Creiamo un oggetto Locale dalla stringa della lingua (es. "it" o "en")
        Locale locale = new Locale(language);
        Locale.setDefault(locale);

        // Creiamo una nuova configurazione per l'app con la nuova lingua
        Resources res = context.getResources();
        Configuration config = new Configuration(res.getConfiguration());
        config.setLocale(locale);

        // Restituiamo un nuovo contesto con la configurazione aggiornata
        return context.createConfigurationContext(config);
    }
}