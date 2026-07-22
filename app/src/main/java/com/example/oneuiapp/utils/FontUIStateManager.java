package com.example.oneuiapp.utils;

import android.content.Context;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.AppBarLayout;

import com.example.oneuiapp.R;
import com.example.oneuiapp.widget.sort.SortByItemLayout;

/**
 * FontUIStateManager - فئة لإدارة حالات واجهة المستخدم المختلفة
 * تتولى التحكم في إظهار وإخفاء العناصر وحفظ واستعادة حالة المكونات
 *
 * ★ الإصلاح (المشكلة 3): إضافة defaultEmptyMessageResId مع setDefaultEmptyMessage() ★
 * يتيح لكل Fragment تخصيص رسالة الشاشة الفارغة بدلاً من استخدام
 * R.string.font_fragment_empty_message المثبتة لجميع القوائم.
 * يُستدعى setDefaultEmptyMessage() مباشرةً بعد إنشاء FontUIStateManager في كل Fragment.
 *
 * ★ الإصلاح (مشكلة العنوان): إضافة emptyTitleView مع setEmptyTitleView() ★
 * يُخفى عنوان الحالة الفارغة تلقائياً عند تفعيل البحث بلا نتائج،
 * لأن جملة "لا توجد نتائج" تكفي وحدها دون الحاجة لعرض العنوان معها.
 * يُستدعى setEmptyTitleView() بعد setViews() مباشرةً في كل Fragment يحتوي على empty_title.
 *
 * ★ الإضافة (تمييز الحالتين): إضافة noResultsTextView مع setNoResultsTextView() ★
 * يُتيح تمييز رسالة "لا توجد نتائج" (عند البحث) عن رسالة الحالة الفارغة الحقيقية
 * بلون وحجم مختلفين، لأن كلًّا منهما يخدم سياقاً مختلفاً من منظور تجربة المستخدم.
 *
 * ★ آلية التمييز:
 *   - عند البحث بلا نتائج : يُخفى empty_title + empty_text، يُعرض no_results_text
 *   - عند الفراغ الحقيقي  : يُعرض empty_title + empty_text، يُخفى no_results_text
 *
 * ★ التوافق مع الوراء: إذا لم يُربط no_results_text (setNoResultsTextView لم تُستدعَ)،
 *   يعود الـ Manager تلقائياً إلى السلوك القديم (empty_text للحالتين معاً). ★
 *
 * يُستدعى setNoResultsTextView() بعد setViews() وsetEmptyTitleView() في كل Fragment.
 *
 * ★ دعم TrashFragment (الإضافة الجديدة): ★
 * يدعم الـ Manager الآن قوائم لا تحتاج إلى اختيار مجلد أو بحث (كسلة المحذوفات)
 * عبر دالة setViews() المُحمَّلة بثلاثة معاملات فقط، ودالة updateEmptyView(boolean)
 * التي تفترض دائماً عدم وجود بحث نشط. نمط الاستخدام في TrashFragment:
 *   mUIManager = new FontUIStateManager(mContext);
 *   mUIManager.setDefaultEmptyMessage(R.string.trash_empty_message);
 *   mUIManager.setViews(emptyView, emptyTextView, recyclerView);
 *   mUIManager.setEmptyTitleView(emptyTitleView);
 *   mUIManager.updateEmptyView(trashList.isEmpty());
 *
 * ★ إضافة مؤشر التحميل (loadingContainer): ★
 * دالة setLoadingContainer() مخصصة لـ LocalFontListFragment فقط.
 * لا تؤثر على SystemFontListFragment ولا FavoriteFontListFragment لأنها
 * دالة منفصلة لا تغيّر توقيع setViews() الموجود، مما يمنع أي Build Errors.
 */
public class FontUIStateManager {
    
    private static final String TAG = "FontUIStateManager";
    
    private final Context context;
    private View selectFolderContainer;
    private View mainContentLayout;
    private View emptyView;
    private TextView emptyTextView;

    // ★ عنوان الحالة الفارغة — يُخفى تلقائياً عند تفعيل البحث بلا نتائج ★
    private TextView emptyTitleView;

