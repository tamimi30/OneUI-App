package com.oneui.fontviewer.ui.appscreen.main.fragment.localfont;

import android.app.Application;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;

import com.oneui.fontviewer.data.common.model.FontItem;
import com.oneui.fontviewer.R;
import com.oneui.fontviewer.data.localfont.LocalFontCache;
import com.oneui.fontviewer.data.localfont.LocalFontRepository;
import com.oneui.fontviewer.data.localfont.model.FontRenameResult;
import com.oneui.fontviewer.data.localfont.file.LocalFontDirectory;
import com.oneui.fontviewer.data.localfont.file.FontFileManager;
import com.oneui.fontviewer.data.trash.TrashRepository;
import com.oneui.fontviewer.data.localfont.LocalFontPreferenceManager;
import com.oneui.fontviewer.data.settings.SettingsRepository;
import com.oneui.fontviewer.service.BatchOperationState;
import com.oneui.fontviewer.service.OperationForegroundService;
import com.oneui.fontviewer.service.OperationNotifications;
import com.oneui.fontviewer.ui.appscreen.main.navigation.drawer.DrawerListItem;
import com.oneui.fontviewer.data.localfont.model.FontFileInfoWithMetadata;

public class LocalFontListViewModel extends AndroidViewModel {
    
    private static final String TAG = "LocalFontListViewModel";

    private static final long MIN_DIALOG_DURATION_MS = 2500;
    
    private final LocalFontRepository repository;
    private final TrashRepository     trashRepository; 
    private final LocalFontPreferenceManager preferenceManager;
    // يُنشأ عند أول استخدام لقائمة المفضلة: يحفظ آخر خط مفتوح فيها منفصلاً عن قائمة الخطوط الأساسية
    private LocalFontPreferenceManager favoritesPreferenceManager;
    private final MutableLiveData<Boolean> isLoadingLiveData;
    private final MutableLiveData<String>  errorMessageLiveData;
    private final MutableLiveData<List<FontItem>> fontsLiveData;

    private final MutableLiveData<List<FontItem>> favoritesLiveData;

    private boolean mIsFolderSyncing = false;
    private List<FontItem> mPendingSyncFonts = null;

    // أقل مدة بين فحصين صامتين متتاليين للمجلد، حتى لا يتكرر الفحص عند التنقل السريع بين التطبيقات
    private static final long MIN_SILENT_REFRESH_INTERVAL_MS = 2000;
    private long mLastSilentRefreshTime = 0;

    private AtomicBoolean trashCancelFlag = new AtomicBoolean(false);

    // خيط خلفي مخصص لتحميل معاينات الخطوط قبل نشر القائمة للواجهة
    private final ExecutorService fontPreloadExecutor = Executors.newSingleThreadExecutor();

    // نحتفظ بمرجع الـ LiveData الأصلية ومرجع الـ Observer لكل منهما
    // حتى نقدر نفك ربطهما صح في onCleared() ونمنعهما من الاستمرار بالعمل بعد موت الـ ViewModel
    private final LiveData<List<FontItem>> mLocalFontsSource;
    private final LiveData<List<FontItem>> mFavoriteFontsSource;
    private final Observer<List<FontItem>> mLocalFontsObserver;
    private final Observer<List<FontItem>> mFavoriteFontsObserver;
    

    public LocalFontListViewModel(@NonNull Application application) {
        super(application);
        
        repository      = LocalFontRepository.getInstance(application);
        trashRepository = TrashRepository.getInstance(application); 
        preferenceManager = new LocalFontPreferenceManager(application);
        isLoadingLiveData    = new MutableLiveData<>(false);
        errorMessageLiveData = new MutableLiveData<>();
        
        fontsLiveData = new MutableLiveData<>();

        // بلا قيمة ابتدائية: القيمة الفارغة المبدئية كانت تصل للواجهة قبل قائمة المفضلة الحقيقية،
        // فتعرض رسالة "لا توجد خطوط في المفضلة بعد" بالخطأ
        favoritesLiveData = new MutableLiveData<>();
        
        mLocalFontsSource = repository.getLocalFonts();
        mLocalFontsObserver = items -> {
            if (items != null) {
                if (mIsFolderSyncing) {
                    mPendingSyncFonts = items;
                } else {
                    publishFontsAfterPreload(items, null);
                }
            }
        };
        mLocalFontsSource.observeForever(mLocalFontsObserver);

        mFavoriteFontsSource = repository.getFavoriteFonts();
        mFavoriteFontsObserver = items -> {
            if (items != null) {
                favoritesLiveData.postValue(items);
            }
        };
        mFavoriteFontsSource.observeForever(mFavoriteFontsObserver);
    }

