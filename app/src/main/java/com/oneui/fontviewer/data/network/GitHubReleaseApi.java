package com.oneui.fontviewer.data.network;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.oneui.fontviewer.data.model.UpdateInfo;
import com.oneui.fontviewer.utils.AppLinks;
import com.oneui.fontviewer.utils.VersionComparator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * يجلب أحدث إصدار منشور للتطبيق من GitHub Releases.
 *
 * نستخدم قائمة الإصدارات (/releases) وليس /releases/latest، لأن /releases/latest يتجاهل
 * إصدارات pre-release تماماً، فلا يمكن تجربة التحديث بنسخة تجريبية.
 */
public class GitHubReleaseApi {

    private static final String TAG = "GitHubReleaseApi";
    private static final int TIMEOUT_MS = 15000;

    private final boolean includePrereleases;

    public GitHubReleaseApi(boolean includePrereleases) {
        this.includePrereleases = includePrereleases;
    }

    /**
     * @return أحدث إصدار منشور، أو null إذا لم يُنشر أي إصدار بعد.
     * @throws IOException عند فشل الاتصال أو رجوع استجابة غير ناجحة أو غير مفهومة.
     */
    @Nullable
    public UpdateInfo fetchLatestRelease() throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(AppLinks.GITHUB_RELEASES_API_URL).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            // GitHub API يرفض الطلبات التي لا تحمل User-Agent
            connection.setRequestProperty("User-Agent", AppLinks.GITHUB_REPOSITORY);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "GitHub API returned HTTP " + responseCode);
                throw new IOException("HTTP " + responseCode);
            }

            return parseLatestRelease(readFully(connection.getInputStream()));
        } catch (JSONException e) {
            throw new IOException("Malformed GitHub response", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @Nullable
    private UpdateInfo parseLatestRelease(@NonNull String body) throws JSONException {
        JSONArray releases = new JSONArray(body);
        UpdateInfo latest = null;

        for (int i = 0; i < releases.length(); i++) {
            JSONObject release = releases.optJSONObject(i);
            if (release == null) {
                continue;
            }
            if (release.optBoolean("draft", false)) {
                continue;
            }
            if (release.optBoolean("prerelease", false) && !includePrereleases) {
                continue;
            }

            String tagName = release.optString("tag_name", "");
            if (!VersionComparator.isParsable(tagName)) {
                continue;
            }

            String releaseUrl = release.optString("html_url", "");
            if (releaseUrl.isEmpty()) {
                releaseUrl = AppLinks.GITHUB_RELEASES_URL;
            }

            // القائمة مرتبة بتاريخ الإنشاء وليس برقم الإصدار، لذلك نختار الأعلى رقماً صراحةً
            if (latest == null || VersionComparator.isNewer(tagName, latest.getVersionName())) {
                latest = new UpdateInfo(tagName, releaseUrl);
            }
        }
        return latest;
    }

    @NonNull
    private static String readFully(@NonNull InputStream inputStream) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }
}
