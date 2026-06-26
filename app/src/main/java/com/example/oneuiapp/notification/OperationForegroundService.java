package com.example.oneuiapp.notification;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.oneuiapp.R;
import com.example.oneuiapp.activity.AppScreen;
import com.example.oneuiapp.activity.MainActivity;
import com.example.oneuiapp.dialog.TrashActionDialogs;

/**
 * OperationForegroundService — الدرع الواقي لعمليات السلة في الخلفية
 *
 * ════════════════════════════════════════════════════════════════════════
 * الغرض:
 *   يُبقي هذا الـ Service العملية الجارية (نقل/استعادة/حذف) حيةً حتى لو
 *   أغلق المستخدم التطبيق أو أزاله من التطبيقات الأخيرة.
 *
 * آلية العمل — المشكلتان (1) و(3):
 *   المشكلة القديمة: الإشعار عادي (NotificationManagerCompat.notify) يبقى في الشريط
 *   إذا قُتلت العملية، لأن Android لا يُزيله تلقائياً. الإشعار يتجمد ولا يمكن إزالته.
 *
 *   الحل:
 *   1. startForeground(notifId, notification): يُحوّل إشعار التقدم إلى إشعار مرتبط بالخدمة
 *      بنفس الـ ID الذي تستخدمه TrashActionDialogs (مثلاً: 1001 لـ NOTIF_ID_MOVE).
 *   2. TrashRepository يُحدِّث هذا الإشعار عبر NotificationManagerCompat.notify(سنفس ID)
 *      — الإشعار المرتبط بالخدمة يتحدث بشكل طبيعي.
 *   3. عند انتهاء العملية: context.stopService() → onDestroy() → stopForeground() → إزالة الإشعار.
 *   4. عند قتل العملية من التطبيقات الأخيرة: Android يُوقف الخدمة تلقائياً
 *      ويُزيل إشعار الـ Foreground Service — هذا يحل المشكلة (3): لا إشعار عالق.
 *
 * START_NOT_STICKY:
 *   لا تُعاد تشغيل الخدمة إذا قتلها النظام بعد نفاد ذاكرة RAM،
 *   لأنه لا فائدة من إعادة تشغيل خدمة بدون استمرار العملية الأصلية.
 *
 * التسجيل المطلوب في AndroidManifest.xml:
 *   <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
 *   <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
 *   <service android:name=".utils.OperationForegroundService"
 *            android:exported="false"
 *            android:foregroundServiceType="dataSync" />
 *
 * المسار: app/src/main/java/com/example/oneuiapp/utils/OperationForegroundService.java
 * ════════════════════════════════════════════════════════════════════════
 */
public class OperationForegroundService extends Service {

    private static final String TAG = "OperationFgService";

    // ─────────────────────────────────────────────────────────────────────
    // Extras للـ Intent — يُرسلها TrashRepository عند تشغيل الخدمة
    // ─────────────────────────────────────────────────────────────────────

    /**
     * معرّف الإشعار المُستخدَم في startForeground() — يجب أن يطابق
     * NOTIF_ID_* المُعرَّفة في TrashActionDialogs (1001، 1002، 1003).
     * يضمن هذا أن TrashRepository يُحدِّث الإشعار الصحيح عبر notify(sameId).
     */
    public static final String EXTRA_NOTIF_ID        = "extra_notif_id";

    /**
     * عنوان الإشعار الأولي من Plurals (مثال: "جارٍ نقل الملفات إلى سلة المحذوفات…").
     * يُبنى في TrashRepository بعد معرفة العدد الإجمالي.
     */
    public static final String EXTRA_TITLE           = "extra_title";

    /**
     * إجمالي عدد الملفات — يُستخدم لعرض "0/total" في الإشعار الأولي.
     * يتغير إلى "X/total" عند تحديثات TrashRepository اللاحقة.
     */
    public static final String EXTRA_TOTAL           = "extra_total";

