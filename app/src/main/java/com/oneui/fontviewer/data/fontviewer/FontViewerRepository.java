package com.oneui.fontviewer.data.fontviewer;

import android.content.Context;
import android.graphics.Typeface;
import android.net.Uri;
import android.util.Log;

import com.oneui.fontviewer.data.settings.SettingsRepository;
import com.oneui.fontviewer.data.systemfont.SystemFontCache;
import com.oneui.fontviewer.utils.metadata.FontMetadataReader;
import com.oneui.fontviewer.utils.metadata.FontWeightWidthExtractor;
import com.oneui.fontviewer.utils.variable.InstanceLabelResolver;
import com.oneui.fontviewer.utils.variable.VariableFontAxisExtractor;
import com.oneui.fontviewer.utils.variable.VariableTypefaceFactory;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * كل منطق عارض الخط غير المرتبط بالواجهة: تحليل الخط ومحاوره، بناء الـ Typeface، قراءة وحفظ آخر خط وقيم المحاور،
 * نسخ الخط القادم من Uri، وتجميع بيانات الخط الوصفية. لا يعرف أي View أو Fragment أو موارد نصوص (R)،
 * وأسماء قيم المحاور تأتيه جاهزة من الواجهة عبر {@link InstanceLabelResolver}.
 *
 * دوال التحميل والنسخ ثقيلة (قراءة ملفات): تُستدعى من خيط خلفي، وتتولى ذلك FontViewerViewModel.
 */
public class FontViewerRepository {

    private static final String TAG = "FontViewerRepository";

    private static final float DEFAULT_FONT_WEIGHT = 400f;

    // القيم الافتراضية الاحتياطية لكل محور، تُستخدم فقط اذا تعذّرت قراءة القيمة الافتراضية الفعلية
    // من جدول fvar الخاص بالخط نفسه
    private static final Map<String, Float> AXIS_FALLBACK_DEFAULTS = new HashMap<>();
    static {
        AXIS_FALLBACK_DEFAULTS.put(VariableFontAxisExtractor.AXIS_WGHT, DEFAULT_FONT_WEIGHT);
        AXIS_FALLBACK_DEFAULTS.put(VariableFontAxisExtractor.AXIS_WDTH, 100f);
        AXIS_FALLBACK_DEFAULTS.put(VariableFontAxisExtractor.AXIS_ITAL, 0f);
        AXIS_FALLBACK_DEFAULTS.put(VariableFontAxisExtractor.AXIS_GRAD, 0f);
        AXIS_FALLBACK_DEFAULTS.put(VariableFontAxisExtractor.AXIS_ROND, 0f);
        AXIS_FALLBACK_DEFAULTS.put(VariableFontAxisExtractor.AXIS_MONOSPACE, 0f);
    }

    private final Context appContext;
    private final FontViewerFileManager fileManager;
    private final FontViewerPreferenceManager preferenceManager;

    public FontViewerRepository(Context context) {
        this.appContext = context.getApplicationContext();
        this.fileManager = new FontViewerFileManager(appContext);
        this.preferenceManager = new FontViewerPreferenceManager(appContext);
    }

    // ───────────────────────── نتائج العمليات ─────────────────────────

    /** نتيجة تحليل محاور خط: هل هو متغير، القيم المسمّاة لكل محور، والقيمة الافتراضية الفعلية للمحاور المدعومة. */
    public static final class AxisAnalysis {
        public final boolean isVariable;
        // خريطة وسم المحور الى قائمة القيم المسمّاة المتاحة له
        public final Map<String, List<VariableFontAxisExtractor.VariableInstance>> axisInstancesMap;
        // القيمة الافتراضية الفعلية لكل محور مدعوم (المحاور التي لا قيم لها غير موجودة هنا)
        public final Map<String, Float> axisDefaults;

        AxisAnalysis(boolean isVariable,
                     Map<String, List<VariableFontAxisExtractor.VariableInstance>> axisInstancesMap,
                     Map<String, Float> axisDefaults) {
            this.isVariable = isVariable;
            this.axisInstancesMap = axisInstancesMap;
            this.axisDefaults = axisDefaults;
        }
    }

    public enum LoadStatus {
        SUCCESS,
        /** الملف غير موجود. */
        FILE_MISSING,
        /** فشل إنشاء الـ Typeface (الخط قد يكون تالفاً). */
        TYPEFACE_ERROR,
        /** أي خطأ آخر أثناء التحميل. */
        ERROR
    }

