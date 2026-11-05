package com.oneui.fontviewer.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.view.animation.ScaleAnimation;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.appcompat.animation.SeslAnimationUtils;
import androidx.reflect.content.res.SeslConfigurationReflector;
import androidx.reflect.widget.SeslTextViewReflector;

import java.util.Locale;

import dev.oneuiproject.oneui.design.R;

/**
 * نسخة معدّلة من {@code dev.oneuiproject.oneui.widget.TipPopup} (بإسم مختلف لتجنب تعارض الكلاسات).
 *
 * الفرق عن كلاس المكتبة:
 * 1) لا توجد فقاعة "علامة الاستفهام" إطلاقاً، فالبالون يظهر موسَّعاً مباشرة.
 * 2) عند الظهور يُشغَّل نفس أنيميشن التوسّع الذي تشغّله المكتبة بعد الضغط على علامة الاستفهام
 *    (scale مرن 500ms + alpha + ظهور النص)، بينما كلاس المكتبة مع setExpanded(true) يعرض البالون بلا أي أنيميشن.
 * 3) اتجاه البالون واتجاه النص داخله يؤخذان من الـ locale (نفس مصدر android:layoutDirection="locale")
 *    بدل Configuration.getLayoutDirection().
 */
public class OneUiTipPopup {
    private static final int ANIMATION_DURATION_DISMISS_ALPHA = 166;
    private static final int ANIMATION_DURATION_DISMISS_SCALE = 166;
    private static final int ANIMATION_DURATION_EXPAND_ALPHA = 83;
    private static final int ANIMATION_DURATION_EXPAND_TEXT = 167;
    private static final int ANIMATION_DURATION_SHOW_SCALE = 500;
    private static final int ANIMATION_OFFSET_EXPAND_TEXT = 100;

    // نقطة بداية أنيميشن التوسّع كما في المكتبة: العرض يبدأ من 0.27، أما الارتفاع فيبدأ من 0
    // (المكتبة تحسبه بقسمة أعداد صحيحة questionHeight / mBalloonHeight فتكون النتيجة 0 دائماً).
    private static final float EXPAND_START_SCALE_X = 0.27f;
    private static final float EXPAND_START_SCALE_Y = 0.0f;

    public static final int DIRECTION_BOTTOM_LEFT = 2;
    public static final int DIRECTION_BOTTOM_RIGHT = 3;
    public static final int DIRECTION_DEFAULT = -1;
    public static final int DIRECTION_TOP_LEFT = 0;
    public static final int DIRECTION_TOP_RIGHT = 1;

    private static final int TYPE_BALLOON_ACTION = 1;
    private static final int TYPE_BALLOON_SIMPLE = 0;

    private View.OnClickListener mActionClickListener;
    private CharSequence mActionText;
    private Integer mActionTextColor;
    private final Button mActionView;
    private int mArrowDirection;
    private final int mArrowHeight;
    private int mArrowPositionX;
    private int mArrowPositionY;
    private final int mArrowWidth;
    private int mBackgroundColor;
    private final View mBalloonView;
    private final FrameLayout mBalloonBubble;
    private final FrameLayout mBalloonPanel;
    private final FrameLayout mBalloonContent;
    private final ImageView mBalloonBg1;
    private final ImageView mBalloonBg2;
    private TipWindow mBalloonPopup;
    private int mBalloonHeight;
    private int mBalloonPopupX;
    private int mBalloonPopupY;
    private int mBalloonWidth;
    private int mBalloonX;
    private int mBalloonY;
    // أبعاد "الفقاعة" الصغيرة لا تُعرض هنا، لكنها تحدد عرض فتحة السهم في خلفية البالون.
    private final int mBubbleHeight;
    private final int mBubbleWidth;
    private int mBubbleX;
    private int mBubbleY;
    private final Context mContext;
    private final Rect mDisplayFrame;
    private DisplayMetrics mDisplayMetrics;
    private boolean mForceRealDisplay;
    private final int mHorizontalTextMargin;
    private int mInitialmMessageViewWidth;
    private boolean mIsDefaultPosition;
    private boolean mIsMessageViewMeasured;
    private CharSequence mMessageText;
    private Integer mMessageTextColor;
    private final TextView mMessageView;
    private OnDismissListener mOnDismissListener;
    private final View mParentView;
    private final Resources mResources;
    private final int mScaleMargin;
    private int mSideMargin;
    private int mType;
    private final int mVerticalTextMargin;
    private final WindowManager mWindowManager;
    private static Interpolator INTERPOLATOR_SINE_IN_OUT_33 = null;
    private static Interpolator INTERPOLATOR_ELASTIC_CUSTOM = null;

    public interface OnDismissListener {
        void onDismiss();
    }

