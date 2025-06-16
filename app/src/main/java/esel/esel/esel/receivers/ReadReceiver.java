// ---------------- INIZIO CODICE COMPLETO E CORRETTO PER ReadReceiver.java ----------------
package esel.esel.esel.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import esel.esel.esel.datareader.EsNotificationListener;
import esel.esel.esel.datareader.SGV;
import esel.esel.esel.services.DataMonitorService;
import esel.esel.esel.util.EselLog;
import esel.esel.esel.util.LocalBroadcaster;
import esel.esel.esel.util.SP;

public class ReadReceiver extends BroadcastReceiver {

    public static final long REPEAT_TIME = 5 * 60 * 1000L; // 5 minuti

    private static final String TAG = "ReadReceiver";

    @Override
    public synchronized void onReceive(Context context, Intent intent) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Esel:ReadReceiver:Broadcast");
        wl.acquire(20 * 1000L);

        EselLog.LogV(TAG, "ReadReceiver onReceive. Avvio il DataMonitorService per assicurarmi che sia attivo.");
        ContextCompat.startForegroundService(context, new Intent(context, DataMonitorService.class));

        if (wl.isHeld()) {
            wl.release();
        }
    }

    public void CallBroadcast(Context context){
        EselLog.LogV(TAG, "CallBroadcast eseguito.");
        try {
            SP.putLong("readReceiver-called", System.currentTimeMillis());

            // CORREZIONE: Aggiunta la 'L' a '0' per specificare che è un tipo long.
            long lastReadingTime = SP.getLong("lastReadingTime", 0L);

            broadcastData(context, lastReadingTime);
        } catch (Exception e) {
            EselLog.LogE(TAG, "Errore in CallBroadcast: " + e.getMessage());
        }
    }

    public static void FullExport(Context context, File file, int syncHours){
        String msg = "Funzione di Esportazione Disabilitata. Richiede un aggiornamento alle nuove API di Android.";
        EselLog.LogW(TAG, msg,true);
    }

    private static void WriteData(Context context, File file, String data){
        EselLog.LogW(TAG, "WriteData è disabilitato.");
    }

    public int broadcastData(Context context, long lastReadingTime) {
        int valuesProcessed = 0;
        try {
            while (true) {
                List<SGV> valueArray = EsNotificationListener.getData(1, lastReadingTime);

                if (valueArray.isEmpty()) {
                    break;
                }

                valuesProcessed += ProcesssValues(valueArray);

                long newLastReadingTime = SP.getLong("lastReadingTime", lastReadingTime);
                if (newLastReadingTime == lastReadingTime) {
                    break;
                }
                lastReadingTime = newLastReadingTime;
            }
        } catch (Exception e) {
            EselLog.LogE(TAG, "Errore in broadcastData: " + e.getMessage());
            e.printStackTrace();
        }

        if (valuesProcessed > 0) {
            EselLog.LogI(TAG, "Sincronizzazione completata. Processati " + valuesProcessed + " nuovi valori.");
        } else {
            EselLog.LogV(TAG, "Nessun nuovo valore da sincronizzare.");
        }

        return valuesProcessed;
    }

    private int ProcesssValues(List<SGV> valueArray) {
        int result = 0;
        DateFormat df = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        long currentTime = System.currentTimeMillis();

        for (SGV sgv : valueArray) {
            long oldTime = SP.getLong("lastReadingTime", -1L);

            boolean isNewValue = oldTime != sgv.timestamp;
            boolean isFutureValue = sgv.timestamp > currentTime + (5 * 60 * 1000);

            if (isNewValue && !isFutureValue) {
                int oldValue = SP.getInt("lastReadingValue", -1);
                boolean hasTimeGap = (sgv.timestamp - oldTime) > 12 * 60 * 1000L;

                if (sgv.value >= 39) {
                    boolean enable_smooth = SP.getBoolean("smooth_data", false) && !hasTimeGap;
                    sgv.smooth(oldValue, enable_smooth);

                    double slopeByMinute = 0d;
                    if (oldTime > 0 && oldTime != sgv.timestamp) {
                        slopeByMinute = (double) (sgv.value - oldValue) * 60000.0d / (double) (sgv.timestamp - oldTime);
                    }
                    if (!hasTimeGap) {
                        sgv.setDirection(slopeByMinute);
                    }

                    try {
                        EselLog.LogI(TAG, "Invio valore: " + sgv.value + " | timestamp: " + df.format(new Date(sgv.timestamp)));
                        LocalBroadcaster.broadcast(sgv, true);
                        EsNotificationListener.setLastSentToApsTimestamp(sgv.timestamp);
                        result++;
                    } catch(Exception e){
                        EselLog.LogE(TAG,"Errore nel LocalBroadcaster: " + e.getMessage());
                    }
                }
                SP.putLong("lastReadingTime", sgv.timestamp);
                SP.putInt("lastReadingValue", sgv.value);
            }
        }
        return result;
    }
}
// ---------------- FINE CODICE COMPLETO E CORRETTO PER ReadReceiver.java ----------------