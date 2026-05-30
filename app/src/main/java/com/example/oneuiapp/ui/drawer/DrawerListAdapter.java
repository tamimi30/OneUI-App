package com.example.oneuiapp.ui.drawer;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import com.example.oneuiapp.R;
import com.example.oneuiapp.activity.AppScreen;

/**
 * DrawerListAdapter - محول قائمة الدرج
 *
 * ★ الخطوة الرابعة من خطة الإصلاح: فصل الدرج الجانبي عن المعمارية ★
 *
 * التعديلات المطبَّقة في هذا الملف:
 *
 * 1. استبدال List<Fragment> بـ List<AppScreen>:
 *    - يستقبل الـ Adapter الآن قائمة شاشات (AppScreen) بدلاً من قائمة فراغمنتات.
 *    - null في القائمة تُمثّل فاصلاً بصرياً بين مجموعات العناصر.
 *    - هذا يُلغي تماماً أي اعتماد على أنواع الفراغمنتات أو ترتيبها في الذاكرة.
 *
 * 2. تحديث DrawerListener:
 *    - onDrawerItemSelected(AppScreen) بدلاً من onDrawerItemSelected(int fragmentIndex).
 *    - المستدعي (MainActivity) يستقبل AppScreen ويقرر بنفسه:
 *        HOME → startActivity(HomeActivity)
 *        غيره → mNavManager.navigateFromDrawer(screen)
 *
 * 3. تحديث setSelectedItem:
 *    - setSelectedItem(AppScreen) بدلاً من setSelectedItem(int).
 *    - يُستدعى من NavManager و MainActivity مباشرةً بـ AppScreen.
 *    - التحديد البصري يعتمد على مقارنة AppScreen == AppScreen (لا أرقام).
 *
 * 4. استبدال instanceof Fragment بـ switch (AppScreen):
 *    - getIconForScreen(AppScreen) بدلاً من getIconForFragment(Fragment).
 *    - getTitleForScreen(AppScreen) بدلاً من getTitleForFragment(Fragment).
 *    - لا استيرادات لأي Fragment class في هذا الملف.
 *
 * 5. إزالة آلية toFragmentIndex/toDisplayPos:
 *    - لم تعد ضرورية لأن القائمة تُخزّن AppScreen مباشرةً،
 *      وإيجاد موضع AppScreen يتم بـ indexOfScreen() البسيطة.
 *
 * ★ الفائدة الجوهرية:
 *   الدرج الجانبي أصبح "قائمة أزرار" محضة — لا يعرف شيئاً عن الفراغمنتات
 *   المحملة في الذاكرة أو ترتيبها. يمكن حذف HomeFragment أو إضافة شاشة جديدة
 *   دون لمس هذا الملف أو إعادة بناء قائمة عرضه.
 *
 * ━━ ترتيب العرض في الدرج (يُحدَّد في MainActivity.setupDrawer()) ━━
 *   AppScreen.HOME
 *   AppScreen.FONT_VIEWER
 *   null  ← فاصل بين عارض الخطوط وقوائمها
 *   AppScreen.LOCAL_FONTS
 *   AppScreen.SYSTEM_FONTS
 *   AppScreen.FAVORITES
 *   null  ← فاصل بين قوائم الخطوط وسلة المحذوفات
 *   AppScreen.TRASH
 */
public class DrawerListAdapter extends RecyclerView.Adapter<DrawerListViewHolder> {

    // ════════════════════════════════════════════════════════
    //  ثوابت نوع الـ View
    // ════════════════════════════════════════════════════════

    // ★ نوع العرض للفاصل المتقطع — يتوافق مع قيم getItemViewType ★
    private static final int VIEW_TYPE_SEPARATOR = 0;
    private static final int VIEW_TYPE_ITEM      = 1;

    // ════════════════════════════════════════════════════════
    //  الحقول
    // ════════════════════════════════════════════════════════

    private final Context mContext;

    /**
     * ★ الخطوة الرابعة: List<AppScreen> بدلاً من List<Fragment> ★
     *
     * تحتوي على null في مواضع الفواصل البصرية.
     * القائمة مُمرَّرة مباشرةً من MainActivity.setupDrawer()،
     * مما يُحكم الفصل بين آلية عرض الدرج وبنية الفراغمنتات المحملة في الذاكرة.
     *
     * لم يعد الـ Adapter بحاجة لبناء قائمة عرض داخلية (buildDisplayList)
     * لأن المُستدعي يُمرّر القائمة بشكلها النهائي مع مواضع الفواصل.
     */
    private final List<AppScreen> mScreenList;

    private final DrawerListener mListener;

