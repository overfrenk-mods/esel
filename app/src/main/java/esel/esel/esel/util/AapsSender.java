// ---------- CODICE FINALE E CORRETTO PER AapsSender.java ----------
package esel.esel.esel.util;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

import esel.esel.esel.datareader.SGV;

public class AapsSender {
    private static final String TAG = "AapsSender";

    // --- MODIFICA: Usiamo gli stessi "canali" del vecchio codice funzionante ---
    private static final String XDRIP_PLUS_NS_EMULATOR_ACTION = "com.eveningoutpost.dexdrip.NS_EMULATOR";
    private static final String NSCLIENT_ACTION_DATABASE = "info.nightscout.client.DBACCESS";

    private static final SimpleDateFormat jsonDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US);

    /**
     * Invia i dati nel formato corretto per AAPS / xDrip+ (emulando un NS Client)
     */
    public static void sendToAaps(Context context, SGV sgv) {
        if (sgv == null) {
            EselLog.LogW(TAG, "SGV è nullo, impossibile inviare ad AAPS.");
            return;
        }

        try {
            // --- MODIFICA: Creiamo il corpo del messaggio come JSONArray, esattamente come nel vecchio codice ---
            JSONArray entriesBody = new JSONArray();
            entriesBody.put(generateSgvEntryJson(sgv));

            // Invochiamo il metodo di invio generico con i parametri corretti per AAPS/xDrip
            sendBundle(context, "add", "entries", entriesBody.toString(), XDRIP_PLUS_NS_EMULATOR_ACTION, "AAPS/xDrip");

        } catch (Exception e) {
            EselLog.LogE(TAG, "Errore durante la preparazione dei dati per AAPS: " + e.getMessage());
        }
    }

    /**
     * Invia i dati nel formato corretto per i client Nightscout
     */
    public static void sendToNsClient(Context context, SGV sgv) {
        if (sgv == null) {
            EselLog.LogW(TAG, "SGV è nullo, impossibile inviare a NSClient.");
            return;
        }

        try {
            // --- MODIFICA: Creiamo il corpo del messaggio come JSONObject singolo ---
            JSONObject sgvJson = generateSgvEntryJson(sgv);

            // Invochiamo il metodo di invio generico con i parametri corretti per NSClient
            sendBundle(context, "dbAdd", "entries", sgvJson.toString(), NSCLIENT_ACTION_DATABASE, "NSClient");

        } catch (Exception e) {
            EselLog.LogE(TAG, "Errore durante la preparazione dei dati per NSClient: " + e.getMessage());
        }
    }

    /**
     * Metodo helper privato per creare l'oggetto JSON standard, identico al vecchio codice.
     */
    private static JSONObject generateSgvEntryJson(SGV sgv) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("sgv", sgv.value);
        json.put("rawbg", sgv.raw);
        if (sgv.direction == null) {
            json.put("direction", "NONE");
        } else {
            json.put("direction", sgv.direction);
        }
        json.put("device", "ESEL");
        json.put("type", "sgv");
        json.put("date", sgv.timestamp);
        json.put("dateString", jsonDateFormat.format(sgv.timestamp));
        return json;
    }

    /**
     * Metodo di invio generico e robusto, identico al vecchio codice.
     */
    private static void sendBundle(Context context, String action, String collection, String jsonData, String intentAction, String targetLogName) {
        final Bundle bundle = new Bundle();
        bundle.putString("action", action);
        bundle.putString("collection", collection);
        bundle.putString("data", jsonData); // I dati vengono inviati come stringa JSON

        final Intent intent = new Intent(intentAction);
        intent.putExtras(bundle).addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);

        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> receivers = pm.queryBroadcastReceivers(intent, 0);

        if (receivers.isEmpty()) {
            EselLog.LogE(TAG, "INVIO FALLITO: Nessuna app trovata in ascolto per l'azione " + targetLogName);
            return;
        }

        for (ResolveInfo receiver : receivers) {
            if (receiver.activityInfo != null) {
                String packageName = receiver.activityInfo.packageName;
                intent.setPackage(packageName);
                context.sendBroadcast(intent);
                EselLog.LogI(TAG, "Dati inviati a (" + packageName + ") con successo.");
            }
        }
    }
}