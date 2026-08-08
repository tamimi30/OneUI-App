package com.oneui.fontviewer.fragment.systemfont.manager;

import android.graphics.fonts.Font;
import android.graphics.fonts.SystemFonts;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.oneui.fontviewer.fragment.systemfont.data.SystemFontInfo;

public class SystemFontManager {
    
    private static final String TAG = "SystemFontManager";
    private static SystemFontManager instance;
    
    private SystemFontManager() {
    }
    
    public static synchronized SystemFontManager getInstance() {
        if (instance == null) {
            instance = new SystemFontManager();
        }
        return instance;
    }
    
    @RequiresApi(api = Build.VERSION_CODES.Q)
    public List<SystemFontInfo> getSystemFonts() {
        if (!isSystemFontsAvailable()) {
            Log.w(TAG, "SystemFonts API not available on this Android version");
            return new ArrayList<>();
        }
        
        List<SystemFontInfo> fontInfoList = new ArrayList<>();
        Set<String> processedPaths = new HashSet<>();
        
        try {
            Set<Font> availableFonts = SystemFonts.getAvailableFonts();
            
            if (availableFonts == null || availableFonts.isEmpty()) {
                Log.w(TAG, "No system fonts available");
                return fontInfoList;
            }
            
            Log.d(TAG, "Found " + availableFonts.size() + " system fonts");
            
            for (Font font : availableFonts) {
                try {
                    File fontFile = font.getFile();
                    
                    if (fontFile == null || !fontFile.exists()) {
                        continue;
                    }
                    
                    String fontPath = fontFile.getAbsolutePath();
                    
                    if (processedPaths.contains(fontPath)) {
                        continue;
                    }
                    
                    processedPaths.add(fontPath);
                    
                    String fontName = fontFile.getName();
                    long fileSize = fontFile.length();
                    long lastModified = fontFile.lastModified();
                    
                    int weight = 400;
                    int slant = 0;
                    int ttcIndex = 0;
                    String axes = null;
                    
                    try {
                        weight = font.getStyle().getWeight();
                        slant = font.getStyle().getSlant();
                        ttcIndex = font.getTtcIndex();
                        axes = extractAxesInfo(font);
                    } catch (Exception e) {
                        Log.w(TAG, "Could not extract font style info for: " + fontName);
                    }
                    
                    SystemFontInfo fontInfo = new SystemFontInfo(
                        fontName,
                        fontPath,
                        fileSize,
                        lastModified,
                        weight,
                        slant,
                        ttcIndex,
                        axes
                    );
                    
                    fontInfoList.add(fontInfo);
                    
                } catch (Exception e) {
                    Log.w(TAG, "Error processing font: " + e.getMessage());
                }
            }
            
            Collections.sort(fontInfoList, (f1, f2) -> 
                f1.getName().compareToIgnoreCase(f2.getName()));
            
            Log.d(TAG, "Successfully processed " + fontInfoList.size() + " unique system fonts");
            
        } catch (Exception e) {
            Log.e(TAG, "Error getting system fonts", e);
        }
        
        return fontInfoList;
    }
    
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private String extractAxesInfo(Font font) {
        try {
            android.graphics.fonts.FontVariationAxis[] axes = font.getAxes();
            if (axes != null && axes.length > 0) {
                StringBuilder axesInfo = new StringBuilder();
                for (int i = 0; i < axes.length; i++) {
                    if (i > 0) axesInfo.append(", ");
                    axesInfo.append(axes[i].getTag());
                }
                return axesInfo.toString();
            }
        } catch (Exception e) {
            Log.w(TAG, "Error extracting axes info: " + e.getMessage());
        }
        return null;
    }
}
