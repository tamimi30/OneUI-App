package com.oneui.fontviewer.dialog;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.oneui.fontviewer.R;
import com.oneui.fontviewer.activity.AppScreen;
import com.oneui.fontviewer.activity.MainActivity;
import com.oneui.fontviewer.utils.notification.BatchOperationState;
import com.oneui.fontviewer.utils.notification.NotificationActionReceiver;

import dev.oneuiproject.oneui.dialog.ProgressDialog;

/**
 * TrashActionDialogs — ديالوجات وإشعارات سلة المحذوفات
 *
 * ════════════════════════════════════════════════════════════════════════
 * المتطلبات المطبَّقة:
 *
 * ★ (16) showMoveToTrashDialog:
 *        لا عنوان، رسالة بصيغة Plurals، زران: إلغاء + نقل إلى سلة المحذوفات
 *
 * ★ (17) showDeletePermanentlyDialog:
 *        لا عنوان، رسالة بصيغة Plurals، زر "حذف" باللون الأحمر (oui_functional_red_color)
 *        يُطبَّق اللون بعد show() كما هو موضح في الملاحظة 28
 *
 * ★ (18) createMoveToTrashProgressDialog:
 *        STYLE_HORIZONTAL، OneUI يعرض X/Y والنسبة المئوية تلقائياً
 *        زران: إلغاء (يوقف العملية) + إخفاء الإطار المنبثق (تكمل في الخلفية)
 *        + إشعار في شريط الحالة أثناء عمل العملية في الخلفية
 *
 * ★ (19) createDeleteProgressDialog + createRestoreProgressDialog:
 *        نفس سلوك وتصميم ديالوج التقدم في النقطة 18
 *        + إشعارات مستقلة لكل نوع عملية
 *
 * ★ (28) اللون الأحمر مُطبَّق عبر:
 *        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(...)
 *        بعد builder.show() وليس قبله
 *
 * ★ إصلاح المشكلات (إضافة جديدة):
 *   - (3) PendingIntent على الإشعار لفتح MainActivity ثم الانتقال لشاشة السلة
 *         وإعادة عرض ديالوج التقدم — عبر إضافة Extra «EXTRA_NAVIGATE_TO_TRASH»
 *         الذي تقرأه handleIntent() في MainActivity وتستدعي navigateFromDrawer(5).
 *   - (4) addAction() لإضافة زر "إلغاء" في الإشعار الموسَّع
 *   - (6) عناوين ديالوج التقدم بصيغة عامة بدون أرقام:
 *         count=1  → "Moving 1 file to Trash…"
 *         count>1  → "Moving files to Trash…"
 *         لأن العداد التفصيلي (X/Y) يظهر بالفعل أسفل شريط التقدم
 *
 * ★ إصلاح المشكلة (2) — التنقل للشاشة الصحيحة:
 *   - buildContentIntent() الآن تستخدم BatchOperationState.getSourceScreen()
 *     وتضع AppScreen.name() في MainActivity.EXTRA_TARGET_FRAGMENT بدلاً من int.
 *   - هذا يضمن أن الضغط على الإشعار يفتح الشاشة التي بدأت منها العملية
 *     (LocalFonts بدلاً من دائماً فتح سلة المحذوفات).
 *
 * ★ إصلاح المشكلتين (1) و (3) — فتح ديالوج التقدم تلقائياً:
 *   - buildContentIntent() الآن تُضيف «from_notification=true» إلى الـ Intent،
 *     مما يُخبر handleIntent() في MainActivity بأن الفتح جاء من الإشعار،
 *     فتستدعي BatchOperationState.setShouldReopenDialog(true) لإعادة فتح الديالوج.
 *
 * ★ الإصلاح الجوهري (الخطوة الخامسة — الأرقام السحرية → AppScreen):
 *   buildContentIntent() تُرسل الآن AppScreen.name() كـ String في EXTRA_TARGET_FRAGMENT
 *   بدلاً من الفهرس الرقمي (int). هذا يقطع آخر اعتماد على الأرقام في نظام الإشعارات.
 *   handleIntent() في MainActivity تقرأ هذا النص وتُحوّله عبر AppScreen.valueOf(name).
 * ════════════════════════════════════════════════════════════════════════
 *
 * الملفات المطلوبة (strings.xml - يجب إضافتها):
 *   • action_move_to_trash   → "نقل إلى سلة المحذوفات" / "Move to Trash"
 *   • action_hide_dialog     → "إخفاء الإطار المنبثق"  / "Hide"
 *   • trash_notif_channel_name → "سلة المحذوفات"        / "Trash"
 *   • trash_notif_channel_desc → وصف قناة الإشعارات
 *
 * الملفات الموجودة المستخدمة (plurals):
 *   • dialog_move_to_trash_question    • dialog_delete_permanently_question
 *   • progress_moving_to_trash         • progress_deleting
 *   • progress_restoring
 *
 * المسار: app/src/main/java/com/example/oneuiapp/dialog/TrashActionDialogs.java
 */
