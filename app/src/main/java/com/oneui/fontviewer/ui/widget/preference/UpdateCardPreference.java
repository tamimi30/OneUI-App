package com.oneui.fontviewer.ui.widget.preference;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.oneui.fontviewer.R;

/**
 * بطاقة "يتوفر تحديث جديد" في أعلى الإعدادات، بزرّي "تحديث" و"ليس الآن".
 *
 * نبنيها بأنفسنا بدل TipsCardPreference لأن الأخيرة تضيف أزرارها مرة واحدة فقط عند أول رسم،
 * فتضيع الأزرار إذا أُعيد رسم البطاقة بعد إخفائها وإظهارها. هنا نربط الأزرار في كل مرة تُرسم فيها البطاقة.
 */
public class UpdateCardPreference extends Preference {

    private View.OnClickListener mUpdateClickListener;
    private View.OnClickListener mNotNowClickListener;

    public UpdateCardPreference(@NonNull Context context, @Nullable AttributeSet attrs,
                                int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public UpdateCardPreference(@NonNull Context context, @Nullable AttributeSet attrs,
                                int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public UpdateCardPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, androidx.preference.R.attr.preferenceStyle);
    }

    public UpdateCardPreference(@NonNull Context context) {
        this(context, null);
    }

    private void init() {
        setSelectable(false);
        setPersistent(false);
        setLayoutResource(R.layout.preference_update_card);
    }

    public void setOnUpdateClickListener(@Nullable View.OnClickListener listener) {
        mUpdateClickListener = listener;
    }

    public void setOnNotNowClickListener(@Nullable View.OnClickListener listener) {
        mNotNowClickListener = listener;
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        // نفس ما تفعله TipsCardPreference في المكتبة: بدون ضبط اللون صراحةً يظهر النص باهتاً
        // لأن البطاقة غير قابلة للتحديد (setSelectable(false))
        int textColor = getContext().getColor(R.color.oui_primary_text_color);
        ((TextView) holder.itemView.findViewById(android.R.id.title)).setTextColor(textColor);
        ((TextView) holder.itemView.findViewById(android.R.id.summary)).setTextColor(textColor);

        holder.itemView.findViewById(R.id.update_card_update).setOnClickListener(mUpdateClickListener);
        holder.itemView.findViewById(R.id.update_card_not_now).setOnClickListener(mNotNowClickListener);
    }
}