    // ★ رسالة البحث بلا نتائج — مستقلة عن emptyTextView لتمكين تخصيص مظهرها من الـ layout ★
    // نصها وأيقونتها ثابتان ("لا توجد نتائج") ويُعيَّنان في XML مباشرةً.
    // الـ Manager يكتفي بإظهارها وإخفائها دون الحاجة لاستدعاء setText() عليها.
    private TextView noResultsTextView;

    private RecyclerView recyclerView;
    private AppBarLayout appBarLayout;

    // ★ حاوية مؤشر التحميل — تُستخدم في LocalFontListFragment فقط ★
    // لا تؤثر على الفراجمنتات الأخرى لأن setLoadingContainer() دالة اختيارية منفصلة
    private View loadingContainer;
    
    private Parcelable recyclerViewState;
    private SortByItemLayout.SortType savedSortType;
    private boolean savedSortAscending;

    // ★ الإصلاح (المشكلة 3): رسالة الشاشة الفارغة الافتراضية لكل Fragment ★
    // القيمة الابتدائية هي رسالة المجلد المحلي للتوافق مع LocalFontListFragment.
    // يستدعي FavoriteFontListFragment وTrashFragment (وأي Fragment مستقبلي) setDefaultEmptyMessage()
    // لتعيين رسالته الخاصة بعد إنشاء هذا الـ Manager مباشرةً.
    private int defaultEmptyMessageResId = R.string.font_fragment_empty_message;
    
    /**
     * Constructor
     * @param context السياق المطلوب
     */
    public FontUIStateManager(Context context) {
        this.context = context;
    }
    
    /**
     * تعيين العناصر المُدارة — النسخة الكاملة للـ LocalFontListFragment.
     * تشمل حاوية اختيار المجلد والمحتوى الرئيسي.
     *
     * @param selectFolderContainer حاوية زر اختيار المجلد
     * @param mainContentLayout     المحتوى الرئيسي
     * @param emptyView             عرض الحالة الفارغة
     * @param emptyTextView         نص وصف الحالة الفارغة الحقيقية
     * @param recyclerView          قائمة الخطوط
     */
    public void setViews(View selectFolderContainer, View mainContentLayout, 
                        View emptyView, TextView emptyTextView, RecyclerView recyclerView) {
        this.selectFolderContainer = selectFolderContainer;
        this.mainContentLayout = mainContentLayout;
        this.emptyView = emptyView;
        this.emptyTextView = emptyTextView;
        this.recyclerView = recyclerView;
    }

    /**
     * ★ تعيين العناصر المُدارة — النسخة المبسَّطة للـ TrashFragment وما شابهه ★
     *
     * مخصصة للـ Fragments التي لا تحتاج إلى اختيار مجلد ولا تبديل mainContentLayout،
     * مثل TrashFragment وFavoriteFontListFragment عند الرغبة في تبسيط الإعداد.
     *
     * ★ الفرق عن النسخة الكاملة:
     *   - لا يُعيَّن selectFolderContainer → دالة updateUIVisibility() لن تؤثر على شيء
     *   - لا يُعيَّن mainContentLayout    → المحتوى دائماً ظاهر ولا يحتاج تبديلاً ★
     *
     * نمط الاستخدام الموصى به في TrashFragment:
     *   mUIManager.setViews(emptyView, emptyTextView, recyclerView);
     *   mUIManager.setEmptyTitleView(emptyTitleView);
     *
     * @param emptyView     عرض الحالة الفارغة
     * @param emptyTextView نص وصف الحالة الفارغة
     * @param recyclerView  قائمة العناصر
     */
    public void setViews(View emptyView, TextView emptyTextView, RecyclerView recyclerView) {
        this.emptyView = emptyView;
        this.emptyTextView = emptyTextView;
        this.recyclerView = recyclerView;
    }

    /**
     * تعيين عنوان الحالة الفارغة (empty_title).
     *
     * مُصمَّمة كدالة منفصلة عن setViews() للحفاظ على توافق الاستدعاءات الموجودة.
     * تُستدعى مباشرةً بعد setViews() في كل Fragment يحتوي على empty_title.
     *
     * ★ السلوك:
     *   - عند البحث بلا نتائج  → يُخفى العنوان، يبقى الوصف فقط ("لا توجد نتائج")
     *   - عند فراغ القائمة     → يُعرض العنوان والوصف معاً
     *   - في TrashFragment      → يُعرض العنوان دائماً (لا يوجد بحث) ★
     *
     * @param emptyTitleView الـ TextView المعرَّف بـ id/empty_title في الـ layout
     */
    public void setEmptyTitleView(TextView emptyTitleView) {
        this.emptyTitleView = emptyTitleView;
    }

