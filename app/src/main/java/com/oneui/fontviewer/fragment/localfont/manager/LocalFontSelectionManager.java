package com.oneui.fontviewer.fragment.localfont.manager;

import android.content.res.Configuration;
import android.os.Build;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import androidx.activity.OnBackPressedCallback;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.oneui.fontviewer.R;
import com.oneui.fontviewer.fragment.localfont.adapter.LocalFontListAdapter;

import java.util.List;

import com.oneui.fontviewer.widget.OneUiDrawerLayout;

public class LocalFontSelectionManager {

    private final FragmentActivity activity;
    private final OneUiDrawerLayout drawerLayout;
    private final LocalFontListAdapter adapter;
    private final RecyclerView recyclerView;
    
    private boolean isSelecting = false;
    private boolean checkAllListening = true;
    
    private SelectionActionListener actionListener;
    private OnBackPressedCallback onBackPressedCallback;
    private OnBackInvokedCallback onBackInvokedCallback;

    private FavoriteStatusChecker favoriteStatusChecker;

    public interface FavoriteStatusChecker {
        boolean isFavorited(String path);
    }

    // الإجراءات تُمرَّر بمسارات الملفات وليس بمواضعها، لأن المواضع قد تتغير أثناء التحديد أو أثناء ظهور الحوارات
    public interface SelectionActionListener {
        void onRenameRequested(String path);
        void onDeleteRequested(List<String> paths);

        void onFavoriteRequested(List<String> paths, boolean addToFavorites);
    }

    public LocalFontSelectionManager(FragmentActivity activity,
        OneUiDrawerLayout drawerLayout,
        LocalFontListAdapter adapter,
        RecyclerView recyclerView) {
        this.activity = activity;
        this.drawerLayout = drawerLayout;
        this.adapter = adapter;
        this.recyclerView = recyclerView;

        setupRecyclerViewListener();
        setupBackHandling();

        // إذا اختفى عنصر محدد من القائمة (تحديث من قاعدة البيانات مثلاً) نحدّث العدّاد وأزرار القائمة
        adapter.setOnSelectionPrunedListener(this::refreshActionMode);
    }

    public void setActionListener(SelectionActionListener listener) {
        this.actionListener = listener;
    }

    public void setFavoriteStatusChecker(FavoriteStatusChecker checker) {
        this.favoriteStatusChecker = checker;
    }

    private void setupRecyclerViewListener() {
        recyclerView.seslSetLongPressMultiSelectionListener(
            new RecyclerView.SeslLongPressMultiSelectionListener() {
                @Override
                public void onItemSelected(RecyclerView view, View child, int position, long id) {
                    if (adapter.getItemViewType(position) == LocalFontListAdapter.VIEW_TYPE_FONT) {
                        toggleSelection(position);
                    }
                }

                @Override
                public void onLongPressMultiSelectionStarted(int x, int y) {}

                @Override
                public void onLongPressMultiSelectionEnded(int x, int y) {}
            }
        );
    }

