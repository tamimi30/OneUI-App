package com.oneui.fontviewer.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.TimeInterpolator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.util.Property;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.appcompat.widget.AppCompatCheckBox;

import com.oneui.fontviewer.R;

public class SelectableLinearLayout extends LinearLayout {

    private static final int CHECK_MODE_CHECKBOX = 0;
    private static final int CHECK_MODE_OVERLAY = 1;

    private ColorDrawable selectedHighlightColor;
    private int checkMode = CHECK_MODE_CHECKBOX;

    private CheckBox checkBox;
    private SelectableAnimatedDrawable checkDrawable;
    private ImageView imageTarget;
    private int imageTargetId = 0;

    private boolean isSelectionMode = false;

    // قيم مطابقة تماماً لقيم LayoutTransition الافتراضي (animateLayoutChanges="true"):
    //   كل مرحلة 300ms، والانزلاق Decelerate، والظهور/الاختفاء التدريجي AccelerateDecelerate.
    //   عند الظهور: ينزلق المحتوى أولاً ثم يظهر الـ CheckBox (يتأخر 300ms)،
    //   وعند الاختفاء: يختفي الـ CheckBox أولاً ثم ينزلق المحتوى.
    private static final long SELECTION_ANIM_DURATION = 300;
    private static final long SELECTION_APPEAR_FADE_DELAY = 300;
    private static final TimeInterpolator SLIDE_INTERPOLATOR = new DecelerateInterpolator();
    private static final TimeInterpolator FADE_INTERPOLATOR = new AccelerateDecelerateInterpolator();

    private static final Property<SelectableLinearLayout, Float> CONTENT_SLIDE =
            new Property<SelectableLinearLayout, Float>(Float.class, "contentSlide") {
                @Override
                public Float get(SelectableLinearLayout layout) {
                    return layout.contentSlide;
                }

                @Override
                public void set(SelectableLinearLayout layout, Float value) {
                    layout.setContentSlide(value);
                }
            };

    // الإزاحة الحالية (translationX) المطبّقة على محتوى العنصر، أي كل أبناء الـ Layout عدا الـ CheckBox
    private float contentSlide = 0f;
    private ObjectAnimator slideAnimator;
    private ObjectAnimator fadeAnimator;

    public SelectableLinearLayout(Context context) {
        super(context);
        init(context, null, 0, 0);
    }

