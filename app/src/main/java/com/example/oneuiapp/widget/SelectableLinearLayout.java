package com.example.oneuiapp.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.util.Log;

import androidx.appcompat.widget.AppCompatCheckBox;

import com.example.oneuiapp.R;

public class SelectableLinearLayout extends LinearLayout {

    private static final int CHECK_MODE_CHECKBOX = 0;
    private static final int CHECK_MODE_OVERLAY  = 1;

    // مدة الأنيميشن بالمللي ثانية
    private static final long ANIM_DURATION_MS = 200L;

    private ColorDrawable selectedHighlightColor;
    private int checkMode = CHECK_MODE_CHECKBOX;

    private CheckBox checkBox;
    private SelectableAnimatedDrawable checkDrawable;
    private ImageView imageTarget;
    private int imageTargetId = 0;

    private boolean isSelectionMode = false;

    // ─── متغيرات الأنيميشن المخصص ───
    private ValueAnimator mSelectionAnimator;
    private int mCachedCheckBoxSpace = -1; // عرض CheckBox + هوامشه (يُحسب مرةً واحدة)

    // ─────────────────────────────────────────────────
    // Constructors
    // ─────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────
    // init
    // ─────────────────────────────────────────────────

    private void init(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {

        // ★ الإصلاح الجوهري:
        //   مكتبة OneUI تضبط LayoutTransition تلقائياً على ViewGroups.
        //   هذا الـ Transition يعمل في LTR لأن الحافة اليسرى للمحتوى تتحرك.
        //   في RTL الحافة اليسرى لا تتحرك (فقط اليمنى تنكمش)، فلا يصدر أنيميشن
        //   ويظهر القفز المفاجئ. نعطّله هنا ونستبدله بأنيميشن ValueAnimator مخصص. ★
        setLayoutTransition(null);

        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(
                    attrs, R.styleable.SelectableLinearLayout, defStyleAttr, defStyleRes);

            int color = a.getColor(
                    R.styleable.SelectableLinearLayout_selectedHighlightColor,
                    Color.parseColor("#08000000"));
            selectedHighlightColor = new ColorDrawable(color);
            checkMode = a.getInt(R.styleable.SelectableLinearLayout_checkMode, CHECK_MODE_CHECKBOX);

            if (checkMode == CHECK_MODE_CHECKBOX) {
                int spacing = a.getDimensionPixelSize(
                        R.styleable.SelectableLinearLayout_checkableButtonSpacing,
                        dpToPx(context, 14));

                checkBox = new AppCompatCheckBox(context);
                LayoutParams params = new LayoutParams(
                        LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
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
                checkDrawable = SelectableAnimatedDrawable.create(
                        context,
                        R.drawable.oui_des_list_item_selection_anim_selector,
                        context.getTheme());

                if (a.hasValue(R.styleable.SelectableLinearLayout_cornerRadius)) {
                    if (checkDrawable != null)
                        checkDrawable.setCornerRadius(
                                a.getDimension(R.styleable.SelectableLinearLayout_cornerRadius, 0f));
                }
                imageTargetId = a.getResourceId(
                        R.styleable.SelectableLinearLayout_targetImage, 0);
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
                if (Build.VERSION.SDK_INT >= 23)
                    imageTarget.setForeground(checkDrawable);
                else
                    imageTarget.setBackground(checkDrawable);
            }
        }
    }

    // ─────────────────────────────────────────────────
    // دوال مساعدة للأنيميشن
    // ─────────────────────────────────────────────────

    /**
     * يحسب المساحة الكاملة التي يشغلها CheckBox (عرض + هوامش).
     * النتيجة مؤقتة في الذاكرة لتجنب إعادة القياس في كل أنيميشن.
     */
    private int getCheckBoxSpace() {
        if (mCachedCheckBoxSpace < 0 && checkBox != null) {
            checkBox.measure(
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            MarginLayoutParams lp = (MarginLayoutParams) checkBox.getLayoutParams();
            // marginStart = -4dp → ينقص من المجموع، marginEnd = 14dp
            int space = checkBox.getMeasuredWidth() + lp.getMarginStart() + lp.getMarginEnd();
            mCachedCheckBoxSpace = Math.max(1, space);
        }
        return mCachedCheckBoxSpace > 0 ? mCachedCheckBoxSpace : 1;
    }

    /**
     * يلغي أي أنيميشن جارٍ ويُعيد المشاهد لحالة نظيفة (translationX=0، alpha=1).
     */
    private void cancelAnimation() {
        if (mSelectionAnimator != null) {
            mSelectionAnimator.cancel();
            mSelectionAnimator = null;
        }
        if (checkBox != null) {
            checkBox.setTranslationX(0f);
            checkBox.setAlpha(1f);
            checkBox.setLayerType(View.LAYER_TYPE_NONE, null);
        }
        if (getChildCount() > 1)
            getChildAt(1).setTranslationX(0f);
    }

    // ─────────────────────────────────────────────────
    // ★ الأنيميشن الرئيسي — يعمل صحيحاً في LTR و RTL ★
    // ─────────────────────────────────────────────────

    /**
     * منطق الأنيميشن:
     *
     * LTR (إنجليزي):
     *   عند ظهور CheckBox، المحتوى يتحرك يميناً بمقدار checkBoxSpace.
     *   نُزيح المحتوى والـ CheckBox بـ (-checkBoxSpace) لإعادتهما بصرياً للموضع القديم،
     *   ثم نُحرّكهما إلى صفر → يبدو كأنهما ينزلقان معاً إلى موضعهما الجديد.
     *
     * RTL (عربي):
     *   عند ظهور CheckBox، الحافة اليسرى للمحتوى تبقى ثابتة (الحافة اليمنى تنكمش).
     *   نُزيح المحتوى والـ CheckBox بـ (+checkBoxSpace) لإخفاء الانكماش المفاجئ،
     *   ثم نُحرّكهما إلى صفر → يبدو كأن المحتوى ينزلق يساراً مع دخول الـ CheckBox.
     *
     * النتيجة: أنيميشن سلس في الاتجاهين بدون وميض.
     */
    private void animateCheckBoxVisibility(boolean show) {
        cancelAnimation();

        boolean isRtl     = (getLayoutDirection() == View.LAYOUT_DIRECTION_RTL);
        int checkBoxSpace = getCheckBoxSpace();

        // LTR: إزاحة سالبة (يسار)، RTL: إزاحة موجبة (يمين)
        final float slideOffset = isRtl ? checkBoxSpace : -checkBoxSpace;
        final View  contentView = (getChildCount() > 1) ? getChildAt(1) : null;

        if (show) {
            // ── أنيميشن الدخول ─────────────────────────────────────────

            // 1. اجعل CheckBox مرئياً فوراً (يؤدي Layout Change فوري)
            checkBox.setVisibility(View.VISIBLE);
            checkBox.setAlpha(0f);

            // 2. أزح كلاً من CheckBox والمحتوى إلى الموضع البصري القديم
            checkBox.setTranslationX(slideOffset);
            if (contentView != null) contentView.setTranslationX(slideOffset);

            // 3. Hardware layer لأداء أفضل
            checkBox.setLayerType(View.LAYER_TYPE_HARDWARE, null);

            // 4. حرّك من slideOffset → 0
            mSelectionAnimator = ValueAnimator.ofFloat(1f, 0f);
            mSelectionAnimator.setDuration(ANIM_DURATION_MS);
            mSelectionAnimator.setInterpolator(new DecelerateInterpolator());

            mSelectionAnimator.addUpdateListener(anim -> {
                float t  = (float) anim.getAnimatedValue(); // 1 → 0
                float tx = slideOffset * t;
                checkBox.setTranslationX(tx);
                checkBox.setAlpha(1f - t);
                if (contentView != null) contentView.setTranslationX(tx);
            });

            mSelectionAnimator.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator a) {
                    checkBox.setLayerType(View.LAYER_TYPE_NONE, null);
                    checkBox.setTranslationX(0f);
                    checkBox.setAlpha(1f);
                    if (contentView != null) contentView.setTranslationX(0f);
                }
            });

            mSelectionAnimator.start();

        } else {
            // ── أنيميشن الخروج ─────────────────────────────────────────

            checkBox.setLayerType(View.LAYER_TYPE_HARDWARE, null);

            // حرّك من 0 → slideOffset (عكس الدخول)
            mSelectionAnimator = ValueAnimator.ofFloat(0f, 1f);
            mSelectionAnimator.setDuration(ANIM_DURATION_MS);
            mSelectionAnimator.setInterpolator(new AccelerateInterpolator());

            mSelectionAnimator.addUpdateListener(anim -> {
                float t  = (float) anim.getAnimatedValue(); // 0 → 1
                float tx = slideOffset * t;
                checkBox.setTranslationX(tx);
                checkBox.setAlpha(1f - t);
                // LTR: حرّك المحتوى مع الـ CheckBox (كانت حافته اليسرى تتحرك)
                // RTL: لا تحريك للمحتوى (حافته اليسرى ثابتة — التمديد اليميني يحدث تلقائياً)
                if (contentView != null && !isRtl)
                    contentView.setTranslationX(tx);
            });

            mSelectionAnimator.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator a) {
                    // اجعل CheckBox مخفياً (يؤدي Layout Change — المحتوى يعود لعرضه الكامل)
                    checkBox.setVisibility(View.GONE);
                    checkBox.setLayerType(View.LAYER_TYPE_NONE, null);
                    checkBox.setAlpha(1f);
                    checkBox.setTranslationX(0f);
                    if (contentView != null) contentView.setTranslationX(0f);
                }
            });

            mSelectionAnimator.start();
        }
    }

    // ─────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────

    public void setSelectionMode(boolean mode) {
        if (isSelectionMode == mode) return;
        isSelectionMode = mode;
        if (checkMode == CHECK_MODE_CHECKBOX && checkBox != null) {
            animateCheckBoxVisibility(mode);
        }
    }

    public boolean isSelectionMode() {
        return isSelectionMode;
    }

    @Override
    public void setSelected(boolean selected) {
        setSelectedAnimate(selected);
        if (checkDrawable != null) checkDrawable.jumpToCurrentState();
    }

    public void setSelectedAnimate(boolean isSelected) {
        if (checkMode == CHECK_MODE_CHECKBOX && checkBox != null) {
            checkBox.setChecked(isSelected);
        } else if (checkMode == CHECK_MODE_OVERLAY && imageTarget != null) {
            if (Build.VERSION.SDK_INT < 23)
                imageTarget.setImageAlpha(!isSelected ? 255 : 0);
            imageTarget.setSelected(isSelected);
        }
        setBackground(isSelected ? selectedHighlightColor : null);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cancelAnimation(); // منع memory leaks
    }

    // ─────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────

    private int dpToPx(Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }
                }
