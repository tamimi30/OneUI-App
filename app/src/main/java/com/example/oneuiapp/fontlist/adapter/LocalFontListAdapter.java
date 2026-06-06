package com.example.oneuiapp.fontlist.adapter;

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

import com.example.oneuiapp.R;
import com.example.oneuiapp.fontlist.FontFileInfo;
import com.example.oneuiapp.fontlist.localfont.LocalFontCache;
import com.example.oneuiapp.fontlist.localfont.LocalFontPreferenceManager;
import com.example.oneuiapp.fontlist.search.FontTextHighlighter;
import com.example.oneuiapp.fontlist.viewholder.LocalFontViewHolder;
import com.example.oneuiapp.fontlist.viewholder.SortHeaderViewHolder;
import com.example.oneuiapp.metadata.FontWeightWidthExtractor;
import com.example.oneuiapp.utils.ExactLineHeightSpan;
import com.example.oneuiapp.utils.FileUtils;
import com.example.oneuiapp.utils.SettingsHelper;
import com.example.oneuiapp.viewmodel.LocalFontListViewModel;
import com.example.oneuiapp.ui.widget.SortByItemLayout;

import dev.oneuiproject.oneui.widget.RoundLinearLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * LocalFontListAdapter — مبني على SortedList لأنيميشن الفرز الانسيابي
 * ★ SortedList يتولى الترتيب وتوليد onMoved/onInserted/onRemoved تلقائياً ★
 * ★ AdapterDataObserver يصحح زوايا OneUI عند كل تغيير ★
 * ★ isTransparentTheme يُعطّل حسابات الزوايا والفواصل غير الضرورية لتوفير المعالجة ★
 *
 * ★ التعديل: تمرير weightWidthLabel من FontFileInfoWithMetadata إلى LocalFontViewHolder ★
 * ★ التعديل: إضافة weightWidthLabel كمعامل خامس في OnFontClickListener
 *   لتمريره إلى NavManager ثم FontViewerFragment دون إعادة استخراجه ★
 *
 * ★ التعديل: إضافة FavoriteStatusProvider لفحص حالة المفضلة وعرض أيقونة ic_favorite
 *   بجانب العناصر المفضلة في قائمة الخطوط المحلية ★
 *
 * ★ الإصلاح (المشكلة 1 و 4): إضافة PAYLOAD_UPDATE_SELECTION لتحديث حالة الـ CheckBox
 *   فقط دون إعادة رسم العنصر كاملاً، مما يمنع وميض النجمة ويُبقي أنيميشن RTL سليماً ★
 *
 * ★ الإصلاح الجوهري (وميض النجمة عند العودة من عارض الخطوط):
 *   إضافة PAYLOAD_UPDATE_LAST_OPENED واستخدامه في smartUpdate() و saveLastOpenedAndUpdate().
 *
 *   السبب الجذري للوميض: كانت smartUpdate() تستدعي notifyItemRangeChanged(1, size) بدون payload،
 *   مما يُشغّل onBindViewHolder كاملاً. خلال هذا الربط، كانت bind() ذات 9 معاملات تُحيل
 *   إلى bind() الكاملة بـ isFavorite=false → setFavoriteIndicator(false) → نجمة مخفية →
 *   ثم setFavoriteIndicator(true) بعدها. إذا كان ItemAnimator نشطاً (قبل إبطاله في
 *   onHiddenChanged أو بعد إعادة تفعيله بعد 100ms)، كان يلتقط الحالة الوسيطة → وميض.
 *
 *   الحل: PAYLOAD_UPDATE_LAST_OPENED يُحدِّث لون نص اسم الخط فقط عبر
 *   updateLastOpenedHighlight() في LocalFontViewHolder، دون أي مساس بأيقونة النجمة.
 *   لا حالة وسيطة → لا وميض. ★
 *
 * ★ الإصلاح (الخطوة الرابعة من إصلاح مشكلة السكرول):
 *   تحديث areContentsTheSame في FontSortedListCallback ليشمل الاسم والحجم وتاريخ التعديل،
 *   لضمان عدم إعادة رسم العناصر بشكل عشوائي عند وصول تحديثات من LiveData. ★
 *
 * ملاحظة للمطوّر: بعد تحديث هذه الواجهة، يجب تحديث LocalFontListFragment ليعكس
 * التغيير في تنفيذه لـ onFontClick وليمرر weightWidthLabel إلى onFontSelected.
 */
public class LocalFontListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements SectionIndexer {

    public static final int VIEW_TYPE_HEADER = 0;
    public static final int VIEW_TYPE_FONT   = 1;
    public static final int VIEW_TYPE_SPACE  = 2;

