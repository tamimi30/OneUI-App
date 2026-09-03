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
            
            if (ranges.containsKey("wght")) {
                float min = ranges.get("wght")[0]; float max = ranges.get("wght")[1];
                List<VariableInstance> list = new ArrayList<>();
                addIfInRange(list, "Thin", "wght", 100, min, max);
                addIfInRange(list, "Extra Light", "wght", 200, min, max);
                addIfInRange(list, "Light", "wght", 300, min, max);
                addIfInRange(list, "Regular", "wght", 400, min, max);
                addIfInRange(list, "Medium", "wght", 500, min, max);
                addIfInRange(list, "Semi Bold", "wght", 600, min, max);
                addIfInRange(list, "Bold", "wght", 700, min, max);
                addIfInRange(list, "Extra Bold", "wght", 800, min, max);
                addIfInRange(list, "Black", "wght", 900, min, max);
                instancesMap.put("wght", list);
            }
            if (ranges.containsKey("wdth")) {
                float min = ranges.get("wdth")[0]; float max = ranges.get("wdth")[1];
                List<VariableInstance> list = new ArrayList<>();
                addIfInRange(list, "Ultra Condensed", "wdth", 50, min, max);
                addIfInRange(list, "Extra Condensed", "wdth", 62.5f, min, max);
                addIfInRange(list, "Condensed", "wdth", 75, min, max);
                addIfInRange(list, "Semi Condensed", "wdth", 87.5f, min, max);
                addIfInRange(list, "Normal Width", "wdth", 100, min, max);
                addIfInRange(list, "Semi Expanded", "wdth", 112.5f, min, max);
                addIfInRange(list, "Expanded", "wdth", 125, min, max);
                instancesMap.put("wdth", list);
            }
            if (ranges.containsKey("ital")) {
                float min = ranges.get("ital")[0]; float max = ranges.get("ital")[1];
                List<VariableInstance> list = new ArrayList<>();
                addIfInRange(list, "Upright", "ital", 0, min, max);
                addIfInRange(list, "Italic", "ital", 1, min, max);
                instancesMap.put("ital", list);
            }
            if (ranges.containsKey("grad")) {
                float min = ranges.get("grad")[0]; float max = ranges.get("grad")[1];
                List<VariableInstance> list = new ArrayList<>();
                addIfInRange(list, "Low Grade", "grad", -200, min, max);
                addIfInRange(list, "Normal Grade", "grad", 0, min, max);
                addIfInRange(list, "High Grade", "grad", 150, min, max);
                instancesMap.put("grad", list);
            }
            if (ranges.containsKey("spac")) {
                float min = ranges.get("spac")[0]; float max = ranges.get("spac")[1];
                List<VariableInstance> list = new ArrayList<>();
                addIfInRange(list, "Tight Spacing", "spac", min, min, max);
                if(0 > min && 0 < max) addIfInRange(list, "Normal Spacing", "spac", 0, min, max);
                addIfInRange(list, "Wide Spacing", "spac", max, min, max);
                instancesMap.put("spac", list);
            }
            if (ranges.containsKey("rond")) {
                float min = ranges.get("rond")[0]; float max = ranges.get("rond")[1];
                List<VariableInstance> list = new ArrayList<>();
                addIfInRange(list, "Square", "rond", 0, min, max);
                addIfInRange(list, "Round", "rond", 100, min, max);
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
                
                if ("wght".equals(axisTagStr) || "wdth".equals(axisTagStr) || "ital".equals(axisTagStr) ||
                    "grad".equals(axisTagStr) || "spac".equals(axisTagStr) || "rond".equals(axisTagStr)) {
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
