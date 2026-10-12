package com.oneui.fontviewer.utils;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.util.SeslRoundedCorner;
import androidx.appcompat.util.SeslSubheaderRoundedCorner;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.oneui.fontviewer.R;

/**
 * FontItemDecoration - فئة لرسم الفواصل بين عناصر القائمة
 * تستخدم في قوائم الخطوط لإضافة خطوط فاصلة بين العناصر
 *
 * كما ترسم زوايا القائمة المدورة: أي عنصر نوعه ليس contentViewType (شريط الفرز / Bottom space)
 * يُعتبر فاصلاً، فتُرسم الزوايا العلوية أسفله (أول عنصر خط).
 *
 * الـ Bottom space (آخر عنصر في الـ Adapter) أصبح شفافاً وغير مرئي، ولا يُرسم أي شيء عنده.
 * بدلاً من ذلك يرسم هذا الـ Decoration المنطقة الرمادية أسفل آخر عنصر خط والزوايا السفلية
 * لآخر عنصر خط مباشرة، بالاعتماد على موضع آخر عنصر خط الفعلي (مع الـ translation أثناء الأنيميشن)،
 * وبذلك لا يظهر أي جسم رمادي يتحرك بشكل مستقل أثناء الأنيميشن.
 */
public class FontItemDecoration extends RecyclerView.ItemDecoration {
    
    private final Drawable divider;
    private final SeslSubheaderRoundedCorner roundedCorner;
    private final int contentViewType;

    // لون المنطقة الرمادية أسفل آخر عنصر (نفس لون شريط الفرز: oui_round_and_bgcolor)
    private final Paint bottomFillPaint = new Paint();

    // View وهمي غير مضاف لأي Layout، يُستخدم فقط كحامل لإحداثيات موضع الزوايا السفلية
    // حتى نرسمها أسفل آخر عنصر خط بنفس الـ API الذي يرسم زوايا الفواصل
    private final View cornerAnchor;
    
    /**
     * @param contentViewType نوع العنصر الذي يمثل الخط (VIEW_TYPE_FONT / VIEW_TYPE_ITEM)،
     *                        وكل ما سواه يُعتبر عنصراً فاصلاً.
     */
    public FontItemDecoration(@NonNull Context context, int contentViewType) {
        this.contentViewType = contentViewType;

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
        // mutate حتى لا يؤثر تغيير الشفافية أثناء الأنيميشن على بقية الأماكن التي تستخدم نفس الـ drawable
        divider = dividerDrawable != null ? dividerDrawable.mutate() : null;

        roundedCorner = new SeslSubheaderRoundedCorner(context);
        roundedCorner.setRoundedCorners(SeslRoundedCorner.ROUNDED_CORNER_ALL);

        bottomFillPaint.setColor(ContextCompat.getColor(context, R.color.oui_round_and_bgcolor));
        cornerAnchor = new View(context);
    }
    
    @Override
    public void onDraw(@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        super.onDraw(canvas, parent, state);

        RecyclerView.Adapter<?> adapter = parent.getAdapter();

        // المنطقة أسفل آخر عنصر خط تُلوَّن بالرمادي (تُرسم تحت العناصر)،
        // وتتبع موضع آخر عنصر الفعلي أثناء الأنيميشن
        View lastContent = findLastContentChild(parent, adapter);
        float fillTop = 0f;
        boolean hasLastContent = lastContent != null;
        if (hasLastContent) {
            fillTop = lastContent.getBottom() + lastContent.getTranslationY();
            if (fillTop < parent.getHeight()) {
                canvas.drawRect(0, fillTop, parent.getWidth(), parent.getHeight(), bottomFillPaint);
            }
        }
        
        if (divider == null) {
            return;
        }
        
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);

            if (isSeparator(parent, child)) {
                continue;
            }

            int position = parent.getChildAdapterPosition(child);
            if (position != RecyclerView.NO_POSITION) {
                // لا نرسم خطاً فاصلاً أسفل آخر عنصر خط
                if (isLastContentPosition(adapter, position)) {
                    continue;
                }
            } else if (hasLastContent && child.getTop() + child.getTranslationY() >= fillTop - 1) {
                // عنصر يُحذف الآن (أنيميشن الحذف) وموضعه أسفل آخر عنصر باقٍ: لا نرسم له فاصلاً
                continue;
            }

            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) child.getLayoutParams();
            
            int top = child.getBottom() + params.bottomMargin + Math.round(child.getTranslationY());
            int bottom = top + divider.getIntrinsicHeight();
            
            divider.setAlpha(Math.round(child.getAlpha() * 255));
            divider.setBounds(0, top, parent.getWidth(), bottom);
            divider.draw(canvas);
        }
    }

    @Override
    public void seslOnDispatchDraw(@NonNull Canvas canvas, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        RecyclerView.Adapter<?> adapter = parent.getAdapter();
        View lastContent = null;

        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);

            if (isSeparator(parent, child)) {
                // الـ Bottom space لا يرسم شيئاً، زواياه تُرسم أسفل آخر عنصر خط (انظر أدناه)
                if (isTrailingSeparator(parent, adapter, child)) {
                    continue;
                }
                roundedCorner.drawRoundedCorner(child, canvas);
            } else if (lastContent == null) {
                int position = parent.getChildAdapterPosition(child);
                if (position != RecyclerView.NO_POSITION && isLastContentPosition(adapter, position)) {
                    lastContent = child;
                }
            }
        }

        // الزوايا السفلية لآخر عنصر خط: تُرسم عند أسفله الفعلي، فتتحرك معه أثناء الأنيميشن
        if (lastContent != null) {
            int y = lastContent.getBottom() + Math.round(lastContent.getTranslationY());
            cornerAnchor.layout(0, y, parent.getWidth(), y + 1);
            roundedCorner.drawRoundedCorner(cornerAnchor, canvas);
        }
    }

    private boolean isSeparator(@NonNull RecyclerView parent, @NonNull View child) {
        return parent.getChildViewHolder(child).getItemViewType() != contentViewType;
    }

    // آخر عنصر خط: عنصر محتوى يليه عنصر فاصل (الـ Bottom space)
    private boolean isLastContentPosition(@Nullable RecyclerView.Adapter<?> adapter, int position) {
        if (adapter == null) return false;
        int count = adapter.getItemCount();
        return position >= 0
                && position + 1 < count
                && adapter.getItemViewType(position) == contentViewType
                && adapter.getItemViewType(position + 1) != contentViewType;
    }

    // الـ Bottom space: العنصر الفاصل الأخير في الـ Adapter (وليس شريط الفرز في الأعلى)
    private boolean isTrailingSeparator(@NonNull RecyclerView parent,
                                        @Nullable RecyclerView.Adapter<?> adapter,
                                        @NonNull View child) {
        if (adapter == null) return false;
        int position = parent.getChildAdapterPosition(child);
        return position > 0 && position == adapter.getItemCount() - 1;
    }

    @Nullable
    private View findLastContentChild(@NonNull RecyclerView parent,
                                      @Nullable RecyclerView.Adapter<?> adapter) {
        if (adapter == null) return null;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int position = parent.getChildAdapterPosition(child);
            if (position != RecyclerView.NO_POSITION && isLastContentPosition(adapter, position)) {
                return child;
            }
        }
        return null;
    }
        }
