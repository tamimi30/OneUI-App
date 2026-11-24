package com.oneui.fontviewer.ui.core.utils;

import android.content.Context;

import com.oneui.fontviewer.R;
import com.oneui.fontviewer.utils.metadata.FontWeightWidthExtractor;

/**
 * يحوّل تسمية الوزن/العرض المخزّنة (مثل 400|5|1) إلى نص يُعرض للمستخدم حسب لغة التطبيق.
 * استخراج الصيغة من ملف الخط يتم في FontWeightWidthExtractor، أما النصوص المعروضة فمكانها هنا مع بقية الواجهة.
 */
public final class FontLabelFormatter {

    private static final String VF_PREFIX = "VF";

    private FontLabelFormatter() {
    }

    // تحويل الصيغة المخزّنة لنص يُعرض حسب لغة التطبيق. أي نص لا يطابق الصيغة
    // (مثل النصوص القديمة المخزّنة سابقاً) يُعاد كما هو
    public static String formatLabel(Context context, String raw) {
        if (raw == null || raw.isEmpty()) return raw;

        FontWeightWidthExtractor.ParsedLabel parsed = FontWeightWidthExtractor.parse(raw);
        if (parsed == null) return raw;

        String label = buildLabel(getWeightName(context, parsed.weightClass), getWidthName(context, parsed.widthClass));
        if (label == null) return null;

        return parsed.variable ? VF_PREFIX + " • " + label : label;
    }

    private static String buildLabel(String weight, String width) {
        boolean hasWeight = weight != null && !weight.isEmpty();
        boolean hasWidth  = width  != null && !width.isEmpty();
        if (!hasWeight && !hasWidth) return null;
        if (!hasWeight) return width;
        if (!hasWidth)  return weight;
        return weight + " • " + width;
    }


    public static String getWeightName(Context context, int w) {
        if (w <= 0) return null;

        switch (w) {
            case 100: return context.getString(R.string.font_info_weight_thin);
            case 200: return context.getString(R.string.font_info_weight_extra_light);
            case 300: return context.getString(R.string.font_info_weight_light);
            case 400: return context.getString(R.string.font_info_weight_regular);
            case 500: return context.getString(R.string.font_info_weight_medium);
            case 600: return context.getString(R.string.font_info_weight_semi_bold);
            case 700: return context.getString(R.string.font_info_weight_bold);
            case 800: return context.getString(R.string.font_info_weight_extra_bold);
            case 900: return context.getString(R.string.font_info_weight_black);
            default: return context.getString(R.string.font_info_weight_other, w);
        }
    }

    public static String getWidthName(Context context, int d) {
        switch (d) {
            case 1: return context.getString(R.string.font_info_width_ultra_condensed);
            case 2: return context.getString(R.string.font_info_width_extra_condensed);
            case 3: return context.getString(R.string.font_info_width_condensed);
            case 4: return context.getString(R.string.font_info_width_semi_condensed);
            case 5: return null; 
            case 6: return context.getString(R.string.font_info_width_semi_expanded);
            case 7: return context.getString(R.string.font_info_width_expanded);
            case 8: return context.getString(R.string.font_info_width_extra_expanded);
            case 9: return context.getString(R.string.font_info_width_ultra_expanded);
            default: return null;
        }
    }
}