public final class TrashActionDialogs {

    // ─────────────────────────────────────────────────────────────────────
    // ثوابت الإشعارات
    // ─────────────────────────────────────────────────────────────────────

    /** معرّد قناة إشعارات عمليات السلة — مشترك بين الثلاث عمليات */
    private static final String CHANNEL_ID = "trash_ops";

    /**
     * معرّفات إشعار مستقلة لكل نوع عملية.
     * ★ public لأنها تُستخدم في OperationForegroundService.startForeground() ★
     * يجب أن تتطابق IDs الخدمة مع IDs الإشعارات لضمان تحديث إشعار واحد فقط.
     */
    public static final int NOTIF_ID_MOVE    = 1001;
    public static final int NOTIF_ID_RESTORE = 1002;
    public static final int NOTIF_ID_DELETE  = 1003;

    /**
     * ★ رموز طلب PendingIntent — يجب أن تكون مميزة لتجنب تعارض الـ PendingIntents.
     * contentIntent و cancelIntent لهما رموز مختلفة.
     */
    private static final int PI_REQUEST_CONTENT = 100;
    private static final int PI_REQUEST_CANCEL  = 101;

    // منع التهيئة — كل الدوال ثابتة (static only)
    private TrashActionDialogs() {}

    // ─────────────────────────────────────────────────────────────────────
    // الواجهات
    // ─────────────────────────────────────────────────────────────────────

    /** مستمع تأكيد العملية في الديالوجات النصية (نقل / حذف) */
    public interface OnConfirmListener {
        void onConfirmed();
    }

    /** مستمع ضغط "إلغاء" في ديالوج التقدم — يجب أن يستدعي ViewModel.cancelCurrentOperation() */
    public interface OnProgressCancelListener {
        void onCancel();
    }

    /**
     * مستمع ضغط "إخفاء الإطار المنبثق" في ديالوج التقدم.
     * العملية تكمل في الخلفية والإشعار يظهر في شريط الحالة.
     */
    public interface OnProgressHideListener {
        void onHide();
    }


    // ════════════════════════════════════════════════════════════════════════
    // ★ (16) ديالوج تأكيد النقل لسلة المحذوفات
    // ════════════════════════════════════════════════════════════════════════

    /**
     * يُظهر ديالوج تأكيد نقل ملفات إلى سلة المحذوفات.
     *
     * التصميم (الملاحظة 16):
     *   • لا عنوان
     *   • رسالة: "هل تريد نقل ملف واحد إلى سلة المحذوفات؟" (Plurals تلقائي)
     *   • زر موجب: "نقل إلى سلة المحذوفات" — يستدعي onConfirmed()
     *   • زر سالب: "إلغاء" — يُغلق الديالوج فقط
     *
     * @param context  السياق (يفضَّل أن يكون Context من Fragment لا Application)
     * @param count    عدد الملفات المراد نقلها (يؤثر في صيغة Plurals)
     * @param listener يُستدعى عند موافقة المستخدم
     */
    public static void showMoveToTrashDialog(
            @NonNull Context context,
            int count,
            @NonNull OnConfirmListener listener) {

        if (count <= 0) return;

        String message = context.getResources()
                .getQuantityString(R.plurals.dialog_move_to_trash_question, count, count);

        new AlertDialog.Builder(context)
                .setMessage(message)
                .setPositiveButton(R.string.action_move_to_trash,
                        (d, w) -> listener.onConfirmed())
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }


