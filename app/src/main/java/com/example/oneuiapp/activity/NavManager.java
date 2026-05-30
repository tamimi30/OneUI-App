package com.example.oneuiapp.activity;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.view.GravityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.example.oneuiapp.R;
import com.example.oneuiapp.fragment.LocalFontListFragment;
import com.example.oneuiapp.fragment.FontViewerFragment;
import com.example.oneuiapp.fragment.SystemFontListFragment;
import com.example.oneuiapp.fragment.FavoriteFontListFragment;
import com.example.oneuiapp.fragment.TrashFragment;
import com.example.oneuiapp.ui.drawer.DrawerListAdapter;
import com.example.oneuiapp.fontlist.search.SearchCoordinator;

import java.util.ArrayDeque;

import dev.oneuiproject.oneui.layout.DrawerLayout;

/**
 * NavManager - مدير التنقل المركزي للتطبيق
 *
 * يتولى هذا الكلاس جميع عمليات التنقل بين الشاشات (الفراغمنتات)
 * التي كانت مضمّنة سابقاً في MainActivity، ويتواصل مع النشاط
 * عبر واجهة Host لتجنب الاقتران المباشر.
 *
 * ★ نظام التنقل بزر الرجوع ★
 * يعتمد على مكدس تنقل مخصص (mNavBackStack) يتتبع مصدر اختيار الخط فقط.
 * أولوية زر الرجوع:
 *   1. إغلاق درج التنقل إذا كان مفتوحاً
 *   2. إلغاء الانتقال المعلّق خلال الـ RIPPLE_DELAY_MS
 *   3. إلغاء وضع التحديد المتعدد إذا كان نشطاً
 *   4. إغلاق البحث إذا كان مفتوحاً
 *   5. العودة لمصدر اختيار الخط من مكدس التنقل
 *   6. الانتقال لشاشة عارض الخطوط (الشاشة الجذر دائماً قبل الخروج)
 *   7. الخروج من التطبيق
 *
 * ★ آلية رصد حالة الدرج ★
 * مكتبة OneUI تُغلِّف الـ androidx.drawerlayout.widget.DrawerLayout داخلياً
 * ولا تُوفّر دالة عامة للوصول إليه. نبحث عنه مرة واحدة في setup()
 * عبر اجتياز شجرة الـ Views، ونحفظه في mInnerDrawer.
 * عند كل ضغطة رجوع نستدعي mInnerDrawer.isDrawerOpen() مباشرةً،
 * وهي دالة عامة موجودة في androidx DrawerLayout تعكس الحالة الفعلية دائماً
 * دون الحاجة لأي متغير تتبع منفصل أو مستمع إضافي.
 *
 * ★ التعديل: إضافة weightWidthLabel كمعامل في handleFontSelected
 *   ليُمرَّر مباشرةً إلى FontViewerFragment.loadFontFromPath دون إعادة استخراجه.
 *
 * ★ الإصلاح الجوهري (خطة الإصلاح — الخطوة الأولى إلى الثالثة):
 *   الانتقال الكامل من int/List<Fragment> إلى AppScreen في كل من:
 *   - واجهة Host (الخطوة الأولى + الثانية)
 *   - دوال التنقل الداخلية (الخطوة الثالثة)
 *
 * ★ مكدس التنقل (mNavBackStack):
 *   يحفظ الآن AppScreen.name() بدلاً من Tags أو أرقام.
 *   عند الرجوع: AppScreen.valueOf(name) لاستعادة الشاشة الصحيحة.
 *   هذا يضمن صحة التنقل حتى لو تغيّر ترتيب الشاشات مستقبلاً.
 *
 * ★ خطة الإصلاح — الخطوة الأولى: العودة لحلقة for الآمنة ★
 *   showFragmentFast() و showFragmentWithAnimation() يمران على جميع
 *   AppScreen.values() ويُخفيان كل شاشة باستثناء المستهدفة.
 *   سبب العودة: الاعتماد على getCurrentScreen() يفشل في حالتين:
 *     1. عند تدوير الشاشة أو إعادة بناء النشاط: قد لا يكون currentScreen
 *        متزامناً مع ما رسمه النظام فعلياً.
 *     2. عند إضافة فراجمنت جديد لأول مرة: إذا لم يُخفَ فوراً، يبقى ظاهراً
 *        تحت الفراجمنت الجديد ويُسبب التداخل البصري.
 */
public class NavManager {

    // ════════════════════════════════════════════════════════
    //  واجهة Host — تربط NavManager بـ MainActivity
    // ════════════════════════════════════════════════════════

    /**
     * واجهة الربط بين NavManager والنشاط المضيف (MainActivity).
     * تُزوّد NavManager بكل ما يحتاجه للتنقل دون اقتران مباشر بالنشاط.
     *
     * ★ الخطوة الأولى + الثانية من خطة الإصلاح:
     *   استبدال int/List بـ AppScreen في جميع دوال الواجهة:
     *   - getFragment(AppScreen) بدلاً من getFragments()
     *   - getCurrentScreen() بدلاً من getCurrentIndex()
     *   - setCurrentScreen(AppScreen) بدلاً من setCurrentIndex(int)
     *   - updateDrawerTitle(AppScreen) بدلاً من updateDrawerTitle(int)
     *   - updateMenuVisibility(AppScreen) بدلاً من updateMenuVisibility(int)
     *   - updateFabVisibility(AppScreen) بدلاً من updateFabVisibility(int)
     */
    public interface Host {
        /**
         * يُعيد الـ FragmentManager للتعاملات مع الفراغمنتات.
         * ★ الإصلاح: تُسمَّى getAppFragmentManager() بدلاً من getFragmentManager()
         *   لتجنب التعارض مع الدالة المُهمَلة android.app.FragmentManager
         *   الموروثة من Activity، والتي تُسبب خطأ تجميع عند التنفيذ في MainActivity. ★
         */
        FragmentManager getAppFragmentManager();

