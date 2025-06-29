// ---------- CODICE DEFINITIVO CON RANGE DI LETTURA UFFICIALE (40-400) ----------
package esel.esel.esel.datareader;

import android.app.Notification;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import java.util.HashSet;
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

    private static String lastProcessedText = "";
    private static long lastProcessedTimeMs = 0;
    private static final long DEBOUNCE_WINDOW_MS = 10000;

    private static final Pattern VALUE_PATTERN = Pattern.compile("(?<!\\d:)\\b(\\d+([,.]\\d+)?)\\b(?!:\\d)");

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (SP.getBoolean("use_patched_es", false)) return;

        String packageName = sbn.getPackageName();
        if (packageName == null || !packageName.startsWith("com.senseonics")) return;

        Notification notification = sbn.getNotification();
        if (notification == null) return;

        String fullText = extractFullText(notification);
        if (fullText.isEmpty()) {
            EselLog.LogW(TAG, "Notifica ricevuta ma il testo è vuoto.");
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (fullText.equals(lastProcessedText) && (currentTime - lastProcessedTimeMs < DEBOUNCE_WINDOW_MS)) {
            EselLog.LogI(TAG, "Notifica duplicata ignorata (debounce): \"" + fullText + "\"");
            return;
        }
        lastProcessedText = fullText;
        lastProcessedTimeMs = currentTime;

        SGV sgv = generateSGVFromText(fullText, notification.when);
        if (sgv == null) {
            EselLog.LogW(TAG, "Nessun valore glicemico valido trovato nel testo: \"" + fullText + "\"");
            return;
        }

        SP.putString(KEY_LAST_SEEN_NOTIFICATION_TEXT, fullText);
        SP.putLong(KEY_LAST_SEEN_NOTIFICATION_WHEN, notification.when);

        EselLog.LogI(TAG, "Notifica valida (" + sgv.value + ") accettata. Invio broadcast locale al servizio.");
        Intent serviceIntent = new Intent(ACTION_NEW_SGV_DATA);
        serviceIntent.putExtra(EXTRA_SGV_DATA, sgv);
        LocalBroadcastManager.getInstance(this).sendBroadcast(serviceIntent);
    }

    private String extractFullText(Notification notification) {
        Set<String> textParts = new HashSet<>();
        Bundle extras = notification.extras;

        if (extras != null) {
            CharSequence titleChars = extras.getCharSequence(Notification.EXTRA_TITLE);
            if (titleChars != null && !titleChars.toString().trim().isEmpty()) {
                textParts.add(titleChars.toString().trim());
            }

            CharSequence textChars = extras.getCharSequence(Notification.EXTRA_TEXT);
            if (textChars != null && !textChars.toString().trim().isEmpty()) {
                textParts.add(textChars.toString().trim());
            }
        }

        CharSequence tickerChars = notification.tickerText;
        if (tickerChars != null && !tickerChars.toString().trim().isEmpty()) {
            textParts.add(tickerChars.toString().trim());
        }

        StringJoiner joiner = new StringJoiner(" ");
        for (String part : textParts) {
            joiner.add(part);
        }
        return joiner.toString();
    }


    public static SGV generateSGVFromText(String textToParse, long timestamp) {
        Matcher matcher = VALUE_PATTERN.matcher(textToParse);
        String valueString = null;
        if (matcher.find()) {
            valueString = matcher.group(1);
        }
        if (valueString == null) return null;
        int value;
        try {
            if (valueString.contains(".") || valueString.contains(",")) {
                value = SGV.Convert(Float.parseFloat(valueString.replace(",", ".")));
            } else {
                value = Integer.parseInt(valueString);
            }
        } catch (NumberFormatException e) {
            return null;
        }

        // --- FIX: Aggiornato al range ufficiale Eversense (40-400) ---
        if (value >= 40 && value <= 400) {
            return new SGV(value, timestamp, 0);
        } else {
            EselLog.LogW(TAG, "Valore estratto (" + value + ") fuori dal range ufficiale (40-400). Scartato.");
            return null;
        }
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        EselLog.LogI(TAG, "Notification Listener connesso.");
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        EselLog.LogW(TAG, "Notification Listener disconnesso.");
    }
}