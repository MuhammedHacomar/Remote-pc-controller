package com.example.odev1;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.WindowManager;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_splash);
        //progressBar gosterme
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        // ekranı full ekran yapma
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        // 1.5 sanniye
        new Handler().postDelayed(() -> {
            Intent intent = new Intent(SplashActivity.this, LoadingActivity.class);
            startActivity(intent);
            finish();
        }, 1500);
    }
}