// ---------- CODICE COMPLETO E DEFINITIVO PER SGV.java ----------
package esel.esel.esel.datareader;

import java.io.Serializable; // MODIFICA: Aggiunto l'import necessario
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import esel.esel.esel.util.EselLog;
import esel.esel.esel.util.SP;

import static java.lang.Math.min;

// MODIFICA: Aggiunta l'implementazione di Serializable
public class SGV implements Serializable {
    public int value;
    public int raw;
    public long timestamp;
    public int record;
    public String direction;

    private static final String CLASS_TAG = "SGV";

    public SGV(int value, long timestamp, int record) {
        if (value < 0) { this.value = 38; }
        else if (value < 40) { this.value = 39; }
        else if (value > 400) { this.value = 400; }
        else { this.value = value; }

        this.raw = this.value;
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

    public void smooth(int last) {
        EselLog.LogV(CLASS_TAG, "Smoothing: Grezzo=" + this.raw + ", Ultimo=" + last);

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
            EselLog.LogI(CLASS_TAG, "Smoothing Applicato: Grezzo=" + this.raw + ", Lisciato=" + this.value);
        } else {
            EselLog.LogI(CLASS_TAG, "Smoothing Saltato: Valore ("+ this.raw +") <= Limite Inferiore (" + lower_limit + ")");
        }
    }
}