    /**
     * ★ الخطوة الخامسة (الأرقام السحرية → AppScreen):
     * اسم الشاشة المصدر كـ String (AppScreen.name()) بدلاً من فهرس رقمي (int).
     *
     * يُضمَّن في PendingIntent الخاص بالإشعار لتوجيه المستخدم للشاشة الصحيحة
     * عند الضغط عليه — يُمرَّر مباشرةً إلى MainActivity.EXTRA_TARGET_FRAGMENT.
     *
     * القيم المقبولة: "LOCAL_FONTS"، "TRASH"، "FAVORITES"، أو null.
     * يُعيَّن بواسطة من يُشغّل الخدمة (TrashViewModel / LocalFontListViewModel)
     * عبر: intent.putExtra(EXTRA_SOURCE_FRAGMENT, AppScreen.TRASH.name())
     */
    public static final String EXTRA_SOURCE_FRAGMENT = "extra_source_fragment";

    // ─────────────────────────────────────────────────────────────────────
    // الحالة الداخلية
    // ─────────────────────────────────────────────────────────────────────

    /** نفس قناة الإشعارات المُعرَّفة في TrashActionDialogs لضمان التوافق */
    private static final String CHANNEL_ID = "trash_ops";

    /**
     * آخر notif_id استُخدم في startForeground().
     * يُستخدم في onDestroy() لضمان إزالة الإشعار الصحيح عند وقف الخدمة.
     */
    private int mNotifId = TrashActionDialogs.NOTIF_ID_MOVE;

    // ═══════════════════════════════════════════════════════════════════════
    // دورة حياة الخدمة
    // ═══════════════════════════════════════════════════════════════════════

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: foreground service starting");

        if (intent != null) {
            mNotifId = intent.getIntExtra(EXTRA_NOTIF_ID, TrashActionDialogs.NOTIF_ID_MOVE);
            String title      = intent.getStringExtra(EXTRA_TITLE);
            int    total      = intent.getIntExtra(EXTRA_TOTAL, 0);
            // ★ الخطوة الخامسة: قراءة AppScreen.name() كـ String بدلاً من int ★
            String sourceName = intent.getStringExtra(EXTRA_SOURCE_FRAGMENT);

            if (title == null) title = getString(R.string.app_name);

            Notification notification = buildInitialNotification(title, total, sourceName);
            startForeground(mNotifId, notification);

            Log.d(TAG, "startForeground: id=" + mNotifId + ", total=" + total
                    + ", source=" + sourceName);
        } else {
            // ★ حماية دفاعية: يجب استدعاء startForeground خلال 5 ثواني من onStartCommand() ★
            // غياب intent نادر جداً — يحدث إذا أُعيدت تشغيل الخدمة بواسطة النظام
            // وهو مستحيل هنا لأننا نستخدم START_NOT_STICKY، لكن نبقى حذرين.
            Log.w(TAG, "onStartCommand: null intent — using fallback notification");
            startForeground(mNotifId, buildFallbackNotification());
        }