    /** نتيجة تحميل خط بمحاوره. الحقول غير الخاصة بالنجاح تكون null. */
    public static final class LoadResult {
        public final LoadStatus status;
        public final Typeface typeface;
        public final boolean isVariable;
        public final Map<String, List<VariableFontAxisExtractor.VariableInstance>> axisInstancesMap;
        // القيم المختارة فعلياً لكل محور مدعوم
        public final Map<String, Float> resolvedAxisValues;
        public final Exception error;

        private LoadResult(LoadStatus status, Typeface typeface, boolean isVariable,
                           Map<String, List<VariableFontAxisExtractor.VariableInstance>> axisInstancesMap,
                           Map<String, Float> resolvedAxisValues, Exception error) {
            this.status = status;
            this.typeface = typeface;
            this.isVariable = isVariable;
            this.axisInstancesMap = axisInstancesMap;
            this.resolvedAxisValues = resolvedAxisValues;
            this.error = error;
        }

        static LoadResult success(Typeface typeface, boolean isVariable,
                                  Map<String, List<VariableFontAxisExtractor.VariableInstance>> axisInstancesMap,
                                  Map<String, Float> resolvedAxisValues) {
            return new LoadResult(LoadStatus.SUCCESS, typeface, isVariable, axisInstancesMap, resolvedAxisValues, null);
        }

        static LoadResult failure(LoadStatus status, Exception error) {
            return new LoadResult(status, null, false, null, null, error);
        }
    }

    /** خط نُسخ من Uri الى مجلد التطبيق مع بياناته المستخرجة (قد يكون realName وweightWidthLabel بقيمة null). */
    public static final class ImportedFont {
        public final File file;
        public final String realName;
        public final String weightWidthLabel;

        ImportedFont(File file, String realName, String weightWidthLabel) {
            this.file = file;
            this.realName = realName;
            this.weightWidthLabel = weightWidthLabel;
        }
    }

    /** آخر خط عُرض كما حُفظ في الإعدادات (بعد التأكد من وجود ملفه). */
    public static final class LastViewedFont {
        public final String path;
        public final String fileName;
        public final String realName;
        public final String weightWidthLabel;
        public final String originalPath;

        LastViewedFont(String path, String fileName, String realName, String weightWidthLabel, String originalPath) {
            this.path = path;
            this.fileName = fileName;
            this.realName = realName;
            this.weightWidthLabel = weightWidthLabel;
            this.originalPath = originalPath;
        }
    }

    // ───────────────────────── تحليل الخط وبناء الـ Typeface ─────────────────────────

    /**
     * يحلّل الخط: هل هو متغير، وإن كان كذلك يستخرج القيم المسمّاة لكل محور مدعوم (wght, wdth, ital, GRAD, ROND, MONO)
     * وقيمته الافتراضية الفعلية من جدول fvar. دالة ثابتة بلا حالة، يستخدمها التحميل العادي والتجهيز المسبق معاً.
     */
    public static AxisAnalysis analyzeAxes(File fontFile, int ttcIndex, InstanceLabelResolver labels) {
        boolean isVar = VariableFontAxisExtractor.isVariableFont(fontFile, ttcIndex);

        // خريطة وسم المحور الى قائمة القيم المسمّاة المتاحة له
        Map<String, List<VariableFontAxisExtractor.VariableInstance>> axisInstancesMap = new LinkedHashMap<>();
        Map<String, Float> axisDefaults = new LinkedHashMap<>();

        if (isVar) {
            axisInstancesMap.put(VariableFontAxisExtractor.AXIS_WGHT, VariableFontAxisExtractor.extractVariableInstances(labels, fontFile, ttcIndex));
            axisInstancesMap.put(VariableFontAxisExtractor.AXIS_WDTH, VariableFontAxisExtractor.extractWidthInstances(labels, fontFile, ttcIndex));
            axisInstancesMap.put(VariableFontAxisExtractor.AXIS_ITAL, VariableFontAxisExtractor.extractItalicInstances(labels, fontFile, ttcIndex));
            axisInstancesMap.put(VariableFontAxisExtractor.AXIS_GRAD, VariableFontAxisExtractor.extractGradeInstances(labels, fontFile, ttcIndex));
            axisInstancesMap.put(VariableFontAxisExtractor.AXIS_ROND, VariableFontAxisExtractor.extractRoundnessInstances(labels, fontFile, ttcIndex));
            axisInstancesMap.put(VariableFontAxisExtractor.AXIS_MONOSPACE, VariableFontAxisExtractor.extractMonospaceInstances(labels, fontFile, ttcIndex));

            for (Map.Entry<String, List<VariableFontAxisExtractor.VariableInstance>> entry : axisInstancesMap.entrySet()) {
                String axisTag = entry.getKey();
                List<VariableFontAxisExtractor.VariableInstance> instances = entry.getValue();

                if (instances == null || instances.isEmpty()) {
                    continue;
                }

                Float fallbackBoxed = AXIS_FALLBACK_DEFAULTS.get(axisTag);
                float fallback = fallbackBoxed != null ? fallbackBoxed : 0f;
                axisDefaults.put(axisTag, VariableFontAxisExtractor.readAxisDefaultValue(fontFile, ttcIndex, axisTag, fallback));
            }
        }

        return new AxisAnalysis(isVar, axisInstancesMap, axisDefaults);
    }