    // ════════════════════════════════════════════════════════════════════════
    // ★ (17) ديالوج تأكيد الحذف النهائي
    // ════════════════════════════════════════════════════════════════════════

    /**
     * يُظهر ديالوج تأكيد الحذف النهائي من سلة المحذوفات.
     *
     * التصميم (الملاحظات 17 و 28):
     *   • لا عنوان
     *   • رسالة: "هل تريد حذف ملف واحد نهائياً؟" (Plurals تلقائي)
     *   • زر موجب: "حذف" — باللون الأحمر (oui_functional_red_color) — يستدعي onConfirmed()
     *   • زر سالب: "إلغاء" — يُغلق الديالوج فقط
     *
     * ملاحظة: اللون الأحمر يُطبَّق بعد builder.show() كما أوضحت الملاحظة 28،
     * لأن getButton() يُعيد null إذا استُدعي قبل show().
     *
     * @param context  السياق
     * @param count    عدد الملفات المراد حذفها نهائياً
     * @param listener يُستدعى عند موافقة المستخدم على الحذف النهائي
     */
    public static void showDeletePermanentlyDialog(
            @NonNull Context context,
            int count,
            @NonNull OnConfirmListener listener) {

        if (count <= 0) return;

        String message = context.getResources()
                .getQuantityString(R.plurals.dialog_delete_permanently_question, count, count);

        // ★ (28): نستخدم builder.show() لا builder.create() + dialog.show()
        // لأن الزر يكون null إذا حاولنا تلوينه قبل الظهور الفعلي على الشاشة
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setMessage(message)
                .setPositiveButton(R.string.action_delete,
                        (d, w) -> listener.onConfirmed())
                .setNegativeButton(R.string.action_cancel, null)
                .show();

        // ★ (17) + (28): تلوين زر "حذف" باللون الأحمر الوظيفي من مكتبة OneUI
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(ContextCompat.getColor(context,
                        dev.oneuiproject.oneui.design.R.color.oui_functional_red_color));
    }


    // ════════════════════════════════════════════════════════════════════════
    // ★ (18) ديالوج تقدم النقل إلى سلة المحذوفات
    // ════════════════════════════════════════════════════════════════════════

    /**
     * ينشئ ويُهيّئ ديالوج التقدم لعملية النقل إلى سلة المحذوفات.
     *
     * التصميم (الملاحظة 18):
     *   • العنوان (★ إصلاح المشكلة 6):
     *     - count = 1  → "Moving 1 file to Trash…"   / "جارٍ نقل ملف واحد…"
     *     - count > 1  → "Moving files to Trash…"    / "جارٍ نقل الملفات…"
     *     لا أرقام في العنوان لأن عداد X/Y يظهر أسفل شريط التقدم تلقائياً
     *   • شريط تقدم: STYLE_HORIZONTAL
     *     - مكتبة OneUI تعرض X/Y على اليمين والنسبة المئوية على اليسار تلقائياً
     *   • setCancelable(false): لا يُغلق بالضغط خارجه أو بزر Back
     *   • زر سالب (يسار): "إلغاء" — يوقف العملية ويُغلق الديالوج
     *   • زر موجب (يمين): "إخفاء الإطار المنبثق" — يُغلق الديالوج والعملية تكمل
     *
     * ★ الديالوج يُعاد غير مُعروض — يستدعي الـ Fragment progressDialog.show()
     *   ثم يحدّثه عبر progressDialog.setProgress(current) عند مراقبة LiveData.
     *
     * @param context        السياق
     * @param total          إجمالي عدد الملفات (يُعيَّن كـ max لشريط التقدم)
     * @param cancelListener يُستدعى عند الضغط على "إلغاء" — يجب أن يستدعي cancelCurrentOperation()
     * @param hideListener   يُستدعى عند الضغط على "إخفاء"
     * @return ProgressDialog مُهيَّأ وجاهز لـ .show()
     */
    @NonNull
    public static ProgressDialog createMoveToTrashProgressDialog(
            @NonNull Context context,
            int total,
            @NonNull OnProgressCancelListener cancelListener,
            @NonNull OnProgressHideListener hideListener) {

        // ★ (6): getQuantityString بمعامل واحد فقط (بدون %d) ★
        String title = context.getResources()
                .getQuantityString(R.plurals.progress_moving_to_trash, total);

        return buildProgressDialog(context, title, total, cancelListener, hideListener);
    }


