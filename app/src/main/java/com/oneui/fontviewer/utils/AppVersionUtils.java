package com.oneui.fontviewer.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AppVersionUtils {

    private static final Pattern DIGITS = Pattern.compile("\\d+");

    private AppVersionUtils() {}

    private static PackageInfo getPackageInfo(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        } catch (Exception e) {
            return null;
        }
    }

    public static String getVersionName(Context context) {
        PackageInfo info = getPackageInfo(context);
        return info != null && info.versionName != null ? info.versionName : "";
    }

    @SuppressWarnings("deprecation")
    public static long getVersionCode(Context context) {
        PackageInfo info = getPackageInfo(context);
        if (info == null) return 0L;
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? info.getLongVersionCode()
                : info.versionCode;
    }

    /**
     * يقارن اسمي إصدارين (مثل 1.2.0 و v1.10). يتجاهل البادئة v واللاحقة مثل -beta.
     * @return قيمة موجبة إن كان a أحدث من b، وسالبة إن كان أقدم، وصفر إن تساويا.
     */
    public static int compareVersionNames(String a, String b) {
        List<Long> pa = parseParts(a);
        List<Long> pb = parseParts(b);
        int n = Math.max(pa.size(), pb.size());
        for (int i = 0; i < n; i++) {
            long x = i < pa.size() ? pa.get(i) : 0L;
            long y = i < pb.size() ? pb.get(i) : 0L;
            if (x != y) return x > y ? 1 : -1;
        }
        return 0;
    }

    private static List<Long> parseParts(String version) {
        List<Long> parts = new ArrayList<>();
        if (version == null) return parts;
        String core = version.trim();
        int dash = core.indexOf('-');
        if (dash > 0) core = core.substring(0, dash);
        Matcher m = DIGITS.matcher(core);
        while (m.find()) {
            try {
                parts.add(Long.parseLong(m.group()));
            } catch (NumberFormatException e) {
                parts.add(0L);
            }
        }
        return parts;
    }
}
