// ---------- CODICE CON LOGICA DI SCRITTURA E TRONCAMENTO DEFINITIVI ----------
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

                // --- NUOVA LOGICA DI SCRITTURA SICURA ---
                // Aggiungiamo solo la nuova riga e forziamo il salvataggio immediato su disco.
                appendLineToFile(formattedLine);

                // Aggiorniamo la lista in memoria per l'interfaccia utente
                List<String> currentUiLogs;
                synchronized (logLines) {
                    logLines.add(0, formattedLine);
                    int maxLogLines = getMaxLogLinesFromPrefs();
                    while (logLines.size() > maxLogLines) {
                        logLines.remove(logLines.size() - 1);
                    }
                    currentUiLogs = new ArrayList<>(logLines);
                }

                // Notifichiamo l'UI
                logsLiveData.postValue(currentUiLogs);

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

            // --- NUOVA LOGICA DI TRONCAMENTO ALL'AVVIO ---
            // Se il file è più grande del limite, lo tronchiamo e lo riscriviamo.
            int maxLogLines = getMaxLogLinesFromPrefs();
            if (tempLines.size() > maxLogLines) {
                Log.w(TAG, "File di log troppo grande (" + tempLines.size() + " righe). Troncamento a " + maxLogLines + " righe.");
                int excess = tempLines.size() - maxLogLines;
                tempLines = tempLines.subList(excess, tempLines.size());
                writeFullFile(tempLines); // Riscrive il file troncato
            }

            synchronized (logLines) {
                logLines.clear();
                logLines.addAll(tempLines);
                Collections.reverse(logLines); // Visualizza i più recenti in cima
            }
            logsLiveData.postValue(new ArrayList<>(logLines));
        });
    }

    // Metodo per aggiungere una singola riga in modo sicuro
    private void appendLineToFile(String line) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) { // true per APPEND
            writer.println(line);
            writer.flush(); // Forza il salvataggio immediato su disco
        } catch (IOException e) {
            Log.e(TAG, "Errore durante la scrittura su file di log", e);
        }
    }

    // Metodo per riscrivere l'intero file, usato solo per il troncamento
    private void writeFullFile(List<String> linesToWrite) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, false))) { // false per SOVRASCRIVERE
            for (String line : linesToWrite) {
                writer.println(line);
            }
            writer.flush();
        } catch (IOException e) {
            Log.e(TAG, "Errore durante la riscrittura del file di log troncato", e);
        }
    }

    public void clearLogs() {
        executor.execute(() -> {
            synchronized (logLines) {
                logLines.clear();
            }
            if (logFile.exists()) {
                if (logFile.delete()) {
                    Log.w(TAG, "File di log eliminato con successo.");
                } else {
                    Log.e(TAG, "Impossibile eliminare il file di log.");
                }
            }
            logsLiveData.postValue(new ArrayList<>());
        });
    }

    private int getMaxLogLinesFromPrefs() {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(appContext);
            return Integer.parseInt(prefs.getString("log_max_lines", "1000"));
        } catch (Exception e) {
            return 1000;
        }
    }
}