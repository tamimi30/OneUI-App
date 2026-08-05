package com.oneui.fontviewer.fragment.trash.data;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;

import com.oneui.fontviewer.data.dao.FontDao;
import com.oneui.fontviewer.data.database.AppDatabase;
import com.oneui.fontviewer.data.entity.FontEntity;
import com.oneui.fontviewer.fragment.trash.manager.TrashFileManager;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public class TrashRepository {

    private static final String TAG = "TrashRepository";

    public static final long TRASH_EXPIRY_MS = 30L * 24 * 60 * 60 * 1000;

    private static volatile TrashRepository INSTANCE;

    private final FontDao         fontDao;
    private final ExecutorService executorService;
    
    private TrashRepository(Context context) {
        AppDatabase database = AppDatabase.getInstance(context);
        fontDao         = database.fontDao();
        executorService = AppDatabase.databaseWriteExecutor;
    }

    public static TrashRepository getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (TrashRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new TrashRepository(context.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }


    public LiveData<List<FontEntity>> getTrashedFonts() {
        return fontDao.getTrashFonts();
    }

    public LiveData<Integer> getTrashedFontsCount() {
        return fontDao.getTrashFontsCount();
    }


    public void moveToTrashBatch(Context context,
                                  List<FontEntity> fonts,
                                  AtomicBoolean cancelFlag,
                                  OnProgressListener progressListener,
                                  OnBatchCompleteListener completeListener) {
        executorService.execute(() -> {
            try {
                int total     = fonts.size();
                int succeeded = 0;
                int failed    = 0;
                long now      = System.currentTimeMillis();

                for (int i = 0; i < total; i++) {

                    if (cancelFlag != null && cancelFlag.get()) {
                        Log.d(TAG, "moveToTrashBatch: cancelled at index " + i);
                        break;
                    }

                    FontEntity font = fonts.get(i);

                    File sourceFile = new File(font.getPath());
                    if (!sourceFile.exists()) {
                        Log.w(TAG, "Ghost file detected. Removing from DB: " + font.getPath());
                        fontDao.deleteByPath(font.getPath()); 
                        succeeded++; 
                        if (progressListener != null) progressListener.onProgress(i + 1, total);
                        continue;
                    }

                    String newTrashedPath = TrashFileManager.moveToTrash(
                            context, font.getPath());

                    if (newTrashedPath != null) {
                        try {
                            fontDao.moveToTrash(
                                    font.getPath(),   
                                    newTrashedPath,   
                                    now,              
                                    now               
                            );
                            succeeded++;
                            Log.d(TAG, "Moved to trash [" + (i + 1) + "/" + total + "]: "
                                    + font.getFileName());
                        } catch (Exception e) {
                            Log.e(TAG, "DB update failed after moving: " + font.getPath(), e);
                            fontDao.deleteByPath(font.getPath()); 
                            succeeded++;
                        }
                    } else {
                        Log.e(TAG, "Failed to move file to trash: " + font.getPath());
                        failed++;
                    }

                    if (progressListener != null) {
                        int currentProgress = i + 1;
                        progressListener.onProgress(currentProgress, total);
                    }
                }

                Log.d(TAG, "moveToTrashBatch done — succeeded: " + succeeded + ", failed: " + failed);
                if (completeListener != null) {
                    completeListener.onBatchComplete(succeeded, failed);
                }
            } catch (Exception e) {
                Log.e(TAG, "moveToTrashBatch: unexpected error", e);
                if (completeListener != null) {
                    completeListener.onBatchComplete(0, 0);
                }
            }
        });
    }


    public void restoreBatch(Context context,
                              List<FontEntity> fonts,
                              AtomicBoolean cancelFlag,
                              OnProgressListener progressListener,
                              OnBatchCompleteListener completeListener) {
        executorService.execute(() -> {
            try {
                int total     = fonts.size();
                int succeeded = 0;
                int failed    = 0;
                long now      = System.currentTimeMillis();

                for (int i = 0; i < total; i++) {

                    if (cancelFlag != null && cancelFlag.get()) {
                        Log.d(TAG, "restoreBatch: cancelled at index " + i);
                        break;
                    }

                    FontEntity font = fonts.get(i);

                    File trashedFile = new File(font.getPath());
                    if (!trashedFile.exists()) {
                        Log.w(TAG, "Ghost file detected in trash. Cleaning DB: " + font.getPath());
                        fontDao.deleteFromTrash(font.getPath()); 
                        succeeded++;
                        if (progressListener != null) progressListener.onProgress(i + 1, total);
                        continue;
                    }

                    String originalPath = font.getOriginalPath() != null
                            ? font.getOriginalPath()
                            : font.getPath(); 

                    String restoredPath = TrashFileManager.restoreFromTrash(
                            context,
                            font.getPath(),    
                            originalPath       
                    );

                    if (restoredPath != null) {
                        String newFileName = new java.io.File(restoredPath).getName();
                        try {
                            fontDao.restoreFromTrash(
                                    font.getPath(),  
                                    restoredPath,    
                                    newFileName,     
                                    now              
                            );
                            succeeded++;
                            Log.d(TAG, "Restored [" + (i + 1) + "/" + total + "]: "
                                    + font.getFileName() + " → " + restoredPath);
                        } catch (Exception e) {
                            Log.e(TAG, "DB update failed after restoring: " + font.getPath(), e);
                            fontDao.deleteFromTrash(font.getPath()); 
                            succeeded++;
                        }
                    } else {
                        Log.e(TAG, "Failed to restore file: " + font.getPath());
                        failed++;
                    }

                    if (progressListener != null) {
                        int currentProgress = i + 1;
                        progressListener.onProgress(currentProgress, total);
                    }
                }

                Log.d(TAG, "restoreBatch done — succeeded: " + succeeded + ", failed: " + failed);
                if (completeListener != null) {
                    completeListener.onBatchComplete(succeeded, failed);
                }
            } catch (Exception e) {
                Log.e(TAG, "restoreBatch: unexpected error", e);
                if (completeListener != null) {
                    completeListener.onBatchComplete(0, 0);
                }
            }
        });
    }


    public void deletePermanentlyBatch(Context context,
                                        List<FontEntity> fonts,
                                        AtomicBoolean cancelFlag,
                                        OnProgressListener progressListener,
                                        OnBatchCompleteListener completeListener) {
        executorService.execute(() -> {
            try {
                int total     = fonts.size();
                int succeeded = 0;
                int failed    = 0;

                for (int i = 0; i < total; i++) {

                    if (cancelFlag != null && cancelFlag.get()) {
                        Log.d(TAG, "deletePermanentlyBatch: cancelled at index " + i);
                        break;
                    }

                    FontEntity font = fonts.get(i);

                    boolean fileDeleted = TrashFileManager.deletePermanently(font.getPath());

                    if (fileDeleted) {
                        try {
                            fontDao.deleteByPath(font.getPath());
                            succeeded++;
                            Log.d(TAG, "Permanently deleted [" + (i + 1) + "/" + total + "]: "
                                    + font.getFileName());
                        } catch (Exception e) {
                            Log.e(TAG, "DB delete failed after file deletion: " + font.getPath(), e);
                            failed++;
                        }
                    } else {
                        Log.e(TAG, "Failed to permanently delete file: " + font.getPath());
                        failed++;
                    }

                    if (progressListener != null) {
                        int currentProgress = i + 1;
                        progressListener.onProgress(currentProgress, total);
                    }
                }

                Log.d(TAG, "deletePermanentlyBatch done — succeeded: " + succeeded
                        + ", failed: " + failed);
                if (completeListener != null) {
                    completeListener.onBatchComplete(succeeded, failed);
                }
            } catch (Exception e) {
                Log.e(TAG, "deletePermanentlyBatch: unexpected error", e);
                if (completeListener != null) {
                    completeListener.onBatchComplete(0, 0);
                }
            }
        });
    }


    public void emptyTrash(Context context,
                            AtomicBoolean cancelFlag,
                            OnProgressListener progressListener,
                            OnBatchCompleteListener completeListener) {
        executorService.execute(() -> {
            try {
                List<FontEntity> allTrashed = fontDao.getTrashFontsSync();

                if (allTrashed == null || allTrashed.isEmpty()) {
                    Log.d(TAG, "emptyTrash: trash is already empty");
                    if (completeListener != null) {
                        completeListener.onBatchComplete(0, 0);
                    }
                    return;
                }

                Log.d(TAG, "emptyTrash: deleting " + allTrashed.size() + " items");

                int total     = allTrashed.size();
                int succeeded = 0;
                int failed    = 0;

                for (int i = 0; i < total; i++) {
                    if (cancelFlag != null && cancelFlag.get()) {
                        Log.d(TAG, "emptyTrash: cancelled at index " + i);
                        break;
                    }

                    FontEntity font = allTrashed.get(i);
                    boolean fileDeleted = TrashFileManager.deletePermanently(font.getPath());

                    if (fileDeleted) {
                        try {
                            fontDao.deleteByPath(font.getPath());
                            succeeded++;
                        } catch (Exception e) {
                            Log.e(TAG, "DB delete failed during emptyTrash: " + font.getPath(), e);
                            failed++;
                        }
                    } else {
                        Log.e(TAG, "File delete failed during emptyTrash: " + font.getPath());
                        failed++;
                    }

                    if (progressListener != null) {
                        progressListener.onProgress(i + 1, total);
                    }
                }

                Log.d(TAG, "emptyTrash done — succeeded: " + succeeded + ", failed: " + failed);
                if (completeListener != null) {
                    completeListener.onBatchComplete(succeeded, failed);
                }

            } catch (Exception e) {
                Log.e(TAG, "emptyTrash: unexpected error", e);
                if (completeListener != null) {
                    completeListener.onBatchComplete(0, 0);
                }
            }
        });
    }


    public void deleteExpiredItems(OnBatchCompleteListener completeListener) {
        executorService.execute(() -> {
            try {
                long expirationThreshold = System.currentTimeMillis() - TRASH_EXPIRY_MS;

                List<FontEntity> expiredFonts =
                        fontDao.getExpiredTrashFonts(expirationThreshold);

                if (expiredFonts == null || expiredFonts.isEmpty()) {
                    Log.d(TAG, "deleteExpiredItems: no expired items found");
                    if (completeListener != null) {
                        completeListener.onBatchComplete(0, 0);
                    }
                    return;
                }

                Log.d(TAG, "deleteExpiredItems: found " + expiredFonts.size() + " expired items");

                int succeeded = 0;
                int failed    = 0;

                for (FontEntity font : expiredFonts) {
                    boolean fileDeleted = TrashFileManager.deletePermanently(font.getPath());
                    if (fileDeleted) {
                        try {
                            fontDao.deleteByPath(font.getPath());
                            succeeded++;
                        } catch (Exception e) {
                            Log.e(TAG, "DB delete failed for expired item: " + font.getPath(), e);
                            failed++;
                        }
                    } else {
                        failed++;
                    }
                }

                Log.d(TAG, "deleteExpiredItems done — deleted: " + succeeded
                        + ", failed: " + failed);
                if (completeListener != null) {
                    completeListener.onBatchComplete(succeeded, failed);
                }

            } catch (Exception e) {
                Log.e(TAG, "deleteExpiredItems: unexpected error", e);
                if (completeListener != null) {
                    completeListener.onBatchComplete(0, 0);
                }
            }
        });
    }


    public interface OnProgressListener {
        void onProgress(int current, int total);
    }

    public interface OnBatchCompleteListener {
        void onBatchComplete(int succeeded, int failed);
    }
        }
