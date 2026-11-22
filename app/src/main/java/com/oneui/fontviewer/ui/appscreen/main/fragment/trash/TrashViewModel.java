package com.oneui.fontviewer.ui.appscreen.main.fragment.trash;

import android.app.Application;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.oneui.fontviewer.data.common.model.FontItem;
import com.oneui.fontviewer.data.trash.TrashRepository;
import com.oneui.fontviewer.service.BatchOperationState;
import com.oneui.fontviewer.service.OperationForegroundService;
import com.oneui.fontviewer.service.OperationNotifications;
import com.oneui.fontviewer.ui.appscreen.main.navigation.drawer.DrawerListItem;
import com.oneui.fontviewer.R;
import com.oneui.fontviewer.data.trash.model.OperationProgress;
import com.oneui.fontviewer.data.trash.model.OperationResult;
import com.oneui.fontviewer.data.trash.model.OperationType;

public class TrashViewModel extends AndroidViewModel {

    private static final String TAG = "TrashViewModel";
    private static final long MIN_DIALOG_DURATION_MS = 2500;
    private final TrashRepository repository;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final MutableLiveData<OperationProgress> _operationProgress = new MutableLiveData<>();
    private final MutableLiveData<OperationResult> _operationResult = new MutableLiveData<>();
    private final MutableLiveData<Boolean> _isLoading = new MutableLiveData<>(false);
    private AtomicBoolean cancelFlag = new AtomicBoolean(false);

    public TrashViewModel(@NonNull Application application) {
        super(application);
        repository = TrashRepository.getInstance(application);

        repository.deleteExpiredItems(null);
    }

    public LiveData<List<FontItem>> getTrashedFontsLiveData() {
        return repository.getTrashedFonts();
    }

    public LiveData<Integer> getTrashedFontsCountLiveData() {
        return repository.getTrashedFontsCount();
    }

    public LiveData<OperationProgress> getOperationProgressLiveData() {
        return _operationProgress;
    }

    public LiveData<OperationResult> getOperationResultLiveData() {
        return _operationResult;
    } 

    public void restoreFonts(@NonNull List<FontItem> fonts) {
        if (fonts.isEmpty()) return;

        BatchOperationState.setSourceScreen(DrawerListItem.TRASH.name());
        BatchOperationState.setProcessing(true);
        cancelFlag = new AtomicBoolean(false);
        BatchOperationState.setCancelFlag(cancelFlag);
        _isLoading.postValue(true);
        final long startTime = System.currentTimeMillis();

        String restoreTitle = getApplication().getResources()
        .getQuantityString(R.plurals.progress_restoring, fonts.size());
        Intent restoreServiceIntent = new Intent(getApplication(), OperationForegroundService.class);
        restoreServiceIntent.putExtra(OperationForegroundService.EXTRA_NOTIF_ID, OperationNotifications.NOTIF_ID_RESTORE);
        restoreServiceIntent.putExtra(OperationForegroundService.EXTRA_TITLE, restoreTitle);
        restoreServiceIntent.putExtra(OperationForegroundService.EXTRA_TOTAL, fonts.size());
        ContextCompat.startForegroundService(getApplication(), restoreServiceIntent);

        OperationNotifications.showRestoreNotification(getApplication(), fonts.size());

        repository.restoreBatch(
                getApplication(),
                fonts,
                cancelFlag,

                (current, total) -> {
                    OperationNotifications.updateRestoreNotification(getApplication(), current, total);
                    _operationProgress.postValue(
                            new OperationProgress(current, total, OperationType.RESTORE));

                    String progressTitle = getApplication().getResources()
                            .getQuantityString(R.plurals.progress_restoring, total);
                    BatchOperationState.updateProgress(current, total, progressTitle, 2);
                },

                (succeeded, failed) -> {
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    long delay = Math.max(0, MIN_DIALOG_DURATION_MS - elapsedTime);

                    mainHandler.postDelayed(() -> {
                        getApplication().stopService(
                                new Intent(getApplication(), OperationForegroundService.class));
                        OperationNotifications.dismissRestoreNotification(getApplication());
                        _isLoading.postValue(false);
                        _operationResult.postValue(new OperationResult(
                                succeeded, failed,
                                OperationType.RESTORE,
                                cancelFlag.get()
                        ));
                        BatchOperationState.setProcessing(false);
                        Log.d(TAG, "restoreFonts complete — succeeded: " + succeeded
                                + ", failed: " + failed);
                    }, delay);
                }
        );
    }

