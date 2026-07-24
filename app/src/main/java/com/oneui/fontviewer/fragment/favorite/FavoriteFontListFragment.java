package com.oneui.fontviewer.fragment.favorite;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.AppBarLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.oneuiproject.oneui.dialog.ProgressDialog;
import dev.oneuiproject.oneui.layout.DrawerLayout;

import com.oneui.fontviewer.R;
import com.oneui.fontviewer.activity.AppScreen;           
import com.oneui.fontviewer.activity.MainActivity;
import com.oneui.fontviewer.fragment.trash.data.TrashRepository;
import com.oneui.fontviewer.dialog.FontActionDialogs;
import com.oneui.fontviewer.data.entity.FontFileInfo;
import com.oneui.fontviewer.widget.sort.FontSortManager;
import com.oneui.fontviewer.widget.sort.SortByItemLayout;
import com.oneui.fontviewer.utils.FontUIStateManager;
import com.oneui.fontviewer.utils.FontItemDecoration;
import com.oneui.fontviewer.fragment.localfont.adapter.LocalFontListAdapter;
import com.oneui.fontviewer.fragment.localfont.data.LocalFontCache;
import com.oneui.fontviewer.fragment.localfont.manager.LocalFontSelectionManager;
import com.oneui.fontviewer.widget.search.FontSearchManager;
import com.oneui.fontviewer.widget.search.SearchViewModel;
import com.oneui.fontviewer.fragment.localfont.viewmodel.LocalFontListViewModel;
import com.oneui.fontviewer.fragment.settings.viewmodel.SettingsViewModel;
import com.oneui.fontviewer.utils.notification.BatchOperationState;

public class FavoriteFontListFragment extends Fragment implements AppBarLayout.OnOffsetChangedListener {

    private static final String TAG = "FavoriteFontListFragment";

    private Context mContext;
    private RecyclerView mRecyclerView;
    private LocalFontListAdapter mAdapter;
    private OnFontSelectedListener mFontSelectedListener;
    private Handler mMainHandler;
    private ExecutorService mExecutor;
    private AppBarLayout mAppBarLayout;
    private DrawerLayout mDrawerLayout;

    private FontSearchManager mSearchManager;
    private FontSortManager mSortManager;
    private FontUIStateManager mUIManager;
    private LocalFontSelectionManager mSelectionManager;

    private LocalFontListViewModel mViewModel;
    private SearchViewModel mSearchViewModel;
    private SettingsViewModel mSettingsViewModel;

    private List<LocalFontListViewModel.FontFileInfoWithMetadata> mCurrentFavoritesList = new ArrayList<>();

    private boolean mIsBatchOperationRunning = false;

    @Nullable
    private List<LocalFontListViewModel.FontFileInfoWithMetadata> mPendingFavoritesUpdate = null;

    @Nullable
    private ProgressDialog mCurrentProgressDialog;

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


        mSearchManager = new FontSearchManager();

        mSortManager = new FontSortManager(mContext, "FAVORITES");

        mUIManager = new FontUIStateManager(mContext);

        mUIManager.setDefaultEmptyMessage(R.string.favorites_empty_message);

