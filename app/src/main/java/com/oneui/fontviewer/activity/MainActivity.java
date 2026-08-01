package com.oneui.fontviewer.activity;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Toast;
import android.os.Looper;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.core.splashscreen.SplashScreen;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.app.AlertDialog;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import dev.oneuiproject.oneui.dialog.ProgressDialog;
import dev.oneuiproject.oneui.layout.DrawerLayout;

import com.oneui.fontviewer.dialog.FontInfoDialog;
import com.oneui.fontviewer.fragment.fontviewer.FontViewerActivity;
import com.oneui.fontviewer.fragment.localfont.LocalFontListFragment;
import com.oneui.fontviewer.fragment.systemfont.SystemFontListFragment;
import com.oneui.fontviewer.fragment.favorite.FavoriteFontListFragment;
import com.oneui.fontviewer.fragment.trash.TrashFragment;
import com.oneui.fontviewer.drawer.DrawerListAdapter;
import com.oneui.fontviewer.R;
import com.oneui.fontviewer.utils.FileUtils;
import com.oneui.fontviewer.utils.translation.TranslationService;
import com.oneui.fontviewer.widget.search.SearchCoordinator;
import com.oneui.fontviewer.widget.TextDrawable;
import com.oneui.fontviewer.fragment.settings.SettingsActivity;
import com.oneui.fontviewer.fragment.home.HomeActivity;
import com.oneui.fontviewer.utils.notification.BatchOperationState;

