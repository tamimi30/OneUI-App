package com.oneui.fontviewer.utils.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class NotificationActionReceiver extends BroadcastReceiver {

    private static final String TAG = "NotifActionReceiver";

    public static final String ACTION_CANCEL =
            "com.oneui.fontviewer.ACTION_CANCEL_OPERATION";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }

        if (ACTION_CANCEL.equals(intent.getAction())) {
            Log.d(TAG, "Cancel requested from notification — setting global cancel flag");

            BatchOperationState.requestCancel();
        }
    }
}
