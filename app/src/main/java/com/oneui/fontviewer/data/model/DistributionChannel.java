package com.oneui.fontviewer.data.model;

/**
 * المصدر الذي نُزّل منه التطبيق على جهاز المستخدم.
 * يحدد من أين يُفحص التحديث، وإلى أي صفحة يُوجَّه المستخدم عند الضغط على "تحديث".
 */
public enum DistributionChannel {
    GITHUB,
    GOOGLE_PLAY,
    GALAXY_STORE
}
