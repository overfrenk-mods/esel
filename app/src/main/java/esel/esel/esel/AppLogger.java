// ---------- CODICE CON SCRITTURA SICURA "ATOMICA" E TRONCAMENTO A ROTAZIONE ----------
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
    private static final String TEMP_LOG_FILE_NAME = "app_log.txt.tmp"; // File temporaneo per la scrittura sicura

    private static volatile AppLogger INSTANCE;
    private final Context appContext;
    private final File logFile;
    private final File tempLogFile; // Aggiunto riferimento al file temporaneo
    private final ExecutorService executor;

    private final MutableLiveData<List<String>> logsLiveData = new MutableLiveData<>();
    private final List<String> logLines = new ArrayList<>();

    private AppLogger(Context context) {
        this.appContext = context.getApplicationContext();
        this.logFile = new File(appContext.getFilesDir(), LOG_FILE_NAME);
        this.tempLogFile = new File(appContext.getFilesDir(), TEMP_LOG_FILE_NAME);
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

                List<String> currentLogsForFile;
                synchronized (logLines) {
                    logLines.add(0, formattedLine);
                    int maxLogLines = getMaxLogLinesFromPrefs();
                    while (logLines.size() > maxLogLines) {
                        logLines.remove(logLines.size() - 1);
                    }
                    currentLogsForFile = new ArrayList<>(logLines);
                }

                logsLiveData.postValue(currentLogsForFile);

                // Ora usiamo il metodo di scrittura sicura
                writeLogsToFileSafely(currentLogsForFile);

            } catch (Exception e) {
                Log.e(TAG, "Errore durante l'aggiunta del log", e);
            }
        });
    }

    private void writeLogsToFileSafely(List<String> linesToWrite) {
        List<String> linesToPersist = new ArrayList<>(linesToWrite);
        Collections.reverse(linesToPersist);

        // 1. Scrivi su un file temporaneo
        try (PrintWriter writer = new PrintWriter(new FileWriter(tempLogFile, false))) {
            for (String line : linesToPersist) {
                writer.println(line);
            }
            writer.flush();
        } catch (IOException e) {
            Log.e(TAG, "Errore durante la scrittura sul file di log temporaneo", e);
            // Se la scrittura fallisce, non continuiamo per non corrompere il file originale
            if(tempLogFile.exists()) tempLogFile.delete();
            return;
        }

        // 2. Se la scrittura ha successo, esegui lo scambio atomico
        try {
            if (logFile.exists()) {
                logFile.delete();
            }
            if (!tempLogFile.renameTo(logFile)) {
                Log.e(TAG, "FALLIMENTO: Impossibile rinominare il file di log temporaneo.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Errore durante lo scambio dei file di log", e);
        }
    }

    public LiveData<List<String>> getLogs() {
        return logsLiveData;
    }

    private void loadLogsFromFile() {
        executor.execute(() -> {
            // Controllo di sicurezza: se esiste un file temporaneo all'avvio, significa che c'è stato un crash.
            // Lo cancelliamo per ripartire puliti.
            if(tempLogFile.exists()){
                Log.w(TAG, "Trovato file di log temporaneo all'avvio. Potrebbe esserci stato un crash. Lo elimino.");
                tempLogFile.delete();
            }

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
                Log.w(TAG, "File di log troppo grande (" + tempLines.size() + " righe). Troncamento a " + maxLogLines + " righe.");
                int excess = tempLines.size() - maxLogLines;
                tempLines = tempLines.subList(excess, tempLines.size());
                writeLogsToFileSafely(tempLines);
            }

            synchronized (logLines) {
                logLines.clear();
                logLines.addAll(tempLines);
                Collections.reverse(logLines);
            }
            logsLiveData.postValue(new ArrayList<>(logLines));
        });
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

    private int getMaxLogLinesFromPrefs() {
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(appContext);
            return Integer.parseInt(prefs.getString("log_max_lines", "15000"));
        } catch (Exception e) {
            return 15000;
        }
    }
}