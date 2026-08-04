package com.oneui.fontviewer.utils.notification;

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
import androidx.core.app.NotificationCompat;

import androidx.annotation.Nullable;

import com.oneui.fontviewer.R;
import com.oneui.fontviewer.activity.AppScreen;
import com.oneui.fontviewer.activity.MainActivity;
import com.oneui.fontviewer.dialog.TrashActionDialogs;

public class OperationForegroundService extends Service {

    private static final String TAG = "OperationFgService";

    public static final String EXTRA_NOTIF_ID        = "extra_notif_id";

    public static final String EXTRA_TITLE           = "extra_title";

    public static final String EXTRA_TOTAL           = "extra_total";

    public static final String EXTRA_SOURCE_FRAGMENT = "extra_source_fragment";

    private static final String CHANNEL_ID = "trash_ops";

    private int mNotifId = TrashActionDialogs.NOTIF_ID_MOVE;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: foreground service starting");

        if (intent != null) {
            mNotifId = intent.getIntExtra(EXTRA_NOTIF_ID, TrashActionDialogs.NOTIF_ID_MOVE);
            String title      = intent.getStringExtra(EXTRA_TITLE);
            int    total      = intent.getIntExtra(EXTRA_TOTAL, 0);
            String sourceName = intent.getStringExtra(EXTRA_SOURCE_FRAGMENT);

            if (title == null) {
                title = getString(R.string.app_name);
            }

            Notification notification = buildInitialNotification(title, total, sourceName);
            startForeground(mNotifId, notification);

            Log.d(TAG, "startForeground: id=" + mNotifId + ", total=" + total
                    + ", source=" + sourceName);
        } else {
            Log.w(TAG, "onStartCommand: null intent — using fallback notification");
            startForeground(mNotifId, buildFallbackNotification());
        }

        return START_NOT_STICKY;
    }

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

    private Notification buildInitialNotification(
            String title, int total, String sourceName) {
        ensureChannelCreated();

        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (sourceName != null) {
            openIntent.putExtra(MainActivity.EXTRA_TARGET_FRAGMENT, sourceName);
        }
        openIntent.putExtra("from_notification", true);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this, 100, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

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
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(contentIntent)
                .addAction(0, getString(android.R.string.cancel), cancelPendingIntent)
                .build();
    }

    private Notification buildFallbackNotification() {
        ensureChannelCreated();
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(dev.oneuiproject.oneui.R.drawable.ic_oui_delete)
                .setContentTitle(getString(R.string.app_name))
                .setOngoing(true)
                .build();
    }

    private void ensureChannelCreated() {
        NotificationManager nm =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) {
            return;
        }

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
