package com.example.drplantadmin.adapter;


import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.drplantadmin.R;
import com.example.drplantadmin.UiUtil;
import com.example.drplantadmin.data.AdminRepository;
import com.example.drplantadmin.Models.Diagnosis;
import com.example.drplantadmin.Models.Disease;
import com.example.drplantadmin.Models.Plant;
import com.example.drplantadmin.Models.Tree;

import java.util.ArrayList;
import java.util.List;

public class TreeAdapter extends RecyclerView.Adapter<TreeAdapter.VH> {

    public interface Listener {
        void onOpen(Tree tree);
        void onEdit(Tree tree);
        void onMove(Tree tree);
        void onDelete(Tree tree);
    }

    private final List<Tree> items = new ArrayList<>();
    private final AdminRepository repo;
    private final Listener listener;

    public TreeAdapter(Context context, Listener listener) {
        this.repo = AdminRepository.get(context);
        this.listener = listener;
    }

    public void submit(List<Tree> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_tree, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        final Tree t = items.get(position);
        Diagnosis last = repo.latest(t.id);
        int status = repo.statusOf(t.id);

        String sub = Plant.label(t.plant);
        if (status == AdminRepository.STATUS_SICK) {
            Disease d = repo.getDisease(last.diseaseId);
            sub += " · " + (d != null ? d.name : last.diseaseName);
        } else if (last != null) {
            sub += " · Chẩn đoán " + UiUtil.formatDate(last.time);
        }

        h.code.setText(t.code);
        h.sub.setText(sub);
        UiUtil.bindStatus(h.status, status);

        h.itemView.setOnClickListener(v -> listener.onOpen(t));
        h.more.setOnClickListener(v -> {
            PopupMenu menu = new PopupMenu(v.getContext(), v);
            menu.getMenu().add(0, 1, 0, "Sửa cây");
            menu.getMenu().add(0, 2, 1, "Chuyển vùng");
            menu.getMenu().add(0, 3, 2, "Xóa cây");
            menu.setOnMenuItemClickListener(item -> {
                switch (item.getItemId()) {
                    case 1: listener.onEdit(t); break;
                    case 2: listener.onMove(t); break;
                    default: listener.onDelete(t); break;
                }
                return true;
            });
            menu.show();
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView code, sub, status;
        final View more;

        VH(@NonNull View v) {
            super(v);
            code = v.findViewById(R.id.tvCode);
            sub = v.findViewById(R.id.tvSub);
            status = v.findViewById(R.id.tvStatus);
            more = v.findViewById(R.id.btnMore);
        }
    }
}