        setupSearchListener();
        setupSortListener();
    }

    private void setupSearchListener() {
        mSearchManager.setSearchResultListener((count, empty) -> {
            mUIManager.updateEmptyView(empty, mSearchManager.isSearchActive());
        });
    }

    private void setupSortListener() {
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
        setupViewModelObservers();
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
        mViewModel         = new ViewModelProvider(this).get(LocalFontListViewModel.class);
        mSearchViewModel   = new ViewModelProvider(this).get(SearchViewModel.class);
        mSettingsViewModel = new ViewModelProvider(this).get(SettingsViewModel.class);
    }

    private void setupViewModelObservers() {
        mViewModel.getFavoritesLiveData().observe(this, favorites -> {
            if (favorites != null) {
                if (mIsBatchOperationRunning) {
                    mPendingFavoritesUpdate = new ArrayList<>(favorites);
                    return;
                }

                mCurrentFavoritesList = new ArrayList<>(favorites);

                if (mAdapter != null) {
                    mAdapter.setAllFontsMetadata(favorites);
                }

                refreshAdapterData();
                updateMainActivityFontsCount(favorites.size());
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

        BatchOperationState.getIsProcessing().observe(this, isProcessing -> {
            mIsBatchOperationRunning = isProcessing;

            if (!isProcessing && mCurrentProgressDialog != null && mCurrentProgressDialog.isShowing()) {
                mCurrentProgressDialog.dismiss();
                mCurrentProgressDialog = null;
            }

            if (!isProcessing && mPendingFavoritesUpdate != null) {
                mCurrentFavoritesList = new ArrayList<>(mPendingFavoritesUpdate);
                if (mAdapter != null) {
                    mAdapter.setAllFontsMetadata(mPendingFavoritesUpdate);
                }
                refreshAdapterData();
                updateMainActivityFontsCount(mCurrentFavoritesList.size());
                mPendingFavoritesUpdate = null;
            }
        });

        BatchOperationState.getProgress().observe(this, progressData -> {
            if (mCurrentProgressDialog != null && mCurrentProgressDialog.isShowing() && progressData != null) {
                mCurrentProgressDialog.setMax(progressData.total);
                mCurrentProgressDialog.setProgress(progressData.current);
            }
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_favorite_font_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);

        initializeViews(view);
        setupRecyclerView();
        setupDrawerLayout();
        initializeSelectionManager();
    }

    private void initializeViews(@NonNull View view) {
        mRecyclerView = view.findViewById(R.id.font_recycler_view);

        mUIManager.setViews(
            null,                                        
            view.findViewById(R.id.main_content_layout),
            view.findViewById(R.id.empty_view),
            view.findViewById(R.id.empty_text),
            mRecyclerView
        );

        mUIManager.setEmptyTitleView(view.findViewById(R.id.empty_title));

        mUIManager.setNoResultsTextView(view.findViewById(R.id.no_results_text));

        mUIManager.setEmptyIconView(view.findViewById(R.id.empty_icon));

        mUIManager.updateUIVisibility(true);
    }

    private void setupDrawerLayout() {
        if (getActivity() != null) {
            View drawer = getActivity().findViewById(R.id.drawer_layout);
            if (drawer instanceof DrawerLayout) {
                mDrawerLayout = (DrawerLayout) drawer;
                mAppBarLayout = mDrawerLayout.getAppBarLayout();
                if (mAppBarLayout != null) {
                    mAppBarLayout.addOnOffsetChangedListener(this);
                    mUIManager.setAppBarLayout(mAppBarLayout);
                }
            }
        }
    }

    private void setupRecyclerView() {
        mRecyclerView.setLayoutManager(new LinearLayoutManager(mContext));

        mAdapter = new LocalFontListAdapter(mContext, mExecutor);

        mAdapter.setFontClickListener((fontPath, realName, fileName, ttcIndex, weightWidthLabel) -> {

            if (mFontSelectedListener != null) {
                mFontSelectedListener.onFontSelected(fontPath, realName, fileName,
                                                     ttcIndex, weightWidthLabel);
            }

            mMainHandler.postDelayed(() -> mAdapter.saveLastOpenedAndUpdate(fontPath), 400);
        });

        mAdapter.setSortChangeListener((type, asc) -> {
            mSortManager.setSortOptions(type, asc);
        });

        mAdapter.setFavoriteStatusProvider(fontPath -> true);

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
        animator.setRemoveDuration(250);
        animator.setMoveDuration(250);
        animator.setSupportsChangeAnimations(false);
        mRecyclerView.setItemAnimator(animator);
    }

    private void initializeSelectionManager() {
        if (mDrawerLayout == null || mAdapter == null || mRecyclerView == null) return;

        mSelectionManager = new LocalFontSelectionManager(
            requireActivity(),
            mDrawerLayout,
            mAdapter,
            mRecyclerView,
            null 
        );

        mSelectionManager.setFavoriteStatusChecker(position -> true);

        mAdapter.setSelectionListener(new LocalFontListAdapter.OnSelectionListener() {
            @Override
            public void onStartSelection(int position) {
                mSelectionManager.setSelecting(true);
                mSelectionManager.toggleSelection(position);
            }

            @Override
            public void onToggleSelection(int position) {
                mSelectionManager.toggleSelection(position);
            }
        });

        mSelectionManager.setActionListener(new LocalFontSelectionManager.SelectionActionListener() {
            @Override
            public void onRenameRequested(int position) {
                handleRename(position);
            }

            @Override
            public void onDeleteRequested(List<Integer> positions) {
                handleDelete(positions);
            }

            @Override
            public void onFavoriteRequested(List<Integer> positions, boolean addToFavorites) {
                handleFavoriteAction(positions, addToFavorites);
            }
        });

        requireActivity().getOnBackPressedDispatcher().addCallback(
            getViewLifecycleOwner(),
            mSelectionManager.getOnBackPressedCallback()
        );
    }


    private void handleRename(int position) {
        String path = mAdapter.getFilePath(position);
        if (path == null) return;

        FontActionDialogs.showRenameDialog(mContext, path, (oldPath, newFileName) -> {
            boolean success = mViewModel.renameFontInMemory(oldPath, newFileName);

            if (success) {
                mSelectionManager.setSelecting(false);

                mMainHandler.postDelayed(() -> {
                    String newPath = oldPath.substring(0, oldPath.lastIndexOf("/") + 1) + newFileName;
                    int newPosition = mAdapter.findPositionByPath(newPath);
                    if (newPosition != -1 && mRecyclerView != null) {
                        mRecyclerView.smoothScrollToPosition(newPosition);
                    }
                }, 300);

                Toast.makeText(mContext, R.string.success_renamed, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(mContext, R.string.error_rename_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void handleDelete(List<Integer> positions) {
        if (positions == null || positions.isEmpty()) return;

        List<String> pathsToMove = new ArrayList<>();
        for (int position : positions) {
            String path = mAdapter.getFilePath(position);
            if (path != null) pathsToMove.add(path);
        }

        if (pathsToMove.isEmpty()) return;

        int count = pathsToMove.size();

        String message = getResources().getQuantityString(
                R.plurals.dialog_move_to_trash_question, count, count);

        AlertDialog confirmDialog = new AlertDialog.Builder(mContext)
                .setMessage(message)
                .setPositiveButton(R.string.action_move_to_trash, null) 
                .setNegativeButton(R.string.action_cancel, null)
                .create();

        confirmDialog.setOnShowListener(d -> {
            confirmDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                confirmDialog.dismiss();
                showMoveToTrashProgressDialog(pathsToMove);
            });
        });

        confirmDialog.show();
    }

    private void showMoveToTrashProgressDialog(@NonNull List<String> pathsToMove) {
        int count = pathsToMove.size();

        mSelectionManager.setSelecting(false);

        mCurrentProgressDialog = new ProgressDialog(mContext);
        mCurrentProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mCurrentProgressDialog.setCancelable(false);
        mCurrentProgressDialog.setTitle(getResources().getQuantityString(
                R.plurals.progress_moving_to_trash, count, count));
        mCurrentProgressDialog.setMax(count);

        BatchOperationState.setSourceScreen(AppScreen.FAVORITES);

        mCurrentProgressDialog.setButton(
                ProgressDialog.BUTTON_NEGATIVE,
                getString(R.string.action_cancel),
                (dialog, which) -> {
                    mViewModel.cancelTrashOperation();
                    dialog.dismiss();
                });

        mCurrentProgressDialog.setButton(
                ProgressDialog.BUTTON_POSITIVE,
                getString(R.string.action_hide_dialog),
                (dialog, which) -> dialog.dismiss());

        mCurrentProgressDialog.show();

        TrashRepository.OnProgressListener progressListener = (current, total) ->
                mMainHandler.post(() -> {
                    if (mCurrentProgressDialog != null && mCurrentProgressDialog.isShowing()) {
                        mCurrentProgressDialog.setProgress(current);
                    }
                });

        mViewModel.moveFontsToTrashInMemory(pathsToMove, progressListener, () -> {
            if (mCurrentProgressDialog != null && mCurrentProgressDialog.isShowing()) {
                mCurrentProgressDialog.dismiss();
            }
            mCurrentProgressDialog = null;
        });
    }

    public void checkAndReopenProgressDialogPublic() {
        if (isHidden() || !isAdded() || mContext == null) return;

        Boolean isProcessing = BatchOperationState.getIsProcessing().getValue();
        if (!Boolean.TRUE.equals(isProcessing)) return;

        if (BatchOperationState.getSourceScreen() != AppScreen.FAVORITES) return;

        if (!BatchOperationState.consumeShouldReopenDialog()) return;

        if (mCurrentProgressDialog != null && mCurrentProgressDialog.isShowing()) return;

        reconnectToProgressDialog();
    }

    private void reconnectToProgressDialog() {
        if (!isAdded() || mContext == null) return;

        mCurrentProgressDialog = new ProgressDialog(mContext);
        mCurrentProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mCurrentProgressDialog.setCancelable(false);

        BatchOperationState.ProgressData lastProgress =
                BatchOperationState.getProgress().getValue();

        if (lastProgress != null) {
            String localTitle = getResources().getQuantityString(
                    R.plurals.progress_moving_to_trash, lastProgress.total);
            mCurrentProgressDialog.setTitle(localTitle);
            mCurrentProgressDialog.setMax(lastProgress.total);
            mCurrentProgressDialog.setProgress(lastProgress.current);
        } else {
            mCurrentProgressDialog.setTitle(getResources().getQuantityString(
                    R.plurals.progress_moving_to_trash, 1));
        }

        mCurrentProgressDialog.setButton(ProgressDialog.BUTTON_NEGATIVE,
                getString(R.string.action_cancel), (dialog, which) -> {
                    BatchOperationState.requestCancel();
                    mViewModel.cancelTrashOperation();
                    dialog.dismiss();
                });

        mCurrentProgressDialog.setButton(ProgressDialog.BUTTON_POSITIVE,
                getString(R.string.action_hide_dialog), (dialog, which) -> dialog.dismiss());

        mCurrentProgressDialog.show();
    }

    private void handleFavoriteAction(List<Integer> positions, boolean addToFavorites) {
        if (positions == null || positions.isEmpty()) return;

        List<String> paths = new ArrayList<>();
        for (int position : positions) {
            String path = mAdapter.getFilePath(position);
            if (path != null) paths.add(path);
        }

        if (paths.isEmpty()) return;

        mSelectionManager.setSelecting(false);

        mViewModel.toggleFavoritesBatch(paths, addToFavorites, () -> {
            String message = addToFavorites
                ? getString(R.string.action_favorite)    
                : getString(R.string.action_unfavorite);
            Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show();
        });
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
        mSearchViewModel.setSearchQuery(query);
    }

    public void resetFilter() {
        mSearchViewModel.deactivateSearch();
    }


    public boolean handleBackPressed() {
        if (mSelectionManager != null) return mSelectionManager.handleBackPress();
        return false;
    }


    private void refreshAdapterData() {
        if (mCurrentFavoritesList.isEmpty()) {
            mSearchManager.updateFontsList(new ArrayList<>());
            if (mAdapter != null) {
                mAdapter.updateFilteredFonts(new ArrayList<>(), mSearchManager.getCurrentSearchQuery());
                mAdapter.updateSortOptionsOnly(
                    mSortManager.getCurrentSortType(),
                    mSortManager.isSortAscending()
                );
            }
            mUIManager.updateEmptyView(true, mSearchManager.isSearchActive());
            return;
        }

        List<FontFileInfo> rawFonts = new ArrayList<>();
        for (LocalFontListViewModel.FontFileInfoWithMetadata font : mCurrentFavoritesList) {
            rawFonts.add(new FontFileInfo(
                font.getName(),
                font.getPath(),
                font.getSize(),
                font.getLastModified()
            ));
        }

        mSearchManager.updateFontsList(rawFonts);

        if (mAdapter != null) {
            mAdapter.updateFilteredFonts(
                mSearchManager.getFilteredFonts(),
                mSearchManager.getCurrentSearchQuery()
            );
            mAdapter.updateSortOptionsOnly(
                mSortManager.getCurrentSortType(),
                mSortManager.isSortAscending()
            );
        }

        mMainHandler.post(() -> mUIManager.restoreRecyclerViewState());
    }


    private void updateMainActivityFontsCount(int count) {
        if (mSelectionManager != null && mSelectionManager.isSelecting()) return;

        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).updateFontsCount(AppScreen.FAVORITES, count);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isHidden()) {
            updateMainActivityFontsCount(mCurrentFavoritesList.size());
            checkAndReopenProgressDialogPublic();
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

            if (mSelectionManager != null && mSelectionManager.isSelecting()) {
                mSelectionManager.setSelecting(false);
            }
        } else {
            if (mAdapter != null) mAdapter.smartUpdate();

            updateMainActivityFontsCount(mCurrentFavoritesList.size());

            checkAndReopenProgressDialogPublic();

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

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mSelectionManager != null) mSelectionManager.refreshActionMode();
    }

    @Override
    public void onOffsetChanged(AppBarLayout bar, int offset) {
        mUIManager.updateEmptyViewPosition(offset);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        mUIManager.saveRecyclerViewState();
        if (mUIManager.getRecyclerViewState() != null) {
            out.putParcelable("recycler_state", mUIManager.getRecyclerViewState());
        }
        out.putString("sort_type", mSortManager.getCurrentSortType().name());
        out.putBoolean("sort_asc", mSortManager.isSortAscending());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (mSelectionManager != null) {
            mSelectionManager.cleanup();
            mSelectionManager = null;
        }

        if (mAppBarLayout != null) {
            mAppBarLayout.removeOnOffsetChangedListener(this);
            mAppBarLayout = null;
        }

        mPendingFavoritesUpdate = null;
        mIsBatchOperationRunning = false;
        mCurrentProgressDialog = null;

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
