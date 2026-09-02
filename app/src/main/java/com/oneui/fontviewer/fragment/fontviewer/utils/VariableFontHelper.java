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
        public float weight;
        public float width;
        public String variationSettings;
        
        public VariableInstance(String name, float weight, float width) {
            this.name = name;
            this.weight = weight;
            this.width = width;
            this.variationSettings = "'wght' " + weight + (width > 0 ? ", 'wdth' " + width : "");
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
            float[][] ranges = readAxesRangesFromFvar(fontFile, ttcIndex);
            float minWeight = ranges[0][0];
            float maxWeight = ranges[0][1];
            float minWidth = ranges[1][0];
            float maxWidth = ranges[1][1];
            
            float normalWidth = Math.max(minWidth, Math.min(100f, maxWidth));
            
            addCombinations(instances, "", minWeight, maxWeight, normalWidth);
            
            if (minWidth <= 75f && maxWidth >= 75f && minWidth != maxWidth) {
                addCombinations(instances, "Condensed ", minWeight, maxWeight, 75f);
            } else if (minWidth < normalWidth && minWidth != maxWidth) {
                addCombinations(instances, "Condensed ", minWeight, maxWeight, minWidth);
            }
            
            if (maxWidth >= 125f && minWidth != maxWidth) {
                addCombinations(instances, "Expanded ", minWeight, maxWeight, 125f);
            } else if (maxWidth > normalWidth && minWidth != maxWidth) {
                addCombinations(instances, "Expanded ", minWeight, maxWeight, maxWidth);
            }
            
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to extract instances", e);
        }
        
        return instances;
    }
    
    private static float[][] readAxesRangesFromFvar(File fontFile, int ttcIndex) {
        float[][] ranges = {{100f, 900f}, {100f, 100f}};
        
        try (RandomAccessFile raf = new RandomAccessFile(fontFile, "r")) {
            byte[] header = new byte[4];
            raf.read(header);
            String tag = new String(header, "US-ASCII");
            
            long fontOffset = 0;
            
            if ("ttcf".equals(tag)) {
                raf.seek(8);
                long numFonts = readUInt32(raf);
                if (ttcIndex >= numFonts) {
                    return ranges;
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
                return ranges;
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
                    ranges[0][0] = readFixed(raf);
                    readFixed(raf);
                    ranges[0][1] = readFixed(raf);
                } else if ("wdth".equals(axisTagStr)) {
                    ranges[1][0] = readFixed(raf);
                    readFixed(raf);
                    ranges[1][1] = readFixed(raf);
                }
            }
            
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to read axes ranges from fvar", e);
        }
        
        return ranges;
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
    
    private static void addCombinations(List<VariableInstance> instances, String prefix, float minWght, float maxWght, float wdth) {
        addIfInRange(instances, prefix + "Thin", 100, minWght, maxWght, wdth);
        addIfInRange(instances, prefix + "Extra Light", 200, minWght, maxWght, wdth);
        addIfInRange(instances, prefix + "Light", 300, minWght, maxWght, wdth);
        addIfInRange(instances, prefix + "Regular", 400, minWght, maxWght, wdth);
        addIfInRange(instances, prefix + "Medium", 500, minWght, maxWght, wdth);
        addIfInRange(instances, prefix + "Semi Bold", 600, minWght, maxWght, wdth);
        addIfInRange(instances, prefix + "Bold", 700, minWght, maxWght, wdth);
        addIfInRange(instances, prefix + "Extra Bold", 800, minWght, maxWght, wdth);
        addIfInRange(instances, prefix + "Black", 900, minWght, maxWght, wdth);
    }

    private static void addIfInRange(List<VariableInstance> instances, String name, float wght, float minWght, float maxWght, float wdth) {
        if (wght >= minWght && wght <= maxWght) {
            instances.add(new VariableInstance(name, wght, wdth));
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
            }
            
            Font font = fontBuilder.build();
            
            Typeface.CustomFallbackBuilder fallbackBuilder = 
                new Typeface.CustomFallbackBuilder(
                    new android.graphics.fonts.FontFamily.Builder(font).build()
                );
            
            return fallbackBuilder.build();
            
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to create variable Typeface", e);
            try {
                return Typeface.createFromFile(fontFile);
            } catch (Exception ex) {
                return null;
            }
        }
    }
}
