package com.oneui.fontviewer.utils;

import java.io.File;

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
    
    public static String getFileName(String path) {
        return new File(path).getName();
    }
    
    public static String getParentPath(String path) {
        return new File(path).getParent();
    }
    
    public static boolean exists(String parentPath, String fileName) {
        return new File(parentPath, fileName).exists();
    }
    
    public static boolean isValidFileName(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        String[] forbiddenChars = {"/", "\\", ":", "*", "?", "\"", "<", ">", "|"};
        for (String forbidden : forbiddenChars) {
            if (name.contains(forbidden)) return false;
        }
        return true;
    }
    
}