    /**
     * ★ تعيين رسالة البحث بلا نتائج (no_results_text) المستقلة ★
     *
     * تُستدعى بعد setViews() وsetEmptyTitleView() في كل Fragment يريد تمييز
     * مظهر رسالة "لا توجد نتائج" عن مظهر رسالة الحالة الفارغة الحقيقية.
     *
     * ★ الفرق عن emptyTextView:
     *   - emptyTextView     → رسالة الحالة الفارغة الحقيقية (القائمة فارغة أصلاً)
     *   - noResultsTextView → رسالة البحث بلا نتائج (توجد خطوط لكن لا نتائج للبحث)
     *   كلٌّ منهما يُعيَّن لونه وحجمه بشكل مستقل تماماً من الـ layout. ★
     *
     * ★ ملاحظة: نص هذا الـ TextView يُعيَّن في XML مباشرةً (android:text="@string/no_results_found")
     *   ولا يحتاج الـ Manager لاستدعاء setText() عليه. ★
     *
     * ★ ملاحظة: لا يُستدعى هذا في TrashFragment لأنه لا يحتوي على بحث. ★
     *
     * ★ التوافق مع الوراء: إذا لم تُستدعَ هذه الدالة، يعود الـ Manager تلقائياً
     *   إلى السلوك القديم (empty_text يخدم الحالتين معاً). ★
     *
     * @param noResultsTextView الـ TextView المعرَّف بـ id/no_results_text في الـ layout
     */
    public void setNoResultsTextView(TextView noResultsTextView) {
        this.noResultsTextView = noResultsTextView;
    }

    /**
     * ★ ربط حاوية مؤشر التحميل بالمدير ★
     *
     * دالة مخصصة لـ LocalFontListFragment فقط — لا تؤثر على SystemFontListFragment
     * ولا FavoriteFontListFragment لأنهما لا يستدعيانها على الإطلاق.
     *
     * الحل الذكي: بدلاً من تغيير توقيع setViews() وتخريب الفراجمنتات الأخرى،
     * أُضيفت هذه الدالة الجديدة المستقلة لربط مؤشر التحميل دون أي تأثير جانبي.
     *
     * تُستدعى مباشرةً بعد setViews() في initializeViews() في LocalFontListFragment:
     *   mUIManager.setLoadingContainer(view.findViewById(R.id.loading_container));
     *
     * @param loadingContainer الـ View المعرَّف بـ id/loading_container في الـ layout
     */
    public void setLoadingContainer(View loadingContainer) {
        this.loadingContainer = loadingContainer;
    }
    
    /**
     * تعيين AppBarLayout
     * @param appBarLayout شريط التطبيق العلوي
     */
    public void setAppBarLayout(AppBarLayout appBarLayout) {
        this.appBarLayout = appBarLayout;
    }

    /**
     * ★ الإصلاح (المشكلة 3): تعيين رسالة الشاشة الفارغة المخصصة لكل Fragment ★
     *
     * يُستدعى مباشرةً بعد إنشاء FontUIStateManager في أي Fragment يحتاج رسالة مختلفة.
     *
     * أمثلة:
     *   FavoriteFontListFragment → setDefaultEmptyMessage(R.string.favorites_empty_message)
     *   TrashFragment           → setDefaultEmptyMessage(R.string.trash_empty_message)
     *
     * إذا لم يُستدعَ، تُستخدم القيمة الافتراضية (font_fragment_empty_message)
     * وهي مناسبة لـ LocalFontListFragment دون الحاجة لأي استدعاء.
     *
     * @param resId معرّف نص الرسالة المطلوبة من ملفات strings
     */
    public void setDefaultEmptyMessage(int resId) {
        this.defaultEmptyMessageResId = resId;
    }
    
