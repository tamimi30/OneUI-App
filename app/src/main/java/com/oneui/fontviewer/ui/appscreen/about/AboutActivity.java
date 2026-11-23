package com.oneui.fontviewer.ui.appscreen.about;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;

import dev.oneuiproject.oneui.layout.AppInfoLayout;
import dev.oneuiproject.oneui.widget.Toast;

import com.oneui.fontviewer.R;
import com.oneui.fontviewer.data.update.UpdateRepository;
import com.oneui.fontviewer.data.update.model.UpdateCheckState;
import com.oneui.fontviewer.ui.appscreen.license.LicensesActivity;
import com.oneui.fontviewer.ui.core.update.UpdateLauncher;
import com.oneui.fontviewer.utils.update.AppLinks;

public class AboutActivity extends AppCompatActivity {

    private static final String TAG = "AboutActivity";

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

        // لإضافة نص إضافي تحت رقم الإصدار استخدم:
        // appInfoLayout.addOptionalText("النص هنا");
        // (حُذف النصان التجريبيان Extra 1 و Extra 2 فقط، والمكتبة ما زالت تدعم النصوص الإضافية)

        updateRepository = UpdateRepository.getInstance(this);

        appInfoLayout.setMainButtonClickListener(new AppInfoLayout.OnClickListener() {
            @Override
            public void onUpdateClicked(View v) {
                // نفس تطبيقات سامسونج: ننتقل إلى المتجر ونغلق صفحة "حول التطبيق"
                boolean opened = UpdateLauncher.open(
                        AboutActivity.this, updateRepository.getAvailableUpdateNow());
                if (opened) {
                    finish();
                } else {
                    Toast.makeText(AboutActivity.this,
                            R.string.update_open_store_failed, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onRetryClicked(View v) {
                updateRepository.retryCheck();
            }
        });

        // يعرض مؤشر التحميل ثم النتيجة. أول فتح للصفحة في جلسة الإعدادات يتحقق من التحديث،
        // والفتح التالي في نفس الجلسة يعرض النتيجة المحفوظة فوراً بدون مؤشر تحميل.
        updateRepository.getCheckState().observe(this, this::showCheckState);
        updateRepository.requestAboutPageCheck();
    }

    private void showCheckState(UpdateCheckState state) {
        switch (state) {
            case UPDATE_AVAILABLE:
                appInfoLayout.setStatus(AppInfoLayout.UPDATE_AVAILABLE);
                break;
            case UP_TO_DATE:
                appInfoLayout.setStatus(AppInfoLayout.NO_UPDATE);
                break;
            case NO_CONNECTION:
                appInfoLayout.setStatus(AppInfoLayout.NO_CONNECTION);
                break;
            case CHECKING:
            case IDLE:
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

    public void openGithubPage(View v) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(AppLinks.GITHUB_PROFILE_URL));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "No app can open the GitHub page: " + e.getMessage());
        }
    }

    public void openLicensesPage(View v) {
        Intent intent = new Intent(this, LicensesActivity.class);
        startActivity(intent);
    }
}
