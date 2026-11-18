package com.oneui.fontviewer.ui.navigation;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;

import com.oneui.fontviewer.data.repository.UpdateRepository;
import com.oneui.fontviewer.ui.widget.layout.OneUiDrawerLayout;

/**
 * يعرض نقطة توفر التحديث على أيقونة درج التنقل وعلى أيقونة الإعدادات داخل الدرج،
 * ويحدّثها فوراً عند تغيّر حالة التحديث، كما في تطبيقات Samsung.
 */
public final class UpdateBadgeController {

    private UpdateBadgeController() {
    }

    public static void attach(
            @NonNull LifecycleOwner owner,
            @NonNull OneUiDrawerLayout drawerLayout,
            @NonNull UpdateRepository repository) {

        // لا نلمس الأيقونات إلا عند تغيّر الحالة فعلاً، فلا نعيد تعيين أيقونة الدرج بلا داعٍ
        final boolean[] shown = {false};

        repository.getUpdateAvailable().observe(owner, available -> {
            boolean show = Boolean.TRUE.equals(available);
            if (show == shown[0]) {
                return;
            }
            shown[0] = show;

            int badge = show ? OneUiDrawerLayout.DOT_BADGE : 0;
            drawerLayout.setButtonBadges(badge, badge);
        });
    }
}
