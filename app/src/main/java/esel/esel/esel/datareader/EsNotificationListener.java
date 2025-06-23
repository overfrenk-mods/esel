// ---------- CODICE FINALE CON FIX PER RACE CONDITION ----------
package esel.esel.esel.datareader;

import android.app.Notification;
import android.content.Intent;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import androidx.core.content.ContextCompat;
import esel.esel.esel.services.DataMonitorService;
import esel.esel.esel.util.EselLog;
import esel.esel.esel.util.SP;

public class EsNotificationListener extends NotificationListenerService {
    private static final String TAG = "EsNotificationListener";
    private static final long COOLDOWN_PERIOD_MS = (5 * 60 * 1000L) - 10000L; // 4 minuti e 50 secondi

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
        if (sgv == null || sgv.value < 39) {
            EselLog.LogW(TAG, "SGV non valido o troppo basso. Ignorato.");
            return;
        }

        // ----- LA CORREZIONE È QUI! -----
        // "Blocchiamo la porta" immediatamente per prevenire invii multipli.
        // Diciamo subito che stiamo per fare un invio ORA.
        long now = System.currentTimeMillis();
        SP.putLong("lastSuccessfulSendTime", now);
        SP.putLong("lastNotificationTimestamp", notification.when);
        // --------------------------------

        EselLog.LogI(TAG, "Notifica valida (" + sgv.value + ") accettata. Blocco lo slot e avvio il servizio.");
        Intent serviceIntent = new Intent(this, DataMonitorService.class);
        serviceIntent.putExtra("sgv_data", sgv);
        ContextCompat.startForegroundService(this, serviceIntent);
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