package com.oneui.fontviewer.utils;

public class FileUtils {
    
    public static String removeExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        
        int lastDotIndex = fileName.lastIndexOf('.');
        
        if (lastDotIndex > 0) {
            return fileName.substring(0, lastDotIndex);
        }
        
        return fileName;
    }
    
    public static String getExtension(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "";
        }
        
        int lastDotIndex = fileName.lastIndexOf('.');
        
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex + 1);
        }
        
        return "";
    }
    
    public static boolean isFontFile(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return false;
        }
        
        String extension = getExtension(fileName).toLowerCase();
        return "ttf".equals(extension) || 
               "otf".equals(extension) || 
               "ttc".equals(extension);
    }
    
    public static String getFileNameFromPath(String fullPath) {
        if (fullPath == null || fullPath.isEmpty()) {
            return "";
        }
        
        int lastSlashIndex = fullPath.lastIndexOf('/');
        if (lastSlashIndex >= 0 && lastSlashIndex < fullPath.length() - 1) {
            return fullPath.substring(lastSlashIndex + 1);
        }
        
        return fullPath;
    }
    
    public static String getFileNameWithoutExtension(String fullPath) {
        String fileName = getFileNameFromPath(fullPath);
        return removeExtension(fileName);
    }
}
