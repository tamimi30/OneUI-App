package com.oneui.fontviewer.data.network;

import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.Xml;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.oneui.fontviewer.BuildConfig;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * يسأل خادم Galaxy Store عن وجود تحديث للتطبيق، بنفس الطلب الذي يستخدمه Samsung Video
 * (stubUpdateCheck.as) والاستجابة XML فيها resultCode:
 *  0 أو 1 = لا يوجد تحديث، 2 = يتوفر تحديث.
 *
 * ملاحظة: هذه الواجهة تعمل للتطبيقات المنشورة في Galaxy Store فقط، وسامسونج لا توثقها رسمياً.
 */
public class GalaxyStoreUpdateApi {

    private static final String TAG = "GalaxyStoreUpdateApi";
    private static final String BASE_URL = "https://vas.samsungapps.com/stub/stubUpdateCheck.as";
    private static final int TIMEOUT_MS = 15000;

    public static final int RESULT_NO_UPDATE = 0;
    public static final int RESULT_NO_UPDATE_ALT = 1;
    public static final int RESULT_CAN_UPDATE = 2;

    public static final class Result {
        public final int resultCode;
        @Nullable
        public final String versionName;
        public final long versionCode;

        Result(int resultCode, @Nullable String versionName, long versionCode) {
            this.resultCode = resultCode;
            this.versionName = versionName;
            this.versionCode = versionCode;
        }
    }

    @NonNull
    public Result check(@NonNull Context context) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(buildUrl(context)).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setDoInput(true);

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "Galaxy Store returned HTTP " + responseCode);
                throw new IOException("HTTP " + responseCode);
            }
            return parse(connection.getInputStream());
        } catch (XmlPullParserException e) {
            throw new IOException("Malformed Galaxy Store response", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    @NonNull
    private static Result parse(@NonNull InputStream inputStream)
            throws IOException, XmlPullParserException {
        int resultCode = -1;
        String versionName = null;
        long versionCode = 0;

        try (InputStream stream = inputStream) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(stream, null);
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    String name = parser.getName();
                    if ("resultCode".equalsIgnoreCase(name)) {
                        resultCode = parseInt(parser.nextText(), -1);
                    } else if ("versionName".equalsIgnoreCase(name)) {
                        versionName = parser.nextText();
                    } else if ("versionCode".equalsIgnoreCase(name)) {
                        versionCode = parseInt(parser.nextText(), 0);
                    }
                }
                eventType = parser.next();
            }
        }
        return new Result(resultCode, versionName, versionCode);
    }

    private static int parseInt(@Nullable String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @NonNull
    private static String buildUrl(@NonNull Context context) {
        String packageName = context.getPackageName();
        String[] operator = getSimOperator(context);

        StringBuilder url = new StringBuilder(BASE_URL).append('?');
        append(url, "appId", packageName, true);
        append(url, "callerId", packageName, false);
        append(url, "versionCode", String.valueOf(BuildConfig.VERSION_CODE), false);
        append(url, "deviceId", Build.MODEL.replaceFirst("SAMSUNG-", ""), false);
        append(url, "mcc", operator[0], false);
        append(url, "mnc", operator[1], false);
        append(url, "cc", "NONE", false);
        append(url, "csc", "NONE", false);
        append(url, "sdkVer", String.valueOf(Build.VERSION.SDK_INT), false);
        append(url, "systemId",
                String.valueOf(System.currentTimeMillis() - SystemClock.elapsedRealtime()), false);
        append(url, "abiType", getAbiType(), false);
        append(url, "extuk", "", false);
        append(url, "updateType", "self", false);
        append(url, "pd", "0", false);
        return url.toString();
    }

    private static void append(@NonNull StringBuilder url, @NonNull String key,
                               @NonNull String value, boolean first) {
        if (!first) {
            url.append('&');
        }
        try {
            url.append(key).append('=').append(URLEncoder.encode(value, StandardCharsets.UTF_8.name()));
        } catch (java.io.UnsupportedEncodingException e) {
            // UTF-8 مدعوم دائماً على أندرويد
            url.append(key).append('=');
        }
    }

    @NonNull
    private static String[] getSimOperator(@NonNull Context context) {
        TelephonyManager telephonyManager =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        String simOperator = telephonyManager != null ? telephonyManager.getSimOperator() : null;
        if (simOperator == null || simOperator.length() <= 3) {
            return new String[]{"", ""};
        }
        return new String[]{simOperator.substring(0, 3), simOperator.substring(3)};
    }

    @NonNull
    private static String getAbiType() {
        if (Build.SUPPORTED_64_BIT_ABIS.length > 0) {
            return "64";
        }
        return Build.SUPPORTED_32_BIT_ABIS.length > 0 ? "32" : "ex";
    }
}
