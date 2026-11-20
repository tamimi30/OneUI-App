package com.oneui.fontviewer.data.update.source;

import androidx.annotation.NonNull;

import com.oneui.fontviewer.BuildConfig;
import com.oneui.fontviewer.data.update.api.GitHubReleaseApi;
import com.oneui.fontviewer.data.update.model.UpdateInfo;
import com.oneui.fontviewer.utils.update.VersionComparator;

import java.io.IOException;
import java.util.Optional;

/** مصدر التحديث للنسخة المثبتة يدوياً من ملف APK: إصدارات GitHub. */
public class GitHubUpdateSource implements UpdateSource {

    private final GitHubReleaseApi releaseApi;

    public GitHubUpdateSource(boolean includePrereleases) {
        this.releaseApi = new GitHubReleaseApi(includePrereleases);
    }

    @NonNull
    @Override
    public Optional<UpdateInfo> checkForUpdate() throws IOException {
        UpdateInfo latest = releaseApi.fetchLatestRelease();
        if (latest != null
                && VersionComparator.isNewer(latest.getVersionName(), BuildConfig.VERSION_NAME)) {
            return Optional.of(latest);
        }
        return Optional.empty();
    }
}