    /**
     * تحديث رؤية واجهة المستخدم بناءً على وجود مجلد محدد.
     * مخصصة لـ LocalFontListFragment حصراً — لا تأثير لها إذا لم يُعيَّن
     * selectFolderContainer عبر setViews() الكاملة.
     *
     * @param hasFolderUri هل يوجد مجلد محدد
     */
    public void updateUIVisibility(boolean hasFolderUri) {
        if (selectFolderContainer != null) {
            selectFolderContainer.setVisibility(hasFolderUri ? View.GONE : View.VISIBLE);
        }
        
        if (mainContentLayout != null) {
            mainContentLayout.setVisibility(hasFolderUri ? View.VISIBLE : View.GONE);
        }
    }
    
    /**
     * تحديث عرض الحالة الفارغة مع دعم حالة البحث.
     * مخصصة للـ Fragments التي تحتوي على بحث (LocalFontListFragment، FavoriteFontListFragment).
     *
     * @param isEmpty         هل القائمة فارغة
     * @param isSearchActive  هل هناك بحث نشط
     */
    public void updateEmptyView(boolean isEmpty, boolean isSearchActive) {
        if (isEmpty) {
            showEmptyView(isSearchActive);
        } else {
            hideEmptyView();
        }
    }

    /**
     * ★ تحديث عرض الحالة الفارغة بدون معامل البحث — مخصصة لـ TrashFragment ★
     *
     * نسخة مبسَّطة من updateEmptyView() تفترض دائماً عدم وجود بحث نشط،
     * وهو السلوك الصحيح لسلة المحذوفات التي لا تحتوي على وظيفة بحث.
     *
     * مكافئة تماماً لاستدعاء: updateEmptyView(isEmpty, false)
     *
     * @param isEmpty هل القائمة فارغة
     */
    public void updateEmptyView(boolean isEmpty) {
        updateEmptyView(isEmpty, false);
    }
    
    /**
     * إظهار عرض الحالة الفارغة مع التمييز بين الحالتين.
     *
     * ★ إذا كان noResultsTextView مُربوطاً (setNoResultsTextView استُدعيت):
     *   - عند البحث : أخفِ empty_title + empty_text، أظهر no_results_text
     *   - عند الفراغ: أظهر empty_title + empty_text، أخفِ no_results_text
     *
     * ★ إذا لم يكن noResultsTextView مُربوطاً (التوافق مع الوراء):
     *   - يُستخدم empty_text للحالتين مع تغيير نصه برمجياً كما كان سابقاً ★
     *
     * ★ في TrashFragment: isSearchActive دائماً false، لذا يُعرض empty_title + empty_text
     *   دائماً دون أي منطق إضافي. ★
     *
     * @param isSearchActive هل هناك بحث نشط
     */
    private void showEmptyView(boolean isSearchActive) {
        if (recyclerView != null) {
            recyclerView.setVisibility(View.GONE);
        }
        
        if (emptyView != null) {
            emptyView.setVisibility(View.VISIBLE);
            
            if (appBarLayout != null) {
                updateEmptyViewPosition(Math.abs(appBarLayout.getTop()));
            }
        }

        if (noResultsTextView != null) {
            // ★ وضع التمييز: كل TextView مخصص لحالة واحدة فقط ★
            // اللون والحجم يُعيَّنان بالكامل من الـ layout — الـ Manager لا يتدخل فيهما
            if (isSearchActive) {
                // حالة البحث بلا نتائج: يظهر no_results_text وحده
                if (emptyTitleView != null) emptyTitleView.setVisibility(View.GONE);
                if (emptyTextView  != null) emptyTextView.setVisibility(View.GONE);
                noResultsTextView.setVisibility(View.VISIBLE);
            } else {
                // حالة الفراغ الحقيقي: يظهر empty_title + empty_text
                if (emptyTitleView != null) emptyTitleView.setVisibility(View.VISIBLE);
                if (emptyTextView  != null) {
                    emptyTextView.setVisibility(View.VISIBLE);
                    // ★ الإصلاح (المشكلة 3): استخدام الرسالة المخصصة بدلاً من الرسالة المثبتة ★
                    emptyTextView.setText(context.getString(defaultEmptyMessageResId));
                }
                noResultsTextView.setVisibility(View.GONE);
            }
        } else {
            // ★ التوافق مع الوراء: noResultsTextView غير مُربوط → السلوك القديم ★
            // empty_text يخدم الحالتين مع تغيير نصه برمجياً
            if (emptyTitleView != null) {
                emptyTitleView.setVisibility(isSearchActive ? View.GONE : View.VISIBLE);
            }
            if (emptyTextView != null) {
                if (isSearchActive) {
                    emptyTextView.setText(context.getString(R.string.no_results_found));
                } else {
                    // ★ الإصلاح (المشكلة 3): استخدام الرسالة المخصصة بدلاً من الرسالة المثبتة ★
                    emptyTextView.setText(context.getString(defaultEmptyMessageResId));
                }
            }
        }
    }
    
