package com.oneui.fontviewer.fragment.settings.about;

import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.oneui.fontviewer.R;
import com.oneui.fontviewer.activity.BaseActivity;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

import dev.oneuiproject.oneui.layout.ToolbarLayout;

public class LicensesActivity extends BaseActivity {

    private static final String TAG = "LicensesActivity";
    private ToolbarLayout mToolbarLayout;
    private TextView mLicenseTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_licenses);

        initViews();
        loadLicenseText();
    }

    private void initViews() {
        mToolbarLayout = findViewById(R.id.toolbar_layout);
        mLicenseTextView = findViewById(R.id.license_text_view);

        if (mToolbarLayout != null) {
            mToolbarLayout.setNavigationButtonTooltip(getString(R.string.sesl_action_bar_up_description));
            mToolbarLayout.setNavigationButtonOnClickListener(v -> onBackPressed());
        }

        if (mLicenseTextView != null) {
            mLicenseTextView.setMovementMethod(LinkMovementMethod.getInstance());
        }
    }

    private void loadLicenseText() {
        if (mLicenseTextView == null) return;

        StringBuilder text = new StringBuilder();
        try {
            InputStream is = getResources().openRawResource(R.raw.license);
            BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) {
                text.append(line).append("\n");
            }
            br.close();
            is.close();

            mLicenseTextView.setText(text.toString());
        } catch (Exception e) {
            Log.e(TAG, "Error reading license file", e);
            mLicenseTextView.setText("Error loading licenses.");
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
