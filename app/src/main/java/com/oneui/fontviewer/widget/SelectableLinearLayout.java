package com.oneui.fontviewer.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
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

    // يبقى null في القوائم التي تعتمد على animateLayoutChanges (مثل قائمة المحذوفات)،
    // ويُفعَّل فقط عبر enableSelectionAnimation() في قائمة الخطوط المحلية
    // وحتى في تلك القائمة لا يُنشأ إلا إذا كان الـ layout يحمل android:animateLayoutChanges="true"
    private SelectionModeAnimator selectionAnimator;

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
     * تغيير فوري دون أنيميشن. يُستخدم عند ربط العنصر (bind) وفي القوائم التي تعتمد على animateLayoutChanges.
     */
    public void setSelectionMode(boolean mode) {
        if (isSelectionMode == mode) return;
        isSelectionMode = mode;
        if (checkMode == CHECK_MODE_CHECKBOX && checkBox != null) {
            if (selectionAnimator != null) selectionAnimator.cancel();
            checkBox.setVisibility(mode ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * يفعّل أنيميشن وضع التحديد لهذا العنصر. الأنيميشن نفسه موجود في [SelectionModeAnimator].
     *
     * لا يعمل إلا إذا كان العنصر يحمل android:animateLayoutChanges="true" (كما هو الحال في الوضع العادي)،
     * وإلا يبقى التبديل فورياً دون أنيميشن. وعند تفعيله يُستبدل الـ LayoutTransition الافتراضي بهذا الأنيميشن
     * حتى لا يعمل الاثنان معاً.
     */
    public void enableSelectionAnimation() {
        if (checkMode != CHECK_MODE_CHECKBOX || checkBox == null || selectionAnimator != null) return;
        // getLayoutTransition() ليس null إلا إذا كان animateLayoutChanges="true"
        if (getLayoutTransition() == null) return;
        setLayoutTransition(null);
        selectionAnimator = new SelectionModeAnimator(this, checkBox);
    }

    /**
     * @param animate true لتشغيل أنيميشن الانزلاق والظهور التدريجي (يتطلب enableSelectionAnimation())،
     *                false لتغيير فوري.
     */
    public void setSelectionMode(boolean mode, boolean animate) {
        if (!animate || selectionAnimator == null || checkMode != CHECK_MODE_CHECKBOX || checkBox == null) {
            setSelectionMode(mode);
            return;
        }
        if (isSelectionMode == mode) return;
        isSelectionMode = mode;
        selectionAnimator.start(mode);
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
            // أنيميشن علامة الصح لا يتقدم والـ CheckBox مخفي (GONE)، فيبقى معلّقاً ويُعرض لاحقاً عند ظهوره
            // (يظهر الـ CheckBox أزرق لجزء من الثانية على عنصر قديم). لذلك ننهيه فوراً عندما يكون مخفياً.
            if (checkBox.getVisibility() != View.VISIBLE) {
                checkBox.jumpDrawablesToCurrentState();
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
