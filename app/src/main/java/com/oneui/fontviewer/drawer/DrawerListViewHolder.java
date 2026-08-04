package com.oneui.fontviewer.drawer;

import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.recyclerview.widget.RecyclerView;

import com.oneui.fontviewer.R;

public class DrawerListViewHolder extends RecyclerView.ViewHolder {

    private final AppCompatImageView mIconView;
    private final TextView mTitleView;

    private final boolean mIsSeparator;

    private final Typeface mNormalTypeface = Typeface.create("sec-roboto-light", Typeface.NORMAL);
    private final Typeface mSelectedTypeface = Typeface.create("sec-roboto-light", Typeface.BOLD);

    public DrawerListViewHolder(@NonNull View itemView, boolean isSeparator) {
        super(itemView);
        mIsSeparator = isSeparator;

        mIconView = itemView.findViewById(R.id.drawer_item_icon);
        mTitleView = itemView.findViewById(R.id.drawer_item_title);
    }

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

    public void setSelected(boolean selected) {
        if (mTitleView == null) {
            return;
        }

        itemView.setSelected(selected);

        mTitleView.setTypeface(selected ? mSelectedTypeface : mNormalTypeface);

        mTitleView.setEllipsize(selected ?
                TextUtils.TruncateAt.MARQUEE : TextUtils.TruncateAt.END);
    }
}
