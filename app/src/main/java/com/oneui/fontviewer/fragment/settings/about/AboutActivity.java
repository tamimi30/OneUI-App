package com.oneui.fontviewer.fragment.settings.about;

import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;

import dev.oneuiproject.oneui.layout.AppInfoLayout;
import dev.oneuiproject.oneui.widget.Toast;

import com.oneui.fontviewer.R;

public class AboutActivity extends AppCompatActivity {
private AppInfoLayout appInfoLayout;
    
@Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_about);

    appInfoLayout = findViewById(R.id.appInfoLayout);

    setButtonsWidth(0.65f, R.id.button_status, R.id.button_licenses);

    appInfoLayout.addOptionalText("Extra 1");
    appInfoLayout.addOptionalText("Extra 2");

    appInfoLayout.setMainButtonClickListener(new AppInfoLayout.OnClickListener() {
        @Override
        public void onUpdateClicked(View v) {
            Toast.makeText(AboutActivity.this, "onUpdateClicked", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onRetryClicked(View v) {
            Toast.makeText(AboutActivity.this, "onRetryClicked", Toast.LENGTH_SHORT).show();
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

public void changeStatus(View v) {
    int s = appInfoLayout.getStatus() + 1;
    if (s == 4) s = -1;
    appInfoLayout.setStatus(s);
}

public void openLicensesPage(View v) {
        Intent intent = new Intent(this, LicensesActivity.class);
        startActivity(intent);
}
}
