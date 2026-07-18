package com.example.oneuiapp.fragment.favorite;

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

import com.example.oneuiapp.R;
import com.example.oneuiapp.activity.AppScreen;           // ★ الإصلاح الجوهري: استيراد AppScreen ★
import com.example.oneuiapp.activity.MainActivity;
import com.example.oneuiapp.fragment.trash.data.TrashRepository;
import com.example.oneuiapp.dialog.FontActionDialogs;
import com.example.oneuiapp.data.entity.FontFileInfo;
import com.example.oneuiapp.sort.FontSortManager;
import com.example.oneuiapp.utils.FontUIStateManager;
import com.example.oneuiapp.utils.FontItemDecoration;
import com.example.oneuiapp.fragment.localfont.adapter.LocalFontListAdapter;
import com.example.oneuiapp.fragment.localfont.data.LocalFontCache;
import com.example.oneuiapp.fragment.localfont.manager.LocalFontSelectionManager;
import com.example.oneuiapp.search.FontSearchManager;
import com.example.oneuiapp.sort.SortByItemLayout;
import com.example.oneuiapp.fragment.localfont.viewmodel.LocalFontListViewModel;
import com.example.oneuiapp.search.SearchViewModel;
import com.example.oneuiapp.fragment.settings.viewmodel.SettingsViewModel;
import com.example.oneuiapp.notification.BatchOperationState;

