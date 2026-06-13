package com.example.oneuiapp.fontlist.search;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.widget.ActionMenuView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;

import com.example.oneuiapp.R;
import com.example.oneuiapp.activity.AppScreen;
import com.example.oneuiapp.fragment.FavoriteFontListFragment; // ★ دعم قائمة المفضلة ★
import com.example.oneuiapp.fragment.LocalFontListFragment;
import com.example.oneuiapp.fragment.SystemFontListFragment;
import com.example.oneuiapp.fragment.TrashFragment; // ★ الإصلاح: دعم سلة المحذوفات في اعتراض إغلاق البحث ★

import dev.oneuiproject.oneui.layout.DrawerLayout;

/**
 * SearchCoordinator - منسّق البحث المركزي
 *
 * يُوجّه استدعاءات filterFonts() و resetFilter() للـ Fragment الظاهر حالياً.
 *
 * ★ الإصلاح الجوهري: الانتقال الكامل من int/List إلى AppScreen ★
 * بدلاً من FragmentIndexProvider (تُعيد int) و List<Fragment>، أصبح الكود يستخدم:
 *   - ScreenProvider  → تُعيد AppScreen الحالية مباشرةً بدلاً من ordinal()
 *   - FragmentProvider → تُعيد Fragment بناءً على AppScreen بدلاً من فهرس قائمة
 *
 * هذا يقطع الاعتماد على ترتيب الفراغمنتات نهائياً ويُعالج اختفاء الأيقونات
 * الناتج عن فشل رصد الشاشة الصحيحة في handleSearchCollapse() عند استخدام
 * الأرقام الصلبة (2، 3، 4) مقارنةً بـ AppScreen.
 *
 * ★ دعم FavoriteFontListFragment (AppScreen.FAVORITES) في جميع عمليات البحث:
 *   - performSearch()         → يُفعّل البحث في قائمة المفضلة
 *   - handleSearchCollapse()  → يُعيد قائمة المفضلة لوضعها الكامل عند إغلاق البحث
 *   - handleSearchIntent()    → يقبل Intent البحث عند وجود المفضلة في المقدمة
 *   - saveState()             → يحفظ حالة البحث عند كون المفضلة في المقدمة
 *   - restoreState()          → يستعيد البحث في قائمة المفضلة بعد إعادة البناء
 *
 * ★ الإصلاح: تعطيل عنصر القائمة (searchMenuItem) عند توسيع البحث وإعادة تفعيله
 *   عند طيّه، لمنع منطقة اللمس الشبحية للأيقونة الأصلية من الاستجابة خلف
 *   أزرار SearchView (الصوت وX والأيقونات الأخرى). ★
 *
 * ★ إصلاح سباق الزمني (Race Condition) عند تغيير اللغة أو إعادة البناء ★
 * تم فصل مسؤوليتَي setup() القديمة إلى دالتين مستقلتين:
 *   - setProviders()         → يُعطى في onCreate() لتجهيز البيانات المنطقية فوراً
 *                              (ScreenProvider + FragmentProvider) دون الحاجة للأيقونة
 *   - bindSearchMenuItem()   → يُعطى في onCreateOptionsMenu() بعد أن تُرسم الأيقونة
 *
 * بهذا يمتلك المنسق screenProvider جاهزاً حين يستدعي النظام restoreState()
 * عند إعادة بناء النشاط بعد تغيير اللغة، فلا يحدث NullPointerException.
 *
 * ★ الإصلاح (خطة الإصلاح الشاملة — الخطوة الأولى):
 *   collapseSearch() أصبحت تتحقق من isSearchExpanded أولاً، وتُجبر على
 *   إعادة ضبط الحالة حتى لو كان searchMenuItem مخفياً أو غير متاح.
 *   handleSearchCollapse() أصبحت تُصفّر فلاتر جميع القوائم الثلاث مباشرةً
 *   بدلاً من الفراغمنت الحالي فقط، مما يضمن نظافتها عند العودة لأي شاشة. ★
 *
 * ★ الإصلاح (اعتراض إغلاق البحث عند وجود وضع التحديد المتعدد النشط):
 *   handleSearchCollapse() أصبحت تتحقق أولاً من وجود وضع تحديد نشط في الفراغمنت
 *   الحالي قبل السماح بإغلاق البحث. إذا كان وضع التحديد نشطاً، يتم إغلاقه
 *   أولاً وتُعاد false لمنع البحث من الإغلاق، مما يُبقي المستخدم في نتائج
 *   البحث. هذا يتوافق مع سلوك تطبيقات One UI الرسمية. ★
 */
