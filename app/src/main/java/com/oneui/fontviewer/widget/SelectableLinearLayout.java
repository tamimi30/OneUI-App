package com.oneui.fontviewer.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.PathInterpolator;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.appcompat.widget.AppCompatCheckBox;

import com.oneui.fontviewer.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SelectableLinearLayout extends LinearLayout {

    private static final int CHECK_MODE_CHECKBOX = 0;
    private static final int CHECK_MODE_OVERLAY = 1;

    private static final long SLIDE_DURATION = 275L;

    private ColorDrawable selectedHighlightColor;
    private int checkMode = CHECK_MODE_CHECKBOX;

    private CheckBox checkBox;
    private SelectableAnimatedDrawable checkDrawable;
    private ImageView imageTarget;
    private int imageTargetId = 0;

    private boolean isSelectionMode = false;

    private AnimatorSet slideAnimator;
    private List<View> slideTargets;
    private BitmapDrawable ghostDrawable; // صورة مؤقتة للمربع تتلاشى عند الإخفاء

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
        // الأنيميشن يُنفَّذ يدوياً داخل setSelectionMode، لذلك نعطّل LayoutTransition لمنع التعارض
        setLayoutTransition(null);

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

    public void setSelectionMode(boolean mode) {
        setSelectionMode(mode, true);
    }

    /**
     * @param animate false لتطبيق الحالة فوراً بدون أنيميشن (مثلاً عند إعادة الربط بعد تدوير الشاشة)
     */
    public void setSelectionMode(final boolean mode, boolean animate) {
        if (isSelectionMode == mode) return;
        isSelectionMode = mode;
        if (checkMode != CHECK_MODE_CHECKBOX || checkBox == null) return;

        boolean canAnimate = animate && isLaidOut() && getWidth() > 0 && isShown();
        if (!canAnimate) {
            cancelSlide();
            checkBox.setAlpha(1f);
            checkBox.setVisibility(mode ? View.VISIBLE : View.GONE);
            return;
        }

        // 1) التقاط مواضع العناصر قبل التغيير (أخذاً بالاعتبار أي أنيميشن جارٍ)
        final List<View> targets = new ArrayList<>();
        collectLeafViews(this, targets);
        final Map<View, Float> before = new HashMap<>();
        for (View v : targets) {
            before.put(v, startEdge(v) + v.getTranslationX());
        }
        cancelSlide();

        // عند الإخفاء: نلتقط صورة للمربع ونضعها في الـ overlay لتتلاشى بعد أن يصبح View نفسه GONE
        if (!mode && checkBox.getVisibility() == View.VISIBLE
                && checkBox.getWidth() > 0 && checkBox.getHeight() > 0) {
            Bitmap bmp = Bitmap.createBitmap(checkBox.getWidth(), checkBox.getHeight(), Bitmap.Config.ARGB_8888);
            checkBox.draw(new Canvas(bmp));
            ghostDrawable = new BitmapDrawable(getResources(), bmp);
            ghostDrawable.setBounds(checkBox.getLeft(), checkBox.getTop(), checkBox.getRight(), checkBox.getBottom());
            getOverlay().add(ghostDrawable);
        }

        // 2) تنفيذ التغيير
        checkBox.setVisibility(mode ? View.VISIBLE : View.GONE);

        // 3) بعد اكتمال الـ layout نحسب الفرق ونُزلق كل عنصر من موضعه القديم إلى الجديد
        getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                getViewTreeObserver().removeOnPreDrawListener(this);

                List<Animator> animators = new ArrayList<>();
                for (View v : targets) {
                    Float b = before.get(v);
                    if (b == null) continue;
                    float dx = b - startEdge(v);
                    if (dx != 0f) {
                        v.setTranslationX(dx);
                        animators.add(ObjectAnimator.ofFloat(v, View.TRANSLATION_X, dx, 0f));
                    }
                }
                if (mode) {
                    checkBox.setAlpha(0f);
                    animators.add(ObjectAnimator.ofFloat(checkBox, View.ALPHA, 0f, 1f));
                } else if (ghostDrawable != null) {
                    final BitmapDrawable ghost = ghostDrawable;
                    ValueAnimator fadeOut = ValueAnimator.ofInt(255, 0);
                    fadeOut.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                        @Override
                        public void onAnimationUpdate(ValueAnimator animation) {
                            ghost.setAlpha((int) animation.getAnimatedValue());
                        }
                    });
                    animators.add(fadeOut);
                }
                if (animators.isEmpty()) return true;

                slideTargets = targets;
                slideAnimator = new AnimatorSet();
                slideAnimator.playTogether(animators);
                slideAnimator.setDuration(SLIDE_DURATION);
                slideAnimator.setInterpolator(new PathInterpolator(0.33f, 0f, 0.67f, 1f));
                slideAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        resetSlideState();
                    }
                });
                slideAnimator.start();
                return true;
            }
        });
    }

    public boolean isSelectionMode() {
        return isSelectionMode;
    }

    private void cancelSlide() {
        if (slideAnimator != null) {
            AnimatorSet running = slideAnimator;
            slideAnimator = null;
            running.cancel();
        }
        resetSlideState();
    }

    private void resetSlideState() {
        if (slideTargets != null) {
            for (View v : slideTargets) v.setTranslationX(0f);
            slideTargets = null;
        }
        if (checkBox != null) checkBox.setAlpha(1f);
        if (ghostDrawable != null) {
            getOverlay().remove(ghostDrawable);
            ghostDrawable = null;
        }
    }

    /** يجمع العناصر الطرفية (غير الحاويات) ما عدا الـ CheckBox. */
    private void collectLeafViews(ViewGroup parent, List<View> out) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child == checkBox || child.getVisibility() == View.GONE) continue;
            if (child instanceof ViewGroup) {
                collectLeafViews((ViewGroup) child, out);
            } else {
                out.add(child);
            }
        }
    }

    /**
     * موضع حافة "البداية" للعنصر داخل هذه الحاوية، حسب اتجاه الواجهة:
     * اليسار في LTR واليمين في RTL. هذا هو سبب نجاح الحل في العربية.
     */
    private float startEdge(View v) {
        boolean rtl = getLayoutDirection() == LAYOUT_DIRECTION_RTL;
        float x = rtl ? v.getRight() : v.getLeft();
        View p = (View) v.getParent();
        while (p != null && p != this) {
            x += p.getLeft();
            p = (View) p.getParent();
        }
        return x;
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
