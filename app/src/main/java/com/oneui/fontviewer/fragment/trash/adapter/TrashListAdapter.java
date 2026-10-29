package com.oneui.fontviewer.fragment.trash.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListUpdateCallback;
import androidx.recyclerview.widget.RecyclerView;

import com.oneui.fontviewer.R;
import com.oneui.fontviewer.data.entity.FontEntity;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TrashListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int VIEW_TYPE_HEADER = 0;
    public static final int VIEW_TYPE_ITEM   = 1;
    public static final int VIEW_TYPE_SPACE  = 2;

    private static final String PAYLOAD_UPDATE_SELECTION = "UPDATE_SELECTION";

    private final Context context;
    private List<FontEntity> mItems = new ArrayList<>();

    private boolean isSelectionMode = false;

    // التحديد مرتبط بمعرّف العنصر (id) وليس بموضعه، حتى لا يتحدد عنصر آخر إذا تغيرت القائمة أثناء التحديد
    private final Set<Long> selectedIds = new HashSet<>();

    // يُستدعى عندما يُحذف من التحديد عنصر لم يعد موجوداً في القائمة، ليُحدّث مدير التحديد العدّاد
    private Runnable onSelectionPrunedListener;

    private OnSelectionListener selectionListener;
    private RecyclerView recyclerView;


    public interface OnSelectionListener {
        void onStartSelection(int adapterPosition);
        void onToggleSelection(int adapterPosition);
    }


    public TrashListAdapter(@NonNull Context context) {
        this.context = context;
        setHasStableIds(true);
    }


    public void setSelectionListener(@Nullable OnSelectionListener listener) {
        this.selectionListener = listener;
    }

    public void setOnSelectionPrunedListener(@Nullable Runnable listener) {
        this.onSelectionPrunedListener = listener;
    }


    public void submitList(@Nullable List<FontEntity> newList) {
        List<FontEntity> safeNew = (newList != null) ? newList : Collections.emptyList();

        DiffUtil.DiffResult result = DiffUtil.calculateDiff(
                new TrashDiffCallback(mItems, safeNew));
        mItems = new ArrayList<>(safeNew);

        pruneSelection();

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

    // يزيل من التحديد أي عنصر لم يعد موجوداً في القائمة بعد تحديثها
    private void pruneSelection() {
        if (selectedIds.isEmpty()) return;

        Set<Long> present = new HashSet<>();
        for (FontEntity font : mItems) {
            long id = font.getId();
            present.add(id);
        }

        if (selectedIds.retainAll(present) && onSelectionPrunedListener != null) {
            onSelectionPrunedListener.run();
        }
    }


    @Override
    public int getItemCount() {
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
        if (position == 0) return Long.MIN_VALUE;              
        if (position == getItemCount() - 1) return Long.MAX_VALUE; 
        return mItems.get(position - 1).getId();                
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
                return new TrashItemViewHolder(inf.inflate(R.layout.trash_list_item, parent, false));
        }
    }
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof TrashHeaderViewHolder) {
            ((TrashHeaderViewHolder) holder).bind();
        } else if (holder instanceof TrashItemViewHolder) {
            bindItem((TrashItemViewHolder) holder, mItems.get(position - 1), position);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position,
                                 @NonNull List<Object> payloads) {
        if (!payloads.isEmpty()) {
            if (payloads.contains(PAYLOAD_UPDATE_SELECTION) && holder instanceof TrashItemViewHolder) {
                updateCheckBoxState((TrashItemViewHolder) holder, position);
            }
        } else {
            super.onBindViewHolder(holder, position, payloads);
        }
    }


    private void bindItem(@NonNull TrashItemViewHolder holder,
                          @NonNull FontEntity font,
                          int position) {
        holder.fontNameTextView.setText(font.getDisplayName());

        holder.daysRemainingTextView.setText(buildDaysRemainingText(font.getDeletedAt()));

        updateCheckBoxState(holder, position);

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

        holder.itemView.setOnLongClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return false;
            if (selectionListener != null) {
                if (!isSelectionMode) {
                    selectionListener.onStartSelection(pos);
                } else {
                    selectionListener.onToggleSelection(pos);
                }
            }
            if (recyclerView != null) {
                recyclerView.seslStartLongPressMultiSelection();
            }
            return true;
        });
    }


    private void updateCheckBoxState(@NonNull TrashItemViewHolder holder, int position) {
        if (holder.selectableLayout != null) {
            holder.selectableLayout.setSelectionMode(isSelectionMode);
            holder.selectableLayout.setSelectedAnimate(isItemSelected(position));
        }
    }


    @NonNull
    private String buildDaysRemainingText(long deletedAt) {
        if (deletedAt <= 0) {
            return context.getResources().getQuantityString(
                    R.plurals.trash_days_remaining, 30, 30);
        }
        long diffMs = System.currentTimeMillis() - deletedAt;
        int daysElapsed   = (int) (diffMs / (24L * 60L * 60L * 1000L));
        int daysRemaining = Math.max(0, 30 - daysElapsed);
        return context.getResources().getQuantityString(
                R.plurals.trash_days_remaining, daysRemaining, daysRemaining);
    }


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


    public void setSelectionMode(boolean enabled) {
        this.isSelectionMode = enabled;
        if (!enabled) selectedIds.clear();
        notifyItemRangeChanged(1, mItems.size(), PAYLOAD_UPDATE_SELECTION);
    }

    public void setItemSelected(int adapterPosition, boolean selected) {
        FontEntity font = getItemAtAdapterPosition(adapterPosition);
        if (font == null) return;

        long id = font.getId();
        if (selected) selectedIds.add(id);
        else selectedIds.remove(id);

        notifyItemChanged(adapterPosition, PAYLOAD_UPDATE_SELECTION);
    }

    

    public void clearSelection() {
        selectedIds.clear();
        notifyItemRangeChanged(1, mItems.size(), PAYLOAD_UPDATE_SELECTION);
    }

    public boolean isItemSelected(int adapterPosition) {
        FontEntity font = getItemAtAdapterPosition(adapterPosition);
        if (font == null) return false;

        long id = font.getId();
        return selectedIds.contains(id);
    }

    public int getSelectedCount() {
        return selectedIds.size();
    }

    // العناصر المحددة بترتيب ظهورها في القائمة
    @NonNull
    public List<FontEntity> getSelectedFonts() {
        List<FontEntity> result = new ArrayList<>(selectedIds.size());
        if (selectedIds.isEmpty()) return result;

        for (FontEntity font : mItems) {
            long id = font.getId();
            if (selectedIds.contains(id)) result.add(font);
        }
        return result;
    }

    public boolean isSelectionMode() {
        return isSelectionMode;
    }

    

    @NonNull
    public List<FontEntity> getAllFonts() {
        return new ArrayList<>(mItems);
    }

    @Nullable
    public FontEntity getItemAtAdapterPosition(int adapterPosition) {
        int itemIndex = adapterPosition - 1;
        if (itemIndex < 0 || itemIndex >= mItems.size()) return null;
        return mItems.get(itemIndex);
    }


    static class TrashHeaderViewHolder extends RecyclerView.ViewHolder {

        private final TextView messageTextView;

        TrashHeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            messageTextView = itemView.findViewById(R.id.trash_header_message);
        }

        void bind() {
            if (messageTextView != null) {
                messageTextView.setText(R.string.trash_items_expiry_info);
            }
        }
    }

    static class TrashItemViewHolder extends RecyclerView.ViewHolder {

        final LinearLayout selectableLayout;
        final TextView fontNameTextView;
        final TextView daysRemainingTextView;

        TrashItemViewHolder(@NonNull View itemView) {
            super(itemView);
            selectableLayout      = itemView.findViewById(R.id.selectable_layout);
            fontNameTextView      = itemView.findViewById(R.id.trash_item_font_name);
            daysRemainingTextView = itemView.findViewById(R.id.trash_item_days_remaining);
        }
    }

    static class SpaceViewHolder extends RecyclerView.ViewHolder {
        SpaceViewHolder(@NonNull View itemView) {
            super(itemView);
            itemView.setFocusable(false);
            itemView.setClickable(false);
        }
    }


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

        @Override
        public boolean areItemsTheSame(int oldPos, int newPos) {
            return oldList.get(oldPos).getId() == newList.get(newPos).getId();
        }

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
