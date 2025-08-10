// ---------- CODICE CON LOGR POTENZIATO COME ERRORE ----------
package esel.esel.esel.util;

import android.content.Context;
import android.util.Log;

import esel.esel.esel.AppLogger;

/**
 * Wrapper statico per un accesso pulito al logger da tutta l'app.
 * Fornisce un punto di accesso unico e semplificato.
 */
public final class EselLog {

    private static AppLogger logger;

    /**
     * Metodo di inizializzazione. DEVE essere chiamato una sola volta
     * all'avvio dell'app (nella classe Application).
     * @param context Il contesto dell'applicazione.
     */
    public static void initialize(Context context) {
        if (logger == null) {
            logger = AppLogger.getInstance(context);
        }
    }

    /**
     * Log di tipo Informativo (I).
     */
    public static void LogI(String tag, String message) {
        if (logger != null) {
            logger.add("I", tag, message);
        } else {
            // Fallback su Logcat se il logger non è ancora pronto
            Log.i(tag, "LOGGER_NOT_READY: " + message);
        }
    }

    /**
     * Log di tipo Avviso (W).
     */
    public static void LogW(String tag, String message) {
        if (logger != null) {
            logger.add("W", tag, message);
        } else {
            Log.w(tag, "LOGGER_NOT_READY: " + message);
        }
    }

    /**
     * Log di tipo Errore (E).
     */
    public static void LogE(String tag, String message) {
        if (logger != null) {
            logger.add("E", tag, message);
        } else {
            Log.e(tag, "LOGGER_NOT_READY: " + message);
        }
    }

    /**
     * Log di tipo Verboso (V).
     */
    public static void LogV(String tag, String message) {
        if (logger != null) {
            logger.add("V", tag, message);
        } else {
            Log.v(tag, "LOGGER_NOT_READY: " + message);
        }
    }

    /**
     * Log speciale per Riavvi (R). Ora viene loggato come ERRORE per massima visibilità.
     */
    public static void LogR(String tag, String message) {
        if (logger != null) {
            // --- MODIFICA INIZIO: Logga come Errore [E] ma mantiene il testo [RESTART] ---
            logger.add("E", tag, "[RESTART] " + message);
            // --- MODIFICA FINE ---
        } else {
            Log.e(tag, "LOGGER_NOT_READY (RESTART): " + message);
        }
    }
}