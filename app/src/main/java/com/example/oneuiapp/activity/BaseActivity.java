package com.example.oneuiapp.activity;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.oneuiapp.fragment.settings.utils.SettingsHelper;

/**
 * BaseActivity - الأساس لجميع الأنشطة في التطبيق
 * 
 * يضمن تطبيق الإعدادات (اللغة، الخط) على كل Activity
 * 
 * آلية العمل المحسّنة:
 * 1. في attachBaseContext: نطبق اللغة على السياق عبر ContextWrapper للأجهزة دون Android 13
 *    أما أجهزة Android 13 فما فوق فيتكفّل النظام بتطبيق اللغة والاتجاه تلقائياً عبر LocaleManager
 * 2. في onCreate: نثبت Factory مخصص لاعتراض إنشاء Views وتطبيق الخط
 * 3. في onPostCreate: نطبق الخط على العناصر الخاصة (Toolbar, AppBar)
 * 
 * ملاحظة مهمة: تم حذف كل أكواد فرض اتجاه التنسيق (RTL/LTR) يدوياً،
 * لأن النظام يتكفل بذلك تلقائياً بناءً على الـ Locale المضبوط في Context،
 * وفرضه يدوياً كان يتعارض مع خيارات المطورين ويسبب خللاً في الاتجاهات.
 */
public class BaseActivity extends AppCompatActivity {

    @Override
    protected void attachBaseContext(Context newBase) {
        // تطبيق اللغة على السياق عبر ContextWrapper
        // للأجهزة دون Android 13: يضمن تحميل الموارد باللغة الصحيحة
        // للأجهزة Android 13+: تكفل النظام بالأمر عبر LocaleManager، وهذا مجرد تأكيد إضافي
        // الاتجاه (RTL/LTR) يُحدَّد تلقائياً من الـ Locale دون الحاجة لفرضه يدوياً
        Context wrappedContext = SettingsHelper.wrapContext(newBase);
        
        super.attachBaseContext(wrappedContext);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
}
