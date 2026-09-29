package com.example.odev1;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;


import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class AnaActivity extends AppCompatActivity {

    // التحكم - تغيير لـ LinearLayout
    private LinearLayout volume_up_btn, volume_down_btn, volume_mute_btn;
    private LinearLayout screen_plus_btn, screen_mins_btn, apps_btn, folder_btn;
    private LinearLayout lock_btn, sleep_btn, restart_btn, shutdown_btn;
    private LinearLayout open_camera_btn, screenShot_btn, recShot_btn,
            capture_image_btn, close_camera_btn;

    private LinearLayout camera_controls;
    private TextView txt_device_name, txt_state, txt_time;

    // Firebase
    private DatabaseReference dbRefCommands, dbRefStatus;

    // الإنترنت
    private NetworkReceiver networkReceiver;
    private Dialog noInternetDialog;
    private String lastState = "OFF";

    private boolean isCameraOpen = false;


    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ana);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        101
                );
            }
        }

        dbRefCommands = FirebaseDatabase.getInstance().getReference("commands");
        dbRefStatus = FirebaseDatabase.getInstance().getReference("status");
        createNotificationChannel();

        noInternetDialog = new Dialog(this);
        noInternetDialog.setContentView(R.layout.dialog_on_internet);
        noInternetDialog.setCancelable(false);
        noInternetDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        // 💫 تشغيل الأنيميشن

        Button btnTryAgain = noInternetDialog.findViewById(R.id.btn_tryagain);
        Button btnExit = noInternetDialog.findViewById(R.id.btn_exit);

        btnTryAgain.setOnClickListener(v -> {

            // استخدم نفس فحص الإنترنت من NetworkReceiver
            boolean isConnected = isInternetAvailableThroughReceiver();
            View dialogRoot = noInternetDialog.findViewById(android.R.id.content);
            dialogRoot.startAnimation(AnimationUtils.loadAnimation(this, R.anim.dialog_anim));

            if (isConnected) {
                noInternetDialog.dismiss();
                recreate();
            } else {
                Toast.makeText(this, "Still no Internet!", Toast.LENGTH_SHORT).show();
            }
        });

        btnExit.setOnClickListener(v -> {
            finishAffinity(); // يغلق التطبيق كامل
        });

        networkReceiver = new NetworkReceiver(isConnected -> {
            if (!isConnected) {
                if (!noInternetDialog.isShowing()) noInternetDialog.show();
                setButtonsEnabled(false);
            } else {
                if (noInternetDialog.isShowing()) noInternetDialog.dismiss();
                setButtonsEnabled(true);
            }
        });

        initializeViews();
        setupClickListeners();
        setupStatusListener();
        startAutoRefresh();   // تحديث تلقائي كل 5 ثواني
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

    private boolean isInternetAvailableThroughReceiver() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isConnected();
    }

    // =========================================================
    //   UI & LISTENERS
    // =========================================================

    private void initializeViews() {
        // الصوت
        volume_up_btn = findViewById(R.id.volume_up);
        volume_down_btn = findViewById(R.id.volume_down);
        volume_mute_btn = findViewById(R.id.volume_mute);

        // الشاشة
        screen_plus_btn = findViewById(R.id.screen_plus);
        screen_mins_btn = findViewById(R.id.screen_mins);
        folder_btn = findViewById(R.id.btn_gallery);
        apps_btn = findViewById(R.id.btn_apps);

        // الطاقة
        lock_btn = findViewById(R.id.lock);
        sleep_btn = findViewById(R.id.sleep);
        restart_btn = findViewById(R.id.restart);
        shutdown_btn = findViewById(R.id.shutdown);

        // الكاميرا
        open_camera_btn = findViewById(R.id.open_camera);
        screenShot_btn = findViewById(R.id.screenShot);
        recShot_btn = findViewById(R.id.recShot);
        capture_image_btn = findViewById(R.id.capture_image_btn);
        close_camera_btn = findViewById(R.id.close_camera_btn);

        camera_controls = findViewById(R.id.camera_controls);

        txt_device_name = findViewById(R.id.txt_device_name);
        txt_state = findViewById(R.id.txt_state);
        txt_time = findViewById(R.id.txt_time);
    }

    private void setupClickListeners() {
        // التحكم بالصوت
        volume_up_btn.setOnClickListener(v -> checkAndSend("volume_up"));
        volume_down_btn.setOnClickListener(v -> checkAndSend("volume_down"));
        volume_mute_btn.setOnClickListener(v -> checkAndSend("mute"));

        // التحكم بالشاشة
        screen_plus_btn.setOnClickListener(v -> checkAndSend("brightness_up"));
        screen_mins_btn.setOnClickListener(v -> checkAndSend("brightness_down"));

        folder_btn.setOnClickListener(v -> {
            Intent intent = new Intent(AnaActivity.this, GalleryActivity.class);
            startActivity(intent);
        });

        apps_btn.setOnClickListener(v -> {
            Intent intent = new Intent(AnaActivity.this, RunningAppsActivity.class);
            startActivity(intent);
        });

        // الطاقة
        shutdown_btn.setOnClickListener(v -> showConfirmationDialog("shutdown", "Are you sure you want to shutdown the computer?"));
        restart_btn.setOnClickListener(v -> showConfirmationDialog("restart", "Are you sure you want to restart the computer?"));
        sleep_btn.setOnClickListener(v -> showConfirmationDialog("sleep", "Are you sure you want to sleep the computer?"));
        lock_btn.setOnClickListener(v -> showConfirmationDialog("logout", "Are you sure you want to logout the user?"));

        // الكاميرا
        open_camera_btn.setOnClickListener(v -> {
            checkAndSend("camera");
            camera_controls.setVisibility(View.VISIBLE);
            isCameraOpen = true;
        });

        screenShot_btn.setOnClickListener(v -> checkAndSend("screenshot"));
        recShot_btn.setOnClickListener(v -> checkAndSend("record"));
        capture_image_btn.setOnClickListener(v -> checkAndSend("capture_image"));

        close_camera_btn.setOnClickListener(v -> {
            checkAndSend("close_camera");
            camera_controls.setVisibility(View.GONE);
            isCameraOpen = false;
        });
    }


    // =========================================================
    //       تحديث الواجهة حسب Snapshot
    // =========================================================

    private void updateUIFromSnapshot(DataSnapshot snapshot) {
        if (snapshot == null) return;

        String computerName = snapshot.child("computer_name").getValue(String.class);
        txt_device_name.setText(computerName != null ? computerName : "Unknown Device");

        Long lastSeen = snapshot.child("last_seen").getValue(Long.class);

        long now = System.currentTimeMillis() / 1000;
        boolean online = lastSeen != null && (now - lastSeen) <= 5;

        if (online) {
            txt_state.setText("Connected");
            txt_state.setTextColor(getResources().getColor(R.color.connected_green));
            setButtonsEnabled(true);

            // uptime فقط لما يكون الجهاز شغال
            String uptime = snapshot.child("uptime").getValue(String.class);
            txt_time.setText(uptime != null ? uptime : "00:00:00");

        } else {
            txt_state.setText("Disconnected");
            txt_state.setTextColor(getResources().getColor(R.color.disconnected_red));
            setButtonsEnabled(false);

            // Last Seen بدل uptime
            if (lastSeen != null) {
                long diff = now - lastSeen;
                txt_time.setText("Last seen: " + formatLastSeen(diff));
            } else {
                txt_time.setText("Last seen: N/A");
            }
        }
        boolean isNowOnline = online;

// إذا كان الجهاز Offline ثم أصبح Online → أرسل إشعار
        if (lastState.equals("OFF") && isNowOnline) {
            showPcStartedNotification();
        }

// تحديث الحالة السابقة
        lastState = isNowOnline ? "ON" : "OFF";
    }
    private String formatLastSeen(long diffSeconds) {

        if (diffSeconds < 0) diffSeconds = 0;

        long seconds = diffSeconds % 60;
        long minutes = (diffSeconds / 60) % 60;
        long hours   = (diffSeconds / 3600) % 24;
        long days    = diffSeconds / 86400;

        // أقل من دقيقة → ثواني فقط
        if (diffSeconds < 60) {
            return seconds + " sec ago";
        }

        // أقل من ساعة → دقائق + ثواني
        if (diffSeconds < 3600) {
            return minutes + " min " + seconds + " sec ago";
        }

        // أقل من يوم → ساعات + دقائق
        if (diffSeconds < 86400) {
            return hours + " hr " + minutes + " min ago";
        }

        // يوم أو أكثر → أيام + ساعات
        return days + " day " + hours + " hr ago";
    }

    // =========================================================
    //       Firebase Listener
    // =========================================================

    private void setupStatusListener() {
        dbRefStatus.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                updateUIFromSnapshot(snapshot);

            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                txt_state.setText("Error");
                txt_state.setTextColor(getResources().getColor(R.color.disconnected_red));
            }
        });
    }


    // =========================================================
    //      Auto Refresh — تحديث إجباري كل 5 ثواني
    // =========================================================

    private void startAutoRefresh() {
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                dbRefStatus.get().addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        updateUIFromSnapshot(task.getResult());
                    }
                });
                handler.postDelayed(this, 1500);  // كل 5 ثواني
            }
        }, 3000); // أول تحديث بعد 3 ثواني
    }


    // =========================================================
    //      إرسال الأوامر بأمان
    // =========================================================

    private void checkAndSend(String command) {
        String state = txt_state.getText().toString();
        if (!state.equals("Connected")) {
            showToast("⚠ Computer is disconnected");
            return;
        }
        sendCommand(command);
    }

    private void sendCommand(String command) {
        dbRefCommands.setValue(command)
                .addOnSuccessListener(aVoid -> showToast("Command sent: " + command))
                .addOnFailureListener(e -> showToast("Failed: " + e.getMessage()));
    }


    // =========================================================
    //      تفعيل/تعطيل الأزرار مع تأثيرات بصرية
    // =========================================================

    private void setButtonsEnabled(boolean enabled) {
        float alphaVal = enabled ? 1f : 0.4f;

        // جميع الأزرار (LinearLayout)
        LinearLayout[] buttons = {
                volume_up_btn, volume_down_btn, volume_mute_btn,
                screen_plus_btn, screen_mins_btn, apps_btn,
                lock_btn, sleep_btn, restart_btn, shutdown_btn,
                open_camera_btn, screenShot_btn, recShot_btn,
                folder_btn, capture_image_btn, close_camera_btn
        };

        for (LinearLayout btn : buttons) {
            btn.setEnabled(enabled);
            btn.setAlpha(alphaVal);
            btn.setClickable(enabled);

            // تأثير بسيط عند التعطيل
            if (!enabled) {
                btn.animate().scaleX(0.95f).scaleY(0.95f).setDuration(300).start();
            } else {
                btn.animate().scaleX(1f).scaleY(1f).setDuration(300).start();
            }
        }

        camera_controls.setAlpha(alphaVal);
        camera_controls.setEnabled(enabled);
        camera_controls.setClickable(enabled);
    }


    // =========================================================
    //   واجهة مستخدم — Toast + Dialogs
    // =========================================================

    private void showConfirmationDialog(String command, String message) {
        new AlertDialog.Builder(this)
                .setTitle("Confirmation")
                .setMessage(message)
                .setPositiveButton("Yes", (dialog, which) -> sendCommand(command))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showToast(String msg) {
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show();
    }
    // قسم الاشعارات
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "pc_alerts",
                    "PC Notifications",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for PC status changes");
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }
    private void showPcStartedNotification() {

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "pc_alerts")
                .setSmallIcon(R.drawable.ic_notifications) // حط أيقونة عندك
                .setContentTitle("PC Online")
                .setContentText("Your computer has just turned ON.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManagerCompat manager = NotificationManagerCompat.from(this);
        Log.d("NOTIF_TEST", "🔥 Notification SHOULD appear now!");


        // ✅ فحص صلاحية الإشعار لأندرويد 13 وما فوق
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            // هون يا بتطلب الصلاحية من المستخدم، يا بتكتفي بالإلغاء حالياً
            return;
        }

        manager.notify(1001, builder.build());
    }



    // =========================================================
    //     العودة للخلف
    // =========================================================

    @Override
    public void onBackPressed() {
        if (isCameraOpen) {
            sendCommand("close_camera");
            camera_controls.setVisibility(View.GONE);
            isCameraOpen = false;
        } else {
            super.onBackPressed();
        }
    }
}