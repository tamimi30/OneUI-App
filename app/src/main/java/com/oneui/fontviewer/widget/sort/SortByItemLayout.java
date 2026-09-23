package com.oneui.fontviewer.widget.sort;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.TooltipCompat;

import com.oneui.fontviewer.R;

public class SortByItemLayout extends LinearLayout {

    private TextView mSortTextView;
    private View mTextContainer;
    private ImageView mOrderIcon;
    private View mOrderContainer;
    private OnSortChangeListener mListener;

    public enum SortType {
        NAME, DATE, SIZE
    }

    private SortType mCurrentSortType = SortType.NAME;
    private boolean mIsAscending = true;

    public interface OnSortChangeListener {
        void onSortChanged(SortType type, boolean ascending);
    }

    public SortByItemLayout(Context context) {
        super(context);
        init(context);
    }

    public SortByItemLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public SortByItemLayout(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.view_sort_by_item, this, true);

        mSortTextView = findViewById(R.id.sort_current_text);
        mTextContainer = findViewById(R.id.sort_text_container);
        mOrderIcon = findViewById(R.id.sort_order_icon);
        mOrderContainer = findViewById(R.id.sort_order_container);

        setupClickListeners();

        updateUI();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        
        if (mTextContainer != null) mTextContainer.setEnabled(enabled);
        if (mOrderContainer != null) mOrderContainer.setEnabled(enabled);
        
        setAlpha(enabled ? 1.0f : 0.4f);
    }

    public void setupClickListeners() {
        if (mTextContainer != null) {
            mTextContainer.setClickable(true);
            mTextContainer.setFocusable(true);
            mTextContainer.setOnClickListener(v -> {
                if (isEnabled()) showSortMenu();
            });
        }

        if (mOrderContainer != null) {
            mOrderContainer.setClickable(true);
            mOrderContainer.setFocusable(true);

            mOrderContainer.setOnClickListener(v -> {
                if (isEnabled()) {
                    mIsAscending = !mIsAscending;
                    updateUI();
                    notifyListener();
                }
            });
        }
    }

    private void showSortMenu() {
        if (mTextContainer == null) return;

        PopupMenu popup = new PopupMenu(getContext(), mTextContainer);
        popup.getMenuInflater().inflate(R.menu.menu_sort_options, popup.getMenu());

        if (mCurrentSortType == SortType.NAME) popup.getMenu().findItem(R.id.sort_by_name).setChecked(true);
        else if (mCurrentSortType == SortType.DATE) popup.getMenu().findItem(R.id.sort_by_date).setChecked(true);
        else if (mCurrentSortType == SortType.SIZE) popup.getMenu().findItem(R.id.sort_by_size).setChecked(true);

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.sort_by_name) {
                mCurrentSortType = SortType.NAME;
            } else if (id == R.id.sort_by_date) {
                mCurrentSortType = SortType.DATE;
            } else if (id == R.id.sort_by_size) {
                mCurrentSortType = SortType.SIZE;
            }
            updateUI();
            notifyListener();
            return true;
        });

        popup.show();
    }

    private void updateUI() {
        if (mSortTextView == null || mOrderIcon == null) return;

        if (mCurrentSortType == SortType.NAME) {
            mSortTextView.setText(R.string.sort_name);
        } else if (mCurrentSortType == SortType.DATE) {
            mSortTextView.setText(R.string.sort_date);
        } else if (mCurrentSortType == SortType.SIZE) {
            mSortTextView.setText(R.string.sort_size);
        }

        if (mIsAscending) {
            mOrderIcon.setImageResource(dev.oneuiproject.oneui.R.drawable.ic_oui_arrow_up);
            TooltipCompat.setTooltipText(mOrderContainer, getContext().getString(R.string.sort_ascending));
            mOrderContainer.setContentDescription(getContext().getString(R.string.sort_ascending));
        } else {
            mOrderIcon.setImageResource(dev.oneuiproject.oneui.R.drawable.ic_oui_arrow_down);
            TooltipCompat.setTooltipText(mOrderContainer, getContext().getString(R.string.sort_descending));
            mOrderContainer.setContentDescription(getContext().getString(R.string.sort_descending));
        }
    }

    public void setOnSortChangeListener(OnSortChangeListener listener) {
        this.mListener = listener;
    }

    private void notifyListener() {
        if (mListener != null) {
            mListener.onSortChanged(mCurrentSortType, mIsAscending);
        }
    }
    
    public void setSortType(SortType type) {
        this.mCurrentSortType = type;
        updateUI();
    }

    public void setSortAscending(boolean ascending) {
        this.mIsAscending = ascending;
        updateUI();
    }

}
