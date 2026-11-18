package com.oneui.fontviewer.data.model;

/**
 * حالة فحص التحديث.
 * CHECKING تُستخدم لعرض مؤشر التحميل فقط، أما نتيجة أي مصدر فتكون إحدى القيم الأربع الأخرى.
 */
public enum UpdateCheckStatus {
    CHECKING,
    UPDATE_AVAILABLE,
    UP_TO_DATE,
    // لا يوجد اتصال بالإنترنت (يقابل النتيجة 3 في Samsung Video)
    NO_CONNECTION,
    // تعذّر الفحص لسبب آخر (خطأ من الخادم أو استجابة غير مفهومة)
    CHECK_FAILED
}
