package com.oneui.fontviewer.widget.sort;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.oneui.fontviewer.R;

public class SortHeaderViewHolder extends RecyclerView.ViewHolder {
    
    private final SortByItemLayout sortLayout;
    private boolean isListenerSet = false;
    
    public SortHeaderViewHolder(@NonNull View itemView) {
        super(itemView);
        sortLayout = itemView.findViewById(R.id.sort_layout);
        sortLayout.setupClickListeners();
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
            // SortByItemLayout.setEnabled تتولى تعطيل الأزرار الداخلية وضبط الشفافية (0.4 / 1.0)،
            // فلا حاجة لتكرار setAlpha أو setClickable هنا
            sortLayout.setEnabled(enabled);
        }
    }
}
