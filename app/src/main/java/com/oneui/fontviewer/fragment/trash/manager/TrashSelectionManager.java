package com.oneui.fontviewer.fragment.trash.manager;

import android.os.Build;
import android.util.SparseBooleanArray;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import androidx.activity.OnBackPressedCallback;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.oneui.fontviewer.R;
import com.oneui.fontviewer.data.entity.FontEntity;
import com.oneui.fontviewer.fragment.trash.adapter.TrashListAdapter;

import java.util.ArrayList;
import java.util.List;

import com.oneui.fontviewer.widget.OneUiDrawerLayout;

public class TrashSelectionManager {


    private final FragmentActivity activity;
    private final OneUiDrawerLayout drawerLayout;
    private final TrashListAdapter adapter;
    private final RecyclerView     recyclerView;

    private boolean             isSelecting      = false;
    private boolean             checkAllListening = true;
    private SparseBooleanArray  selectedItems    = new SparseBooleanArray();

    private SelectionActionListener actionListener;
    private OnBackPressedCallback   onBackPressedCallback;
    private OnBackInvokedCallback   onBackInvokedCallback;


    public interface SelectionActionListener {
        void onRestoreRequested(List<FontEntity> fonts);

        void onDeletePermanentlyRequested(List<FontEntity> fonts);
    }


    public TrashSelectionManager(
            FragmentActivity activity,
            OneUiDrawerLayout drawerLayout,
            TrashListAdapter adapter,
            RecyclerView     recyclerView) {

        this.activity     = activity;
        this.drawerLayout = drawerLayout;
        this.adapter      = adapter;
        this.recyclerView = recyclerView;

        setupRecyclerViewListener();
        setupBackHandling();
    }


    public void setActionListener(SelectionActionListener listener) {
        this.actionListener = listener;
    }


    private void setupRecyclerViewListener() {
        recyclerView.seslSetLongPressMultiSelectionListener(
                new RecyclerView.SeslLongPressMultiSelectionListener() {
                    @Override
                    public void onItemSelected(RecyclerView view, View child,
                                               int position, long id) {
                        if (adapter.getItemViewType(position) == TrashListAdapter.VIEW_TYPE_ITEM) {
                            toggleSelection(position);
                        }
                    }

                    @Override public void onLongPressMultiSelectionStarted(int x, int y) {}
                    @Override public void onLongPressMultiSelectionEnded(int x, int y)   {}
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
        else          deactivateSelectionMode();
    }

    private void activateSelectionMode() {
        adapter.setSelectionMode(true);

        drawerLayout.getActionModeBottomMenu().clear();
        drawerLayout.setActionModeMenu(R.menu.menu_trash_actions);
        drawerLayout.showActionMode();

        drawerLayout.setActionModeMenuListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_restore) {
                handleRestoreAction();
                return true;
            } else if (id == R.id.action_delete) {
                handleDeleteAction();
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
        selectedItems.clear();
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

        if (selectedItems.get(position, false)) selectedItems.delete(position);
        else selectedItems.put(position, true);

        adapter.setItemSelected(position, selectedItems.get(position, false));
        updateActionModeUI();
    }

    private void toggleSelectAll(boolean selectAll) {
        selectedItems.clear();
        int itemCount = adapter.getItemCount();
        for (int i = 1; i < itemCount - 1; i++) {
            if (selectAll) selectedItems.put(i, true);
            adapter.setItemSelected(i, selectAll);
        }
    }


    private void updateActionModeUI() {
        checkAllListening = false;

        int selectedCount = selectedItems.size();
        int totalCount = adapter.getItemCount() - 2;

        drawerLayout.setActionModeAllSelector(selectedCount, true, selectedCount == totalCount);

        if (selectedCount > 0) {
            Menu bottomMenu  = drawerLayout.getActionModeBottomMenu();
            Menu toolbarMenu = drawerLayout.getActionModeToolbarMenu();

            MenuItem deleteItemBottom   = bottomMenu  != null ? bottomMenu.findItem(R.id.action_delete)    : null;
            MenuItem deleteItemToolbar  = toolbarMenu != null ? toolbarMenu.findItem(R.id.action_delete)   : null;
            MenuItem restoreItemBottom  = bottomMenu  != null ? bottomMenu.findItem(R.id.action_restore)   : null;
            MenuItem restoreItemToolbar = toolbarMenu != null ? toolbarMenu.findItem(R.id.action_restore)  : null;

            boolean isAllSelected = (selectedCount == totalCount);

            String deleteText = isAllSelected
                    ? activity.getString(R.string.action_delete_all)
                    : activity.getString(R.string.action_delete);

            String restoreText = isAllSelected
                    ? activity.getString(R.string.action_restore_all)
                    : activity.getString(R.string.action_restore);

            if (deleteItemBottom  != null) deleteItemBottom.setTitle(deleteText);
            if (deleteItemToolbar != null) deleteItemToolbar.setTitle(deleteText);
            if (restoreItemBottom  != null) restoreItemBottom.setTitle(restoreText);
            if (restoreItemToolbar != null) restoreItemToolbar.setTitle(restoreText);
        }

        checkAllListening = true;
    }

    public void refreshActionMode() {
        if (isSelecting) {
            recyclerView.post(this::updateActionModeUI);
        }
    }


    private void handleRestoreAction() {
        if (selectedItems.size() == 0 || actionListener == null) return;
        actionListener.onRestoreRequested(getSelectedFonts());
    }

    private void handleDeleteAction() {
        if (selectedItems.size() == 0 || actionListener == null) return;
        actionListener.onDeletePermanentlyRequested(getSelectedFonts());
    }

    private List<FontEntity> getSelectedFonts() {
        List<FontEntity> fonts = new ArrayList<>();
        for (int i = 0; i < selectedItems.size(); i++) {
            int adapterPos = selectedItems.keyAt(i);
            FontEntity font = adapter.getItemAtAdapterPosition(adapterPos);
            if (font != null) fonts.add(font);
        }
        return fonts;
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
        onBackPressedCallback = null;
        onBackInvokedCallback = null;
        actionListener        = null;
    }
                }
