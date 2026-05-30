package com.example.oneuiapp.viewmodel;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.oneuiapp.data.entity.FontEntity;
import com.example.oneuiapp.data.repository.LocalFontRepository;
import com.example.oneuiapp.data.repository.TrashRepository;   // ★ جديد ★
import com.example.oneuiapp.dialog.TrashActionDialogs;          // ★ إصلاح (1)(3): إدارة الإشعارات والخدمة ★
import com.example.oneuiapp.fontlist.localfont.LocalFontPreferenceManager;
import com.example.oneuiapp.utils.BatchOperationState;
import com.example.oneuiapp.utils.OperationForegroundService;   // ★ إصلاح (1)(3): الدرع الواقي ★

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;               // ★ جديد ★

/**
 * LocalFontListViewModel - محدث بدعم الحذف وإعادة التسمية في الذاكرة
 *
 * ★ التعديل: إضافة weightWidthLabel إلى FontFileInfoWithMetadata ★
 * يُمرَّر من FontEntity إلى الـ Adapter ومنه إلى LocalFontViewHolder.
 *
 * ★ الإضافة: دعم كامل لقائمة المفضلة ★
 * - favoritesLiveData يراقب قاعدة البيانات تلقائياً
 * - عمليات الحذف وإعادة التسمية تُحدّث القائمتين معاً لضمان التزامن
 * - منطق المفضلة مطابق لـ Samsung Notes (مختلط → إضافة، كلها مفضلة → إزالة)
 *
 * ★ التعديل (سلة المحذوفات): استبدال deleteFontsInMemory بـ moveFontsToTrashInMemory ★
 * بدلاً من حذف الملفات نهائياً بـ file.delete()، يُفوَّض الأمر الآن إلى
 * TrashRepository.moveToTrashBatch() الذي يتولى نقل الملف وتحديث قاعدة
 * البيانات. Room LiveData تُزيل العناصر المنقولة من القوائم تلقائياً
 * لأن استعلامات getLocalFonts() و getFavoriteFonts() تُصفّي is_trashed = 0.
 *
 * ★ إصلاح الأنيميشن (الخطوة الثالثة من خطة الإصلاح): ★
 * تم نقل التحكم في BatchOperationState من طبقة Repository إلى هذه الطبقة.
 * المبدأ: setProcessing(true) يُستدعى هنا قبل بدء العملية،
 * و setProcessing(false) يُستدعى داخل postDelayed بعد إغلاق الديالوج فوراً.
 * هذا يضمن أن أنيميشن الاختفاء/الظهور في القائمة لا يبدأ إلا بعد
 * أن يُغلَق ديالوج التقدم فعلياً.
 *
 * ★ إصلاح المشكلات (1)(2)(3)(4) في moveFontsToTrashInMemory: ★
 * - (2): نقرأ sourceFragmentIndex من BatchOperationState بدلاً من فرض الرقم 2،
 *         مما يحفظ الشاشة المصدر الصحيحة (2 للمحلي، 4 للمفضلة) لتوجيه
 *         إشعار النقل نحو الشاشة التي بدأت منها العملية.
 * - (4): setCancelFlag(trashCancelFlag) يُسجّل العلم في BatchOperationState
 *         لتمكين NotificationActionReceiver من إلغاء العملية من الإشعار.
 * - (1)(3): يُشغَّل OperationForegroundService قبل العملية ويُوقَف عند اكتمالها.
 *           الخدمة تربط الإشعار بعمليتها: عند قتل التطبيق يُزيل Android
 *           الإشعار تلقائياً — هذا يحل مشكلة الإشعار العالق والمتجمد نهائياً.
 *
 * ★ إصلاح المشكلة (1) الجوهري — استخدام Application context في progressListener: ★
 * كان الـ Fragment يمرّر progressListener يستخدم mContext (يصبح null بعد onDetach())
 * و getResources() (يرمي IllegalStateException بعد الفصل). عند إزالة التطبيق
 * من التطبيقات الأخيرة، تُدمَّر الـ Activity وتُفصَل الـ Fragments لكن
 * OperationForegroundService يُبقي العملية حية. نتيجةً لذلك، كان Repository
 * يستدعي الـ listener الفاشل فتتجمد الإشعارات.
 *
 * الحل: ViewModel يُغلّف (wraps) الـ progressListener المُمرَّر من الـ Fragment،
 * ويتولى تحديث الإشعار وBatchOperationState من Application context الآمن
 * الذي لا يرتبط بدورة حياة الـ Activity. الـ Fragment يبقى مسؤولاً فقط
 * عن تحديث الديالوج (الذي يكون null بعد onDestroyView ومحمي بـ null-check).
 *
 * ★ إصلاح اللاج (خطة الإصلاح — الخطوة الرابعة):
 *   mIsFolderSyncing و mPendingSyncFonts يحجبان تحديثات Room عن الـ Adapter
 *   أثناء تحميل مجلد جديد. عند انتهاء الاستخراج الكامل (onFullExtractionComplete)
 *   تُرسَل البيانات دفعة واحدة للواجهة بدون لاج أو أنيميشن متكرر. ★
 *
 * ★ إصلاح الشاشة الفارغة عند اختيار نفس المجلد (خطة الإصلاح):
 *   عند اختيار نفس المجلد، لا تُرسل Room أي تحديث لأن البيانات لم تتغير.
 *   onFullExtractionComplete في فرع else يُعيد إرسال القائمة الحالية بدلاً
 *   من إرسال قائمة فارغة، مما يُجبر الواجهة على التحديث ويُظهر الخطوط الموجودة.
 *   إذا كان المجلد فارغاً فعلاً، تكون currentFonts فارغة وتُرسَل كذلك. ★
 *
 * ★ إضافة مؤشر التحميل (الحد الأدنى 2500ms):
 *   startTime يُحفظ قبل استدعاء loadAndSyncLocalFonts مباشرةً.
 *   onFullExtractionComplete يحسب الوقت المنقضي ويُطبّق تأخيراً إضافياً
 *   إذا كان الوقت الفعلي أقل من 2500ms، مما يمنع "ومضة" المؤشر
 *   عند تحميل مجلد يحتوي على عدد قليل من الخطوط. ★
 */
