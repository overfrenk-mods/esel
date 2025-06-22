// ---------------- INIZIO CODICE COMPLETO E CORRETTO DI EsNotificationListener.java ----------------
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
    private static volatile long lastProcessedTimestamp = 0;

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        synchronized (EsNotificationListener.class) {
            if (SP.getBoolean("use_patched_es", false)) return;
            String packageName = sbn.getPackageName();
            if (packageName != null && packageName.startsWith("com.senseonics")) {
                Notification notification = sbn.getNotification();
                if (notification == null) return;
                if (notification.when <= lastProcessedTimestamp) {
                    return;
                }
                lastProcessedTimestamp = notification.when;
                SGV sgv = generateSGVFromNotification(notification);
                if (sgv != null) {
                    processAndBroadcastSgv(sgv);
                }
            }
        }
    }

    private void processAndBroadcastSgv(SGV sgv) {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Esel:ListenerProcessingWakelock");
        wl.acquire(10 * 1000L);
        try {
            int oldValue = SP.getInt("lastReadingValue", -1);
            long oldTime = SP.getLong("lastReadingTime", -1L);
            boolean hasTimeGap = (oldTime > 0) && (sgv.timestamp - oldTime) > 12 * 60 * 1000L;
            boolean isDuplicateReading = (sgv.value == oldValue && (sgv.timestamp - oldTime) < DUPLICATE_THRESHOLD_MS);
            if (isDuplicateReading) {
                SP.putLong("lastReadingTime", sgv.timestamp);
                return;
            }
            if (sgv.value >= 39) {
                // Calcola la pendenza sui valori GREZZI
                double slopeByMinute = 0d;
                if (oldTime > 0) {
                    slopeByMinute = (double) (sgv.raw - oldValue) * 60000.0d / (double) (sgv.timestamp - oldTime);
                }
                if (!hasTimeGap) {
                    sgv.setDirection(slopeByMinute);
                }
                // Applica lo smoothing (se attivo) solo DOPO
                boolean enable_smooth = SP.getBoolean("smooth_data", false) && !hasTimeGap;
                if (enable_smooth) {
                    sgv.smooth(oldValue);
                }

                DateFormat df = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                EselLog.LogI(TAG, "Invio valore: " + sgv.value + " (Grezzo: " + sgv.raw + ") | Direzione: " + sgv.direction);

                // --- QUESTA È LA RIGA CORRETTA ---
                LocalBroadcaster.broadcast(sgv, true);

            }
            // Salva sempre il valore GREZZO
            SP.putLong("lastReadingTime", sgv.timestamp);
            SP.putInt("lastReadingValue", sgv.raw);
        } catch (Exception e) {
            EselLog.LogE(TAG, "Errore durante il processamento del SGV: " + e.getMessage());
        } finally {
            if (wl.isHeld()) {
                wl.release();
            }
        }
    }

    private SGV generateSGVFromNotification(Notification notification) {
        String valueString = null;
        Bundle extras = notification.extras;
        if (extras != null) {
            CharSequence textChars = extras.getCharSequence(Notification.EXTRA_TEXT);
            if (textChars != null && textChars.length() > 0) { String[] parts = textChars.toString().split(" "); if (parts.length > 0) valueString = parts[0]; }
        }
        if (valueString == null) {
            CharSequence tickerChars = notification.tickerText;
            if (tickerChars != null && tickerChars.length() > 0) { String[] parts = tickerChars.toString().split(" "); if (parts.length > 0) valueString = parts[0]; }
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

    @Override public void onListenerConnected() { super.onListenerConnected(); EselLog.LogI(TAG, "Notification Listener connesso."); }
    @Override public void onListenerDisconnected() { super.onListenerDisconnected(); EselLog.LogW(TAG, "Notification Listener disconnesso."); }
}