package com.oneui.fontviewer.data.model;

/** المصدر الذي ثُبّتت منه النسخة الحالية من التطبيق، وهو يحدد وجهة زر التحديث. */
public enum InstallSource {
    GOOGLE_PLAY,
    GALAXY_STORE,
    /** أي تثبيت يدوي (ملف APK من GitHub أو غيره). */
    GITHUB
}
