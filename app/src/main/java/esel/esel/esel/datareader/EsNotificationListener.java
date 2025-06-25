// ---------- CODICE FINALE CON PARSING A PROVA DI ORARIO ----------
package esel.esel.esel.datareader;

import android.app.Notification;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import androidx.core.content.ContextCompat;

import java.util.regex.Matcher; // NUOVO IMPORT
import java.util.regex.Pattern; // NUOVO IMPORT

import esel.esel.esel.services.DataMonitorService;
import esel.esel.esel.util.EselLog;
import esel.esel.esel.util.SP;

public class EsNotificationListener extends NotificationListenerService {
    private static final String TAG = "EsNotificationListener";
    private static final long COOLDOWN_PERIOD_MS = (5 * 60 * 1000L) - 10000L; // 4 minuti e 50 secondi

    // --- MODIFICA 1: Definiamo il nostro "cercatore" intelligente ---
    // Cerca un numero (intero o decimale) ma solo se NON fa parte di un orario.
    private static final Pattern VALUE_PATTERN = Pattern.compile("(?<!\\d:)\\b(\\d+([,.]\\d+)?)\\b(?!:\\d)");

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (SP.getBoolean("use_patched_es", false)) return;

        String packageName = sbn.getPackageName();
        if (packageName == null || !packageName.startsWith("com.senseonics")) return;

        Notification notification = sbn.getNotification();
        if (notification == null) return;

        // --- REGOLA #1: CONTROLLO DELLA FINESTRA TEMPORALE ---
        long lastSuccessfulSendTime = SP.getLong("lastSuccessfulSendTime", 0L);
        long timeSinceLastSend = System.currentTimeMillis() - lastSuccessfulSendTime;
        if (timeSinceLastSend < COOLDOWN_PERIOD_MS) {
            EselLog.LogI(TAG, "Notifica ignorata (Finestra Temporale Chiusa): " + (timeSinceLastSend / 1000) + "s dall'ultimo invio.");
            return;
        }

        // --- REGOLA #2: CONTROLLO DEL TIMESTAMP PER ANTI-SPAM ---
        long lastNotificationTimestamp = SP.getLong("lastNotificationTimestamp", 0L);
        if (notification.when == lastNotificationTimestamp) {
            EselLog.LogI(TAG, "Notifica ignorata (Timestamp Duplicato): " + notification.when);
            return;
        }

        // --- CONTROLLI SUPERATI: QUESTO È UN DATO VALIDO ---

        SGV sgv = generateSGVFromNotification(notification);
        // MODIFICA: Il controllo ora è solo sul fatto che sgv non sia null
        if (sgv == null) {
            EselLog.LogW(TAG, "Nessun valore glicemico valido trovato nella notifica.");
            return;
        }

        // "Blocchiamo la porta" immediatamente per prevenire invii multipli.
        long now = System.currentTimeMillis();
        SP.putLong("lastSuccessfulSendTime", now);
        SP.putLong("lastNotificationTimestamp", notification.when);

        EselLog.LogI(TAG, "Notifica valida (" + sgv.value + ") accettata. Blocco lo slot e avvio il servizio.");
        Intent serviceIntent = new Intent(this, DataMonitorService.class);
        serviceIntent.putExtra("sgv_data", sgv);
        ContextCompat.startForegroundService(this, serviceIntent);
    }

    // --- MODIFICA 2: Sostituiamo il vecchio metodo di estrazione con quello nuovo e robusto ---
    private SGV generateSGVFromNotification(Notification notification) {
        String fullText = "";
        Bundle extras = notification.extras;
        // Tentativo #1: campo EXTRA_TEXT
        if (extras != null) {
            CharSequence textChars = extras.getCharSequence(Notification.EXTRA_TEXT);
            if (textChars != null) fullText += textChars.toString() + " ";
        }
        // Tentativo #2: campo tickerText (fallback)
        CharSequence tickerChars = notification.tickerText;
        if (tickerChars != null) fullText += tickerChars.toString();

        if (fullText.trim().isEmpty()) return null;

        EselLog.LogI(TAG, "Testo notifica da analizzare: \"" + fullText + "\"");

        Matcher matcher = VALUE_PATTERN.matcher(fullText);
        String valueString = null;

        // Cerchiamo la prima corrispondenza che il nostro pattern intelligente trova
        if (matcher.find()) {
            valueString = matcher.group(1); // group(1) per prendere solo il numero
            EselLog.LogI(TAG, "Valore numerico estratto: " + valueString);
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

        // Ultimo controllo di plausibilità sul risultato
        if (value >= 20 && value <= 600) {
            return new SGV(value, notification.when, 0);
        } else {
            EselLog.LogW(TAG, "Valore estratto (" + value + ") fuori dal range plausibile (20-600). Scartato.");
            return null;
        }
    }

    @Override public void onListenerConnected() { super.onListenerConnected(); EselLog.LogI(TAG, "Notification Listener connesso."); }
    @Override public void onListenerDisconnected() { super.onListenerDisconnected(); EselLog.LogW(TAG, "Notification Listener disconnesso."); }
}