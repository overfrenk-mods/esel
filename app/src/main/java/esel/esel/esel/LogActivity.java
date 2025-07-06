// ---------- CODICE CORRETTO E FINALE PER LogActivity.java ----------
package esel.esel.esel;

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

// IMPORT RIPRISTINATO
import esel.esel.esel.util.EselLog;

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
            getSupportActionBar().setTitle(R.string.log_activity_title);
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
            textViewLogContent.setText(R.string.log_no_data_for_filter);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (String line : filteredList) {
            sb.append(line).append("\n");
        }
        textViewLogContent.setText(sb.toString());
    }

    private void shareLogFile() {
        try {
            File logFile = new File(getFilesDir(), "app_log.txt");

            if (!logFile.exists()) {
                Toast.makeText(this, R.string.log_toast_file_not_found, Toast.LENGTH_SHORT).show();
                return;
            }

            Uri logUri = FileProvider.getUriForFile(
                    this,
                    BuildConfig.APPLICATION_ID + ".provider",
                    logFile
            );

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_STREAM, logUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, getString(R.string.log_share_chooser_title)));

        } catch (Exception e) {
            Toast.makeText(this, R.string.log_toast_share_error, Toast.LENGTH_SHORT).show();
            // --- CHIAMATA DI LOG RIPRISTINATA ALLA VERSIONE CORRETTA ---
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