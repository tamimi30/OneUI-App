# 1. إيقاف التشفير (لتبقى الأسماء واضحة في الكراش)
-dontobfuscate

# 2. الحفاظ على أرقام الأسطر لتسهيل تتبع الأخطاء
-keepattributes SourceFile, LineNumberTable

# 3. حماية الكود الخاص بك وكلاسات BuildConfig
-keep class com.oneui.fontviewer.** { *; }
-keep class **.BuildConfig { *; }

# 4. حماية ملف الترقيع (Shim) الخاص بـ SplashScreen و SESL
-keep class androidx.appcompat.graphics.drawable.DrawableWrapper { *; }

# 5. حماية مكتبة OneUI Design 
-keep class dev.oneuiproject.oneui.** { *; }

# 6. تجاهل التحذيرات للمكاتب الخارجية (ضروري لتجنب فشل البناء)
-dontwarn io.github.oneuiproject.sesl.**
-dontwarn com.airbnb.lottie.**
-dontwarn javax.annotation.**
-dontwarn okio.**

# 7. طباعة قائمة الكود المحذوف للتأكد مما يفعله المحرك
-printusage usage.txt
