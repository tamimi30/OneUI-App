package com.example.oneuiapp.fragment.trash.manager;

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

import com.example.oneuiapp.R;
import com.example.oneuiapp.data.entity.FontEntity;
import com.example.oneuiapp.fragment.trash.adapter.TrashListAdapter;

import java.util.ArrayList;
import java.util.List;

import dev.oneuiproject.oneui.layout.DrawerLayout;

/**
 * TrashSelectionManager — إدارة التحديد المتعدد في سلة المحذوفات
 *
 * ════════════════════════════════════════════════════════════════════
 * المتطلبات المطبَّقة:
 *
 * ★ (13) إجراءان فقط في شريط الـ Action Mode:
 *        استعادة (ic_oui_refresh) وحذف نهائي (ic_oui_delete_outline)
 *
 * ★ (14) النصوص تتغيّر ديناميكياً بناءً على حالة التحديد:
 *        • إذا كانت جميع العناصر محددة:
 *             الحذف  → "حذف الكل"      | الاستعادة → "استعادة الكل"
 *        • إذا كان التحديد جزئياً:
 *             الحذف  → "حذف"           | الاستعادة → "استعاده"
 *
 * ★ لا توجد قائمة فرز (SortByItemLayout) في سلة المحذوفات
 * ★ الإجراء الوحيد عند النقر العادي هو Toast — يُعالَج في TrashListAdapter
 * ★ الضغط المطول يبدأ وضع التحديد — يُعالَج عبر
 *   OnSelectionListener المُمرَّر من TrashFragment
 * ════════════════════════════════════════════════════════════════════
 *
 * الملفات المطلوبة:
 *   - res/menu/menu_trash_actions.xml
 *       • R.id.action_restore  (أيقونة: ic_oui_refresh)
 *       • R.id.action_delete   (أيقونة: ic_oui_delete_outline)
 *   - res/values/strings.xml
 *       • action_restore      → "استعاده"
 *       • action_restore_all  → "استعادة الكل"
 *       • action_delete       → "حذف"
 *       • action_delete_all   → "حذف الكل"
 *
 * المسار: app/src/main/java/com/example/oneuiapp/trash/TrashSelectionManager.java
 */
public class TrashSelectionManager {

    // ─────────────────────────────────────────────────────────
    // الحقول الأساسية
    // ─────────────────────────────────────────────────────────

    private final FragmentActivity activity;
    private final DrawerLayout     drawerLayout;
    private final TrashListAdapter adapter;
    private final RecyclerView     recyclerView;

    private boolean             isSelecting      = false;
    private boolean             checkAllListening = true;
    private SparseBooleanArray  selectedItems    = new SparseBooleanArray();

    private SelectionActionListener actionListener;
    private OnBackPressedCallback   onBackPressedCallback;
    private OnBackInvokedCallback   onBackInvokedCallback;

    // ─────────────────────────────────────────────────────────
    // واجهة الأحداث
    // ─────────────────────────────────────────────────────────

    /**
     * يُنفّذها TrashFragment للتعامل مع طلبات الاستعادة والحذف النهائي.
     * تُمرَّر قائمة FontEntity مباشرةً بدلاً من المواقع لتجنب أي تحوّل
     * في الـ Index بعد أن تبدأ العمليات الخلفية في TrashViewModel.
     */
    public interface SelectionActionListener {
        /**
         * يُستدعى عند ضغط المستخدم على زر الاستعادة.
         * @param fonts قائمة الخطوط المراد استعادتها إلى مساراتها الأصلية
         */
        void onRestoreRequested(List<FontEntity> fonts);

        /**
         * يُستدعى عند ضغط المستخدم على زر الحذف النهائي.
         * يجب على TrashFragment عرض ديالوج التأكيد قبل إرسال الطلب للـ ViewModel.
         * @param fonts قائمة الخطوط المراد حذفها نهائياً من مجلد .Trash
         */
        void onDeletePermanentlyRequested(List<FontEntity> fonts);
    }

