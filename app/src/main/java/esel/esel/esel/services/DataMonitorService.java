// ---------- CODICE VERSIONE "FORCE TIME" + NOTIFICA UNICA DETTAGLIATA ----------
package esel.esel.esel.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import esel.esel.esel.MainActivity;
import esel.esel.esel.R;
import esel.esel.esel.datareader.SGV;
import esel.esel.esel.datareader.EsNotificationListener;
import esel.esel.esel.receivers.FastPatrolReceiver;
import esel.esel.esel.receivers.ServiceRestarter;
import esel.esel.esel.receivers.WatchdogReceiver;
import esel.esel.esel.util.AapsSender;
import esel.esel.esel.util.EselLog;
import esel.esel.esel.util.SP;

public class DataMonitorService extends Service {
    private static final String TAG = "DataMonitorService";
    public static final String CHANNEL_ID = "EselMonitorChannel";
    public static final int NOTIFICATION_ID = 101;
    public static final String ACTION_STOP_SERVICE = "esel.esel.esel.ACTION_STOP_SERVICE";
    public static final String ACTION_MANUAL_SYNC = "esel.esel.esel.ACTION_MANUAL_SYNC";
    public static final String ACTION_REQUEST_SGV_READ = "esel.esel.esel.ACTION_REQUEST_SGV_READ";
    public static final int WATCHDOG_REQUEST_CODE = 901;

    public static final String KEY_LAST_SUCCESSFUL_SEND_MS = "status_last_successful_send_ms";
    public static final String KEY_LAST_SGV_TIMESTAMP = "status_last_sgv_timestamp";
    public static final String KEY_LAST_SGV_RAW_VALUE = "status_last_sgv_raw_value";
    public static final String KEY_LAST_SGV_FINAL_VALUE = "status_last_sgv_final_value";
    public static final String KEY_SGV_HISTORY_JSON = "sgv_history_json";

    private static final long TIMESTAMP_COOLDOWN_MS = 4 * 60 * 1000L;
    private static final long LONG_PAUSE_THRESHOLD_MS = 15 * 60 * 1000L;
    private static final long INITIAL_TRIGGER_DELAY_MS = 15 * 1000L;

    private ExecutorService executor;
    private BroadcastReceiver sgvDataReceiver;
    private PowerManager.WakeLock wakeLock;
    private Gson gson;

    private Handler syncTriggerHandler;
    private Runnable syncTriggerRunnable;

    // Variabile per mostrare lo stato dello smoothing nella notifica
    private String currentSmoothingStatus = "OFF";

    public static class SgvHistoryPoint {
        public long timestamp;
        public int value;

        public SgvHistoryPoint(long timestamp, int value) {
            this.timestamp = timestamp;
            this.value = value;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        EselLog.LogI(TAG, "DataMonitorService onCreate.");

        // --- MODIFICA PER NOTIFICA UNICA ---
        // Ho commentato l'avvio del CompanionService per eliminare la seconda icona fastidiosa.
        /*
        try {
            Intent companionIntent = new Intent(this, CompanionService.class);
            ContextCompat.startForegroundService(this, companionIntent);
            EselLog.LogW(TAG, "Servizio Compagno avviato.");
        } catch (Exception e) {
            EselLog.LogE(TAG, "Impossibile avviare il CompanionService: " + e.getMessage());
        }
        */

        executor = Executors.newSingleThreadExecutor();
        gson = new Gson();
        syncTriggerHandler = new Handler(Looper.getMainLooper());

        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EselReader::DataProcessingWakeLock");
        } else {
            EselLog.LogE(TAG, "PowerManager non disponibile!");
            wakeLock = null;
        }

        createNotificationChannel();
        setupSgvDataReceiver();

        WatchdogReceiver.scheduleNextWatchdog(this);
        FastPatrolReceiver.schedule(this);

        startSyncTrigger();