    // ════════════════════════════════════════════════════════════════════════
    // ★ (19) ديالوج تقدم الحذف النهائي
    // ════════════════════════════════════════════════════════════════════════

    /**
     * ينشئ ويُهيّئ ديالوج التقدم لعملية الحذف النهائي من السلة.
     *
     * ★ (6): العنوان بصيغة عامة — count=1 → "1 file"، count>1 → "files" (بدون رقم) ★
     *
     * @param context        السياق
     * @param total          إجمالي عدد الملفات المراد حذفها
     * @param cancelListener يستدعي cancelCurrentOperation() في TrashViewModel
     * @param hideListener   يُعلم الـ Fragment بإخفاء الديالوج
     * @return ProgressDialog مُهيَّأ وجاهز لـ .show()
     */
    @NonNull
    public static ProgressDialog createDeleteProgressDialog(
            @NonNull Context context,
            int total,
            @NonNull OnProgressCancelListener cancelListener,
            @NonNull OnProgressHideListener hideListener) {

        // ★ (6): getQuantityString بمعامل واحد فقط (بدون %d) ★
        String title = context.getResources()
                .getQuantityString(R.plurals.progress_deleting, total);

        return buildProgressDialog(context, title, total, cancelListener, hideListener);
    }


    // ════════════════════════════════════════════════════════════════════════
    // ★ (19) ديالوج تقدم الاستعادة
    // ════════════════════════════════════════════════════════════════════════

    /**
     * ينشئ ويُهيّئ ديالوج التقدم لعملية استعادة الملفات من السلة.
     *
     * ★ (6): العنوان بصيغة عامة — count=1 → "1 file"، count>1 → "files" (بدون رقم) ★
     *
     * @param context        السياق
     * @param total          إجمالي عدد الملفات المراد استعادتها
     * @param cancelListener يستدعي cancelCurrentOperation() في TrashViewModel
     * @param hideListener   يُعلم الـ Fragment بإخفاء الديالوج
     * @return ProgressDialog مُهيَّأ وجاهز لـ .show()
     */
    @NonNull
    public static ProgressDialog createRestoreProgressDialog(
            @NonNull Context context,
            int total,
            @NonNull OnProgressCancelListener cancelListener,
            @NonNull OnProgressHideListener hideListener) {

        // ★ (6): getQuantityString بمعامل واحد فقط (بدون %d) ★
        String title = context.getResources()
                .getQuantityString(R.plurals.progress_restoring, total);

        return buildProgressDialog(context, title, total, cancelListener, hideListener);
    }


    // ─────────────────────────────────────────────────────────────────────
    // المُنشئ الداخلي المشترك لجميع ديالوجات التقدم
    // ─────────────────────────────────────────────────────────────────────

