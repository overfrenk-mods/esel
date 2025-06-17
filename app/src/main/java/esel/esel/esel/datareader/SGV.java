// ---------------- INIZIO CODICE COMPLETO E PULITO PER SGV.java ----------------
package esel.esel.esel.datareader;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import esel.esel.esel.util.EselLog;
import esel.esel.esel.util.SP;

import static java.lang.Math.min;

/**
 * SGV (Sensor Glucose Value)
 * Rappresenta un singolo valore di glicemia, con il suo timestamp e direzione.
 * Contiene anche la logica per l'algoritmo di smoothing.
 */
public class SGV {
    public int value; // unità: mg/dl (sempre usata internamente)
    public int raw;   // Valore grezzo originale, prima dello smoothing
    public long timestamp; // Tempo UNIX in ms
    public int record;
    public String direction;

    private static final String CLASS_TAG = "SGV";

    public SGV(int value, long timestamp, int record) {
        // Normalizzazione dei valori per evitare estremi irrealistici
        if (value < 0) { this.value = 38; }
        else if (value < 40) { this.value = 39; }
        else if (value > 400) { this.value = 400; }
        else { this.value = value; }

        this.raw = this.value; // Il valore grezzo è quello iniziale
        this.timestamp = timestamp;
        this.record = record;
    }

    static public int Convert(float mmoll) {
        float mgdl = mmoll * 18.0182f;
        return Math.round(mgdl);
    }

    @Override
    public String toString() {
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        return df.format(new Date(timestamp)) + ": " + value;
    }

    public void setDirection(double slope_by_minute) {
        direction = "None";
        if (slope_by_minute <= -3.5) { direction = "DoubleDown"; }
        else if (slope_by_minute <= -2.0) { direction = "SingleDown"; }
        else if (slope_by_minute <= -1.0) { direction = "FortyFiveDown"; }
        else if (slope_by_minute < 1.0) { direction = "Flat"; }
        else if (slope_by_minute < 2.0) { direction = "FortyFiveUp"; }
        else if (slope_by_minute < 3.5) { direction = "SingleUp"; }
        else { direction = "DoubleUp"; }
    }

    /**
     * Applica un algoritmo di smoothing a 3 stadi al valore 'value' di questo oggetto SGV.
     * @param last L'ultimo valore di glicemia processato (grezzo).
     * @param enable_smooth true se lo smoothing è attivato dalle impostazioni.
     */
    public void smooth(int last, boolean enable_smooth) {
        EselLog.LogV(CLASS_TAG, "Smoothing: Grezzo=" + this.value + ", Ultimo=" + last + ", Abilitato=" + enable_smooth);

        if (!enable_smooth) {
            SP.putInt("lastReadingRaw", this.value);
            SP.putFloat("readingSmooth", (float)this.value);
            return;
        }

        // Carica i valori necessari dalle SharedPreferences
        float lastSmooth = SP.getFloat("readingSmooth", (float) last);
        float factor = SP.getFloat("smooth_factor", 0.3f);
        float correction = SP.getFloat("correction_factor", 0.5f);
        float descent_factor = SP.getFloat("descent_factor", 0.0f);
        int lastRaw = SP.getInt("lastReadingRaw", this.value);

        // Salva il valore grezzo attuale per la prossima iterazione
        SP.putInt("lastReadingRaw", this.value);

        // Se l'ultimo valore non è valido, resetta i valori di smoothing per evitare calcoli errati
        if (last < 39) {
            lastRaw = this.value;
            lastSmooth = this.value;
        }

        // --- STADIO 1: Smoothing Esponenziale (EMA) ---
        // Formula: y'[t] = y'[t-1] + a * (y[t] - y'[t-1])
        double smooth_val = lastSmooth + (factor * (this.value - lastSmooth));

        // --- STADIO 2: Correzione del Ritardo (Lag) ---
        // Aggiunge una frazione della media dei delta (grezzo-smussato) per ridurre il ritardo del filtro.
        smooth_val = smooth_val + (correction * ((lastRaw - lastSmooth) + (this.value - smooth_val)) / 2.0d);

        // --- STADIO 3: Fattore di Sicurezza in Discesa ---
        // Se il valore grezzo scende, tira giù il valore smussato più velocemente per non nascondere una ipo.
        smooth_val = smooth_val - (descent_factor * (smooth_val - min(this.value, smooth_val)));

        // Salva il nuovo valore smussato per la prossima iterazione
        SP.putFloat("readingSmooth", (float) smooth_val);

        int lower_limit = SP.getInt("lower_limit", 65);
        if (this.value > lower_limit) {
            this.value = (int) Math.round(smooth_val);
            EselLog.LogI(CLASS_TAG, "Smoothing Applicato: Grezzo=" + this.raw + ", Lisciato=" + this.value);
        } else {
            EselLog.LogI(CLASS_TAG, "Smoothing Saltato: Valore ("+ this.raw +") <= Limite Inferiore (" + lower_limit + ")");
        }
    }
}
// ---------------- FINE CODICE COMPLETO E PULITO PER SGV.java ----------------