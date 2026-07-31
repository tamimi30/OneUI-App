package com.oneui.fontviewer.fragment.localfont.adapter;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseBooleanArray;
import android.util.TypedValue;
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
import com.oneui.fontviewer.fragment.localfont.data.LocalFontCache;
import com.oneui.fontviewer.fragment.localfont.manager.LocalFontPreferenceManager;
import com.oneui.fontviewer.widget.search.FontTextHighlighter;
import com.oneui.fontviewer.fragment.localfont.adapter.LocalFontViewHolder;
import com.oneui.fontviewer.widget.sort.SortHeaderViewHolder;
import com.oneui.fontviewer.widget.sort.SortByItemLayout;
import com.oneui.fontviewer.metadata.FontWeightWidthExtractor;
import com.oneui.fontviewer.utils.FileUtils;
import com.oneui.fontviewer.fragment.settings.utils.SettingsHelper;
import com.oneui.fontviewer.fragment.localfont.viewmodel.LocalFontListViewModel;

import dev.oneuiproject.oneui.widget.RoundLinearLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;

public class LocalFontListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements SectionIndexer {

    public static final int VIEW_TYPE_HEADER = 0;
    public static final int VIEW_TYPE_FONT   = 1;
    public static final int VIEW_TYPE_SPACE  = 2;

    private static final String PAYLOAD_UPDATE_CORNERS   = "UPDATE_CORNERS";
    private static final String PAYLOAD_UPDATE_HIGHLIGHT = "UPDATE_HIGHLIGHT";

    private static final String PAYLOAD_UPDATE_FAVORITE  = "UPDATE_FAVORITE";

    private static final String PAYLOAD_UPDATE_SELECTION = "UPDATE_SELECTION";

    private static final String PAYLOAD_UPDATE_LAST_OPENED = "UPDATE_LAST_OPENED";

    private final Context context;
    private final LocalFontPreferenceManager preferenceManager;
    private final FontTextHighlighter highlighter;
    private final Handler mainHandler;
    private final ExecutorService executor;
    private RecyclerView recyclerView;

    private boolean mIsFontPreviewEnabled = true;

    private final SortedList<FontFileInfo> mSortedList;

    private HashMap<String, LocalFontListViewModel.FontFileInfoWithMetadata> fontsMetadataMap;
    private String currentSearchQuery;

    private List<String> sections;
    private List<Integer> sectionPositions;
    private List<Integer> positionSections;

    private SortByItemLayout.SortType currentSortType;
    private boolean currentSortAscending;

    private boolean isSelectionMode = false;
    private SparseBooleanArray selectedItems = new SparseBooleanArray();

    private OnFontClickListener fontClickListener;
    private SortByItemLayout.OnSortChangeListener sortChangeListener;
    private OnSelectionListener selectionListener;

