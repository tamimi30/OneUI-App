package com.oneui.fontviewer.utils;

import androidx.annotation.NonNull;

/**
 * مقارنة أسماء الإصدارات مثل 1.0 و v1.2.3 و 2.0.1-beta.
 * تقارن الأرقام رقماً رقماً، والرقم الناقص يُعتبر صفراً (1.0 تساوي 1.0.0).
 */
public final class VersionComparator {

    private VersionComparator() {
    }

    /** سالب إن كان a أقدم من b، وصفر إن تساويا، وموجب إن كان a أحدث. */
    public static int compare(@NonNull String a, @NonNull String b) {
        int[] partsA = parse(a);
        int[] partsB = parse(b);

        int length = Math.max(partsA.length, partsB.length);
        for (int i = 0; i < length; i++) {
            int partA = i < partsA.length ? partsA[i] : 0;
            int partB = i < partsB.length ? partsB[i] : 0;
            if (partA != partB) {
                return partA < partB ? -1 : 1;
            }
        }
        return 0;
    }

    private static int[] parse(String version) {
        String core = version.trim();

        // حذف أي بادئة غير رقمية (v1.2.0 أو release-1.2.0)
        int start = 0;
        while (start < core.length() && !Character.isDigit(core.charAt(start))) {
            start++;
        }
        core = core.substring(start);

        // إهمال ما بعد - أو + (مثل 1.2.0-beta أو 1.2.0+build5) فهي ليست جزءاً من رقم الإصدار
        int suffix = indexOfAny(core, '-', '+');
        if (suffix >= 0) {
            core = core.substring(0, suffix);
        }

        if (core.isEmpty()) {
            return new int[0];
        }

        String[] segments = core.split("\\.");
        int[] parts = new int[segments.length];
        for (int i = 0; i < segments.length; i++) {
            parts[i] = leadingNumber(segments[i]);
        }
        return parts;
    }

    private static int indexOfAny(String text, char first, char second) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == first || c == second) {
                return i;
            }
        }
        return -1;
    }

    private static int leadingNumber(String segment) {
        int end = 0;
        while (end < segment.length() && Character.isDigit(segment.charAt(end))) {
            end++;
        }
        if (end == 0) {
            return 0;
        }
        try {
            return Integer.parseInt(segment.substring(0, end));
        } catch (NumberFormatException e) {
            // رقم أكبر من حدود int
            return Integer.MAX_VALUE;
        }
    }
}