    /**
     * يبني Typeface الخط بقيم المحاور المعطاة: من SystemFontCache لخطوط النظام، ومن VariableTypefaceFactory لغيرها.
     * قد يعيد null أو يرمي استثناءً اذا كان الخط تالفاً.
     */
    public static Typeface createTypeface(String path, File fontFile, Map<String, Float> axisValues,
                                          int ttcIndex, boolean systemFont) {
        if (systemFont) {
            SystemFontCache cache = SystemFontCache.getInstance();
            return cache.getTypefaceWithAxes(path, axisValues, ttcIndex);
        }
        return VariableTypefaceFactory.createTypefaceWithAxes(fontFile, axisValues, ttcIndex);
    }

    /**
     * يحمّل الخط من مساره، ويحدد لكل محور مدعوم القيمة التي سيُفتح بها، وفق ثلاث حالات:
     * 1) restoreAxisValues غير فارغة لمحور معيّن: تُستخدم القيمة المستعادة كما هي.
     * 2) resetToDefaults = true: خط جديد، فيعود المحور الى قيمته الافتراضية الفعلية في الخط وتُحفظ.
     * 3) غير ذلك: آخر قيمة محفوظة لهذا المحور.
     * لا ترمي استثناءً أبداً: كل فشل يعود في LoadResult بحالته.
     */
    public LoadResult loadFont(String path, int ttcIndex, boolean systemFont,
                               Map<String, Float> restoreAxisValues, boolean resetToDefaults,
                               InstanceLabelResolver labels) {
        try {
            File fontFile = new File(path);
            if (!fontFile.exists()) {
                return LoadResult.failure(LoadStatus.FILE_MISSING, null);
            }

            AxisAnalysis analysis = analyzeAxes(fontFile, ttcIndex, labels);

            // القيم المختارة فعلياً لكل محور مدعوم
            Map<String, Float> resolvedAxisValues = new LinkedHashMap<>();

            for (Map.Entry<String, Float> entry : analysis.axisDefaults.entrySet()) {
                String axisTag = entry.getKey();
                float fontDefault = entry.getValue();

                float resolvedValue;
                if (restoreAxisValues != null && restoreAxisValues.containsKey(axisTag)) {
                    resolvedValue = restoreAxisValues.get(axisTag);
                } else if (resetToDefaults) {
                    resolvedValue = fontDefault;
                    preferenceManager.saveFontAxisValue(axisTag, resolvedValue);
                } else {
                    resolvedValue = preferenceManager.getFontAxisValue(axisTag, fontDefault);
                }

                resolvedAxisValues.put(axisTag, resolvedValue);
            }

            Typeface typeface;

            try {
                typeface = createTypeface(path, fontFile, resolvedAxisValues, ttcIndex, systemFont);
            } catch (Exception e) {
                Log.e(TAG, "★ Typeface creation failed - font might be corrupted", e);
                return LoadResult.failure(LoadStatus.TYPEFACE_ERROR, e);
            }

            if (typeface == null) {
                throw new Exception("Failed to create Typeface - returned null");
            }

            return LoadResult.success(typeface, analysis.isVariable, analysis.axisInstancesMap, resolvedAxisValues);

        } catch (Exception e) {
            return LoadResult.failure(LoadStatus.ERROR, e);
        }
    }

    // ───────────────────────── الملفات والمسارات ─────────────────────────

    public String getRealPathFromUri(Uri uri) {
        return fileManager.getRealPathFromUri(uri);
    }

    public String resolveOriginalPath(String pathOrUri) {
        if (pathOrUri == null) return null;

        if (pathOrUri.startsWith("content://")) {
            Uri uri = Uri.parse(pathOrUri);
            String realPath = fileManager.getRealPathFromUri(uri);
            if (realPath != null && !realPath.isEmpty()) {
                return realPath;
            }
        }

        return pathOrUri;
    }

