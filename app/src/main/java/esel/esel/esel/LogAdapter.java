// File: esel/esel/esel/LogAdapter.java
package esel.esel.esel;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class LogAdapter extends RecyclerView.Adapter<LogAdapter.LogViewHolder> {

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

    // Metodo per aggiornare la lista di log e notificare al RecyclerView di ridisegnarsi
    public void submitList(List<String> newLogs) {
        logLines.clear();
        logLines.addAll(newLogs);
        notifyDataSetChanged(); // Dice al RecyclerView che i dati sono cambiati
    }

    // ViewHolder: tiene in memoria i riferimenti agli elementi della UI di una singola riga
    static class LogViewHolder extends RecyclerView.ViewHolder {
        private final TextView logLineText;

        public LogViewHolder(@NonNull View itemView) {
            super(itemView);
            logLineText = itemView.findViewById(R.id.log_line_text);
        }

        public void bind(String logLine) {
            logLineText.setText(logLine);
        }
    }
}