    private static final String PAYLOAD_UPDATE_CORNERS   = "UPDATE_CORNERS";
    private static final String PAYLOAD_UPDATE_HIGHLIGHT = "UPDATE_HIGHLIGHT";

    // ★ Payload خاص بتحديث أيقونة المفضلة فقط دون إعادة رسم العنصر كاملاً ★
    private static final String PAYLOAD_UPDATE_FAVORITE  = "UPDATE_FAVORITE";

    // ★ الإصلاح (المشكلة 1 و 4): Payload خاص بتحديث حالة الـ CheckBox فقط ★
    // استخدامه بدلاً من notifyItemRangeChanged() العادية يمنع إعادة رسم العنصر كاملاً،
    // مما يحفظ أنيميشن الانتقال في RTL (العربية) ويمنع وميض أيقونة النجمة
    private static final String PAYLOAD_UPDATE_SELECTION = "UPDATE_SELECTION";

    // ★ الإصلاح الجوهري (وميض النجمة): Payload خاص بتحديث لون اسم الخط فقط ★
    // يُستخدم في smartUpdate() و saveLastOpenedAndUpdate() بدلاً من notifyItemRangeChanged()
    // بدون payload. يُحدِّث isLastOpened عبر updateLastOpenedHighlight() في LocalFontViewHolder
    // دون أي مساس بأيقونة النجمة → لا حالة وسيطة → لا وميض.
    private static final String PAYLOAD_UPDATE_LAST_OPENED = "UPDATE_LAST_OPENED";

    private final Context context;
    private final LocalFontPreferenceManager preferenceManager;
    private final FontTextHighlighter highlighter;
    private final Handler mainHandler;
    private final ExecutorService executor;
    private RecyclerView recyclerView;

    // ★ حالة الثيم الشفاف — تُقرأ مرة واحدة عند إنشاء الـ Adapter
    //   تُحدّد أي Layout يُستخدم لعناصر الخطوط وتُعطّل حسابات الزوايا والفواصل غير المطلوبة ★
    private final boolean isTransparentTheme;

    // ★ يمنع تشغيل نقرتين متزامنتين قبل اكتمال الانتقال الأول ★
    private boolean mClickGuard = false;

    // ★ الإصلاح (مشكلة السكرول): إعداد معاينة الخط يُقرأ مرة واحدة فقط عند إنشاء الأدابتر
    //   بدلاً من قراءته من DataStore لكل عنصر أثناء السكرول، مما يُسبب التقطيع.
    //   يُحدَّث عبر setFontPreviewEnabled() عند تغيير الإعداد من الـ Fragment. ★
    private boolean mIsFontPreviewEnabled = true;

    // ★ SortedList يحل محل List العادية — يرتب ويُنيم تلقائياً ★
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

    // ★ مزوّد حالة المفضلة — يُستدعى عند ربط كل عنصر لتحديد ظهور أيقونة ic_favorite ★
    // يجب على Fragment تطبيق هذه الواجهة وتمريرها عبر setFavoriteStatusProvider()
    private FavoriteStatusProvider favoriteStatusProvider;

    // ─────────────────────────────────────────────────────────
    // ★ SortedList.Callback — يُترجم أحداث SortedList إلى إشعارات الـ Adapter ★
    // الإزاحة +1 ضرورية لأن position=0 محجوزة للـ Header
    // ─────────────────────────────────────────────────────────
    private class FontSortedListCallback extends SortedList.Callback<FontFileInfo> {

        @Override
        public int compare(FontFileInfo a, FontFileInfo b) {
            return compareItems(a, b);
        }

        @Override
        public boolean areItemsTheSame(FontFileInfo a, FontFileInfo b) {
            return a.getPath().equals(b.getPath());
        }

        /**
         * ★ الخطوة الرابعة من إصلاح مشكلة السكرول ★
         *
         * إضافة التحقق من تاريخ التعديل (getLastModified) إلى جانب الاسم والحجم.
         * هذا يضمن عدم إعادة رسم العنصر عشوائياً عند وصول تحديثات من LiveData
         * لا تتعلق بمحتوى الخط الفعلي (مثل تغيير is_cached أو last_access_time)،
         * وهو ما كان يُسبب Over-emission Death Loop قبل تطبيق الإصلاح.
         *
         * ملاحظة: بعد تطبيق الخطوتين الأولى والثانية (تعطيل الكتابة في DB أثناء التمرير
         * وتسجيل الاستخدام عند النقر فقط)، لن تعود هناك تحديثات متكررة أصلاً، لكن
         * هذا الإصلاح يُضيف طبقة حماية إضافية لمنع إعادة الرسم غير الضرورية.
         */
        @Override
        public boolean areContentsTheSame(FontFileInfo a, FontFileInfo b) {
            // يكفي التحقق من الاسم والحجم وتاريخ التعديل لضمان عدم إعادة الرسم العشوائي
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
            // ★ هذا هو قلب أنيميشن الفرز — يُحرّك العنصر من موقعه إلى موقعه الجديد ★
            notifyItemMoved(fromPosition + 1, toPosition + 1);
        }

