package com.oneui.fontviewer.fragment.systemfont.adapter;

import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.oneui.fontviewer.R;
import com.oneui.fontviewer.widget.search.FontTextHighlighter;
import com.oneui.fontviewer.metadata.FontWeightWidthExtractor;

import com.google.android.material.color.MaterialColors;

public class SystemFontViewHolder extends RecyclerView.ViewHolder {

    private final TextView nameView;
    private final TextView weightWidthView;
    public final View dividerView; 

    public SystemFontViewHolder(@NonNull View itemView) {
        super(itemView);
        nameView        = itemView.findViewById(R.id.font_item_name);
        weightWidthView = itemView.findViewById(R.id.font_item_weight_width); 
        dividerView     = itemView.findViewById(R.id.item_divider); 
    }

    public void bind(String displayName, String path, boolean isSearchActive,
                     String searchQuery, boolean isLastOpened, FontTextHighlighter highlighter,
                     String weightWidthLabel) {

        if (isSearchActive && searchQuery != null && !searchQuery.isEmpty()) {
            nameView.setText(highlighter.highlightText(displayName, searchQuery));
        } else {
            nameView.setText(displayName);
        }

        bindLastOpened(isLastOpened);

        if (weightWidthView != null) {
            String label = (weightWidthLabel != null && !weightWidthLabel.isEmpty())
                    ? weightWidthLabel
                    : itemView.getContext().getString(R.string.unknown_font);
            weightWidthView.setText(label);
        }

        nameView.setTag(path);
    }

    public void bindLastOpened(boolean isLastOpened) {
        if (nameView == null) return;

        if (isLastOpened) {
            int primaryColor = MaterialColors.getColor(
                nameView.getContext(),
                androidx.appcompat.R.attr.colorPrimary,
                nameView.getContext().getColor(android.R.color.holo_blue_light) 
            );
            nameView.setTextColor(primaryColor);
        } else {
            nameView.setTextColor(
                ContextCompat.getColor(nameView.getContext(), R.color.primary_text_color)
            );
        }
    }

    public void setTypeface(Typeface typeface) {
        if (nameView != null && typeface != null) {
            nameView.setTypeface(typeface);
        }
    }

    public void setDefaultTypeface(Typeface defaultTypeface) {
        if (nameView != null) {
            nameView.setTypeface(defaultTypeface != null ? defaultTypeface : Typeface.DEFAULT);
        }
    }

    public Object getTag() {
        return nameView != null ? nameView.getTag() : null;
    }

    public void setOnClickListener(View.OnClickListener listener) {
        if (itemView != null) {
            itemView.setOnClickListener(listener);
        }
    }
}