    /**
     * ★ الشاشة المختارة حالياً — تُحفظ كـ AppScreen بدلاً من موضع رقمي ★
     * يضمن صحة التحديد حتى لو تغيّر ترتيب العناصر مستقبلاً.
     * القيمة الافتراضية FONT_VIEWER تعكس الشاشة الافتراضية عند فتح التطبيق.
     */
    private AppScreen mSelectedScreen = AppScreen.FONT_VIEWER;

    // ════════════════════════════════════════════════════════
    //  الواجهة
    // ════════════════════════════════════════════════════════

    public interface DrawerListener {
        /**
         * ★ الخطوة الرابعة: يُعيد AppScreen بدلاً من int fragmentIndex ★
         *
         * يُستدعى عند نقر المستخدم على عنصر تنقل.
         * المُستدعي (MainActivity) يقرر بنفسه ما يجب فعله:
         *   - AppScreen.HOME    → startActivity(HomeActivity)
         *   - أي شاشة أخرى   → mNavManager.navigateFromDrawer(screen)
         *
         * @param screen الشاشة التي نقر عليها المستخدم
         * @return true إذا نجح التنقل ويجب تحديث التحديد البصري
         */
        boolean onDrawerItemSelected(AppScreen screen);
    }

    // ════════════════════════════════════════════════════════
    //  البناء
    // ════════════════════════════════════════════════════════

    /**
     * ★ الخطوة الرابعة: المُنشئ يستقبل List<AppScreen> بدلاً من List<Fragment> ★
     *
     * @param context    السياق
     * @param screenList قائمة الشاشات مع null للفواصل البصرية،
     *                   مُرتَّبة وفق الترتيب المطلوب في الدرج
     * @param listener   مستمع النقر على عناصر الدرج
     */
    public DrawerListAdapter(
            @NonNull Context context, List<AppScreen> screenList, DrawerListener listener) {
        mContext    = context;
        mScreenList = screenList;
        mListener   = listener;
    }

    // ════════════════════════════════════════════════════════
    //  RecyclerView.Adapter
    // ════════════════════════════════════════════════════════

    @Override
    public int getItemCount() {
        return mScreenList.size();
    }

    /**
     * ★ يُميّز بين الفاصل (VIEW_TYPE_SEPARATOR=0) والعنصر العادي (VIEW_TYPE_ITEM=1) ★
     * null في mScreenList يعني فاصلاً.
     */
    @Override
    public int getItemViewType(int position) {
        return (mScreenList.get(position) == null) ? VIEW_TYPE_SEPARATOR : VIEW_TYPE_ITEM;
    }

    @NonNull
    @Override
    public DrawerListViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(mContext);

