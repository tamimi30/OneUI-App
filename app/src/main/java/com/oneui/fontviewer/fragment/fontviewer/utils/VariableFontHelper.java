package com.oneui.fontviewer.fragment.fontviewer.utils;

import android.graphics.Typeface;
import android.graphics.fonts.Font;
import android.graphics.fonts.FontVariationAxis;


import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class VariableFontHelper {
    
    private static final String TAG = "VariableFontHelper";

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
    
    public static List<VariableInstance> extractVariableInstances(File fontFile, int ttcIndex) {
        List<VariableInstance> instances = new ArrayList<>();
        
        
        
        if (!isVariableFont(fontFile, ttcIndex)) {
            return instances;
        }
        
        try {
            float[] range = readWeightRangeFromFvar(fontFile, ttcIndex);
            float minWeight = range[0];
            float maxWeight = range[1];
            
            addWeightIfInRange(instances, "Thin", 100, minWeight, maxWeight);
            addWeightIfInRange(instances, "Extra Light", 200, minWeight, maxWeight);
            addWeightIfInRange(instances, "Light", 300, minWeight, maxWeight);
            addWeightIfInRange(instances, "Regular", 400, minWeight, maxWeight);
            addWeightIfInRange(instances, "Medium", 500, minWeight, maxWeight);
            addWeightIfInRange(instances, "Semi Bold", 600, minWeight, maxWeight);
            addWeightIfInRange(instances, "Bold", 700, minWeight, maxWeight);
            addWeightIfInRange(instances, "Extra Bold", 800, minWeight, maxWeight);
            addWeightIfInRange(instances, "Black", 900, minWeight, maxWeight);
            
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
    public static List<VariableInstance> extractWidthInstances(File fontFile, int ttcIndex) {
        List<VariableInstance> instances = new ArrayList<>();

        float[] range = readAxisRangeFromFvar(fontFile, ttcIndex, AXIS_WDTH);
        if (range == null) {
            return instances;
        }

        float minWidth = range[0];
        float maxWidth = range[2];

        addNamedInstanceIfInRange(instances, "Condensed", AXIS_WDTH, 75f, minWidth, maxWidth);
        addNamedInstanceIfInRange(instances, "Normal", AXIS_WDTH, 100f, minWidth, maxWidth);
        addNamedInstanceIfInRange(instances, "Expanded", AXIS_WDTH, 125f, minWidth, maxWidth);

        return instances;
    }

    /**
     * محور الميل (ital). محور ثنائي عادة: 0 = Upright، 1 = Italic.
     */
    public static List<VariableInstance> extractItalicInstances(File fontFile, int ttcIndex) {
        List<VariableInstance> instances = new ArrayList<>();

        float[] range = readAxisRangeFromFvar(fontFile, ttcIndex, AXIS_ITAL);
        if (range == null) {
            return instances;
        }

        float minItal = range[0];
        float maxItal = range[2];

        addNamedInstanceIfInRange(instances, "Upright", AXIS_ITAL, 0f, minItal, maxItal);
        addNamedInstanceIfInRange(instances, "Italic", AXIS_ITAL, 1f, minItal, maxItal);

        return instances;
    }

    /**
     * محور التدرج (GRAD). هنا نستخدم القيم الفعلية للخط: Low = الحد الأدنى الذي يدعمه الخط،
     * High = الحد الأقصى الذي يدعمه الخط، Normal = القيمة الافتراضية الفعلية المسجّلة في fvar
     * (وليست 0 دائماً، فبعض الخطوط تجعل قيمتها الافتراضية مختلفة).
     */
    public static List<VariableInstance> extractGradeInstances(File fontFile, int ttcIndex) {
        List<VariableInstance> instances = new ArrayList<>();

        float[] range = readAxisRangeFromFvar(fontFile, ttcIndex, AXIS_GRAD);
        if (range == null) {
            return instances;
        }

        float minGrade     = range[0];
        float defaultGrade = range[1];
        float maxGrade     = range[2];

        if (minGrade < defaultGrade) {
            instances.add(new VariableInstance("Low", AXIS_GRAD, minGrade));
        }
        instances.add(new VariableInstance("Normal", AXIS_GRAD, defaultGrade));
        if (maxGrade > defaultGrade) {
            instances.add(new VariableInstance("High", AXIS_GRAD, maxGrade));
        }

        return instances;
    }

    /**
     * محور الاستدارة (ROND). Sharp = الحد الأدنى الفعلي، Rounded = الحد الأقصى الفعلي للخط.
     */
    public static List<VariableInstance> extractRoundnessInstances(File fontFile, int ttcIndex) {
        List<VariableInstance> instances = new ArrayList<>();

        float[] range = readAxisRangeFromFvar(fontFile, ttcIndex, AXIS_ROND);
        if (range == null) {
            return instances;
        }

        float minRound = range[0];
        float maxRound = range[2];

        if (maxRound > minRound) {
            instances.add(new VariableInstance("Sharp", AXIS_ROND, minRound));
            instances.add(new VariableInstance("Rounded", AXIS_ROND, maxRound));
        }

        return instances;
    }

    /**
     * محور التباعد الأحادي (MONO). Proportional = الحد الأدنى (القيمة الأصغر)،
     * Monospaced = الحد الأقصى (القيمة الأكبر) — مرتبة تصاعدياً حسب القيمة كما هو مطلوب.
     */
    public static List<VariableInstance> extractMonoInstances(File fontFile, int ttcIndex) {
        List<VariableInstance> instances = new ArrayList<>();

        float[] range = readAxisRangeFromFvar(fontFile, ttcIndex, AXIS_MONO);
        if (range == null) {
            return instances;
        }

        float minMono = range[0];
        float maxMono = range[2];

        if (maxMono > minMono) {
            instances.add(new VariableInstance("Proportional", AXIS_MONO, minMono));
            instances.add(new VariableInstance("Monospaced", AXIS_MONO, maxMono));
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
        try (RandomAccessFile raf = new RandomAccessFile(fontFile, "r")) {
            byte[] header = new byte[4];
            raf.read(header);
            String tag = new String(header, "US-ASCII");
            
            long fontOffset = 0;
            
            if ("ttcf".equals(tag)) {
                raf.seek(8);
                long numFonts = readUInt32(raf);
                if (ttcIndex >= numFonts) {
                    return null;
                }
                
                raf.seek(12 + (ttcIndex * 4));
                fontOffset = readUInt32(raf);
            }
            
            raf.seek(fontOffset + 4);
            int numTables = readUInt16(raf);
            
            long fvarOffset = -1;
            for (int i = 0; i < numTables; i++) {
                raf.seek(fontOffset + 12 + i * 16);
                byte[] tableTag = new byte[4];
                raf.read(tableTag);
                String tagStr = new String(tableTag, "US-ASCII");
                
                if ("fvar".equals(tagStr)) {
                    raf.seek(fontOffset + 12 + i * 16 + 8);
                    fvarOffset = readUInt32(raf);
                    break;
                }
            }
            
            if (fvarOffset == -1) {
                return null;
            }
            
            raf.seek(fvarOffset + 4);
            int axesArrayOffset = readUInt16(raf);
            raf.seek(fvarOffset + 8);
            int axisCount = readUInt16(raf);
            int axisSize = readUInt16(raf);
            
            for (int i = 0; i < axisCount; i++) {
                long axisPos = fvarOffset + axesArrayOffset + (i * axisSize);
                raf.seek(axisPos);
                
                byte[] axisTag = new byte[4];
                raf.read(axisTag);
                String axisTagStr = new String(axisTag, "US-ASCII");
                
                if (targetTag.equals(axisTagStr)) {
                    float minValue     = readFixed(raf);
                    float defaultValue = readFixed(raf);
                    float maxValue     = readFixed(raf);
                    
                    return new float[]{minValue, defaultValue, maxValue};
                }
            }
            
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to read axis range from fvar for tag: " + targetTag, e);
        }
        
        return null;
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

    

    public static Typeface createTypefaceWithWeight(File fontFile, float weight, int ttcIndex) {
        
        
        try {
            Font.Builder fontBuilder = new Font.Builder(fontFile);
            
            if (ttcIndex > 0) {
                fontBuilder.setTtcIndex(ttcIndex);
                android.util.Log.d(TAG, "Set TTC index: " + ttcIndex);
            }
            
            if (weight > 0) {
                String variationSettings = "'wght' " + weight;
                fontBuilder.setFontVariationSettings(variationSettings);
                android.util.Log.d(TAG, "Set variation settings: " + variationSettings);
            }
            
            Font font = fontBuilder.build();
            
            Typeface.CustomFallbackBuilder fallbackBuilder = 
                new Typeface.CustomFallbackBuilder(
                    new android.graphics.fonts.FontFamily.Builder(font).build()
                );
            
            Typeface result = fallbackBuilder.build();
            android.util.Log.d(TAG, "Successfully created variable Typeface with weight: " + weight + 
                                   ", ttcIndex: " + ttcIndex);
            
            return result;
            
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to create variable Typeface with weight: " + weight + 
                                    ", ttcIndex: " + ttcIndex, e);
            
            try {
                return Typeface.createFromFile(fontFile);
            } catch (Exception ex) {
                return null;
            }
        }
    }

    /**
     * يبني نص إعدادات تنويع الخط (font-variation-settings) من خريطة قيم المحاور،
     * بالصيغة التي يتوقعها Font.Builder.setFontVariationSettings في اندرويد، مثل:
     * "'wght' 700, 'wdth' 100, 'GRAD' 50, 'ROND' 100, 'ital' 1, 'MONO' 1"
     */
    public static String buildVariationSettingsString(Map<String, Float> axisValues) {
        if (axisValues == null || axisValues.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Float> entry : axisValues.entrySet()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append("'").append(entry.getKey()).append("' ").append(entry.getValue());
        }

        return builder.toString();
    }

    /**
     * ينشئ Typeface بتطبيق عدة محاور دفعة واحدة (وزن، عرض، ميل، تدرج، استدارة، تباعد أحادي...)،
     * بنفس فلسفة createTypefaceWithWeight لكن بشكل عام يدعم أي عدد من المحاور معاً.
     */
    public static Typeface createTypefaceWithAxes(File fontFile, Map<String, Float> axisValues, int ttcIndex) {
        try {
            Font.Builder fontBuilder = new Font.Builder(fontFile);

            if (ttcIndex > 0) {
                fontBuilder.setTtcIndex(ttcIndex);
                android.util.Log.d(TAG, "Set TTC index: " + ttcIndex);
            }

            String variationSettings = buildVariationSettingsString(axisValues);
            if (!variationSettings.isEmpty()) {
                fontBuilder.setFontVariationSettings(variationSettings);
                android.util.Log.d(TAG, "Set variation settings: " + variationSettings);
            }

            Font font = fontBuilder.build();

            Typeface.CustomFallbackBuilder fallbackBuilder =
                new Typeface.CustomFallbackBuilder(
                    new android.graphics.fonts.FontFamily.Builder(font).build()
                );

            Typeface result = fallbackBuilder.build();
            android.util.Log.d(TAG, "Successfully created variable Typeface with axes: " + axisValues +
                                   ", ttcIndex: " + ttcIndex);

            return result;

        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to create variable Typeface with axes: " + axisValues +
                                    ", ttcIndex: " + ttcIndex, e);

            try {
                return Typeface.createFromFile(fontFile);
            } catch (Exception ex) {
                return null;
            }
        }
    }
                        }