public class MainActivity extends BaseActivity
    implements LocalFontListFragment.OnFontSelectedListener,
    SystemFontListFragment.OnFontSelectedListener,
    FavoriteFontListFragment.OnFontSelectedListener,
    NavManager.Host {

    private boolean isUIReady = false;
    private long mSplashStartTime = 0L;
    private DrawerLayout mDrawerLayout;
    private RecyclerView mDrawerListView;
    private DrawerListAdapter mDrawerAdapter;

    private final Map<AppScreen, Fragment> mFragmentsMap = new EnumMap<>(AppScreen.class);

    private AppScreen mCurrentScreen = AppScreen.LOCAL_FONTS;

    private static final String KEY_CURRENT_SCREEN          = "current_screen";
    private static final String KEY_LOCAL_FONTS_COUNT        = "local_fonts_count";
    private static final String KEY_SYSTEM_FONTS_COUNT       = "system_fonts_count";
    private static final String KEY_FAVORITE_FONTS_COUNT     = "favorite_fonts_count";
    private static final String KEY_TRASH_FONTS_COUNT        = "trash_fonts_count";

    private static final int PERM_REQUEST_POST_NOTIFICATIONS = 1001;

    public static final String EXTRA_TARGET_FRAGMENT = "target_fragment";

    private int mLocalFontsCount    = 0;
    private int mSystemFontsCount   = 0;
    private int mFavoriteFontsCount = 0;
    private int mTrashFontsCount    = 0;

    private NavManager mNavManager;

    private SearchCoordinator mSearchCoordinator;

    private ProgressDialog loadingDialog;

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

        mNavManager = new NavManager(this);

        initViews();
        initFragmentsList();

        setupSearchCoordinator();

        setupDrawerButton();

        if (savedInstanceState != null) {
            mLocalFontsCount    = savedInstanceState.getInt(KEY_LOCAL_FONTS_COUNT, 0);
            mSystemFontsCount   = savedInstanceState.getInt(KEY_SYSTEM_FONTS_COUNT, 0);
            mFavoriteFontsCount = savedInstanceState.getInt(KEY_FAVORITE_FONTS_COUNT, 0);
            mTrashFontsCount    = savedInstanceState.getInt(KEY_TRASH_FONTS_COUNT, 0);
            restoreFragmentsState(savedInstanceState);
            mSearchCoordinator.restoreState(savedInstanceState);
        } else {
            addAllFragments();
            mCurrentScreen = AppScreen.LOCAL_FONTS;
            mNavManager.showFragmentFast(AppScreen.LOCAL_FONTS);
            warmUpOtherScreens();
        }

        setupDrawer();
        updateDrawerTitle(mCurrentScreen);

        handleIntent(getIntent());

        requestNotificationPermissionIfNeeded();

        long elapsedTime = System.currentTimeMillis() - mSplashStartTime;
        long remainingDelay = Math.max(0L, 800L - elapsedTime);

        new Handler(getMainLooper()).postDelayed(() -> {
            isUIReady = true;
        }, remainingDelay);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        mSearchCoordinator.handleSearchIntent(intent);

        if (intent != null) {
            

            boolean fromNotif = intent.getBooleanExtra("from_notification", false);
            if (fromNotif) {
                BatchOperationState.setShouldReopenDialog(true);
                intent.removeExtra("from_notification");
            }

            String targetScreenName = intent.getStringExtra(EXTRA_TARGET_FRAGMENT);
            if (targetScreenName != null) {
                intent.removeExtra(EXTRA_TARGET_FRAGMENT);
                try {
                    AppScreen targetScreen = AppScreen.valueOf(targetScreenName);
                    if (targetScreen != AppScreen.HOME) {
                        if (mNavManager != null && mCurrentScreen != targetScreen) {
                            mNavManager.navigateFromDrawer(targetScreen);
                        } else if (fromNotif) {
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
                    android.util.Log.w("MainActivity", "Unknown AppScreen name: " + targetScreenName);
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
            mFragmentsMap.put(AppScreen.LOCAL_FONTS, new LocalFontListFragment());
            mFragmentsMap.put(AppScreen.SYSTEM_FONTS, new SystemFontListFragment());
            mFragmentsMap.put(AppScreen.FAVORITES, new FavoriteFontListFragment());
            mFragmentsMap.put(AppScreen.TRASH, new TrashFragment());
        }
    }

    private void setupSearchCoordinator() {
        mSearchCoordinator = new SearchCoordinator(this, mDrawerLayout);

        mSearchCoordinator.setProviders(
                () -> mCurrentScreen,
                screen -> mFragmentsMap.get(screen)
        );

        mSearchCoordinator.setSearchStateListener(new SearchCoordinator.SearchStateListener() {
            @Override
            public void onSearchExpanded() {
                notifyFragmentsSearchState(true);
            }

            @Override
            public void onSearchCollapsed() {
                updateDrawerTitle(mCurrentScreen);
                notifyFragmentsSearchState(false);
            }

            @Override
            public void onSearchQueryChanged(String query) {
            }
        });
    }

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

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        return super.onOptionsItemSelected(item);
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

    private void setDrawerOpen(boolean open, boolean animate) {
        if (mDrawerLayout != null) {
            mDrawerLayout.setDrawerOpen(open, animate);
        }
    }

    private void openSettingsActivity() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    

    private void restoreFragmentsState(Bundle savedInstanceState) {
        String screenName = savedInstanceState.getString(
                KEY_CURRENT_SCREEN, AppScreen.FONT_VIEWER.name());
        try {
            mCurrentScreen = AppScreen.valueOf(screenName);
        } catch (IllegalArgumentException e) {
            mCurrentScreen = AppScreen.FONT_VIEWER;
        }
        if (mCurrentScreen == AppScreen.HOME || mCurrentScreen == AppScreen.FONT_VIEWER) {
            mCurrentScreen = AppScreen.LOCAL_FONTS;
        }

        FragmentManager fm = getSupportFragmentManager();
        for (AppScreen screen : AppScreen.values()) {
            if (screen == AppScreen.HOME || screen == AppScreen.FONT_VIEWER) continue;
            Fragment f = fm.findFragmentByTag(screen.name());
            if (f != null) {
                mFragmentsMap.put(screen, f);
            }
        }

        mNavManager.showFragmentFast(mCurrentScreen);

        if (mDrawerAdapter != null) {
            mDrawerAdapter.setSelectedItem(mCurrentScreen);
        }
    }

    private void addAllFragments() {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();

        for (Map.Entry<AppScreen, Fragment> entry : mFragmentsMap.entrySet()) {
            AppScreen screen = entry.getKey();
            Fragment fragment = entry.getValue();
            transaction.add(R.id.main_content, fragment, screen.name());
            if (screen != mCurrentScreen) {
                transaction.hide(fragment);
            }
        }

        transaction.commitNow();
    }

    private void warmUpOtherScreens() {
        Handler warmupHandler = new Handler(getMainLooper());
        warmupHandler.postDelayed(() -> {
            if (isFinishing() || isDestroyed() || getSupportFragmentManager().isStateSaved()) return;
            warmUpScreenSilently(AppScreen.SYSTEM_FONTS);
            warmupHandler.postDelayed(() -> {
                if (isFinishing() || isDestroyed() || getSupportFragmentManager().isStateSaved()) return;
                warmUpScreenSilently(AppScreen.FAVORITES);
                warmupHandler.postDelayed(() -> {
                    if (isFinishing() || isDestroyed() || getSupportFragmentManager().isStateSaved()) return;
                    warmUpScreenSilently(AppScreen.TRASH);
                    warmupHandler.postDelayed(() -> {
                        if (isFinishing() || isDestroyed() || getSupportFragmentManager().isStateSaved()) return;
                        warmUpScreenSilently(null);
                    }, 60);
                }, 60);
            }, 60);
        }, 60);
    }

    private void warmUpScreenSilently(AppScreen screenToShow) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

        for (AppScreen s : AppScreen.values()) {
            if (s == mCurrentScreen) continue;

            Fragment frag = mFragmentsMap.get(s);
            if (frag == null || !frag.isAdded()) continue;

            if (s == screenToShow) {
                transaction.show(frag);
            } else {
                transaction.hide(frag);
            }
            frag.setMenuVisibility(false);
        }

        transaction.commitNow();

        if (screenToShow != null) {
            Fragment warmedFrag = mFragmentsMap.get(screenToShow);
            if (warmedFrag != null && warmedFrag.getView() != null) {
                warmedFrag.getView().setVisibility(View.INVISIBLE);
            }
        }
    }

    private void setupDrawer() {
        mDrawerListView.setLayoutManager(new LinearLayoutManager(this));

        List<AppScreen> drawerScreenList = Arrays.asList(
                AppScreen.HOME,
                AppScreen.FONT_VIEWER,
                null,                   
                AppScreen.LOCAL_FONTS,
                AppScreen.SYSTEM_FONTS,
                AppScreen.FAVORITES,
                null,                   
                AppScreen.TRASH
        );

        mDrawerAdapter = new DrawerListAdapter(
                this,
                drawerScreenList,
                screen -> {
                    if (screen == AppScreen.HOME) {
                        Intent intent = new Intent(MainActivity.this, HomeActivity.class);
                        startActivity(intent);
                        return false; 
                    }

                    if (screen == AppScreen.FONT_VIEWER) {
                        Intent intent = new Intent(MainActivity.this, FontViewerActivity.class);
                        startActivity(intent);
                        return false;
                    }

                    setDrawerOpen(false, true);

                    if (screen != mCurrentScreen) {
                        mNavManager.navigateFromDrawer(screen);
                        return true;
                    }
                    return false;
                });
        mDrawerListView.setAdapter(mDrawerAdapter);

        mDrawerAdapter.setSelectedItem(mCurrentScreen);

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
    public void updateDrawerTitle(AppScreen screen) {
        if (mDrawerLayout == null) {
            return;
        }

        if (mSearchCoordinator.isSearchExpanded() && screen != AppScreen.FONT_VIEWER) {
            return;
        }

        String title;
        String subtitle;

        switch (screen) {
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
            return getString(R.string.no_fonts_found);
        }
        return getResources().getQuantityString(R.plurals.font_count_subtitle, count, count);
    }

    @Override
    public void updateMenuVisibility(AppScreen screen) {
    }

    


    public void updateFontsCount(AppScreen screen, int count) {
        if (screen == AppScreen.LOCAL_FONTS) {
            mLocalFontsCount = count;
        } else if (screen == AppScreen.SYSTEM_FONTS) {
            mSystemFontsCount = count;
        } else if (screen == AppScreen.FAVORITES) {
            mFavoriteFontsCount = count;
        } else if (screen == AppScreen.TRASH) {
            mTrashFontsCount = count;
            if (mCurrentScreen == AppScreen.TRASH) {
                invalidateOptionsMenu();
            }
        }

        if (screen == mCurrentScreen && !mSearchCoordinator.isSearchExpanded()) {
            runOnUiThread(() -> {
                if (mDrawerLayout != null) {
                    mDrawerLayout.setExpandedSubtitle(getFontsCountString(count));
                }
            });
        }
    }

    @Deprecated
    public void updateFontsCount(int fromFragmentIndex, int count) {
        AppScreen screen;
        switch (fromFragmentIndex) {
            case 2:  screen = AppScreen.LOCAL_FONTS;  break;
            case 3:  screen = AppScreen.SYSTEM_FONTS; break;
            case 4:  screen = AppScreen.FAVORITES;    break;
            case 5:  screen = AppScreen.TRASH;        break;
            default: return; 
        }
        updateFontsCount(screen, count);
    }

    


    @Override
    public void onFontSelected(String fontPath, String realName, String fileName,
                               int ttcIndex, String weightWidthLabel) {
        boolean isSystemFont = mCurrentScreen == AppScreen.SYSTEM_FONTS;

        if (mSearchCoordinator != null) {
            mSearchCoordinator.clearSearchFocus();
        }

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
    public Fragment getFragment(AppScreen screen) {
        return mFragmentsMap.get(screen);
    }

    @Override
    public AppScreen getCurrentScreen() {
        return mCurrentScreen;
    }

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
        outState.putString(KEY_CURRENT_SCREEN, mCurrentScreen.name());
        outState.putInt(KEY_LOCAL_FONTS_COUNT,    mLocalFontsCount);
        outState.putInt(KEY_SYSTEM_FONTS_COUNT,   mSystemFontsCount);
        outState.putInt(KEY_FAVORITE_FONTS_COUNT, mFavoriteFontsCount);
        outState.putInt(KEY_TRASH_FONTS_COUNT,    mTrashFontsCount);
        mSearchCoordinator.saveState(outState);
    }

    @Override
    protected void onDestroy() {
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
        }
        mSearchCoordinator.cleanup();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        mNavManager.handleBackPressed();
    }


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
