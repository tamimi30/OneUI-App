package com.oneui.fontviewer.ui.appscreen.main.sort;

import android.content.Context;
import android.util.Log;
import com.oneui.fontviewer.data.common.list.SortPreferenceManager;
import com.oneui.fontviewer.ui.core.widget.layout.SortByItemLayout;


public class SortManager {

    private static final String TAG = "SortManager";

    private final SortPreferenceManager sortPreferences;

    private final String listType;

    private SortByItemLayout.SortType currentSortType;
    private boolean isSortAscending;
    private SortChangeListener listener;

    public interface SortChangeListener {
        void onSortChanged(SortByItemLayout.SortType sortType, boolean ascending);
    }


    public SortManager(Context context, boolean isSystemFont) {
        this.sortPreferences = new SortPreferenceManager(context);
        this.listType     = isSystemFont ? SortPreferenceManager.LIST_TYPE_SYSTEM : SortPreferenceManager.LIST_TYPE_LOCAL;
        loadSortPreferences();
    }

    public SortManager(Context context, String listType) {
        this.sortPreferences = new SortPreferenceManager(context);
        this.listType     = (listType != null) ? listType.toUpperCase() : SortPreferenceManager.LIST_TYPE_LOCAL;
        loadSortPreferences();
    }

    public void setSortChangeListener(SortChangeListener listener) {
        this.listener = listener;
    }


    private void loadSortPreferences() {
        try {
            String sortTypeName = sortPreferences.loadSortTypeName(listType);
            currentSortType = SortByItemLayout.SortType.valueOf(sortTypeName);
        } catch (Exception e) {
            Log.w(TAG, "Invalid or missing sort type, using default", e);
            currentSortType = SortByItemLayout.SortType.NAME;
        }

        try {
            isSortAscending = sortPreferences.loadSortAscending(listType);
        } catch (Exception e) {
            Log.w(TAG, "Missing sort ascending value, using default", e);
            isSortAscending = true;
        }

        Log.d(TAG, "Loaded sort preferences [" + listType + "]: "
                + "type=" + currentSortType + ", ascending=" + isSortAscending);
    }

    private void saveSortPreferences() {
        sortPreferences.saveSortType(listType, currentSortType.name());
        sortPreferences.saveSortAscending(listType, isSortAscending);
    }


    public void setSortOptions(SortByItemLayout.SortType sortType, boolean ascending) {
        boolean changed = (this.currentSortType != sortType) || (this.isSortAscending != ascending);

        this.currentSortType = sortType;
        this.isSortAscending = ascending;

        if (changed) {
            saveSortPreferences();
            notifySortChanged();
        }
    }

    public SortByItemLayout.SortType getCurrentSortType() { return currentSortType; }

    public boolean isSortAscending() { return isSortAscending; }

    

    private void notifySortChanged() {
        if (listener != null) listener.onSortChanged(currentSortType, isSortAscending);
    }

    
                            }
