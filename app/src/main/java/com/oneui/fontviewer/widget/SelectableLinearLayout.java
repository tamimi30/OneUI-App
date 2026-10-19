package com.oneui.fontviewer.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.appcompat.widget.AppCompatCheckBox;

import com.oneui.fontviewer.R;

import java.util.ArrayList;
import java.util.List;

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

    // ====================== أنيميشن انزلاق النص (اختياري) ======================
    // لا يعمل إلا للعناصر التي استدعت setSlideTargets (قائمة الخطوط المحلية).
    // بقية القوائم (مثل المحذوفات) تبقى على سلوكها القديم عبر animateLayoutChanges في الـ XML.
    //
    // القيم مطابقة للقيم الافتراضية لـ LayoutTransition (animateLayoutChanges) في AOSP:
    //  - مدة كل مرحلة 300ms
    //  - الانزلاق: DecelerateInterpolator (سرعة تبدأ عالية ثم تتباطأ، وليس خطياً)
    //  - ظهور/اختفاء الـ CheckBox: AccelerateDecelerateInterpolator
    //  - عند الدخول: ينزلق النص أولاً، ثم يظهر الـ CheckBox بعد 300ms
    //  - عند الخروج: يختفي الـ CheckBox أولاً، ثم ينزلق النص بعد 300ms
    private static final long SLIDE_DURATION = 300L;
    private static final long FADE_DURATION = 300L;
    private static final long FADE_IN_DELAY = 0L;
    private static final long SLIDE_OUT_DELAY = 0L;

    private final List<View> slideTargets = new ArrayList<>();
    private ValueAnimator slideAnimator;
    private ValueAnimator fadeAnimator;

    // شفافية الـ CheckBox المعروضة حالياً (1 = ظاهر تماماً، 0 = مخفي)
    private float checkAlpha = 0f;

    // أثناء الاختفاء يكون الـ CheckBox قد خرج من الـ layout (GONE) فيتحرك النص إلى مكانه النهائي فوراً،
    // ونرسم الـ CheckBox يدوياً في مكانه القديم وهو يتلاشى (ghost).
    private boolean ghostActive = false;
    private int ghostLeft = 0;
    private int ghostTop = 0;
    // حالة التحديد المطلوبة أثناء الـ ghost، تُطبَّق بعد انتهائه حتى لا تتحول الدائرة المحددة إلى فارغة قبل أن تتلاشى
    private Boolean deferredChecked = null;

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
     * تسجّل الـ Views التي يجب أن تنزلق (ببدء النص) عند ظهور/اختفاء الـ CheckBox.
     * تُستدعى مرة واحدة بعد الـ inflate. الـ Views التي تثبت حافة نهايتها (مثل أيقونة المفضلة) لا تُسجَّل.
     *
     * لماذا ليس ChangeBounds / animateLayoutChanges هنا؟
     * هما يحرّكان حدود الحاوية (left/right)، أما الأبناء فيكونون مرتّبين مسبقاً على الحجم النهائي.
     * في اللغة العربية (RTL) النص محاذٍ لحافة الحاوية اليمنى، والتي لا تتغير إحداثياتها بالنسبة لأبنائها،
     * فيقفز النص إلى مكانه النهائي فوراً بينما تتحرك الحاوية فقط. في الإنجليزية تتحرك الحافة اليسرى
     * التي ينطلق منها النص، فيبدو الأنيميشن سليماً.
     * هنا نحرّك translationX للنصوص نفسها، فلا يعتمد الأمر على اتجاه اللغة ولا على إعادة قياس كل إطار
     * (فلا تختفي الأحرف واحداً تلو الآخر).
     */
    public void setSlideTargets(View... targets) {
        slideTargets.clear();
        if (targets == null) return;
        for (View target : targets) {
            if (target == null) continue;
            slideTargets.add(target);
            // translationX يُخرج النص عن حدود الحاويات الوسيطة أثناء الحركة، فلا بد من إيقاف قصّ الأبناء فيها
            ViewParent p = target.getParent();
            while (p instanceof ViewGroup && p != this) {
                ((ViewGroup) p).setClipChildren(false);
                p = p.getParent();
            }
        }
    }

    public void setSelectionMode(boolean mode) {
        setSelectionMode(mode, false);
    }

    /**
     * @param animate true عند التغيير الحي لوضع التحديد (payload)، false عند الـ bind الكامل (إعادة تدوير العنصر).
     */
    public void setSelectionMode(boolean mode, boolean animate) {
        final boolean changed = isSelectionMode != mode;
        isSelectionMode = mode;

        if (checkMode != CHECK_MODE_CHECKBOX || checkBox == null) return;

        // العناصر التي لم تسجّل slideTargets: السلوك القديم كما هو (الأنيميشن عبر animateLayoutChanges في الـ XML)
        if (slideTargets.isEmpty()) {
            if (changed) checkBox.setVisibility(mode ? View.VISIBLE : View.GONE);
            return;
        }

        if (!changed) {
            // لا نقطع أنيميشن جارياً بسبب إشعار تحديد عنصر؛ أما الـ bind الكامل فيُعيد الحالة النظيفة (العنصر معاد تدويره)
            if (!animate) snapToState();
            return;
        }

        if (animate && canAnimateNow()) {
            animateCheckBox(mode);
        } else {
            snapToState();
        }
    }

    // لا نستخدم isShown() هنا عمداً: أثناء إعادة ربط العنصر داخل layout الـ RecyclerView يكون العنصر مفصولاً مؤقتاً عن أبيه
    // (attached to window لكن parent == null) فترجع isShown() قيمة false دائماً ولا يبدأ الأنيميشن.
    // العنصر الذي لم يُرتَّب بعد (width == 0) أو غير المرتبط بالنافذة (مخزّن في الـ cache) يأخذ حالته النهائية مباشرة.
    private boolean canAnimateNow() {
        return isAttachedToWindow() && getWidth() > 0 && checkBox.getParent() == this;
    }

    private void animateCheckBox(final boolean enter) {
        cancelAnimators();

        final float endDir = (getLayoutDirection() == LAYOUT_DIRECTION_RTL) ? -1f : 1f;
        final float slot = measureCheckBoxSlot();

        // عند الدخول يتحرك بدء النص في الـ layout بمقدار slot نحو النهاية، فنعوّضه بإزاحة معاكسة ثم نعيدها إلى 0
        // (والعكس عند الخروج). نبدأ من الإزاحة الحالية إن كان هناك أنيميشن جارٍ تم قطعه، فلا يحدث أي قفز.
        final float startShift = slideTargets.get(0).getTranslationX() + (enter ? -endDir : endDir) * slot;
        final float startAlpha = checkAlpha;

        if (enter) {
            ghostActive = false;
            deferredChecked = null;
            checkBox.setAlpha(startAlpha);
            checkBox.setVisibility(View.VISIBLE);
        } else {
            ghostLeft = checkBox.getLeft();
            ghostTop = checkBox.getTop();
            ghostActive = checkBox.getWidth() > 0 && checkBox.getVisibility() == View.VISIBLE;
            checkBox.setVisibility(View.GONE);
        }

        // تُطبَّق فوراً حتى لا يظهر أي إطار بالموضع النهائي قبل بدء الحركة (خاصة مع startDelay)
        applyShift(startShift);

        slideAnimator = ValueAnimator.ofFloat(startShift, 0f);
        slideAnimator.setDuration(SLIDE_DURATION);
        slideAnimator.setInterpolator(new DecelerateInterpolator());
        slideAnimator.setStartDelay(enter ? 0L : SLIDE_OUT_DELAY);
        slideAnimator.addUpdateListener(a -> applyShift((float) a.getAnimatedValue()));

        fadeAnimator = ValueAnimator.ofFloat(startAlpha, enter ? 1f : 0f);
        fadeAnimator.setDuration(FADE_DURATION);
        fadeAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        fadeAnimator.setStartDelay(enter ? FADE_IN_DELAY : 0L);
        fadeAnimator.addUpdateListener(a -> {
            checkAlpha = (float) a.getAnimatedValue();
            if (ghostActive) {
                invalidate();
            } else {
                checkBox.setAlpha(checkAlpha);
            }
        });
        fadeAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean canceled = false;

            @Override
            public void onAnimationCancel(Animator animation) {
                canceled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (canceled) return;
                if (enter) {
                    checkBox.setAlpha(1f);
                } else {
                    ghostActive = false;
                    flushDeferredChecked();
                    invalidate();
                }
            }
        });

        slideAnimator.start();
        fadeAnimator.start();
    }

    // المساحة التي يشغلها الـ CheckBox في الـ layout: عرضه + هامشاه
    private float measureCheckBoxSlot() {
        checkBox.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        LayoutParams lp = (LayoutParams) checkBox.getLayoutParams();
        return checkBox.getMeasuredWidth() + lp.getMarginStart() + lp.getMarginEnd();
    }

    private void applyShift(float shift) {
        for (int i = 0; i < slideTargets.size(); i++) {
            slideTargets.get(i).setTranslationX(shift);
        }
    }

    private void cancelAnimators() {
        if (slideAnimator != null) {
            slideAnimator.cancel();
            slideAnimator = null;
        }
        if (fadeAnimator != null) {
            fadeAnimator.cancel();
            fadeAnimator = null;
        }
    }

    // الحالة النهائية المستقرة حسب isSelectionMode، بلا أنيميشن
    private void snapToState() {
        cancelAnimators();
        ghostActive = false;
        applyShift(0f);
        checkAlpha = isSelectionMode ? 1f : 0f;
        checkBox.setAlpha(1f);
        checkBox.setVisibility(isSelectionMode ? View.VISIBLE : View.GONE);
        flushDeferredChecked();
        invalidate();
    }

    private void flushDeferredChecked() {
        if (deferredChecked != null) {
            checkBox.setChecked(deferredChecked);
            deferredChecked = null;
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (ghostActive && checkBox != null && checkBox.getWidth() > 0) {
            final int w = checkBox.getWidth();
            final int h = checkBox.getHeight();
            final int save = canvas.saveLayerAlpha(
                    ghostLeft, ghostTop, ghostLeft + w, ghostTop + h, Math.round(checkAlpha * 255f));
            canvas.translate(ghostLeft, ghostTop);
            checkBox.draw(canvas);
            canvas.restoreToCount(save);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (!slideTargets.isEmpty() && checkBox != null) snapToState();
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
            if (ghostActive) {
                // الـ CheckBox يتلاشى الآن: لا نغيّر حالته الظاهرة حتى ينتهي
                deferredChecked = isSelected;
            } else {
                checkBox.setChecked(isSelected);
            }
        } else if (checkMode == CHECK_MODE_OVERLAY && imageTarget != null) {
            imageTarget.setSelected(isSelected);
        }
        setBackground(isSelected ? selectedHighlightColor : null);
    }
    
    private int dpToPx(Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }
}
