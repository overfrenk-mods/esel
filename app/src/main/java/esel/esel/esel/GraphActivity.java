// ---------- CODICE FINALE CON LETTURA PREFERENZE ROBUSTA ----------
package esel.esel.esel;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import esel.esel.esel.services.DataMonitorService;
import esel.esel.esel.util.SP;

public class GraphActivity extends AppCompatActivity {

    private LineChart lineChart;
    private Gson gson = new Gson();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_graph);

        Toolbar toolbar = findViewById(R.id.toolbar_graph);
        setSupportActionBar(toolbar);

        // --- MODIFICA: Leggiamo le preferenze con il metodo standard ---
        if (getSupportActionBar() != null) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            String durationHours = prefs.getString("graph_duration_hours", "3");
            String title;
            if ("1".equals(durationHours)) {
                title = "Grafico Ultima Ora";
            } else {
                title = "Grafico Ultime " + durationHours + " Ore";
            }
            getSupportActionBar().setTitle(title);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        lineChart = findViewById(R.id.lineChart);
        setupChart();
        loadAndDisplayData();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private void setupChart() {
        lineChart.getDescription().setEnabled(false);
        lineChart.setDrawGridBackground(false);
        lineChart.setNoDataText("Nessun dato da visualizzare.");
        lineChart.setNoDataTextColor(Color.WHITE);
        lineChart.getLegend().setTextColor(Color.WHITE);


        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(true);
        xAxis.setTextColor(Color.WHITE);
        xAxis.setGridColor(Color.GRAY);
        xAxis.setValueFormatter(new ValueFormatter() {
            private final SimpleDateFormat mFormat = new SimpleDateFormat("HH:mm", Locale.ITALY);
            @Override
            public String getFormattedValue(float value) {
                return mFormat.format(new Date((long) value));
            }
        });

        YAxis leftAxis = lineChart.getAxisLeft();
        leftAxis.setTextColor(Color.WHITE);
        leftAxis.setGridColor(Color.GRAY);
        leftAxis.setAxisMinimum(40f);

        lineChart.getAxisRight().setEnabled(false);
    }

    private void loadAndDisplayData() {
        // --- MODIFICA: Usiamo il metodo standard anche qui per coerenza ---
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String historyJson = prefs.getString(DataMonitorService.KEY_SGV_HISTORY_JSON, "[]");

        Type listType = new TypeToken<ArrayList<DataMonitorService.SgvHistoryPoint>>() {}.getType();
        List<DataMonitorService.SgvHistoryPoint> history = gson.fromJson(historyJson, listType);

        if (history == null || history.isEmpty()) {
            lineChart.clear();
            lineChart.invalidate();
            return;
        }

        ArrayList<Entry> chartEntries = new ArrayList<>();
        for (DataMonitorService.SgvHistoryPoint point : history) {
            chartEntries.add(new Entry(point.timestamp, point.value));
        }

        LineDataSet dataSet = new LineDataSet(chartEntries, "Glicemia (mg/dL)");
        dataSet.setColor(ContextCompat.getColor(this, R.color.green_primary));
        dataSet.setCircleColor(Color.WHITE);
        dataSet.setLineWidth(2.5f);
        dataSet.setCircleRadius(4f);
        dataSet.setDrawCircleHole(true);
        dataSet.setCircleHoleRadius(2f);
        dataSet.setValueTextSize(10f);
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setDrawFilled(true);
        dataSet.setFillDrawable(ContextCompat.getDrawable(this, R.drawable.graph_fill));
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);

        LineData lineData = new LineData(dataSet);
        lineChart.setData(lineData);
        lineChart.animateX(1000);
    }
}