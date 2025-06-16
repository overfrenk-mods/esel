package esel.esel.esel;

import android.os.Bundle;
// Rimuovi import relativi al Drawer, Toolbar, NavigationView se non più usati qui
// import android.content.Intent;
// import android.view.MenuItem;
// import android.view.View;
// import android.view.Menu;
// import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity; // Mantieni questo
// import androidx.appcompat.widget.Toolbar;
// import androidx.core.view.GravityCompat;
// import androidx.drawerlayout.widget.DrawerLayout;
// import com.google.android.material.navigation.NavigationView;

// Import relativi a Preferences, LogActivity, ecc. se non usati altrove in MenuActivity
// import esel.esel.esel.preferences.Preferences;
// import esel.esel.esel.LogActivity;


/**
 * Created by adrian on 04/08/17.
 */

// Se MenuActivity non implementa più NavigationView.OnNavigationItemSelectedListener,
// rimuovi "implements NavigationView.OnNavigationItemSelectedListener"
public class MenuActivity extends AppCompatActivity { // Rimuovi il listener se non più implementato

    // Rimuovi le dichiarazioni delle variabili per Toolbar, DrawerLayout, NavigationView se non più usate qui
    // private Toolbar toolbar;
    // private DrawerLayout drawer;
    // private NavigationView navigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Se MenuActivity non ha più un suo layout, puoi rimuovere o modificare questa riga.
        // Ad esempio, setContentView(R.layout.some_other_menu_layout);
        // O rimuoverla se MenuActivity è solo una classe base astratta.
        // Se MenuActivity era la tua Activity principale che mostrava il drawer,
        // e ora MainActivity lo fa, potresti non voler mostrare nulla qui.
        // Per ora, la lascio ma tieni presente che potrebbe non essere necessaria
        // o dovrebbe puntare a un layout diverso.
        // setContentView(R.layout.activity_main); // O R.layout.some_other_layout_if_this_activity_is_still_used

        // Rimuovi il codice di setup del drawer se è stato spostato
        // toolbar = findViewById(R.id.toolbar);
        // setSupportActionBar(toolbar);
        // drawer = findViewById(R.id.drawer_layout);
        // navigationView = findViewById(R.id.nav_view);
        // ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(...);
        // drawer.addDrawerListener(toggle);
        // toggle.syncState();
        // navigationView.setNavigationItemSelectedListener(this);

        // setupView(); // Se questo metodo era legato al drawer, potresti volerlo rimuovere o modificarlo.
    }

    // Rimuovi il metodo setupView() se non più usato o se non fa nulla
    // private void setupView() { }

    // Rimuovi i metodi onBackPressed, onCreateOptionsMenu, onOptionsItemSelected, onNavigationItemSelected
    // se non sono più rilevanti per questa classe o se sono stati spostati.
    // @Override
    // public void onBackPressed() { ... }
    // @Override
    // public boolean onCreateOptionsMenu(Menu menu) { ... }
    // @Override
    // public boolean onOptionsItemSelected(MenuItem item) { ... }
    // @Override
    // public boolean onNavigationItemSelected(MenuItem item) { ... }
}