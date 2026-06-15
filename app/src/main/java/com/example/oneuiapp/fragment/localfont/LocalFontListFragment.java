package com.example.oneuiapp.fragment.localfont;

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
import dev.oneuiproject.oneui.widget.Toast;
import dev.oneuiproject.oneui.layout.DrawerLayout;

import com.example.oneuiapp.activity.AppScreen;           // ★ الإصلاح الجوهري: استيراد AppScreen ★
import com.example.oneuiapp.activity.MainActivity;
import com.example.oneuiapp.data.repository.TrashRepository;
import com.example.oneuiapp.dialog.FontActionDialogs;
import com.example.oneuiapp.dialog.TrashActionDialogs; // ★ إضافة: لإظهار وتحديث وإخفاء إشعار النقل ★
import com.example.oneuiapp.fontlist.FontFileInfo;
import com.example.oneuiapp.fragment.localfont.manager.LocalFontSelectionManager;
import com.example.oneuiapp.fragment.localfont.manager.LocalFontPermissionManager;
import com.example.oneuiapp.fragment.localfont.fontdirectory.LocalFontDirectoryPicker;
import com.example.oneuiapp.fontlist.FontUIStateManager;
import com.example.oneuiapp.fontlist.search.FontTextHighlighter;
import com.example.oneuiapp.fontlist.FontSortManager;
import com.example.oneuiapp.fontlist.search.FontSearchManager;
import com.example.oneuiapp.fragment.localfont.data.LocalFontCache;
import com.example.oneuiapp.fragment.localfont.adapter.LocalFontListAdapter;
import com.example.oneuiapp.utils.FontItemDecoration;
import com.example.oneuiapp.R;
import com.example.oneuiapp.ui.widget.SortByItemLayout;
import com.example.oneuiapp.fragment.localfont.viewmodel.LocalFontListViewModel;
import com.example.oneuiapp.viewmodel.SearchViewModel;
import com.example.oneuiapp.fragment.settings.viewmodel.SettingsViewModel;

