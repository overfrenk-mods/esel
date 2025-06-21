// Codice da incollare in EsNotificationListener.java
package esel.esel.esel.datareader;

import android.app.Notification;
import android.os.Bundle;
import android.os.PowerManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import esel.esel.esel.util.EselLog;
import esel.esel.esel.util.LocalBroadcaster;
import esel.esel.esel.util.SP;

public class EsNotificationListener extends NotificationListenerService {

    private static final String TAG = "EsNotificationListener";
    private static final long DUPLICATE_THRESHOLD_MS = 4 * 60 * 1000L;
    // Variabile statica per tenere traccia dell'ultimo timestamp processato con successo
    private static volatile long lastProcessedTimestamp = 0;

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        // Blocco synchronized sull'intera classe per garantire che una sola notifica venga processata alla volta
        synchronized (EsNotificationListener.class) {
            if (SP.getBoolean("use_patched_es", false)) return;

            String packageName = sbn.getPackageName();
            if (packageName != null && packageName.startsWith("com.senseonics")) {
                Notification notification = sbn.getNotification();
                if (notification == null) return;

                // Controlliamo se il timestamp di questa notifica è già stato processato.
                // Questo è il controllo anti-duplicato atomico.
                if (notification.when <= lastProcessedTimestamp) {
                    EselLog.LogW(TAG, "Notifica scartata (timestamp " + notification.when + " <= ultimo processato " + lastProcessedTimestamp + ").");
                    return;
                }

                // Se la notifica è nuova, aggiorniamo subito il timestamp e procediamo
                lastProcessedTimestamp = notification.when;
                EselLog.LogI(TAG, "Nuova notifica accettata. Timestamp: " + lastProcessedTimestamp);

                SGV sgv = generateSGVFromNotification(notification);

                if (sgv != null) {
                    EselLog.LogI(TAG, "SGV estratto: " + sgv.value + ". Avvio processamento.");
                    processAndBroadcastSgv(sgv);
                }
            }
        }
    }

    private void processAndBroadcastSgv(SGV sgv) {
        // Questa funzione ora può essere chiamata direttamente, senza thread separato,
        // perché siamo già in un contesto sicuro.
        try {
            long oldTime = SP.getLong("lastReadingTime", -1L);
            int oldValue = SP.getInt("lastReadingValue", -1);
            boolean hasTimeGap = (oldTime > 0) && (sgv.timestamp - oldTime) > 12 * 60 * 1000L;

            boolean isDuplicateReading = (sgv.value == oldValue && (sgv.timestamp - oldTime) < DUPLICATE_THRESHOLD_MS);
            if (isDuplicateReading) {
                EselLog.LogW(TAG, "Valore duplicato scartato (stesso valore, intervallo < 4 min).");
                SP.putLong("lastReadingTime", sgv.timestamp); // Aggiorniamo comunque il tempo
                return;
            }

            if (sgv.value >= 39) {
                boolean enable_smooth = SP.getBoolean("smooth_data", false) && !hasTimeGap;
                sgv.smooth(oldValue, enable_smooth);

                double slopeByMinute = 0d;
                if (oldTime > 0) {
                    slopeByMinute = (double) (sgv.value - oldValue) * 60000.0d / (double) (sgv.timestamp - oldTime);
                }
                if (!hasTimeGap) sgv.setDirection(slopeByMinute);

                DateFormat df = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                EselLog.LogI(TAG, "Invio valore: " + sgv.value + " | timestamp: " + df.format(new Date(sgv.timestamp)));
                LocalBroadcaster.broadcast(sgv, true);
            }
            SP.putLong("lastReadingTime", sgv.timestamp);
            SP.putInt("lastReadingValue", sgv.value);
        } catch (Exception e) {
            EselLog.LogE(TAG, "Errore durante il processamento del SGV: " + e.getMessage());
        }
    }

    private SGV generateSGVFromNotification(Notification notification) {
        // ... (questo metodo rimane invariato, con la logica a doppio ascolto)
        String valueString = null;
        Bundle extras = notification.extras;
        if (extras != null) {
            CharSequence textChars = extras.getCharSequence(Notification.EXTRA_TEXT);
            if (textChars != null && textChars.length() > 0) {
                String[] parts = textChars.toString().split(" ");
                if (parts.length > 0) { valueString = parts[0]; }
            }
        }
        if (valueString == null) {
            CharSequence tickerChars = notification.tickerText;
            if (tickerChars != null && tickerChars.length() > 0) {
                String[] parts = tickerChars.toString().split(" ");
                if (parts.length > 0) { valueString = parts[0]; }
            }
        }
        if (valueString == null) return null;
        int value;
        try {
            if (valueString.contains(".") || valueString.contains(",")) {
                value = SGV.Convert(Float.parseFloat(valueString.replace(",", ".")));
            } else {
                value = Integer.parseInt(valueString);
            }
        } catch (NumberFormatException e) { return null; }
        return new SGV(value, notification.when, 0);
    }

    @Override public void onListenerConnected() { super.onListenerConnected(); EselLog.LogI(TAG, "Notification Listener connesso e operativo."); }
    @Override public void onListenerDisconnected() { super.onListenerDisconnected(); EselLog.LogW(TAG, "Notification Listener disconnesso!"); }
}