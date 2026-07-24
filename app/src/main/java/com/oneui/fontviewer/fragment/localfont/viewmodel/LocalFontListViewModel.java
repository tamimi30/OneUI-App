package com.oneui.fontviewer.fragment.localfont.viewmodel;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.oneui.fontviewer.data.entity.FontEntity;
import com.oneui.fontviewer.R;
import com.oneui.fontviewer.fragment.localfont.data.LocalFontRepository;
import com.oneui.fontviewer.fragment.trash.data.TrashRepository;   
import com.oneui.fontviewer.dialog.TrashActionDialogs;          
import com.oneui.fontviewer.fragment.localfont.manager.LocalFontPreferenceManager;
import com.oneui.fontviewer.utils.notification.BatchOperationState;
import com.oneui.fontviewer.utils.notification.OperationForegroundService;                  

public class LocalFontListViewModel extends AndroidViewModel {
    
    private static final String TAG = "LocalFontListViewModel";

    private static final long MIN_DIALOG_DURATION_MS = 2500;
    
    private final LocalFontRepository repository;
    private final TrashRepository     trashRepository; 
    private final LocalFontPreferenceManager preferenceManager;
    private final MutableLiveData<Boolean> isLoadingLiveData;
    private final MutableLiveData<String>  errorMessageLiveData;
    private final MutableLiveData<List<FontEntity>> fontsLiveData;

    private final MutableLiveData<List<FontEntity>> favoritesLiveData;

    private boolean mIsFolderSyncing = false;
    private List<FontEntity> mPendingSyncFonts = null;

    private AtomicBoolean trashCancelFlag = new AtomicBoolean(false);
    

    public static class FontFileInfoWithMetadata {
        private final String name;
        private final String path;
        private final long size;
        private final long lastModified;
        private final String realName;
        private final String weightWidthLabel;
        private final boolean isFavorite;
        
        public FontFileInfoWithMetadata(FontEntity entity) {
            this.name             = entity.getFileName();
            this.path             = entity.getPath();
            this.size             = entity.getSize();
            this.lastModified     = entity.getLastModified();
            this.realName         = entity.getRealName();
            this.weightWidthLabel = entity.getWeightWidthLabel(); 
            this.isFavorite       = entity.isFavorite();          
        }
        
        public FontFileInfoWithMetadata(String name, String path, long size,
                                        long lastModified, String realName,
                                        String weightWidthLabel, boolean isFavorite) {
            this.name             = name;
            this.path             = path;
            this.size             = size;
            this.lastModified     = lastModified;
            this.realName         = realName;
            this.weightWidthLabel = weightWidthLabel; 
            this.isFavorite       = isFavorite;       
        }
        
        public String  getName()             { return name; }
        public String  getPath()             { return path; }
        public long    getSize()             { return size; }
        public long    getLastModified()     { return lastModified; }
        public String  getRealName()         { return realName; }
        public String  getWeightWidthLabel() { return weightWidthLabel; }
        public boolean isFavorite()          { return isFavorite; }
        
        private String getDisplayName() {
            String displayName = name;
            if (displayName.toLowerCase().endsWith(".ttf") || 
                displayName.toLowerCase().endsWith(".otf") ||
                displayName.toLowerCase().endsWith(".ttc")) {
                int extensionPos = displayName.lastIndexOf('.');
                if (extensionPos > 0) {
                    displayName = displayName.substring(0, extensionPos);
                }
            }
            return displayName;
        }
    }
    
    public LocalFontListViewModel(@NonNull Application application) {
        super(application);
        
        repository      = LocalFontRepository.getInstance(application);
        trashRepository = TrashRepository.getInstance(application); 
        preferenceManager = new LocalFontPreferenceManager(application);
        isLoadingLiveData    = new MutableLiveData<>(false);
        errorMessageLiveData = new MutableLiveData<>();
        
        fontsLiveData = new MutableLiveData<>(new ArrayList<>());

        favoritesLiveData = new MutableLiveData<>(new ArrayList<>());
        
        repository.getLocalFonts().observeForever(entities -> {
            if (entities != null) {
                if (mIsFolderSyncing) {
                    mPendingSyncFonts = entities;
                } else {
                    fontsLiveData.postValue(entities);
                }
            }
        });

        repository.getFavoriteFonts().observeForever(entities -> {
            if (entities != null) {
                favoritesLiveData.postValue(entities);
            }
        });
    }
    
