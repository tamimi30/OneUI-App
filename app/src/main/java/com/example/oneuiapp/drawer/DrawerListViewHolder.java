package com.example.oneuiapp.drawer;

import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.recyclerview.widget.RecyclerView;

import com.example.oneuiapp.R;

/**
 * DrawerListViewHolder - حامل عرض عنصر الدرج
 *
 * التعديلات المطبقة:
 * 1. إزالة الخطوط الثابتة (Cached Typefaces)
 * 2. تطبيق الخط المخصص ديناميكياً عند setSelected
 * 3. دعم كامل لتغيير الخط عند إعادة إنشاء الـ Adapter
 * ★ 4. إضافة دعم الخط الفاصل عبر حقل mIsSeparator ★
 *       - الـ ViewHolder المُنشأ من drawer_list_separator لا يحتوي على أيقونة
 *         أو عنوان، لذا يُخزَّن نوعه في mIsSeparator لتجنب أي استدعاء غير ضروري
 *         على الدوال setIcon/setTitle/setSelected في DrawerListAdapter.
 */
public class DrawerListViewHolder extends RecyclerView.ViewHolder {

    private final AppCompatImageView mIconView;
    private final TextView mTitleView;

    // ★ تمييز نوع الـ ViewHolder: فاصل أم عنصر تنقل عادي ★
    private final boolean mIsSeparator;

    // ★ خط sec-roboto-light لعناوين درج التنقل (كما في مكتبة One UI الرسمية) ★
    private final Typeface mNormalTypeface = Typeface.create("sec-roboto-light", Typeface.NORMAL);
    private final Typeface mSelectedTypeface = Typeface.create("sec-roboto-light", Typeface.BOLD);

    /**
     * بناء ViewHolder لعنصر تنقل عادي.
     * يُستدعى من DrawerListAdapter عند viewType == VIEW_TYPE_ITEM.
     */
    public DrawerListViewHolder(@NonNull View itemView) {
        this(itemView, false);
    }

    /**
     * بناء ViewHolder مع تحديد نوعه صراحةً.
     * يُستدعى من DrawerListAdapter عند viewType == VIEW_TYPE_SEPARATOR.
     *
     * @param itemView   الـ View المُضخَّم من الـ layout
     * @param isSeparator true إذا كان هذا ViewHolder خاصاً بالفاصل
     */
    public DrawerListViewHolder(@NonNull View itemView, boolean isSeparator) {
        super(itemView);
        mIsSeparator = isSeparator;

        // ★ عناصر الفاصل لا تحتوي على أيقونة أو عنوان — ستُعاد قيمة null ★
        mIconView = itemView.findViewById(R.id.drawer_item_icon);
        mTitleView = itemView.findViewById(R.id.drawer_item_title);
    }

    /** يُعيد true إذا كان هذا ViewHolder خاصاً بالفاصل المتقطع */
    public boolean isSeparator() {
        return mIsSeparator;
    }

    public void setIcon(@DrawableRes int resId) {
        if (mIconView != null) {
            mIconView.setImageResource(resId);
        }
    }

    public void setTitle(String title) {
        if (mTitleView != null) {
            mTitleView.setText(title);
        }
    }

    /**
     * تطبيق حالة التحديد باستخدام خط sec-roboto-light
     */
    public void setSelected(boolean selected) {
        if (mTitleView == null) {
            return;
        }

        itemView.setSelected(selected);

        mTitleView.setTypeface(selected ? mSelectedTypeface : mNormalTypeface);

        // تطبيق تأثير Marquee للعنصر المختار
        mTitleView.setEllipsize(selected ?
                TextUtils.TruncateAt.MARQUEE : TextUtils.TruncateAt.END);
    }
}
