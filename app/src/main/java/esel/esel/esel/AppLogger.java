// ---------------- CODICE CON LOGICA DI SCRITTURA CORRETTA E DEFINITIVA ----------------
package esel.esel.esel;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.preference.PreferenceManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppLogger {

    private static final String TAG = "AppLogger";
    private static final String LOG_FILE_NAME = "app_log.txt";

    private static volatile AppLogger INSTANCE;
    private final Context appContext;
    private final File logFile;
    private final ExecutorService executor;

    private final MutableLiveData<List<String>> logsLiveData = new MutableLiveData<>();
    private final List<String> logLines = new ArrayList<>();

    private AppLogger(Context context) {
        this.appContext = context.getApplicationContext();
        this.logFile = new File(appContext.getFilesDir(), LOG_FILE_NAME);
        this.executor = Executors.newSingleThreadExecutor();
        loadLogsFromFile();
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
                LocalDateTime currentTime = LocalDateTime.now();
                DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

                String threadName = Thread.currentThread().getName();
                String formattedLine = String.format("%s: [%s] [%s] %s: %s", currentTime.format(format), type, threadName, tag, value);

                int maxLogLines = getMaxLogLinesFromPrefs();

                List<String> currentLogs;
                synchronized (logLines) {
                    logLines.add(0, formattedLine);
                    // Applica il troncamento se si supera il limite
                    if (logLines.size() > maxLogLines) {
                        logLines.remove(logLines.size() - 1);
                    }
                    currentLogs = new ArrayList<>(logLines);
                }

                // Notifica l'UI
                logsLiveData.postValue(currentLogs);

                // --- FIX: Riscrive l'intero file con la lista aggiornata e troncata ---
                // In questo modo il file su disco rispetta sempre il limite.
                writeLogsToFile(currentLogs);

            } catch (Exception e) {
                Log.e(TAG, "Errore durante l'aggiunta del log", e);
            }
        });
    }

    public LiveData<List<String>> getLogs() {
        return logsLiveData;
    }

    private void loadLogsFromFile() {
        executor.execute(() -> {
            if (!logFile.exists()) {
                logsLiveData.postValue(Collections.emptyList());
                return;
            }

            List<String> tempLines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    tempLines.add(line);
                }
            } catch (IOException e) {
                Log.e(TAG, "Errore durante la lettura del file di log", e);
            }

            int maxLogLines = getMaxLogLinesFromPrefs();
            if (tempLines.size() > maxLogLines) {
                int excess = tempLines.size() - maxLogLines;
                tempLines = tempLines.subList(excess, tempLines.size());
                // Riscrive il file troncato per tenerlo pulito alla dimensione corretta
                writeLogsToFile(tempLines);
            }

            synchronized (logLines) {
                logLines.clear();
                logLines.addAll(tempLines);
                // Il file viene letto dal più vecchio al più nuovo,
                // ma noi vogliamo visualizzare i più recenti in cima.
                Collections.reverse(logLines);
            }
            logsLiveData.postValue(new ArrayList<>(logLines));
        });
    }

    private void writeLogsToFile(List<String> linesToWrite) {
        // Cloniamo la lista per evitare problemi di concorrenza durante la scrittura
        List<String> linesToPersist = new ArrayList<>(linesToWrite);
        // La visualizzazione è invertita (nuovi in cima), ma su disco salviamo in ordine cronologico.
        Collections.reverse(linesToPersist);

        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, false))) { // false per sovrascrivere
            for (String line : linesToPersist) {
                writer.println(line);
            }
        } catch (IOException e) {
            Log.e(TAG, "Errore durante la riscrittura del file di log", e);
        }
    }

    // Il metodo appendLineToFile non è più necessario con la nuova logica
    /*
    private void appendLineToFile(String line) {
        try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(logFile, true)))) {
            writer.println(line);
        } catch (IOException e) {
            Log.e(TAG, "Errore durante l'append del log su file", e);
        }
    }
    */

    public void clearLogs() {
        executor.execute(() -> {
            synchronized (logLines) {
                logLines.clear();
            }
            if (logFile.exists()) {
                logFile.delete();
            }
            logsLiveData.postValue(new ArrayList<>());
            Log.w(TAG, "Log pulito dall'utente.");
        });
    }

    private int getMaxLogLinesFromPrefs() {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(appContext);
            // Legge il valore come stringa e lo converte in intero.
            // --- VALORE DI DEFAULT AGGIORNATO A 1000 ---
            return Integer.parseInt(prefs.getString("log_max_lines", "1000"));
        } catch (NumberFormatException e) {
            // Se l'utente inserisce un valore non valido (es. testo), usa il default
            return 1000;
        }
    }
}