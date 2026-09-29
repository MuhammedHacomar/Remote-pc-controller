package com.example.odev1;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.FirebaseDatabase;

import java.util.List;

public class AppAdapter extends RecyclerView.Adapter<AppAdapter.ViewHolder> {

    private Context context;
    private List<AppModel> data;

    public AppAdapter(Context c, List<AppModel> data) {
        this.context = c;
        this.data = data;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.app_item, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppModel app = data.get(position);

// اسم التطبيق (بدون استخدام الـ title أبداً)
        holder.txtName.setText(
                app.getName() != null && !app.getName().isEmpty()
                        ? app.getName()
                        : "Unknown"
        );

// مسح أي صورة سابقة نهائياً
        holder.app_icon.setImageDrawable(null);

        if (app.getIcon() != null && !app.getIcon().isEmpty()) {
            Glide.with(context)
                    .load(app.getIcon())
                    .placeholder(null)                 // بدون صورة مؤقتة
                    .error(R.drawable.application)     // إذا فشل التحميل
                    .dontAnimate()                     // منع أي Animation تسبب ترميش
                    .into(holder.app_icon);
        } else {
            holder.app_icon.setImageResource(R.drawable.application);
        }

        // 🔥 Memory
        if (app.getMemory() != null && !app.getMemory().isEmpty()) {
            holder.txtMemory.setText(app.getMemory());
        } else {
            holder.txtMemory.setText("-- MB");
        }

        // 🔥 CPU — لازم نضمن ما نكرر علامة %
        if (app.getCpu() != null && !app.getCpu().isEmpty()) {
            String cpu = app.getCpu();
            if (cpu.contains("%")) {
                holder.txtCpu.setText(cpu);  // جاهزة
            } else {
                holder.txtCpu.setText(cpu + "%");
            }
        } else {
            holder.txtCpu.setText("--%");
        }

        // 🔥 زر إيقاف التطبيق
        holder.btnKill.setOnClickListener(v -> {
            FirebaseDatabase.getInstance()
                    .getReference("commands")
                    .setValue("kill:" + app.getPid());
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView app_icon;
        TextView txtName, txtMemory, txtCpu;
        MaterialButton btnKill;

        public ViewHolder(View v) {
            super(v);
            app_icon = v.findViewById(R.id.app_icon);
            txtName = v.findViewById(R.id.txtName);
            txtMemory = v.findViewById(R.id.txtMemory);
            txtCpu = v.findViewById(R.id.txtCpu);
            btnKill = v.findViewById(R.id.btnKill);
        }
    }

    // دالة لتحديث البيانات
    public void updateData(List<AppModel> newData) {
        data.clear();
        data.addAll(newData);
        notifyDataSetChanged();
    }
}