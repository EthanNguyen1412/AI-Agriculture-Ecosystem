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
import com.example.drplantadmin.data.AdminRepository;
import com.example.drplantadmin.Models.Plant;
import com.example.drplantadmin.Models.Region;
import java.util.ArrayList;
import java.util.List;

public class RegionAdapter extends RecyclerView.Adapter<RegionAdapter.VH> {

    public interface Listener {
        void onOpen(Region region);
        void onEdit(Region region);
        void onDelete(Region region);
    }

    private final List<Region> items = new ArrayList<>();
    private final AdminRepository repo;
    private final Listener listener;

    public RegionAdapter(Context context, Listener listener) {
        this.repo = AdminRepository.get(context);
        this.listener = listener;
    }

    public void submit(List<Region> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_region, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        final Region r = items.get(position);
        int[] s = repo.stats(r.id);
        h.name.setText(r.name);
        h.location.setText(r.location);
        h.meta.setText(Plant.label(r.plant) + " · " + s[0] + " cây · " + s[1] + " cây bệnh");
        h.itemView.setOnClickListener(v -> listener.onOpen(r));
        h.more.setOnClickListener(v -> {
            PopupMenu menu = new PopupMenu(v.getContext(), v);
            menu.getMenu().add(0, 1, 0, "Sửa vùng");
            menu.getMenu().add(0, 2, 1, "Xóa vùng");
            menu.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) listener.onEdit(r);
                else listener.onDelete(r);
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
        final TextView name, location, meta;
        final View more;

        VH(@NonNull View v) {
            super(v);
            name = v.findViewById(R.id.tvName);
            location = v.findViewById(R.id.tvLocation);
            meta = v.findViewById(R.id.tvMeta);
            more = v.findViewById(R.id.btnMore);
        }
    }
}