    // ─────────────────────────────────────────────────────────
    // المُنشئ
    // ─────────────────────────────────────────────────────────

    public TrashSelectionManager(
            FragmentActivity activity,
            DrawerLayout     drawerLayout,
            TrashListAdapter adapter,
            RecyclerView     recyclerView) {

        this.activity     = activity;
        this.drawerLayout = drawerLayout;
        this.adapter      = adapter;
        this.recyclerView = recyclerView;

        setupRecyclerViewListener();
        setupBackHandling();
    }

    // ─────────────────────────────────────────────────────────
    // Setters
    // ─────────────────────────────────────────────────────────

    public void setActionListener(SelectionActionListener listener) {
        this.actionListener = listener;
    }

    // ─────────────────────────────────────────────────────────
    // إعداد Rubber-Band Multi-Selection
    // ─────────────────────────────────────────────────────────

    /**
     * يُفعّل ميزة الـ Rubber-Band Multi-Selection الخاصة بـ OneUI.
     * تُعيد عناصر VIEW_TYPE_HEADER و VIEW_TYPE_SPACE دون تأثير
     * بفضل الفحص في onItemSelected().
     */
    private void setupRecyclerViewListener() {
        recyclerView.seslSetLongPressMultiSelectionListener(
                new RecyclerView.SeslLongPressMultiSelectionListener() {
                    @Override
                    public void onItemSelected(RecyclerView view, View child,
                                               int position, long id) {
                        // تجاهل الهيدر والفراغ السفلي — العناصر القابلة للتحديد فقط
                        if (adapter.getItemViewType(position) == TrashListAdapter.VIEW_TYPE_ITEM) {
                            toggleSelection(position);
                        }
                    }

                    @Override public void onLongPressMultiSelectionStarted(int x, int y) {}
                    @Override public void onLongPressMultiSelectionEnded(int x, int y)   {}
                }
        );
    }

    // ─────────────────────────────────────────────────────────
    // إدارة زر الرجوع
    // ─────────────────────────────────────────────────────────

    /**
     * يُهيّئ معالجَي زر الرجوع:
     *  • API 33+: OnBackInvokedCallback (النظام الجديد)
     *  • API < 33: OnBackPressedCallback (AndroidX)
     * كلاهما يستدعي setSelecting(false) عند الضغط أثناء وضع التحديد.
     */
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

    // ─────────────────────────────────────────────────────────
    // تفعيل / تعطيل وضع التحديد
    // ─────────────────────────────────────────────────────────

    public void setSelecting(boolean enabled) {
        if (isSelecting == enabled) return;
        isSelecting = enabled;
        if (enabled) activateSelectionMode();
        else          deactivateSelectionMode();
    }