    public OneUiTipPopup(View parentView) {
        this.mIsDefaultPosition = true;
        this.mMessageText = null;
        this.mActionText = null;
        this.mActionClickListener = null;
        this.mMessageTextColor = null;
        this.mActionTextColor = null;
        this.mInitialmMessageViewWidth = 0;
        this.mIsMessageViewMeasured = false;
        this.mForceRealDisplay = false;
        Context context = parentView.getContext();
        this.mContext = context;
        Resources resources = context.getResources();
        this.mResources = resources;
        this.mParentView = parentView;
        this.mWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.mDisplayMetrics = resources.getDisplayMetrics();
        this.mType = TYPE_BALLOON_SIMPLE;
        this.mBackgroundColor = context.getColor(R.color.sem_tip_popup_background_color);
        initInterpolator();

        View inflate = LayoutInflater.from(context).inflate(R.layout.sem_tip_popup_balloon, (ViewGroup) null);
        this.mBalloonView = inflate;
        // اتجاه التخطيط والنص داخل البالون يتبع الـ locale (مثل android:layoutDirection="locale").
        inflate.setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);

        this.mBalloonBubble = (FrameLayout) inflate.findViewById(R.id.sem_tip_popup_balloon_bubble);
        this.mBalloonPanel = (FrameLayout) inflate.findViewById(R.id.sem_tip_popup_balloon_panel);
        this.mBalloonContent = (FrameLayout) inflate.findViewById(R.id.sem_tip_popup_balloon_content);
        this.mBalloonBg1 = (ImageView) inflate.findViewById(R.id.sem_tip_popup_balloon_bg_01);
        this.mBalloonBg2 = (ImageView) inflate.findViewById(R.id.sem_tip_popup_balloon_bg_02);
        this.mMessageView = (TextView) inflate.findViewById(R.id.sem_tip_popup_message);
        this.mActionView = (Button) inflate.findViewById(R.id.sem_tip_popup_action);

        // علامة الاستفهام مخفية دائماً، والبالون الموسَّع هو الظاهر.
        this.mBalloonBubble.setVisibility(View.GONE);
        this.mBalloonPanel.setVisibility(View.VISIBLE);
        this.mMessageView.setVisibility(View.VISIBLE);
        this.mActionView.setVisibility(View.GONE);

        initBalloonPopup();

