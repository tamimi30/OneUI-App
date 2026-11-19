package com.oneui.fontviewer.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * مقارنة أرقام الإصدارات النصية مثل "1.0" و "v1.2.3" و "1.4-beta".
 * تُقارن الأجزاء الرقمية فقط، وتتجاهل البادئة v وأي لاحقة بعد الأرقام.
 */
public final class VersionComparator {

    private static final Pattern NUMERIC_VERSION = Pattern.compile("^[vV]?(\\d+(?:\\.\\d+)*)");

    private VersionComparator() {
    }

    /** يعيد true إذا كان الإصدار candidate أحدث من الإصدار current. */
    public static boolean isNewer(@Nullable String candidate, @Nullable String current) {
        return compare(candidate, current) > 0;
    }

    /** يعيد true إذا كان النص يحتوي على رقم إصدار يمكن مقارنته. */
    public static boolean isParsable(@Nullable String version) {
        return parse(version).length > 0;
    }

    public static int compare(@Nullable String a, @Nullable String b) {
        int[] partsA = parse(a);
        int[] partsB = parse(b);
        int length = Math.max(partsA.length, partsB.length);
        for (int i = 0; i < length; i++) {
            int valueA = i < partsA.length ? partsA[i] : 0;
            int valueB = i < partsB.length ? partsB[i] : 0;
            if (valueA != valueB) {
                return Integer.compare(valueA, valueB);
            }
        }
        return 0;
    }

    @NonNull
    private static int[] parse(@Nullable String version) {
        if (version == null) {
            return new int[0];
        }
        Matcher matcher = NUMERIC_VERSION.matcher(version.trim());
        if (!matcher.find()) {
            return new int[0];
        }
        String[] segments = matcher.group(1).split("\\.");
        int[] parts = new int[segments.length];
        for (int i = 0; i < segments.length; i++) {
            try {
                parts[i] = Integer.parseInt(segments[i]);
            } catch (NumberFormatException e) {
                // رقم أكبر من حدود int: نعتبره أكبر قيمة ممكنة
                parts[i] = Integer.MAX_VALUE;
            }
        }
        return parts;
    }
}
