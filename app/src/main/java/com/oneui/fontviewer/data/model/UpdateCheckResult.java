package com.oneui.fontviewer.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * نتيجة فحص واحد للتحديث. الإصدار المتوفر يكون غير null فقط عند UPDATE_AVAILABLE.
 */
public final class UpdateCheckResult {

    @NonNull
    private final UpdateCheckStatus mStatus;

    @Nullable
    private final AppVersion mAvailableVersion;

    private UpdateCheckResult(@NonNull UpdateCheckStatus status, @Nullable AppVersion availableVersion) {
        mStatus = status;
        mAvailableVersion = availableVersion;
    }

    public static UpdateCheckResult updateAvailable(@NonNull AppVersion availableVersion) {
        return new UpdateCheckResult(UpdateCheckStatus.UPDATE_AVAILABLE, availableVersion);
    }

    public static UpdateCheckResult upToDate() {
        return new UpdateCheckResult(UpdateCheckStatus.UP_TO_DATE, null);
    }

    public static UpdateCheckResult noConnection() {
        return new UpdateCheckResult(UpdateCheckStatus.NO_CONNECTION, null);
    }

    public static UpdateCheckResult checkFailed() {
        return new UpdateCheckResult(UpdateCheckStatus.CHECK_FAILED, null);
    }

    @NonNull
    public UpdateCheckStatus getStatus() {
        return mStatus;
    }

    @Nullable
    public AppVersion getAvailableVersion() {
        return mAvailableVersion;
    }
}
