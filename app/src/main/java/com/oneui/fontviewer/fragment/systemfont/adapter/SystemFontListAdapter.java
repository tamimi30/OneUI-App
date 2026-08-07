package com.oneui.fontviewer.fragment.systemfont.adapter;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SectionIndexer;

import androidx.annotation.NonNull;
import androidx.appcompat.util.SeslRoundedCorner;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SortedList;

import com.oneui.fontviewer.R;
import com.oneui.fontviewer.data.entity.FontFileInfo;
import com.oneui.fontviewer.widget.search.FontTextHighlighter;
import com.oneui.fontviewer.widget.sort.SortHeaderViewHolder;
import com.oneui.fontviewer.widget.sort.SortByItemLayout;
import com.oneui.fontviewer.fragment.systemfont.data.SystemFontCache;
import com.oneui.fontviewer.fragment.systemfont.data.SystemFontInfo;
import com.oneui.fontviewer.fragment.systemfont.manager.SystemFontPreferenceManager;
import com.oneui.fontviewer.utils.FileUtils;
import com.oneui.fontviewer.fragment.settings.utils.SettingsHelper;

import dev.oneuiproject.oneui.widget.RoundLinearLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class SystemFontListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements SectionIndexer {

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_FONT   = 1;
    private static final int VIEW_TYPE_SPACE  = 2;

    private static final String PAYLOAD_UPDATE_CORNERS    = "UPDATE_CORNERS";
    private static final String PAYLOAD_UPDATE_HIGHLIGHT  = "UPDATE_HIGHLIGHT";
    private static final String PAYLOAD_UPDATE_LAST_OPENED = "UPDATE_LAST_OPENED";

    private final Context context;
    private final SystemFontPreferenceManager preferenceManager;
    private final FontTextHighlighter highlighter;
    private final Handler mainHandler;
    private final ExecutorService executor;

    private RecyclerView recyclerView;

    private boolean mIsFontPreviewEnabled = true;

    private String mCurrentLastOpenedPath = null;

    private final SortedList<FontFileInfo> mSortedList;

    private List<SystemFontInfo> allFontsInfo;
    private String currentSearchQuery;

    private List<String> sections;
    private List<Integer> sectionPositions;
    private List<Integer> positionSections;

    private SortByItemLayout.SortType currentSortType;
    private boolean currentSortAscending;

    private OnFontClickListener fontClickListener;
    private SortByItemLayout.OnSortChangeListener sortChangeListener;

    private class FontSortedListCallback extends SortedList.Callback<FontFileInfo> {

        @Override
        public int compare(FontFileInfo a, FontFileInfo b) {
            return compareItems(a, b);
        }

        @Override
        public boolean areItemsTheSame(FontFileInfo a, FontFileInfo b) {
            return a.getPath().equals(b.getPath());
        }

        @Override
        public boolean areContentsTheSame(FontFileInfo a, FontFileInfo b) {
            return a.getName().equals(b.getName()) &&
                   a.getSize() == b.getSize() &&
                   a.getLastModified() == b.getLastModified();
        }

        @Override public void onInserted(int position, int count) { notifyItemRangeInserted(position + 1, count); }
        @Override public void onRemoved(int position, int count)  { notifyItemRangeRemoved(position + 1, count); }
        @Override public void onMoved(int from, int to)           { notifyItemMoved(from + 1, to + 1); }
        @Override public void onChanged(int position, int count)  { notifyItemRangeChanged(position + 1, count); }
    }

    private int compareItems(FontFileInfo a, FontFileInfo b) {
        if (a == null) return 1;
        if (b == null) return -1;
        int result;
        switch (currentSortType) {
            case DATE:  result = Long.compare(a.getLastModified(), b.getLastModified()); break;
            case SIZE:  result = Long.compare(a.getSize(), b.getSize()); break;
            case NAME:
            default:
                String nameA = a.getName() != null ? a.getName() : "";
                String nameB = b.getName() != null ? b.getName() : "";
                result = nameA.compareToIgnoreCase(nameB);
        }
        return currentSortAscending ? result : -result;
    }

    public static class SpaceViewHolder extends RecyclerView.ViewHolder {
        public SpaceViewHolder(@NonNull View itemView) {
            super(itemView);
            itemView.setFocusable(false);
            itemView.setClickable(false);
        }
    }

    public interface OnFontClickListener {
        void onFontClick(String fontPath, String realName, String fileName,
                         int ttcIndex, String weightWidthLabel);
    }

    public SystemFontListAdapter(Context context, ExecutorService executor) {
        this.context           = context;
        this.preferenceManager = new SystemFontPreferenceManager(context);
        this.highlighter       = new FontTextHighlighter(context);
        this.mainHandler       = new Handler(Looper.getMainLooper());
        this.executor          = executor;

        this.mCurrentLastOpenedPath = preferenceManager.getLastOpenedFont();

        this.allFontsInfo       = new ArrayList<>();
        this.currentSearchQuery = "";

        this.sections         = new ArrayList<>();
        this.sectionPositions = new ArrayList<>();
        this.positionSections = new ArrayList<>();

        this.currentSortType      = SortByItemLayout.SortType.NAME;
        this.currentSortAscending = true;

        

        this.mIsFontPreviewEnabled = SettingsHelper.isFontPreviewEnabled(context);

        this.mSortedList = new SortedList<>(
            FontFileInfo.class,
            new FontSortedListCallback()
        );

        setHasStableIds(true);

        registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override public void onItemRangeInserted(int p, int c) { updateListEdges(); }
            @Override public void onItemRangeRemoved(int p, int c)  { updateListEdges(); }
            @Override public void onItemRangeMoved(int f, int t, int c) { updateListEdges(); }

            private void updateListEdges() {
                if (recyclerView == null) return;

                recyclerView.post(() -> {
                    if (recyclerView == null || recyclerView.isComputingLayout()) return;
                    int total = getItemCount();
                    if (total > 0) {
                        notifyItemRangeChanged(0, total, PAYLOAD_UPDATE_CORNERS);
                    }
                });
            }
        });
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

    public void setFontClickListener(OnFontClickListener l)                    { this.fontClickListener = l; }
    public void setSortChangeListener(SortByItemLayout.OnSortChangeListener l) { this.sortChangeListener = l; }

    

    public void setFontPreviewEnabled(boolean enabled) {
        if (this.mIsFontPreviewEnabled != enabled) {
            this.mIsFontPreviewEnabled = enabled;
            if (recyclerView != null && !recyclerView.isComputingLayout()) {
                notifyItemRangeChanged(1, mSortedList.size());
            } else {
                notifyDataSetChanged();
            }
        }
    }

    public void saveLastOpenedAndUpdate(String path) {
        String prevPath = mCurrentLastOpenedPath;
        mCurrentLastOpenedPath = path;
        preferenceManager.saveLastOpenedFont(path);

        notifyLastOpenedChanged(prevPath, path);
    }

    private void notifyLastOpenedChanged(String prevPath, String newPath) {
        if (recyclerView == null) return;
        recyclerView.post(() -> {
            if (recyclerView == null || recyclerView.isComputingLayout()) return;
            int size = mSortedList.size();
            for (int i = 0; i < size; i++) {
                String p = mSortedList.get(i).getPath();
                if ((newPath != null && p.equals(newPath))
                        || (prevPath != null && p.equals(prevPath))) {
                    notifyItemChanged(i + 1, PAYLOAD_UPDATE_LAST_OPENED);
                }
            }
        });
    }

    public void setAllFontsInfo(List<SystemFontInfo> fontsInfo) {
        this.allFontsInfo = fontsInfo != null ? new ArrayList<>(fontsInfo) : new ArrayList<>();
    }

    public void updateFilteredFonts(List<FontFileInfo> fonts, String searchQuery) {
        String oldQuery = this.currentSearchQuery;
        this.currentSearchQuery = searchQuery != null ? searchQuery : "";
        List<FontFileInfo> newList = fonts != null ? fonts : new ArrayList<>();

        mSortedList.replaceAll(newList);
        buildSections();

        if (!this.currentSearchQuery.equals(oldQuery) && recyclerView != null) {
            recyclerView.post(() -> {
                if (recyclerView != null && !recyclerView.isComputingLayout()) {
                    int size = mSortedList.size();
                    if (size > 0) notifyItemRangeChanged(1, size, PAYLOAD_UPDATE_HIGHLIGHT);
                }
            });
        }
    }

    public void setSortOptions(SortByItemLayout.SortType sortType, boolean ascending) {
        this.currentSortType      = sortType;
        this.currentSortAscending = ascending;

        final int size = mSortedList.size();
        List<FontFileInfo> snapshot = new ArrayList<>(size);
        for (int i = 0; i < size; i++) snapshot.add(mSortedList.get(i));

        mSortedList.replaceAll(snapshot);
        buildSections();

        if (recyclerView != null) {
            recyclerView.post(() -> {
                if (recyclerView != null && !recyclerView.isComputingLayout()) {
                    notifyItemChanged(0);
                }
            });
        }
    }

    public void updateSortOptionsOnly(SortByItemLayout.SortType sortType, boolean ascending) {
        this.currentSortType      = sortType;
        this.currentSortAscending = ascending;
        notifyItemChanged(0);
    }

    public void smartUpdate() {
        buildSections();
        int size = mSortedList.size();
        if (size > 0) {
            if (recyclerView != null) {
                recyclerView.post(() -> {
                    if (recyclerView != null && !recyclerView.isComputingLayout()) {
                        notifyItemRangeChanged(1, size, PAYLOAD_UPDATE_LAST_OPENED);
                        notifyItemRangeChanged(1, size, PAYLOAD_UPDATE_HIGHLIGHT);
                    }
                });
            } else {
                notifyItemRangeChanged(1, size, PAYLOAD_UPDATE_LAST_OPENED);
                notifyItemRangeChanged(1, size, PAYLOAD_UPDATE_HIGHLIGHT);
            }
        } else {
            notifyDataSetChanged();
        }
    }

    private void buildSections() {
        sections.clear();
        sectionPositions.clear();
        positionSections.clear();

        for (int i = 0; i < mSortedList.size(); i++) {
            String name   = mSortedList.get(i).getName();
            String letter = (name != null && !name.isEmpty()) ? name.substring(0, 1).toUpperCase() : "#";
            if (!Character.isLetter(letter.charAt(0))) letter = "#";

            if (sections.isEmpty() || !sections.get(sections.size() - 1).equals(letter)) {
                sections.add(letter);
                sectionPositions.add(i + 1);
            }
            positionSections.add(sections.size() - 1);
        }
    }

    private SystemFontInfo getFontInfoForPath(String path) {
        for (SystemFontInfo font : allFontsInfo)
            if (font.getPath().equals(path)) return font;
        return null;
    }

    @Override public int getItemCount() { return mSortedList.size() + 2; }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) return VIEW_TYPE_HEADER;
        if (position == getItemCount() - 1) return VIEW_TYPE_SPACE;
        return VIEW_TYPE_FONT;
    }

    @Override
    public long getItemId(int position) {
        if (position == 0) return "HEADER".hashCode();
        if (position == getItemCount() - 1) return "SPACE".hashCode();
        return mSortedList.get(position - 1).getPath().hashCode();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(context);

        if (viewType == VIEW_TYPE_HEADER) {
            return new SortHeaderViewHolder(inf.inflate(R.layout.sort_header_item, parent, false));
        }

        if (viewType == VIEW_TYPE_SPACE) {
            return new SpaceViewHolder(inf.inflate(R.layout.item_bottom_space, parent, false));
        }

        return new SystemFontViewHolder(inf.inflate(R.layout.font_list_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof SortHeaderViewHolder) {
            ((SortHeaderViewHolder) holder).bind(currentSortType, currentSortAscending, sortChangeListener);
        } else if (holder instanceof SystemFontViewHolder) {
            bindFontViewHolder((SystemFontViewHolder) holder, mSortedList.get(position - 1));
        }
        updateItemAppearance(holder, position);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position,
                                 @NonNull List<Object> payloads) {
        if (!payloads.isEmpty()) {
            if (payloads.contains(PAYLOAD_UPDATE_CORNERS)) {
                updateItemAppearance(holder, position);
            }

            boolean isSearchActive = currentSearchQuery != null && !currentSearchQuery.isEmpty();
            if ((payloads.contains(PAYLOAD_UPDATE_HIGHLIGHT) || !isSearchActive) && holder instanceof SystemFontViewHolder) {
                FontFileInfo fontInfo      = mSortedList.get(position - 1);
                String displayName         = FileUtils.removeExtension(fontInfo.getName());
                boolean isLastOpened       = preferenceManager.isLastOpenedFont(fontInfo.getPath());
                
                if (isSearchActive) isLastOpened = false;
                
                SystemFontInfo sfi         = getFontInfoForPath(fontInfo.getPath());
                String weightWidthLabel    = (sfi != null) ? sfi.getWeightWidthLabel() : null;
                ((SystemFontViewHolder) holder).bind(
                    displayName, fontInfo.getPath(), isSearchActive,
                    currentSearchQuery, isLastOpened, highlighter, weightWidthLabel
                );
            }

            if (payloads.contains(PAYLOAD_UPDATE_LAST_OPENED) && holder instanceof SystemFontViewHolder) {
                FontFileInfo fontInfo = mSortedList.get(position - 1);
                boolean isLastOpened  = preferenceManager.isLastOpenedFont(fontInfo.getPath());
                
                if (isSearchActive) isLastOpened = false;
                
                ((SystemFontViewHolder) holder).bindLastOpened(isLastOpened);
            }
        } else {
            super.onBindViewHolder(holder, position, payloads);
        }
    }

    private void updateItemAppearance(RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof SortHeaderViewHolder) return;

        if (holder instanceof SystemFontViewHolder) {
            SystemFontViewHolder sfh = (SystemFontViewHolder) holder;
            RoundLinearLayout root   = (RoundLinearLayout) sfh.itemView;
            int totalFonts           = mSortedList.size();
            boolean isFirst          = (position == 1);
            boolean isLast           = (position == getItemCount() - 2);

            if (totalFonts == 1) {
                root.setRoundedCorners(SeslRoundedCorner.ROUNDED_CORNER_ALL);
                if (sfh.dividerView != null) sfh.dividerView.setVisibility(View.GONE);
            } else if (isFirst) {
                root.setRoundedCorners(SeslRoundedCorner.ROUNDED_CORNER_TOP_LEFT
                                     | SeslRoundedCorner.ROUNDED_CORNER_TOP_RIGHT);
                if (sfh.dividerView != null) sfh.dividerView.setVisibility(View.VISIBLE);
            } else if (isLast) {
                root.setRoundedCorners(SeslRoundedCorner.ROUNDED_CORNER_BOTTOM_LEFT
                                     | SeslRoundedCorner.ROUNDED_CORNER_BOTTOM_RIGHT);
                if (sfh.dividerView != null) sfh.dividerView.setVisibility(View.INVISIBLE);
            } else {
                root.setRoundedCorners(SeslRoundedCorner.ROUNDED_CORNER_NONE);
                if (sfh.dividerView != null) sfh.dividerView.setVisibility(View.VISIBLE);
            }
        }
    }

    private void bindFontViewHolder(SystemFontViewHolder holder, FontFileInfo fontInfo) {
        String fileName    = fontInfo.getName();
        String path        = fontInfo.getPath();
        String displayName = FileUtils.removeExtension(fileName);

        SystemFontInfo sfi = getFontInfoForPath(path);
        String realName    = (sfi != null) ? sfi.getRealName() : null;
        int ttcIndex       = (sfi != null) ? sfi.getTtcIndex() : 0;

       

        String weightWidthLabel = (sfi != null) ? sfi.getWeightWidthLabel() : null;

        boolean isSearchActive = currentSearchQuery != null && !currentSearchQuery.isEmpty();
        boolean isLastOpened   = preferenceManager.isLastOpenedFont(path);

        if (isSearchActive) isLastOpened = false;

        holder.bind(displayName, path, isSearchActive, currentSearchQuery,
                    isLastOpened, highlighter, weightWidthLabel);

        if (mIsFontPreviewEnabled) loadFontPreview(holder, path);
        else holder.setDefaultTypeface(null);

        final String finalRealName    = realName;
        final int    finalTtcIndex    = ttcIndex;
        final String finalWeightWidth = weightWidthLabel;

        holder.setOnClickListener(v -> {
            if (fontClickListener != null)
                fontClickListener.onFontClick(path, finalRealName, fileName, finalTtcIndex, finalWeightWidth);
        });
    }

    private void loadFontPreview(SystemFontViewHolder holder, String path) {
        SystemFontCache cache = SystemFontCache.getInstance();
        Typeface cached       = cache.getIfCached(path);

        if (cached != null) {
            holder.setTypeface(cached);
        } else {
            holder.setDefaultTypeface(null);
            if (executor != null && !executor.isShutdown()) {
                executor.execute(() -> {
                    Typeface loaded = cache.getTypeface(path);
                    if (loaded != null) {
                        mainHandler.post(() -> {
                            if (path.equals(holder.getTag())) holder.setTypeface(loaded);
                        });
                    }
                });
            }
        }
    }

    @Override public Object[] getSections() { return sections.toArray(); }

    @Override
    public int getPositionForSection(int sectionIndex) {
        if (sectionIndex < 0 || sectionIndex >= sectionPositions.size()) return 0;
        return sectionPositions.get(sectionIndex);
    }

    @Override
    public int getSectionForPosition(int position) {
        if (position <= 0) return 0;
        if (position >= getItemCount() - 1)
            return positionSections.isEmpty() ? 0 : positionSections.get(positionSections.size() - 1);
        int adj = position - 1;
        if (adj < 0 || adj >= positionSections.size()) return 0;
        return positionSections.get(adj);
    }
}
