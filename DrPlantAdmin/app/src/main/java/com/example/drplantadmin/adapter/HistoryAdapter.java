package com.example.drplantadmin.adapter;


import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.drplantadmin.R;
import com.example.drplantadmin.UiUtil;
import com.example.drplantadmin.data.AdminRepository;
import com.example.drplantadmin.Models.Diagnosis;
import com.example.drplantadmin.Models.Disease;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.VH> {

    public interface Listener {
        void onOpen(Diagnosis diagnosis);
    }

    private final List<Diagnosis> items = new ArrayList<>();
    private final AdminRepository repo;
    private final Listener listener;

    public HistoryAdapter(Context context, Listener listener) {
        this.repo = AdminRepository.get(context);
        this.listener = listener;
    }

    public void submit(List<Diagnosis> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        final Diagnosis d = items.get(position);
        boolean sick = d.diseaseId != null;
        Disease disease = repo.getDisease(d.diseaseId);

        String result = "Khỏe mạnh";
        if (sick) result = disease != null ? disease.name : d.diseaseName;

        h.result.setText(result);
        h.time.setText(UiUtil.formatTime(d.time));
        UiUtil.bindStatus(h.status, sick ? AdminRepository.STATUS_SICK : AdminRepository.STATUS_HEALTHY);

        // Ảnh thật nếu có, không thì dùng icon lá làm ảnh giữ chỗ
        if (d.imagePath != null && new File(d.imagePath).exists()) {
            h.thumb.setPadding(0, 0, 0, 0);
            h.thumb.setImageURI(android.net.Uri.fromFile(new File(d.imagePath)));
        } else {
            int pad = (int) (14 * h.itemView.getResources().getDisplayMetrics().density);
            h.thumb.setPadding(pad, pad, pad, pad);
            h.thumb.setImageResource(R.drawable.ic_leaf);
        }
        h.itemView.setOnClickListener(v -> listener.onOpen(d));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView thumb;
        final TextView result, time, status;

        VH(@NonNull View v) {
            super(v);
            thumb = v.findViewById(R.id.imgThumb);
            result = v.findViewById(R.id.tvResult);
            time = v.findViewById(R.id.tvTime);
            status = v.findViewById(R.id.tvStatus);
        }
    }
}
