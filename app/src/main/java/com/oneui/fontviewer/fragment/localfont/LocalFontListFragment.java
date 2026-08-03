package com.oneui.fontviewer.fragment.localfont;

import android.content.Context;
import android.content.Intent;
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

import com.oneui.fontviewer.activity.AppScreen;           
import com.oneui.fontviewer.activity.MainActivity;
import com.oneui.fontviewer.fragment.trash.data.TrashRepository;
import com.oneui.fontviewer.dialog.FontActionDialogs;
import com.oneui.fontviewer.dialog.TrashActionDialogs; 
import com.oneui.fontviewer.data.entity.FontFileInfo;
import com.oneui.fontviewer.fragment.localfont.manager.LocalFontSelectionManager;
import com.oneui.fontviewer.fragment.localfont.manager.LocalFontPermissionManager;
import com.oneui.fontviewer.fragment.localfont.fontdirectory.LocalFontDirectoryPicker;
import com.oneui.fontviewer.utils.FontUIStateManager;
import com.oneui.fontviewer.widget.search.FontTextHighlighter;
import com.oneui.fontviewer.widget.search.SearchViewModel;
import com.oneui.fontviewer.widget.search.FontSearchManager;
import com.oneui.fontviewer.widget.sort.FontSortManager;
import com.oneui.fontviewer.widget.sort.SortByItemLayout;
import com.oneui.fontviewer.fragment.localfont.data.LocalFontCache;
import com.oneui.fontviewer.fragment.localfont.adapter.LocalFontListAdapter;
import com.oneui.fontviewer.utils.FontItemDecoration;
import com.oneui.fontviewer.R;
import com.oneui.fontviewer.fragment.localfont.viewmodel.LocalFontListViewModel;
import com.oneui.fontviewer.fragment.settings.viewmodel.SettingsViewModel;
import com.oneui.fontviewer.utils.notification.BatchOperationState;

public class LocalFontListFragment extends Fragment implements AppBarLayout.OnOffsetChangedListener {

    private static final String TAG = "LocalFontListFragment";

    private Context mContext;
    private RecyclerView mRecyclerView;
    private LocalFontListAdapter mAdapter;
    private OnFontSelectedListener mFontSelectedListener;
    private Handler mMainHandler;
    private ExecutorService mExecutor;
    private AppBarLayout mAppBarLayout;
    private DrawerLayout mDrawerLayout;
    private SortByItemLayout mSortBar;

    private LocalFontPermissionManager mLocalFontPermissionManager;
    private LocalFontDirectoryPicker mLocalFontDirectoryPicker;
    private FontSearchManager mSearchManager;
    private FontSortManager mSortManager;
    private FontUIStateManager mUIManager;

    private LocalFontSelectionManager mSelectionManager;

    private LocalFontListViewModel mViewModel;
    private SearchViewModel mSearchViewModel;
    private SettingsViewModel mSettingsViewModel;
    private List<LocalFontListViewModel.FontFileInfoWithMetadata> mCurrentFontsList = new ArrayList<>();

    private boolean mIsFirstLoad = true;

    private boolean mNeedsScrollRestore = false;

    private long mBackPressedTime = 0;
    private static final long BACK_PRESS_EXIT_INTERVAL = 2000;

    private Menu mMenu;


    private boolean mIsBatchOperationRunning = false;

    @Nullable
    private List<LocalFontListViewModel.FontFileInfoWithMetadata> mPendingFontsUpdate = null;

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

        mLocalFontPermissionManager      = new LocalFontPermissionManager(this);
        mLocalFontDirectoryPicker = new LocalFontDirectoryPicker(this);
        mSearchManager          = new FontSearchManager();

        mSortManager = new FontSortManager(mContext, false);

        mUIManager = new FontUIStateManager(mContext);

