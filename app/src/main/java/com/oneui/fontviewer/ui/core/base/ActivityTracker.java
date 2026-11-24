package com.oneui.fontviewer.ui.core.base;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * يتتبّع الأنشطة الحية في التطبيق ليتمكن من إعادة بنائها كلها دفعة واحدة عند تغيير الثيم أو الخط أو اللغة.
 * يُسجَّل مرة واحدة من FontViewerApp عبر {@link #register(Application)}.
 */
public final class ActivityTracker {

    private static final List<WeakReference<Activity>> activities = new ArrayList<>();

    private ActivityTracker() {
    }

    public static void register(Application application) {
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
                activities.add(new WeakReference<>(activity));
            }

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {
                for (int i = activities.size() - 1; i >= 0; i--) {
                    Activity a = activities.get(i).get();
                    if (a == null || a == activity) {
                        activities.remove(i);
                    }
                }
            }

            @Override public void onActivityStarted(@NonNull Activity activity) {}
            @Override public void onActivityResumed(@NonNull Activity activity) {}
            @Override public void onActivityPaused(@NonNull Activity activity) {}
            @Override public void onActivityStopped(@NonNull Activity activity) {}
            @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}
        });
    }

    /**
     * إعادة بناء جميع الأنشطة الحية
     * تُستخدم عند تغيير الثيم أو الخط أو الإعدادات العامة التي تتطلب
     * إعادة رسم كامل للواجهة
     */
    public static void recreateAllActivities() {
        for (WeakReference<Activity> ref : new ArrayList<>(activities)) {
            Activity act = ref.get();
            if (act != null && !act.isFinishing()) {
                act.recreate();
            }
        }
    }
}
