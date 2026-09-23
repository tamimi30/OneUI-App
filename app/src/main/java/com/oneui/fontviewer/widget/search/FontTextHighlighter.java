package com.oneui.fontviewer.widget.search;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import androidx.core.content.ContextCompat;
import com.oneui.fontviewer.R;

import java.util.Locale;

public class FontTextHighlighter {
    
    private final int highlightColor;
    
    public FontTextHighlighter(Context context) {
        this.highlightColor = ContextCompat.getColor(context, R.color.oui_primary_color);
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
    
}
