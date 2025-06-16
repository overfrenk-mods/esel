package esel.esel.esel.datareader;

import android.util.Log; // Mantenuto per compatibilità, ma useremo EselLog

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import esel.esel.esel.util.EselLog; // AGGIUNTO per un logging coerente
import esel.esel.esel.util.SP;

// Rimosso: static android.content.ContentValues.TAG; // Questo era un problema, usiamo un TAG di classe
import static java.lang.Math.min; // Mantenuto se min() è usato

/**
 * Creato da adrian il 04/08/17.
 */

public class SGV {
    public int value; // unità: mg/dl (sempre usata internamente)
    public int raw;
    public long timestamp; // Tempo UNIX in ms
    public int record;
    public String direction;

    private static final String CLASS_TAG = "SGV"; // Tag per i log della classe SGV

    public SGV(int value, long timestamp, int record){
        this.value = value;
        this.raw = value;
        this.timestamp = timestamp;
        this.record = record;

        if (this.value < 0) { this.value = 38;}
        else if (this.value < 40) { this.value = 39;}
        else if (this.value > 1000) { this.value = 38;}
        else if (this.value > 400) { this.value = 400;}
    }

    static public int Convert(float mmoll){
        float mgdl = mmoll * 18.0182f;
        return Math.round(mgdl);
    }

    @Override
    public String toString(){
        DateFormat df = SimpleDateFormat.getDateTimeInstance();
        return df.format(new Date(timestamp)) + ": " + value;
    }

    public void setDirection(double slope_by_minute) {
        direction = "NONE";
        if (slope_by_minute <= (-3.5d)) {
            direction = "DoubleDown";
        } else if (slope_by_minute <= (-2d)) {
            direction = "SingleDown";
        } else if (slope_by_minute <= (-1d)) {
            direction = "FortyFiveDown";
        } else if (slope_by_minute <= (1d)) {
            direction = "Flat";
        } else if (slope_by_minute <= (2d)) {
            direction = "FortyFiveUp";
        } else if (slope_by_minute <= (3.5d)) {
            direction = "SingleUp";
        } else if (slope_by_minute <= (40d)) {
            direction = "DoubleUp";
        }
    }

    /**
     * Creato da bernhard il 2018-11-18.
     */

    public void smooth(int last,boolean enable_smooth){
        double value = (double)this.value;
        double lastSmooth = (double)last;

        EselLog.LogV(CLASS_TAG, "Smoothing: Valore grezzo = " + this.value + ", Ultimo processato = " + last + ", Smoothing Abilitato = " + enable_smooth);

        if(!enable_smooth){
            SP.putInt("lastReadingRaw", this.value);
            SP.putFloat("readingSmooth",(float)this.value);
            EselLog.LogV(CLASS_TAG, "Smoothing disabilitato. Valore grezzo memorizzato.");
            return;
        }

        try{
            lastSmooth = SP.getFloat("readingSmooth",(float)lastSmooth);
        }catch (Exception e){
            EselLog.LogW(CLASS_TAG, "Nessun valore smooth precedente trovato. Utilizzo valore predefinito. Errore: " + e.getMessage());
            // Prima volta: nessun valore disponibile, soluzione di fallback è il valore predefinito
        }
        double factor = SP.getDouble("smooth_factor",0.3,0.0,1.0);
        double correction = SP.getDouble("correction_factor",0.5,0.0,1.0);
        double descent_factor = SP.getDouble("descent_factor",0.0,0.0,1.0);
        float lastRaw = (float)SP.getInt("lastReadingRaw", this.value); // Cast a float per consistenza con lastSmooth

        SP.putInt("lastReadingRaw", this.value);

        if(last < 39) {// Nessun valore utile, es. a causa di una pausa nell'uso del trasmettitore
            lastRaw = this.value;
            lastSmooth = this.value;
            EselLog.LogW(CLASS_TAG, "Ultimo valore < 39. Resetting lastRaw e lastSmooth.");
        }

        // Smoothing esponenziale, vedi https://en.wikipedia.org/wiki/Exponential_smoothing
        // y'[t]=y'[t-1] + (a*(y-y'[t-1])) = a*y+(1-a)*y'[t-1]
        // factor è a, value è y, lastSmooth y'[t-1], smooth y'
        // factor tra 0 e 1, default 0.3
        // factor = 0: sempre l'ultimo smooth (costante)
        // factor = 1: nessun smoothing
        double smooth_val=lastSmooth+(factor*(value-lastSmooth));

        // Correzione: media del delta tra valore grezzo e valore smooth, aggiunta a smooth con fattore di correzione
        // correction tra 0 e 1, default 0.5
        // correction = 0: nessuna correzione, smoothing completo
        // correction > 0: meno smoothing
        smooth_val=smooth_val+(correction*((lastRaw-lastSmooth)+(value-smooth_val))/2.0d);

        smooth_val = smooth_val - descent_factor*(smooth_val-min(value,smooth_val));

        SP.putFloat("readingSmooth",(float)smooth_val);

        if(this.value > SP.getInt("lower_limit",65)){
            this.value = (int)Math.round(smooth_val);
            EselLog.LogI(CLASS_TAG, "Smoothing Applicato: Grezzo=" + (int)value + ", Lisciato=" + this.value + ", Fattore=" + factor + ", Correzione=" + correction + ", Discesa=" + descent_factor);
        } else {
            EselLog.LogI(CLASS_TAG, "Smoothing Saltato (sotto limite inferiore): Grezzo=" + (int)value + ", LimiteInferiore=" + SP.getInt("lower_limit",65));
        }

        // Rimosso Log.d(TAG, "readDataFromContentProvider called, result = " + this.value);
        // perché il messaggio era fuorviante e usava il TAG sbagliato.

    }
}