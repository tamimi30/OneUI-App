package com.oneui.fontviewer.utils.variable;

import android.graphics.fonts.Font;
import android.graphics.fonts.FontVariationAxis;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads variable-font data from font files: fvar axes, named instances,
 * axis ranges and default values.
 */
public class VariableFontAxisExtractor {
    
    private static final String TAG = "VariableFontAxisExtractor";

    // ذاكرة مؤقتة لمحاور fvar: كان جدول fvar يُفتح ويُقرأ نحو 12 مرة لكل خط متغير، فصار يُقرأ مرة واحدة ثم تُخدم بقية الطلبات منها.
    // المفتاح يتضمن المسار وفهرس TTC وتاريخ التعديل والحجم، فاذا تغيّر الملف تُقرأ بياناته من جديد
    private static final Map<String, Map<String, float[]>> FVAR_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    // ذاكرة مؤقتة لنتيجة isVariableFont: كانت تبني Font.Builder وتقرأ الملف مرتين لكل خط متغير (مرة مباشرة ومرة داخل
    // extractVariableInstances). نخزّن النتيجة الموجبة (الخط متغير) فقط، حتى لا يعلق false ناتج عن خطأ قراءة عابر.
    // المفتاح يتضمن تاريخ التعديل والحجم، فاذا تغيّر الملف يُفحص من جديد
    private static final Map<String, Boolean> VARIABLE_FONT_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    // وسوم محاور الخط المتغير المدعومة.
    // ملاحظة تقنية مهمة: wght, wdth, ital محاور مسجّلة رسمياً في OpenType لذلك وسومها بأحرف صغيرة،
    // بينما GRAD, ROND, MONO محاور غير رسمية (foundry-defined) ويجب أن تكون وسومها بأحرف كبيرة بالكامل،
    // هذا جزء من بنية الخط نفسه (كما هو مخزن فعلياً داخل جدول fvar) وليس مجرد اختيار تسمية.
    public static final String AXIS_WGHT = "wght";
    public static final String AXIS_WDTH = "wdth";
    public static final String AXIS_ITAL = "ital";
    public static final String AXIS_GRAD = "GRAD";
    public static final String AXIS_ROND = "ROND";
    public static final String AXIS_MONO = "MONO";
    
    public static class VariableInstance {
        public String name;
        public String tag;
        public float value;
        
        public VariableInstance(String name, String tag, float value) {
            this.name = name;
            this.tag = tag;
            this.value = value;
        }
        
        @Override
        public String toString() {
            return name;
        }
    }
    
    public static boolean isVariableFont(File fontFile, int ttcIndex) {
        
        
        if (fontFile == null || !fontFile.exists()) {
            return false;
        }

        String cacheKey = fontFile.getAbsolutePath() + '|' + ttcIndex + '|'
                + fontFile.lastModified() + '|' + fontFile.length();
        if (VARIABLE_FONT_CACHE.containsKey(cacheKey)) {
            return true;
        }

        boolean result = detectVariableFont(fontFile, ttcIndex);
        if (result) {
            if (VARIABLE_FONT_CACHE.size() >= 64) VARIABLE_FONT_CACHE.clear();
            VARIABLE_FONT_CACHE.put(cacheKey, Boolean.TRUE);
        }
        return result;
    }

    private static boolean detectVariableFont(File fontFile, int ttcIndex) {
        try {
            FontVariationAxis[] axes = getVariationAxes(fontFile, ttcIndex);
            if (axes != null && axes.length > 0) {
                return true;
            }
        } catch (Exception e) {
        }
        
        return fvarTableExists(fontFile, ttcIndex);
    }
    
    
    
    private static FontVariationAxis[] getVariationAxes(File fontFile, int ttcIndex) {
        try {
            Font.Builder fontBuilder = new Font.Builder(fontFile);
            if (ttcIndex > 0) {
                fontBuilder.setTtcIndex(ttcIndex);
            }
            Font font = fontBuilder.build();
            return font.getAxes();
        } catch (Exception e) {
            return null; 
        }
    }
    
