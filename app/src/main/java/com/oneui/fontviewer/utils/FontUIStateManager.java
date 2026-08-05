package com.oneui.fontviewer.utils;

import android.content.Context;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.AppBarLayout;

import com.oneui.fontviewer.R;
import com.oneui.fontviewer.widget.sort.SortByItemLayout;

public class FontUIStateManager {
    
    private static final String TAG = "FontUIStateManager";
    
    private final Context context;
    private View selectFolderContainer;
    private View mainContentLayout;
    private View emptyView;
    private TextView emptyTextView;

    private TextView emptyTitleView;

    private TextView noResultsTextView;

    private View emptyIconView;

    private RecyclerView recyclerView;
    private AppBarLayout appBarLayout;

    private View loadingContainer;
    
    private Parcelable recyclerViewState;
    private SortByItemLayout.SortType savedSortType;
    private boolean savedSortAscending;

    private int defaultEmptyMessageResId = R.string.local_fonts_empty_message;
    
    public FontUIStateManager(Context context) {
        this.context = context;
    }
    
    public void setViews(View selectFolderContainer, View mainContentLayout, 
        View emptyView, TextView emptyTextView, RecyclerView recyclerView) {
        this.selectFolderContainer = selectFolderContainer;
        this.mainContentLayout = mainContentLayout;
        this.emptyView = emptyView;
        this.emptyTextView = emptyTextView;
        this.recyclerView = recyclerView;
    }

    public void setViews(View emptyView, TextView emptyTextView, RecyclerView recyclerView) {
        this.emptyView = emptyView;
        this.emptyTextView = emptyTextView;
        this.recyclerView = recyclerView;
    }

    public void setEmptyTitleView(TextView emptyTitleView) {
        this.emptyTitleView = emptyTitleView;
    }

    public void setNoResultsTextView(TextView noResultsTextView) {
        this.noResultsTextView = noResultsTextView;
    }

    public void setEmptyIconView(View emptyIconView) {
        this.emptyIconView = emptyIconView;
    }

    public void setLoadingContainer(View loadingContainer) {
        this.loadingContainer = loadingContainer;
    }
    
    public void setAppBarLayout(AppBarLayout appBarLayout) {
        this.appBarLayout = appBarLayout;
    }

    public void setDefaultEmptyMessage(int resId) {
        this.defaultEmptyMessageResId = resId;
    }
    
    public void updateUIVisibility(boolean hasFolderUri) {
        if (selectFolderContainer != null) {
            selectFolderContainer.setVisibility(hasFolderUri ? View.GONE : View.VISIBLE);
        }
        
        if (mainContentLayout != null) {
            mainContentLayout.setVisibility(hasFolderUri ? View.VISIBLE : View.GONE);
        }
    }
    
    public void updateEmptyView(boolean isEmpty, boolean isSearchActive) {
        if (isEmpty) {
            showEmptyView(isSearchActive);
        } else {
            hideEmptyView();
        }
    }

    public void updateEmptyView(boolean isEmpty) {
        updateEmptyView(isEmpty, false);
    }
    
    private void showEmptyView(boolean isSearchActive) {
        if (recyclerView != null) {
            recyclerView.setVisibility(View.GONE);
        }
        
        if (emptyView != null) {
            emptyView.setVisibility(View.VISIBLE);
            
            if (appBarLayout != null) {
                updateEmptyViewPosition(Math.abs(appBarLayout.getTop()));
            }
        }

        if (emptyIconView != null) {
            emptyIconView.setVisibility(isSearchActive ? View.VISIBLE : View.GONE);
        }

        if (noResultsTextView != null) {
            if (isSearchActive) {
                if (emptyTitleView != null) emptyTitleView.setVisibility(View.GONE);
                if (emptyTextView  != null) emptyTextView.setVisibility(View.GONE);
                noResultsTextView.setVisibility(View.VISIBLE);
            } else {
                if (emptyTitleView != null) emptyTitleView.setVisibility(View.VISIBLE);
                if (emptyTextView  != null) {
                    emptyTextView.setVisibility(View.VISIBLE);
                    emptyTextView.setText(context.getString(defaultEmptyMessageResId));
                }
                noResultsTextView.setVisibility(View.GONE);
            }
        } else {
            if (emptyTitleView != null) {
                emptyTitleView.setVisibility(isSearchActive ? View.GONE : View.VISIBLE);
            }
            if (emptyTextView != null) {
                if (isSearchActive) {
                    emptyTextView.setText(context.getString(R.string.no_results_found));
                } else {
                    emptyTextView.setText(context.getString(defaultEmptyMessageResId));
                }
            }
        }
    }
    
    private void hideEmptyView() {
        if (recyclerView != null) {
            recyclerView.setVisibility(View.VISIBLE);
        }
        
        if (emptyView != null) {
            emptyView.setVisibility(View.GONE);
        }

        if (noResultsTextView != null) {
            noResultsTextView.setVisibility(View.GONE);
        }
    }
    
    public void updateEmptyViewPosition(int verticalOffset) {
        if (appBarLayout == null) {
            return;
        }
        
        int totalScrollRange = appBarLayout.getTotalScrollRange();
        float translationY = 0f;
        
        if (totalScrollRange != 0) {
            translationY = (Math.abs(verticalOffset) - totalScrollRange) / 2.0f;
        }
        
        if (emptyView != null && emptyView.getVisibility() == View.VISIBLE) {
            View innerEmptyView = emptyView.findViewById(R.id.empty_container);
            if (innerEmptyView != null) {
                innerEmptyView.setTranslationY(translationY);
            }
        }

        if (selectFolderContainer != null && selectFolderContainer.getVisibility() == View.VISIBLE) {
            View innerFolderView = selectFolderContainer.findViewById(R.id.folder_container_inner);
            if (innerFolderView != null) {
                innerFolderView.setTranslationY(translationY);
            }
        }
    }
    
    public void saveRecyclerViewState() {
        if (recyclerView != null && recyclerView.getLayoutManager() != null) {
            recyclerViewState = recyclerView.getLayoutManager().onSaveInstanceState();
            Log.d(TAG, "RecyclerView state saved");
        }
    }
    
    public void restoreRecyclerViewState() {
        if (recyclerViewState != null && recyclerView != null && recyclerView.getLayoutManager() != null) {
            recyclerView.getLayoutManager().onRestoreInstanceState(recyclerViewState);
            recyclerViewState = null;
            Log.d(TAG, "RecyclerView state restored");
        }
    }
    
    public void setRecyclerViewState(Parcelable state) {
        this.recyclerViewState = state;
    }
    
    public Parcelable getRecyclerViewState() {
        return recyclerViewState;
    }
    
    public void saveSortState(SortByItemLayout.SortType sortType, boolean ascending) {
        this.savedSortType = sortType;
        this.savedSortAscending = ascending;
        Log.d(TAG, "Sort state saved: type=" + sortType + ", ascending=" + ascending);
    }
    
    
    
    public void showLoadingState() {
        if (recyclerView != null) recyclerView.setVisibility(View.GONE);
        if (emptyView != null) emptyView.setVisibility(View.GONE);
        if (loadingContainer != null) loadingContainer.setVisibility(View.VISIBLE); 
    }
    
    public void hideLoadingState() {
        if (loadingContainer != null) {
            loadingContainer.setVisibility(View.GONE); 
        }

    }
    
    
}
