// ---------- CODICE PROFESSIONALE CON STRATEGIA "APPEND-E-TRONCA" ----------
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
    private static final String LOG_FILE_NAME = "app_log.txt";
    private static final String TEMP_LOG_FILE_NAME = "app_log.txt.tmp";

    private static volatile AppLogger INSTANCE;
    private final Context appContext;
    private final File logFile;
    private final ExecutorService executor;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    // LiveData per l'aggiornamento della UI
    private final MutableLiveData<List<String>> logsLiveData = new MutableLiveData<>(new ArrayList<>());
    // Cache in memoria delle righe di log, sempre in ordine inverso (nuove in cima)
    private final List<String> logLinesCache = new ArrayList<>();

    private AppLogger(Context context) {
        this.appContext = context.getApplicationContext();
        this.logFile = new File(appContext.getFilesDir(), LOG_FILE_NAME);
        this.executor = Executors.newSingleThreadExecutor();
        loadAndTrimLogFile();
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

                // 1. Aggiungi la riga al file in modo efficiente (append)
                appendLogToFile(formattedLine);

                // 2. Aggiorna la cache in memoria e il LiveData per la UI
                updateMemoryCache(formattedLine);

            } catch (Exception e) {
                Log.e(TAG, "Errore durante l'aggiunta del log", e);
            }
        });
    }

    private void appendLogToFile(String line) {
        // Scrive in modalità 'append' (true), molto efficiente
        try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(logFile, true)))) {
            writer.println(line);
        } catch (IOException e) {
            Log.e(TAG, "Errore critico durante la scrittura (append) del log", e);
        }
    }

    private void updateMemoryCache(String line) {
        synchronized (logLinesCache) {
            // Aggiungi in cima
            logLinesCache.add(0, line);

            // Rimuovi dal fondo se la cache supera la dimensione massima
            int maxLogLines = getMaxLogLinesFromPrefs();
            while (logLinesCache.size() > maxLogLines) {
                logLinesCache.remove(logLinesCache.size() - 1);
            }

            // Notifica la UI con una nuova lista per triggerare l'aggiornamento
            logsLiveData.postValue(new ArrayList<>(logLinesCache));
        }
    }

    /**
     * Questo metodo viene eseguito solo all'avvio.
     * Legge il file, lo tronca se necessario e popola la cache iniziale.
     */
    private void loadAndTrimLogFile() {
        executor.execute(() -> {
            if (!logFile.exists()) {
                return;
            }

            List<String> linesFromFile = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    linesFromFile.add(line);
                }
            } catch (IOException e) {
                Log.e(TAG, "Errore durante la lettura del file di log", e);
                return;
            }

            int maxLogLines = getMaxLogLinesFromPrefs();
            List<String> finalLines;

            // Se il file è più grande del consentito, lo tronchiamo
            if (linesFromFile.size() > maxLogLines) {
                Log.w(TAG, "File di log (" + linesFromFile.size() + ") supera il limite (" + maxLogLines + "). Troncamento...");
                int startIndex = linesFromFile.size() - maxLogLines;
                finalLines = linesFromFile.subList(startIndex, linesFromFile.size());
                rewriteFileWithLines(finalLines); // Operazione costosa, ma eseguita solo una volta
            } else {
                finalLines = linesFromFile;
            }

            // Popola la cache in memoria e notifica la UI
            synchronized (logLinesCache) {
                logLinesCache.clear();
                logLinesCache.addAll(finalLines);
                Collections.reverse(logLinesCache); // La UI vuole le righe nuove in cima
                logsLiveData.postValue(new ArrayList<>(logLinesCache));
            }
        });
    }

    private void rewriteFileWithLines(List<String> lines) {
        File tempFile = new File(appContext.getFilesDir(), TEMP_LOG_FILE_NAME);
        try (PrintWriter writer = new PrintWriter(new FileWriter(tempFile))) {
            for (String line : lines) {
                writer.println(line);
            }
        } catch (IOException e) {
            Log.e(TAG, "Impossibile riscrivere il file di log troncato.", e);
            if(tempFile.exists()) tempFile.delete(); // Pulisci il file temp
            return;
        }

        // Scambio atomico
        if (logFile.delete()) {
            if (!tempFile.renameTo(logFile)) {
                Log.e(TAG, "FALLIMENTO ATOMICO: Impossibile rinominare il file temp in quello definitivo.");
            }
        } else {
            Log.e(TAG, "FALLIMENTO ATOMICO: Impossibile eliminare il vecchio file di log.");
        }
    }


    public LiveData<List<String>> getLogs() {
        return logsLiveData;
    }

    public void clearLogs() {
        executor.execute(() -> {
            synchronized (logLinesCache) {
                logLinesCache.clear();
            }
            if (logFile.exists()) {
                logFile.delete();
            }
            // Aggiorna la UI con una lista vuota
            logsLiveData.postValue(new ArrayList<>());
            Log.w(TAG, "Log pulito manualmente dall'utente.");
        });
    }

    private int getMaxLogLinesFromPrefs() {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(appContext);
            String value = prefs.getString("log_max_lines", "15000");
            return Integer.parseInt(value);
        } catch (Exception e) {
            return 15000; // Valore di fallback sicuro
        }
    }
}