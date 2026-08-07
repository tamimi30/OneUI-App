package com.oneui.fontviewer.widget.sort;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.oneui.fontviewer.R;

public class SortHeaderViewHolder extends RecyclerView.ViewHolder {

    private final SortByItemLayout sortLayout;
    private boolean isInitialized = false;
    private boolean isListenerSet = false;

    public SortHeaderViewHolder(@NonNull View itemView) {
        super(itemView);
        sortLayout = itemView.findViewById(R.id.sort_layout);
        sortLayout.setupClickListeners();
        isInitialized = true;
    }

    public void bind(SortByItemLayout.SortType sortType, boolean ascending,
                    SortByItemLayout.OnSortChangeListener listener) {
        if (sortLayout != null) {
            if (listener != null && !isListenerSet) {
                sortLayout.setOnSortChangeListener(listener);
                isListenerSet = true;
            }
            sortLayout.setSortType(sortType);
            sortLayout.setSortAscending(ascending);
        }
    }

    public void setSortEnabled(boolean enabled) {
        if (sortLayout != null) {
            sortLayout.setEnabled(enabled);
            sortLayout.setClickable(enabled);
            sortLayout.setAlpha(enabled ? 1.0f : 0.4f);
        }
    }
}
