// ---------- CODICE CON LOGICA DI COLORAZIONE DELLE RIGHE ----------
package esel.esel.esel;

import android.content.Context;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class LogAdapter extends RecyclerView.Adapter<LogAdapter.LogViewHolder> {

    // NOTA: Usare DiffUtil sarebbe più efficiente di notifyDataSetChanged(),
    // ma per un log viewer dove i filtri cambiano l'intera lista,
    // questo approccio è semplice e funzionale.
    private final List<String> logLines = new ArrayList<>();

    @NonNull
    @Override
    public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.log_item, parent, false);
        return new LogViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
        holder.bind(logLines.get(position));
    }

    @Override
    public int getItemCount() {
        return logLines.size();
    }

    public void submitList(List<String> newLogs) {
        logLines.clear();
        logLines.addAll(newLogs);
        notifyDataSetChanged();
    }

    static class LogViewHolder extends RecyclerView.ViewHolder {
        private final TextView logLineText;

        public LogViewHolder(@NonNull View itemView) {
            super(itemView);
            logLineText = itemView.findViewById(R.id.log_line_text);
        }

        // --- MODIFICA INIZIO: Logica di colorazione implementata qui ---
        public void bind(String logLine) {
            logLineText.setText(logLine);

            Context context = itemView.getContext();
            int color;

            if (logLine.contains("[E]")) {
                color = ContextCompat.getColor(context, R.color.log_error);
            } else if (logLine.contains("[RESTART]")) {
                color = ContextCompat.getColor(context, R.color.log_restart);
            } else if (logLine.contains("[W]")) {
                color = ContextCompat.getColor(context, R.color.log_warning);
            } else {
                // Per il colore di default, lo prendiamo dal tema corrente dell'app
                // in modo che funzioni correttamente sia in tema chiaro che scuro.
                TypedValue typedValue = new TypedValue();
                context.getTheme().resolveAttribute(android.R.attr.textColorPrimary, typedValue, true);
                color = ContextCompat.getColor(context, typedValue.resourceId);
            }

            logLineText.setTextColor(color);
        }
        // --- MODIFICA FINE ---
    }
}