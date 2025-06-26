// ---------- CODICE FINALE CON FILTRO BASATO SUL CONTENUTO ----------
package esel.esel.esel.datareader;

import android.app.Notification;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import androidx.core.content.ContextCompat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import esel.esel.esel.services.DataMonitorService;
import esel.esel.esel.util.EselLog;
import esel.esel.esel.util.SP;

public class EsNotificationListener extends NotificationListenerService {
    private static final String TAG = "EsNotificationListener";
    private static final long COOLDOWN_PERIOD_MS = (5 * 60 * 1000L) - 10000L;
    private static final Pattern VALUE_PATTERN = Pattern.compile("(?<!\\d:)\\b(\\d+([,.]\\d+)?)\\b(?!:\\d)");

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (SP.getBoolean("use_patched_es", false)) return;

        String packageName = sbn.getPackageName();
        if (packageName == null || !packageName.startsWith("com.senseonics")) return;

        Notification notification = sbn.getNotification();
        if (notification == null) return;

        // --- REGOLA #1: CONTROLLO DELLA FINESTRA TEMPORALE (INVARIATO) ---
        long lastSuccessfulSendTime = SP.getLong("lastSuccessfulSendTime", 0L);
        long timeSinceLastSend = System.currentTimeMillis() - lastSuccessfulSendTime;
        if (timeSinceLastSend < COOLDOWN_PERIOD_MS) {
            return; // Non logghiamo per non riempire i log con notifiche vicine
        }

        // --- MODIFICA CHIAVE: Estraiamo il testo e lo usiamo per il controllo anti-spam ---
        String fullText = extractFullText(notification);
        if (fullText.isEmpty()) {
            EselLog.LogW(TAG, "Notifica ricevuta ma senza testo estraibile.");
            return;
        }

        // --- REGOLA #2: NUOVO CONTROLLO ANTI-SPAM BASATO SUL CONTENUTO ---
        String lastProcessedText = SP.getString("last_processed_notification_text", "");
        if (fullText.equals(lastProcessedText)) {
            EselLog.LogI(TAG, "Notifica ignorata (Contenuto Duplicato): \"" + fullText + "\"");
            return;
        }

        // --- CONTROLLI SUPERATI: QUESTO È UN DATO VALIDO ---
        SGV sgv = generateSGVFromText(fullText, notification.when);
        if (sgv == null) {
            EselLog.LogW(TAG, "Nessun valore glicemico valido trovato nel testo: \"" + fullText + "\"");
            return;
        }

        // "Blocchiamo la porta" immediatamente salvando lo stato
        SP.putLong("lastSuccessfulSendTime", System.currentTimeMillis());
        SP.putString("last_processed_notification_text", fullText); // Salviamo il testo di questa notifica

        EselLog.LogI(TAG, "Notifica valida (" + sgv.value + ") accettata. Avvio il servizio.");
        Intent serviceIntent = new Intent(this, DataMonitorService.class);
        serviceIntent.putExtra("sgv_data", sgv);
        ContextCompat.startForegroundService(this, serviceIntent);
    }

    // --- NUOVO METODO HELPER PER ESTRARRE IL TESTO ---
    private String extractFullText(Notification notification) {
        String text = "";
        Bundle extras = notification.extras;
        // Tentativo #1: campo EXTRA_TEXT
        if (extras != null) {
            CharSequence textChars = extras.getCharSequence(Notification.EXTRA_TEXT);
            if (textChars != null) text += textChars.toString() + " ";
        }
        // Tentativo #2: campo tickerText (fallback)
        CharSequence tickerChars = notification.tickerText;
        if (tickerChars != null) text += tickerChars.toString();

        return text.trim();
    }

    // --- METODO DI ESTRAZIONE MODIFICATO PER PRENDERE IL TESTO COME INPUT ---
    private SGV generateSGVFromText(String textToParse, long timestamp) {
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

        if (value >= 20 && value <= 600) {
            return new SGV(value, timestamp, 0);
        } else {
            EselLog.LogW(TAG, "Valore estratto (" + value + ") fuori dal range plausibile (20-600). Scartato.");
            return null;
        }
    }

    @Override public void onListenerConnected() { super.onListenerConnected(); EselLog.LogI(TAG, "Notification Listener connesso."); }
    @Override public void onListenerDisconnected() { super.onListenerDisconnected(); EselLog.LogW(TAG, "Notification Listener disconnesso."); }
}