package com.example.oneuiapp.trash.adapter;

import android.content.Context;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListUpdateCallback;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.util.SeslRoundedCorner;

import dev.oneuiproject.oneui.widget.RoundLinearLayout;

import com.example.oneuiapp.R;
import com.example.oneuiapp.data.entity.FontEntity;
import com.example.oneuiapp.fragment.settings.utils.SettingsHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * TrashListAdapter — محول عرض سلة المحذوفات
 *
 * ════════════════════════════════════════════════════════════
 * الميزات المطبَّقة:
 *
 * ★ (1) لا فرز — الترتيب يأتي من FontDao (الأحدث حذفاً أولاً)
 * ★ (2) لا بحث
 * ★ (3) الضغط العادي يُظهر Toast بدلاً من فتح الخط
 * ★ (4) لا تطبيق Typeface على أسماء الخطوط (لا معاينات)
 * ★ (5) بطاقات شفافة دائماً (trash_list_item.xml)
 * ★ (7) رسالة الـ 30 يوماً كـ VIEW_TYPE_HEADER داخل الـ Adapter
 *        لكي تتدفق وتتحرك مع القائمة بدلاً من أن تكون ثابتة
 *        (النهج الاحترافي المذكور في الملاحظة 27)
 * ★ (8) لا عرض لوزن وعرض الخط
 * ★ (9) عرض الأيام المتبقية على كل عنصر باستخدام Plurals
 * ★ (13) الضغط المطول يُشغّل وضع التحديد المتعدد
 * ════════════════════════════════════════════════════════════
 *
 * الملفات المطلوبة بجانب هذا الـ Adapter:
 *   - res/layout/trash_list_item.xml     : تصميم عنصر السلة
 *   - res/layout/trash_header_item.xml   : تصميم رسالة الـ 30 يوماً
 *   - res/values/strings.xml             : يجب أن يحتوي على:
 *       ● trash_items_expiry_info
 *       ● toast_prepare_file_to_open
 *
 * معرّفات Views المطلوبة في trash_list_item.xml:
 *   - @+id/checkbox
 *   - @+id/trash_item_font_name
 *   - @+id/trash_item_days_remaining
 *   - @+id/item_divider
 *
 * معرّف View المطلوب في trash_header_item.xml:
 *   - @+id/trash_header_message
 */
