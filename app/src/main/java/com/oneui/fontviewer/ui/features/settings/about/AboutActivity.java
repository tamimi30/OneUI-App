package com.oneui.fontviewer.ui.features.settings.about;

import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;

import dev.oneuiproject.oneui.layout.AppInfoLayout;

import com.oneui.fontviewer.R;
import com.oneui.fontviewer.data.repository.UpdateRepository;
import com.oneui.fontviewer.data.model.UpdateCheckResult;
import com.oneui.fontviewer.utils.StoreLauncher;

public class AboutActivity extends AppCompatActivity {
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
}

@Override
protected void onResume() {
    super.onResume();
    // مؤشر تحميل ثم النتيجة، عند كل فتح للصفحة (مثل Samsung Video)
    checkForUpdate();
}

private void checkForUpdate() {
    appInfoLayout.setStatus(AppInfoLayout.LOADING);

    UpdateRepository.getInstance(this).forceCheck(result -> {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        if (result.status == UpdateCheckResult.Status.UPDATE_AVAILABLE) {
            appInfoLayout.setStatus(AppInfoLayout.UPDATE_AVAILABLE);
        } else if (result.status == UpdateCheckResult.Status.NO_UPDATE) {
            appInfoLayout.setStatus(AppInfoLayout.NO_UPDATE);
        } else {
            appInfoLayout.setStatus(AppInfoLayout.NO_CONNECTION);
        }
    });
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
