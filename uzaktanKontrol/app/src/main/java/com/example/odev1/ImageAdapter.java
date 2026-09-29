package com.example.odev1;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ImageViewHolder> {

    private List<ImageModel> images;
    private Context context;

    public ImageAdapter(Context context, List<ImageModel> images) {
        this.context = context;
        this.images = images;
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_image, parent, false);
        return new ImageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        ImageModel img = images.get(position);

        // تحميل الصورة مع تأثير انتقال
        Glide.with(context)
                .load(img.getUrl())
                .placeholder(R.drawable.image_placeholder) // إضافة placeholder
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(holder.imageView);

        // تنسيق التاريخ
        String date = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                .format(img.getTimestamp() * 1000L);
        holder.date.setText(date);

        // عرض اسم الملف
        String imgName = img.getFilename();
        if (imgName.length() > 25) {
            imgName = imgName.substring(0, 22) + "...";
        }
        holder.image_name.setText(imgName);

        // عرض نوع الصورة
        String typeText = img.getType().equals("camera") ? "CAMERA" : "SCREENSHOT";
        holder.type.setText(typeText);

        // فتح الصورة بحجم كامل
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, FullScreenActivity.class);
            intent.putExtra("imageUrl", img.getUrl());
            intent.putExtra("imageKey", img.getKey());
            intent.putExtra("imageType", img.getType());
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return images.size();
    }

    public static class ImageViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        TextView date, image_name, type;

        public ImageViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.image_item);
            date = itemView.findViewById(R.id.tv_date);
            image_name = itemView.findViewById(R.id.tv_img_name);
            type = itemView.findViewById(R.id.tv_type); // إضافة هذا
        }
    }
}