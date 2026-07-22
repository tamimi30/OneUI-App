package com.example.oneuiapp.fragment.trash;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;                          // ★ إضافة

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.AppBarLayout;

import java.util.ArrayList;
import java.util.List;

import dev.oneuiproject.oneui.dialog.ProgressDialog;
import dev.oneuiproject.oneui.layout.DrawerLayout;

import com.example.oneuiapp.R;
import com.example.oneuiapp.activity.AppScreen;           // ★ الإصلاح الجوهري: استيراد AppScreen ★
import com.example.oneuiapp.activity.MainActivity;
import com.example.oneuiapp.data.entity.FontEntity;
import com.example.oneuiapp.dialog.TrashActionDialogs;
import com.example.oneuiapp.utils.FontUIStateManager;  // ★ إضافة
import com.example.oneuiapp.fragment.trash.manager.TrashSelectionManager;
import com.example.oneuiapp.fragment.trash.adapter.TrashListAdapter;
import com.example.oneuiapp.fragment.trash.viewmodel.TrashViewModel;
import com.example.oneuiapp.utils.notification.BatchOperationState;

/**
 * TrashFragment — شاشة سلة المحذوفات
 *
 * ════════════════════════════════════════════════════════════════════════
 * المتطلبات المطبَّقة:
 *
 * ★ (1)  لا فرز — الترتيب يأتي من FontDao (الأحدث حذفاً أولاً)
 * ★ (2)  لا بحث
 * ★ (3)  الضغط العادي → Toast (مُنفَّذ في TrashListAdapter)
 * ★ (4)  لا معاينات للخطوط (مُنفَّذ في TrashListAdapter)
 * ★ (5)  بطاقات شفافة (trash_list_item.xml)
 * ★ (6)  شاشة فارغة: أيقونة + "لا توجد ملفات" + وصف 30 يوماً (fragment_trash.xml)
 * ★ (7)  رسالة المحتوى كـ VIEW_TYPE_HEADER في TrashListAdapter — تتدفق مع القائمة
 * ★ (8)  لا وزن/عرض للخط (مُنفَّذ في TrashListAdapter)
 * ★ (9)  الأيام المتبقية على كل عنصر (مُنفَّذ في TrashListAdapter)
 * ★ (10) زر (ic_oui_more) بخيار "إفراغ" فقط — لا زر "تعديل"
 *        ★ المرحلة الأولى من خطة التحسين: انتقل الزر من MainActivity إلى هذا الـ Fragment ★
 *        يُدار الآن عبر setHasOptionsMenu(true) وonCreateOptionsMenu() وonOptionsItemSelected()،
 *        مما يجعل دورة حياة الزر مرتبطة بدورة حياة هذا الـ Fragment بدلاً من النشاط.
 *        الزر يظهر فقط عند وجود عناصر في السلة — يُتحكَّم فيه عبر onPrepareOptionsMenu()
 *        التي تُستدعى كلما طلب الكود invalidateOptionsMenu() عند تغيّر عدد العناصر.
 * ★ (11) عدد الملفات في عنوان CollapsingToolbar الفرعي (مع حالة الصفر للإنجليزية)
 * ★ (12) Fragment وليس Activity
 * ★ (13) الضغط المطول → استعادة + حذف فقط (مُنفَّذ في TrashSelectionManager)
 * ★ (14) نصوص الأزرار تتغيّر بحسب التحديد الكلي/الجزئي (مُنفَّذ في TrashSelectionManager)
 * ★ (17) ديالوج تأكيد الحذف النهائي بزر أحمر (TrashActionDialogs)
 * ★ (18) ديالوج تقدم + إشعار لعمليات النقل/الاستعادة/الحذف
 * ★ (19) نفس سلوك (18) للحذف النهائي من السلة والاستعادة
 * ★ (24) طبقتا Repository و ViewModel موجودتان في TrashRepository و TrashViewModel
 * ★ (27) رسالة الـ 30 يوماً كـ HEADER في الـ Adapter لا في NestedScrollView
 *
 * ★ إصلاح الأنيميشن (خطة الإصلاح الثلاثية):
 *   1. setupRecyclerViewAnimator() مع setRemoveDuration(0) → اختفاء فوري دفعةً واحدة
 *   2. mIsBatchOperationRunning → يحجب تحديث الـ Adapter أثناء ديالوج التقدم
 *   3. الحد الأدنى 300ms في ViewModel → لا يومض الديالوج عند عنصر واحد ★
 *
 * ★ إصلاح اللاج (Global State Interception):
 *   بدلاً من إدارة mIsBatchOperationRunning يدوياً من showRestoreProgressAndExecute
 *   وشowDeleteProgressAndExecute، يراقب الآن BatchOperationState.getIsProcessing() —
 *   إشارة مرور عامة تُفعَّل من Repository مباشرةً. هذا يضمن أن أي Fragment يُنشأ
 *   أثناء عملية جارية (مثل الانتقال من الخطوط المحلية إلى سلة المحذوفات) يحجز
 *   بياناته فوراً بدلاً من تحديث الواجهة عنصراً تلو الآخر مما يُسبب اللاج. ★
 *
 * ★ إصلاح المشكلة (2): إظهار الإشعار فور بدء العملية ★
 *   سابقاً: الإشعار لا يظهر إلا عند ضغط "إخفاء الإطار المنبثق".
 *   الآن:   الإشعار يُنشأ مباشرةً قبل عرض الديالوج في showRestoreProgressAndExecute
 *           وشowDeleteProgressAndExecute، بغض النظر عن أي زر يضغطه المستخدم.
 *
 * ★ إصلاح المشكلة (3): فتح الديالوج عند الضغط على الإشعار ★
 *   عند ضغط المستخدم على الإشعار يُفتح التطبيق وينتقل لشاشة السلة (عبر MainActivity).
 *   checkAndReopenProgressDialogPublic() تُستدعى من onResume() وonHiddenChanged(false)
 *   لتتحقق من وجود عملية جارية وتُعيد عرض الديالوج تلقائياً.
 *
 * ★ إصلاح المشكلة (1) — الجذر الحقيقي:
 *   reconnectToProgressDialog() تقرأ التقدم من BatchOperationState العالمية بدلاً
 *   من ViewModel المحلي الذي يُدمَّر عند قتل التطبيق وإعادة فتحه من الإشعار،
 *   مما كان يُفشل فتح الديالوج بسبب lastProgress == null. ★
 *
 * ★ إصلاح تجمّد شريط التقدم عند العودة من الإشعار بعد قتل التطبيق ★
 *   setupViewModelObservers() تراقب الآن BatchOperationState.getProgress() العالمية
 *   إضافةً إلى mViewModel.getOperationProgressLiveData() المحلي. هذا يضمن أن
 *   الديالوج المُعاد فتحه يستلم التحديثات الحية من عملية الخلفية حتى بعد تدمير
 *   الـ ViewModel القديم وإنشاء ViewModel جديد فارغ — وهو السبب الجذري للتجمّد.
 *
 * ★ إصلاح تشتت العنوان الفرعي (توحيد السلوك):
 *   mPendingTrashCount → يحجز آخر عدد قادم من LiveData أثناء عملية جارية.
 *   يُطبَّق على العنوان الفرعي فور انتهاء العملية ليتزامن مع تحديث القائمة،
 *   بدلاً من تغيير الرقم أمام عين المستخدم أثناء عرض ديالوج التقدم.
 *
 * ★ الإصلاح الجوهري (الأرقام السحرية → AppScreen):
 *   بدلاً من: updateFontsCount(FRAGMENT_INDEX, count) حيث FRAGMENT_INDEX = 5
 *   نستخدم:  updateFontsCount(AppScreen.TRASH, count)
 *
 *   بدلاً من: getSourceFragmentIndex() != 5
 *   نستخدم:  getSourceScreen() != AppScreen.TRASH
 *
 *   الفائدة: حذف HomeFragment أو تغيير ترتيب الشاشات لا يُغيّر أرقام الشاشات
 *   الأخرى، لأن كل شاشة تُعرِّف نفسها باسمها (TRASH) لا برقمها (5).
 * ════════════════════════════════════════════════════════════════════════
 *
 * الملفات المطلوبة خارج هذا الـ Fragment:
 *   • res/menu/menu_trash_more.xml:
 *       <item android:id="@+id/action_empty_trash"
 *             android:title="@string/action_empty_trash"
 *             android:icon="@drawable/ic_oui_more"
 *             app:showAsAction="always" />
 *
 *   • strings.xml (إضافة):
 *       <string name="action_empty_trash">Empty</string>
 *       (العربية) <string name="action_empty_trash">إفراغ</string>
 *
 * المسار: app/src/main/java/com/example/oneuiapp/fragment/TrashFragment.java
 */
