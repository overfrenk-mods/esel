// File: esel/esel/esel/CrashCatcher.java
package esel.esel.esel;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class CrashCatcher implements Thread.UncaughtExceptionHandler {

    private final Thread.UncaughtExceptionHandler defaultUEH;
    private final File logFile;
    private static final String TAG = "CrashCatcher";

    public CrashCatcher(Context context) {
        // Salva il gestore di default, per non rompere la catena
        this.defaultUEH = Thread.getDefaultUncaughtExceptionHandler();
        this.logFile = new File(context.getFilesDir(), "app_log.txt");
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        // Questo metodo viene chiamato un istante prima del crash
        Log.e(TAG, "CRASH RILEVATO! Scrivo la causa su file di log...");

        // Convertiamo l'errore (Throwable) in una stringa di testo leggibile
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        String stackTrace = sw.toString();

        // Formattiamo il messaggio di crash
        LocalDateTime currentTime = LocalDateTime.now();
        DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        // --- MODIFICA: Aggiunto il tag [Error] per la compatibilità con i filtri ---
        String crashLog = String.format(
                "\n\n%s: [Error] CRASH_DETECTED: App terminata in modo anomalo!\n--- INIZIO STACK TRACE ---\n%s--- FINE STACK TRACE ---\n\n",
                currentTime.format(format),
                stackTrace
        );

        // Scriviamo il crash su file in modo sincrono e sicuro
        try (FileWriter writer = new FileWriter(logFile, true)) { // true = append
            writer.append(crashLog);
            writer.flush();
        } catch (Exception ex) {
            Log.e(TAG, "Impossibile scrivere il log del crash su file", ex);
        }

        // Passiamo l'eccezione al gestore di default per far crashare l'app come previsto
        if (defaultUEH != null) {
            defaultUEH.uncaughtException(t, e);
        }
    }

    /**
     * Metodo statico per installare il nostro CrashCatcher
     */
    public static void install(Context context) {
        if (!(Thread.getDefaultUncaughtExceptionHandler() instanceof CrashCatcher)) {
            Thread.setDefaultUncaughtExceptionHandler(new CrashCatcher(context));
            Log.i(TAG, "CrashCatcher installato con successo.");
        }
    }
}