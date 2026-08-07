package com.oneui.fontviewer.fragment.trash;

import android.content.Context;
import android.content.res.Configuration;
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
import android.widget.TextView;                          

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.AppBarLayout;

import java.util.ArrayList;
import java.util.List;

import dev.oneuiproject.oneui.dialog.ProgressDialog;
import dev.oneuiproject.oneui.layout.DrawerLayout;

import com.oneui.fontviewer.R;
import com.oneui.fontviewer.activity.AppScreen;           
import com.oneui.fontviewer.activity.MainActivity;
import com.oneui.fontviewer.data.entity.FontEntity;
import com.oneui.fontviewer.dialog.TrashActionDialogs;
import com.oneui.fontviewer.utils.FontUIStateManager;  
import com.oneui.fontviewer.fragment.trash.manager.TrashSelectionManager;
import com.oneui.fontviewer.fragment.trash.adapter.TrashListAdapter;
import com.oneui.fontviewer.fragment.trash.viewmodel.TrashViewModel;
import com.oneui.fontviewer.utils.notification.BatchOperationState;

public class TrashFragment extends Fragment implements AppBarLayout.OnOffsetChangedListener {

    private static final String TAG = "TrashFragment";

    private Context      mContext;
    private Handler      mMainHandler;

    private RecyclerView mRecyclerView;
    private View         mMainContentLayout;
    private View         mEmptyView;

    private DrawerLayout mDrawerLayout;
    private AppBarLayout mAppBarLayout;

    private TrashListAdapter       mAdapter;
    private TrashSelectionManager  mSelectionManager;
    private TrashViewModel         mViewModel;

    private FontUIStateManager mUIManager;

    @Nullable
    private ProgressDialog mCurrentProgressDialog;

    private boolean mIsDialogHidden = false;

    @Nullable
    private TrashViewModel.OperationType mCurrentOperationType;

    private boolean mIsBatchOperationRunning = false;

    @Nullable
    private List<FontEntity> mPendingTrashUpdate = null;

    @Nullable
    private Integer mPendingTrashCount = null;

    private int mTrashCount = 0;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mContext = context;

