package com.oneui.fontviewer.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.DisplayCutout;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.view.animation.ScaleAnimation;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.appcompat.animation.SeslAnimationUtils;
import androidx.reflect.content.res.SeslConfigurationReflector;

import dev.oneuiproject.oneui.design.R;

/**
 * نسخة معدّلة من {@code dev.oneuiproject.oneui.widget.TipPopup} (الكلاس الأصلي داخل المكتبة
 * ولا يمكن تعديله، لذلك نسخناه باسم مختلف لتجنب أي تعارض في البناء).
 *
 * الفرق عن الأصل:
 * - الأصل يعرض أولاً "فقاعة علامة السؤال" (bubble)، وعند الضغط عليها تتحول إلى "نافذة الرسالة"
 *   (balloon) بأنيميشن التكبير animateScaleUp. وبما أن التطبيق لديه أيقونة Tips خاصة به،
 *   فهذا الكلاس لا يعرض علامة السؤال إطلاقاً، بل يعرض نافذة الرسالة مباشرة عند الضغط على
 *   الأيقونة، ويشغّل عليها نفس أنيميشن التكبير الحرفي الموجود في المكتبة (نفس المدة، نفس
 *   الـ interpolator المرن، نفس نقطة الارتكاز عند السهم).
 * - في الأصل، عند استخدام setExpanded(true) تُعرض النافذة بلا أي أنيميشن، وهذا سبب اختلاف
 *   الأنيميشن عن المكتبة. هنا الأنيميشن يعمل دائماً، ولذلك نُبقي هامش التكبير (scaleMargin)
 *   كما هو في الأصل حتى لا يُقص تجاوز الـ elastic عند حدود النافذة.
 */
public class ExpandedTipPopup {
    public static final int DIRECTION_DEFAULT = -1;
    public static final int DIRECTION_TOP_LEFT = 0;
    public static final int DIRECTION_TOP_RIGHT = 1;
    public static final int DIRECTION_BOTTOM_LEFT = 2;
    public static final int DIRECTION_BOTTOM_RIGHT = 3;

    // نفس قيم المكتبة الأصلية بالضبط
    private static final int ANIMATION_DURATION_DISMISS_ALPHA = 166;
    private static final int ANIMATION_DURATION_DISMISS_SCALE = 166;
    private static final int ANIMATION_DURATION_EXPAND_ALPHA = 83;
    private static final int ANIMATION_DURATION_EXPAND_TEXT = 167;
    private static final int ANIMATION_DURATION_SHOW_SCALE = 500;
    private static final int ANIMATION_OFFSET_EXPAND_TEXT = 100;
    private static final float ANIMATION_START_SCALE_X = 0.27f;

    // sine_in_out_33 في المكتبة = cubic-bezier(0.33, 0, 0.67, 1)
    private static Interpolator interpolatorSineInOut33 = null;
    private static Interpolator interpolatorElasticCustom = null;

    private final Context context;
    private final Resources resources;
    private final View parentView;
    private final WindowManager windowManager;
    private DisplayMetrics displayMetrics;

    private final View balloonView;
    private final FrameLayout balloonBubble;
    private final FrameLayout balloonPanel;
    private final FrameLayout balloonContent;
    private final ImageView balloonBg1;
    private final ImageView balloonBg2;
    private final TextView messageView;
    private final Button actionView;
    private final TipWindow balloonPopup;

    private CharSequence messageText = null;
    private Integer messageTextColor = null;
    private int backgroundColor;

    private final int arrowHeight;
    private final int arrowWidth;
    private final int horizontalTextMargin;
    private final int verticalTextMargin;
    private final int scaleMargin;
    private final int sideMargin;
    private final Rect displayFrame = new Rect();

    // الأيقونة (Tips) هي بديل علامة السؤال، فنستخدم أبعاد علامة السؤال كحجم لنقطة الارتكاز
    private final int anchorWidth;
    private final int anchorHeight;

    private int arrowDirection = DIRECTION_DEFAULT;
    private int arrowPositionX = -1;
    private int arrowPositionY = -1;
    private int anchorX;
    private int anchorY;
    private int balloonX = -1;
    private int balloonY;
    private int balloonWidth;
    private int balloonHeight;
    private int balloonPopupX;
    private int balloonPopupY;
    private int initialMessageViewWidth = 0;
    private boolean isMessageViewMeasured = false;