    /**
     * يبني ويُهيّئ ProgressDialog بإعدادات مشتركة لجميع عمليات السلة.
     *
     * ★ (28): لا نُعيَّن ألوان أو أحجام نصوص يدوياً — مكتبة OneUI
     * تتكفّل بذلك تلقائياً عبر ProgressDialog.STYLE_HORIZONTAL.
     * نمرر فقط setMax() و setProgress() والباقي يُحسب آلياً.
     */
    @NonNull
    private static ProgressDialog buildProgressDialog(
            @NonNull Context context,
            @NonNull String title,
            int total,
            @NonNull OnProgressCancelListener cancelListener,
            @NonNull OnProgressHideListener hideListener) {

        ProgressDialog dialog = new ProgressDialog(context);
        dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        dialog.setTitle(title);

        // ★ setMax(): يُعيَّن بعدد الملفات — OneUI يحسب النسبة والـ X/Y تلقائياً
        // الـ Fragment سيستدعي setProgress(current) عند كل تحديث من LiveData
        dialog.setMax(total);

        // لا يُغلق بزر Back أو بالضغط خارج الديالوج —
        // المستخدم مُلزَم باستخدام أحد الزرين
        dialog.setCancelable(false);

        // ★ زر "إلغاء" (BUTTON_NEGATIVE — يسار):
        // يوقف العملية فوراً عبر cancelListener، ثم يُغلق الديالوج
        dialog.setButton(
                ProgressDialog.BUTTON_NEGATIVE,
                context.getString(R.string.action_cancel),
                (d, which) -> {
                    cancelListener.onCancel();
                    d.dismiss();
                });

        // ★ زر "إخفاء الإطار المنبثق" (BUTTON_POSITIVE — يمين):
        // يُغلق الديالوج فقط، العملية تكمل في الخلفية، الإشعار يتولى الإبلاغ
        dialog.setButton(
                ProgressDialog.BUTTON_POSITIVE,
                context.getString(R.string.action_hide_dialog),
                (d, which) -> {
                    hideListener.onHide();
                    d.dismiss();
                });

        return dialog;
    }


    // ════════════════════════════════════════════════════════════════════════
    // ★ (18)(19) إشعارات شريط الحالة للعمليات الجارية في الخلفية
    // ════════════════════════════════════════════════════════════════════════
    //
    // ★ إصلاحات المشكلات (1، 2، 3، 4):
    //   - (1)(2): الإشعار يُظهر فوراً من الـ ViewModel عند بدء العملية،
    //     وليس فقط عند ضغط "إخفاء".
    //   - (2): buildContentIntent() تستخدم الآن BatchOperationState.getSourceScreen()
    //     لقراءة شاشة المصدر وإضافة AppScreen.name() كـ EXTRA_TARGET_FRAGMENT في PendingIntent،
    //     مما يضمن أن الضغط على الإشعار يفتح الشاشة الصحيحة (LocalFonts أو Trash).
    //   - (1)(3): buildContentIntent() تُضيف «from_notification=true» لإخبار
    //     MainActivity بإعادة فتح ديالوج التقدم تلقائياً عند الضغط على الإشعار.
    //   - (4): كل إشعار يحمل زر "إلغاء" في النسخة الموسَّعة.
    //
    // ★ ملاحظة POST_NOTIFICATIONS:
    //   API 33+ يتطلب إذن POST_NOTIFICATIONS — يُطلب في MainActivity.
    //   requestNotificationPermissionIfNeeded() هناك تتكفل بذلك.
    //   NotificationManagerCompat.notify() يصمت تلقائياً إذا لم يُمنح الإذن،
    //   دون أن يُسبب استثناءً.
    // ════════════════════════════════════════════════════════════════════════


    // ─── إعداد قناة الإشعارات ───────────────────────────────────────────

