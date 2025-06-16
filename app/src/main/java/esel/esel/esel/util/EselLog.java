// ---------------- INIZIO CODICE PER EselLog.java ----------------
package esel.esel.esel.util;

import android.content.Context;
import android.util.Log;

import esel.esel.esel.AppLogger; // MODIFICA: Importiamo il nuovo AppLogger

public class EselLog {

    private static Context appContext; // MODIFICA: Aggiunto per "ricordare" il contesto dell'app

    /**
     * MODIFICA: Questo nuovo metodo va chiamato una sola volta all'avvio dell'app.
     * Serve per dare al nostro logger il contesto necessario per funzionare.
     */
    public static void init(Context context) {
        appContext = context.getApplicationContext();
        // Inizializziamo subito il nostro logger, così è pronto all'uso
        AppLogger.getInstance(appContext);
    }

    public static void LogI(String tag, String value, boolean toast) {
        if(toast) {
            ToastUtils.makeToast("Info: " + value);
        }
        LogI(tag,value);
    }

    public static void LogI(String tag, String value){
        String type = "Info"; // Tolto lo spazio, più pulito
        Log.i(tag, value); // MODIFICA: Usiamo Log.i per Info, per coerenza in Logcat

        // MODIFICA: Sostituita la vecchia chiamata con il nuovo AppLogger
        if (appContext != null) {
            AppLogger.getInstance(appContext).add(type, tag, value);
        } else {
            Log.e("EselLog", "Logger non inizializzato! Chiamare EselLog.init() all'avvio dell'app.");
        }
    }

    public static void LogE(String tag, String value, boolean toast) {
        if(toast) {
            ToastUtils.makeToast("Error: " + value);
        }
        LogE(tag,value);
    }

    public static void LogE(String tag, String value){
        String type = "Error";
        Log.e(tag, value); // MODIFICA: Usiamo Log.e per Error

        // MODIFICA: Sostituita la vecchia chiamata con il nuovo AppLogger
        if (appContext != null) {
            AppLogger.getInstance(appContext).add(type, tag, value);
        } else {
            Log.e("EselLog", "Logger non inizializzato! Chiamare EselLog.init() all'avvio dell'app.");
        }
    }

    public static void LogW(String tag, String value, boolean toast) {
        if(toast) {
            ToastUtils.makeToast("Warning: " + value);
        }
        LogW(tag,value);
    }

    public static void LogW(String tag, String value){
        String type = "Warning";
        Log.w(tag, value); // MODIFICA: Usiamo Log.w per Warning

        // MODIFICA: Sostituita la vecchia chiamata con il nuovo AppLogger
        if (appContext != null) {
            AppLogger.getInstance(appContext).add(type, tag, value);
        } else {
            Log.e("EselLog", "Logger non inizializzato! Chiamare EselLog.init() all'avvio dell'app.");
        }
    }

    public static void LogV(String tag, String value, boolean toast) {
        if(toast) {
            ToastUtils.makeToast("Message: " + value);
        }
        LogV(tag,value);
    }

    public static void LogV(String tag, String value){
        String type = "Message";
        Log.v(tag,value);
        // MODIFICA: Rispettiamo la scelta originale di non salvare i log "Verbose" (V) nel file.
        // Quindi qui non aggiungiamo la chiamata al nostro AppLogger.
    }
}
// ---------------- FINE CODICE PER EselLog.java ----------------