/**
 * LocalFontListFragment — محدث ليفوّض الفرز بالكامل إلى SortedList داخل LocalFontListAdapter.
 * ★ لم يعد هذا الـ Fragment يستدعي FontSortManager.sortFontsList() ★
 * ★ عند تغيير الفرز → mAdapter.setSortOptions() → أنيميشن مباشر ★
 * ★ عند وصول بيانات جديدة → mAdapter.updateFilteredFonts() → SortedList يرتبها تلقائياً ★
 *
 * ★ يستخدم FontSortManager(context, false) → مفاتيح DataStore خاصة بالمجلد المحلي فقط ★
 *
 * ★ التعديل: تحديث OnFontSelectedListener ليشمل weightWidthLabel كمعامل خامس ★
 *   لتمريره مباشرةً إلى NavManager ثم FontViewerFragment دون إعادة استخراجه،
 *   إذ أن الوزن مستخرج مسبقاً وموجود في بيانات القائمة.
 *
 * ★ الإضافة: دعم المفضلة بثلاثة عناصر:
 *   1. setFavoriteStatusProvider → يُعرض أيقونة النجمة بجانب العناصر المفضلة
 *   2. setFavoriteStatusChecker → يُطبَّق منطق Samsung Notes في وضع التحديد
 *   3. onFavoriteRequested + handleFavoriteAction → تنفيذ الإضافة/الإزالة الفعلية ★
 *
 * ★ الإصلاح (المشكلة 2): استدعاء notifyAllFavoritesChanged() داخل مراقب getFontsLiveData()
 *   بعد تجديد mCurrentFontsList مباشرةً، لضمان قراءة FavoriteStatusProvider للقيم المحدّثة.
 *   كان الاستدعاء في callback handleFavoriteAction يسبق وصول LiveData، فتظهر النجمة
 *   فقط بعد Scroll بسبب قراءة القيم القديمة. ★
 *
 * ★ التعديل (سلة المحذوفات): استبدال منطق الحذف النهائي في handleDelete()
 *   بعرض ديالوج تأكيد النقل إلى السلة ثم ديالوج التقدم، والاستعانة بـ
 *   mViewModel.moveFontsToTrashInMemory() بدلاً من deleteFontsInMemory(). ★
 *
 * ★ إصلاح الأنيميشن (خطة الإصلاح الثلاثية):
 *   1. setRemoveDuration(0) → اختفاء فوري بلا وميض متتابع عند حذف عناصر متعددة
 *   2. mIsBatchOperationRunning → يحجب تحديث الـ Adapter أثناء ديالوج التقدم
 *   3. الحد الأدنى 300ms في ViewModel → لا يومض الديالوج عند عنصر واحد ★
 *
 * ★ إصلاح اللاج (Global State Interception):
 *   بدلاً من إدارة mIsBatchOperationRunning محلياً في هذا الـ Fragment فقط،
 *   يراقب الآن BatchOperationState.getIsProcessing() — إشارة مرور عامة تُفعَّل
 *   من Repository مباشرةً. هذا يضمن أن أي Fragment يُنشأ أثناء عملية جارية
 *   (مثل الانتقال من سلة المحذوفات إلى الخطوط المحلية) يحجز بياناته فوراً
 *   بدلاً من تحديث الواجهة عنصراً تلو الآخر مما يُسبب اللاج. ★
 *
 * ★ إصلاح المشكلات (1)(2)(3) — الإشعار والديالوج:
 *   - (1)(2): setSourceScreen(AppScreen.LOCAL_FONTS) يُستدعى في بداية showMoveToTrashProgressDialog()
 *             قبل إظهار الإشعار مباشرةً، لضمان أن PendingIntent يحمل الشاشة الصحيحة.
 *   - (2)(3): mCurrentProgressDialog حقل على مستوى الـ Fragment يتيح إعادة ربطه
 *             عند العودة من الإشعار عبر checkAndReopenProgressDialogPublic().
 *   - (3): BatchOperationState.updateProgress() يُستدعى في progressListener لحفظ
 *           آخر تقدم، ويُقرأ في reconnectToProgressDialog() لإظهار قيمة محدَّثة. ★
 *
 * ★ إصلاح المشكلة الأولى (اللاج): حذف استدعاءات preloadNewlyAddedFonts بالكامل.
 *   كانت هذه الدالة تُحدِّث قاعدة البيانات لكل خط على حدة مما يُرسل مئات التحديثات
 *   لـ LiveData وتُجبر الـ Adapter على إعادة الرسم مئات المرات (Over-emission Death Loop).
 *   الـ RecyclerView مُصمَّم أصلاً ليُحمِّل الخطوط بذكاء عند ظهورها على الشاشة. ★
 *
 * ★ إصلاح مشكلة السكرول (الخطوة الثانية):
 *   تسجيل الاستخدام في قاعدة البيانات (recordFontAccess) أصبح يتم عند النقر
 *   الفعلي على الخط فقط، بدلاً من حدوثه تلقائياً أثناء التمرير من داخل LocalFontCache.
 *   هذا يمنع Room من إرسال تحديثات LiveData متكررة أثناء التمرير. ★
 *
 * ★ إصلاح مشكلة الشاشة الفارغة عند اختيار نفس المجلد:
 *   - إلغاء التفريغ اليدوي (mCurrentFontsList.clear + refreshAdapterData) من
 *     setupDirectoryPickerListener، لأن Room لا يُرسل تحديثاً جديداً عند عدم وجود تغيير،
 *     فتبقى القائمة فارغة بلا تحديث يُعيد تعبئتها.
 *   - إضافة refreshAdapterData() في مراقب getIsLoadingLiveData() بعد hideLoadingState()
 *     لإجبار الواجهة على إعادة رسم القائمة بعد اختفاء مؤشر التحميل حتى لو لم يُرسل
 *     Room أي تحديث جديد. ★
 *
 * ★ الإصلاح الجوهري (الخطوة الأولى + الثالثة من خطة الإصلاح):
 *   استبدال الأرقام السحرية بـ AppScreen enum في جميع نقاط التواصل مع MainActivity
 *   و BatchOperationState، مما يُتيح حذف HomeFragment أو تغيير ترتيب الشاشات
 *   دون أن يتأثر أي عداد أو إشعار أو منطق واجهة. ★
 *
 * ★ المرحلة الأولى من خطة التحسين: اللامركزية في قوائم AppBar ★
 *   هذا الـ Fragment أصبح مسؤولاً عن أيقوناته الخاصة عبر:
 *   - setHasOptionsMenu(true) في onCreate()
 *   - onCreateOptionsMenu() لنفخ menu_font_list_search + menu_local_fonts_more
 *   - onPrepareOptionsMenu() للتحكم الديناميكي بظهور زر الثلاث نقاط
 *   - onOptionsItemSelected() لمعالجة action_change_folder
 *   - setMenuVisibility(!hidden) في onHiddenChanged() للتبديل التلقائي
 *
 * ★ إصلاح Menu State Corruption:
 *   استبدال invalidateOptionsMenu() في مراقب حالة البحث بتحديث مباشر وصامت
 *   لرؤية زر الثلاث نقاط عبر mMenu المحفوظ، مما يمنع تدمير وإعادة بناء القائمة
 *   أثناء تمدد SearchView ويحل مشاكل: الكيبورد، زر الرجوع، وتجميد العنوان. ★
 *
 * ★ الإصلاح (خطة الإصلاح الشاملة — الخطوة الثالثة):
 *   إضافة onSearchStateChanged(boolean) لمزامنة SearchViewModel مع الحالة البصرية
 *   لحقل البحث فوراً عند تمدده أو طيّه، مما يُخفي/يُظهر زر الثلاث نقاط
 *   في نفس اللحظة بدلاً من انتظار كتابة أول حرف. ★
 */
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

    // ★ إضافة هذا المتغير لمنع القفز العشوائي ★
    private boolean mNeedsScrollRestore = false;

    // ─────────────────────────────────────────────────────────
    // ★ الخطوة الأولى من إصلاح Menu State Corruption ★
    // حفظ مرجع القائمة لتحديث رؤية الأزرار مباشرةً بدون invalidateOptionsMenu()
    // ─────────────────────────────────────────────────────────
    private Menu mMenu;

    // ─────────────────────────────────────────────────────────
    // ★ متغيرات تأجيل تحديث الواجهة أثناء ديالوج التقدم ★
    // ─────────────────────────────────────────────────────────

    /**
     * true إذا كانت عملية نقل إلى السلة جارية والديالوج مفتوح.
     * يمنع LiveData من تحديث الـ Adapter مباشرةً — يحجز البيانات بدلاً من ذلك.
     *
     * ★ بعد إصلاح اللاج: هذا المتغير لم يعد يُضبط يدوياً من showMoveToTrashProgressDialog،
     *   بل يُحدَّث تلقائياً من مراقب BatchOperationState.getIsProcessing() الذي
     *   يستقبل الإشارة مباشرةً من TrashRepository عبر خيط الخلفية. ★
     */
    private boolean mIsBatchOperationRunning = false;

    /**
     * يحتجز آخر تحديث للبيانات القادم من LiveData أثناء عملية جارية.
     * يُطبَّق على الـ Adapter فور إغلاق الديالوج لتبدأ حركة الاختفاء/الصعود دفعةً واحدة.
     */
    @Nullable
    private List<LocalFontListViewModel.FontFileInfoWithMetadata> mPendingFontsUpdate = null;

    /**
     * ★ مرجع ديالوج التقدم الجاري عرضه — null عندما لا توجد عملية جارية ★
     *
     * يُحفظ كحقل على مستوى الـ Fragment لإتاحة:
     *   1. تحديثه من مراقب BatchOperationState.getProgress() عند إعادة فتحه.
     *   2. إغلاقه في onDestroyView() لمنع تسرب الذاكرة.
     *   3. التحقق من حالته (isShowing) في checkAndReopenProgressDialogPublic().
     */
    @Nullable
    private ProgressDialog mCurrentProgressDialog;

    // ★ يحجب جميع أحداث اللمس على الـ RecyclerView ★
    private final RecyclerView.OnItemTouchListener mTouchBlocker =
        new RecyclerView.OnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv,
                                                 @NonNull MotionEvent e) { return true; }
            @Override
            public void onTouchEvent(@NonNull RecyclerView rv,
                                     @NonNull MotionEvent e) {}
            @Override
            public void onRequestDisallowInterceptTouchEvent(boolean b) {}
        };

    /**
     * ★ التعديل: إضافة weightWidthLabel كمعامل خامس ★
     * يحمل وصف الوزن/العرض الجاهز من القائمة لتمريره لـ NavManager ثم FontViewerFragment.
     */
    public interface OnFontSelectedListener {
        void onFontSelected(String fontPath, String realName, String fileName,
                            int ttcIndex, String weightWidthLabel);
    }

    // ─────────────────────────────────────────────────────────
    // دوال التحكم في اللمس — تُستدعى من MainActivity
    // ─────────────────────────────────────────────────────────

    /** تعطيل اللمس فوراً عند النقر على خط */
    public void blockTouch() {
        if (mRecyclerView != null) {
            mRecyclerView.removeOnItemTouchListener(mTouchBlocker);
            mRecyclerView.addOnItemTouchListener(mTouchBlocker);
        }
    }

    /** تفعيل اللمس عند العودة للقائمة */
    public void unblockTouch() {
        if (mRecyclerView != null)
            mRecyclerView.removeOnItemTouchListener(mTouchBlocker);
        // ★ إعادة تفعيل الحارس لقبول النقرات مجدداً ★
        if (mAdapter != null) mAdapter.resetClickGuard();
        View root = getView();
        if (root != null) {
            root.setClickable(true);
            root.setFocusable(true);
            root.setEnabled(true);
            root.bringToFront();
            root.requestFocus();
        }
    }

    /** حفظ آخر خط مفتوح وتمييزه — يُستدعى بعد تأكيد الانتقال */
    public void saveAndHighlight(String path) {
        if (mAdapter != null) {
            mAdapter.saveLastOpenedAndUpdate(path);
        }
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

        // ★ false = خطوط المجلد المحلي → يقرأ/يكتب KEY_SORT_TYPE و KEY_SORT_ASCENDING ★
        // هذا يمنع التداخل مع إعدادات فرز خطوط النظام ويحل مشكلة التجمد نهائياً
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
                    // ❌ تم حذف التفريغ اليدوي للقائمة من هنا (إصلاح مشكلة الشاشة الفارغة)
                    // السبب: عند اختيار نفس المجلد، لا يُرسل Room تحديثاً جديداً عبر LiveData
                    // لأنه لا يجد تغييراً في البيانات. وبما أن mCurrentFontsList.clear() فرّغ
                    // القائمة يدوياً، تبقى الواجهة فارغة لأنه لا يأتيها تحديث ليُعيد تعبئتها.
                    // الإصلاح الصحيح: إجبار الواجهة على التحديث من مراقب getIsLoadingLiveData().
                    // mCurrentFontsList.clear();
                    // refreshAdapterData();

                    mViewModel.saveFolderPath(directoryPath);
                    mViewModel.loadFontsFromPath(directoryPath);
                }
                mUIManager.updateUIVisibility(true);
                // ★ إصلاح (المشكلة 1): تمت إزالة رسالة Toast "Folder selected" ★
                // ★ إصلاح المشكلة الثانية: إخبار MainActivity بأن مجلداً تم اختياره ★
                // يُتيح هذا لـ MainActivity إظهار زر "تغيير مجلد الخطوط" في الـ Toolbar
                updateMainActivityFolderState(true);

                // ★ الخطوة الرابعة من إصلاح Menu State Corruption:
                //   تحديث رؤية زر المجلد بشكل مباشر وصامت عبر mMenu المحفوظ
                //   بدلاً من invalidateOptionsMenu() الذي يدمر ويعيد بناء القائمة بأكملها ★
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
        // ★ المستمع يحدّث حالة الواجهة فقط — تحديث الـ Adapter يتم من المراقب ★
        mSearchManager.setSearchResultListener((count, empty) -> {
            mUIManager.updateEmptyView(empty, mSearchManager.isSearchActive());
        });
    }

    private void setupSortListener() {
        // ★ التغيير الجوهري: عند تغيير الفرز نستدعي setSortOptions مباشرة ★
        // هذا يُشغّل أنيميشن SortedList بدلاً من إعادة تحميل البيانات كلها
        mSortManager.setSortChangeListener((type, asc) -> {
            if (mAdapter != null) {
                mAdapter.setSortOptions(type, asc);
            }
        });
    }

    @Override
    public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);

        // ★ المرحلة الأولى: إعلام النظام أن هذا الـ Fragment يمتلك أيقونات AppBar خاصة به ★
        // يضمن هذا استدعاء onCreateOptionsMenu() عند ظهور الـ Fragment
        // وإخفاء الأيقونات تلقائياً عند إخفائه عبر setMenuVisibility() في onHiddenChanged()
        setHasOptionsMenu(true);

        mMainHandler = new Handler(Looper.getMainLooper());
        mExecutor    = Executors.newSingleThreadExecutor();

        initializeViewModels();
        setupViewModelObservers();

        if (state != null) {
            restoreInstanceState(state);
        }
    }

    /**
     * ★ المرحلة الأولى: نفخ أيقونات هذا الـ Fragment في AppBar ★
     *
     * ينفخ قائمة البحث وقائمة المزيد الخاصة بهذا الـ Fragment،
     * ثم يربط أيقونة البحث بـ SearchCoordinator الموجود في MainActivity.
     * menu.clear() يضمن نظافة القائمة قبل كل نفخ جديد.
     *
     * ★ الخطوة الثانية من إصلاح Menu State Corruption:
     *   حفظ مرجع القائمة في mMenu فور إنشائها لاستخدامه لاحقاً في
     *   التحديث المباشر الصامت لرؤية الأزرار. ★
     */
    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        // ★ الخطوة الثانية: حفظ مرجع القائمة لتحديث الأزرار مباشرةً دون إعادة بناء ★
        this.mMenu = menu;

        menu.clear(); // تنظيف أي قوائم سابقة
        // ★ نفخ قائمة البحث وقائمة المزيد الخاصة بهذا الفراجمنت ★
        inflater.inflate(R.menu.menu_font_list_search, menu);
        inflater.inflate(R.menu.menu_local_fonts_more, menu);

        // ★ ربط أيقونة البحث بالـ Coordinator الموجود في MainActivity ★
        // bindSearchMenuItem() يُعدّ SearchView ويضبط مستمعاته لتفعيل البحث
        MenuItem searchItem = menu.findItem(R.id.action_search_fonts);
        if (getActivity() instanceof MainActivity && searchItem != null) {
            ((MainActivity) getActivity()).getSearchCoordinator().bindSearchMenuItem(searchItem);
        }

        // تم حذف كود الرؤية (Visibility) من هنا، وتم نقله إلى onPrepareOptionsMenu()
        super.onCreateOptionsMenu(menu, inflater);
    }

    /**
     * ★ الإصلاح (المشكلة 1 و 2): التحكم الديناميكي في ظهور زر الثلاث نقاط ★
     *
     * onPrepareOptionsMenu هي المكان الصحيح للتحكم الديناميكي بظهور عناصر القائمة.
     * تُستدعى في كل مرة يستدعي فيها الكود getActivity().invalidateOptionsMenu()،
     * على عكس onCreateOptionsMenu التي تُستدعى مرة واحدة فقط عند البناء الأول.
     *
     * منطق الإخفاء والإظهار:
     *   - البحث نشط   → يُخفي changeFolderItem → النظام يُخفي زر الثلاث نقاط تلقائياً
     *   - البحث مغلق  → يُظهر changeFolderItem فقط إذا كان هناك مجلد محفوظ
     *
     * هذا يحل المشكلة الثانية تلقائياً: عندما يكون البحث مفتوحاً لا يمكن الوصول
     * لزر تغيير المجلد، فلا تحدث حالة التداخل التي تُجمّد العناوين وزر الرجوع.
     */
    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        super.onPrepareOptionsMenu(menu);

        MenuItem changeFolderItem = menu.findItem(R.id.action_change_folder);
        if (changeFolderItem != null) {
            // نتحقق مما إذا كان البحث نشطاً
            boolean isSearchActive = mSearchViewModel != null && mSearchViewModel.isSearchActive();

            // يظهر الخيار (وبالتالي زر الثلاث نقاط) فقط إذا كان هناك مجلد محفوظ والبحث مغلق!
            changeFolderItem.setVisible(!isSearchActive && mViewModel.hasSavedFolder());
        }
    }

    /**
     * ★ المرحلة الأولى: معالجة نقرات أيقونات هذا الـ Fragment ★
     *
     * عند الضغط على زر تغيير المجلد (action_change_folder)،
     * تُفتح نافذة اختيار المجلد مباشرةً.
     */
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        // ★ الإصلاح: استخدام الـ ID الصحيح للعنصر الموجود داخل الثلاث نقاط ★
        if (item.getItemId() == R.id.action_change_folder) {
            // ★ فتح منتقي المجلد عند الضغط على زر تغيير مجلد الخطوط ★
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
                // ★ إذا كانت عملية نقل جارية، نحجز البيانات ولا نُحدِّث الواجهة ★
                // يضمن هذا أن اختفاء العناصر يبدأ دفعةً واحدة فقط بعد إغلاق الديالوج،
                // بدلاً من وميض متتابع عنصراً تلو الآخر أثناء جريان العملية.
                if (mIsBatchOperationRunning) {
                    mPendingFontsUpdate = new ArrayList<>(fonts);
                    return;
                }

                mCurrentFontsList = new ArrayList<>(fonts);

                if (mAdapter != null) {
                    mAdapter.setAllFontsMetadata(fonts);

                    // ★ الإصلاح (المشكلة 2): إجبار الـ Adapter على تحديث أيقونات النجمة
                    //   بعد وصول البيانات المحدّثة من Room مباشرةً.
                    //
                    // السبب الجذري للمشكلة:
                    //   عند إضافة خط للمفضلة، يُستدعى notifyAllFavoritesChanged() في
                    //   callback handleFavoriteAction قبل أن تُحدِّث Room LiveData
                    //   mCurrentFontsList. نتيجةً لذلك تقرأ FavoriteStatusProvider
                    //   القيم القديمة (isFavorite = false) فلا تظهر النجمة.
                    //
                    // الحل: إعادة استدعاء notifyAllFavoritesChanged() هنا بعد أن
                    //   تجدّد mCurrentFontsList بالبيانات الصحيحة من Room. ★
                    mAdapter.notifyAllFavoritesChanged();
                }

                // ★ تحديث البيانات — SortedList يرتبها تلقائياً حسب معيار الفرز الحالي ★
                refreshAdapterData();

                // ★ الإصلاح (المشكلة الأولى): تمّ حذف استدعاء preloadNewlyAddedFonts هنا.
                //   كانت هذه الدالة تُسبب لاجاً شديداً عبر تحديث قاعدة البيانات لكل خط
                //   على حدة مما يُرسل مئات التحديثات لـ LiveData ويُجبر الـ Adapter على
                //   إعادة الرسم مئات المرات (Over-emission Death Loop). ★

                updateMainActivityFontsCount(fonts.size());
            }
        });

        mViewModel.getIsLoadingLiveData().observe(this, isLoading -> {
            if (isLoading != null && isLoading) {
                mUIManager.showLoadingState();
            } else {
                mUIManager.hideLoadingState();
                // ✅ إجبار الواجهة على إعادة رسم القائمة بعد اختفاء مؤشر التحميل
                // (يحل مشكلة عدم الإظهار إذا لم يرسل Room أي تحديث جديد،
                //  وهو ما يحدث عند اختيار نفس المجلد لأن البيانات لم تتغير)
                refreshAdapterData();
            }
        });

        mSearchViewModel.getSearchQueryLiveData().observe(this, query -> {
            if (query != null) {
                // فلترة ثم تحديث الـ Adapter مرة واحدة فقط
                mSearchManager.filterFonts(query);
                if (mAdapter != null) {
                    mAdapter.updateFilteredFonts(
                        mSearchManager.getFilteredFonts(),
                        mSearchManager.getCurrentSearchQuery()
                    );
                }
            }
        });

        // ★ الإصلاح (مشكلة السكرول): استخدام setFontPreviewEnabled() بدلاً من smartUpdate() ★
        // smartUpdate() كانت تستدعي notifyItemRangeChanged() مما يُعيد تشغيل onBindViewHolder
        // لكل عنصر ويُقرأ الإعداد من DataStore مجدداً — وهو المصدر الأصلي للتقطيع.
        // setFontPreviewEnabled() تُحدِّث المتغير المحفوظ في الذاكرة مرة واحدة فقط،
        // ثم تستدعي smartUpdate() داخلياً للتحديث بعد التغيير — بدون قراءة DataStore. ★
        mSettingsViewModel.getFontPreviewEnabled().observe(this, enabled -> {
            if (mAdapter != null && isAdded()) {
                mAdapter.setFontPreviewEnabled(enabled); // استخدام الدالة الجديدة
                Log.d(TAG, "Font preview setting changed: " + enabled);
            }
        });

        // ★ مراقب الإشارة العامة للعمليات الضخمة ★
        // يستجيب لأي عملية ضخمة في التطبيق بالكامل بغض النظر عن الشاشة التي بدأت منها.
        // هذا يحل مشكلة اللاج عند الانتقال من سلة المحذوفات إلى الخطوط المحلية
        // أثناء عملية استعادة جارية: كان Fragment جديد يُنشأ بـ mIsBatchOperationRunning=false
        // فيستقبل مئات التحديثات متتاليةً مما يُجمّد المعالج. الآن يرصد الإشارة العامة
        // ويحجز البيانات بصمت حتى تنتهي العملية ثم يطبّقها دفعةً واحدة.
        //
        // ★ إصلاح (3): إذا كانت العملية جاريةً وعاد المستخدم لهذه الشاشة (عبر الإشعار)،
        //   يُعاد عرض الديالوج تلقائياً بشرط أن يكون هذا الـ Fragment هو المصدر. ★
        com.example.oneuiapp.utils.BatchOperationState.getIsProcessing().observe(this, isProcessing -> {
            mIsBatchOperationRunning = isProcessing;

            // ★ إصلاح (3): إغلاق الديالوج فور انتهاء العملية (التعديل الثالث من خطة الإصلاح) ★
            // يضمن أن الديالوج يُغلق تلقائياً حتى عند إعادة بناء التطبيق من الإشعار،
            // لأن callback انتهاء العملية في showMoveToTrashProgressDialog قد يُرسَل
            // إلى الشاشة القديمة المُدمَّرة وليس إلى الشاشة الجديدة.
            if (!isProcessing && mCurrentProgressDialog != null && mCurrentProgressDialog.isShowing()) {
                mCurrentProgressDialog.dismiss();
                mCurrentProgressDialog = null;
            }

            // ★ إصلاح (3): إذا كانت العملية جارية والشاشة مرئية والديالوج مغلق → افتحه ★
            // ★ الإصلاح الجوهري: getSourceScreen() == AppScreen.LOCAL_FONTS بدلاً من getSourceFragmentIndex() == 2 ★
            // يضمن صحة الفحص حتى لو تغيّر ترتيب الشاشات في مصفوفة mFragments
            if (isProcessing
                    && !isHidden()
                    && com.example.oneuiapp.utils.BatchOperationState.getSourceScreen() == AppScreen.LOCAL_FONTS
                    && (mCurrentProgressDialog == null || !mCurrentProgressDialog.isShowing())) {
                reconnectToProgressDialog();
            }

            if (!isProcessing && mPendingFontsUpdate != null) {
                // العملية انتهت! نُطبق التحديث المحجوز دفعة واحدة ليعمل أنيميشن واحد فقط
                mCurrentFontsList = new ArrayList<>(mPendingFontsUpdate);
                if (mAdapter != null) {
                    mAdapter.setAllFontsMetadata(mPendingFontsUpdate);
                    mAdapter.notifyAllFavoritesChanged();
                }
                refreshAdapterData();

                // ★ الإصلاح (المشكلة الأولى): تمّ حذف استدعاء preloadNewlyAddedFonts هنا أيضاً.
                //   نفس السبب: كانت تُسبب Over-emission Death Loop عند تحديث المجلد. ★

                updateMainActivityFontsCount(mCurrentFontsList.size());
                mPendingFontsUpdate = null;
            }
        });

        // ★ إصلاح (3): مراقبة آخر تقدم لتحديث الديالوج المُعاد فتحه ★
        // يُستدعى عند كل تحديث تقدم (من showMoveToTrashProgressDialog → progressListener)
        // فيحدّث mCurrentProgressDialog إذا كان مفتوحاً — سواء الديالوج الأصلي أو المُعاد فتحه.
        com.example.oneuiapp.utils.BatchOperationState.getProgress().observe(this, progressData -> {
            if (mCurrentProgressDialog != null
                    && mCurrentProgressDialog.isShowing()
                    && progressData != null) {
                mCurrentProgressDialog.setMax(progressData.total);
                mCurrentProgressDialog.setProgress(progressData.current);
            }
        });

        // ★ الخطوة الثالثة من إصلاح Menu State Corruption:
        //   تحديث رؤية زر الثلاث نقاط مباشرةً وبصمت عبر mMenu المحفوظ،
        //   بدلاً من invalidateOptionsMenu() الذي كان يدمر القائمة بأكملها
        //   (بما فيها SearchView المتمدد) ثم يُعيد بناءها من الصفر — مسبباً:
        //   الكيبورد لا يفتح، زر الرجوع يتعطل، والعنوان يعلق. ★
        mSearchViewModel.getIsSearchActiveLiveData().observe(this, isActive -> {
            // ✅ تحديث رؤية الزر بشكل صامت ومباشر دون تدمير القائمة
            if (mMenu != null) {
                MenuItem changeFolderItem = mMenu.findItem(R.id.action_change_folder);
                if (changeFolderItem != null) {
                    changeFolderItem.setVisible(!isActive && mViewModel.hasSavedFolder());
                }
            }
        });
    }

    private void restoreInstanceState(@NonNull Bundle state) {
        mUIManager.setRecyclerViewState(state.getParcelable("recycler_state"));

        // ★ تفعيل الاستعادة فقط إذا كانت هناك حالة تمرير محفوظة فعلاً ★
        // هذا هو المكان الوحيد المسموح فيه بضبط mNeedsScrollRestore على true.
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

        // ★ الإصلاح الجوهري: حُذف بلوك hasSavedSortState ★
        // السبب: كان يعمل بعد وصول البيانات من LiveData مما يُسبب "سباق زمني".
        // الحل: تهيئة الفرز داخل setupRecyclerView() مباشرةً بعد setAdapter()
        // لضمان جهوزية الـ Adapter قبل أي وصول للبيانات.

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

        // ★ ربط عنوان الحالة الفارغة — يُخفى تلقائياً عند البحث بلا نتائج ★
        mUIManager.setEmptyTitleView(view.findViewById(R.id.empty_title));

        // ★ الإضافة (تمييز الحالتين): ربط رسالة البحث بلا نتائج المستقلة ★
        // يُتيح تخصيص لون no_results_text وحجمه من الـ layout بشكل مستقل تماماً
        // عن empty_text، لأن كلًّا منهما يخدم سياقاً مختلفاً من منظور تجربة المستخدم.
        // - empty_text   → رسالة الفراغ الحقيقي (لا توجد خطوط في المجلد)
        // - no_results_text → رسالة البحث بلا نتائج (توجد خطوط لكن لا نتائج للبحث)
        mUIManager.setNoResultsTextView(view.findViewById(R.id.no_results_text));

        // ★ ربط مؤشر التحميل بالمدير (هذا السطر فقط في الخطوط المحلية) ★
        // لا تؤثر على SystemFontListFragment ولا FavoriteFontListFragment
        // لأنهما لا يستدعيان setLoadingContainer() على الإطلاق.
        mUIManager.setLoadingContainer(view.findViewById(R.id.loading_container));

        mUIManager.updateUIVisibility(mViewModel.hasSavedFolder());

        // ★ إصلاح المشكلة الثانية: إخبار MainActivity بحالة المجلد عند تهيئة الـ View ★
        // يضمن ظهور زر "تغيير مجلد الخطوط" في الـ Toolbar إذا كان مجلد محفوظ بالفعل
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

        // ★ إصلاح (المشكلة 2): زيادة عدد العناصر المجهزة مسبقاً في الذاكرة لتحسين سلاسة التمرير ★
        // يُقلّل من التوقف أثناء التمرير الأول عبر القائمة الكبيرة عن طريق إبقاء
        // 20 عنصراً إضافياً جاهزة في الكاش بدلاً من إعادة إنشائها عند ظهورها.
        mRecyclerView.setItemViewCacheSize(20);

        mAdapter = new LocalFontListAdapter(mContext, mExecutor);

        // ★ التعديل: استقبال weightWidthLabel كمعامل خامس وتمريره إلى mFontSelectedListener ★
        // ★ الخطوة الثانية من إصلاح مشكلة السكرول:
        //   تسجيل الاستخدام في قاعدة البيانات يتم هنا عند النقر الفعلي على الخط فقط.
        //   بذلك تتوقف Room عن إرسال تحديثات LiveData أثناء التمرير،
        //   ويصبح التمرير سلساً من التمريرة الأولى. ★
        mAdapter.setFontClickListener((fontPath, realName, fileName, ttcIndex, weightWidthLabel) -> {
            // ✅ تسجيل الاستخدام في قاعدة البيانات عند النقر فقط (وليس أثناء التمرير)
            mViewModel.recordFontAccess(fontPath);

            if (mFontSelectedListener != null) {
                mFontSelectedListener.onFontSelected(fontPath, realName, fileName,
                                                     ttcIndex, weightWidthLabel);
            }
        });

        // ★ عند الضغط على شريط الفرز: حفظ التفضيل في DataStore عبر SortManager
        // SortManager يُشعر مستمعه (setupSortListener) الذي يستدعي mAdapter.setSortOptions → أنيميشن ★
        mAdapter.setSortChangeListener((type, asc) -> {
            mSortManager.setSortOptions(type, asc);
        });

        // ★ FavoriteStatusProvider: يُزوّد الـ Adapter بحالة المفضلة لكل مسار خط ★
        // يُستخدم لعرض أيقونة النجمة الصفراء (ic_favorite) بجانب العناصر المفضلة
        // في قائمة الخطوط المحلية بجانب ظهورها في قائمة المفضلة.
        // mCurrentFontsList تحتوي على isFavorite المُحدَّث من Room LiveData تلقائياً.
        mAdapter.setFavoriteStatusProvider(fontPath -> {
            for (LocalFontListViewModel.FontFileInfoWithMetadata font : mCurrentFontsList) {
                if (font.getPath().equals(fontPath)) {
                    return font.isFavorite();
                }
            }
            return false;
        });

        mRecyclerView.setAdapter(mAdapter);

        // ★★★ الإصلاح السحري: إخبار الـ Adapter بنوع الفرز المحفوظ قبل أن يستلم أي بيانات ★★★
        // يضمن هذا أنه عندما تصل البيانات من LiveData، سيرتبها الـ SortedList مباشرةً
        // بالمعيار الصحيح بدون أي سباق زمني بين وصول البيانات وتهيئة الفرز
        mAdapter.updateSortOptionsOnly(
            mSortManager.getCurrentSortType(),
            mSortManager.isSortAscending()
        );

        // ★ استدعاء دالة الأنيميشن المركزية بدلاً من كتابة الكود هنا مباشرة ★
        // هذا يسمح بإعادة تهيئة الأنيميشن بسهولة عند العودة للشاشة بعد إيقافه
        setupRecyclerViewAnimator();

        mRecyclerView.seslSetFillBottomEnabled(false);
        mRecyclerView.seslSetLastRoundedCorner(false);
        mRecyclerView.seslSetFastScrollerEnabled(false);
        mRecyclerView.seslSetIndexTipEnabled(false);
        mRecyclerView.seslSetGoToTopEnabled(true);
        mRecyclerView.seslSetSmoothScrollEnabled(true);
    }

    /**
     * ★ دالة مركزية لتهيئة أنيميشن الـ RecyclerView ★
     * تُستدعى عند الإنشاء الأول وعند العودة للشاشة بعد إيقاف الأنيميشن.
     *
     * ★ إصلاح الأنيميشن الجماعي:
     *   setRemoveDuration(0) → يُختفي العناصر المحذوفة فوراً بلا تأخير،
     *   ثم تصعد العناصر المتبقية بأنيميشن سلس عبر setMoveDuration(250).
     *   هذا يمنع الوميض المتتابع عند حذف مئات العناصر دفعةً واحدة. ★
     */
    private void setupRecyclerViewAnimator() {
        if (mRecyclerView == null) return;
        androidx.recyclerview.widget.DefaultItemAnimator animator =
            new androidx.recyclerview.widget.DefaultItemAnimator();
        animator.setAddDuration(150);
        // ★ صفر: اختفاء فوري للعناصر المحذوفة بلا وميض متتابع ★
        animator.setRemoveDuration(250);
        // ★ 250ms: صعود سلس للعناصر المتبقية لتملأ الفراغ ★
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

        // ★ FavoriteStatusChecker: يُزوّد SelectionManager بحالة المفضلة لكل موضع ★
        // يُطبَّق منطق Samsung Notes بناءً على هذه القيم:
        //   - كل العناصر المحددة مفضلة   → يُعرض "إزاله من المفضله"
        //   - مختلطة أو كلها غير مفضلة  → يُعرض "إضافه إلى المفضله"
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

            /**
             * ★ إجراء المفضلة — يُستدعى من SelectionManager بعد تحديد نوع العملية ★
             * addToFavorites يأتي جاهزاً من resolveFavoriteAction() في SelectionManager
             * وفق منطق Samsung Notes المُطبَّق هناك.
             */
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

    /**
     * ★ التعديل (سلة المحذوفات): استبدال الحذف النهائي بالنقل إلى سلة المحذوفات ★
     *
     * الخطوات:
     *   1. جمع مسارات العناصر المحددة.
     *   2. عرض ديالوج تأكيد النقل إلى السلة بدون عنوان (الملاحظة 16):
     *      - رسالة بصيغة الجمع من plurals.dialog_move_to_trash_question.
     *      - زران: إلغاء و نقل إلى سلة المحذوفات.
     *   3. عند تأكيد النقل → استدعاء showMoveToTrashProgressDialog().
     *
     * ملاحظة: لم يعد هذا الأسلوب يستدعي FontActionDialogs.showDeleteDialog()
     * لأن تصميم الديالوج مختلف تماماً عن ديالوج الحذف القديم.
     */
    private void handleDelete(List<Integer> positions) {
        if (positions == null || positions.isEmpty()) return;

        // ★ جمع المسارات أولاً قبل فتح الديالوج (positions صالحة الآن فقط) ★
        List<String> pathsToMove = new ArrayList<>();
        for (int position : positions) {
            String path = mAdapter.getFilePath(position);
            if (path != null) pathsToMove.add(path);
        }

        if (pathsToMove.isEmpty()) return;

        int count = pathsToMove.size();

        // ★ ديالوج تأكيد النقل إلى سلة المحذوفات (الملاحظة 16) ★
        // - بدون عنوان
        // - رسالة بصيغة الجمع بحسب عدد الملفات
        // - زر إيجابي: نقل إلى سلة المحذوفات
        // - زر سلبي: إلغاء
        String message = getResources().getQuantityString(
                R.plurals.dialog_move_to_trash_question, count, count);

        AlertDialog confirmDialog = new AlertDialog.Builder(mContext)
                .setMessage(message)
                .setPositiveButton(R.string.action_move_to_trash, null) // null — نضبطه يدوياً بعد show()
                .setNegativeButton(R.string.action_cancel, null)
                .create();

        confirmDialog.setOnShowListener(d -> {
            // ★ ضبط المستمع يدوياً لمنع الديالوج من الإغلاق التلقائي عند الضغط ★
            // مما يُتيح عرض ديالوج التقدم أولاً ثم إغلاق ديالوج التأكيد
            confirmDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                confirmDialog.dismiss();
                showMoveToTrashProgressDialog(pathsToMove);
            });
        });

        confirmDialog.show();
    }

    /**
     * ★ عرض ديالوج التقدم وتنفيذ النقل إلى سلة المحذوفات (الملاحظة 18) ★
     *
     * التصميم مستلهم من AppPickerActivity.loadApps():
     * - ProgressDialog.STYLE_HORIZONTAL (مكتبة OneUI تتكفل بعرض X/Y والنسبة المئوية تلقائياً).
     * - زران:
     *     • BUTTON_NEGATIVE (إلغاء) → يوقف العملية عبر mViewModel.cancelTrashOperation().
     *     • BUTTON_NEUTRAL  (إخفاء الإطار المنبثق) → يُغلق الديالوج والعملية تكمل في الخلفية.
     *
     * ★ الإصلاح الجوهري (الخطوة الأولى + الثالثة من خطة الإصلاح):
     *   setSourceScreen(AppScreen.LOCAL_FONTS) يُستدعى قبل showMoveToTrashNotification().
     *   يضمن أن PendingIntent في الإشعار يحمل الشاشة الصحيحة (AppScreen.LOCAL_FONTS)
     *   بدلاً من الرقم الخاطئ الموروث من عملية سابقة (مثلاً 5=سلة المحذوفات).
     *   BatchOperationState.setSourceScreen() آمن ضد تغيير ترتيب الشاشات. ★
     *
     * ★ إصلاح (6): عنوان الديالوج بصيغة عامة — getQuantityString بمعامل واحد (بدون %d) ★
     *   - count = 1  → "Moving 1 file to Trash…" / "جارٍ نقل ملف واحد إلى سلة المحذوفات…"
     *   - count > 1  → "Moving files to Trash…"  / "جارٍ نقل الملفات إلى سلة المحذوفات…"
     *   لأن عداد X/Y يظهر بالفعل أسفل شريط التقدم تلقائياً في مكتبة OneUI.
     *
     * ★ إصلاح (1)(2): الإشعار يُظهر فوراً قبل عرض الديالوج — بغض النظر عن أي زر يضغطه المستخدم ★
     *   سابقاً: لم يكن هناك أي إشعار في هذا الـ Fragment على الإطلاق.
     *   الآن: TrashActionDialogs.showMoveToTrashNotification() يُستدعى قبل progressDialog.show().
     *
     * ★ إصلاح (3): مرجع الديالوج يُحفظ في mCurrentProgressDialog (حقل الـ Fragment) ★
     *   يُتيح هذا إعادة فتحه عبر reconnectToProgressDialog() عند العودة من الإشعار.
     *
     * ★ إصلاح (3): BatchOperationState.updateProgress() يُستدعى في progressListener ★
     *   يحفظ آخر تقدم في الحالة المشتركة، فيقرأه reconnectToProgressDialog() عند
     *   إعادة إنشاء الديالوج لإظهار قيمة محدَّثة بدلاً من البدء من الصفر.
     *
     * ★ إصلاح اللاج (Global State Interception):
     *   لم يعد هذا الـ Fragment يضبط mIsBatchOperationRunning يدوياً.
     *   TrashRepository يُفعّل BatchOperationState.setProcessing(true) عند بدء العملية،
     *   فيستجيب مراقب getIsProcessing() في setupViewModelObservers() ويضبط
     *   mIsBatchOperationRunning=true تلقائياً على جميع الشاشات بما فيها هذه.
     *   عند انتهاء العملية، يُطبَّق mPendingFontsUpdate دفعةً واحدة بأنيميشن سلس. ★
     *
     * @param pathsToMove قائمة مسارات الخطوط المراد نقلها (مجمَّعة مسبقاً في handleDelete)
     */
    private void showMoveToTrashProgressDialog(@NonNull List<String> pathsToMove) {
        int count = pathsToMove.size();

        // ★ الإصلاح الجوهري (الخطوة الأولى + الثالثة من خطة الإصلاح):
        //   setSourceScreen(AppScreen.LOCAL_FONTS) بدلاً من setSourceFragmentIndex(2) ★
        // يضمن صحة التوجيه من الإشعار إلى الشاشة الصحيحة حتى لو تغيّر ترتيب الشاشات.
        // يجب أن يسبق showMoveToTrashNotification() لأن PendingIntent يُبنى أثناءها.
        com.example.oneuiapp.utils.BatchOperationState.setSourceScreen(AppScreen.LOCAL_FONTS);

        // ★ إغلاق وضع التحديد قبل عرض الديالوج لتجنب تعارض الواجهات ★
        mSelectionManager.setSelecting(false);

        // ─── إنشاء ديالوج التقدم ───────────────────────────────────────────
        // ★ إصلاح (3): استخدام mCurrentProgressDialog (حقل الـ Fragment) بدلاً من متغير محلي ★
        // يُتيح هذا إعادة ربطه من checkAndReopenProgressDialogPublic() عند العودة من الإشعار.
        mCurrentProgressDialog = new ProgressDialog(mContext);
        mCurrentProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mCurrentProgressDialog.setCancelable(false);

        // ★ إصلاح (6): getQuantityString بمعامل واحد فقط — بدون %d ★
        // الأرقام التفصيلية (X/Y) تظهر تلقائياً أسفل الشريط في مكتبة OneUI
        mCurrentProgressDialog.setTitle(getResources().getQuantityString(
                R.plurals.progress_moving_to_trash, count));
        mCurrentProgressDialog.setMax(count);

        // ★ زر إلغاء — يوقف العملية الجارية ويُغلق الديالوج ★
        mCurrentProgressDialog.setButton(
                ProgressDialog.BUTTON_NEGATIVE,
                getString(R.string.action_cancel),
                (dialog, which) -> {
                    mViewModel.cancelTrashOperation();
                    // إشعار الإلغاء عبر BatchOperationState أيضاً (طريق مزدوج آمن)
                    com.example.oneuiapp.utils.BatchOperationState.requestCancel();
                    // الإشعار والخدمة تُوقَفان بواسطة callback اكتمال العملية في ViewModel
                    dialog.dismiss();
                });

        // ★ زر إخفاء الإطار المنبثق — يُغلق الديالوج والعملية تكمل في الخلفية ★
        // الإشعار موجود بالفعل منذ بداية العملية — لا حاجة لإعادة إنشائه
        mCurrentProgressDialog.setButton(
                ProgressDialog.BUTTON_POSITIVE,
                getString(R.string.action_hide_dialog),
                (dialog, which) -> {
                    // العملية تكمل في الخلفية، الإشعار يُبقي المستخدم على اطلاع
                    dialog.dismiss();
                });

        // ★ إصلاح (1)(2): إظهار الإشعار فوراً — قبل عرض الديالوج ★
        // يضمن وجود الإشعار في شريط الحالة بمجرد بدء العملية، سواء أغلق المستخدم
        // الديالوج أم أبقاه مفتوحاً أم خرج من التطبيق. getSourceScreen()
        // الآن يُعيد AppScreen.LOCAL_FONTS (مضبوط في أول هذه الدالة) فيُبنى PendingIntent الصحيح.
        TrashActionDialogs.showMoveToTrashNotification(mContext, count);

        mCurrentProgressDialog.show();

        // ─── مُستمع التقدم ─────────────────────────────────────────────────
        // ★ يُستدعى من خيط الخلفية في TrashRepository بعد كل ملف ★
        // mMainHandler يُعيد تحديث الديالوج إلى الخيط الرئيسي بأمان.
        // ★ إصلاح (5): تحديث الإشعار أيضاً من نفس الخيط — يضمن عدم التجمد عند الخروج ★
        // ★ إصلاح (3): تحديث BatchOperationState.updateProgress() — يُحفظ آخر تقدم لإعادة ★
        //   عرضه في reconnectToProgressDialog() عند العودة من الإشعار.
        TrashRepository.OnProgressListener progressListener = (current, total) -> {
            // ★ (3): تحديث الحالة المشتركة لإتاحة إعادة عرض الديالوج بقيمة محدَّثة ★
            String progressTitle = getResources().getQuantityString(
                    R.plurals.progress_moving_to_trash, total);
            com.example.oneuiapp.utils.BatchOperationState.updateProgress(
                    current, total, progressTitle);

            // ★ (5): تحديث الإشعار مباشرةً من خيط الخلفية — مستقل عن حالة الديالوج ★
            // لا يتأثر بما إذا كان التطبيق في المقدمة أو الخلفية
            TrashActionDialogs.updateMoveToTrashNotification(mContext, current, total);

            // تحديث الديالوج على الخيط الرئيسي
            mMainHandler.post(() -> {
                if (mCurrentProgressDialog != null && mCurrentProgressDialog.isShowing()) {
                    mCurrentProgressDialog.setProgress(current);
                }
            });
        };

        // ─── تنفيذ العملية ─────────────────────────────────────────────────
        mViewModel.moveFontsToTrashInMemory(pathsToMove, progressListener, () -> {
            // ★ يُستدعى على الخيط الرئيسي عند انتهاء العملية (نجاح أو إلغاء) ★
            // بعد الحد الأدنى 2500ms المُطبَّق في ViewModel لمنع وميض الديالوج
            if (mCurrentProgressDialog != null && mCurrentProgressDialog.isShowing()) {
                mCurrentProgressDialog.dismiss();
            }
            mCurrentProgressDialog = null;

            // ★ الإشعار والخدمة يُوقَفان بواسطة ViewModel قبل استدعاء هذا الـ callback ★
            // نستدعيها هنا احتياطاً فقط في حال استخدام Context مختلف عن Context الـ ViewModel
            if (mContext != null) {
                TrashActionDialogs.dismissMoveToTrashNotification(mContext);
            }

            // ★ لا حاجة لتطبيق mPendingFontsUpdate هنا يدوياً ★
            // مراقب BatchOperationState.getIsProcessing() في setupViewModelObservers()
            // يتولى ذلك تلقائياً فور أن يُرسل TrashRepository إشارة false عند انتهاء العملية.
        });
    }

    // ════════════════════════════════════════════════════════════════════════
    // ★ إصلاح (3): إعادة فتح ديالوج التقدم عند العودة من الإشعار
    // ════════════════════════════════════════════════════════════════════════

    /**
     * ★ إصلاح (3): التحقق من وجود عملية جارية وإعادة عرض الديالوج إذا لزم ★
     *
     * تُستدعى من:
     *   - onResume() عند استئناف التطبيق من الخلفية (مثلاً: بعد الضغط على الإشعار)
     *   - onHiddenChanged(false) عند الانتقال لهذه الشاشة عبر درج التنقل
     *   - MainActivity عند الضغط على الإشعار وكانت الشاشة الصحيحة مفتوحة بالفعل
     *
     * الشروط لإعادة العرض:
     *   1. الـ Fragment مرئي (غير مخفي) — لمنع عرض الديالوج خلف شاشة أخرى
     *   2. عملية جارية (BatchOperationState.getIsProcessing() = true)
     *   3. ★ الإصلاح الجوهري: العملية من AppScreen.LOCAL_FONTS بدلاً من رقم 2 ★
     *      يضمن صحة الفحص حتى لو تغيّر ترتيب الشاشات
     *   4. ★ حل المشكلة (3): الفتح تم عبر الإشعار (consumeShouldReopenDialog() = true)
     *   5. لا يوجد ديالوج تقدم مرئي حالياً
     */
    public void checkAndReopenProgressDialogPublic() {
        // ★ الشرط 1: الـ Fragment مرئي فعلاً ★
        if (isHidden() || !isAdded() || mContext == null) return;

        // ★ الشرط 2: عملية جارية ★
        Boolean isProcessing = com.example.oneuiapp.utils.BatchOperationState
                .getIsProcessing().getValue();
        if (!Boolean.TRUE.equals(isProcessing)) return;

        // ★ الشرط 3 (الإصلاح الجوهري): العملية من AppScreen.LOCAL_FONTS بدلاً من getSourceFragmentIndex() != 2 ★
        // يمنع عرض ديالوج هنا إذا كانت العملية استعادة/حذف من سلة المحذوفات (AppScreen.TRASH)
        if (com.example.oneuiapp.utils.BatchOperationState.getSourceScreen() != AppScreen.LOCAL_FONTS) return;

        // ★ حل المشكلة (3): لا تفتح الديالوج إلا إذا تم استهلاك علامة الإشعار
        if (!com.example.oneuiapp.utils.BatchOperationState.consumeShouldReopenDialog()) return;

        // ★ الشرط 4: لا يوجد ديالوج مرئي حالياً ★
        if (mCurrentProgressDialog != null && mCurrentProgressDialog.isShowing()) return;

        Log.d(TAG, "checkAndReopenProgressDialog: reopening dialog for ongoing operation");
        reconnectToProgressDialog();
    }

    /**
     * ★ إصلاح (3): إنشاء وعرض ديالوج تقدم جديد مرتبط بالعملية الجارية ★
     *
     * يُستدعى من checkAndReopenProgressDialogPublic() عند العودة للتطبيق من الإشعار.
     *
     * يستعيد آخر قيمة تقدم من BatchOperationState.getProgress() لتجنب
     * ظهور الشريط فارغاً عند إعادة الفتح.
     *
     * ★ إصلاح (4): تجاهل lastProgress.title واستبداله بنص مبني محلياً ★
     * يضمن ظهور عنوان الديالوج باللغة المختارة في التطبيق (الإنجليزية)
     * بدلاً من اللغة المحفوظة في BatchOperationState (المبنية على سياق Application
     * الذي يقرأ لغة النظام بدلاً من لغة التطبيق المحددة).
     *
     * أزرار الديالوج المُعاد:
     *   - "إلغاء": يستدعي BatchOperationState.requestCancel() وmViewModel.cancelTrashOperation()
     *   - "إخفاء": يُغلق الديالوج والعملية تكمل (الإشعار يُبلّغ عن التقدم)
     */
    private void reconnectToProgressDialog() {
        if (!isAdded() || mContext == null) return;

        mCurrentProgressDialog = new ProgressDialog(mContext);
        mCurrentProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mCurrentProgressDialog.setCancelable(false);

        // ★ استرداد آخر تقدم معروف لتعيين العنوان والقيمة بدلاً من البدء من الصفر ★
        com.example.oneuiapp.utils.BatchOperationState.ProgressData lastProgress =
                com.example.oneuiapp.utils.BatchOperationState.getProgress().getValue();

        if (lastProgress != null) {
            // ★ إصلاح (4): تجاهل lastProgress.title واستخدام getResources() المحلي ★
            // lastProgress.title مبني بسياق Application الذي يقرأ لغة النظام (العربية)
            // بدلاً من لغة التطبيق المختارة (الإنجليزية). إعادة البناء هنا بـ getResources()
            // يضمن ظهور النص بالسياق الصحيح الملفوف باللغة المحددة في التطبيق.
            String localTitle = getResources().getQuantityString(
                    R.plurals.progress_moving_to_trash, lastProgress.total);
            mCurrentProgressDialog.setTitle(localTitle);
            mCurrentProgressDialog.setMax(lastProgress.total);
            mCurrentProgressDialog.setProgress(lastProgress.current);
        } else {
            // عنوان احتياطي إذا لم تكن هناك بيانات تقدم محفوظة بعد
            mCurrentProgressDialog.setTitle(getResources().getQuantityString(
                    R.plurals.progress_moving_to_trash, 1));
        }

        // ★ زر إلغاء: يوقف العملية عبر مسارين آمنين ★
        // requestCancel() يضبط _currentCancelFlag المُسجَّل في BatchOperationState
        // وهو نفسه trashCancelFlag الذي يتحقق منه TrashRepository في حلقة المعالجة.
        mCurrentProgressDialog.setButton(
                ProgressDialog.BUTTON_NEGATIVE,
                getString(R.string.action_cancel),
                (dialog, which) -> {
                    com.example.oneuiapp.utils.BatchOperationState.requestCancel();
                    mViewModel.cancelTrashOperation();
                    dialog.dismiss();
                });

        // ★ زر إخفاء: يُغلق الديالوج والعملية تكمل — الإشعار يُبلّغ عن التقدم ★
        mCurrentProgressDialog.setButton(
                ProgressDialog.BUTTON_POSITIVE,
                getString(R.string.action_hide_dialog),
                (dialog, which) -> dialog.dismiss());

        mCurrentProgressDialog.show();
        Log.d(TAG, "reconnectToProgressDialog: progress dialog reopened successfully");
    }

    /**
     * ★ إجراء المفضلة في قائمة الخطوط المحلية ★
     *
     * يُطبّق الإضافة أو الإزالة من المفضلة على العناصر المحددة.
     * Room LiveData يُحدَّث تلقائياً → مراقب getFontsLiveData() يُجدّد mCurrentFontsList
     * → notifyAllFavoritesChanged() يُحدّث أيقونات النجمة بالبيانات الصحيحة.
     *
     * ملاحظة للمطوّر: الاستدعاء الاحترازي لـ notifyAllFavoritesChanged() في callback
     * هذه الدالة يُحدِّث الأيقونات بسرعة، لكنه قد يقرأ قيماً قديمة إذا سبق وصول LiveData.
     * الاستدعاء الثاني في مراقب getFontsLiveData() هو الضمان النهائي للصحة.
     *
     * @param positions     مواضع العناصر المحددة في الـ Adapter
     * @param addToFavorites true = إضافة إلى المفضلة، false = إزالة منها
     */
    private void handleFavoriteAction(List<Integer> positions, boolean addToFavorites) {
        if (positions == null || positions.isEmpty()) return;

        // جمع مسارات العناصر المحددة
        List<String> paths = new ArrayList<>();
        for (int position : positions) {
            String path = mAdapter.getFilePath(position);
            if (path != null) paths.add(path);
        }

        if (paths.isEmpty()) return;

        mSelectionManager.setSelecting(false);

        // ★ تحديث قاعدة البيانات في الخلفية عبر ViewModel ★
        // toggleFavoritesBatch يستدعي updateFavoriteStatusBatch في Repository
        // الذي يُحدّث Room → LiveData يُحدَّث تلقائياً → مراقب getFontsLiveData()
        // يُجدّد mCurrentFontsList → notifyAllFavoritesChanged() يُحدّث الأيقونات بصحة تامة.
        mViewModel.toggleFavoritesBatch(paths, addToFavorites, () -> {
            // ★ استدعاء احترازي: يُحدّث الأيقونات فوراً إن كانت LiveData قد وصلت ★
            // في حالة تأخر LiveData، سيُكمل المراقب في setupViewModelObservers() العمل.
            if (mAdapter != null) {
                mAdapter.notifyAllFavoritesChanged();
            }
        });
    }

    public boolean handleBackPressed() {
        if (mSelectionManager != null) return mSelectionManager.handleBackPress();
        return false;
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
        // ★ الإصلاح: تحديث العدد فقط إذا كان هذا الـ Fragment ظاهراً للمستخدم
        // هذا يمنع الكتابة فوق العدد الصحيح عند استئناف التطبيق ★
        if (!isHidden()) {
            updateMainActivityFontsCount(mCurrentFontsList.size());

            // ★ إصلاح (3): التحقق من عملية جارية عند استئناف التطبيق ★
            // يُغطي سيناريو: المستخدم يضغط على الإشعار → يُفتح التطبيق → MainActivity
            // تُنقله لشاشة الخطوط المحلية → onResume() يتحقق ويُعيد عرض الديالوج إذا
            // كانت العملية لا تزال جارية. يعمل أيضاً عند تدوير الشاشة مع عملية جارية.
            checkAndReopenProgressDialogPublic();
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);

        // ★ المرحلة الأولى: إدارة ظهور أيقونات AppBar عند التنقل بين الشاشات ★
        // setMenuVisibility يُخفي أيقونات هذا الـ Fragment عند إخفائه ويُظهرها عند عودته،
        // وinvalidateOptionsMenu يُجبر الـ AppBar على إعادة رسم الأيقونات فور ظهور الشاشة
        setMenuVisibility(!hidden);
        if (!hidden && getActivity() != null) {
            getActivity().invalidateOptionsMenu(); // إجبار الـ AppBar على التحديث
        }

        if (hidden) {
            // ★ 1. حفظ موضع التمرير فوراً قبل إخفاء الشاشة وقبل أي تحديث للـ Adapter ★
            mUIManager.saveRecyclerViewState();

            // ★ 2. إيقاف الأنيميشن فوراً لنزع قدرة القائمة على الحركة في الخلفية ★
            // هذا يضمن أن أي تحديثات تحدث في الخلفية (مثل إعادة الترتيب عند إغلاق البحث)
            // تُطبَّق بصمت تام دون أن يراها المستخدم عند العودة
            if (mRecyclerView != null) {
                mRecyclerView.setItemAnimator(null);
            }

            mSearchViewModel.deactivateSearch();
            if (mSelectionManager != null && mSelectionManager.isSelecting()) {
                mSelectionManager.setSelecting(false);
            }
        } else {
            // ★ إعادة تفعيل اللمس وإعادة تفعيل الحارس لقبول النقرات مجدداً ★
            unblockTouch();

            // ★ إعادة رسم القائمة عند العودة لإظهار تمييز آخر خط تم فتحه ★
            if (mAdapter != null) mAdapter.smartUpdate();

            updateMainActivityFontsCount(mCurrentFontsList.size());

            // ★ 3. استعادة موضع التمرير بعد ظهور الشاشة مع تأجيل بـ post()
            // لضمان اكتمال layout قبل تطبيق الاستعادة ★
            mMainHandler.post(() -> mUIManager.restoreRecyclerViewState());

            // ★ 4. الضربة القاضية للأنيميشن: تأخير إعادته 100 ملي ثانية ★
            // هذا يضمن أن الـ RecyclerView قد رسم العناصر في مواضعها النهائية بدون حركة،
            // وبعد ذلك فقط نعيد الأنيميشن للاستخدام الطبيعي
            if (mRecyclerView != null) {
                mRecyclerView.postDelayed(() -> {
                    if (isAdded() && !isHidden()) {
                        setupRecyclerViewAnimator();
                    }
                }, 100);
            }

            // ★ إصلاح (3): التحقق من عملية جارية عند الانتقال لهذه الشاشة ★
            // يُغطي سيناريو: المستخدم يضغط على الإشعار → MainActivity تُنقله
            // لشاشة الخطوط المحلية → onHiddenChanged(false) يتحقق ويُعيد عرض الديالوج.
            checkAndReopenProgressDialogPublic();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (!mLocalFontDirectoryPicker.handleActivityResult(requestCode, resultCode, data)) {
            if (mLocalFontPermissionManager.handleActivityResult(requestCode)) {
                mLocalFontDirectoryPicker.openDirectoryPicker();
            }
        }
    }

    private void updateMainActivityFontsCount(int count) {
        // ★ الإصلاح 1 (المشكلة 4): لا تُحدّث العنوان الفرعي إذا كان وضع التحديد نشطاً ★
        // هذا يمنع ظهور عدد الخطوط في العنوان الفرعي أثناء وضع التحديد عند العودة للتطبيق
        if (mSelectionManager != null && mSelectionManager.isSelecting()) return;

        // ★ الإصلاح الجوهري (الخطوة الثالثة من خطة الإصلاح):
        //   تمرير AppScreen.LOCAL_FONTS بدلاً من الرقم الصلب 2 ★
        // يضمن تمييز هذا الفراجمنت بالاسم لا بالرقم، فلا يتأثر بتغيير ترتيب الشاشات
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).updateFontsCount(AppScreen.LOCAL_FONTS, count);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // ★ إصلاح المشكلة الثانية: دوال إدارة حالة زر "تغيير مجلد الخطوط" ★
    // ════════════════════════════════════════════════════════════════════════

    /**
     * ★ إصلاح المشكلة الثانية: دالة عامة لفتح منتقي المجلدات من MainActivity ★
     *
     * تُستدعى من onOptionsItemSelected() عند اختيار "تغيير مجلد الخطوط" من الـ AppBar.
     * تتحقق من الإذن أولاً: إذا كان ممنوحاً تفتح منتقي المجلدات مباشرةً،
     * وإلا تطلب الإذن (الذي عند منحه يستدعي منتقي المجلدات تلقائياً).
     */
    public void openFolderPickerPublic() {
        if (mLocalFontPermissionManager.hasRequiredPermissions()) {
            mLocalFontDirectoryPicker.openDirectoryPicker();
        } else {
            mLocalFontPermissionManager.requestPermissions();
        }
    }

    /**
     * ★ إصلاح المشكلة الثانية: إخبار MainActivity بحالة المجلد ★
     *
     * يُستدعى في حالتين:
     *   1. عند اختيار مجلد بنجاح في setupDirectoryPickerListener() → hasFolder = true
     *   2. في نهاية initializeViews() بقيمة mViewModel.hasSavedFolder() →
     *      يُظهر الزر إذا كان مجلد محفوظ بالفعل، أو يُخفيه إذا لم يكن كذلك.
     *
     * @param hasFolder true إذا كان مجلد خطوط محدداً، false إذا لم يكن كذلك
     */
    private void updateMainActivityFolderState(boolean hasFolder) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setLocalFolderSelected(hasFolder);
        }
    }

    /**
     * ★ يُغذّي الـ Adapter بالبيانات الخام بدون فرز مسبق.
     * SortedList داخل الـ Adapter يتولى الترتيب وتوليد الأنيميشن المناسب.
     */
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
            // ★ الإصلاح هنا: إخبار مدير الواجهة بإظهار الشاشة الفارغة فوراً ★
            // كان هذا السطر مفقوداً مما يسبب تأخر ظهور شاشة "لا توجد خطوط"
            // حتى يتم فتح البحث أو التنقل لشاشة أخرى والعودة.
            mUIManager.updateEmptyView(true, mSearchManager.isSearchActive());
            return;
        }

        // بناء قائمة FontFileInfo — بدون استدعاء sortFontsList ★
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
            // SortedList يرتب القائمة تلقائياً حسب currentSortType/currentSortAscending
            mAdapter.updateFilteredFonts(
                mSearchManager.getFilteredFonts(),
                mSearchManager.getCurrentSearchQuery()
            );
            // تحديث الهيدر فقط ليعكس الحالة الحالية
            mAdapter.updateSortOptionsOnly(
                mSortManager.getCurrentSortType(),
                mSortManager.isSortAscending()
            );
        }

        // ★ الإصلاح هنا أيضاً: إخبار مدير الواجهة بإخفاء الشاشة الفارغة (لأن القائمة ليست فارغة) ★
        mUIManager.updateEmptyView(false, mSearchManager.isSearchActive());

        // ★ الإصلاح الجذري لمشكلة القفز: استعادة موضع التمرير فقط عند أول وصول للبيانات
        // بعد إعادة البناء من الـ bundle. هذا يمنع القفز عند النقر على خط (الذي يُحدّث قاعدة البيانات).
        if (mNeedsScrollRestore) {
            mNeedsScrollRestore = false; // استهلاك العلامة
            mMainHandler.post(() -> {
                if (isAdded() && getView() != null) {
                    mUIManager.restoreRecyclerViewState();
                }
            });
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // ★ دوال البحث — تُستدعى من SearchCoordinator
    // ════════════════════════════════════════════════════════════════════════

    /**
     * ★ يُستدعى من MainActivity عند تمدد أو طي حقل البحث بصرياً.
     * يضمن مزامنة SearchViewModel مع الحالة البصرية فوراً، مما يخفي/يظهر
     * زر الثلاث نقاط في نفس اللحظة. ★
     */
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
        mSearchViewModel.activateSearch();
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

        // ★ تنظيف البيانات المحجوزة عند تدمير الـ View لمنع تسرب الذاكرة ★
        mPendingFontsUpdate = null;
        mIsBatchOperationRunning = false;

        // ★ إصلاح (3): إغلاق ديالوج التقدم إن كان لا يزال مفتوحاً عند تدمير الـ View ★
        // يمنع WindowLeakedException الناتج عن ديالوج مرتبط بـ Context محذوف.
        // ملاحظة: العملية تكمل في الخلفية عبر ViewModel — فقط الديالوج يُغلق هنا.
        if (mCurrentProgressDialog != null && mCurrentProgressDialog.isShowing()) {
            try {
                mCurrentProgressDialog.dismiss();
            } catch (Exception e) {
                Log.w(TAG, "onDestroyView: failed to dismiss progress dialog", e);
            }
        }
        mCurrentProgressDialog = null;

        // ★ الخطوة الخامسة من إصلاح Menu State Corruption:
        //   تنظيف مرجع القائمة لمنع تسرب الذاكرة عند تدمير الـ View ★
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
                            }
