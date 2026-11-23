package com.oneui.fontviewer.ui.appscreen.main.navigation;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;

import com.oneui.fontviewer.data.update.model.UpdateInfo;
import com.oneui.fontviewer.ui.core.widget.layout.OneUiDrawerLayout;

/**
 * يعرض المؤشر البرتقالي (الدائرة التي فيها N أو ج) عند توفر تحديث، على:
 *  - أيقونة فتح الدرج في الشريط العلوي
 *  - أيقونة الإعدادات داخل الدرج
 * ويخفيه عند عدم وجود تحديث.
 */
public final class UpdateBadgeController {

    private final OneUiDrawerLayout mDrawerLayout;

    // الحالة المطبّقة حالياً، لتجنب إعادة ضبط أيقونة التنقل بلا داعٍ
    private boolean mBadgeShown = false;

    public UpdateBadgeController(@NonNull OneUiDrawerLayout drawerLayout) {
        mDrawerLayout = drawerLayout;
    }

    public void bind(@NonNull LifecycleOwner owner, @NonNull LiveData<UpdateInfo> availableUpdate) {
        availableUpdate.observe(owner, update -> applyBadge(update != null));
    }

    private void applyBadge(boolean show) {
        if (mBadgeShown == show) {
            return;
        }
        mBadgeShown = show;

        int badge = show ? OneUiDrawerLayout.N_BADGE : 0;
        mDrawerLayout.setButtonBadges(badge, badge);
    }
}