public class LocalFontListViewModel extends AndroidViewModel {
    
    private static final String TAG = "LocalFontListViewModel";

    /** الحد الأدنى لمدة ظهور ديالوج التقدم (بالميلي ثانية) */
    private static final long MIN_DIALOG_DURATION_MS = 2500;
    
    private final LocalFontRepository repository;
    private final TrashRepository     trashRepository; // ★ جديد: مستودع سلة المحذوفات ★
    private final LocalFontPreferenceManager preferenceManager;
    private final MutableLiveData<Boolean> isLoadingLiveData;
    private final MutableLiveData<String>  errorMessageLiveData;
    private final MutableLiveData<List<FontEntity>> fontsLiveData;

    // ★ LiveData الخاصة بقائمة المفضلة — تراقب قاعدة البيانات تلقائياً ★
    private final MutableLiveData<List<FontEntity>> favoritesLiveData;

    // ★ متغيرات حجز الواجهة لمنع اللاج ★
    // mIsFolderSyncing: يُفعَّل عند بدء تحميل مجلد جديد ويُطفأ عند onFullExtractionComplete
    // mPendingSyncFonts: يحتفظ بآخر قائمة أرسلها Room أثناء فترة الحجز
    private boolean mIsFolderSyncing = false;
    private List<FontEntity> mPendingSyncFonts = null;

    // ★ علامة إلغاء عملية النقل إلى السلة الجارية ★
    // تُستخدم من LocalFontListFragment عند ضغط "إلغاء" في ديالوج التقدم
    private AtomicBoolean trashCancelFlag = new AtomicBoolean(false);
    
    // ════════════════════════════════════════════════════════════
    // نموذج البيانات الموسّع بالوزن/العرض والمفضلة
    // ════════════════════════════════════════════════════════════

    public static class FontFileInfoWithMetadata {
        private final String name;
        private final String path;
        private final long size;
        private final long lastModified;
        private final String realName;
        // ★ وصف الوزن والعرض ("Bold, Condensed" أو "غير معروف" إلخ) ★
        private final String weightWidthLabel;
        // ★ حالة المفضلة — تُستخدم لعرض أيقونة النجمة بجانب العنصر ★
        private final boolean isFavorite;
        
        /**
         * المُنشئ الأساسي: يُنشأ من FontEntity المُخزَّن في قاعدة البيانات.
         * يقرأ weightWidthLabel و isFavorite مباشرةً من الكيان.
         */
        public FontFileInfoWithMetadata(FontEntity entity) {
            this.name             = entity.getFileName();
            this.path             = entity.getPath();
            this.size             = entity.getSize();
            this.lastModified     = entity.getLastModified();
            this.realName         = entity.getRealName();
            this.weightWidthLabel = entity.getWeightWidthLabel(); // ★ جديد ★
            this.isFavorite       = entity.isFavorite();          // ★ جديد ★
        }
        
