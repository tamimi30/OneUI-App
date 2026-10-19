package com.oneui.fontviewer.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.appcompat.widget.AppCompatCheckBox;

import com.oneui.fontviewer.R;

public class SelectableLinearLayout extends LinearLayout {

    private static final int CHECK_MODE_CHECKBOX = 0;
    private static final int CHECK_MODE_OVERLAY = 1;

    private static final long SELECTION_ANIM_DURATION = 275L;

    private ColorDrawable selectedHighlightColor;
    private int checkMode = CHECK_MODE_CHECKBOX;

    private CheckBox checkBox;
    private SelectableAnimatedDrawable checkDrawable;
    private ImageView imageTarget;
    private int imageTargetId = 0;

    private boolean isSelectionMode = false;

    // تقدّم أنيميشن الـ CheckBox: 0 = مخفي تماماً، 1 = ظاهر تماماً.
    // الأنيميشن مبني على ValueAnimator بدل TransitionManager، فلا يعتمد على isShown() ولا على
    // حالة الـ View أثناء الـ layout، ويعمل بنفس الطريقة في LTR وRTL لأن الـ LinearLayout هو من يعكس الاتجاه.
    private float selectionProgress = 0f;
    private ValueAnimator selectionAnimator;
    private int checkBoxRestMarginStart;
    private int checkBoxRestMarginEnd;
    private int checkBoxFullWidth = -1;

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
                checkBoxRestMarginStart = dpToPx(context, -4);
                checkBoxRestMarginEnd = spacing;
                params.setMarginEnd(checkBoxRestMarginEnd);
                params.setMarginStart(checkBoxRestMarginStart); 
                checkBox.setLayoutParams(params);
                checkBox.setClickable(false);
                checkBox.setLongClickable(false);
                checkBox.setVisibility(View.GONE);
                checkBox.setBackground(null);
                addView(checkBox, 0);
                // حتى يظهر الـ CheckBox منزلقاً من حافة الصف نفسها لا من حافة الـ padding
                setClipToPadding(false);
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
     * عند animate = true: يظهر الـ CheckBox منزلقاً من جهة البداية (يمين في العربية، يسار في الإنجليزية)
     * بينما يتحرك محتوى الصف تدريجياً نحو الجهة المقابلة، وتبقى أيقونات نهاية الصف في مكانها.
     * يتم ذلك بتحريك هامش الـ CheckBox عبر ValueAnimator، فيحسب الـ LinearLayout موضع كل شيء بنفسه.
     *
     * عند animate = false: تُطبَّق الحالة النهائية فوراً (يُستخدم عند bind صف جديد أو معاد تدويره،
     * حتى لا يظهر أنيميشن على صف لا يراه المستخدم).
     *
     * لا يوجد هنا أي شرط على isShown(): الصف قد يكون مفصولاً مؤقتاً عن أبيه أثناء layout الـ RecyclerView
     * وعندها تُرجع isShown() القيمة false دائماً. الشرط الوحيد هو isAttachedToWindow().
     */
    public void setSelectionMode(boolean mode, boolean animate) {
        if (checkMode != CHECK_MODE_CHECKBOX || checkBox == null) {
            isSelectionMode = mode;
            return;
        }

        if (isSelectionMode == mode) {
            if (!animate && selectionAnimator != null) selectionAnimator.end();
            return;
        }
        isSelectionMode = mode;

        cancelSelectionAnimation();

        final float target = mode ? 1f : 0f;

        if (!animate || !isAttachedToWindow()) {
            applyProgress(target);
            return;
        }

        final ValueAnimator animator = ValueAnimator.ofFloat(selectionProgress, target);
        animator.setDuration(SELECTION_ANIM_DURATION);
        animator.setInterpolator(new PathInterpolator(0.33f, 0f, 0.67f, 1f));
        animator.addUpdateListener(a -> applyProgress((float) a.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                if (selectionAnimator == a) selectionAnimator = null;
            }
        });
        selectionAnimator = animator;
        animator.start();
    }

    private void cancelSelectionAnimation() {
        if (selectionAnimator != null) {
            ValueAnimator running = selectionAnimator;
            selectionAnimator = null;
            running.removeAllUpdateListeners();
            running.cancel();
        }
    }

    /**
     * يطبّق تقدّم الأنيميشن على الـ CheckBox:
     * المساحة التي يشغلها في الصف = progress × (عرضه + هوامشه)، وشفافيته = progress.
     * الهامش في جهة البداية يصبح سالباً بمقدار الجزء المخفي، فينزلق الـ CheckBox من خارج الصف.
     * نستخدم setMarginStart/End مع setLayoutParams حتى يُعاد حلّ الهوامش حسب اتجاه الـ layout.
     */
    private void applyProgress(float progress) {
        selectionProgress = progress;
        if (checkBox == null) return;

        if (progress <= 0f) {
            checkBox.setVisibility(View.GONE);
            return;
        }

        if (checkBoxFullWidth < 0) {
            checkBox.measure(
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            checkBoxFullWidth = checkBox.getMeasuredWidth()
                    + checkBoxRestMarginStart + checkBoxRestMarginEnd;
        }

        LayoutParams params = (LayoutParams) checkBox.getLayoutParams();
        float hiddenPart = (1f - progress) * checkBoxFullWidth;
        params.setMarginStart(Math.round(checkBoxRestMarginStart - hiddenPart));
        params.setMarginEnd(checkBoxRestMarginEnd);
        checkBox.setLayoutParams(params);
        checkBox.setAlpha(progress);
        checkBox.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        // الصف خرج من الشاشة أو أُعيد تدويره: أنهِ الأنيميشن فوراً حتى لا يبقى في منتصفه عند إعادة استخدامه
        if (selectionAnimator != null) selectionAnimator.end();
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
