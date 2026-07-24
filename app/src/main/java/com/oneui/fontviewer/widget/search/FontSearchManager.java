package com.oneui.fontviewer.widget.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.oneui.fontviewer.data.entity.FontFileInfo;
import com.oneui.fontviewer.utils.FileUtils;

public class FontSearchManager {
    
    private static final String TAG = "FontSearchManager";
    
    private final List<FontFileInfo> allFonts;
    private final List<FontFileInfo> filteredFonts;
    private String currentSearchQuery;
    private SearchResultListener listener;
    
    public interface SearchResultListener {
        void onSearchResultsChanged(int resultCount, boolean isEmpty);
    }
    
    public FontSearchManager() {
        this.allFonts          = new ArrayList<>();
        this.filteredFonts     = new ArrayList<>();
        this.currentSearchQuery = "";
    }
    
    public void setSearchResultListener(SearchResultListener listener) {
        this.listener = listener;
    }
    
    public void updateFontsList(List<FontFileInfo> fonts) {
        allFonts.clear();
        if (fonts != null) {
            allFonts.addAll(fonts);
        }
        applyCurrentFilter();
    }
    
    public void filterFonts(String query) {
        currentSearchQuery = query == null ? "" : query.trim();
        applyCurrentFilter();
    }
    
    public void resetFilter() {
        if (!currentSearchQuery.isEmpty()) {
            currentSearchQuery = "";
            applyCurrentFilter();
        }
    }
    
    private void applyCurrentFilter() {
        filteredFonts.clear();
        
        if (currentSearchQuery.isEmpty()) {
            filteredFonts.addAll(allFonts);
        } else {
            String lowerCaseQuery = currentSearchQuery.toLowerCase(Locale.getDefault());
            for (FontFileInfo font : allFonts) {
                if (font.getName() != null) {
                    String nameWithoutExtension = FileUtils.removeExtension(font.getName());
                    if (nameWithoutExtension.toLowerCase(Locale.getDefault()).contains(lowerCaseQuery)) {
                        filteredFonts.add(font);
                    }
                }
            }
        }
        
        notifySearchResultsChanged();
    }
    
    public void reapplyFilter() {
        applyCurrentFilter();
    }
    
    public List<FontFileInfo> getFilteredFonts() {
        return new ArrayList<>(filteredFonts);
    }
    
    public List<FontFileInfo> getAllFonts() {
        return new ArrayList<>(allFonts);
    }
    
    public String getCurrentSearchQuery() {
        return currentSearchQuery;
    }
    
    public boolean isSearchActive() {
        return !currentSearchQuery.isEmpty();
    }
    
    public boolean isFilteredListEmpty() {
        return filteredFonts.isEmpty();
    }
    
    public int getFilteredCount() {
        return filteredFonts.size();
    }
    
    public int getTotalCount() {
        return allFonts.size();
    }
    
    public void clear() {
        allFonts.clear();
        filteredFonts.clear();
        currentSearchQuery = "";
        notifySearchResultsChanged();
    }
    
    private void notifySearchResultsChanged() {
        if (listener != null) {
            listener.onSearchResultsChanged(filteredFonts.size(), filteredFonts.isEmpty());
        }
    }
    
    public boolean matchesCurrentQuery(FontFileInfo fontInfo) {
        if (currentSearchQuery.isEmpty()) {
            return true;
        }
        
        if (fontInfo == null || fontInfo.getName() == null) {
            return false;
        }

        String nameWithoutExtension = FileUtils.removeExtension(fontInfo.getName());
        String lowerFontName = nameWithoutExtension.toLowerCase(Locale.getDefault());
        String lowerQuery    = currentSearchQuery.toLowerCase(Locale.getDefault());
        
        return lowerFontName.contains(lowerQuery);
    }
            }
