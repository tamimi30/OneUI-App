package com.oneui.fontviewer.ui.widget.list;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.util.SeslRoundedCorner;
import androidx.appcompat.util.SeslSubheaderRoundedCorner;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.oneui.fontviewer.R;

public class ItemDecoration extends RecyclerView.ItemDecoration {

    private final Drawable divider;
    private final SeslSubheaderRoundedCorner roundedCorner;

    private final Paint bottomFillPaint = new Paint();

    private final int fontViewType;

    public ItemDecoration(@NonNull Context context, int fontViewType) {
        this.fontViewType = fontViewType;

        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(
            dev.oneuiproject.oneui.design.R.attr.isLightTheme, 
            typedValue, 
            true
        );

        int dividerResId = typedValue.data == 0 ? 
            dev.oneuiproject.oneui.design.R.drawable.sesl_list_divider_dark :
            dev.oneuiproject.oneui.design.R.drawable.sesl_list_divider_light;

        Drawable dividerDrawable = context.getDrawable(dividerResId);
        divider = dividerDrawable != null ? dividerDrawable.mutate() : null;

        TypedValue roundedCornerColorValue = new TypedValue();
        context.getTheme().resolveAttribute(R.attr.roundedCornerColor, roundedCornerColorValue, true);
        int roundedCornerColor = roundedCornerColorValue.resourceId != 0
            ? ContextCompat.getColor(context, roundedCornerColorValue.resourceId)
            : roundedCornerColorValue.data;

        roundedCorner = new SeslSubheaderRoundedCorner(context);
        roundedCorner.setRoundedCorners(SeslRoundedCorner.ROUNDED_CORNER_ALL);
        roundedCorner.setRoundedCornerColor(SeslRoundedCorner.ROUNDED_CORNER_ALL, roundedCornerColor);

        bottomFillPaint.setColor(roundedCornerColor);
        bottomFillPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    public void onDraw(@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        super.onDraw(canvas, parent, state);

        if (divider == null) {
            return;
        }

        final int left = parent.getPaddingLeft();
        final int right = parent.getWidth() - parent.getPaddingRight();

        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);

            if (parent.getChildViewHolder(child).getItemViewType() != fontViewType) {
                continue;
            }

            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) child.getLayoutParams();

            int top = child.getBottom() + params.bottomMargin + Math.round(child.getTranslationY());
            int bottom = top + divider.getIntrinsicHeight();

            divider.setBounds(left, top, right, bottom);
            divider.setAlpha(Math.round(child.getAlpha() * 255));
            divider.draw(canvas);
        }
    }

    @Override
    public void seslOnDispatchDraw(@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        super.seslOnDispatchDraw(canvas, parent, state);

        final RecyclerView.Adapter<?> adapter = parent.getAdapter();
        final int lastPosition = adapter != null ? adapter.getItemCount() - 1 : -1;

        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            RecyclerView.ViewHolder holder = parent.getChildViewHolder(child);

            if (holder.getItemViewType() != fontViewType) {
                if (holder.getBindingAdapterPosition() == lastPosition) {
                    final float bottom = child.getBottom() + child.getTranslationY();
                    if (bottom < parent.getHeight()) {
                        canvas.drawRect(0f, bottom, parent.getWidth(), parent.getHeight(), bottomFillPaint);
                    }
                }

                roundedCorner.drawRoundedCorner(child, canvas);
            }
        }
    }
}
