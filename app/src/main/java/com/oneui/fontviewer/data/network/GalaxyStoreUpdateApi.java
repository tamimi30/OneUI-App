package com.oneui.fontviewer.data.network;

import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.Xml;

import com.oneui.fontviewer.data.model.UpdateCheckResult;
import com.oneui.fontviewer.utils.UpdateConfig;
import com.oneui.fontviewer.utils.AppVersionUtils;

import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * التحقق من التحديث عبر خادم Galaxy Store نفسه، بنفس الطلب الذي يستخدمه تطبيق Samsung Video
 * (stubUpdateCheck). الرد XML فيه resultCode: القيمة 2 تعني "يتوفر تحديث"، و0 أو 1 تعني "لا تحديث".
 * دالة {@link #check(Context)} تحجب الخيط، لذلك تُستدعى من خيط خلفي فقط.
 *
 * ملاحظة: هذه الواجهة غير موثقة رسمياً، ولا تعطي نتيجة إلا إذا كان التطبيق منشوراً فعلاً في Galaxy Store.
 */
public class GalaxyStoreUpdateApi {

    private static final String TAG = "GalaxyStoreUpdateApi";
    private static final String BASE_URL = "https://vas.samsungapps.com/stub/stubUpdateCheck.as";

    private static final int RESULT_CAN_UPDATE = 2;

    public static UpdateCheckResult check(Context context) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(buildUrl(context)).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(UpdateConfig.NETWORK_TIMEOUT_MS);
            connection.setReadTimeout(UpdateConfig.NETWORK_TIMEOUT_MS);
            connection.setDoInput(true);

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return UpdateCheckResult.error();
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), "UTF-8"))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
            }
            return parse(sb.toString());
        } catch (Exception e) {
            Log.w(TAG, "Galaxy Store update check failed", e);
            return UpdateCheckResult.error();
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static UpdateCheckResult parse(String xml) throws Exception {
        if (xml == null || xml.trim().isEmpty()) {
            return UpdateCheckResult.error();
        }

        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(new ByteArrayInputStream(xml.getBytes("UTF-8")), null);

        int resultCode = 1;
        String versionName = "";
        int event = parser.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                String name = parser.getName();
                if ("resultCode".equalsIgnoreCase(name)) {
                    try {
                        resultCode = Integer.parseInt(parser.nextText().trim());
                    } catch (NumberFormatException e) {
                        resultCode = 1;
                    }
                } else if ("versionName".equalsIgnoreCase(name)) {
                    versionName = parser.nextText();
                }
            }
            event = parser.next();
        }

        return resultCode == RESULT_CAN_UPDATE
                ? UpdateCheckResult.updateAvailable(versionName, 0L)
                : UpdateCheckResult.noUpdate();
    }

    private static String buildUrl(Context context) throws Exception {
        String pkg = context.getPackageName();
        String simOperator = getSimOperator(context);
        String mcc = simOperator.length() > 3 ? simOperator.substring(0, 3) : "";
        String mnc = simOperator.length() > 3 ? simOperator.substring(3) : "";

        return BASE_URL
                + "?appId=" + pkg
                + "&callerId=" + pkg
                + "&versionCode=" + AppVersionUtils.getVersionCode(context)
                + "&deviceId=" + URLEncoder.encode(Build.MODEL.replaceFirst("SAMSUNG-", ""), "UTF-8")
                + "&mcc=" + mcc
                + "&mnc=" + mnc
                + "&csc=" + getSalesCode()
                + "&sdkVer=" + Build.VERSION.SDK_INT
                + "&systemId=" + (System.currentTimeMillis() - SystemClock.elapsedRealtime())
                + "&abiType=" + (Build.SUPPORTED_64_BIT_ABIS.length > 0 ? "64" : "32")
                + "&updateType=self&pd=0";
    }

    private static String getSimOperator(Context context) {
        try {
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            String op = tm != null ? tm.getSimOperator() : null;
            return op == null ? "" : op;
        } catch (Exception e) {
            return "";
        }
    }

    // رمز المبيعات (CSC). لا توجد له واجهة عامة، فنقرؤه من خصائص النظام وإن فشل نرسله فارغاً.
    private static String getSalesCode() {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Object value = sp.getMethod("get", String.class, String.class)
                    .invoke(null, "ro.csc.sales_code", "");
            return value == null ? "" : URLEncoder.encode(value.toString(), "UTF-8");
        } catch (Throwable t) {
            return "";
        }
    }
}
