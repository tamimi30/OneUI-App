package com.oneui.fontviewer.fragment.fontviewer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Property;
import android.view.View;
import android.view.ViewGroup;
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

    // تأخير ظهور الـ FAB وشريط التنسيق بعد فتح الشاشة
    private static final long FAB_SHOW_DELAY_MS = 300;
    // مدة احتياطية للحركة اذا تعذّر تحميل mtrl_fab_show_motion_spec لأي سبب
    private static final long FALLBACK_MOTION_DURATION_MS = 330;
    // حجم صندوق أيقونة الـ FAB (design_fab_image_size = 24dp)، تُستخدم كحجم ذاتي احتياطي لأيقونة الرقم
    private static final float FAB_ICON_BOX_DP = 24f;

    private ToolbarLayout mToolbarLayout;
    private FontViewerFragment mFontViewerFragment;

    private FloatingActionButton fabFontSize;
    private View formatBar;
    private View btnBold;
    private View btnItalic;

    // المحتوى الداخلي لشريط التنسيق (زرا Bold/Italic)، يقابل أيقونة الـ FAB التي لها حركة iconScale مستقلة
    private View formatBarContent;
    // حركة ظهور شريط التنسيق، نحتفظ بها لإلغائها عند إغلاق الشاشة
    private AnimatorSet formatBarAnimator;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

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

        // الـ LinearLayout الداخلي للشريط: يأخذ حركة iconScale تماماً كما تأخذها أيقونة الـ FAB
        if (formatBar instanceof ViewGroup && ((ViewGroup) formatBar).getChildCount() > 0) {
            formatBarContent = ((ViewGroup) formatBar).getChildAt(0);
        }

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

        uiHandler.postDelayed(this::setupFab, FAB_SHOW_DELAY_MS);
    }

    private void setupFab() {
        if (isFinishing() || isDestroyed()) return;

        if (fabFontSize != null) {
            fabFontSize.setOnClickListener(v -> {
                if (mFontViewerFragment != null) {
                    mFontViewerFragment.showFontSizeDialogPublic();
                }
            });
        }

        // show() لا تشغّل الأنيميشن الا اذا كان الـ View قد تم تخطيطه (isLaidOut)، وإلا يظهر الزر فجأة بدون حركة.
        // لذلك ننتظر التخطيط أولاً، ثم نشغّل حركة الـ FAB وحركة شريط التنسيق في نفس اللحظة تماماً.
        View trigger = fabFontSize != null ? fabFontSize : formatBar;
        if (trigger == null) return;
        doWhenLaidOut(trigger, this::playEntranceAnimations);
    }

    /**
     * يشغّل حركة ظهور الـ FAB وحركة ظهور شريط التنسيق معاً.
     */
    private void playEntranceAnimations() {
        if (isFinishing() || isDestroyed()) return;

        if (fabFontSize != null) {
            // FabStyle يضبط showMotionSpec على mtrl_fab_show_motion_spec، لذلك show() تشغّلها تلقائيًا.
            fabFontSize.show();
        }
        if (formatBar != null) {
            showFormatBarWithFabMotion();
        }
    }

    /**
     * ينفّذ الإجراء عندما يصبح الـ View مُخطَّطاً وله أبعاد فعلية.
     * داخل onLayoutChange لا تكون isLaidOut() قد أصبحت true بعد، لذلك نؤجّل التنفيذ بـ post().
     */
    private void doWhenLaidOut(View view, Runnable action) {
        if (view.isLaidOut() && view.getWidth() > 0 && view.getHeight() > 0) {
            action.run();
            return;
        }
        view.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (right - left > 0 && bottom - top > 0) {
                    v.removeOnLayoutChangeListener(this);
                    v.post(action);
                }
            }
        });
    }

    /**
     * MaterialCardView لا يملك showMotionSpec، لذلك نعيد بناء نفس حركة الـ FAB
     * (opacity + scale) يدويًا هنا ونطبّقها على format_bar.
     *
     * ★ ملف mtrl_fab_show_motion_spec يحتوي على التوقيتات (timing) فقط بدون قيم (valueFrom/valueTo)،
     *   وهذا ما يفعله FloatingActionButtonImpl.createAnimator: ينشئ ObjectAnimator.ofFloat بنفسه
     *   ثم يطبّق عليه spec.getTiming(...). أما spec.hasPropertyValues() و spec.getAnimator() فهما
     *   مخصصتان للـ ExtendedFloatingActionButton (الذي تحمل مواصفاته قيماً جاهزة)، وترجعان false هنا،
     *   ولذلك لم تكن تُنشأ أي حركة وكان الشريط يبقى (alpha=0, scale=0) رغم أنه VISIBLE ويستجيب للمس.
     *
     * ★ نطبّق أيضاً حركة iconScale على محتوى الشريط (زرا Bold/Italic) كما تُطبَّق على أيقونة الـ FAB.
     */
    private void showFormatBarWithFabMotion() {
        if (formatBarAnimator != null) {
            formatBarAnimator.cancel();
        }

        // الحالة الابتدائية قبل إظهار الشريط حتى لا يظهر لحظياً بحجمه الكامل
        formatBar.setAlpha(0f);
        formatBar.setScaleX(0f);
        formatBar.setScaleY(0f);
        if (formatBarContent != null) {
            formatBarContent.setScaleX(0f);
            formatBarContent.setScaleY(0f);
        }
        formatBar.setVisibility(View.VISIBLE);

        MotionSpec spec = MotionSpec.createFromResource(this, R.animator.mtrl_fab_show_motion_spec);
        List<Animator> animators = new ArrayList<>();

        animators.add(createMotionAnimator(spec, "opacity", formatBar, View.ALPHA, 0f, 1f));
        animators.add(createMotionAnimator(spec, "scale", formatBar, View.SCALE_X, 0f, 1f));
        animators.add(createMotionAnimator(spec, "scale", formatBar, View.SCALE_Y, 0f, 1f));

        if (formatBarContent != null) {
            animators.add(createMotionAnimator(spec, "iconScale", formatBarContent, View.SCALE_X, 0f, 1f));
            animators.add(createMotionAnimator(spec, "iconScale", formatBarContent, View.SCALE_Y, 0f, 1f));
        }

        AnimatorSet set = new AnimatorSet();
        playTogetherCompat(set, animators);
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                // ضمان الحالة النهائية حتى لا يبقى الشريط شفافاً أو مصغّراً مهما حدث
                formatBar.setAlpha(1f);
                formatBar.setScaleX(1f);
                formatBar.setScaleY(1f);
                if (formatBarContent != null) {
                    formatBarContent.setScaleX(1f);
                    formatBarContent.setScaleY(1f);
                }
            }
        });

        formatBarAnimator = set;
        set.start();
    }

    /**
     * ينشئ ObjectAnimator ويطبّق عليه توقيت (تأخير + مدة + interpolator) الخاص بالاسم المطلوب
     * من الـ MotionSpec، تماماً كما يفعل FloatingActionButtonImpl.createAnimator.
     */
    private static ObjectAnimator createMotionAnimator(MotionSpec spec, String timingName, View target,
                                                       Property<View, Float> property,
                                                       float from, float to) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(target, property, from, to);
        if (spec != null && spec.hasTiming(timingName)) {
            spec.getTiming(timingName).apply(animator);
        } else {
            animator.setDuration(FALLBACK_MOTION_DURATION_MS);
        }
        return animator;
    }

    /**
     * نفس الحل الذي تستخدمه مكتبة Material في AnimatorSetCompat: نضيف animator وهمياً مدته
     * تساوي أطول (تأخير + مدة) حتى لا يخطئ AnimatorSet في حساب المدة الكلية عندما يكون لبعض
     * العناصر startDelay (خطأ معروف في الأجهزة القديمة).
     */
    private static void playTogetherCompat(AnimatorSet set, List<Animator> items) {
        long totalDuration = 0;
        for (Animator animator : items) {
            totalDuration = Math.max(totalDuration, animator.getStartDelay() + animator.getDuration());
        }
        ValueAnimator fix = ValueAnimator.ofInt(0, 0);
        fix.setDuration(totalDuration);
        items.add(0, fix);
        set.playTogether(items);
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

            // الـ FAB يحرّك أيقونته (iconScale) عبر Matrix تعتمد على الحجم الذاتي للـ Drawable.
            // اذا لم يكن للـ TextDrawable حجم ذاتي، يتجاهل الـ FAB هذه الحركة فيظهر الرقم مع الدائرة
            // بدل أن ينبثق بعدها. FabIconDrawable يضمن وجود حجم ذاتي دون تغيير شكل الرقم.
            int iconBoxPx = Math.round(FAB_ICON_BOX_DP * getResources().getDisplayMetrics().density);
            fabFontSize.setImageDrawable(new FabIconDrawable(
                    new TextDrawable(
                            this,
                            sizeText,
                            fabTextSizeDp,
                            textColor
                    ),
                    iconBoxPx
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
        // إيقاف تشغيل الـ FAB المؤجَّل وحركة الشريط الجارية عند إغلاق الشاشة
        uiHandler.removeCallbacksAndMessages(null);
        if (formatBarAnimator != null) {
            formatBarAnimator.cancel();
            formatBarAnimator = null;
        }
        dismissLoadingDialog();
        super.onDestroy();
    }

    /**
     * Drawable يلفّ أيقونة الرقم داخل الـ FAB ويضمن أن يكون لها حجم ذاتي موجب.
     * يستخدم الحجم الذاتي للأيقونة الأصلية اذا كان موجباً، وإلا يستخدم حجم صندوق أيقونة الـ FAB
     * (24dp) حتى تعمل حركة iconScale الخاصة بالـ FAB على الرقم. شكل الرقم الثابت لا يتغير.
     */
    private static class FabIconDrawable extends Drawable implements Drawable.Callback {

        private final Drawable inner;
        private final int fallbackSizePx;

        FabIconDrawable(Drawable inner, int fallbackSizePx) {
            this.inner = inner;
            this.fallbackSizePx = fallbackSizePx;
            inner.setCallback(this);
        }

        @Override
        public void draw(Canvas canvas) {
            inner.draw(canvas);
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            inner.setBounds(bounds);
        }

        @Override
        public int getIntrinsicWidth() {
            int w = inner.getIntrinsicWidth();
            return w > 0 ? w : fallbackSizePx;
        }

        @Override
        public int getIntrinsicHeight() {
            int h = inner.getIntrinsicHeight();
            return h > 0 ? h : fallbackSizePx;
        }

        @Override
        public void setAlpha(int alpha) {
            inner.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            inner.setColorFilter(colorFilter);
        }

        @SuppressWarnings("deprecation")
        @Override
        public int getOpacity() {
            return inner.getOpacity();
        }

        @Override
        public void invalidateDrawable(Drawable who) {
            invalidateSelf();
        }

        @Override
        public void scheduleDrawable(Drawable who, Runnable what, long when) {
            scheduleSelf(what, when);
        }

        @Override
        public void unscheduleDrawable(Drawable who, Runnable what) {
            unscheduleSelf(what);
        }
    }
                }
