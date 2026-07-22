package com.oneui.fontviewer.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Xml;
import android.widget.ImageView;
import android.util.TypedValue;
import android.graphics.drawable.Drawable;

import androidx.appcompat.graphics.drawable.AnimatedStateListDrawableCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

public class SelectableAnimatedDrawable extends AnimatedStateListDrawableCompat {

    private static final String LOG_TAG = "SelectableAnimDrawable";
    private float radius = -1f;
    private int selectedColor = -1;
    private Paint backgroundPaint;
    private final RectF shapeBounds = new RectF();
    private ValueAnimator backgroundAnimator;

    public SelectableAnimatedDrawable() {
        backgroundPaint = new Paint();
        backgroundPaint.setColor(Color.TRANSPARENT);
        backgroundPaint.setStyle(Paint.Style.FILL);
        backgroundPaint.setAntiAlias(true);
    }

    @Override
    public void inflate(Context context, Resources resources, XmlPullParser parser, AttributeSet attrs, Resources.Theme theme) throws XmlPullParserException, IOException {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true);
        selectedColor = ColorUtils.setAlphaComponent(ContextCompat.getColor(context, typedValue.resourceId), (int) (0.8f * 255));
        super.inflate(context, resources, parser, attrs, theme);
    }

    @Override
    public void draw(Canvas canvas) {
        Drawable.Callback callback = getCallback();
        if (callback instanceof ImageView) {
            ImageView imageView = (ImageView) callback;
            if (imageView.getDrawable() != null) {
                RectF drawableBounds = new RectF(imageView.getDrawable().getBounds());
                imageView.getImageMatrix().mapRect(shapeBounds, drawableBounds);
                float radiusPx = (radius == -1f) ? shapeBounds.height() / 2f : radius;
                canvas.drawRoundRect(shapeBounds, radiusPx, radiusPx, backgroundPaint);
            }
        }
        super.draw(canvas);
    }

    @Override
    public void setAlpha(int alpha) {
        super.setAlpha(alpha);
        if (backgroundPaint != null) backgroundPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        super.setColorFilter(colorFilter);
        if (backgroundPaint != null) backgroundPaint.setColorFilter(colorFilter);
    }

    @Override
    protected boolean onStateChange(int[] stateSet) {
        boolean changed = super.onStateChange(stateSet);
        if (!changed) return false;

        if (backgroundAnimator != null) backgroundAnimator.cancel();

        if (backgroundPaint != null) {
            boolean isSelected = false;
            for (int state : stateSet) {
                if (state == android.R.attr.state_selected) {
                    isSelected = true;
                    break;
                }
            }

            if (isSelected) {
                if (backgroundPaint.getColor() != selectedColor) {
                    backgroundPaint.setColor(selectedColor);
                    invalidateSelf();
                }
            } else {
                if (backgroundPaint.getColor() != Color.TRANSPARENT) {
                    backgroundAnimator = ValueAnimator.ofArgb(selectedColor, Color.TRANSPARENT);
                    backgroundAnimator.setDuration(250);
                    backgroundAnimator.setStartDelay(100);
                    backgroundAnimator.addUpdateListener(animator -> {
                        backgroundPaint.setColor((int) animator.getAnimatedValue());
                        invalidateSelf();
                    });
                    backgroundAnimator.start();
                }
            }
        }
        return true;
    }

    @Override
    public void jumpToCurrentState() {
        super.jumpToCurrentState();
        if (backgroundAnimator != null) backgroundAnimator.cancel();
        if (backgroundPaint != null) {
            boolean isSelected = false;
            for (int state : getState()) {
                if (state == android.R.attr.state_selected) {
                    isSelected = true;
                    break;
                }
            }
            backgroundPaint.setColor(isSelected ? selectedColor : Color.TRANSPARENT);
            invalidateSelf();
        }
    }

    public void setCornerRadius(float radius) {
        this.radius = radius;
        invalidateSelf();
    }

    public static SelectableAnimatedDrawable create(Context context, int resId, Resources.Theme theme) {
        try {
            Resources res = context.getResources();
            XmlPullParser parser = res.getXml(resId);
            AttributeSet attrs = Xml.asAttributeSet(parser);

            int type;
            while ((type = parser.next()) != XmlPullParser.START_TAG && type != XmlPullParser.END_DOCUMENT) {
                // Empty loop
            }

            if (type != XmlPullParser.START_TAG) throw new XmlPullParserException("No start tag found");
            if (!"animated-selector".equals(parser.getName())) throw new XmlPullParserException("invalid animated-selector tag");

            SelectableAnimatedDrawable drawable = new SelectableAnimatedDrawable();
            drawable.inflate(context, res, parser, attrs, theme);
            return drawable;
        } catch (Exception e) {
            Log.e(LOG_TAG, "parser error", e);
        }
        return null;
    }
}