        /**
         * المُنشئ اليدوي: يُستخدم في حالات خاصة (مثل إعادة التسمية).
         * weightWidthLabel و isFavorite اختياريان ويمكن تمرير null/false إذا لم يكونا متاحَين.
         */
        public FontFileInfoWithMetadata(String name, String path, long size,
                                        long lastModified, String realName,
                                        String weightWidthLabel, boolean isFavorite) {
            this.name             = name;
            this.path             = path;
            this.size             = size;
            this.lastModified     = lastModified;
            this.realName         = realName;
            this.weightWidthLabel = weightWidthLabel; // ★ جديد ★
            this.isFavorite       = isFavorite;       // ★ جديد ★
        }
        
        public String  getName()             { return name; }
        public String  getPath()             { return path; }
        public long    getSize()             { return size; }
        public long    getLastModified()     { return lastModified; }
        public String  getRealName()         { return realName; }
        // ★ getters الجديدة ★
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
        trashRepository = TrashRepository.getInstance(application); // ★ جديد ★
        preferenceManager = new LocalFontPreferenceManager(application);
        isLoadingLiveData    = new MutableLiveData<>(false);
        errorMessageLiveData = new MutableLiveData<>();
        
        // ★ تحويل إلى MutableLiveData للتحكم اليدوي ★
        fontsLiveData = new MutableLiveData<>(new ArrayList<>());

        // ★ تهيئة LiveData للمفضلة ★
        favoritesLiveData = new MutableLiveData<>(new ArrayList<>());
        
        // تحميل البيانات من قاعدة البيانات وربطها
        // ★ Room تُصفّي is_trashed = 0 تلقائياً، لذا عند نقل خط للسلة
        //   سيُزال من هذه القائمة دون أي تدخل يدوي ★
        repository.getLocalFonts().observeForever(entities -> {
            if (entities != null) {
                // ★ اعتراض التحديثات: إذا كنا في مرحلة المزامنة، نحجز البيانات ولا نرسلها للواجهة ★
                // هذا يمنع الـ Adapter من حساب الفروقات وعمل أنيميشن لكل خط على حدة،
                // ويضمن إرسال القائمة الكاملة النهائية دفعة واحدة عند onFullExtractionComplete.
                if (mIsFolderSyncing) {
                    mPendingSyncFonts = entities;
                } else {
                    fontsLiveData.postValue(entities);
                }
            }
        });

        // ★ مراقبة قاعدة البيانات للمفضلة تلقائياً ★
        // ★ Room تُصفّي is_trashed = 0 هنا أيضاً — انظر FontDao.getFavoriteFonts() ★
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

    // ════════════════════════════════════════════════════════════
    // ★ Favorites LiveData — قائمة المفضلة ★
    // ════════════════════════════════════════════════════════════

    /**
     * إرجاع قائمة المفضلة كـ LiveData<List<FontFileInfoWithMetadata>>
     * تُراقَب تلقائياً — أي تغيير في قاعدة البيانات يُحدِّث الواجهة فوراً
     */
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

    /**
     * عدد الخطوط المفضلة — يُستخدم للعرض في رأس القائمة
     */
    public LiveData<Integer> getFavoritesCountLiveData() {
        return repository.getFavoriteFontsCount();
    }

    /**
     * ★ تبديل حالة المفضلة لخط واحد ★
     *
     * يُستخدم عند الضغط على أيقونة المفضلة في قائمة الخطوط المحلية
     *
     * @param path       مسار الخط
     * @param isFavorite true للإضافة، false للإزالة
     */
    public void toggleFavorite(String path, boolean isFavorite) {
        repository.updateFavoriteStatus(path, isFavorite, success -> {
            if (success) {
                Log.d(TAG, "★ Favorite toggled: " + path + " → " + isFavorite);
            } else {
                Log.w(TAG, "Failed to toggle favorite: " + path);
            }
        });
    }

