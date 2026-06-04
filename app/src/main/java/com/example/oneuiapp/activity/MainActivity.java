package com.example.oneuiapp.activity;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.splashscreen.SplashScreen;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.app.AlertDialog;

//import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;


import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import dev.oneuiproject.oneui.layout.DrawerLayout;
import dev.oneuiproject.oneui.dialog.ProgressDialog;

import com.example.oneuiapp.dialog.FontInfoDialog;
import com.example.oneuiapp.fragment.FontViewerFragment;
import com.example.oneuiapp.fragment.LocalFontListFragment;
import com.example.oneuiapp.fragment.SystemFontListFragment;
import com.example.oneuiapp.fragment.FavoriteFontListFragment;
import com.example.oneuiapp.fragment.TrashFragment;
import com.example.oneuiapp.ui.drawer.DrawerListAdapter;
import com.example.oneuiapp.R;
import com.example.oneuiapp.utils.FileUtils;
import com.example.oneuiapp.utils.TranslationService;
import com.example.oneuiapp.fontlist.search.SearchCoordinator;

/**
 * MainActivity - معدّل لعرض العناوين بشكل صحيح
 * العنوان الرئيسي: الاسم الحقيقي للخط
 * العنوان الفرعي: اسم الملف بدون صيغة
 *
 * ★ نظام التنقل ★
 * جميع منطق التنقل (زر الرجوع، المكدس، الأنيميشن، إدارة الفراغمنتات)
 * منقول إلى NavManager.java للفصل بين مسؤوليات النشاط ومنطق التنقل.
 * تُنفّذ هذه الفئة واجهة NavManager.Host لتزويد NavManager بما يحتاجه
 * دون اقتران مباشر بين الكلاسين.
 *
 * ★ آلية رصد حالة الدرج ★
 * مكتبة OneUI تُغلِّف الـ androidx.drawerlayout.widget.DrawerLayout داخلياً
 * ولا تُوفّر دالة عامة للوصول إليه. يتولى NavManager البحث عنه مرة واحدة
 * في setup() عبر اجتياز شجرة الـ Views، واستدعاء isDrawerOpen() مباشرةً
 * دون الحاجة لأي متغير تتبع منفصل أو مستمع إضافي.
 *
 * ★ التعديل: تحديث onFontSelected لاستقبال weightWidthLabel وتمريره لـ NavManager ★
 * يجب تحديث واجهتَي LocalFontListFragment.OnFontSelectedListener
 * و SystemFontListFragment.OnFontSelectedListener في ملفي الفراغمنتات المقابلين
 * ليتضمنا المعامل الجديد String weightWidthLabel.
 *
 * ★ الإضافة: تنفيذ FavoriteFontListFragment.OnFontSelectedListener ★
 * يتيح لـ FavoriteFontListFragment استدعاء onFontSelected عبر
 * mFontSelectedListener = (OnFontSelectedListener) context
 * كما تفعل LocalFontListFragment و SystemFontListFragment.
 * لا يُضاف تنفيذ جديد لـ onFontSelected لأن التوقيع مطابق
 * للتنفيذ الموجود من LocalFontListFragment.OnFontSelectedListener.
 *
 * ★ الإصلاح (المشكلة 5): تحديث getFontsCountString() لاستخدام getQuantityString() ★
 * استبدال المنطق البدائي (if/else) بنظام <plurals> الرسمي من Android،
 * مما يُطبّق قواعد الجمع العربية الكاملة (مفرد/مثنى/جمع قلة/جمع كثرة)
 * والإنجليزية تلقائياً دون أي كود إضافي.
 *
 * ★ إضافة TrashFragment ★
 * يُمثّل سلة المحذوفات في درج التنقل ويعرض عدد العناصر في عنوانه الفرعي.
 * لا يدعم البحث ولا زر عارض المعلومات ولا زر FAB.
 *
 * ★ الإصلاح الجوهري (الأرقام السحرية → AppScreen Enum) ★
 * الخطوة الأولى: استبدال List<Fragment> بـ Map<AppScreen, Fragment>
 *   - يُغلق باب الأرقام السحرية نهائياً: لا يوجد index 0، 1، 2...
 *   - كل شاشة تُعرِّف نفسها بـ AppScreen.TRASH لا بالرقم 5.
 *   - HomeFragment محذوفة من Map الفراغمنتات لتوفير RAM — تُفتح كـ HomeActivity منفصل.
 *
 * الخطوة الثانية: استبدال int mCurrentFragmentIndex بـ AppScreen mCurrentScreen
 *   - كل دالة تستقبل position (int) أصبحت تستقبل AppScreen مباشرةً.
 *   - updateDrawerTitle لا تقارن أرقاماً.
 *
 * ★ إصلاح إضافي: معالجة EXTRA_TARGET_FRAGMENT كـ String ★
 * بدلاً من إرسال فهرس رقمي في PendingIntent (targetIndex=5 للسلة)،
 * يُرسَل الآن AppScreen.name() كـ String ("TRASH"، "LOCAL_FONTS"...)
 * ثم يُحوَّل في handleIntent() عبر AppScreen.valueOf(name).
 * هذا يقطع الاعتماد الأخير على الأرقام في نظام الإشعارات.
 *
 * ★ إصلاح سباق الزمني (Race Condition) عند تغيير اللغة ★
 * تم فصل إعداد SearchCoordinator إلى مرحلتين:
 *   - setupSearchCoordinator() في onCreate() → setProviders() + setSearchStateListener()
 *     لتجهيز البيانات المنطقية قبل أي استدعاء لـ restoreState() أو saveState().
 *   - bindSearchMenuItem() في onCreateOptionsMenu() كل Fragment → ربط الأيقونة بعد رسمها.
 *
 * ★ المرحلة الأولى من خطة التحسين: اللامركزية في قوائم AppBar ★
 * أيقونات الـ AppBar انتقلت من هذا النشاط إلى الفراجمنتات المعنية:
 *   - LocalFontListFragment  → menu_font_list_search + menu_local_fonts_more
 *   - SystemFontListFragment → menu_font_list_search
 *   - FavoriteFontListFragment → menu_font_list_search
 *   - TrashFragment          → menu_trash_more
 *   - FontViewerFragment     → menu_main_font_meta
 * كل فراجمنت يستدعي setHasOptionsMenu(true) ويُدير أيقوناته عبر دورة حياته الخاصة.
 * هذا يجعل اختفاء الأيقونات أمراً مستحيلاً لأن دورة حياة الأيقونة مرتبطة
 * بالشاشة المعروضة فقط لا بالنشاط بأكمله.
 *
 * ★ الإصلاح (خطة الإصلاح الشاملة — الخطوة الثانية):
 *   - updateDrawerTitle() تسمح الآن لشاشة FONT_VIEWER بتحديث عنوانها حتى
 *     لو كان البحث مفتوحاً في شاشة أخرى، لأن الانتقال لعارض الخطوط يستوجب
 *     عرض اسم الخط المحمّل بغض النظر عن حالة البحث.
 *   - setupSearchCoordinator() أصبحت تُشعر الفراغمنتات فور تمدد أو طي البحث
 *     عبر notifyFragmentsSearchState()، مما يضمن إخفاء زر الثلاث نقاط فوراً
 *     بدلاً من انتظار كتابة أول حرف. ★
 */
