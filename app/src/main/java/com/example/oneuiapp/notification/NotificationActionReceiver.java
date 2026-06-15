package com.example.oneuiapp.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.oneuiapp.notification.BatchOperationState;

/**
 * NotificationActionReceiver — مُستقبل إجراءات إشعارات عمليات السلة
 *
 * ════════════════════════════════════════════════════════════════════════
 * الغرض:
 *   يُستدعى هذا الـ Receiver عند ضغط المستخدم على زر "إلغاء" في إشعار
 *   عملية السلة الجارية (نقل / استعادة / حذف نهائي).
 *
 * آلية الإلغاء:
 *   يضبط عَلَم الإلغاء العام في BatchOperationState بقيمة true.
 *   هذا العَلَم هو نفسه الـ AtomicBoolean المُمرَّر للـ Repository عند بدء العملية،
 *   لذا ستتوقف حلقة المعالجة في TrashRepository في الدورة التالية.
 *
 * التسجيل:
 *   يجب تسجيله في AndroidManifest.xml:
 *   <receiver
 *       android:name=".NotificationActionReceiver"
 *       android:exported="false">
 *       <intent-filter>
 *           <action android:name="com.example.oneuiapp.ACTION_CANCEL_OPERATION"/>
 *       </intent-filter>
 *   </receiver>
 *
 * المسار: app/src/main/java/com/example/oneuiapp/NotificationActionReceiver.java
 * ════════════════════════════════════════════════════════════════════════
 */
public class NotificationActionReceiver extends BroadcastReceiver {

    private static final String TAG = "NotifActionReceiver";

    /**
     * الإجراء المُرسَل عند ضغط زر "إلغاء" في الإشعار.
     * يُستخدم في TrashActionDialogs لإنشاء الـ PendingIntent.
     */
    public static final String ACTION_CANCEL =
            "com.example.oneuiapp.ACTION_CANCEL_OPERATION";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        if (ACTION_CANCEL.equals(intent.getAction())) {
            Log.d(TAG, "Cancel requested from notification — setting global cancel flag");

            // ★ ضبط عَلَم الإلغاء العام في BatchOperationState ★
            // هذا العَلَم هو نفسه الـ AtomicBoolean الذي يتحقق منه TrashRepository
            // في حلقة المعالجة، لذا ستتوقف العملية في الدورة التالية فور قراءة هذه القيمة.
            BatchOperationState.requestCancel();
        }
    }
}
