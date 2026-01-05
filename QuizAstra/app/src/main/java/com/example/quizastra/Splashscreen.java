package com.example.quizastra;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;

public class Splashscreen extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set your layout
        setContentView(R.layout.activity_splashscreen);

        // Request notification permission on Android 13+
        maybeRequestPostNotifications();

        // Splash delay (e.g., 2 seconds)
        new Handler().postDelayed(() -> {
            // Move to login/main activity
            Intent intent = new Intent(Splashscreen.this, UserLogin.class);
            startActivity(intent);
            finish();
        }, 2000); // 2 seconds
    }

    private void maybeRequestPostNotifications() {
        // Use numeric API level and string literal to avoid requiring compileSdk 33 constant
        if (Build.VERSION.SDK_INT >= 33) {
            final String POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS";
            if (ContextCompat.checkSelfPermission(this, POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{POST_NOTIFICATIONS}, 1000);
            }
        }
    }
}