public class MainActivity extends BaseActivity
        implements FontViewerFragment.OnFontChangedListener,
        LocalFontListFragment.OnFontSelectedListener,
        SystemFontListFragment.OnFontSelectedListener,
        FavoriteFontListFragment.OnFontSelectedListener,
        NavManager.Host {

    private boolean isUIReady = false;
    private DrawerLayout mDrawerLayout;
    private RecyclerView mDrawerListView;
    private DrawerListAdapter mDrawerAdapter;

    // ════════════════════════════════════════════════════════════════════════
    //  ★ الخطوة الأولى: Map<AppScreen, Fragment> بدلاً من List<Fragment> ★
    //
    //  الفائدة الجوهرية:
    //  - HomeFragment لا تُضاف هنا → تُوفَّر الذاكرة وتُفتح فقط عند الطلب.
    //  - مفتاح الوصول للفراغمنت هو AppScreen.TRASH لا الرقم 5،
    //    مما يجعل الكود محصّناً ضد أي تغيير مستقبلي في ترتيب الشاشات.
    // ════════════════════════════════════════════════════════════════════════
    private final Map<AppScreen, Fragment> mFragmentsMap = new EnumMap<>(AppScreen.class);

    // ★ الخطوة الثانية: AppScreen بدلاً من int ★
    // الشاشة الافتراضية عند الإطلاق الأول هي عارض الخطوط
    private AppScreen mCurrentScreen = AppScreen.FONT_VIEWER;

    private static final String KEY_CURRENT_SCREEN          = "current_screen";
    private static final String KEY_LOCAL_FONTS_COUNT        = "local_fonts_count";
    private static final String KEY_SYSTEM_FONTS_COUNT       = "system_fonts_count";
    private static final String KEY_FAVORITE_FONTS_COUNT     = "favorite_fonts_count";
    private static final String KEY_TRASH_FONTS_COUNT        = "trash_fonts_count";

    // ★ إصلاح (1): رمز طلب إذن POST_NOTIFICATIONS — يجب أن يكون فريداً لتمييزه ★
    private static final int PERM_REQUEST_POST_NOTIFICATIONS = 1001;

    /**
     * ★ إصلاح (2)(3): Extra المُرفَق في PendingIntent لإشعارات العمليات ★
     *
     * يُخبر MainActivity بـ AppScreen التي يجب الانتقال إليها عند الضغط على الإشعار.
     * يُستخدم في:
     *   - TrashActionDialogs.buildContentIntent()
     *   - OperationForegroundService.buildInitialNotification()
     *
     * ★ القيمة الآن String (AppScreen.name()) بدلاً من int ★
     *   "LOCAL_FONTS" = الخطوط المحلية
     *   "TRASH"       = سلة المحذوفات
     *   null / غائب   = يُبقي المستخدم على الشاشة الحالية
     *
     * هذا يُزيل آخر اعتماد على الأرقام في نظام الإشعارات،
     * ويجعل التوجيه محصّناً ضد أي تغيير في ترتيب AppScreen.
     */
    public static final String EXTRA_TARGET_FRAGMENT = "target_fragment";

    private String currentFontRealName;
    private String currentFontFileName;

    // ★ الإصلاح الجوهري: فصل عداد المجلد المحلي عن عداد خطوط النظام ★
    // هذا يمنع أي فراجمنت من الكتابة فوق عدد الفراجمنت الآخر عند إعادة البناء
    private int mLocalFontsCount    = 0;
    private int mSystemFontsCount   = 0;
    // ★ عداد مستقل لخطوط المفضلة لمنع التداخل مع عدادات القوائم الأخرى ★
    private int mFavoriteFontsCount = 0;
    // ★ عداد مستقل لعناصر سلة المحذوفات ★
    private int mTrashFontsCount    = 0;

    // ★ مدير التنقل المركزي — يتولى جميع عمليات التنقل بين الشاشات ★
    // يتواصل مع هذا النشاط عبر واجهة NavManager.Host المُنفَّذة أدناه
    private NavManager mNavManager;

    // ★ المرحلة الأولى: حُذفت حقول MenuItem من هنا ★
    // mFontMetaMenuItem   → انتقل إلى FontViewerFragment
    // mSearchMenuItem     → انتقل إلى LocalFontListFragment + SystemFontListFragment + FavoriteFontListFragment
    // mEmptyTrashMenuItem → انتقل إلى TrashFragment
    // mLocalFontsMoreMenuItem → انتقل إلى LocalFontListFragment
    // كل فراجمنت يُدير أيقوناته بشكل مستقل عبر setHasOptionsMenu(true)

    private ExtendedFloatingActionButton fabFontSize;



    private SearchCoordinator mSearchCoordinator;

    private ProgressDialog loadingDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);

        super.onCreate(savedInstanceState);

        splashScreen.setKeepOnScreenCondition(() -> !isUIReady);

        if (android.os.Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, android.R.anim.fade_in, android.R.anim.fade_out);
        }

        setContentView(R.layout.activity_main);

        // ★ تهيئة NavManager مبكراً قبل أي دالة تستدعي التنقل ★
        mNavManager = new NavManager(this);

        initViews();
        initFragmentsList();

        // ★ إصلاح سباق الزمني: إعداد SearchCoordinator مبكراً بالبيانات المنطقية ★
        // setProviders() و setSearchStateListener() يُستدعيان هنا في onCreate() لضمان
        // جهوزية screenProvider قبل استدعاء restoreState() أو saveState() من قِبَل النظام.
        // أما ربط الأيقونة (bindSearchMenuItem) فيتم الآن داخل كل Fragment عبر
        // onCreateOptionsMenu() بعد أن تُرسم الأيقونة على الشاشة فعلياً.
        setupSearchCoordinator();

        setupDrawerButton();

        if (savedInstanceState != null) {
            // ★ استعادة العدادات المستقلة عند إعادة البناء ★
            mLocalFontsCount    = savedInstanceState.getInt(KEY_LOCAL_FONTS_COUNT, 0);
            mSystemFontsCount   = savedInstanceState.getInt(KEY_SYSTEM_FONTS_COUNT, 0);
            mFavoriteFontsCount = savedInstanceState.getInt(KEY_FAVORITE_FONTS_COUNT, 0);
            // ★ استعادة عداد سلة المحذوفات عند إعادة البناء ★
            mTrashFontsCount    = savedInstanceState.getInt(KEY_TRASH_FONTS_COUNT, 0);
            // ★ استعادة مكدس التنقل عند إعادة البناء — مُفوَّضة لـ NavManager ★
            mNavManager.restoreNavBackStack(savedInstanceState);
            restoreFragmentsState(savedInstanceState);
            mSearchCoordinator.restoreState(savedInstanceState);
        } else {
            addAllFragments();
            mCurrentScreen = AppScreen.FONT_VIEWER;
            mNavManager.showFragmentFast(AppScreen.FONT_VIEWER);
        }

        setupDrawer();
        setupFabFontSize();
        updateDrawerTitle(mCurrentScreen);

        handleIntent(getIntent());

        // ★ إصلاح (1): طلب إذن الإشعارات بعد اكتمال تهيئة الواجهة ★
        // يُستدعى بعد handleIntent() لضمان جهوزية الـ NavManager قبل أي تنقل محتمل
        requestNotificationPermissionIfNeeded();

        isUIReady = true;
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    /**
     * معالج الـ Intents الواردة — يُستدعى من onCreate() ومن onNewIntent().
     *
     * يتولى معالجة نوعين من الـ Intents:
     *   1. Intents البحث (SearchManager) — مُفوَّضة للـ SearchCoordinator.
     *   2. ★ إصلاح (2)(3): Intents الإشعارات — التنقل للشاشة الصحيحة وإعادة عرض الديالوج.
     *
     * ★ إصلاح (2)(3) — آلية العمل الكاملة:
     *   أ. TrashActionDialogs.buildContentIntent() و OperationForegroundService
     *      يُضيفان EXTRA_TARGET_FRAGMENT (AppScreen.name()) إلى PendingIntent
     *      وكذلك "from_notification" = true.
     *   ب. عند ضغط المستخدم على الإشعار يُفتح التطبيق:
     *      - إذا كان التطبيق يعمل: onNewIntent() → handleIntent() → هذا الكود.
     *      - إذا كان التطبيق مُغلقاً: onCreate() → handleIntent() → هذا الكود.
     *   ج. fromNotif = true → يُعيَّن BatchOperationState.setShouldReopenDialog(true).
     *   د. navigateFromDrawer(targetScreen) يُنقل المستخدم للشاشة الصحيحة.
     *   هـ. الشاشة المستهدفة تستدعي checkAndReopenProgressDialogPublic() تلقائياً.
     *   و. إذا كنا في الشاشة الصحيحة بالفعل، نُجبر الديالوج على الفتح مباشرةً.
     *
     * ★ تنظيف الـ Extras:
     *   intent.removeExtra() يمنع إعادة التنقل عند دوران الشاشة.
     *
     * ★ الإصلاح الجوهري: EXTRA_TARGET_FRAGMENT كـ String (AppScreen.name()) ★
     * يُحوَّل إلى AppScreen عبر AppScreen.valueOf(name) ثم يُمرَّر لـ navigateFromDrawer().
     */
    private void handleIntent(android.content.Intent intent) {
        mSearchCoordinator.handleSearchIntent(intent);

        if (intent != null) {
            // ★ التحقق مما إذا كان الدخول عبر الإشعار
            boolean fromNotif = intent.getBooleanExtra("from_notification", false);
            if (fromNotif) {
                com.example.oneuiapp.utils.BatchOperationState.setShouldReopenDialog(true);
                intent.removeExtra("from_notification");
            }

            // ★ الإصلاح: قراءة AppScreen.name() كـ String بدلاً من int ★
            String targetScreenName = intent.getStringExtra(EXTRA_TARGET_FRAGMENT);
            if (targetScreenName != null) {
                // تنظيف الـ Extra فوراً لمنع إعادة التنقل في حالات إعادة البناء
                intent.removeExtra(EXTRA_TARGET_FRAGMENT);
                try {
                    AppScreen targetScreen = AppScreen.valueOf(targetScreenName);
                    if (targetScreen != AppScreen.HOME) {
                        if (mNavManager != null && mCurrentScreen != targetScreen) {
                            // التنقل للشاشة المستهدفة — ستستدعي checkAndReopenProgressDialogPublic() تلقائياً
                            mNavManager.navigateFromDrawer(targetScreen);
                        } else if (fromNotif) {
                            // ★ إذا كنا في الشاشة الصحيحة بالفعل وضغطنا على الإشعار، نجبر الديالوج على الفتح ★
                            Fragment frag = mFragmentsMap.get(targetScreen);
                            if (frag instanceof LocalFontListFragment) {
                                ((LocalFontListFragment) frag).checkAndReopenProgressDialogPublic();
                            } else if (frag instanceof TrashFragment) {
                                ((TrashFragment) frag).checkAndReopenProgressDialogPublic();
                            } else if (frag instanceof FavoriteFontListFragment) {
                                ((FavoriteFontListFragment) frag).checkAndReopenProgressDialogPublic();
                            }
                        }
                    }
                } catch (IllegalArgumentException e) {
                    // ★ اسم شاشة غير معروف — تجاهل بأمان ★
                    android.util.Log.w("MainActivity", "Unknown AppScreen name: " + targetScreenName);
                }
            }
        }
    }

    /**
     * ★ إصلاح (1): طلب إذن POST_NOTIFICATIONS في وقت التشغيل لـ API 33+ ★
     *
     * ملخص المشكلة:
     *   POST_NOTIFICATIONS مُعلَن في AndroidManifest.xml، لكن Android 13 (Tiramisu)
     *   أضاف شرطاً جديداً: يجب على التطبيق طلب الإذن صراحةً عبر requestPermissions()
     *   وانتظار موافقة المستخدم. الإعلان في الـ Manifest وحده لا يكفي.
     *
     * أثر غياب الإذن:
     *   NotificationManagerCompat.notify() تصمت تماماً دون رمي أي استثناء
     *   أو تسجيل أي رسالة خطأ، مما يجعل تشخيص المشكلة صعباً جداً.
     *   هذا هو السبب الجذري للمشكلة (1): لا يظهر أي إشعار أثناء نقل الملفات.
     *
     * آلية الطلب:
     *   يُستدعى هذا الطلب مرة واحدة فقط عند الإطلاق الأول. إذا منح المستخدم الإذن
     *   لاحقاً من إعدادات الجهاز، تعمل الإشعارات في الجلسة التالية.
     *   لا نحتاج لمعالجة نتيجة الطلب (onRequestPermissionsResult) لأن الإشعارات
     *   اختيارية من منظور منطق التطبيق — التطبيق يعمل بالكامل بدونها.
     */
    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        PERM_REQUEST_POST_NOTIFICATIONS
                );
            }
        }
    }

    private void initViews() {
        mDrawerLayout   = findViewById(R.id.drawer_layout);
        mDrawerListView = findViewById(R.id.drawer_list_view);
        fabFontSize = findViewById(R.id.fab_font_size);
    }


    /**
     * ★ الخطوة الأولى: تهيئة Map الفراغمنتات بدون HomeFragment ★
     *
     * HomeFragment حُذفت عمداً من هذا الـ Map:
     *   - لتوفير الذاكرة (RAM): HomeFragment لا تُفتح كثيراً وتظل محملة طوال الوقت.
     *   - فتحها كـ HomeActivity مستقل يعني أنها تُدار بدورة حياة منفصلة
     *     وتُحذف من الذاكرة تلقائياً عند العودة منها.
     *   - وجودها في قائمة الدرج (DrawerListAdapter) لا يتطلب وجودها في Map الفراغمنتات.
     *
     * كل فراغمنت يُخزَّن بمفتاح AppScreen المناسب.
     * الـ Tag المُستخدَم عند إضافته لـ FragmentManager = AppScreen.name()
     * (مثل "FONT_VIEWER"، "LOCAL_FONTS"...).
     */
    private void initFragmentsList() {
        if (mFragmentsMap.isEmpty()) {
            // ★ HomeFragment لا تُضاف هنا — تُفتح كـ HomeActivity عند الضغط على الدرج ★
            mFragmentsMap.put(AppScreen.FONT_VIEWER, new FontViewerFragment());
            mFragmentsMap.put(AppScreen.LOCAL_FONTS, new LocalFontListFragment());
            mFragmentsMap.put(AppScreen.SYSTEM_FONTS, new SystemFontListFragment());
            mFragmentsMap.put(AppScreen.FAVORITES, new FavoriteFontListFragment());
            mFragmentsMap.put(AppScreen.TRASH, new TrashFragment());
        }
    }

    /**
     * ★ إصلاح سباق الزمني: إعداد SearchCoordinator بمرحلتين ★
     *
     * هذه الدالة تُنفّذ المرحلة الأولى فقط — البيانات المنطقية — وتُستدعى
     * من onCreate() قبل restoreState() مباشرةً.
     *
     * ★ المرحلة الأولى من خطة التحسين:
     *   المرحلة الثانية (ربط الأيقونة) تتم الآن داخل كل Fragment عبر
     *   bindSearchMenuItem() في onCreateOptionsMenu() بعد أن تُرسم الأيقونة على
     *   الشاشة فعلياً. هذا يُحلّ مشكلة اختفاء الأيقونات نهائياً لأن دورة حياة
     *   الأيقونة أصبحت مرتبطة بدورة حياة الفراغمنت لا بالنشاط.
     *
     * السبب الجذري للكراش الذي كان يحدث عند تغيير اللغة:
     *   النظام يستدعي saveState() ثم onCreate() ثم restoreState() تسلسلياً.
     *   إذا كان screenProvider لا يزال null في هذه اللحظة (لأن setup() كانت
     *   تُستدعى فقط في onCreateOptionsMenu() اللاحق)، حدث NullPointerException.
     *   الحل: استدعاء setProviders() هنا يضمن جهوزية screenProvider قبل
     *   أي محاولة للوصول إليه من saveState() أو restoreState().
     *
     * ★ الإصلاح (خطة الإصلاح الشاملة — الخطوة الثانية):
     *   onSearchExpanded()  → يُشعر جميع الفراغمنتات فور تمدد البحث لإخفاء
     *                         زر الثلاث نقاط في نفس اللحظة.
     *   onSearchCollapsed() → يُشعر جميع الفراغمنتات بطي البحث لإعادة الأزرار
     *                         المخفية وتحديث العناوين. ★
     */
    private void setupSearchCoordinator() {
        mSearchCoordinator = new SearchCoordinator(this, mDrawerLayout);

        // 1. تزويد المنسق بالبيانات المنطقية فوراً (بدون الأيقونة)
        mSearchCoordinator.setProviders(
                () -> mCurrentScreen,
                screen -> mFragmentsMap.get(screen)
        );

        // 2. إعداد مستمع الحالة
        mSearchCoordinator.setSearchStateListener(new SearchCoordinator.SearchStateListener() {
            @Override
            public void onSearchExpanded() {
                // ★ إشعار الفراغمنتات فور تمدد البحث لإخفاء زر الثلاث نقاط ★
                notifyFragmentsSearchState(true);
            }

            @Override
            public void onSearchCollapsed() {
                updateDrawerTitle(mCurrentScreen);
                // ★ إشعار الفراغمنتات بطي البحث لإعادة الأزرار المخفية ★
                notifyFragmentsSearchState(false);
            }

            @Override
            public void onSearchQueryChanged(String query) {
            }
        });
    }

    /**
     * ★ الإصلاح (خطة الإصلاح الشاملة — الخطوة الثانية):
     *   دالة مساعدة لتمرير حالة البحث إلى الفراغمنتات النشطة ★
     *
     * تُستدعى من onSearchExpanded() و onSearchCollapsed() في setupSearchCoordinator().
     * تُشعر الفراغمنتات الثلاثة بحالة البحث بغض النظر عن أيها هو الظاهر حالياً،
     * مما يضمن أن زر الثلاث نقاط يختفي فوراً عند تمدد البحث حتى قبل كتابة أي حرف،
     * وأن SearchViewModel في كل فراجمنت مزامَن مع الحالة البصرية دائماً.
     *
     * @param isExpanded true عند تمدد البحث، false عند طيّه
     */
    private void notifyFragmentsSearchState(boolean isExpanded) {
        Fragment localFrag = mFragmentsMap.get(AppScreen.LOCAL_FONTS);
        if (localFrag instanceof LocalFontListFragment) {
            ((LocalFontListFragment) localFrag).onSearchStateChanged(isExpanded);
        }

        Fragment sysFrag = mFragmentsMap.get(AppScreen.SYSTEM_FONTS);
        if (sysFrag instanceof SystemFontListFragment) {
            ((SystemFontListFragment) sysFrag).onSearchStateChanged(isExpanded);
        }

        Fragment favFrag = mFragmentsMap.get(AppScreen.FAVORITES);
        if (favFrag instanceof FavoriteFontListFragment) {
            ((FavoriteFontListFragment) favFrag).onSearchStateChanged(isExpanded);
        }
    }

    /**
     * ★ المرحلة الأولى من خطة التحسين: onCreateOptionsMenu في النشاط أصبح فارغاً ★
     *
     * كانت هذه الدالة تنفخ جميع قوائم جميع الشاشات وتُخفي غير المطلوبة منها.
     * هذا النهج كان السبب الجذري لاختفاء الأيقونات عند التنقل.
     *
     * الآن: كل فراجمنت يتولى نفخ قائمته الخاصة عبر onCreateOptionsMenu() الخاص به،
     * وربط SearchCoordinator من داخل الفراجمنتات المعنية.
     * هذا يجعل دورة حياة الأيقونة مرتبطة بدورة حياة الفراجمنت المعروض فعلاً.
     *
     * نُعيد true لإعلام النظام أن القائمة جاهزة (الفراجمنتات ستُضيف مساهماتها).
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // ★ المرحلة الأولى: النشاط لا يُنفَّخ أي قائمة — الفراجمنتات تتولى ذلك ★
        return true;
    }

    /**
     * ★ المرحلة الأولى من خطة التحسين: onOptionsItemSelected في النشاط أصبح يُحيل لـ super ★
     *
     * كانت هذه الدالة تعالج نقرات أزرار تخص الفراجمنتات (معلومات الخط، إفراغ السلة، تغيير المجلد).
     * الآن: كل فراجمنت يعالج نقرات أيقوناته في onOptionsItemSelected() الخاص به.
     */
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        // ★ المرحلة الأولى: معالجة النقرات أصبحت في عهدة كل Fragment بشكل مستقل ★
        return super.onOptionsItemSelected(item);
    }

    /**
     * ★ دالة مساعدة: البحث عن فراغمنت بنوعه في Map الفراغمنتات ★
     *
     * @param type نوع الفراغمنت المطلوب
     * @return أول فراغمنت من هذا النوع، أو null إن لم يُعثر عليه
     */
    @androidx.annotation.Nullable
    private <T extends Fragment> Fragment findFragmentByType(Class<T> type) {
        for (Fragment f : mFragmentsMap.values()) {
            if (type.isInstance(f)) return f;
        }
        return null;
    }

    /**
     * ★ عرض معلومات الخط — أصبحت public لاستدعائها من FontViewerFragment ★
     *
     * يُستدعى من FontViewerFragment.onOptionsItemSelected() عند الضغط على
     * زر معلومات الخط (action_font_meta) في الـ AppBar.
     *
     * ★ المرحلة الأولى: الدالة انتقلت من private إلى public ★
     * لتمكين FontViewerFragment من استدعائها مباشرةً دون وسيط.
     */
    public void showFontMetaFromFragment() {
        Fragment frag = findFragmentByType(FontViewerFragment.class);
        if (!(frag instanceof FontViewerFragment)) {
            showNoFontDialog();
            return;
        }

        FontViewerFragment fvf = (FontViewerFragment) frag;
        if (!fvf.hasFontSelected()) {
            showNoFontDialog();
            return;
        }

        Map<String, String> meta = fvf.getFontMetaData();

        TranslationService translationService = new TranslationService(this);
        if (translationService.isTranslationEnabled()) {
            showLoadingDialog();
            translationService.translateMetadata(meta, new TranslationService.TranslationCallback() {
                @Override
                public void onTranslationComplete(Map<String, String> translatedData) {
                    runOnUiThread(() -> {
                        dismissLoadingDialog();
                        showFontInfoDialog(translatedData);
                    });
                }

                @Override
                public void onTranslationFailed(String error) {
                    runOnUiThread(() -> {
                        dismissLoadingDialog();
                        android.util.Log.w("MainActivity", "Translation failed: " + error);
                        showFontInfoDialog(meta);
                    });
                }
            });
        } else {
            showFontInfoDialog(meta);
        }
    }

    /**
     * ★ الإضافة: استقبال حالة المجلد المحلي من LocalFontListFragment ★
     *
     * ★ المرحلة الأولى: هذه الدالة أصبحت لا عملية (no-op) ★
     * ظهور زر تغيير المجلد يُدار الآن داخل LocalFontListFragment مباشرةً
     * عبر onCreateOptionsMenu() و invalidateOptionsMenu().
     * نُبقي على الدالة للتوافق مع الكود الموجود في LocalFontListFragment.
     *
     * @param selected true إذا تم اختيار مجلد خطوط، false إذا لم يُختر بعد
     */
    public void setLocalFolderSelected(boolean selected) {
        // ★ المرحلة الأولى: ظهور زر تغيير المجلد يُدار الآن داخل LocalFontListFragment ★
        // هذه الدالة أصبحت لا عملية (no-op)
    }

    private void setupDrawerButton() {
        if (mDrawerLayout != null) {
            mDrawerLayout.setDrawerButtonIcon(getDrawable(dev.oneuiproject.oneui.R.drawable.ic_oui_settings_outline));
            mDrawerLayout.setDrawerButtonTooltip(getText(R.string.title_settings));
            mDrawerLayout.setDrawerButtonOnClickListener(v -> openSettingsActivity());
        }
    }

    private void setDrawerOpen(boolean open, boolean animate) {
        if (mDrawerLayout != null) {
            mDrawerLayout.setDrawerOpen(open, animate);
        }
    }

    private void openSettingsActivity() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    private void setupFabFontSize() {
        if (fabFontSize != null) {
            fabFontSize.setOnClickListener(v -> {
                // ★ الإصلاح: استخدام mFragmentsMap.get(FONT_VIEWER) بدلاً من mFragments.get(1) ★
                Fragment currentFragment = mFragmentsMap.get(mCurrentScreen);
                if (currentFragment instanceof FontViewerFragment) {
                    ((FontViewerFragment) currentFragment).showFontSizeDialogPublic();
                }
            });
        }
    }

    /**
     * ★ استعادة حالة الفراغمنتات من Bundle ★
     *
     * يُعيد بناء mFragmentsMap من FragmentManager باستخدام AppScreen.name() كـ Tags.
     */
    private void restoreFragmentsState(Bundle savedInstanceState) {
        // ★ استعادة الشاشة الحالية من AppScreen.name() ★
        String screenName = savedInstanceState.getString(
                KEY_CURRENT_SCREEN, AppScreen.FONT_VIEWER.name());
        try {
            mCurrentScreen = AppScreen.valueOf(screenName);
        } catch (IllegalArgumentException e) {
            mCurrentScreen = AppScreen.FONT_VIEWER;
        }
        // HOME ليس فراغمنت في Map — إعادة التوجيه لـ FONT_VIEWER إن جاء من حالة قديمة
        if (mCurrentScreen == AppScreen.HOME) {
            mCurrentScreen = AppScreen.FONT_VIEWER;
        }

        FragmentManager fm = getSupportFragmentManager();
        // ★ محاولة استعادة كل فراغمنت من FragmentManager بـ Tag = AppScreen.name() ★
        for (AppScreen screen : AppScreen.values()) {
            if (screen == AppScreen.HOME) continue; // HOME ليس في Map الفراغمنتات
            Fragment f = fm.findFragmentByTag(screen.name());
            if (f != null) {
                mFragmentsMap.put(screen, f);
            }
            // إذا لم يُعثر عليه، يُبقى الفراغمنت المُنشأ في initFragmentsList()
        }

        mNavManager.showFragmentFast(mCurrentScreen);

        if (mDrawerAdapter != null) {
            mDrawerAdapter.setSelectedItem(mCurrentScreen);
        }
    }

    /**
     * ★ إضافة جميع الفراغمنتات إلى FragmentManager ★
     *
     * يُستخدم AppScreen.name() كـ Tag لكل فراغمنت بدلاً من ثوابت نصية مشفرة.
     * FONT_VIEWER هي الشاشة الافتراضية — تُعرَض أولاً والبقية مخفية.
     *
     * ★ EnumMap يحفظ الترتيب بترتيب AppScreen.values() ★
     * لكن الترتيب في FragmentManager لا يؤثر على الوظيفة —
     * ما يهم هو الـ Tag لا الموضع.
     */
    private void addAllFragments() {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();

        for (Map.Entry<AppScreen, Fragment> entry : mFragmentsMap.entrySet()) {
            AppScreen screen = entry.getKey();
            Fragment fragment = entry.getValue();
            // ★ AppScreen.name() كـ Tag يضمن استعادة الفراغمنت الصحيح بعد إعادة البناء ★
            transaction.add(R.id.main_content, fragment, screen.name());
            if (screen != mCurrentScreen) {
                transaction.hide(fragment);
            }
        }

        transaction.commitNow();
    }

    /**
     * ★ إعداد درج التنقل ★
     *
     * الخطوة الرابعة من خطة الإصلاح: DrawerListAdapter يستقبل الآن List<AppScreen>
     * بدلاً من List<Fragment>. هذا يفصل Adapter الدرج تماماً عن بنية الفراغمنتات.
     *
     * قائمة الشاشات تحتوي على null للفواصل المرئية في الدرج.
     *
     * ★ AppScreen.HOME مدرج في الدرج لكنه ليس في Map الفراغمنتات ★
     * عند النقر عليه يُفتح HomeActivity مباشرةً بدلاً من التنقل بين الفراغمنتات.
     */
    private void setupDrawer() {
        mDrawerListView.setLayoutManager(new LinearLayoutManager(this));

        // ★ قائمة شاشات الدرج: null تُمثل فاصلاً بصرياً ★
        List<AppScreen> drawerScreenList = Arrays.asList(
                AppScreen.HOME,
                AppScreen.FONT_VIEWER,
                null,                   // فاصل بين عارض الخطوط وقوائمها
                AppScreen.LOCAL_FONTS,
                AppScreen.SYSTEM_FONTS,
                AppScreen.FAVORITES,
                null,                   // فاصل بين قوائم الخطوط وسلة المحذوفات
                AppScreen.TRASH
        );

        mDrawerAdapter = new DrawerListAdapter(
                this,
                drawerScreenList,
                screen -> {
                    // ★ الخطوة الرابعة: HOME → HomeActivity، غيره → NavManager ★
                    if (screen == AppScreen.HOME) {
                        Intent intent = new Intent(MainActivity.this, HomeActivity.class);
                        startActivity(intent);
                        return false; // لا تغيير في التحديد البصري
                    }

                    setDrawerOpen(false, true);

                    if (screen != mCurrentScreen) {
                        // ★ التنقل عبر الدرج مُفوَّض بالكامل لـ NavManager ★
                        mNavManager.navigateFromDrawer(screen);
                        return true;
                    }
                    return false;
                });
        mDrawerListView.setAdapter(mDrawerAdapter);

        mDrawerAdapter.setSelectedItem(mCurrentScreen);

        // ★ تهيئة NavManager بالـ DrawerLayout لاستخراج الـ inner DrawerLayout ★
        // يستخرج NavManager.setup() الـ inner DrawerLayout مرة واحدة ويحفظه داخلياً
        // لاستدعاء isDrawerOpen() مباشرةً عند كل ضغطة رجوع.
        if (mDrawerLayout != null) {
            mNavManager.setup(mDrawerLayout);
        }

        final int touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        mDrawerListView.setOnTouchListener(new View.OnTouchListener() {
            private float startX, startY;

            @Override
            public boolean onTouch(View v, MotionEvent ev) {
                int action = ev.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    startX = ev.getX();
                    startY = ev.getY();
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                } else if (action == MotionEvent.ACTION_MOVE) {
                    float dx = Math.abs(ev.getX() - startX);
                    float dy = Math.abs(ev.getY() - startY);
                    if (dx > touchSlop && dx > dy) {
                        v.getParent().requestDisallowInterceptTouchEvent(false);
                    } else {
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                }
                return false;
            }
        });
    }

    /**
     * ★ الخطوة الثانية: updateDrawerTitle يستقبل AppScreen بدلاً من int ★
     *
     * المقارنة المباشرة بـ AppScreen أوضح وأكثر أماناً من:
     *   if (fragmentIndex == 2) ... else if (fragmentIndex == 3) ...
     *
     * لا استخدام لـ instanceof لأن AppScreen.TRASH أصرح من TrashFragment instanceof.
     *
     * ★ الإصلاح (خطة الإصلاح الشاملة — الخطوة الثانية):
     *   شاشة FONT_VIEWER مُستثناة من حظر تحديث العنوان أثناء البحث.
     *   عند الانتقال لعارض الخطوط من قائمة الخطوط أثناء فتح البحث،
     *   يجب أن يظهر عنوان الخط المحمّل فوراً بدلاً من بقاء "بحث" مجمّداً. ★
     */
    @Override
    public void updateDrawerTitle(AppScreen screen) {
        if (mDrawerLayout == null) {
            return;
        }

        // ★ الإصلاح: حظر تحديث العنوان أثناء البحث، باستثناء شاشة عارض الخطوط ★
        // FONT_VIEWER تُستثنى لأن الانتقال إليها يستوجب عرض اسم الخط بغض النظر عن حالة البحث
        if (mSearchCoordinator.isSearchExpanded() && screen != AppScreen.FONT_VIEWER) {
            return;
        }

        String title;
        String subtitle;

        switch (screen) {
            case HOME:
                // HOME لا يُفتح كفراغمنت ولكن نتعامل معه دفاعياً
                title    = getString(R.string.app_name);
                subtitle = getString(R.string.app_subtitle);
                break;

            case FONT_VIEWER:
                if (currentFontRealName != null && !currentFontRealName.isEmpty()) {
                    title = currentFontRealName;
                } else {
                    title = getString(R.string.drawer_font_viewer);
                }
                if (currentFontFileName != null && !currentFontFileName.isEmpty()) {
                    subtitle = FileUtils.removeExtension(currentFontFileName);
                } else {
                    subtitle = getString(R.string.font_viewer_select_description);
                }
                break;

            case LOCAL_FONTS:
                title    = getString(R.string.drawer_local_fonts);
                // ★ يستخدم العداد المخصص للمجلد المحلي فقط ★
                subtitle = getFontsCountString(mLocalFontsCount);
                break;

            case SYSTEM_FONTS:
                title    = getString(R.string.drawer_system_fonts);
                // ★ يستخدم العداد المخصص لخطوط النظام فقط ★
                subtitle = getFontsCountString(mSystemFontsCount);
                break;

            case FAVORITES:
                // ★ عنوان قائمة المفضلة بعداد مستقل لا يتداخل مع القوائم الأخرى ★
                title    = getString(R.string.drawer_favorites);
                subtitle = getFontsCountString(mFavoriteFontsCount);
                break;

            case TRASH:
                // ★ عنوان سلة المحذوفات بعداد مستقل لا يتداخل مع القوائم الأخرى ★
                title    = getString(R.string.drawer_trash);
                subtitle = getFontsCountString(mTrashFontsCount);
                break;

            default:
                title    = getString(R.string.app_name);
                subtitle = getString(R.string.app_subtitle);
        }

        mDrawerLayout.setTitle(title);
        mDrawerLayout.setExpandedSubtitle(subtitle);
    }

    /**
     * ★ الإصلاح (المشكلة 5): استبدال المنطق البدائي بـ getQuantityString() ★
     *
     * المنطق القديم كان يستخدم if/else ثلاثية لا تُميّز إلا بين:
     *   - صفر (font_list_count_none)
     *   - واحد (font_list_count_single)
     *   - أكثر من واحد (font_list_count_multiple)
     *
     * المشكلة: لا يدعم قواعد الجمع العربي (مثنى / جمع قلة / جمع كثرة).
     * النتيجة: "2 خط" بدلاً من "خطين"، و"5 خط" بدلاً من "5 خطوط".
     *
     * الحل: getQuantityString() يقرأ الـ <plurals> المُعرَّفة في:
     *   - values/strings.xml      → قواعد الإنجليزية (one / other)
     *   - values-ar/strings.xml   → قواعد العربية    (one / two / few / many / other)
     * ويختار الصيغة الصحيحة تلقائياً بناءً على لغة الجهاز والعدد المُمرَّر.
     *
     * ★ إصلاح إضافي: معالجة العدد صفر في اللغة الإنجليزية ★
     * Android لا يعترف بـ quantity="zero" في اللغة الإنجليزية، فيسقط العدد صفر
     * في خانة "other" ويعرض "0 Fonts" بدلاً من "No Fonts".
     * الحل: عند العدد صفر نستخدم no_fonts_found مباشرةً لكلا اللغتين.
     *
     * @param count عدد الخطوط في القائمة الحالية
     * @return نص العدد المُنسَّق وفق قواعد لغة الجهاز
     */
    private String getFontsCountString(int count) {
        // ★ استخدام no_fonts_found عند العدد صفر لتجنب "0 Fonts" في الإنجليزية ★
        if (count == 0) {
            return getString(R.string.no_fonts_found);
        }
        // ★ getQuantityString يُطبّق قواعد الجمع تلقائياً ★
        return getResources().getQuantityString(R.plurals.font_count_subtitle, count, count);
    }

    /**
     * ★ المرحلة الأولى: updateMenuVisibility أصبحت لا عملية (no-op) ★
     *
     * كانت هذه الدالة تُظهر وتُخفي أيقونات الـ AppBar بناءً على الشاشة المفتوحة.
     * الآن: كل فراجمنت يُدير أيقوناته بشكل مستقل عبر:
     *   - setHasOptionsMenu(true) في onCreate()
     *   - onCreateOptionsMenu() لنفخ الأيقونات عند عرض الفراجمنت
     *   - setMenuVisibility(!hidden) في onHiddenChanged() للتبديل التلقائي
     *
     * تظل الدالة هنا لإرضاء واجهة NavManager.Host — NavManager لا يزال يستدعيها
     * لكنها لا تفعل شيئاً (harmless no-op).
     */
    @Override
    public void updateMenuVisibility(AppScreen screen) {
        // ★ المرحلة الأولى: أيقونات الـ AppBar تُدار الآن في كل Fragment بشكل مستقل ★
        // هذه الدالة أصبحت لا عملية (no-op) — تظل موجودة لإرضاء واجهة NavManager.Host
    }

    /**
     * ★ الخطوة الثانية: updateFabVisibility يستقبل AppScreen بدلاً من int ★
     *
     * المقارنة المباشرة بـ AppScreen.FONT_VIEWER تحل محل:
     *   if (position == 1) fabFontSize.show()
     */
    @Override
    public void updateFabVisibility(AppScreen screen) {
        if (fabFontSize == null) return;

        if (screen == AppScreen.FONT_VIEWER) {
            if (fabFontSize.getVisibility() != android.view.View.VISIBLE) {
                fabFontSize.setVisibility(android.view.View.VISIBLE);
                fabFontSize.setScaleX(0f);
                fabFontSize.setScaleY(0f);
                fabFontSize.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
            }
        } else {
            if (fabFontSize.getVisibility() == android.view.View.VISIBLE) {
                fabFontSize.animate().scaleX(0f).scaleY(0f).setDuration(150).withEndAction(() -> {
                    fabFontSize.setVisibility(android.view.View.GONE);
                }).start();
            }
        }
    }

    public void updateFabFontSizeText(float size) {
        if (fabFontSize != null) {
            fabFontSize.setText(String.valueOf(Math.round(size)));
        }
    }

    /**
     * ★ الإصلاح الجوهري للمشكلتين 1 و 3 — الانتقال إلى AppScreen ★
     *
     * بدلاً من استقبال رقم fromFragmentIndex لتمييز الفراجمنتات:
     *   القديم: if (fromFragmentIndex == 2) ... else if (fromFragmentIndex == 5) ...
     *   الجديد: if (screen == AppScreen.LOCAL_FONTS) ... else if (screen == AppScreen.TRASH) ...
     *
     * كل فراجمنت يُرسل شاشته مع العدد، فيُخزَّن في عداده المستقل.
     * تحديث الواجهة يحدث فقط إذا كان الطلب قادماً من الشاشة الظاهرة حالياً،
     * مما يمنع أي فراجمنت مخفي من الكتابة فوق العنوان الفرعي الصحيح.
     *
     * ★ التحديث الخاص بسلة المحذوفات ★
     * عند تغيُّر عدد عناصر السلة بينما المستخدم في شاشتها، يُحدَّث AppBar عبر
     * invalidateOptionsMenu() لإجبار TrashFragment على تحديث ظهور action_empty_trash.
     *
     * @param screen نوع الشاشة المُرسِلة كـ AppScreen enum
     * @param count  العدد الجديد للخطوط
     */
    public void updateFontsCount(AppScreen screen, int count) {
        // تخزين العدد لكل شاشة بشكل مستقل بغض النظر عن الشاشة الظاهرة
        if (screen == AppScreen.LOCAL_FONTS) {
            mLocalFontsCount = count;
        } else if (screen == AppScreen.SYSTEM_FONTS) {
            mSystemFontsCount = count;
        } else if (screen == AppScreen.FAVORITES) {
            // ★ تخزين عدد المفضلة في عداده المستقل ★
            mFavoriteFontsCount = count;
        } else if (screen == AppScreen.TRASH) {
            // ★ تخزين عدد عناصر سلة المحذوفات في عداده المستقل ★
            mTrashFontsCount = count;
            // ★ المرحلة الأولى: إشعار TrashFragment بتحديث أيقوناته (action_empty_trash) ★
            // invalidateOptionsMenu() يُجبر TrashFragment على إعادة بناء قائمته،
            // فيتحقق من عدد العناصر ويُظهر أو يُخفي action_empty_trash وفقاً له.
            if (mCurrentScreen == AppScreen.TRASH) {
                invalidateOptionsMenu();
            }
        }

        // ★ تحديث الواجهة فقط إذا كان المُرسِل هو الشاشة الظاهرة حالياً ★
        if (screen == mCurrentScreen && !mSearchCoordinator.isSearchExpanded()) {
            runOnUiThread(() -> {
                if (mDrawerLayout != null) {
                    mDrawerLayout.setExpandedSubtitle(getFontsCountString(count));
                }
            });
        }
    }

    /**
     * ★ دالة توافق مرحلية للفراجمنتات التي لم تُحدَّث بعد ★
     *
     * تُحوِّل الفهرس الرقمي إلى AppScreen وتُحيل للدالة الجديدة.
     * يُتيح هذا لـ SystemFontListFragment وFavoriteFontListFragment
     * الاستمرار في العمل ريثما تُحدَّث لاستخدام AppScreen مباشرةً.
     *
     * القيم المقبولة:
     *   2 = LOCAL_FONTS
     *   3 = SYSTEM_FONTS
     *   4 = FAVORITES
     *   5 = TRASH
     *
     * @deprecated استخدم updateFontsCount(AppScreen, int) مباشرةً بدلاً من ذلك
     */
    @Deprecated
    public void updateFontsCount(int fromFragmentIndex, int count) {
        AppScreen screen;
        switch (fromFragmentIndex) {
            case 2:  screen = AppScreen.LOCAL_FONTS;  break;
            case 3:  screen = AppScreen.SYSTEM_FONTS; break;
            case 4:  screen = AppScreen.FAVORITES;    break;
            case 5:  screen = AppScreen.TRASH;        break;
            default: return; // فهرس غير معروف — تجاهل
        }
        updateFontsCount(screen, count);
    }

    private void showFontInfoDialog(Map<String, String> metadata) {
        Fragment frag = findFragmentByType(FontViewerFragment.class);
        if (!(frag instanceof FontViewerFragment)) {
            return;
        }

        FontViewerFragment fvf = (FontViewerFragment) frag;

        String fileName = fvf.getCurrentFontFileName();
        String path     = fvf.originalFontPath;

        FontInfoDialog dialog = new FontInfoDialog(this, metadata, fileName, path);
        dialog.show();
    }

    private void showLoadingDialog() {
        dismissLoadingDialog();
        try {
            loadingDialog = new ProgressDialog(this);
            loadingDialog.setMessage("Translating...");
            loadingDialog.setCancelable(false);
            loadingDialog.show();
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Failed to show loading dialog", e);
        }
    }

    private void dismissLoadingDialog() {
        if (loadingDialog != null && loadingDialog.isShowing()) {
            try {
                loadingDialog.dismiss();
            } catch (Exception e) {
                android.util.Log.e("MainActivity", "Failed to dismiss loading dialog", e);
            }
            loadingDialog = null;
        }
    }

    private void showNoFontDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.font_viewer_select_font))
                .setMessage(getString(R.string.font_viewer_no_font_selected))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  واجهات Listener — مُفوَّضة لـ NavManager
    // ════════════════════════════════════════════════════════════════════════

    /**
     * ★ تنفيذ موحّد لجميع واجهات OnFontSelectedListener ★
     *
     * هذا التنفيذ الواحد يخدم ثلاث واجهات في آنٍ واحد:
     *   - LocalFontListFragment.OnFontSelectedListener
     *   - SystemFontListFragment.OnFontSelectedListener
     *   - FavoriteFontListFragment.OnFontSelectedListener  ← الإضافة الجديدة
     *
     * الثلاثة يشتركون في نفس توقيع onFontSelected، لذا يكفي تنفيذ واحد.
     * weightWidthLabel يُمرَّر مباشرةً لـ NavManager دون إعادة استخراجه.
     */
    @Override
    public void onFontSelected(String fontPath, String realName, String fileName,
                               int ttcIndex, String weightWidthLabel) {
        mNavManager.handleFontSelected(fontPath, realName, fileName, ttcIndex, weightWidthLabel);
    }

    @Override
    public void onFontChanged(String fontRealName, String fontFileName) {
        this.currentFontRealName = fontRealName;
        this.currentFontFileName = fontFileName;

        // ★ الإصلاح: مقارنة AppScreen.FONT_VIEWER بدلاً من int ★
        if (mCurrentScreen == AppScreen.FONT_VIEWER) {
            runOnUiThread(() -> updateDrawerTitle(mCurrentScreen));
        }
    }

    @Override
    public void onFontCleared() {
        this.currentFontRealName = null;
        this.currentFontFileName = null;

        // ★ الإصلاح: مقارنة AppScreen.FONT_VIEWER بدلاً من int ★
        if (mCurrentScreen == AppScreen.FONT_VIEWER) {
            updateDrawerTitle(mCurrentScreen);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  تنفيذ NavManager.Host
    // ════════════════════════════════════════════════════════════════════════

    /**
     * ★ تُسمَّى getAppFragmentManager() بدلاً من getFragmentManager() ★
     * السبب: getFragmentManager() موجودة في Activity وتُعيد android.app.FragmentManager (مُهمَلة).
     * تجاوزها بنوع إعادة androidx.fragment.app.FragmentManager يُسبب خطأ تجميع
     * لأن النوعين غير متوافقَين من منظور Java. تغيير الاسم يتجنب التعارض كلياً.
     */
    @Override
    public FragmentManager getAppFragmentManager() {
        return getSupportFragmentManager();
    }

    /**
     * ★ الخطوة الأولى: Fragment getFragment(AppScreen) بدلاً من List<Fragment> getFragments() ★
     *
     * NavManager يطلب فراغمنت بشاشته لا بفهرسه، مما يقطع الاعتماد على الترتيب نهائياً.
     * مُعاد null لـ HOME لأنه ليس في Map الفراغمنتات.
     */
    @Override
    public Fragment getFragment(AppScreen screen) {
        return mFragmentsMap.get(screen);
    }

    /**
     * ★ الخطوة الثانية: AppScreen getCurrentScreen() بدلاً من int getCurrentIndex() ★
     *
     * تُعيد مباشرةً المتغير mCurrentScreen المحفوظ.
     * هذا أبسط وأكثر موثوقية من استخدام instanceof لاستنتاج الشاشة.
     */
    @Override
    public AppScreen getCurrentScreen() {
        return mCurrentScreen;
    }

    /**
     * ★ الخطوة الثانية: setCurrentScreen(AppScreen) بدلاً من setCurrentIndex(int) ★
     */
    @Override
    public void setCurrentScreen(AppScreen screen) {
        mCurrentScreen = screen;
    }

    @Override
    public DrawerLayout getDrawerLayout() {
        return mDrawerLayout;
    }

    @Override
    public DrawerListAdapter getDrawerAdapter() {
        return mDrawerAdapter;
    }

    @Override
    public SearchCoordinator getSearchCoordinator() {
        return mSearchCoordinator;
    }

    @Override
    public String getFontRealName() {
        return currentFontRealName;
    }

    @Override
    public String getFontFileName() {
        return currentFontFileName;
    }

    @Override
    public void setFontRealName(String name) {
        currentFontRealName = name;
    }

    @Override
    public void setFontFileName(String name) {
        currentFontFileName = name;
    }

    /**
     * يُنفّذ الخروج الفعلي من التطبيق.
     * يُفرّق بين Android O (API 26) مع isTaskRoot وبقية الإصدارات.
     */
    @Override
    public void performExit() {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O && isTaskRoot()) {
            finishAfterTransition();
        } else {
            super.onBackPressed();
        }
    }

    /** يعرض رسالة Toast تطلب من المستخدم الضغط مرة أخرى للخروج */
    @Override
    public void showPressAgainToExitToast() {
        Toast.makeText(this,
                getString(R.string.exit_on_double_back),
                Toast.LENGTH_SHORT).show();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  دورة حياة النشاط
    // ════════════════════════════════════════════════════════════════════════

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // ★ حفظ AppScreen.name() بدلاً من int ★
        outState.putString(KEY_CURRENT_SCREEN, mCurrentScreen.name());
        // ★ حفظ جميع العدادات بشكل مستقل لاستعادتها بدقة عند إعادة البناء ★
        outState.putInt(KEY_LOCAL_FONTS_COUNT,    mLocalFontsCount);
        outState.putInt(KEY_SYSTEM_FONTS_COUNT,   mSystemFontsCount);
        outState.putInt(KEY_FAVORITE_FONTS_COUNT, mFavoriteFontsCount);
        outState.putInt(KEY_TRASH_FONTS_COUNT,    mTrashFontsCount);
        // ★ حفظ مكدس التنقل — مُفوَّض لـ NavManager ★
        mNavManager.saveState(outState);
        mSearchCoordinator.saveState(outState);
    }

    @Override
    protected void onDestroy() {
        dismissLoadingDialog();
        mSearchCoordinator.cleanup();
        super.onDestroy();
    }

    /**
     * ★ منطق زر الرجوع — مُفوَّض بالكامل لـ NavManager ★
     * التفاصيل الكاملة لأولويات التنفيذ موثَّقة في NavManager.handleBackPressed().
     */
    @Override
    public void onBackPressed() {
        mNavManager.handleBackPressed();
    }

    // ════════════════════════════════════════════════════════════════════════
    //  دوال مساعدة عامة
    // ════════════════════════════════════════════════════════════════════════

    /**
     * ★ دالة توافق للفراجمنتات التي تُمرّر موضعاً رقمياً قديماً ★
     *
     * AppScreen.ordinal() يطابق الأرقام القديمة:
     *   HOME=0, FONT_VIEWER=1, LOCAL_FONTS=2, SYSTEM_FONTS=3, FAVORITES=4, TRASH=5
     *
     * @param position الموضع الرقمي القديم (يُحوَّل داخلياً إلى AppScreen)
     */
    public void updateDrawerSelection(int position) {
        if (position < 0 || position >= AppScreen.values().length) return;
        AppScreen screen = AppScreen.values()[position];
        mCurrentScreen = screen;
        if (mDrawerAdapter != null) {
            mDrawerAdapter.setSelectedItem(screen);
        }
        updateDrawerTitle(screen);
    }
                                                          }