        @Override
        public void onChanged(int position, int count) {
            notifyItemRangeChanged(position + 1, count);
        }
    }

    // ─────────────────────────────────────────────────────────
    // دالة المقارنة — تقرأ currentSortType و currentSortAscending مباشرة
    // ─────────────────────────────────────────────────────────
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

    // ─────────────────────────────────────────────────────────
    // ★ ViewHolder للفراغ السفلي الوهمي ★
    // ─────────────────────────────────────────────────────────
    public static class SpaceViewHolder extends RecyclerView.ViewHolder {
        public SpaceViewHolder(@NonNull View itemView) {
            super(itemView);
            itemView.setFocusable(false);
            itemView.setClickable(false);
        }
    }

    /**
     * ★ التعديل: إضافة weightWidthLabel كمعامل خامس ★
     * يحمل وصف الوزن والعرض الجاهز من القائمة (مثل "Bold, Condensed" أو "VF · Regular")
     * مما يُغني عن إعادة استخراجه عند فتح شاشة العارض.
     *
     * تحديث مطلوب في LocalFontListFragment:
     *   void onFontClick(String fontPath, String realName, String fileName,
     *                    int ttcIndex, String weightWidthLabel) {
     *       listener.onFontSelected(fontPath, realName, fileName, ttcIndex, weightWidthLabel);
     *   }
     */
    public interface OnFontClickListener {
        void onFontClick(String fontPath, String realName, String fileName,
                         int ttcIndex, String weightWidthLabel);
    }

    public interface OnSelectionListener {
        void onStartSelection(int position);
        void onToggleSelection(int position);
    }

    /**
     * ★ واجهة مزوّد حالة المفضلة ★
     * يُنفّذها Fragment لتزويد الـ Adapter بحالة المفضلة لكل مسار خط،
     * مما يُتيح عرض أيقونة ic_favorite بجانب العناصر المفضلة.
     *
     * ملاحظة للمطوّر: يجب على LocalFontListFragment تطبيق هذه الواجهة
     * واستدعاء setFavoriteStatusProvider(this) بعد إنشاء الـ Adapter.
     */
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

        // ★ قراءة حالة الثيم الشفاف مرة واحدة عند الإنشاء
        //   إذا كان مفعّلاً سيتم استخدام layouts الـ MaterialCardView لعناصر الخطوط
        //   وتعطيل حسابات الزوايا الدائرية لتوفير معالجة الـ CPU ★
        this.isTransparentTheme = SettingsHelper.isTransparentThemeEnabled(context);

        // ★ الإصلاح (مشكلة السكرول): اقرأ الإعداد مرة واحدة عند إنشاء الأدابتر
        //   بدلاً من قراءته من DataStore (عملية I/O) لكل عنصر أثناء السكرول ★
        this.mIsFontPreviewEnabled = SettingsHelper.isFontPreviewEnabled(context);

        this.mSortedList = new SortedList<>(
            FontFileInfo.class,
            new FontSortedListCallback()
        );

        setHasStableIds(true); // ★ يضمن تتبع هوية العناصر لمنع إعادة رسمها عشوائياً ★

        // ★★★ المراقب: يراقب كل تغيير ويصحح الزوايا فوراً ★★★
        // ★ في الثيم الشفاف: لا حاجة لهذه الحسابات فيُتجاهل التنفيذ تماماً ★
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
                // ★ توفير معالجة: في الثيم الشفاف لا زوايا دائرية للحساب ★
                if (isTransparentTheme || recyclerView == null) return;

