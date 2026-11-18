package com.oneui.fontviewer.ui.features.settings.about;

import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;

import dev.oneuiproject.oneui.layout.AppInfoLayout;

import com.oneui.fontviewer.R;
import com.oneui.fontviewer.data.repository.UpdateRepository;
import com.oneui.fontviewer.data.model.UpdateCheckResult;
import com.oneui.fontviewer.utils.StoreLauncher;

public class AboutActivity extends AppCompatActivity {

// أقل مدة يبقى فيها مؤشر التحميل ظاهراً قبل عرض نتيجة التحقق (بالمللي ثانية)
private static final long MIN_LOADING_TIME_MS = 700L;

private AppInfoLayout appInfoLayout;
    
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_about);

    appInfoLayout = findViewById(R.id.appInfoLayout);

    setButtonsWidth(0.61f,
            R.id.button_licenses,
            R.id.app_info_update);

    appInfoLayout.addOptionalText("Extra 1");
    appInfoLayout.addOptionalText("Extra 2");

    appInfoLayout.setMainButtonClickListener(new AppInfoLayout.OnClickListener() {
        @Override
        public void onUpdateClicked(View v) {
            // يفتح صفحة التطبيق في المتجر الذي ثبّت منه المستخدم، ثم نغلق صفحة "حول" (مثل Samsung Video)
            if (StoreLauncher.openUpdatePage(AboutActivity.this)) {
                finish();
            }
        }

        @Override
        public void onRetryClicked(View v) {
            checkForUpdate();
        }
    });

    // أول فتح لصفحة "حول" في هذه الزيارة للإعدادات: مؤشر تحميل ثم النتيجة.
    // أي فتح تالٍ (بعد الرجوع منها) يعرض النتيجة نفسها فوراً دون مؤشر تحميل (مثل Samsung Video).
    UpdateRepository updateRepository = UpdateRepository.getInstance(this);
    if (updateRepository.hasAboutResult()) {
        showUpdateStatus(updateRepository.getAboutResult());
    } else {
        checkForUpdate();
    }
}

private void checkForUpdate() {
    appInfoLayout.setStatus(AppInfoLayout.LOADING);
    final long startTime = SystemClock.uptimeMillis();

    UpdateRepository.getInstance(this).checkForAbout(result -> {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        // الرد عندما لا يوجد تحديث سريع جداً (أقل من ثانية)، فيختفي مؤشر التحميل قبل أن يُرى.
        // نُبقيه ظاهراً مدة قصيرة على الأقل قبل عرض النتيجة.
        long remaining = MIN_LOADING_TIME_MS - (SystemClock.uptimeMillis() - startTime);
        if (remaining > 0) {
            appInfoLayout.postDelayed(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    showUpdateStatus(result);
                }
            }, remaining);
        } else {
            showUpdateStatus(result);
        }
    });
}

private void showUpdateStatus(UpdateCheckResult result) {
    if (result.status == UpdateCheckResult.Status.UPDATE_AVAILABLE) {
        appInfoLayout.setStatus(AppInfoLayout.UPDATE_AVAILABLE);
    } else if (result.status == UpdateCheckResult.Status.NO_UPDATE) {
        appInfoLayout.setStatus(AppInfoLayout.NO_UPDATE);
    } else {
        appInfoLayout.setStatus(AppInfoLayout.NO_CONNECTION);
    }
}

private void setButtonsWidth(float ratio, int... buttonIds) {
    Resources res = getResources();
    int w = res.getDisplayMetrics().widthPixels / res.getConfiguration().orientation;
    int width = (int) (w * ratio);

    for (int id : buttonIds) {
        View button = findViewById(id);
        if (button != null) {
            ViewGroup.LayoutParams lp = button.getLayoutParams();
            lp.width = width;
            button.setLayoutParams(lp);
        }
    }
}

public void openLicensesPage(View v) {
        Intent intent = new Intent(this, LicensesActivity.class);
        startActivity(intent);
}
}