        mUIManager = new FontUIStateManager(mContext);
        mUIManager.setDefaultEmptyMessage(R.string.trash_empty_description);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);

        mMainHandler = new Handler(Looper.getMainLooper());
        mViewModel   = new ViewModelProvider(this).get(TrashViewModel.class);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        menu.clear(); 
        inflater.inflate(R.menu.menu_trash_more, menu);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        super.onPrepareOptionsMenu(menu);

        MenuItem emptyTrashItem = menu.findItem(R.id.action_empty_trash);
        if (emptyTrashItem != null) {
            boolean shouldShow = (mTrashCount > 0) && !mIsBatchOperationRunning;

            emptyTrashItem.setVisible(shouldShow);
            emptyTrashItem.setEnabled(shouldShow);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_empty_trash) {
            handleEmptyTrash();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_trash, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupDrawerLayout();
        setupRecyclerView();
        initSelectionManager();
        setupViewModelObservers();
    }


    private void initViews(@NonNull View view) {
        mRecyclerView      = view.findViewById(R.id.trash_recycler_view);
        mMainContentLayout = view.findViewById(R.id.main_content_layout);
        mEmptyView         = view.findViewById(R.id.empty_view);

        TextView emptyTitleView = view.findViewById(R.id.empty_title);
        TextView emptyTextView  = view.findViewById(R.id.empty_text);

        mUIManager.setViews(mEmptyView, emptyTextView, mRecyclerView);
        mUIManager.setEmptyTitleView(emptyTitleView);
    }

    private void setupDrawerLayout() {
        if (getActivity() == null) return;
        View drawerView = getActivity().findViewById(R.id.drawer_layout);
        if (drawerView instanceof DrawerLayout) {
            mDrawerLayout = (DrawerLayout) drawerView;
            mAppBarLayout = mDrawerLayout.getAppBarLayout();
            if (mAppBarLayout != null) {
                mAppBarLayout.addOnOffsetChangedListener(this);

                mUIManager.setAppBarLayout(mAppBarLayout);
            }
        }
    }

    private void setupRecyclerView() {
        mAdapter = new TrashListAdapter(mContext);

        mRecyclerView.setLayoutManager(new LinearLayoutManager(mContext));
        mRecyclerView.setAdapter(mAdapter);

        mRecyclerView.seslSetFillBottomEnabled(false);
        mRecyclerView.seslSetLastRoundedCorner(false);
        mRecyclerView.seslSetFastScrollerEnabled(false);
        mRecyclerView.seslSetGoToTopEnabled(true);
        mRecyclerView.seslSetSmoothScrollEnabled(true);

        setupRecyclerViewAnimator();
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


    private void initSelectionManager() {
        if (mDrawerLayout == null || mAdapter == null || mRecyclerView == null) return;

        mSelectionManager = new TrashSelectionManager(
                requireActivity(),
                mDrawerLayout,
                mAdapter,
                mRecyclerView
        );

        mAdapter.setSelectionListener(new TrashListAdapter.OnSelectionListener() {
            @Override
            public void onStartSelection(int adapterPosition) {
                mSelectionManager.setSelecting(true);
                mSelectionManager.toggleSelection(adapterPosition);
            }

            @Override
            public void onToggleSelection(int adapterPosition) {
                mSelectionManager.toggleSelection(adapterPosition);
            }
        });

        mSelectionManager.setActionListener(new TrashSelectionManager.SelectionActionListener() {

            @Override
            public void onRestoreRequested(List<FontEntity> fonts) {
                handleRestoreAction(fonts);
            }

            @Override
            public void onDeletePermanentlyRequested(List<FontEntity> fonts) {
                handleDeletePermanentlyAction(fonts);
            }
        });

        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                mSelectionManager.getOnBackPressedCallback()
        );
    }


    private void setupViewModelObservers() {

        mViewModel.getTrashedFontsLiveData().observe(getViewLifecycleOwner(), fonts -> {
            if (fonts == null) return;

            if (mIsBatchOperationRunning) {
                mPendingTrashUpdate = new ArrayList<>(fonts);
                return;
            }

            boolean isEmpty = fonts.isEmpty();
            updateEmptyState(isEmpty);
            mAdapter.submitList(isEmpty ? null : fonts);

            Log.d(TAG, "Trash list updated: " + fonts.size() + " items");
        });

        mViewModel.getTrashedFontsCountLiveData().observe(getViewLifecycleOwner(), count -> {
            if (count != null) {
                mTrashCount = count;

                if (getActivity() != null) {
                    getActivity().invalidateOptionsMenu();
                }

                if (mIsBatchOperationRunning) {
                    mPendingTrashCount = count;
                    return;
                }
                updateSubtitle(count);
            }
        });

        mViewModel.getOperationProgressLiveData().observe(getViewLifecycleOwner(), progress -> {
            if (progress == null) return;

            if (mCurrentProgressDialog != null && mCurrentProgressDialog.isShowing()) {
                mCurrentProgressDialog.setProgress(progress.current);

            } else if (mIsDialogHidden) {
                updateNotificationForOperation(
                        progress.operationType, progress.current, progress.total);
            }
        });

        mViewModel.getOperationResultLiveData().observe(getViewLifecycleOwner(), result -> {
            if (result == null) return;

            if (mCurrentProgressDialog != null && mCurrentProgressDialog.isShowing()) {
                mCurrentProgressDialog.dismiss();
            }
            mCurrentProgressDialog = null;

            dismissNotificationForOperation(result.operationType);

            mIsDialogHidden       = false;
            mCurrentOperationType = null;

            mViewModel.clearOperationResult();

            Log.d(TAG, "Operation complete: " + result.operationType
                    + " | succeeded=" + result.succeeded
                    + " | failed=" + result.failed
                    + " | cancelled=" + result.wasCancelled);
        });

        BatchOperationState.getIsProcessing().observe(
                getViewLifecycleOwner(), isProcessing -> {
            mIsBatchOperationRunning = isProcessing;

            if (getActivity() != null) {
                getActivity().invalidateOptionsMenu();
            }

            if (!isProcessing && mCurrentProgressDialog != null && mCurrentProgressDialog.isShowing()) {
                mCurrentProgressDialog.dismiss();
                mCurrentProgressDialog = null;
            }

            if (!isProcessing && mPendingTrashUpdate != null) {
                boolean isEmpty = mPendingTrashUpdate.isEmpty();
                updateEmptyState(isEmpty);
                mAdapter.submitList(isEmpty ? null : mPendingTrashUpdate);
                mPendingTrashUpdate = null;
            }

            if (!isProcessing && mPendingTrashCount != null) {
                updateSubtitle(mPendingTrashCount);
                mPendingTrashCount = null;
            }
        });

        BatchOperationState.getProgress().observe(
                getViewLifecycleOwner(), progressData -> {
            if (mCurrentProgressDialog != null && mCurrentProgressDialog.isShowing() && progressData != null) {
                mCurrentProgressDialog.setMax(progressData.total);
                mCurrentProgressDialog.setProgress(progressData.current);
            }
        });
    }


    public void handleEmptyTrash() {
        List<FontEntity> allFonts = mAdapter.getAllFonts();
        if (allFonts.isEmpty()) return;

        TrashActionDialogs.showDeletePermanentlyDialog(
                mContext,
                allFonts.size(),
                () -> showDeleteProgressAndExecute(allFonts, true)  
        );
    }

    private void handleRestoreAction(@NonNull List<FontEntity> fonts) {
        if (fonts.isEmpty()) return;

        mSelectionManager.setSelecting(false);

        showRestoreProgressAndExecute(fonts);
    }

    private void handleDeletePermanentlyAction(@NonNull List<FontEntity> fonts) {
        if (fonts.isEmpty()) return;

        TrashActionDialogs.showDeletePermanentlyDialog(
                mContext,
                fonts.size(),
                () -> {
                    mSelectionManager.setSelecting(false);
                    showDeleteProgressAndExecute(fonts, false);  
                }
        );
    }


    private void showRestoreProgressAndExecute(@NonNull List<FontEntity> fonts) {
        mCurrentOperationType = TrashViewModel.OperationType.RESTORE;
        mIsDialogHidden       = false;

        mCurrentProgressDialog = TrashActionDialogs.createRestoreProgressDialog(
                mContext,
                fonts.size(),

                () -> {
                    mViewModel.cancelCurrentOperation();
                    mIsDialogHidden = false;
                    Log.d(TAG, "Restore operation cancelled by user");
                },

                () -> {
                    mIsDialogHidden = true;
                    TrashActionDialogs.showRestoreNotification(mContext, fonts.size());
                    Log.d(TAG, "Restore dialog hidden, notification continues");
                }
        );

        TrashActionDialogs.showRestoreNotification(mContext, fonts.size());

        mCurrentProgressDialog.show();

        mViewModel.restoreFonts(fonts);
    }

    private void showDeleteProgressAndExecute(@NonNull List<FontEntity> fonts,
                                               boolean isEmptyAll) {
        mCurrentOperationType = isEmptyAll
                ? TrashViewModel.OperationType.EMPTY_TRASH
                : TrashViewModel.OperationType.DELETE_PERMANENTLY;
        mIsDialogHidden = false;

        mCurrentProgressDialog = TrashActionDialogs.createDeleteProgressDialog(
                mContext,
                fonts.size(),

                () -> {
                    mViewModel.cancelCurrentOperation();
                    mIsDialogHidden = false;
                    Log.d(TAG, "Delete operation cancelled by user");
                },

                () -> {
                    mIsDialogHidden = true;
                    TrashActionDialogs.showDeleteNotification(mContext, fonts.size());
                    Log.d(TAG, "Delete dialog hidden, notification continues");
                }
        );

        TrashActionDialogs.showDeleteNotification(mContext, fonts.size());

        mCurrentProgressDialog.show();

        if (isEmptyAll) {
            mViewModel.emptyTrash();
        } else {
            mViewModel.deletePermanently(fonts);
        }
    }


    private void updateNotificationForOperation(
            @NonNull TrashViewModel.OperationType type, int current, int total) {
        switch (type) {
            case RESTORE:
                TrashActionDialogs.updateRestoreNotification(mContext, current, total);
                break;
            case DELETE_PERMANENTLY:
            case EMPTY_TRASH:
                TrashActionDialogs.updateDeleteNotification(mContext, current, total);
                break;
            case MOVE_TO_TRASH:
                TrashActionDialogs.updateMoveToTrashNotification(mContext, current, total);
                break;
        }
    }

    private void dismissNotificationForOperation(
            @NonNull TrashViewModel.OperationType type) {
        switch (type) {
            case RESTORE:
                TrashActionDialogs.dismissRestoreNotification(mContext);
                break;
            case DELETE_PERMANENTLY:
            case EMPTY_TRASH:
                TrashActionDialogs.dismissDeleteNotification(mContext);
                break;
            case MOVE_TO_TRASH:
                TrashActionDialogs.dismissMoveToTrashNotification(mContext);
                break;
        }
    }


    public void checkAndReopenProgressDialogPublic() {
        if (isHidden() || !isAdded() || mContext == null || mViewModel == null) return;

        Boolean isProcessing = BatchOperationState.getIsProcessing().getValue();
        if (!Boolean.TRUE.equals(isProcessing)) return;

        if (BatchOperationState.getSourceScreen() != AppScreen.TRASH) return;

        if (!BatchOperationState.consumeShouldReopenDialog()) return;

        if (mCurrentProgressDialog != null && mCurrentProgressDialog.isShowing()) return;

        reconnectToProgressDialog();
    }

    private void reconnectToProgressDialog() {
        if (!isAdded() || mContext == null) return;

        mIsDialogHidden = false;

        BatchOperationState.ProgressData lastProgress =
                BatchOperationState.getProgress().getValue();

        if (lastProgress == null) return;

        mCurrentProgressDialog = new ProgressDialog(mContext);
        mCurrentProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mCurrentProgressDialog.setCancelable(false);

        String localTitle;
        if (lastProgress.operationCode == 2) {
            localTitle = getResources().getQuantityString(R.plurals.progress_restoring, lastProgress.total);
        } else if (lastProgress.operationCode == 3) {
            localTitle = getResources().getQuantityString(R.plurals.progress_deleting, lastProgress.total);
        } else {
            localTitle = getResources().getQuantityString(R.plurals.progress_moving_to_trash, lastProgress.total);
        }

        mCurrentProgressDialog.setTitle(localTitle);
        mCurrentProgressDialog.setMax(lastProgress.total);
        mCurrentProgressDialog.setProgress(lastProgress.current);

        mCurrentProgressDialog.setButton(ProgressDialog.BUTTON_NEGATIVE,
                getString(android.R.string.cancel), (dialog, which) -> {
                    BatchOperationState.requestCancel();
                    mViewModel.cancelCurrentOperation();
                    dialog.dismiss();
                });

        mCurrentProgressDialog.setButton(ProgressDialog.BUTTON_POSITIVE,
                getString(R.string.action_hide_dialog), (dialog, which) -> {
                    mIsDialogHidden = true;
                    dialog.dismiss();
                });

        mCurrentProgressDialog.show();
        Log.d(TAG, "reconnectToProgressDialog: progress dialog reopened successfully from global state");
    }


    private void updateEmptyState(boolean isEmpty) {
        if (mMainContentLayout != null) {
            mMainContentLayout.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        }

        mUIManager.updateEmptyView(isEmpty);
    }


    private void updateSubtitle(int count) {
        if (mSelectionManager != null && mSelectionManager.isSelecting()) return;

        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).updateFontsCount(AppScreen.TRASH, count);
        }
    }


    @Override
    public void onOffsetChanged(AppBarLayout bar, int offset) {
        mUIManager.updateEmptyViewPosition(offset);
    }


    public boolean handleBackPressed() {
        if (mSelectionManager != null) return mSelectionManager.handleBackPress();
        return false;
    }


    @Override
    public void onResume() {
        super.onResume();

        checkAndReopenProgressDialogPublic();

        if (!isHidden()) {
            Integer count = mViewModel.getTrashedFontsCountLiveData().getValue();
            if (count != null) updateSubtitle(count);
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);

        setMenuVisibility(!hidden);
        if (!hidden && getActivity() != null) {
            getActivity().invalidateOptionsMenu(); 
        }

        if (!hidden) {
            checkAndReopenProgressDialogPublic();
            Integer count = mViewModel.getTrashedFontsCountLiveData().getValue();
            if (count != null) updateSubtitle(count);
        } else {
            if (mSelectionManager != null && mSelectionManager.isSelecting()) {
                mSelectionManager.setSelecting(false);
            }
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mSelectionManager != null) mSelectionManager.refreshActionMode();
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

        if (mCurrentProgressDialog != null && mCurrentProgressDialog.isShowing()) {
            mCurrentProgressDialog.dismiss();
        }
        mCurrentProgressDialog = null;

        mPendingTrashUpdate = null;
        mPendingTrashCount = null;
        mIsBatchOperationRunning = false;

        mDrawerLayout = null;
        mRecyclerView = null;
        mAdapter      = null;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mContext = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mMainHandler != null) mMainHandler.removeCallbacksAndMessages(null);
    }
    }