                // ★ تأجيل التحديث حتى ينتهي RecyclerView من حسابات الأنيميشن ★
                recyclerView.post(() -> {
                    if (recyclerView == null || recyclerView.isComputingLayout()) return;
                    int total = getItemCount();
                    if (total > 0) {
                        // ★ الإصلاح (المشكلة 1): تحديث زوايا جميع العناصر عبر Payload ★
                        // هذه العملية خفيفة جداً وتضمن إزالة الزوايا الدائرية عن العنصر
                        // الذي كان أخيراً في نتائج البحث وأصبح في منتصف القائمة الكاملة.
                        // السبب: العنصر الوسطي لا يصله PAYLOAD_UPDATE_CORNERS في المنطق
                        // القديم (الذي يُحدّث الأوّل والأخيرين فقط)، فيحتفظ بزواياه الدائرية
                        // حتى يُجبره السكرول على إعادة الرسم. notifyItemRangeChanged هنا
                        // خفيفة لأنها تمرّ فقط على updateItemAppearance دون Full Bind. ★
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

    // ─────────────────────────────────────────────────────────
    // Setters
    // ─────────────────────────────────────────────────────────

    public void setFontClickListener(OnFontClickListener listener)            { this.fontClickListener = listener; }
    public void setSortChangeListener(SortByItemLayout.OnSortChangeListener l) { this.sortChangeListener = l; }
    public void setSelectionListener(OnSelectionListener listener)             { this.selectionListener = listener; }

    // ★ يُستدعى من Fragment لتزويد الـ Adapter بمزوّد حالة المفضلة ★
    public void setFavoriteStatusProvider(FavoriteStatusProvider provider) {
        this.favoriteStatusProvider = provider;
    }

    // ★ يُستدعى من saveLastOpenedAndUpdate (انتقال مؤكد) أو من unblockTouch (إلغاء) ★
    public void resetClickGuard() { mClickGuard = false; }

    /**
     * ★ الإصلاح (مشكلة السكرول): تحديث متغير معاينة الخط عند تغيير الإعداد من الـ Fragment ★
     *
     * يُستدعى من Observer في LocalFontListFragment عوضاً عن smartUpdate()،
     * فيُحدِّث المتغير مرة واحدة ثم يُحدِّث القائمة لتعكس التغيير.
     * هذا يمنع قراءة DataStore لكل عنصر أثناء السكرول التي كانت تُسبب التقطيع.
     *
     * @param enabled true إذا كانت معاينة الخط مفعّلة
     */
    public void setFontPreviewEnabled(boolean enabled) {
        if (this.mIsFontPreviewEnabled != enabled) {
            this.mIsFontPreviewEnabled = enabled;
            smartUpdate(); // تحديث القائمة لتعكس التغيير
        }
    }

    public void saveLastOpenedAndUpdate(String path) {
        mClickGuard = false;
        preferenceManager.saveLastOpenedFont(path);
        // ★ الإصلاح: smartUpdate() تستخدم الآن PAYLOAD_UPDATE_LAST_OPENED ★
        // لذا يكفي استدعاؤها مباشرةً — لون نص اسم الخط يُحدَّث بصمت دون وميض النجمة
        smartUpdate();
    }

    // ─────────────────────────────────────────────────────────
    // دوال التحديد المتعدد
    // ─────────────────────────────────────────────────────────

    /**
     * ★ الإصلاح (المشكلة 1 و 4): استخدام PAYLOAD_UPDATE_SELECTION بدلاً من إعادة الرسم الكامل ★
     * notifyItemRangeChanged() بدون Payload كانت تُعيد رسم كل عنصر من الصفر مما:
     *   - يقتل أنيميشن الانتقال في RTL (العربية)
     *   - يسبب وميض أيقونة النجمة للعناصر المفضلة
     * مع PAYLOAD_UPDATE_SELECTION يُحدَّث الـ CheckBox فقط دون المساس بباقي العنصر.
     */
    public void setSelectionMode(boolean enabled) {
        this.isSelectionMode = enabled;
        if (!enabled) selectedItems.clear();
        // ★ الإصلاح: Payload يُحدّث الـ CheckBox فقط دون إعادة رسم العنصر كاملاً ★
        notifyItemRangeChanged(0, getItemCount(), PAYLOAD_UPDATE_SELECTION);
    }

    /**
     * ★ الإصلاح (المشكلة 1): استخدام PAYLOAD_UPDATE_SELECTION لتحديث عنصر واحد بصمت ★
     */
    public void setItemSelected(int position, boolean selected) {
        if (selected) selectedItems.put(position, true);
        else selectedItems.delete(position);
        // ★ الإصلاح: Payload يُحدّث الـ CheckBox فقط دون إعادة رسم العنصر كاملاً ★
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

    /**
     * ★ تحديث أيقونة المفضلة لعنصر محدد عبر Payload لتفادي إعادة رسم العنصر كاملاً ★
     * يُستدعى من Fragment بعد تغيير حالة مفضلة خط معين.
     */
    public void notifyFavoriteChanged(String path) {
        int position = findPositionByPath(path);
        if (position != -1) notifyItemChanged(position, PAYLOAD_UPDATE_FAVORITE);
    }

    /**
     * ★ تحديث أيقونة المفضلة لجميع العناصر دفعةً واحدة عبر Payload ★
     * يُستدعى من Fragment بعد عمليات المفضلة الجماعية (مثل حذف متعدد).
     */
    public void notifyAllFavoritesChanged() {
        int size = mSortedList.size();
        if (size > 0) notifyItemRangeChanged(1, size, PAYLOAD_UPDATE_FAVORITE);
    }

    // ─────────────────────────────────────────────────────────
    // ★ تحديث البيانات — SortedList.replaceAll يحسب الفرق ويولد الأنيميشن ★
    // ─────────────────────────────────────────────────────────
    public void updateFilteredFonts(List<FontFileInfo> fonts, String searchQuery) {
        String oldQuery = this.currentSearchQuery;
        this.currentSearchQuery = searchQuery != null ? searchQuery : "";
        List<FontFileInfo> newList = fonts != null ? fonts : new ArrayList<>();

        // 1. تطبيق الفلتر: SortedList يُخفي/يُحرّك العناصر تلقائياً بأنيميشن
        mSortedList.replaceAll(newList);
        buildSections();

        // 2. ★ تحديث تظليل النص للعناصر المتبقية بصمت عبر Payload دون إعادة رسمها ★
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

    /**
     * ★ الدالة الجوهرية لأنيميشن الفرز ★
     * عند تغيير معيار الفرز: تلتقط snapshot من العناصر الحالية، تحدّث حقول الفرز،
     * ثم تُعيد الإدراج. SortedList يستخدم areItemsTheSame() لاكتشاف أن العناصر
     * ذاتها تحركت فيولّد استدعاءات onMoved() → أنيميشن انزلاق حقيقي.
     */
    public void setSortOptions(SortByItemLayout.SortType sortType, boolean ascending) {
        this.currentSortType = sortType;
        this.currentSortAscending = ascending;

        // التقاط snapshot قبل إعادة الفرز
        final int size = mSortedList.size();
        List<FontFileInfo> snapshot = new ArrayList<>(size);
        for (int i = 0; i < size; i++) snapshot.add(mSortedList.get(i));

        // replaceAll تستخدم الـ Comparator الجديد (الذي يقرأ الحقول المحدّثة)
        // وتولد onMoved عند اكتشاف تغير الموضع
        mSortedList.replaceAll(snapshot);
        buildSections();

        // ★ تحديث الهيدر بشكل غير متزامن لتجنب قطع أنيميشن العناصر ★
        if (recyclerView != null) {
            recyclerView.post(() -> {
                if (recyclerView != null && !recyclerView.isComputingLayout()) {
                    notifyItemChanged(0);
                }
            });
        }
    }

    /**
     * تحديث خيارات العرض في الهيدر فقط — بدون إعادة فرز (للتهيئة الأولية)
     */
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

    /**
     * تفويض إلى updateFilteredFonts — SortedList يتولى الأنيميشن بدلاً من DiffUtil
     */
    public void updateListWithAnimation(List<FontFileInfo> newFonts) {
        updateFilteredFonts(newFonts, currentSearchQuery);
    }

    /**
     * ★ الإصلاح الجوهري (وميض النجمة): تحديث لون اسم الخط فقط عبر PAYLOAD_UPDATE_LAST_OPENED ★
     *
     * المشكلة السابقة: كانت هذه الدالة تستدعي notifyItemRangeChanged(1, size) بدون payload،
     * مما يُشغّل onBindViewHolder كاملاً لكل عنصر. خلال الربط الكامل:
     *   - bind() ذات 9 معاملات تستدعي bind() الكاملة بـ isFavorite=false
     *   - هذا يستدعي setFavoriteIndicator(false) → النجمة مخفية مؤقتاً
     *   - ثم يستدعي الـ Adapter setFavoriteIndicator(true) → النجمة ظاهرة
     *   إذا كان ItemAnimator نشطاً أثناء هذه العملية، يُنتج cross-fade يُظهر الحالة الوسيطة.
     *
     * الحل: PAYLOAD_UPDATE_LAST_OPENED يُحدِّث فقط لون نص اسم الخط عبر
     * updateLastOpenedHighlight() في LocalFontViewHolder، دون أي مساس بأيقونة النجمة.
     *
     * ★ الإصلاح (المشكلة 2): تغليف إشعارات الـ Payload بـ recyclerView.post() ★
     * يضمن هذا أن الإشعارات لا تُرسَل أثناء كون الـ RecyclerView مخفياً أو قيد البناء،
     * مما يمنع تراكم الـ Payloads وتداخلها عند العودة إلى الـ Fragment.
     * بالإضافة إلى إرسال PAYLOAD_UPDATE_HIGHLIGHT لمسح أي لون أزرق عالق في النصوص.
     */
    public void smartUpdate() {
        buildSections();
        int size = mSortedList.size();
        if (size > 0) {
            // ★ الإصلاح (المشكلة 2): تأجيل إرسال الـ Payloads عبر post ★
            // يضمن هذا عدم ضياع الإشعارات أثناء كون الـ RecyclerView مخفياً أو قيد البناء
            if (recyclerView != null) {
                recyclerView.post(() -> {
                    if (recyclerView != null && !recyclerView.isComputingLayout()) {
                        // ★ PAYLOAD_UPDATE_LAST_OPENED: يُحدّث لون نص اسم الخط فقط — النجمة لا تُلمس ★
                        notifyItemRangeChanged(1, size, PAYLOAD_UPDATE_LAST_OPENED);
                        // ★ PAYLOAD_UPDATE_HIGHLIGHT: يمسح أي لون أزرق عالق من بحث سابق ★
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

    // ─────────────────────────────────────────────────────────
    // بناء الـ Sections
    // ─────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────
    // RecyclerView.Adapter
    // ─────────────────────────────────────────────────────────

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
            // ★ الخطوة 3: نستخدم دائماً الملف الموحد لأن شريط الفرز
            //   أصبح شفافاً في كلا الثيمين — لم تعد هناك حاجة للملف المنفصل ★
            return new SortHeaderViewHolder(inf.inflate(R.layout.sort_header_item, parent, false));
        }

        if (viewType == VIEW_TYPE_SPACE) {
            return new SpaceViewHolder(inf.inflate(R.layout.item_bottom_space, parent, false));
        }

        // ★ اختيار Layout عنصر الخط بناءً على الثيم:
        //   - الثيم الشفاف: MaterialCardView بزوايا دائرية كاملة وخلفية شفافة
        //   - الثيم الافتراضي: RoundLinearLayout مع حسابات زوايا OneUI ★
        int itemLayout = isTransparentTheme
                ? R.layout.font_list_item_transparent
                : R.layout.font_list_item;
        return new LocalFontViewHolder(inf.inflate(itemLayout, parent, false));
    }

    // ★ الربط الكامل
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

            // ★ الإصلاح السحري (المشكلة 2): إضافة (!isSearchActive) للشرط ★
            // هذا يضمن أنه إذا استلم العنصر أي Payload (مثل الزوايا) وكان البحث مغلقاً،
            // سيتم إجبار النص على التخلص من اللون الأزرق العالق دون الحاجة لربط كامل.
            // السيناريو المُصلَح: انتقلت لـ Fragment آخر دون إغلاق البحث → عدت →
            // smartUpdate() أرسل PAYLOAD_UPDATE_LAST_OPENED → هذا الشرط يرصد أن البحث
            // مغلق الآن فيمسح اللون الأزرق العالق فوراً.
            boolean isSearchActive = currentSearchQuery != null && !currentSearchQuery.isEmpty();
            if ((payloads.contains(PAYLOAD_UPDATE_HIGHLIGHT) || !isSearchActive)
                    && holder instanceof LocalFontViewHolder) {
                FontFileInfo fontInfo = mSortedList.get(position - 1);
                String displayName = FileUtils.removeExtension(fontInfo.getName());

                // ★ تحديد الارتفاع المطلوب للنص وتحويله من DP إلى PX ★
                int targetHeightPx = (int) TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        35, // الارتفاع المطلوب بوحدة الـ DP
                        holder.itemView.getContext().getResources().getDisplayMetrics()
                );

                if (isSearchActive) {
                    android.text.Spannable highlighted = highlighter.highlightText(displayName, currentSearchQuery);
                    // ★ تطبيق ExactLineHeightSpan لقص الهوامش المدمجة في الخط وتوحيد ارتفاع القائمة ★
                    android.text.SpannableString spanned = new android.text.SpannableString(highlighted);
                    spanned.setSpan(new ExactLineHeightSpan(targetHeightPx), 0, spanned.length(),
                            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    ((LocalFontViewHolder) holder).fontNameTextView.setText(spanned);
                } else {
                    // ★ SpannableString مع ExactLineHeightSpan يمسح اللون الأزرق ويوحد الارتفاع ★
                    android.text.SpannableString spanned = new android.text.SpannableString(displayName);
                    spanned.setSpan(new ExactLineHeightSpan(targetHeightPx), 0, spanned.length(),
                            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    ((LocalFontViewHolder) holder).fontNameTextView.setText(spanned);
                }
            }

            // ★ تحديث أيقونة المفضلة بصمت دون إعادة رسم العنصر كاملاً ★
            // يُستدعى من notifyFavoriteChanged() أو notifyAllFavoritesChanged()
            if (payloads.contains(PAYLOAD_UPDATE_FAVORITE) && holder instanceof LocalFontViewHolder) {
                FontFileInfo fontInfo = mSortedList.get(position - 1);
                boolean isFavorited = favoriteStatusProvider != null
                        && favoriteStatusProvider.isFavorited(fontInfo.getPath());
                ((LocalFontViewHolder) holder).setFavoriteIndicator(isFavorited);
            }

            // ★ الإصلاح (المشكلة 1 و 4): تحديث حالة الـ CheckBox فقط بصمت تام ★
            // هذا يحفظ:
            //   - أنيميشن انتقال العناصر في RTL (العربية) لأن العنصر لم يُعاد رسمه
            //   - أيقونة النجمة من الوميض لأن setFavoriteIndicator() لم تُستدعَ
            if (payloads.contains(PAYLOAD_UPDATE_SELECTION)) {
                if (holder instanceof LocalFontViewHolder) {
                    LocalFontViewHolder vh = (LocalFontViewHolder) holder;
                    if (isSelectionMode) {
                        vh.checkBox.setVisibility(View.VISIBLE);
                        vh.checkBox.setChecked(isItemSelected(position));
                    } else {
                        vh.checkBox.setVisibility(View.GONE);
                        vh.checkBox.setChecked(false);
                    }
                } else if (holder instanceof SortHeaderViewHolder) {
                    // ★ تعطيل/تفعيل شريط الفرز حسب وضع التحديد ★
                    ((SortHeaderViewHolder) holder).setSortEnabled(!isSelectionMode);
                }
            }

            // ★ الإصلاح الجوهري (وميض النجمة): تحديث لون اسم الخط فقط بصمت تام ★
            // يُستدعى من smartUpdate() و saveLastOpenedAndUpdate() عبر PAYLOAD_UPDATE_LAST_OPENED.
            // updateLastOpenedHighlight() في LocalFontViewHolder لا تلمس favoriteIconView إطلاقاً،
            // مما يضمن عدم وجود أي حالة وسيطة يلتقطها الـ ItemAnimator → لا وميض.
            if (payloads.contains(PAYLOAD_UPDATE_LAST_OPENED) && holder instanceof LocalFontViewHolder) {
                FontFileInfo fontInfo = mSortedList.get(position - 1);
                boolean isLastOpened = preferenceManager.isLastOpenedFont(fontInfo.getPath());
                
                // ★ إلغاء تمييز آخر خط تم فتحه مؤقتاً أثناء البحث ★
                if (isSearchActive) isLastOpened = false;
                
                ((LocalFontViewHolder) holder).updateLastOpenedHighlight(isLastOpened);
                // ★ لا setFavoriteIndicator — لا checkBox — لا وميض ★
            }

        } else {
            super.onBindViewHolder(holder, position, payloads);
        }
    }

    // ─────────────────────────────────────────────────────────
    // ★ الخطوة 4: التعديل الجوهري لهندسة الزوايا (updateItemAppearance) ★
    //
    // المنطق الجديد:
    //   • شريط الفرز أصبح شفافاً — نعود فوراً دون أي معالجة
    //   • العنصر الأول الحقيقي (position==1) يأخذ الزوايا العلوية
    //   • العنصر الأخير يأخذ الزوايا السفلية
    //   • إذا كان عنصراً وحيداً يأخذ الزوايا الأربع
    //   • العناصر الوسطى بدون تدوير
    // ─────────────────────────────────────────────────────────
    private void updateItemAppearance(RecyclerView.ViewHolder holder, int position) {
        // ★ توفير معالجة: في الثيم الشفاف، MaterialCardView يدير الزوايا تلقائياً ★
        if (isTransparentTheme) return;

        // ★ شريط الفرز أصبح شفافاً — لا يحتاج أي معالجة للزوايا أو الفواصل ★
        if (holder instanceof SortHeaderViewHolder) return;

        if (holder instanceof LocalFontViewHolder) {
            LocalFontViewHolder fh = (LocalFontViewHolder) holder;
            RoundLinearLayout root = (RoundLinearLayout) fh.itemView;
            int totalFonts         = mSortedList.size();
            boolean isFirst        = (position == 1);
            boolean isLast         = (position == getItemCount() - 2);

            if (totalFonts == 1) {
                // ★ عنصر وحيد في القائمة: تدوير الزوايا الأربع وإخفاء الفاصل ★
                root.setRoundedCorners(SeslRoundedCorner.ROUNDED_CORNER_ALL);
                if (fh.dividerView != null) fh.dividerView.setVisibility(View.GONE);
            } else if (isFirst) {
                // ★ العنصر الأول: يأخذ الزوايا العلوية الدائرية ويُظهر الفاصل ★
                root.setRoundedCorners(SeslRoundedCorner.ROUNDED_CORNER_TOP_LEFT
                                     | SeslRoundedCorner.ROUNDED_CORNER_TOP_RIGHT);
                if (fh.dividerView != null) fh.dividerView.setVisibility(View.VISIBLE);
            } else if (isLast) {
                // ★ العنصر الأخير: يأخذ الزوايا السفلية الدائرية ويخفي الفاصل ★
                root.setRoundedCorners(SeslRoundedCorner.ROUNDED_CORNER_BOTTOM_LEFT
                                     | SeslRoundedCorner.ROUNDED_CORNER_BOTTOM_RIGHT);
                if (fh.dividerView != null) fh.dividerView.setVisibility(View.INVISIBLE);
            } else {
                // ★ عنصر وسطي: بدون تدوير للزوايا ويُظهر الفاصل ★
                root.setRoundedCorners(SeslRoundedCorner.ROUNDED_CORNER_NONE);
                if (fh.dividerView != null) fh.dividerView.setVisibility(View.VISIBLE);
            }
        }
    }

    // ─────────────────────────────────────────────────────────
    // ربط بيانات عنصر الخط
    // ─────────────────────────────────────────────────────────
    private void bindLocalFontViewHolder(LocalFontViewHolder holder, FontFileInfo fontInfo, int position) {
        String fileName    = fontInfo.getName();
        String path        = fontInfo.getPath();
        String displayName = FileUtils.removeExtension(fileName);

        LocalFontListViewModel.FontFileInfoWithMetadata metadata = getFontMetadataForPath(path);
        String realName = (metadata != null) ? metadata.getRealName() : null;
        if (realName == null || realName.isEmpty()) realName = context.getString(R.string.unknown_font);

        // ★ استخراج وصف الوزن/العرض من البيانات الوصفية ★
        // إذا لم يُستخرج بعد يُعرض "غير معروف" تلقائياً من الـ ViewHolder
        String weightWidthLabel = (metadata != null) ? metadata.getWeightWidthLabel() : null;

        boolean isSearchActive = currentSearchQuery != null && !currentSearchQuery.isEmpty();
        boolean isLastOpened   = preferenceManager.isLastOpenedFont(path);

        // ★ إلغاء تمييز آخر خط تم فتحه مؤقتاً أثناء البحث ★
        if (isSearchActive) isLastOpened = false;

        // ★ تمرير weightWidthLabel إلى bind() ذات 9 معاملات ★
        // ★ bind() ذات 9 معاملات تستدعي bindCore() التي لا تلمس setFavoriteIndicator ★
        // ★ بذلك لا توجد حالة وسيطة بين السطرين التاليين → لا وميض للنجمة ★
        holder.bind(displayName, path, isSearchActive, currentSearchQuery,
                    isLastOpened, highlighter, isSelectionMode, isItemSelected(position),
                    weightWidthLabel);

        // ★ عرض أيقونة ic_favorite بجانب العناصر المفضلة في قائمة الخطوط المحلية ★
        // الأيقونة صفراء اللون وتظهر فقط للعناصر التي أضافها المستخدم إلى المفضلة
        // ملاحظة للمطوّر: يجب أن يحتوي LocalFontViewHolder على setFavoriteIndicator(boolean)
        boolean isFavorited = favoriteStatusProvider != null && favoriteStatusProvider.isFavorited(path);
        holder.setFavoriteIndicator(isFavorited);

        // ★ الإصلاح (مشكلة السكرول): استخدام المتغير المحفوظ في الذاكرة بدلاً من قراءة DataStore ★
        // السطر القديم: if (SettingsHelper.isFontPreviewEnabled(context)) loadFontPreview(holder, path);
        // كان يُسبب عملية I/O لكل عنصر يظهر على الشاشة أثناء السكرول → تقطيع.
        // السطر الجديد: مقارنة boolean سريعة جداً من الذاكرة العشوائية → سكرول سلس. ★
        if (mIsFontPreviewEnabled) loadFontPreview(holder, path);
        else holder.setDefaultTypeface(SettingsHelper.getTypeface(context));

        final String finalRealName      = realName;
        // ★ حفظ weightWidthLabel كـ final لاستخدامه في مستمع النقر ★
        final String finalWeightWidth   = weightWidthLabel;

        holder.itemView.setOnClickListener(v -> {
            // ★ الحارس: يمنع تشغيل نقرتين متزامنتين ★
            if (mClickGuard) return;
            mClickGuard = true;
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) { mClickGuard = false; return; }
            if (isSelectionMode) {
                mClickGuard = false;
                if (selectionListener != null) selectionListener.onToggleSelection(pos);
            } else {
                if (fontClickListener != null)
                    // ★ التعديل: تمرير finalWeightWidth كمعامل خامس ★
                    fontClickListener.onFontClick(path, finalRealName, fileName, 0, finalWeightWidth);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return false;
            if (!isSelectionMode && selectionListener != null) selectionListener.onStartSelection(pos);
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
            holder.setDefaultTypeface(SettingsHelper.getTypeface(context));
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

    // ─────────────────────────────────────────────────────────
    // SectionIndexer
    // ─────────────────────────────────────────────────────────

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
