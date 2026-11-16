package com.oneui.fontviewer.ui.navigation;

import android.view.View;
import android.view.ViewGroup;

import androidx.core.view.GravityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.oneui.fontviewer.ui.widget.layout.OneUiDrawerLayout;

import com.oneui.fontviewer.ui.features.localfont.LocalFontListFragment;
import com.oneui.fontviewer.ui.features.favorite.FavoriteFontListFragment;
import com.oneui.fontviewer.ui.features.trash.TrashFragment;
import com.oneui.fontviewer.ui.list.search.SearchCoordinator;
import com.oneui.fontviewer.R;
import com.oneui.fontviewer.ui.navigation.drawerlist.recyclerview.DrawerListAdapter;
import com.oneui.fontviewer.ui.navigation.drawerlist.DrawerListItem;

public class NavManager {

    public interface Host {
        FragmentManager getAppFragmentManager();
        Fragment getFragment(DrawerListItem drawerListItem);
        DrawerListItem getCurrentDrawerListItem();
        void setCurrentDrawerListItem(DrawerListItem drawerListItem);
        OneUiDrawerLayout getDrawerLayout();
        DrawerListAdapter getDrawerAdapter();
        SearchCoordinator getSearchCoordinator();
        void updateDrawerTitle(DrawerListItem drawerListItem);
        void performExit();
    }

    private final Host mHost;
    private androidx.drawerlayout.widget.DrawerLayout mInnerDrawer;

    public NavManager(Host host) {
        mHost = host;
    }

    public void setup(OneUiDrawerLayout drawerLayout) {
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

    public void navigateFromDrawer(DrawerListItem drawerListItem) {
        mHost.setCurrentDrawerListItem(drawerListItem);
        showFragmentFast(drawerListItem);
        mHost.getDrawerAdapter().setSelectedItem(drawerListItem);
        mHost.updateDrawerTitle(drawerListItem);
    }

    public void showFragmentFast(DrawerListItem drawerListItem) {
        FragmentManager fm = mHost.getAppFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();

        for (DrawerListItem s : DrawerListItem.values()) {
            Fragment frag = mHost.getFragment(s);
            if (frag != null && frag.isAdded()) {
                if (s == drawerListItem) {
                    transaction.show(frag);
                    frag.setMenuVisibility(true);
                } else {
                    transaction.hide(frag);
                    frag.setMenuVisibility(false);
                }
            }
        }

        transaction.commitNow();

        mHost.setCurrentDrawerListItem(drawerListItem);
        if (mHost.getSearchCoordinator() != null) {
            mHost.getSearchCoordinator().onFragmentChanged(drawerListItem);
        }
    }

    public void navigateFromDrawerAnimated(DrawerListItem drawerListItem) {
        mHost.setCurrentDrawerListItem(drawerListItem);
        showFragmentAnimated(drawerListItem);
        mHost.getDrawerAdapter().setSelectedItem(drawerListItem);
        mHost.updateDrawerTitle(drawerListItem);
    }

    public void showFragmentAnimated(DrawerListItem drawerListItem) {
        FragmentManager fm = mHost.getAppFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();
        transaction.setCustomAnimations(R.anim.note_style_fragment_enter, R.anim.note_style_fragment_exit);

        for (DrawerListItem s : DrawerListItem.values()) {
            Fragment frag = mHost.getFragment(s);
            if (frag != null && frag.isAdded() && s != drawerListItem && !frag.isHidden()) {
                transaction.hide(frag);
                frag.setMenuVisibility(false);
            }
        }

        Fragment newFrag = mHost.getFragment(drawerListItem);
        if (newFrag != null && newFrag.isAdded()) {
            transaction.show(newFrag);
            newFrag.setMenuVisibility(true);
        }

        transaction.commitNow();

        mHost.setCurrentDrawerListItem(drawerListItem);
        if (mHost.getSearchCoordinator() != null) {
            mHost.getSearchCoordinator().onFragmentChanged(drawerListItem);
        }
    }

    public void handleBackPressed() {
        if (isDrawerCurrentlyOpen()) {
            mHost.getDrawerLayout().setDrawerOpen(false, true);
            return;
        }

        DrawerListItem currentDrawerListItem = mHost.getCurrentDrawerListItem();
        Fragment currentFragment = mHost.getFragment(currentDrawerListItem);
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

        if (mHost.getCurrentDrawerListItem() != DrawerListItem.LOCAL_FONTS) {
            navigateFromDrawerAnimated(DrawerListItem.LOCAL_FONTS);
            return;
        }

        Fragment localFrag = mHost.getFragment(DrawerListItem.LOCAL_FONTS);
        if (localFrag instanceof LocalFontListFragment) {
            if (((LocalFontListFragment) localFrag).handleExitBackPress()) {
                mHost.performExit();
            }
        } else {
            mHost.performExit();
        }
    }
}