        /**
         * ★ الخطوة الأولى: getFragment(AppScreen) بدلاً من getFragments() ★
         * يُعيد الفراغمنت المرتبط بالشاشة المطلوبة، أو null لـ HOME.
         * يقطع الاعتماد على الترتيب نهائياً — كل شاشة تُعرَّف بهويتها لا بموقعها.
         */
        Fragment getFragment(AppScreen screen);

        /**
         * ★ الخطوة الثانية: getCurrentScreen() بدلاً من getCurrentIndex() ★
         * يُعيد الشاشة الظاهرة حالياً كـ AppScreen.
         */
        AppScreen getCurrentScreen();

        /**
         * ★ الخطوة الثانية: setCurrentScreen(AppScreen) بدلاً من setCurrentIndex(int) ★
         * يُحدّث الشاشة الحالية كـ AppScreen.
         */
        void setCurrentScreen(AppScreen screen);

        /** يُعيد الـ OneUI DrawerLayout */
        DrawerLayout getDrawerLayout();

        /** يُعيد محوّل درج التنقل */
        DrawerListAdapter getDrawerAdapter();

        /** يُعيد منسّق البحث */
        SearchCoordinator getSearchCoordinator();

        /** يُعيد الاسم الحقيقي للخط المحدد حالياً */
        String getFontRealName();

        /** يُعيد اسم ملف الخط المحدد حالياً */
        String getFontFileName();

        /** يُحدّث الاسم الحقيقي للخط المحدد */
        void setFontRealName(String name);

        /** يُحدّث اسم ملف الخط المحدد */
        void setFontFileName(String name);

        /**
         * ★ الخطوة الثانية: updateDrawerTitle(AppScreen) بدلاً من updateDrawerTitle(int) ★
         * يُحدّث عنوان الدرج للشاشة المُعطاة.
         */
        void updateDrawerTitle(AppScreen screen);

        /**
         * ★ الخطوة الثانية: updateMenuVisibility(AppScreen) بدلاً من updateMenuVisibility(int) ★
         * يُحدّث ظهور عناصر قائمة شريط الأدوات للشاشة المُعطاة.
         * ★ المرحلة الأولى: هذه الدالة أصبحت لا عملية (no-op) في MainActivity،
         *   لأن كل Fragment يُدير أيقوناته بشكل مستقل عبر setMenuVisibility(). ★
         */
        void updateMenuVisibility(AppScreen screen);

        /**
         * ★ الخطوة الثانية: updateFabVisibility(AppScreen) بدلاً من updateFabVisibility(int) ★
         * يُحدّث ظهور زر الإجراء العائم (FAB) للشاشة المُعطاة.
         */
        void updateFabVisibility(AppScreen screen);

        /**
         * يُنفّذ الخروج الفعلي من التطبيق.
         * يُفرّق بين Android O (API 26) مع isTaskRoot وبقية الإصدارات.
         */
        void performExit();

        /** يعرض رسالة Toast تطلب من المستخدم الضغط مرة أخرى للخروج */
        void showPressAgainToExitToast();
    }

    // ════════════════════════════════════════════════════════
    //  ثوابت التنقل
    // ════════════════════════════════════════════════════════

    // ★ مدة تأخير الانتقال لإتاحة الوقت الكافي لظهور تأثير الريبل ★
    // تُطبَّق فقط على الانتقال الفعلي (showFragmentWithAnimation وتحميل الخط)،
    // بينما تحديث العناوين يحدث فوراً بدون أي تأخير.
    private static final long RIPPLE_DELAY_MS = 200;

    // ★ مدة أنيميشن الانتقال = startOffset (50ms) + duration (450ms) ★
    // تُستخدم لتأجيل تعليم الخط باللون الأزرق حتى اكتمال الانتقال بصرياً،
    // بحيث يرى المستخدم التمييز فقط عند عودته للقائمة لا قبل مغادرتها.
    private static final long FRAGMENT_ANIMATION_DURATION_MS = 500L;

    // ★ المهلة الزمنية المسموح بها بين الضغطتين بالمللي ثانية (2 ثانية) ★
    private static final long BACK_PRESS_EXIT_INTERVAL = 2000;

    // ★ مفتاح حفظ مكدس التنقل عند إعادة البناء ★
    static final String KEY_NAV_BACK_STACK = "nav_back_stack";

    // ════════════════════════════════════════════════════════
    //  حقول الحالة
    // ════════════════════════════════════════════════════════

    private final Host mHost;

    /**
     * ★ مكدس التنقل المخصص — يتتبع مصدر اختيار الخط فقط لدعم زر الرجوع ★
     *
     * القاعدة: المكدس يُملأ فقط عند اختيار خط من قائمة (handleFontSelected).
     * التنقل عبر درج التنقل يُفرّغ المكدس دائماً، لأن المستخدم اختار وجهة جديدة بشكل صريح.
     * شاشة عارض الخطوط (AppScreen.FONT_VIEWER) هي الشاشة الجذر وهي آخر شاشة قبل الخروج.
     *
     * ★ الخطوة الثالثة من خطة الإصلاح:
     *   يُخزَّن الآن AppScreen.name() (نص ثابت لا يتأثر بترتيب الشاشات)
     *   بدلاً من Tags أو أرقام قابلة للتأثر بحذف الشاشات أو إضافتها.
     *   عند الرجوع: AppScreen.valueOf(name) يُعيد الشاشة الصحيحة دائماً. ★
     */
    private final ArrayDeque<String> mNavBackStack = new ArrayDeque<>();

