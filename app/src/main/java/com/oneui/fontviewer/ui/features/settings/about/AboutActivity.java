package com.oneui.fontviewer.ui.features.settings.about;

import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;
import androidx.annotation.Nullable;

import dev.oneuiproject.oneui.layout.AppInfoLayout;
import dev.oneuiproject.oneui.widget.Toast;

import com.oneui.fontviewer.R;
import com.oneui.fontviewer.config.AppLinks;
import com.oneui.fontviewer.data.model.UpdateCheckStatus;
import com.oneui.fontviewer.data.repository.UpdateRepository;
import com.oneui.fontviewer.ui.features.update.StorePageLauncher;

public class AboutActivity extends AppCompatActivity {
private AppInfoLayout appInfoLayout;
private UpdateRepository updateRepository;
    
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_about);

    appInfoLayout = findViewById(R.id.appInfoLayout);

    setButtonsWidth(0.61f,
            R.id.button_github,
            R.id.button_licenses,
            R.id.app_info_update);

    appInfoLayout.addOptionalText("Extra 1");
    appInfoLayout.addOptionalText("Extra 2");

    updateRepository = UpdateRepository.getInstance(this);

    appInfoLayout.setMainButtonClickListener(new AppInfoLayout.OnClickListener() {
        @Override
        public void onUpdateClicked(View v) {
            // مثل Samsung Video: بعد فتح المتجر بنجاح تُغلق الصفحة، وإلا تظهر رسالة خطأ
            if (StorePageLauncher.openUpdatePage(AboutActivity.this, updateRepository.getDistributionChannel())) {
                finish();
            } else {
                Toast.makeText(AboutActivity.this, getString(R.string.toast_cannot_open_link), Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public void onRetryClicked(View v) {
            updateRepository.retryAboutPageCheck();
        }
    });

    observeUpdateStatus();
}

/**
 * الفحص يبدأ مرة واحدة فقط في كل زيارة للإعدادات: أول دخول إلى هذه الصفحة يُظهر مؤشر التحميل ثم النتيجة،
 * وأي دخول لاحق في نفس الزيارة يعرض النتيجة المحفوظة فوراً بدون مؤشر (مثل تطبيقات Samsung).
 */
private void observeUpdateStatus() {
    updateRepository.startAboutPageCheckIfNeeded();

    // عرض الحالة الحالية فوراً قبل أول رسم حتى لا يومض مؤشر التحميل الافتراضي للمكتبة
    showUpdateStatus(updateRepository.getAboutPageStatus().getValue());

    updateRepository.getAboutPageStatus().observe(this, this::showUpdateStatus);
}

private void showUpdateStatus(@Nullable UpdateCheckStatus status) {
    if (status == null) {
        return;
    }

    switch (status) {
        case UPDATE_AVAILABLE:
            appInfoLayout.setStatus(AppInfoLayout.UPDATE_AVAILABLE);
            break;
        case UP_TO_DATE:
            appInfoLayout.setStatus(AppInfoLayout.NO_UPDATE);
            break;
        case NO_CONNECTION:
        case CHECK_FAILED:
            appInfoLayout.setStatus(AppInfoLayout.NO_CONNECTION);
            break;
        case CHECKING:
        default:
            appInfoLayout.setStatus(AppInfoLayout.LOADING);
            break;
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

public void openGitHubPage(View v) {
    if (!StorePageLauncher.openUrl(this, AppLinks.GITHUB_PROFILE_URL)) {
        Toast.makeText(this, getString(R.string.toast_cannot_open_link), Toast.LENGTH_SHORT).show();
    }
}

public void openLicensesPage(View v) {
        Intent intent = new Intent(this, LicensesActivity.class);
        startActivity(intent);
}
}
