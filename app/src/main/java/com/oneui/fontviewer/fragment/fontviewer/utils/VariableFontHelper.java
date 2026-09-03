package com.oneui.fontviewer.fragment.fontviewer.utils;

import android.graphics.Typeface;
import android.graphics.fonts.Font;
import android.graphics.fonts.FontVariationAxis;


import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

public class VariableFontHelper {
    
    private static final String TAG = "VariableFontHelper";
    
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

    // ★ معلومات محور واحد داخل الخط: الوسم كما هو مخزّن فعلياً، ومدى القيم، والاسم المعروض ★
    public static class AxisInfo {
        public final String tag;          // الوسم بالضبط كما هو داخل ملف الخط (بحروفه الكبيرة/الصغيرة الأصلية)
        public final String canonicalKey; // مفتاح موحّد: WGHT / WDTH / GRAD / ROND / ITAL / OPSZ / MONO
        public final String displayName;  // الاسم المعروض للمستخدم: Weight / Width / ...
        public final float min;
        public final float max;
        public final float defaultValue;

        public AxisInfo(String tag, String canonicalKey, String displayName, float min, float max, float defaultValue) {
            this.tag = tag;
            this.canonicalKey = canonicalKey;
            this.displayName = displayName;
            this.min = min;
            this.max = max;
            this.defaultValue = defaultValue;
        }
    }

    // ★ ترتيب عرض المحاور المدعومة في منتقي نوع المحور ★
    private static final String[] SUPPORTED_AXES_ORDER = {"WGHT", "WDTH", "GRAD", "ROND", "ITAL", "OPSZ", "MONO"};
    
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
    
    
    
    private static float[] readWeightRangeFromFvar(File fontFile, int ttcIndex) {
        float[] defaultRange = {100f, 900f};
        
        try (RandomAccessFile raf = new RandomAccessFile(fontFile, "r")) {
            byte[] header = new byte[4];
            raf.read(header);
            String tag = new String(header, "US-ASCII");
            
            long fontOffset = 0;
            
            if ("ttcf".equals(tag)) {
                raf.seek(8);
                long numFonts = readUInt32(raf);
                if (ttcIndex >= numFonts) {
                    return defaultRange;
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
                return defaultRange;
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
                
                if ("wght".equals(axisTagStr)) {
                    float minValue = readFixed(raf);
                    readFixed(raf);
                    float maxValue = readFixed(raf);
                    
                    return new float[]{minValue, maxValue};
                }
            }
            
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to read weight range from fvar", e);
        }
        
        return defaultRange;
    }

    // ★ قراءة كل المحاور الموجودة في جدول fvar للخط (وليس فقط محور الوزن) ★
    public static List<AxisInfo> readAllAxesFromFvar(File fontFile, int ttcIndex) {
        List<AxisInfo> result = new ArrayList<>();

        try (RandomAccessFile raf = new RandomAccessFile(fontFile, "r")) {
            byte[] header = new byte[4];
            raf.read(header);
            String tag = new String(header, "US-ASCII");

            long fontOffset = 0;

            if ("ttcf".equals(tag)) {
                raf.seek(8);
                long numFonts = readUInt32(raf);
                if (ttcIndex >= numFonts) {
                    return result;
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
                return result;
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

                float minValue = readFixed(raf);
                float defaultValueRaw = readFixed(raf);
                float maxValue = readFixed(raf);

                result.add(new AxisInfo(axisTagStr, axisTagStr.trim().toUpperCase(), axisTagStr, minValue, maxValue, defaultValueRaw));
            }

        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to read axes from fvar", e);
        }

        return result;
    }

    // ★ إرجاع المحاور المدعومة فقط (من بين كل المحاور الموجودة في الخط) مع اسم عرض مناسب ومرتبة ★
    public static List<AxisInfo> getSupportedAxes(File fontFile, int ttcIndex) {
        List<AxisInfo> allAxes = readAllAxesFromFvar(fontFile, ttcIndex);
        List<AxisInfo> supported = new ArrayList<>();

        for (String canonicalKey : SUPPORTED_AXES_ORDER) {
            for (AxisInfo axis : allAxes) {
                if (axis.canonicalKey.equals(canonicalKey)) {
                    supported.add(new AxisInfo(axis.tag, canonicalKey, displayNameForAxis(canonicalKey), axis.min, axis.max, axis.defaultValue));
                    break;
                }
            }
        }

        return supported;
    }

    private static String displayNameForAxis(String canonicalKey) {
        switch (canonicalKey) {
            case "WGHT": return "Weight";
            case "WDTH": return "Width";
            case "GRAD": return "Grade";
            case "ROND": return "Roundness";
            case "ITAL": return "Italic";
            case "OPSZ": return "Optical Size";
            case "MONO": return "Mono";
            default:     return canonicalKey;
        }
    }

    private static float clamp(float value, float min, float max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    // ★ بناء قائمة القيم الجاهزة الخاصة بمحور معيّن، مرتبة من الأصغر إلى الأكبر، حسب ما يدعمه الخط فعلياً ★
    public static List<VariableInstance> extractInstancesForAxis(AxisInfo axis) {
        List<VariableInstance> instances = new ArrayList<>();
        if (axis == null) return instances;

        float min = axis.min;
        float max = axis.max;
        float def = clamp(axis.defaultValue, min, max);
        String tag = axis.tag;

        switch (axis.canonicalKey) {
            case "WGHT":
                addNamedInstance(instances, "Thin", 100, min, max, tag);
                addNamedInstance(instances, "Extra Light", 200, min, max, tag);
                addNamedInstance(instances, "Light", 300, min, max, tag);
                
    
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
}
