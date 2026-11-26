package com.oneui.fontviewer.ui.core.widget.preference;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceViewHolder;

import dev.oneuiproject.oneui.design.R;
import dev.oneuiproject.oneui.preference.TipsCardPreference;

/**
 * بطاقة التحديث في أعلى الإعدادات (عنوان + رسالة + زرّان: "ليس الآن" و "تحديث").
 *
 * مبنية على TipsCardPreference من مكتبة OneUI، لكن الأزرار تُرسم هنا عند كل ربط (bind)
 * وليس مرة واحدة كما تفعل addButton في المكتبة، فلا تضيع الأزرار إذا أعاد RecyclerView
 * إنشاء عنصر البطاقة.
 */
public class UpdateCardPreference extends TipsCardPreference {

    private CharSequence mNegativeText;
    private View.OnClickListener mNegativeListener;
    private CharSequence mPositiveText;
    private View.OnClickListener mPositiveListener;

    public UpdateCardPreference(@NonNull Context context) {
        super(context);
    }

    public UpdateCardPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * @param negativeText     نص الزر الأول (ليس الآن)
     * @param positiveText     نص الزر الثاني (تحديث)
     */
    public void setButtons(@Nullable CharSequence negativeText,
                           @Nullable View.OnClickListener negativeListener,
                           @Nullable CharSequence positiveText,
                           @Nullable View.OnClickListener positiveListener) {
        mNegativeText = negativeText;
        mNegativeListener = negativeListener;
        mPositiveText = positiveText;
        mPositiveListener = positiveListener;
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        // TipsCardPreference يفرض لونه على العنوان والملخص داخل onBindViewHolder فيتجاهل ألوان الـ layout،
        // لذلك نقرأ الألوان من الـ layout قبل super ونعيدها بعده ليبقى ملف الـ layout هو المرجع.
        TextView title = (TextView) holder.findViewById(android.R.id.title);
        TextView summary = (TextView) holder.findViewById(android.R.id.summary);
        ColorStateList titleColors = title != null ? title.getTextColors() : null;
        ColorStateList summaryColors = summary != null ? summary.getTextColors() : null;

        super.onBindViewHolder(holder);

        if (title != null && titleColors != null) {
            title.setTextColor(titleColors);
        }
        if (summary != null && summaryColors != null) {
            summary.setTextColor(summaryColors);
        }
        bindButtons(holder.itemView);
    }

    private void bindButtons(@NonNull View itemView) {
        LinearLayout bottomBar = itemView.findViewById(R.id.tips_bottom_bar);
        if (bottomBar == null) {
            return;
        }

        // الفراغ السفلي الافتراضي يُستبدل بشريط الأزرار
        View emptyBottom = itemView.findViewById(R.id.tips_empty_bottom);
        if (emptyBottom != null && emptyBottom.getParent() instanceof ViewGroup) {
            ((ViewGroup) emptyBottom.getParent()).removeView(emptyBottom);
        }

        bottomBar.removeAllViews();
        addButton(bottomBar, mNegativeText, mNegativeListener);
        addButton(bottomBar, mPositiveText, mPositiveListener);
        bottomBar.setVisibility(bottomBar.getChildCount() > 0 ? View.VISIBLE : View.GONE);
    }

    private void addButton(@NonNull LinearLayout bottomBar,
                           @Nullable CharSequence text,
                           @Nullable View.OnClickListener listener) {
        if (text == null) {
            return;
        }
        TextView button = new TextView(getContext(), null, 0,
                R.style.OneUI_TipsCardTextButtonStyle);
        button.setText(text);
        button.setOnClickListener(listener);
        bottomBar.addView(button);
    }
}
