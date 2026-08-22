package com.oneui.fontviewer.fragment.systemfont;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.AppBarLayout;

import com.oneui.fontviewer.widget.OneUiDrawerLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.oneui.fontviewer.activity.AppScreen;           
import com.oneui.fontviewer.activity.MainActivity;
import com.oneui.fontviewer.data.entity.FontEntity;
import com.oneui.fontviewer.data.entity.FontFileInfo;
import com.oneui.fontviewer.widget.search.FontSearchManager;
import com.oneui.fontviewer.widget.search.SearchViewModel;
import com.oneui.fontviewer.widget.sort.FontSortManager;
import com.oneui.fontviewer.widget.sort.SortByItemLayout;
import com.oneui.fontviewer.utils.FontUIStateManager;
import com.oneui.fontviewer.fragment.systemfont.adapter.SystemFontListAdapter;
import com.oneui.fontviewer.R;
import com.oneui.fontviewer.fragment.systemfont.viewmodel.SystemFontListViewModel;
import com.oneui.fontviewer.fragment.settings.viewmodel.SettingsViewModel;
import com.oneui.fontviewer.fragment.systemfont.data.SystemFontInfo;
import com.oneui.fontviewer.fragment.systemfont.data.SystemFontCache;

public class SystemFontListFragment extends Fragment implements AppBarLayout.OnOffsetChangedListener {

    private static final String TAG = "SystemFontListFragment";

    private Context mContext;
    private RecyclerView mRecyclerView;
    private SystemFontListAdapter mAdapter;
    private OnFontSelectedListener mFontSelectedListener;
    private Handler mMainHandler;
    private ExecutorService mExecutor;
    private AppBarLayout mAppBarLayout;
    private OneUiDrawerLayout mDrawerLayout;

    private FontSearchManager mSearchManager;
    private FontSortManager mSortManager;
    private FontUIStateManager mUIManager;

    private SystemFontListViewModel mViewModel;
    private SearchViewModel mSearchViewModel;
    private SettingsViewModel mSettingsViewModel;
    private List<FontEntity> mCurrentFontsList = new ArrayList<>();

    private boolean mIsFirstLoad = true;

    private boolean mNeedsScrollRestore = false;

    public interface OnFontSelectedListener {
        void onFontSelected(String fontPath, String realName, String fileName,
                            int ttcIndex, String weightWidthLabel);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mContext = context;
        if (context instanceof OnFontSelectedListener) {
            mFontSelectedListener = (OnFontSelectedListener) context;
        }

        initializeManagers();
        setupManagerListeners();
    }

    private void initializeManagers() {
        mSearchManager = new FontSearchManager();

        mSortManager = new FontSortManager(mContext, true);

        mUIManager = new FontUIStateManager(mContext);
    }

    private void setupManagerListeners() {
        mSearchManager.setSearchResultListener((count, empty) -> {
            mUIManager.updateEmptyView(empty, mSearchManager.isSearchActive());
        });

        mSortManager.setSortChangeListener((type, asc) -> {
            if (mAdapter != null) {
                mAdapter.setSortOptions(type, asc);
            }
        });
    }