    public void deletePermanently(@NonNull List<FontItem> fonts) {
        if (fonts.isEmpty()) return;

        BatchOperationState.setSourceScreen(DrawerListItem.TRASH.name());
        BatchOperationState.setProcessing(true);

        cancelFlag = new AtomicBoolean(false);
        BatchOperationState.setCancelFlag(cancelFlag);
        _isLoading.postValue(true);

        final long startTime = System.currentTimeMillis();

        String deleteTitle = getApplication().getResources()
        .getQuantityString(R.plurals.progress_deleting, fonts.size());
        Intent deleteServiceIntent = new Intent(getApplication(), OperationForegroundService.class);
        deleteServiceIntent.putExtra(OperationForegroundService.EXTRA_NOTIF_ID, OperationNotifications.NOTIF_ID_DELETE);
        deleteServiceIntent.putExtra(OperationForegroundService.EXTRA_TITLE, deleteTitle);
        deleteServiceIntent.putExtra(OperationForegroundService.EXTRA_TOTAL, fonts.size());
        ContextCompat.startForegroundService(getApplication(), deleteServiceIntent);
        OperationNotifications.showDeleteNotification(getApplication(), fonts.size());

        repository.deletePermanentlyBatch(
                getApplication(),
                fonts,
                cancelFlag,

                (current, total) -> {
                    OperationNotifications.updateDeleteNotification(getApplication(), current, total);
                    _operationProgress.postValue(
                            new OperationProgress(current, total, OperationType.DELETE_PERMANENTLY));

                    String progressTitle = getApplication().getResources()
                            .getQuantityString(R.plurals.progress_deleting, total);
                    BatchOperationState.updateProgress(current, total, progressTitle, 3);
                },

                (succeeded, failed) -> {
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    long delay = Math.max(0, MIN_DIALOG_DURATION_MS - elapsedTime);

                    mainHandler.postDelayed(() -> {
                        getApplication().stopService(
                                new Intent(getApplication(), OperationForegroundService.class));
                        OperationNotifications.dismissDeleteNotification(getApplication());
                        _isLoading.postValue(false);
                        _operationResult.postValue(new OperationResult(
                                succeeded, failed,
                                OperationType.DELETE_PERMANENTLY,
                                cancelFlag.get()
                        ));
                        BatchOperationState.setProcessing(false);
                        Log.d(TAG, "deletePermanently complete — succeeded: " + succeeded
                                + ", failed: " + failed);
                    }, delay);
                }
        );
    }

    public void emptyTrash() {
        BatchOperationState.setSourceScreen(DrawerListItem.TRASH.name());
        BatchOperationState.setProcessing(true);

        cancelFlag = new AtomicBoolean(false);
        BatchOperationState.setCancelFlag(cancelFlag);
        _isLoading.postValue(true);

        final long startTime = System.currentTimeMillis();

        final AtomicBoolean notifShownForEmpty = new AtomicBoolean(false);

        repository.emptyTrash(
                getApplication(),
                cancelFlag,

                (current, total) -> {
                    if (notifShownForEmpty.compareAndSet(false, true)) {
                        String emptyTitle = getApplication().getResources()
                        .getQuantityString(R.plurals.progress_deleting, total);
                        Intent emptyServiceIntent = new Intent(getApplication(), OperationForegroundService.class);
                        emptyServiceIntent.putExtra(OperationForegroundService.EXTRA_NOTIF_ID, OperationNotifications.NOTIF_ID_DELETE);
                        emptyServiceIntent.putExtra(OperationForegroundService.EXTRA_TITLE, emptyTitle);
                        emptyServiceIntent.putExtra(OperationForegroundService.EXTRA_TOTAL, total);
                        ContextCompat.startForegroundService(getApplication(), emptyServiceIntent);
                        OperationNotifications.showDeleteNotification(getApplication(), total);
                    }
                    OperationNotifications.updateDeleteNotification(getApplication(), current, total);
                    _operationProgress.postValue(
                            new OperationProgress(current, total, OperationType.EMPTY_TRASH));

                    String progressTitle = getApplication().getResources()
                    .getQuantityString(R.plurals.progress_deleting, total);
                    BatchOperationState.updateProgress(current, total, progressTitle, 3);
                },

                (succeeded, failed) -> {
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    long delay = Math.max(0, MIN_DIALOG_DURATION_MS - elapsedTime);

                    mainHandler.postDelayed(() -> {
                        getApplication().stopService(
                                new Intent(getApplication(), OperationForegroundService.class));
                        OperationNotifications.dismissDeleteNotification(getApplication());
                        _isLoading.postValue(false);
                        _operationResult.postValue(new OperationResult(
                                succeeded, failed,
                                OperationType.EMPTY_TRASH,
                                cancelFlag.get()
                        ));
                        BatchOperationState.setProcessing(false);
                        Log.d(TAG, "emptyTrash complete — succeeded: " + succeeded
                                + ", failed: " + failed);
                    }, delay);
                }
        );
    }

    public void cancelCurrentOperation() {
        if (cancelFlag != null) {
            cancelFlag.set(true);
            Log.d(TAG, "Operation cancellation requested");
        }
    }

    public void clearOperationResult() {
        _operationResult.postValue(null);
    }
                                   }
