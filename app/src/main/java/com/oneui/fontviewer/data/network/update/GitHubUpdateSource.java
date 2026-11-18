package com.oneui.fontviewer.data.network.update;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import com.oneui.fontviewer.config.AppLinks;
import com.oneui.fontviewer.data.model.AppVersion;
import com.oneui.fontviewer.data.model.UpdateCheckResult;

/**
 * يفحص آخر Release منشور في مستودع GitHub ويقارن اسم الوسم (tag) مع إصدار التطبيق المثبّت.
 *
 * ملاحظات:
 * - الفحص يعتمد على اسم الوسم فقط (مثل v1.0.1)، ولا يهم نوع ملف APK المرفق.
 * - نقطة /releases/latest تتجاهل المسودات (draft) والإصدارات التجريبية (pre-release).
 */
public class GitHubUpdateSource implements UpdateSource {

    private static final String TAG = "GitHubUpdateSource";
    private static final int TIMEOUT_MS = 10000;

    @NonNull
    @Override
    public UpdateCheckResult checkForUpdate(@NonNull Context context, @NonNull AppVersion installedVersion) {
        if (!AppLinks.isGitHubConfigured()) {
            Log.w(TAG, "GitHub repository is not configured in AppLinks, skipping the check");
            return UpdateCheckResult.checkFailed();
        }

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(AppLinks.GITHUB_LATEST_RELEASE_API_URL).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
            // GitHub يرفض الطلبات التي ليس فيها User-Agent
            connection.setRequestProperty("User-Agent",
                    context.getPackageName() + "/" + installedVersion.getVersionName());

            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                // لا يوجد أي Release منشور بعد، أي لا يوجد تحديث
                return UpdateCheckResult.upToDate();
            }
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "GitHub API returned " + responseCode);
                return UpdateCheckResult.checkFailed();
            }

            JSONObject release = new JSONObject(readAll(connection.getInputStream()));
            if (release.optBoolean("draft") || release.optBoolean("prerelease")) {
                return UpdateCheckResult.upToDate();
            }

            // v1.2.0 أو release-1.2.0 → 1.2.0
            String versionName = release.optString("tag_name", "").replaceFirst("^[^0-9]*", "");
            if (versionName.isEmpty()) {
                Log.w(TAG, "Latest release tag has no version number");
                return UpdateCheckResult.checkFailed();
            }

            AppVersion latestVersion = new AppVersion(versionName, 0L);
            return latestVersion.isNewerThan(installedVersion)
                    ? UpdateCheckResult.updateAvailable(latestVersion)
                    : UpdateCheckResult.upToDate();

        } catch (IOException e) {
            Log.w(TAG, "Network error while checking GitHub: " + e.getMessage());
            return UpdateCheckResult.noConnection();
        } catch (JSONException | RuntimeException e) {
            Log.e(TAG, "Unexpected GitHub response", e);
            return UpdateCheckResult.checkFailed();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readAll(InputStream stream) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }
}