        SP.putBoolean("service_should_be_running", true);
        Notification notification = buildNotification(getString(R.string.notification_persistent_text_waiting));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } catch (Exception e) {
                EselLog.LogE(TAG, "Errore startForeground con tipo: " + e.getMessage() + ". Tento senza tipo.");
                try {
                    startForeground(NOTIFICATION_ID, notification);
                } catch (Exception e2) {
                    EselLog.LogE(TAG, "Errore startForeground senza tipo: " + e2.getMessage());
                }
            }
        } else {
            try {
                startForeground(NOTIFICATION_ID, notification);
            } catch (Exception e) {
                EselLog.LogE(TAG, "Errore startForeground pre-Q: " + e.getMessage());
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if (ACTION_STOP_SERVICE.equals(intent.getAction())) {
                EselLog.LogW(TAG, "Azione STOP ricevuta. Termino volontariamente il servizio.");
                stopSelfService();
                return START_NOT_STICKY;
            }
            if (ACTION_MANUAL_SYNC.equals(intent.getAction())) {
                EselLog.LogW(TAG, "RICEVUTO COMANDO DI SYNC MANUALE!");
                if (intent.hasExtra(EsNotificationListener.EXTRA_SGV_DATA)) {
                    final SGV sgv = (SGV) intent.getSerializableExtra(EsNotificationListener.EXTRA_SGV_DATA);
                    if (sgv != null) {
                        executor.execute(() -> processSgv(sgv, true));
                    }
                }
            }
        }
        return START_STICKY;
    }

    private void setupSgvDataReceiver() {
        sgvDataReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null && intent.hasExtra(EsNotificationListener.EXTRA_SGV_DATA)) {
                    final SGV sgv = (SGV) intent.getSerializableExtra(EsNotificationListener.EXTRA_SGV_DATA);
                    if (sgv != null) {
                        executor.execute(() -> processSgv(sgv, false));
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter(EsNotificationListener.ACTION_NEW_SGV_DATA);
        LocalBroadcastManager.getInstance(this).registerReceiver(sgvDataReceiver, filter);
    }

    private void processSgv(SGV sgv, boolean isManualOverride) {
        if (wakeLock != null && !wakeLock.isHeld()) {
            try {
                wakeLock.acquire(20 * 1000L);
                EselLog.LogW(TAG, "WakeLock acquisito per l'elaborazione dei dati.");
            } catch (Exception e) {
                EselLog.LogE(TAG, "Errore acquisizione WakeLock: " + e.getMessage());
            }
        }

        try {
            int rawValue = sgv.raw;
            long originalNotificationTime = sgv.timestamp;
            sgv.timestamp = System.currentTimeMillis();
            EselLog.LogI(TAG, "[FORCE TIME] Timestamp notifica (" + new Date(originalNotificationTime) + ") ignorato. Usato orario sistema: " + new Date(sgv.timestamp));

            if (!isManualOverride) {
                long lastSgvTimestamp = SP.getLong(KEY_LAST_SGV_TIMESTAMP, 0L);
                long timeSinceLastSgv = sgv.timestamp - lastSgvTimestamp;

                if (lastSgvTimestamp > 0 && timeSinceLastSgv > LONG_PAUSE_THRESHOLD_MS) {
                    EselLog.LogW(TAG, "Rilevata lunga pausa di " + (timeSinceLastSgv / 60000) + " min. Resetto stato pendenza e smoothing.");
                    SP.putLong(KEY_LAST_SGV_TIMESTAMP, 0L);
                    SP.putInt(KEY_LAST_SGV_RAW_VALUE, -1);
                    SP.putInt(KEY_LAST_SGV_FINAL_VALUE, -1);
                    lastSgvTimestamp = 0L;
                    timeSinceLastSgv = Long.MAX_VALUE;
                }

                if (lastSgvTimestamp > 0 && timeSinceLastSgv < TIMESTAMP_COOLDOWN_MS) {
                    EselLog.LogI(TAG, "[FILTRO RAFFICA] Dato scartato per cooldown. Intervallo: " + (timeSinceLastSgv / 1000) + "s < " + (TIMESTAMP_COOLDOWN_MS / 1000) + "s");
                    return;
                }

            } else {
                EselLog.LogW(TAG, "SYNC MANUALE: Filtri temporali bypassati.");
            }

            EselLog.LogI(TAG, "SGV(" + sgv.raw + ") ha superato i filtri. Inizio elaborazione.");

            // Calcolo Smoothing (aggiorna anche la variabile currentSmoothingStatus)
            int finalValue = applyEasySmoothing(sgv);
            sgv.value = finalValue;

            calculateTrend(sgv);

            EselLog.LogI(TAG, "Pronto per invio: Valore=" + sgv.value + " (Grezzo=" + sgv.raw + ") | Direzione=" + sgv.direction + " | Timestamp inviato: " + new Date(sgv.timestamp));

            Context appContext = getApplicationContext();
            if (SP.getBoolean("send_to_AAPS", true)) { AapsSender.sendToAaps(appContext, sgv); }
            if (SP.getBoolean("send_to_NS", false)) { AapsSender.sendToNsClient(appContext, sgv); }

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(appContext);
            boolean success = prefs.edit().putLong(KEY_LAST_SGV_TIMESTAMP, sgv.timestamp).commit();
            if (!success) {
                EselLog.LogE(TAG, "Salvataggio SINCRONO di KEY_LAST_SGV_TIMESTAMP fallito!");
            }

            SharedPreferences.Editor editor = prefs.edit();
            editor.putLong(KEY_LAST_SUCCESSFUL_SEND_MS, System.currentTimeMillis());
            editor.putInt(KEY_LAST_SGV_RAW_VALUE, rawValue);
            editor.putInt(KEY_LAST_SGV_FINAL_VALUE, sgv.value);
            editor.apply();

            updateSgvHistory(sgv);

            // --- MODIFICA PER NOTIFICA DETTAGLIATA ---
            DateFormat df = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            String trendArrow = getTrendArrow(sgv.direction);
            String formattedTime = df.format(new Date(sgv.timestamp));

            // Nuova stringa: "142 ↑ (Raw: 145) [MEDIO] alle 12:05:01"
            String notificationText = sgv.value + " " + trendArrow +
                    " (Raw: " + rawValue + ") [" + currentSmoothingStatus + "] alle " + formattedTime;

            updateNotification(notificationText);

        } catch (Throwable t) {
            EselLog.LogE(TAG, "Errore critico durante il processamento del SGV: " + Log.getStackTraceString(t));
        } finally {
            if (wakeLock != null && wakeLock.isHeld()) {
                try {
                    wakeLock.release();
                    EselLog.LogW(TAG, "WakeLock rilasciato.");
                } catch (Exception e) {
                    EselLog.LogE(TAG, "Errore rilascio WakeLock (finally): " + e.getMessage());
                }
            }
        }
    }

    private int applyEasySmoothing(SGV currentSgv) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean smoothingEnabled = prefs.getBoolean("smooth_data", false);

        if (!smoothingEnabled) {
            currentSmoothingStatus = "OFF";
            EselLog.LogI(TAG, "Smoothing SPENTO. Valore raw: " + currentSgv.raw);
            return currentSgv.raw;
        }

        try {
            int lowerLimit = Integer.parseInt(prefs.getString("lower_limit", "65"));
            if (currentSgv.raw < lowerLimit) {
                currentSmoothingStatus = "LIMIT";
                EselLog.LogW(TAG, "[SMOOTHING] SICUREZZA: Valore grezzo (" + currentSgv.raw + ") sotto limite. Smoothing ignorato.");
                return currentSgv.raw;
            }

            // Mappatura Livelli (SOFT-MEDIO-ALTO-FORTE)
            String level = prefs.getString("smoothing_level", "MEDIO").toUpperCase();
            currentSmoothingStatus = level;
            double smoothFactor;

            switch (level) {
                case "SOFT": smoothFactor = 0.5; break;
                case "ALTO": smoothFactor = 0.2; break;
                case "FORTE": smoothFactor = 0.1; break;
                case "MEDIO":
                default:
                    smoothFactor = 0.3;
                    currentSmoothingStatus = "MEDIO";
                    break;
            }

            int lastFinalValue = SP.getInt(KEY_LAST_SGV_FINAL_VALUE, -1);
            if (lastFinalValue == -1) {
                EselLog.LogI(TAG, "Smoothing attivo su " + level + " (Primo avvio). Raw: " + currentSgv.raw);
                return currentSgv.raw;
            }

            double smoothedValue = (currentSgv.raw * smoothFactor) + (lastFinalValue * (1 - smoothFactor));
            int finalValue = (int) Math.round(smoothedValue);

            EselLog.LogI(TAG, "Smoothing ATTIVO su " + level + ". Raw: " + currentSgv.raw + " -> Smooth: " + finalValue);
            return finalValue;

        } catch (Exception e) {
            currentSmoothingStatus = "ERR";
            EselLog.LogE(TAG, "Errore smoothing: " + e.getMessage());
            return currentSgv.raw;
        }
    }

    private void calculateTrend(SGV sgv) {
        long lastSgvTimestamp = SP.getLong(KEY_LAST_SGV_TIMESTAMP, 0L);
        int lastSentFinalValue = SP.getInt(KEY_LAST_SGV_FINAL_VALUE, -1);

        if (lastSgvTimestamp <= 0 || lastSentFinalValue <= 0) {
            EselLog.LogI(TAG, "[TREND] Dati precedenti non validi per calcolo trend.");
            sgv.direction = "Flat";
            return;
        }

        long timeDiff = sgv.timestamp - lastSgvTimestamp;
        if (timeDiff <= 0) {
            EselLog.LogW(TAG, "[TREND] Differenza temporale non valida o zero: " + timeDiff + "ms. Imposto Flat.");
            sgv.direction = "Flat";
            return;
        }

        double valueDiff = (double) sgv.value - lastSentFinalValue;
        double slopeByMinute = valueDiff * 60000.0 / timeDiff;

        double roundedSlope = Math.round(slopeByMinute * 100.0) / 100.0;
        EselLog.LogI(TAG, "[TREND] Calcolo: (ValoreCorrente=" + sgv.value + " - ValorePrecedente=" + lastSentFinalValue + ") / (DiffTempo=" + timeDiff/1000 + "s) * 60 = " + roundedSlope + " mg/dL/min");

        if (slopeByMinute <= -3.5) {
            sgv.direction = "DoubleDown";
        } else if (slopeByMinute <= -2.0) {
            sgv.direction = "SingleDown";
        } else if (slopeByMinute <= -1.0) {
            sgv.direction = "FortyFiveDown";
        } else if (slopeByMinute < 1.0) {
            sgv.direction = "Flat";
        } else if (slopeByMinute < 2.0) {
            sgv.direction = "FortyFiveUp";
        } else if (slopeByMinute < 3.5) {
            sgv.direction = "SingleUp";
        } else {
            sgv.direction = "DoubleUp";
        }
        EselLog.LogI(TAG, "[TREND] Direzione calcolata: " + sgv.direction);
    }

    private void startSyncTrigger() {
        if (syncTriggerHandler != null && syncTriggerRunnable != null) {
            syncTriggerHandler.removeCallbacks(syncTriggerRunnable);
        }

        syncTriggerRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    FastPatrolReceiver.schedule(getApplicationContext());
                } catch (Exception e) {
                    EselLog.LogE(TAG, "Errore schedulazione FastPatrol dal trigger: " + e.getMessage());
                }

                EselLog.LogI(TAG, "Sync Trigger: Richiesta proattiva di lettura dati.");
                requestSgvFromListener();

                long intervalMillis = 5 * 60 * 1000L;
                long now = System.currentTimeMillis();
                long delay = intervalMillis - (now % intervalMillis);
                delay += 250;

                syncTriggerHandler.postDelayed(this, delay);
                EselLog.LogW(TAG, "Sync Trigger: Prossima esecuzione pianificata tra " + delay / 1000 + " secondi per allineamento all'orologio.");
            }
        };
        syncTriggerHandler.postDelayed(syncTriggerRunnable, INITIAL_TRIGGER_DELAY_MS);
        EselLog.LogW(TAG, "Trigger di sincronizzazione proattiva avviato con un ritardo iniziale di " + (INITIAL_TRIGGER_DELAY_MS / 1000) + " secondi.");
    }

    private void stopSyncTrigger() {
        if (syncTriggerHandler != null && syncTriggerRunnable != null) {
            syncTriggerHandler.removeCallbacks(syncTriggerRunnable);
            EselLog.LogW(TAG, "Trigger di sincronizzazione proattiva fermato.");
        }
    }

    private void requestSgvFromListener() {
        try {
            EselLog.LogI(TAG, "Invio comando a EsNotificationListener per forzare la lettura.");
            Intent requestIntent = new Intent(this, EsNotificationListener.class);
            requestIntent.setAction(ACTION_REQUEST_SGV_READ);
            startService(requestIntent);
        } catch (Exception e) {
            EselLog.LogE(TAG, "Impossibile inviare la richiesta a EsNotificationListener: " + e.getMessage());
        }
    }

    private void updateSgvHistory(SGV newSgv) {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            String durationHoursStr = prefs.getString("graph_duration_hours", "3");
            int durationHours = 3;
            try {
                durationHours = Integer.parseInt(durationHoursStr);
                if (durationHours <= 0) durationHours = 3;
            } catch (NumberFormatException e) {
                EselLog.LogW(TAG, "Valore graph_duration_hours non valido: " + durationHoursStr + ". Uso default 3 ore.");
            }

            int historyMaxSize = durationHours * 12;
            String historyJson = SP.getString(KEY_SGV_HISTORY_JSON, "[]");
            Type listType = new TypeToken<ArrayList<SgvHistoryPoint>>() {}.getType();
            List<SgvHistoryPoint> history = gson.fromJson(historyJson, listType);
            if (history == null) {
                history = new ArrayList<>();
            }

            history.add(new SgvHistoryPoint(newSgv.timestamp, newSgv.value));
            while (history.size() > historyMaxSize) {
                history.remove(0);
            }

            String newHistoryJson = gson.toJson(history);
            SP.putString(KEY_SGV_HISTORY_JSON, newHistoryJson);
        } catch (Exception e) {
            EselLog.LogE(TAG, "Errore durante l'aggiornamento della cronologia SGV: " + Log.getStackTraceString(e));
        }
    }

    private void stopSelfService() {
        EselLog.LogW(TAG, "Inizio procedura di arresto volontario del servizio.");
        SP.putBoolean("service_should_be_running", false);
        SP.putBoolean("enable_service", false);
        stopSyncTrigger();

        WatchdogReceiver.cancelWatchdog(this);
        FastPatrolReceiver.cancel(this);

        try {
            stopService(new Intent(this, CompanionService.class));
            EselLog.LogW(TAG, "Servizio Compagno fermato.");
        } catch (Exception e) {
            EselLog.LogE(TAG, "Impossibile fermare il CompanionService: " + e.getMessage());
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        stopSelf();
        EselLog.LogW(TAG, "Servizio fermato.");
    }

    private String getTrendArrow(String direction) {
        if (direction == null) return "→";
        switch (direction) {
            case "DoubleUp": return "↑↑";
            case "SingleUp": return "↑";
            case "FortyFiveUp": return "↗";
            case "Flat": return "→";
            case "FortyFiveDown": return "↘";
            case "SingleDown": return "↓";
            case "DoubleDown": return "↓↓";
            default:
                EselLog.LogW(TAG, "Direzione trend non riconosciuta: " + direction + ". Uso Flat.");
                return "→";
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EselLog.LogW(TAG, "DataMonitorService onDestroy.");
        stopSyncTrigger();
        if (sgvDataReceiver != null) {
            try {
                LocalBroadcastManager.getInstance(this).unregisterReceiver(sgvDataReceiver);
            } catch (Exception e) {
                EselLog.LogE(TAG,"Errore unregisterReceiver: " + e.getMessage());
            }
        }
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            EselLog.LogI(TAG, "Executor shutdown richiesto.");
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            try {
                wakeLock.release();
                EselLog.LogW(TAG, "WakeLock rilasciato in onDestroy.");
            } catch (Exception e) {
                EselLog.LogE(TAG, "Errore rilascio WakeLock in onDestroy: " + e.getMessage());
            }
        }

        if (SP.getBoolean("service_should_be_running", false)) {
            EselLog.LogW(TAG, "Distruzione non volontaria. Invio broadcast per il riavvio...");
            Intent broadcastIntent = new Intent(this, ServiceRestarter.class);
            try {
                sendBroadcast(broadcastIntent);
            } catch (Exception e) {
                EselLog.LogE(TAG, "Errore invio broadcast ServiceRestarter: " + e.getMessage());
            }
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        EselLog.LogW(TAG, "Task rimosso dall'utente.");
        if (SP.getBoolean("service_should_be_running", false)) {
            Intent broadcastIntent = new Intent(this, ServiceRestarter.class);
            try {
                sendBroadcast(broadcastIntent);
            } catch (Exception e) {
                EselLog.LogE(TAG, "Errore invio broadcast ServiceRestarter (onTaskRemoved): " + e.getMessage());
            }
        }
        super.onTaskRemoved(rootIntent);
    }

    private void updateNotification(String contentText) {
        try {
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.notify(NOTIFICATION_ID, buildNotification(contentText));
            }
        } catch (Exception e) {
            EselLog.LogE(TAG, "Errore durante l'aggiornamento della notifica: " + e.getMessage());
        }
    }

    private Notification buildNotification(String contentText) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_persistent_title))
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_stat_esel_sync)
                .setColor(ContextCompat.getColor(this, R.color.green_primary))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setSilent(true)
                .build();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            serviceChannel.setDescription(getString(R.string.notification_channel_description));
            serviceChannel.setSound(null, null);
            serviceChannel.enableVibration(false);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
                EselLog.LogI(TAG,"Canale di notifica creato: " + CHANNEL_ID);
            }
        }
    }
}