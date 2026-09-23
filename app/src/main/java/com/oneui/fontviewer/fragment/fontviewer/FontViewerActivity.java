package com.oneui.fontviewer.fragment.fontviewer;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.animation.MotionSpec;
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
    private View btnBold;
    private View btnItalic;
    // أيقونتا bold/italic نفسهما (ImageView) بداخل حاوياتهما، نحتاجهما منفصلتين عن الحاويات
    // لتطبيق أنيميشن iconScale المتأخر عليهما فقط (انظر شرح showFormatBarWithFabMotion).
    private View iconBold;
    private View iconItalic;

    private String currentFontRealName;
    private String currentFontFileName;

    private ProgressDialog loadingDialog;
    private long loadingDialogShownAt = 0L;
    private static final long MIN_LOADING_DIALOG_MS = 500;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_font_viewer);

        mToolbarLayout = findViewById(R.id.toolbar_layout);
        fabFontSize = findViewById(R.id.fab_font_size);
        formatBar = findViewById(R.id.format_bar);
        btnBold = findViewById(R.id.btn_bold_container);
        btnItalic = findViewById(R.id.btn_italic_container);
        iconBold = findViewById(R.id.btn_bold);
        iconItalic = findViewById(R.id.btn_italic);

        if (fabFontSize != null) {
            fabFontSize.setVisibility(View.INVISIBLE);
        }
        if (formatBar != null) {
            formatBar.setVisibility(View.INVISIBLE);
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

    private void setupFab() {
        if (fabFontSize != null) {
            // FabStyle يضبط showMotionSpec على mtrl_fab_show_motion_spec، لذلك show() تشغّلها تلقائيًا.
            fabFontSize.show();
            fabFontSize.setOnClickListener(v -> {
                if (mFontViewerFragment != null) {
                    mFontViewerFragment.showFontSizeDialogPublic();
                }
            });
        }
        if (formatBar != null) {
            showFormatBarWithFabMotion();
        }
    }

    /**
     * MaterialCardView لا يملك showMotionSpec، لذلك نعيد بناء نفس حركة الـ FAB
     * (opacity + scale) يدويًا هنا ونطبّقها على format_bar.
     *
     * ملاحظة إصلاح: كنا نستخدم spec.getAnimator(name, target, property)، لكن هذه الدالة
     * تعتمد على قيم from/to معرّفة داخل ملف الـ motion spec نفسه، وهي غير موجودة في
     * mtrl_fab_show_motion_spec.xml (الذي يحتوي فقط على توقيت الحركة: duration/interpolator).
     * لذلك كان format_bar يبقى عند alpha = 0 و scale = 0 بشكل دائم (أي غير ظاهر) رغم أن
     * visibility تصبح VISIBLE، فيستمر باستقبال لمسات المستخدم رغم عدم ظهوره.
     * الحل: ننشئ الأنيميترز يدويًا بقيمة from/to صريحة (بنفس أسلوب
     * FloatingActionButtonImpl.createAnimator())، ونطبّق عليها فقط توقيت الحركة
     * (spec.getTiming) القادم من ملف الـ motion spec.
     *
     * ملاحظة إضافية (مطابقة سلوك ظهور أيقونة الـ FAB):
     * في FloatingActionButtonImpl.createAnimator() هناك أنيميشن مستقل باسم "iconScale" يُطبَّق
     * فقط على مصفوفة رسم الأيقونة داخل الـ FAB (وليس على جسم الزر كاملاً)، وتوقيته
     * startOffset=90 / duration=240، بينما أنيميشن "scale" الخاص بجسم الزر نفسه يبدأ فوراً
     * (startOffset=0) ومدته 330. بما أن الأيقونة المرسومة هي حاصل ضرب (تراكب) بين تحويل الزر
     * الأب وتحويل الأيقونة الخاص بها، فإنها تبقى بحجم صفر (غير ظاهرة) خلال أول 90ms حتى لو كان
     * الزر نفسه قد بدأ بالتكبّر فعلاً، ثم تلحق بالتكبير لتصل الحجم الكامل في نفس لحظة انتهاء
     * أنيميشن الزر (330ms). هذا هو ما يجعل أيقونة الـ FAB "تظهر بعد الزر بقليل".
     *
     * لمحاكاة هذا في format_bar: نطبّق أنيميشن "scale" على البطاقة نفسها (formatBar) بدءًا من
     * الصفر كما كان، ونضيف أنيميشن "iconScale" منفصلاً ومتأخرًا على أيقونتي bold/italic تحديدًا
     * (وليس على حاويتيهما)، فتُصبح الأيقونتان أيضًا بحجم صفر خلال أول 90ms بغض النظر عن نمو
     * البطاقة نفسها، بفضل تراكب (تضاعف) تحويلي القياس بين البطاقة الأم والأيقونة الابنة —
     * تمامًا كما يحصل بين الزر ومصفوفة أيقونته في الـ FAB.
     */
    private void showFormatBarWithFabMotion() {
        formatBar.setVisibility(View.VISIBLE);
        formatBar.setAlpha(0f);
        formatBar.setScaleX(0f);
        formatBar.setScaleY(0f);

        // تصفير حجم الأيقونتين قبل بدء الأنيميشن: أنيميشن iconScale أدناه يبدأ متأخراً
        // (startOffset=90) عن أنيميشن scale الخاص بالبطاقة (startOffset=0)، فيجب أن تكونا
        // بحجم صفر بمجرد ظهور البطاقة، تمامًا كأيقونة الـ FAB.
        if (iconBold != null) {
            iconBold.setScaleX(0f);
            iconBold.setScaleY(0f);
        }
        if (iconItalic != null) {
            iconItalic.setScaleX(0f);
            iconItalic.setScaleY(0f);
        }

        MotionSpec spec = MotionSpec.createFromResource(this, R.animator.mtrl_fab_show_motion_spec);
        List<Animator> animators = new ArrayList<>();

        ObjectAnimator opacityAnimator = ObjectAnimator.ofFloat(formatBar, View.ALPHA, 1f);
        spec.getTiming("opacity").apply(opacityAnimator);
        animators.add(opacityAnimator);

        ObjectAnimator scaleXAnimator = ObjectAnimator.ofFloat(formatBar, View.SCALE_X, 1f);
        spec.getTiming("scale").apply(scaleXAnimator);
        animators.add(scaleXAnimator);

        ObjectAnimator scaleYAnimator = ObjectAnimator.ofFloat(formatBar, View.SCALE_Y, 1f);
        spec.getTiming("scale").apply(scaleYAnimator);
        animators.add(scaleYAnimator);

        // أنيميشن ظهور الأيقونتين، مطابق لتوقيت "iconScale" في الـ FAB (متأخر ومنفصل عن
        // أنيميشن البطاقة نفسها) — راجع الشرح في التعليق أعلى الدالة.
        if (iconBold != null) {
            ObjectAnimator iconBoldScaleX = ObjectAnimator.ofFloat(iconBold, View.SCALE_X, 1f);
            spec.getTiming("iconScale").apply(iconBoldScaleX);
            animators.add(iconBoldScaleX);

            ObjectAnimator iconBoldScaleY = ObjectAnimator.ofFloat(iconBold, View.SCALE_Y, 1f);
            spec.getTiming("iconScale").apply(iconBoldScaleY);
            animators.add(iconBoldScaleY);
        }

        if (iconItalic != null) {
            ObjectAnimator iconItalicScaleX = ObjectAnimator.ofFloat(iconItalic, View.SCALE_X, 1f);
            spec.getTiming("iconScale").apply(iconItalicScaleX);
            animators.add(iconItalicScaleX);

            ObjectAnimator iconItalicScaleY = ObjectAnimator.ofFloat(iconItalic, View.SCALE_Y, 1f);
            spec.getTiming("iconScale").apply(iconItalicScaleY);
            animators.add(iconItalicScaleY);
        }

        AnimatorSet set = new AnimatorSet();
        set.playTogether(animators);
        set.start();
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

    public View getBtnBold() { return btnBold; }
    public View getBtnItalic() { return btnItalic; }

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

        boolean isFontInvalid = currentFontRealName == null || currentFontRealName.isEmpty()
                || meta == null || meta.isEmpty();
        if (isFontInvalid) {
            showFontInfoDialog(meta);
            return;
        }

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
                        dismissLoadingDialogThenRun(() -> showFontInfoDialog(translatedData));
                    });
                }

                @Override
                public void onTranslationFailed(String error) {
                    runOnUiThread(() -> {
                        isFinished[0] = true;
                        dismissLoadingDialogThenRun(() -> {
                            if ("NO_INTERNET".equals(error)) {
                                Toast.makeText(FontViewerActivity.this,
                                        R.string.toast_no_internet_connection,
                                        Toast.LENGTH_LONG).show();
                            } else if ("API_ERROR".equals(error)) {
                                Toast.makeText(FontViewerActivity.this,
                                        R.string.toast_translation_api_error,
                                        Toast.LENGTH_LONG).show();
                            }
                            showFontInfoDialog(meta);
                        });
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
        loadingDialogShownAt = System.currentTimeMillis();
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

    private void dismissLoadingDialogThenRun(Runnable action) {
        if (loadingDialog != null && loadingDialog.isShowing()) {
            long elapsed = System.currentTimeMillis() - loadingDialogShownAt;
            long remaining = MIN_LOADING_DIALOG_MS - elapsed;
            if (remaining > 0) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    dismissLoadingDialog();
                    action.run();
                }, remaining);
                return;
            }
        }
        dismissLoadingDialog();
        action.run();
    }

    @Override
    protected void onDestroy() {
        dismissLoadingDialog();
        super.onDestroy();
    }
                    }
