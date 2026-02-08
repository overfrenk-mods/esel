// ---------- CODICE SGV.java "PASS-THROUGH" (SENZA TAPPI) ----------
package esel.esel.esel.datareader;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import esel.esel.esel.util.EselLog;
import esel.esel.esel.util.SP;

import static java.lang.Math.min;

public class SGV implements Serializable {
    public int value;
    public int raw;
    public long timestamp;
    public int record; // Usato per il trend o ID
    public String direction;

    private static final String CLASS_TAG = "SGV";

    public SGV(int value, long timestamp, int record) {
        // MODIFICA FONDAMENTALE: Rimosso il "Tappo" 40-400.
        // Se il Listener invia 401 (HI) o 39 (LO), questo oggetto DEVE accettarlo.
        // La validazione di sicurezza (30-500) è già fatta nel DataMonitorService.

        this.value = value;
        this.raw = value; // Il raw iniziale è uguale al valore ricevuto
        this.timestamp = timestamp;
        this.record = record;
        this.direction = "None"; // Default
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

    // Metodo di supporto per calcolare la direzione (usato anche internamente)
    public void setDirection(double slope_by_minute) {
        direction = "None";
        if (slope_by_minute <= -3.5) {
            direction = "DoubleDown";
        } else if (slope_by_minute <= -2.0) {
            direction = "SingleDown";
        } else if (slope_by_minute <= -1.0) {
            direction = "FortyFiveDown";
        } else if (slope_by_minute < 1.0) {
            direction = "Flat";
        } else if (slope_by_minute < 2.0) {
            direction = "FortyFiveUp";
        } else if (slope_by_minute < 3.5) {
            direction = "SingleUp";
        } else {
            direction = "DoubleUp";
        }
    }

    /**
     * @deprecated Questo metodo è obsoleto. Lo smoothing ora viene gestito
     * in modo centralizzato e sicuro da DataMonitorService (xDrip Style).
     * Manteniamo il metodo solo per evitare errori di compilazione in parti legacy.
     */
    @Deprecated
    public void smooth(int last) {
        // LOGICA LEGACY (Disattivata o usata solo se richiamata esplicitamente da vecchi moduli)
        // Il nuovo DataMonitorService NON usa questo metodo.

        EselLog.LogV(CLASS_TAG, "Legacy Smoothing chiamato (non dovrebbe accadere nel nuovo Loop).");

        float lastSmooth = SP.getFloat("readingSmooth", (float) last);
        float factor = SP.getFloat("smooth_factor", 0.3f);
        float correction = SP.getFloat("correction_factor", 0.5f);
        float descent_factor = SP.getFloat("descent_factor", 0.0f);
        int lastRaw = SP.getInt("lastReadingRaw", this.raw);

        SP.putInt("lastReadingRaw", this.raw);

        if (last < 39) {
            lastRaw = this.raw;
            lastSmooth = this.raw;
        }

        double smooth_val = lastSmooth + (factor * (this.raw - lastSmooth));
        smooth_val = smooth_val + (correction * ((lastRaw - lastSmooth) + (this.raw - smooth_val)) / 2.0d);
        smooth_val = smooth_val - (descent_factor * (smooth_val - min(this.raw, smooth_val)));

        SP.putFloat("readingSmooth", (float) smooth_val);

        int lower_limit = SP.getInt("lower_limit", 65);
        if (this.raw > lower_limit) {
            this.value = (int) Math.round(smooth_val);
        }
    }
}