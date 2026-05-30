package com.example.oneuiapp.activity;

/**
 * AppScreen — قائمة ثابتة (Enum) بأسماء شاشات التطبيق بدلاً من الأرقام السحرية.
 *
 * الهدف: استبدال الأرقام المُشفَّرة (0، 1، 2..) بأنواع آمنة (Type-Safe) تضمن:
 *   - استقلالية كل شاشة عن ترتيبها في قائمة الفراغمنتات.
 *   - إمكانية حذف أي شاشة أو إضافة أخرى دون تأثير على بقية الشاشات.
 *   - قابلية قراءة الكود: AppScreen.TRASH أوضح بكثير من الرقم المُشفَّر 5.
 *
 * يُستخدم في:
 *   - MainActivity.updateFontsCount(AppScreen, int)
 *   - MainActivity.getCurrentScreen() لتحديد الشاشة الحالية بالنوع لا بالرقم
 *   - BatchOperationState.getSourceScreen() و setSourceScreen(AppScreen)
 *   - جميع الفراغمنتات التي تُعلم MainActivity بتغيُّر عدادات الخطوط.
 *
 * ★ الفائدة الجوهرية:
 *   قبل هذا التعديل: حذف HomeFragment يُغيِّر فهارس جميع الشاشات الأخرى،
 *   فيتحدث TrashFragment (الذي أصبح رقم 4) بيانات خاطئة في شاشة LocalFonts (رقم 2).
 *   بعد هذا التعديل: كل شاشة تُعرِّف نفسها باسمها (TRASH, LOCAL_FONTS)،
 *   فيمكن حذف HomeFragment أو إضافة عشر شاشات دون أن يتأثر أي عداد أو منطق.
 */
public enum AppScreen {
    HOME,
    FONT_VIEWER,
    LOCAL_FONTS,
    SYSTEM_FONTS,
    FAVORITES,
    TRASH
}
