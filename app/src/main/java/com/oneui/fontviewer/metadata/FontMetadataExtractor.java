package com.oneui.fontviewer.metadata;

import android.util.Log;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class FontMetadataExtractor {
    
    private static final String TAG = "FontMetadataExtractor";
    
    public static String extractFontName(File fontFile, int ttcIndex) {
        if (fontFile == null || !fontFile.exists()) {
            Log.w(TAG, "Font file is null or does not exist");
            return "Unknown Font";
        }
        
        try {
            String fontName = FontMetaData.extractFontName(fontFile, ttcIndex);
            
            if (fontName != null && !fontName.isEmpty() && !fontName.equals("Unknown Font")) {
                Log.d(TAG, "Successfully extracted font name: " + fontName);
                return fontName;
            }
        } catch (Exception e) {
            Log.w(TAG, "FontMetaData.extractFontName failed: " + e.getMessage());
        }
        
        Log.d(TAG, "Could not extract real name, returning Unknown Font");
        return "Unknown Font";
    }
    
    
    
    public static Map<String, String> extractMetadataWithTtcIndex(File fontFile, int ttcIndex) {
        if (fontFile == null || !fontFile.exists()) {
            Log.w(TAG, "Font file is null or does not exist");
            return new HashMap<>();
        }
        
        try {
            Map<String, String> metadata = FontMetaData.extractMetaDataWithTtcIndex(fontFile, ttcIndex);
            
            if (metadata != null && !metadata.isEmpty() && 
                (metadata.containsKey("FullName") || metadata.containsKey("Family"))) {
                Log.d(TAG, "Successfully extracted metadata with TTC index: " + ttcIndex);
                return metadata;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to extract metadata with TTC index: " + e.getMessage());
        }
        
        try {
            Map<String, String> metadata = FontMetaData.extractMetaData(fontFile);
            
            if (metadata != null && !metadata.isEmpty() && 
                (metadata.containsKey("FullName") || metadata.containsKey("Family"))) {
                Log.d(TAG, "Successfully extracted metadata using fallback method");
                return metadata;
            }
        } catch (Exception e) {
            Log.w(TAG, "FontMetaData.extractMetaData failed: " + e.getMessage());
        }
        
        Log.w(TAG, "Failed to extract metadata, returning empty map");
        return new HashMap<>();
    }
    
    
                }
