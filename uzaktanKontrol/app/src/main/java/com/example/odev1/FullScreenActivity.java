package com.example.odev1;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.github.chrisbanes.photoview.PhotoView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FullScreenActivity extends AppCompatActivity {

    private String image_url, imageKey, imageType;
    private ProgressBar progressDelete;
    private MaterialCardView cardInfo;
    private FloatingActionButton fabInfo;
    private Dialog noInternetDialog;
    private NetworkReceiver networkReceiver;
    private boolean infoVisible = false;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fullscreen_image);

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
        networkReceiver = new NetworkReceiver(isConnected -> {
            if (!isConnected) {
                if (!noInternetDialog.isShowing()) noInternetDialog.show();
            } else {
                if (noInternetDialog.isShowing()) noInternetDialog.dismiss();
            }
        });

        image_url = getIntent().getStringExtra("imageUrl");
        imageKey = getIntent().getStringExtra("imageKey");
        imageType = getIntent().getStringExtra("imageType");

        PhotoView photoView = findViewById(R.id.full_image);
        MaterialButton downloadBtn = findViewById(R.id.btnDownload);
        MaterialButton deleteBtn = findViewById(R.id.btnDelete);
        progressDelete = findViewById(R.id.progressDelete);
        cardInfo = findViewById(R.id.card_info);
        fabInfo = findViewById(R.id.fab_info);

        // تحميل الصورة
        Glide.with(this)
                .load(image_url)
                .into(photoView);

        // إعداد معلومات الصورة
        setupImageInfo();

        // أحداث الأزرار
        deleteBtn.setOnClickListener(v -> showDeleteConfirmDialog());
        downloadBtn.setOnClickListener(v -> showDownloadConfirmDialog());
        fabInfo.setOnClickListener(v -> toggleInfoCard());

        // النقر على الصورة لإخفاء/إظهار المعلومات
        photoView.setOnClickListener(v -> {
            if (infoVisible) {
                cardInfo.setVisibility(View.GONE);
                infoVisible = false;
            }
        });
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

    private void setupImageInfo() {
        // يمكنك إضافة معلومات إضافية هنا مثل الحجم، الدقة، إلخ
        String currentTime = new SimpleDateFormat("HH:mm", Locale.getDefault())
                .format(new Date());

        // مثال: يمكنك حساب حجم الصورة أو الحصول على معلوماتها
        // هذا يحتاج لـ API call إضافي
    }

    private void toggleInfoCard() {
        if (infoVisible) {
            cardInfo.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                cardInfo.setVisibility(View.GONE);
            }).start();
            infoVisible = false;
        } else {
            cardInfo.setAlpha(0f);
            cardInfo.setVisibility(View.VISIBLE);
            cardInfo.animate().alpha(1f).setDuration(300).start();
            infoVisible = true;
        }
    }

    private void downloadImage() {
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(image_url));
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(
                Environment.DIRECTORY_PICTURES,
                "PC_Image_" + System.currentTimeMillis() + ".jpg"
        );
        request.setTitle("Downloading Image");
        request.setDescription("Image from PC Remote Control");

        DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        manager.enqueue(request);

        Toast.makeText(FullScreenActivity.this, "📥 Download started", Toast.LENGTH_SHORT).show();
    }

    private void deleteImage() {
        progressDelete.setVisibility(View.VISIBLE);

        DatabaseReference dbRef = FirebaseDatabase.getInstance()
                .getReference("images/" + imageType)
                .child(imageKey);

        dbRef.removeValue().addOnSuccessListener(unused -> {
            StorageReference storageRef = FirebaseStorage.getInstance()
                    .getReferenceFromUrl(image_url);

            storageRef.delete().addOnSuccessListener(unused2 -> {
                Toast.makeText(this, "🗑 Image deleted successfully", Toast.LENGTH_SHORT).show();
                progressDelete.setVisibility(View.GONE);
                finish(); // العودة للمعرض
            }).addOnFailureListener(e -> {
                Toast.makeText(this, "❌ Storage delete failed", Toast.LENGTH_SHORT).show();
                progressDelete.setVisibility(View.GONE);
            });
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "❌ Database delete failed", Toast.LENGTH_SHORT).show();
            progressDelete.setVisibility(View.GONE);
        });
    }

    private void showDeleteConfirmDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Image")
                .setMessage("Are you sure you want to delete this image?")
                .setPositiveButton("Yes", (dialog, which) -> deleteImage())
                .setNegativeButton("No", null)
                .setCancelable(true)
                .show();
    }

    private void showDownloadConfirmDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Download Image")
                .setMessage("Do you want to save this image to your device?")
                .setPositiveButton("Yes", (dialog, which) -> downloadImage())
                .setNegativeButton("No", null)
                .setCancelable(true)
                .show();
    }


    @Override
    public void onBackPressed() {
        if (infoVisible) {
            cardInfo.setVisibility(View.GONE);
            infoVisible = false;
        } else {
            super.onBackPressed();
        }
    }
}