    public ExpandedTipPopup(View parentView) {
        Context ctx = parentView.getContext();
        this.context = ctx;
        this.resources = ctx.getResources();
        this.parentView = parentView;
        this.windowManager = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        this.displayMetrics = resources.getDisplayMetrics();
        this.backgroundColor = ctx.getColor(R.color.sem_tip_popup_background_color);
        initInterpolator();

        balloonView = LayoutInflater.from(ctx).inflate(R.layout.sem_tip_popup_balloon, (ViewGroup) null);
        // نفس مبدأ android:layoutDirection="locale": اتجاه محتوى النافذة يتبع اللغة
        balloonView.setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);

        balloonBubble = balloonView.findViewById(R.id.sem_tip_popup_balloon_bubble);
        balloonPanel = balloonView.findViewById(R.id.sem_tip_popup_balloon_panel);
        balloonContent = balloonView.findViewById(R.id.sem_tip_popup_balloon_content);
        balloonBg1 = balloonView.findViewById(R.id.sem_tip_popup_balloon_bg_01);
        balloonBg2 = balloonView.findViewById(R.id.sem_tip_popup_balloon_bg_02);
        messageView = balloonView.findViewById(R.id.sem_tip_popup_message);
        actionView = balloonView.findViewById(R.id.sem_tip_popup_action);

        // لا نريد علامة السؤال أبداً، ونافذة الرسالة مرئية من البداية (الأنيميشن يتكفل بالظهور)
        balloonBubble.setVisibility(View.GONE);
        balloonPanel.setVisibility(View.VISIBLE);
        messageView.setVisibility(View.VISIBLE);
        actionView.setVisibility(View.GONE);

