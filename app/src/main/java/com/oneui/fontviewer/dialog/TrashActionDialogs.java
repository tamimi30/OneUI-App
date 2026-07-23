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

public final class TrashActionDialogs {

    private static final String CHANNEL_ID = "trash_ops";

    public static final int NOTIF_ID_MOVE    = 1001;
    public static final int NOTIF_ID_RESTORE = 1002;
    public static final int NOTIF_ID_DELETE  = 1003;

    private static final int PI_REQUEST_CONTENT = 100;
    private static final int PI_REQUEST_CANCEL  = 101;

    private TrashActionDialogs() {}


    public interface OnConfirmListener {
        void onConfirmed();
    }

    public interface OnProgressCancelListener {
        void onCancel();
    }

    public interface OnProgressHideListener {
        void onHide();
    }

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

    public static void showDeletePermanentlyDialog(
            @NonNull Context context,
            int count,
            @NonNull OnConfirmListener listener) {

        if (count <= 0) return;

        String message = context.getResources()
                .getQuantityString(R.plurals.dialog_delete_permanently_question, count, count);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setMessage(message)
                .setPositiveButton(R.string.action_delete,
                        (d, w) -> listener.onConfirmed())
                .setNegativeButton(R.string.action_cancel, null)
                .show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(ContextCompat.getColor(context,
                        dev.oneuiproject.oneui.design.R.color.oui_functional_red_color));
    }

    @NonNull
    public static ProgressDialog createMoveToTrashProgressDialog(
            @NonNull Context context,
            int total,
            @NonNull OnProgressCancelListener cancelListener,
            @NonNull OnProgressHideListener hideListener) {

        String title = context.getResources()
                .getQuantityString(R.plurals.progress_moving_to_trash, total);

        return buildProgressDialog(context, title, total, cancelListener, hideListener);
    }

    @NonNull
    public static ProgressDialog createDeleteProgressDialog(
            @NonNull Context context,
            int total,
            @NonNull OnProgressCancelListener cancelListener,
            @NonNull OnProgressHideListener hideListener) {

        String title = context.getResources()
                .getQuantityString(R.plurals.progress_deleting, total);

        return buildProgressDialog(context, title, total, cancelListener, hideListener);
    }

    @NonNull
    public static ProgressDialog createRestoreProgressDialog(
            @NonNull Context context,
            int total,
            @NonNull OnProgressCancelListener cancelListener,
            @NonNull OnProgressHideListener hideListener) {

        String title = context.getResources()
                .getQuantityString(R.plurals.progress_restoring, total);

        return buildProgressDialog(context, title, total, cancelListener, hideListener);
    }

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

        dialog.setMax(total);

        dialog.setCancelable(false);

        dialog.setButton(
                ProgressDialog.BUTTON_NEGATIVE,
                context.getString(R.string.action_cancel),
                (d, which) -> {
                    cancelListener.onCancel();
                    d.dismiss();
                });

        dialog.setButton(
                ProgressDialog.BUTTON_POSITIVE,
                context.getString(R.string.action_hide_dialog),
                (d, which) -> {
                    hideListener.onHide();
                    d.dismiss();
                });

        return dialog;
    }

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

    @NonNull
    private static PendingIntent buildContentIntent(@NonNull Context context) {
        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        AppScreen sourceScreen = BatchOperationState.getSourceScreen();
        if (sourceScreen != null) {
            openIntent.putExtra(MainActivity.EXTRA_TARGET_FRAGMENT, sourceScreen.name());
        }

        openIntent.putExtra("from_notification", true);

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
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(buildContentIntent(context))
                .addAction(0,
                        context.getString(R.string.action_cancel),
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
