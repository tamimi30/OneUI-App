public class CardRecyclerView extends RecyclerView {
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mPath = new Path();
    private final RectF mCard = new RectF();
    private final float mRadius;

    public CardRecyclerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        SeslRoundedCorner corner = new SeslRoundedCorner(context);
        mPaint.setColor(corner.getRoundedCornerColor(
                SeslRoundedCorner.ROUNDED_CORNER_TOP_LEFT));
        // 26dp = RADIUS في SeslRoundedCorner
        mRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 26f,
                getResources().getDisplayMetrics());
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        mCard.set(getPaddingLeft(), 0, getWidth() - getPaddingRight(), getHeight());
        mPath.reset();
        mPath.setFillType(Path.FillType.EVEN_ODD);
        mPath.addRect(0, 0, getWidth(), getHeight(), Path.Direction.CW);
        mPath.addRoundRect(mCard, mRadius, mRadius, Path.Direction.CW);
        canvas.drawPath(mPath, mPaint);
    }
}
