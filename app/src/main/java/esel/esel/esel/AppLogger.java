// ---------------- CODICE CON NOME DEL THREAD NEL LOG ----------------
package esel.esel.esel;

import android.content.Context;
import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import java.io.BufferedReader;
import java.io.BufferedWriter;
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
    private static final int MAX_LOG_LINES = 32000;

    private static volatile AppLogger INSTANCE;
    private final File logFile;
    private final ExecutorService executor;

    private final MutableLiveData<List<String>> logsLiveData = new MutableLiveData<>();
    private final List<String> logLines = new ArrayList<>();

    private AppLogger(Context context) {
        logFile = new File(context.getFilesDir(), LOG_FILE_NAME);
        executor = Executors.newSingleThreadExecutor();
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

                // --- MODIFICA: Aggiunto il nome del thread al log ---
                String threadName = Thread.currentThread().getName();
                String formattedLine = String.format("%s: [%s] [%s] %s: %s", currentTime.format(format), type, threadName, tag, value);

                // Aggiunge in memoria
                synchronized (logLines) {
                    logLines.add(0, formattedLine);
                    if (logLines.size() > MAX_LOG_LINES) {
                        logLines.remove(logLines.size() - 1);
                    }
                }

                // Notifica l'UI
                logsLiveData.postValue(new ArrayList<>(logLines));

                // Scrive solo la nuova riga su file in modo sicuro
                appendLineToFile(formattedLine);

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

            if (tempLines.size() > MAX_LOG_LINES) {
                int excess = tempLines.size() - MAX_LOG_LINES;
                tempLines = tempLines.subList(excess, tempLines.size());
                writeLogsToFile(tempLines);
            }

            synchronized (logLines) {
                logLines.clear();
                logLines.addAll(tempLines);
                Collections.reverse(logLines);
            }
            logsLiveData.postValue(new ArrayList<>(logLines));
        });
    }

    private void writeLogsToFile(List<String> linesToWrite) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, false))) {
            for (String line : linesToWrite) {
                writer.println(line);
            }
        } catch (IOException e) {
            Log.e(TAG, "Errore durante la riscrittura del file di log", e);
        }
    }

    private void appendLineToFile(String line) {
        try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(logFile, true)))) {
            writer.println(line);
        } catch (IOException e) {
            Log.e(TAG, "Errore durante l'append del log su file", e);
        }
    }

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
}