    public LiveData<List<FontFileInfoWithMetadata>> getFontsLiveData() {
        return Transformations.map(fontsLiveData, entities -> {
            if (entities == null) {
                return new ArrayList<>();
            }
            
            List<FontFileInfoWithMetadata> result = new ArrayList<>();
            for (FontEntity entity : entities) {
                result.add(new FontFileInfoWithMetadata(entity));
            }
            return result;
        });
    }


    public LiveData<List<FontFileInfoWithMetadata>> getFavoritesLiveData() {
        return Transformations.map(favoritesLiveData, entities -> {
            if (entities == null) {
                return new ArrayList<>();
            }

            List<FontFileInfoWithMetadata> result = new ArrayList<>();
            for (FontEntity entity : entities) {
                result.add(new FontFileInfoWithMetadata(entity));
            }
            return result;
        });
    }

    public LiveData<Integer> getFavoritesCountLiveData() {
        return repository.getFavoriteFontsCount();
    }

    public void toggleFavorite(String path, boolean isFavorite) {
        repository.updateFavoriteStatus(path, isFavorite, success -> {
            if (success) {
                Log.d(TAG, "★ Favorite toggled: " + path + " → " + isFavorite);
            } else {
                Log.w(TAG, "Failed to toggle favorite: " + path);
            }
        });
    }

