// ---------------- INIZIO CODICE COMPLETO E MODIFICATO PER DataMonitorService.java ----------------
package esel.esel.esel.services;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import esel.esel.esel.R;
import esel.esel.esel.receivers.ReadReceiver;
import esel.esel.esel.util.EselLog;

public class DataMonitorService extends Service {

    private static final String TAG = "DataMonitorService";
    public static final String CHANNEL_ID = "EselMonitorChannel";
    public static final int NOTIFICATION_ID = 101;
    public static final int ALARM_REQUEST_CODE = 123; // Codice univoco per la nostra sveglia

    @Override
    public void onCreate() {
        super.onCreate();
        EselLog.LogI(TAG, "DataMonitorService onCreate. Preparazione del servizio in primo piano.");

        // La logica di creazione del canale e della notifica rimane identica
        createNotificationChannel();
        Notification notification = buildNotification();
        startForeground(NOTIFICATION_ID, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        EselLog.LogI(TAG, "DataMonitorService onStartCommand. Avvio e programmazione della sveglia periodica.");

        // MODIFICA: Qui programmiamo la sveglia invece di avviare un Handler
        scheduleAlarm();

        // START_STICKY è importante per far sì che il servizio riparta se il sistema lo chiude,
        // mantenendo la notifica persistente e ri-programmando la sveglia se necessario.
        return START_STICKY;
    }

    /**
     * NUOVO METODO: Programma una sveglia inesatta e ripetuta che attiverà il ReadReceiver.
     * Usiamo una sveglia "inesatta" perché è molto più efficiente a livello di batteria
     * e il sistema la eseguirà comunque molto vicino all'intervallo di 5 minuti.
     * RTC_WAKEUP assicura che il dispositivo si svegli per eseguire l'operazione.
     */
    private void scheduleAlarm() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, ReadReceiver.class);

        // FLAG_IMMUTABLE è obbligatorio per le versioni recenti di Android
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, ALARM_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        long interval = ReadReceiver.REPEAT_TIME; // Il nostro intervallo di 5 minuti

        // Cancella eventuali sveglie precedenti per evitare duplicati
        alarmManager.cancel(pendingIntent);

        // Programma la sveglia per partire subito e ripetersi ogni 5 minuti
        alarmManager.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime(), // Parte subito
                interval,
                pendingIntent
        );

        EselLog.LogI(TAG, "Sveglia programmata con successo. Intervallo: " + interval / 1000 + " secondi.");
    }

    /**
     * NUOVO METODO: Cancella la sveglia quando il servizio viene fermato intenzionalmente.
     */
    private void cancelAlarm() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, ReadReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, ALARM_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        alarmManager.cancel(pendingIntent);
        EselLog.LogI(TAG, "Sveglia cancellata.");
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // Non usiamo il binding, quindi restituiamo null
        return null;
    }

    @Override
    public void onDestroy() {
        EselLog.LogI(TAG, "DataMonitorService onDestroy. Fermo il servizio e cancello la sveglia.");

        // MODIFICA: Cancelliamo la sveglia per non avere esecuzioni "fantasma"
        cancelAlarm();

        super.onDestroy();
    }

    // I metodi per la creazione della notifica restano invariati
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            serviceChannel.setDescription(getString(R.string.channel_description));
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private Notification buildNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_content))
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)
                .build();
    }
}
// ---------------- FINE CODICE COMPLETO E MODIFICATO PER DataMonitorService.java ----------------