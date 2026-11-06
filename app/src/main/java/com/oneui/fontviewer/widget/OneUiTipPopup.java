package com.oneui.fontviewer.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;
import android.view.animation.Interpolator;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.appcompat.animation.SeslAnimationUtils;
import androidx.reflect.content.res.SeslConfigurationReflector;

import com.oneui.fontviewer.BuildConfig;
import com.oneui.fontviewer.R;

import java.lang.reflect.Constructor;

/**
 * Java port of the TipPopup class from OneUI Design 8.
 * Uses the resources already shipped with OneUI Design 4 (the sem_tip_popup_* files).
 */
public class OneUiTipPopup {

    public enum Mode {
        NORMAL,
        TRANSLUCENT
    }

    public enum Direction {
        DEFAULT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT,
        TOP_LEFT,
        TOP_RIGHT
    }

    private enum Type {
        BALLOON_SIMPLE,
        BALLOON_ACTION,
        BALLOON_CUSTOM
    }

    public enum State {
        DISMISSED,
        EXPANDED,
        HINT
    }

    public interface OnDismissListener {
        void onDismiss();
    }

    public interface OnStateChangeListener {
        void onStateChanged(State state);
    }

    private static final long ANIMATION_DURATION_BOUNCE_SCALE1 = 167L;
    private static final long ANIMATION_DURATION_BOUNCE_SCALE2 = 250L;
    private static final long ANIMATION_DURATION_DISMISS_ALPHA = 167L;
    private static final long ANIMATION_DURATION_DISMISS_SCALE = 167L;
    private static final long ANIMATION_DURATION_EXPAND_ALPHA = 83L;
    private static final long ANIMATION_DURATION_EXPAND_SCALE = 500L;
    private static final long ANIMATION_DURATION_EXPAND_TEXT = 167L;
    private static final long ANIMATION_DURATION_SHOW_SCALE = 500L;
    private static final long ANIMATION_OFFSET_BOUNCE_SCALE = 3000L;
    private static final long ANIMATION_OFFSET_EXPAND_TEXT = 333L;

    private static final int MSG_TIMEOUT = 0;
    private static final int MSG_DISMISS = 1;
    private static final int MSG_SCALE_UP = 2;
    private static final String TAG = "OneUiTipPopup";
    private static final long TIMEOUT_DURATION_MS = 7100L;

    private static Interpolator interpolatorSineInOut33;
    private static Interpolator interpolatorSineInOut70;
    private static Interpolator interpolatorElastic50;
    private static Interpolator interpolatorElasticCustom;

    private final View parentView;
    private final Context context;
    private final Resources resources;
    private final WindowManager windowManager;
    private final Mode mode;

    private final Button actionView;
    private View.OnClickListener actionClickListener;
    private CharSequence actionText;
    private Integer actionTextColor;

    private Direction arrowDirection = Direction.DEFAULT;
    private int arrowPositionX = -1;
    private int arrowPositionY = -1;
    private final int arrowHeight;
    private final int arrowWidth;

    private final TextView messageView;

    private int backgroundColor;
    private ImageView balloonBg1;
    private ImageView balloonBg2;
    private FrameLayout balloonBubble;
    private ImageView balloonBubbleHint;
    private ImageView balloonBubbleIcon;
    private FrameLayout balloonContent;
    private int balloonHeight = 0;
    private FrameLayout balloonPanel;
    private TipWindow balloonPopup;
    private int balloonPopupX = 0;
    private int balloonPopupY = 0;
    private final View balloonView;
    private int balloonWidth = 0;
    private int balloonX = -1;
    private int balloonY = 0;
    private Integer iconColor;
    private ImageView bubbleBackground;
    private int bubbleHeight = 0;
    private ImageView bubbleIcon;
    private TipWindow bubblePopup;
    private int bubblePopupX = 0;
    private int bubblePopupY = 0;
    private final View bubbleView;
    private int bubbleWidth = 0;
    private int bubbleX = 0;
    private int bubbleY = 0;

    private DisplayMetrics displayMetrics;
    private boolean forceRealDisplay = false;
    // true when the anchor lives inside a Dialog (see setDialogMode)
    private boolean dialogMode = false;
    // true when the pop-up windows are positioned in absolute screen coordinates
    private boolean useScreenCoordinates = false;
    private CharSequence hintDescription;

    private int initialMessageViewWidth = 0;
    private boolean isDefaultPosition = true;
    private boolean isMessageViewMeasured = false;
    private CharSequence messageText;
    private Integer messageTextColor;

    private boolean needToCallParentViewsOnClick = false;
    private OnDismissListener onDismissListener;
    private OnStateChangeListener onStateChangeListener;

    private int scaleMargin;
    private int sideMargin;

    private State state = State.HINT;
    private Type type = Type.BALLOON_SIMPLE;

    private final Rect displayFrame = new Rect();
    private final int horizontalTextMargin;
    private final int verticalTextMargin;
    private Handler handler;

    public OneUiTipPopup(View parentView) {
        this(parentView, Mode.NORMAL);
    }

