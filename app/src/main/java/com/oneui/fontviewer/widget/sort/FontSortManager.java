package com.oneui.fontviewer.widget.sort;

import android.content.Context;
import android.util.Log;


import io.reactivex.rxjava3.schedulers.Schedulers;

import com.oneui.fontviewer.fragment.settings.datastore.SettingsDataStore;

public class FontSortManager {

    private static final String TAG = "FontSortManager";

    private static final String LIST_TYPE_LOCAL     = "LOCAL";
    private static final String LIST_TYPE_SYSTEM    = "SYSTEM";
    private static final String LIST_TYPE_FAVORITES = "FAVORITES";

    private final SettingsDataStore dataStore;

    private final String listType;

    private final boolean isSystemFont;

    private SortByItemLayout.SortType currentSortType;
    private boolean isSortAscending;
    private SortChangeListener listener;

    public interface SortChangeListener {
        void onSortChanged(SortByItemLayout.SortType sortType, boolean ascending);
    }


    public FontSortManager(Context context, boolean isSystemFont) {
        this.dataStore    = SettingsDataStore.getInstance(context);
        this.isSystemFont = isSystemFont;
        this.listType     = isSystemFont ? LIST_TYPE_SYSTEM : LIST_TYPE_LOCAL;
        loadSortPreferences();
    }

    public FontSortManager(Context context, String listType) {
        this.dataStore    = SettingsDataStore.getInstance(context);
        this.listType     = (listType != null) ? listType.toUpperCase() : LIST_TYPE_LOCAL;
        this.isSystemFont = LIST_TYPE_SYSTEM.equals(this.listType);
        loadSortPreferences();
    }

    public void setSortChangeListener(SortChangeListener listener) {
        this.listener = listener;
    }


    private void loadSortPreferences() {
        try {
            String sortTypeName;
            switch (listType) {
                case LIST_TYPE_SYSTEM:
                    sortTypeName = dataStore.getSystemSortType().blockingFirst();
                    break;
                case LIST_TYPE_FAVORITES:
                    sortTypeName = dataStore.getFavoritesSortType().blockingFirst();
                    break;
                case LIST_TYPE_LOCAL:
                default:
                    sortTypeName = dataStore.getSortType().blockingFirst();
                    break;
            }
            currentSortType = SortByItemLayout.SortType.valueOf(sortTypeName);
        } catch (Exception e) {
            Log.w(TAG, "Invalid or missing sort type, using default", e);
            currentSortType = SortByItemLayout.SortType.NAME;
        }

        try {
            switch (listType) {
                case LIST_TYPE_SYSTEM:
                    isSortAscending = dataStore.getSystemSortAscending().blockingFirst();
                    break;
                case LIST_TYPE_FAVORITES:
                    isSortAscending = dataStore.getFavoritesSortAscending().blockingFirst();
                    break;
                case LIST_TYPE_LOCAL:
                default:
                    isSortAscending = dataStore.getSortAscending().blockingFirst();
                    break;
            }
        } catch (Exception e) {
            Log.w(TAG, "Missing sort ascending value, using default", e);
            isSortAscending = true;
        }

        Log.d(TAG, "Loaded sort preferences [" + listType + "]: "
                + "type=" + currentSortType + ", ascending=" + isSortAscending);
    }

    private void saveSortPreferences() {
        switch (listType) {

            case LIST_TYPE_SYSTEM:
                dataStore.setSystemSortType(currentSortType.name())
                        .subscribeOn(Schedulers.io())
                        .subscribe(
                            prefs -> Log.d(TAG, "[SYSTEM] Saved sort type: " + currentSortType),
                            error -> Log.e(TAG, "[SYSTEM] Error saving sort type", error)
                        );
                dataStore.setSystemSortAscending(isSortAscending)
                        .subscribeOn(Schedulers.io())
                        .subscribe(
                            prefs -> Log.d(TAG, "[SYSTEM] Saved sort ascending: " + isSortAscending),
                            error -> Log.e(TAG, "[SYSTEM] Error saving sort ascending", error)
                        );
                break;

            case LIST_TYPE_FAVORITES:
                dataStore.setFavoritesSortType(currentSortType.name())
                        .subscribeOn(Schedulers.io())
                        .subscribe(
                            prefs -> Log.d(TAG, "[FAVORITES] Saved sort type: " + currentSortType),
                            error -> Log.e(TAG, "[FAVORITES] Error saving sort type", error)
                        );
                dataStore.setFavoritesSortAscending(isSortAscending)
                        .subscribeOn(Schedulers.io())
                        .subscribe(
                            prefs -> Log.d(TAG, "[FAVORITES] Saved sort ascending: " + isSortAscending),
                            error -> Log.e(TAG, "[FAVORITES] Error saving sort ascending", error)
                        );
                break;

            case LIST_TYPE_LOCAL:
            default:
                dataStore.setSortType(currentSortType.name())
                        .subscribeOn(Schedulers.io())
                        .subscribe(
                            prefs -> Log.d(TAG, "[LOCAL] Saved sort type: " + currentSortType),
                            error -> Log.e(TAG, "[LOCAL] Error saving sort type", error)
                        );
                dataStore.setSortAscending(isSortAscending)
                        .subscribeOn(Schedulers.io())
                        .subscribe(
                            prefs -> Log.d(TAG, "[LOCAL] Saved sort ascending: " + isSortAscending),
                            error -> Log.e(TAG, "[LOCAL] Error saving sort ascending", error)
                        );
                break;
        }
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