    public SelectableLinearLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0, 0);
    }

    public SelectableLinearLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr, 0);
    }

    private void init(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SelectableLinearLayout, defStyleAttr, defStyleRes);
            int color = a.getColor(R.styleable.SelectableLinearLayout_selectedHighlightColor, Color.parseColor("#08000000"));
            selectedHighlightColor = new ColorDrawable(color);
            checkMode = a.getInt(R.styleable.SelectableLinearLayout_checkMode, CHECK_MODE_CHECKBOX);

            if (checkMode == CHECK_MODE_CHECKBOX) {
                int spacing = a.getDimensionPixelSize(R.styleable.SelectableLinearLayout_checkableButtonSpacing, dpToPx(context, 14));
                checkBox = new AppCompatCheckBox(context);
                LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                params.gravity = Gravity.CENTER_VERTICAL;
                params.setMarginEnd(spacing);
                params.setMarginStart(dpToPx(context, -4)); 
                checkBox.setLayoutParams(params);
                checkBox.setClickable(false);
                checkBox.setLongClickable(false);
                checkBox.setVisibility(View.GONE);
                checkBox.setBackground(null);
                addView(checkBox, 0);
            } else if (checkMode == CHECK_MODE_OVERLAY) {
                // ★ تم إصلاح الخطأ: الآن نقوم بإنشاء رسمة "علامة الصح" الأنيميشن ★
                checkDrawable = SelectableAnimatedDrawable.create(context, R.drawable.oui_des_list_item_selection_anim_selector, context.getTheme());
                
                if (a.hasValue(R.styleable.SelectableLinearLayout_cornerRadius)) {
                    if (checkDrawable != null) {
                        checkDrawable.setCornerRadius(a.getDimension(R.styleable.SelectableLinearLayout_cornerRadius, 0f));
                    }
                }
                imageTargetId = a.getResourceId(R.styleable.SelectableLinearLayout_targetImage, 0);
            }
            a.recycle();
        }
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        if (checkMode == CHECK_MODE_OVERLAY && imageTargetId != 0) {
            imageTarget = findViewById(imageTargetId);
            if (imageTarget != null && checkDrawable != null) {
                imageTarget.setForeground(checkDrawable);
            }
        }
    }

    /**
     * تغيير وضع التحديد بلا أنيميشن (الحالة النهائية فوراً).
     * هذا هو الاستدعاء القديم، ويستخدمه الباقون (مثل قائمة المحذوفات التي تعتمد على animateLayoutChanges).
     */
    public void setSelectionMode(boolean mode) {
        setSelectionMode(mode, false);
    }

    /**
     * @param animate true لتشغيل أنيميشن الانزلاق وظهور الـ CheckBox (يُستخدم في قائمة الخطوط المحلية).
     *                لا يُشغَّل الأنيميشن إلا إذا كان الـ View متصلاً بالنافذة وسبق رسمه،
     *                لذلك تُربط العناصر الجديدة والمعاد تدويرها مباشرة بالحالة النهائية.
     *                لا يعتمد على isShown() عمداً: العنصر المفصول مؤقتاً عن الـ RecyclerView أثناء
     *                الـ layout يرجع isShown() = false رغم أنه متصل بالنافذة.
     */
    public void setSelectionMode(boolean mode, boolean animate) {
        if (isSelectionMode == mode) return;
        isSelectionMode = mode;
        if (checkMode != CHECK_MODE_CHECKBOX || checkBox == null) return;

        if (animate && canAnimateSelectionChange()) {
            if (mode) animateSelectionOpen();
            else animateSelectionClose();
        } else {
            stopSelectionAnimators();
            applyFinalSelectionState(mode);
        }
    }

    private boolean canAnimateSelectionChange() {
        return isAttachedToWindow()
                && getWindowVisibility() == View.VISIBLE
                && getWidth() > 0
                && getHeight() > 0;
    }

    private void applyFinalSelectionState(boolean mode) {
        if (contentSlide != 0f) setContentSlide(0f);
        if (checkBox.getAlpha() != 1f) checkBox.setAlpha(1f);
        checkBox.setVisibility(mode ? View.VISIBLE : View.GONE);
    }

    // ───────── أنيميشن وضع التحديد ─────────
    //
    // لماذا لا نستخدم LayoutTransition / ChangeBounds هنا؟
    // كلاهما يحرّك حدود الحاوية (left/right) لا موضعها. حاوية النص ذات layout_weight=1، وفي RTL
    // يبقى حدّها الأيسر ثابتاً ويتحرك الأيمن فقط، فيكون النص (المرتّب أصلاً على مخططه النهائي) في مكانه
    // النهائي من أول إطار، فيقفز دون أي انزلاق.
    // الحل: يُحسب المخطط النهائي مرة واحدة، ثم يُحرَّك translationX للمحتوى كجسم صلب بنفس
    // قيم LayoutTransition. لا يتغير أي قياس أثناء الأنيميشن، فلا يعاد تقسيم النص ولا تتقطع الأحرف.

    private void animateSelectionOpen() {
        stopSelectionAnimators();

        float startSlide = contentSlide;
        float startAlpha = checkBox.getAlpha();

        if (checkBox.getVisibility() != View.VISIBLE) {
            // سيُدفع المحتوى بمقدار shift عند ظهور الـ CheckBox في المخطط، فنعوّضه ليبقى في مكانه المرئي
            startSlide -= getSelectionShift();
            startAlpha = 0f;
            checkBox.setAlpha(0f);
            checkBox.setVisibility(View.VISIBLE);
        }
        setContentSlide(startSlide);

        boolean needsSlide = Math.abs(startSlide) > 0.5f;
        if (needsSlide) {
            slideAnimator = ObjectAnimator.ofFloat(this, CONTENT_SLIDE, startSlide, 0f);
            slideAnimator.setDuration(SELECTION_ANIM_DURATION);
            slideAnimator.setInterpolator(SLIDE_INTERPOLATOR);
            slideAnimator.start();
        }

        if (startAlpha < 1f) {
            fadeAnimator = ObjectAnimator.ofFloat(checkBox, View.ALPHA, startAlpha, 1f);
            fadeAnimator.setDuration(SELECTION_ANIM_DURATION);
            fadeAnimator.setInterpolator(FADE_INTERPOLATOR);
            fadeAnimator.setStartDelay(needsSlide ? SELECTION_APPEAR_FADE_DELAY : 0);
            fadeAnimator.start();
        }
    }

    private void animateSelectionClose() {
        stopSelectionAnimators();

        if (checkBox.getAlpha() <= 0.01f) {
            startCloseSlide();
            return;
        }

        fadeAnimator = ObjectAnimator.ofFloat(checkBox, View.ALPHA, checkBox.getAlpha(), 0f);
        fadeAnimator.setDuration(SELECTION_ANIM_DURATION);
        fadeAnimator.setInterpolator(FADE_INTERPOLATOR);
        fadeAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled = false;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (cancelled) return;
                fadeAnimator = null;
                startCloseSlide();
            }
        });
        fadeAnimator.start();
    }

    private void startCloseSlide() {
        // الـ CheckBox يغادر المخطط الآن، فينسحب المحتوى بمقدار shift نحو البداية؛ نعوّضه ثم ننزلق
        float startSlide = contentSlide + getSelectionShift();
        checkBox.setVisibility(View.GONE);
        checkBox.setAlpha(1f);
        setContentSlide(startSlide);

        if (Math.abs(startSlide) <= 0.5f) {
            setContentSlide(0f);
            return;
        }
        slideAnimator = ObjectAnimator.ofFloat(this, CONTENT_SLIDE, startSlide, 0f);
        slideAnimator.setDuration(SELECTION_ANIM_DURATION);
        slideAnimator.setInterpolator(SLIDE_INTERPOLATOR);
        slideAnimator.start();
    }

    /**
     * المسافة التي يدفعها الـ CheckBox بها المحتوى نحو النهاية (موجبة نحو اليمين وسالبة نحو اليسار)،
     * أي عرضه مع هوامشه، بحسب اتجاه الـ Layout (RTL / LTR).
     */
    private float getSelectionShift() {
        LayoutParams lp = (LayoutParams) checkBox.getLayoutParams();
        checkBox.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        float space = checkBox.getMeasuredWidth() + lp.getMarginStart() + lp.getMarginEnd();
        return getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ? -space : space;
    }

    private void setContentSlide(float value) {
        contentSlide = value;
        for (int i = 0, count = getChildCount(); i < count; i++) {
            View child = getChildAt(i);
            if (child != checkBox) child.setTranslationX(value);
        }
    }

    private void stopSelectionAnimators() {
        if (slideAnimator != null) {
            slideAnimator.cancel();
            slideAnimator = null;
        }
        if (fadeAnimator != null) {
            fadeAnimator.cancel();
            fadeAnimator = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        // العنصر يُعاد تدويره: نُنهي أي أنيميشن جارٍ ونثبّت الحالة النهائية حتى لا يظهر بحالة نصف منتهية
        if (checkMode == CHECK_MODE_CHECKBOX && checkBox != null) {
            stopSelectionAnimators();
            applyFinalSelectionState(isSelectionMode);
        }
    }


    public boolean isSelectionMode() {
        return isSelectionMode;
    }

    @Override
    public void setSelected(boolean selected) {
        setSelectedAnimate(selected);
        if (checkDrawable != null) {
            checkDrawable.jumpToCurrentState();
        }
    }

    public void setSelectedAnimate(boolean isSelected) {
        if (checkMode == CHECK_MODE_CHECKBOX && checkBox != null) {
            checkBox.setChecked(isSelected);
        } else if (checkMode == CHECK_MODE_OVERLAY && imageTarget != null) {
            imageTarget.setSelected(isSelected);
        }
        setBackground(isSelected ? selectedHighlightColor : null);
    }
    
    private int dpToPx(Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }
}
