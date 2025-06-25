// ---------- CODICE COMPLETO E CORRETTO PER ActivationActivity.java ----------
package esel.esel.esel;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;
import java.util.Random;

import esel.esel.esel.util.EselLog;
import esel.esel.esel.util.SP;

public class ActivationActivity extends AppCompatActivity {

    private static final String TAG = "ActivationActivity";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Come primissima cosa, controlliamo se l'app è già stata sbloccata
        if (SP.getBoolean("is_app_unlocked", false)) {
            // Se sì, andiamo direttamente alla MainActivity e chiudiamo questa schermata
            unlockAppAndProceed(); // <-- MODIFICA: Corretto il nome del metodo
            return;
        }

        // Se non è sbloccata, mostriamo la nostra interfaccia di attivazione
        setContentView(R.layout.activity_activation);

        TextView textViewGeneratedCode = findViewById(R.id.textViewGeneratedCode);
        EditText editTextUnlockCode = findViewById(R.id.editTextUnlockCode);
        Button buttonUnlock = findViewById(R.id.buttonUnlock);

        // Generiamo e mostriamo il codice a 4 cifre
        String generatedCode = String.format(Locale.US, "%04d", new Random().nextInt(10000));
        textViewGeneratedCode.setText(generatedCode);
        EselLog.LogI(TAG, "Codice di attivazione generato: " + generatedCode);

        // Gestiamo il click sul pulsante "Sblocca"
        buttonUnlock.setOnClickListener(v -> {
            String userInput = editTextUnlockCode.getText().toString().trim();

            if (userInput.isEmpty()) {
                Toast.makeText(this, "Per favore, inserisci un codice.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Chiamiamo il metodo che contiene la TUA logica di verifica
            if (isUnlockCodeCorrect(generatedCode, userInput)) {
                EselLog.LogI(TAG, "Codice di sblocco corretto! App attivata.");
                Toast.makeText(this, "App attivata con successo!", Toast.LENGTH_SHORT).show();
                unlockAppAndProceed();
            } else {
                EselLog.LogW(TAG, "Codice di sblocco errato inserito.");
                Toast.makeText(this, "Codice di sblocco errato!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Contiene la TUA logica segreta per verificare il codice.
     */
    private boolean isUnlockCodeCorrect(String generatedCode, String userInput) {
        try {
            int numeroProposto = Integer.parseInt(generatedCode);
            int codiceInserito = Integer.parseInt(userInput);

            int risultatoSottrazione = 9999 - numeroProposto;
            int primiDue = Integer.parseInt(generatedCode.substring(0, 2));
            int risultatoIntermedio = primiDue * risultatoSottrazione;

            int codiceCalcolato = risultatoIntermedio % 1000;

            EselLog.LogI(TAG, "Verifica codice: Generato=" + generatedCode + ", Inserito=" + codiceInserito + ", Calcolato=" + codiceCalcolato);
            return codiceInserito == codiceCalcolato;

        } catch (NumberFormatException e) {
            EselLog.LogW(TAG, "Input non valido per il codice di sblocco.");
            return false;
        }
    }

    /**
     * Salva lo stato "sbloccato" e avvia la MainActivity.
     */
    private void unlockAppAndProceed() {
        SP.putBoolean("is_app_unlocked", true);
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish(); // Chiude l'ActivationActivity in modo che non si possa tornare indietro
    }
}