public class SearchCoordinator {

    private static final String TAG = "SearchCoordinator";
    private static final String KEY_SEARCH_QUERY    = "search_query";
    private static final String KEY_SEARCH_EXPANDED = "search_expanded";

    private final Activity     activity;
    private final DrawerLayout drawerLayout;

    private MenuItem       searchMenuItem;
    private SearchView     searchView;
    private ScreenProvider screenProvider;
    private FragmentProvider fragmentProvider;

    private boolean    isSearchExpanded = false;
    private String     savedSearchQuery = "";
    private AppScreen  lastScreen       = null; // ★ الإصلاح: AppScreen بدلاً من int lastFragmentIndex ★

    private SearchStateListener stateListener;

    // ★ إصلاح Race Condition: متغير تتبع الاستعادة المعلقة ★
    // يُفعَّل في restoreState() عندما تكون الأيقونة غير جاهزة بعد،
    // ويُستهلك في bindSearchMenuItem() حين تُصبح الأيقونة حقيقية وجاهزة.
    private boolean mPendingSearchRestore = false;

    // ★ متغير لمنع إغلاق البحث برمجياً عند إغلاق وضع التحديد ★
    public boolean ignoreNextCollapse = false;

    // ════════════════════════════════════════════════════════
    //  الواجهات العامة
    // ════════════════════════════════════════════════════════

    /**
     * ★ الإصلاح: ScreenProvider بدلاً من FragmentIndexProvider ★
     *
     * تُعيد الشاشة الحالية كـ AppScreen بدلاً من رقم ordinal()،
     * مما يقطع الاعتماد على ترتيب الفراغمنتات نهائياً.
     */
    public interface ScreenProvider {
        AppScreen getCurrentScreen();
    }

    /**
     * ★ الإصلاح: FragmentProvider بدلاً من List<Fragment> ★
     *
     * تُعيد Fragment بناءً على AppScreen مباشرةً من mFragmentsMap،
     * وهو دائماً محدَّث بعد دوران الشاشة دون الحاجة لإعادة بناء قائمة وسيطة.
     */
    public interface FragmentProvider {
        Fragment getFragment(AppScreen screen);
    }

    public interface SearchStateListener {
        void onSearchExpanded();
        void onSearchCollapsed();
        void onSearchQueryChanged(String query);
    }

    // ════════════════════════════════════════════════════════
    //  البناء والإعداد
    // ════════════════════════════════════════════════════════

    public SearchCoordinator(@NonNull Activity activity, @NonNull DrawerLayout drawerLayout) {
        this.activity     = activity;
        this.drawerLayout = drawerLayout;
    }

    /**
     * ★ الخطوة الأولى من خطة الإصلاح: تزويد المنسق بالبيانات المنطقية ★
     *
     * يُستدعى من setupSearchCoordinator() في onCreate() — قبل أي استدعاء
     * لـ restoreState() أو saveState() — لضمان أن screenProvider ليس null
     * حين يستدعي النظام هذه الدوال عند تغيير اللغة أو إعادة البناء.
     *
     * لا يحتاج هذا المنسق إلى الأيقونة (MenuItem) في هذه المرحلة؛
     * ستُربط لاحقاً عبر bindSearchMenuItem() حين تُرسمها المكتبة على الشاشة.
     *
     * @param screenProvider   موفّر الشاشة الحالية كـ AppScreen
     * @param fragmentProvider موفّر الـ Fragment لشاشة بعينها
     */
    public void setProviders(@NonNull ScreenProvider screenProvider,
                             @NonNull FragmentProvider fragmentProvider) {
        this.screenProvider   = screenProvider;
        this.fragmentProvider = fragmentProvider;
    }

