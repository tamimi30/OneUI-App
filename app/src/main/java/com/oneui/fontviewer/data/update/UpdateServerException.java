package com.oneui.fontviewer.data.update;

import java.io.IOException;

/**
 * وصلنا إلى مصدر التحديث، أي أن الاتصال بالإنترنت سليم، لكنه لم يعطِ جواباً صالحاً:
 * رفض الطلب (مثل 403 عند تجاوز حد الطلبات، أو 404، أو خطأ في الخادم) أو أرسل رداً غير مفهوم.
 * يميّزه UpdateRepository عن فشل الاتصال نفسه حتى لا يُخبَر المستخدم أن شبكته غير مستقرة وهي سليمة.
 */
public class UpdateServerException extends IOException {

    public UpdateServerException(String message) {
        super(message);
    }

    public UpdateServerException(String message, Throwable cause) {
        super(message, cause);
    }
}
