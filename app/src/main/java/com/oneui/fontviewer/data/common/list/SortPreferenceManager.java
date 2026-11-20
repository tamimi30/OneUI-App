package com.oneui.fontviewer.data.common.list;

import android.content.Context;
import android.util.Log;


import io.reactivex.rxjava3.schedulers.Schedulers;

import com.oneui.fontviewer.data.AppDataStore;

public class SortPreferenceManager {

    private static final String TAG = "SortPreferenceManager";

    public static final String LIST_TYPE_LOCAL     = "LOCAL";
    public static final String LIST_TYPE_SYSTEM    = "SYSTEM";
    public static final String LIST_TYPE_FAVORITES = "FAVORITES";

    private final AppDataStore dataStore;

    public SortPreferenceManager(Context context) {
        this.dataStore = AppDataStore.getInstance(context);
    }

    public String loadSortTypeName(String listType) {
        switch (listType) {
            case LIST_TYPE_SYSTEM:
                return dataStore.getSystemSortType().blockingFirst();
            case LIST_TYPE_FAVORITES:
                return dataStore.getFavoritesSortType().blockingFirst();
            case LIST_TYPE_LOCAL:
            default:
                return dataStore.getSortType().blockingFirst();
        }
    }

    public boolean loadSortAscending(String listType) {
        switch (listType) {
            case LIST_TYPE_SYSTEM:
                return dataStore.getSystemSortAscending().blockingFirst();
            case LIST_TYPE_FAVORITES:
                return dataStore.getFavoritesSortAscending().blockingFirst();
            case LIST_TYPE_LOCAL:
            default:
                return dataStore.getSortAscending().blockingFirst();
        }
    }

    public void saveSortType(String listType, String sortTypeName) {
        switch (listType) {

            case LIST_TYPE_SYSTEM:
                dataStore.setSystemSortType(sortTypeName)
                        .subscribeOn(Schedulers.io())
                        .subscribe(
                            prefs -> Log.d(TAG, "[SYSTEM] Saved sort type: " + sortTypeName),
                            error -> Log.e(TAG, "[SYSTEM] Error saving sort type", error)
                        );
                break;

            case LIST_TYPE_FAVORITES:
                dataStore.setFavoritesSortType(sortTypeName)
                        .subscribeOn(Schedulers.io())
                        .subscribe(
                            prefs -> Log.d(TAG, "[FAVORITES] Saved sort type: " + sortTypeName),
                            error -> Log.e(TAG, "[FAVORITES] Error saving sort type", error)
                        );
                break;

            case LIST_TYPE_LOCAL:
            default:
                dataStore.setSortType(sortTypeName)
                        .subscribeOn(Schedulers.io())
                        .subscribe(
                            prefs -> Log.d(TAG, "[LOCAL] Saved sort type: " + sortTypeName),
                            error -> Log.e(TAG, "[LOCAL] Error saving sort type", error)
                        );
                break;
        }
    }

    public void saveSortAscending(String listType, boolean ascending) {
        switch (listType) {

            case LIST_TYPE_SYSTEM:
                dataStore.setSystemSortAscending(ascending)
                        .subscribeOn(Schedulers.io())
                        .subscribe(
                            prefs -> Log.d(TAG, "[SYSTEM] Saved sort ascending: " + ascending),
                            error -> Log.e(TAG, "[SYSTEM] Error saving sort ascending", error)
                        );
                break;

            case LIST_TYPE_FAVORITES:
                dataStore.setFavoritesSortAscending(ascending)
                        .subscribeOn(Schedulers.io())
                        .subscribe(
                            prefs -> Log.d(TAG, "[FAVORITES] Saved sort ascending: " + ascending),
                            error -> Log.e(TAG, "[FAVORITES] Error saving sort ascending", error)
                        );
                break;

            case LIST_TYPE_LOCAL:
            default:
                dataStore.setSortAscending(ascending)
                        .subscribeOn(Schedulers.io())
                        .subscribe(
                            prefs -> Log.d(TAG, "[LOCAL] Saved sort ascending: " + ascending),
                            error -> Log.e(TAG, "[LOCAL] Error saving sort ascending", error)
                        );
                break;
        }
    }
}
