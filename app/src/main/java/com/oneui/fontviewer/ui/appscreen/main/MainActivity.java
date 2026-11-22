package com.oneui.fontviewer.ui.appscreen.main;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.core.splashscreen.SplashScreen;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.oneui.fontviewer.ui.core.widget.layout.OneUiDrawerLayout;

import com.oneui.fontviewer.ui.appscreen.fontviewer.FontViewerActivity;
import com.oneui.fontviewer.data.fontviewer.FontPrefetcher;
import com.oneui.fontviewer.ui.core.utils.AxisLabelProvider;
import com.oneui.fontviewer.ui.appscreen.main.fragment.localfont.LocalFontListFragment;
import com.oneui.fontviewer.ui.appscreen.main.fragment.systemfont.SystemFontListFragment;
import com.oneui.fontviewer.ui.appscreen.main.fragment.favorite.FavoriteFontListFragment;
import com.oneui.fontviewer.ui.appscreen.main.fragment.trash.TrashFragment;
import com.oneui.fontviewer.ui.appscreen.main.navigation.drawer.DrawerListAdapter;
import com.oneui.fontviewer.R;
import com.oneui.fontviewer.ui.appscreen.main.search.SearchCoordinator;
import com.oneui.fontviewer.ui.appscreen.settings.SettingsActivity;
import com.oneui.fontviewer.ui.appscreen.home.HomeActivity;
import com.oneui.fontviewer.service.BatchOperationState;
import com.oneui.fontviewer.service.OperationNotifications;
import com.oneui.fontviewer.ui.appscreen.main.navigation.drawer.DrawerListItem;
import com.oneui.fontviewer.ui.appscreen.main.navigation.NavManager;
import com.oneui.fontviewer.ui.appscreen.main.navigation.UpdateBadgeController;
import com.oneui.fontviewer.data.update.UpdateRepository;
import com.oneui.fontviewer.ui.core.base.BaseActivity;

