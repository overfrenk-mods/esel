// ---------- CODICE COMPLETO E AGGIORNATO PER LogActivity.java ----------
package esel.esel.esel;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.Observer;
import java.util.List;

public class LogActivity extends AppCompatActivity {

    private TextView textViewLogContent;
    private AppLogger appLogger;

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

        final Observer<List<String>> logObserver = logLines -> {
            if (logLines == null || logLines.isEmpty()) {
                textViewLogContent.setText("Nessun log da mostrare.");
                return;
            }
            StringBuilder sb = new StringBuilder();
            for (String line : logLines) {
                sb.append(line).append("\n");
            }
            textViewLogContent.setText(sb.toString());
        };

        appLogger.getLogs().observe(this, logObserver);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    // --- NUOVI METODI PER GESTIRE IL MENU ---
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.log_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_clear_log) {
            // Quando l'utente clicca sul cestino, chiamiamo il nostro nuovo metodo
            appLogger.clearLogs();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}