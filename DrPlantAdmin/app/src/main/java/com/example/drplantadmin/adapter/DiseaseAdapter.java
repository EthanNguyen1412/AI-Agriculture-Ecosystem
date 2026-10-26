package com.example.drplantadmin.adapter;


import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.drplantadmin.R;
import com.example.drplantadmin.Models.Disease;
import com.example.drplantadmin.Models.Plant;
import java.util.ArrayList;
import java.util.List;

public class DiseaseAdapter extends RecyclerView.Adapter<DiseaseAdapter.VH> {

    public interface Listener {
        void onOpen(Disease disease);
    }

    private final List<Disease> items = new ArrayList<>();
    private final Listener listener;

    public DiseaseAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<Disease> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_disease, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        final Disease d = items.get(position);
        h.name.setText(d.name);
        h.plant.setText(Plant.label(d.plant) + (d.builtIn ? " · Bệnh gốc của model" : ""));
        h.symptoms.setText(d.symptoms == null ? "" : d.symptoms);
        h.itemView.setOnClickListener(v -> listener.onOpen(d));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView name, plant, symptoms;

        VH(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.tvName);
            plant = v.findViewById(R.id.tvPlant);
            symptoms = v.findViewById(R.id.tvSymptoms);
        }
    }
}