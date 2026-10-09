package com.oneui.fontviewer.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.oneui.fontviewer.R;

/**
 * RecyclerView يرسم هامشيه الجانبيين (منطقة الـ padding الأفقي) وزوايا البطاقة
 * بلون oui_round_and_bgcolor، في dispatchDraw، أي قبل رسم شريط التمرير.
 * لذلك يظهر شريط التمرير فوق الزوايا ولا يُقص عند بداية القائمة أو نهايتها.
 *
 * الاستخدام: android:paddingHorizontal="10dp" و android:scrollbarStyle="outsideOverlay"
 */
public class OneUiRecyclerView extends RecyclerView {

    // يطابق الثابت RADIUS في SeslRoundedCorner، وهو نصف قطر زوايا عناصر القائمة
    private static final float CORNER_RADIUS_DP = 26f;

    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mPath = new Path();
    private final RectF mCard = new RectF();
    private final float mRadius;

    public OneUiRecyclerView(@NonNull Context context, AttributeSet attrs) {
        super(context, attrs);

        mPaint.setColor(ContextCompat.getColor(context, R.color.oui_round_and_bgcolor));
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
