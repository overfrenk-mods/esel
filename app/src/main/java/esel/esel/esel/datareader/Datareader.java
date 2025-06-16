package esel.esel.esel.datareader;

import android.content.Context; // Mantenuto per compatibilità, ma non più usato da metodi qui.
import android.database.Cursor; // Mantenuto per compatibilità, ma non più usato da metodi qui.
import android.net.Uri; // Mantenuto per compatibilità, ma non più usato da metodi qui.
import android.util.Log; // Mantenuto per compatibilità.

import java.io.BufferedReader; // Rimosso se non strettamente necessario
import java.io.DataOutputStream; // Rimosso se non strettamente necessario
import java.io.IOException; // Rimosso se non strettamente necessario
import java.io.InputStreamReader; // Rimosso se non strettamente necessario
import java.util.ArrayList; // Mantenuto per compatibilità
import java.util.List; // Mantenuto per compatibilità

import esel.esel.esel.Esel; // Mantenuto per compatibilità
import esel.esel.esel.R; // Mantenuto per compatibilità
import esel.esel.esel.util.EselLog; // Mantenuto per compatibilità
import esel.esel.esel.util.SP; // Mantenuto per compatibilità

import static android.content.ContentValues.TAG; // Mantenuto per compatibilità

/**
 * Created by bernhard on 18-11-03.
 */

public class Datareader {

    // Questi URI non sono più usati dato che la lettura da Content Provider è rimossa.
    // public static String uriGlucose = "content://com.senseonics.gen12androidapp.glucose";
    // public static String uriTransmitter = "content://com.senseonics.gen12androidapp.transmitter";

    private static final String CLASS_TAG = "Datareader";

    // Metodo readDataFromContentProvider rimosso.
    // Metodo readDataFromContentProvider (con overload) rimosso.

    public static SGV generateSGV(String dataString){
        String[] tokens = dataString.split(",");
        if (tokens.length < 3) {
            EselLog.LogE(CLASS_TAG, "Invalid dataString format for SGV: " + dataString + ". Expected at least 3 tokens.");
            throw new IllegalArgumentException("Invalid SGV data format");
        }
        long timestamp = Long.parseLong(tokens[0]);
        int value = Integer.parseInt(tokens[1]);
        int record = Integer.parseInt(tokens[2]);
        return new SGV(value, timestamp,record);
    }

}