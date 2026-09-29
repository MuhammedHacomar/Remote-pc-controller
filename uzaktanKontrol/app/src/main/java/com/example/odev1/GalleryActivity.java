package com.example.odev1;

import android.app.Dialog;
import android.content.Context;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class GalleryActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ImageAdapter adapter;
    TextView tv_total_image, tv_total_screenshot;
    int cameraCount = 0;
    int screenshotCount = 0;
    private Dialog noInternetDialog;
    private NetworkReceiver networkReceiver;
    private List<ImageModel> imageUrls = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);


        recyclerView = findViewById(R.id.rv_images);
        tv_total_image = findViewById(R.id.tv_total_image);
        tv_total_screenshot = findViewById(R.id.tv_total_screenshot);

        // تغيير GridLayoutManager لـ 2 أعمدة
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        recyclerView.setHasFixedSize(true);

        adapter = new ImageAdapter(this, imageUrls);
        recyclerView.setAdapter(adapter);
        noInternetDialog = new Dialog(this);
        noInternetDialog.setContentView(R.layout.dialog_on_internet);
        noInternetDialog.setCancelable(false);
        noInternetDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        View dialogRoot = noInternetDialog.findViewById(android.R.id.content);
        dialogRoot.startAnimation(AnimationUtils.loadAnimation(this, R.anim.dialog_anim));
        Button btnTryAgain = noInternetDialog.findViewById(R.id.btn_tryagain);
        Button btnExit = noInternetDialog.findViewById(R.id.btn_exit);


        btnTryAgain.setOnClickListener(v -> {
            recreate(); // يعيد تحميل النشاط ويحاول يتصل مرة ثانية
        });

        btnExit.setOnClickListener(v -> {
            finishAffinity(); // يغلق التطبيق كامل
        });
        networkReceiver = new NetworkReceiver(isConnected ->{
            if(!isConnected){
                if (!noInternetDialog.isShowing()) noInternetDialog.show();
            } else {
                if (noInternetDialog.isShowing()) noInternetDialog.dismiss();
            }
        });
        loadImagesFromFirebase();
    }
    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(networkReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(networkReceiver);
    }

    private void loadImagesFromFirebase() {
        DatabaseReference refCamera = FirebaseDatabase.getInstance().getReference("images/camera");
        DatabaseReference refScreenshots = FirebaseDatabase.getInstance().getReference("images/screenshots");

        imageUrls.clear();
        screenshotCount = 0;
        cameraCount = 0;

        // ----- CAMERA IMAGES -----
        refCamera.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot img : snapshot.getChildren()) {
                    String url = img.child("url").getValue(String.class);
                    String filename = img.child("filename").getValue(String.class);
                    Long timestamp = img.child("timestamp").getValue(Long.class);

                    if (url != null) {
                        imageUrls.add(new ImageModel(url, filename, timestamp, img.getKey(), "camera"));
                        cameraCount++;
                    }
                }
                tv_total_image.setText(String.valueOf(cameraCount));
                // تحديث جزئي بعد تحميل الكاميرا
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("GalleryActivity", "Camera Error: " + error.getMessage());
            }
        });

        // ----- SCREENSHOTS -----
        refScreenshots.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot img : snapshot.getChildren()) {
                    String url = img.child("url").getValue(String.class);
                    String filename = img.child("filename").getValue(String.class);
                    Long timestamp = img.child("timestamp").getValue(Long.class);

                    if (url != null) {
                        imageUrls.add(new ImageModel(url, filename, timestamp, img.getKey(), "screenshots"));
                        screenshotCount++;
                    }
                }
                tv_total_screenshot.setText(String.valueOf(screenshotCount));
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("GalleryActivity", "Screenshot Error: " + error.getMessage());
            }
        });
    }

}