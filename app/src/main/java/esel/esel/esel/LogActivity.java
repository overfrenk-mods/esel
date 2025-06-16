// ---------------- INIZIO CODICE PER LogActivity.java ----------------
package esel.esel.esel;

import android.os.Bundle;
import android.widget.TextView;
import androidx.lifecycle.Observer;
import java.util.List;

public class LogActivity extends MenuActivity {

    private TextView textViewValue;
    private AppLogger appLogger;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_errors);

        textViewValue = findViewById(R.id.textview_main);

        // Ottieni l'istanza del nostro nuovo gestore di log
        appLogger = AppLogger.getInstance(getApplicationContext());

        // Imposta un Observer sul LiveData dei log.
        // Questo blocco di codice verrà eseguito automaticamente ogni volta che un nuovo log viene aggiunto,
        // in modo sicuro e senza rischi di crash, anche se il log viene da un servizio in background.
        final Observer<List<String>> logObserver = new Observer<List<String>>() {
            @Override
            public void onChanged(List<String> logLines) {
                // Unisci la lista di stringhe in un unico testo per la TextView
                // Usiamo StringBuilder per efficienza
                if (logLines == null) return;
                StringBuilder sb = new StringBuilder();
                for (String line : logLines) {
                    sb.append(line).append("\n");
                }
                textViewValue.setText(sb.toString());
            }
        };

        // Collega l'observer al LiveData.
        // Android gestirà automaticamente la rimozione dell'observer quando l'activity viene distrutta,
        // prevenendo memory leak.
        appLogger.getLogs().observe(this, logObserver);
    }
}
// ---------------- FINE CODICE PER LogActivity.java ----------------