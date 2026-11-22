package com.oneui.fontviewer.utils.variable;

/**
 * يحوّل مفتاح {@link InstanceLabel} إلى النص الذي يُعرض للمستخدم.
 * تنفّذه طبقة الواجهة (من strings.xml حسب لغة التطبيق) وتمرّره إلى VariableFontAxisExtractor.
 */
public interface InstanceLabelResolver {
    String nameOf(InstanceLabel label);
}
