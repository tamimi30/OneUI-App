package com.oneui.fontviewer.data.source;

import android.os.Environment;
import android.util.Log;

import com.oneui.fontviewer.data.model.FontFileInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class LocalFontDirectory {

    private static final String TAG = "LocalFontDirectory";

    public static List<FontFileInfo> getFontsInDirectory(String directoryPath) {
        List<FontFileInfo> fontFiles = new ArrayList<>();

        if (directoryPath == null || directoryPath.isEmpty()) {
            Log.w(TAG, "Directory path is null or empty");
            return fontFiles;
        }

        try {
            File directory = new File(directoryPath);

            if (!directory.exists()) {
                Log.w(TAG, "Directory does not exist: " + directoryPath);
                return fontFiles;
            }

            if (!directory.isDirectory()) {
                Log.w(TAG, "Path is not a directory: " + directoryPath);
                return fontFiles;
            }

            File[] files = directory.listFiles();

            if (files == null) {
                Log.w(TAG, "Cannot list files in directory (permission denied?): " + directoryPath);
                return fontFiles;
            }

            for (File file : files) {
                if (file.isFile() && file.canRead()) {
                    String name      = file.getName();
                    String nameLower = name.toLowerCase();

                    if (nameLower.endsWith(".ttf") || nameLower.endsWith(".otf") || nameLower.endsWith(".ttc")) {
                        fontFiles.add(new FontFileInfo(
                            name,
                            file.getAbsolutePath(),
                            file.length(),
                            file.lastModified()
                        ));
                    }
                }
            }

            Log.d(TAG, "Found " + fontFiles.size() + " font files in: " + directoryPath);

        } catch (SecurityException e) {
            Log.e(TAG, "Security exception reading directory: " + directoryPath, e);
        } catch (Exception e) {
            Log.e(TAG, "Error reading directory: " + directoryPath, e);
        }

        return fontFiles;
    }

    

    public static boolean isAccessible(String folderPath) {
        File directory = new File(folderPath);
        return directory.isDirectory() && directory.list() != null;
    }

    public static String getDownloadsDirectoryPath() {
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (downloadsDir != null && downloadsDir.exists()) {
            return downloadsDir.getAbsolutePath();
        }
        return null;
    }

    public static String getRealPathFromTreeUri(android.net.Uri treeUri) {
        if (treeUri == null) {
            return null;
        }
        
        try {
            String uriPath = treeUri.toString();
            
            if (uriPath.contains("primary:")) {
                String relativePath = uriPath.substring(uriPath.indexOf("primary:") + 8);
                return Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + relativePath;
            }
            
            if (uriPath.contains("/storage/")) {
                int storageIndex = uriPath.indexOf("/storage/");
                String potentialPath = uriPath.substring(storageIndex);
                
                if (potentialPath.contains("%3A")) {
                    potentialPath = potentialPath.replace("%3A", "/");
                }
                if (potentialPath.contains(":")) {
                    potentialPath = potentialPath.replace(":", "/");
                }
                
                if (potentialPath.startsWith("/storage/emulated/0/")) {
                    return potentialPath;
                } else if (potentialPath.contains("/storage/")) {
                    return potentialPath.substring(potentialPath.indexOf("/storage/"));
                }
            }
            
            String documentId = android.provider.DocumentsContract.getTreeDocumentId(treeUri);
            if (documentId != null) {
                if (documentId.startsWith("primary:")) {
                    String path = documentId.substring(8);
                    return Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + path;
                } else if (documentId.contains(":")) {
                    String[] parts = documentId.split(":");
                    if (parts.length > 1) {
                        String basePath = "/storage/" + parts[0];
                        File baseDir = new File(basePath);
                        if (baseDir.exists()) {
                            return basePath + "/" + parts[1];
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error converting URI to path", e);
        }
        
        Log.w(TAG, "Could not determine real path, using fallback");
        return null;
    }
}