public class TrashFragment extends Fragment implements AppBarLayout.OnOffsetChangedListener {

    private static final String TAG = "TrashFragment";

    // ─────────────────────────────────────────────────────────
    // حقول الـ Fragment
    // ─────────────────────────────────────────────────────────

    private Context      mContext;
    private Handler      mMainHandler;

    // Views
    private RecyclerView mRecyclerView;
    private View         mMainContentLayout;
    private View         mEmptyView;

    // OneUI Layout
    private DrawerLayout mDrawerLayout;
    private AppBarLayout mAppBarLayout;

    // طبقات البيانات والمنطق
    private TrashListAdapter       mAdapter;
    private TrashSelectionManager  mSelectionManager;
    private TrashViewModel         mViewModel;

    /**
     * ★ مدير حالة الواجهة — يتولى:
     *   1. تحريك الشاشة الفارغة جزئياً عند تمرير الـ AppBar (سلوك سامسونج)
     *   2. إظهار/إخفاء عناصر الحالة الفارغة (emptyView ← emptyTitle + emptyText)
     * متوافق مع النمط المُطبَّق في LocalFontListFragment وFavoriteFontListFragment.
     */
    private FontUIStateManager mUIManager;

    // ─────────────────────────────────────────────────────────
    // حالة ديالوج التقدم
    // ─────────────────────────────────────────────────────────

    /** مرجع ديالوج التقدم الجاري عرضه — null عندما لا توجد عملية جارية */
    @Nullable
    private ProgressDialog mCurrentProgressDialog;

    /**
     * true إذا ضغط المستخدم على "إخفاء الإطار المنبثق":
     *   • نُحدّث الإشعار بدلاً من الديالوج.
     *   false (الافتراضي): الديالوج مرئي أو العملية لم تبدأ بعد.
     */
    private boolean mIsDialogHidden = false;

    /**
     * نوع العملية الجارية — يُستخدم لتحديد الإشعار الصحيح
     * للتحديث عند إخفاء الديالوج وللإلغاء عند انتهاء العملية.
     */
    @Nullable
    private TrashViewModel.OperationType mCurrentOperationType;

    // ─────────────────────────────────────────────────────────
    // ★ متغيرات تأجيل تحديث الواجهة أثناء ديالوج التقدم ★
    // ─────────────────────────────────────────────────────────

    /**
     * true إذا كانت عملية (استعادة أو حذف) جارية والديالوج مفتوح.
     * يمنع getTrashedFontsLiveData من تحديث الـ Adapter مباشرةً،
     * ويحجز البيانات في mPendingTrashUpdate بدلاً من ذلك.
     *
     * ★ بعد إصلاح اللاج: هذا المتغير لم يعد يُضبط يدوياً من showRestoreProgressAndExecute
     *   وشowDeleteProgressAndExecute، بل يُحدَّث تلقائياً من مراقب
     *   BatchOperationState.getIsProcessing() الذي يستقبل الإشارة مباشرةً من
     *   TrashRepository عبر خيط الخلفية. ★
     */
    private boolean mIsBatchOperationRunning = false;

    /**
     * يحتجز آخر تحديث للقائمة القادم من LiveData أثناء عملية جارية.
     * يُطبَّق على الـ Adapter فور إغلاق الديالوج لتبدأ
     * حركة الاختفاء/الصعود دفعةً واحدة بلا وميض متتابع.
     */
    @Nullable
    private List<FontEntity> mPendingTrashUpdate = null;

    /**
     * يحتجز آخر تحديث لعدد الملفات القادم من LiveData أثناء عملية جارية.
     * يُطبَّق على العنوان الفرعي فور إغلاق الديالوج ليتزامن مع تحديث القائمة.
     */
    @Nullable
    private Integer mPendingTrashCount = null;

    /**
     * ★ الإصلاح (المشكلة الثالثة): حفظ عدد عناصر السلة لاستخدامه في onPrepareOptionsMenu ★
     *
     * لا يمكن الاعتماد على getTrashedFontsCountLiveData().getValue() داخل
     * onCreateOptionsMenu لأن LiveData تعمل بشكل غير متزامن (Asynchronous)،
     * وعادةً ما تكون قيمتها null لحظة بناء القائمة لأول مرة.
     * الحل: حفظ العدد هنا كلما وصل من المراقب، ثم قراءته في onPrepareOptionsMenu.
     */
    private int mTrashCount = 0;

    // ════════════════════════════════════════════════════════════════════════
    // دورة حياة الـ Fragment
    // ════════════════════════════════════════════════════════════════════════

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mContext = context;

        // ★ إنشاء FontUIStateManager مبكراً في onAttach() — متوافق مع نمط LocalFontListFragment ★
        // setDefaultEmptyMessage() تُخصّص رسالة الشاشة الفارغة لسلة المحذوفات بدلاً من
        // الرسالة الافتراضية (font_fragment_empty_message) المخصصة للمجلد المحلي.
        mUIManager = new FontUIStateManager(mContext);
        mUIManager.setDefaultEmptyMessage(R.string.trash_empty_description);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ★ المرحلة الأولى من خطة التحسين: إعلام النظام أن هذا الـ Fragment يمتلك أيقونات AppBar خاصة به ★
        // يضمن هذا استدعاء onCreateOptionsMenu() عند ظهور الـ Fragment
        // وإخفاء الأيقونات تلقائياً عند إخفائه عبر setMenuVisibility() في onHiddenChanged().
        // زر إفراغ السلة (action_empty_trash) يُدار الآن من onCreateOptionsMenu() هذا الـ Fragment
        // بدلاً من إدارته مركزياً في MainActivity، مما يجعل دورة حياته مرتبطة بدورة حياة الـ Fragment.
        setHasOptionsMenu(true);

