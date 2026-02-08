package esel.esel.esel.datareader;

import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import esel.esel.esel.util.EselLog;
import esel.esel.esel.util.SP;

public class EsNotificationListener extends NotificationListenerService {
    private static final String TAG = "EsNotificationListener";

    public static final String ACTION_NEW_SGV_DATA = "esel.esel.esel.ACTION_NEW_SGV_DATA";
    public static final String EXTRA_SGV_DATA = "esel.esel.esel.EXTRA_SGV_DATA";
    public static final String KEY_LAST_SEEN_NOTIFICATION_TEXT = "last_seen_notification_text";
    public static final String KEY_LAST_SEEN_NOTIFICATION_WHEN = "last_seen_notification_when";

    // Regex per trovare numeri (es. 120, 5.5)
    private static final Pattern VALUE_PATTERN = Pattern.compile("(?<!\\d:)\\b(\\d+([,.]\\d+)?)\\b(?!:\\d)");

    private static volatile long lastProcessTimeMs = 0;
    private static final long NOTIFICATION_COOLDOWN_MS = 10000; // Ridotto a 10s per reattività

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        // 1. WAKELOCK ISTANTANEO (Zero Lag)
        // Svegliamo la CPU appena Android ci avvisa della notifica.
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = null;
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Esel:NotificationCatch");
            wakeLock.acquire(2000); // 2 secondi per elaborare e passare la palla al Service
        }

        try {
            if (!shouldReadNotification(sbn)) return;

            Notification notification = sbn.getNotification();
            if (notification == null) return;

            String fullText = extractFullText(notification);
            if (fullText.isEmpty()) {
                EselLog.LogW(TAG, "Notifica ricevuta ma testo vuoto.");
                return;
            }

            // Salviamo per debug/sync
            SP.putString(KEY_LAST_SEEN_NOTIFICATION_TEXT, fullText);
            SP.putLong(KEY_LAST_SEEN_NOTIFICATION_WHEN, notification.when);

            // Filtro anti-doppioni temporali
            long now = System.currentTimeMillis();
            if (now - lastProcessTimeMs < NOTIFICATION_COOLDOWN_MS) {
                // Se arriva la stessa notifica dopo 1 secondo, la ignoriamo.
                return;
            }
            lastProcessTimeMs = now;

            // 2. PARSING VERO (Numeri + LO/HI)
            SGV sgv = generateSGVFromText(fullText, notification.when);

            if (sgv == null) {
                EselLog.LogW(TAG, "Impossibile estrarre glicemia da: \"" + fullText + "\"");
                return;
            }

            broadcastSGV(sgv);

        } catch (Exception e) {
            EselLog.LogE(TAG, "Errore cattura notifica: " + e.getMessage());
        } finally {
            // Rilascia il lock se è ancora attivo (sicurezza)
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        }
    }

    private boolean shouldReadNotification(StatusBarNotification sbn) {
        if (SP.getBoolean("use_patched_es", false)) return false;
        if (sbn == null) return false;
        String packageName = sbn.getPackageName();
        // Filtriamo rigorosamente Senseonics
        return packageName != null && packageName.startsWith("com.senseonics");
    }

    private void broadcastSGV(SGV sgv) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        EselLog.LogI(TAG, "Notifica catturata -> Valore: " + sgv.value + " | Orario: " + sdf.format(new Date(sgv.timestamp)));

        Intent serviceIntent = new Intent(ACTION_NEW_SGV_DATA);
        serviceIntent.putExtra(EXTRA_SGV_DATA, sgv);
        LocalBroadcastManager.getInstance(this).sendBroadcast(serviceIntent);
    }

    private String extractFullText(Notification notification) {
        Set<String> textParts = new HashSet<>();
        Bundle extras = notification.extras;

        if (extras != null) {
            CharSequence title = extras.getCharSequence(Notification.EXTRA_TITLE);
            if (title != null) textParts.add(title.toString().trim());

            CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);
            if (text != null) textParts.add(text.toString().trim());
        }

        if (notification.tickerText != null) {
            textParts.add(notification.tickerText.toString().trim());
        }

        StringJoiner joiner = new StringJoiner(" ");
        for (String part : textParts) {
            if (!part.isEmpty()) joiner.add(part);
        }
        return joiner.toString();
    }

    public static SGV generateSGVFromText(String textToParse, long timestamp) {
        if (textToParse == null) return null;

        // Pulizia testo per facilitare il riconoscimento
        String cleanText = textToParse.toUpperCase().replace(".", ",");

        // --- 1. GESTIONE LO / HI (Prioritaria) ---
        // Se Eversense dice LO, restituiamo 39.
        if (cleanText.contains(" LO ") || cleanText.equals("LO") || cleanText.startsWith("LO ") || cleanText.endsWith(" LO")) {
            return new SGV(39, timestamp, 0);
        }
        // Se Eversense dice HI, restituiamo 401.
        if (cleanText.contains(" HI ") || cleanText.equals("HI") || cleanText.startsWith("HI ") || cleanText.endsWith(" HI")) {
            return new SGV(401, timestamp, 0);
        }

        // --- 2. GESTIONE NUMERICA ---
        Matcher matcher = VALUE_PATTERN.matcher(textToParse);
        String valueString = null;
        if (matcher.find()) {
            valueString = matcher.group(1);
        }

        if (valueString == null) return null;

        int value;
        try {
            if (valueString.contains(",") || valueString.contains(".")) {
                value = SGV.Convert(Float.parseFloat(valueString.replace(",", ".")));
            } else {
                value = Integer.parseInt(valueString);
            }
        } catch (NumberFormatException e) {
            return null;
        }

        // --- 3. VALIDAZIONE RANGE ESTESO (30-500) ---
        // Accettiamo i valori estremi convertiti sopra, ma scartiamo gli errori (es. 0)
        if (value >= 30 && value <= 500) {
            return new SGV(value, timestamp, 0);
        } else {
            EselLog.LogW(TAG, "Valore " + value + " fuori range (30-500). Scartato.");
            return null;
        }
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        EselLog.LogI(TAG, "Notification Listener connesso e pronto.");
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        EselLog.LogW(TAG, "Notification Listener disconnesso!");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }
}