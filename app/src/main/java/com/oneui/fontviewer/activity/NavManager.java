package com.oneui.fontviewer.activity;

import android.view.View;
import android.view.ViewGroup;

import androidx.core.view.GravityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import dev.oneuiproject.oneui.layout.DrawerLayout;

import com.oneui.fontviewer.fragment.localfont.LocalFontListFragment;
import com.oneui.fontviewer.fragment.favorite.FavoriteFontListFragment;
import com.oneui.fontviewer.fragment.trash.TrashFragment;
import com.oneui.fontviewer.drawer.DrawerListAdapter;
import com.oneui.fontviewer.widget.search.SearchCoordinator;
import com.oneui.fontviewer.R;

public class NavManager {

    public interface Host {
        FragmentManager getAppFragmentManager();
        Fragment getFragment(AppScreen screen);
        AppScreen getCurrentScreen();
        void setCurrentScreen(AppScreen screen);
        DrawerLayout getDrawerLayout();
        DrawerListAdapter getDrawerAdapter();
        SearchCoordinator getSearchCoordinator();
        void updateDrawerTitle(AppScreen screen);
        void performExit();
    }

    private final Host mHost;
    private androidx.drawerlayout.widget.DrawerLayout mInnerDrawer;

    public NavManager(Host host) {
        mHost = host;
    }

    public void setup(DrawerLayout drawerLayout) {
        if (drawerLayout != null) {
            mInnerDrawer = findInnerDrawerLayout(drawerLayout);
        }
    }

    public boolean isDrawerCurrentlyOpen() {
        return mInnerDrawer != null && mInnerDrawer.isDrawerOpen(GravityCompat.START);
    }

    private androidx.drawerlayout.widget.DrawerLayout findInnerDrawerLayout(ViewGroup parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof androidx.drawerlayout.widget.DrawerLayout) {
                return (androidx.drawerlayout.widget.DrawerLayout) child;
            }
            if (child instanceof ViewGroup) {
                androidx.drawerlayout.widget.DrawerLayout result =
                        findInnerDrawerLayout((ViewGroup) child);
                if (result != null) return result;
            }
        }
        return null;
    }

    public void navigateFromDrawer(AppScreen screen) {
        mHost.setCurrentScreen(screen);
        showFragmentFast(screen);
        mHost.getDrawerAdapter().setSelectedItem(screen);
        mHost.updateDrawerTitle(screen);
    }

    public void showFragmentFast(AppScreen screen) {
        FragmentManager fm = mHost.getAppFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();

        for (AppScreen s : AppScreen.values()) {
            Fragment frag = mHost.getFragment(s);
            if (frag != null && frag.isAdded()) {
                if (s == screen) {
                    transaction.show(frag);
                    frag.setMenuVisibility(true);
                } else {
                    transaction.hide(frag);
                    frag.setMenuVisibility(false);
                }
            }
        }

        transaction.commitNow();

        mHost.setCurrentScreen(screen);
        if (mHost.getSearchCoordinator() != null) {
            mHost.getSearchCoordinator().onFragmentChanged(screen);
        }
    }

    public void navigateFromDrawerAnimated(AppScreen screen) {
        mHost.setCurrentScreen(screen);
        showFragmentAnimated(screen);
        mHost.getDrawerAdapter().setSelectedItem(screen);
        mHost.updateDrawerTitle(screen);
    }

    public void showFragmentAnimated(AppScreen screen) {
        FragmentManager fm = mHost.getAppFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();
        transaction.setCustomAnimations(R.anim.note_style_fragment_enter, R.anim.fragment_close_exit);

        for (AppScreen s : AppScreen.values()) {
            Fragment frag = mHost.getFragment(s);
            if (frag != null && frag.isAdded() && s != screen && !frag.isHidden()) {
                transaction.hide(frag);
                frag.setMenuVisibility(false);
            }
        }

        Fragment newFrag = mHost.getFragment(screen);
        if (newFrag != null && newFrag.isAdded()) {
            transaction.show(newFrag);
            newFrag.setMenuVisibility(true);
        }

        transaction.commitNow();

        mHost.setCurrentScreen(screen);
        if (mHost.getSearchCoordinator() != null) {
            mHost.getSearchCoordinator().onFragmentChanged(screen);
        }
    }

    public void handleBackPressed() {
        if (isDrawerCurrentlyOpen()) {
            mHost.getDrawerLayout().setDrawerOpen(false, true);
            return;
        }

        AppScreen currentScreen = mHost.getCurrentScreen();
        Fragment currentFragment = mHost.getFragment(currentScreen);
        if (currentFragment instanceof LocalFontListFragment) {
            if (((LocalFontListFragment) currentFragment).handleBackPressed()) return;
        } else if (currentFragment instanceof FavoriteFontListFragment) {
            if (((FavoriteFontListFragment) currentFragment).handleBackPressed()) return;
        } else if (currentFragment instanceof TrashFragment) {
            if (((TrashFragment) currentFragment).handleBackPressed()) return;
        }

        if (mHost.getSearchCoordinator().isSearchExpanded()) {
            mHost.getSearchCoordinator().collapseSearch();
            return;
        }

        if (mHost.getCurrentScreen() != AppScreen.LOCAL_FONTS) {
            navigateFromDrawerAnimated(AppScreen.LOCAL_FONTS);
            return;
        }

        Fragment localFrag = mHost.getFragment(AppScreen.LOCAL_FONTS);
        if (localFrag instanceof LocalFontListFragment) {
            if (((LocalFontListFragment) localFrag).handleExitBackPress()) {
                mHost.performExit();
            }
        } else {
            mHost.performExit();
        }
    }
}
