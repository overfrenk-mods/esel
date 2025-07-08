// File: esel/esel/esel/util/WakeLockHelper.java
package esel.esel.esel.util;

import android.content.Context;
import android.os.PowerManager;

public class WakeLockHelper {

    private static final String TAG = "WakeLockHelper";
    private static PowerManager.WakeLock wakeLock = null;

    /**
     * Acquisisce un WakeLock parziale per un breve periodo di tempo.
     * Impedisce al processore di andare in deep sleep mentre l'app esegue un compito critico.
     *
     * @param context Il contesto dell'applicazione.
     * @param timeoutMs Il tempo in millisecondi per cui mantenere il lock (es. 60000 per un minuto).
     */
    public static synchronized void acquire(Context context, long timeoutMs) {
        // Se un WakeLock è già attivo, non facciamo nulla per evitare conflitti.
        if (wakeLock != null && wakeLock.isHeld()) {
            return;
        }

        try {
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Esel::TaskWakeLock");
                wakeLock.setReferenceCounted(false); // Importante per la nostra logica di rilascio
                wakeLock.acquire(timeoutMs);
                EselLog.LogW(TAG, "WakeLock acquisito per " + (timeoutMs / 1000) + " secondi.");
            }
        } catch (Exception e) {
            EselLog.LogE(TAG, "Errore durante l'acquisizione del WakeLock: " + e.getMessage());
        }
    }

    /**
     * Rilascia il WakeLock se è attualmente attivo.
     * Questo metodo va chiamato SEMPRE in un blocco 'finally' per garantire il rilascio.
     */
    public static synchronized void release() {
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
                EselLog.LogW(TAG, "WakeLock rilasciato.");
            } catch (Exception e) {
                EselLog.LogE(TAG, "Errore durante il rilascio del WakeLock: " + e.getMessage());
            } finally {
                wakeLock = null;
            }
        }
    }
}