    // ★ مرجع الـ inner androidx DrawerLayout المُغلَّف داخل OneUI DrawerLayout ★
    // يُستخرج مرة واحدة في setup() ويُستخدم لاستدعاء isDrawerOpen() مباشرةً
    // عند كل ضغطة رجوع، وهو أنظف من تتبع الحالة عبر متغير أو مستمع منفصل.
    private androidx.drawerlayout.widget.DrawerLayout mInnerDrawer;

    // ★ مرجع لـ Runnable الانتقال المعلّق لإمكانية إلغائه عند الضغط على زر الرجوع ★
    private Runnable mPendingNavigation;

    // ★ يحفظ حالة العنوان قبل النقر لاستعادتها إذا أُلغي الانتقال ★
    private String mSavedFontRealName;
    private String mSavedFontFileName;

    // ★ متغير لتتبع وقت أول ضغطة رجوع على الشاشة الجذر ★
    // يُستخدم لتفعيل ميزة "اضغط مرة أخرى للخروج"
    // القيمة الافتراضية 0 تعني لم يُضغط بعد
    private long mBackPressedTime = 0;

    // ════════════════════════════════════════════════════════
    //  البناء والإعداد
    // ════════════════════════════════════════════════════════

    public NavManager(Host host) {
        mHost = host;
    }

    /**
     * ★ تهيئة NavManager عبر الـ OneUI DrawerLayout ★
     * يستخرج الـ inner DrawerLayout مرة واحدة ويحفظه في mInnerDrawer.
     * يجب استدعاؤها بعد inflating الـ layout مباشرةً من setupDrawer() في MainActivity.
     *
     * @param drawerLayout الـ OneUI DrawerLayout الرئيسي في النشاط
     */
    public void setup(DrawerLayout drawerLayout) {
        if (drawerLayout != null) {
            // ★ استخراج الـ inner DrawerLayout مرة واحدة وتخزينه في mInnerDrawer ★
            // يُستخدم لاحقاً في isDrawerCurrentlyOpen() لاستدعاء isDrawerOpen() مباشرةً
            // دون الحاجة لأي متغير تتبع أو مستمع إضافي.
            mInnerDrawer = findInnerDrawerLayout(drawerLayout);
        }
    }

    // ════════════════════════════════════════════════════════
    //  حالة الدرج
    // ════════════════════════════════════════════════════════

    /**
     * ★ التحقق من حالة الدرج مباشرةً من mInnerDrawer ★
     * isDrawerOpen() دالة عامة في androidx DrawerLayout تعكس الحالة الفعلية
     * لحظة الاستدعاء، وتشمل الفتح البرمجي والفتح بالسحب اليدوي على حدٍّ سواء.
     * GravityCompat.START يستهدف الدرج الجانبي بغض النظر عن اتجاه التخطيط.
     */
    public boolean isDrawerCurrentlyOpen() {
        return mInnerDrawer != null && mInnerDrawer.isDrawerOpen(GravityCompat.START);
    }