        mMainHandler = new Handler(Looper.getMainLooper());
        mViewModel   = new ViewModelProvider(this).get(TrashViewModel.class);
    }

    /**
     * ★ المرحلة الأولى: نفخ أيقونات هذا الـ Fragment في AppBar ★
     *
     * ينفخ قائمة المزيد (menu_trash_more) التي تحتوي زر إفراغ السلة.
     * menu.clear() يضمن نظافة القائمة قبل كل نفخ جديد.
     *
     * ★ الإصلاح (المشكلة الثالثة): تم حذف كود التحقق من الظهور (Visibility) من هنا
     *   ونقله إلى onPrepareOptionsMenu() وهو المكان الصحيح للتحكم الديناميكي.
     *   السبب: onCreateOptionsMenu تُستدعى مرة واحدة فقط عند البناء، ووقتها تكون
     *   قيمة LiveData لم تصل بعد (null)، فلا يظهر الزر أبداً حتى لو كانت السلة
     *   تحتوي على عناصر. onPrepareOptionsMenu تُستدعى في كل مرة نستدعي فيها
     *   invalidateOptionsMenu() وبالتالي تعمل بالقيمة الحقيقية المحفوظة في mTrashCount. ★
     */
    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        menu.clear(); // تنظيف أي قوائم سابقة
        // ★ نفخ قائمة المزيد الخاصة بسلة المحذوفات ★
        inflater.inflate(R.menu.menu_trash_more, menu);

        // تم حذف كود الرؤية (Visibility) من هنا، وتم نقله إلى onPrepareOptionsMenu()
        super.onCreateOptionsMenu(menu, inflater);
    }

    /**
     * ★ الإصلاح (المشكلة الثالثة) + إصلاح زر الثلاث نقاط أثناء الاستعادة:
     *   التحكم الديناميكي في ظهور زر الثلاث نقاط ★
     *
     * onPrepareOptionsMenu هي المكان الصحيح للتحكم الديناميكي بظهور عناصر القائمة.
     * تُستدعى في كل مرة يستدعي فيها الكود getActivity().invalidateOptionsMenu()،
     * على عكس onCreateOptionsMenu التي تُستدعى مرة واحدة فقط عند البناء الأول.
     *
     * منطق الإظهار والإخفاء:
     *   - mTrashCount > 0 و لا توجد عملية جارية → يُظهر زر الإفراغ → النظام يُظهر زر الثلاث نقاط تلقائياً
     *   - mTrashCount == 0 أو توجد عملية جارية → يُخفي زر الإفراغ → النظام يُخفي زر الثلاث نقاط تلقائياً
     *
     * mTrashCount يُحدَّث في مراقب getTrashedFontsCountLiveData() كلما تغيّر عدد
     * العناصر، ثم يستدعي invalidateOptionsMenu() لإعادة رسم القائمة بالقيمة الصحيحة.
     *
     * ★ الإصلاح: إضافة شرط !mIsBatchOperationRunning لمنع ظهور الزر أثناء العمليات الجارية.
     *   يُطبَّق أيضاً setEnabled(shouldShow) لقتل اللمس الشبحي تماماً. ★
     */
    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        super.onPrepareOptionsMenu(menu);

        MenuItem emptyTrashItem = menu.findItem(R.id.action_empty_trash);
        if (emptyTrashItem != null) {
            // ★ يظهر زر الإفراغ فقط إذا كان:
            // 1. العدد أكبر من صفر.
            // 2. لا توجد عملية (استعادة أو حذف) جارية في الخلفية. ★
            boolean shouldShow = (mTrashCount > 0) && !mIsBatchOperationRunning;

            emptyTrashItem.setVisible(shouldShow);
            emptyTrashItem.setEnabled(shouldShow);
        }
    }

    /**
     * ★ المرحلة الأولى: معالجة نقرات أيقونات هذا الـ Fragment ★
     *
     * عند الضغط على زر إفراغ السلة (action_empty_trash)،
     * يُستدعى handleEmptyTrash() لعرض ديالوج التأكيد ثم تنفيذ العملية.
     */
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_empty_trash) {
            // ★ إفراغ سلة المحذوفات بالكامل بعد تأكيد المستخدم ★
            handleEmptyTrash();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_trash, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initViews(view);
        setupDrawerLayout();
        setupRecyclerView();
        initSelectionManager();
        setupViewModelObservers();
    }

    // ════════════════════════════════════════════════════════════════════════
    // تهيئة العناصر البصرية
    // ════════════════════════════════════════════════════════════════════════

    private void initViews(@NonNull View view) {
        mRecyclerView      = view.findViewById(R.id.trash_recycler_view);
        mMainContentLayout = view.findViewById(R.id.main_content_layout);
        mEmptyView         = view.findViewById(R.id.empty_view);

        // ★ ربط FontUIStateManager بعناصر الشاشة الفارغة ★
        // النسخة الثلاثية المعاملات مخصصة للـ Fragments التي لا تحتاج اختيار مجلد،
        // كما هو موثَّق في FontUIStateManager — نمط الاستخدام لـ TrashFragment.
        // setNoResultsTextView() لا تُستدعى لأن سلة المحذوفات لا تحتوي وظيفة بحث.
        TextView emptyTitleView = view.findViewById(R.id.empty_title);
        TextView emptyTextView  = view.findViewById(R.id.empty_text);

        mUIManager.setViews(mEmptyView, emptyTextView, mRecyclerView);
        mUIManager.setEmptyTitleView(emptyTitleView);
    }

    private void setupDrawerLayout() {
        if (getActivity() == null) return;
        View drawerView = getActivity().findViewById(R.id.drawer_layout);
        if (drawerView instanceof DrawerLayout) {
            mDrawerLayout = (DrawerLayout) drawerView;
            mAppBarLayout = mDrawerLayout.getAppBarLayout();
            if (mAppBarLayout != null) {
                mAppBarLayout.addOnOffsetChangedListener(this);

                // ★ تمرير AppBarLayout لـ FontUIStateManager ★
                // ضروري لحساب الإزاحة الصحيحة داخل updateEmptyViewPosition()
                // التي تُشغَّل من onOffsetChanged() أثناء التمرير.
                mUIManager.setAppBarLayout(mAppBarLayout);
            }
        }
    }

    private void setupRecyclerView() {
        mAdapter = new TrashListAdapter(mContext);

        mRecyclerView.setLayoutManager(new LinearLayoutManager(mContext));
        mRecyclerView.setAdapter(mAdapter);

        // إعدادات OneUI القياسية للـ RecyclerView — متطابقة مع قوائم الخطوط الأخرى
        mRecyclerView.seslSetFillBottomEnabled(false);
        mRecyclerView.seslSetLastRoundedCorner(false);
        mRecyclerView.seslSetFastScrollerEnabled(false);
        mRecyclerView.seslSetGoToTopEnabled(true);
        mRecyclerView.seslSetSmoothScrollEnabled(true);

        // ★ تهيئة الأنيميشن بعد ضبط كل إعدادات الـ RecyclerView ★
        setupRecyclerViewAnimator();
    }

    /**
     * ★ دالة مركزية لتهيئة أنيميشن الـ RecyclerView ★
     * تُستدعى عند الإنشاء الأول وعند العودة للشاشة بعد إيقاف الأنيميشن.
     *
     * ★ إصلاح الأنيميشن الجماعي (الخطوة الأولى من خطة الإصلاح):
     *   setRemoveDuration(0) → يُختفي العناصر المحذوفة/المستعادة فوراً بلا تأخير،
     *   ثم تصعد العناصر المتبقية بأنيميشن سلس عبر setMoveDuration(250).
     *   هذا يمنع الوميض المتتابع عند معالجة مئات العناصر دفعةً واحدة. ★
     */
    private void setupRecyclerViewAnimator() {
        if (mRecyclerView == null) return;
        androidx.recyclerview.widget.DefaultItemAnimator animator =
            new androidx.recyclerview.widget.DefaultItemAnimator();
        animator.setAddDuration(150);
        // ★ صفر: اختفاء فوري للعناصر بلا وميض متتابع ★
        animator.setRemoveDuration(250);
        // ★ 250ms: صعود سلس للعناصر المتبقية لتملأ الفراغ ★
        animator.setMoveDuration(250);
        animator.setSupportsChangeAnimations(false);
        mRecyclerView.setItemAnimator(animator);
    }

    // ════════════════════════════════════════════════════════════════════════
    // ★ (13) إعداد التحديد المتعدد
    // ════════════════════════════════════════════════════════════════════════

    private void initSelectionManager() {
        if (mDrawerLayout == null || mAdapter == null || mRecyclerView == null) return;

        mSelectionManager = new TrashSelectionManager(
                requireActivity(),
                mDrawerLayout,
                mAdapter,
                mRecyclerView
        );

        // ربط مستمع الـ Adapter بالـ SelectionManager
        mAdapter.setSelectionListener(new TrashListAdapter.OnSelectionListener() {
            @Override
            public void onStartSelection(int adapterPosition) {
                mSelectionManager.setSelecting(true);
                mSelectionManager.toggleSelection(adapterPosition);
            }

            @Override
            public void onToggleSelection(int adapterPosition) {
                mSelectionManager.toggleSelection(adapterPosition);
            }
        });

        // ربط مستمع إجراءات الـ SelectionManager بالـ Fragment
        mSelectionManager.setActionListener(new TrashSelectionManager.SelectionActionListener() {

            /** يُستدعى عند ضغط زر الاستعادة في شريط الـ Action Mode */
            @Override
            public void onRestoreRequested(List<FontEntity> fonts) {
                handleRestoreAction(fonts);
            }

            /**
             * يُستدعى عند ضغط زر الحذف النهائي في شريط الـ Action Mode.
             * يعرض ديالوج التأكيد (الملاحظة 17) قبل تنفيذ العملية.
             */
            @Override
            public void onDeletePermanentlyRequested(List<FontEntity> fonts) {
                handleDeletePermanentlyAction(fonts);
            }
        });

        // تسجيل معالج زر الرجوع لإنهاء وضع التحديد بضغطة Back
        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                mSelectionManager.getOnBackPressedCallback()
        );
    }

    // ════════════════════════════════════════════════════════════════════════
    // ★ مراقبة ViewModel
    // ════════════════════════════════════════════════════════════════════════

    private void setupViewModelObservers() {

        // ★ قائمة عناصر السلة: تحديث الـ Adapter وحالة الشاشة
        mViewModel.getTrashedFontsLiveData().observe(getViewLifecycleOwner(), fonts -> {
            if (fonts == null) return;

            // ★ إذا كانت عملية جارية، نحجز البيانات ولا نُحدِّث الـ Adapter ★
            // يضمن هذا أن اختفاء العناصر يبدأ دفعةً واحدة فقط بعد إغلاق الديالوج،
            // بدلاً من وميض متتابع عنصراً تلو الآخر أثناء جريان العملية.
            if (mIsBatchOperationRunning) {
                mPendingTrashUpdate = new ArrayList<>(fonts);
                return;
            }

            boolean isEmpty = fonts.isEmpty();
            updateEmptyState(isEmpty);
            // submitList تعمل بكفاءة عبر DiffUtil حتى مع قائمة فارغة
            mAdapter.submitList(isEmpty ? null : fonts);

            Log.d(TAG, "Trash list updated: " + fonts.size() + " items");
        });

        // ★ (11) عدد عناصر السلة: تحديث العنوان الفرعي في CollapsingToolbar ★
        // ★ الإصلاح (المشكلة الثالثة): حفظ العدد في mTrashCount واستدعاء invalidateOptionsMenu() ★
        // هذا يضمن أن onPrepareOptionsMenu تعمل بالقيمة الحقيقية في كل مرة يتغيّر
        // فيها عدد العناصر، بدلاً من الاعتماد على getValue() غير المتزامن في onCreateOptionsMenu.
        mViewModel.getTrashedFontsCountLiveData().observe(getViewLifecycleOwner(), count -> {
            if (count != null) {
                // 1. حفظ العدد الحالي
                mTrashCount = count;

                // 2. إجبار القائمة على التحديث لتُظهر أو تُخفي زر الثلاث نقاط فوراً
                if (getActivity() != null) {
                    getActivity().invalidateOptionsMenu();
                }

                // ★ إذا كانت عملية جارية، نحجز العدد ولا نُحدِّث العنوان الفرعي ★
                // يضمن هذا عدم تغيير الرقم أمام عين المستخدم أثناء عمل الديالوج
                if (mIsBatchOperationRunning) {
                    mPendingTrashCount = count;
                    return;
                }
                updateSubtitle(count);
            }
        });

        // ★ (18)(19) تقدم العملية الجارية: تحديث ديالوج التقدم أو الإشعار
        mViewModel.getOperationProgressLiveData().observe(getViewLifecycleOwner(), progress -> {
            if (progress == null) return;

            if (mCurrentProgressDialog != null && mCurrentProgressDialog.isShowing()) {
                // الديالوج مرئي → تحديثه مباشرةً
                // مكتبة OneUI تعرض X/Y والنسبة المئوية تلقائياً (الملاحظة 28)
                mCurrentProgressDialog.setProgress(progress.current);

            } else if (mIsDialogHidden) {
                // المستخدم أخفى الديالوج → تحديث الإشعار في شريط الحالة
                // ملاحظة: الـ ViewModel أيضاً يُحدِّث الإشعار من خيط الخلفية (إصلاح م5)
                // هذا الاستدعاء احتياطي للحالات التي يكون فيها الـ Fragment في المقدمة
                updateNotificationForOperation(
                        progress.operationType, progress.current, progress.total);
            }
        });

        // ★ (18)(19) نتيجة العملية عند اكتمالها: إخفاء الديالوج وإلغاء الإشعار
        mViewModel.getOperationResultLiveData().observe(getViewLifecycleOwner(), result -> {
            if (result == null) return;

            // إغلاق ديالوج التقدم إن كان مرئياً
            if (mCurrentProgressDialog != null && mCurrentProgressDialog.isShowing()) {
                mCurrentProgressDialog.dismiss();
            }
            mCurrentProgressDialog = null;

            // إلغاء إشعار العملية المنتهية
            dismissNotificationForOperation(result.operationType);

            // إعادة تعيين حالة الديالوج
            mIsDialogHidden       = false;
            mCurrentOperationType = null;

            // ★ ضروري: إعلام ViewModel بأن النتيجة استُهلكت
            // يمنع إعادة معالجتها عند دوران الشاشة أو استئناف الـ Fragment
            mViewModel.clearOperationResult();

            Log.d(TAG, "Operation complete: " + result.operationType
                    + " | succeeded=" + result.succeeded
                    + " | failed=" + result.failed
                    + " | cancelled=" + result.wasCancelled);
            // ★ لا حاجة لتطبيق mPendingTrashUpdate هنا يدوياً ★
            // مراقب BatchOperationState.getIsProcessing() أدناه يتولى ذلك
            // تلقائياً فور أن يُرسل TrashRepository إشارة false عند انتهاء العملية.
        });

        // ★ مراقب الإشارة العامة للعمليات الضخمة ★
        // يستجيب لأي عملية ضخمة في التطبيق بالكامل بغض النظر عن الشاشة التي بدأت منها.
        // هذا يحل مشكلة اللاج عند الانتقال من الخطوط المحلية/المفضلة إلى سلة المحذوفات
        // أثناء عملية جارية: كان Fragment جديد يُنشأ بـ mIsBatchOperationRunning=false
        // فيستقبل مئات التحديثات متتاليةً مما يُجمّد المعالج. الآن يرصد الإشارة العامة
        // ويحجز البيانات بصمت حتى تنتهي العملية ثم يطبّقها دفعةً واحدة.
        BatchOperationState.getIsProcessing().observe(
                getViewLifecycleOwner(), isProcessing -> {
            mIsBatchOperationRunning = isProcessing;

            // ★ الإصلاح هنا: إجبار القائمة على التحديث فور بدء أو انتهاء العملية ★
            // هذا سيستدعي onPrepareOptionsMenu لإخفاء/إظهار زر الثلاث نقاط برمجياً
            if (getActivity() != null) {
                getActivity().invalidateOptionsMenu();
            }

            // ★ إغلاق الديالوج فور انتهاء العملية (يحل المشكلة 3) ★
            if (!isProcessing && mCurrentProgressDialog != null && mCurrentProgressDialog.isShowing()) {
                mCurrentProgressDialog.dismiss();
                mCurrentProgressDialog = null;
            }

            if (!isProcessing && mPendingTrashUpdate != null) {
                // العملية انتهت! نُطبق التحديث المحجوز دفعة واحدة ليعمل أنيميشن واحد فقط
                boolean isEmpty = mPendingTrashUpdate.isEmpty();
                updateEmptyState(isEmpty);
                mAdapter.submitList(isEmpty ? null : mPendingTrashUpdate);
                mPendingTrashUpdate = null;
            }

            if (!isProcessing && mPendingTrashCount != null) {
                // العملية انتهت! نُطبق العدد المحجوز دفعة واحدة ليتزامن مع القائمة
                updateSubtitle(mPendingTrashCount);
                mPendingTrashCount = null;
            }
        });

        // ★ الإضافة الجوهرية لحل مشكلة تجمد شريط التقدم عند العودة من الإشعار ★
        // مراقبة التقدم من الملف العالمي المستقل الذي ينجو من قتل التطبيق وإعادة بنائه.
        // هذا يضمن أن الديالوج المُعاد فتحه يستلم التحديثات الحية من عملية الخلفية.
        BatchOperationState.getProgress().observe(
                getViewLifecycleOwner(), progressData -> {
            if (mCurrentProgressDialog != null && mCurrentProgressDialog.isShowing() && progressData != null) {
                mCurrentProgressDialog.setMax(progressData.total);
                mCurrentProgressDialog.setProgress(progressData.current);
            }
        });
    }

    // ════════════════════════════════════════════════════════════════════════
    // ★ (10) إفراغ سلة المحذوفات — يُستدعى من onOptionsItemSelected
    // ════════════════════════════════════════════════════════════════════════

    /**
     * ★ (10) إفراغ سلة المحذوفات بالكامل.
     *
     * تُستدعى هذه الدالة من onOptionsItemSelected() عند ضغط المستخدم على زر
     * "إفراغ" (action_empty_trash) في الـ Toolbar. الوصول public لأن MainActivity
     * في حزمة مختلفة (activity) عن هذا الـ Fragment (fragment).
     *
     * يعرض ديالوج تأكيد الحذف النهائي (الملاحظة 17) قبل تنفيذ العملية.
     * إذا كانت السلة فارغة لا يفعل شيئاً (حماية دفاعية — لا يجب أن يحدث
     * لأن الزر يُخفى عندما تكون السلة فارغة).
     */
    public void handleEmptyTrash() {
        List<FontEntity> allFonts = mAdapter.getAllFonts();
        if (allFonts.isEmpty()) return;

        // ★ (17) ديالوج تأكيد الحذف النهائي بزر "حذف" أحمر (TrashActionDialogs)
        TrashActionDialogs.showDeletePermanentlyDialog(
                mContext,
                allFonts.size(),
                () -> showDeleteProgressAndExecute(allFonts, true)  // isEmptyAll = true
        );
    }

    /**
     * ★ (13)(19) استعادة الملفات المحددة من سلة المحذوفات.
     *
     * لا يتطلب ديالوج تأكيد — العملية آمنة وقابلة للتراجع.
     * يُغلق وضع التحديد فوراً قبل عرض ديالوج التقدم.
     *
     * @param fonts قائمة الخطوط المراد استعادتها (مُجمَّعة مسبقاً في SelectionManager)
     */
    private void handleRestoreAction(@NonNull List<FontEntity> fonts) {
        if (fonts.isEmpty()) return;

        // إغلاق وضع التحديد قبل عرض الديالوج لتجنب تعارض الواجهات
        mSelectionManager.setSelecting(false);

        showRestoreProgressAndExecute(fonts);
    }

    /**
     * ★ (13)(17)(19) حذف الملفات المحددة نهائياً من سلة المحذوفات.
     *
     * يعرض ديالوج التأكيد أولاً (الملاحظة 17)، ثم يُغلق وضع التحديد
     * ويعرض ديالوج التقدم عند موافقة المستخدم.
     *
     * @param fonts قائمة الخطوط المراد حذفها نهائياً
     */
    private void handleDeletePermanentlyAction(@NonNull List<FontEntity> fonts) {
        if (fonts.isEmpty()) return;

        // ★ (17) ديالوج تأكيد: بدون عنوان، رسالة Plurals، زر "حذف" أحمر
        TrashActionDialogs.showDeletePermanentlyDialog(
                mContext,
                fonts.size(),
                () -> {
                    // إغلاق وضع التحديد بعد الموافقة، قبل عرض ديالوج التقدم
                    mSelectionManager.setSelecting(false);
                    showDeleteProgressAndExecute(fonts, false);  // isEmptyAll = false
                }
        );
    }

    // ════════════════════════════════════════════════════════════════════════
    // ★ (18)(19) ديالوجات التقدم وتنفيذ العمليات
    // ════════════════════════════════════════════════════════════════════════

    /**
     * ★ (19) ينشئ ويعرض ديالوج تقدم الاستعادة ثم يبدأ العملية.
     *
     * تصميم الديالوج مستلهم من AppPickerActivity.loadApps() المرفق:
     *   • STYLE_HORIZONTAL — مكتبة OneUI تعرض X/Y والنسبة المئوية تلقائياً
     *   • setCancelable(false) — لا يُغلق بالـ Back أو الضغط خارجه
     *   • زر "إلغاء"  → يوقف العملية ويُغلق الديالوج
     *   • زر "إخفاء"  → يُغلق الديالوج، العملية تكمل، الإشعار يتولى الإبلاغ
     *
     * ★ إصلاح (2): إظهار الإشعار فور بدء العملية (قبل dialog.show()) ★
     *   سابقاً: الإشعار لا يظهر إلا عند ضغط "إخفاء الإطار المنبثق".
     *   الآن: الإشعار يُنشأ مباشرةً قبل عرض الديالوج بغض النظر عن أي زر يضغطه المستخدم.
     *   الـ ViewModel أيضاً يُظهر الإشعار من خيط الخلفية — هذا الاستدعاء يضمن
     *   الظهور الفوري على الخيط الرئيسي قبل انتقال الـ Fragment لحالة أخرى.
     *
     * ★ إصلاح اللاج (Global State Interception):
     *   لم يعد هذا الـ Fragment يضبط mIsBatchOperationRunning يدوياً.
     *   TrashRepository يُفعّل BatchOperationState.setProcessing(true) عند بدء العملية،
     *   فيستجيب مراقب getIsProcessing() في setupViewModelObservers() ويضبط
     *   mIsBatchOperationRunning=true تلقائياً على جميع الشاشات بما فيها هذه.
     *   عند انتهاء العملية يُطبَّق mPendingTrashUpdate دفعةً واحدة بأنيميشن سلس. ★
     *
     * @param fonts قائمة الخطوط المراد استعادتها
     */
    private void showRestoreProgressAndExecute(@NonNull List<FontEntity> fonts) {
        mCurrentOperationType = TrashViewModel.OperationType.RESTORE;
        mIsDialogHidden       = false;

        mCurrentProgressDialog = TrashActionDialogs.createRestoreProgressDialog(
                mContext,
                fonts.size(),

                // ★ زر "إلغاء": يوقف حلقة الخلفية في TrashRepository
                () -> {
                    mViewModel.cancelCurrentOperation();
                    mIsDialogHidden = false;
                    Log.d(TAG, "Restore operation cancelled by user");
                },

                // ★ زر "إخفاء الإطار المنبثق": يُغلق الديالوج والعملية تكمل
                // الإشعار كان موجوداً بالفعل (أُنشئ قبل الديالوج) — هذا الاستدعاء
                // يُحدِّثه بآخر قيمة عداد احتياطاً لأي حالة استثنائية
                () -> {
                    mIsDialogHidden = true;
                    TrashActionDialogs.showRestoreNotification(mContext, fonts.size());
                    Log.d(TAG, "Restore dialog hidden, notification continues");
                }
        );

        // ★ إصلاح (2): إظهار الإشعار فوراً — قبل عرض الديالوج ★
        // يضمن وجود الإشعار في شريط الحالة بمجرد بدء العملية، سواء أغلق المستخدم
        // الديالوج أم أبقاه مفتوحاً أم خرج من التطبيق. هذا يُصلح السلوك السابق
        // الذي كان الإشعار فيه لا يظهر إلا عند الضغط على زر "إخفاء".
        TrashActionDialogs.showRestoreNotification(mContext, fonts.size());

        mCurrentProgressDialog.show();

        // بدء العملية بعد عرض الديالوج مباشرةً
        // النتيجة ستُعالَج في مراقب getOperationResultLiveData
        // ومراقب BatchOperationState سيطبّق mPendingTrashUpdate عند انتهاء العملية
        mViewModel.restoreFonts(fonts);
    }

    /**
     * ★ (18)(19) ينشئ ويعرض ديالوج تقدم الحذف النهائي ثم يبدأ العملية.
     *
     * يُستخدم لكل من:
     *   • الحذف الجزئي (isEmptyAll=false): يستدعي mViewModel.deletePermanently()
     *   • إفراغ السلة (isEmptyAll=true):   يستدعي mViewModel.emptyTrash()
     *
     * ★ إصلاح (2): إظهار الإشعار فور بدء العملية (قبل dialog.show()) ★
     *   نفس آلية الإصلاح في showRestoreProgressAndExecute — راجع توثيقه أعلاه.
     *
     * ★ إصلاح اللاج (Global State Interception):
     *   لم يعد هذا الـ Fragment يضبط mIsBatchOperationRunning يدوياً.
     *   TrashRepository يُفعّل BatchOperationState.setProcessing(true) عند بدء العملية،
     *   فيستجيب مراقب getIsProcessing() في setupViewModelObservers() ويضبط
     *   mIsBatchOperationRunning=true تلقائياً على جميع الشاشات بما فيها هذه.
     *   عند انتهاء العملية يُطبَّق mPendingTrashUpdate دفعةً واحدة بأنيميشن سلس. ★
     *
     * @param fonts      قائمة الخطوط المراد حذفها نهائياً
     * @param isEmptyAll true = إفراغ السلة بالكامل | false = حذف المحدد فقط
     */
    private void showDeleteProgressAndExecute(@NonNull List<FontEntity> fonts,
                                               boolean isEmptyAll) {
        mCurrentOperationType = isEmptyAll
                ? TrashViewModel.OperationType.EMPTY_TRASH
                : TrashViewModel.OperationType.DELETE_PERMANENTLY;
        mIsDialogHidden = false;

        mCurrentProgressDialog = TrashActionDialogs.createDeleteProgressDialog(
                mContext,
                fonts.size(),

                // ★ زر "إلغاء": يوقف حلقة الخلفية في TrashRepository
                () -> {
                    mViewModel.cancelCurrentOperation();
                    mIsDialogHidden = false;
                    Log.d(TAG, "Delete operation cancelled by user");
                },

                // ★ زر "إخفاء الإطار المنبثق": الإشعار كان موجوداً بالفعل — هذا تحديث احتياطي
                () -> {
                    mIsDialogHidden = true;
                    TrashActionDialogs.showDeleteNotification(mContext, fonts.size());
                    Log.d(TAG, "Delete dialog hidden, notification continues");
                }
        );

        // ★ إصلاح (2): إظهار الإشعار فوراً — قبل عرض الديالوج ★
        // يضمن وجود الإشعار في شريط الحالة بمجرد بدء عملية الحذف النهائي/إفراغ السلة،
        // سواء أغلق المستخدم الديالوج أم أبقاه مفتوحاً أم خرج من التطبيق.
        TrashActionDialogs.showDeleteNotification(mContext, fonts.size());

        mCurrentProgressDialog.show();

        // تنفيذ النوع الصحيح من العملية
        // النتيجة ستُعالَج في مراقب getOperationResultLiveData
        // ومراقب BatchOperationState سيطبّق mPendingTrashUpdate عند انتهاء العملية
        if (isEmptyAll) {
            mViewModel.emptyTrash();
        } else {
            mViewModel.deletePermanently(fonts);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // ★ (18)(19) إدارة الإشعارات
    // ════════════════════════════════════════════════════════════════════════

    /**
     * يُحدّث الإشعار المناسب بناءً على نوع العملية الجارية.
     * يُستدعى من مراقب OperationProgress عندما يكون الديالوج مخفياً.
     *
     * ملاحظة أذونات API 33+: NotificationManagerCompat.notify() يصمت
     * تلقائياً إذا لم يُمنح إذن POST_NOTIFICATIONS، دون رمي استثناء.
     * يُنصح بطلب الإذن في MainActivity عند الإطلاق الأول.
     */
    private void updateNotificationForOperation(
            @NonNull TrashViewModel.OperationType type, int current, int total) {
        switch (type) {
            case RESTORE:
                TrashActionDialogs.updateRestoreNotification(mContext, current, total);
                break;
            case DELETE_PERMANENTLY:
            case EMPTY_TRASH:
                TrashActionDialogs.updateDeleteNotification(mContext, current, total);
                break;
            case MOVE_TO_TRASH:
                // MOVE_TO_TRASH يُنفَّذ من LocalFontListFragment/FavoriteFontListFragment
                // لكن ندعمه هنا على سبيل الحيطة
                TrashActionDialogs.updateMoveToTrashNotification(mContext, current, total);
                break;
        }
    }

    /**
     * يُلغي إشعار العملية المنتهية.
     * يُستدعى من مراقب OperationResult عند اكتمال أي عملية أو إلغائها.
     */
    private void dismissNotificationForOperation(
            @NonNull TrashViewModel.OperationType type) {
        switch (type) {
            case RESTORE:
                TrashActionDialogs.dismissRestoreNotification(mContext);
                break;
            case DELETE_PERMANENTLY:
            case EMPTY_TRASH:
                TrashActionDialogs.dismissDeleteNotification(mContext);
                break;
            case MOVE_TO_TRASH:
                TrashActionDialogs.dismissMoveToTrashNotification(mContext);
                break;
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // ★ إصلاح (3): إعادة عرض الديالوج عند العودة من الإشعار
    // ════════════════════════════════════════════════════════════════════════

    /**
     * ★ إصلاح (3) + إصلاح السبب الجذري (1):
     *   التحقق من عملية جارية وإعادة عرض ديالوج التقدم إذا لزم ★
     *
     * تُستدعى من:
     *   - onResume() عند استئناف التطبيق من الخلفية (مثلاً: بعد الضغط على الإشعار)
     *   - onHiddenChanged(false) عند الانتقال لهذه الشاشة عبر درج التنقل
     *   - MainActivity عند الضغط على الإشعار وكانت الشاشة الصحيحة مفتوحة بالفعل
     *
     * الشروط لإعادة العرض:
     *   1. الـ Fragment مرئي (غير مخفي) — لمنع عرض الديالوج خلف شاشة أخرى
     *   2. العملية جارية في الإشارة العالمية BatchOperationState
     *   3. ★ الإصلاح الجوهري: العملية من AppScreen.TRASH بدلاً من getSourceFragmentIndex() != 5 ★
     *      يضمن صحة الفحص حتى لو تغيّر ترتيب الشاشات في مصفوفة mFragments
     *   4. الفتح تم عبر الإشعار (consumeShouldReopenDialog() = true)
     *   5. لا يوجد ديالوج تقدم مرئي حالياً
     *
     * ★ الفرق عن النسخة القديمة:
     *   النسخة القديمة كانت تستدعي reopenProgressDialog() التي تقرأ lastProgress
     *   من ViewModel المحلي — يُرجع null دائماً بعد قتل التطبيق وإعادة فتحه.
     *   النسخة الجديدة تستدعي reconnectToProgressDialog() التي تقرأ من
     *   BatchOperationState العالمية التي تنجو من قتل التطبيق. ★
     */
    public void checkAndReopenProgressDialogPublic() {
        // ★ الشرط 1: الـ Fragment مرئي فعلاً — لا نفتح الديالوج في الخلفية ★
        if (isHidden() || !isAdded() || mContext == null || mViewModel == null) return;

        Boolean isProcessing = BatchOperationState.getIsProcessing().getValue();
        if (!Boolean.TRUE.equals(isProcessing)) return;

        // ★ الإصلاح الجوهري: مقارنة بـ AppScreen.TRASH بدلاً من getSourceFragmentIndex() != 5 ★
        // يضمن صحة الفحص حتى لو حُذف HomeFragment أو تغيّر ترتيب الشاشات،
        // لأن AppScreen.TRASH ثابت لا يتأثر بموضع الـ Fragment في مصفوفة mFragments.
        if (BatchOperationState.getSourceScreen() != AppScreen.TRASH) return;

        if (!BatchOperationState.consumeShouldReopenDialog()) return;

        if (mCurrentProgressDialog != null && mCurrentProgressDialog.isShowing()) return;

        reconnectToProgressDialog();
    }

    /**
     * ★ إصلاح السبب الجذري (1): إعادة إنشاء الديالوج من الحالة العالمية ★
     *
     * يُستدعى من checkAndReopenProgressDialogPublic() عند العودة للتطبيق من الإشعار
     * بعد قتله وإعادة فتحه.
     *
     * ★ الجوهر: يقرأ التقدم من BatchOperationState.getProgress() العالمية
     *   بدلاً من ViewModel المحلي الذي يُنشأ جديداً فارغاً بعد قتل التطبيق.
     *   هذا يحل المشكلة الجذرية التي كانت تُفشل فتح الديالوج بسبب lastProgress == null.
     *
     * ★ حل مشكلة الترجمة (المشكلة 4): يستخدم operationCode المحفوظ في ProgressData
     *   لاستخلاص العنوان الصحيح من موارد السياق المحلي (getResources()) بدلاً من
     *   الاعتماد على النص المخزَّن في الحالة العالمية الذي قد يكون بلغة مختلفة. ★
     */
    private void reconnectToProgressDialog() {
        if (!isAdded() || mContext == null) return;

        // ★ إعادة تعيين علامة الإخفاء لأن الديالوج يظهر من جديد ★
        mIsDialogHidden = false;

        // ★ الاعتماد على الحالة العالمية المحفوظة بدلاً من ViewModel المحلي الذي يُدمَّر بعد قتل التطبيق ★
        BatchOperationState.ProgressData lastProgress =
                BatchOperationState.getProgress().getValue();

        if (lastProgress == null) return;

        mCurrentProgressDialog = new ProgressDialog(mContext);
        mCurrentProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mCurrentProgressDialog.setCancelable(false);

        // ★ حل مشكلة الترجمة (المشكلة 4): استخدام السياق المحلي مع كود العملية لمعرفة العنوان الصحيح ★
        String localTitle;
        if (lastProgress.operationCode == 2) {
            // كود 2 يعني استعادة
            localTitle = getResources().getQuantityString(R.plurals.progress_restoring, lastProgress.total);
        } else if (lastProgress.operationCode == 3) {
            // كود 3 يعني حذف نهائي
            localTitle = getResources().getQuantityString(R.plurals.progress_deleting, lastProgress.total);
        } else {
            // كود 1 أو افتراضي يعني نقل للسلة (احتياط)
            localTitle = getResources().getQuantityString(R.plurals.progress_moving_to_trash, lastProgress.total);
        }

        mCurrentProgressDialog.setTitle(localTitle);
        mCurrentProgressDialog.setMax(lastProgress.total);
        mCurrentProgressDialog.setProgress(lastProgress.current);

        // زر إلغاء
        mCurrentProgressDialog.setButton(ProgressDialog.BUTTON_NEGATIVE,
                getString(R.string.action_cancel), (dialog, which) -> {
                    BatchOperationState.requestCancel();
                    mViewModel.cancelCurrentOperation();
                    dialog.dismiss();
                });

        // زر إخفاء
        mCurrentProgressDialog.setButton(ProgressDialog.BUTTON_POSITIVE,
                getString(R.string.action_hide_dialog), (dialog, which) -> {
                    mIsDialogHidden = true;
                    dialog.dismiss();
                });

        mCurrentProgressDialog.show();
        Log.d(TAG, "reconnectToProgressDialog: progress dialog reopened successfully from global state");
    }

    // ════════════════════════════════════════════════════════════════════════
    // ★ (6) حالة الشاشة الفارغة
    // ════════════════════════════════════════════════════════════════════════

    /**
     * يُبدّل بين حالة القائمة وحالة الشاشة الفارغة.
     *
     * الشاشة الفارغة (fragment_trash.xml → empty_view) تحتوي على:
     *   • أيقونة ic_oui_delete_outline
     *   • عنوان "لا توجد ملفات" (R.string.trash_empty_title) — بالألون الأساسي
     *   • وصف رسالة الـ 30 يوماً (R.string.trash_empty_description) — باللون الثانوي
     *
     * ★ التعديل: تفويض إدارة emptyView وrecyclerView إلى FontUIStateManager ★
     * يبقى mMainContentLayout تحت السيطرة المباشرة لأن FontUIStateManager
     * لا يعلم بوجوده (لم يُمرَّر في setViews() الثلاثية المعاملات).
     *
     * @param isEmpty true = عرض الشاشة الفارغة | false = عرض القائمة
     */
    private void updateEmptyState(boolean isEmpty) {
        // ★ التحكم في mMainContentLayout يدوياً — FontUIStateManager لا يديره ★
        if (mMainContentLayout != null) {
            mMainContentLayout.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        }

        // ★ تفويض إظهار/إخفاء emptyView والـ recyclerView لـ FontUIStateManager ★
        // النسخة الأحادية المعامل تفترض دائماً عدم وجود بحث (صحيح لسلة المحذوفات)
        mUIManager.updateEmptyView(isEmpty);
    }

    // ════════════════════════════════════════════════════════════════════════
    // ★ (11) العنوان الفرعي في CollapsingToolbar
    // ════════════════════════════════════════════════════════════════════════

    /**
     * يُحدّث عدد الملفات في العنوان الفرعي (subtitle) لـ CollapsingToolbarLayout.
     *
     * ★ الإصلاح الجوهري (الخطوة الثالثة من خطة الإصلاح):
     *   تمرير AppScreen.TRASH بدلاً من FRAGMENT_INDEX (الذي كان = 5).
     *   يضمن تمييز هذا الفراجمنت بالاسم لا بالرقم، فلا يتأثر بتغيير ترتيب الشاشات
     *   أو بحذف HomeFragment أو إضافة شاشات جديدة.
     *
     * ★ منطق الصفر في الإنجليزية (الملاحظة 11):
     *   نظام الجمع الإنجليزي لا يدعم quantity="zero" في بعض إصدارات Android،
     *   مما يُؤدي إلى عرض "0 Fonts" بدلاً من "No fonts".
     *   الحل: تمرير العدد إلى MainActivity.updateFontsCount() التي تتعامل مع
     *   هذه الحالة باستخدام R.string.no_fonts_found عند count == 0.
     *
     * ★ تحديث زر إفراغ السلة:
     *   MainActivity.updateFontsCount() تستدعي invalidateOptionsMenu() عندما تكون
     *   شاشة السلة هي الشاشة الحالية، مما يُعيد بناء onPrepareOptionsMenu() في هذا الـ Fragment
     *   فيتحقق من mTrashCount ويُظهر أو يُخفي action_empty_trash وفقاً له.
     *
     * ★ الشرط الحارس: لا نُحدّث العنوان الفرعي أثناء وضع التحديد
     *   لمنع ظهور عدد الملفات فوق عدد العناصر المحددة.
     *
     * @param count عدد الخطوط الحالي في السلة
     */
    private void updateSubtitle(int count) {
        // ★ الحارس: لا تُحدّث العنوان الفرعي أثناء وضع التحديد المتعدد
        if (mSelectionManager != null && mSelectionManager.isSelecting()) return;

        if (getActivity() instanceof MainActivity) {
            // ★ الإصلاح الجوهري: AppScreen.TRASH بدلاً من الرقم المُشفَّر 5 ★
            // updateFontsCount() تتولى: (1) تحديث العنوان الفرعي في DrawerLayout
            //                          (2) تحديث ظهور زر إفراغ السلة عبر invalidateOptionsMenu()
            ((MainActivity) getActivity()).updateFontsCount(AppScreen.TRASH, count);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // AppBarLayout.OnOffsetChangedListener
    // ════════════════════════════════════════════════════════════════════════

    /**
     * يُستدعى عند تغيُّر إزاحة AppBarLayout أثناء التمرير.
     *
     * ★ الإصلاح: تفويض الحساب لـ FontUIStateManager.updateEmptyViewPosition() ★
     * هذا يُنفّذ سلوك سامسونج القياسي: عند تمرير الـ AppBar لأسفل وتوسيع العنوان،
     * تتحرك الشاشة الفارغة بمسافة أقل من مسافة الـ AppBar لتبقى في منتصف
     * المساحة المرئية دون أن تختفي — وهو السلوك الموجود في القوائم الأخرى.
     */
    @Override
    public void onOffsetChanged(AppBarLayout bar, int offset) {
        mUIManager.updateEmptyViewPosition(offset);
    }

    // ════════════════════════════════════════════════════════════════════════
    // دوال عامة يستدعيها NavManager
    // ════════════════════════════════════════════════════════════════════════

    /**
     * يُعالج ضغط زر الرجوع بتفويضه إلى SelectionManager.
     * يُستدعى من NavManager.handleBackPressed().
     *
     * @return true = استُهلك الحدث (كنا في وضع التحديد) | false = تجاهله
     */
    public boolean handleBackPressed() {
        if (mSelectionManager != null) return mSelectionManager.handleBackPress();
        return false;
    }

    // ════════════════════════════════════════════════════════════════════════
    // أحداث دورة حياة الـ Fragment
    // ════════════════════════════════════════════════════════════════════════

    @Override
    public void onResume() {
        super.onResume();

        // ★ إصلاح (3): إعادة عرض ديالوج التقدم عند استئناف التطبيق من الخلفية ★
        // يُغطي سيناريو: المستخدم يضغط على الإشعار → يُفتح التطبيق → MainActivity
        // تُنقله لشاشة السلة → onResume() يتحقق ويُعيد عرض الديالوج إذا كانت
        // العملية لا تزال جارية. يعمل أيضاً عند تدوير الشاشة مع عملية جارية.
        checkAndReopenProgressDialogPublic();

        // تحديث العنوان الفرعي عند استئناف التطبيق من الخلفية
        if (!isHidden()) {
            Integer count = mViewModel.getTrashedFontsCountLiveData().getValue();
            if (count != null) updateSubtitle(count);
        }
    }

    /**
     * يُستدعى عند التبديل بين الفراجمنتات في DrawerLayout.
     *
     * ★ المرحلة الأولى: إدارة ظهور أيقونات AppBar عند التنقل بين الشاشات ★
     * setMenuVisibility يُخفي أيقونات هذا الـ Fragment عند إخفائه ويُظهرها عند عودته،
     * وinvalidateOptionsMenu يُجبر الـ AppBar على إعادة رسم الأيقونات فور ظهور الشاشة.
     *
     * عند الإظهار: تحديث العنوان الفرعي + التحقق من عملية جارية.
     *   تحديث زر إفراغ السلة مُعالَج في updateFontsCount() التي تستدعيها updateSubtitle().
     * عند الإخفاء: إغلاق وضع التحديد إن كان نشطاً لمنع حالة
     *   تكون فيها Action Mode مفعّلة وهذا الـ Fragment غير مرئي.
     *
     * @param hidden true = الـ Fragment مخفي | false = الـ Fragment مرئي
     */
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);

        // ★ المرحلة الأولى: إدارة ظهور أيقونات AppBar عند التنقل بين الشاشات ★
        // setMenuVisibility يُخفي أيقونات هذا الـ Fragment عند إخفائه ويُظهرها عند عودته،
        // وinvalidateOptionsMenu يُجبر الـ AppBar على إعادة رسم الأيقونات فور ظهور الشاشة
        setMenuVisibility(!hidden);
        if (!hidden && getActivity() != null) {
            getActivity().invalidateOptionsMenu(); // إجبار الـ AppBar على التحديث
        }

        if (!hidden) {
            // ★ إصلاح (3): التحقق من عملية جارية عند الانتقال لهذه الشاشة ★
            // يُغطي سيناريو: عملية نقل/استعادة/حذف جارية والمستخدم ينتقل لشاشة
            // السلة — يجب عرض الديالوج تلقائياً لإتاحة الإلغاء أو الإخفاء.
            checkAndReopenProgressDialogPublic();
            Integer count = mViewModel.getTrashedFontsCountLiveData().getValue();
            if (count != null) updateSubtitle(count);
        } else {
            if (mSelectionManager != null && mSelectionManager.isSelecting()) {
                mSelectionManager.setSelecting(false);
            }
        }
    }

    /**
     * يُعيد تطبيق واجهة شريط الـ Action Mode بعد دوران الجهاز.
     * يُضمن صحة النصوص والحالة بعد إعادة بناء الـ Layout.
     */
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mSelectionManager != null) mSelectionManager.refreshActionMode();
    }

    // ════════════════════════════════════════════════════════════════════════
    // تنظيف الموارد
    // ════════════════════════════════════════════════════════════════════════

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // ★ تنظيف SelectionManager أولاً لمنع استخدامه بعد تدمير الـ View
        if (mSelectionManager != null) {
            mSelectionManager.cleanup();
            mSelectionManager = null;
        }

        // إزالة مستمع AppBarLayout لمنع تسرب الذاكرة
        if (mAppBarLayout != null) {
            mAppBarLayout.removeOnOffsetChangedListener(this);
            mAppBarLayout = null;
        }

        // إغلاق ديالوج التقدم إن كان لا يزال مفتوحاً عند تدمير الـ View
        // (مثلاً: إذا انتقل المستخدم لشاشة أخرى أثناء عملية جارية)
        if (mCurrentProgressDialog != null && mCurrentProgressDialog.isShowing()) {
            mCurrentProgressDialog.dismiss();
        }
        mCurrentProgressDialog = null;

        // ★ تنظيف البيانات المحجوزة لمنع تسرب الذاكرة ★
        mPendingTrashUpdate = null;
        mPendingTrashCount = null;
        mIsBatchOperationRunning = false;

        // تحرير المراجع لمنع تسرب الذاكرة
        mDrawerLayout = null;
        mRecyclerView = null;
        mAdapter      = null;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mContext = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mMainHandler != null) mMainHandler.removeCallbacksAndMessages(null);
    }
    }