    /**
     * ★ الخطوة الثالثة من خطة الإصلاح: ربط الأيقونة بعد رسمها ★
     *
     * يُستدعى من onCreateOptionsMenu() بعد أن تُصبح أيقونة البحث حقيقية
     * ومربوطة بالشاشة. يتولى إعداد SearchView وجميع مستمعاته.
     *
     * بفضل setProviders() الذي استُدعي مسبقاً في onCreate()، يمتلك
     * المنسق بالفعل screenProvider و fragmentProvider جاهزَين.
     *
     * ★ الإصلاح: تنفيذ الاستعادة المعلقة (mPendingSearchRestore) فور ربط الأيقونة ★
     * إذا كانت restoreState() قد اكتشفت أن البحث يجب أن يُفتح لكن الأيقونة
     * لم تكن جاهزة آنذاك، يُنفَّذ الفتح الآن بأمان داخل drawerLayout.post().
     *
     * @param searchMenuItem عنصر قائمة البحث من الـ Toolbar
     */
    public void bindSearchMenuItem(@NonNull MenuItem searchMenuItem) {
        this.searchMenuItem = searchMenuItem;
        setupSearchView();

        // ★ الإصلاح: تنفيذ الاستعادة المعلقة الآن بعد أن أصبحت الأيقونة حقيقية وجاهزة ★
        if (mPendingSearchRestore) {
            mPendingSearchRestore = false;
            if (drawerLayout != null) {
                drawerLayout.post(() -> {
                    if (this.searchMenuItem != null) {
                        // 1. فتح مربع البحث بصرياً
                        this.searchMenuItem.expandActionView();
                        // 2. إعادة كتابة النص المبحوث عنه
                        if (searchView != null && !savedSearchQuery.isEmpty()) {
                            searchView.setQuery(savedSearchQuery, false);
                            // إزالة التركيز لمنع لوحة المفاتيح من الانبثاق فجأة في وجه المستخدم
                           // searchView.clearFocus();
                        }
                    }
                });
            }
        }
    }

    public void setSearchStateListener(@Nullable SearchStateListener listener) {
        this.stateListener = listener;
    }

    // ════════════════════════════════════════════════════════
    //  إعداد SearchView
    // ════════════════════════════════════════════════════════

