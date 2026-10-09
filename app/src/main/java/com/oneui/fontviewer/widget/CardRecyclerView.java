package com.oneui.fontviewer.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.appcompat.util.SeslRoundedCorner;
import androidx.recyclerview.widget.RecyclerView;

/**
 * RecyclerView يرسم هامشيه الجانبيين (منطقة الـ padding الأفقي) وزوايا البطاقة
 * بلون زوايا Sesl نفسه، في dispatchDraw، أي قبل رسم شريط التمرير.
 * لذلك يظهر شريط التمرير فوق الزوايا ولا يُقص عند بداية القائمة أو نهايتها.
 *
 * الاستخدام: android:paddingHorizontal="10dp" و android:scrollbarStyle="outsideOverlay"
 */
public class CardRecyclerView extends RecyclerView {

    // يطابق الثابت RADIUS في SeslRoundedCorner
    private static final float CORNER_RADIUS_DP = 26f;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mPath = new Path();
    private final RectF mCard = new RectF();
    private final float mRadius;

    public CardRecyclerView(@NonNull Context context, AttributeSet attrs) {
        super(context, attrs);

        SeslRoundedCorner corner = new SeslRoundedCorner(context);
        mPaint.setColor(corner.getRoundedCornerColor(SeslRoundedCorner.ROUNDED_CORNER_TOP_LEFT));
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
