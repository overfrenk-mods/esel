package esel.esel.esel.datareader;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Comparator;

import esel.esel.esel.util.EselLog;
import esel.esel.esel.util.SP;

/**
 * Creato da OverFrenK il 24-02-24.
 */
public class EsNotificationListener extends NotificationListenerService {

    private static SGV latestStoredSgv = null;
    private static long lastSentToApsTimestamp = 0;

    private static final String TAG = "EsNotificationListener";

    private static final long FIVE_MINUTES_MS = 5 * 60 * 1000L;
    private static final long SYNC_TOLERANCE_MS = 10 * 1000L;
    private static final long SYNC_GAP_THRESHOLD_MS = 10 * 60 * 1000L;

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        EselLog.LogI(TAG, "onNotificationPosted: Notifica ricevuta da package: " + sbn.getPackageName());

        if (SP.getBoolean("use_patched_es", false)) {
            EselLog.LogV(TAG, "Notifica ricevuta ma modalità patch attiva, ignoro.");
            return;
        }

        if (sbn.getPackageName().equals("com.senseonics.gen12androidapp") ||
                sbn.getPackageName().equals("com.senseonics.androidapp") ||
                sbn.getPackageName().equals("com.senseonics.eversense365.us") ||
                sbn.getPackageName().contains("com.senseonics.")) {
            Notification notification = sbn.getNotification();
            if (notification != null && notification.tickerText != null) {
                try {
                    long currentNotificationTimestamp = notification.when;

                    long lastEversenseDataTimestamp = SP.getLong("last_eversense_data_timestamp", 0L);

                    boolean isValidTimeSlot = false;

                    if (lastEversenseDataTimestamp == 0L || (currentNotificationTimestamp - lastEversenseDataTimestamp) >= FIVE_MINUTES_MS) {
                        isValidTimeSlot = true;
                        EselLog.LogI(TAG, "Notifica in nuovo slot 5min o primo allineamento. Timestamp: " + currentNotificationTimestamp);
                    } else {
                        long timeSinceLastSlot = currentNotificationTimestamp - lastEversenseDataTimestamp;
                        if (timeSinceLastSlot < FIVE_MINUTES_MS) {
                            EselLog.LogV(TAG, "Notifica ricevuta nello stesso slot di 5 minuti. Scarto. Attuale: " + currentNotificationTimestamp + ", Ultimo Slot: " + lastEversenseDataTimestamp);
                        } else {
                            EselLog.LogW(TAG, "Notifica inaspettata. Scarto. Attuale: " + currentNotificationTimestamp + ", Ultimo Slot: " + lastEversenseDataTimestamp);
                        }
                    }

                    if (isValidTimeSlot) {
                        if (latestStoredSgv != null && currentNotificationTimestamp <= latestStoredSgv.timestamp) {
                            EselLog.LogW(TAG, "Salto notifica più vecchia di quella già memorizzata. Attuale: " + currentNotificationTimestamp + ", Memorizzata: " + latestStoredSgv.timestamp);
                            return;
                        }

                        if (currentNotificationTimestamp <= lastSentToApsTimestamp) {
                            EselLog.LogW(TAG, "Salto notifica con timestamp <= ultimo inviato ad APS. Attuale: " + currentNotificationTimestamp + ", Ultimo inviato: " + lastSentToApsTimestamp);
                            return;
                        }

                        EselLog.LogI(TAG, "Processo notifica Eversense valida per SGV: " + notification.tickerText);
                        SGV sgv = generateSGV(notification, 0);
                        if (sgv != null) {
                            synchronized (EsNotificationListener.class) {
                                latestStoredSgv = sgv;
                            }
                            SP.putLong("last_eversense_data_timestamp", sgv.timestamp);
                            EselLog.LogI(TAG, "latestStoredSgv aggiornato: " + sgv.value + " a " + sgv.timestamp);
                        } else {
                            EselLog.LogW(TAG, "generateSGV ha restituito null per la notifica: " + notification.tickerText);
                        }
                    }

                } catch (NumberFormatException err) {
                    EselLog.LogE(TAG, "NumberFormatException in onNotificationPosted per tickerText: " + notification.tickerText + ", Errore: " + err.getMessage());
                } catch (Exception e) {
                    EselLog.LogE(TAG, "Eccezione generica in onNotificationPosted: " + e.getMessage());
                }
            } else {
                EselLog.LogV(TAG, "Notifica o tickerText è nullo per il package: " + sbn.getPackageName());
            }
        } else {
            EselLog.LogV(TAG, "Salto la notifica da un package non-Eversense: " + sbn.getPackageName());
        }
    }

    @Override
    public void onListenerConnected() {
        EselLog.LogI(TAG, "Notification Listener connesso!");
    }

    @Override
    public void onListenerDisconnected() {
        EselLog.LogW(TAG, "Notification Listener disconnesso!");
    }

    public static List<SGV> getData(int number, long lastReadingTime) {
        List<SGV> result = new ArrayList<>();
        SGV sgvToSend = null;

        synchronized (EsNotificationListener.class) {
            if (latestStoredSgv != null) {
                if (latestStoredSgv.timestamp > lastReadingTime && latestStoredSgv.timestamp > lastSentToApsTimestamp) {
                    sgvToSend = latestStoredSgv;
                    EselLog.LogI(TAG, "getData ha trovato un nuovo SGV più fresco da inviare: " + sgvToSend.value + " a " + sgvToSend.timestamp);
                    latestStoredSgv = null;
                } else {
                    EselLog.LogV(TAG, "getData ha trovato un SGV ma non è più fresco o è già stato inviato. Salto. Valore: " + latestSgv.value + ", Timestamp: " + latestSgv.timestamp);
                    latestStoredSgv = null;
                }
            }
        }

        if (sgvToSend != null) {
            result.add(sgvToSend);
        } else {
            EselLog.LogV(TAG, "getData restituisce lista vuota (nessun nuovo SGV da inviare).");
        }

        return result;
    }

    public static void setLastSentToApsTimestamp(long timestamp) {
        lastSentToApsTimestamp = timestamp;
        EselLog.LogI(TAG, "lastSentToApsTimestamp aggiornato a: " + timestamp);
    }

    public static SGV generateSGV(Notification notification, int record) {
        long timestamp = notification.when;
        CharSequence tickerTextCharSeq = notification.tickerText;
        if (tickerTextCharSeq == null) {
            EselLog.LogE(TAG, "Ticker text è nullo per la notifica con timestamp: " + timestamp);
            return null;
        }
        String tickerText = tickerTextCharSeq.toString();

        int value;
        try {
            if (tickerText.contains(".") || tickerText.contains(",")) {
                String formattedTickerText = tickerText.replace(",", ".");
                float valuef = Float.parseFloat(formattedTickerText);
                value = SGV.Convert(valuef);
            } else {
                value = Integer.parseInt(tickerText);
            }
        } catch (NumberFormatException e) {
            EselLog.LogE(TAG, "NumberFormatException in generateSGV per tickerText: '" + tickerText + "', Errore: " + e.getMessage());
            return null;
        }

        // ********************************************************************************
        // INIZIO BLOCCO COMMENTATO (Rimosso per via della gestione con latestStoredSgv)
        // ********************************************************************************
        /*
        if (lastReadings.size() > 0) { // lastReadings non è più una lista accumulativa in questo file
            long five_min = 300000l;
            SGV oldSgv = null;
            synchronized (lastReadings) {
                if (!lastReadings.isEmpty()) {
                    oldSgv = lastReadings.get(lastReadings.size() - 1);
                }
            }
            if(oldSgv != null) {
                long lastreadingtime = oldSgv.timestamp;
                int lastreadingvalue = oldSgv.raw;
                if (value == lastreadingvalue && (timestamp - lastreadingtime) < (FIVE_MINUTES_MS * 0.9) ) {
                    EselLog.LogW(TAG, "Salto SGV duplicato o troppo frequente (in generateSGV): " + value + " a " + timestamp);
                    return null;
                }
            }
        }
        */
        // ********************************************************************************
        // FINE BLOCCO COMMENTATO
        // ********************************************************************************

        return new SGV(value, timestamp, record);
    }
}