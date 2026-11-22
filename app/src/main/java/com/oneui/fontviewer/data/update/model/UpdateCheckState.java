package com.oneui.fontviewer.data.update.model;

/** حالة التحقق من التحديث كما تعرضها صفحة "حول التطبيق". */
public enum UpdateCheckState {
    /** لم يبدأ أي تحقق بعد. */
    IDLE,
    /** التحقق جارٍ الآن (يظهر مؤشر التحميل). */
    CHECKING,
    /** يتوفر إصدار أحدث. */
    UPDATE_AVAILABLE,
    /** النسخة المثبتة هي الأحدث. */
    UP_TO_DATE,
    /** تعذر التحقق (لا يوجد اتصال أو فشل الطلب). */
    NO_CONNECTION
}