    private void setupSearchView() {
        if (searchMenuItem == null) {
            return;
        }

        searchView = (SearchView) searchMenuItem.getActionView();
        if (searchView == null) {
            Log.e(TAG, "SearchView is null");
            return;
        }

        searchView.setQueryHint(activity.getString(R.string.search_font));
        searchView.setMaxWidth(Integer.MAX_VALUE);

        SearchManager searchManager =
                (SearchManager) activity.getSystemService(Context.SEARCH_SERVICE);
        if (searchManager != null) {
            searchView.setSearchableInfo(
                    searchManager.getSearchableInfo(activity.getComponentName()));
        }

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                performSearch(query);
                if (stateListener != null) stateListener.onSearchQueryChanged(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                performSearch(newText);
                if (stateListener != null) stateListener.onSearchQueryChanged(newText);
                return true;
            }
        });

        searchMenuItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                return handleSearchExpand();
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                return handleSearchCollapse();
            }
        });

        Log.d(TAG, "SearchView setup completed successfully");
    }

    // ════════════════════════════════════════════════════════
    //  منطق فتح / إغلاق البحث
    // ════════════════════════════════════════════════════════

    private boolean handleSearchExpand() {
        isSearchExpanded = true;

        // ★ الإصلاح: تعطيل عنصر القائمة فور توسيع البحث ★
        // هذا يُلغي منطقة اللمس الشبحية للأيقونة الأصلية التي كانت تستجيب
        // خلف أزرار SearchView (زر الصوت وزر X وغيرهما) رغم إخفائها بصرياً.
        // تعطيل MenuItem لا يؤثر على SearchView لأنه action view مستقل.
        if (searchMenuItem != null) {
            searchMenuItem.setEnabled(false);
        }

        // ★ الإضافة الجوهرية: تعطيل اللمس الشبحي للثلاث نقاط ★
        // نستخدم post() لضمان تنفيذ التعطيل بعد انتهاء إطار Android من
        // معالجة توسيع الـ SearchView بالكامل، لأن الإطار يُعيد ضبط حالات
        // setEnabled/setClickable على عناصر القائمة أثناء عملية التوسيع.
        if (drawerLayout != null) {
            drawerLayout.post(() -> toggleToolbarGhostTouches(true));
        } else {
            toggleToolbarGhostTouches(true);
        }

        if (drawerLayout != null) {
            drawerLayout.setTitle(activity.getString(R.string.search_font));
            drawerLayout.setExpandedSubtitle(null);

            // ★ الميزة الجديدة: طي الـ AppBar بتأثير حركي عند فتح البحث
            // يمنح المستخدم مساحة أكبر لعرض النتائج ولوحة المفاتيح ★
            if (drawerLayout.getAppBarLayout() != null) {
                drawerLayout.getAppBarLayout().setExpanded(false, true);
            }
        }

        if (stateListener != null) {
            stateListener.onSearchExpanded();
        }

        Log.d(TAG, "Search expanded");
        return true;
    }

    // ════════════════════════════════════════════════════════
    // ★ الإصلاح (خطة الإصلاح الشاملة — الخطوة الأولى):
    //   handleSearchCollapse() أصبحت تُصفّر فلاتر جميع القوائم الثلاث مباشرةً
    //   بدلاً من الفراغمنت الحالي فقط. هذا يضمن نظافة الفلاتر عند العودة لأي
    //   شاشة حتى لو كان البحث قد انتقل لشاشة عارض الخطوط قبل الإغلاق.
    //
    // ★ الإصلاح (اعتراض إغلاق البحث عند وجود وضع التحديد المتعدد النشط):
    //   عند ضغط زر الرجوع وهو البحث مفتوحاً، يعترض SearchView الحدث ليُغلق
    //   نفسه مباشرةً قبل أن يصل إلى NavManager أو OnBackPressedDispatcher.
    //   لذلك نتحقق في بداية هذه الدالة من وجود وضع تحديد نشط في الفراغمنت
    //   الحالي؛ إذا كان نشطاً نغلقه ونُعيد false لمنع إغلاق البحث، فيبقى
    //   المستخدم في نتائج البحث. هذا مطابق لسلوك تطبيقات One UI الرسمية.
    // ════════════════════════════════════════════════════════

    private boolean handleSearchCollapse() {
        // ★ إذا تم طلب التجاهل برمجياً (عند إغلاق وضع التحديد)، نمنع إغلاق البحث فوراً ★
        if (ignoreNextCollapse) {
            ignoreNextCollapse = false;
            return false;
        }

        // ====================================================================
        // ★ الإصلاح الجوهري: منع إغلاق البحث إذا كان وضع التحديد المتعدد نشطاً ★
        // ====================================================================
        // عندما يكون البحث متمدداً، يعترض SearchView زر الرجوع قبل أن يصل إلى
        // NavManager. لذلك، قبل أن نسمح للبحث بالإغلاق، نتحقق مما إذا كان
        // وضع التحديد المتعدد نشطاً في الشاشة الحالية. إذا كان نشطاً، نغلقه
        // ونُعيد false لمنع البحث من الإغلاق، ليبقى المستخدم في نتائج البحث.
        Fragment currentFragment = getCurrentFragment();
        if (currentFragment != null) {
            boolean selectionHandled = false;
            if (currentFragment instanceof LocalFontListFragment) {
                selectionHandled = ((LocalFontListFragment) currentFragment).handleBackPressed();
            } else if (currentFragment instanceof FavoriteFontListFragment) {
                selectionHandled = ((FavoriteFontListFragment) currentFragment).handleBackPressed();
            } else if (currentFragment instanceof TrashFragment) {
                // سلة المحذوفات لا تحتوي بحثاً بالأساس، لكن وضعناها لتوحيد المنطق دفاعياً
                selectionHandled = ((TrashFragment) currentFragment).handleBackPressed();
            }

            if (selectionHandled) {
                Log.d(TAG, "Search collapse intercepted: Selection mode closed instead.");
                return false; // نُعيد false لنخبر MenuItem برفض عملية الإغلاق
            }
        }
        // ====================================================================

        isSearchExpanded = false;
        savedSearchQuery = "";

        // ★ الإصلاح: إعادة تفعيل عنصر القائمة عند طي البحث ★
        // يُعيد منطقة اللمس للأيقونة الأصلية حتى يتمكن المستخدم من فتح البحث مجدداً.
        if (searchMenuItem != null) {
            searchMenuItem.setEnabled(true);
        }

        // ★ الإضافة الجوهرية: إعادة تفعيل اللمس لأزرار الـ Toolbar ★
        toggleToolbarGhostTouches(false);

        if (searchView != null) {
            searchView.setQuery("", false);
        }

        // ★ الإصلاح: تصفير فلاتر جميع القوائم مباشرة من هنا لضمان نظافتها عند العودة ★
        // يُعالج حالة الانتقال لشاشة عارض الخطوط أثناء البحث: حتى لو اختفى searchMenuItem
        // من الشاشة، تبقى الفلاتر نظيفة في جميع القوائم عند العودة إليها.
        if (fragmentProvider != null) {
            Fragment localFrag = fragmentProvider.getFragment(AppScreen.LOCAL_FONTS);
            if (localFrag instanceof LocalFontListFragment) ((LocalFontListFragment) localFrag).resetFilter();

            Fragment sysFrag = fragmentProvider.getFragment(AppScreen.SYSTEM_FONTS);
            if (sysFrag instanceof SystemFontListFragment) ((SystemFontListFragment) sysFrag).resetFilter();

            Fragment favFrag = fragmentProvider.getFragment(AppScreen.FAVORITES);
            if (favFrag instanceof FavoriteFontListFragment) ((FavoriteFontListFragment) favFrag).resetFilter();
        }

        // ★ تنبيه MainActivity لتحديث العناوين وحالة الفراغمنتات ★
        if (stateListener != null) {
            stateListener.onSearchCollapsed();
        }

        Log.d(TAG, "Search collapsed");
        return true;
    }

    // ════════════════════════════════════════════════════════
    //  تنفيذ البحث والحصول على الـ Fragment الحالي
    // ════════════════════════════════════════════════════════

    /**
     * ★ تنفيذ البحث في الـ Fragment الظاهر حالياً ★
     *
     * يدعم ثلاث قوائم:
     *   - AppScreen.LOCAL_FONTS  → LocalFontListFragment  (الخطوط المحلية)
     *   - AppScreen.SYSTEM_FONTS → SystemFontListFragment (خطوط النظام)
     *   - AppScreen.FAVORITES    → FavoriteFontListFragment (المفضلة)
     */
    private void performSearch(String query) {
        Fragment currentFragment = getCurrentFragment();
        if (currentFragment instanceof LocalFontListFragment) {
            ((LocalFontListFragment) currentFragment).filterFonts(query);
            Log.d(TAG, "Search performed on LocalFontListFragment with query: " + query);
        } else if (currentFragment instanceof SystemFontListFragment) {
            ((SystemFontListFragment) currentFragment).filterFonts(query);
            Log.d(TAG, "Search performed on SystemFontListFragment with query: " + query);
        } else if (currentFragment instanceof FavoriteFontListFragment) {
            // ★ تمرير نص البحث لقائمة المفضلة لتصفية عناصرها في الذاكرة ★
            ((FavoriteFontListFragment) currentFragment).filterFonts(query);
            Log.d(TAG, "Search performed on FavoriteFontListFragment with query: " + query);
        }
    }

    /**
     * ★ الإصلاح: الحصول على الـ Fragment الحالي عبر AppScreen مباشرةً ★
     *
     * بدلاً من fragments.get(currentIndex)، يقرأ الآن من fragmentProvider
     * الذي يُعيد Fragment من mFragmentsMap — دائماً محدَّث بعد دوران الشاشة.
     */
    @Nullable
    private Fragment getCurrentFragment() {
        if (fragmentProvider == null || screenProvider == null) {
            return null;
        }
        // ★ الإصلاح: الحصول على الفراغمنت بالشاشة لا بالفهرس الرقمي ★
        AppScreen currentScreen = screenProvider.getCurrentScreen();
        return fragmentProvider.getFragment(currentScreen);
    }

    // ════════════════════════════════════════════════════════
    //  معالجة Intent البحث الخارجي
    // ════════════════════════════════════════════════════════

    /**
     * ★ الإصلاح: استخدام AppScreen بدلاً من int في handleSearchIntent ★
     *
     * يُقبل Intent البحث في قوائم الخطوط الثلاث: LOCAL_FONTS، SYSTEM_FONTS، FAVORITES.
     * يُرفض Intent البحث عند وجود فراجمنت آخر في المقدمة.
     */
    public boolean handleSearchIntent(@Nullable Intent intent) {
        if (intent == null || !Intent.ACTION_SEARCH.equals(intent.getAction())) {
            return false;
        }

        // ★ الإصلاح: مقارنة AppScreen بدلاً من مقارنة الأرقام الصلبة (2، 3، 4) ★
        AppScreen currentScreen = screenProvider.getCurrentScreen();
        if (currentScreen != AppScreen.LOCAL_FONTS
                && currentScreen != AppScreen.SYSTEM_FONTS
                && currentScreen != AppScreen.FAVORITES) {
            intent.removeExtra(SearchManager.QUERY);
            return false;
        }

        if (searchMenuItem == null || !searchMenuItem.isActionViewExpanded()) {
            intent.removeExtra(SearchManager.QUERY);
            return false;
        }

        String query = intent.getStringExtra(SearchManager.QUERY);
        if (query != null && searchView != null) {
            searchView.setQuery(query, false);
            performSearch(query);
            Log.d(TAG, "Search intent handled with query: " + query);
            return true;
        }

        return false;
    }

    // ════════════════════════════════════════════════════════
    //  حفظ الحالة واستعادتها
    // ════════════════════════════════════════════════════════

    /**
     * ★ الإصلاح: استخدام AppScreen بدلاً من int في saveState ★
     *
     * يحفظ الحالة لقوائم البحث: LOCAL_FONTS، SYSTEM_FONTS، FAVORITES.
     * المفضلة (FAVORITES) تُحفظ وتُستعاد كالقوائم الأخرى.
     *
     * ★ سطر الحماية: إذا لم يُستدعَ setProviders() بعد (screenProvider == null)
     * يُعاد فوراً بأمان دون رمي NullPointerException. ★
     */
    public void saveState(@NonNull Bundle outState) {
        if (screenProvider == null) return; // سطر الحماية

        // ★ الإصلاح: مقارنة AppScreen بدلاً من مقارنة الأرقام الصلبة ★
        AppScreen currentScreen = screenProvider.getCurrentScreen();
        boolean isSearchableScreen = (currentScreen == AppScreen.LOCAL_FONTS
                || currentScreen == AppScreen.SYSTEM_FONTS
                || currentScreen == AppScreen.FAVORITES);

        if (isSearchExpanded && isSearchableScreen) {
            outState.putBoolean(KEY_SEARCH_EXPANDED, true);
            if (searchView != null) {
                String currentQuery = searchView.getQuery().toString();
                outState.putString(KEY_SEARCH_QUERY, currentQuery);
            } else {
                outState.putString(KEY_SEARCH_QUERY, "");
            }
        } else {
            outState.putBoolean(KEY_SEARCH_EXPANDED, false);
            outState.putString(KEY_SEARCH_QUERY, "");
        }

        Log.d(TAG, "Search state saved - expanded: " + isSearchExpanded
                + ", query: " + savedSearchQuery);
    }

    /**
     * ★ الإصلاح: استخدام AppScreen بدلاً من int في restoreState ★
     *
     * يستعيد البحث في أي قائمة خطوط (LOCAL_FONTS، SYSTEM_FONTS، FAVORITES)
     * كانت ظاهرة عند حدوث إعادة البناء.
     *
     * ★ سطر الحماية: إذا لم يُستدعَ setProviders() بعد (screenProvider == null)
     * يُعاد فوراً بأمان دون رمي NullPointerException. ★
     * في الواقع العملي، setProviders() يُستدعى في setupSearchCoordinator() قبل
     * restoreState() مباشرةً، لكن سطر الحماية يضمن السلامة المطلقة. ★
     *
     * ★ إصلاح Race Condition: إذا كانت الأيقونة غير جاهزة بعد (searchMenuItem == null)،
     * يُفعَّل mPendingSearchRestore ليُنفَّذ الفتح لاحقاً في bindSearchMenuItem(). ★
     */
    public void restoreState(@NonNull Bundle savedInstanceState) {
        if (screenProvider == null) return; // سطر الحماية

        isSearchExpanded = savedInstanceState.getBoolean(KEY_SEARCH_EXPANDED, false);
        savedSearchQuery = savedInstanceState.getString(KEY_SEARCH_QUERY, "");

        // ★ الإصلاح: مقارنة AppScreen بدلاً من مقارنة الأرقام الصلبة ★
        AppScreen currentScreen = screenProvider.getCurrentScreen();
        boolean isSearchableScreen = (currentScreen == AppScreen.LOCAL_FONTS
                || currentScreen == AppScreen.SYSTEM_FONTS
                || currentScreen == AppScreen.FAVORITES);

        if (isSearchExpanded && isSearchableScreen) {
            if (searchMenuItem != null && drawerLayout != null) {
                drawerLayout.post(() -> {
                    if (searchMenuItem != null) {
                        searchMenuItem.expandActionView();
                        if (searchView != null && !savedSearchQuery.isEmpty()) {
                            searchView.setQuery(savedSearchQuery, false);
                        }
                    }
                });
            } else {
                // ★ الإصلاح: حفظ حالة الاستعادة لتطبيقها عند ربط الأيقونة لاحقاً ★
                // تحدث هذه الحالة عند تغيير اللغة حيث تُستدعى restoreState() قبل
                // أن تُرسم أيقونة البحث في onCreateOptionsMenu()
                mPendingSearchRestore = true;
            }
        }

        Log.d(TAG, "Search state restored - expanded: " + isSearchExpanded
                + ", query: " + savedSearchQuery);
    }

    // ════════════════════════════════════════════════════════
    //  الدوال العامة المساعدة
    // ════════════════════════════════════════════════════════

    /**
     * ★ الإصلاح (خطة الإصلاح الشاملة — الخطوة الأولى):
     *   collapseSearch() أصبحت تتحقق من isSearchExpanded أولاً لتجنب العمل غير الضروري.
     *   إذا كان searchMenuItem مخفياً أو غير متاح (مثلاً بعد الانتقال لشاشة عارض الخطوط)،
     *   تُجبر على إعادة ضبط الحالة مباشرةً عبر handleSearchCollapse() بدلاً من التوقف.
     *   هذا يحل مشكلة تجمّد العنوان وتعطّل زر الرجوع بعد الانتقال لعارض الخطوط
     *   أثناء فتح البحث في قائمة الخطوط المحلية أو خطوط النظام. ★
     */
    public void collapseSearch() {
        if (!isSearchExpanded) return;

        if (searchMenuItem != null && searchMenuItem.isActionViewExpanded()) {
            searchMenuItem.collapseActionView();
            Log.d(TAG, "Search collapsed programmatically");
        } else {
            // ★ الإصلاح الجوهري: إجبار إعادة ضبط الحالة إذا كان العنصر مخفياً أو غير متاح ★
            handleSearchCollapse();
        }
    }

    public void expandSearch() {
        if (searchMenuItem != null && !searchMenuItem.isActionViewExpanded()) {
            searchMenuItem.expandActionView();
            Log.d(TAG, "Search expanded programmatically");
        }
    }

    public void setSearchQuery(@NonNull String query) {
        if (searchView != null) {
            searchView.setQuery(query, false);
            performSearch(query);
        }
    }

    public boolean isSearchExpanded() {
        return isSearchExpanded;
    }

    @NonNull
    public String getCurrentSearchQuery() {
        if (searchView != null) {
            return searchView.getQuery().toString();
        }
        return savedSearchQuery;
    }

    public boolean isSearchActive() {
        return isSearchExpanded && !getCurrentSearchQuery().isEmpty();
    }

    public void clearSearchQuery() {
        if (searchView != null) {
            searchView.setQuery("", false);
            performSearch("");
        }
    }

    /**
     * ★ الإصلاح الجوهري: تغيير المعامل من int إلى AppScreen ★
     *
     * بدلاً من استقبال ordinal() وتخزينه في int lastFragmentIndex،
     * يُخزَّن الآن AppScreen مباشرةً في lastScreen.
     * هذا يضمن صحة المقارنة بغض النظر عن أي تغيير مستقبلي في ترتيب AppScreen.
     *
     * في NavManager يُستدعى هكذا:
     *   mHost.getSearchCoordinator().onFragmentChanged(screen)
     * بدلاً من:
     *   mHost.getSearchCoordinator().onFragmentChanged(screen.ordinal())
     */
    public void onFragmentChanged(AppScreen newScreen) {
        // ★ الإصلاح: مقارنة AppScreen بدلاً من مقارنة أرقام ordinal() ★
        if (lastScreen != null && !lastScreen.equals(newScreen)) {
            collapseSearch();
        }
        lastScreen = newScreen;

        Log.d(TAG, "Fragment changed to: " + newScreen.name());
    }

    // ════════════════════════════════════════════════════════
    //  قاتل اللمس الشبحي (Ghost Touch Killer)
    // ════════════════════════════════════════════════════════

    /**
     * يُعطّل أو يُفعّل مناطق اللمس للأزرار المخفية (كزر الثلاث نقاط)
     * لمنع استجابتها للمس أثناء تمدد الـ SearchView.
     */
    private void toggleToolbarGhostTouches(boolean isSearchActive) {
        if (activity == null) return;

        // 1. الحصول على الـ Toolbar مباشرةً
        androidx.appcompat.widget.Toolbar toolbar = null;
        if (drawerLayout != null) {
            toolbar = drawerLayout.getToolbar();
        }
        if (toolbar == null) {
            toolbar = activity.findViewById(R.id.toolbar);
        }

        if (toolbar == null) return;

        // 2. إغلاق أي قائمة منبثقة (Popup Menu) معلقة
        if (isSearchActive) {
            toolbar.dismissPopupMenus();

            // ★ الحل السحري الأول: إعدام الـ TouchDelegate ★
            // يمسح إحداثيات اللمس الشبحية (الـ 100 بكسل الإضافية) التي كونتها
            // مكتبة سامسونج (SeslTouchTargetDelegate) للزر قبل اختفائه.
            toolbar.setTouchDelegate(null);
        }

        // 3. البحث عن حاوية الأيقونات والثلاث نقاط (ActionMenuView)
        for (int i = 0; i < toolbar.getChildCount(); i++) {
            View child = toolbar.getChildAt(i);
            if (child instanceof ActionMenuView) {
                // إخفاء الحاوية الأم بصرياً
                child.setVisibility(isSearchActive ? View.GONE : View.VISIBLE);
                child.setEnabled(!isSearchActive);

                // الدخول إلى الحاوية وتطبيق الحظر على جميع الأزرار بداخلها
                if (child instanceof ViewGroup) {
                    ViewGroup actionMenuView = (ViewGroup) child;
                    for (int j = 0; j < actionMenuView.getChildCount(); j++) {
                        View menuChild = actionMenuView.getChildAt(j);

                        // ★ الحل السحري الثاني: الإخفاء الكلي (GONE) ★
                        // إخفاء زر الثلاث نقاط بـ GONE يضمن أن نظام سامسونج لن
                        // يضع له منطقة لمس جديدة في دورة الرسم (onGlobalLayout) القادمة.
                        // كما يوقف المستمع (ForwardingListener) الذي يفتح القائمة المنبثقة.
                        menuChild.setVisibility(isSearchActive ? View.GONE : View.VISIBLE);
                        menuChild.setEnabled(!isSearchActive);
                        menuChild.setClickable(!isSearchActive);
                    }
                }
            }
        }
    }

    /** دالة مساعدة للعثور على Toolbar برمجياً مهما كان موقعه في شجرة الـ Views */
    private androidx.appcompat.widget.Toolbar findToolbar(ViewGroup root) {
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child instanceof androidx.appcompat.widget.Toolbar) {
                return (androidx.appcompat.widget.Toolbar) child;
            } else if (child instanceof ViewGroup) {
                androidx.appcompat.widget.Toolbar found = findToolbar((ViewGroup) child);
                if (found != null) return found;
            }
        }
        return null;
    }

    public void cleanup() {
        searchMenuItem        = null;
        searchView            = null;
        screenProvider        = null;
        fragmentProvider      = null;
        stateListener         = null;
        mPendingSearchRestore = false;

        Log.d(TAG, "SearchCoordinator cleaned up");
    }
                    }
