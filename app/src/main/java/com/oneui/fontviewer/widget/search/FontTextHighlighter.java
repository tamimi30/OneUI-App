package com.oneui.fontviewer.widget.search;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import androidx.annotation.ColorInt;
import com.google.android.material.color.MaterialColors;

import java.util.Locale;

public class FontTextHighlighter {
    
    private final Context context;
    private final int highlightColor;
    
    public FontTextHighlighter(Context context) {
        this.context = context;
        this.highlightColor = MaterialColors.getColor(
            context,
            androidx.appcompat.R.attr.colorPrimary,
            context.getColor(android.R.color.holo_blue_light) 
        );
    }
    
    public FontTextHighlighter(Context context, @ColorInt int highlightColor) {
        this.context = context;
        this.highlightColor = highlightColor;
    }
    
    public SpannableString highlightText(String text, String searchQuery) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        
        if (searchQuery == null || searchQuery.isEmpty()) {
            return new SpannableString(text);
        }
        
        SpannableString spannableString = new SpannableString(text);
        String lowerText = text.toLowerCase(Locale.getDefault());
        String lowerQuery = searchQuery.toLowerCase(Locale.getDefault());
        
        int startPos = lowerText.indexOf(lowerQuery);
        
        if (startPos >= 0) {
            spannableString.setSpan(
                new ForegroundColorSpan(highlightColor),
                startPos,
                startPos + lowerQuery.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        
        return spannableString;
    }
    
    public SpannableString highlightAllOccurrences(String text, String searchQuery) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        
        if (searchQuery == null || searchQuery.isEmpty()) {
            return new SpannableString(text);
        }
        
        SpannableString spannableString = new SpannableString(text);
        String lowerText = text.toLowerCase(Locale.getDefault());
        String lowerQuery = searchQuery.toLowerCase(Locale.getDefault());
        
        int startPos = 0;
        while (startPos >= 0 && startPos < lowerText.length()) {
            startPos = lowerText.indexOf(lowerQuery, startPos);
            
            if (startPos >= 0) {
                spannableString.setSpan(
                    new ForegroundColorSpan(highlightColor),
                    startPos,
                    startPos + lowerQuery.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                );
                startPos += lowerQuery.length();
            }
        }
        
        return spannableString;
    }
    
    public static boolean containsQuery(String text, String searchQuery) {
        if (text == null || searchQuery == null) {
            return false;
        }
        
        if (searchQuery.isEmpty()) {
            return true;
        }
        
        String lowerText = text.toLowerCase(Locale.getDefault());
        String lowerQuery = searchQuery.toLowerCase(Locale.getDefault());
        
        return lowerText.contains(lowerQuery);
    }
    
    @ColorInt
    public int getHighlightColor() {
        return highlightColor;
    }
}