    /**
     * يُنشئ قناة إشعارات عمليات السلة إذا لم تكن موجودة بعد.
     *
     * يُستدعى داخلياً قبل أي إشعار. آمن للاستدعاء المتعدد —
     * Android يتجاهل createNotificationChannel() إذا كانت القناة موجودة.
     *
     * الخصائص:
     *   • IMPORTANCE_LOW: لا صوت ولا اهتزاز أثناء تحديثات التقدم المتكررة
     *   • setShowBadge(false): لا تظهر نقطة العدد على أيقونة التطبيق
     *   • setSound(null, null): يُلغي أي صوت محتمل من القناة
     */
    private static void createChannelIfNeeded(@NonNull Context context) {
        NotificationManager nm = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) return;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.trash_notif_channel_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(context.getString(R.string.trash_notif_channel_desc));
        channel.setShowBadge(false);
        channel.setSound(null, null);
        nm.createNotificationChannel(channel);
    }


    // ─── منشئ الـ PendingIntent لفتح التطبيق والانتقال للشاشة الصحيحة ──

    /**
     * ★ (2) يبني PendingIntent يفتح MainActivity عند ضغط المستخدم على الإشعار،
     *       ويُنقله مباشرةً للشاشة التي أطلقت العملية (وليس دائماً للسلة).
     *
     * ★ الإصلاح الجوهري (الخطوة الخامسة — الأرقام السحرية → AppScreen) ★
     *   بدلاً من إرسال فهرس رقمي (int) في EXTRA_TARGET_FRAGMENT،
     *   يُرسَل الآن AppScreen.name() كـ String:
     *     "LOCAL_FONTS" = الخطوط المحلية
     *     "TRASH"       = سلة المحذوفات
     *   هذا يُزيل آخر اعتماد على الأرقام في نظام الإشعارات،
     *   ويجعل التوجيه محصّناً ضد أي تغيير مستقبلي في ترتيب AppScreen.
     *   handleIntent() في MainActivity تقرأ هذا النص وتُحوّله عبر AppScreen.valueOf(name).
     *
     * ★ إصلاح المشكلتين (1) و (3): إضافة «from_notification=true» ★
     *   يُخبر handleIntent() في MainActivity أن الفتح جاء من الإشعار،
     *   فتستدعي BatchOperationState.setShouldReopenDialog(true) لإعادة
     *   فتح ديالوج التقدم تلقائياً — سواء كان التطبيق مفتوحاً أو مغلقاً.
     *
     * FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP:
     *   يعيد تشغيل النسخة الموجودة من MainActivity دون إنشاء نسخة جديدة،
     *   مما يُعيد التطبيق للواجهة مع الحفاظ على حالتها كاملةً.
     *
     * FLAG_IMMUTABLE: مطلوب في API 31+ لأسباب أمنية.
     */
    @NonNull
    private static PendingIntent buildContentIntent(@NonNull Context context) {
        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        // ★ الإصلاح الجوهري: إرسال AppScreen.name() كـ String بدلاً من int ★
        // getSourceScreen() يُعيد الشاشة المُضبوطة في BatchOperationState
        // من قِبَل الـ Fragment أو ViewModel الذي بدأ العملية:
        //   AppScreen.LOCAL_FONTS = الخطوط المحلية
        //   AppScreen.TRASH       = سلة المحذوفات
        //   null                  = لا توجد عملية جارية (لا يُضاف Extra)
        AppScreen sourceScreen = BatchOperationState.getSourceScreen();
        if (sourceScreen != null) {
            openIntent.putExtra(MainActivity.EXTRA_TARGET_FRAGMENT, sourceScreen.name());
        }

        // ★ الإصلاح هنا: إخبار MainActivity أن الفتح تم عبر الإشعار ★
        // هذا يجعل handleIntent() تستدعي BatchOperationState.setShouldReopenDialog(true)
        // مما يُعيد فتح ديالوج التقدم تلقائياً عند الضغط على الإشعار من داخل التطبيق
        openIntent.putExtra("from_notification", true);

        return PendingIntent.getActivity(context, PI_REQUEST_CONTENT, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }


    // ─── منشئ الـ PendingIntent لزر الإلغاء (مشكلة 4) ─────────────────────

    /**
     * ★ (4) يبني PendingIntent يُرسل إشارة الإلغاء عند ضغط زر "إلغاء" في الإشعار.
     *
     * يُشغَّل NotificationActionReceiver الذي يستدعي BatchOperationState.requestCancel()
     * لإيقاف حلقة المعالجة في TrashRepository.
     *
     * ★ تذكير: NotificationActionReceiver يجب أن يكون مسجَّلاً في AndroidManifest.xml ★
     */
    @NonNull
    private static PendingIntent buildCancelIntent(@NonNull Context context) {
        Intent cancelIntent = new Intent(context, NotificationActionReceiver.class);
        cancelIntent.setAction(NotificationActionReceiver.ACTION_CANCEL);
        return PendingIntent.getBroadcast(context, PI_REQUEST_CANCEL, cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }


    // ─── منشئ الإشعار الأساسي ────────────────────────────────────────────

    /**
     * يبني القاعدة المشتركة لجميع إشعارات عمليات السلة.
     *
     * ★ (2): يُضاف contentIntent مبني على AppScreen.name() (لا دائماً للسلة).
     * ★ (1)(3): يُضاف «from_notification=true» في contentIntent لإعادة فتح الديالوج.
     * ★ (4): يُضاف زر "إلغاء" في الإشعار الموسَّع عبر addAction().
     *        في Android 12+ لا تظهر أيقونات الأزرار، لذلك نمرر 0 للأيقونة.
     *
     * @param title    نص العنوان (من Plurals بدون أرقام — إصلاح المشكلة 6)
     * @param progress عدد الملفات المُعالَجة حتى الآن
     * @param total    إجمالي الملفات (يظهر في نص "X/Y" داخل الإشعار)
     */
    @NonNull
    private static NotificationCompat.Builder buildNotificationBase(
            @NonNull Context context,
            @NonNull String title,
            int progress,
            int total) {

        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(dev.oneuiproject.oneui.R.drawable.ic_oui_delete)
                .setContentTitle(title)
                .setContentText(progress + "/" + total)
                .setProgress(total, progress, false)
                // setOngoing: يمنع المستخدم من إزالة الإشعار يدوياً أثناء العملية
                .setOngoing(true)
                // setOnlyAlertOnce: لا صوت/اهتزاز عند تحديث الإشعار — فقط عند ظهوره أول مرة
                .setOnlyAlertOnce(true)
                // ★ (2)(1)(3): فتح التطبيق والانتقال للشاشة الصحيحة مع إعادة فتح الديالوج ★
                .setContentIntent(buildContentIntent(context))
                // ★ (4): زر "إلغاء" في الإشعار الموسَّع — يوقف العملية من خارج التطبيق ★
                .addAction(0,
                        context.getString(R.string.action_cancel),
                        buildCancelIntent(context));
    }


    // ─── النقل إلى السلة ─────────────────────────────────────────────────

    /**
     * ★ (1)(2): يُظهر إشعار بداية عملية النقل إلى سلة المحذوفات.
     *
     * يُستدعى الآن مباشرةً عند بدء العملية في TrashViewModel.moveToTrash()
     * وفي LocalFontListFragment.showMoveToTrashProgressDialog()،
     * بغض النظر عن حالة ديالوج التقدم (مرئي أو مخفي).
     *
     * ★ (6): العنوان بصيغة عامة — getQuantityString بمعامل واحد (بدون %d) ★
     *
     * @param context السياق
     * @param total   إجمالي الملفات المراد نقلها
     */
    public static void showMoveToTrashNotification(@NonNull Context context, int total) {
        createChannelIfNeeded(context);
        // ★ (6): معامل واحد فقط — الـ plural form يُحسب من total لكن بدون إدراج رقم ★
        String title = context.getResources()
                .getQuantityString(R.plurals.progress_moving_to_trash, total);
        NotificationManagerCompat.from(context).notify(NOTIF_ID_MOVE,
                buildNotificationBase(context, title, 0, total).build());
    }

    /**
     * يُحدّث إشعار تقدم عملية النقل إلى السلة.
     *
     * ★ (5): يُستدعى الآن من خيط الخلفية مباشرةً في TrashViewModel وفي
     *   LocalFontListFragment (عبر progress callback)، مما يضمن استمرار
     *   تحديث الإشعار حتى لو كان التطبيق في الخلفية.
     *
     * @param context  السياق
     * @param progress عدد الملفات المُعالَجة حتى الآن
     * @param total    إجمالي الملفات
     */
    public static void updateMoveToTrashNotification(
            @NonNull Context context, int progress, int total) {
        // ★ (6): معامل واحد فقط ★
        String title = context.getResources()
                .getQuantityString(R.plurals.progress_moving_to_trash, total);
        NotificationManagerCompat.from(context).notify(NOTIF_ID_MOVE,
                buildNotificationBase(context, title, progress, total).build());
    }

    /**
     * يُزيل إشعار عملية النقل إلى السلة.
     * يُستدعى عند اكتمال العملية أو إلغائها.
     */
    public static void dismissMoveToTrashNotification(@NonNull Context context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID_MOVE);
    }


    // ─── الاستعادة من السلة ──────────────────────────────────────────────

    /**
     * ★ (1)(2): يُظهر إشعار بداية عملية استعادة الملفات من السلة.
     *
     * يُستدعى الآن مباشرةً عند بدء العملية في TrashViewModel.restoreFonts()،
     * بغض النظر عن حالة ديالوج التقدم.
     *
     * ★ (6): العنوان بصيغة عامة بدون أرقام ★
     *
     * @param context السياق
     * @param total   إجمالي الملفات المراد استعادتها
     */
    public static void showRestoreNotification(@NonNull Context context, int total) {
        createChannelIfNeeded(context);
        // ★ (6): معامل واحد فقط ★
        String title = context.getResources()
                .getQuantityString(R.plurals.progress_restoring, total);
        NotificationManagerCompat.from(context).notify(NOTIF_ID_RESTORE,
                buildNotificationBase(context, title, 0, total).build());
    }

    /**
     * يُحدّث إشعار تقدم عملية الاستعادة.
     *
     * ★ (5): يُستدعى من خيط الخلفية في TrashViewModel مباشرةً ★
     *
     * @param context  السياق
     * @param progress عدد الملفات المُستعادة حتى الآن
     * @param total    إجمالي الملفات
     */
    public static void updateRestoreNotification(
            @NonNull Context context, int progress, int total) {
        // ★ (6): معامل واحد فقط ★
        String title = context.getResources()
                .getQuantityString(R.plurals.progress_restoring, total);
        NotificationManagerCompat.from(context).notify(NOTIF_ID_RESTORE,
                buildNotificationBase(context, title, progress, total).build());
    }

    /**
     * يُزيل إشعار عملية الاستعادة.
     * يُستدعى عند اكتمال العملية أو إلغائها.
     */
    public static void dismissRestoreNotification(@NonNull Context context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID_RESTORE);
    }


    // ─── الحذف النهائي من السلة ──────────────────────────────────────────

    /**
     * ★ (1)(2): يُظهر إشعار بداية عملية الحذف النهائي.
     *
     * يُستدعى الآن مباشرةً عند بدء العملية في TrashViewModel.deletePermanently()
     * وTrashViewModel.emptyTrash()، بغض النظر عن حالة ديالوج التقدم.
     *
     * ★ (6): العنوان بصيغة عامة بدون أرقام ★
     *
     * @param context السياق
     * @param total   إجمالي الملفات المراد حذفها نهائياً
     */
    public static void showDeleteNotification(@NonNull Context context, int total) {
        createChannelIfNeeded(context);
        // ★ (6): معامل واحد فقط ★
        String title = context.getResources()
                .getQuantityString(R.plurals.progress_deleting, total);
        NotificationManagerCompat.from(context).notify(NOTIF_ID_DELETE,
                buildNotificationBase(context, title, 0, total).build());
    }

    /**
     * يُحدّث إشعار تقدم عملية الحذف النهائي.
     *
     * ★ (5): يُستدعى من خيط الخلفية في TrashViewModel مباشرةً ★
     *
     * @param context  السياق
     * @param progress عدد الملفات المحذوفة حتى الآن
     * @param total    إجمالي الملفات
     */
    public static void updateDeleteNotification(
            @NonNull Context context, int progress, int total) {
        // ★ (6): معامل واحد فقط ★
        String title = context.getResources()
                .getQuantityString(R.plurals.progress_deleting, total);
        NotificationManagerCompat.from(context).notify(NOTIF_ID_DELETE,
                buildNotificationBase(context, title, progress, total).build());
    }

    /**
     * يُزيل إشعار عملية الحذف النهائي.
     * يُستدعى عند اكتمال العملية أو إلغائها.
     */
    public static void dismissDeleteNotification(@NonNull Context context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID_DELETE);
    }
            }
