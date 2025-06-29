// ---------------- CODICE COMPLETO E AGGIORNATO PER AppLogger.java ----------------
package esel.esel.esel;

import android.content.Context;
import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Singleton per la gestione centralizzata dei log dell'applicazione.
 * Scrive i log su un file interno in modo efficiente e asincrono.
 * Offre LiveData per notificare l'interfaccia utente dei cambiamenti in modo sicuro.
 */
public class AppLogger {

    private static final String TAG = "AppLogger";
    private static final String LOG_FILE_NAME = "app_log.txt";
    private static final int MAX_LOG_LINES = 8000; // Limite massimo di righe nel log

    private static volatile AppLogger INSTANCE;
    private final File logFile;
    private final ExecutorService executor; // Per eseguire operazioni su file in un thread separato

    // LiveData per notificare l'UI in modo lifecycle-aware (senza crash)
    private final MutableLiveData<List<String>> logsLiveData = new MutableLiveData<>();
    private final List<String> logLines = new ArrayList<>();

    // Costruttore privato per il pattern Singleton
    private AppLogger(Context context) {
        // Usa la memoria interna dell'app, che non richiede permessi speciali
        logFile = new File(context.getFilesDir(), LOG_FILE_NAME);
        // Un solo thread per eseguire le operazioni in coda, garantendo l'ordine di scrittura
        executor = Executors.newSingleThreadExecutor();
        loadLogsFromFile();
    }

    /**
     * Ottiene l'istanza unica del logger.
     */
    public static AppLogger getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppLogger.class) {
                if (INSTANCE == null) {
                    INSTANCE = new AppLogger(context.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Aggiunge una nuova riga di log.
     * Questo metodo può essere chiamato da qualsiasi punto dell'app (Activity, Service, etc.).
     */
    public void add(String type, String tag, String value) {
        executor.execute(() -> {
            try {
                // Formatta il messaggio di log
                LocalDateTime currentTime = LocalDateTime.now();
                DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                String formattedLine = String.format("%s: [%s] %s: %s", currentTime.format(format), type, tag, value);

                // Aggiunge in cima alla lista in memoria
                synchronized (logLines) {
                    logLines.add(0, formattedLine);
                    // Applica il troncamento se si supera il limite
                    if (logLines.size() > MAX_LOG_LINES) {
                        logLines.subList(MAX_LOG_LINES, logLines.size()).clear();
                    }
                }

                // Notifica gli observer (come LogActivity) con la lista aggiornata
                logsLiveData.postValue(new ArrayList<>(logLines));

                // Scrive l'intera lista aggiornata su file.
                writeLogsToFile();

            } catch (Exception e) {
                Log.e(TAG, "Errore durante la scrittura del log", e);
            }
        });
    }

    /**
     * Fornisce l'accesso ai log come LiveData per essere osservato dall'UI.
     */
    public LiveData<List<String>> getLogs() {
        return logsLiveData;
    }

    /**
     * Carica i log dal file di testo all'avvio.
     */
    private void loadLogsFromFile() {
        executor.execute(() -> {
            if (!logFile.exists()) {
                logsLiveData.postValue(Collections.emptyList());
                return;
            }
            try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
                synchronized (logLines) {
                    logLines.clear();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logLines.add(line);
                    }
                }
                // Notifica l'UI dopo aver caricato i log
                logsLiveData.postValue(new ArrayList<>(logLines));
            } catch (IOException e) {
                Log.e(TAG, "Errore durante la lettura del file di log", e);
            }
        });
    }

    /**
     * Scrive la lista corrente di log nel file, sovrascrivendolo.
     */
    private void writeLogsToFile() {
        try (FileOutputStream fos = new FileOutputStream(logFile, false); // false per sovrascrivere
             OutputStreamWriter writer = new OutputStreamWriter(fos)) {

            synchronized (logLines) {
                for (String line : logLines) {
                    writer.write(line + "\n");
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Errore durante la scrittura del file di log", e);
        }
    }

    // --- NUOVO METODO AGGIUNTO ---
    /**
     * Pulisce tutti i log, sia in memoria che su file.
     * Viene eseguito in un thread separato per non bloccare l'interfaccia utente.
     */
    public void clearLogs() {
        executor.execute(() -> {
            // Pulisce la lista in memoria
            synchronized (logLines) {
                logLines.clear();
            }

            // Cancella il file fisico
            if (logFile.exists()) {
                logFile.delete();
            }

            // Notifica l'UI che la lista è ora vuota
            logsLiveData.postValue(new ArrayList<>());

            Log.w(TAG, "Log pulito dall'utente.");
        });
    }
}