    private void setupBackHandling() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedCallback = () -> {
                if (isSelecting) setSelecting(false);
            };
        }

        onBackPressedCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                if (isSelecting) setSelecting(false);
            }
        };
    }

    public void setSelecting(boolean enabled) {
        if (isSelecting == enabled) return;
        isSelecting = enabled;
        if (enabled) activateSelectionMode();
        else deactivateSelectionMode();
    }

    private void activateSelectionMode() {
        adapter.setSelectionMode(true);

        drawerLayout.getActionModeBottomMenu().clear();
        drawerLayout.setActionModeMenu(R.menu.menu_font_actions);
        drawerLayout.showActionMode();

        drawerLayout.setActionModeMenuListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_delete) {
                handleDeleteAction();
                return true;
            } else if (id == R.id.action_rename) {
                handleRenameAction();
                return true;
            } else if (id == R.id.action_favorite) {
                handleFavoriteAction();
                return true;
            }
            return false;
        });

        drawerLayout.setActionModeCheckboxListener((menuItem, isChecked) -> {
            if (checkAllListening) toggleSelectAll(isChecked);
            updateActionModeUI();
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                onBackInvokedCallback
            );
        }
        onBackPressedCallback.setEnabled(true);
    }

    private void deactivateSelectionMode() {
        adapter.clearSelection();
        adapter.setSelectionMode(false);

        
        drawerLayout.dismissActionMode();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(
                onBackInvokedCallback
            );
        }
        onBackPressedCallback.setEnabled(false);
    }

    public void toggleSelection(int position) {
        if (!isSelecting) setSelecting(true);

        // حالة التحديد محفوظة في الـ Adapter بمسار الملف
        adapter.setItemSelected(position, !adapter.isItemSelected(position));
        updateActionModeUI();
    }

    private void toggleSelectAll(boolean selectAll) {
        int itemCount = adapter.getItemCount();
        for (int i = 1; i < itemCount - 1; i++) {
            adapter.setItemSelected(i, selectAll);
        }
    }

    private void updateActionModeUI() {
        checkAllListening = false;

        int selectedCount = adapter.getSelectedCount();

        int totalCount = adapter.getItemCount() - 2;

        drawerLayout.setActionModeAllSelector(selectedCount, true, selectedCount == totalCount);

        if (selectedCount > 0) {
            Menu bottomMenu  = drawerLayout.getActionModeBottomMenu();
            Menu toolbarMenu = drawerLayout.getActionModeToolbarMenu();

            MenuItem renameItemBottom  = bottomMenu  != null ? bottomMenu.findItem(R.id.action_rename)   : null;
            MenuItem renameItemToolbar = toolbarMenu != null ? toolbarMenu.findItem(R.id.action_rename)  : null;
            MenuItem deleteItemBottom  = bottomMenu  != null ? bottomMenu.findItem(R.id.action_delete)   : null;
            MenuItem deleteItemToolbar = toolbarMenu != null ? toolbarMenu.findItem(R.id.action_delete)  : null;
            MenuItem favoriteItemBottom  = bottomMenu  != null ? bottomMenu.findItem(R.id.action_favorite)  : null;
            MenuItem favoriteItemToolbar = toolbarMenu != null ? toolbarMenu.findItem(R.id.action_favorite) : null;

            boolean isSingleSelection = (selectedCount == 1);

            boolean isPortrait = activity.getResources().getConfiguration().orientation
                    == Configuration.ORIENTATION_PORTRAIT;

            if (renameItemBottom  != null) renameItemBottom.setVisible(isSingleSelection);
            if (renameItemToolbar != null) renameItemToolbar.setVisible(!isPortrait && isSingleSelection);

            String deleteText = (selectedCount == totalCount)
                    ? activity.getString(R.string.action_delete_all)
                    : activity.getString(R.string.action_delete);

            if (deleteItemBottom  != null) deleteItemBottom.setTitle(deleteText);
            if (deleteItemToolbar != null) deleteItemToolbar.setTitle(deleteText);

            boolean allFavorited = resolveFavoriteAction();

            String favoriteText = allFavorited
                    ? activity.getString(R.string.action_unfavorite)
                    : activity.getString(R.string.action_favorite);
            int favoriteIcon = allFavorited
                    ? dev.oneuiproject.oneui.R.drawable.ic_oui_favorite_off
                    : dev.oneuiproject.oneui.R.drawable.ic_oui_favorite_on;

            if (favoriteItemBottom != null) {
                favoriteItemBottom.setTitle(favoriteText);
                favoriteItemBottom.setIcon(favoriteIcon);
            }
            if (favoriteItemToolbar != null) {
                favoriteItemToolbar.setTitle(favoriteText);
            }
        }

        checkAllListening = true;
    }

    private boolean resolveFavoriteAction() {
        if (favoriteStatusChecker == null || adapter.getSelectedCount() == 0) return false;
        for (String path : adapter.getSelectedPaths()) {
            if (!favoriteStatusChecker.isFavorited(path)) {
                return false;
            }
        }
        return true;
    }

    public void refreshActionMode() {
        if (isSelecting) {
            recyclerView.post(this::updateActionModeUI);
        }
    }

    private void handleRenameAction() {
        if (adapter.getSelectedCount() != 1 || actionListener == null) return;
        List<String> paths = adapter.getSelectedPaths();
        if (paths.size() != 1) return;
        actionListener.onRenameRequested(paths.get(0));
    }

    private void handleDeleteAction() {
        if (adapter.getSelectedCount() == 0 || actionListener == null) return;
        actionListener.onDeleteRequested(adapter.getSelectedPaths());
    }

    private void handleFavoriteAction() {
        if (adapter.getSelectedCount() == 0 || actionListener == null) return;

        boolean allFavorited = resolveFavoriteAction();
        boolean addToFavorites = !allFavorited;

        actionListener.onFavoriteRequested(adapter.getSelectedPaths(), addToFavorites);
    }
 
    public boolean isSelecting()   { return isSelecting; }

    public OnBackPressedCallback getOnBackPressedCallback() { return onBackPressedCallback; }

    public boolean handleBackPress() {
        if (isSelecting) {
            setSelecting(false);
            return true;
        }
        return false;
    }

    public void cleanup() {
        if (isSelecting) setSelecting(false);
        adapter.setOnSelectionPrunedListener(null);
        onBackPressedCallback = null;
        onBackInvokedCallback = null;
        actionListener = null;
        favoriteStatusChecker = null;
    }
    }
