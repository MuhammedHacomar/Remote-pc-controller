package com.example.odev1;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class LoadingActivity extends AppCompatActivity {

    private ProgressBar pbLoading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loading);

        pbLoading = findViewById(R.id.pbLoading);

        if (!isInternetAvailable()) {
            showInternetDialog();
            return;
        }

        loadFirebaseData();
    }

    private void loadFirebaseData() {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("images");

        ref.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                startActivity(new Intent(LoadingActivity.this, AnaActivity.class)); // الصفحة الرئيسية
                finish();
            } else {
                Toast.makeText(this, "❌ Error loading data", Toast.LENGTH_SHORT).show();
            }
        });
    }
    private boolean isInternetAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnected();
    }
    private void showInternetDialog() {
        new AlertDialog.Builder(this)
                .setTitle("No Internet Connection ⚠️")
                .setMessage("Please connect to Wi-Fi or mobile data")
                .setCancelable(false)
                .setPositiveButton("Try Again", (dialog, which) -> recreate())
                .setNegativeButton("Exit", (dialog, which) -> finish())
                .show();
    }
}