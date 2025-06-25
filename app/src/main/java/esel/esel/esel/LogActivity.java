// ---------- CODICE FINALE CON LOGICA DI CONDIVISIONE LOG ----------
package esel.esel.esel;

import esel.esel.esel.util.EselLog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import androidx.lifecycle.Observer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class LogActivity extends AppCompatActivity {

    private TextView textViewLogContent;
    private AppLogger appLogger;

    private List<String> allLogLines = new ArrayList<>();
    private String currentFilter = "ALL";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_errors);

        Toolbar toolbar = findViewById(R.id.toolbar_log);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Log Applicazione");
        }

        textViewLogContent = findViewById(R.id.textview_log_content);
        appLogger = AppLogger.getInstance(getApplicationContext());

        final Observer<List<String>> logObserver = newLogLines -> {
            this.allLogLines = new ArrayList<>(newLogLines);
            updateDisplayedLogs();
        };

        appLogger.getLogs().observe(this, logObserver);
    }

    private void updateDisplayedLogs() {
        List<String> filteredList;

        if (currentFilter.equals("ALL")) {
            filteredList = allLogLines;
        } else {
            filteredList = allLogLines.stream()
                    .filter(line -> line.contains("[" + currentFilter + "]"))
                    .collect(Collectors.toList());
        }

        if (filteredList.isEmpty()) {
            textViewLogContent.setText("Nessun log da mostrare per il filtro selezionato.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (String line : filteredList) {
            sb.append(line).append("\n");
        }
        textViewLogContent.setText(sb.toString());
    }

    // --- NUOVO METODO PER GESTIRE LA CONDIVISIONE DEL FILE DI LOG ---
    private void shareLogFile() {
        try {
            // Definiamo il percorso del nostro file di log
            File logFile = new File(getFilesDir(), "app_log.txt");

            if (!logFile.exists()) {
                Toast.makeText(this, "File di log non trovato.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Usiamo FileProvider per creare un URI sicuro per la condivisione
            Uri logUri = FileProvider.getUriForFile(
                    this,
                    BuildConfig.APPLICATION_ID + ".provider", // L'authority che abbiamo definito nel Manifest
                    logFile
            );

            // Creiamo l'intent di condivisione
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_STREAM, logUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); // Diamo i permessi temporanei

            // Avviamo il selettore di app di Android
            startActivity(Intent.createChooser(shareIntent, "Condividi log via..."));

        } catch (Exception e) {
            Toast.makeText(this, "Impossibile condividere il file di log.", Toast.LENGTH_SHORT).show();
            EselLog.LogE("LogActivity", "Errore condivisione log: " + e.getMessage());
        }
    }


    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.log_menu, menu);
        return true;
    }

    // --- METODO AGGIORNATO PER GESTIRE TUTTI I PULSANTI DEL MENU ---
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();

        if (itemId == R.id.action_clear_log) {
            appLogger.clearLogs();
            return true;
        } else if (itemId == R.id.action_share_log) { // --- NUOVO BLOCCO ---
            shareLogFile(); // Chiamiamo il nostro nuovo metodo
            return true;
        } else if (itemId == R.id.filter_all) {
            currentFilter = "ALL";
            updateDisplayedLogs();
            return true;
        } else if (itemId == R.id.filter_info) {
            currentFilter = "Info";
            updateDisplayedLogs();
            return true;
        } else if (itemId == R.id.filter_warning) {
            currentFilter = "Warning";
            updateDisplayedLogs();
            return true;
        } else if (itemId == R.id.filter_error) {
            currentFilter = "Error";
            updateDisplayedLogs();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}