public class TrashListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    // ─────────────────────────────────────────────────────────
    // أنواع العناصر
    // ─────────────────────────────────────────────────────────
    public static final int VIEW_TYPE_HEADER = 0;
    public static final int VIEW_TYPE_ITEM   = 1;
    public static final int VIEW_TYPE_SPACE  = 2;

    // مدة الاحتفاظ: 30 يوماً بالميلي ثانية
    private static final long DAYS_30_MS = 30L * 24L * 60L * 60L * 1000L;

    // Payload للتحديث الجزئي لـ CheckBox دون إعادة رسم العنصر كاملاً
    private static final String PAYLOAD_UPDATE_SELECTION = "UPDATE_SELECTION";
    private static final String PAYLOAD_UPDATE_CORNERS   = "UPDATE_CORNERS";

    private final Context context;
    private final boolean isTransparentTheme;
    private List<FontEntity> mItems = new ArrayList<>();

    private boolean isSelectionMode = false;

    // المفتاح: موقع الـ Adapter (يشمل إزاحة الهيدر +1)
    private final SparseBooleanArray selectedItems = new SparseBooleanArray();

    private OnSelectionListener selectionListener;
    private RecyclerView recyclerView;

    // ─────────────────────────────────────────────────────────
    // Interfaces
    // ─────────────────────────────────────────────────────────

    /**
     * مُستمع وضع التحديد المتعدد.
     * يُنفَّذ بواسطة TrashFragment للتعامل مع أحداث بدء التحديد وتبديله.
     */
    public interface OnSelectionListener {
        /** يُستدعى عند بدء وضع التحديد عبر الضغط المطول */
        void onStartSelection(int adapterPosition);
        /** يُستدعى عند النقر على عنصر أثناء وضع التحديد */
        void onToggleSelection(int adapterPosition);
    }

    // ─────────────────────────────────────────────────────────
    // Constructor
    // ─────────────────────────────────────────────────────────

    public TrashListAdapter(@NonNull Context context) {
        this.context = context;
        setHasStableIds(true);
        this.isTransparentTheme = SettingsHelper.isTransparentThemeEnabled(context);

        registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override public void onItemRangeInserted(int p, int c) { updateListEdges(); }
            @Override public void onItemRangeRemoved(int p, int c)  { updateListEdges(); }
            @Override public void onItemRangeMoved(int f, int t, int c) { updateListEdges(); }

            private void updateListEdges() {
                if (isTransparentTheme || recyclerView == null) return;
                recyclerView.post(() -> {
                    if (recyclerView == null || recyclerView.isComputingLayout()) return;
                    int total = getItemCount();
                    if (total > 0) notifyItemRangeChanged(0, total, PAYLOAD_UPDATE_CORNERS);
                });
            }
        });
    }

    // ─────────────────────────────────────────────────────────
    // Setters
    // ─────────────────────────────────────────────────────────

    public void setSelectionListener(@Nullable OnSelectionListener listener) {
        this.selectionListener = listener;
    }

    // ─────────────────────────────────────────────────────────
    // تحديث البيانات عبر DiffUtil
    // ─────────────────────────────────────────────────────────

    /**
     * يُحدّث قائمة السلة بكفاءة عبر DiffUtil.
     *
     * يستخدم ListUpdateCallback مخصصاً لإضافة إزاحة +1 لجميع المواقع
     * تعويضاً عن عنصر الهيدر الثابت في position=0.
     *
     * ملاحظة: يُنصح بمسح التحديد (setSelectionMode(false)) قبل تحديث
     * البيانات لتجنب عدم تطابق مواقع SparseBooleanArray مع العناصر الجديدة.
     */
    public void submitList(@Nullable List<FontEntity> newList) {
        List<FontEntity> safeNew = (newList != null) ? newList : Collections.emptyList();

        DiffUtil.DiffResult result = DiffUtil.calculateDiff(
                new TrashDiffCallback(mItems, safeNew));
        mItems = new ArrayList<>(safeNew);

        // توزيع التحديثات مع إزاحة +1 لاستيعاب الهيدر في position=0
        result.dispatchUpdatesTo(new ListUpdateCallback() {
            @Override
            public void onInserted(int position, int count) {
                notifyItemRangeInserted(position + 1, count);
            }
            @Override
            public void onRemoved(int position, int count) {
                notifyItemRangeRemoved(position + 1, count);
            }
            @Override
            public void onMoved(int fromPosition, int toPosition) {
                notifyItemMoved(fromPosition + 1, toPosition + 1);
            }
            @Override
            public void onChanged(int position, int count, @Nullable Object payload) {
                notifyItemRangeChanged(position + 1, count, payload);
            }
        });
    }

    // ─────────────────────────────────────────────────────────
    // RecyclerView.Adapter — الأساسيات
    // ─────────────────────────────────────────────────────────

    @Override
    public int getItemCount() {
        // هيدر (1) + عناصر السلة (N) + فراغ سفلي (1)
        return mItems.size() + 2;
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) return VIEW_TYPE_HEADER;
        if (position == getItemCount() - 1) return VIEW_TYPE_SPACE;
        return VIEW_TYPE_ITEM;
    }

    @Override
    public long getItemId(int position) {
        if (position == 0) return Long.MIN_VALUE;              // HEADER — معرّف ثابت
        if (position == getItemCount() - 1) return Long.MAX_VALUE; // SPACE — معرّف ثابت
        return mItems.get(position - 1).getId();                // معرّف قاعدة البيانات
    }


    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(context);
        switch (viewType) {
            case VIEW_TYPE_HEADER:
                return new TrashHeaderViewHolder(
                        inf.inflate(R.layout.trash_header_item, parent, false));
            case VIEW_TYPE_SPACE:
                return new SpaceViewHolder(
                        inf.inflate(R.layout.item_bottom_space, parent, false));
            default:
                int itemLayout = isTransparentTheme
                        ? R.layout.trash_list_item_transparent
                        : R.layout.trash_list_item;
                return new TrashItemViewHolder(inf.inflate(itemLayout, parent, false));
        }
    }
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof TrashHeaderViewHolder) {
            ((TrashHeaderViewHolder) holder).bind();
        } else if (holder instanceof TrashItemViewHolder) {
            bindItem((TrashItemViewHolder) holder, mItems.get(position - 1), position);
        }
        updateItemAppearance(holder, position);
    }

    /**
     * الربط الجزئي عبر Payload — يُحدّث CheckBox فقط دون إعادة رسم العنصر كاملاً.
     * يحفظ هذا السلاسة البصرية ويمنع أي وميض أثناء تفعيل/تعطيل وضع التحديد.
     */    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position,
                                 @NonNull List<Object> payloads) {
        if (!payloads.isEmpty()) {
            if (payloads.contains(PAYLOAD_UPDATE_CORNERS)) {
                updateItemAppearance(holder, position);
            }
            if (payloads.contains(PAYLOAD_UPDATE_SELECTION) && holder instanceof TrashItemViewHolder) {
                updateCheckBoxState((TrashItemViewHolder) holder, position);
            }
        } else {
            super.onBindViewHolder(holder, position, payloads);
        }
    }

    private void updateItemAppearance(RecyclerView.ViewHolder holder, int position) {
        if (isTransparentTheme) return;
        if (holder instanceof TrashHeaderViewHolder || holder instanceof SpaceViewHolder) return;

        if (holder instanceof TrashItemViewHolder) {
            TrashItemViewHolder th = (TrashItemViewHolder) holder;
            RoundLinearLayout root = (RoundLinearLayout) th.itemView;
            
            int totalFonts = mItems.size();
            boolean isFirst = (position == 1);
            boolean isLast  = (position == getItemCount() - 2);

            if (totalFonts == 1) {
                root.setRoundedCorners(SeslRoundedCorner.ROUNDED_CORNER_ALL);
                if (th.dividerView != null) th.dividerView.setVisibility(View.GONE);
            } else if (isFirst) {
                root.setRoundedCorners(SeslRoundedCorner.ROUNDED_CORNER_TOP_LEFT
                                     | SeslRoundedCorner.ROUNDED_CORNER_TOP_RIGHT);
                if (th.dividerView != null) th.dividerView.setVisibility(View.VISIBLE);
            } else if (isLast) {
                root.setRoundedCorners(SeslRoundedCorner.ROUNDED_CORNER_BOTTOM_LEFT
                                     | SeslRoundedCorner.ROUNDED_CORNER_BOTTOM_RIGHT);
                if (th.dividerView != null) th.dividerView.setVisibility(View.INVISIBLE);
            } else {
                root.setRoundedCorners(SeslRoundedCorner.ROUNDED_CORNER_NONE);
                if (th.dividerView != null) th.dividerView.setVisibility(View.VISIBLE);
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // ربط بيانات عنصر السلة
    // ─────────────────────────────────────────────────────────

    private void bindItem(@NonNull TrashItemViewHolder holder,
                          @NonNull FontEntity font,
                          int position) {
        // ★ (8): اسم الخط فقط — بدون وزن/عرض
        // ★ (4): بدون تطبيق Typeface (لا معاينة)
        holder.fontNameTextView.setText(font.getDisplayName());

        // ★ (9): الأيام المتبقية حتى الحذف النهائي
        holder.daysRemainingTextView.setText(buildDaysRemainingText(font.getDeletedAt()));

        // حالة CheckBox
        updateCheckBoxState(holder, position);

        // ★ (3): النقر العادي يُظهر Toast بدلاً من فتح الخط
        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;
            if (isSelectionMode) {
                if (selectionListener != null) selectionListener.onToggleSelection(pos);
            } else {
                Toast.makeText(context, R.string.toast_prepare_file_to_open,
                        Toast.LENGTH_SHORT).show();
            }
        });

        // ★ (13): الضغط المطول يبدأ وضع التحديد المتعدد
        holder.itemView.setOnLongClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return false;
            if (!isSelectionMode && selectionListener != null) {
                selectionListener.onStartSelection(pos);
            }
            // تفعيل الـ rubber-band multi-selection الخاص بـ OneUI
            if (recyclerView != null) {
                recyclerView.seslStartLongPressMultiSelection();
            }
            return true;
        });
    }

    /**
     * تحديث حالة CheckBox للعنصر — مُستخدَم في الربط الكامل والجزئي على حدٍّ سواء.
     */

    private void updateCheckBoxState(@NonNull TrashItemViewHolder holder, int position) {
        if (holder.selectableLayout != null) {
            holder.selectableLayout.setSelectionMode(isSelectionMode);
            holder.selectableLayout.setSelectedAnimate(isItemSelected(position));
        }
    }

    // ─────────────────────────────────────────────────────────
    // ★ (9): حساب نص الأيام المتبقية
    // ─────────────────────────────────────────────────────────

    /**
     * يحسب نص الأيام المتبقية حتى الحذف النهائي باستخدام ملفات Plurals.
     *
     * المنطق (يتطابق مع ملف plurals الموجود):
     *   ● 0 يوم  → quantity = 0  → "اليوم"     / "Today"
     *   ● 1 يوم  → quantity = 1  → "يوم واحد"  / "1 day"
     *   ● 2 يوم  → quantity = 2  → "يومان"     / "2 days"    (يُطبَّق في العربية)
     *   ● 3–10   → quantity = few → "%d أيام"  / "%d days"
     *   ● >10    → quantity = many → "%d يوماً" / "%d days"
     *
     * @param deletedAt الطابع الزمني (Unix ms) لوقت النقل إلى السلة
     */
    @NonNull
    private String buildDaysRemainingText(long deletedAt) {
        if (deletedAt <= 0) {
            // قيمة احتياطية: 30 يوماً كاملة إذا لم يُسجَّل تاريخ الحذف
            return context.getResources().getQuantityString(
                    R.plurals.trash_days_remaining, 30, 30);
        }
        long diffMs = System.currentTimeMillis() - deletedAt;
        int daysElapsed   = (int) (diffMs / (24L * 60L * 60L * 1000L));
        int daysRemaining = Math.max(0, 30 - daysElapsed);
        return context.getResources().getQuantityString(
                R.plurals.trash_days_remaining, daysRemaining, daysRemaining);
    }

    // ─────────────────────────────────────────────────────────
    // RecyclerView — الربط والفصل
    // ─────────────────────────────────────────────────────────

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        this.recyclerView = recyclerView;
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        this.recyclerView = null;
    }

    // ─────────────────────────────────────────────────────────
    // إدارة وضع التحديد المتعدد
    // ─────────────────────────────────────────────────────────

    /**
     * تفعيل أو تعطيل وضع التحديد.
     * عند التعطيل يُمسح selectedItems تلقائياً.
     * يستخدم PAYLOAD_UPDATE_SELECTION لتحديث CheckBox فقط دون وميض.
     */
    public void setSelectionMode(boolean enabled) {
        this.isSelectionMode = enabled;
        if (!enabled) selectedItems.clear();
        notifyItemRangeChanged(1, mItems.size(), PAYLOAD_UPDATE_SELECTION);
    }

    /**
     * تحديد أو إلغاء تحديد عنصر واحد بموقعه في الـ Adapter.
     * يستخدم Payload لتحديث CheckBox فقط دون إعادة رسم بقية العنصر.
     *
     * @param adapterPosition موقع العنصر في الـ Adapter (يشمل إزاحة الهيدر)
     */
    public void setItemSelected(int adapterPosition, boolean selected) {
        if (selected) selectedItems.put(adapterPosition, true);
        else selectedItems.delete(adapterPosition);
        notifyItemChanged(adapterPosition, PAYLOAD_UPDATE_SELECTION);
    }

    /**
     * تحديد جميع العناصر (لزر "تحديد الكل" في Toolbar).
     */
    public void selectAll() {
        // المواقع 1..N تمثل العناصر (إزاحة الهيدر = 1)
        for (int i = 1; i <= mItems.size(); i++) {
            selectedItems.put(i, true);
        }
        notifyItemRangeChanged(1, mItems.size(), PAYLOAD_UPDATE_SELECTION);
    }

    /**
     * إلغاء تحديد جميع العناصر.
     */
    public void clearSelection() {
        selectedItems.clear();
        notifyItemRangeChanged(1, mItems.size(), PAYLOAD_UPDATE_SELECTION);
    }

    public boolean isItemSelected(int adapterPosition) {
        return selectedItems.get(adapterPosition, false);
    }

    public boolean isSelectionMode() {
        return isSelectionMode;
    }

    /**
     * عدد العناصر المحددة حالياً.
     */
    public int getSelectedCount() {
        int count = 0;
        for (int i = 0; i < selectedItems.size(); i++) {
            if (selectedItems.valueAt(i)) count++;
        }
        return count;
    }

    /**
     * عدد العناصر الكلي في السلة (دون الهيدر والفراغ السفلي).
     */
    public int getTotalItemCount() {
        return mItems.size();
    }

    /**
     * ★ (14): هل جميع العناصر محددة؟
     * يُستخدم في TrashFragment لتحديد نص أزرار الـ bottom bar:
     *   true  → "حذف الكل" / "استعادة الكل"
     *   false → "حذف" / "استعاده"
     */
    public boolean isAllSelected() {
        return !mItems.isEmpty() && getSelectedCount() == mItems.size();
    }

    /**
     * إرجاع قائمة بـ FontEntity للعناصر المحددة حالياً.
     * يُستخدم لتمريرها إلى TrashViewModel لتنفيذ الاستعادة أو الحذف.
     */
    @NonNull
    public List<FontEntity> getSelectedFonts() {
        List<FontEntity> selected = new ArrayList<>();
        for (int i = 0; i < selectedItems.size(); i++) {
            if (!selectedItems.valueAt(i)) continue;
            int adapterPos = selectedItems.keyAt(i);
            int itemIndex  = adapterPos - 1; // تحويل موقع الـ Adapter إلى فهرس القائمة
            if (itemIndex >= 0 && itemIndex < mItems.size()) {
                selected.add(mItems.get(itemIndex));
            }
        }
        return selected;
    }

    /**
     * إرجاع جميع العناصر في السلة.
     * يُستخدم لعملية "إفراغ السلة" التي تُمرر الكل إلى TrashViewModel.
     */
    @NonNull
    public List<FontEntity> getAllFonts() {
        return new ArrayList<>(mItems);
    }

    /**
     * إرجاع FontEntity بموقعه في الـ Adapter.
     *
     * @param adapterPosition يشمل إزاحة الهيدر (position=1 هو العنصر الأول)
     * @return الكيان المطابق، أو null إذا كان الموقع خارج النطاق
     */
    @Nullable
    public FontEntity getItemAtAdapterPosition(int adapterPosition) {
        int itemIndex = adapterPosition - 1;
        if (itemIndex < 0 || itemIndex >= mItems.size()) return null;
        return mItems.get(itemIndex);
    }

    // ─────────────────────────────────────────────────────────
    // ViewHolders
    // ─────────────────────────────────────────────────────────

    /**
     * ★ (7) ViewHolder رسالة الـ 30 يوماً.
     *
     * يُضاف كـ VIEW_TYPE_HEADER ليكون جزءاً من القائمة ويتحرك معها
     * بدلاً من أن يكون في NestedScrollView منفصل (الملاحظة 27).
     * هذا يضمن الأداء الكامل لـ RecyclerView مع تمرير سلس.
     *
     * النص محدد في:
     *   strings.xml → R.string.trash_items_expiry_info
     */
    static class TrashHeaderViewHolder extends RecyclerView.ViewHolder {

        private final TextView messageTextView;

        TrashHeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            messageTextView = itemView.findViewById(R.id.trash_header_message);
        }

        void bind() {
            // النص ثابت ومحدد في strings.xml
            // يمكن تعيينه هنا أو تركه محدداً كـ android:text في XML
            if (messageTextView != null) {
                messageTextView.setText(R.string.trash_items_expiry_info);
            }
        }
    }

    /**
     * ViewHolder عنصر الخط في سلة المحذوفات.
     *
     * ★ (8): لا weightWidthTextView — لا عرض لوزن/عرض الخط.
     * ★ (4): لا تطبيق Typeface — لا معاينة للخط.
     * ★ (9): daysRemainingTextView لعرض الأيام المتبقية.
     *
     * يعتمد على trash_list_item.xml المبني على CardView الذي يتكفل
     * بالزوايا الدائرية والحدود البصرية تلقائياً — لا كود جافا مطلوب.
     *
     * يجب أن يحتوي الـ XML على:
     *   - @+id/checkbox                  (CheckBox)
     *   - @+id/trash_item_font_name      (TextView — اللون الأساسي)
     *   - @+id/trash_item_days_remaining (TextView — اللون الثانوي)
     *
     * لا حاجة لـ @+id/item_divider — CardView يفصل العناصر بصرياً.
     */
    static class TrashItemViewHolder extends RecyclerView.ViewHolder {

        final com.example.oneuiapp.widget.SelectableLinearLayout selectableLayout;
        final TextView fontNameTextView;
        final TextView daysRemainingTextView;
        final View dividerView;

        TrashItemViewHolder(@NonNull View itemView) {
            super(itemView);
            selectableLayout      = itemView.findViewById(R.id.selectable_layout);
            fontNameTextView      = itemView.findViewById(R.id.trash_item_font_name);
            daysRemainingTextView = itemView.findViewById(R.id.trash_item_days_remaining);
            dividerView           = itemView.findViewById(R.id.item_divider);
        }
    }

    /**
     * ViewHolder الفراغ السفلي الوهمي.
     * يمنع اختباء آخر عنصر خلف شريط التنقل السفلي.
     */
    static class SpaceViewHolder extends RecyclerView.ViewHolder {
        SpaceViewHolder(@NonNull View itemView) {
            super(itemView);
            itemView.setFocusable(false);
            itemView.setClickable(false);
        }
    }

    // ─────────────────────────────────────────────────────────
    // DiffUtil Callback
    // ─────────────────────────────────────────────────────────

    /**
     * يقارن قائمتي FontEntity لحساب الفرق بكفاءة.
     * يعمل على القوائم الخام (بدون هيدر/فراغ) —
     * الإزاحة +1 تُعالَج في ListUpdateCallback المخصص في submitList().
     */
    private static class TrashDiffCallback extends DiffUtil.Callback {

        private final List<FontEntity> oldList;
        private final List<FontEntity> newList;

        TrashDiffCallback(@NonNull List<FontEntity> oldList,
                          @NonNull List<FontEntity> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override public int getOldListSize() { return oldList.size(); }
        @Override public int getNewListSize() { return newList.size(); }

        /**
         * يتحقق من هوية العنصر عبر الـ ID في قاعدة البيانات (مستقر دائماً).
         */
        @Override
        public boolean areItemsTheSame(int oldPos, int newPos) {
            return oldList.get(oldPos).getId() == newList.get(newPos).getId();
        }

        /**
         * يتحقق مما إذا كان محتوى العنصر تغيّر
         * (اسم الملف أو تاريخ الحذف — الأيام المتبقية تتغير مع الوقت).
         */
        @Override
        public boolean areContentsTheSame(int oldPos, int newPos) {
            FontEntity o = oldList.get(oldPos);
            FontEntity n = newList.get(newPos);
            return o.getFileName().equals(n.getFileName())
                    && o.getDeletedAt() == n.getDeletedAt()
                    && o.isTrashed() == n.isTrashed();
        }
    }
    }