        // ★ اختيار الـ layout المناسب بحسب نوع الـ ViewHolder ★
        boolean isSeparator = (viewType == VIEW_TYPE_SEPARATOR);
        int layoutRes = isSeparator ? R.layout.drawer_list_separator
                                    : R.layout.drawer_list_item;
        View view = inflater.inflate(layoutRes, parent, false);
        return new DrawerListViewHolder(view, isSeparator);
    }

    @Override
    public void onBindViewHolder(@NonNull DrawerListViewHolder holder, int position) {
        // ★ الفاصل لا يحتاج أي ربط بيانات — نتوقف هنا مباشرةً ★
        if (holder.isSeparator()) return;

        AppScreen screen = mScreenList.get(position);
        if (screen == null) return;

        int iconRes = getIconForScreen(screen);
        String title = getTitleForScreen(screen);

        if (iconRes != 0)     holder.setIcon(iconRes);
        if (!title.isEmpty()) holder.setTitle(title);

        // ★ التحديد البصري يعتمد على مقارنة AppScreen مباشرةً — لا أرقام ★
        holder.setSelected(screen == mSelectedScreen);

        holder.itemView.setOnClickListener(v -> {
            final int adapterPos = holder.getBindingAdapterPosition();
            if (adapterPos == RecyclerView.NO_POSITION) return;

            AppScreen clickedScreen = mScreenList.get(adapterPos);
            if (clickedScreen == null) return;

            // ★ الخطوة الرابعة: إرسال AppScreen للـ Listener بدلاً من int ★
            // MainActivity تقرر بنفسها ما يجب فعله (HomeActivity أو navigateFromDrawer)
            boolean selectionChanged = false;
            if (mListener != null) {
                selectionChanged = mListener.onDrawerItemSelected(clickedScreen);
            }

            if (selectionChanged) {
                setSelectedItem(clickedScreen);
            }
        });
    }

    // ════════════════════════════════════════════════════════
    //  بيانات العناصر — مُستقاة من AppScreen لا من instanceof Fragment
    // ════════════════════════════════════════════════════════

    /**
     * ★ الخطوة الرابعة: تحديد الأيقونة بناءً على AppScreen بدلاً من instanceof Fragment ★
     *
     * switch (AppScreen) أوضح وأسرع من سلسلة if/instanceof،
     * ولا يتطلب أي استيراد لأنواع الفراغمنتات.
     *
     * الآن هناك 6 شاشات: HOME، FONT_VIEWER، LOCAL_FONTS، SYSTEM_FONTS، FAVORITES، TRASH
     */
    private int getIconForScreen(AppScreen screen) {
        switch (screen) {
            case HOME:
                return dev.oneuiproject.oneui.R.drawable.ic_oui_home_outline;
            case FONT_VIEWER:
                return R.drawable.ic_oui_text_style_default;
            case LOCAL_FONTS:
                return dev.oneuiproject.oneui.R.drawable.ic_oui_device_outline;
            case SYSTEM_FONTS:
                return R.drawable.ic_android_3;
            case FAVORITES:
                // ★ أيقونة قائمة المفضلة في الدرج ★
                return dev.oneuiproject.oneui.R.drawable.ic_oui_favorite_off;
            case TRASH:
                // ★ أيقونة سلة المحذوفات في الدرج ★
                return dev.oneuiproject.oneui.R.drawable.ic_oui_delete_outline;
            default:
                return 0;
        }
    }

    /**
     * ★ الخطوة الرابعة: تحديد العنوان بناءً على AppScreen بدلاً من instanceof Fragment ★
     *
     * الآن هناك 6 شاشات: HOME، FONT_VIEWER، LOCAL_FONTS، SYSTEM_FONTS، FAVORITES، TRASH
     */
    private String getTitleForScreen(AppScreen screen) {
        switch (screen) {
            case HOME:
                return mContext.getString(R.string.drawer_home);
            case FONT_VIEWER:
                return mContext.getString(R.string.drawer_font_viewer);
            case LOCAL_FONTS:
                return mContext.getString(R.string.drawer_local_fonts);
            case SYSTEM_FONTS:
                return mContext.getString(R.string.drawer_system_fonts);
            case FAVORITES:
                // ★ عنوان قائمة المفضلة في الدرج ★
                return mContext.getString(R.string.drawer_favorites);
            case TRASH:
                // ★ عنوان سلة المحذوفات في الدرج ★
                return mContext.getString(R.string.drawer_trash);
            default:
                return "";
        }
    }

    // ════════════════════════════════════════════════════════
    //  التحديد
    // ════════════════════════════════════════════════════════

    /**
     * ★ الخطوة الرابعة: setSelectedItem(AppScreen) بدلاً من setSelectedItem(int) ★
     *
     * يُستدعى من:
     *   - MainActivity.setupDrawer() عند إعداد الدرج أول مرة
     *   - MainActivity.restoreFragmentsState() عند استعادة حالة النشاط
     *   - NavManager في كل عملية تنقل (navigateFromDrawer، handleBackPressed،
     *     handleFontSelected، showFragmentFast)
     *
     * يُحدِّث فقط موضعَي العنصر السابق والجديد بدلاً من تحديث القائمة كاملاً.
     *
     * @param screen الشاشة المختارة الجديدة
     */
    public void setSelectedItem(AppScreen screen) {
        if (screen == null || screen == mSelectedScreen) return;

        AppScreen prev = mSelectedScreen;
        mSelectedScreen = screen;

        // ★ تحديث موضعَي التغيير فقط لتجنب إعادة رسم القائمة بالكامل ★
        int prevPos = indexOfScreen(prev);
        int newPos  = indexOfScreen(screen);

        if (prevPos >= 0) notifyItemChanged(prevPos);
        if (newPos  >= 0) notifyItemChanged(newPos);
    }

    /**
     * ★ دالة مساعدة: إيجاد موضع AppScreen في mScreenList ★
     *
     * تستبدل آلية toDisplayPos/toFragmentIndex القديمة التي كانت تُحوِّل
     * بين فهرس الفراغمنت وموضع العرض. هنا البحث مباشر بمقارنة AppScreen.
     *
     * @param screen الشاشة المطلوب إيجاد موضعها
     * @return الموضع في mScreenList، أو -1 إن لم تُعثر عليها
     */
    private int indexOfScreen(AppScreen screen) {
        if (screen == null) return -1;
        for (int i = 0; i < mScreenList.size(); i++) {
            if (mScreenList.get(i) == screen) return i;
        }
        return -1;
    }

    /**
     * ★ يُعيد الشاشة المختارة حالياً ★
     * يُستخدم للاستعلام عن حالة التحديد البصري للدرج دون الحاجة لتتبع خارجي.
     *
     * @return AppScreen الشاشة المختارة حالياً في الدرج
     */
    public AppScreen getSelectedScreen() {
        return mSelectedScreen;
    }
                }
