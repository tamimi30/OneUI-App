package com.oneui.fontviewer.widget.sort;

import android.content.Context;
import android.util.Log;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import io.reactivex.rxjava3.schedulers.Schedulers;

import com.oneui.fontviewer.fragment.settings.datastore.SettingsDataStore;
import com.oneui.fontviewer.data.entity.FontFileInfo;

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

    public void setSortType(SortByItemLayout.SortType sortType) {
        setSortOptions(sortType, this.isSortAscending);
    }

    public void setSortAscending(boolean ascending) {
        setSortOptions(this.currentSortType, ascending);
    }

    public void toggleSortDirection() {
        setSortAscending(!isSortAscending);
    }

    @Deprecated
    public void sortFontsList(List<FontFileInfo> fontsToSort) {
        if (fontsToSort == null || fontsToSort.isEmpty()) return;
        Comparator<FontFileInfo> comparator = getComparatorForCurrentSort();
        Collections.sort(fontsToSort, comparator);
    }

    private Comparator<FontFileInfo> getComparatorForCurrentSort() {
        Comparator<FontFileInfo> comparator;

        switch (currentSortType) {
            case DATE:
                comparator = (f1, f2) -> {
                    if (f1 == null || f2 == null) return f1 == null ? 1 : -1;
                    return Long.compare(f1.getLastModified(), f2.getLastModified());
                };
                break;
            case SIZE:
                comparator = (f1, f2) -> {
                    if (f1 == null || f2 == null) return f1 == null ? 1 : -1;
                    return Long.compare(f1.getSize(), f2.getSize());
                };
                break;
            case NAME:
            default:
                comparator = (f1, f2) -> {
                    if (f1 == null || f1.getName() == null) return 1;
                    if (f2 == null || f2.getName() == null) return -1;
                    return f1.getName().compareToIgnoreCase(f2.getName());
                };
                break;
        }

        if (!isSortAscending) comparator = Collections.reverseOrder(comparator);
        return comparator;
    }

    public SortByItemLayout.SortType getCurrentSortType() { return currentSortType; }

    public boolean isSortAscending() { return isSortAscending; }

    public void reloadPreferences() {
        loadSortPreferences();
        notifySortChanged();
    }

    public void resetToDefaults() {
        setSortOptions(SortByItemLayout.SortType.NAME, true);
    }

    private void notifySortChanged() {
        if (listener != null) listener.onSortChanged(currentSortType, isSortAscending);
    }

    public String getSortDescription() {
        String typeName;
        switch (currentSortType) {
            case DATE:  typeName = "Date";  break;
            case SIZE:  typeName = "Size";  break;
            case NAME:
            default:    typeName = "Name";  break;
        }
        return typeName + " (" + (isSortAscending ? "Ascending" : "Descending") + ")"
                + " [" + listType + "]";
    }
                            }