    /**
     * ★ البحث عن الـ inner androidx DrawerLayout داخل شجرة الـ Views ★
     * يتجول في أبناء الـ ViewGroup بشكل تكراري حتى يجد أول
     * androidx.drawerlayout.widget.DrawerLayout مُغلَّف داخل OneUI DrawerLayout.
     * هذا البديل ضروري لأن مكتبة OneUI لا تُوفّر دالة مباشرة للوصول للـ inner drawer.
     * تُستدعى هذه الدالة مرة واحدة فقط في setup() لتجنب أي تكلفة متكررة.
     *
     * @param parent جذر الشجرة للبحث فيها
     * @return الـ inner DrawerLayout إن وُجد، أو null إن لم يُعثر عليه
     */
    private androidx.drawerlayout.widget.DrawerLayout findInnerDrawerLayout(ViewGroup parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof androidx.drawerlayout.widget.DrawerLayout) {
                return (androidx.drawerlayout.widget.DrawerLayout) child;
            }
            if (child instanceof ViewGroup) {
                androidx.drawerlayout.widget.DrawerLayout result =
                        findInnerDrawerLayout((ViewGroup) child);
                if (result != null) return result;
            }
        }
        return null;
    }

    // ════════════════════════════════════════════════════════
    //  التنقل بين الفراغمنتات — الخطوة الثالثة من خطة الإصلاح
    // ════════════════════════════════════════════════════════

    /**
     * ★ التنقل عبر درج التنقل — يُفرَّغ المكدس دائماً ★
     *
     * القاعدة الجوهرية:
     * - التنقل عبر الدرج يمثّل اختياراً صريحاً من المستخدم لوجهة جديدة،
     *   لذا يُفرَّغ المكدس بالكامل في جميع الحالات.
     * - المكدس مخصص حصراً لتتبع "مصدر اختيار الخط" (handleFontSelected)،
     *   وليس لتتبع تاريخ التنقل عبر الدرج.
     *
     * ★ الخطوة الثالثة: استقبال AppScreen بدلاً من int ★
     *
     * @param screen الشاشة المستهدفة
     */
    public void navigateFromDrawer(AppScreen screen) {
        // ★ تفريغ المكدس دائماً عند التنقل عبر الدرج ★
        // أي تنقل عبر الدرج يلغي تاريخ اختيار الخط السابق
        mNavBackStack.clear();

        mHost.setCurrentScreen(screen);
        showFragmentFast(screen);
        mHost.getDrawerAdapter().setSelectedItem(screen);
        mHost.updateDrawerTitle(screen);
    }

    /**
     * ★ الخطوة الأولى من خطة الإصلاح: العودة لحلقة for الآمنة ★
     *
     * عرض الفراجمنت بشكل فوري دون أنيميشن، مع إخفاء جميع الفراجمنتات الأخرى.
     * يُستخدم للتنقل عبر الدرج وعند استعادة حالة النشاط.
     *
     * سبب العودة للحلقة بدلاً من O(1):
     * الاعتماد على getCurrentScreen() لمعرفة الشاشة الحالية يفشل في حالتين:
     *   1. عند تدوير الشاشة أو إعادة بناء النشاط: قد لا يكون currentScreen
     *      متزامناً مع ما رسمه النظام فعلياً.
     *   2. عند إضافة فراجمنت جديد لأول مرة: إذا لم يُخفَ فوراً، يبقى ظاهراً
     *      تحت الفراجمنت الجديد ويُسبب التداخل البصري.
     * الحل: نمر على جميع AppScreen.values() ونُخفي كل شاشة ليست المستهدفة،
     * مما يضمن أن مدير الفراجمنتات يُخفي كل شيء باستثناء الشاشة المستهدفة.
     *
     * ★ الخطوة الثالثة: showFragmentFast(AppScreen) بدلاً من showFragmentFast(int) ★
     *
     * @param screen الشاشة المستهدفة
     */
    public void showFragmentFast(AppScreen screen) {
        // ★ الإصلاح: استدعاء getAppFragmentManager() بدلاً من getFragmentManager() ★
        FragmentManager fm = mHost.getAppFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();

        // ★ الخطوة الأولى: نمر على جميع الشاشات المتاحة في الـ Enum ★
        // يجب التحقق من أن الفراجمنت تمت إضافته فعلياً لتجنب NullPointerException
        for (AppScreen s : AppScreen.values()) {
            Fragment frag = mHost.getFragment(s);
            if (frag != null && frag.isAdded()) {
                if (s == screen) {
                    // إظهار الشاشة المستهدفة
                    transaction.show(frag);
                    // تحديث الأيقونات عند إظهار الشاشة
                    frag.setMenuVisibility(true);
                } else {
                    // إخفاء أي شاشة أخرى بقوة
                    transaction.hide(frag);
                    // إخفاء الأيقونات للشاشات المخفية
                    frag.setMenuVisibility(false);
                }
            }
        }

        // ★ استخدام commitNow لتطبيق التغيير فوراً ومنع التداخل البصري ★
        transaction.commitNow();

        // ★ تحديث الشاشة الحالية في الحاوية ★
        mHost.setCurrentScreen(screen);
        // ★ تحديث ظهور زر FAB للشاشة الجديدة ★
        mHost.updateFabVisibility(screen);
        // ★ إشعار منسّق البحث بالشاشة الظاهرة حالياً ★
        if (mHost.getSearchCoordinator() != null) {
            mHost.getSearchCoordinator().onFragmentChanged(screen);
        }
    }

    /**
     * ★ الخطوة الأولى من خطة الإصلاح: نفس منطق showFragmentFast مع إضافة الأنيميشن ★
     *
     * عرض الفراجمنت مع أنيميشن انتقال أفقي، مع إخفاء جميع الفراجمنتات الأخرى.
     * يُستخدم حصراً عند الانتقال بين قوائم الخطوط وشاشة عارض الخطوط،
     * ولا يُطبَّق على أي حالة تنقل أخرى في التطبيق.
     *
     * الأنيميشن المستخدم مأخوذ من ملفات depth_* الموجودة مسبقاً في المشروع،
     * وهي تعتمد على حركة أفقية بنسبة 27.8% مع تلاشي تدريجي.
     *
     * الفرق الوحيد عن showFragmentFast:
     * - إضافة transaction.setCustomAnimations(...) قبل بدء حلقة for.
     * - استخدام commit() بدلاً من commitNow() لأن الأنيميشن لا يعمل مع commitNow().
     *
     * ★ الخطوة الثالثة: showFragmentWithAnimation(AppScreen, boolean) ★
     *
     * @param screen     الشاشة المستهدفة
     * @param isForward  true للانتقال إلى الأمام (قائمة ← عارض)،
     *                   false للرجوع إلى الخلف (عارض ← قائمة)
     */
    public void showFragmentWithAnimation(AppScreen screen, boolean isForward) {
        // ★ الإصلاح: استدعاء getAppFragmentManager() بدلاً من getFragmentManager() ★
        FragmentManager fm = mHost.getAppFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();

        // ★ تطبيق الأنيميشن المناسب قبل بدء حلقة for — هذا هو الفرق الوحيد عن showFragmentFast ★
        // isForward=true : الشاشة الجديدة تدخل من اليمين، والحالية تخرج لليسار
        // isForward=false: الشاشة السابقة تعود من اليسار، والحالية تخرج لليمين
        if (isForward) {
            transaction.setCustomAnimations(
                    R.anim.depth_in_current_view,   // enter: شاشة العارض تدخل من اليمين
                    R.anim.depth_in_previous_view   // exit:  شاشة القائمة تخرج لليسار
            );
        } else {
            transaction.setCustomAnimations(
                    R.anim.depth_out_current_view,  // enter: شاشة القائمة تعود من اليسار
                    R.anim.depth_out_previous_view  // exit:  شاشة العارض تخرج لليمين
            );
        }

        // ★ نفس منطق showFragmentFast بالضبط: نمر على جميع الشاشات المتاحة في الـ Enum ★
        // يجب التحقق من أن الفراجمنت تمت إضافته فعلياً لتجنب NullPointerException
        for (AppScreen s : AppScreen.values()) {
            Fragment frag = mHost.getFragment(s);
            if (frag != null && frag.isAdded()) {
                if (s == screen) {
                    // إظهار الشاشة المستهدفة
                    transaction.show(frag);
                    // تحديث الأيقونات عند إظهار الشاشة
                    frag.setMenuVisibility(true);
                } else {
                    // إخفاء أي شاشة أخرى بقوة
                    transaction.hide(frag);
                    // إخفاء الأيقونات للشاشات المخفية
                    frag.setMenuVisibility(false);
                }
            }
        }

        // ★ commit() بدلاً من commitNow() لأن الأنيميشن لا يعمل مع commitNow() ★
        transaction.commit();

        // ★ تحديث الشاشة الحالية في الحاوية ★
        mHost.setCurrentScreen(screen);
        // ★ تحديث ظهور زر FAB للشاشة الجديدة ★
        mHost.updateFabVisibility(screen);
        // ★ إشعار منسّق البحث بالشاشة الظاهرة حالياً ★
        if (mHost.getSearchCoordinator() != null) {
            mHost.getSearchCoordinator().onFragmentChanged(screen);
        }
    }

    // ════════════════════════════════════════════════════════
    //  اختيار الخط والتنقل إلى العارض
    // ════════════════════════════════════════════════════════

    /**
     * ★ الإصلاح الجوهري لمشكلة أنيميشن إعادة الترتيب ★
     * الترتيب الصحيح للعمليات:
     * 1. الانتقال إلى شاشة العرض أولاً (لإخفاء القائمة عن أعين المستخدم)
     * 2. إغلاق البحث بعد الإخفاء (لكي يتم إعادة ترتيب القائمة في الخلفية بصمت تام)
     * هذا يضمن أن المستخدم لا يرى أي أنيميشن غير مرغوب فيه عند العودة للقائمة
     *
     * ★ ملاحظة على مكدس التنقل ★
     * عند اختيار خط من قائمة، نُضيف AppScreen.name() للمكدس قبل الانتقال لعارض الخطوط،
     * مما يُمكّن المستخدم من العودة للقائمة التي جاء منها بزر الرجوع.
     * هذا هو المكان الوحيد الذي يُضاف فيه للمكدس (وليس navigateFromDrawer).
     * المصادر الممكنة: LOCAL_FONTS, SYSTEM_FONTS, FAVORITES فقط.
     * TrashFragment (TRASH) ليس مصدراً لاختيار الخطوط.
     *
     * ★ الخطوة الثالثة: تخزين AppScreen.name() بدلاً من Tags أو أرقام ★
     * عوضاً عن تخزين sourceIndex (رقم قابل للتأثر بتغيير ترتيب الشاشات)،
     * يُخزَّن الآن AppScreen.name() (نص ثابت). عند العودة يُحوَّل عبر
     * AppScreen.valueOf(name) للحصول على الشاشة الصحيحة دائماً.
     *
     * ★ آلية تأخير الريبل ★
     * لإظهار تأثير الريبل على عنصر القائمة بشكل كامل قبل الانتقال، تنقسم العملية إلى:
     *   - فوري: تحديث العناوين وتحديد عنصر الدرج وحفظ المكدس وتعطيل اللمس.
     *     هذا يضمن أن العنوان يتغير لحظة الضغط دون أي تأخير مرئي.
     *   - مؤجَّل بـ RIPPLE_DELAY_MS: الانتقال الفعلي للفراغمنت وتحميل الخط وتفعيل العارض.
     *     هذا يمنح الريبل وقته الكامل للظهور قبل إخفاء شاشة القائمة.
     *
     * ★ ترتيب العمليات داخل mPendingNavigation ★
     * 1. enableTouch() أولاً — قبل showFragmentWithAnimation() لضمان استجابة اللمس
     *    الفورية على العارض بمجرد ظهوره، بدلاً من الانتظار حتى اكتمال commit() غير المتزامن.
     * 2. showFragmentWithAnimation() — يبدأ الأنيميشن.
     * 3. collapseSearch() + تحميل الخط — عمليات مرافقة للأنيميشن.
     * 4. saveAndHighlight() مؤجَّل بـ FRAGMENT_ANIMATION_DURATION_MS — يضمن أن التمييز
     *    الأزرق يظهر فقط بعد اكتمال الانتقال بصرياً، لا خلاله.
     *
     * ★ الأنيميشن ★
     * يُطبَّق عند الانتقال من LOCAL_FONTS أو SYSTEM_FONTS أو FAVORITES
     * إلى FONT_VIEWER فقط، دون أي حالة تنقل أخرى.
     *
     * ★ التعديل: إضافة معامل weightWidthLabel ★
     * يُمرَّر مباشرةً إلى FontViewerFragment.loadFontFromPath بدلاً من إعادة استخراجه،
     * إذ أن الوزن مستخرج مسبقاً وموجود في بيانات القائمة.
     *
     * ★ الخطوة الثالثة: mHost.getFragment(AppScreen.FONT_VIEWER) بدلاً من fragments.get(1) ★
     *
     * @param fontPath         مسار ملف الخط أو content URI
     * @param realName         الاسم الحقيقي للخط
     * @param fileName         اسم ملف الخط
     * @param ttcIndex         فهرس الخط داخل ملف TTC
     * @param weightWidthLabel وصف الوزن والعرض الجاهز من القائمة (قد يكون null)
     */
    public void handleFontSelected(String fontPath, String realName, String fileName,
                                   int ttcIndex, String weightWidthLabel) {

        // ★ الخطوة الثالثة: تحديد الشاشة المصدر الفعلي بالبحث عن قائمة الخطوط غير المخفية ★
        // الحالة المُعطِلة: عند ضغط زر الرجوع والنقر على خط في آنٍ واحد،
        // يُغيِّر handleBackPressed قيمة getCurrentScreen إلى FONT_VIEWER بشكل متزامن
        // قبل أن تُستدعى هذه الدالة. لكن عمليات hide/show تعتمد على commit()
        // غير المتزامن، لذا يعكس isHidden() الحالة الصحيحة لحظة الاستدعاء.
        // الحل: نبحث عن قائمة الخطوط غير المخفية فعلياً للحصول على المصدر الصحيح.
        AppScreen detectedSource = mHost.getCurrentScreen();
        for (AppScreen s : new AppScreen[]{AppScreen.LOCAL_FONTS, AppScreen.SYSTEM_FONTS, AppScreen.FAVORITES}) {
            Fragment f = mHost.getFragment(s);
            if (f != null && !f.isHidden()) {
                // ★ TrashFragment مستثنى عمداً: لا يمكن الانتقال منه إلى عارض الخطوط ★
                detectedSource = s;
                break;
            }
        }
        final AppScreen sourceScreen = detectedSource;

        // ★ حفظ حالة العنوان الحالية قبل أي تعديل للاستعادة عند الإلغاء ★
        mSavedFontRealName = mHost.getFontRealName();
        mSavedFontFileName = mHost.getFontFileName();

        // ★ الإجراء الأول: تعطيل قائمة الخطوط وظيفياً فوراً ★
        Fragment sourceFragment = mHost.getFragment(sourceScreen);
        if (sourceFragment instanceof LocalFontListFragment)
            ((LocalFontListFragment) sourceFragment).blockTouch();
        else if (sourceFragment instanceof SystemFontListFragment)
            ((SystemFontListFragment) sourceFragment).blockTouch();
        else if (sourceFragment instanceof FavoriteFontListFragment)
            ((FavoriteFontListFragment) sourceFragment).blockTouch();

        // ★ الإجراء الثاني: تفعيل شاشة عارض الخطوط فوراً قبل بدء الأنيميشن ★
        // استدعاؤه هنا يضمن أن root view العارض يملك clickable=true
        // قبل بدء الـ RIPPLE_DELAY_MS وقبل commit() غير المتزامن،
        // مما يُحل مشكلة بطء الاستجابة عند الدخول للعارض.
        // ★ الخطوة الثالثة: mHost.getFragment(AppScreen.FONT_VIEWER) بدلاً من fragments.get(1) ★
        Fragment viewerFragment = mHost.getFragment(AppScreen.FONT_VIEWER);
        if (viewerFragment instanceof FontViewerFragment)
            ((FontViewerFragment) viewerFragment).enableTouch();

        // ★ الخطوة الثالثة: تخزين AppScreen.name() في المكدس ★
        // يضمن صحة العودة بزر الرجوع حتى لو تغيّر ترتيب الشاشات مستقبلاً.
        mNavBackStack.addLast(sourceScreen.name());

        // ★ تحديث فوري: العناوين وعنصر الدرج بدون أي تأخير ★
        // يضمن أن المستخدم يرى العنوان الجديد لحظة الضغط على الخط.
        // ★ الخطوة الثالثة: setCurrentScreen(AppScreen) و setSelectedItem(AppScreen) ★
        mHost.setCurrentScreen(AppScreen.FONT_VIEWER);
        mHost.getDrawerAdapter().setSelectedItem(AppScreen.FONT_VIEWER);
        mHost.setFontRealName(realName);
        mHost.setFontFileName(fileName);
        mHost.updateDrawerTitle(AppScreen.FONT_VIEWER);

        DrawerLayout drawerLayout = mHost.getDrawerLayout();

        // ★ تأخير RIPPLE_DELAY_MS قبل الانتقال الفعلي ★
        // يمنح تأثير الريبل على عنصر القائمة وقتاً كافياً للظهور بالكامل
        // قبل أن تُخفى شاشة القائمة وتظهر شاشة العارض.
        mPendingNavigation = () -> {
            mPendingNavigation = null;

            // 1. ★ الانتقال إلى شاشة العرض مع أنيميشن أفقي (قائمة ← عارض) ★
            showFragmentWithAnimation(AppScreen.FONT_VIEWER, true);

            // 2. ★ إغلاق البحث بعد الإخفاء ★
            // onHiddenChanged في الـ Fragment سيُوقف الأنيميشن فور استدعاء collapseSearch
            mHost.getSearchCoordinator().collapseSearch();

            // 3. ★ تحميل الخط في فراغمنت العارض ★
            // ★ الخطوة الثالثة: mHost.getFragment(AppScreen.FONT_VIEWER) بدلاً من fragments.get(1) ★
            Fragment frag = mHost.getFragment(AppScreen.FONT_VIEWER);
            if (frag instanceof FontViewerFragment) {
                FontViewerFragment fontViewerFragment = (FontViewerFragment) frag;
                fontViewerFragment.originalFontPath = fontPath;
                // ★ خطوط المفضلة هي خطوط محلية دائماً (isSystemFont = false) ★
                // ★ الخطوة الثالثة: مقارنة AppScreen بدلاً من مقارنة رقم الفهرس ★
                boolean isSystemFont = (sourceScreen == AppScreen.SYSTEM_FONTS);
                if (fontPath != null && fontPath.startsWith("content://")) {
                    // ★ خطوط URI: لا يوجد weightWidthLabel من القائمة — يُستخرج التنوع تلقائياً ★
                    fontViewerFragment.loadFontFromUri(Uri.parse(fontPath), realName);
                } else {
                    // ★ التعديل: تمرير weightWidthLabel مباشرةً للفراغمنت ★
                    fontViewerFragment.loadFontFromPath(fontPath, fileName, realName,
                                                        ttcIndex, isSystemFont, weightWidthLabel);
                }
            }

            // 4. ★ تعليم الخط باللون الأزرق بعد اكتمال الأنيميشن ★
            // التأجيل بـ FRAGMENT_ANIMATION_DURATION_MS يضمن أن التمييز البصري
            // يظهر فقط بعد انتهاء الانتقال، لا أثناءه أو قبله.
            drawerLayout.postDelayed(() -> {
                // ★ الخطوة الثالثة: mHost.getFragment(sourceScreen) بدلاً من fragments.get(index) ★
                Fragment src = mHost.getFragment(sourceScreen);
                if (src instanceof LocalFontListFragment)
                    ((LocalFontListFragment) src).saveAndHighlight(fontPath);
                else if (src instanceof SystemFontListFragment)
                    ((SystemFontListFragment) src).saveAndHighlight(fontPath);
                else if (src instanceof FavoriteFontListFragment)
                    ((FavoriteFontListFragment) src).saveAndHighlight(fontPath);
            }, FRAGMENT_ANIMATION_DURATION_MS);
        };
        drawerLayout.postDelayed(mPendingNavigation, RIPPLE_DELAY_MS);
    }

    // ════════════════════════════════════════════════════════
    //  زر الرجوع
    // ════════════════════════════════════════════════════════

    /**
     * ★ منطق زر الرجوع المحدَّث — الخطوة الثالثة من خطة الإصلاح ★
     *
     * أولوية التنفيذ:
     * 1. إغلاق درج التنقل إذا كان مفتوحاً — يُستعلم مباشرةً من mInnerDrawer
     * 2. إلغاء الانتقال المعلّق إذا ضغط المستخدم على زر الرجوع خلال الـ RIPPLE_DELAY_MS
     *    مع استعادة حالة العنوان واللمس كما كانت قبل النقر
     * 3. إلغاء وضع التحديد المتعدد — مع الإبقاء على البحث مفتوحاً
     *    يشمل: LocalFontListFragment, FavoriteFontListFragment, TrashFragment
     * 4. إغلاق البحث إذا كان مفتوحاً
     * 5. العودة لمصدر اختيار الخط من مكدس التنقل (AppScreen.valueOf(name))
     * 6. الانتقال لشاشة عارض الخطوط (الشاشة الجذر دائماً قبل الخروج)
     * 7. الخروج من التطبيق
     *
     * ★ الإصلاح: استبدال if (mHost.getCurrentIndex() != 1) بـ:
     *            if (mHost.getCurrentScreen() != AppScreen.FONT_VIEWER) ★
     */
    public void handleBackPressed() {
        // 1. ★ الأولوية القصوى: إغلاق درج التنقل إذا كان مفتوحاً ★
        // isDrawerCurrentlyOpen() تستعلم من mInnerDrawer مباشرةً دون أي متغير وسيط
        if (isDrawerCurrentlyOpen()) {
            mHost.getDrawerLayout().setDrawerOpen(false, true);
            return;
        }

        // 2. ★ إلغاء الانتقال المعلّق إذا ضغط المستخدم على زر الرجوع خلال الـ RIPPLE_DELAY_MS ★
        if (mPendingNavigation != null) {
            mHost.getDrawerLayout().removeCallbacks(mPendingNavigation);
            mPendingNavigation = null;
            // ★ استعادة حالة العنوان كما كانت قبل النقر ★
            mHost.setFontRealName(mSavedFontRealName);
            mHost.setFontFileName(mSavedFontFileName);
            // ★ الخطوة الثالثة: استرداد AppScreen.name() وتحويله عبر AppScreen.valueOf() ★
            AppScreen sourceScreen = mHost.getCurrentScreen();
            if (!mNavBackStack.isEmpty()) {
                try {
                    sourceScreen = AppScreen.valueOf(mNavBackStack.removeLast());
                } catch (IllegalArgumentException e) {
                    // اسم شاشة غير معروف — استخدام الشاشة الحالية كاحتياط
                    android.util.Log.w("NavManager", "Unknown AppScreen name in back stack");
                }
            }
            mHost.setCurrentScreen(sourceScreen);
            mHost.getDrawerAdapter().setSelectedItem(sourceScreen);
            mHost.updateDrawerTitle(sourceScreen);
            // ★ إعادة تفعيل اللمس على القائمة المصدر ★
            Fragment sourceFrag = mHost.getFragment(sourceScreen);
            if (sourceFrag instanceof LocalFontListFragment)
                ((LocalFontListFragment) sourceFrag).unblockTouch();
            else if (sourceFrag instanceof SystemFontListFragment)
                ((SystemFontListFragment) sourceFrag).unblockTouch();
            else if (sourceFrag instanceof FavoriteFontListFragment)
                ((FavoriteFontListFragment) sourceFrag).unblockTouch();
            return;
        }

        // 3. ★ الأولوية الثانية: إلغاء وضع التحديد في الفراغمنت الحالي ★
        // إذا كان الفراغمنت في وضع التحديد، سيقوم بإلغائه ونتوقف هنا
        // ليبقى البحث مفتوحاً مع نتائجه كما هو.
        // ★ الخطوة الثالثة: getCurrentScreen() + getFragment(AppScreen) ★
        AppScreen currentScreen = mHost.getCurrentScreen();
        Fragment currentFragment = mHost.getFragment(currentScreen);
        if (currentFragment instanceof LocalFontListFragment) {
            if (((LocalFontListFragment) currentFragment).handleBackPressed()) return;
        } else if (currentFragment instanceof FavoriteFontListFragment) {
            // ★ دعم إلغاء وضع التحديد المتعدد في قائمة المفضلة ★
            if (((FavoriteFontListFragment) currentFragment).handleBackPressed()) return;
        } else if (currentFragment instanceof TrashFragment) {
            // ★ دعم إلغاء وضع التحديد المتعدد في سلة المحذوفات ★
            if (((TrashFragment) currentFragment).handleBackPressed()) return;
        }

        // 4. ★ الأولوية الثالثة: إغلاق البحث إذا كان مفتوحاً ★
        if (mHost.getSearchCoordinator().isSearchExpanded()) {
            mHost.getSearchCoordinator().collapseSearch();
            return;
        }

        // 5. ★ الأولوية الرابعة: العودة لمصدر اختيار الخط مع أنيميشن أفقي ★
        // ★ الخطوة الثالثة: استرداد AppScreen.name() وتحويله إلى AppScreen عبر valueOf() ★
        if (!mNavBackStack.isEmpty()) {
            AppScreen previousScreen;
            try {
                previousScreen = AppScreen.valueOf(mNavBackStack.removeLast());
            } catch (IllegalArgumentException e) {
                android.util.Log.w("NavManager", "Unknown AppScreen name in back stack — ignoring");
                return;
            }
            mHost.setCurrentScreen(previousScreen);
            showFragmentWithAnimation(previousScreen, false);
            // ★ تفعيل الشاشة التي نعود إليها وظيفياً فوراً ★
            Fragment prevFrag = mHost.getFragment(previousScreen);
            if (prevFrag instanceof LocalFontListFragment)
                ((LocalFontListFragment) prevFrag).unblockTouch();
            else if (prevFrag instanceof SystemFontListFragment)
                ((SystemFontListFragment) prevFrag).unblockTouch();
            else if (prevFrag instanceof FavoriteFontListFragment)
                ((FavoriteFontListFragment) prevFrag).unblockTouch();
            else if (prevFrag instanceof FontViewerFragment)
                ((FontViewerFragment) prevFrag).enableTouch();
            mHost.getDrawerAdapter().setSelectedItem(previousScreen);
            mHost.updateDrawerTitle(previousScreen);
            return;
        }

        // 6. ★ الأولوية الخامسة: الانتقال لشاشة عارض الخطوط (الشاشة الجذر) مع أنيميشن ★
        // ★ الإصلاح الجوهري: استبدال if (mHost.getCurrentIndex() != 1) ★
        // شاشة عارض الخطوط هي آخر شاشة دائماً قبل الخروج من التطبيق
        // يُطبَّق أنيميشن الرجوع الأفقي لأن المستخدم يتراجع نحو الشاشة الجذر
        if (mHost.getCurrentScreen() != AppScreen.FONT_VIEWER) {
            AppScreen prev = mHost.getCurrentScreen();
            mHost.setCurrentScreen(AppScreen.FONT_VIEWER);
            showFragmentWithAnimation(AppScreen.FONT_VIEWER, false);
            // ★ القائمة الخارجة تُحجب، والعارض الداخل يُفعَّل ★
            // TrashFragment لا يملك blockTouch() لأنه لا يعاني من مشكلة الضغط
            // المتعدد التي تستوجب تعطيل اللمس — الأنيميشن يعمل بشكل سليم دون ذلك.
            Fragment prevFrag = mHost.getFragment(prev);
            if (prevFrag instanceof LocalFontListFragment)
                ((LocalFontListFragment) prevFrag).blockTouch();
            else if (prevFrag instanceof SystemFontListFragment)
                ((SystemFontListFragment) prevFrag).blockTouch();
            else if (prevFrag instanceof FavoriteFontListFragment)
                ((FavoriteFontListFragment) prevFrag).blockTouch();
            Fragment viewer = mHost.getFragment(AppScreen.FONT_VIEWER);
            if (viewer instanceof FontViewerFragment)
                ((FontViewerFragment) viewer).enableTouch();
            mHost.getDrawerAdapter().setSelectedItem(AppScreen.FONT_VIEWER);
            mHost.updateDrawerTitle(AppScreen.FONT_VIEWER);
            return;
        }

        // 7. ★ الخروج من التطبيق — نحن على الشاشة الجذر والمكدس فارغ ★
        // ميزة "اضغط مرة أخرى للخروج": عند الضغطة الأولى تظهر رسالة Toast،
        // وعند الضغطة الثانية خلال المهلة المحددة يتم الخروج فعلياً.
        long currentTime = System.currentTimeMillis();
        if (currentTime - mBackPressedTime < BACK_PRESS_EXIT_INTERVAL) {
            // الضغطة الثانية في الوقت المحدد — الخروج من التطبيق
            mHost.performExit();
        } else {
            // الضغطة الأولى — عرض رسالة Toast وحفظ وقت الضغط
            mBackPressedTime = currentTime;
            mHost.showPressAgainToExitToast();
        }
    }

    // ════════════════════════════════════════════════════════
    //  حفظ الحالة واستعادتها
    // ════════════════════════════════════════════════════════

    /**
     * ★ حفظ مكدس التنقل في الـ Bundle ★
     * يُستدعى من onSaveInstanceState في MainActivity.
     *
     * ★ الخطوة الثالثة: يحفظ AppScreen.name() كمصفوفة نصية ★
     *
     * @param outState الـ Bundle المستهدف
     */
    public void saveState(Bundle outState) {
        // ★ حفظ مكدس التنقل كمصفوفة AppScreen.name() ★
        outState.putStringArray(KEY_NAV_BACK_STACK, navBackStackToStringArray());
    }

    /**
     * ★ استعادة مكدس التنقل من الـ Bundle ★
     * يُحوّل المصفوفة النصية المحفوظة إلى ArrayDeque بنفس الترتيب الأصلي.
     *
     * ★ الخطوة الثالثة: يستعيد AppScreen.name() من المصفوفة النصية ★
     *
     * @param savedInstanceState الـ Bundle المصدر
     */
    public void restoreNavBackStack(Bundle savedInstanceState) {
        mNavBackStack.clear();
        // ★ استعادة AppScreen.name() من المصفوفة المحفوظة ★
        String[] stackArray = savedInstanceState.getStringArray(KEY_NAV_BACK_STACK);
        if (stackArray != null) {
            for (String screenName : stackArray) {
                mNavBackStack.addLast(screenName);
            }
        }
    }

    /**
     * ★ تحويل مكدس التنقل إلى مصفوفة نصية للحفظ في Bundle ★
     * يحافظ على الترتيب الصحيح (من الأقدم للأحدث).
     *
     * ★ الخطوة الثالثة: تحويل لمصفوفة String[] تحتوي AppScreen.name() ★
     */
    private String[] navBackStackToStringArray() {
        String[] array = new String[mNavBackStack.size()];
        int i = 0;
        for (String screenName : mNavBackStack) {
            array[i++] = screenName;
        }
        return array;
    }
}
