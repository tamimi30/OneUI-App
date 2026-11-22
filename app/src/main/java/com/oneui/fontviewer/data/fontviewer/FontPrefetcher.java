package com.oneui.fontviewer.data.fontviewer;

import android.graphics.Typeface;
import android.util.Log;

import com.oneui.fontviewer.utils.variable.InstanceLabelResolver;
import com.oneui.fontviewer.utils.variable.VariableFontAxisExtractor;
import com.oneui.fontviewer.data.localfont.LocalFontCache;
import com.oneui.fontviewer.data.systemfont.SystemFontCache;

import java.io.File;
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
        public final Map<String, List<VariableFontAxisExtractor.VariableInstance>> axisInstancesMap;
        public final Map<String, Float> axisDefaults;
        public final Typeface typeface;

        PrefetchedFont(boolean isVariable,
                       Map<String, List<VariableFontAxisExtractor.VariableInstance>> axisInstancesMap,
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
     * labels يحوّل مفاتيح أسماء قيم المحاور الى نصوص اللغة الحالية، وlocaleTags وسوم هذه اللغة (جزء من مفتاح التجهيز).
     */
    public static void prefetch(InstanceLabelResolver labels, String localeTags, String path, int ttcIndex, boolean systemFont) {
        if (labels == null || path == null || path.isEmpty() || path.startsWith("content://")) {
            return;
        }

        sExecutor.execute(() -> {
            try {
                File fontFile = new File(path);
                if (!fontFile.exists()) {
                    return;
                }

                // هذا المنطق يطابق FontViewerRepository.loadFont عند resetToDefaults = true (فتح خط جديد من القائمة)
                FontViewerRepository.AxisAnalysis analysis = FontViewerRepository.analyzeAxes(fontFile, ttcIndex, labels);
                boolean isVar = analysis.isVariable;
                Map<String, List<VariableFontAxisExtractor.VariableInstance>> axisInstancesMap = analysis.axisInstancesMap;
                Map<String, Float> axisDefaults = analysis.axisDefaults;

                Typeface typeface = FontViewerRepository.createTypeface(path, fontFile, axisDefaults, ttcIndex, systemFont);

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
    public static PrefetchedFont take(String localeTags, String path, int ttcIndex, boolean systemFont) {
        String key = buildKey(path, ttcIndex, systemFont, localeTags);
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
}
