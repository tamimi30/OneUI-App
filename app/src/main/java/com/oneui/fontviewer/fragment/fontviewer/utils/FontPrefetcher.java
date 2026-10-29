package com.oneui.fontviewer.fragment.fontviewer.utils;

import android.content.Context;
import android.graphics.Typeface;
import android.util.Log;

import com.oneui.fontviewer.fragment.localfont.data.LocalFontCache;
import com.oneui.fontviewer.fragment.systemfont.data.SystemFontCache;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * يجهّز الخط مسبقاً قبل أن تُرسم شاشة عارض الخط، حتى لا يظهر الخط الافتراضي ولا تتأخر بطاقات المحاور.
 *
 * التجهيز المسبق (Prefetch): يُستدعى {@link #prefetch} من MainActivity لحظة اختيار الخط من القائمة، قبل بدء فتح الشاشة.
 * يحلّل الخط (هل هو متغير، المحاور، القيم الافتراضية) ويبني Typeface في خيط خلفي، أثناء تشغيل أنيميشن فتح الشاشة،
 * فتكون النتيجة جاهزة في ذاكرة مؤقتة صغيرة عند إنشاء FontViewerFragment، فيأخذها عبر {@link #take}.
 * اذا لم تكن جاهزة في الوقت المناسب (أو فشل التجهيز لأي سبب) يعمل مسار التحميل الخلفي في الـ Fragment كما هو دون أي تغيير.
 *
 * التطبيق الفوري من الكاش: {@link #getCachedTypeface} يعيد Typeface الخط من LocalFontCache أو SystemFontCache
 * إن كان محمّلاً مسبقاً، لاستخدامه فوراً بدل انتظار الخيط الخلفي.
 */
public final class FontPrefetcher {

    private static final String TAG = "FontPrefetcher";

    private static final int CACHE_MAX_ENTRIES = 3;

    // القيم الافتراضية الاحتياطية لكل محور، تُستخدم فقط اذا تعذّرت قراءة القيمة الافتراضية الفعلية
    // من جدول fvar الخاص بالخط نفسه (تطابق AXIS_FALLBACK_DEFAULTS في FontViewerFragment)
    private static final float DEFAULT_FONT_WEIGHT = 400f;
    private static final Map<String, Float> AXIS_FALLBACK_DEFAULTS = new HashMap<>();
    static {
        AXIS_FALLBACK_DEFAULTS.put(VariableFontHelper.AXIS_WGHT, DEFAULT_FONT_WEIGHT);
        AXIS_FALLBACK_DEFAULTS.put(VariableFontHelper.AXIS_WDTH, 100f);
        AXIS_FALLBACK_DEFAULTS.put(VariableFontHelper.AXIS_ITAL, 0f);
        AXIS_FALLBACK_DEFAULTS.put(VariableFontHelper.AXIS_GRAD, 0f);
        AXIS_FALLBACK_DEFAULTS.put(VariableFontHelper.AXIS_ROND, 0f);
        AXIS_FALLBACK_DEFAULTS.put(VariableFontHelper.AXIS_MONO, 0f);
    }

    private static final Map<String, PrefetchedFont> sCache = new LinkedHashMap<>();
    private static final ExecutorService sExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "FontViewer-Prefetch");
        thread.setDaemon(true);
        return thread;
    });

    private FontPrefetcher() {
    }

    /** نتيجة تجهيز مسبق لخط واحد: كل ما يحتاجه الـ Fragment لعرضه فوراً. */
    public static final class PrefetchedFont {
        public final boolean isVariable;
        public final Map<String, List<VariableFontHelper.VariableInstance>> axisInstancesMap;
        public final Map<String, Float> axisDefaults;
        public final Typeface typeface;

        PrefetchedFont(boolean isVariable,
                       Map<String, List<VariableFontHelper.VariableInstance>> axisInstancesMap,
                       Map<String, Float> axisDefaults, Typeface typeface) {
            this.isVariable = isVariable;
            this.axisInstancesMap = axisInstancesMap;
            this.axisDefaults = axisDefaults;
            this.typeface = typeface;
        }
    }

    /**
     * تُستدعى من MainActivity قبل startActivity. لا تحجب الخيط الرئيسي: كل العمل في خيط خلفي مستقل.
     * تتجاهل الخطوط القادمة من Uri (تُنسخ أولاً ولها مسارها الخاص).
     */
    public static void prefetch(Context context, String path, int ttcIndex, boolean systemFont) {
        if (context == null || path == null || path.isEmpty() || path.startsWith("content://")) {
            return;
        }

        final Context taskContext = context;
        final String localeTags = localeTagsOf(context);

        sExecutor.execute(() -> {
            try {
                File fontFile = new File(path);
                if (!fontFile.exists()) {
                    return;
                }

                // هذا المنطق يطابق loadFontFromPathWithAxes عند resetToDefaults = true (فتح خط جديد من القائمة)
                boolean isVar = VariableFontHelper.isVariableFont(fontFile, ttcIndex);

                Map<String, List<VariableFontHelper.VariableInstance>> axisInstancesMap = new LinkedHashMap<>();
                Map<String, Float> axisDefaults = new LinkedHashMap<>();

                if (isVar) {
                    axisInstancesMap.put(VariableFontHelper.AXIS_WGHT, VariableFontHelper.extractVariableInstances(taskContext, fontFile, ttcIndex));
                    axisInstancesMap.put(VariableFontHelper.AXIS_WDTH, VariableFontHelper.extractWidthInstances(taskContext, fontFile, ttcIndex));
                    axisInstancesMap.put(VariableFontHelper.AXIS_ITAL, VariableFontHelper.extractItalicInstances(taskContext, fontFile, ttcIndex));
                    axisInstancesMap.put(VariableFontHelper.AXIS_GRAD, VariableFontHelper.extractGradeInstances(taskContext, fontFile, ttcIndex));
                    axisInstancesMap.put(VariableFontHelper.AXIS_ROND, VariableFontHelper.extractRoundnessInstances(taskContext, fontFile, ttcIndex));
                    axisInstancesMap.put(VariableFontHelper.AXIS_MONO, VariableFontHelper.extractMonoInstances(taskContext, fontFile, ttcIndex));

                    for (Map.Entry<String, List<VariableFontHelper.VariableInstance>> entry : axisInstancesMap.entrySet()) {
                        String axisTag = entry.getKey();
                        List<VariableFontHelper.VariableInstance> instances = entry.getValue();

                        if (instances == null || instances.isEmpty()) {
                            continue;
                        }

                        Float fallbackBoxed = AXIS_FALLBACK_DEFAULTS.get(axisTag);
                        float fallback = fallbackBoxed != null ? fallbackBoxed : 0f;
                        axisDefaults.put(axisTag, VariableFontHelper.readAxisDefaultValue(fontFile, ttcIndex, axisTag, fallback));
                    }
                }

                Typeface typeface;
                if (systemFont) {
                    typeface = SystemFontCache.getInstance().getTypefaceWithAxes(path, axisDefaults, ttcIndex);
                } else {
                    typeface = VariableFontHelper.createTypefaceWithAxes(fontFile, axisDefaults, ttcIndex);
                }

                // أي فشل: لا نخزّن شيئاً، فيتولى مسار التحميل الخلفي القديم معالجة الخطأ وعرض رسالته كما كان
                if (typeface == null) {
                    return;
                }

                String key = buildKey(path, ttcIndex, systemFont, localeTags);
                synchronized (sCache) {
                    while (sCache.size() >= CACHE_MAX_ENTRIES) {
                        String eldestKey = sCache.keySet().iterator().next();
                        sCache.remove(eldestKey);
                    }
                    sCache.put(key, new PrefetchedFont(isVar, axisInstancesMap, axisDefaults, typeface));
                }
                Log.d(TAG, "Prefetch ready for: " + path);
            } catch (Exception e) {
                Log.w(TAG, "Prefetch failed for: " + path, e);
            }
        });
    }

    /**
     * يأخذ نتيجة التجهيز المسبق لهذا الخط (ويزيلها من الذاكرة المؤقتة)، أو null اذا لم تكن جاهزة.
     */
    public static PrefetchedFont take(Context context, String path, int ttcIndex, boolean systemFont) {
        String key = buildKey(path, ttcIndex, systemFont, localeTagsOf(context));
        synchronized (sCache) {
            return sCache.remove(key);
        }
    }

    /**
     * يعيد Typeface الخط من الذاكرة المؤقتة (LocalFontCache أو SystemFontCache) اذا كان محمّلاً مسبقاً، وإلا null،
     * دون أي قراءة ملفات أو تحميل. الكاشان يخزّنان الخط بقيم محاوره الافتراضية وبفهرس TTC = 0 فقط، وهذا مطابق لما يبنيه
     * التحميل الخلفي عند اختيار خط جديد (لأن كل محاوره تُعاد الى قيمها الافتراضية). لا نستخدم الكاش مع ttcIndex > 0
     * لأنه لا يميّز بين خطوط الملف الواحد.
     */
    public static Typeface getCachedTypeface(String path, int ttcIndex, boolean systemFont) {
        if (ttcIndex > 0) {
            return null;
        }
        return systemFont
                ? SystemFontCache.getInstance().getIfCached(path)
                : LocalFontCache.getInstance().getIfCached(path);
    }

    /**
     * مفتاح التجهيز المسبق: المسار + فهرس TTC + نوع الخط + تاريخ تعديل الملف وحجمه (حتى لا نستخدم نتيجة قديمة اذا تغيّر الملف)
     * + لغة التطبيق (لأن أسماء قيم المحاور تُقرأ من strings.xml حسب اللغة).
     */
    private static String buildKey(String path, int ttcIndex, boolean systemFont, String localeTags) {
        File file = new File(path);
        return path + "|" + ttcIndex + "|" + systemFont + "|" + file.lastModified() + "|" + file.length() + "|" + localeTags;
    }

    private static String localeTagsOf(Context context) {
        return context.getResources().getConfiguration().getLocales().toLanguageTags();
    }
}