    private static boolean fvarTableExists(File fontFile, int ttcIndex) {
        try (RandomAccessFile raf = new RandomAccessFile(fontFile, "r")) {
            byte[] header = new byte[4];
            raf.read(header);
            String tag = new String(header, "US-ASCII");
            
            if ("ttcf".equals(tag)) {
                raf.seek(8);
                long numFonts = readUInt32(raf);
                if (ttcIndex >= numFonts) {
                    return false;
                }
                
                raf.seek(12 + (ttcIndex * 4));
                long fontOffset = readUInt32(raf);
                return checkFvarAtOffset(raf, fontOffset);
            } else {
                raf.seek(4);
                int numTables = readUInt16(raf);
                
                for (int i = 0; i < numTables; i++) {
                    raf.seek(12 + i * 16);
                    byte[] tableTag = new byte[4];
                    raf.read(tableTag);
                    String tagStr = new String(tableTag, "US-ASCII");
                    
                    if ("fvar".equals(tagStr)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
        }
        return false;
    }
    
    private static boolean checkFvarAtOffset(RandomAccessFile raf, long fontOffset) {
        try {
            raf.seek(fontOffset + 4);
            int numTables = readUInt16(raf);
            
            for (int i = 0; i < numTables; i++) {
                raf.seek(fontOffset + 12 + i * 16);
                byte[] tableTag = new byte[4];
                raf.read(tableTag);
                String tagStr = new String(tableTag, "US-ASCII");
                
                if ("fvar".equals(tagStr)) {
                    return true;
                }
            }
        } catch (Exception e) {
        }
        return false;
    }
    
    public static List<VariableInstance> extractVariableInstances(InstanceLabelResolver labels, File fontFile, int ttcIndex) {
        List<VariableInstance> instances = new ArrayList<>();
        
        
        
        if (!isVariableFont(fontFile, ttcIndex)) {
            return instances;
        }
        
        try {
            float[] range = readWeightRangeFromFvar(fontFile, ttcIndex);
            float minWeight = range[0];
            float maxWeight = range[1];
            
            addWeightIfInRange(instances, labels.nameOf(InstanceLabel.WEIGHT_THIN), 100, minWeight, maxWeight);
            addWeightIfInRange(instances, labels.nameOf(InstanceLabel.WEIGHT_EXTRA_LIGHT), 200, minWeight, maxWeight);
            addWeightIfInRange(instances, labels.nameOf(InstanceLabel.WEIGHT_LIGHT), 300, minWeight, maxWeight);
            addWeightIfInRange(instances, labels.nameOf(InstanceLabel.WEIGHT_REGULAR), 400, minWeight, maxWeight);
            addWeightIfInRange(instances, labels.nameOf(InstanceLabel.WEIGHT_MEDIUM), 500, minWeight, maxWeight);
            addWeightIfInRange(instances, labels.nameOf(InstanceLabel.WEIGHT_SEMI_BOLD), 600, minWeight, maxWeight);
            addWeightIfInRange(instances, labels.nameOf(InstanceLabel.WEIGHT_BOLD), 700, minWeight, maxWeight);
            addWeightIfInRange(instances, labels.nameOf(InstanceLabel.WEIGHT_EXTRA_BOLD), 800, minWeight, maxWeight);
            addWeightIfInRange(instances, labels.nameOf(InstanceLabel.WEIGHT_BLACK), 900, minWeight, maxWeight);
            
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to extract instances", e);
        }
        
        return instances;
    }

    // ════════════════════════════════════════════════════════════
    // محاور إضافية: العرض (wdth)، الميل (ital)، التدرج (GRAD)،
    // الاستدارة (ROND)، والتباعد الأحادي (MONO)
    // ════════════════════════════════════════════════════════════

    /**
     * محور العرض (wdth). يستخدم القيم الشائعة القياسية (نفس نسب usWidthClass في OS/2):
     * Condensed = 75%, Normal = 100%, Expanded = 125% — تماماً كما يحدث مع الوزن (Bold = 700 مثلاً)،
     * وليس الحد الأدنى/الأقصى الفعلي للخط.
     */
    public static List<VariableInstance> extractWidthInstances(InstanceLabelResolver labels, File fontFile, int ttcIndex) {
        List<VariableInstance> instances = new ArrayList<>();

        float[] range = readAxisRangeFromFvar(fontFile, ttcIndex, AXIS_WDTH);
        if (range == null) {
            return instances;
        }

        float minWidth = range[0];
        float maxWidth = range[2];

        addNamedInstanceIfInRange(instances, labels.nameOf(InstanceLabel.WIDTH_CONDENSED), AXIS_WDTH, 75f, minWidth, maxWidth);
        addNamedInstanceIfInRange(instances, labels.nameOf(InstanceLabel.WIDTH_NORMAL), AXIS_WDTH, 100f, minWidth, maxWidth);
        addNamedInstanceIfInRange(instances, labels.nameOf(InstanceLabel.WIDTH_EXPANDED), AXIS_WDTH, 125f, minWidth, maxWidth);

        return instances;
    }

    /**
     * محور الميل (ital). محور ثنائي عادة: 0 = Upright، 1 = Italic.
     */
    public static List<VariableInstance> extractItalicInstances(InstanceLabelResolver labels, File fontFile, int ttcIndex) {
        List<VariableInstance> instances = new ArrayList<>();

        float[] range = readAxisRangeFromFvar(fontFile, ttcIndex, AXIS_ITAL);
        if (range == null) {
            return instances;
        }

        float minItal = range[0];
        float maxItal = range[2];

        addNamedInstanceIfInRange(instances, labels.nameOf(InstanceLabel.ITALIC_UPRIGHT), AXIS_ITAL, 0f, minItal, maxItal);
        addNamedInstanceIfInRange(instances, labels.nameOf(InstanceLabel.ITALIC_ITALIC), AXIS_ITAL, 1f, minItal, maxItal);

        return instances;
    }

    /**
     * محور التدرج (GRAD). هنا نستخدم القيم الفعلية للخط: Low = الحد الأدنى الذي يدعمه الخط،
     * High = الحد الأقصى الذي يدعمه الخط، Normal = القيمة الافتراضية الفعلية المسجّلة في fvar
     * (وليست 0 دائماً، فبعض الخطوط تجعل قيمتها الافتراضية مختلفة).
     */
    public static List<VariableInstance> extractGradeInstances(InstanceLabelResolver labels, File fontFile, int ttcIndex) {
        List<VariableInstance> instances = new ArrayList<>();

        float[] range = readAxisRangeFromFvar(fontFile, ttcIndex, AXIS_GRAD);
        if (range == null) {
            return instances;
        }

        float minGrade     = range[0];
        float defaultGrade = range[1];
        float maxGrade     = range[2];

        if (minGrade < defaultGrade) {
            instances.add(new VariableInstance(labels.nameOf(InstanceLabel.GRADE_LOW), AXIS_GRAD, minGrade));
        }
        instances.add(new VariableInstance(labels.nameOf(InstanceLabel.WIDTH_NORMAL), AXIS_GRAD, defaultGrade));
        if (maxGrade > defaultGrade) {
            instances.add(new VariableInstance(labels.nameOf(InstanceLabel.GRADE_HIGH), AXIS_GRAD, maxGrade));
        }

        return instances;
    }

    /**
     * محور الاستدارة (ROND). Sharp = الحد الأدنى الفعلي، Rounded = الحد الأقصى الفعلي للخط.
     */
    public static List<VariableInstance> extractRoundnessInstances(InstanceLabelResolver labels, File fontFile, int ttcIndex) {
        List<VariableInstance> instances = new ArrayList<>();

        float[] range = readAxisRangeFromFvar(fontFile, ttcIndex, AXIS_ROND);
        if (range == null) {
            return instances;
        }

        float minRound = range[0];
        float maxRound = range[2];

        if (maxRound > minRound) {
            instances.add(new VariableInstance(labels.nameOf(InstanceLabel.ROUNDNESS_SHARP), AXIS_ROND, minRound));
            instances.add(new VariableInstance(labels.nameOf(InstanceLabel.ROUNDNESS_ROUNDED), AXIS_ROND, maxRound));
        }

        return instances;
    }

    /**
     * محور التباعد الأحادي (MONO). Proportional = الحد الأدنى (القيمة الأصغر)،
     * Monospaced = الحد الأقصى (القيمة الأكبر) — مرتبة تصاعدياً حسب القيمة كما هو مطلوب.
     */
    public static List<VariableInstance> extractMonoInstances(InstanceLabelResolver labels, File fontFile, int ttcIndex) {
        List<VariableInstance> instances = new ArrayList<>();

        float[] range = readAxisRangeFromFvar(fontFile, ttcIndex, AXIS_MONO);
        if (range == null) {
            return instances;
        }

        float minMono = range[0];
        float maxMono = range[2];

        if (maxMono > minMono) {
            instances.add(new VariableInstance(labels.nameOf(InstanceLabel.MONO_PROPORTIONAL), AXIS_MONO, minMono));
            instances.add(new VariableInstance(labels.nameOf(InstanceLabel.MONO_MONOSPACED), AXIS_MONO, maxMono));
        }

        return instances;
    }

    private static void addNamedInstanceIfInRange(List<VariableInstance> instances, String name,
                                                   String axisTag, float value,
                                                   float min, float max) {
        if (value >= min && value <= max) {
            instances.add(new VariableInstance(name, axisTag, value));
        }
    }
    
    
    
    private static float[] readWeightRangeFromFvar(File fontFile, int ttcIndex) {
        float[] defaultRange = {100f, 900f};

        float[] axisRange = readAxisRangeFromFvar(fontFile, ttcIndex, AXIS_WGHT);
        if (axisRange == null) {
            return defaultRange;
        }

        return new float[]{axisRange[0], axisRange[2]};
    }

    /**
     * يقرأ الحد الأدنى والقيمة الافتراضية والحد الأقصى لمحور معيّن (بالبحث عن وسمه) من جدول fvar.
     * يعيد null اذا كان هذا المحور غير موجود في الخط (اي أن الخط لا يدعمه).
     */
    private static float[] readAxisRangeFromFvar(File fontFile, int ttcIndex, String targetTag) {
        String key = fontFile.getAbsolutePath() + '|' + ttcIndex + '|'
                + fontFile.lastModified() + '|' + fontFile.length();
        Map<String, float[]> axes = FVAR_CACHE.get(key);
        if (axes == null) {
            axes = parseAllAxesFromFvar(fontFile, ttcIndex);
            if (axes == null) return null;          // فشلت القراءة: لا نخزّن
            if (FVAR_CACHE.size() >= 64) FVAR_CACHE.clear();
            FVAR_CACHE.put(key, axes);
        }
        float[] range = axes.get(targetTag);
        return range != null ? range.clone() : null;
    }

    /**
     * يقرأ جدول fvar مرة واحدة ويعيد كل محاور الخط (وسم المحور -> {min, default, max}).
     * يعيد خريطة فارغة اذا لم يكن في الخط جدول fvar، و null اذا فشلت القراءة.
     */
    private static Map<String, float[]> parseAllAxesFromFvar(File fontFile, int ttcIndex) {
        Map<String, float[]> result = new java.util.HashMap<>();
        try (RandomAccessFile raf = new RandomAccessFile(fontFile, "r")) {
            byte[] header = new byte[4];
            raf.read(header);
            long fontOffset = 0;
            if ("ttcf".equals(new String(header, "US-ASCII"))) {
                raf.seek(8);
                long numFonts = readUInt32(raf);
                if (ttcIndex >= numFonts) return null;
                raf.seek(12 + (ttcIndex * 4));
                fontOffset = readUInt32(raf);
            }
            raf.seek(fontOffset + 4);
            int numTables = readUInt16(raf);

            long fvarOffset = -1;
            for (int i = 0; i < numTables; i++) {
                raf.seek(fontOffset + 12 + i * 16);
                byte[] t = new byte[4];
                raf.read(t);
                if ("fvar".equals(new String(t, "US-ASCII"))) {
                    raf.seek(fontOffset + 12 + i * 16 + 8);
                    fvarOffset = readUInt32(raf);
                    break;
                }
            }
            if (fvarOffset == -1) return result;    // لا fvar: خريطة فارغة

            raf.seek(fvarOffset + 4);
            int axesArrayOffset = readUInt16(raf);
            raf.seek(fvarOffset + 8);
            int axisCount = readUInt16(raf);
            int axisSize = readUInt16(raf);

            for (int i = 0; i < axisCount; i++) {
                raf.seek(fvarOffset + axesArrayOffset + (i * axisSize));
                byte[] tag = new byte[4];
                raf.read(tag);
                float min = readFixed(raf), def = readFixed(raf), max = readFixed(raf);
                result.putIfAbsent(new String(tag, "US-ASCII"), new float[]{min, def, max});
            }
            return result;
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to parse fvar", e);
            return null;
        }
    }

    /**
     * يعيد القيمة الافتراضية الفعلية لمحور معيّن كما هي مسجّلة في جدول fvar الخاص بالخط،
     * أو fallback في حال تعذّرت القراءة.
     */
    public static float readAxisDefaultValue(File fontFile, int ttcIndex, String axisTag, float fallback) {
        float[] range = readAxisRangeFromFvar(fontFile, ttcIndex, axisTag);
        if (range == null) {
            return fallback;
        }
        return range[1];
    }
    
    private static int readUInt16(RandomAccessFile raf) throws Exception {
        byte[] bytes = new byte[2];
        raf.read(bytes);
        return ((bytes[0] & 0xFF) << 8) | (bytes[1] & 0xFF);
    }
    
    private static long readUInt32(RandomAccessFile raf) throws Exception {
        byte[] bytes = new byte[4];
        raf.read(bytes);
        return ((long)(bytes[0] & 0xFF) << 24) | 
               ((long)(bytes[1] & 0xFF) << 16) | 
               ((long)(bytes[2] & 0xFF) << 8) | 
               (long)(bytes[3] & 0xFF);
    }
    
    private static float readFixed(RandomAccessFile raf) throws Exception {
        byte[] bytes = new byte[4];
        raf.read(bytes);
        int value = ((bytes[0] & 0xFF) << 24) | 
                    ((bytes[1] & 0xFF) << 16) | 
                    ((bytes[2] & 0xFF) << 8) | 
                    (bytes[3] & 0xFF);
        return value / 65536.0f;
    }
    
    private static void addWeightIfInRange(List<VariableInstance> instances, 
                                          String name, float value, 
                                          float min, float max) {
        if (value >= min && value <= max) {
            instances.add(new VariableInstance(name, "wght", value));
        }
    }
}