    public OneUiTipPopup(View parentView, Mode mode) {
        this.parentView = parentView;
        this.mode = mode;
        this.context = parentView.getContext();
        this.resources = context.getResources();
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.displayMetrics = resources.getDisplayMetrics();

        this.arrowHeight = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_balloon_arrow_height);
        this.arrowWidth = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_balloon_arrow_width);
        this.horizontalTextMargin = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_balloon_message_margin_horizontal);
        this.verticalTextMargin = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_balloon_message_margin_vertical);
        this.scaleMargin = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_scale_margin);
        this.sideMargin = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_side_margin);

        this.backgroundColor = context.getColor(R.color.sem_tip_popup_background_color);

        initInterpolators();

        LayoutInflater inflater = LayoutInflater.from(context);
        this.bubbleView = inflater.inflate(R.layout.sem_tip_popup_bubble, null);
        this.balloonView = inflater.inflate(R.layout.sem_tip_popup_balloon, null);
        this.messageView = balloonView.findViewById(R.id.sem_tip_popup_message);
        this.actionView = balloonView.findViewById(R.id.sem_tip_popup_action);
        messageView.setVisibility(View.GONE);
        actionView.setVisibility(View.GONE);

        initBubblePopup(mode);
        initBalloonPopup(mode);

        if (mode == Mode.TRANSLUCENT) {
            int translucentTextColor = context.getColor(R.color.sem_tip_popup_text_color_translucent);
            messageView.setTextColor(translucentTextColor);
            actionView.setTextColor(translucentTextColor);
        }

        bubblePopup.setOnDismissListener(() -> {
            if (state == State.HINT) {
                state = State.DISMISSED;
                if (onStateChangeListener != null) {
                    onStateChangeListener.onStateChanged(state);
                    debugLog("isShowing : " + isShowing());
                }
                if (handler != null) {
                    handler.removeCallbacksAndMessages(null);
                    handler = null;
                }
                debugLog("onDismiss - BubblePopup");
            }
        });

        balloonPopup.setOnDismissListener(() -> {
            state = State.DISMISSED;
            if (onStateChangeListener != null) {
                onStateChangeListener.onStateChanged(state);
                debugLog("isShowing : " + isShowing());
            }
            debugLog("onDismiss - BalloonPopup");
            dismissBubble(false);
            if (handler != null) {
                handler.removeCallbacksAndMessages(null);
                handler = null;
            }
        });

        balloonView.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                        AccessibilityNodeInfo.ACTION_CLICK,
                        context.getString(R.string.oui_common_close)));
            }
        });
    }

    private void initInterpolators() {
        if (interpolatorSineInOut33 == null) {
            interpolatorSineInOut33 = AnimationUtils.loadInterpolator(context, R.interpolator.sine_in_out_33);
        }
        if (interpolatorSineInOut70 == null) {
            interpolatorSineInOut70 = AnimationUtils.loadInterpolator(context, R.interpolator.sine_in_out_70);
        }
        if (interpolatorElastic50 == null) {
            interpolatorElastic50 = SeslAnimationUtils.ELASTIC_50;
        }
        if (interpolatorElasticCustom == null) {
            interpolatorElasticCustom = createElasticInterpolator(1.0f, 1.3f);
        }
    }

    @SuppressLint("PrivateApi")
    private static Interpolator createElasticInterpolator(float amplitude, float period) {
        try {
            Constructor<?> constructor = Class.forName("androidx.appcompat.animation.SeslElasticInterpolator")
                    .getDeclaredConstructor(float.class, float.class);
            constructor.setAccessible(true);
            Object interpolator = constructor.newInstance(amplitude, period);
            if (interpolator instanceof Interpolator) {
                return (Interpolator) interpolator;
            }
        } catch (Throwable ignored) {
        }
        return SeslAnimationUtils.ELASTIC_40;
    }

    private void initBubblePopup(Mode mode) {
        bubbleBackground = bubbleView.findViewById(R.id.sem_tip_popup_bubble_bg);
        bubbleIcon = bubbleView.findViewById(R.id.sem_tip_popup_bubble_icon);

        if (mode == Mode.TRANSLUCENT) {
            bubbleBackground.setImageResource(R.drawable.sem_tip_popup_hint_background_translucent);
            bubbleBackground.setImageTintList(null);
            bubbleIcon.setImageResource(hintIconTranslucentRes());
            bubbleIcon.setImageTintList(null);
            bubbleWidth = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_bubble_width_translucent);
            bubbleHeight = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_bubble_height_translucent);
        } else {
            bubbleWidth = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_bubble_width);
            bubbleHeight = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_bubble_height);
        }

        bubblePopup = new TipWindowBubble(bubbleView, bubbleWidth, bubbleHeight, false);
        bubblePopup.setTouchable(true);
        bubblePopup.setOutsideTouchable(true);
        bubblePopup.setAttachedInDecor(false);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initBalloonPopup(Mode mode) {
        balloonBubble = balloonView.findViewById(R.id.sem_tip_popup_balloon_bubble);
        balloonBubble.setVisibility(View.VISIBLE);
        balloonBubbleHint = balloonView.findViewById(R.id.sem_tip_popup_balloon_bubble_hint);
        balloonBubbleIcon = balloonView.findViewById(R.id.sem_tip_popup_balloon_bubble_icon);
        balloonPanel = balloonView.findViewById(R.id.sem_tip_popup_balloon_panel);
        balloonPanel.setVisibility(View.GONE);
        balloonContent = balloonView.findViewById(R.id.sem_tip_popup_balloon_content);
        balloonBg1 = balloonView.findViewById(R.id.sem_tip_popup_balloon_bg_01);
        balloonBg2 = balloonView.findViewById(R.id.sem_tip_popup_balloon_bg_02);

        if (mode == Mode.TRANSLUCENT) {
            balloonBg1.setBackgroundResource(R.drawable.sem_tip_popup_balloon_background_left_translucent);
            balloonBg1.setBackgroundTintList(null);
            balloonBg2.setBackgroundResource(R.drawable.sem_tip_popup_balloon_background_right_translucent);
            balloonBg2.setBackgroundTintList(null);
        }

        balloonPopup = new TipWindowBalloon(balloonView, balloonWidth, balloonHeight, true);
        balloonPopup.setFocusable(true);
        balloonPopup.setTouchable(true);
        balloonPopup.setOutsideTouchable(true);
        balloonPopup.setAttachedInDecor(false);
        balloonPopup.setTouchInterceptor((v, event) -> {
            if (needToCallParentViewsOnClick && parentView.hasOnClickListeners()
                    && (event.getAction() == MotionEvent.ACTION_DOWN
                    || event.getAction() == MotionEvent.ACTION_OUTSIDE)) {
                int[] outLocation = new int[2];
                parentView.getLocationOnScreen(outLocation);
                Rect parentViewBounds = new Rect(
                        outLocation[0],
                        outLocation[1],
                        outLocation[0] + parentView.getWidth(),
                        outLocation[1] + parentView.getHeight());
                if (parentViewBounds.contains((int) event.getRawX(), (int) event.getRawY())) {
                    debugLog("callOnClick for parent view");
                    parentView.callOnClick();
                }
            }
            return false;
        });
    }

    public void setOnStateChangeListener(OnStateChangeListener changeListener) {
        this.onStateChangeListener = changeListener;
    }

    public void setOnDismissListener(OnDismissListener onDismissListener) {
        this.onDismissListener = onDismissListener;
    }

    public void show() {
        show(Direction.DEFAULT, false);
    }

    public void show(Direction direction) {
        show(direction, false);
    }

    public void show(Direction direction, boolean dismissOnTimeout) {
        displayMetrics = resources.getDisplayMetrics();
        setInternal();
        setPositionsAndSizes(direction);
        setBubblePanel();
        setBalloonPanel();
        showInternal();
        if (state == State.HINT && dismissOnTimeout) {
            scheduleTimeout();
        }
    }

    private void setPositionsAndSizes(Direction direction) {
        if (arrowPositionX == -1 || arrowPositionY == -1) {
            calculateArrowPosition();
        }
        if (direction == Direction.DEFAULT) {
            calculateArrowDirection(arrowPositionX, arrowPositionY);
        } else {
            arrowDirection = direction;
        }
        calculatePopupSize();
        calculatePopupPosition();
    }

    public void setMessage(CharSequence message) {
        this.messageText = message;
    }

    public void setAction(CharSequence actionText, View.OnClickListener listener) {
        this.actionText = actionText;
        this.actionClickListener = listener;
    }

    public void semCallParentViewsOnClick(boolean needToCall) {
        this.needToCallParentViewsOnClick = needToCall;
    }

    public boolean isShowing() {
        return bubblePopup.isShowing() || balloonPopup.isShowing();
    }

    public void dismiss() {
        dismiss(true);
    }

    public void dismiss(boolean withAnimation) {
        bubblePopup.setUseDismissAnimation(withAnimation);
        debugLog("bubblePopup.isDismissing = " + bubblePopup.isDismissing);
        bubblePopup.dismiss();

        balloonPopup.setUseDismissAnimation(withAnimation);
        debugLog("balloonPopup.isDismissing = " + balloonPopup.isDismissing);
        balloonPopup.dismiss();

        if (onDismissListener != null) {
            onDismissListener.onDismiss();
        }
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
            handler = null;
        }
    }

    public void setExpanded(boolean expanded) {
        if (expanded) {
            state = State.EXPANDED;
            scaleMargin = 0;
            return;
        }
        scaleMargin = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_scale_margin);
    }

    public void setTargetPosition(int x, int y) {
        if (x < 0 || y < 0) return;

        isDefaultPosition = false;
        arrowPositionX = x;
        arrowPositionY = y;
    }

    public void setHintDescription(CharSequence hintDescription) {
        this.hintDescription = hintDescription;
    }

    public void update() {
        update(Direction.DEFAULT, false);
    }

    public void update(Direction direction, boolean dismissOnTimeout) {
        if (!isShowing()) {
            return;
        }
        doOnNextLayout(parentView, () -> parentView.post(() -> {
            debugLog("update");
            resetBalloonProperties();
            displayMetrics = resources.getDisplayMetrics();
            setInternal();
            setPositionsAndSizes(direction);
            setBubblePanel();
            setBalloonPanel();
            switch (state) {
                case EXPANDED:
                    balloonPopup.update(balloonPopupX, balloonPopupY,
                            balloonPopup.getWidth(), balloonPopup.getHeight());
                    break;
                case HINT:
                    bubblePopup.update(bubblePopupX, bubblePopupY,
                            bubblePopup.getWidth(), bubblePopup.getHeight());
                    if (dismissOnTimeout) {
                        debugLog("Timer Reset!");
                        scheduleTimeout();
                    }
                    break;
                case DISMISSED:
                default:
                    break;
            }
        }));
    }

    private static void doOnNextLayout(View view, Runnable action) {
        view.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                v.removeOnLayoutChangeListener(this);
                action.run();
            }
        });
    }

    private void resetBalloonProperties() {
        balloonX = -1;
        balloonY = -1;
        isMessageViewMeasured = false;
        resetRotation(balloonBg1);
        resetRotation(balloonBg2);
        resetRotation(balloonBubbleHint);
        if (isDefaultPosition) {
            arrowPositionX = -1;
            arrowPositionY = -1;
        }
    }

    private static void resetRotation(View view) {
        view.setRotation(0f);
        view.setRotationX(0f);
        view.setRotationY(0f);
    }

    public void setMessageTextColor(@ColorInt int color) {
        messageTextColor = opaque(color);
    }

    public void setActionTextColor(@ColorInt int color) {
        actionTextColor = opaque(color);
    }

    public void setBackgroundColor(@ColorInt int color) {
        backgroundColor = opaque(color);
    }

    public void setBackgroundColorWithAlpha(@ColorInt int color) {
        backgroundColor = color;
    }

    public void setIconTint(@ColorInt int color) {
        iconColor = opaque(color);
    }

    public void setOutsideTouchEnabled(boolean enabled) {
        bubblePopup.setFocusable(enabled);
        bubblePopup.setOutsideTouchable(enabled);
        balloonPopup.setFocusable(enabled);
        balloonPopup.setOutsideTouchable(enabled);
        debugLog("outside enabled : " + enabled);
    }

    public void setPopupWindowClippingEnabled(boolean enabled) {
        bubblePopup.setClippingEnabled(enabled);
        balloonPopup.setClippingEnabled(enabled);
        forceRealDisplay = !enabled;
        sideMargin = enabled ? resources.getDimensionPixelSize(R.dimen.sem_tip_popup_side_margin) : 0;
        debugLog("clipping enabled : " + enabled);
    }

    /**
     * Call this when the anchor view ({@code parentView}) is inside a Dialog.
     * <p>
     * By default a PopupWindow is clipped to the window that contains its anchor. In an
     * Activity that window is the whole screen, so nothing is cut. Inside a Dialog the
     * containing window is the (smaller) dialog window, so the pop-up gets cut at the
     * dialog's edges. This is the difference between your app and the OneUI 8 sample app,
     * where the pop-up is shown from an Activity/Fragment and not from a Dialog.
     * <p>
     * With dialog mode enabled the pop-up windows are:
     * <ul>
     *     <li>clipped to the screen, not to the dialog window,</li>
     *     <li>positioned in absolute screen coordinates (all the position math here then uses
     *     {@code getLocationOnScreen()} instead of {@code getLocationInWindow()}),</li>
     *     <li>kept inside the horizontal bounds of the dialog window, so the pop-up doesn't
     *     stick out of the dialog.</li>
     * </ul>
     * Must be called before {@link #show()}.
     */
    public void setDialogMode(boolean enabled) {
        dialogMode = enabled;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            useScreenCoordinates = enabled;
            bubblePopup.setIsClippedToScreen(enabled);
            bubblePopup.setIsLaidOutInScreen(enabled);
            balloonPopup.setIsClippedToScreen(enabled);
            balloonPopup.setIsLaidOutInScreen(enabled);
        } else {
            // Before Android 10 there is no public way to lay the window out in screen
            // coordinates, so keep window coordinates and just drop the clipping.
            useScreenCoordinates = false;
            bubblePopup.setClippingEnabled(!enabled);
            balloonPopup.setClippingEnabled(!enabled);
        }
        debugLog("dialog mode : " + enabled + " / screen coordinates : " + useScreenCoordinates);
    }

    private void getAnchorLocation(int[] outLocation) {
        if (useScreenCoordinates) {
            parentView.getLocationOnScreen(outLocation);
        } else {
            parentView.getLocationInWindow(outLocation);
        }
    }

    private static int opaque(int color) {
        return (color & 0x00FFFFFF) | 0xFF000000;
    }

    private void setInternal() {
        if (handler == null) {
            handler = new Handler(Looper.getMainLooper()) {
                @Override
                public void handleMessage(Message message) {
                    switch (message.what) {
                        case MSG_TIMEOUT:
                            dismissBubble(true);
                            break;
                        case MSG_DISMISS:
                            dismissBubble(false);
                            break;
                        case MSG_SCALE_UP:
                            animateScaleUp();
                            break;
                        default:
                            break;
                    }
                }
            };
        }

        float currentFontScale = resources.getConfiguration().fontScale;
        int messageTextSize = resources.getDimensionPixelOffset(R.dimen.sem_tip_popup_balloon_message_text_size);
        int actionTextSize = resources.getDimensionPixelOffset(R.dimen.sem_tip_popup_balloon_action_text_size);

        if (currentFontScale > 1.2f) {
            messageView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    (float) Math.floor((float) Math.ceil(messageTextSize / currentFontScale) * 1.2f));
            actionView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    (float) Math.floor((float) Math.ceil(actionTextSize / currentFontScale) * 1.2f));
        }

        messageView.setText(messageText);

        if (TextUtils.isEmpty(actionText) || actionClickListener == null) {
            actionView.setVisibility(View.GONE);
            actionView.setOnClickListener(null);
            type = Type.BALLOON_SIMPLE;
        } else {
            actionView.setVisibility(View.VISIBLE);
            actionView.setText(actionText);
            actionView.setOnClickListener(view -> {
                if (actionClickListener != null) {
                    actionClickListener.onClick(view);
                }
                dismiss(true);
            });
            type = Type.BALLOON_ACTION;
        }

        bubbleIcon.setContentDescription(hintDescription);

        if (mode == Mode.TRANSLUCENT) return;

        if (messageTextColor != null) {
            messageView.setTextColor(messageTextColor);
        }

        if (actionTextColor != null) {
            actionView.setTextColor(actionTextColor);
        }

        bubbleBackground.setColorFilter(backgroundColor);
        balloonBubbleHint.setColorFilter(backgroundColor);

        balloonBg1.setBackgroundTintList(ColorStateList.valueOf(backgroundColor));
        balloonBg2.setBackgroundTintList(ColorStateList.valueOf(backgroundColor));

        if (iconColor != null) {
            bubbleIcon.setColorFilter(iconColor);
            balloonBubbleIcon.setColorFilter(iconColor);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void showInternal() {
        if (state != State.EXPANDED) {
            state = State.HINT;
            if (onStateChangeListener != null) {
                onStateChangeListener.onStateChanged(State.HINT);
                debugLog("isShowing : " + isShowing());
            }

            bubblePopup.showAtLocation(parentView, 0, bubblePopupX, bubblePopupY);
            animateViewIn();

            bubbleView.setOnTouchListener((v, event) -> {
                state = State.EXPANDED;
                if (onStateChangeListener != null) {
                    onStateChangeListener.onStateChanged(state);
                }
                balloonPopup.showAtLocation(parentView, 0, balloonPopupX, balloonPopupY);
                if (handler != null) {
                    handler.removeMessages(0);
                    handler.sendMessageDelayed(Message.obtain(handler, MSG_DISMISS), 10L);
                    handler.sendMessageDelayed(Message.obtain(handler, MSG_SCALE_UP), 20L);
                }
                return false;
            });
        } else {
            balloonBubble.setVisibility(View.GONE);
            balloonPanel.setVisibility(View.VISIBLE);
            messageView.setVisibility(View.VISIBLE);
            if (onStateChangeListener != null) {
                onStateChangeListener.onStateChanged(state);
            }
            balloonPopup.showAtLocation(parentView, 0, balloonPopupX, balloonPopupY);
            animateBalloonScaleUp();
        }

        balloonView.setOnTouchListener((v, event) -> {
            if (type == Type.BALLOON_SIMPLE) {
                dismiss(true);
                return false;
            }
            return false;
        });
    }

    private boolean isRtlLocale() {
        return isRTL() && !"iw_IL".equals(getLocale());
    }

    private int hintIconRes() {
        return isRtlLocale()
                ? R.drawable.sem_tip_popup_hint_icon_rtl
                : R.drawable.sem_tip_popup_hint_icon;
    }

    private int hintIconTranslucentRes() {
        return isRtlLocale()
                ? R.drawable.sem_tip_popup_hint_icon_translucent_rtl
                : R.drawable.sem_tip_popup_hint_icon_translucent;
    }

    private void setBubblePanel() {
        FrameLayout.LayoutParams paramBubblePanel = (FrameLayout.LayoutParams) bubbleBackground.getLayoutParams();
        if (mode == Mode.TRANSLUCENT) {
            paramBubblePanel.width = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_bubble_width_translucent);
            paramBubblePanel.height = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_bubble_height_translucent);
        }
        switch (arrowDirection) {
            case TOP_LEFT:
                bubblePopup.setPivot(bubblePopup.getWidth(), bubblePopup.getHeight());
                paramBubblePanel.gravity = Gravity.BOTTOM | Gravity.END;
                bubblePopupX = bubbleX - (scaleMargin * 2);
                bubblePopupY = bubbleY - (scaleMargin * 2);
                if (mode == Mode.NORMAL) {
                    bubbleBackground.setImageResource(R.drawable.sem_tip_popup_hint_background_03);
                    bubbleIcon.setImageResource(hintIconRes());
                } else {
                    bubbleBackground.setRotationX(180.0f);
                }
                break;

            case TOP_RIGHT:
                bubblePopup.setPivot(0.0f, bubblePopup.getHeight());
                paramBubblePanel.gravity = Gravity.BOTTOM | Gravity.START;
                bubblePopupX = bubbleX;
                bubblePopupY = bubbleY - (scaleMargin * 2);
                if (mode == Mode.NORMAL) {
                    bubbleBackground.setImageResource(R.drawable.sem_tip_popup_hint_background_04);
                    bubbleIcon.setImageResource(hintIconRes());
                } else {
                    bubbleBackground.setRotation(180.0f);
                }
                break;

            case BOTTOM_LEFT:
                bubblePopup.setPivot(bubblePopup.getWidth(), 0.0f);
                paramBubblePanel.gravity = Gravity.TOP | Gravity.END;
                bubblePopupX = bubbleX - (scaleMargin * 2);
                bubblePopupY = bubbleY;
                if (mode == Mode.NORMAL) {
                    bubbleBackground.setImageResource(R.drawable.sem_tip_popup_hint_background_01);
                    bubbleIcon.setImageResource(hintIconRes());
                }
                break;

            case BOTTOM_RIGHT:
                bubblePopup.setPivot(0.0f, 0.0f);
                paramBubblePanel.gravity = Gravity.TOP | Gravity.START;
                bubblePopupX = bubbleX;
                bubblePopupY = bubbleY;
                if (mode == Mode.NORMAL) {
                    bubbleBackground.setImageResource(R.drawable.sem_tip_popup_hint_background_02);
                    bubbleIcon.setImageResource(hintIconRes());
                } else {
                    bubbleBackground.setRotationY(180.0f);
                }
                break;

            case DEFAULT:
            default:
                throw new IllegalStateException();
        }
        bubbleBackground.setLayoutParams(paramBubblePanel);
        bubbleIcon.setLayoutParams(paramBubblePanel);
        bubblePopup.setWidth(bubbleWidth + (scaleMargin * 2));
        bubblePopup.setHeight(bubbleHeight + (scaleMargin * 2));
    }

    private void setBalloonPanel() {
        debugLog("setBalloonPanel()");

        int leftMargin = bubbleX - balloonX;
        int rightMargin = (balloonX + balloonWidth) - bubbleX;
        int topMargin = bubbleY - balloonY;
        int bottomMargin = (balloonY + balloonHeight) - (bubbleY + bubbleHeight);

        int minBackgroundWidth = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_balloon_background_minwidth);

        debugLog("leftMargin[" + leftMargin + "]");
        debugLog("rightMargin[" + rightMargin + "] balloonWidth[" + balloonWidth + "]");

        int horizontalContentMargin = horizontalTextMargin
                - resources.getDimensionPixelSize(R.dimen.sem_tip_popup_button_padding_horizontal);
        int verticalButtonPadding = actionView.getVisibility() == View.VISIBLE
                ? resources.getDimensionPixelSize(R.dimen.sem_tip_popup_button_padding_vertical)
                : 0;
        FrameLayout.LayoutParams paramBalloonBubble = (FrameLayout.LayoutParams) balloonBubble.getLayoutParams();
        FrameLayout.LayoutParams paramBalloonPanel = (FrameLayout.LayoutParams) balloonPanel.getLayoutParams();
        FrameLayout.LayoutParams paramBalloonContent = (FrameLayout.LayoutParams) balloonContent.getLayoutParams();
        FrameLayout.LayoutParams paramBalloonBg1 = (FrameLayout.LayoutParams) balloonBg1.getLayoutParams();
        FrameLayout.LayoutParams paramBalloonBg2 = (FrameLayout.LayoutParams) balloonBg2.getLayoutParams();

        int scaleFactor;
        if (mode == Mode.TRANSLUCENT) {
            balloonBubbleHint.setImageResource(R.drawable.sem_tip_popup_hint_background_translucent);
            balloonBubbleHint.setImageTintList(null);
            balloonBubbleIcon.setImageResource(hintIconTranslucentRes());
            balloonBubbleIcon.setImageTintList(null);
            paramBalloonBubble.width = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_bubble_width_translucent);
            paramBalloonBubble.height = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_bubble_height_translucent);
            scaleFactor = 0;
        } else if (Color.alpha(backgroundColor) < 255) {
            debugLog("Updating scaleFactor to 0 because transparency is applied to background.");
            scaleFactor = 0;
        } else {
            DisplayMetrics realMetrics = new DisplayMetrics();
            getRealMetrics(realMetrics);
            scaleFactor = (int) Math.ceil(realMetrics.density);
        }

        switch (arrowDirection) {
            case TOP_LEFT:
                balloonPopup.setPivot(
                        (arrowPositionX - balloonX) + scaleMargin,
                        balloonHeight + scaleMargin);

                if (mode == Mode.NORMAL) {
                    balloonBubbleHint.setImageResource(R.drawable.sem_tip_popup_hint_background_03);
                    balloonBubbleIcon.setImageResource(R.drawable.sem_tip_popup_hint_icon);
                } else {
                    balloonBubbleHint.setRotationX(180.0f);
                }

                balloonBg1.setRotationX(180.0f);
                balloonBg2.setRotationX(180.0f);
                paramBalloonBg2.gravity = Gravity.BOTTOM | Gravity.END;
                paramBalloonBg1.gravity = Gravity.BOTTOM | Gravity.END;
                paramBalloonBubble.gravity = Gravity.BOTTOM | Gravity.END;

                if (rightMargin - bubbleWidth < minBackgroundWidth) {
                    int scaledLeftMargin = balloonWidth - minBackgroundWidth;
                    paramBalloonBg1.setMargins(0, 0, minBackgroundWidth, 0);
                    paramBalloonBg2.setMargins(scaledLeftMargin - scaleFactor, 0, 0, 0);
                    debugLog("Right Margin is less then minimum background width!");
                    debugLog("updated !! leftMargin[" + scaledLeftMargin + "],  rightMargin[" + minBackgroundWidth + "]");
                } else {
                    paramBalloonBg1.setMargins(0, 0, rightMargin - bubbleWidth, 0);
                    paramBalloonBg2.setMargins(bubbleWidth + leftMargin, 0, 0, 0);
                }

                paramBalloonContent.setMargins(
                        horizontalContentMargin,
                        verticalTextMargin,
                        horizontalContentMargin,
                        (arrowHeight + verticalTextMargin) - verticalButtonPadding);
                break;

            case TOP_RIGHT:
                balloonPopup.setPivot(
                        arrowPositionX - balloonX + scaleMargin,
                        balloonHeight + scaleMargin);

                if (mode == Mode.NORMAL) {
                    balloonBubbleHint.setImageResource(R.drawable.sem_tip_popup_hint_background_04);
                    balloonBubbleIcon.setImageResource(R.drawable.sem_tip_popup_hint_icon);
                } else {
                    balloonBubbleHint.setRotation(180.0f);
                }

                balloonBg1.setRotation(180.0f);
                balloonBg2.setRotation(180.0f);
                paramBalloonBg2.gravity = Gravity.BOTTOM | Gravity.START;
                paramBalloonBg1.gravity = Gravity.BOTTOM | Gravity.START;
                paramBalloonBubble.gravity = Gravity.BOTTOM | Gravity.START;

                if (leftMargin < minBackgroundWidth) {
                    int scaledRightMargin = balloonWidth - minBackgroundWidth;
                    paramBalloonBg1.setMargins(minBackgroundWidth, 0, 0, 0);
                    paramBalloonBg2.setMargins(0, 0, scaledRightMargin - scaleFactor, 0);
                    debugLog("Left Margin is less then minimum background width!");
                    debugLog("updated !! leftMargin[" + minBackgroundWidth + "],  rightMargin[]");
                } else {
                    paramBalloonBg1.setMargins(leftMargin, 0, 0, 0);
                    paramBalloonBg2.setMargins(0, 0, rightMargin - scaleFactor, 0);
                }

                paramBalloonContent.setMargins(
                        horizontalContentMargin,
                        verticalTextMargin,
                        horizontalContentMargin,
                        (arrowHeight + verticalTextMargin) - verticalButtonPadding);
                break;

            case BOTTOM_LEFT:
                balloonPopup.setPivot(
                        arrowPositionX - balloonX + scaleMargin,
                        scaleMargin);

                if (mode == Mode.NORMAL) {
                    balloonBubbleHint.setImageResource(R.drawable.sem_tip_popup_hint_background_01);
                    balloonBubbleIcon.setImageResource(R.drawable.sem_tip_popup_hint_icon);
                }

                paramBalloonBg2.gravity = Gravity.TOP | Gravity.END;
                paramBalloonBg1.gravity = Gravity.TOP | Gravity.END;
                paramBalloonBubble.gravity = Gravity.TOP | Gravity.END;
                paramBalloonBg1.setMargins(0, 0, rightMargin - bubbleWidth, 0);
                paramBalloonBg2.setMargins((bubbleWidth + leftMargin) - scaleFactor, 0, 0, 0);

                paramBalloonContent.setMargins(
                        horizontalContentMargin,
                        arrowHeight + verticalTextMargin,
                        horizontalContentMargin,
                        verticalTextMargin - verticalButtonPadding);
                break;

            case BOTTOM_RIGHT:
                balloonPopup.setPivot(
                        arrowPositionX - balloonX + scaleMargin,
                        scaleMargin);

                if (mode == Mode.NORMAL) {
                    balloonBubbleHint.setImageResource(R.drawable.sem_tip_popup_hint_background_02);
                    balloonBubbleIcon.setImageResource(R.drawable.sem_tip_popup_hint_icon);
                } else {
                    balloonBubbleHint.setRotationY(180.0f);
                }

                balloonBg1.setRotationY(180.0f);
                balloonBg2.setRotationY(180.0f);
                paramBalloonBg2.gravity = Gravity.TOP | Gravity.START;
                paramBalloonBg1.gravity = Gravity.TOP | Gravity.START;
                paramBalloonBubble.gravity = Gravity.TOP | Gravity.START;
                paramBalloonBg1.setMargins(leftMargin, 0, 0, 0);
                paramBalloonBg2.setMargins(0, 0, rightMargin - scaleFactor, 0);

                paramBalloonContent.setMargins(
                        horizontalContentMargin,
                        arrowHeight + verticalTextMargin,
                        horizontalContentMargin,
                        verticalTextMargin - verticalButtonPadding);
                break;

            case DEFAULT:
            default:
                break;
        }

        paramBalloonBubble.setMargins(
                leftMargin + scaleMargin,
                topMargin + scaleMargin,
                (rightMargin - bubbleWidth) + scaleMargin,
                bottomMargin + scaleMargin);

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

    private void calculateArrowDirection(int arrowX, int arrowY) {
        if (isDefaultPosition) {
            int[] location = new int[2];
            getAnchorLocation(location);
            int parentY = location[1] + (parentView.getHeight() / 2);

            if (arrowX * 2 <= displayMetrics.widthPixels) {
                arrowDirection = arrowY <= parentY ? Direction.TOP_RIGHT : Direction.BOTTOM_RIGHT;
            } else {
                arrowDirection = arrowY <= parentY ? Direction.TOP_LEFT : Direction.BOTTOM_LEFT;
            }
        } else if (arrowX * 2 <= displayMetrics.widthPixels && arrowY * 2 <= displayMetrics.heightPixels) {
            arrowDirection = Direction.BOTTOM_RIGHT;
        } else if (arrowX * 2 > displayMetrics.widthPixels && arrowY * 2 <= displayMetrics.heightPixels) {
            arrowDirection = Direction.TOP_LEFT;
        } else if (arrowX * 2 <= displayMetrics.widthPixels && arrowY * 2 > displayMetrics.heightPixels) {
            arrowDirection = Direction.TOP_RIGHT;
        } else if (arrowX * 2 > displayMetrics.widthPixels && arrowY * 2 > displayMetrics.heightPixels) {
            arrowDirection = Direction.TOP_LEFT;
        }
        debugLog("calculateArrowDirection : arrow position (" + arrowX + ", " + arrowY + ") / arrowDirection = " + arrowDirection);
    }

    private void calculateArrowPosition() {
        int[] location = new int[2];
        getAnchorLocation(location);
        debugLog("calculateArrowPosition anchor location : " + location[0] + ", " + location[1]);

        arrowPositionX = location[0] + (parentView.getWidth() / 2);
        int y = location[1] + (parentView.getHeight() / 2);
        if (y * 2 <= displayMetrics.heightPixels) {
            arrowPositionY = (parentView.getHeight() / 2) + y;
        } else {
            arrowPositionY = y - (parentView.getHeight() / 2);
        }
        debugLog("calculateArrowPosition arrowPosition : " + arrowPositionX + ", " + arrowPositionY);
    }

    private void calculatePopupSize() {
        int balloonMaxWidth;
        if (isDeskTopMode()) {
            int[] windowLocation = new int[2];
            View rootView = parentView.getRootView();
            rootView.getLocationOnScreen(windowLocation);
            int windowWidthInDexMode = rootView.getMeasuredWidth() + Math.min(windowLocation[0], 0);
            debugLog("Window width in DexMode " + windowWidthInDexMode);
            balloonMaxWidth = maxWidthFor(windowWidthInDexMode, windowWidthInDexMode);
        } else {
            int screenWidthDp = resources.getConfiguration().screenWidthDp;
            debugLog("screen width DP " + screenWidthDp);
            balloonMaxWidth = maxWidthFor(screenWidthDp, displayMetrics.widthPixels);
        }

        if (!isMessageViewMeasured) {
            messageView.measure(0, 0);
            initialMessageViewWidth = messageView.getMeasuredWidth();
            isMessageViewMeasured = true;
        }

        int balloonMinWidth = arrowWidth + (horizontalTextMargin * 2);

        balloonWidth = Math.max(balloonMinWidth,
                Math.min(initialMessageViewWidth + (horizontalTextMargin * 2), balloonMaxWidth));

        messageView.setWidth(balloonWidth - (horizontalTextMargin * 2));
        messageView.measure(0, 0);
        balloonHeight = messageView.getMeasuredHeight() + (verticalTextMargin * 2) + arrowHeight;

        if (type == Type.BALLOON_ACTION) {
            actionView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            balloonWidth = Math.max(balloonWidth, actionView.getMeasuredWidth()
                    + (resources.getDimensionPixelSize(R.dimen.sem_tip_popup_button_padding_horizontal) * 2));
            balloonHeight += (actionView.getMeasuredHeight()
                    - resources.getDimensionPixelSize(R.dimen.sem_tip_popup_button_padding_vertical));
        }
    }

    private static int maxWidthFor(int widthForBreakpoint, int widthPixels) {
        if (widthForBreakpoint <= 480) {
            return (int) (widthPixels * 0.83f);
        } else if (widthForBreakpoint <= 960) {
            return (int) (widthPixels * 0.6f);
        } else if (widthForBreakpoint <= 1280) {
            return (int) (widthPixels * 0.45f);
        } else {
            return (int) (widthPixels * 0.25f);
        }
    }

    private void calculatePopupPosition() {
        getDisplayFrame(displayFrame);

        if (balloonX < 0) {
            if (arrowDirection == Direction.BOTTOM_RIGHT || arrowDirection == Direction.TOP_RIGHT) {
                balloonX = (arrowPositionX + arrowWidth) - (balloonWidth / 2);
            } else {
                balloonX = (arrowPositionX - arrowWidth) - (balloonWidth / 2);
            }
        }

        if (arrowDirection == Direction.BOTTOM_RIGHT || arrowDirection == Direction.TOP_RIGHT) {
            int minX = displayFrame.left + sideMargin + horizontalTextMargin;
            int maxX = Math.max(minX, (displayFrame.right - sideMargin) - horizontalTextMargin - arrowWidth);
            arrowPositionX = coerceIn(arrowPositionX, minX, maxX);
        } else {
            int minX = displayFrame.left + sideMargin + horizontalTextMargin + arrowWidth;
            int maxX = Math.max(minX, (displayFrame.right - sideMargin) - horizontalTextMargin);
            arrowPositionX = coerceIn(arrowPositionX, minX, maxX);
        }

        if (isDeskTopMode()) {
            int[] windowLocation = new int[2];
            View rootView = parentView.getRootView();
            rootView.getLocationOnScreen(windowLocation);
            int windowWidthInDexMode = windowLocation[0] < 0
                    ? rootView.getMeasuredWidth() + windowLocation[0]
                    : rootView.getMeasuredWidth();
            int minX = displayFrame.left + sideMargin;
            int maxX = Math.max(minX,
                    (windowWidthInDexMode - sideMargin) - balloonWidth - Math.min(windowLocation[0], 0));
            balloonX = coerceIn(balloonX, minX, maxX);
        } else {
            int minX = displayFrame.left + sideMargin;
            int maxX = Math.max(minX, (displayFrame.right - sideMargin) - balloonWidth);
            balloonX = coerceIn(balloonX, minX, maxX);
        }

        switch (arrowDirection) {
            case TOP_LEFT:
                bubbleX = arrowPositionX - bubbleWidth;
                bubbleY = arrowPositionY - bubbleHeight;
                balloonY = arrowPositionY - balloonHeight;
                break;

            case TOP_RIGHT:
                bubbleX = arrowPositionX;
                bubbleY = arrowPositionY - bubbleHeight;
                balloonY = arrowPositionY - balloonHeight;
                break;

            case BOTTOM_LEFT:
                bubbleX = arrowPositionX - bubbleWidth;
                bubbleY = arrowPositionY;
                balloonY = arrowPositionY;
                break;

            case BOTTOM_RIGHT:
                bubbleX = arrowPositionX;
                bubbleY = arrowPositionY;
                balloonY = arrowPositionY;
                break;

            case DEFAULT:
            default:
                throw new IllegalStateException();
        }

        debugLog("QuestionPopup : " + bubbleX + ", " + bubbleY + ", " + bubbleWidth + ", " + bubbleHeight);
        debugLog("BalloonPopup : " + balloonX + ", " + balloonY + ", " + balloonWidth + ", " + balloonHeight);
    }

    private static int coerceIn(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private void dismissBubble(boolean withAnimation) {
        bubblePopup.setUseDismissAnimation(withAnimation);
        bubblePopup.dismiss();
        if (onDismissListener != null) {
            onDismissListener.onDismiss();
        }
    }

    private void scheduleTimeout() {
        if (handler != null) {
            handler.removeMessages(0);
            handler.sendMessageDelayed(Message.obtain(handler, MSG_TIMEOUT), TIMEOUT_DURATION_MS);
        }
    }

    private void animateViewIn() {
        float pivotX;
        float pivotY;
        switch (arrowDirection) {
            case TOP_LEFT:
                pivotX = 1.0f;
                pivotY = 1.0f;
                break;
            case TOP_RIGHT:
                pivotX = 0.0f;
                pivotY = 1.0f;
                break;
            case BOTTOM_LEFT:
                pivotX = 1.0f;
                pivotY = 0.0f;
                break;
            case BOTTOM_RIGHT:
                pivotX = 0.0f;
                pivotY = 0.0f;
                break;
            case DEFAULT:
            default:
                throw new IllegalStateException();
        }

        ScaleAnimation animScale = new ScaleAnimation(0.0f, 1.0f, 0.0f, 1.0f,
                Animation.RELATIVE_TO_SELF, pivotX, Animation.RELATIVE_TO_SELF, pivotY);
        animScale.setInterpolator(interpolatorElastic50);
        animScale.setDuration(500L);
        listen(animScale, null, () -> {
            scheduleTimeout();
            animateBounce();
        });
        bubbleView.startAnimation(animScale);
    }

    private void animateBounce() {
        float pivotX;
        float pivotY;
        switch (arrowDirection) {
            case TOP_LEFT:
                pivotX = bubblePopup.getWidth();
                pivotY = bubblePopup.getHeight();
                break;
            case TOP_RIGHT:
                pivotX = 0.0f;
                pivotY = bubblePopup.getHeight();
                break;
            case BOTTOM_LEFT:
                pivotX = bubblePopup.getWidth();
                pivotY = 0.0f;
                break;
            case BOTTOM_RIGHT:
                pivotX = 0.0f;
                pivotY = 0.0f;
                break;
            case DEFAULT:
            default:
                throw new IllegalStateException();
        }

        AnimationSet animationSet = new AnimationSet(false);

        ScaleAnimation scaleAnimation1 = new ScaleAnimation(1.0f, 1.2f, 1.0f, 1.2f,
                Animation.ABSOLUTE, pivotX, Animation.ABSOLUTE, pivotY);
        scaleAnimation1.setDuration(ANIMATION_DURATION_BOUNCE_SCALE1);
        scaleAnimation1.setInterpolator(interpolatorSineInOut70);

        ScaleAnimation scaleAnimation2 = new ScaleAnimation(1.0f, 0.833f, 1.0f, 0.833f,
                Animation.ABSOLUTE, pivotX, Animation.ABSOLUTE, pivotY);
        scaleAnimation2.setStartOffset(ANIMATION_DURATION_BOUNCE_SCALE1);
        scaleAnimation2.setDuration(ANIMATION_DURATION_BOUNCE_SCALE2);
        scaleAnimation2.setInterpolator(interpolatorSineInOut33);
        final int[] count = {0};
        listen(scaleAnimation2, () -> count[0]++, () -> {
            debugLog("repeat count " + count[0]);
            bubbleView.startAnimation(animationSet);
        });

        animationSet.addAnimation(scaleAnimation1);
        animationSet.addAnimation(scaleAnimation2);
        animationSet.setStartOffset(ANIMATION_OFFSET_BOUNCE_SCALE);
        bubbleView.startAnimation(animationSet);
    }

    private void animateScaleUp() {
        float deltaHintY;
        float pivotHintX;
        float pivotHintY;
        switch (arrowDirection) {
            case TOP_LEFT:
                pivotHintX = balloonBubble.getWidth();
                pivotHintY = balloonBubble.getHeight();
                deltaHintY = 0.0f - (arrowHeight / 2.0f);
                break;
            case TOP_RIGHT:
                pivotHintX = 0.0f;
                pivotHintY = balloonBubble.getHeight();
                deltaHintY = 0.0f - (arrowHeight / 2.0f);
                break;
            case BOTTOM_LEFT:
                pivotHintX = balloonBubble.getWidth();
                pivotHintY = 0.0f;
                deltaHintY = arrowHeight / 2.0f;
                break;
            case BOTTOM_RIGHT:
            case DEFAULT:
            default:
                pivotHintX = 0.0f;
                pivotHintY = 0.0f;
                deltaHintY = arrowHeight / 2.0f;
                break;
        }

        AnimationSet animationBubble = new AnimationSet(false);

        TranslateAnimation translate = new TranslateAnimation(
                Animation.ABSOLUTE, 0.0f, Animation.ABSOLUTE, 0.0f,
                Animation.ABSOLUTE, 0.0f, Animation.ABSOLUTE, deltaHintY);
        translate.setDuration(ANIMATION_DURATION_EXPAND_SCALE);
        translate.setInterpolator(interpolatorElasticCustom);
        animationBubble.addAnimation(translate);

        ScaleAnimation scale = new ScaleAnimation(1.0f, 1.5f, 1.0f, 1.5f,
                Animation.ABSOLUTE, pivotHintX, Animation.ABSOLUTE, pivotHintY);
        scale.setDuration(ANIMATION_DURATION_EXPAND_SCALE);
        scale.setInterpolator(interpolatorElastic50);
        animationBubble.addAnimation(scale);

        AlphaAnimation alpha = new AlphaAnimation(1.0f, 0.0f);
        alpha.setDuration(ANIMATION_DURATION_EXPAND_TEXT);
        alpha.setInterpolator(interpolatorSineInOut70);
        animationBubble.addAnimation(alpha);

        listen(animationBubble,
                () -> balloonPanel.setVisibility(View.VISIBLE),
                () -> balloonBubble.setVisibility(View.GONE));

        balloonBubble.startAnimation(animationBubble);
        animateBalloonScaleUp();
    }

    private void animateBalloonScaleUp() {
        float pivotPanelX;
        float pivotPanelY;
        int questionHeight = resources.getDimensionPixelSize(R.dimen.sem_tip_popup_bubble_height);
        float panelScale = (float) (questionHeight / balloonHeight);

        switch (arrowDirection) {
            case TOP_LEFT:
                pivotPanelX = arrowPositionX - balloonX;
                pivotPanelY = balloonHeight;
                break;
            case TOP_RIGHT:
                pivotPanelX = bubbleX - balloonX;
                pivotPanelY = balloonHeight;
                break;
            case BOTTOM_LEFT:
                pivotPanelX = arrowPositionX - balloonX;
                pivotPanelY = 0.0f;
                break;
            case BOTTOM_RIGHT:
                pivotPanelX = bubbleX - balloonX;
                pivotPanelY = 0.0f;
                break;
            case DEFAULT:
            default:
                pivotPanelX = 0.0f;
                pivotPanelY = 0.0f;
                break;
        }

        AnimationSet animationPanel = new AnimationSet(false);

        ScaleAnimation panelScaleAnimation = new ScaleAnimation(0.32f, 1.0f, panelScale, 1.0f,
                Animation.ABSOLUTE, pivotPanelX, Animation.ABSOLUTE, pivotPanelY);
        panelScaleAnimation.setInterpolator(interpolatorElasticCustom);
        panelScaleAnimation.setDuration(ANIMATION_DURATION_SHOW_SCALE);
        animationPanel.addAnimation(panelScaleAnimation);

        AlphaAnimation panelAlpha = new AlphaAnimation(0.0f, 1.0f);
        panelAlpha.setInterpolator(interpolatorSineInOut70);
        panelAlpha.setDuration(ANIMATION_DURATION_EXPAND_ALPHA);
        animationPanel.addAnimation(panelAlpha);

        balloonPanel.startAnimation(animationPanel);

        AlphaAnimation animationText = new AlphaAnimation(0.0f, 1.0f);
        animationText.setInterpolator(interpolatorSineInOut33);
        animationText.setStartOffset(ANIMATION_OFFSET_EXPAND_TEXT);
        animationText.setDuration(ANIMATION_DURATION_EXPAND_TEXT);
        listen(animationText,
                () -> messageView.setVisibility(View.VISIBLE),
                () -> {
                    bubblePopup.setUseDismissAnimation(false);
                    bubblePopup.dismiss();
                });
        messageView.startAnimation(animationText);
        actionView.startAnimation(animationText);
    }

    private boolean isNavigationbarHide() {
        return Settings.Global.getInt(context.getContentResolver(), "navigationbar_hide_bar_enabled", 0) == 1;
    }

    @SuppressLint({"InternalInsetResource", "DiscouragedApi"})
    private int getNavigationbarHeight() {
        int identifier = resources.getIdentifier("navigation_bar_height", "dimen", "android");
        return identifier > 0 ? resources.getDimensionPixelSize(identifier) : 0;
    }

    private boolean isTablet() {
        return resources.getConfiguration().smallestScreenWidthDp >= 600;
    }

    private boolean isDeskTopMode() {
        try {
            return SeslConfigurationReflector.isDexEnabled(resources.getConfiguration());
        } catch (Throwable ignored) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private void getRealMetrics(DisplayMetrics outMetrics) {
        windowManager.getDefaultDisplay().getRealMetrics(outMetrics);
    }

    @SuppressWarnings("deprecation")
    private void getDisplayFrame(Rect screenRect) {
        int navigationbarHeight = getNavigationbarHeight();
        boolean navigationbarHide = isNavigationbarHide();

        int displayRotation = windowManager.getDefaultDisplay().getRotation();

        DisplayMetrics realMetrics = new DisplayMetrics();
        getRealMetrics(realMetrics);

        debugLog("realMetrics = " + realMetrics);
        debugLog("is tablet? = " + isTablet());

        if (forceRealDisplay) {
            screenRect.left = 0;
            screenRect.top = 0;
            screenRect.right = realMetrics.widthPixels;
            screenRect.bottom = realMetrics.heightPixels;
            debugLog("Screen Rect = " + screenRect + " forceRealDisplay = " + forceRealDisplay);
            return;
        }
        screenRect.left = 0;
        screenRect.top = 0;
        screenRect.right = displayMetrics.widthPixels;
        screenRect.bottom = displayMetrics.heightPixels;

        if (dialogMode) {
            // Keep the pop-up inside the dialog window (same coordinate space as the
            // anchor position: screen coordinates or window coordinates).
            View rootView = parentView.getRootView();
            int[] rootLocation = new int[2];
            if (useScreenCoordinates) {
                rootView.getLocationOnScreen(rootLocation);
            }
            screenRect.left = rootLocation[0];
            screenRect.right = rootLocation[0] + rootView.getWidth();
            debugLog("Screen Rect (dialog mode) = " + screenRect);
            return;
        }

        Rect bounds = new Rect();
        parentView.getRootView().getWindowVisibleDisplayFrame(bounds);
        debugLog("Bounds = " + bounds);
        if (isTablet()) {
            debugLog("tablet");
            if (realMetrics.widthPixels == displayMetrics.widthPixels
                    && realMetrics.heightPixels - displayMetrics.heightPixels == navigationbarHeight
                    && navigationbarHide) {
                screenRect.bottom += navigationbarHeight;
            }
        } else {
            debugLog("phone");
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
                    if (windowInsets != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        DisplayCutout cutout = windowInsets.getDisplayCutout();
                        if (cutout != null) {
                            screenRect.left += cutout.getSafeInsetLeft();
                            screenRect.right += cutout.getSafeInsetLeft();
                            debugLog("displayCutout.getSafeInsetLeft() :  " + cutout.getSafeInsetLeft());
                        }
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
                        debugLog("Top Docked");
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
                        debugLog("Left Docked");
                        screenRect.left += navigationbarHeight;
                        screenRect.right += navigationbarHeight;
                    }
                    break;

                default:
                    break;
            }
        }
        debugLog("Screen Rect = " + screenRect);
    }

    public PopupWindow semGetBubblePopupWindow() {
        return bubblePopup;
    }

    public PopupWindow semGetBalloonPopupWindow() {
        return balloonPopup;
    }

    private boolean isRTL() {
        return resources.getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
    }

    private String getLocale() {
        return resources.getConfiguration().getLocales().get(0).toString();
    }

    private static void debugLog(String msg) {
        if (BuildConfig.DEBUG) {
            Log.v(TAG, " #### " + msg);
        }
    }

    private static void listen(Animation animation, Runnable onStart, Runnable onEnd) {
        animation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation a) {
                if (onStart != null) onStart.run();
            }

            @Override
            public void onAnimationEnd(Animation a) {
                if (onEnd != null) onEnd.run();
            }

            @Override
            public void onAnimationRepeat(Animation a) {
            }
        });
    }

    static class TipWindow extends PopupWindow {
        boolean isDismissing = false;
        private boolean isUsingDismissAnimation = true;
        float pivotX = 0.0f;
        float pivotY = 0.0f;

        TipWindow(View contentView, int width, int height, boolean focusable) {
            super(contentView, width, height, focusable);
        }

        void setUseDismissAnimation(boolean useAnimation) {
            isUsingDismissAnimation = useAnimation;
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

        void dismissFinal() {
            super.dismiss();
        }

        void animateViewOut() {
        }
    }

    private static class TipWindowBubble extends TipWindow {
        TipWindowBubble(View contentView, int width, int height, boolean focusable) {
            super(contentView, width, height, focusable);
        }

        @Override
        void animateViewOut() {
            AnimationSet animationSet = new AnimationSet(true);

            ScaleAnimation scale = new ScaleAnimation(1.0f, 0.81f, 1.0f, 0.81f,
                    Animation.ABSOLUTE, pivotX, Animation.ABSOLUTE, pivotY);
            scale.setInterpolator(interpolatorElasticCustom);
            scale.setDuration(ANIMATION_DURATION_DISMISS_SCALE);
            animationSet.addAnimation(scale);

            AlphaAnimation alpha = new AlphaAnimation(1.0f, 0.0f);
            alpha.setInterpolator(interpolatorSineInOut33);
            alpha.setDuration(ANIMATION_DURATION_DISMISS_ALPHA);
            animationSet.addAnimation(alpha);

            listen(animationSet, () -> isDismissing = true, this::dismissFinal);
            getContentView().startAnimation(animationSet);
        }
    }

    private static class TipWindowBalloon extends TipWindow {
        TipWindowBalloon(View contentView, int width, int height, boolean focusable) {
            super(contentView, width, height, focusable);
        }

        @Override
        void animateViewOut() {
            View messageView = getContentView().findViewById(R.id.sem_tip_popup_message);

            AlphaAnimation animAlpha = new AlphaAnimation(1.0f, 0.0f);
            animAlpha.setDuration(ANIMATION_DURATION_EXPAND_SCALE);

            AnimationSet animationSet = new AnimationSet(true);

            ScaleAnimation scale = new ScaleAnimation(1.0f, 0.32f, 1.0f, 0.32f,
                    Animation.ABSOLUTE, pivotX, Animation.ABSOLUTE, pivotY);
            scale.setDuration(ANIMATION_DURATION_DISMISS_SCALE);
            scale.setInterpolator(interpolatorElasticCustom);
            animationSet.addAnimation(scale);
            animationSet.addAnimation(animAlpha);

            listen(animationSet, () -> isDismissing = true, this::dismissFinal);
            getContentView().startAnimation(animationSet);
            messageView.startAnimation(animAlpha);
        }
    }
}