/**
 * FavoriteFontListFragment — قائمة الخطوط المفضلة
 *
 * ★ مبني هيكلياً على LocalFontListFragment مع التعديلات التالية:
 *   1. لا يوجد اختيار مجلد (لا Permission Manager، لا Directory Picker)
 *   2. يراقب getFavoritesLiveData() بدلاً من getFontsLiveData()
 *   3. يستخدم FontSortManager بمعامل "FAVORITES" لمفاتيح DataStore المستقلة
 *      (يتطلب تحديث FontSortManager لدعم معرّف نصي ثالث بجانب true/false)
 *   4. FavoriteStatusProvider يُعيد true دائماً (كل عناصر المفضلة هي مفضلة)
 *   5. FavoriteStatusChecker يُعيد true دائماً (نفس السبب)
 *      → resolveFavoriteAction() يُعيد true → يُعرض "إزاله من المفضله" دائماً
 *   6. onFavoriteRequested يستدعي toggleFavoritesBatch بـ false (إزالة)
 *   7. العناصر تختفي تلقائياً من القائمة عند إزالتها من المفضلة (Room LiveData reactive)
 *
 * ★ الإصلاح (المشكلة 3): استدعاء mUIManager.setDefaultEmptyMessage(R.string.favorites_empty_message)
 *   مباشرةً بعد إنشاء FontUIStateManager في onAttach()، لضمان عرض رسالة المفضلة الصحيحة
 *   بدلاً من رسالة المجلد المحلي (font_fragment_empty_message) عند فراغ القائمة. ★
 *
 * ★ الإصلاح (مشكلة البحث): استدعاء mUIManager.setEmptyTitleView(empty_title) في initializeViews()
 *   لإخبار FontUIStateManager بوجود العنوان، مما يُتيح له إخفاءه تلقائياً عند البحث بلا نتائج.
 *   بدون هذا السطر، كان العنوان يظهر مع رسالة "لا توجد نتائج" معاً بدلاً من الرسالة وحدها. ★
 *
 * ★ الإضافة (تمييز الحالتين): استدعاء mUIManager.setNoResultsTextView(no_results_text)
 *   في initializeViews() لربط TextView مستقل برسالة "لا توجد نتائج"، مما يُتيح تخصيص
 *   لونها وحجمها من الـ layout بشكل مستقل تماماً عن رسالة الحالة الفارغة الحقيقية. ★
 *
 * ★ التعديل (سلة المحذوفات): استبدال منطق الحذف النهائي في handleDelete()
 *   بعرض ديالوج تأكيد النقل إلى السلة ثم ديالوج التقدم، والاستعانة بـ
 *   mViewModel.moveFontsToTrashInMemory() بدلاً من deleteFontsInMemory(). ★
 *
 * ★ إصلاح الأنيميشن (خطة الإصلاح الثلاثية):
 *   1. setRemoveDuration(0) → اختفاء فوري للعناصر دفعةً واحدة بلا وميض متتابع
 *   2. mIsBatchOperationRunning → يحجب تحديث الـ Adapter أثناء ديالوج التقدم
 *   3. الحد الأدنى 300ms في ViewModel → لا يومض الديالوج عند عنصر واحد ★
 *
 * ★ إصلاح اللاج (Global State Interception):
 *   بدلاً من إدارة mIsBatchOperationRunning محلياً في showMoveToTrashProgressDialog،
 *   يراقب الآن BatchOperationState.getIsProcessing() — إشارة مرور عامة تُفعَّل
 *   من Repository مباشرةً. هذا يضمن أن أي Fragment يُنشأ أثناء عملية جارية
 *   (مثل الانتقال من الخطوط المحلية إلى المفضلة) يحجز بياناته فوراً
 *   بدلاً من تحديث الواجهة عنصراً تلو الآخر مما يُسبب اللاج. ★
 *
 * ★ الإصلاح الجوهري (الأرقام السحرية → AppScreen):
 *   بدلاً من: updateFontsCount(FRAGMENT_INDEX, count) حيث FRAGMENT_INDEX = 4
 *   نستخدم:  updateFontsCount(AppScreen.FAVORITES, count)
 *
 *   بدلاً من: getSourceFragmentIndex() != 4
 *   نستخدم:  getSourceScreen() != AppScreen.FAVORITES
 *
 *   بدلاً من: setSourceFragmentIndex(4)
 *   نستخدم:  setSourceScreen(AppScreen.FAVORITES)
 *
 *   الفائدة: حذف HomeFragment أو تغيير ترتيب الشاشات لا يُغيّر أرقام الشاشات
 *   الأخرى، لأن كل شاشة تُعرِّف نفسها باسمها (FAVORITES) لا برقمها (4).
 *
 * ★ ملاحظات للمطوّر:
 *   - يجب أن تُنفّذ MainActivity واجهة FavoriteFontListFragment.OnFontSelectedListener
 *     وإضافتها إلى قائمة implements في تعريف الكلاس.
 *   - يجب إضافة دالة setFavoriteIndicator(boolean) إلى LocalFontViewHolder
 *     (مُشار إليها في LocalFontListAdapter عبر PAYLOAD_UPDATE_FAVORITE).
 *   - يتطلب FontSortManager دعم معرّف نصي "FAVORITES" لقراءة/كتابة
 *     KEY_FAVORITES_SORT_TYPE و KEY_FAVORITES_SORT_ASCENDING من SettingsDataStore.
 *
 * ★ المرحلة الأولى من خطة التحسين: اللامركزية في قوائم AppBar ★
 *   هذا الـ Fragment أصبح مسؤولاً عن أيقوناته الخاصة عبر:
 *   - setHasOptionsMenu(true) في onCreate()
 *   - onCreateOptionsMenu() لنفخ menu_font_list_search
 *   - onOptionsItemSelected() لمعالجة النقرات (يُحال لـ super — لا أيقونات إضافية)
 *   - setMenuVisibility(!hidden) في onHiddenChanged() للتبديل التلقائي
 *
 * ★ الإصلاح (خطة الإصلاح الشاملة — الخطوة الثالثة):
 *   إضافة onSearchStateChanged(boolean) لمزامنة SearchViewModel مع الحالة البصرية
 *   لحقل البحث فوراً عند تمدده أو طيّه، مما يُخفي/يُظهر زر الثلاث نقاط
 *   في نفس اللحظة بدلاً من انتظار كتابة أول حرف. ★
 */
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

    // ★ لا يوجد SortByItemLayout منفصل — الهيدر داخل الـ Adapter يتولى ذلك ★
    private FontSearchManager mSearchManager;
    private FontSortManager mSortManager;
    private FontUIStateManager mUIManager;
    private LocalFontSelectionManager mSelectionManager;

    private LocalFontListViewModel mViewModel;
    private SearchViewModel mSearchViewModel;
    private SettingsViewModel mSettingsViewModel;

    // ★ القائمة الحالية للخطوط المفضلة — تُحدَّث من favoritesLiveData ★
    private List<LocalFontListViewModel.FontFileInfoWithMetadata> mCurrentFavoritesList = new ArrayList<>();

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
    private List<LocalFontListViewModel.FontFileInfoWithMetadata> mPendingFavoritesUpdate = null;

    // ★ مرجع ديالوج التقدم الحالي — يُستخدم لإعادة الفتح عند الضغط على الإشعار ★
    @Nullable
    private ProgressDialog mCurrentProgressDialog;

    

    // ════════════════════════════════════════════════════════════
    // ★ واجهة الإشعار عند اختيار خط ★
    // يجب أن تُنفّذها MainActivity (إضافتها إلى قائمة implements)
    // ════════════════════════════════════════════════════════════

    /**
     * مطابقة لـ LocalFontListFragment.OnFontSelectedListener
     * يُمرَّر weightWidthLabel لتجنب إعادة استخراجه في NavManager أو FontViewerFragment
     */
    public interface OnFontSelectedListener {
        void onFontSelected(String fontPath, String realName, String fileName,
                            int ttcIndex, String weightWidthLabel);
    }

    

    // ════════════════════════════════════════════════════════════
    // دورة حياة الـ Fragment
    // ════════════════════════════════════════════════════════════

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mContext = context;

        // ★ ربط مستمع اختيار الخط بـ MainActivity ★
        if (context instanceof OnFontSelectedListener) {
            mFontSelectedListener = (OnFontSelectedListener) context;
        }

        // ★ لا يوجد Permission Manager أو Directory Picker في المفضلة ★

        mSearchManager = new FontSearchManager();

        // ★ "FAVORITES" يُخبر FontSortManager باستخدام مفاتيح DataStore الخاصة بالمفضلة:
        //   KEY_FAVORITES_SORT_TYPE و KEY_FAVORITES_SORT_ASCENDING
        //   (يتطلب تحديث FontSortManager لدعم هذا المعرّف النصي) ★
        mSortManager = new FontSortManager(mContext, "FAVORITES");

        mUIManager = new FontUIStateManager(mContext);

        // ★ الإصلاح (المشكلة 3): تخصيص رسالة الشاشة الفارغة لقائمة المفضلة ★
        // بدون هذا السطر، كانت FontUIStateManager تعرض font_fragment_empty_message
        // (رسالة المجلد المحلي) عند فراغ قائمة المفضلة — وهي رسالة غير صحيحة السياق.
        // setDefaultEmptyMessage() يُغيّر defaultEmptyMessageResId المُستخدَم في showEmptyView().
        mUIManager.setDefaultEmptyMessage(R.string.favorites_empty_message);

        setupSearchListener();
        setupSortListener();
    }

    private void setupSearchListener() {
        // ★ المستمع يُحدّث حالة الواجهة فقط — تحديث الـ Adapter يتم من المراقب ★
        mSearchManager.setSearchResultListener((count, empty) -> {
            mUIManager.updateEmptyView(empty, mSearchManager.isSearchActive());
        });
    }

    private void setupSortListener() {
        // ★ عند تغيير الفرز: setSortOptions → SortedList يعيد الترتيب بأنيميشن ★
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
    }

    /**
     * ★ المرحلة الأولى: نفخ أيقونات هذا الـ Fragment في AppBar ★
     *
     * ينفخ قائمة البحث الخاصة بقائمة المفضلة،
     * ثم يربط أيقونة البحث بـ SearchCoordinator الموجود في MainActivity.
     * menu.clear() يضمن نظافة القائمة قبل كل نفخ جديد.
     */
    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        menu.clear(); // تنظيف أي قوائم سابقة
        // ★ نفخ قائمة البحث الخاصة بقائمة المفضلة ★
        inflater.inflate(R.menu.menu_font_list_search, menu);

        // ★ ربط أيقونة البحث بالـ Coordinator الموجود في MainActivity ★
        // bindSearchMenuItem() يُعدّ SearchView ويضبط مستمعاته لتفعيل البحث
        MenuItem searchItem = menu.findItem(R.id.action_search_fonts);
        if (getActivity() instanceof MainActivity && searchItem != null) {
            ((MainActivity) getActivity()).getSearchCoordinator().bindSearchMenuItem(searchItem);
        }

        super.onCreateOptionsMenu(menu, inflater);
    }

    /**
     * ★ المرحلة الأولى: معالجة نقرات أيقونات هذا الـ Fragment ★
     * قائمة المفضلة لا تحتوي أيقونات إضافية غير البحث — يُحال لـ super.
     */
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    private void initializeViewModels() {
        // ★ نفس ViewModel المستخدم في LocalFontListFragment — يشاركان البيانات
        //   لضمان التزامن الفوري بين القائمتين عند تغيير حالة المفضلة ★
        mViewModel         = new ViewModelProvider(this).get(LocalFontListViewModel.class);
        mSearchViewModel   = new ViewModelProvider(this).get(SearchViewModel.class);
        mSettingsViewModel = new ViewModelProvider(this).get(SettingsViewModel.class);
    }

    private void setupViewModelObservers() {
        // ★ مراقبة قائمة المفضلة — تتحدث تلقائياً عند إضافة أو إزالة خط من المفضلة ★
        mViewModel.getFavoritesLiveData().observe(this, favorites -> {
            if (favorites != null) {
                // ★ إذا كانت عملية نقل جارية، نحجز البيانات ولا نُحدِّث الواجهة ★
                // يضمن هذا أن اختفاء العناصر يبدأ دفعةً واحدة فقط بعد إغلاق الديالوج،
                // بدلاً من وميض متتابع عنصراً تلو الآخر أثناء جريان العملية.
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

        // ★ مراقبة نص البحث — يُفلتر قائمة المفضلة في الذاكرة ★
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

        // ★ مراقب الإشارة العامة للعمليات الضخمة ★
        // يستجيب لأي عملية ضخمة في التطبيق بالكامل بغض النظر عن الشاشة التي بدأت منها.
        // هذا يحل مشكلة اللاج عند الانتقال من الخطوط المحلية/سلة المحذوفات إلى المفضلة
        // أثناء عملية جارية: كان Fragment جديد يُنشأ بـ mIsBatchOperationRunning=false
        // فيستقبل مئات التحديثات متتاليةً مما يُجمّد المعالج. الآن يرصد الإشارة العامة
        // ويحجز البيانات بصمت حتى تنتهي العملية ثم يطبّقها دفعةً واحدة.
        BatchOperationState.getIsProcessing().observe(this, isProcessing -> {
            mIsBatchOperationRunning = isProcessing;

            // ★ إغلاق الديالوج فور انتهاء العملية (يحل المشكلة 3) ★
            if (!isProcessing && mCurrentProgressDialog != null && mCurrentProgressDialog.isShowing()) {
                mCurrentProgressDialog.dismiss();
                mCurrentProgressDialog = null;
            }

            if (!isProcessing && mPendingFavoritesUpdate != null) {
                // العملية انتهت! نُطبق التحديث المحجوز دفعة واحدة ليعمل أنيميشن واحد فقط
                mCurrentFavoritesList = new ArrayList<>(mPendingFavoritesUpdate);
                if (mAdapter != null) {
                    mAdapter.setAllFontsMetadata(mPendingFavoritesUpdate);
                }
                refreshAdapterData();
                updateMainActivityFontsCount(mCurrentFavoritesList.size());
                mPendingFavoritesUpdate = null;
            }
        });

        // ★ مراقب التقدم لتحديث الديالوج المُعاد فتحه (يحل المشكلة 2) ★
        // يُستدعى عند كل تحديث تقدم من showMoveToTrashProgressDialog → progressListener
        // فيحدّث mCurrentProgressDialog إذا كان مفتوحاً — سواء الديالوج الأصلي أو المُعاد فتحه.
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

        // ★ لا يوجد select_folder_container في المفضلة ★
        // main_content_layout يكون مرئياً دائماً (قد يُعرض الـ empty_view بداخله)
        mUIManager.setViews(
            null,                                        // لا يوجد select_folder_container
            view.findViewById(R.id.main_content_layout),
            view.findViewById(R.id.empty_view),
            view.findViewById(R.id.empty_text),
            mRecyclerView
        );

        // ★ الإصلاح (مشكلة البحث): ربط عنوان الحالة الفارغة بـ FontUIStateManager ★
        // بدون هذا السطر، لا تعلم FontUIStateManager بوجود empty_title فلا تُخفيه
        // عند البحث بلا نتائج، فيظهر العنوان مع رسالة "لا توجد نتائج" معاً.
        // بعد هذا السطر، يُخفى العنوان تلقائياً عند البحث ويظهر فقط رسالة "لا توجد نتائج". ★
        mUIManager.setEmptyTitleView(view.findViewById(R.id.empty_title));

        // ★ الإضافة (تمييز الحالتين): ربط رسالة البحث بلا نتائج المستقلة ★
        // يُتيح تخصيص لون no_results_text وحجمه من الـ layout بشكل مستقل تماماً
        // عن empty_text، لأن كلًّا منهما يخدم سياقاً مختلفاً من منظور تجربة المستخدم.
        mUIManager.setNoResultsTextView(view.findViewById(R.id.no_results_text));

        // ★ قائمة المفضلة دائماً في وضع العرض (لا يوجد وضع "اختيار مجلد") ★
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

        // ★ مستمع النقر على الخط: تمرير weightWidthLabel لـ NavManager عبر MainActivity ★
        mAdapter.setFontClickListener((fontPath, realName, fileName, ttcIndex, weightWidthLabel) -> {

            mAdapter.saveLastOpenedAndUpdate(fontPath);

            if (mFontSelectedListener != null) {
                mFontSelectedListener.onFontSelected(fontPath, realName, fileName,
                                                     ttcIndex, weightWidthLabel);
            }
        });

        // ★ مستمع تغيير الفرز: يحفظ التفضيل عبر SortManager ثم يُطبّق الأنيميشن ★
        mAdapter.setSortChangeListener((type, asc) -> {
            mSortManager.setSortOptions(type, asc);
        });

        // ★ FavoriteStatusProvider: كل عناصر قائمة المفضلة هي مفضلة بالتعريف ★
        // → يُعرض أيقونة النجمة الصفراء (ic_favorite) بجانب جميع العناصر دائماً
        mAdapter.setFavoriteStatusProvider(fontPath -> true);

        mRecyclerView.setAdapter(mAdapter);

        // ★ تهيئة معيار الفرز المحفوظ قبل وصول أي بيانات لتجنب السباق الزمني ★
        mAdapter.updateSortOptionsOnly(
            mSortManager.getCurrentSortType(),
            mSortManager.isSortAscending()
        );

        // ★ استدعاء دالة الأنيميشن المركزية ★
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
            null // ★ لا يوجد SortByItemLayout منفصل في المفضلة ★
        );

        // ★ FavoriteStatusChecker: كل عناصر قائمة المفضلة مفضلة بالتعريف ★
        // → resolveFavoriteAction() يُعيد true دائماً
        // → يُعرض "إزاله من المفضله" (ic_oui_favorite_off) في وضع التحديد دائماً
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

            /**
             * ★ إجراء المفضلة في قائمة المفضلة ★
             * addToFavorites سيكون دائماً false هنا لأن FavoriteStatusChecker يُعيد true دائماً،
             * مما يعني أن resolveFavoriteAction() يُعيد true → addToFavorites = !true = false.
             * النتيجة: يُزيل العناصر المحددة من المفضلة وتختفي تلقائياً من القائمة.
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

    // ════════════════════════════════════════════════════════════
    // معالجات الإجراءات في وضع التحديد المتعدد
    // ════════════════════════════════════════════════════════════

    /**
     * إعادة تسمية الخط المحدد وتحديث القائمتين (المحلية والمفضلة) في الذاكرة.
     * يستفيد من renameFontInMemory الذي يُحدّث favoritesLiveData تلقائياً.
     */
    private void handleRename(int position) {
        String path = mAdapter.getFilePath(position);
        if (path == null) return;

        FontActionDialogs.showRenameDialog(mContext, path, (oldPath, newFileName) -> {
            boolean success = mViewModel.renameFontInMemory(oldPath, newFileName);

            if (success) {
                mSelectionManager.setSelecting(false);

                // ★ التمرير السلس للعنصر المُعاد تسميته بعد إعادة الترتيب ★
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
        // - رسالة بصيغة الجمع بحسب عدد الملفات من plurals.dialog_move_to_trash_question
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
     * - العنوان يستخدم plurals.progress_moving_to_trash بحسب عدد الملفات.
     * - زران:
     *     • BUTTON_NEGATIVE (إلغاء) → يوقف العملية عبر mViewModel.cancelTrashOperation().
     *     • BUTTON_NEUTRAL  (إخفاء الإطار المنبثق) → يُغلق الديالوج والعملية تكمل في الخلفية.
     *
     * مُستمع التقدم OnProgressListener يُستدعى من خيط الخلفية (TrashRepository)،
     * لذلك يُعاد نقل تحديث الـ UI إلى الخيط الرئيسي عبر mMainHandler.
     *
     * ★ الإصلاح الجوهري (الأرقام السحرية → AppScreen):
     *   setSourceScreen(AppScreen.FAVORITES) بدلاً من setSourceFragmentIndex(4).
     *   يضمن أن PendingIntent في الإشعار يحمل الشاشة الصحيحة (AppScreen.FAVORITES)
     *   بدلاً من الرقم الخاطئ الموروث من عملية سابقة.
     *   آمن ضد تغيير ترتيب الشاشات أو حذف HomeFragment. ★
     *
     * ★ إصلاح اللاج (Global State Interception):
     *   لم يعد هذا الـ Fragment يضبط mIsBatchOperationRunning يدوياً.
     *   TrashRepository يُفعّل BatchOperationState.setProcessing(true) عند بدء العملية،
     *   فيستجيب مراقب getIsProcessing() في setupViewModelObservers() ويضبط
     *   mIsBatchOperationRunning=true تلقائياً على جميع الشاشات بما فيها هذه.
     *   عند انتهاء العملية، يُطبَّق mPendingFavoritesUpdate دفعةً واحدة بأنيميشن سلس. ★
     *
     * ★ إصلاح المشكلة (2): استخدام mCurrentProgressDialog (حقل الـ Fragment) بدلاً من متغير محلي ★
     *   كانت progressDialog متغيراً محلياً، فكان الـ Callback القديم يحاول إغلاقه بعد إعادة الفتح
     *   مما يُسبب تسرباً للنافذة (WindowLeak) وانهياراً.
     *   الآن: كل الكود يقرأ mCurrentProgressDialog مباشرةً، فلا تعارض بين الـ Callbacks. ★
     *
     * @param pathsToMove قائمة مسارات الخطوط المراد نقلها (مجمَّعة مسبقاً في handleDelete)
     */
    private void showMoveToTrashProgressDialog(@NonNull List<String> pathsToMove) {
        int count = pathsToMove.size();

        // ★ إغلاق وضع التحديد قبل عرض الديالوج لتجنب تعارض الواجهات ★
        mSelectionManager.setSelecting(false);

        // ─── إنشاء ديالوج التقدم ───────────────────────────────────────────
        // ★ إصلاح (2): استخدام mCurrentProgressDialog مباشرةً بدلاً من متغير محلي ★
        mCurrentProgressDialog = new ProgressDialog(mContext);
        mCurrentProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mCurrentProgressDialog.setCancelable(false);
        mCurrentProgressDialog.setTitle(getResources().getQuantityString(
                R.plurals.progress_moving_to_trash, count, count));
        mCurrentProgressDialog.setMax(count);

        // ★ الإصلاح الجوهري: setSourceScreen(AppScreen.FAVORITES) بدلاً من setSourceFragmentIndex(4) ★
        // يضمن صحة التوجيه من الإشعار إلى الشاشة الصحيحة حتى لو تغيّر ترتيب الشاشات.
        // يجب أن يسبق showMoveToTrashNotification() لأن PendingIntent يُبنى أثناءها.
        BatchOperationState.setSourceScreen(AppScreen.FAVORITES);

        // ★ زر إلغاء — يوقف العملية الجارية ويُغلق الديالوج ★
        mCurrentProgressDialog.setButton(
                ProgressDialog.BUTTON_NEGATIVE,
                getString(R.string.action_cancel),
                (dialog, which) -> {
                    mViewModel.cancelTrashOperation();
                    dialog.dismiss();
                });

        // ★ زر إخفاء الإطار المنبثق — يُغلق الديالوج والعملية تكمل في الخلفية ★
        // مفيد عند نقل ملفات كثيرة ويريد المستخدم متابعة الاستخدام
        mCurrentProgressDialog.setButton(
                ProgressDialog.BUTTON_POSITIVE,
                getString(R.string.action_hide_dialog),
                (dialog, which) -> dialog.dismiss());

        mCurrentProgressDialog.show();

        // ─── مُستمع التقدم ─────────────────────────────────────────────────
        // ★ يُستدعى من خيط الخلفية في TrashRepository بعد كل ملف ★
        // mMainHandler يُعيد التنفيذ إلى الخيط الرئيسي لتحديث الـ UI بأمان
        TrashRepository.OnProgressListener progressListener = (current, total) ->
                mMainHandler.post(() -> {
                    if (mCurrentProgressDialog != null && mCurrentProgressDialog.isShowing()) {
                        mCurrentProgressDialog.setProgress(current);
                    }
                });

        // ─── تنفيذ العملية ─────────────────────────────────────────────────
        mViewModel.moveFontsToTrashInMemory(pathsToMove, progressListener, () -> {
            // ★ يُستدعى على الخيط الرئيسي عند انتهاء العملية (نجاح أو إلغاء) ★
            // بعد الحد الأدنى 300ms المُطبَّق في ViewModel (الخطوة الثالثة من خطة الإصلاح)
            // ★ إصلاح (2): الـ Callback يقرأ mCurrentProgressDialog لا متغيراً محلياً ★
            if (mCurrentProgressDialog != null && mCurrentProgressDialog.isShowing()) {
                mCurrentProgressDialog.dismiss();
            }
            mCurrentProgressDialog = null;
            // ★ لا حاجة لتطبيق mPendingFavoritesUpdate هنا يدوياً ★
            // مراقب BatchOperationState.getIsProcessing() في setupViewModelObservers()
            // يتولى ذلك تلقائياً فور أن يُرسل TrashRepository إشارة false عند انتهاء العملية.
        });
    }

    /**
     * ★ إعادة فتح ديالوج التقدم عند الضغط على الإشعار ★
     *
     * تُستدعى من:
     *   - onResume() عندما يكون الـ Fragment ظاهراً
     *   - onHiddenChanged() عندما يظهر الـ Fragment
     *   - MainActivity.handleIntent() إذا كانت الشاشة الصحيحة مفتوحة مسبقاً
     *
     * الشروط اللازمة للفتح:
     *   1. الـ Fragment مضاف وظاهر وغير محذوف.
     *   2. عملية ضخمة جارية (BatchOperationState.isProcessing = true).
     *   3. ★ الإصلاح الجوهري: العملية من AppScreen.FAVORITES بدلاً من getSourceFragmentIndex() != 4 ★
     *      يضمن صحة الفحص حتى لو تغيّر ترتيب الشاشات في مصفوفة mFragments.
     *   4. تم استهلاك علامة الإشعار (consumeShouldReopenDialog = true).
     *   5. لا يوجد ديالوج مفتوح بالفعل.
     */
    public void checkAndReopenProgressDialogPublic() {
        if (isHidden() || !isAdded() || mContext == null) return;

        Boolean isProcessing = BatchOperationState.getIsProcessing().getValue();
        if (!Boolean.TRUE.equals(isProcessing)) return;

        // ★ الإصلاح الجوهري: مقارنة بـ AppScreen.FAVORITES بدلاً من getSourceFragmentIndex() != 4 ★
        // يضمن صحة الفحص حتى لو حُذف HomeFragment أو تغيّر ترتيب الشاشات،
        // لأن AppScreen.FAVORITES ثابت لا يتأثر بموضع الـ Fragment في مصفوفة mFragments.
        if (BatchOperationState.getSourceScreen() != AppScreen.FAVORITES) return;

        // ★ شرط الفتح عبر الإشعار ★
        if (!BatchOperationState.consumeShouldReopenDialog()) return;

        if (mCurrentProgressDialog != null && mCurrentProgressDialog.isShowing()) return;

        reconnectToProgressDialog();
    }

    /**
     * ★ إنشاء ديالوج تقدم جديد وربطه بالحالة الجارية ★
     *
     * يُستدعى فقط من checkAndReopenProgressDialogPublic() عند التحقق من جميع الشروط.
     * يقرأ آخر قيمة للتقدم من BatchOperationState.getProgress() لعرض الحالة الصحيحة.
     *
     * ★ إصلاح المشكلة (4): تجاهل lastProgress.title واستخدام getResources() ★
     * سياق التطبيق (Application Context) المُستخدَم في ViewModel يقرأ لغة النظام (العربية)،
     * بينما getResources() هنا يستخدم سياق الشاشة (Activity Context) الملفوف باللغة
     * المختارة في التطبيق (الإنجليزية) عبر LanguageHelper. هذا يضمن عرض العنوان
     * باللغة الصحيحة عند إعادة فتح الديالوج من الإشعار. ★
     */
    private void reconnectToProgressDialog() {
        if (!isAdded() || mContext == null) return;

        mCurrentProgressDialog = new ProgressDialog(mContext);
        mCurrentProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mCurrentProgressDialog.setCancelable(false);

        BatchOperationState.ProgressData lastProgress =
                BatchOperationState.getProgress().getValue();

        if (lastProgress != null) {
            // ★ إصلاح (4): تجاهل lastProgress.title واستخدم getResources() لجلب النص باللغة الصحيحة ★
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

    /**
     * ★ إزالة الخطوط المحددة من المفضلة ★
     *
     * addToFavorites سيكون دائماً false في هذه القائمة.
     * بعد نجاح العملية، تختفي العناصر من القائمة تلقائياً
     * لأن Room LiveData يُحدَّث فور تغيير is_favorite في قاعدة البيانات.
     *
     * ★ يُحدّث أيضاً أيقونة المفضلة في قائمة الخطوط المحلية تلقائياً
     *   لأن favoritesLiveData و fontsLiveData يراقبان نفس قاعدة البيانات ★
     */
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
            // ★ العناصر تختفي من القائمة تلقائياً عبر Room LiveData — لا حاجة لتحديث يدوي ★
            String message = addToFavorites
                ? getString(R.string.action_favorite)    // نادراً ما يحدث هنا
                : getString(R.string.action_unfavorite);
            Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show();
        });
    }

    // ════════════════════════════════════════════════════════════
    // دوال البحث — تُستدعى من SearchCoordinator
    // ════════════════════════════════════════════════════════════

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

    /** تفعيل البحث وتصفية قائمة المفضلة بنص البحث المعطى */
    public void filterFonts(String query) {
        mSearchViewModel.setSearchQuery(query);
    }

    /** إلغاء البحث وإعادة عرض قائمة المفضلة كاملةً */
    public void resetFilter() {
        mSearchViewModel.deactivateSearch();
    }

    // ════════════════════════════════════════════════════════════
    // دعم زر الرجوع — تُستدعى من NavManager
    // ════════════════════════════════════════════════════════════

    /**
     * يُعيد true إذا أُلغي وضع التحديد المتعدد (يمنع NavManager من معالجة زر الرجوع)
     */
    public boolean handleBackPressed() {
        if (mSelectionManager != null) return mSelectionManager.handleBackPress();
        return false;
    }

    // ════════════════════════════════════════════════════════════
    // تحديث بيانات الـ Adapter
    // ════════════════════════════════════════════════════════════

    /**
     * يُغذّي الـ Adapter بقائمة المفضلة الخام.
     * SortedList داخل الـ Adapter يتولى الترتيب وتوليد الأنيميشن.
     */
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
            // ★ إظهار الحالة الفارغة مع رسالة مناسبة ★
            mUIManager.updateEmptyView(true, mSearchManager.isSearchActive());
            return;
        }

        // ★ بناء قائمة FontFileInfo من بيانات المفضلة بدون فرز مسبق ★
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
            // SortedList يرتب القائمة تلقائياً حسب currentSortType/currentSortAscending
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

    // ════════════════════════════════════════════════════════════
    // دوال دورة حياة Fragment
    // ════════════════════════════════════════════════════════════

    /**
     * يُحدّث عدد المفضلة في MainActivity.
     *
     * ★ الإصلاح الجوهري (الخطوة الثالثة من خطة الإصلاح):
     *   تمرير AppScreen.FAVORITES بدلاً من FRAGMENT_INDEX (الذي كان = 4).
     *   يضمن تمييز هذا الفراجمنت بالاسم لا بالرقم، فلا يتأثر بتغيير ترتيب الشاشات
     *   أو بحذف HomeFragment أو إضافة شاشات جديدة.
     */
    private void updateMainActivityFontsCount(int count) {
        // ★ لا تُحدّث العنوان الفرعي إذا كان وضع التحديد نشطاً ★
        if (mSelectionManager != null && mSelectionManager.isSelecting()) return;

        // ★ الإصلاح الجوهري: AppScreen.FAVORITES بدلاً من الرقم المُشفَّر 4 ★
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).updateFontsCount(AppScreen.FAVORITES, count);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // ★ تحديث العدد وفحص الإشعار فقط إذا كان هذا الـ Fragment ظاهراً للمستخدم ★
        if (!isHidden()) {
            updateMainActivityFontsCount(mCurrentFavoritesList.size());
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
            // ★ 1. حفظ موضع التمرير قبل الإخفاء ★
            mUIManager.saveRecyclerViewState();

            // ★ 2. إيقاف الأنيميشن لتطبيق التحديثات الخلفية بصمت ★
            if (mRecyclerView != null) {
                mRecyclerView.setItemAnimator(null);
            }

            mSearchViewModel.deactivateSearch();

            if (mSelectionManager != null && mSelectionManager.isSelecting()) {
                mSelectionManager.setSelecting(false);
            }
        } else {
            // ★ إعادة رسم القائمة لتمييز آخر خط مفتوح ★
            if (mAdapter != null) mAdapter.smartUpdate();

            updateMainActivityFontsCount(mCurrentFavoritesList.size());

            // ★ فحص الإشعار عند ظهور الـ Fragment ★
            checkAndReopenProgressDialogPublic();

            // ★ 3. استعادة موضع التمرير بعد اكتمال layout ★
            mMainHandler.post(() -> mUIManager.restoreRecyclerViewState());

            // ★ 4. تأخير إعادة الأنيميشن لضمان رسم العناصر في مواضعها أولاً ★
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
        // ★ تحديث واجهة وضع التحديد بعد دوران الجهاز ★
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

        // ★ تنظيف البيانات المحجوزة عند تدمير الـ View لمنع تسرب الذاكرة ★
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