    /**
     * ★ تبديل حالة المفضلة لمجموعة خطوط (التحديد المتعدد) ★
     *
     * المنطق المتبع (مطابق لـ Samsung Notes):
     * - إذا كانت العناصر مزيجاً من مفضلة وغير مفضلة → إضافة الجميع
     * - إذا كانت جميعها مفضلة → إزالة الجميع
     * - إذا كانت جميعها غير مفضلة → إضافة الجميع
     *
     * ★ إصلاح الأنيميشن (الخطوة الثالثة من خطة الإصلاح):
     *   setProcessing(true) يُستدعى هنا قبل تفويض العملية إلى Repository،
     *   و setProcessing(false) يُستدعى بعد onSuccess.run() مباشرةً
     *   لضمان أن أنيميشن القائمة لا يبدأ إلا بعد اكتمال العملية. ★
     *
     * @param paths      قائمة مسارات الخطوط المحددة
     * @param isFavorite الحالة الجديدة المراد تطبيقها
     * @param onSuccess  يُنفَّذ على الخيط الرئيسي بعد نجاح العملية
     */
    public void toggleFavoritesBatch(List<String> paths, boolean isFavorite, Runnable onSuccess) {
        if (paths == null || paths.isEmpty()) return;

        // ★ 1. إعلام الشاشات ببدء العملية لمنع تحديث الـ Adapter أثناءها ★
        BatchOperationState.setProcessing(true);

        repository.updateFavoriteStatusBatch(paths, isFavorite, success -> {
            if (success) {
                Log.d(TAG, "★ Batch favorite toggled: " + paths.size() + " fonts → " + isFavorite);
                if (onSuccess != null) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        onSuccess.run();
                        // ★ 2. إنهاء الحجز بعد تنفيذ onSuccess (إغلاق الديالوج) لتبدأ الأنيميشن ★
                        BatchOperationState.setProcessing(false);
                    });
                } else {
                    // لا يوجد onSuccess — نُنهي الحجز مباشرة
                    BatchOperationState.setProcessing(false);
                }
            } else {
                Log.w(TAG, "Failed to batch toggle favorites");
                // فشلت العملية — نُنهي الحجز حتماً لتجنب تجميد الواجهة
                BatchOperationState.setProcessing(false);
            }
        });
    }

    /**
     * ★ حساب حالة المفضلة للتحديد المتعدد (منطق Samsung Notes) ★
     *
     * يُستدعى من وضع التحديد المتعدد لتحديد ما إذا كان يجب عرض
     * "إضافة إلى المفضلة" أو "إزالة من المفضلة"
     *
     * - إذا كانت جميع العناصر المحددة مفضلة → return false (إزالة)
     * - في أي حالة أخرى (مختلطة أو كلها غير مفضلة) → return true (إضافة)
     *
     * @param selectedPaths قائمة مسارات العناصر المحددة
     * @return true إذا كان يجب إضافة إلى المفضلة، false للإزالة
     */
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

        // إذا كانت جميعها مفضلة → إزالة، وإلا → إضافة
        return favoriteCount < selectedPaths.size();
    }

    /**
     * ★ نسخة shouldAddToFavorites للاستخدام من داخل قائمة المفضلة ★
     *
     * تعمل على favoritesLiveData بدلاً من fontsLiveData
     * لأن قائمة المفضلة تعرض فقط الخطوط المفضلة
     *
     * @param selectedPaths قائمة مسارات العناصر المحددة في قائمة المفضلة
     * @return دائماً false لأن جميع عناصر قائمة المفضلة هي مفضلة بالتعريف
     */
    public boolean shouldAddToFavoritesFromFavoritesList(List<String> selectedPaths) {
        // جميع عناصر قائمة المفضلة هي مفضلة بالتعريف → دائماً إزالة
        return false;
    }

    /**
     * البحث في الخطوط المفضلة
     */
    public LiveData<List<FontEntity>> searchFavorites(String query) {
        if (query == null || query.trim().isEmpty()) {
            return repository.getFavoriteFonts();
        }
        return repository.searchFavoriteFonts(query.trim());
    }

    /**
     * الخطوط المفضلة مع الترتيب
     */
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

    // ════════════════════════════════════════════════════════════
    // عمليات إعادة التسمية والنقل إلى السلة — تُحدِّث القائمتين معاً
    // ════════════════════════════════════════════════════════════
    
    /**
     * ★ إعادة تسمية خط وتحديث القائمتين في الذاكرة (النقطة 9) ★
     * 
     * هذه الدالة تعيد تسمية الملف الفعلي ثم تحدث قائمة الخطوط المحلية
     * وقائمة المفضلة في الذاكرة مباشرة دون إعادة تحميل من القرص لتجنب الوميض.
     *
     * ★ إضافة: تحديث favoritesLiveData إذا كان الخط مفضلاً ★
     */
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
        
        // التحقق من عدم وجود ملف بنفس الاسم
        if (newFile.exists()) {
            errorMessageLiveData.postValue("الاسم موجود بالفعل");
            return false;
        }
        
        // إعادة تسمية الملف الفعلي
        if (!oldFile.renameTo(newFile)) {
            errorMessageLiveData.postValue("فشلت إعادة التسمية");
            return false;
        }
        
        // ★ تحديث قائمة الخطوط المحلية في الذاكرة ★
        List<FontEntity> currentList = fontsLiveData.getValue();
        if (currentList != null) {
            List<FontEntity> updatedList = new ArrayList<>(currentList);
            
            for (int i = 0; i < updatedList.size(); i++) {
                FontEntity entity = updatedList.get(i);
                if (entity.getPath().equals(oldPath)) {
                    // إنشاء كيان محدث مع الحفاظ على البيانات الأصلية
                    FontEntity updatedEntity = new FontEntity(
                        newFile.getAbsolutePath(),
                        newFileName
                    );
                    updatedEntity.setSize(entity.getSize());
                    updatedEntity.setLastModified(newFile.lastModified());
                    updatedEntity.setRealName(entity.getRealName());
                    updatedEntity.setAccessCount(entity.getAccessCount());
                    updatedEntity.setLastAccessTime(entity.getLastAccessTime());
                    // ★ الحفاظ على وصف الوزن/العرض بعد إعادة التسمية ★
                    updatedEntity.setWeightWidthLabel(entity.getWeightWidthLabel());
                    // ★ الحفاظ على حالة المفضلة بعد إعادة التسمية ★
                    updatedEntity.setFavorite(entity.isFavorite());
                    
                    updatedList.set(i, updatedEntity);
                    break;
                }
            }
            
            // تحديث LiveData فوراً
            fontsLiveData.postValue(updatedList);
        }

        // ★ تحديث قائمة المفضلة في الذاكرة إذا كان الخط مفضلاً ★
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
                    updatedEntity.setFavorite(true); // بالتعريف دائماً true هنا
                    
                    updatedFavorites.set(i, updatedEntity);
                    break;
                }
            }

            favoritesLiveData.postValue(updatedFavorites);
        }
        
        // تحديث قاعدة البيانات في الخلفية
        repository.updatePath(oldPath, newFile.getAbsolutePath(), newFileName);
        
        Log.d(TAG, "Font renamed in memory: " + oldPath + " -> " + newFile.getAbsolutePath());
        
        return true;
    }

    // ════════════════════════════════════════════════════════════
    // ★ النقل إلى سلة المحذوفات — استبدال الحذف النهائي ★
    // ════════════════════════════════════════════════════════════

    /**
     * ★ ينقل خطوطاً إلى سلة المحذوفات بدلاً من حذفها نهائياً ★
     *
     * هذه الدالة تحلّ محلّ deleteFontsInMemory السابقة تماماً.
     * الفرق الجوهري: بدلاً من file.delete()، تُستدعى TrashRepository.moveToTrashBatch()
     * التي تتولى:
     *   1. نقل الملف فعلياً إلى مجلد .Trash عبر TrashFileManager.
     *   2. تحديث قاعدة البيانات (is_trashed=1, deleted_at, original_path).
     *   3. Room LiveData تُزيل العناصر من fontsLiveData و favoritesLiveData
     *      تلقائياً لأن استعلامات getLocalFonts() و getFavoriteFonts()
     *      تُصفّي is_trashed = 0 — لا حاجة لتحديث يدوي.
     *
     * ★ إصلاح المشكلة (2): قراءة sourceIndex من BatchOperationState ★
     *   بدلاً من فرض الرقم 2 بشكل صلب (Hardcoded)، نقرأ الفهرس الذي ضبطه
     *   الفراجمنت المُطلِق للعملية (2 للمحلي، 4 للمفضلة). هذا يضمن أن
     *   TrashActionDialogs.buildContentIntent() تقرأ الفهرس الصحيح
     *   وتضيفه كـ EXTRA_TARGET_FRAGMENT في PendingIntent، مما يوجّه
     *   المستخدم للشاشة التي بدأت منها العملية عند ضغط الإشعار.
     *
     * ★ إصلاح المشكلة (4): setCancelFlag ★
     *   تسجيل trashCancelFlag في BatchOperationState يُمكّن
     *   NotificationActionReceiver من إيقاف الحلقة عند ضغط "إلغاء"
     *   من الإشعار الموسَّع بدون فتح التطبيق.
     *
     * ★ إصلاح المشكلتين (1)(3): OperationForegroundService ★
     *   يُشغَّل OperationForegroundService قبل العملية ويُوقَف في callback الاكتمال.
     *   الخدمة تستخدم startForeground(NOTIF_ID_MOVE, notif) مما يجعل الإشعار
     *   مرتبطاً بالخدمة. عند قتل التطبيق من التطبيقات الأخيرة:
     *   - Android يوقف الخدمة تلقائياً.
     *   - Service.onDestroy() يستدعي stopForeground(STOP_FOREGROUND_REMOVE).
     *   - الإشعار يُزال تلقائياً — لا إشعار عالق، لا شريط متجمد.
     *
     * ★ إصلاح المشكلة (1) الجوهري — Application context في progressListener: ★
     *   المشكلة: الـ Fragment يمرّر progressListener يستخدم mContext (null بعد onDetach)
     *   و getResources() (يرمي IllegalStateException بعد فصل الـ Fragment).
     *   عند إزالة التطبيق من التطبيقات الأخيرة، تُدمَّر الـ Activity وتُفصَل الـ Fragments
     *   لكن OperationForegroundService يُبقي العملية حية. Repository يستدعي الـ listener
     *   الفاشل → تتجمد الإشعارات.
     *
     *   الحل: ViewModel يُغلّف progressListener المُمرَّر من الـ Fragment:
     *   - يُحدِّث الإشعار وBatchOperationState من getApplication() الآمن.
     *   - يُفوّض تحديث الديالوج للـ Fragment (محمي بـ null-check بعد onDestroyView).
     *   - يُلتقط أي استثناء من الـ Fragment listener دون إيقاف العملية.
     *
     * ★ إصلاح الأنيميشن (الخطوة الثالثة من خطة الإصلاح):
     *   1. setProcessing(true, sourceIndex) يُستدعى هنا فور بدء العملية لحجز جميع الشاشات.
     *   2. يُسجَّل startTime قبل بدء العملية لحساب التأخير.
     *   3. داخل postDelayed: يُستدعى onComplete.run() أولاً (يُغلق الديالوج)،
     *      ثم setProcessing(false) مباشرةً بعده (تبدأ الأنيميشن).
     *   هذا يضمن الترتيب الصحيح: إغلاق الديالوج → بداية الأنيميشن. ★
     *
     * @param paths            قائمة مسارات الخطوط المراد نقلها إلى السلة
     * @param progressListener مُستمع التقدم لتحديث ديالوج التقدم
     *                         (يُستدعى من خيط الخلفية — ViewModel يُغلّفه للأمان)
     * @param onComplete       يُنفَّذ على الخيط الرئيسي عند انتهاء العملية
     *                         (بعد ضمان الحد الأدنى لمدة الديالوج)
     */
    public void moveFontsToTrashInMemory(@NonNull List<String> paths,
                                          TrashRepository.OnProgressListener progressListener,
                                          Runnable onComplete) {
        if (paths.isEmpty()) return;

        // ★ الخطوة 1: تحديد FontEntity المُقابلة للمسارات المُختارة ★
        // نحتاج إلى الكيانات الكاملة لأن TrashRepository يستخدم
        // font.getPath() و font.getFileName() أثناء العملية
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

        // ★ الخطوة 2: إعلام جميع الشاشات ببدء العملية مع الحفاظ على الشاشة المصدر ★
        // نقرأ الفهرس الذي تم ضبطه في الفراجمنت (2 للمحلي، 4 للمفضلة)
        int sourceIndex = BatchOperationState.getSourceFragmentIndex();
        if (sourceIndex == -1) sourceIndex = 2; // قيمة احتياطية
        BatchOperationState.setProcessing(true, sourceIndex);

        // ★ الخطوة 3: إنشاء cancelFlag جديد لهذه العملية ★
        trashCancelFlag = new AtomicBoolean(false);
        // ★ إصلاح (4): تسجيل العلم في BatchOperationState فوراً ★
        // يُمكّن NotificationActionReceiver من إيقاف الحلقة عند ضغط "إلغاء" في الإشعار
        BatchOperationState.setCancelFlag(trashCancelFlag);

        // ★ الخطوة 4: تسجيل وقت بداية العملية لحساب التأخير لاحقاً ★
        // يضمن أن الديالوج يظهر لمدة لا تقل عن MIN_DIALOG_DURATION_MS
        // حتى عند حذف خط واحد ينتهي بأجزاء من الميلي ثانية.
        final long startTime = System.currentTimeMillis();

        // ★ الخطوة 5: تشغيل OperationForegroundService — إصلاح المشكلتين (1) و (3) ★
        // الخدمة تربط الإشعار بعمليتها: عند قتل التطبيق يُزيل Android الإشعار تلقائياً
        String movingTitle = getApplication().getResources()
                .getQuantityString(com.example.oneuiapp.R.plurals.progress_moving_to_trash, fontsToMove.size());
        android.content.Intent moveServiceIntent = new android.content.Intent(
                getApplication(), OperationForegroundService.class);
        moveServiceIntent.putExtra(OperationForegroundService.EXTRA_NOTIF_ID,
                TrashActionDialogs.NOTIF_ID_MOVE);
        moveServiceIntent.putExtra(OperationForegroundService.EXTRA_TITLE, movingTitle);
        moveServiceIntent.putExtra(OperationForegroundService.EXTRA_TOTAL, fontsToMove.size());
        // ★ الإصلاح هنا: تمرير sourceIndex الصحيح بدلاً من 2 ★
        moveServiceIntent.putExtra(OperationForegroundService.EXTRA_SOURCE_FRAGMENT, sourceIndex);
        ContextCompat.startForegroundService(getApplication(), moveServiceIntent);

        // ★ الخطوة 6: تفويض العملية إلى TrashRepository ★
        // ★ إصلاح المشكلة (1) الجوهري: ViewModel يُغلّف progressListener المُمرَّر من الـ Fragment ★
        //
        // السبب: progressListener الخارجي (من LocalFontListFragment) يستخدم:
        //   - mContext     → يصبح null بعد Fragment.onDetach() (عند إزالة التطبيق من الأخيرة)
        //   - getResources() → يرمي IllegalStateException بعد فصل الـ Fragment
        //
        // ViewModel يتولى تحديث الإشعار وBatchOperationState من getApplication() الآمن،
        // ثم يُفوّض للـ Fragment listener فقط تحديث الديالوج (محمي بـ null-check).
        // أي استثناء من listener الـ Fragment يُلتقط دون إيقاف العملية.
        final int totalFonts = fontsToMove.size();
        trashRepository.moveToTrashBatch(
                getApplication(),
                fontsToMove,
                trashCancelFlag,

                // ★ ViewModel-level wrapper: يضمن استمرار الإشعار حتى بعد تدمير الـ Activity ★
                (current, total) -> {
                    // ★ إصلاح (1)(5): تحديث الإشعار من Application context — آمن دائماً ★
                    // يعمل حتى لو كانت الـ Activity مُدمَّرة والـ Fragment مفصولاً
                    TrashActionDialogs.updateMoveToTrashNotification(getApplication(), current, total);

                    // ★ إصلاح (3): تحديث BatchOperationState من Application context الآمن ★
                    // يُحفظ آخر تقدم لـ reconnectToProgressDialog() عند العودة من الإشعار
                    String progressTitle = getApplication().getResources()
                            .getQuantityString(
                                    com.example.oneuiapp.R.plurals.progress_moving_to_trash, total);
                    BatchOperationState.updateProgress(current, total, progressTitle);

                    // ★ تفويض تحديث الديالوج للـ Fragment ★
                    // Fragment.onDestroyView() يُعيّن mCurrentProgressDialog = null
                    // لذا أي null-check في الـ listener يحمي من الـ NPE
                    // نلتقط الاستثناء لمنع إيقاف العملية إذا تعطّل الـ Fragment
                    if (progressListener != null) {
                        try {
                            progressListener.onProgress(current, total);
                        } catch (Exception e) {
                            Log.w(TAG, "progressListener.onProgress() failed"
                                    + " (fragment may be detached): " + e.getMessage());
                        }
                    }
                },

                // مُستمع الاكتمال — يُعيد التنفيذ إلى الخيط الرئيسي مع ضمان MIN_DIALOG_DURATION_MS
                (succeeded, failed) -> {
                    Log.d(TAG, "moveFontsToTrashInMemory done"
                            + " — succeeded: " + succeeded
                            + ", failed: " + failed
                            + ", cancelled: " + trashCancelFlag.get());

                    // ★ Room LiveData ستُحدِّث fontsLiveData و favoritesLiveData
                    //   تلقائياً لأن is_trashed=1 يُخرج العناصر من استعلام getLocalFonts() ★

                    // ★ حساب التأخير اللازم لضمان الحد الأدنى لمدة الديالوج ★
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    long delay = Math.max(0, MIN_DIALOG_DURATION_MS - elapsedTime);

                    if (onComplete != null) {
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            // ★ إيقاف OperationForegroundService — يُزيل الإشعار المرتبط بالخدمة ★
                            // الخدمة ستستدعي stopForeground(STOP_FOREGROUND_REMOVE) في onDestroy()
                            getApplication().stopService(new android.content.Intent(
                                    getApplication(), OperationForegroundService.class));
                            // ★ إزالة الإشعار الثانوي احتياطاً (في حال كان هناك إشعار مستقل) ★
                            TrashActionDialogs.dismissMoveToTrashNotification(getApplication());

                            onComplete.run(); // ★ يُغلق الديالوج ★
                            // ★ إنهاء الحجز فور إغلاق الديالوج لتبدأ الأنيميشن بعده مباشرة ★
                            BatchOperationState.setProcessing(false);
                        }, delay);
                    } else {
                        // لا يوجد onComplete — نُوقف الخدمة ونُنهي الحجز بعد انقضاء التأخير
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

    /**
     * ★ يُلغي عملية النقل إلى السلة الجارية ★
     *
     * يُستدعى من LocalFontListFragment عند ضغط المستخدم على "إلغاء"
     * في ديالوج التقدم. يضبط cancelFlag إلى true فتتوقف الحلقة
     * في TrashRepository في بداية الدورة التالية.
     */
    public void cancelTrashOperation() {
        if (trashCancelFlag != null) {
            trashCancelFlag.set(true);
            Log.d(TAG, "cancelTrashOperation: cancellation requested");
        }
    }

    // ════════════════════════════════════════════════════════════
    // الدوال المساعدة
    // ════════════════════════════════════════════════════════════
    
    /**
     * البحث عن موقع خط بعد إعادة التسمية (للتمرير السلس)
     */
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

    /**
     * البحث عن موقع خط في قائمة المفضلة (للتمرير السلس بعد إعادة التسمية)
     */
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

        // ★ الإصلاح الجوهري للمشكلة 4: حماية التزامن (Race Condition Guard) ★
        // إذا كانت هناك عملية ضخمة جارية (نقل/استعادة) عبر خيط الخلفية،
        // نمنع المزامنة تماماً حتى لا تقوم بمسح سجلات الملفات التي يتم نقلها.
        if (Boolean.TRUE.equals(BatchOperationState.getIsProcessing().getValue())) {
            Log.w(TAG, "Skipping load and sync: A batch operation is currently running.");
            return;
        }

        // ★ إظهار مؤشر التحميل دائماً عند اختيار مجلد — سواء كانت هناك بيانات سابقة أم لا ★
        // يضمن ظهور المؤشر في كل مرة يُطلب فيها تحميل مجلد جديد
        isLoadingLiveData.postValue(true);

        // ★ تفعيل وضع الحجز لمنع الـ Adapter من التحديث العشوائي ★
        // يُبقي mIsFolderSyncing = true حتى يُستدعى onFullExtractionComplete()
        mIsFolderSyncing = true;

        // ★ حفظ وقت بدء التحميل لحساب الحد الأدنى (2500ms) لمؤشر التحميل ★
        // يمنع "ومضة" المؤشر عند تحميل مجلد يحتوي على عدد قليل من الخطوط
        final long startTime = System.currentTimeMillis();
        
        repository.loadAndSyncLocalFonts(folderPath, new LocalFontRepository.OnSyncCompleteListener() {
            @Override
            public void onSyncComplete(int added, int updated, int deleted) {
                // لا نوقف التحميل هنا، ننتظر انتهاء الاستخراج الكامل
                String message = String.format("Synced: %d added, %d updated, %d deleted",
                    added, updated, deleted);
                Log.d(TAG, message);
            }

            @Override
            public void onFullExtractionComplete() {
                // ★ حساب الوقت المنقضي فعلياً منذ بدء التحميل ★
                long elapsedTime = System.currentTimeMillis() - startTime;
                // ★ إذا انتهى التحميل قبل 2500ms، ننتظر الفارق المتبقي ★
                // إذا تجاوز 2500ms، يكون delay = 0 وتظهر النتائج فوراً
                long delay = Math.max(0, 2500 - elapsedTime);

                // ★ استخدام Handler لتأخير عرض النتائج وإخفاء مؤشر التحميل ★
                // يضمن ظهور المؤشر لمدة لا تقل عن 2.5 ثانية مهما كان حجم المجلد
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    // رفع الحجز يُسمح لـ observeForever بإرسال البيانات للواجهة
                    mIsFolderSyncing = false;

                    if (mPendingSyncFonts != null) {
                        // إرسال آخر قائمة تم حجزها أثناء فترة الاستخراج — هي القائمة الكاملة النهائية
                        fontsLiveData.postValue(mPendingSyncFonts);
                        mPendingSyncFonts = null;
                    } else {
                        // ✅ الإصلاح: إذا لم تكن هناك تغييرات (اخترنا نفس المجلد)، أعد إرسال القائمة الحالية
                        // بدلاً من إرسال قائمة فارغة، وإذا كانت فارغة فعلاً ستُرسل فارغة.
                        // السبب: عند اختيار نفس المجلد، لا يُرسل Room تحديثاً جديداً عبر LiveData
                        // لأنه لا يجد تغييراً في البيانات، فـ mPendingSyncFonts تبقى null.
                        // إعادة إرسال القائمة الحالية تُجبر الواجهة على التحديث وإظهار الخطوط.
                        List<FontEntity> currentFonts = fontsLiveData.getValue();
                        fontsLiveData.postValue(currentFonts != null ? currentFonts : new java.util.ArrayList<>());
                    }

                    // إخفاء مؤشر التحميل بعد انقضاء التأخير
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
