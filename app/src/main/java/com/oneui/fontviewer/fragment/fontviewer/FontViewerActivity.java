package com.oneui.fontviewer.fragment.fontviewer;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import java.util.Map;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import dev.oneuiproject.oneui.dialog.ProgressDialog;
import dev.oneuiproject.oneui.layout.ToolbarLayout;

import com.oneui.fontviewer.R;
import com.oneui.fontviewer.activity.BaseActivity;
import com.oneui.fontviewer.dialog.FontInfoDialog;
import com.oneui.fontviewer.dialog.FontErrorDialog;
import com.oneui.fontviewer.utils.translation.TranslationService;
import com.oneui.fontviewer.utils.FileUtils;
import com.oneui.fontviewer.widget.TextDrawable;

public class FontViewerActivity extends BaseActivity
        implements FontViewerFragment.OnFontChangedListener {

    public static final String EXTRA_FONT_PATH          = "extra_font_path";
    public static final String EXTRA_FONT_REAL_NAME     = "extra_font_real_name";
    public static final String EXTRA_FONT_FILE_NAME     = "extra_font_file_name";
    public static final String EXTRA_TTC_INDEX          = "extra_ttc_index";
    public static final String EXTRA_IS_SYSTEM_FONT     = "extra_is_system_font";
    public static final String EXTRA_WEIGHT_WIDTH_LABEL = "extra_weight_width_label";

    private ToolbarLayout mToolbarLayout;
    private FontViewerFragment mFontViewerFragment;

    private FloatingActionButton fabFontSize;
    private View formatBar;
    private ImageView btnBold;
    private ImageView btnItalic;

    private String currentFontRealName;
    private String currentFontFileName;

    private ProgressDialog loadingDialog;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_font_viewer);

        mToolbarLayout = findViewById(R.id.toolbar_layout);
        fabFontSize = findViewById(R.id.fab_font_size);
        formatBar = findViewById(R.id.format_bar);
        btnBold = findViewById(R.id.btn_bold);
        btnItalic = findViewById(R.id.btn_italic);

        if (fabFontSize != null) {
            fabFontSize.setVisibility(View.INVISIBLE);
            fabFontSize.setAlpha(0f);
            fabFontSize.setScaleX(0f);
            fabFontSize.setScaleY(0f);
        }
        if (formatBar != null) {
            formatBar.setVisibility(View.INVISIBLE);
            formatBar.setAlpha(0f);
            formatBar.setScaleX(0f);
            formatBar.setScaleY(0f);
        }

        if (mToolbarLayout != null) {
            mToolbarLayout.setNavigationButtonTooltip(getString(R.string.sesl_action_bar_up_description));
            mToolbarLayout.setNavigationButtonOnClickListener(v -> onBackPressed());
        }

        if (savedInstanceState == null) {
            mFontViewerFragment = new FontViewerFragment();
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.font_viewer_container, mFontViewerFragment)
                    .commitNow();
            loadFontFromIntent(getIntent());
        } else {
            mFontViewerFragment = (FontViewerFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.font_viewer_container);
        }

        new Handler(Looper.getMainLooper()).postDelayed(this::setupFab, 300);
    }


    private void loadFontFromIntent(Intent intent) {
        if (intent == null || mFontViewerFragment == null) return;

        String path = intent.getStringExtra(EXTRA_FONT_PATH);
        if (path == null || path.isEmpty()) return;

        if (path.startsWith("content://")) {
            String fileName = intent.getStringExtra(EXTRA_FONT_FILE_NAME);
            mFontViewerFragment.loadFontFromUri(Uri.parse(path), fileName);
        } else {
            String realName = intent.getStringExtra(EXTRA_FONT_REAL_NAME);
            String fileName = intent.getStringExtra(EXTRA_FONT_FILE_NAME);
            int ttcIndex = intent.getIntExtra(EXTRA_TTC_INDEX, 0);
            boolean isSystemFont = intent.getBooleanExtra(EXTRA_IS_SYSTEM_FONT, false);
            String weightWidthLabel = intent.getStringExtra(EXTRA_WEIGHT_WIDTH_LABEL);
            mFontViewerFragment.loadFontFromPath(path, fileName, realName, ttcIndex, isSystemFont, weightWidthLabel);
        }
    }

    public ImageView getBtnBold() { return btnBold; }
    public ImageView getBtnItalic() { return btnItalic; }

    public void updateFabFontSizeText(float size) {
        if (fabFontSize != null) {
            int textColor = getColor(dev.oneuiproject.oneui.design.R.color.oui_primary_text_color);
            String sizeText = String.valueOf(Math.round(size));
            float fabTextSizeDp = sizeText.length() >= 3 ? 19f : 24f;
            fabFontSize.setImageDrawable(new TextDrawable(
                    this,
                    sizeText,
                    fabTextSizeDp,
                    textColor
            ));
        }
    }

    @Override
    public void onFontChanged(String fontRealName, String fontFileName) {
        this.currentFontRealName = fontRealName;
        this.currentFontFileName = fontFileName;
        updateTitle();
    }

    @Override
    public void onFontCleared() {
        this.currentFontRealName = null;
        this.currentFontFileName = null;
        updateTitle();
    }

    private void updateTitle() {
        if (mToolbarLayout == null) return;

        String title;
        if (currentFontRealName != null && !currentFontRealName.isEmpty()) {
            title = currentFontRealName;
        } else if (currentFontFileName != null && !currentFontFileName.isEmpty()) {
            title = getString(R.string.unknown_font);
        } else {
            title = getString(R.string.drawer_font_viewer);
        }

        String subtitle = (currentFontFileName != null && !currentFontFileName.isEmpty())
                ? FileUtils.removeExtension(currentFontFileName)
                : getString(R.string.font_viewer_select_description);

        mToolbarLayout.setTitle(title);
        mToolbarLayout.setExpandedSubtitle(subtitle);
    }

    public void showFontMetaFromFragment() {
        if (mFontViewerFragment == null || !mFontViewerFragment.hasFontSelected()) {
            showNoFontDialog();
            return;
        }

        Map<String, String> meta = mFontViewerFragment.getFontMetaData();

        TranslationService translationService = new TranslationService(this);
        if (translationService.isTranslationEnabled()) {
            boolean[] isFinished = {false};

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (!isFinished[0]) showLoadingDialog();
            }, 250);

            translationService.translateMetadata(meta, new TranslationService.TranslationCallback() {
                @Override
                public void onTranslationComplete(Map<String, String> translatedData) {
                    runOnUiThread(() -> {
                        isFinished[0] = true;
                        dismissLoadingDialog();
                        showFontInfoDialog(translatedData);
                    });
                }

                @Override
                public void onTranslationFailed(String error) {
                    runOnUiThread(() -> {
                        isFinished[0] = true;
                        dismissLoadingDialog();
                        showFontInfoDialog(meta);
                    });
                }
            });
        } else {
            showFontInfoDialog(meta);
        }
    }

    private void showFontInfoDialog(Map<String, String> metadata) {
        if (mFontViewerFragment == null) return;

        if (currentFontRealName == null || currentFontRealName.isEmpty()
                || metadata == null || metadata.isEmpty()) {
            FontErrorDialog.show(this);
            return;
        }

        String fileName = mFontViewerFragment.getCurrentFontFileName();
        String path = mFontViewerFragment.originalFontPath;

        FontInfoDialog dialog = new FontInfoDialog(this, metadata, fileName, path);
        dialog.show();
    }

    private void showNoFontDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.font_viewer_select_font))
                .setMessage(getString(R.string.font_viewer_no_font_selected))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void showLoadingDialog() {
    dismissLoadingDialog();
    try {
        loadingDialog = new ProgressDialog(this);
        loadingDialog.setMessage(getString(R.string.translating));
        loadingDialog.setCancelable(false);
        loadingDialog.show();
        } catch (Exception ignored) {}
    }

    private void dismissLoadingDialog() {
        if (loadingDialog != null && loadingDialog.isShowing()) {
            try {
                loadingDialog.dismiss();
            } catch (Exception ignored) {}
            loadingDialog = null;
        }
    }

    @Override
    protected void onDestroy() {
        dismissLoadingDialog();
        super.onDestroy();
    }
              }
