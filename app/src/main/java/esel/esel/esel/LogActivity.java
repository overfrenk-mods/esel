// Codice da incollare in LogActivity.java
package esel.esel.esel;

import android.os.Bundle;
import android.widget.TextView;
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
        // Usa il nuovo layout che abbiamo definito
        setContentView(R.layout.activity_errors);

        // Imposta la Toolbar e il tasto "Indietro"
        Toolbar toolbar = findViewById(R.id.toolbar_log);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Log Applicazione");
        }

        textViewLogContent = findViewById(R.id.textview_log_content);

        // Ottieni l'istanza del nostro logger
        appLogger = AppLogger.getInstance(getApplicationContext());

        // Imposta un Observer per ricevere i log e mostrarli nella TextView
        // Questo è lo stesso meccanismo sicuro che avevamo preparato in passato
        final Observer<List<String>> logObserver = logLines -> {
            if (logLines == null || logLines.isEmpty()) {
                textViewLogContent.setText("Nessun log da mostrare.");
                return;
            }
            // Uniamo tutte le righe di log in un unico testo
            StringBuilder sb = new StringBuilder();
            for (String line : logLines) {
                sb.append(line).append("\n");
            }
            textViewLogContent.setText(sb.toString());
        };

        // Collega l'observer al LiveData dei log
        appLogger.getLogs().observe(this, logObserver);
    }

    // Gestisce il click sul tasto "Indietro" nella Toolbar
    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed(); // Torna alla schermata precedente
        return true;
    }
}