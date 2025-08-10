// ---------- CODICE CON FILTRO RESTART CORRETTO COME RICERCA PREIMPOSTATA ----------
package esel.esel.esel;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;
import com.google.android.material.textfield.TextInputEditText;

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
import java.util.stream.Stream;

import esel.esel.esel.util.EselLog;

public class LogActivity extends AppCompatActivity {

    private RecyclerView logRecyclerView;
    private LogAdapter logAdapter;
    private AppLogger appLogger;

    private TextInputEditText searchEditText;
    private List<String> allLogLines = new ArrayList<>();
    private String currentLevelFilter = "ALL";
    private String currentSearchTerm = "";

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
        setupSearch();

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

    private void setupSearch() {
        searchEditText = findViewById(R.id.log_search_edit_text);
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchTerm = s.toString();
                updateDisplayedLogs();
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });
    }

    private void updateDisplayedLogs() {
        Stream<String> stream = allLogLines.stream();

        if (!currentLevelFilter.equals("ALL")) {
            stream = stream.filter(line -> line.contains("[" + currentLevelFilter + "]"));
        }

        if (!currentSearchTerm.isEmpty()) {
            String lowerCaseTerm = currentSearchTerm.toLowerCase();
            stream = stream.filter(line -> line.toLowerCase().contains(lowerCaseTerm));
        }

        List<String> filteredList = stream.collect(Collectors.toList());

        logAdapter.submitList(filteredList);
        if (!filteredList.isEmpty()) {
            logRecyclerView.scrollToPosition(0);
        }
    }

    private void shareLogFile() {
        List<String> completeLog = appLogger.getCompleteLogForSharing();

        if (completeLog.isEmpty()) {
            Toast.makeText(this, R.string.log_toast_file_not_found, Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            File filesPath = getFilesDir();
            File shareDir = new File(filesPath, "shared_logs");
            shareDir.mkdirs();
            File tempFile = new File(shareDir, "eversense_reader_log.txt");

            try (FileWriter writer = new FileWriter(tempFile)) {
                for (String line : completeLog) {
                    writer.append(line).append("\n");
                }
            }

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
        // Reset della ricerca testuale quando si usa un filtro dal menu,
        // tranne per il caso speciale del filtro "Restart".
        if (item.getItemId() != R.id.filter_restart) {
            searchEditText.setText("");
            currentSearchTerm = "";
        }

        int itemId = item.getItemId();

        if (itemId == R.id.action_clear_log) {
            appLogger.clearLogs();
            return true;
        } else if (itemId == R.id.action_share_log) {
            shareLogFile();
            return true;
        } else if (itemId == R.id.filter_all) {
            currentLevelFilter = "ALL";
            updateDisplayedLogs();
            return true;
        } else if (itemId == R.id.filter_info) {
            currentLevelFilter = "I";
            updateDisplayedLogs();
            return true;
        } else if (itemId == R.id.filter_warning) {
            currentLevelFilter = "W";
            updateDisplayedLogs();
            return true;
        } else if (itemId == R.id.filter_error) {
            currentLevelFilter = "E";
            updateDisplayedLogs();
            return true;
        } else if (itemId == R.id.filter_verbose) {
            currentLevelFilter = "V";
            updateDisplayedLogs();
            return true;
            // --- MODIFICA INIZIO: Il filtro Restart ora è una ricerca preimpostata ---
        } else if (itemId == R.id.filter_restart) {
            currentLevelFilter = "ALL"; // Mostra tutti i livelli...
            currentSearchTerm = "[RESTART]"; // ...che contengono la parola [RESTART]
            searchEditText.setText(currentSearchTerm); // Mostra il testo nella barra di ricerca
            // updateDisplayedLogs() viene già chiamato dal TextWatcher di searchEditText
            return true;
        }
        // --- MODIFICA FINE ---

        return super.onOptionsItemSelected(item);
    }
}