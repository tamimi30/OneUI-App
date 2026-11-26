package com.oneui.fontviewer.ui.core.utils;

import android.content.Context;

import androidx.annotation.NonNull;

import com.oneui.fontviewer.R;
import com.oneui.fontviewer.utils.variable.InstanceLabel;
import com.oneui.fontviewer.utils.variable.InstanceLabelResolver;

/**
 * يقرأ أسماء قيم محاور الخط المتغير من strings.xml حسب لغة الـ Context الممرَّر
 * (يجب أن يكون Context الشاشة لا Context التطبيق، حتى تُطبَّق لغة التطبيق المختارة).
 */
public final class AxisLabelProvider implements InstanceLabelResolver {

    private final Context context;

    private AxisLabelProvider(Context context) {
        this.context = context;
    }

    public static InstanceLabelResolver of(@NonNull Context context) {
        return new AxisLabelProvider(context);
    }

    /**
     * وسوم لغة الـ Context الحالية، تُستخدم في مفتاح التجهيز المسبق لأن أسماء قيم المحاور تتغير بتغير اللغة.
     */
    public static String localeTagsOf(@NonNull Context context) {
        return context.getResources().getConfiguration().getLocales().toLanguageTags();
    }

    @Override
    public String nameOf(InstanceLabel label) {
        switch (label) {
            case WEIGHT_THIN:        return context.getString(R.string.font_info_weight_thin);
            case WEIGHT_EXTRA_LIGHT: return context.getString(R.string.font_info_weight_extra_light);
            case WEIGHT_LIGHT:       return context.getString(R.string.font_info_weight_light);
            case WEIGHT_REGULAR:     return context.getString(R.string.font_info_weight_regular);
            case WEIGHT_MEDIUM:      return context.getString(R.string.font_info_weight_medium);
            case WEIGHT_SEMI_BOLD:   return context.getString(R.string.font_info_weight_semi_bold);
            case WEIGHT_BOLD:        return context.getString(R.string.font_info_weight_bold);
            case WEIGHT_EXTRA_BOLD:  return context.getString(R.string.font_info_weight_extra_bold);
            case WEIGHT_BLACK:       return context.getString(R.string.font_info_weight_black);

            case WIDTH_CONDENSED:    return context.getString(R.string.font_info_width_condensed);
            case WIDTH_NORMAL:       return context.getString(R.string.font_info_width_normal);
            case WIDTH_EXPANDED:     return context.getString(R.string.font_info_width_expanded);

            case ITALIC_UPRIGHT:     return context.getString(R.string.font_info_italic_upright);
            case ITALIC_ITALIC:      return context.getString(R.string.font_info_axis_italic);

            case GRADE_LOW:          return context.getString(R.string.font_info_grade_low);
            case GRADE_HIGH:         return context.getString(R.string.font_info_grade_high);

            case ROUNDNESS_SHARP:    return context.getString(R.string.font_info_roundness_sharp);
            case ROUNDNESS_ROUNDED:  return context.getString(R.string.font_info_roundness_rounded);

            case MONOSPACE_PROPORTIONAL:  return context.getString(R.string.font_info_monospace_proportional);
            case MONOSPACE_MONOSPACED:    return context.getString(R.string.font_info_monospace_monospaced);

            default:                 return label.name();
        }
    }
}
