package com.oneui.fontviewer.activity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.PathInterpolator;

import androidx.appcompat.app.AppCompatActivity;

import com.oneui.fontviewer.R;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DELAY = 800; // مدة الانتظار قبل بدء حركة الخروج
    private static final int ANIM_DURATION = 500; // مدة حركة الخروج

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        View splashIcon = findViewById(R.id.splash_icon);
        PathInterpolator oneEasingInterpolator = new PathInterpolator(0.22f, 0.25f, 0f, 1f);

        // الانتظار قليلاً ثم تشغيل أنيميشن خروج الأيقونة والانتقال
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            
            splashIcon.animate()
                    .scaleX(0.80f)
                    .scaleY(0.80f)
                    .alpha(0f)
                    .setDuration(ANIM_DURATION)
                    .setInterpolator(oneEasingInterpolator)
                    .withEndAction(this::startMainActivity)
                    .start();

        }, SPLASH_DELAY);
    }

    private void startMainActivity() {
        Intent intent = new Intent(SplashActivity.this, MainActivity.class);
        
        // تمرير أي بيانات قادمة (مثل الإشعارات أو النوايا الخارجية) إلى MainActivity
        if (getIntent() != null && getIntent().getExtras() != null) {
            intent.putExtras(getIntent().getExtras());
        }
        
        startActivity(intent);
        // جعل الانتقال بين الشاشتين سلساً (تلاشي)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
}
