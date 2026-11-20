package com.oneui.fontviewer.service;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.oneui.fontviewer.R;

public class OperationForegroundService extends Service {

    private static final String TAG = "OperationFgService";

    public static final String EXTRA_NOTIF_ID        = "extra_notif_id";

    public static final String EXTRA_TITLE           = "extra_title";

    public static final String EXTRA_TOTAL           = "extra_total";

    private int mNotifId = OperationNotifications.NOTIF_ID_MOVE;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: foreground service starting");

        if (intent != null) {
            mNotifId = intent.getIntExtra(EXTRA_NOTIF_ID, OperationNotifications.NOTIF_ID_MOVE);
            String title      = intent.getStringExtra(EXTRA_TITLE);
            int    total      = intent.getIntExtra(EXTRA_TOTAL, 0);

            if (title == null) title = getString(R.string.app_name);

            Notification notification = buildInitialNotification(title, total);
            startForeground(mNotifId, notification);

            Log.d(TAG, "startForeground: id=" + mNotifId + ", total=" + total
                    + ", source=" + BatchOperationState.getSourceDrawerListItem());
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

    private Notification buildInitialNotification(String title, int total) {
        OperationNotifications.createChannelIfNeeded(this);
        return OperationNotifications.buildNotificationBase(this, title, 0, total).build();
    }

    private Notification buildFallbackNotification() {
        OperationNotifications.createChannelIfNeeded(this);
        return new NotificationCompat.Builder(this, OperationNotifications.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.app_name))
                .setOngoing(true)
                .build();
    }
}
