package com.oneui.fontviewer.data.network.update;

import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.Xml;

import androidx.annotation.NonNull;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

import com.oneui.fontviewer.data.model.AppVersion;
import com.oneui.fontviewer.data.model.UpdateCheckResult;

/**
 * يفحص التحديث من خادم Galaxy Store بنفس الطلب الذي تستخدمه تطبيقات Samsung (stubUpdateCheck)،
 * وقد استُخرجت صيغته من تطبيق Samsung Video (الكلاسان w3.h و w3.b).
 *
 * الفروق عن Samsung Video (لأنها واجهات خاصة بتطبيقات النظام):
 * - كود الدولة cc يُرسل NONE بدل طلبه من خدمة Galaxy Apps.
 * - معرّف الجهاز extuk يُرسل فارغاً بدل طلبه من خدمة DeviceIdService.
 * - رمز المبيعات csc يُقرأ بالانعكاس من خصائص النظام، ويُرسل NONE إن تعذّرت قراءته.
 */
public class GalaxyStoreUpdateSource implements UpdateSource {

    private static final String TAG = "GalaxyStoreUpdateSource";
    private static final String CHECK_URL = "https://vas.samsungapps.com/stub/stubUpdateCheck.as";
    private static final int TIMEOUT_MS = 15000;

    // نتائج الخادم كما في Samsung Video: 2 = يوجد تحديث، و0 أو 1 = لا يوجد
    private static final int RESULT_CAN_UPDATE = 2;
    private static final int RESULT_NO_UPDATE_A = 0;
    private static final int RESULT_NO_UPDATE_B = 1;

    @NonNull
    @Override
    public UpdateCheckResult checkForUpdate(@NonNull Context context, @NonNull AppVersion installedVersion) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(buildUrl(context, installedVersion)).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setDoInput(true);

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "Galaxy Store returned " + responseCode);
                return UpdateCheckResult.checkFailed();
            }

            return parse(connection.getInputStream());

        } catch (IOException e) {
            Log.w(TAG, "Network error while checking Galaxy Store: " + e.getMessage());
            return UpdateCheckResult.noConnection();
        } catch (XmlPullParserException | RuntimeException e) {
            Log.e(TAG, "Unexpected Galaxy Store response", e);
            return UpdateCheckResult.checkFailed();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static UpdateCheckResult parse(InputStream stream) throws IOException, XmlPullParserException {
        int resultCode = -1;
        String versionName = "";
        long versionCode = 0L;

        try (InputStream input = stream) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(input, null);

            int event = parser.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    String name = parser.getName();
                    if ("resultCode".equalsIgnoreCase(name)) {
                        resultCode = parseInt(parser.nextText(), -1);
                    } else if ("versionName".equalsIgnoreCase(name)) {
                        versionName = parser.nextText().trim();
                    } else if ("versionCode".equalsIgnoreCase(name)) {
                        versionCode = parseInt(parser.nextText(), 0);
                    }
                }
                event = parser.next();
            }
        }

        if (resultCode == RESULT_CAN_UPDATE) {
            return UpdateCheckResult.updateAvailable(new AppVersion(versionName, versionCode));
        }
        if (resultCode == RESULT_NO_UPDATE_A || resultCode == RESULT_NO_UPDATE_B) {
            return UpdateCheckResult.upToDate();
        }
        Log.w(TAG, "Unknown resultCode: " + resultCode);
        return UpdateCheckResult.checkFailed();
    }

    private static int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String buildUrl(Context context, AppVersion installedVersion) {
        String packageName = context.getPackageName();
        String[] operator = readSimOperator(context);

        return CHECK_URL
                + "?appId=" + encode(packageName)
                + "&callerId=" + encode(packageName)
                + "&versionCode=" + installedVersion.getVersionCode()
                + "&deviceId=" + encode(Build.MODEL.replaceFirst("SAMSUNG-", ""))
                + "&mcc=" + operator[0]
                + "&mnc=" + operator[1]
                + "&cc=NONE"
                + "&csc=" + encode(readSalesCode())
                + "&sdkVer=" + Build.VERSION.SDK_INT
                + "&systemId=" + (System.currentTimeMillis() - SystemClock.elapsedRealtime())
                + "&abiType=" + (Build.SUPPORTED_64_BIT_ABIS.length > 0 ? "64" : "32")
                + "&extuk="
                + "&updateType=self"
                + "&pd=0";
    }

    /** {MCC, MNC} من شريحة الاتصال، وفارغان إن لم توجد شريحة. */
    private static String[] readSimOperator(Context context) {
        TelephonyManager manager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        String operator = manager == null ? null : manager.getSimOperator();
        if (operator == null || operator.length() <= 3) {
            return new String[]{"", ""};
        }
        return new String[]{operator.substring(0, 3), operator.substring(3)};
    }

    /** رمز المبيعات (CSC) عبر خصائص النظام، يستخدمه Samsung Video عبر SemSystemProperties غير المتاحة لتطبيقنا. */
    private static String readSalesCode() {
        try {
            Class<?> properties = Class.forName("android.os.SystemProperties");
            Object value = properties.getMethod("get", String.class, String.class)
                    .invoke(null, "ro.csc.sales_code", "");
            if (value instanceof String && !((String) value).isEmpty()) {
                return (String) value;
            }
        } catch (Exception e) {
            Log.d(TAG, "Sales code is not readable: " + e.getMessage());
        }
        return "NONE";
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }
}
