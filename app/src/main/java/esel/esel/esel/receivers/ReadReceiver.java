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

import esel.esel.esel.datareader.EsNotificationListener;
import esel.esel.esel.datareader.SGV;
import esel.esel.esel.services.DataMonitorService;
import esel.esel.esel.util.EselLog;
import esel.esel.esel.util.LocalBroadcaster;
import esel.esel.esel.util.SP;

/**
 * Created by adrian on 04/08/17.
 */

public class ReadReceiver extends BroadcastReceiver {

    public static final long REPEAT_TIME = 1 * 20 * 1000L;

    private static final String TAG = "ReadReceiver";

    private boolean suppressBroadcast =false;
    private JSONArray output = new JSONArray();


    @Override
    public synchronized void onReceive(Context context, Intent intent) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Esel:ReadReceiver:Broadcast");
        wl.acquire(10 * 1000L);

        EselLog.LogV(TAG, "onReceive called. Starting DataMonitorService.");

        ContextCompat.startForegroundService(context, new Intent(context, DataMonitorService.class));

        wl.release();
    }

    public void CallBroadcast(Context context){
        int sync = 8;
        try {
            sync = SP.getInt("max-sync-hours", sync);
        } catch (Exception e) {
            e.printStackTrace();
        }
        long syncTime = sync * 60 * 60 * 1000L;
        long currentTime = System.currentTimeMillis();

        try {
            SP.putLong("readReceiver-called", System.currentTimeMillis());
            long lastReadingTime = SP.getLong("lastReadingTime", currentTime);

            if (lastReadingTime + syncTime < currentTime) {
                lastReadingTime = currentTime - syncTime;
            }
            broadcastData(context, lastReadingTime, true);
        } catch (Exception e) {
            String msg = e.getMessage();
            EselLog.LogE(TAG,msg);
        }
    }

    public static void FullExport(Context context, File file, int syncHours){
        long currentTime = System.currentTimeMillis();
        long syncTime = syncHours * 60 * 60 * 1000L;
        long lastTimestamp = currentTime - syncTime;

        boolean localSuppressBroadcast = true;
        JSONArray localOutput = new JSONArray();

        EselLog.LogI(TAG, "Starting Full Export. Target file: " + file.getAbsolutePath());

        int written =  new ReadReceiver().broadcastData(context, lastTimestamp, false);
        localSuppressBroadcast = false;

        WriteData(context, file, localOutput.toString());
        localOutput = new JSONArray();

        String msg = "Full Sync done: Read " + written + " values from DB\n(last " + syncHours + " hours)";
        EselLog.LogI(TAG, msg,true);

        SP.putLong("last_full_sync", currentTime);
    }

    private static void WriteData(Context context, File file, String data){

        if (!file.getParentFile().exists()) {
            EselLog.LogI(TAG, "Creating parent directory: " + file.getParentFile().getAbsolutePath());
            file.getParentFile().mkdir();
        }
        if (!file.getParentFile().canWrite()) {
            String msg = "Error: can not write data. Please enable the storage access permission for Esel. Path: " + file.getParentFile().getAbsolutePath();
            EselLog.LogE(TAG, msg,true);
            return;
        }
        if (!file.exists()) {
            try {
                file.createNewFile();
                FileWriter fileWriter = new FileWriter(file.getAbsoluteFile());
                BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
                bufferedWriter.write(data);
                bufferedWriter.close();
                EselLog.LogI(TAG, "Data written to file: " + file.getAbsolutePath());

            } catch (IOException err) {
                String msg = "Error creating/writing file: " + err.toString() + " occurred at: " + err.getMessage();
                EselLog.LogE(TAG, msg,true);
            }
        } else {
            EselLog.LogW(TAG, "File already exists, not overwriting: " + file.getAbsolutePath());
        }
    }

    public int broadcastData(Context context, long lastReadingTime, boolean is_continuous_run) {
        int result = 0;
        try {

            long currentTime = System.currentTimeMillis();
            SP.putLong("readReceiver-called", System.currentTimeMillis());

            int size = 1;
            long updatedReadingTime = lastReadingTime;

            boolean use_patched_es_internal = SP.getBoolean("use_patched_es", false);


            do {
                lastReadingTime = updatedReadingTime;

                List<SGV> valueArray = new ArrayList<>();

                if (SP.getBoolean("overwrite_bg", false)) {
                    int bg = SP.getInt("bg_value", 120);
                    SGV sgv = new SGV(bg, currentTime, 1);
                    valueArray.add(new SGV(bg, lastReadingTime, 1));
                    valueArray.add(new SGV(bg, currentTime, 2));

                }else if (use_patched_es_internal){
                    // valueArray = Datareader.readDataFromContentProvider(context, size, lastReadingTime);

                    if (valueArray.size() == 0) {
                        EselLog.LogE(TAG,"DB not readable!",true);
                    }
                }else {
                    boolean read_from_nl = true;
                    if(read_from_nl){
                        valueArray = EsNotificationListener.getData(size,lastReadingTime);
                    }
                }

                if (valueArray.size() != size) {
                    return result;
                }

                result +=  ProcesssValues(is_continuous_run,valueArray);

                updatedReadingTime = SP.getLong("lastReadingTime", lastReadingTime);
            } while (updatedReadingTime != lastReadingTime);

        } catch (android.database.CursorIndexOutOfBoundsException eb) {
            eb.printStackTrace();
            EselLog.LogW(TAG,"DB is empty! It can take up to 15min with running Eversense App until values are available!",true);
        } catch (Exception e) {
            e.printStackTrace();
            SP.putInt("lastReadingValue", 120);
        }

        return result;
    }

    private int ProcesssValues( boolean is_continuous_run, List<SGV> valueArray) {
        int result = 0;

        DateFormat df = new SimpleDateFormat("HH:mm:ss");

        long currentTime = System.currentTimeMillis();

        for (int i = 0; i < valueArray.size(); i++) {
            SGV sgv = valueArray.get(i);
            long oldTime = SP.getLong("lastReadingTime", -1L);

            boolean newValue = oldTime != sgv.timestamp;
            boolean futureValue = false;

            if(sgv.timestamp - currentTime > (60 * 1000)){
                long shiftValue = sgv.timestamp - currentTime;
                float sec = shiftValue/1000f;
                EselLog.LogW(TAG,"broadcastData called, value is in future by [sec] " + sec);
                futureValue = true;
            }

            if (newValue && !futureValue) {
                int oldValue = SP.getInt("lastReadingValue", -1);

                long sgvTime = sgv.timestamp;
                boolean hasTimeGap = (sgvTime - oldTime) > 12 * 60 *1000L;

                if (sgv.value >= 39 /*&& oldValue >= 39*/) {
                    if(is_continuous_run) {
                        boolean enable_smooth = SP.getBoolean("smooth_data", false) && !hasTimeGap;
                        sgv.smooth(oldValue, enable_smooth);
                    }

                    double slopeByMinute = 0d;
                    if (oldTime != sgvTime) {
                        slopeByMinute = (sgv.value - oldValue ) * 60000.0d / (( sgvTime - oldTime) * 1.0d);
                    }
                    if(!hasTimeGap){
                        sgv.setDirection(slopeByMinute);
                    }

                    try {
                        EselLog.LogI(TAG, "Invio SGV ad APS. Valore: " + sgv.value + ", Timestamp Originale: " + sgv.timestamp + " (" + df.format(new Date(sgv.timestamp)) + ")");
                        if (!suppressBroadcast) {
                            LocalBroadcaster.broadcast(sgv,is_continuous_run);
                            // AGGIUNTA CHIAVE: Aggiorna lastSentToApsTimestamp DOPO L'INVIO ad APS
                            EsNotificationListener.setLastSentToApsTimestamp(sgv.timestamp);
                        } else {
                            LocalBroadcaster.addSgvEntry(output, sgv);
                        }

                        result++;
                    }
                    catch(Exception e){
                        EselLog.LogE(TAG,"LocalBroadcaster.broadcast exception, result = " + e.getMessage());
                    }
                } else {
                    // La logica "NOT A READING!" non è più legata a un Toast
                }
                SP.putLong("lastReadingTime", sgvTime);
                SP.putInt("lastReadingValue", sgv.value);
            }
        }

        return result;
    }
}