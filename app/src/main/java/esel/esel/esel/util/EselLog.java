// ---------------- INIZIO CODICE PER EselLog.java CON LogR ----------------
package esel.esel.esel.util;

import android.content.Context;
import android.util.Log;

import esel.esel.esel.AppLogger;

public class EselLog {

    private static Context appContext;

    public static void init(Context context) {
        appContext = context.getApplicationContext();
        AppLogger.getInstance(appContext);
    }

    // --- NUOVO METODO PER LOGGARE I RIAVVII ---
    public static void LogR(String tag, String value){
        String type = "RESTART"; // Un tipo specifico per identificare i riavvii
        Log.e(tag, "RESTART: " + value); // Lo logghiamo come Errore in Logcat per massima visibilità

        if (appContext != null) {
            AppLogger.getInstance(appContext).add(type, tag, value);
        } else {
            Log.e("EselLog", "Logger non inizializzato! Chiamare EselLog.init() all'avvio dell'app.");
        }
    }
    // --- FINE NUOVO METODO ---

    public static void LogI(String tag, String value, boolean toast) {
        if(toast) {
            ToastUtils.makeToast("Info: " + value);
        }
        LogI(tag,value);
    }

    public static void LogI(String tag, String value){
        String type = "Info";
        Log.i(tag, value);

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
        Log.e(tag, value);

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
        Log.w(tag, value);

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
    }
}
// ---------------- FINE CODICE PER EselLog.java ----------------