// ---------- CODICE CON FIX DEFINITIVO PER LA CONDIVISIONE DEL LOG ----------
package esel.esel.esel;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import esel.esel.esel.util.EselLog;

public class LogActivity extends AppCompatActivity {

    private RecyclerView logRecyclerView;
    private LogAdapter logAdapter;
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
            getSupportActionBar().setTitle(R.string.log_activity_title);
        }

        setupRecyclerView();

        appLogger = AppLogger.getInstance(getApplicationContext());

        final Observer<List<String>> logObserver = newLogLines -> {
            this.allLogLines = new ArrayList<>(newLogLines);
            updateDisplayedLogs();
        };

        appLogger.getLogs().observe(this, logObserver);
    }

    private void setupRecyclerView() {
        logRecyclerView = findViewById(R.id.log_recycler_view);
        logAdapter = new LogAdapter();
        logRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        logRecyclerView.setAdapter(logAdapter);
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

        logAdapter.submitList(filteredList);
    }

    // --- METODO DI CONDIVISIONE CORRETTO ---
    private void shareLogFile() {
        List<String> completeLog = appLogger.getCompleteLogForSharing();

        if (completeLog.isEmpty()) {
            Toast.makeText(this, R.string.log_toast_file_not_found, Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // 1. Usiamo la cartella principale dei file, non la cache
            File filesPath = getFilesDir();
            // Creiamo una sottocartella "shared_logs" per pulizia (opzionale ma consigliato)
            File shareDir = new File(filesPath, "shared_logs");
            shareDir.mkdirs();
            File tempFile = new File(shareDir, "eversense_reader_log.txt");

            // 2. Scriviamo il log completo nel nostro file temporaneo
            try (FileWriter writer = new FileWriter(tempFile)) {
                for (String line : completeLog) {
                    writer.append(line).append("\n");
                }
            }

            // 3. Condividiamo il file dalla nuova posizione sicura
            Uri logUri = FileProvider.getUriForFile(
                    this,
                    BuildConfig.APPLICATION_ID + ".provider",
                    tempFile
            );

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_STREAM, logUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, getString(R.string.log_share_chooser_title)));

        } catch (IOException e) {
            Toast.makeText(this, R.string.log_toast_share_error, Toast.LENGTH_SHORT).show();
            EselLog.LogE("LogActivity", "Errore I/O durante la creazione del file di log per la condivisione: " + e.getMessage());
        } catch (Exception e) {
            Toast.makeText(this, R.string.log_toast_share_error, Toast.LENGTH_SHORT).show();
            EselLog.LogE("LogActivity", "Errore generico condivisione log: " + e.getMessage());
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

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();

        if (itemId == R.id.action_clear_log) {
            appLogger.clearLogs();
            return true;
        } else if (itemId == R.id.action_share_log) {
            shareLogFile();
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