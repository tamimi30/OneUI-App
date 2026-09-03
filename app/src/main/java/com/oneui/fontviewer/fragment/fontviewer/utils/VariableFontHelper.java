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
    
    private static String findKeyIgnoreCase(java.util.Map<String, float[]> map, String target) {
        for (String key : map.keySet()) {
            if (key.equalsIgnoreCase(target)) return key;
        }
        return null;
    }
    
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
    
    public static java.util.Map<String, List<VariableInstance>> extractAllVariableInstances(File fontFile, int ttcIndex) {
        java.util.Map<String, List<VariableInstance>> instancesMap = new java.util.HashMap<>();
        
        if (!isVariableFont(fontFile, ttcIndex)) {
            return instancesMap;
        }
        
        try {
            java.util.Map<String, float[]> ranges = readAllAxesRangesFromFvar(fontFile, ttcIndex);
            
            String wghtTag = findKeyIgnoreCase(ranges, "wght");
            if (wghtTag != null) {
                float min = ranges.get(wghtTag)[0]; float max = ranges.get(wghtTag)[1];
                List<VariableInstance> list = new ArrayList<>();
                addIfInRange(list, "Thin", wghtTag, 100, min, max);
                addIfInRange(list, "Extra Light", wghtTag, 200, min, max);
                addIfInRange(list, "Light", wghtTag, 300, min, max);
                addIfInRange(list, "Regular", wghtTag, 400, min, max);
                addIfInRange(list, "Medium", wghtTag, 500, min, max);
                addIfInRange(list, "Semi Bold", wghtTag, 600, min, max);
                addIfInRange(list, "Bold", wghtTag, 700, min, max);
                addIfInRange(list, "Extra Bold", wghtTag, 800, min, max);
                addIfInRange(list, "Black", wghtTag, 900, min, max);
                instancesMap.put("wght", list);
            }
            String wdthTag = findKeyIgnoreCase(ranges, "wdth");
            if (wdthTag != null) {
                float min = ranges.get(wdthTag)[0]; float max = ranges.get(wdthTag)[1];
                List<VariableInstance> list = new ArrayList<>();
                addIfInRange(list, "Ultra Condensed", wdthTag, 50, min, max);
                addIfInRange(list, "Extra Condensed", wdthTag, 62.5f, min, max);
                addIfInRange(list, "Condensed", wdthTag, 75, min, max);
                addIfInRange(list, "Semi Condensed", wdthTag, 87.5f, min, max);
                addIfInRange(list, "Normal Width", wdthTag, 100, min, max);
                addIfInRange(list, "Semi Expanded", wdthTag, 112.5f, min, max);
                addIfInRange(list, "Expanded", wdthTag, 125, min, max);
                instancesMap.put("wdth", list);
            }
            String italTag = findKeyIgnoreCase(ranges, "ital");
            if (italTag != null) {
                float min = ranges.get(italTag)[0]; float max = ranges.get(italTag)[1];
                List<VariableInstance> list = new ArrayList<>();
                addIfInRange(list, "Upright", italTag, 0, min, max);
                addIfInRange(list, "Italic", italTag, 1, min, max);
                instancesMap.put("ital", list);
            }
            String gradTag = findKeyIgnoreCase(ranges, "grad");
            if (gradTag != null) {
                float min = ranges.get(gradTag)[0]; float max = ranges.get(gradTag)[1];
                List<VariableInstance> list = new ArrayList<>();
                addIfInRange(list, "Low Grade", gradTag, -200, min, max);
                addIfInRange(list, "Normal Grade", gradTag, 0, min, max);
                addIfInRange(list, "High Grade", gradTag, 150, min, max);
                instancesMap.put("grad", list);
            }
            String spacTag = findKeyIgnoreCase(ranges, "spac");
            if (spacTag != null) {
                float min = ranges.get(spacTag)[0]; float max = ranges.get(spacTag)[1];
                List<VariableInstance> list = new ArrayList<>();
                addIfInRange(list, "Tight Spacing", spacTag, min, min, max);
                if(0 > min && 0 < max) addIfInRange(list, "Normal Spacing", spacTag, 0, min, max);
                addIfInRange(list, "Wide Spacing", spacTag, max, min, max);
                instancesMap.put("spac", list);
            }
            String rondTag = findKeyIgnoreCase(ranges, "rond");
            if (rondTag != null) {
                float min = ranges.get(rondTag)[0]; float max = ranges.get(rondTag)[1];
                List<VariableInstance> list = new ArrayList<>();
                addIfInRange(list, "Square", rondTag, 0, min, max);
                addIfInRange(list, "Round", rondTag, 100, min, max);
                instancesMap.put("rond", list);
            }
            
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to extract variable axes instances", e);
        }
        
        return instancesMap;
    }
    
    private static java.util.Map<String, float[]> readAllAxesRangesFromFvar(File fontFile, int ttcIndex) {
        java.util.Map<String, float[]> axesRanges = new java.util.HashMap<>();
        
        try (RandomAccessFile raf = new RandomAccessFile(fontFile, "r")) {
            byte[] header = new byte[4];
            raf.read(header);
            String tag = new String(header, "US-ASCII");
            
            long fontOffset = 0;
            
            if ("ttcf".equals(tag)) {
                raf.seek(8);
                long numFonts = readUInt32(raf);
                if (ttcIndex >= numFonts) return axesRanges;
                
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
            
            if (fvarOffset == -1) return axesRanges;
            
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
                String lowerTag = axisTagStr.toLowerCase(java.util.Locale.US);
                
                if ("wght".equals(lowerTag) || "wdth".equals(lowerTag) || "ital".equals(lowerTag) ||
                    "grad".equals(lowerTag) || "spac".equals(lowerTag) || "rond".equals(lowerTag)) {
                    float minValue = readFixed(raf);
                    readFixed(raf); // default
                    float maxValue = readFixed(raf);
                    axesRanges.put(axisTagStr, new float[]{minValue, maxValue});
                }
            }
            
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to read axes ranges from fvar", e);
        }
        return axesRanges;
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
    
    private static void addIfInRange(List<VariableInstance> instances, 
                                     String name, String tag, float value, 
                                     float min, float max) {
        if (value >= min && value <= max) {
            instances.add(new VariableInstance(name, tag, value));
        }
    }

    public static Typeface createTypefaceWithSettings(File fontFile, String variationSettings, int ttcIndex) {
        try {
            Font.Builder fontBuilder = new Font.Builder(fontFile);
            
            if (ttcIndex > 0) {
                fontBuilder.setTtcIndex(ttcIndex);
            }
            
            if (variationSettings != null && !variationSettings.isEmpty()) {
                fontBuilder.setFontVariationSettings(variationSettings);
                android.util.Log.d(TAG, "Set variation settings: " + variationSettings);
            }
            
            Font font = fontBuilder.build();
            
            Typeface.CustomFallbackBuilder fallbackBuilder = 
                new Typeface.CustomFallbackBuilder(
                    new android.graphics.fonts.FontFamily.Builder(font).build()
                );
            
            return fallbackBuilder.build();
            
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to create variable Typeface with settings: " + variationSettings, e);
            try {
                return Typeface.createFromFile(fontFile);
            } catch (Exception ex) {
                return null;
            }
        }
    }
}