    private void activateSelectionMode() {
        adapter.setSelectionMode(true);

        // تحضير شريط الـ Action Mode بقائمة سلة المحذوفات فقط
        drawerLayout.getActionModeBottomMenu().clear();
        drawerLayout.setActionModeMenu(R.menu.menu_trash_actions);
        drawerLayout.showActionMode();

        // معالجة ضغطات أزرار شريط الـ Action Mode
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

        // معالجة checkbox "تحديد الكل" في شريط الـ Action Mode
        drawerLayout.setActionModeCheckboxListener((menuItem, isChecked) -> {
            if (checkAllListening) toggleSelectAll(isChecked);
            updateActionModeUI();
        });

        // تسجيل معالجَي زر الرجوع
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    onBackInvokedCallback
            );
        }
        onBackPressedCallback.setEnabled(true);
    }

    private void deactivateSelectionMode() {
        // ★ التسلسل الحرفي لتطبيق المثال الرسمي:
        // 1. تحديث الـ Adapter فوراً (يُخفي checkboxes في نفس الـ frame)
        // 2. setActionModeAllSelector → يُخفي الشريط السفلي
        // 3. dismissActionMode → يبدأ أنيميشن تلاشي الـ toolbar
        // الثلاثة تحدث معاً فيُخفق عين المستخدم عن ملاحظة اختفاء الشريط
        selectedItems.clear();
        adapter.clearSelection();
        adapter.setSelectionMode(false);

        // ★ حل المشكلة 3: تم حذف هذا السطر لمنع التصفير المبكر والوميض
        // drawerLayout.setActionModeAllSelector(0, true, false);
        
        drawerLayout.dismissActionMode();

        // إلغاء تسجيل معالجَي زر الرجوع
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(
                    onBackInvokedCallback
            );
        }
        onBackPressedCallback.setEnabled(false);
    }

    // ─────────────────────────────────────────────────────────
    // إدارة التحديد الفردي والجماعي
    // ─────────────────────────────────────────────────────────

    /**
     * يُبدّل حالة تحديد عنصر واحد.
     * إذا كان وضع التحديد غير مفعّل، يُفعّله أولاً.
     *
     * @param position موقع العنصر في الـ Adapter (يشمل إزاحة الهيدر)
     */
    public void toggleSelection(int position) {
        if (!isSelecting) setSelecting(true);

        if (selectedItems.get(position, false)) selectedItems.delete(position);
        else selectedItems.put(position, true);

        adapter.setItemSelected(position, selectedItems.get(position, false));
        updateActionModeUI();
    }

    /**
     * يُحدد أو يُلغي تحديد جميع العناصر دفعةً واحدة.
     * يبدأ من الموقع 1 لتخطي الهيدر، وينتهي قبل آخر موقع لتخطي الفراغ السفلي.
     *
     * @param selectAll true = تحديد الكل | false = إلغاء الكل
     */
    private void toggleSelectAll(boolean selectAll) {
        selectedItems.clear();
        int itemCount = adapter.getItemCount();
        for (int i = 1; i < itemCount - 1; i++) {
            if (selectAll) selectedItems.put(i, true);
            adapter.setItemSelected(i, selectAll);
        }
    }

    // ─────────────────────────────────────────────────────────
    // تحديث واجهة شريط الـ Action Mode
    // ─────────────────────────────────────────────────────────

    /**
     * يُحدّث عدد العناصر المحددة في الشريط، ونصوص أزرار الحذف والاستعادة.
     *
     * ★ (14) منطق النصوص:
     *   • selectedCount == totalCount → "حذف الكل" / "استعادة الكل"
     *     (يشمل حالة العنصر الواحد: عند تحديد العنصر الوحيد يُعتبر "الكل" محدداً)
     *   • أي حالة أخرى               → "حذف"     / "استعاده"
     *
     * ★ الإصلاح الجوهري: لا نُحدّث الأيقونات/النصوص إذا كان العدد 0
     *   لتجنب اختفائها الفجائي أثناء أنيميشن انزلاق الشريط للأسفل.
     */
    private void updateActionModeUI() {
        checkAllListening = false;

        int selectedCount = selectedItems.size();
        // العدد الفعلي للعناصر = الإجمالي ناقص الهيدر والفراغ السفلي
        int totalCount = adapter.getItemCount() - 2;

        drawerLayout.setActionModeAllSelector(selectedCount, true, selectedCount == totalCount);

        if (selectedCount > 0) {
            Menu bottomMenu  = drawerLayout.getActionModeBottomMenu();
            Menu toolbarMenu = drawerLayout.getActionModeToolbarMenu();

            MenuItem deleteItemBottom   = bottomMenu  != null ? bottomMenu.findItem(R.id.action_delete)    : null;
            MenuItem deleteItemToolbar  = toolbarMenu != null ? toolbarMenu.findItem(R.id.action_delete)   : null;
            MenuItem restoreItemBottom  = bottomMenu  != null ? bottomMenu.findItem(R.id.action_restore)   : null;
            MenuItem restoreItemToolbar = toolbarMenu != null ? toolbarMenu.findItem(R.id.action_restore)  : null;

            // ★ (14) إصلاح: تحديد ما إذا كانت جميع العناصر محددة.
            // الشرط selectedCount == totalCount يكفي وحده — لا حاجة لـ selectedCount > 1،
            // لأن تحديد العنصر الوحيد في قائمة أحادية يعني تحديد "الكل" بالفعل.
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

    /**
     * يُعيد تطبيق updateActionModeUI() بعد دوران الجهاز.
     * يُستدعى من TrashFragment#onConfigurationChanged() أو ما يعادله.
     * يستخدم post() لضمان التنفيذ بعد أن تُعيد DrawerLayout
     * بناء قائمة الـ Action Mode.
     */
    public void refreshActionMode() {
        if (isSelecting) {
            recyclerView.post(this::updateActionModeUI);
        }
    }

    // ─────────────────────────────────────────────────────────
    // معالجة إجراءات الأزرار
    // ─────────────────────────────────────────────────────────

    /**
     * يجمع الخطوط المحددة ويُخطر TrashFragment بطلب الاستعادة.
     * TrashFragment مسؤول عن تمرير الطلب إلى TrashViewModel.
     */
    private void handleRestoreAction() {
        if (selectedItems.size() == 0 || actionListener == null) return;
        actionListener.onRestoreRequested(getSelectedFonts());
    }

    /**
     * يجمع الخطوط المحددة ويُخطر TrashFragment بطلب الحذف النهائي.
     * TrashFragment مسؤول عن عرض ديالوج التأكيد قبل التنفيذ.
     */
    private void handleDeleteAction() {
        if (selectedItems.size() == 0 || actionListener == null) return;
        actionListener.onDeletePermanentlyRequested(getSelectedFonts());
    }

    /**
     * يبني قائمة FontEntity من المواقع المحددة حالياً في SparseBooleanArray.
     * يستخدم getItemAtAdapterPosition() من TrashListAdapter للتحويل المباشر
     * من موقع الـ Adapter إلى كيان FontEntity، مما يُلغي الحاجة إلى أي حسابات إزاحة هنا.
     */
    private List<FontEntity> getSelectedFonts() {
        List<FontEntity> fonts = new ArrayList<>();
        for (int i = 0; i < selectedItems.size(); i++) {
            int adapterPos = selectedItems.keyAt(i);
            FontEntity font = adapter.getItemAtAdapterPosition(adapterPos);
            if (font != null) fonts.add(font);
        }
        return fonts;
    }

    // ─────────────────────────────────────────────────────────
    // واجهة عامة للـ Fragment
    // ─────────────────────────────────────────────────────────

    /** قائمة بمواقع العناصر المحددة في الـ Adapter (تشمل إزاحة الهيدر). */
    public List<Integer> getSelectedPositions() {
        List<Integer> positions = new ArrayList<>();
        for (int i = 0; i < selectedItems.size(); i++) positions.add(selectedItems.keyAt(i));
        return positions;
    }

    public int getSelectedCount()  { return selectedItems.size(); }
    public boolean isSelecting()   { return isSelecting; }

    /** يُعيد OnBackPressedCallback لتسجيله في Fragment#onViewCreated(). */
    public OnBackPressedCallback getOnBackPressedCallback() { return onBackPressedCallback; }

    /**
     * يُعالج ضغط زر الرجوع يدوياً (بديل عن OnBackPressedCallback في بعض الحالات).
     * @return true إذا تمّ استهلاك الحدث (كنا في وضع التحديد)
     */
    public boolean handleBackPress() {
        if (isSelecting) {
            setSelecting(false);
            return true;
        }
        return false;
    }

    /**
     * يُنظّف جميع المراجع لمنع تسرب الذاكرة.
     * يُستدعى من Fragment#onDestroyView().
     */
    public void cleanup() {
        if (isSelecting) setSelecting(false);
        onBackPressedCallback = null;
        onBackInvokedCallback = null;
        actionListener        = null;
    }
            }
