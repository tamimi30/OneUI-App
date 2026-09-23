package com.oneui.fontviewer.fragment.localfont.adapter;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.oneui.fontviewer.R;
import com.oneui.fontviewer.widget.search.FontTextHighlighter;
import com.oneui.fontviewer.widget.SelectableLinearLayout;

public class LocalFontViewHolder extends RecyclerView.ViewHolder {

    public final TextView fontNameTextView;
    public final TextView weightWidthTextView;
    public final SelectableLinearLayout selectableLayout;
    public final View dividerView; 
    public final ImageView favoriteIconView;
    private String currentPath;
    private final Typeface originalTypeface;

    public LocalFontViewHolder(@NonNull View itemView) {
        super(itemView);
        fontNameTextView    = itemView.findViewById(R.id.font_item_name);
        weightWidthTextView = itemView.findViewById(R.id.font_item_weight_width); 
        selectableLayout = itemView.findViewById(R.id.selectable_layout);
        dividerView         = itemView.findViewById(R.id.item_divider);           
        favoriteIconView    = itemView.findViewById(R.id.font_item_favorite_icon); 
        originalTypeface    = fontNameTextView.getTypeface();
    }

    private void bindCore(String displayName,
                          String path,
                          boolean isSearchActive,
                          String searchQuery,
                          boolean isLastOpened,
                          FontTextHighlighter highlighter,
                          boolean isSelectionMode,
                          boolean isSelected,
                          String weightWidthLabel) {

        this.currentPath = path;
        itemView.setTag(path);

        if (selectableLayout != null) {
            selectableLayout.setSelectionMode(isSelectionMode);
            selectableLayout.setSelectedAnimate(isSelected);
        }
        Context context = fontNameTextView.getContext();

        if (isLastOpened) {
            int primaryColor = ContextCompat.getColor(context, R.color.oui_primary_color);
            fontNameTextView.setTextColor(primaryColor);
        } else {
            fontNameTextView.setTextColor(
                ContextCompat.getColor(context, R.color.primary_text_color)
            );
        }

        if (isSearchActive && searchQuery != null && !searchQuery.isEmpty()) {
            fontNameTextView.setText(highlighter.highlightText(displayName, searchQuery));
        } else {
            fontNameTextView.setText(displayName);
        }

        if (weightWidthTextView != null) {
            String label = (weightWidthLabel != null && !weightWidthLabel.isEmpty())
                    ? weightWidthLabel
                    : itemView.getContext().getString(R.string.unknown_font);
            weightWidthTextView.setText(label);
        }
    }


    public void bind(String displayName,
                     String path,
                     boolean isSearchActive,
                     String searchQuery,
                     boolean isLastOpened,
                     FontTextHighlighter highlighter,
                     boolean isSelectionMode,
                     boolean isSelected,
                     String weightWidthLabel) {
        bindCore(displayName, path, isSearchActive, searchQuery, isLastOpened,
                 highlighter, isSelectionMode, isSelected, weightWidthLabel);
    }
    

     public void setFavoriteIndicator(boolean isFavorite, boolean animate) {
        if (favoriteIconView != null) {
            favoriteIconView.animate().cancel(); 

            if (animate) {
                    boolean isCurrentlyVisible = (favoriteIconView.getVisibility() == View.VISIBLE);
                    if (isFavorite && !isCurrentlyVisible) {
                        favoriteIconView.setAlpha(0f);
                        favoriteIconView.setVisibility(View.VISIBLE);
                        favoriteIconView.animate().alpha(1f).setDuration(350).start();
                    } else if (!isFavorite && isCurrentlyVisible) {
                        favoriteIconView.animate().alpha(0f).setDuration(350).withEndAction(() -> {
                            favoriteIconView.setVisibility(View.INVISIBLE);
                        }).start();
                    } else if (isFavorite && isCurrentlyVisible) {
                        favoriteIconView.setAlpha(1f); 
                    } else if (!isFavorite && !isCurrentlyVisible) {
                        favoriteIconView.setAlpha(0f);
                    }
                } else {

                favoriteIconView.setVisibility(isFavorite ? View.VISIBLE : View.INVISIBLE);
                favoriteIconView.setAlpha(isFavorite ? 1f : 0f);
            }
        }
    }


    public void updateLastOpenedHighlight(boolean isLastOpened) {
        Context context = fontNameTextView.getContext();
        if (isLastOpened) {
            int primaryColor = ContextCompat.getColor(context, R.color.oui_primary_color);
            fontNameTextView.setTextColor(primaryColor);
        } else {
            fontNameTextView.setTextColor(
                ContextCompat.getColor(context, R.color.primary_text_color)
            );
        }
    }
    

    public void setTypeface(Typeface typeface) {
        fontNameTextView.setTypeface(typeface != null ? typeface : originalTypeface);
    }

    public void setDefaultTypeface(Typeface typeface) {
        fontNameTextView.setTypeface(typeface != null ? typeface : originalTypeface);
    }

    public String getTag() {
        return currentPath;
    }
                }
