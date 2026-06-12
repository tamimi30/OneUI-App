package com.example.oneuiapp.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.util.Log;

import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

import com.example.oneuiapp.R;

// ★ الحل الجذري: الوراثة من ConstraintLayout بدلاً من LinearLayout لحل مشكلة أنيميشن الـ RTL ★
public class SelectableLinearLayout extends ConstraintLayout {

    private static final int CHECK_MODE_CHECKBOX = 0;
    private static final int CHECK_MODE_OVERLAY = 1;

    private ColorDrawable selectedHighlightColor;
    private int checkMode = CHECK_MODE_CHECKBOX;

    private CheckBox checkBox;
    private SelectableAnimatedDrawable checkDrawable;
    private ImageView imageTarget;
    private int imageTargetId = 0;

    private boolean isSelectionMode = false;
    private int spacing;

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
                spacing = a.getDimensionPixelSize(R.styleable.SelectableLinearLayout_checkableButtonSpacing, dpToPx(context, 14));
                
                // 1. بناء الـ CheckBox
                checkBox = new AppCompatCheckBox(context);
                checkBox.setId(View.generateViewId());
                checkBox.setClickable(false);
                checkBox.setFocusable(false);
                checkBox.setLongClickable(false);
                checkBox.setVisibility(View.GONE);
                checkBox.setBackground(null);
                
                // إضافته إلى التنسيق
                addView(checkBox, new ConstraintLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                ));
                
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
        
        if (checkMode == CHECK_MODE_CHECKBOX && checkBox != null) {
            // 2. البحث عن المحتوى الفعلي (العنصر الآخر غير الـ CheckBox والموجود في ملف XML)
            View content = null;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child != checkBox) {
                    content = child;
                    break;
                }
            }

            if (content != null) {
                if (content.getId() == View.NO_ID) {
                    content.setId(View.generateViewId());
                }

                // 3. ★ السر هنا: إنشاء قيود (Constraints) مثل تصميم تطبيق سامسونج تماماً ★
                ConstraintSet set = new ConstraintSet();
                set.clone(this);

                // أ. ربط الـ CheckBox ببداية العنصر (Start) والوسط عمودياً
                set.connect(checkBox.getId(), ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
                set.connect(checkBox.getId(), ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
                set.connect(checkBox.getId(), ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
                
                // إضافة الهوامش للـ Checkbox
                set.setMargin(checkBox.getId(), ConstraintSet.START, dpToPx(getContext(), -4));
                set.setMargin(checkBox.getId(), ConstraintSet.END, spacing);

                // ب. ربط المحتوى بنهاية الـ CheckBox (End) ليتفاعل مع ظهوره واختفائه
                set.connect(content.getId(), ConstraintSet.START, checkBox.getId(), ConstraintSet.END);
                set.connect(content.getId(), ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
                set.connect(content.getId(), ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
                set.connect(content.getId(), ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);

                // ج. السماح للمحتوى بالتمدد للمساحة المتبقية والالتزام بالارتفاع الأصلي
                set.constrainWidth(content.getId(), ConstraintSet.MATCH_CONSTRAINT);
                set.constrainHeight(content.getId(), ConstraintSet.WRAP_CONTENT);

                set.applyTo(this);
            }
        } else if (checkMode == CHECK_MODE_OVERLAY && imageTargetId != 0) {
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
            // 1. إيقاف أي حركة عشوائية معلقة لمنع التداخل
            android.transition.TransitionManager.endTransitions(this);

            // 2. إنشاء أنيميشن انزلاق سلس ومستقل (طريقة سامسونج)
            android.transition.ChangeBounds transition = new android.transition.ChangeBounds();
            transition.setDuration(250);
            transition.setInterpolator(new android.view.animation.DecelerateInterpolator());
            
            // 3. تطبيق الأنيميشن داخل هذا العنصر فقط (بمعزل عن القائمة)
            android.transition.TransitionManager.beginDelayedTransition(this, transition);

            // 4. إظهار أو إخفاء الـ CheckBox
            checkBox.setVisibility(mode ? View.VISIBLE : View.GONE);
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
