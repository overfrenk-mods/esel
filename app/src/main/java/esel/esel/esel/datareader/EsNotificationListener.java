// ---------- CODICE FINALE E DEFINITIVO CON FILTRO TEMPORALE PURO ----------
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
    private static final long LONG_PAUSE_THRESHOLD_MS = 15 * 60 * 1000L;
    private static final Pattern VALUE_PATTERN = Pattern.compile("(?<!\\d:)\\b(\\d+([,.]\\d+)?)\\b(?!:\\d)");

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (SP.getBoolean("use_patched_es", false)) return;

        String packageName = sbn.getPackageName();
        if (packageName == null || !packageName.startsWith("com.senseonics")) return;

        Notification notification = sbn.getNotification();
        if (notification == null) return;

        // --- GESTIONE DELLE LUNGHE PAUSE (RICARICA/RIAVVIO) ---
        long lastSuccessfulSendTime = SP.getLong("lastSuccessfulSendTime", 0L);
        long timeSinceLastSend = System.currentTimeMillis() - lastSuccessfulSendTime;

        if (lastSuccessfulSendTime > 0 && timeSinceLastSend > LONG_PAUSE_THRESHOLD_MS) {
            SP.putLong("lastSuccessfulSendTime", System.currentTimeMillis()); // Reset immediato del cooldown
            EselLog.LogW(TAG, "Lunga pausa rilevata (" + (timeSinceLastSend / 60000) + " min). Salto la prima notifica per risincronizzare.");
            return;
        }

        // --- UNICO E SOLO CONTROLLO: IL COOLDOWN TEMPORALE ---
        if (timeSinceLastSend < COOLDOWN_PERIOD_MS) {
            long timeLeft = COOLDOWN_PERIOD_MS - timeSinceLastSend;
            // Logga un messaggio più utile per il debug
            EselLog.LogI(TAG, "Notifica ignorata per cooldown. Mancavano " + (timeLeft / 1000) + " secondi.");
            return; // Ignora notifiche troppo ravvicinate
        }

        // --- CONTROLLI SUPERATI: QUESTO È UN DATO VALIDO ---
        String fullText = extractFullText(notification);
        if (fullText.isEmpty()) {
            EselLog.LogW(TAG, "Notifica ricevuta ma il testo è vuoto.");
            return;
        }

        SGV sgv = generateSGVFromText(fullText, notification.when);
        if (sgv == null) {
            EselLog.LogW(TAG, "Nessun valore glicemico valido trovato nel testo: \"" + fullText + "\"");
            return;
        }

        // "Blocchiamo la porta" salvando l'ora di questo invio
        SP.putLong("lastSuccessfulSendTime", System.currentTimeMillis());

        EselLog.LogI(TAG, "Notifica valida (" + sgv.value + ") accettata. Avvio il servizio.");
        Intent serviceIntent = new Intent(this, DataMonitorService.class);
        serviceIntent.putExtra("sgv_data", sgv);
        ContextCompat.startForegroundService(this, serviceIntent);
    }

    private String extractFullText(Notification notification) {
        String text = "";
        Bundle extras = notification.extras;
        if (extras != null) {
            CharSequence textChars = extras.getCharSequence(Notification.EXTRA_TEXT);
            if (textChars != null) text += textChars.toString() + " ";
        }
        CharSequence tickerChars = notification.tickerText;
        if (tickerChars != null) text += tickerChars.toString();
        return text.trim();
    }

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
    @Override public void onListenerDisconnected() { super.onListenerDisconnected(); EselLog.LogW(TAG, "Notification Listener disconnesso.");
    }
}