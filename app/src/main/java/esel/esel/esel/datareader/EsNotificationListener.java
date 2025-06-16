// ---------------- INIZIO CODICE COMPLETO E MODIFICATO PER EsNotificationListener.java ----------------
package esel.esel.esel.datareader;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.List;

import esel.esel.esel.util.EselLog;
import esel.esel.esel.util.SP;

/**
 * Servizio che ascolta le notifiche di sistema, specificamente quelle dell'app Eversense,
 * per catturare i dati della glicemia in tempo reale.
 * La logica è stata modernizzata per usare notification.extras invece del tickerText obsoleto.
 */
public class EsNotificationListener extends NotificationListenerService {

    private static final String TAG = "EsNotificationListener";

    // Unico punto di stato: l'ultimo valore SGV catturato dalla notifica e non ancora processato.
    // 'volatile' assicura che le modifiche siano visibili a tutti i thread.
    private static volatile SGV latestStoredSgv = null;

    // Timestamp dell'ultimo valore che è stato inviato ai broadcaster (es. ad AAPS).
    private static volatile long lastSentToApsTimestamp = 0;

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        // Ignoriamo tutto se siamo in modalità "Patched", che usa un altro meccanismo.
        if (SP.getBoolean("use_patched_es", false)) {
            return;
        }

        // Controlliamo se la notifica proviene da uno dei package dell'app Eversense.
        String packageName = sbn.getPackageName();
        if (packageName != null && packageName.startsWith("com.senseonics")) {
            Notification notification = sbn.getNotification();
            if (notification == null) {
                EselLog.LogV(TAG, "Notifica nulla ricevuta da: " + packageName);
                return;
            }

            // Estraiamo il valore SGV dalla notifica usando il metodo moderno e affidabile.
            SGV sgv = generateSGVFromNotification(notification);

            if (sgv != null) {
                EselLog.LogI(TAG, "SGV estratto con successo: " + sgv.value + " @ " + sgv.timestamp);

                // Usiamo un blocco synchronized per aggiornare le variabili statiche in modo sicuro.
                synchronized (EsNotificationListener.class) {
                    // Verifichiamo se il valore è nuovo e valido prima di memorizzarlo.
                    if (latestStoredSgv == null || sgv.timestamp > latestStoredSgv.timestamp) {
                        if (sgv.timestamp > lastSentToApsTimestamp) {
                            latestStoredSgv = sgv;
                            EselLog.LogI(TAG, "Nuovo SGV memorizzato. In attesa di essere prelevato da ReadReceiver.");
                        } else {
                            EselLog.LogW(TAG, "SGV scartato perché il suo timestamp (" + sgv.timestamp + ") è <= all'ultimo inviato (" + lastSentToApsTimestamp + ").");
                        }
                    } else {
                        EselLog.LogW(TAG, "SGV scartato perché il suo timestamp (" + sgv.timestamp + ") non è più recente di quello già memorizzato (" + latestStoredSgv.timestamp + ").");
                    }
                }
            }
        }
    }

    /**
     * Metodo chiamato dal nostro ReadReceiver per prelevare l'ultimo dato disponibile.
     * Questo disaccoppia la ricezione dalla processazione.
     */
    public static List<SGV> getData(int number, long lastReadingTime) {
        List<SGV> result = new ArrayList<>();
        SGV sgvToSend = null;

        synchronized (EsNotificationListener.class) {
            if (latestStoredSgv != null) {
                // Forniamo il dato solo se è più recente sia dell'ultima lettura processata,
                // sia dell'ultimo dato già inviato ad AAPS/xDrip.
                if (latestStoredSgv.timestamp > lastReadingTime && latestStoredSgv.timestamp > lastSentToApsTimestamp) {
                    sgvToSend = latestStoredSgv;
                    EselLog.LogI(TAG, "getData fornisce un nuovo SGV a ReadReceiver: " + sgvToSend.value + " @ " + sgvToSend.timestamp);
                    // Una volta "prelevato", lo nullifichiamo per non inviarlo di nuovo.
                    latestStoredSgv = null;
                }
            }
        }

        if (sgvToSend != null) {
            result.add(sgvToSend);
        }

        return result;
    }

    /**
     * Metodo per aggiornare il timestamp dell'ultimo invio, chiamato da ReadReceiver.
     */
    public static void setLastSentToApsTimestamp(long timestamp) {
        synchronized (EsNotificationListener.class) {
            // Assicuriamoci di non andare mai indietro nel tempo
            if(timestamp > lastSentToApsTimestamp) {
                lastSentToApsTimestamp = timestamp;
                EselLog.LogI(TAG, "Timestamp dell'ultimo invio aggiornato a: " + timestamp);
            }
        }
    }

    /**
     * NUOVA FUNZIONE MODERNA: Estrae il valore SGV dagli "extras" della notifica.
     * Questo è il metodo robusto per le versioni recenti di Android.
     */
    private SGV generateSGVFromNotification(Notification notification) {
        Bundle extras = notification.extras;
        if (extras == null) {
            EselLog.LogW(TAG, "Notification.extras è nullo.");
            return null;
        }

        // Il testo principale della notifica è il candidato più probabile per il valore BG.
        CharSequence textChars = extras.getCharSequence(Notification.EXTRA_TEXT);
        if (textChars == null) {
            EselLog.LogW(TAG, "Notification.EXTRA_TEXT non trovato negli extras.");
            return null;
        }

        String text = textChars.toString();
        // Spesso il valore è la prima "parola" del testo. Es. "123 mg/dL →"
        String[] parts = text.split(" ");
        if (parts.length == 0) {
            EselLog.LogW(TAG, "Testo della notifica vuoto: '" + text + "'");
            return null;
        }

        String valueString = parts[0];
        int value;

        try {
            // Pulizia e conversione del valore
            if (valueString.contains(".") || valueString.contains(",")) {
                String formattedValue = valueString.replace(",", ".");
                float valuef = Float.parseFloat(formattedValue);
                value = SGV.Convert(valuef); // Converte da mmol/L a mg/dL se necessario
            } else {
                value = Integer.parseInt(valueString);
            }
        } catch (NumberFormatException e) {
            EselLog.LogE(TAG, "Impossibile convertire il valore dalla notifica. Testo: '" + text + "', Errore: " + e.getMessage());
            return null;
        }

        return new SGV(value, notification.when, 0);
    }

    // Metodi di lifecycle standard del servizio
    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        EselLog.LogI(TAG, "Notification Listener connesso e operativo.");
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        EselLog.LogW(TAG, "Notification Listener disconnesso!");
    }
}
// ---------------- FINE CODICE COMPLETO E MODIFICATO PER EsNotificationListener.java ----------------