        return START_NOT_STICKY;
    }

    /**
     * ★ الإصلاح الجوهري للمشكلة (3): إزالة الإشعار تلقائياً عند وقف الخدمة ★
     *
     * يُستدعى في حالتين:
     *   1. انتهاء العملية بشكل طبيعي:
     *      TrashRepository.finally → appContext.stopService() → onDestroy() → stopForeground(true).
     *   2. قتل العملية من التطبيقات الأخيرة:
     *      Android يُوقف الخدمة → يُزيل إشعار Foreground Service تلقائياً.
     *      (Android يُزيل إشعار Foreground Service حتى لو لم يُستدعَ onDestroy().)
     *
     * هذا يضمن: لا إشعار عالق في شريط الحالة حتى بعد قتل التطبيق.
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy: removing foreground notification (id=" + mNotifId + ")");
        stopForeground(STOP_FOREGROUND_REMOVE);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // بناء الإشعارات
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * يبني الإشعار الأولي الذي يُمرَّر إلى startForeground().
     *
     * ★ نفس بنية إشعارات TrashActionDialogs ★:
     *   - نفس CHANNEL_ID و SmallIcon لضمان التوافق البصري.
     *   - setOngoing(true): يمنع المستخدم من إزالة الإشعار يدوياً أثناء العملية.
     *   - setOnlyAlertOnce(true): لا صوت/اهتزاز عند تحديثات التقدم المتكررة.
     *
     * ★ PendingIntents مطابقة لـ TrashActionDialogs ★:
     *   - contentIntent: يفتح MainActivity ويتنقل للشاشة المصدر (إصلاح م2).
     *   - cancelIntent: يُرسل إشارة إلغاء إلى NotificationActionReceiver.
     *
     * بعد استدعاء startForeground(notifId, notification)، يُحدِّث TrashRepository هذا الإشعار
     * عبر NotificationManagerCompat.notify(notifId, updatedNotif) بنفس ID —
     * يُحدِّث العداد (X/Y) في الوقت الفعلي.
     *
     * ★ الخطوة الخامسة: sourceName (String) بدلاً من sourceIndex (int) ★
     * يُمرَّر مباشرةً إلى EXTRA_TARGET_FRAGMENT كـ AppScreen.name() دون أي تحويل رقمي.
     *
     * @param title      عنوان العملية من Plurals
     * @param total      إجمالي الملفات (يظهر في "0/total")
     * @param sourceName اسم الشاشة المصدر (AppScreen.name()) للتنقل الصحيح عند الضغط
     */
    private Notification buildInitialNotification(
            String title, int total, String sourceName) {
        ensureChannelCreated();

        // ★ الخطوة الخامسة: PendingIntent يوجّه للشاشة المصدر بـ AppScreen.name() ★
        // يُمرَّر sourceName مباشرةً كـ EXTRA_TARGET_FRAGMENT (String) —
        // handleIntent() في MainActivity تُحوّله عبر AppScreen.valueOf(name).
        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (sourceName != null) {
            openIntent.putExtra(MainActivity.EXTRA_TARGET_FRAGMENT, sourceName);
        }
        // ★ إخبار التطبيق أن الفتح تم عبر الإشعار لإعادة فتح ديالوج التقدم ★
        openIntent.putExtra("from_notification", true);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this, 100, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // ★ (4): PendingIntent لزر "إلغاء" في الإشعار الموسَّع ★
        Intent cancelIntent = new Intent(this, NotificationActionReceiver.class);
        cancelIntent.setAction(NotificationActionReceiver.ACTION_CANCEL);
        PendingIntent cancelPendingIntent = PendingIntent.getBroadcast(
                this, 101, cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(dev.oneuiproject.oneui.R.drawable.ic_oui_delete)
                .setContentTitle(title)
                .setContentText("0/" + total)
                .setProgress(total, 0, false)
                // setOngoing: يمنع المستخدم من إزالة الإشعار يدوياً
                .setOngoing(true)
                // setOnlyAlertOnce: لا صوت/اهتزاز عند تحديثات التقدم
                .setOnlyAlertOnce(true)
                .setContentIntent(contentIntent)
                // (4): بدون أيقونة (0) — في Android 12+ لا تظهر أيقونات أزرار الإشعار
                .addAction(0, getString(R.string.action_cancel), cancelPendingIntent)
                .build();
    }

    /**
     * إشعار احتياطي يُستخدم عند غياب Intent (حماية دفاعية).
     * يضمن استدعاء startForeground() دائماً خلال 5 ثواني من onStartCommand().
     */
    private Notification buildFallbackNotification() {
        ensureChannelCreated();
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(dev.oneuiproject.oneui.R.drawable.ic_oui_delete)
                .setContentTitle(getString(R.string.app_name))
                .setOngoing(true)
                .build();
    }

    /**
     * يُنشئ قناة إشعارات عمليات السلة إذا لم تكن موجودة بعد.
     *
     * مطابق لـ createChannelIfNeeded() في TrashActionDialogs لضمان استخدام
     * نفس الإعدادات (IMPORTANCE_LOW، بلا صوت، بلا شارة).
     * آمن للاستدعاء المتعدد — Android يتجاهل الإنشاء المكرر للقناة الموجودة.
     */
    private void ensureChannelCreated() {
        NotificationManager nm =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) return;

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.trash_notif_channel_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.trash_notif_channel_desc));
        channel.setShowBadge(false);
        channel.setSound(null, null);
        nm.createNotificationChannel(channel);
    }
}
