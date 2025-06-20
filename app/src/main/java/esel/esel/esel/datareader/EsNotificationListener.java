// ---------------- INIZIO VERSIONE DEFINITIVA E COMPLETA DI EsNotificationListener.java ----------------
package esel.esel.esel.datareader;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.List;

import esel.esel.esel.util.EselLog;
import esel.esel.esel.util.SP;

public class EsNotificationListener extends NotificationListenerService {

    private static final String TAG = "EsNotificationListener";
    private static volatile SGV latestStoredSgv = null;
    private static volatile long lastSentToApsTimestamp = 0;

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (SP.getBoolean("use_patched_es", false)) return;

        String packageName = sbn.getPackageName();
        if (packageName != null && packageName.startsWith("com.senseonics")) {
            Notification notification = sbn.getNotification();
            if (notification == null) return;

            SGV sgv = generateSGVFromNotification(notification);

            if (sgv != null) {
                EselLog.LogI(TAG, "SGV estratto con successo: " + sgv.value + " @ " + sgv.timestamp);
                synchronized (EsNotificationListener.class) {
                    if (latestStoredSgv == null || sgv.timestamp > latestStoredSgv.timestamp) {
                        if (sgv.timestamp > lastSentToApsTimestamp) {
                            latestStoredSgv = sgv;
                            EselLog.LogI(TAG, "Nuovo SGV memorizzato.");
                        } else {
                            EselLog.LogW(TAG, "SGV scartato (timestamp <= ultimo inviato).");
                        }
                    } else {
                        EselLog.LogW(TAG, "SGV scartato (timestamp non più recente).");
                    }
                }
            }
        }
    }

    private SGV generateSGVFromNotification(Notification notification) {
        String valueString = null;
        Bundle extras = notification.extras;
        if (extras != null) {
            CharSequence textChars = extras.getCharSequence(Notification.EXTRA_TEXT);
            if (textChars != null && textChars.length() > 0) {
                String[] parts = textChars.toString().split(" ");
                if (parts.length > 0) {
                    valueString = parts[0];
                    EselLog.LogI(TAG, "Valore trovato nel campo moderno (EXTRA_TEXT).");
                }
            }
        }
        if (valueString == null) {
            CharSequence tickerChars = notification.tickerText;
            if (tickerChars != null && tickerChars.length() > 0) {
                String[] parts = tickerChars.toString().split(" ");
                if (parts.length > 0) {
                    valueString = parts[0];
                    EselLog.LogW(TAG, "Valore trovato nel campo obsoleto (TickerText).");
                }
            }
        }
        if (valueString == null) {
            EselLog.LogE(TAG, "Impossibile trovare il valore della glicemia in qualsiasi campo della notifica.");
            return null;
        }
        int value;
        try {
            if (valueString.contains(".") || valueString.contains(",")) {
                value = SGV.Convert(Float.parseFloat(valueString.replace(",", ".")));
            } else {
                value = Integer.parseInt(valueString);
            }
        } catch (NumberFormatException e) {
            EselLog.LogE(TAG, "Impossibile convertire il valore trovato. Stringa: '" + valueString + "'");
            return null;
        }
        return new SGV(value, notification.when, 0);
    }

    public static List<SGV> getData(int number, long lastReadingTime) {
        List<SGV> result = new ArrayList<>();
        SGV sgvToSend = null;
        synchronized (EsNotificationListener.class) {
            if (latestStoredSgv != null) {
                if (latestStoredSgv.timestamp > lastReadingTime && latestStoredSgv.timestamp > lastSentToApsTimestamp) {
                    sgvToSend = latestStoredSgv;
                    latestStoredSgv = null;
                }
            }
        }
        if (sgvToSend != null) { result.add(sgvToSend); }
        return result;
    }

    public static void setLastSentToApsTimestamp(long timestamp) {
        synchronized (EsNotificationListener.class) {
            if(timestamp > lastSentToApsTimestamp) {
                lastSentToApsTimestamp = timestamp;
                EselLog.LogI(TAG, "Timestamp dell'ultimo invio AGGIORNATO a: " + timestamp);
            }
        }
    }

    public static long getLastSentToApsTimestamp() {
        return lastSentToApsTimestamp;
    }

    @Override public void onListenerConnected() { super.onListenerConnected(); EselLog.LogI(TAG, "Notification Listener connesso e operativo."); }
    @Override public void onListenerDisconnected() { super.onListenerDisconnected(); EselLog.LogW(TAG, "Notification Listener disconnesso!"); }
}