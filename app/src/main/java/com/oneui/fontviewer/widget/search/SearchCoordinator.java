package com.oneui.fontviewer.widget.search;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;

import dev.oneuiproject.oneui.layout.DrawerLayout;

import com.oneui.fontviewer.R;
import com.oneui.fontviewer.activity.AppScreen;
import com.oneui.fontviewer.fragment.favorite.FavoriteFontListFragment; 
import com.oneui.fontviewer.fragment.localfont.LocalFontListFragment;
import com.oneui.fontviewer.fragment.systemfont.SystemFontListFragment;


public class SearchCoordinator {

    private static final String TAG = "SearchCoordinator";
    private static final String KEY_SEARCH_QUERY    = "search_query";
    private static final String KEY_SEARCH_EXPANDED = "search_expanded";

    private final Activity     activity;
    private final DrawerLayout drawerLayout;

    private MenuItem       searchMenuItem;
    private SearchView     searchView;
    private ScreenProvider screenProvider;
    private FragmentProvider fragmentProvider;

    private boolean    isSearchExpanded = false;
    private String     savedSearchQuery = "";
    private AppScreen  lastScreen       = null; 

    private SearchStateListener stateListener;

    private boolean mPendingSearchRestore = false;


    public interface ScreenProvider {
        AppScreen getCurrentScreen();
    }

    public interface FragmentProvider {
        Fragment getFragment(AppScreen screen);
    }

    public interface SearchStateListener {
        void onSearchExpanded();
        void onSearchCollapsed();
        void onSearchQueryChanged(String query);
    }


    public SearchCoordinator(@NonNull Activity activity, @NonNull DrawerLayout drawerLayout) {
        this.activity     = activity;
        this.drawerLayout = drawerLayout;
    }

    public void setProviders(@NonNull ScreenProvider screenProvider,
                             @NonNull FragmentProvider fragmentProvider) {
        this.screenProvider   = screenProvider;
        this.fragmentProvider = fragmentProvider;
    }

    public void bindSearchMenuItem(@NonNull MenuItem searchMenuItem) {
        this.searchMenuItem = searchMenuItem;
        
        this.searchMenuItem.setActionView(null);
        this.searchMenuItem.setOnMenuItemClickListener(item -> {
            expandSearch();
            return true;
        });

        setupSearchView();

        if (mPendingSearchRestore) {
            mPendingSearchRestore = false;
            if (drawerLayout != null) {
                drawerLayout.post(() -> {
                    expandSearch();
                    if (searchView != null && !savedSearchQuery.isEmpty()) {
                        searchView.setQuery(savedSearchQuery, false);
                    }
                });
            }
        }
    }

    public void setSearchStateListener(@Nullable SearchStateListener listener) {
        this.stateListener = listener;
    }