    /**
     * يحمّل معاينات الخطوط لكل عناصر القائمة داخل LocalFontCache على خيط
     * خلفي، ثم يُرسل القائمة لواجهة المستخدم فقط بعد اكتمال التحميل.
     */
    private void publishFontsAfterPreload(List<FontItem> items, @Nullable Runnable onPublished) {
        if (fontPreloadExecutor.isShutdown()) {
            // الـ ViewModel اتقفلت (onCleared) بالفعل — منحاولش ننفذ حاجة على Executor مقفول
            Log.w(TAG, "publishFontsAfterPreload: executor is shut down, skipping");
            return;
        }
        try {
            fontPreloadExecutor.execute(() -> {
                List<String> paths = new ArrayList<>();
                for (FontItem item : items) {
                    paths.add(item.getPath());
                }
                LocalFontCache.getInstance().preloadFontsBlocking(paths);
                fontsLiveData.postValue(items);
                if (onPublished != null) {
                    onPublished.run();
                }
            });
        } catch (RejectedExecutionException e) {
            Log.w(TAG, "publishFontsAfterPreload: task rejected, executor already shut down", e);
        }
    }

    public LiveData<List<FontFileInfoWithMetadata>> getFontsLiveData() {
        return Transformations.map(fontsLiveData, items -> {
            if (items == null) {
                return new ArrayList<>();
            }
            
            List<FontFileInfoWithMetadata> result = new ArrayList<>();
            for (FontItem item : items) {
                result.add(new FontFileInfoWithMetadata(item));
            }
            return result;
        });
    }


