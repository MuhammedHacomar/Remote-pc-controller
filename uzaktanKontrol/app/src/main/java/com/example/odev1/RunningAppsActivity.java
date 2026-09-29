package com.example.odev1;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class RunningAppsActivity extends AppCompatActivity {

    private RecyclerView rv;
    private AppAdapter adapter;
    private List<AppModel> list = new ArrayList<>();
    private DatabaseReference refApps, refCmd;
    private TextView tv_running_count, tv_memory_usage;
    private Dialog noInternetDialog;
    private NetworkReceiver networkReceiver;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_running_apps);

        rv = findViewById(R.id.rv_apps);
        tv_running_count = findViewById(R.id.tv_running_count);
        tv_memory_usage = findViewById(R.id.tv_memory_usage);

        // استخدام LinearLayoutManager مع Divider
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        rv.setLayoutManager(layoutManager);
        rv.setHasFixedSize(true);

        adapter = new AppAdapter(this, list);
        rv.setAdapter(adapter);
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

        refApps = FirebaseDatabase.getInstance().getReference("running_apps");
        refCmd = FirebaseDatabase.getInstance().getReference("commands");

        loadApps();
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

    private void loadApps() {
        refApps.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                list.clear();
                int totalApps = 0;
                double totalMemory = 0;

                for (DataSnapshot d : snapshot.getChildren()) {
                    AppModel model = d.getValue(AppModel.class);
                    if (model != null) {
                        list.add(model);
                        totalApps++;

                        // 🔥 حساب الذاكرة
                        try {
                            if (model.getMemory() != null) {
                                String memStr = model.getMemory().replace("MB", "").trim();
                                double memVal = Double.parseDouble(memStr);
                                totalMemory += memVal;
                            }
                        } catch (Exception ignored) {}
                    }
                }

                // 🔥 عدد التطبيقات
                tv_running_count.setText(String.valueOf(totalApps));

                // 🔥 إجمالي استخدام الذاكرة
                tv_memory_usage.setText(String.format("%.0f MB", totalMemory));

                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // طلب تحديث للتطبيقات عند العودة للنشاط
        refCmd.setValue("get_apps");
    }
}