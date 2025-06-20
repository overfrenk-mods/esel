// ---------------- INIZIO CODICE OTTIMIZZATO PER ReadReceiver.java ----------------
package esel.esel.esel.receivers;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;

import androidx.core.content.ContextCompat;

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

    public static final long REPEAT_TIME = 5 * 60 * 1000L;
    private static final long DUPLICATE_THRESHOLD_MS = 4 * 60 * 1000L;
    private static final String TAG = "ReadReceiver";
    public static final int ALARM_REQUEST_CODE = 123;

    @Override
    public synchronized void onReceive(Context context, Intent intent) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        // --- MODIFICA DI OTTIMIZZAZIONE ---
        // Ridotto il tempo del WakeLock da 30 a 10 secondi. È più che sufficiente
        // per completare il lavoro e riduce il consumo di batteria.
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Esel:ReadReceiverWakelock");
        wl.acquire(10 * 1000L);

        EselLog.LogI(TAG, "ReadReceiver onReceive - Sveglia esatta ricevuta.");

        new Thread(() -> {
            try {
                CallBroadcast(context.getApplicationContext());
            } finally {
                scheduleNextExactAlarm(context.getApplicationContext());
                if (wl.isHeld()) {
                    wl.release();
                    EselLog.LogV(TAG, "Wakelock rilasciato.");
                }
            }
        }).start();

        ContextCompat.startForegroundService(context, new Intent(context, DataMonitorService.class));
    }

    private void scheduleNextExactAlarm(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pendingIntent = getPendingIntent(context);
        long triggerAtMillis = SystemClock.elapsedRealtime() + REPEAT_TIME;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, pendingIntent);
        } else {
            alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, pendingIntent);
        }
        EselLog.LogI(TAG, "Prossima sveglia esatta programmata tra " + REPEAT_TIME / 1000 + " secondi.");
    }

    public static void cancelAlarm(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(getPendingIntent(context));
        EselLog.LogI(TAG, "Sveglia esatta cancellata.");
    }

    private static PendingIntent getPendingIntent(Context context) {
        Intent intent = new Intent(context, ReadReceiver.class);
        return PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public void CallBroadcast(Context context){
        EselLog.LogV(TAG, "CallBroadcast eseguito.");
        try {
            SP.putLong("readReceiver-called", System.currentTimeMillis());
            long lastReadingTime = SP.getLong("lastReadingTime", 0L);
            broadcastData(context, lastReadingTime);
        } catch (Exception e) {
            EselLog.LogE(TAG, "Errore in CallBroadcast: " + e.getMessage());
        }
    }

    public int broadcastData(Context context, long lastReadingTime) {
        int valuesProcessed = 0;
        try {
            while (true) {
                List<SGV> valueArray = EsNotificationListener.getData(1, lastReadingTime);
                if (valueArray.isEmpty()) break;
                valuesProcessed += ProcesssValues(valueArray);
                long newLastReadingTime = SP.getLong("lastReadingTime", lastReadingTime);
                if (newLastReadingTime == lastReadingTime) break;
                lastReadingTime = newLastReadingTime;
            }
        } catch (Exception e) {
            EselLog.LogE(TAG, "Errore in broadcastData: " + e.getMessage());
            e.printStackTrace();
        }
        if (valuesProcessed > 0) { EselLog.LogI(TAG, "Sincronizzazione completata. Processati " + valuesProcessed + " nuovi valori."); }
        else { EselLog.LogV(TAG, "Nessun nuovo valore da sincronizzare."); }
        return valuesProcessed;
    }

    private int ProcesssValues(List<SGV> valueArray) {
        int result = 0;
        DateFormat df = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        long currentTime = System.currentTimeMillis();

        for (SGV sgv : valueArray) {
            long oldTime = SP.getLong("lastReadingTime", -1L);
            int oldValue = SP.getInt("lastReadingValue", -1);

            boolean hasTimeGap = (oldTime > 0) && (sgv.timestamp - oldTime) > 12 * 60 * 1000L;

            boolean isNewValueByTime = oldTime != sgv.timestamp;
            boolean isFutureValue = sgv.timestamp > currentTime + (5 * 60 * 1000);

            if (isNewValueByTime && !isFutureValue) {
                boolean isDuplicateReading = (sgv.value == oldValue && (sgv.timestamp - oldTime) < DUPLICATE_THRESHOLD_MS);
                if (isDuplicateReading) {
                    EselLog.LogW(TAG, "Valore duplicato scartato (stesso valore, intervallo < 4 min). Valore: " + sgv.value);
                    SP.putLong("lastReadingTime", sgv.timestamp);
                    continue;
                }

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
                        if (sgv.timestamp <= EsNotificationListener.getLastSentToApsTimestamp()) {
                            EselLog.LogW(TAG, "Invio saltato dal controllo finale. Timestamp già inviato: " + sgv.timestamp);
                            continue;
                        }

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

    public static void FullExport(Context context, java.io.File file, int syncHours){ EselLog.LogW(TAG, "Funzione di Esportazione Disabilitata.",true); }
    private static void WriteData(Context context, java.io.File file, String data){ EselLog.LogW(TAG, "WriteData è disabilitato."); }
}