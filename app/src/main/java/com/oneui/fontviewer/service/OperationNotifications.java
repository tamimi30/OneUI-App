package com.oneui.fontviewer.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.oneui.fontviewer.R;

public final class OperationNotifications {

    public static final String CHANNEL_ID = "trash_ops";

    // مفاتيح الـ extras التي يضعها الإشعار في Intent فتح التطبيق، وتقرؤها الواجهة عند الاستلام
    public static final String EXTRA_TARGET_FRAGMENT   = "target_fragment";
    public static final String EXTRA_FROM_NOTIFICATION = "from_notification";

    public static final int NOTIF_ID_MOVE    = 1001;
    public static final int NOTIF_ID_RESTORE = 1002;
    public static final int NOTIF_ID_DELETE  = 1003;

    private static final int PI_REQUEST_CONTENT = 100;
    private static final int PI_REQUEST_CANCEL  = 101;

    private OperationNotifications() {}

    public static void createChannelIfNeeded(@NonNull Context context) {
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

    @Nullable
    private static PendingIntent buildContentIntent(@NonNull Context context) {
        // نحدد نشاط الدخول الرئيسي للتطبيق من المانيفست بدل الإشارة إلى صنف من طبقة الواجهة،
        // ونستخدم مكوّنه فقط (دون action/category الخاصة بالمشغّل) حتى يصل الـ Intent عبر onNewIntent.
        Intent launchIntent = context.getPackageManager()
                .getLaunchIntentForPackage(context.getPackageName());
        ComponentName entry = launchIntent != null ? launchIntent.getComponent() : null;
        if (entry == null) return null;

        Intent openIntent = new Intent();
        openIntent.setComponent(entry);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        String sourceScreen = BatchOperationState.getSourceScreen();
        if (sourceScreen != null) {
            openIntent.putExtra(EXTRA_TARGET_FRAGMENT, sourceScreen);
        }

        openIntent.putExtra(EXTRA_FROM_NOTIFICATION, true);

        return PendingIntent.getActivity(context, PI_REQUEST_CONTENT, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @NonNull
    private static PendingIntent buildCancelIntent(@NonNull Context context) {
        Intent cancelIntent = new Intent(context, NotificationActionReceiver.class);
        cancelIntent.setAction(NotificationActionReceiver.ACTION_CANCEL);
        return PendingIntent.getBroadcast(context, PI_REQUEST_CANCEL, cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @NonNull
    public static NotificationCompat.Builder buildNotificationBase(
            @NonNull Context context,
            @NonNull String title,
            int progress,
            int total) {

        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(progress + "/" + total)
                .setProgress(total, progress, false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(buildContentIntent(context)) // قد تكون null فلا يُضاف إجراء فتح
                .addAction(0,
                        context.getString(android.R.string.cancel),
                        buildCancelIntent(context));
    }

    public static void showMoveToTrashNotification(@NonNull Context context, int total) {
        createChannelIfNeeded(context);
        String title = context.getResources()
                .getQuantityString(R.plurals.progress_moving_to_trash, total);
        NotificationManagerCompat.from(context).notify(NOTIF_ID_MOVE,
                buildNotificationBase(context, title, 0, total).build());
    }

    public static void updateMoveToTrashNotification(
            @NonNull Context context, int progress, int total) {
        String title = context.getResources()
                .getQuantityString(R.plurals.progress_moving_to_trash, total);
        NotificationManagerCompat.from(context).notify(NOTIF_ID_MOVE,
                buildNotificationBase(context, title, progress, total).build());
    }

    public static void dismissMoveToTrashNotification(@NonNull Context context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID_MOVE);
    }

    public static void showRestoreNotification(@NonNull Context context, int total) {
        createChannelIfNeeded(context);
        String title = context.getResources()
                .getQuantityString(R.plurals.progress_restoring, total);
        NotificationManagerCompat.from(context).notify(NOTIF_ID_RESTORE,
                buildNotificationBase(context, title, 0, total).build());
    }

    public static void updateRestoreNotification(
            @NonNull Context context, int progress, int total) {
        String title = context.getResources()
                .getQuantityString(R.plurals.progress_restoring, total);
        NotificationManagerCompat.from(context).notify(NOTIF_ID_RESTORE,
                buildNotificationBase(context, title, progress, total).build());
    }

    public static void dismissRestoreNotification(@NonNull Context context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID_RESTORE);
    }

    public static void showDeleteNotification(@NonNull Context context, int total) {
        createChannelIfNeeded(context);
        String title = context.getResources()
                .getQuantityString(R.plurals.progress_deleting, total);
        NotificationManagerCompat.from(context).notify(NOTIF_ID_DELETE,
                buildNotificationBase(context, title, 0, total).build());
    }

    public static void updateDeleteNotification(
            @NonNull Context context, int progress, int total) {
        String title = context.getResources()
                .getQuantityString(R.plurals.progress_deleting, total);
        NotificationManagerCompat.from(context).notify(NOTIF_ID_DELETE,
                buildNotificationBase(context, title, progress, total).build());
    }

    public static void dismissDeleteNotification(@NonNull Context context) {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID_DELETE);
    }
}