        setupDirectoryPickerListener();
        setupPermissionListener();
        setupSearchListener();
        setupSortListener();
    }

    private void setupDirectoryPickerListener() {
        mLocalFontDirectoryPicker.setDirectorySelectionListener(new LocalFontDirectoryPicker.DirectorySelectionListener() {
            public void onDirectorySelected(String directoryPath) {
                if (mViewModel != null) {
                    if (mAppBarLayout != null) {
                        mAppBarLayout.setExpanded(false, true);
                    }


                    mViewModel.saveFolderPath(directoryPath);
                    mViewModel.loadFontsFromPath(directoryPath);
                }
                mUIManager.updateUIVisibility(true);
                updateMainActivityFolderState(true);

                if (mMenu != null) {
                    MenuItem changeFolderItem = mMenu.findItem(R.id.action_change_folder);
                    if (changeFolderItem != null) {
                        boolean isSearchActive = mSearchViewModel != null && mSearchViewModel.isSearchActive();
                        changeFolderItem.setVisible(!isSearchActive && true);
                    }
                }
            }

            public void onDirectorySelectionCancelled() {
                Toast.makeText(mContext, "Folder selection cancelled", Toast.LENGTH_SHORT).show();
            }

            public void onDirectorySelectionError(Exception e) {
                Toast.makeText(mContext, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setupPermissionListener() {
        mLocalFontPermissionManager.setPermissionResultListener(new LocalFontPermissionManager.PermissionResultListener() {
            public void onPermissionGranted() {
                mLocalFontDirectoryPicker.openDirectoryPicker();
            }

            public void onPermissionDenied() {
                Toast.makeText(mContext, getString(R.string.font_fragment_permission_denied), Toast.LENGTH_LONG).show();
            }

            public void onManageStoragePermissionRequired() {
                Toast.makeText(mContext, "Storage permission required. Please grant access.", Toast.LENGTH_LONG).show();
            }
        });
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

        if (state != null) {
            restoreInstanceState(state);
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        this.mMenu = menu;

        menu.clear(); 
        inflater.inflate(R.menu.menu_font_list_search, menu);
        inflater.inflate(R.menu.menu_local_fonts_more, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search_fonts);
        if (getActivity() instanceof MainActivity && searchItem != null) {
            ((MainActivity) getActivity()).getSearchCoordinator().bindSearchMenuItem(searchItem);
        }

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        super.onPrepareOptionsMenu(menu);

        MenuItem changeFolderItem = menu.findItem(R.id.action_change_folder);
        if (changeFolderItem != null) {
            boolean isSearchActive = mSearchViewModel != null && mSearchViewModel.isSearchActive();

            changeFolderItem.setVisible(!isSearchActive && mViewModel.hasSavedFolder());
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_change_folder) {
            openFolderPickerPublic();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void initializeViewModels() {
        mViewModel         = new ViewModelProvider(this).get(LocalFontListViewModel.class);
        mSearchViewModel   = new ViewModelProvider(this).get(SearchViewModel.class);
        mSettingsViewModel = new ViewModelProvider(this).get(SettingsViewModel.class);
    }

    private void setupViewModelObservers() {
        mViewModel.getFontsLiveData().observe(this, fonts -> {
            if (fonts != null) {
                if (mIsBatchOperationRunning) {
                    mPendingFontsUpdate = new ArrayList<>(fonts);
                    return;
                }

                mCurrentFontsList = new ArrayList<>(fonts);

                if (mAdapter != null) {
                    mAdapter.setAllFontsMetadata(fonts);

                    mAdapter.notifyAllFavoritesChanged();
                }

                refreshAdapterData();


                updateMainActivityFontsCount(fonts.size());

                List<String> pathsToPreload = new ArrayList<>();
                for (LocalFontListViewModel.FontFileInfoWithMetadata font : fonts) {
                    pathsToPreload.add(font.getPath());
                }
                LocalFontCache.getInstance().preloadFonts(pathsToPreload);
            }
        });

        mViewModel.getIsLoadingLiveData().observe(this, isLoading -> {
            if (isLoading != null && isLoading) {
                mUIManager.showLoadingState();
                if (!isHidden()) {
                    setDrawerLocked(true); 
                }
            } else {
                mUIManager.hideLoadingState();
                refreshAdapterData();
                setDrawerLocked(false); 
                
                if (mRecyclerView != null && mRecyclerView.getVisibility() == View.VISIBLE) {
                    mRecyclerView.setAlpha(0f);
                    mRecyclerView.animate().alpha(1f).setDuration(400).start();
                }
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

            if (isProcessing
                    && !isHidden()
                    && BatchOperationState.getSourceScreen() == AppScreen.LOCAL_FONTS
                    && (mCurrentProgressDialog == null || !mCurrentProgressDialog.isShowing())) {
                reconnectToProgressDialog();
            }

            if (!isProcessing && mPendingFontsUpdate != null) {
                mCurrentFontsList = new ArrayList<>(mPendingFontsUpdate);
                if (mAdapter != null) {
                    mAdapter.setAllFontsMetadata(mPendingFontsUpdate);
                    mAdapter.notifyAllFavoritesChanged();
                }
                refreshAdapterData();


                updateMainActivityFontsCount(mCurrentFontsList.size());
                mPendingFontsUpdate = null;
            }
        });

        BatchOperationState.getProgress().observe(this, progressData -> {
            if (mCurrentProgressDialog != null
                    && mCurrentProgressDialog.isShowing()
                    && progressData != null) {
                mCurrentProgressDialog.setMax(progressData.total);
                mCurrentProgressDialog.setProgress(progressData.current);
            }
        });

        mSearchViewModel.getIsSearchActiveLiveData().observe(this, isActive -> {
            if (mMenu != null) {
                MenuItem changeFolderItem = mMenu.findItem(R.id.action_change_folder);
                if (changeFolderItem != null) {
                    if (!isActive) {
                        changeFolderItem.setVisible(mViewModel.hasSavedFolder());
                    }
                }
            }
        });

    }

    private void restoreInstanceState(@NonNull Bundle state) {
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
                Log.e(TAG, "Error restoring sort state", e);
            }
        }
        mIsFirstLoad = state.getBoolean("is_first_load", true);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        return inflater.inflate(R.layout.fragment_local_font_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);

        initializeViews(view);
        setupRecyclerView();
        setupDrawerLayout();
        setupFolderButton(view);

        initializeSelectionManager();


        if (mIsFirstLoad && mViewModel.hasSavedFolder()) {
            mViewModel.loadFonts();
            mIsFirstLoad = false;
        }
    }

    private void initializeViews(@NonNull View view) {
        mRecyclerView = view.findViewById(R.id.font_recycler_view);
        mSortBar = null;

        mUIManager.setViews(
            view.findViewById(R.id.select_folder_container),
            view.findViewById(R.id.main_content_layout),
            view.findViewById(R.id.empty_view),
            view.findViewById(R.id.empty_text),
            mRecyclerView
        );

        mUIManager.setEmptyTitleView(view.findViewById(R.id.empty_title));

        mUIManager.setNoResultsTextView(view.findViewById(R.id.no_results_text));

        mUIManager.setEmptyIconView(view.findViewById(R.id.empty_icon));

        mUIManager.setLoadingContainer(view.findViewById(R.id.loading_container));

        mUIManager.updateUIVisibility(mViewModel.hasSavedFolder());

        updateMainActivityFolderState(mViewModel.hasSavedFolder());
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

    private void setupFolderButton(@NonNull View view) {
        View folderBtn = view.findViewById(R.id.select_folder_button);
        if (folderBtn != null) {
            folderBtn.setOnClickListener(v -> {
                if (mLocalFontPermissionManager.hasRequiredPermissions()) {
                    mLocalFontDirectoryPicker.openDirectoryPicker();
                } else {
                    mLocalFontPermissionManager.requestPermissions();
                }
            });
        }
    }

    private void setupRecyclerView() {
        mRecyclerView.setLayoutManager(new LinearLayoutManager(mContext));

        mRecyclerView.setItemViewCacheSize(20);

        mAdapter = new LocalFontListAdapter(mContext, mExecutor);

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

        mAdapter.setFavoriteStatusProvider(fontPath -> {
            for (LocalFontListViewModel.FontFileInfoWithMetadata font : mCurrentFontsList) {
                if (font.getPath().equals(fontPath)) {
                    return font.isFavorite();
                }
            }
            return false;
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
        animator.setRemoveDuration(250);
        animator.setMoveDuration(500);
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
            mSortBar
        );

        mSelectionManager.setFavoriteStatusChecker(position -> {
            String path = mAdapter.getFilePath(position);
            if (path == null) return false;
            for (LocalFontListViewModel.FontFileInfoWithMetadata font : mCurrentFontsList) {
                if (font.getPath().equals(path)) {
                    return font.isFavorite();
                }
            }
            return false;
        });

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
                        mRecyclerView.scrollToPosition(newPosition);
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
                .setNegativeButton(android.R.string.cancel, null)
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

        BatchOperationState.setSourceScreen(AppScreen.LOCAL_FONTS);

        mSelectionManager.setSelecting(false);

        mCurrentProgressDialog = new ProgressDialog(mContext);
        mCurrentProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mCurrentProgressDialog.setCancelable(false);

        mCurrentProgressDialog.setTitle(getResources().getQuantityString(
                R.plurals.progress_moving_to_trash, count));
        mCurrentProgressDialog.setMax(count);

        mCurrentProgressDialog.setButton(
                ProgressDialog.BUTTON_NEGATIVE,
                getString(android.R.string.cancel),
                (dialog, which) -> {
                    mViewModel.cancelTrashOperation();
                    BatchOperationState.requestCancel();
                    dialog.dismiss();
                });

        mCurrentProgressDialog.setButton(
                ProgressDialog.BUTTON_POSITIVE,
                getString(R.string.action_hide_dialog),
                (dialog, which) -> {
                    dialog.dismiss();
                });

        TrashActionDialogs.showMoveToTrashNotification(mContext, count);

        mCurrentProgressDialog.show();

        TrashRepository.OnProgressListener progressListener = (current, total) -> {
            String progressTitle = getResources().getQuantityString(
                    R.plurals.progress_moving_to_trash, total);
            BatchOperationState.updateProgress(
                    current, total, progressTitle);

            TrashActionDialogs.updateMoveToTrashNotification(mContext, current, total);

            mMainHandler.post(() -> {
                if (mCurrentProgressDialog != null && mCurrentProgressDialog.isShowing()) {
                    mCurrentProgressDialog.setProgress(current);
                }
            });
        };

        mViewModel.moveFontsToTrashInMemory(pathsToMove, progressListener, () -> {
            if (mCurrentProgressDialog != null && mCurrentProgressDialog.isShowing()) {
                mCurrentProgressDialog.dismiss();
            }
            mCurrentProgressDialog = null;

            if (mContext != null) {
                TrashActionDialogs.dismissMoveToTrashNotification(mContext);
            }

        });
    }


    public void checkAndReopenProgressDialogPublic() {
        if (isHidden() || !isAdded() || mContext == null) return;

        Boolean isProcessing = BatchOperationState
                .getIsProcessing().getValue();
        if (!Boolean.TRUE.equals(isProcessing)) return;

        if (BatchOperationState.getSourceScreen() != AppScreen.LOCAL_FONTS) return;

        if (!BatchOperationState.consumeShouldReopenDialog()) return;

        if (mCurrentProgressDialog != null && mCurrentProgressDialog.isShowing()) return;

        Log.d(TAG, "checkAndReopenProgressDialog: reopening dialog for ongoing operation");
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

        mCurrentProgressDialog.setButton(
                ProgressDialog.BUTTON_NEGATIVE,
                getString(android.R.string.cancel),
                (dialog, which) -> {
                    BatchOperationState.requestCancel();
                    mViewModel.cancelTrashOperation();
                    dialog.dismiss();
                });

        mCurrentProgressDialog.setButton(
                ProgressDialog.BUTTON_POSITIVE,
                getString(R.string.action_hide_dialog),
                (dialog, which) -> dialog.dismiss());

        mCurrentProgressDialog.show();
        Log.d(TAG, "reconnectToProgressDialog: progress dialog reopened successfully");
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

        mMainHandler.postDelayed(() -> {
            mViewModel.toggleFavoritesBatch(paths, addToFavorites, () -> {
            });
        }, 400);
    }

    public boolean handleBackPressed() {
        if (mSelectionManager != null) return mSelectionManager.handleBackPress();
        return false;
    }

    public boolean handleExitBackPress() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - mBackPressedTime < BACK_PRESS_EXIT_INTERVAL) {
            return true;
        } else {
            mBackPressedTime = currentTime;
            if (mContext != null) {
                Toast.makeText(mContext,
                        getString(R.string.exit_on_double_back),
                        Toast.LENGTH_SHORT).show();
            }
            return false;
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mSelectionManager != null) mSelectionManager.refreshActionMode();
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
        out.putBoolean("is_first_load", mIsFirstLoad);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!isHidden()) {
            updateMainActivityFontsCount(mCurrentFontsList.size());

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
            setDrawerLocked(false);

            mUIManager.saveRecyclerViewState();

            if (mRecyclerView != null) {
                mRecyclerView.setItemAnimator(null);
            }

            mSearchViewModel.deactivateSearch();
            if (mSelectionManager != null && mSelectionManager.isSelecting()) {
                 mSelectionManager.setSelecting(false);
            }
        } else {
            Boolean isLoading = mViewModel.getIsLoadingLiveData().getValue();
            if (isLoading != null && isLoading) {
                setDrawerLocked(true);
            }

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

            checkAndReopenProgressDialogPublic();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (!mLocalFontDirectoryPicker.handleActivityResult(requestCode, resultCode, data)) {
            mLocalFontPermissionManager.handleActivityResult(requestCode);
        }
    }

    private void updateMainActivityFontsCount(int count) {
        if (mSelectionManager != null && mSelectionManager.isSelecting()) return;

        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).updateFontsCount(AppScreen.LOCAL_FONTS, count);
        }
    }


    public void openFolderPickerPublic() {
        if (mLocalFontPermissionManager.hasRequiredPermissions()) {
            mLocalFontDirectoryPicker.openDirectoryPicker();
        } else {
            mLocalFontPermissionManager.requestPermissions();
        }
    }

    private void updateMainActivityFolderState(boolean hasFolder) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setLocalFolderSelected(hasFolder);
        }
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
            mUIManager.updateEmptyView(true, mSearchManager.isSearchActive());
            return;
        }

        List<FontFileInfo> rawFonts = new ArrayList<>();
        for (LocalFontListViewModel.FontFileInfoWithMetadata font : mCurrentFontsList) {
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

        mUIManager.updateEmptyView(false, mSearchManager.isSearchActive());

        if (mNeedsScrollRestore) {
            mNeedsScrollRestore = false; 
            mMainHandler.post(() -> {
                if (isAdded() && getView() != null) {
                    mUIManager.restoreRecyclerViewState();
                }
            });
        }
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

    @Override
    public void onOffsetChanged(AppBarLayout bar, int offset) {
        mUIManager.updateEmptyViewPosition(offset);
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        mLocalFontPermissionManager.handlePermissionResult(code, results);
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

        mPendingFontsUpdate = null;
        mIsBatchOperationRunning = false;

        if (mCurrentProgressDialog != null && mCurrentProgressDialog.isShowing()) {
            try {
                mCurrentProgressDialog.dismiss();
            } catch (Exception e) {
                Log.w(TAG, "onDestroyView: failed to dismiss progress dialog", e);
            }
        }
        mCurrentProgressDialog = null;

        mMenu = null;

        mDrawerLayout = null;
        mRecyclerView = null;
        mAdapter      = null;
        mSortBar      = null;
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
    
    private void setDrawerLocked(boolean locked) {
        if (mDrawerLayout == null) return;
        
        androidx.drawerlayout.widget.DrawerLayout inner = findInnerDrawer(mDrawerLayout);
        if (inner != null) {
            inner.setDrawerLockMode(locked ? 1 : 0);
        }
        
        androidx.appcompat.widget.Toolbar toolbar = mDrawerLayout.getToolbar();
        if (toolbar != null) {
            for (int i = 0; i < toolbar.getChildCount(); i++) {
                android.view.View child = toolbar.getChildAt(i);
                if (child instanceof android.widget.ImageButton) {
                    child.setEnabled(!locked);
                    child.setClickable(!locked);
                }
            }
        }
    }

    private androidx.drawerlayout.widget.DrawerLayout findInnerDrawer(android.view.ViewGroup parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            android.view.View child = parent.getChildAt(i);
            if (child instanceof androidx.drawerlayout.widget.DrawerLayout) return (androidx.drawerlayout.widget.DrawerLayout) child;
            if (child instanceof android.view.ViewGroup) {
                androidx.drawerlayout.widget.DrawerLayout result = findInnerDrawer((android.view.ViewGroup) child);
                if (result != null) return result;
            }
        }
        return null;
    }

    }
