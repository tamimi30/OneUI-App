package com.example.oneuiapp.widget; // تأكد من تغيير البكج حسب مسار مشروعك

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

public class UnclippedTextView extends AppCompatTextView {

    private int mHorizontalPaddingLeft = 0;
    private int mHorizontalPaddingRight = 0;

    public UnclippedTextView(@NonNull Context context) {
        super(context);
        init();
    }

    public UnclippedTextView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public UnclippedTextView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // 1. التقاط البادنج الأصلي الذي حددته في ملف الـ XML
        mHorizontalPaddingLeft = getPaddingLeft();
        mHorizontalPaddingRight = getPaddingRight();
        
        // 2. تصفير البادنج الأفقي داخلياً لخداع TextView ومنعه من تفعيل مقص clipRect
        super.setPadding(0, getPaddingTop(), 0, getPaddingBottom());
    }

    @Override
    public void setPadding(int left, int top, int right, int bottom) {
        mHorizontalPaddingLeft = left;
        mHorizontalPaddingRight = right;
        // الاستمرار بتمرير 0 لليمين واليسار للـ TextView الأصلي
        super.setPadding(0, top, 0, bottom);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);

        // 3. خصم مساحة البادنج من العرض المتاح للتأكد من أن التفاف النص (Wrapping) 
        // لكل سطر يحدث بدقة وفي المكان الصحيح
        int availableWidth = widthSize;
        if (widthMode != MeasureSpec.UNSPECIFIED) {
            availableWidth = Math.max(0, widthSize - mHorizontalPaddingLeft - mHorizontalPaddingRight);
        }

        int adjustedWidthSpec = MeasureSpec.makeMeasureSpec(availableWidth, widthMode);

        // جعل TextView يحسب أبعاد النص بناءً على المساحة المتبقية فقط
        super.onMeasure(adjustedWidthSpec, heightMeasureSpec);

        // 4. إعادة مساحة البادنج للعرض النهائي حتى تعترف الحاوية الأب بهذا العرض الكلي
        int finalWidth = getMeasuredWidth() + mHorizontalPaddingLeft + mHorizontalPaddingRight;
        setMeasuredDimension(finalWidth, getMeasuredHeight());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.save();
        // 5. إزاحة لوحة الرسم لليمين بمقدار البادنج الأيسر
        // الآن أي حرف مائل في بداية السطر سيمتد نحو الصفر (الذي أصبح مساحة أمان) ولن يُقص
        canvas.translate(mHorizontalPaddingLeft, 0);
        
        super.onDraw(canvas);
        canvas.restore();
    }
}
