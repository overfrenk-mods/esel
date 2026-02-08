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
import java.util.Date; // Importante per la correzione
import java.util.List;
import java.util.Locale;

import esel.esel.esel.datareader.SGV;

public class AapsSender {
    private static final String TAG = "AapsSender";

    private static final String XDRIP_PLUS_NS_EMULATOR_ACTION = "com.eveningoutpost.dexdrip.NS_EMULATOR";
    private static final String NSCLIENT_ACTION_DATABASE = "info.nightscout.client.DBACCESS";

    private static final SimpleDateFormat jsonDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US);

    /**
     * Invia i dati nel formato corretto per AAPS / xDrip+ (emulando un NS Client)
     */
    public static void sendToAaps(Context context, SGV sgv) {
        if (sgv == null) {
            EselLog.LogW(TAG, "SGV nullo. Invio AAPS annullato.");
            return;
        }

        try {
            // AAPS si aspetta un Array di voci
            JSONArray entriesBody = new JSONArray();
            entriesBody.put(generateSgvEntryJson(sgv));

            sendBundle(context, "add", "entries", entriesBody.toString(), XDRIP_PLUS_NS_EMULATOR_ACTION, "AAPS/xDrip");

        } catch (Exception e) {
            EselLog.LogE(TAG, "Errore invio AAPS: " + e.getMessage());
        }
    }

    /**
     * Invia i dati nel formato corretto per i client Nightscout (App ufficiale)
     */
    public static void sendToNsClient(Context context, SGV sgv) {
        if (sgv == null) return;

        try {
            // NSClient gestisce anche il singolo oggetto
            JSONObject sgvJson = generateSgvEntryJson(sgv);

            sendBundle(context, "dbAdd", "entries", sgvJson.toString(), NSCLIENT_ACTION_DATABASE, "NSClient");

        } catch (Exception e) {
            EselLog.LogE(TAG, "Errore invio NSClient: " + e.getMessage());
        }
    }

    /**
     * Genera l'oggetto JSON standard per i dati glicemici
     */
    private static JSONObject generateSgvEntryJson(SGV sgv) throws JSONException {
        JSONObject json = new JSONObject();

        // Passiamo i valori numerici (anche 39 o 401)
        json.put("sgv", sgv.value);
        json.put("rawbg", sgv.raw); // Utile per debug, anche se uguale a sgv

        if (sgv.direction == null) {
            json.put("direction", "NONE");
        } else {
            json.put("direction", sgv.direction);
        }

        json.put("device", "Eversense-Reader");
        json.put("type", "sgv");

        // Timestamp numerico
        json.put("date", sgv.timestamp);

        // --- FIX CRITICO QUI SOTTO ---
        // SimpleDateFormat vuole un oggetto Date, non un long.
        json.put("dateString", jsonDateFormat.format(new Date(sgv.timestamp)));

        return json;
    }

    /**
     * Metodo di invio Broadcast generico e robusto
     */
    private static void sendBundle(Context context, String action, String collection, String jsonData, String intentAction, String targetLogName) {
        final Bundle bundle = new Bundle();
        bundle.putString("action", action);
        bundle.putString("collection", collection);
        bundle.putString("data", jsonData);

        final Intent intent = new Intent(intentAction);
        intent.putExtras(bundle).addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);

        // Cerchiamo chi è in ascolto (AAPS, xDrip, NSClient)
        PackageManager pm = context.getPackageManager();
        List<ResolveInfo> receivers = pm.queryBroadcastReceivers(intent, 0);

        if (receivers.isEmpty()) {
            // È normale se l'utente non ha installato l'app target
            // EselLog.LogW(TAG, "Nessuna app trovata per: " + targetLogName);
            return;
        }

        for (ResolveInfo receiver : receivers) {
            if (receiver.activityInfo != null) {
                String packageName = receiver.activityInfo.packageName;
                intent.setPackage(packageName);
                context.sendBroadcast(intent);
                EselLog.LogI(TAG, "→ Dati inviati a: " + packageName);
            }
        }
    }
}