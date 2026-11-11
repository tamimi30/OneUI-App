package com.oneui.fontviewer.ui.widget.list;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import androidx.annotation.NonNull;


import com.oneui.fontviewer.R;

public class OneUiRecyclerView extends RecyclerView {

    private static final float CORNER_RADIUS_DP = 26F;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mPath = new Path();
    private final RectF mCard = new RectF();
    private final float mRadius;

    public OneUiRecyclerView(@NonNull Context context, AttributeSet attrs) {
        super(context, attrs);

        TypedValue roundedCornerColorValue = new TypedValue();
        context.getTheme().resolveAttribute(R.attr.roundedCornerColor, roundedCornerColorValue, true);
        int roundedCornerColor = roundedCornerColorValue.resourceId != 0
                ? ContextCompat.getColor(context, roundedCornerColorValue.resourceId)
                : roundedCornerColorValue.data;

        mPaint.setColor(roundedCornerColor);
        mPaint.setStyle(Paint.Style.FILL);

        mRadius = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, CORNER_RADIUS_DP, getResources().getDisplayMetrics());
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);

        mCard.set(getPaddingLeft(), 0, getWidth() - getPaddingRight(), getHeight());

        mPath.reset();
        mPath.setFillType(Path.FillType.EVEN_ODD);
        mPath.addRect(0, 0, getWidth(), getHeight(), Path.Direction.CW);
        mPath.addRoundRect(mCard, mRadius, mRadius, Path.Direction.CW);

        canvas.drawPath(mPath, mPaint);
    }
}