public class MainActivity extends BaseActivity
    implements LocalFontListFragment.OnFontSelectedListener,
    SystemFontListFragment.OnFontSelectedListener,
    FavoriteFontListFragment.OnFontSelectedListener,
    NavManager.Host {

    private boolean isUIReady = false;
    private boolean mIsMinSplashTimeElapsed = false;
    private boolean mIsInitialDataReady = false;
    private long mSplashStartTime = 0L;
    private OneUiDrawerLayout mDrawerLayout;
    private RecyclerView mDrawerListView;
    private DrawerListAdapter mDrawerAdapter;

    private final Map<DrawerListItem, Fragment> mFragmentsMap = new EnumMap<>(DrawerListItem.class);

    private DrawerListItem mCurrentDrawerListItem = DrawerListItem.LOCAL_FONTS;

    private static final String KEY_CURRENT_DRAWER_LIST_ITEM = "current_screen";
    private static final String KEY_LOCAL_FONTS_COUNT        = "local_fonts_count";
    private static final String KEY_SYSTEM_FONTS_COUNT       = "system_fonts_count";
    private static final String KEY_FAVORITE_FONTS_COUNT     = "favorite_fonts_count";
    private static final String KEY_TRASH_FONTS_COUNT        = "trash_fonts_count";

    private static final int PERM_REQUEST_POST_NOTIFICATIONS = 1001;

    public static final String EXTRA_TARGET_FRAGMENT = OperationNotifications.EXTRA_TARGET_FRAGMENT;

    private int mLocalFontsCount    = 0;
    private int mSystemFontsCount   = 0;
    private int mFavoriteFontsCount = 0;
    private int mTrashFontsCount    = 0;

    private NavManager mNavManager;

    private SearchCoordinator mSearchCoordinator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
        mSplashStartTime = System.currentTimeMillis();

        super.onCreate(savedInstanceState);

        splashScreen.setKeepOnScreenCondition(() -> !isUIReady);

        if (android.os.Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, android.R.anim.fade_in, android.R.anim.fade_out);
        }

        setContentView(R.layout.activity_main);

        // التحقق مما إذا كان هذا فتحاً جديداً للتطبيق (Cold Start) أم إعادة بناء بسبب تغيير اللغة أو الثيم (Hot Start).
        // عند إعادة البناء، البيانات والواجهات محفوظة مسبقًا، لذا نتخطى الانتظار الاصطناعي فورًا
        // لتفادي التأخير الملحوظ عند الضغط على زر الرجوع بعد تغيير اللغة.
        if (savedInstanceState != null) {
            mIsMinSplashTimeElapsed = true;
        }

        mNavManager = new NavManager(this);

        initViews();
        initFragmentsList();

        setupSearchCoordinator();

        setupDrawerButton();

        setupUpdateBadge();

        if (savedInstanceState != null) {
            mLocalFontsCount    = savedInstanceState.getInt(KEY_LOCAL_FONTS_COUNT, 0);
            mSystemFontsCount   = savedInstanceState.getInt(KEY_SYSTEM_FONTS_COUNT, 0);
            mFavoriteFontsCount = savedInstanceState.getInt(KEY_FAVORITE_FONTS_COUNT, 0);
            mTrashFontsCount    = savedInstanceState.getInt(KEY_TRASH_FONTS_COUNT, 0);
            restoreFragmentsState(savedInstanceState);
            mSearchCoordinator.restoreState(savedInstanceState);
        } else {
            addAllFragments();
            mCurrentDrawerListItem = DrawerListItem.LOCAL_FONTS;
            mNavManager.showFragmentFast(DrawerListItem.LOCAL_FONTS);
            warmUpOtherFragments();
        }

        setupDrawer();
        updateDrawerTitle(mCurrentDrawerListItem);

        // لا نعالج توجيه الإشعار (الشاشة المستهدفة) إلا عند فتح جديد للنشاط (savedInstanceState == null).
        // عند تفعيل "عدم الاحتفاظ بالأنشطة" يُهدم النشاط ويُعاد بناؤه، ويعيد getIntent() عندها الـ Intent الأصلي الذي
        // أُنشئ به السجل في النظام (وهو Intent الإشعار الذي ضُغط عليه سابقاً ويحمل EXTRA_TARGET_FRAGMENT).
        // أما حذف الـ extras في handleIntent فيمسّ نسخة العملية فقط ولا يمسّ نسخة النظام. لذلك كان كل إعادة بناء
        // لاحقة تعيد التنقل إلى شاشة الإشعار القديمة بدلاً من آخر شاشة دخل إليها المستخدم.
        // أما الإشعار الذي يصل والنشاط حيّ أو مُعاد بناؤه فيصل عبر onNewIntent وتُعالجه بشكل صحيح.
        handleIntent(getIntent(), savedInstanceState == null);

        requestNotificationPermissionIfNeeded();

        // التحقق من التحديث عند فتح التطبيق فقط (وليس عند إعادة البناء بسبب تغيير اللغة أو الثيم)
        if (savedInstanceState == null) {
            UpdateRepository.getInstance(this).checkForUpdateOnLaunch();
        }

        long elapsedTime = System.currentTimeMillis() - mSplashStartTime;
        long remainingDelay = Math.max(0L, 4500L - elapsedTime);

        Handler splashHandler = new Handler(getMainLooper());

        splashHandler.postDelayed(() -> {
            mIsMinSplashTimeElapsed = true;
            checkSplashReadyState();
        }, remainingDelay);

        // شبكة أمان: لا تُبقي الشاشة أكثر من هذه المدة حتى لو لم تصل إشارة الجاهزية
        splashHandler.postDelayed(() -> {
            mIsInitialDataReady = true;
            checkSplashReadyState();
        }, 60000L);
    }

    private void checkSplashReadyState() {
        if (mIsMinSplashTimeElapsed && mIsInitialDataReady) {
            isUIReady = true;
        }
    }

    public void setAppReady() {
        mIsInitialDataReady = true;
        checkSplashReadyState();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent, true);
    }

    /**
     * @param shouldHandleNavigation هل يُسمح بمعالجة توجيه الإشعار (الشاشة المستهدفة وإعادة فتح ديالوج التقدم).
     *                               يكون false عند إعادة بناء النشاط (savedInstanceState != null) كي لا يُعاد
     *                               تطبيق Intent قديم على شاشة اختارها المستخدم بعده.
     */
    private void handleIntent(Intent intent, boolean shouldHandleNavigation) {
        mSearchCoordinator.handleSearchIntent(intent);

        if (intent != null && shouldHandleNavigation) {
            

            boolean fromNotif = intent.getBooleanExtra(OperationNotifications.EXTRA_FROM_NOTIFICATION, false);
            if (fromNotif) {
                BatchOperationState.setShouldReopenDialog(true);
                intent.removeExtra(OperationNotifications.EXTRA_FROM_NOTIFICATION);
            }

            String targetDrawerListItemName = intent.getStringExtra(EXTRA_TARGET_FRAGMENT);
            if (targetDrawerListItemName != null) {
                intent.removeExtra(EXTRA_TARGET_FRAGMENT);
                try {
                    DrawerListItem targetDrawerListItem = DrawerListItem.valueOf(targetDrawerListItemName);
                    if (targetDrawerListItem != DrawerListItem.HOME) {
                        if (mNavManager != null && mCurrentDrawerListItem != targetDrawerListItem) {
                            mNavManager.navigateFromDrawer(targetDrawerListItem);
                        } else if (fromNotif) {
                            Fragment frag = mFragmentsMap.get(targetDrawerListItem);
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
                    android.util.Log.w("MainActivity", "Unknown DrawerListItem name: " + targetDrawerListItemName);
                }
            }
        }
    }

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
    }


    private void initFragmentsList() {
        if (mFragmentsMap.isEmpty()) {
            mFragmentsMap.put(DrawerListItem.LOCAL_FONTS, new LocalFontListFragment());
            mFragmentsMap.put(DrawerListItem.SYSTEM_FONTS, new SystemFontListFragment());
            mFragmentsMap.put(DrawerListItem.FAVORITES, new FavoriteFontListFragment());
            mFragmentsMap.put(DrawerListItem.TRASH, new TrashFragment());
        }
    }

    private void setupSearchCoordinator() {
        mSearchCoordinator = new SearchCoordinator(this, mDrawerLayout);

        mSearchCoordinator.setProviders(
                () -> mCurrentDrawerListItem,
                drawerListItem -> mFragmentsMap.get(drawerListItem)
        );

        mSearchCoordinator.setSearchStateListener(new SearchCoordinator.SearchStateListener() {
            @Override
            public void onSearchExpanded() {
                notifyFragmentsSearchState(true);
            }

            @Override
            public void onSearchCollapsed() {
                updateDrawerTitle(mCurrentDrawerListItem);
                notifyFragmentsSearchState(false);
            }

            @Override
            public void onSearchQueryChanged(String query) {
            }
        });
    }

    private void notifyFragmentsSearchState(boolean isExpanded) {
        Fragment localFrag = mFragmentsMap.get(DrawerListItem.LOCAL_FONTS);
        if (localFrag instanceof LocalFontListFragment) {
            ((LocalFontListFragment) localFrag).onSearchStateChanged(isExpanded);
        }

        Fragment sysFrag = mFragmentsMap.get(DrawerListItem.SYSTEM_FONTS);
        if (sysFrag instanceof SystemFontListFragment) {
            ((SystemFontListFragment) sysFrag).onSearchStateChanged(isExpanded);
        }

        Fragment favFrag = mFragmentsMap.get(DrawerListItem.FAVORITES);
        if (favFrag instanceof FavoriteFontListFragment) {
            ((FavoriteFontListFragment) favFrag).onSearchStateChanged(isExpanded);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }


    public void setLocalFolderSelected(boolean selected) {
    }

    private void setupDrawerButton() {
        if (mDrawerLayout != null) {
            mDrawerLayout.setDrawerButtonIcon(getDrawable(dev.oneuiproject.oneui.R.drawable.ic_oui_settings_outline));
            mDrawerLayout.setDrawerButtonTooltip(getText(R.string.drawer_settings));
            mDrawerLayout.setDrawerButtonOnClickListener(v -> openSettingsActivity());
        }
    }

    /**
     * المؤشر البرتقالي (N / ج) على أيقونة الدرج وأيقونة الإعدادات يظهر عند توفر تحديث ويختفي بزواله.
     */
    private void setupUpdateBadge() {
        if (mDrawerLayout != null) {
            new UpdateBadgeController(mDrawerLayout)
                    .bind(this, UpdateRepository.getInstance(this).getAvailableUpdate());
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

    

    private void restoreFragmentsState(Bundle savedInstanceState) {
        String drawerListItemName = savedInstanceState.getString(
                KEY_CURRENT_DRAWER_LIST_ITEM, DrawerListItem.FONT_VIEWER.name());
        try {
            mCurrentDrawerListItem = DrawerListItem.valueOf(drawerListItemName);
        } catch (IllegalArgumentException e) {
            mCurrentDrawerListItem = DrawerListItem.FONT_VIEWER;
        }
        if (mCurrentDrawerListItem == DrawerListItem.HOME || mCurrentDrawerListItem == DrawerListItem.FONT_VIEWER) {
            mCurrentDrawerListItem = DrawerListItem.LOCAL_FONTS;
        }

        FragmentManager fm = getSupportFragmentManager();
        for (DrawerListItem drawerListItem : DrawerListItem.values()) {
            if (drawerListItem == DrawerListItem.HOME || drawerListItem == DrawerListItem.FONT_VIEWER) continue;
            Fragment f = fm.findFragmentByTag(drawerListItem.name());
            if (f != null) {
                mFragmentsMap.put(drawerListItem, f);
            }
        }

        mNavManager.showFragmentFast(mCurrentDrawerListItem);

        if (mDrawerAdapter != null) {
            mDrawerAdapter.setSelectedItem(mCurrentDrawerListItem);
        }
    }

    private void addAllFragments() {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();

        for (Map.Entry<DrawerListItem, Fragment> entry : mFragmentsMap.entrySet()) {
            DrawerListItem drawerListItem = entry.getKey();
            Fragment fragment = entry.getValue();
            transaction.add(R.id.main_content, fragment, drawerListItem.name());
            if (drawerListItem != mCurrentDrawerListItem) {
                transaction.hide(fragment);
            }
        }

        transaction.commitNow();
    }

    private void warmUpOtherFragments() {
        Handler warmupHandler = new Handler(getMainLooper());
        warmupHandler.postDelayed(() -> {
            if (isFinishing() || isDestroyed() || getSupportFragmentManager().isStateSaved()) return;
            warmUpFragmentSilently(DrawerListItem.SYSTEM_FONTS);
            warmupHandler.postDelayed(() -> {
                if (isFinishing() || isDestroyed() || getSupportFragmentManager().isStateSaved()) return;
                warmUpFragmentSilently(DrawerListItem.FAVORITES);
                warmupHandler.postDelayed(() -> {
                    if (isFinishing() || isDestroyed() || getSupportFragmentManager().isStateSaved()) return;
                    warmUpFragmentSilently(DrawerListItem.TRASH);
                    warmupHandler.postDelayed(() -> {
                        if (isFinishing() || isDestroyed() || getSupportFragmentManager().isStateSaved()) return;
                        warmUpFragmentSilently(null);
                    }, 120);
                }, 120);
            }, 120);
        }, 120);
    }

    private void warmUpFragmentSilently(DrawerListItem drawerListItemToShow) {
        // إن كانت الشاشة المراد تسخينها هي الشاشة الحالية نفسها فلا نسخّنها. يحدث هذا عند فتح التطبيق من إشعار التقدم
        // بعد حذفه من التطبيقات الأخيرة: handleIntent تنقل التطبيق مباشرة إلى شاشة العملية (المفضلة أو سلة المحذوفات)،
        // ثم يصل دورها في التسخين فتُجعل واجهتها INVISIBLE وهي معروضة. وبما أن show() لا يعيد الظهور لفرجمنت ظاهر أصلاً،
        // تبقى الواجهة غير مرئية (شاشة سوداء) حتى يخفيها التنقل لقائمة أخرى ثم يُظهرها من جديد.
        if (drawerListItemToShow == mCurrentDrawerListItem) {
            drawerListItemToShow = null;
        }

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

        for (DrawerListItem s : DrawerListItem.values()) {
            if (s == mCurrentDrawerListItem) continue;

            Fragment frag = mFragmentsMap.get(s);
            if (frag == null || !frag.isAdded()) continue;

            if (s == drawerListItemToShow) {
                transaction.show(frag);
            } else {
                transaction.hide(frag);
            }
            frag.setMenuVisibility(false);
        }

        transaction.commitNow();

        if (drawerListItemToShow != null) {
            Fragment warmedFrag = mFragmentsMap.get(drawerListItemToShow);
            if (warmedFrag != null && warmedFrag.getView() != null) {
                warmedFrag.getView().setVisibility(View.INVISIBLE);
            }
        }
    }

    private void setupDrawer() {
        mDrawerListView.setLayoutManager(new LinearLayoutManager(this));

        List<DrawerListItem> drawerListItems = Arrays.asList(
                DrawerListItem.HOME,
                DrawerListItem.FONT_VIEWER,
                null,                   
                DrawerListItem.LOCAL_FONTS,
                DrawerListItem.SYSTEM_FONTS,
                DrawerListItem.FAVORITES,
                null,                   
                DrawerListItem.TRASH
        );

        mDrawerAdapter = new DrawerListAdapter(
                this,
                drawerListItems,
                drawerListItem -> {
                    if (drawerListItem == DrawerListItem.HOME) {
                        Intent intent = new Intent(MainActivity.this, HomeActivity.class);
                        startActivity(intent);
                        return false; 
                    }

                    if (drawerListItem == DrawerListItem.FONT_VIEWER) {
                        Intent intent = new Intent(MainActivity.this, FontViewerActivity.class);
                        // إضافة هذه العلامة لإخبار الشاشة بأن الفتح تم من درج التنقل
                        intent.putExtra("is_from_drawer", true); 
                        startActivity(intent);
                        return false;
                    }

                    setDrawerOpen(false, true);

                    if (drawerListItem != mCurrentDrawerListItem) {
                        mNavManager.navigateFromDrawer(drawerListItem);
                        return true;
                    }
                    return false;
                });
        mDrawerListView.setAdapter(mDrawerAdapter);

        mDrawerAdapter.setSelectedItem(mCurrentDrawerListItem);

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

    @Override
    public void updateDrawerTitle(DrawerListItem drawerListItem) {
        if (mDrawerLayout == null) {
            return;
        }

        if (mSearchCoordinator.isSearchExpanded() && drawerListItem != DrawerListItem.FONT_VIEWER) {
            return;
        }

        String title;
        String subtitle;

        switch (drawerListItem) {
            case HOME:
                title    = getString(R.string.app_name);
                subtitle = getString(R.string.app_subtitle);
                break;

            case FONT_VIEWER:
                title    = getString(R.string.drawer_font_viewer);
                subtitle = getString(R.string.font_viewer_select_description);
                break;

            case LOCAL_FONTS:
                title    = getString(R.string.drawer_local_fonts);
                subtitle = getFontsCountString(mLocalFontsCount);
                break;

            case SYSTEM_FONTS:
                title    = getString(R.string.drawer_system_fonts);
                subtitle = getFontsCountString(mSystemFontsCount);
                break;

            case FAVORITES:
                title    = getString(R.string.drawer_favorites);
                subtitle = getFontsCountString(mFavoriteFontsCount);
                break;

            case TRASH:
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

    private String getFontsCountString(int count) {
        if (count == 0) {
            return getString(R.string.no_fonts_subtitle);
        }
        return getResources().getQuantityString(R.plurals.font_count_subtitle, count, count);
    }


    public void updateFontsCount(DrawerListItem drawerListItem, int count) {
        if (drawerListItem == DrawerListItem.LOCAL_FONTS) {
            mLocalFontsCount = count;
        } else if (drawerListItem == DrawerListItem.SYSTEM_FONTS) {
            mSystemFontsCount = count;
        } else if (drawerListItem == DrawerListItem.FAVORITES) {
            mFavoriteFontsCount = count;
        } else if (drawerListItem == DrawerListItem.TRASH) {
            mTrashFontsCount = count;
            if (mCurrentDrawerListItem == DrawerListItem.TRASH) {
                invalidateOptionsMenu();
            }
        }

        if (drawerListItem == mCurrentDrawerListItem && !mSearchCoordinator.isSearchExpanded()) {
            runOnUiThread(() -> {
                if (mDrawerLayout != null) {
                    mDrawerLayout.setExpandedSubtitle(getFontsCountString(count));
                }
            });
        }
    }

    
    
    @Override
    public void onFontSelected(String fontPath, String realName, String fileName,
                               int ttcIndex, String weightWidthLabel) {
        boolean isSystemFont = mCurrentDrawerListItem == DrawerListItem.SYSTEM_FONTS;

        if (mSearchCoordinator != null) {
            mSearchCoordinator.clearSearchFocus();
        }

        // ★ تجهيز الخط وتحليل محاوره في خيط خلفي قبل فتح الشاشة، ليكون جاهزاً عند رسم أول إطار ولا يحجب الخيط الرئيسي ★
        FontPrefetcher.prefetch(AxisLabelProvider.of(this), AxisLabelProvider.localeTagsOf(this), fontPath, ttcIndex, isSystemFont);

        Intent intent = new Intent(this, FontViewerActivity.class);
        intent.putExtra(FontViewerActivity.EXTRA_FONT_PATH, fontPath);
        intent.putExtra(FontViewerActivity.EXTRA_FONT_REAL_NAME, realName);
        intent.putExtra(FontViewerActivity.EXTRA_FONT_FILE_NAME, fileName);
        intent.putExtra(FontViewerActivity.EXTRA_TTC_INDEX, ttcIndex);
        intent.putExtra(FontViewerActivity.EXTRA_IS_SYSTEM_FONT, isSystemFont);
        intent.putExtra(FontViewerActivity.EXTRA_WEIGHT_WIDTH_LABEL, weightWidthLabel);
        startActivity(intent);
    }


    @Override
    public FragmentManager getAppFragmentManager() {
        return getSupportFragmentManager();
    }

    @Override
    public Fragment getFragment(DrawerListItem drawerListItem) {
        return mFragmentsMap.get(drawerListItem);
    }

    @Override
    public DrawerListItem getCurrentDrawerListItem() {
        return mCurrentDrawerListItem;
    }

    @Override
    public void setCurrentDrawerListItem(DrawerListItem drawerListItem) {
        mCurrentDrawerListItem = drawerListItem;
    }

    @Override
    public OneUiDrawerLayout getDrawerLayout() {
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
    public void performExit() {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O && isTaskRoot()) {
            finishAfterTransition();
        } else {
            super.onBackPressed();
        }
    }


    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_CURRENT_DRAWER_LIST_ITEM, mCurrentDrawerListItem.name());
        outState.putInt(KEY_LOCAL_FONTS_COUNT,    mLocalFontsCount);
        outState.putInt(KEY_SYSTEM_FONTS_COUNT,   mSystemFontsCount);
        outState.putInt(KEY_FAVORITE_FONTS_COUNT, mFavoriteFontsCount);
        outState.putInt(KEY_TRASH_FONTS_COUNT,    mTrashFontsCount);
        mSearchCoordinator.saveState(outState);
    }

    @Override
    protected void onDestroy() {
        mSearchCoordinator.cleanup();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        mNavManager.handleBackPressed();
    }
    
    }
