package com.oneui.fontviewer.data.network.update;

import android.content.Context;

import androidx.annotation.NonNull;

import com.oneui.fontviewer.data.model.AppVersion;
import com.oneui.fontviewer.data.model.UpdateCheckResult;

/**
 * مصدر واحد للتحقق من وجود إصدار أحدث (GitHub أو Google Play أو Galaxy Store).
 */
public interface UpdateSource {

    /**
     * يحجب الخيط أثناء الاتصال بالشبكة، لذلك يجب استدعاؤه من خيط خلفي فقط.
     * لا يرمي استثناءات: كل فشل يُعاد كنتيجة NO_CONNECTION أو CHECK_FAILED.
     */
    @NonNull
    UpdateCheckResult checkForUpdate(@NonNull Context context, @NonNull AppVersion installedVersion);
}