    /**
     * ينسخ الخط القادم من Uri الى مجلد التطبيق ويستخرج اسمه الحقيقي وتسمية وزنه. يعيد null اذا فشل النسخ.
     */
    public ImportedFont importFont(Uri uri, String fileName) {
        File copiedFont = fileManager.copyFontForViewing(uri, fileName);

        if (copiedFont == null || !copiedFont.exists()) {
            return null;
        }

        String realName = null;
        String weightWidthLabel = null;

        try {
            realName = FontMetadataReader.extractFontName(copiedFont, 0);
            weightWidthLabel = FontWeightWidthExtractor.extract(copiedFont, 0);
        } catch (Exception e) {
            Log.e(TAG, "Failed to extract font metadata from URI", e);
        }

        return new ImportedFont(copiedFont, realName, weightWidthLabel);
    }

    // ───────────────────────── آخر خط وقيم المحاور المحفوظة ─────────────────────────

    public void saveLastViewedFont(String path, String fileName, String realName, String originalPath) {
        preferenceManager.saveLastViewedFont(path, fileName, realName);
        preferenceManager.saveLastViewedFontOriginalPath(originalPath);
    }

    public void saveFontAxisValue(String axisTag, float value) {
        preferenceManager.saveFontAxisValue(axisTag, value);
    }

    public void saveFontAxisValues(Map<String, Float> axisValues) {
        for (Map.Entry<String, Float> entry : axisValues.entrySet()) {
            preferenceManager.saveFontAxisValue(entry.getKey(), entry.getValue());
        }
    }

    /**
     * يقرأ آخر خط عُرض (الإعدادات + فحص الملف + استخراج وزنه ومساره الأصلي). يعيد null اذا لم يوجد،
     * واذا كان ملفه قد حُذف يمسح الإعداد المحفوظ.
     */
    public LastViewedFont loadLastViewedFont() {
        String lastPath     = preferenceManager.getLastViewedFontPath();
        String lastFileName = preferenceManager.getLastViewedFontFileName();
        String lastRealName = preferenceManager.getLastViewedFontRealName();

        if (lastPath != null && !lastPath.isEmpty()) {
            File localFile = new File(lastPath);
            if (localFile.exists()) {
                final String weightWidthLabel = FontWeightWidthExtractor.extract(localFile, 0);

                String lastOriginalPath = preferenceManager.getLastViewedFontOriginalPath();
                final String resolvedOriginalPath = (lastOriginalPath != null && !lastOriginalPath.isEmpty())
                        ? lastOriginalPath
                        : resolveOriginalPath(lastPath);

                return new LastViewedFont(lastPath, lastFileName, lastRealName, weightWidthLabel, resolvedOriginalPath);
            } else {
                preferenceManager.clearLastViewedFont();
            }
        }
        return null;
    }

    public String getPreviewText() {
        return SettingsRepository.getPreviewText(appContext);
    }

    // ───────────────────────── البيانات الوصفية ─────────────────────────

    /**
     * يجمّع البيانات الوصفية للخط الحالي. unknownFontLabel هو النص المعروض لـ "خط غير معروف" (يحدده المستدعي حسب اللغة).
     */
    public Map<String, String> buildMetadata(String fontPath, String originalFontPath, String fileName,
                                             String realName, int ttcIndex, String unknownFontLabel) {
        if (fontPath == null) {
            return new HashMap<>();
        }

        File fontFile = new File(fontPath);

        Map<String, String> metadata = FontMetadataReader.extractMetadataWithTtcIndex(fontFile, ttcIndex);

        if (metadata == null) {
            metadata = new HashMap<>();
        }

        String displayPath = (originalFontPath != null && !originalFontPath.isEmpty())
            ? originalFontPath
            : fontPath;

        metadata.put("Path", displayPath);
        metadata.put("FileName", fileName != null ? fileName : "");

        if (!metadata.containsKey("FullName")) {
            if (realName == null || realName.isEmpty() || realName.equals(unknownFontLabel)) {
                metadata.put("FullName", unknownFontLabel);
                metadata.put("Warning", "Font metadata could not be extracted - file may be corrupted");
            } else {
                metadata.put("FullName", realName);
            }
        }

        if (ttcIndex > 0) {
            metadata.put("TTC Index", String.valueOf(ttcIndex));
        }

        return metadata;
    }
}