    public void toggleFavoritesBatch(List<String> paths, boolean isFavorite, Runnable onSuccess) {
        if (paths == null || paths.isEmpty()) return;

        BatchOperationState.setProcessing(true);

        repository.updateFavoriteStatusBatch(paths, isFavorite, success -> {
            if (success) {
                Log.d(TAG, "★ Batch favorite toggled: " + paths.size() + " fonts → " + isFavorite);
                if (onSuccess != null) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        onSuccess.run();
                        BatchOperationState.setProcessing(false);
                    });
                } else {
                    BatchOperationState.setProcessing(false);
                }
            } else {
                Log.w(TAG, "Failed to batch toggle favorites");
                BatchOperationState.setProcessing(false);
            }
        });
    }

    public boolean shouldAddToFavorites(List<String> selectedPaths) {
        if (selectedPaths == null || selectedPaths.isEmpty()) return true;

        List<FontEntity> currentList = fontsLiveData.getValue();
        if (currentList == null) return true;

        int favoriteCount = 0;
        for (FontEntity entity : currentList) {
            if (selectedPaths.contains(entity.getPath()) && entity.isFavorite()) {
                favoriteCount++;
            }
        }

        return favoriteCount < selectedPaths.size();
    }

    public boolean shouldAddToFavoritesFromFavoritesList(List<String> selectedPaths) {
        return false;
    }

    public LiveData<List<FontEntity>> searchFavorites(String query) {
        if (query == null || query.trim().isEmpty()) {
            return repository.getFavoriteFonts();
        }
        return repository.searchFavoriteFonts(query.trim());
    }

    public LiveData<List<FontEntity>> getSortedFavorites(LocalFontRepository.SortType sortType,
                                                          boolean ascending) {
        if (sortType == null) {
            return repository.getFavoriteFonts();
        }

        switch (sortType) {
            case DATE:
                return repository.getFavoritesSortedByDate(ascending);
            case SIZE:
                return repository.getFavoritesSortedBySize(ascending);
            case NAME:
            default:
                return repository.getFavoritesSortedByName(ascending);
        }
    }

    
    public boolean renameFontInMemory(String oldPath, String newFileName) {
        File oldFile = new File(oldPath);
        
        if (!oldFile.exists()) {
            errorMessageLiveData.postValue("الملف غير موجود");
            return false;
        }
        
        File parentDir = oldFile.getParentFile();
        if (parentDir == null) {
            errorMessageLiveData.postValue("خطأ في المسار");
            return false;
        }
        
        File newFile = new File(parentDir, newFileName);
        
        if (newFile.exists()) {
            errorMessageLiveData.postValue("الاسم موجود بالفعل");
            return false;
        }
        
        if (!oldFile.renameTo(newFile)) {
            errorMessageLiveData.postValue("فشلت إعادة التسمية");
            return false;
        }
        
        List<FontEntity> currentList = fontsLiveData.getValue();
        if (currentList != null) {
            List<FontEntity> updatedList = new ArrayList<>(currentList);
            
            for (int i = 0; i < updatedList.size(); i++) {
                FontEntity entity = updatedList.get(i);
                if (entity.getPath().equals(oldPath)) {
                    FontEntity updatedEntity = new FontEntity(
                        newFile.getAbsolutePath(),
                        newFileName
                    );
                    updatedEntity.setSize(entity.getSize());
                    updatedEntity.setLastModified(newFile.lastModified());
                    updatedEntity.setRealName(entity.getRealName());
                    updatedEntity.setAccessCount(entity.getAccessCount());
                    updatedEntity.setLastAccessTime(entity.getLastAccessTime());
                    updatedEntity.setWeightWidthLabel(entity.getWeightWidthLabel());
                    updatedEntity.setFavorite(entity.isFavorite());
                    
                    updatedList.set(i, updatedEntity);
                    break;
                }
            }
            
            fontsLiveData.postValue(updatedList);
        }

        List<FontEntity> currentFavorites = favoritesLiveData.getValue();
        if (currentFavorites != null) {
            List<FontEntity> updatedFavorites = new ArrayList<>(currentFavorites);

            for (int i = 0; i < updatedFavorites.size(); i++) {
                FontEntity entity = updatedFavorites.get(i);
                if (entity.getPath().equals(oldPath)) {
                    FontEntity updatedEntity = new FontEntity(
                        newFile.getAbsolutePath(),
                        newFileName
                    );
                    updatedEntity.setSize(entity.getSize());
                    updatedEntity.setLastModified(newFile.lastModified());
                    updatedEntity.setRealName(entity.getRealName());
                    updatedEntity.setAccessCount(entity.getAccessCount());
                    updatedEntity.setLastAccessTime(entity.getLastAccessTime());
                    updatedEntity.setWeightWidthLabel(entity.getWeightWidthLabel());
                    updatedEntity.setFavorite(true); 
                    
                    updatedFavorites.set(i, updatedEntity);
                    break;
                }
            }

            favoritesLiveData.postValue(updatedFavorites);
        }
        
        repository.updatePath(oldPath, newFile.getAbsolutePath(), newFileName);
        
        Log.d(TAG, "Font renamed in memory: " + oldPath + " -> " + newFile.getAbsolutePath());
        
        return true;
    }


    public void moveFontsToTrashInMemory(@NonNull List<String> paths,
                                          TrashRepository.OnProgressListener progressListener,
                                          Runnable onComplete) {
        if (paths.isEmpty()) return;

        List<FontEntity> currentList = fontsLiveData.getValue();
        if (currentList == null || currentList.isEmpty()) {
            Log.w(TAG, "moveFontsToTrashInMemory: font list is empty");
            if (onComplete != null) new Handler(Looper.getMainLooper()).post(onComplete);
            return;
        }

        List<FontEntity> fontsToMove = new ArrayList<>();
        for (FontEntity entity : currentList) {
            if (paths.contains(entity.getPath())) {
                fontsToMove.add(entity);
            }
        }

        if (fontsToMove.isEmpty()) {
            Log.w(TAG, "moveFontsToTrashInMemory: no matching entities found for given paths");
            if (onComplete != null) new Handler(Looper.getMainLooper()).post(onComplete);
            return;
        }

        int sourceIndex = BatchOperationState.getSourceFragmentIndex();
        if (sourceIndex == -1) sourceIndex = 2; 
        BatchOperationState.setProcessing(true, sourceIndex);

        trashCancelFlag = new AtomicBoolean(false);
        BatchOperationState.setCancelFlag(trashCancelFlag);

        final long startTime = System.currentTimeMillis();

        String movingTitle = getApplication().getResources()
                .getQuantityString(R.plurals.progress_moving_to_trash, fontsToMove.size());
        android.content.Intent moveServiceIntent = new android.content.Intent(
                getApplication(), OperationForegroundService.class);
        moveServiceIntent.putExtra(OperationForegroundService.EXTRA_NOTIF_ID,
                TrashActionDialogs.NOTIF_ID_MOVE);
        moveServiceIntent.putExtra(OperationForegroundService.EXTRA_TITLE, movingTitle);
        moveServiceIntent.putExtra(OperationForegroundService.EXTRA_TOTAL, fontsToMove.size());
        moveServiceIntent.putExtra(OperationForegroundService.EXTRA_SOURCE_FRAGMENT, sourceIndex);
        ContextCompat.startForegroundService(getApplication(), moveServiceIntent);

        final int totalFonts = fontsToMove.size();
        trashRepository.moveToTrashBatch(
                getApplication(),
                fontsToMove,
                trashCancelFlag,

                (current, total) -> {
                    TrashActionDialogs.updateMoveToTrashNotification(getApplication(), current, total);

                    String progressTitle = getApplication().getResources()
                            .getQuantityString(
                                    R.plurals.progress_moving_to_trash, total);
                    BatchOperationState.updateProgress(current, total, progressTitle);

                    if (progressListener != null) {
                        try {
                            progressListener.onProgress(current, total);
                        } catch (Exception e) {
                            Log.w(TAG, "progressListener.onProgress() failed"
                                    + " (fragment may be detached): " + e.getMessage());
                        }
                    }
                },

                (succeeded, failed) -> {
                    Log.d(TAG, "moveFontsToTrashInMemory done"
                            + " — succeeded: " + succeeded
                            + ", failed: " + failed
                            + ", cancelled: " + trashCancelFlag.get());


                    long elapsedTime = System.currentTimeMillis() - startTime;
                    long delay = Math.max(0, MIN_DIALOG_DURATION_MS - elapsedTime);

                    if (onComplete != null) {
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            getApplication().stopService(new android.content.Intent(
                                    getApplication(), OperationForegroundService.class));
                            TrashActionDialogs.dismissMoveToTrashNotification(getApplication());

                            onComplete.run(); 
                            BatchOperationState.setProcessing(false);
                        }, delay);
                    } else {
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            getApplication().stopService(new android.content.Intent(
                                    getApplication(), OperationForegroundService.class));
                            TrashActionDialogs.dismissMoveToTrashNotification(getApplication());
                            BatchOperationState.setProcessing(false);
                        }, delay);
                    }
                }
        );
    }

    public void cancelTrashOperation() {
        if (trashCancelFlag != null) {
            trashCancelFlag.set(true);
            Log.d(TAG, "cancelTrashOperation: cancellation requested");
        }
    }

    
    public int findFontPositionByPath(String path) {
        List<FontEntity> currentList = fontsLiveData.getValue();
        if (currentList == null || path == null) {
            return -1;
        }
        
        for (int i = 0; i < currentList.size(); i++) {
            if (currentList.get(i).getPath().equals(path)) {
                return i;
            }
        }
        
        return -1;
    }

    public int findFavoritePositionByPath(String path) {
        List<FontEntity> currentFavorites = favoritesLiveData.getValue();
        if (currentFavorites == null || path == null) {
            return -1;
        }

        for (int i = 0; i < currentFavorites.size(); i++) {
            if (currentFavorites.get(i).getPath().equals(path)) {
                return i;
            }
        }

        return -1;
    }
    
    public LiveData<Integer> getFontsCountLiveData() {
        return repository.getLocalFontsCount();
    }
    
    public LiveData<Boolean> getIsLoadingLiveData() {
        return isLoadingLiveData;
    }
    
    public LiveData<String> getErrorMessageLiveData() {
        return errorMessageLiveData;
    }
    
    public void loadFonts() {
        String folderPath = preferenceManager.getFontFolderPath();
        if (folderPath == null) {
            Log.w(TAG, "No folder path saved");
            return;
        }
        
        loadFontsFromPath(folderPath);
    }
    
    public void loadFontsFromPath(String folderPath) {
        if (folderPath == null || folderPath.isEmpty()) {
            return;
        }

        if (Boolean.TRUE.equals(BatchOperationState.getIsProcessing().getValue())) {
            Log.w(TAG, "Skipping load and sync: A batch operation is currently running.");
            return;
        }

        isLoadingLiveData.postValue(true);

        mIsFolderSyncing = true;

        final long startTime = System.currentTimeMillis();
        
        repository.loadAndSyncLocalFonts(folderPath, new LocalFontRepository.OnSyncCompleteListener() {
            @Override
            public void onSyncComplete(int added, int updated, int deleted) {
                String message = String.format("Synced: %d added, %d updated, %d deleted",
                    added, updated, deleted);
                Log.d(TAG, message);
            }

            @Override
            public void onFullExtractionComplete() {
                long elapsedTime = System.currentTimeMillis() - startTime;
                long delay = Math.max(0, 2500 - elapsedTime);

                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    mIsFolderSyncing = false;

                    if (mPendingSyncFonts != null) {
                        fontsLiveData.postValue(mPendingSyncFonts);
                        mPendingSyncFonts = null;
                    } else {
                        List<FontEntity> currentFonts = fontsLiveData.getValue();
                        fontsLiveData.postValue(currentFonts != null ? currentFonts : new java.util.ArrayList<>());
                    }

                    isLoadingLiveData.postValue(false);
                }, delay);
            }
        });
    }
    
    public LiveData<List<FontEntity>> searchFonts(String query) {
        if (query == null || query.trim().isEmpty()) {
            return repository.getLocalFonts();
        }
        return repository.searchFonts(false, query.trim());
    }
    
    public LiveData<List<FontEntity>> getSortedFonts(LocalFontRepository.SortType sortType, boolean ascending) {
        if (sortType == null) {
            return repository.getLocalFonts();
        }
        
        switch (sortType) {
            case DATE:
                return ascending ? repository.getFontsSortedByDate(false, true)
                                : repository.getFontsSortedByDate(false, false);
            case SIZE:
                return ascending ? repository.getFontsSortedBySize(false, true)
                                : repository.getFontsSortedBySize(false, false);
            case NAME:
            default:
                return ascending ? repository.getFontsSortedByName(false, true)
                                : repository.getFontsSortedByName(false, false);
        }
    }
    
    public void recordFontAccess(String fontPath) {
        if (fontPath != null && !fontPath.isEmpty()) {
            repository.recordAccess(fontPath);
        }
    }
    
    public void updateFontRealName(String fontPath, String realName) {
        if (fontPath != null && realName != null) {
            repository.updateRealName(fontPath, realName);
        }
    }
    
    public void updateFontCacheStatus(String fontPath, boolean isCached) {
        if (fontPath != null) {
            repository.updateCacheStatus(fontPath, isCached);
        }
    }
    
    public void refreshFonts() {
        String folderPath = preferenceManager.getFontFolderPath();
        if (folderPath != null) {
            loadFontsFromPath(folderPath);
        }
    }
    
    public void saveFolderPath(String folderPath) {
        if (folderPath != null && !folderPath.isEmpty()) {
            preferenceManager.saveFontFolderPath(folderPath);
        }
    }
    
    public String getSavedFolderPath() {
        return preferenceManager.getFontFolderPath();
    }
    
    public boolean hasSavedFolder() {
        return preferenceManager.hasFontFolderPath();
    }
    
    public LiveData<FontEntity> getFontByPath(String fontPath) {
        if (fontPath == null || fontPath.isEmpty()) {
            return new MutableLiveData<>(null);
        }
        return repository.getFontByPath(fontPath);
    }
    
    public void deleteFont(FontEntity font, LocalFontRepository.OnCompleteListener listener) {
        if (font != null) {
            repository.delete(font, listener);
        }
    }
    
    public void deleteAllLocalFonts(LocalFontRepository.OnCompleteListener listener) {
        repository.deleteByPath(null, success -> {
            if (listener != null) {
                listener.onComplete(success);
            }
            if (success) {
                Log.d(TAG, "All local fonts deleted");
            }
        });
    }
                }
