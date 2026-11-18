package com.oneui.fontviewer.data.network;

import android.content.Context;
import android.util.Log;

import com.oneui.fontviewer.data.model.UpdateCheckResult;
import com.oneui.fontviewer.utils.UpdateConfig;
import com.oneui.fontviewer.utils.AppVersionUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * التحقق من آخر إصدار منشور على GitHub Releases (للتثبيت اليدوي من ملف APK).
 * دالة {@link #check(Context)} تحجب الخيط، لذلك تُستدعى من خيط خلفي فقط.
 */
public class GitHubUpdateApi {

    private static final String TAG = "GitHubUpdateApi";

    public static UpdateCheckResult check(Context context) {
        if (!UpdateConfig.isGitHubConfigured()) {
            Log.w(TAG, "GITHUB_OWNER / GITHUB_REPO غير مضبوطين في UpdateConfig، تم تخطي التحقق");
            return UpdateCheckResult.noUpdate();
        }

        // /releases/latest يتجاهل الإصدارات المعلّمة Pre-release، فنستخدم قائمة الإصدارات عند تفعيل التجربة
        String apiUrl = "https://api.github.com/repos/" + UpdateConfig.GITHUB_OWNER + "/"
                + UpdateConfig.GITHUB_REPO
                + (UpdateConfig.INCLUDE_PRERELEASES ? "/releases?per_page=10" : "/releases/latest");

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(apiUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(UpdateConfig.NETWORK_TIMEOUT_MS);
            connection.setReadTimeout(UpdateConfig.NETWORK_TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", context.getPackageName());

            int code = connection.getResponseCode();
            if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                // لا توجد إصدارات منشورة بعد
                return UpdateCheckResult.noUpdate();
            }
            if (code != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "GitHub API returned " + code);
                return UpdateCheckResult.error();
            }

            String body = readAll(connection);
            JSONObject release = UpdateConfig.INCLUDE_PRERELEASES
                    ? firstPublishedRelease(new JSONArray(body))
                    : new JSONObject(body);
            if (release == null) {
                return UpdateCheckResult.noUpdate();
            }

            String latest = release.optString("tag_name", "");
            if (latest.isEmpty()) {
                return UpdateCheckResult.noUpdate();
            }

            String installed = AppVersionUtils.getVersionName(context);
            if (AppVersionUtils.compareVersionNames(latest, installed) > 0) {
                return UpdateCheckResult.updateAvailable(latest, 0L);
            }
            return UpdateCheckResult.noUpdate();
        } catch (Exception e) {
            Log.w(TAG, "GitHub update check failed", e);
            return UpdateCheckResult.error();
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    // أول إصدار غير مسودة (الإصدارات تأتي من الأحدث إلى الأقدم)
    private static JSONObject firstPublishedRelease(JSONArray releases) {
        for (int i = 0; i < releases.length(); i++) {
            JSONObject r = releases.optJSONObject(i);
            if (r != null && !r.optBoolean("draft", false)) {
                return r;
            }
        }
        return null;
    }

    private static String readAll(HttpURLConnection connection) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }
}
