package esel.esel.esel.preferences;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import esel.esel.esel.R;

/**
 * Created by adrian on 04/08/17.
 */

public class Preferences extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preferences); // Imposta il nuovo layout

        Toolbar toolbar = findViewById(R.id.toolbar_preferences);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true); // Abilita il tasto Indietro
        }

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.settings_container, new PrefsFragment())
                .commit();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed(); // Gestisce il tasto Indietro nella Toolbar
        return true;
    }
}