    @Override
    public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);

        setHasOptionsMenu(true);

        mMainHandler = new Handler(Looper.getMainLooper());
        mExecutor    = Executors.newSingleThreadExecutor();

        initializeViewModels();

        if (state != null) {
            restoreInstanceState(state);
            mIsFirstLoad = state.getBoolean("is_first_load", true);
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        menu.clear(); 
        inflater.inflate(R.menu.menu_font_list_search, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search_fonts);
        if (getActivity() instanceof MainActivity && searchItem != null) {
            ((MainActivity) getActivity()).getSearchCoordinator().bindSearchMenuItem(searchItem);
        }

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    private void initializeViewModels() {
        mViewModel         = new ViewModelProvider(this).get(SystemFontListViewModel.class);
        mSearchViewModel   = new ViewModelProvider(this).get(SearchViewModel.class);
        mSettingsViewModel = new ViewModelProvider(this).get(SettingsViewModel.class);

        mViewModel.getFontsLiveData().observe(this, fonts -> {
            if (fonts != null) {
                mCurrentFontsList = new ArrayList<>(fonts);
                refreshAdapterData();
                updateMainActivityFontsCount(fonts.size());

                List<String> pathsToPreload = new ArrayList<>();
                for (FontEntity font : fonts) {
                    pathsToPreload.add(font.getPath());
                }
                SystemFontCache.getInstance().preloadFonts(pathsToPreload);
            }
        });

        mViewModel.getIsLoadingLiveData().observe(this, isLoading -> {
            if (isLoading != null && isLoading) {
                mUIManager.showLoadingState();
            } else {
                mUIManager.hideLoadingState();
            }
        });

        mViewModel.getIsApiAvailableLiveData().observe(this, isAvailable -> {
            if (isAvailable != null && !isAvailable) {
                showUnsupportedView();
            }
        });

        mSearchViewModel.getSearchQueryLiveData().observe(this, query -> {
            if (query != null) {
                mSearchManager.filterFonts(query);
                if (mAdapter != null) {
                    mAdapter.updateFilteredFonts(
                        mSearchManager.getFilteredFonts(),
                        mSearchManager.getCurrentSearchQuery()
                    );
                }
            }
        });

        mSettingsViewModel.getFontPreviewEnabled().observe(this, enabled -> {
            if (mAdapter != null && isAdded()) {
                mAdapter.setFontPreviewEnabled(enabled); 
                Log.d(TAG, "Font preview setting changed: " + enabled);
            }
        });
    }

    private void restoreInstanceState(Bundle state) {
        mUIManager.setRecyclerViewState(state.getParcelable("recycler_state"));

        if (mUIManager.getRecyclerViewState() != null) {
            mNeedsScrollRestore = true;
        }

        String sortType = state.getString("sort_type");
        if (sortType != null) {
            try {
                mUIManager.saveSortState(
                    SortByItemLayout.SortType.valueOf(sortType),
                    state.getBoolean("sort_asc", true)
                );
            } catch (Exception e) {
                Log.e(TAG, "Failed to restore sort state", e);
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_system_font_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);

        initializeViews(view);
        setupRecyclerView();
        setupAppBarLayout();


        if (mIsFirstLoad) {
            mViewModel.loadSystemFonts();
            mIsFirstLoad = false;
        }
    }

    private void initializeViews(View view) {
        mRecyclerView = view.findViewById(R.id.system_font_recycler_view);
        mUIManager.setViews(
            null,
            view.findViewById(R.id.main_content_layout),
            view.findViewById(R.id.empty_view),
            null,
            mRecyclerView
        );

        mUIManager.setNoResultsTextView(view.findViewById(R.id.no_results_text));
        mUIManager.setEmptyIconView(view.findViewById(R.id.empty_icon));
    }

    private void setupRecyclerView() {
        mRecyclerView.setLayoutManager(new LinearLayoutManager(mContext));

        mAdapter = new SystemFontListAdapter(mContext, mExecutor);

        mAdapter.setFontClickListener((fontPath, realName, fileName, ttcIndex, weightWidthLabel) -> {
            mViewModel.recordFontAccess(fontPath);

            if (mFontSelectedListener != null) {
                mFontSelectedListener.onFontSelected(fontPath, realName, fileName,
                                                     ttcIndex, weightWidthLabel);
            }

            mMainHandler.postDelayed(() -> mAdapter.saveLastOpenedAndUpdate(fontPath), 400);
        });


        mAdapter.setSortChangeListener((type, asc) -> {
            mSortManager.setSortOptions(type, asc);
        });

        mRecyclerView.setAdapter(mAdapter);

        mAdapter.updateSortOptionsOnly(
            mSortManager.getCurrentSortType(),
            mSortManager.isSortAscending()
        );

        setupRecyclerViewAnimator();

        mRecyclerView.seslSetFillBottomEnabled(false);
        mRecyclerView.seslSetLastRoundedCorner(false);
        mRecyclerView.seslSetFastScrollerEnabled(false);
        mRecyclerView.seslSetIndexTipEnabled(false);
        mRecyclerView.seslSetGoToTopEnabled(true);
        mRecyclerView.seslSetSmoothScrollEnabled(true);
    }

    private void setupRecyclerViewAnimator() {
        if (mRecyclerView == null) return;
        androidx.recyclerview.widget.DefaultItemAnimator animator =
            new androidx.recyclerview.widget.DefaultItemAnimator();
        animator.setAddDuration(150);
        animator.setRemoveDuration(150);
        animator.setMoveDuration(250);
        animator.setSupportsChangeAnimations(false);
        mRecyclerView.setItemAnimator(animator);
    }

    private void setupAppBarLayout() {
        if (getActivity() != null) {
            View drawer = getActivity().findViewById(R.id.drawer_layout);
            if (drawer instanceof OneUiDrawerLayout) {
                mDrawerLayout = (OneUiDrawerLayout) drawer;
                mAppBarLayout = mDrawerLayout.getAppBarLayout();
                if (mAppBarLayout != null) {
                    mAppBarLayout.addOnOffsetChangedListener(this);
                    mUIManager.setAppBarLayout(mAppBarLayout);
                }
            }
        }
    }

    private void showUnsupportedView() {
        if (getView() == null) return;
        View unsupportedView = getView().findViewById(R.id.unsupported_view);
        View mainContent     = getView().findViewById(R.id.main_content_layout);
        if (unsupportedView != null) unsupportedView.setVisibility(View.VISIBLE);
        if (mainContent != null)     mainContent.setVisibility(View.GONE);
    }

    private void refreshAdapterData() {
        if (mCurrentFontsList.isEmpty()) {
            mSearchManager.updateFontsList(new ArrayList<>());
            if (mAdapter != null) {
                mAdapter.updateFilteredFonts(new ArrayList<>(), mSearchManager.getCurrentSearchQuery());
                mAdapter.updateSortOptionsOnly(
                    mSortManager.getCurrentSortType(),
                    mSortManager.isSortAscending()
                );
            }
            return;
        }

        List<FontFileInfo> rawFonts = new ArrayList<>();
        for (FontEntity font : mCurrentFontsList) {
            rawFonts.add(new FontFileInfo(
                font.getFileName(),
                font.getPath(),
                font.getSize(),
                font.getLastModified()
            ));
        }

        mSearchManager.updateFontsList(rawFonts);

        if (mAdapter != null) {
            mAdapter.setAllFontsInfo(convertEntitiesToSystemFontInfo(mCurrentFontsList));

            mAdapter.updateFilteredFonts(
                mSearchManager.getFilteredFonts(),
                mSearchManager.getCurrentSearchQuery()
            );
            mAdapter.updateSortOptionsOnly(
                mSortManager.getCurrentSortType(),
                mSortManager.isSortAscending()
            );
        }

        if (mNeedsScrollRestore) {
            mNeedsScrollRestore = false;
            mMainHandler.post(() -> {
                if (isAdded() && getView() != null) {
                    mUIManager.restoreRecyclerViewState();
                }
            });
        }
    }

    private List<SystemFontInfo> convertEntitiesToSystemFontInfo(
            List<FontEntity> entities) {
        List<SystemFontInfo> result = new ArrayList<>();
        for (FontEntity entity : entities) {
            SystemFontInfo info =
                new SystemFontInfo(
                    entity.getFileName(),
                    entity.getPath(),
                    entity.getSize(),
                    entity.getLastModified(),
                    400,
                    0,
                    entity.getTtcIndex(),
                    null
                );
            if (entity.getRealName() != null && !entity.getRealName().isEmpty()) {
                info.setRealName(entity.getRealName());
            }
            info.setWeightWidthLabel(entity.getWeightWidthLabel());
            result.add(info);
        }
        return result;
    }


    public void onSearchStateChanged(boolean isExpanded) {
        if (mSearchViewModel != null) {
            if (isExpanded) {
                mSearchViewModel.activateSearch();
            } else {
                mSearchViewModel.deactivateSearch();
            }
        }
    }

    public void filterFonts(String query) {
        mSearchManager.filterFonts(query);
        if (mAdapter != null) {
            mAdapter.updateFilteredFonts(
                mSearchManager.getFilteredFonts(),
                mSearchManager.getCurrentSearchQuery()
            );
        }
    }

    public void resetFilter() {
        mSearchManager.resetFilter();
        if (mAdapter != null) {
            mAdapter.updateFilteredFonts(
                mSearchManager.getFilteredFonts(),
                mSearchManager.getCurrentSearchQuery()
            );
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mUIManager.saveRecyclerViewState();
        if (mUIManager.getRecyclerViewState() != null) {
            outState.putParcelable("recycler_state", mUIManager.getRecyclerViewState());
        }
        outState.putString("sort_type", mSortManager.getCurrentSortType().name());
        outState.putBoolean("sort_asc", mSortManager.isSortAscending());
        outState.putBoolean("is_first_load", mIsFirstLoad);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isHidden()) {
            updateMainActivityFontsCount(mCurrentFontsList.size());
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);

        setMenuVisibility(!hidden);
        if (!hidden && getActivity() != null) {
            getActivity().invalidateOptionsMenu(); 
        }

        if (hidden) {
            mUIManager.saveRecyclerViewState();

            if (mRecyclerView != null) {
                mRecyclerView.setItemAnimator(null);
            }

            mSearchViewModel.deactivateSearch();
        } else {
            if (mAdapter != null) mAdapter.smartUpdate();

            updateMainActivityFontsCount(mCurrentFontsList.size());

            mMainHandler.post(() -> mUIManager.restoreRecyclerViewState());

            if (mRecyclerView != null) {
                mRecyclerView.postDelayed(() -> {
                    if (isAdded() && !isHidden()) {
                        setupRecyclerViewAnimator();
                    }
                }, 100);
            }
        }
    }

    private void updateMainActivityFontsCount(int count) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).updateFontsCount(AppScreen.SYSTEM_FONTS, count);
        }
    }

    @Override
    public void onOffsetChanged(AppBarLayout bar, int offset) {
        mUIManager.updateEmptyViewPosition(offset);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mAppBarLayout != null) {
            mAppBarLayout.removeOnOffsetChangedListener(this);
            mAppBarLayout = null;
        }
        mDrawerLayout = null;
        mRecyclerView = null;
        mAdapter      = null;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mFontSelectedListener = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mMainHandler != null) mMainHandler.removeCallbacksAndMessages(null);
        if (mExecutor != null)    mExecutor.shutdown();
    }
    }
