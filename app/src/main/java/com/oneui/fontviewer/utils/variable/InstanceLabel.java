package com.oneui.fontviewer.utils.variable;

/**
 * مفاتيح أسماء القيم المسمّاة لمحاور الخط المتغير (Thin، Condensed، Italic ...).
 * الاسم المعروض نفسه (حسب لغة التطبيق) تحدده الواجهة عبر {@link InstanceLabelResolver}،
 * فتبقى أدوات قراءة الخط مستقلة عن موارد النصوص (R) وعن Context.
 */
public enum InstanceLabel {
    WEIGHT_THIN,
    WEIGHT_EXTRA_LIGHT,
    WEIGHT_LIGHT,
    WEIGHT_REGULAR,
    WEIGHT_MEDIUM,
    WEIGHT_SEMI_BOLD,
    WEIGHT_BOLD,
    WEIGHT_EXTRA_BOLD,
    WEIGHT_BLACK,

    WIDTH_CONDENSED,
    WIDTH_NORMAL,
    WIDTH_EXPANDED,

    ITALIC_UPRIGHT,
    ITALIC_ITALIC,

    GRADE_LOW,
    GRADE_HIGH,

    ROUNDNESS_SHARP,
    ROUNDNESS_ROUNDED,

    MONOSPACE_PROPORTIONAL,
    MONOSPACE_MONOSPACED
}