        scaleMargin = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_scale_margin);
        sideMargin = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_side_margin);
        arrowHeight = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_balloon_arrow_height);
        arrowWidth = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_balloon_arrow_width);
        horizontalTextMargin = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_balloon_message_margin_horizontal);
        verticalTextMargin = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_balloon_message_margin_vertical);
        anchorWidth = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_bubble_width);
        anchorHeight = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_bubble_height);

        balloonPopup = new TipWindow(balloonView, 0, 0, true);
        balloonPopup.setTouchable(true);
        balloonPopup.setOutsideTouchable(true);
        balloonPopup.setAttachedInDecor(false);

        // نافذة الرسالة البسيطة (بلا زر): أي لمسة عليها تغلقها، كما في الأصل
        balloonView.setOnTouchListener((v, event) -> {
            dismiss(true);
            return false;
        });
    }

    @SuppressLint("RestrictedApi")
    private void initInterpolator() {
        if (interpolatorSineInOut33 == null) {
            interpolatorSineInOut33 = new PathInterpolator(0.33f, 0.0f, 0.67f, 1.0f);
        }
        if (interpolatorElasticCustom == null) {
            interpolatorElasticCustom = SeslAnimationUtils.ELASTIC_40;
        }
    }

    public void setMessage(CharSequence message) {
        this.messageText = message;
    }

    public void setMessageTextColor(int color) {
        this.messageTextColor = (-16777216) | color;
    }

    public void setBackgroundColorWithAlpha(int color) {
        this.backgroundColor = color;
    }

    public boolean isShowing() {
        return balloonPopup.isShowing();
    }

    public void show(int direction) {
        applyContent();
        if (arrowPositionX == -1 || arrowPositionY == -1) {
            calculateArrowPosition();
        }
        if (direction == DIRECTION_DEFAULT) {
            calculateArrowDirection();
        } else {
            arrowDirection = direction;
        }
        calculatePopupSize();
        calculatePopupPosition();
        setBalloonPanel();

        balloonPopup.setUseDismissAnimation(true);
        balloonPopup.showAtLocation(parentView, 0, balloonPopupX, balloonPopupY);
        animateScaleUp();
    }

    public void dismiss(boolean withAnimation) {
        balloonPopup.setUseDismissAnimation(withAnimation);
        balloonPopup.dismiss();
    }

    private void applyContent() {
        float currentFontScale = resources.getConfiguration().fontScale;
        int messageTextSize = resources.getDimensionPixelOffset(R.dimen.sem_tip_popup_balloon_message_text_size);
        if (currentFontScale > 1.2f) {
            messageView.setTextSize(0, (float) Math.floor(Math.ceil(messageTextSize / currentFontScale) * 1.2f));
        }
        messageView.setText(messageText);
        if (messageTextColor != null) {
            messageView.setTextColor(messageTextColor);
        }
        balloonBg1.setBackgroundTintList(ColorStateList.valueOf(backgroundColor));
        balloonBg2.setBackgroundTintList(ColorStateList.valueOf(backgroundColor));
    }

    private void setBalloonPanel() {
        int leftMargin = anchorX - balloonX;
        int rightMargin = (balloonX + balloonWidth) - anchorX;
        int topMargin = anchorY - balloonY;
        int bottomMargin = (balloonY + balloonHeight) - (anchorY + anchorHeight);
        DisplayMetrics realMetrics = new DisplayMetrics();
        getDisplay().getRealMetrics(realMetrics);
        int minBackgroundWidth = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_balloon_background_minwidth);
        int horizontalContentMargin = horizontalTextMargin
                - resources.getDimensionPixelSize(R.dimen.sem_tip_popup_button_padding_horizontal);

        FrameLayout.LayoutParams paramBalloonBubble = (FrameLayout.LayoutParams) balloonBubble.getLayoutParams();
        FrameLayout.LayoutParams paramBalloonPanel = (FrameLayout.LayoutParams) balloonPanel.getLayoutParams();
        FrameLayout.LayoutParams paramBalloonContent = (FrameLayout.LayoutParams) balloonContent.getLayoutParams();
        FrameLayout.LayoutParams paramBalloonBg1 = (FrameLayout.LayoutParams) balloonBg1.getLayoutParams();
        FrameLayout.LayoutParams paramBalloonBg2 = (FrameLayout.LayoutParams) balloonBg2.getLayoutParams();

        int scaleFactor;
        if (Color.alpha(backgroundColor) < 255) {
            scaleFactor = 0;
        } else {
            scaleFactor = (int) Math.ceil(realMetrics.density);
        }

        switch (arrowDirection) {
            case DIRECTION_TOP_LEFT:
                balloonPopup.setPivot(arrowPositionX - balloonX + scaleMargin, balloonHeight + scaleMargin);
                balloonBg1.setRotationX(180.0f);
                balloonBg2.setRotationX(180.0f);
                paramBalloonBg2.gravity = 85;
                paramBalloonBg1.gravity = 85;
                paramBalloonBubble.gravity = 85;
                if (rightMargin - anchorWidth < minBackgroundWidth) {
                    int scaledLeftMargin = balloonWidth - minBackgroundWidth;
                    paramBalloonBg1.setMargins(0, 0, minBackgroundWidth, 0);
                    paramBalloonBg2.setMargins(scaledLeftMargin - scaleFactor, 0, 0, 0);
                } else {
                    paramBalloonBg1.setMargins(0, 0, rightMargin - anchorWidth, 0);
                    paramBalloonBg2.setMargins((anchorWidth + leftMargin) - scaleFactor, 0, 0, 0);
                }
                paramBalloonContent.setMargins(horizontalContentMargin, verticalTextMargin,
                        horizontalContentMargin, arrowHeight + verticalTextMargin);
                break;
            case DIRECTION_TOP_RIGHT:
                balloonPopup.setPivot(arrowPositionX - balloonX + scaleMargin, balloonHeight + scaleMargin);
                balloonBg1.setRotation(180.0f);
                balloonBg2.setRotation(180.0f);
                paramBalloonBg2.gravity = 83;
                paramBalloonBg1.gravity = 83;
                paramBalloonBubble.gravity = 83;
                if (leftMargin < minBackgroundWidth) {
                    int scaledRightMargin = balloonWidth - minBackgroundWidth;
                    paramBalloonBg1.setMargins(minBackgroundWidth, 0, 0, 0);
                    paramBalloonBg2.setMargins(0, 0, scaledRightMargin - scaleFactor, 0);
                } else {
                    paramBalloonBg1.setMargins(leftMargin, 0, 0, 0);
                    paramBalloonBg2.setMargins(0, 0, rightMargin - scaleFactor, 0);
                }
                paramBalloonContent.setMargins(horizontalContentMargin, verticalTextMargin,
                        horizontalContentMargin, arrowHeight + verticalTextMargin);
                break;
            case DIRECTION_BOTTOM_LEFT:
                balloonPopup.setPivot(arrowPositionX - balloonX + scaleMargin, scaleMargin);
                paramBalloonBg2.gravity = 53;
                paramBalloonBg1.gravity = 53;
                paramBalloonBubble.gravity = 53;
                paramBalloonBg1.setMargins(0, 0, rightMargin - anchorWidth, 0);
                paramBalloonBg2.setMargins((anchorWidth + leftMargin) - scaleFactor, 0, 0, 0);
                paramBalloonContent.setMargins(horizontalContentMargin, arrowHeight + verticalTextMargin,
                        horizontalContentMargin, verticalTextMargin);
                break;
            case DIRECTION_BOTTOM_RIGHT:
                balloonPopup.setPivot(arrowPositionX - balloonX + scaleMargin, scaleMargin);
                balloonBg1.setRotationY(180.0f);
                balloonBg2.setRotationY(180.0f);
                paramBalloonBg2.gravity = 51;
                paramBalloonBg1.gravity = 51;
                paramBalloonBubble.gravity = 51;
                paramBalloonBg1.setMargins(leftMargin, 0, 0, 0);
                paramBalloonBg2.setMargins(0, 0, rightMargin - scaleFactor, 0);
                paramBalloonContent.setMargins(horizontalContentMargin, arrowHeight + verticalTextMargin,
                        horizontalContentMargin, verticalTextMargin);
                break;
            default:
                break;
        }

        paramBalloonBubble.setMargins(leftMargin + scaleMargin, topMargin + scaleMargin,
                (rightMargin - anchorWidth) + scaleMargin, bottomMargin + scaleMargin);
        paramBalloonPanel.setMargins(scaleMargin, scaleMargin, scaleMargin, scaleMargin);
        balloonPopupX = balloonX - scaleMargin;
        balloonPopupY = balloonY - scaleMargin;
        balloonBubble.setLayoutParams(paramBalloonBubble);
        balloonPanel.setLayoutParams(paramBalloonPanel);
        balloonBg1.setLayoutParams(paramBalloonBg1);
        balloonBg2.setLayoutParams(paramBalloonBg2);
        balloonContent.setLayoutParams(paramBalloonContent);
        balloonPopup.setWidth(balloonWidth + (scaleMargin * 2));
        balloonPopup.setHeight(balloonHeight + (scaleMargin * 2));
    }

    /**
     * نفس animateScaleUp في المكتبة، لكن بدون الجزء الخاص بتحريك علامة السؤال (bubble)،
     * لأن الأيقونة الموجودة في الديالوج هي التي تلعب دورها.
     */
    private void animateScaleUp() {
        float pivotPanelX;
        float pivotPanelY;
        switch (arrowDirection) {
            case DIRECTION_TOP_LEFT:
            case DIRECTION_TOP_RIGHT:
                pivotPanelX = arrowPositionX - balloonX;
                pivotPanelY = balloonHeight;
                break;
            case DIRECTION_BOTTOM_LEFT:
                pivotPanelX = arrowPositionX - balloonX;
                pivotPanelY = 0.0f;
                break;
            case DIRECTION_BOTTOM_RIGHT:
            default:
                pivotPanelX = anchorX - balloonX;
                pivotPanelY = 0.0f;
                break;
        }

        // في المكتبة الأصلية: float panelScale = questionHeight / mBalloonHeight (قسمة int على int)
        // والناتج صفر دائماً، أي أن النافذة تبدأ من ارتفاع صفر. نكتبها صراحةً لنحصل على نفس الشكل.
        final float startScaleY = 0.0f;

        AnimationSet animationPanel = new AnimationSet(false);
        Animation animationPanelScale = new ScaleAnimation(ANIMATION_START_SCALE_X, 1.0f, startScaleY, 1.0f,
                0, pivotPanelX, 0, pivotPanelY);
        animationPanelScale.setInterpolator(interpolatorElasticCustom);
        animationPanelScale.setDuration(ANIMATION_DURATION_SHOW_SCALE);
        Animation animationPanelAlpha = new AlphaAnimation(0.0f, 1.0f);
        animationPanelAlpha.setInterpolator(interpolatorSineInOut33);
        animationPanelAlpha.setDuration(ANIMATION_DURATION_EXPAND_ALPHA);
        animationPanel.addAnimation(animationPanelScale);
        animationPanel.addAnimation(animationPanelAlpha);
        balloonPanel.startAnimation(animationPanel);

        Animation animationText = new AlphaAnimation(0.0f, 1.0f);
        animationText.setInterpolator(interpolatorSineInOut33);
        animationText.setStartOffset(ANIMATION_OFFSET_EXPAND_TEXT);
        animationText.setDuration(ANIMATION_DURATION_EXPAND_TEXT);
        messageView.startAnimation(animationText);
    }

    private void calculateArrowDirection() {
        int[] location = new int[2];
        parentView.getLocationInWindow(location);
        int parentY = location[1] + (parentView.getHeight() / 2);
        if (arrowPositionX * 2 <= displayMetrics.widthPixels) {
            arrowDirection = arrowPositionY <= parentY ? DIRECTION_TOP_RIGHT : DIRECTION_BOTTOM_RIGHT;
        } else {
            arrowDirection = arrowPositionY <= parentY ? DIRECTION_TOP_LEFT : DIRECTION_BOTTOM_LEFT;
        }
    }

    private void calculateArrowPosition() {
        int[] location = new int[2];
        parentView.getLocationInWindow(location);
        int x = location[0] + (parentView.getWidth() / 2);
        int y = location[1] + (parentView.getHeight() / 2);
        if (y * 2 <= displayMetrics.heightPixels) {
            arrowPositionY = (parentView.getHeight() / 2) + y;
        } else {
            arrowPositionY = y - (parentView.getHeight() / 2);
        }
        arrowPositionX = x;
    }

    @SuppressLint("RestrictedApi")
    private void calculatePopupSize() {
        int balloonMaxWidth;
        displayMetrics = resources.getDisplayMetrics();
        int screenWidthDp = resources.getConfiguration().screenWidthDp;
        int balloonMinWidth = arrowWidth + (horizontalTextMargin * 2);
        if (SeslConfigurationReflector.isDexEnabled(context.getResources().getConfiguration())) {
            int windowWidthInDexMode = parentView.getRootView().getMeasuredWidth();
            int[] windowLocation = new int[2];
            parentView.getRootView().getLocationOnScreen(windowLocation);
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
                balloonMaxWidth = (int) (displayMetrics.widthPixels * 0.83f);
            } else if (screenWidthDp <= 960) {
                balloonMaxWidth = (int) (displayMetrics.widthPixels * 0.6f);
            } else if (screenWidthDp <= 1280) {
                balloonMaxWidth = (int) (displayMetrics.widthPixels * 0.45f);
            } else {
                balloonMaxWidth = (int) (displayMetrics.widthPixels * 0.25f);
            }
        }
        if (!isMessageViewMeasured) {
            messageView.measure(0, 0);
            initialMessageViewWidth = messageView.getMeasuredWidth();
            isMessageViewMeasured = true;
        }
        int width = initialMessageViewWidth + (horizontalTextMargin * 2);
        if (width < balloonMinWidth) {
            width = balloonMinWidth;
        } else if (width > balloonMaxWidth) {
            width = balloonMaxWidth;
        }
        balloonWidth = width;
        messageView.setWidth(width - (horizontalTextMargin * 2));
        messageView.measure(0, 0);
        balloonHeight = messageView.getMeasuredHeight() + (verticalTextMargin * 2) + arrowHeight;
    }

    @SuppressLint("RestrictedApi")
    private void calculatePopupPosition() {
        getDisplayFrame(displayFrame);
        if (balloonX < 0) {
            if (arrowDirection == DIRECTION_BOTTOM_RIGHT || arrowDirection == DIRECTION_TOP_RIGHT) {
                balloonX = (arrowPositionX + arrowWidth) - (balloonWidth / 2);
            } else {
                balloonX = (arrowPositionX - arrowWidth) - (balloonWidth / 2);
            }
        }
        if (arrowDirection == DIRECTION_BOTTOM_RIGHT || arrowDirection == DIRECTION_TOP_RIGHT) {
            if (arrowPositionX < displayFrame.left + sideMargin + horizontalTextMargin) {
                arrowPositionX = displayFrame.left + sideMargin + horizontalTextMargin;
            } else if (arrowPositionX > ((displayFrame.right - sideMargin) - horizontalTextMargin) - arrowWidth) {
                arrowPositionX = ((displayFrame.right - sideMargin) - horizontalTextMargin) - arrowWidth;
            }
        } else if (arrowPositionX < displayFrame.left + sideMargin + horizontalTextMargin + arrowWidth) {
            arrowPositionX = displayFrame.left + sideMargin + horizontalTextMargin + arrowWidth;
        } else if (arrowPositionX > (displayFrame.right - sideMargin) - horizontalTextMargin) {
            arrowPositionX = (displayFrame.right - sideMargin) - horizontalTextMargin;
        }
        if (SeslConfigurationReflector.isDexEnabled(context.getResources().getConfiguration())) {
            int windowWidthInDexMode = parentView.getRootView().getMeasuredWidth();
            int[] windowLocation = new int[2];
            parentView.getRootView().getLocationOnScreen(windowLocation);
            if (windowLocation[0] < 0) {
                windowWidthInDexMode += windowLocation[0];
            }
            if (balloonX < displayFrame.left + sideMargin) {
                balloonX = displayFrame.left + sideMargin;
            } else if (balloonX + balloonWidth > windowWidthInDexMode - sideMargin) {
                balloonX = (windowWidthInDexMode - sideMargin) - balloonWidth;
                if (windowLocation[0] < 0) {
                    balloonX = balloonX - windowLocation[0];
                }
            }
        } else if (balloonX < displayFrame.left + sideMargin) {
            balloonX = displayFrame.left + sideMargin;
        } else if (balloonX + balloonWidth > displayFrame.right - sideMargin) {
            balloonX = (displayFrame.right - sideMargin) - balloonWidth;
        }
        switch (arrowDirection) {
            case DIRECTION_TOP_LEFT:
                anchorX = arrowPositionX - anchorWidth;
                anchorY = arrowPositionY - anchorHeight;
                balloonY = arrowPositionY - balloonHeight;
                break;
            case DIRECTION_TOP_RIGHT:
                anchorX = arrowPositionX;
                anchorY = arrowPositionY - anchorHeight;
                balloonY = arrowPositionY - balloonHeight;
                break;
            case DIRECTION_BOTTOM_LEFT:
                anchorX = arrowPositionX - anchorWidth;
                anchorY = arrowPositionY;
                balloonY = arrowPositionY;
                break;
            case DIRECTION_BOTTOM_RIGHT:
                anchorX = arrowPositionX;
                anchorY = arrowPositionY;
                balloonY = arrowPositionY;
                break;
            default:
                break;
        }
    }

    @SuppressWarnings("deprecation")
    private android.view.Display getDisplay() {
        return windowManager.getDefaultDisplay();
    }

    private boolean isNavigationbarHide() {
        return Settings.Global.getInt(context.getContentResolver(), "navigationbar_hide_bar_enabled", 0) == 1;
    }

    private int getNavigationbarHeight() {
        int resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return resources.getDimensionPixelSize(resourceId);
        }
        return 0;
    }

    private boolean isTablet() {
        DisplayMetrics realMetrics = new DisplayMetrics();
        getDisplay().getRealMetrics(realMetrics);
        int shortSize = Math.min(realMetrics.widthPixels, realMetrics.heightPixels);
        int shortSizeDp = (shortSize * 160) / realMetrics.densityDpi;
        return shortSizeDp >= 600;
    }

    private void getDisplayFrame(Rect screenRect) {
        DisplayCutout displayCutout;
        int navigationbarHeight = getNavigationbarHeight();
        boolean navigationbarHide = isNavigationbarHide();
        int displayRotation = getDisplay().getRotation();
        DisplayMetrics realMetrics = new DisplayMetrics();
        getDisplay().getRealMetrics(realMetrics);
        screenRect.left = 0;
        screenRect.top = 0;
        screenRect.right = displayMetrics.widthPixels;
        screenRect.bottom = displayMetrics.heightPixels;
        Rect bounds = new Rect();
        parentView.getRootView().getWindowVisibleDisplayFrame(bounds);
        if (isTablet()) {
            if (realMetrics.widthPixels == displayMetrics.widthPixels
                    && realMetrics.heightPixels - displayMetrics.heightPixels == navigationbarHeight
                    && navigationbarHide) {
                screenRect.bottom += navigationbarHeight;
            }
        } else {
            switch (displayRotation) {
                case Surface.ROTATION_0:
                    if (realMetrics.widthPixels == displayMetrics.widthPixels
                            && realMetrics.heightPixels - displayMetrics.heightPixels == navigationbarHeight
                            && navigationbarHide) {
                        screenRect.bottom += navigationbarHeight;
                    }
                    break;
                case Surface.ROTATION_90:
                    if (realMetrics.heightPixels == displayMetrics.heightPixels
                            && realMetrics.widthPixels - displayMetrics.widthPixels == navigationbarHeight
                            && navigationbarHide) {
                        screenRect.right += navigationbarHeight;
                    }
                    WindowInsets windowInsets = parentView.getRootWindowInsets();
                    if (windowInsets != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                            && (displayCutout = windowInsets.getDisplayCutout()) != null) {
                        screenRect.left += displayCutout.getSafeInsetLeft();
                        screenRect.right += displayCutout.getSafeInsetLeft();
                    }
                    break;
                case Surface.ROTATION_180:
                    if (realMetrics.widthPixels == displayMetrics.widthPixels
                            && realMetrics.heightPixels - displayMetrics.heightPixels == navigationbarHeight) {
                        if (navigationbarHide) {
                            screenRect.bottom += navigationbarHeight;
                        } else {
                            screenRect.top += navigationbarHeight;
                            screenRect.bottom += navigationbarHeight;
                        }
                    } else if (realMetrics.widthPixels == displayMetrics.widthPixels
                            && bounds.top == navigationbarHeight) {
                        screenRect.top += navigationbarHeight;
                        screenRect.bottom += navigationbarHeight;
                    }
                    break;
                case Surface.ROTATION_270:
                    if (realMetrics.heightPixels == displayMetrics.heightPixels
                            && realMetrics.widthPixels - displayMetrics.widthPixels == navigationbarHeight) {
                        if (navigationbarHide) {
                            screenRect.right += navigationbarHeight;
                        } else {
                            screenRect.left += navigationbarHeight;
                            screenRect.right += navigationbarHeight;
                        }
                    } else if (realMetrics.heightPixels == displayMetrics.heightPixels
                            && bounds.left == navigationbarHeight) {
                        screenRect.left += navigationbarHeight;
                        screenRect.right += navigationbarHeight;
                    }
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * نفس TipWindow في المكتبة: أنيميشن الإغلاق (تصغير 0.81 + اختفاء) يعمل سواء أُغلقت
     * النافذة بالكود أو بلمسة خارجها.
     */
    private static class TipWindow extends PopupWindow {
        private boolean isDismissing = false;
        private boolean isUsingDismissAnimation = true;
        private float pivotX = 0.0f;
        private float pivotY = 0.0f;

        private TipWindow(View contentView, int width, int height, boolean focusable) {
            super(contentView, width, height, focusable);
        }

        void setUseDismissAnimation(boolean useAnimation) {
            this.isUsingDismissAnimation = useAnimation;
        }

        void setPivot(float pivotX, float pivotY) {
            this.pivotX = pivotX;
            this.pivotY = pivotY;
        }

        @Override
        public void dismiss() {
            if (isUsingDismissAnimation && !isDismissing) {
                animateViewOut();
            } else {
                super.dismiss();
            }
        }

        private void animateViewOut() {
            AnimationSet animationSet = new AnimationSet(true);
            Animation animScale = new ScaleAnimation(1.0f, 0.81f, 1.0f, 0.81f, 0, pivotX, 0, pivotY);
            animScale.setInterpolator(interpolatorElasticCustom);
            animScale.setDuration(ANIMATION_DURATION_DISMISS_SCALE);
            Animation animAlpha = new AlphaAnimation(1.0f, 0.0f);
            animAlpha.setInterpolator(interpolatorSineInOut33);
            animAlpha.setDuration(ANIMATION_DURATION_DISMISS_ALPHA);
            animationSet.addAnimation(animScale);
            animationSet.addAnimation(animAlpha);
            animationSet.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {
                    isDismissing = true;
                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    TipWindow.super.dismiss();
                    isDismissing = false;
                }

                @Override
                public void onAnimationRepeat(Animation animation) {
                }
            });
            getContentView().startAnimation(animationSet);
        }
    }
}