    /**
     * إخفاء عرض الحالة الفارغة وإعادة إظهار القائمة.
     *
     * ★ يُخفى noResultsTextView أيضاً لضمان عدم بقائه ظاهراً عند استعادة القائمة ★
     */
    private void hideEmptyView() {
        if (recyclerView != null) {
            recyclerView.setVisibility(View.VISIBLE);
        }
        
        if (emptyView != null) {
            emptyView.setVisibility(View.GONE);
        }

        // ★ إخفاء رسالة البحث بلا نتائج عند استعادة القائمة ★
        // ضروري لتجنب ظهورها في الحالة التالية: كان المستخدم يبحث بلا نتائج،
        // ثم حذف نص البحث، فأعادت القائمة نفسها → يجب أن يختفي no_results_text تماماً.
        if (noResultsTextView != null) {
            noResultsTextView.setVisibility(View.GONE);
        }
    }
    
    /**
     * تحديث موضع عرض الحالة الفارغة وحاوية اختيار المجلد بناءً على حركة AppBar.
     *
     * ★ الإصلاح (مشكلة الوضع الأفقي مع الكيبورد):
     * نعتمد فقط على إزاحة الـ AppBar كما تفعل مكتبة OneUI الرسمية، وندع نظام Android
     * يتولى تلقائياً توسيط المحتوى عند فتح الكيبورد في الوضع الأفقي.
     * في الوضع الأفقي يكون totalScrollRange = 0 فتصبح translationY = 0،
     * مما يمنع سحب المحتوى للأعلى مرتين (Double Lifting) الذي كان يُطير الأيقونة
     * ويقص النص خارج حدود الشاشة. ★
     *
     * @param verticalOffset الإزاحة العمودية للـ AppBar
     */
    public void updateEmptyViewPosition(int verticalOffset) {
        if (appBarLayout == null) {
            return;
        }
        
        int totalScrollRange = appBarLayout.getTotalScrollRange();
        float translationY = 0f;
        
        // نحسب الإزاحة فقط إذا كان الـ AppBar قابلاً للتمرير (وهذا يحدث في الوضع العمودي فقط)
        // في الوضع الأفقي سيكون totalScrollRange = 0 وبالتالي translationY = 0
        // والنظام سيتكفل بتوسيط المحتوى عند فتح الكيبورد
        if (totalScrollRange != 0) {
            translationY = (Math.abs(verticalOffset) - totalScrollRange) / 2.0f;
        }
        
        // 1. تطبيق الإزاحة على المحتوى الداخلي للشاشة الفارغة
        if (emptyView != null && emptyView.getVisibility() == View.VISIBLE) {
            // نستخدم الـ ID الذي أضفناه في XML بدلاً من getChildAt(0)
            View innerEmptyView = emptyView.findViewById(R.id.empty_container);
            if (innerEmptyView != null) {
                innerEmptyView.setTranslationY(translationY);
            }
        }

        // 2. تطبيق الإزاحة على المحتوى الداخلي لشاشة اختيار المجلد
        if (selectFolderContainer != null && selectFolderContainer.getVisibility() == View.VISIBLE) {
            View innerFolderView = selectFolderContainer.findViewById(R.id.folder_container_inner);
            if (innerFolderView != null) {
                innerFolderView.setTranslationY(translationY);
            }
        }
    }
    
    /**
     * حفظ حالة RecyclerView
     */
    public void saveRecyclerViewState() {
        if (recyclerView != null && recyclerView.getLayoutManager() != null) {
            recyclerViewState = recyclerView.getLayoutManager().onSaveInstanceState();
            Log.d(TAG, "RecyclerView state saved");
        }
    }
    