    public LiveData<List<FontFileInfoWithMetadata>> getFavoritesLiveData() {
        return Transformations.map(favoritesLiveData, items -> {
            if (items == null) {
                return new ArrayList<>();
            }

            List<FontFileInfoWithMetadata> result = new ArrayList<>();
            for (FontItem item : items) {
                result.add(new FontFileInfoWithMetadata(item));
            }
            return result;
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

    public boolean renameFontInMemory(String oldPath, String newFileName) {
        FontRenameResult renameResult = FontFileManager.renameFile(oldPath, newFileName);
        
        if (!renameResult.isSuccess()) {
            errorMessageLiveData.postValue(renameResult.getErrorMessage());
            return false;
        }
        
        String newPath = renameResult.getNewPath();
        long newLastModified = renameResult.getNewLastModified();
        
        List<FontItem> currentList = fontsLiveData.getValue();
        if (currentList != null) {
            List<FontItem> updatedList = new ArrayList<>(currentList);
            
            for (int i = 0; i < updatedList.size(); i++) {
                FontItem item = updatedList.get(i);
                if (item.getPath().equals(oldPath)) {
                    FontItem updatedItem = new FontItem(
                        newPath,
                        newFileName
                    );
                    updatedItem.setSize(item.getSize());
                    updatedItem.setLastModified(newLastModified);
                    updatedItem.setRealName(item.getRealName());
                    updatedItem.setAccessCount(item.getAccessCount());
                    updatedItem.setLastAccessTime(item.getLastAccessTime());
                    updatedItem.setWeightWidthLabel(item.getWeightWidthLabel());
                    updatedItem.setFavorite(item.isFavorite());
                    
                    updatedList.set(i, updatedItem);
                    break;
                }
            }
            
            fontsLiveData.postValue(updatedList);
        }

        List<FontItem> currentFavorites = favoritesLiveData.getValue();
        if (currentFavorites != null) {
            List<FontItem> updatedFavorites = new ArrayList<>(currentFavorites);

            for (int i = 0; i < updatedFavorites.size(); i++) {
                FontItem item = updatedFavorites.get(i);
                if (item.getPath().equals(oldPath)) {
                    FontItem updatedItem = new FontItem(
                        newPath,
                        newFileName
                    );
                    updatedItem.setSize(item.getSize());
                    updatedItem.setLastModified(newLastModified);
                    updatedItem.setRealName(item.getRealName());
                    updatedItem.setAccessCount(item.getAccessCount());
                    updatedItem.setLastAccessTime(item.getLastAccessTime());
                    updatedItem.setWeightWidthLabel(item.getWeightWidthLabel());
                    updatedItem.setFavorite(true); 
                    
                    updatedFavorites.set(i, updatedItem);
                    break;
                }
            }

            favoritesLiveData.postValue(updatedFavorites);
        }
        
        repository.updatePath(oldPath, newPath, newFileName);
        
        Log.d(TAG, "Font renamed in memory: " + oldPath + " -> " + newPath);
        
        return true;
    }


    /** يستقبل تقدّم نقل الخطوط الى سلة المحذوفات (عدد المنجَز من الإجمالي). */
    public interface OnTrashProgressListener {
        void onProgress(int current, int total);
    }

    public void moveFontsToTrashInMemory(@NonNull List<String> paths,
                                          OnTrashProgressListener progressListener,
                                          Runnable onComplete) {
        if (paths.isEmpty()) return;

        List<FontItem> currentList = fontsLiveData.getValue();
        if (currentList == null || currentList.isEmpty()) {
            Log.w(TAG, "moveFontsToTrashInMemory: font list is empty");
            if (onComplete != null) new Handler(Looper.getMainLooper()).post(onComplete);
            return;
        }

        List<FontItem> fontsToMove = new ArrayList<>();
        for (FontItem item : currentList) {
            if (paths.contains(item.getPath())) {
                fontsToMove.add(item);
            }
        }

        if (fontsToMove.isEmpty()) {
            Log.w(TAG, "moveFontsToTrashInMemory: no matching items found for given paths");
            if (onComplete != null) new Handler(Looper.getMainLooper()).post(onComplete);
            return;
        }

        if (BatchOperationState.getSourceScreen() == null) {
            BatchOperationState.setSourceScreen(DrawerListItem.LOCAL_FONTS.name());
        }
        BatchOperationState.setProcessing(true);

        trashCancelFlag = new AtomicBoolean(false);
        BatchOperationState.setCancelFlag(trashCancelFlag);

        final long startTime = System.currentTimeMillis();

        String movingTitle = getApplication().getResources()
                .getQuantityString(R.plurals.progress_moving_to_trash, fontsToMove.size());
        android.content.Intent moveServiceIntent = new android.content.Intent(
                getApplication(), OperationForegroundService.class);
        moveServiceIntent.putExtra(OperationForegroundService.EXTRA_NOTIF_ID,
                OperationNotifications.NOTIF_ID_MOVE);
        moveServiceIntent.putExtra(OperationForegroundService.EXTRA_TITLE, movingTitle);
        moveServiceIntent.putExtra(OperationForegroundService.EXTRA_TOTAL, fontsToMove.size());
        ContextCompat.startForegroundService(getApplication(), moveServiceIntent);
 
        trashRepository.moveToTrashBatch(
                getApplication(),
                fontsToMove,
                trashCancelFlag,

                (current, total) -> {
                    OperationNotifications.updateMoveToTrashNotification(getApplication(), current, total);

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
                            OperationNotifications.dismissMoveToTrashNotification(getApplication());

                            onComplete.run(); 
                            BatchOperationState.setProcessing(false);
                        }, delay);
                    } else {
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            getApplication().stopService(new android.content.Intent(
                                    getApplication(), OperationForegroundService.class));
                            OperationNotifications.dismissMoveToTrashNotification(getApplication());
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
    
    public LiveData<Boolean> getIsLoadingLiveData() {
        return isLoadingLiveData;
    }
    
    public void loadFonts() {
        String folderPath = preferenceManager.getFontFolderPath();
        if (folderPath == null) {
            Log.w(TAG, "No folder path saved");
            return;
        }
        
        loadFontsFromPath(folderPath, false);
    }
    
    public void loadFontsFromPath(String folderPath) {
        loadFontsFromPath(folderPath, true);
    }

    /**
     * يفحص مجلد الخطوط بصمت بعد عودة المستخدم للتطبيق (مثلاً بعد إضافة ملف من تطبيق ملفات آخر)،
     * دون شاشة تحميل، وينشر القائمة للواجهة فقط إن تغيّر شيء فعلاً، حتى لا يحدث وميض عند عدم وجود تغييرات.
     */
    public void refreshFontsSilently() {
        final String folderPath = preferenceManager.getFontFolderPath();
        if (folderPath == null || folderPath.isEmpty()) return;

        // فحص سابق أو تحميل ما زال شغالاً، لا نبدأ فحصاً ثانياً فوقه
        if (mIsFolderSyncing || Boolean.TRUE.equals(isLoadingLiveData.getValue())) return;

        long now = android.os.SystemClock.elapsedRealtime();
        if (now - mLastSilentRefreshTime < MIN_SILENT_REFRESH_INTERVAL_MS) return;
        mLastSilentRefreshTime = now;

        if (fontPreloadExecutor.isShutdown()) return;
        try {
            fontPreloadExecutor.execute(() -> {
                // إن كان المجلد غير متاح الآن (صلاحية سُحبت، أو ذاكرة خارجية انفصلت) نتجاهل الفحص،
                // لأن المزامنة كانت ستعدّ ذلك مجلداً فارغاً وتحذف كل الخطوط من قاعدة البيانات
                if (!LocalFontDirectory.isAccessible(folderPath)) {
                    Log.w(TAG, "refreshFontsSilently: folder not accessible, skipping");
                    return;
                }
                new Handler(Looper.getMainLooper()).post(() -> {
                    // قد يبدأ تحميل آخر أو يتغير المجلد أثناء فحص الوصول، فنتأكد مرة أخيرة قبل المزامنة
                    if (mIsFolderSyncing || !folderPath.equals(preferenceManager.getFontFolderPath())) return;
                    loadFontsFromPath(folderPath, false, true);
                });
            });
        } catch (RejectedExecutionException e) {
            Log.w(TAG, "refreshFontsSilently: task rejected, executor already shut down", e);
        }
    }

    public void loadFontsFromPath(String folderPath, boolean showLoading) {
        loadFontsFromPath(folderPath, showLoading, false);
    }

    private void loadFontsFromPath(String folderPath, boolean showLoading, boolean publishOnlyIfChanged) {
        if (folderPath == null || folderPath.isEmpty()) {
            return;
        }

        if (Boolean.TRUE.equals(BatchOperationState.getIsProcessing().getValue())) {
            Log.w(TAG, "Skipping load and sync: A batch operation is currently running.");
            return;
        }

        if (showLoading) {
            isLoadingLiveData.postValue(true);
        }

        mIsFolderSyncing = true;

        final long startTime = System.currentTimeMillis();

        // عدد التغييرات التي وجدتها المزامنة (إضافة + تعديل + حذف)، نحتاجه في الفحص الصامت
        final int[] changedCount = {0};

        repository.loadAndSyncLocalFonts(folderPath, new LocalFontRepository.OnSyncCompleteListener() {
            @Override
            public void onSyncComplete(int added, int updated, int deleted) {
                changedCount[0] = added + updated + deleted;
                String message = String.format("Synced: %d added, %d updated, %d deleted",
                    added, updated, deleted);
                Log.d(TAG, message);
            }

            @Override
            public void onFullExtractionComplete() {
                // الفحص الصامت ولم يتغير شيء في المجلد: لا ننشر القائمة ولا نلمس الواجهة، فلا وميض
                if (publishOnlyIfChanged && changedCount[0] == 0) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        mIsFolderSyncing = false;

                        // إن وصل تحديث من قاعدة البيانات أثناء الفحص (مثل استخراج اسم خط) لا نضيّعه
                        List<FontItem> pending = mPendingSyncFonts;
                        mPendingSyncFonts = null;
                        if (pending != null) {
                            publishFontsAfterPreload(pending, null);
                        }
                    });
                    return;
                }

                List<FontItem> freshFontsFromDb;
                try {
                    freshFontsFromDb = repository.getLocalFontsSync();
                } catch (Exception e) {
                    freshFontsFromDb = null;
                }
                final List<FontItem> finalFreshFonts = freshFontsFromDb;

                long elapsedTime = System.currentTimeMillis() - startTime;
                long delay = showLoading ? Math.max(0, 2500 - elapsedTime) : 0;

                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    mIsFolderSyncing = false;

                    List<FontItem> toPublish;
                    if (finalFreshFonts != null) {
                        toPublish = finalFreshFonts;
                    } else if (mPendingSyncFonts != null) {
                        toPublish = mPendingSyncFonts;
                    } else {
                        List<FontItem> currentFonts = fontsLiveData.getValue();
                        toPublish = currentFonts != null ? currentFonts : new ArrayList<>();
                    }
                    mPendingSyncFonts = null;

                    publishFontsAfterPreload(toPublish, () -> {
                        if (showLoading) {
                            isLoadingLiveData.postValue(false);
                        }
                    });
                }, delay);
            }
        });
    }
    
    public void recordFontAccess(String fontPath) {
        if (fontPath != null && !fontPath.isEmpty()) {
            repository.recordAccess(fontPath);
        }
    }
    
    
    
    public void saveFolderPath(String folderPath) {
        if (folderPath != null && !folderPath.isEmpty()) {
            preferenceManager.saveFontFolderPath(folderPath);
        }
    }
    
    public boolean hasSavedFolder() {
        return preferenceManager.hasFontFolderPath();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        // لازم نلغي تسجيل الـ Observers قبل قفل الـ Executor
        // عشان أي تحديث متأخر من قاعدة البيانات ميحاولش يستخدم Executor مقفول
        mLocalFontsSource.removeObserver(mLocalFontsObserver);
        mFavoriteFontsSource.removeObserver(mFavoriteFontsObserver);
        fontPreloadExecutor.shutdownNow();
    }

    // ───────────────────────── آخر خط مفتوح (القائمة الأساسية والمفضلة) ─────────────────────────

    private LocalFontPreferenceManager lastOpenedPrefs(boolean favoritesList) {
        if (!favoritesList) {
            return preferenceManager;
        }
        if (favoritesPreferenceManager == null) {
            favoritesPreferenceManager = new LocalFontPreferenceManager(getApplication(), true);
        }
        return favoritesPreferenceManager;
    }

    public void saveLastOpenedFont(boolean favoritesList, String fontPath) {
        lastOpenedPrefs(favoritesList).saveLastOpenedFont(fontPath);
    }

    public boolean isLastOpenedFont(boolean favoritesList, String fontPath) {
        return lastOpenedPrefs(favoritesList).isLastOpenedFont(fontPath);
    }

    // ───────────────────────── معاينة الخطوط ─────────────────────────

    public boolean isFontPreviewEnabled() {
        return SettingsRepository.isFontPreviewEnabled(getApplication());
    }

    public Typeface getCachedTypeface(String fontPath) {
        return LocalFontCache.getInstance().getIfCached(fontPath);
    }

    // قد تكون ثقيلة اذا لم يكن الخط في الكاش، فتُستدعى من خيط خلفي
    public Typeface loadTypeface(String fontPath) {
        return LocalFontCache.getInstance().getTypeface(fontPath);
    }

    public void preloadFonts(List<String> fontPaths) {
        LocalFontCache.getInstance().preloadFonts(fontPaths);
    }
}