    private FavoriteStatusProvider favoriteStatusProvider;

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
        public void onChanged(int position, int count) {
            notifyItemRangeChanged(position + 1, count);
        }
    }

    private int compareItems(FontFileInfo a, FontFileInfo b) {
        if (a == null) return 1;
        if (b == null) return -1;
        int result;
        switch (currentSortType) {
            case DATE:
                result = Long.compare(a.getLastModified(), b.getLastModified());
                break;
            case SIZE:
                result = Long.compare(a.getSize(), b.getSize());
                break;
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

    public interface OnSelectionListener {
        void onStartSelection(int position);
        void onToggleSelection(int position);
    }

    public interface FavoriteStatusProvider {
        boolean isFavorited(String fontPath);
    }

    public LocalFontListAdapter(Context context, ExecutorService executor) {
        this.context = context;
        this.preferenceManager = new LocalFontPreferenceManager(context);
        this.highlighter = new FontTextHighlighter(context);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.executor = executor;

        this.fontsMetadataMap = new HashMap<>();
        this.currentSearchQuery = "";

        this.sections = new ArrayList<>();
        this.sectionPositions = new ArrayList<>();
        this.positionSections = new ArrayList<>();

        this.currentSortType = SortByItemLayout.SortType.NAME;
        this.currentSortAscending = true;

        

        this.mIsFontPreviewEnabled = SettingsHelper.isFontPreviewEnabled(context);

        this.mSortedList = new SortedList<>(
            FontFileInfo.class,
            new FontSortedListCallback()
        );

        setHasStableIds(true); 

        registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                updateListEdges();
            }

            @Override
            public void onItemRangeRemoved(int positionStart, int itemCount) {
                updateListEdges();
            }

            @Override
            public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
                updateListEdges();
            }

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


    public void setFontClickListener(OnFontClickListener listener)            { this.fontClickListener = listener; }
    public void setSortChangeListener(SortByItemLayout.OnSortChangeListener l) { this.sortChangeListener = l; }
    public void setSelectionListener(OnSelectionListener listener)             { this.selectionListener = listener; }

    public void setFavoriteStatusProvider(FavoriteStatusProvider provider) {
        this.favoriteStatusProvider = provider;
    }

    

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
        preferenceManager.saveLastOpenedFont(path);
        smartUpdate();
    }


    public void setSelectionMode(boolean enabled) {
        this.isSelectionMode = enabled;
        if (!enabled) selectedItems.clear();
        notifyItemRangeChanged(0, getItemCount(), PAYLOAD_UPDATE_SELECTION);
    }

    public void setItemSelected(int position, boolean selected) {
        if (selected) selectedItems.put(position, true);
        else selectedItems.delete(position);
        notifyItemChanged(position, PAYLOAD_UPDATE_SELECTION);
    }

    public void clearSelection()                         { selectedItems.clear(); }
    public boolean isItemSelected(int position)          { return selectedItems.get(position, false); }
    public boolean isSelectionMode()                     { return isSelectionMode; }

    public String getFilePath(int position) {
        if (position > 0 && position <= mSortedList.size())
            return mSortedList.get(position - 1).getPath();
        return null;
    }

    public int findPositionByPath(String path) {
        if (path == null) return -1;
        for (int i = 0; i < mSortedList.size(); i++)
            if (path.equals(mSortedList.get(i).getPath())) return i + 1;
        return -1;
    }

    public void notifyFavoriteChanged(String path) {
        int position = findPositionByPath(path);
        if (position != -1) notifyItemChanged(position, PAYLOAD_UPDATE_FAVORITE);
    }

    public void notifyAllFavoritesChanged() {
        int size = mSortedList.size();
        if (size > 0) notifyItemRangeChanged(1, size, PAYLOAD_UPDATE_FAVORITE);
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
                    if (size > 0) {
                        notifyItemRangeChanged(1, size, PAYLOAD_UPDATE_HIGHLIGHT);
                    }
                }
            });
        }
    }

    public void setSortOptions(SortByItemLayout.SortType sortType, boolean ascending) {
        this.currentSortType = sortType;
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
        this.currentSortType = sortType;
        this.currentSortAscending = ascending;
        notifyItemChanged(0);
    }

    public void setAllFontsMetadata(List<LocalFontListViewModel.FontFileInfoWithMetadata> metadata) {
        fontsMetadataMap.clear();
        if (metadata != null)
            for (LocalFontListViewModel.FontFileInfoWithMetadata item : metadata)
                fontsMetadataMap.put(item.getPath(), item);
    }

    public void updateListWithAnimation(List<FontFileInfo> newFonts) {
        updateFilteredFonts(newFonts, currentSearchQuery);
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

    private LocalFontListViewModel.FontFileInfoWithMetadata getFontMetadataForPath(String path) {
        return fontsMetadataMap.get(path);
    }


    @Override
    public int getItemCount() { return mSortedList.size() + 2; }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) return VIEW_TYPE_HEADER;
        if (position == getItemCount() - 1) return VIEW_TYPE_SPACE;
        return VIEW_TYPE_FONT;
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

        return new LocalFontViewHolder(inf.inflate(R.layout.font_list_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof SortHeaderViewHolder) {
            SortHeaderViewHolder h = (SortHeaderViewHolder) holder;
            h.bind(currentSortType, currentSortAscending, sortChangeListener);
            h.setSortEnabled(!isSelectionMode);
        } else if (holder instanceof LocalFontViewHolder) {
            bindLocalFontViewHolder((LocalFontViewHolder) holder, mSortedList.get(position - 1), position);
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
            if ((payloads.contains(PAYLOAD_UPDATE_HIGHLIGHT) || !isSearchActive)
                    && holder instanceof LocalFontViewHolder) {
                FontFileInfo fontInfo = mSortedList.get(position - 1);
                String displayName = FileUtils.removeExtension(fontInfo.getName());

                if (isSearchActive) {
                    android.text.Spannable highlighted = highlighter.highlightText(displayName, currentSearchQuery);
                    ((LocalFontViewHolder) holder).fontNameTextView.setText(highlighted);
                } else {
                CharSequence curr = ((LocalFontViewHolder) holder).fontNameTextView.getText();
                if (curr instanceof android.text.Spanned || !displayName.contentEquals(curr)) {
                    ((LocalFontViewHolder) holder).fontNameTextView.setText(displayName);
                }
            }

                boolean isLastOpened = preferenceManager.isLastOpenedFont(fontInfo.getPath());
                if (isSearchActive) isLastOpened = false; 
                ((LocalFontViewHolder) holder).updateLastOpenedHighlight(isLastOpened);
            }

            if (payloads.contains(PAYLOAD_UPDATE_FAVORITE) && holder instanceof LocalFontViewHolder) {
                FontFileInfo fontInfo = mSortedList.get(position - 1);
                boolean isFavorited = favoriteStatusProvider != null
                        && favoriteStatusProvider.isFavorited(fontInfo.getPath());
                ((LocalFontViewHolder) holder).setFavoriteIndicator(isFavorited, true);
            }

            if (payloads.contains(PAYLOAD_UPDATE_SELECTION)) {
                if (holder instanceof LocalFontViewHolder) {
                    LocalFontViewHolder vh = (LocalFontViewHolder) holder;
                    if (vh.selectableLayout != null) {
                        vh.selectableLayout.setSelectionMode(isSelectionMode);
                        vh.selectableLayout.setSelectedAnimate(isItemSelected(position));
                        
                    }
                } else if (holder instanceof SortHeaderViewHolder) {
                    ((SortHeaderViewHolder) holder).setSortEnabled(!isSelectionMode);
                }
            }

            if (payloads.contains(PAYLOAD_UPDATE_LAST_OPENED) && holder instanceof LocalFontViewHolder) {
                FontFileInfo fontInfo = mSortedList.get(position - 1);
                boolean isLastOpened = preferenceManager.isLastOpenedFont(fontInfo.getPath());
                
                if (isSearchActive) isLastOpened = false;
                
                ((LocalFontViewHolder) holder).updateLastOpenedHighlight(isLastOpened);
            }

        } else {
            super.onBindViewHolder(holder, position, payloads);
        }
    }

    private void updateItemAppearance(RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof SortHeaderViewHolder) return;

        if (holder instanceof LocalFontViewHolder) {
            LocalFontViewHolder fh = (LocalFontViewHolder) holder;
            RoundLinearLayout root = (RoundLinearLayout) fh.itemView;
            int totalFonts         = mSortedList.size();
            boolean isFirst        = (position == 1);
            boolean isLast         = (position == getItemCount() - 2);

            if (totalFonts == 1) {
                root.setRoundedCorners(SeslRoundedCorner.ROUNDED_CORNER_ALL);
                if (fh.dividerView != null) fh.dividerView.setVisibility(View.GONE);
            } else if (isFirst) {
                root.setRoundedCorners(SeslRoundedCorner.ROUNDED_CORNER_TOP_LEFT
                                     | SeslRoundedCorner.ROUNDED_CORNER_TOP_RIGHT);
                if (fh.dividerView != null) fh.dividerView.setVisibility(View.VISIBLE);
            } else if (isLast) {
                root.setRoundedCorners(SeslRoundedCorner.ROUNDED_CORNER_BOTTOM_LEFT
                                     | SeslRoundedCorner.ROUNDED_CORNER_BOTTOM_RIGHT);
                if (fh.dividerView != null) fh.dividerView.setVisibility(View.INVISIBLE);
            } else {
                root.setRoundedCorners(SeslRoundedCorner.ROUNDED_CORNER_NONE);
                if (fh.dividerView != null) fh.dividerView.setVisibility(View.VISIBLE);
            }
        }
    }

    private void bindLocalFontViewHolder(LocalFontViewHolder holder, FontFileInfo fontInfo, int position) {
        String fileName    = fontInfo.getName();
        String path        = fontInfo.getPath();
        String displayName = FileUtils.removeExtension(fileName);

        LocalFontListViewModel.FontFileInfoWithMetadata metadata = getFontMetadataForPath(path);
        String realName = (metadata != null) ? metadata.getRealName() : null;
        

        String weightWidthLabel = (metadata != null) ? metadata.getWeightWidthLabel() : null;

        boolean isSearchActive = currentSearchQuery != null && !currentSearchQuery.isEmpty();
        boolean isLastOpened   = preferenceManager.isLastOpenedFont(path);

        if (isSearchActive) isLastOpened = false;

        holder.bind(displayName, path, isSearchActive, currentSearchQuery,
                    isLastOpened, highlighter, isSelectionMode, isItemSelected(position),
                    weightWidthLabel);

        boolean isFavorited = favoriteStatusProvider != null && favoriteStatusProvider.isFavorited(path);
        holder.setFavoriteIndicator(isFavorited, false);

        if (mIsFontPreviewEnabled) loadFontPreview(holder, path);
        else holder.setDefaultTypeface(null);

        final String finalRealName      = realName;
        final String finalWeightWidth   = weightWidthLabel;

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;
            if (isSelectionMode) {
                if (selectionListener != null) selectionListener.onToggleSelection(pos);
            } else {
                if (fontClickListener != null)
                    fontClickListener.onFontClick(path, finalRealName, fileName, 0, finalWeightWidth);
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
            if (recyclerView != null) recyclerView.seslStartLongPressMultiSelection();
            return true;
        });
    }

    private void loadFontPreview(LocalFontViewHolder holder, String path) {
        LocalFontCache cache = LocalFontCache.getInstance();
        Typeface cached = cache.getIfCached(path);

        if (cached != null) {
            holder.setTypeface(cached);
        } else {
            holder.setDefaultTypeface(null);
            if (executor != null && !executor.isShutdown()) {
                executor.execute(() -> {
                    Typeface loaded = cache.getTypeface(path);
                    if (loaded != null) {
                        mainHandler.post(() -> {
                            if (!SettingsHelper.isFontPreviewEnabled(context)) return;
                            if (path.equals(holder.getTag())) holder.setTypeface(loaded);
                        });
                    }
                });
            }
        }
    }

    @Override
    public long getItemId(int position) {
        if (position == 0) return "HEADER".hashCode();
        if (position == getItemCount() - 1) return "SPACE".hashCode();
        return mSortedList.get(position - 1).getPath().hashCode();
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