    private void setupSearchView() {
        if (drawerLayout == null) return;

        searchView = drawerLayout.getSearchView();

        searchView.setQueryHint(activity.getString(R.string.search_font));
        searchView.setMaxWidth(Integer.MAX_VALUE);

        SearchManager searchManager = (SearchManager) activity.getSystemService(Context.SEARCH_SERVICE);
        if (searchManager != null) {
            searchView.setSearchableInfo(searchManager.getSearchableInfo(activity.getComponentName()));
        }

        searchView.setImeOptions(searchView.getImeOptions() | android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI | android.view.inputmethod.EditorInfo.IME_FLAG_NO_FULLSCREEN);

        drawerLayout.setSearchModeListener(new dev.oneuiproject.oneui.layout.ToolbarLayout.SearchModeListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                performSearch(query);
                if (stateListener != null) stateListener.onSearchQueryChanged(query);
                return true;
            }
            @Override
            public boolean onQueryTextChange(String newText) {
                performSearch(newText);
                if (stateListener != null) stateListener.onSearchQueryChanged(newText);
                return true;
            }

            @Override
            public void onSearchModeToggle(SearchView view, boolean visible) {
                if (visible) {
                    handleSearchExpand();
                } else {
                    handleSearchCollapse();
                }
            }
        });

        Log.d(TAG, "SearchView setup completed successfully");
    }


    private boolean handleSearchExpand() {
        isSearchExpanded = true;
        
        if (stateListener != null) {
            stateListener.onSearchExpanded();
        }

        Log.d(TAG, "Search expanded");
        return true;
    }

    private boolean handleSearchCollapse() {
        isSearchExpanded = false;
        savedSearchQuery = "";


        if (fragmentProvider != null) {
            Fragment localFrag = fragmentProvider.getFragment(AppScreen.LOCAL_FONTS);
            if (localFrag instanceof LocalFontListFragment) ((LocalFontListFragment) localFrag).resetFilter();

            Fragment sysFrag = fragmentProvider.getFragment(AppScreen.SYSTEM_FONTS);
            if (sysFrag instanceof SystemFontListFragment) ((SystemFontListFragment) sysFrag).resetFilter();

            Fragment favFrag = fragmentProvider.getFragment(AppScreen.FAVORITES);
            if (favFrag instanceof FavoriteFontListFragment) ((FavoriteFontListFragment) favFrag).resetFilter();
        }

        if (stateListener != null) {
            stateListener.onSearchCollapsed();
        }

        Log.d(TAG, "Search collapsed");
        return true;
    }



    private void performSearch(String query) {
        Fragment currentFragment = getCurrentFragment();
        if (currentFragment instanceof LocalFontListFragment) {
            ((LocalFontListFragment) currentFragment).filterFonts(query);
            Log.d(TAG, "Search performed on LocalFontListFragment with query: " + query);
        } else if (currentFragment instanceof SystemFontListFragment) {
            ((SystemFontListFragment) currentFragment).filterFonts(query);
            Log.d(TAG, "Search performed on SystemFontListFragment with query: " + query);
        } else if (currentFragment instanceof FavoriteFontListFragment) {
            ((FavoriteFontListFragment) currentFragment).filterFonts(query);
            Log.d(TAG, "Search performed on FavoriteFontListFragment with query: " + query);
        }
    }

    @Nullable
    private Fragment getCurrentFragment() {
        if (fragmentProvider == null || screenProvider == null) {
            return null;
        }
        AppScreen currentScreen = screenProvider.getCurrentScreen();
        return fragmentProvider.getFragment(currentScreen);
    }


    public boolean handleSearchIntent(@Nullable Intent intent) {
        if (intent == null || !Intent.ACTION_SEARCH.equals(intent.getAction())) {
            return false;
        }

        AppScreen currentScreen = screenProvider.getCurrentScreen();
        if (currentScreen != AppScreen.LOCAL_FONTS
                && currentScreen != AppScreen.SYSTEM_FONTS
                && currentScreen != AppScreen.FAVORITES) {
            intent.removeExtra(SearchManager.QUERY);
            return false;
        }

        if (searchMenuItem == null || !isSearchExpanded) {
            intent.removeExtra(SearchManager.QUERY);
            return false;
        }

        String query = intent.getStringExtra(SearchManager.QUERY);
        if (query != null && searchView != null) {
            searchView.setQuery(query, false);
            performSearch(query);
            Log.d(TAG, "Search intent handled with query: " + query);
            return true;
        }

        return false;
    }


    public void saveState(@NonNull Bundle outState) {
        if (screenProvider == null) return; 

        AppScreen currentScreen = screenProvider.getCurrentScreen();
        boolean isSearchableScreen = (currentScreen == AppScreen.LOCAL_FONTS
                || currentScreen == AppScreen.SYSTEM_FONTS
                || currentScreen == AppScreen.FAVORITES);

        if (isSearchExpanded && isSearchableScreen) {
            outState.putBoolean(KEY_SEARCH_EXPANDED, true);
            if (searchView != null) {
                String currentQuery = searchView.getQuery().toString();
                outState.putString(KEY_SEARCH_QUERY, currentQuery);
            } else {
                outState.putString(KEY_SEARCH_QUERY, "");
            }
        } else {
            outState.putBoolean(KEY_SEARCH_EXPANDED, false);
            outState.putString(KEY_SEARCH_QUERY, "");
        }

        Log.d(TAG, "Search state saved - expanded: " + isSearchExpanded
                + ", query: " + savedSearchQuery);
    }

    public void restoreState(@NonNull Bundle savedInstanceState) {
        if (screenProvider == null) return; 

        isSearchExpanded = savedInstanceState.getBoolean(KEY_SEARCH_EXPANDED, false);
        savedSearchQuery = savedInstanceState.getString(KEY_SEARCH_QUERY, "");

        AppScreen currentScreen = screenProvider.getCurrentScreen();
        boolean isSearchableScreen = (currentScreen == AppScreen.LOCAL_FONTS
                || currentScreen == AppScreen.SYSTEM_FONTS
                || currentScreen == AppScreen.FAVORITES);

        if (isSearchExpanded && isSearchableScreen) {
            if (searchMenuItem != null && drawerLayout != null) {
                drawerLayout.post(() -> {
                    if (searchMenuItem != null) {
                        searchMenuItem.expandActionView();
                        if (searchView != null && !savedSearchQuery.isEmpty()) {
                            searchView.setQuery(savedSearchQuery, false);
                        }
                    }
                });
            } else {
                mPendingSearchRestore = true;
            }
        }

        Log.d(TAG, "Search state restored - expanded: " + isSearchExpanded
                + ", query: " + savedSearchQuery);
    }


    public void collapseSearch() {
        if (!isSearchExpanded) return;
        if (drawerLayout != null) {
            drawerLayout.dismissSearchMode(); 
        }
    }

    public void expandSearch() {
        if (drawerLayout != null && !drawerLayout.isSearchMode()) {
            drawerLayout.showSearchMode(); 
        }
    }

    public void clearSearchFocus() {
        if (searchView == null) return;

        searchView.clearFocus();

        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(searchView.getWindowToken(), 0);
        }
    }

    public boolean isSearchExpanded() {
        return isSearchExpanded;
    }

    @NonNull
    public String getCurrentSearchQuery() {
        if (searchView != null) {
            return searchView.getQuery().toString();
        }
        return savedSearchQuery;
    }

    public void onFragmentChanged(AppScreen newScreen) {
        if (lastScreen != null && !lastScreen.equals(newScreen)) {
            collapseSearch();
        }
        lastScreen = newScreen;

        Log.d(TAG, "Fragment changed to: " + newScreen.name());
    }


    public void cleanup() {
        searchMenuItem        = null;
        searchView            = null;
        screenProvider        = null;
        fragmentProvider      = null;
        stateListener         = null;
        mPendingSearchRestore = false;

        Log.d(TAG, "SearchCoordinator cleaned up");
    }
    }
