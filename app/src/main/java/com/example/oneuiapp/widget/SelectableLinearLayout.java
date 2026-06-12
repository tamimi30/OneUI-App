package com.example.oneuiapp.widget;

import android.animation.LayoutTransition;
import android.animation.TimeInterpolator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.appcompat.widget.AppCompatCheckBox;

import com.example.oneuiapp.R;

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
                if (Build.VERSION.SDK_INT >= 23) {
                    imageTarget.setForeground(checkDrawable);
                } else {
                    imageTarget.setBackground(checkDrawable);
                }
            }
        }
    }

    public void setSelectionMode(boolean mode) {
        if (isSelectionMode == mode) return;
        isSelectionMode = mode;
        if (checkMode == CHECK_MODE_CHECKBOX && checkBox != null) {
            // ★ إصلاح جراحي خاص بـ RTL فقط: نعكس اتجاه انزلاق العناصر المجاورة ★
            // لا نلمس أنيميشن ظهور/اختفاء CheckBox (السطر التالي مباشرة) ولا سلوك LTR إطلاقًا.
            if (getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
                prepareRtlSlideFix(mode);
            }
            checkBox.setVisibility(mode ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * يُصلح فقط اتجاه أنيميشن انزلاق العناصر المجاورة لـ CheckBox في RTL.
     *
     * في LTR، يقوم LayoutTransition الافتراضي (CHANGE_APPEARING / CHANGE_DISAPPEARING)
     * بتحريك هذه العناصر بسلاسة عند ظهور/اختفاء CheckBox. في RTL هذا التحريك لا يُفعَّل
     * (العناصر تنتقل فجأة بدون أنيميشن)، بينما أنيميشن ظهور/اختفاء CheckBox نفسه
     * (APPEARING/DISAPPEARING) يبقى يعمل بشكل صحيح ولا نلمسه هنا.
     *
     * هنا نسجّل الموضع الحالي لكل عنصر مجاور (قبل تغيير الـ visibility)، ثم بعد أن
     * يعيد LinearLayout ترتيب العناصر (في OnPreDrawListener، أي بعد التخطيط ومباشرة
     * قبل الرسم) نحسب الفرق الحقيقي ونُشغّل translationX من هذا الفرق إلى صفر —
     * بنفس فكرة الانزلاق الافتراضي تمامًا، لكن بقيم حقيقية بعد التخطيط، فيكون
     * الاتجاه صحيحًا تلقائيًا في RTL دون أي ثابت اتجاه يدوي.
     */
    private void prepareRtlSlideFix(final boolean mode) {
        final int childCount = getChildCount();
        if (childCount <= 1) return;

        final float[] startLefts = new float[childCount];
        for (int i = 1; i < childCount; i++) {
            startLefts[i] = getChildAt(i).getLeft();
        }

        getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                getViewTreeObserver().removeOnPreDrawListener(this);

                // نحاول مطابقة مدة وتسارع أنيميشن CheckBox نفسه لتبقى الحركتان متزامنتين
                long duration = 300L; // قيمة LayoutTransition الافتراضية
                TimeInterpolator interpolator = new AccelerateDecelerateInterpolator();
                LayoutTransition lt = getLayoutTransition();
                if (lt != null) {
                    int type = mode ? LayoutTransition.APPEARING : LayoutTransition.DISAPPEARING;
                    duration = lt.getDuration(type);
                    TimeInterpolator custom = lt.getInterpolator(type);
                    if (custom != null) {
                        interpolator = custom;
                    }
                }

                for (int i = 1; i < childCount; i++) {
                    View child = getChildAt(i);
                    float delta = startLefts[i] - child.getLeft();
                    if (Math.abs(delta) >= 1f) {
                        child.setTranslationX(delta);
                        child.animate()
                                .translationX(0f)
                                .setDuration(duration)
                                .setInterpolator(interpolator)
                                .start();
                    }
                }
                return true;
            }
        });
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
            if (Build.VERSION.SDK_INT < 23) {
                imageTarget.setImageAlpha(!isSelected ? 255 : 0);
            }
            imageTarget.setSelected(isSelected);
        }
        setBackground(isSelected ? selectedHighlightColor : null);
    }
    
    private int dpToPx(Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }
}