    /**
     * استعادة حالة RecyclerView
     */
    public void restoreRecyclerViewState() {
        if (recyclerViewState != null && recyclerView != null && recyclerView.getLayoutManager() != null) {
            recyclerView.getLayoutManager().onRestoreInstanceState(recyclerViewState);
            recyclerViewState = null;
            Log.d(TAG, "RecyclerView state restored");
        }
    }
    
    /**
     * تعيين حالة RecyclerView المحفوظة مسبقاً
     * @param state الحالة المحفوظة
     */
    public void setRecyclerViewState(Parcelable state) {
        this.recyclerViewState = state;
    }
    
    /**
     * الحصول على حالة RecyclerView الحالية
     * @return الحالة المحفوظة أو null
     */
    public Parcelable getRecyclerViewState() {
        return recyclerViewState;
    }
    
    /**
     * حفظ حالة الفرز
     * @param sortType  نوع الفرز
     * @param ascending اتجاه الفرز
     */
    public void saveSortState(SortByItemLayout.SortType sortType, boolean ascending) {
        this.savedSortType = sortType;
        this.savedSortAscending = ascending;
        Log.d(TAG, "Sort state saved: type=" + sortType + ", ascending=" + ascending);
    }
    
    /**
     * الحصول على نوع الفرز المحفوظ
     * @return نوع الفرز
     */
    public SortByItemLayout.SortType getSavedSortType() {
        return savedSortType;
    }
    
    /**
     * الحصول على اتجاه الفرز المحفوظ
     * @return اتجاه الفرز
     */
    public boolean isSavedSortAscending() {
        return savedSortAscending;
    }
    
    /**
     * فحص ما إذا كانت هناك حالة فرز محفوظة
     * @return true إذا كانت هناك حالة محفوظة
     */
    public boolean hasSavedSortState() {
        return savedSortType != null;
    }
    
    /**
     * مسح جميع الحالات المحفوظة
     */
    public void clearSavedStates() {
        recyclerViewState = null;
        savedSortType = null;
        savedSortAscending = true;
        Log.d(TAG, "All saved states cleared");
    }
    
    /**
     * إظهار حالة التحميل.
     *
     * ★ يُخفى الـ RecyclerView والـ emptyView ويُظهر loadingContainer (إن كان مُربوطاً) ★
     * loadingContainer يُربط فقط في LocalFontListFragment عبر setLoadingContainer()،
     * لذا في الفراجمنتات الأخرى يكون null ولا يحدث أي شيء إضافي.
     */
    public void showLoadingState() {
        if (recyclerView != null) recyclerView.setVisibility(View.GONE);
        if (emptyView != null) emptyView.setVisibility(View.GONE);
        if (loadingContainer != null) loadingContainer.setVisibility(View.VISIBLE); // إظهار التحميل
    }
    
    /**
     * إخفاء حالة التحميل وإظهار المحتوى.
     *
     * ★ يُخفى loadingContainer (إن كان مُربوطاً) فقط.
     */
    public void hideLoadingState() {
        if (loadingContainer != null) {
            loadingContainer.setVisibility(View.GONE); // إخفاء التحميل
        }

        // ❌ تم حذف السطر التالي:
        // if (recyclerView != null) recyclerView.setVisibility(View.VISIBLE);
        // ★ السبب: دالة updateEmptyView هي المسؤولة الوحيدة عن تحديد
        // ما إذا كان الـ RecyclerView يجب أن يظهر (إذا كان هناك خطوط)
        // أو يختفي (إذا كان المجلد فارغاً). إجبار ظهوره هنا يكسر الشاشة الفارغة.
    }
    
    /**
     * التمرير إلى أعلى القائمة
     */
    public void scrollToTop() {
        if (recyclerView != null) {
            recyclerView.scrollToPosition(0);
        }
    }
    
    /**
     * التمرير إلى موضع محدد
     * @param position الموضع المراد التمرير إليه
     */
    public void scrollToPosition(int position) {
        if (recyclerView != null && position >= 0) {
            recyclerView.scrollToPosition(position);
        }
    }
    
    /**
     * التمرير السلس إلى موضع محدد
     * @param position الموضع المراد التمرير إليه
     */
    public void smoothScrollToPosition(int position) {
        if (recyclerView != null && position >= 0) {
            recyclerView.smoothScrollToPosition(position);
        }
    }
}