        this.mArrowPositionX = -1;
        this.mArrowPositionY = -1;
        this.mArrowDirection = DIRECTION_DEFAULT;
        this.mBalloonX = -1;
        this.mScaleMargin = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_scale_margin);
        this.mSideMargin = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_side_margin);
        this.mArrowHeight = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_balloon_arrow_height);
        this.mArrowWidth = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_balloon_arrow_width);
        this.mHorizontalTextMargin = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_balloon_message_margin_horizontal);
        this.mVerticalTextMargin = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_balloon_message_margin_vertical);
        this.mBubbleWidth = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_bubble_width);
        this.mBubbleHeight = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_bubble_height);
        this.mDisplayFrame = new Rect();
        this.mBalloonPopup.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                if (OneUiTipPopup.this.mOnDismissListener != null) {
                    OneUiTipPopup.this.mOnDismissListener.onDismiss();
                }
            }
        });
        inflate.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.addAction(new AccessibilityNodeInfo.AccessibilityAction(16, OneUiTipPopup.this.mContext.getString(R.string.oui_common_close)));
            }
        });
    }

    @SuppressLint("RestrictedApi")
    private void initInterpolator() {
        if (INTERPOLATOR_SINE_IN_OUT_33 == null) {
            INTERPOLATOR_SINE_IN_OUT_33 = AnimationUtils.loadInterpolator(this.mContext, R.interpolator.sine_in_out_33);
        }
        if (INTERPOLATOR_ELASTIC_CUSTOM == null) {
            INTERPOLATOR_ELASTIC_CUSTOM = SeslAnimationUtils.ELASTIC_40;
        }
    }

    private void initBalloonPopup() {
        TipWindow tipWindow = new TipWindow(this.mBalloonView, this.mBalloonWidth, this.mBalloonHeight, true);
        this.mBalloonPopup = tipWindow;
        tipWindow.setFocusable(true);
        this.mBalloonPopup.setTouchable(true);
        this.mBalloonPopup.setOutsideTouchable(true);
        this.mBalloonPopup.setAttachedInDecor(false);
    }

    /**
     * يعرض البالون، والاتجاه (يمين/يسار) يُحدَّد من الـ locale.
     */
    public void show() {
        show(isLocaleRtl() ? DIRECTION_BOTTOM_RIGHT : DIRECTION_BOTTOM_LEFT);
    }

    public void show(int direction) {
        if (isShowing()) {
            return;
        }
        applyContent();
        if (this.mArrowPositionX == -1 || this.mArrowPositionY == -1) {
            calculateArrowPosition();
        }
        if (direction == DIRECTION_DEFAULT) {
            calculateArrowDirection(this.mArrowPositionX, this.mArrowPositionY);
        } else {
            this.mArrowDirection = direction;
        }
        calculatePopupSize();
        calculatePopupPosition();
        setBalloonPanel();
        this.mBalloonPopup.showAtLocation(this.mParentView, Gravity.NO_GRAVITY, this.mBalloonPopupX, this.mBalloonPopupY);
        this.mBalloonView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (OneUiTipPopup.this.mType == TYPE_BALLOON_SIMPLE) {
                    OneUiTipPopup.this.dismiss(true);
                    return false;
                }
                return false;
            }
        });
        animateExpand();
    }

    public void setMessage(CharSequence message) {
        this.mMessageText = message;
    }

    public void setAction(CharSequence actionText, View.OnClickListener listener) {
        this.mActionText = actionText;
        this.mActionClickListener = listener;
    }

    public boolean isShowing() {
        TipWindow tipWindow = this.mBalloonPopup;
        return tipWindow != null && tipWindow.isShowing();
    }

    public void dismiss(boolean withAnimation) {
        TipWindow tipWindow = this.mBalloonPopup;
        if (tipWindow != null && tipWindow.isShowing()) {
            tipWindow.setUseDismissAnimation(withAnimation);
            tipWindow.dismiss();
        }
    }

    public void setTargetPosition(int x, int y) {
        if (x < 0 || y < 0) {
            return;
        }
        this.mIsDefaultPosition = false;
        this.mArrowPositionX = x;
        this.mArrowPositionY = y;
    }

    public void update() {
        if (!isShowing() || this.mParentView == null) {
            return;
        }
        applyContent();
        this.mBalloonX = -1;
        this.mBalloonY = -1;
        if (this.mIsDefaultPosition) {
            calculateArrowPosition();
        }
        calculatePopupSize();
        calculatePopupPosition();
        setBalloonPanel();
        this.mBalloonPopup.update(this.mBalloonPopupX, this.mBalloonPopupY, this.mBalloonPopup.getWidth(), this.mBalloonPopup.getHeight());
    }

    public void setMessageTextColor(int color) {
        this.mMessageTextColor = Integer.valueOf((-16777216) | color);
    }

    public void setActionTextColor(int color) {
        this.mActionTextColor = Integer.valueOf((-16777216) | color);
    }

    public void setBackgroundColor(int color) {
        this.mBackgroundColor = (-16777216) | color;
    }

    public void setBackgroundColorWithAlpha(int color) {
        this.mBackgroundColor = color;
    }

    public void setOutsideTouchEnabled(boolean enabled) {
        this.mBalloonPopup.setFocusable(enabled);
        this.mBalloonPopup.setOutsideTouchable(enabled);
    }

    public void setPopupWindowClippingEnabled(boolean enabled) {
        this.mBalloonPopup.setClippingEnabled(enabled);
        this.mForceRealDisplay = !enabled;
        this.mSideMargin = enabled ? this.mResources.getDimensionPixelSize(R.dimen.sem_tip_popup_side_margin) : 0;
    }

    public void setOnDismissListener(OnDismissListener onDismissListener) {
        this.mOnDismissListener = onDismissListener;
    }

    /**
     * نفس مصدر android:layoutDirection="locale": Locale الافتراضي للتطبيق.
     */
    private static boolean isLocaleRtl() {
        return TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) == View.LAYOUT_DIRECTION_RTL;
    }

    private void applyContent() {
        float currentFontScale = this.mResources.getConfiguration().fontScale;
        int messageTextSize = this.mResources.getDimensionPixelOffset(R.dimen.sem_tip_popup_balloon_message_text_size);
        int actionTextSize = this.mResources.getDimensionPixelOffset(R.dimen.sem_tip_popup_balloon_action_text_size);
        if (currentFontScale > 1.2f) {
            this.mMessageView.setTextSize(TypedValue.COMPLEX_UNIT_PX, (float) Math.floor(Math.ceil(messageTextSize / currentFontScale) * 1.2f));
            this.mActionView.setTextSize(TypedValue.COMPLEX_UNIT_PX, (float) Math.floor(Math.ceil(actionTextSize / currentFontScale) * 1.2f));
        }
        this.mMessageView.setText(this.mMessageText);
        if (TextUtils.isEmpty(this.mActionText) || this.mActionClickListener == null) {
            this.mActionView.setVisibility(View.GONE);
            this.mActionView.setOnClickListener(null);
            this.mType = TYPE_BALLOON_SIMPLE;
        } else {
            this.mActionView.setVisibility(View.VISIBLE);
            SeslTextViewReflector.semSetButtonShapeEnabled(mActionView, true, mBackgroundColor);
            this.mActionView.setText(this.mActionText);
            this.mActionView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (OneUiTipPopup.this.mActionClickListener != null) {
                        OneUiTipPopup.this.mActionClickListener.onClick(view);
                    }
                    OneUiTipPopup.this.dismiss(true);
                }
            });
            this.mType = TYPE_BALLOON_ACTION;
        }
        if (this.mMessageTextColor != null) {
            this.mMessageView.setTextColor(this.mMessageTextColor.intValue());
        }
        if (this.mActionTextColor != null) {
            this.mActionView.setTextColor(this.mActionTextColor.intValue());
        }
        this.mBalloonBg1.setBackgroundTintList(ColorStateList.valueOf(this.mBackgroundColor));
        this.mBalloonBg2.setBackgroundTintList(ColorStateList.valueOf(this.mBackgroundColor));
    }

    private void setBalloonPanel() {
        int leftMargin = this.mBubbleX - this.mBalloonX;
        int rightMargin = (this.mBalloonX + this.mBalloonWidth) - this.mBubbleX;
        DisplayMetrics realMetrics = new DisplayMetrics();
        this.mWindowManager.getDefaultDisplay().getRealMetrics(realMetrics);
        int scaleFactor = (int) Math.ceil(realMetrics.density);
        int minBackgroundWidth = this.mResources.getDimensionPixelSize(R.dimen.sem_tip_popup_balloon_background_minwidth);
        int horizontalContentMargin = this.mHorizontalTextMargin - this.mResources.getDimensionPixelSize(R.dimen.sem_tip_popup_button_padding_horizontal);
        int verticalButtonPadding = this.mActionView.getVisibility() == View.VISIBLE ? this.mResources.getDimensionPixelSize(R.dimen.sem_tip_popup_button_padding_vertical) : 0;
        FrameLayout.LayoutParams paramBalloonPanel = (FrameLayout.LayoutParams) this.mBalloonPanel.getLayoutParams();
        FrameLayout.LayoutParams paramBalloonContent = (FrameLayout.LayoutParams) this.mBalloonContent.getLayoutParams();
        FrameLayout.LayoutParams paramBalloonBg1 = (FrameLayout.LayoutParams) this.mBalloonBg1.getLayoutParams();
        FrameLayout.LayoutParams paramBalloonBg2 = (FrameLayout.LayoutParams) this.mBalloonBg2.getLayoutParams();
        if (Color.alpha(this.mBackgroundColor) < 255) {
            scaleFactor = 0;
        }
        // تصفير الدوران حتى لا يبقى أثر من اتجاه سابق عند استدعاء update().
        this.mBalloonBg1.setRotation(0.0f);
        this.mBalloonBg1.setRotationX(0.0f);
        this.mBalloonBg1.setRotationY(0.0f);
        this.mBalloonBg2.setRotation(0.0f);
        this.mBalloonBg2.setRotationX(0.0f);
        this.mBalloonBg2.setRotationY(0.0f);
        switch (this.mArrowDirection) {
            case DIRECTION_TOP_LEFT:
                this.mBalloonPopup.setPivot((this.mArrowPositionX - this.mBalloonX) + this.mScaleMargin, this.mBalloonHeight + this.mScaleMargin);
                this.mBalloonBg1.setRotationX(180.0f);
                this.mBalloonBg2.setRotationX(180.0f);
                paramBalloonBg2.gravity = 85;
                paramBalloonBg1.gravity = 85;
                if (rightMargin - this.mBubbleWidth < minBackgroundWidth) {
                    int scaledLeftMargin = this.mBalloonWidth - minBackgroundWidth;
                    paramBalloonBg1.setMargins(0, 0, minBackgroundWidth, 0);
                    paramBalloonBg2.setMargins(scaledLeftMargin - scaleFactor, 0, 0, 0);
                } else {
                    paramBalloonBg1.setMargins(0, 0, rightMargin - this.mBubbleWidth, 0);
                    paramBalloonBg2.setMargins((this.mBubbleWidth + leftMargin) - scaleFactor, 0, 0, 0);
                }
                paramBalloonContent.setMargins(horizontalContentMargin, this.mVerticalTextMargin, horizontalContentMargin, (this.mArrowHeight + this.mVerticalTextMargin) - verticalButtonPadding);
                break;
            case DIRECTION_TOP_RIGHT:
                this.mBalloonPopup.setPivot((this.mArrowPositionX - this.mBalloonX) + this.mScaleMargin, this.mBalloonHeight + this.mScaleMargin);
                this.mBalloonBg1.setRotation(180.0f);
                this.mBalloonBg2.setRotation(180.0f);
                paramBalloonBg2.gravity = 83;
                paramBalloonBg1.gravity = 83;
                if (leftMargin < minBackgroundWidth) {
                    int scaledRightMargin = this.mBalloonWidth - minBackgroundWidth;
                    paramBalloonBg1.setMargins(minBackgroundWidth, 0, 0, 0);
                    paramBalloonBg2.setMargins(0, 0, scaledRightMargin - scaleFactor, 0);
                } else {
                    paramBalloonBg1.setMargins(leftMargin, 0, 0, 0);
                    paramBalloonBg2.setMargins(0, 0, rightMargin - scaleFactor, 0);
                }
                paramBalloonContent.setMargins(horizontalContentMargin, this.mVerticalTextMargin, horizontalContentMargin, (this.mArrowHeight + this.mVerticalTextMargin) - verticalButtonPadding);
                break;
            case DIRECTION_BOTTOM_LEFT:
                this.mBalloonPopup.setPivot((this.mArrowPositionX - this.mBalloonX) + this.mScaleMargin, this.mScaleMargin);
                paramBalloonBg2.gravity = 53;
                paramBalloonBg1.gravity = 53;
                paramBalloonBg1.setMargins(0, 0, rightMargin - this.mBubbleWidth, 0);
                paramBalloonBg2.setMargins((this.mBubbleWidth + leftMargin) - scaleFactor, 0, 0, 0);
                paramBalloonContent.setMargins(horizontalContentMargin, this.mArrowHeight + this.mVerticalTextMargin, horizontalContentMargin, this.mVerticalTextMargin - verticalButtonPadding);
                break;
            case DIRECTION_BOTTOM_RIGHT:
                this.mBalloonPopup.setPivot((this.mArrowPositionX - this.mBalloonX) + this.mScaleMargin, this.mScaleMargin);
                this.mBalloonBg1.setRotationY(180.0f);
                this.mBalloonBg2.setRotationY(180.0f);
                paramBalloonBg2.gravity = 51;
                paramBalloonBg1.gravity = 51;
                paramBalloonBg1.setMargins(leftMargin, 0, 0, 0);
                paramBalloonBg2.setMargins(0, 0, rightMargin - scaleFactor, 0);
                paramBalloonContent.setMargins(horizontalContentMargin, this.mArrowHeight + this.mVerticalTextMargin, horizontalContentMargin, this.mVerticalTextMargin - verticalButtonPadding);
                break;
            default:
                break;
        }
        paramBalloonPanel.setMargins(this.mScaleMargin, this.mScaleMargin, this.mScaleMargin, this.mScaleMargin);
        this.mBalloonPopupX = this.mBalloonX - this.mScaleMargin;
        this.mBalloonPopupY = this.mBalloonY - this.mScaleMargin;
        this.mBalloonPanel.setLayoutParams(paramBalloonPanel);
        this.mBalloonBg1.setLayoutParams(paramBalloonBg1);
        this.mBalloonBg2.setLayoutParams(paramBalloonBg2);
        this.mBalloonContent.setLayoutParams(paramBalloonContent);
        this.mBalloonPopup.setWidth(this.mBalloonWidth + (this.mScaleMargin * 2));
        this.mBalloonPopup.setHeight(this.mBalloonHeight + (this.mScaleMargin * 2));
    }

    private void calculateArrowDirection(int arrowX, int arrowY) {
        View view = this.mParentView;
        if (view != null && this.mIsDefaultPosition) {
            int[] location = new int[2];
            view.getLocationInWindow(location);
            int parentY = location[1] + (this.mParentView.getHeight() / 2);
            if (arrowX * 2 <= this.mDisplayMetrics.widthPixels) {
                if (arrowY <= parentY) {
                    this.mArrowDirection = DIRECTION_TOP_RIGHT;
                } else {
                    this.mArrowDirection = DIRECTION_BOTTOM_RIGHT;
                }
            } else if (arrowY <= parentY) {
                this.mArrowDirection = DIRECTION_TOP_LEFT;
            } else {
                this.mArrowDirection = DIRECTION_BOTTOM_LEFT;
            }
        } else if (arrowX * 2 <= this.mDisplayMetrics.widthPixels && arrowY * 2 <= this.mDisplayMetrics.heightPixels) {
            this.mArrowDirection = DIRECTION_BOTTOM_RIGHT;
        } else if (arrowX * 2 > this.mDisplayMetrics.widthPixels && arrowY * 2 <= this.mDisplayMetrics.heightPixels) {
            this.mArrowDirection = DIRECTION_TOP_LEFT;
        } else if (arrowX * 2 <= this.mDisplayMetrics.widthPixels && arrowY * 2 > this.mDisplayMetrics.heightPixels) {
            this.mArrowDirection = DIRECTION_TOP_RIGHT;
        } else if (arrowX * 2 > this.mDisplayMetrics.widthPixels && arrowY * 2 > this.mDisplayMetrics.heightPixels) {
            this.mArrowDirection = DIRECTION_TOP_LEFT;
        }
    }

    private void calculateArrowPosition() {
        View view = this.mParentView;
        if (view == null) {
            this.mArrowPositionX = 0;
            this.mArrowPositionY = 0;
            return;
        }
        int[] location = new int[2];
        view.getLocationInWindow(location);
        int x = location[0] + (this.mParentView.getWidth() / 2);
        int y = location[1] + (this.mParentView.getHeight() / 2);
        if (y * 2 <= this.mDisplayMetrics.heightPixels) {
            this.mArrowPositionY = (this.mParentView.getHeight() / 2) + y;
        } else {
            this.mArrowPositionY = y - (this.mParentView.getHeight() / 2);
        }
        this.mArrowPositionX = x;
    }

    @SuppressLint("RestrictedApi")
    private void calculatePopupSize() {
        int balloonMaxWidth;
        this.mDisplayMetrics = this.mResources.getDisplayMetrics();
        int screenWidthDp = this.mResources.getConfiguration().screenWidthDp;
        int balloonMinWidth = this.mArrowWidth + (this.mHorizontalTextMargin * 2);
        if (SeslConfigurationReflector.isDexEnabled(mContext.getResources().getConfiguration())) {
            int windowWidthInDexMode = this.mParentView.getRootView().getMeasuredWidth();
            int[] windowLocation = new int[2];
            this.mParentView.getRootView().getLocationOnScreen(windowLocation);
            if (windowLocation[0] < 0) {
                windowWidthInDexMode += windowLocation[0];
            }
            if (windowWidthInDexMode <= 480) {
                balloonMaxWidth = (int) (windowWidthInDexMode * 0.83f);
            } else if (windowWidthInDexMode <= 960) {
                balloonMaxWidth = (int) (windowWidthInDexMode * 0.6f);
            } else if (windowWidthInDexMode <= 1280) {
                balloonMaxWidth = (int) (windowWidthInDexMode * 0.45f);
            } else {
                balloonMaxWidth = (int) (windowWidthInDexMode * 0.25f);
            }
        } else {
            if (screenWidthDp <= 480) {
                balloonMaxWidth = (int) (this.mDisplayMetrics.widthPixels * 0.83f);
            } else if (screenWidthDp <= 960) {
                balloonMaxWidth = (int) (this.mDisplayMetrics.widthPixels * 0.6f);
            } else if (screenWidthDp <= 1280) {
                balloonMaxWidth = (int) (this.mDisplayMetrics.widthPixels * 0.45f);
            } else {
                balloonMaxWidth = (int) (this.mDisplayMetrics.widthPixels * 0.25f);
            }
        }
        if (!this.mIsMessageViewMeasured) {
            this.mMessageView.measure(0, 0);
            this.mInitialmMessageViewWidth = this.mMessageView.getMeasuredWidth();
            this.mIsMessageViewMeasured = true;
        }
        int i = this.mInitialmMessageViewWidth;
        int i2 = this.mHorizontalTextMargin;
        int balloonWidth = i + (i2 * 2);
        if (balloonWidth < balloonMinWidth) {
            balloonWidth = balloonMinWidth;
        } else if (balloonWidth > balloonMaxWidth) {
            balloonWidth = balloonMaxWidth;
        }
        this.mBalloonWidth = balloonWidth;
        this.mMessageView.setWidth(balloonWidth - (i2 * 2));
        this.mMessageView.measure(0, 0);
        this.mBalloonHeight = this.mMessageView.getMeasuredHeight() + (this.mVerticalTextMargin * 2) + this.mArrowHeight;
        if (this.mType == TYPE_BALLOON_ACTION) {
            this.mActionView.measure(0, 0);
            if (this.mBalloonWidth < this.mActionView.getMeasuredWidth()) {
                this.mBalloonWidth = this.mActionView.getMeasuredWidth() + (this.mResources.getDimensionPixelSize(R.dimen.sem_tip_popup_button_padding_horizontal) * 2);
            }
            this.mBalloonHeight += this.mActionView.getMeasuredHeight() - this.mResources.getDimensionPixelSize(R.dimen.sem_tip_popup_button_padding_vertical);
        }
    }

    @SuppressLint("RestrictedApi")
    private void calculatePopupPosition() {
        getDisplayFrame(this.mDisplayFrame);
        if (this.mBalloonX < 0) {
            int i = this.mArrowDirection;
            if (i == 3 || i == 1) {
                this.mBalloonX = (this.mArrowPositionX + this.mArrowWidth) - (this.mBalloonWidth / 2);
            } else {
                this.mBalloonX = (this.mArrowPositionX - this.mArrowWidth) - (this.mBalloonWidth / 2);
            }
        }
        int i2 = this.mArrowDirection;
        if (i2 == 3 || i2 == 1) {
            if (this.mArrowPositionX < this.mDisplayFrame.left + this.mSideMargin + this.mHorizontalTextMargin) {
                this.mArrowPositionX = this.mDisplayFrame.left + this.mSideMargin + this.mHorizontalTextMargin;
            } else if (this.mArrowPositionX > ((this.mDisplayFrame.right - this.mSideMargin) - this.mHorizontalTextMargin) - this.mArrowWidth) {
                this.mArrowPositionX = ((this.mDisplayFrame.right - this.mSideMargin) - this.mHorizontalTextMargin) - this.mArrowWidth;
            }
        } else if (this.mArrowPositionX < this.mDisplayFrame.left + this.mSideMargin + this.mHorizontalTextMargin + this.mArrowWidth) {
            this.mArrowPositionX = this.mDisplayFrame.left + this.mSideMargin + this.mHorizontalTextMargin + this.mArrowWidth;
        } else if (this.mArrowPositionX > (this.mDisplayFrame.right - this.mSideMargin) - this.mHorizontalTextMargin) {
            this.mArrowPositionX = (this.mDisplayFrame.right - this.mSideMargin) - this.mHorizontalTextMargin;
        }
        if (SeslConfigurationReflector.isDexEnabled(mContext.getResources().getConfiguration())) {
            int windowWidthInDexMode = this.mParentView.getRootView().getMeasuredWidth();
            int[] windowLocation = new int[2];
            this.mParentView.getRootView().getLocationOnScreen(windowLocation);
            if (windowLocation[0] < 0) {
                windowWidthInDexMode += windowLocation[0];
            }
            int i3 = this.mBalloonX;
            int i4 = this.mDisplayFrame.left;
            int i5 = this.mSideMargin;
            if (i3 < i4 + i5) {
                this.mBalloonX = this.mDisplayFrame.left + this.mSideMargin;
            } else {
                int i6 = this.mBalloonX;
                int i7 = this.mBalloonWidth;
                if (i6 + i7 > windowWidthInDexMode - i5) {
                    int i8 = (windowWidthInDexMode - i5) - i7;
                    this.mBalloonX = i8;
                    if (windowLocation[0] < 0) {
                        this.mBalloonX = i8 - windowLocation[0];
                    }
                }
            }
        } else if (this.mBalloonX < this.mDisplayFrame.left + this.mSideMargin) {
            this.mBalloonX = this.mDisplayFrame.left + this.mSideMargin;
        } else if (this.mBalloonX + this.mBalloonWidth > this.mDisplayFrame.right - this.mSideMargin) {
            this.mBalloonX = (this.mDisplayFrame.right - this.mSideMargin) - this.mBalloonWidth;
        }
        switch (this.mArrowDirection) {
            case DIRECTION_TOP_LEFT:
                this.mBubbleX = this.mArrowPositionX - this.mBubbleWidth;
                int i9 = this.mArrowPositionY;
                this.mBubbleY = i9 - this.mBubbleHeight;
                this.mBalloonY = i9 - this.mBalloonHeight;
                break;
            case DIRECTION_TOP_RIGHT:
                this.mBubbleX = this.mArrowPositionX;
                int i10 = this.mArrowPositionY;
                this.mBubbleY = i10 - this.mBubbleHeight;
                this.mBalloonY = i10 - this.mBalloonHeight;
                break;
            case DIRECTION_BOTTOM_LEFT:
                this.mBubbleX = this.mArrowPositionX - this.mBubbleWidth;
                int i11 = this.mArrowPositionY;
                this.mBubbleY = i11;
                this.mBalloonY = i11;
                break;
            case DIRECTION_BOTTOM_RIGHT:
                this.mBubbleX = this.mArrowPositionX;
                int i12 = this.mArrowPositionY;
                this.mBubbleY = i12;
                this.mBalloonY = i12;
                break;
        }
    }

    /**
     * نفس أنيميشن animateScaleUp في المكتبة (بدون الجزء الخاص بتكبير علامة الاستفهام وإخفائها):
     * scale مرن لمدة 500ms من نقطة السهم + alpha سريع + ظهور النص بعد 100ms.
     */
    private void animateExpand() {
        float pivotPanelX = 0.0f;
        float pivotPanelY = 0.0f;
        switch (this.mArrowDirection) {
            case DIRECTION_TOP_LEFT:
                pivotPanelX = this.mArrowPositionX - this.mBalloonX;
                pivotPanelY = this.mBalloonHeight;
                break;
            case DIRECTION_TOP_RIGHT:
                pivotPanelX = this.mArrowPositionX - this.mBalloonX;
                pivotPanelY = this.mBalloonHeight;
                break;
            case DIRECTION_BOTTOM_LEFT:
                pivotPanelX = this.mArrowPositionX - this.mBalloonX;
                pivotPanelY = 0.0f;
                break;
            case DIRECTION_BOTTOM_RIGHT:
                pivotPanelX = this.mBubbleX - this.mBalloonX;
                pivotPanelY = 0.0f;
                break;
        }
        AnimationSet animationPanel = new AnimationSet(false);
        Animation animationPanelScale = new ScaleAnimation(EXPAND_START_SCALE_X, 1.0f, EXPAND_START_SCALE_Y, 1.0f, Animation.ABSOLUTE, pivotPanelX, Animation.ABSOLUTE, pivotPanelY);
        animationPanelScale.setInterpolator(INTERPOLATOR_ELASTIC_CUSTOM);
        animationPanelScale.setDuration(ANIMATION_DURATION_SHOW_SCALE);
        Animation animationPanelAlpha = new AlphaAnimation(0.0f, 1.0f);
        animationPanelAlpha.setInterpolator(INTERPOLATOR_SINE_IN_OUT_33);
        animationPanelAlpha.setDuration(ANIMATION_DURATION_EXPAND_ALPHA);
        animationPanel.addAnimation(animationPanelScale);
        animationPanel.addAnimation(animationPanelAlpha);
        this.mBalloonPanel.startAnimation(animationPanel);
        this.mMessageView.startAnimation(createTextAnimation());
        if (this.mActionView.getVisibility() == View.VISIBLE) {
            this.mActionView.startAnimation(createTextAnimation());
        }
    }

    private Animation createTextAnimation() {
        Animation animationText = new AlphaAnimation(0.0f, 1.0f);
        animationText.setInterpolator(INTERPOLATOR_SINE_IN_OUT_33);
        animationText.setStartOffset(ANIMATION_OFFSET_EXPAND_TEXT);
        animationText.setDuration(ANIMATION_DURATION_EXPAND_TEXT);
        return animationText;
    }

    private boolean isNavigationbarHide() {
        Context context = this.mContext;
        return context != null && android.provider.Settings.Global.getInt(context.getContentResolver(), "navigationbar_hide_bar_enabled", 0) == 1;
    }

    private int getNavagationbarHeight() {
        int resourceId = this.mResources.getIdentifier("navigation_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return this.mResources.getDimensionPixelSize(resourceId);
        }
        return 0;
    }

    @SuppressWarnings("deprecation")
    private boolean isTablet() {
        DisplayMetrics realMetrics = new DisplayMetrics();
        this.mWindowManager.getDefaultDisplay().getRealMetrics(realMetrics);
        int shortSize = realMetrics.widthPixels > realMetrics.heightPixels ? realMetrics.heightPixels : realMetrics.widthPixels;
        int shortSizeDp = (shortSize * 160) / realMetrics.densityDpi;
        return shortSizeDp >= 600;
    }

    @SuppressWarnings("deprecation")
    private void getDisplayFrame(Rect screenRect) {
        DisplayCutout displayCutout;
        int navigationbarHeight = getNavagationbarHeight();
        boolean navigationbarHide = isNavigationbarHide();
        int displayRotation = this.mWindowManager.getDefaultDisplay().getRotation();
        DisplayMetrics realMetrics = new DisplayMetrics();
        this.mWindowManager.getDefaultDisplay().getRealMetrics(realMetrics);
        if (this.mForceRealDisplay) {
            screenRect.left = 0;
            screenRect.top = 0;
            screenRect.right = realMetrics.widthPixels;
            screenRect.bottom = realMetrics.heightPixels;
            return;
        }
        screenRect.left = 0;
        screenRect.top = 0;
        screenRect.right = this.mDisplayMetrics.widthPixels;
        screenRect.bottom = this.mDisplayMetrics.heightPixels;
        Rect bounds = new Rect();
        this.mParentView.getRootView().getWindowVisibleDisplayFrame(bounds);
        if (isTablet()) {
            if (realMetrics.widthPixels == this.mDisplayMetrics.widthPixels && realMetrics.heightPixels - this.mDisplayMetrics.heightPixels == navigationbarHeight && navigationbarHide) {
                screenRect.bottom += navigationbarHeight;
            }
        } else {
            switch (displayRotation) {
                case Surface.ROTATION_0:
                    if (realMetrics.widthPixels == this.mDisplayMetrics.widthPixels && realMetrics.heightPixels - this.mDisplayMetrics.heightPixels == navigationbarHeight && navigationbarHide) {
                        screenRect.bottom += navigationbarHeight;
                        break;
                    }
                    break;
                case Surface.ROTATION_90:
                    if (realMetrics.heightPixels == this.mDisplayMetrics.heightPixels && realMetrics.widthPixels - this.mDisplayMetrics.widthPixels == navigationbarHeight && navigationbarHide) {
                        screenRect.right += navigationbarHeight;
                    }
                    WindowInsets windowInsets = this.mParentView.getRootWindowInsets();
                    if (windowInsets != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && (displayCutout = windowInsets.getDisplayCutout()) != null) {
                        screenRect.left += displayCutout.getSafeInsetLeft();
                        screenRect.right += displayCutout.getSafeInsetLeft();
                        break;
                    }
                    break;
                case Surface.ROTATION_180:
                    if (realMetrics.widthPixels == this.mDisplayMetrics.widthPixels && realMetrics.heightPixels - this.mDisplayMetrics.heightPixels == navigationbarHeight) {
                        if (navigationbarHide) {
                            screenRect.bottom += navigationbarHeight;
                            break;
                        } else {
                            screenRect.top += navigationbarHeight;
                            screenRect.bottom += navigationbarHeight;
                            break;
                        }
                    } else if (realMetrics.widthPixels == this.mDisplayMetrics.widthPixels && bounds.top == navigationbarHeight) {
                        screenRect.top += navigationbarHeight;
                        screenRect.bottom += navigationbarHeight;
                        break;
                    }
                    break;
                case Surface.ROTATION_270:
                    if (realMetrics.heightPixels == this.mDisplayMetrics.heightPixels && realMetrics.widthPixels - this.mDisplayMetrics.widthPixels == navigationbarHeight) {
                        if (navigationbarHide) {
                            screenRect.right += navigationbarHeight;
                            break;
                        } else {
                            screenRect.left += navigationbarHeight;
                            screenRect.right += navigationbarHeight;
                            break;
                        }
                    } else if (realMetrics.heightPixels == this.mDisplayMetrics.heightPixels && bounds.left == navigationbarHeight) {
                        screenRect.left += navigationbarHeight;
                        screenRect.right += navigationbarHeight;
                        break;
                    }
                    break;
            }
        }
    }

    private static class TipWindow extends PopupWindow {
        private boolean mIsDismissing;
        private boolean mIsUsingDismissAnimation;
        private float mPivotX;
        private float mPivotY;

        private TipWindow(View contentView, int width, int height, boolean focusable) {
            super(contentView, width, height, focusable);
            this.mIsUsingDismissAnimation = true;
            this.mIsDismissing = false;
            this.mPivotX = 0.0f;
            this.mPivotY = 0.0f;
        }

        public void setUseDismissAnimation(boolean useAnimation) {
            this.mIsUsingDismissAnimation = useAnimation;
        }

        public void setPivot(float pivotX, float pivotY) {
            this.mPivotX = pivotX;
            this.mPivotY = pivotY;
        }

        @Override
        public void dismiss() {
            if (this.mIsUsingDismissAnimation && !this.mIsDismissing) {
                animateViewOut();
            } else {
                super.dismiss();
            }
        }

        private void animateViewOut() {
            AnimationSet animationSet = new AnimationSet(true);
            Animation animScale = new ScaleAnimation(1.0f, 0.81f, 1.0f, 0.81f, Animation.ABSOLUTE, this.mPivotX, Animation.ABSOLUTE, this.mPivotY);
            animScale.setInterpolator(OneUiTipPopup.INTERPOLATOR_ELASTIC_CUSTOM);
            animScale.setDuration(ANIMATION_DURATION_DISMISS_SCALE);
            Animation animAlpha = new AlphaAnimation(1.0f, 0.0f);
            animAlpha.setInterpolator(OneUiTipPopup.INTERPOLATOR_SINE_IN_OUT_33);
            animAlpha.setDuration(ANIMATION_DURATION_DISMISS_ALPHA);
            animationSet.addAnimation(animScale);
            animationSet.addAnimation(animAlpha);
            animationSet.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                    TipWindow.this.mIsDismissing = true;
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    TipWindow.super.dismiss();
                    TipWindow.this.mIsDismissing = false;
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            });
            getContentView().startAnimation(animationSet);
        }
    }
}
