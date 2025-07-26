// ---------- CODICE CON LOGICA PROFESSIONALE A ROTAZIONE DI FILE ----------
package esel.esel.esel;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.preference.PreferenceManager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppLogger {

    private static final String TAG = "AppLogger";
    private static final String LOG_FILE_BASE_NAME = "app_log";
    private static final String LOG_FILE_EXTENSION = ".txt";
    private static final int NUM_LOG_FILES = 3; // Usiamo 3 file a rotazione

    private static volatile AppLogger INSTANCE;
    private final Context appContext;
    private final ExecutorService executor;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    // LiveData e Cache in memoria, come prima
    private final MutableLiveData<List<String>> logsLiveData = new MutableLiveData<>(new ArrayList<>());
    private final List<String> logLinesCache = new ArrayList<>();

    // Contatore per le righe nel file corrente, per sapere quando ruotare
    private int currentLogFileLines = 0;

    private AppLogger(Context context) {
        this.appContext = context.getApplicationContext();
        this.executor = Executors.newSingleThreadExecutor();
        // All'avvio, carichiamo i log da tutti i file a rotazione
        loadLogsFromRotatingFiles();
    }

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

    public void add(String type, String tag, String value) {
        executor.execute(() -> {
            try {
                String timestamp = dateFormat.format(new Date());
                String threadName = Thread.currentThread().getName();
                String formattedLine = String.format("%s: [%s] [%s] %s: %s", timestamp, type, threadName, tag, value);

                // 1. Scrivi sul file corrente (app_log.0.txt)
                appendLogToFile(formattedLine);

                // 2. Aggiorna la cache in memoria per la UI
                updateMemoryCache(formattedLine);

                // 3. Controlla se è ora di ruotare i file
                checkAndRotateLogs();

            } catch (Exception e) {
                Log.e(TAG, "Errore durante l'aggiunta del log", e);
            }
        });
    }

    private void appendLogToFile(String line) {
        File currentLogFile = new File(appContext.getFilesDir(), LOG_FILE_BASE_NAME + ".0" + LOG_FILE_EXTENSION);
        try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(currentLogFile, true)))) {
            writer.println(line);
            currentLogFileLines++; // Incrementiamo il contatore solo se la scrittura va a buon fine
        } catch (IOException e) {
            Log.e(TAG, "Errore critico durante la scrittura (append) del log", e);
        }
    }

    private void checkAndRotateLogs() {
        int maxLinesPerFile = getMaxLogLinesFromPrefs() / NUM_LOG_FILES;
        if (currentLogFileLines >= maxLinesPerFile) {
            Log.i(TAG, "Limite per il file di log corrente raggiunto. Avvio la rotazione...");
            rotateLogFiles();
            currentLogFileLines = 0; // Azzera il contatore per il nuovo file
        }
    }

    private void rotateLogFiles() {
        // 1. Cancella il file più vecchio (es. app_log.2.txt)
        File oldestFile = new File(appContext.getFilesDir(), LOG_FILE_BASE_NAME + "." + (NUM_LOG_FILES - 1) + LOG_FILE_EXTENSION);
        if (oldestFile.exists()) {
            oldestFile.delete();
        }

        // 2. Rinomina gli altri file a scalare (es. 1->2, 0->1)
        for (int i = NUM_LOG_FILES - 2; i >= 0; i--) {
            File sourceFile = new File(appContext.getFilesDir(), LOG_FILE_BASE_NAME + "." + i + LOG_FILE_EXTENSION);
            if (sourceFile.exists()) {
                File destFile = new File(appContext.getFilesDir(), LOG_FILE_BASE_NAME + "." + (i + 1) + LOG_FILE_EXTENSION);
                sourceFile.renameTo(destFile);
            }
        }
    }

    private void updateMemoryCache(String line) {
        synchronized (logLinesCache) {
            logLinesCache.add(0, line); // Aggiungi in cima (più recente)

            // Rimuovi dal fondo se la cache supera il limite totale
            int maxTotalLines = getMaxLogLinesFromPrefs();
            while (logLinesCache.size() > maxTotalLines) {
                logLinesCache.remove(logLinesCache.size() - 1);
            }
            logsLiveData.postValue(new ArrayList<>(logLinesCache));
        }
    }

    private void loadLogsFromRotatingFiles() {
        executor.execute(() -> {
            // Per sicurezza, se troviamo ancora il vecchio file di log, lo cancelliamo
            // per garantire la transizione al nuovo sistema a rotazione.
            File oldLogFile = new File(appContext.getFilesDir(), "app_log.txt");
            if (oldLogFile.exists()) {
                Log.w(TAG, "Trovato vecchio file di log. Verrà eliminato per passare al nuovo sistema a rotazione.");
                oldLogFile.delete();
            }

            List<String> allLines = new ArrayList<>();
            int linesInCurrentFile = 0;

            // Leggiamo i file dall'ultimo al primo (es. 2, 1, 0) per avere l'ordine cronologico corretto
            for (int i = NUM_LOG_FILES - 1; i >= 0; i--) {
                File file = new File(appContext.getFilesDir(), LOG_FILE_BASE_NAME + "." + i + LOG_FILE_EXTENSION);
                if (file.exists()) {
                    List<String> linesFromFile = readAllLines(file);
                    allLines.addAll(linesFromFile);
                    if (i == 0) { // Se stiamo leggendo il file corrente (0), contiamo le sue righe
                        linesInCurrentFile = linesFromFile.size();
                    }
                }
            }

            this.currentLogFileLines = linesInCurrentFile;

            synchronized (logLinesCache) {
                logLinesCache.clear();
                // Aggiungiamo tutte le righe e poi le invertiamo, perché la cache vuole le più recenti in cima
                logLinesCache.addAll(allLines);
                Collections.reverse(logLinesCache);

                // Assicuriamoci che la cache non superi comunque il limite massimo
                int maxTotalLines = getMaxLogLinesFromPrefs();
                while (logLinesCache.size() > maxTotalLines) {
                    logLinesCache.remove(logLinesCache.size() - 1);
                }

                logsLiveData.postValue(new ArrayList<>(logLinesCache));
            }
            Log.i(TAG, "Caricate " + logLinesCache.size() + " righe di log dai file a rotazione.");
        });
    }

    private List<String> readAllLines(File file) {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            Log.e(TAG, "Errore durante la lettura del file " + file.getName(), e);
        }
        return lines;
    }


    public LiveData<List<String>> getLogs() {
        return logsLiveData;
    }

    /**
     * Usato per la funzione "Condividi Log".
     * Unisce tutti i file di log in una singola lista di stringhe.
     * @return Una lista contenente tutte le righe di log in ordine cronologico.
     */
    public List<String> getCompleteLogForSharing() {
        List<String> allLines = new ArrayList<>();
        // Leggiamo i file dall'ultimo al primo (2, 1, 0) per avere l'ordine cronologico.
        for (int i = NUM_LOG_FILES - 1; i >= 0; i--) {
            File file = new File(appContext.getFilesDir(), LOG_FILE_BASE_NAME + "." + i + LOG_FILE_EXTENSION);
            if (file.exists()) {
                allLines.addAll(readAllLines(file));
            }
        }
        return allLines;
    }


    public void clearLogs() {
        executor.execute(() -> {
            // Cancella tutti i file a rotazione
            for (int i = 0; i < NUM_LOG_FILES; i++) {
                File file = new File(appContext.getFilesDir(), LOG_FILE_BASE_NAME + "." + i + LOG_FILE_EXTENSION);
                if (file.exists()) {
                    file.delete();
                }
            }
            synchronized (logLinesCache) {
                logLinesCache.clear();
                logsLiveData.postValue(new ArrayList<>());
            }
            currentLogFileLines = 0;
            Log.w(TAG, "Tutti i file di log sono stati puliti dall'utente.");
        });
    }

    private int getMaxLogLinesFromPrefs() {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(appContext);
            String value = prefs.getString("log_max_lines", "15000");
            // Assicuriamoci che il valore sia divisibile per il numero di file per evitare problemi
            int parsedValue = Integer.parseInt(value);
            return (parsedValue / NUM_LOG_FILES) * NUM_LOG_FILES;
        } catch (Exception e) {
            return 15000;
        }
    }
}