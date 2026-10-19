package com.oneui.fontviewer.widget;

import android.animation.LayoutTransition;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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

    // نفس LayoutTransition الذي ينشئه android:animateLayoutChanges="true" (بمدده وسرعاته الافتراضية)،
    // لكنه يُركَّب على الصف فقط لحظة تبديل وضع التحديد، حتى لا تتحرك الصفوف أثناء التمرير أو إعادة الـ bind.
    private LayoutTransition selectionTransition;

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

    public void setSelectionMode(boolean mode) {
        setSelectionMode(mode, false);
    }

    /**
     * يفعّل أو يُلغي وضع التحديد لهذا الصف.
     *
     * عند animate = true: يُنفَّذ نفس أنيميشن animateLayoutChanges الافتراضي:
     * يتحرك محتوى الصف إلى الجانب، ثم تظهر دائرة الـ CheckBox بالتلاشي (alpha) في مكانها.
     * هذا الأنيميشن يحرّك حدود الـ View فقط (left/right) دون إعادة قياس النص في كل إطار،
     * لذلك لا تتقطع حروف الأسماء الطويلة.
     *
     * عند animate = false: تُطبَّق الحالة النهائية فوراً (يُستخدم عند bind صف جديد أو معاد تدويره،
     * حتى لا يظهر أنيميشن على صف لا يراه المستخدم).
     *
     * لا يوجد هنا أي شرط على isShown(): الصف قد يكون مفصولاً مؤقتاً عن أبيه أثناء layout الـ RecyclerView
     * وعندها تُرجع isShown() القيمة false دائماً. الشرط الوحيد هو isAttachedToWindow().
     */
    public void setSelectionMode(boolean mode, boolean animate) {
        if (isSelectionMode == mode) {
            if (!animate) {
                if (getLayoutTransition() != null) setLayoutTransition(null);
                resetContentSlide();
            }
            return;
        }
        isSelectionMode = mode;

        if (checkMode == CHECK_MODE_CHECKBOX && checkBox != null) {
            if (animate && isAttachedToWindow()) {
                final boolean isRtl = getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;

                if (selectionTransition == null) selectionTransition = createSelectionTransition();

                // سبب الاستثناء في RTL: أنيميشن انزلاق المحتوى في LayoutTransition يحرّك حدود الـ View (left/right)
                // بينما أبناؤه يبقون في مواضعهم النهائية داخله، فينزلق النص فقط إذا كانت حافة البداية هي الحافة اليسرى (LTR).
                // في العربية تكون حافة البداية هي اليمنى، وهي لا تتحرك بهذه الطريقة، فيقفز النص مباشرة.
                // لذلك نعطّل انزلاق LayoutTransition في RTL ونحرّك المحتوى بأنفسنا بنفس المدة والتأخير والسرعة،
                // ويبقى تلاشي الـ CheckBox (APPEARING / DISAPPEARING) من LayoutTransition كما هو.
                if (isRtl) {
                    selectionTransition.disableTransitionType(LayoutTransition.CHANGE_APPEARING);
                    selectionTransition.disableTransitionType(LayoutTransition.CHANGE_DISAPPEARING);
                } else {
                    selectionTransition.enableTransitionType(LayoutTransition.CHANGE_APPEARING);
                    selectionTransition.enableTransitionType(LayoutTransition.CHANGE_DISAPPEARING);
                }

                if (getLayoutTransition() != selectionTransition) {
                    setLayoutTransition(selectionTransition);
                }

                // المساحة التي سيشغلها الـ CheckBox أو يتركها، وتُحسب قبل تغيير الـ visibility
                final int shift = isRtl ? getCheckBoxFullWidth() : 0;

                checkBox.setVisibility(mode ? View.VISIBLE : View.GONE);

                if (isRtl) slideContentRtl(mode, shift);
            } else {
                // null يُنهي أي أنيميشن جارٍ فوراً عند حالته النهائية
                setLayoutTransition(null);
                resetContentSlide();
                checkBox.setVisibility(mode ? View.VISIBLE : View.GONE);
            }
        }
    }

    /**
     * عرض المساحة التي يشغلها الـ CheckBox في الصف (عرضه + هوامشه)، وهي مقدار إزاحة محتوى الصف عند تبديل الوضع.
     */
    private int getCheckBoxFullWidth() {
        checkBox.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        LayoutParams params = (LayoutParams) checkBox.getLayoutParams();
        return checkBox.getMeasuredWidth() + params.getMarginStart() + params.getMarginEnd();
    }

    /**
     * انزلاق المحتوى في RTL: الـ layout يضع المحتوى في موضعه النهائي فوراً،
     * فنزيحه بمقدار shift ليبقى ظاهرياً في موضعه القديم، ثم نعيده إلى صفر بنفس مدة وتأخير وسرعة LayoutTransition.
     * الإزاحة تُضاف إلى الإزاحة الحالية، فيستمر الحركة بسلاسة إذا عُكس الوضع أثناء الأنيميشن.
     */
    private void slideContentRtl(boolean appearing, int shift) {
        final int type = appearing ? LayoutTransition.CHANGE_APPEARING : LayoutTransition.CHANGE_DISAPPEARING;

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child == checkBox) continue;

            child.animate().cancel();
            child.setTranslationX(child.getTranslationX() + (appearing ? shift : -shift));
            child.animate()
                    .translationX(0f)
                    .setDuration(selectionTransition.getDuration(type))
                    .setStartDelay(selectionTransition.getStartDelay(type))
                    .setInterpolator(selectionTransition.getInterpolator(type))
                    .start();
        }
    }

    private void resetContentSlide() {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child == checkBox) continue;
            if (child.getTranslationX() != 0f) {
                child.animate().cancel();
                child.setTranslationX(0f);
            }
        }
    }

    private LayoutTransition createSelectionTransition() {
        // المنشئ الافتراضي = ما ينشئه animateLayoutChanges="true" تماماً:
        //  - مدة كل أنيميشن 300ms
        //  - الظهور: تلاشي alpha من 0 إلى 1 بعد تأخير 300ms (أي بعد انتهاء انزلاق المحتوى)، بسرعة AccelerateDecelerate
        //  - الاختفاء: تلاشي من 1 إلى 0 فوراً، ثم ينزلق المحتوى بعد 300ms
        //  - انزلاق المحتوى (CHANGE_APPEARING / CHANGE_DISAPPEARING) بسرعة Decelerate
        final LayoutTransition transition = new LayoutTransition();

        // ارتفاع الصف لا يتغير، وموضعه يتولاه الـ RecyclerView، فلا حاجة لتحريك الأب
        transition.setAnimateParentHierarchy(false);

        transition.addTransitionListener(new LayoutTransition.TransitionListener() {
            @Override
            public void startTransition(LayoutTransition t, ViewGroup container, View view, int transitionType) {}

            @Override
            public void endTransition(LayoutTransition t, ViewGroup container, View view, int transitionType) {
                // بعد انتهاء كل الأنيميشن نُزيل الـ LayoutTransition حتى لا يؤثر على أي تغيير لاحق في الصف
                post(() -> {
                    if (getLayoutTransition() == selectionTransition && !selectionTransition.isRunning()) {
                        setLayoutTransition(null);
                    }
                });
            }
        });
        return transition;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        // الصف خرج من الشاشة أو أُعيد تدويره: أنهِ الأنيميشن فوراً حتى لا يبقى في منتصفه عند إعادة استخدامه
        if (getLayoutTransition() != null) setLayoutTransition(null);
        resetContentSlide();
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
