package esel.esel.esel;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import esel.esel.esel.util.SP;

// LogActivity estende MenuActivity. Assicurati che MenuActivity sia la superclasse corretta.
// LogActivity necessita di estendere AppCompatActivity (direttamente o indirettamente)
// per funzionare correttamente in AndroidX. Poiché estende MenuActivity,
// e MenuActivity estende AppCompatActivity, questo è corretto.

public class LogActivity extends MenuActivity { // Mantieni LogActivity che estende MenuActivity

    private static TextView textViewValue; // Nota: static TextView può causare memory leak o NPE se l'Activity viene distrutta.

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // ********************************************************************************
        // CORREZIONE: Imposta il layout usando setContentView()
        // ********************************************************************************
        setContentView(R.layout.activity_errors); // Questo è il modo standard per impostare il layout

        textViewValue = (TextView) findViewById(R.id.textview_main);
        String msg = SP.getString("logging","");
        textViewValue.setText(msg);
    }

    public static void addLog(String type,String tag, String value){
        String msg = SP.getString("logging","");
        int lines_limit = 800;
        String[] lines = msg.split("\n");
        if(lines.length>lines_limit){
            int limit_to = (int)(lines_limit * 0.7);
            StringBuilder strbuild = new StringBuilder();
            for (int i = 0; i<limit_to; i++){
                strbuild = new StringBuilder(strbuild + lines[i] + "\n");
            }
            msg = strbuild.toString();
        }
        LocalDateTime currentTime = LocalDateTime.now();
        DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String line = currentTime.format(format) + ": "+type + " "  +value;
        msg = line + "\n" + msg;
        SP.putString("logging",msg);

        // ********************************************************************************
        // Nota: Questo aggiornamento di textViewValue da un metodo statico può causare problemi
        // (NullPointerException) se l'Activity non è attiva o viene distrutta.
        // ********************************************************************************
        if(textViewValue != null){
            textViewValue.setText(msg);
        }
    }

}