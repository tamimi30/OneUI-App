package com.oneui.fontviewer.fragment.localfont.fontdirectory;

import android.util.Log;

import com.oneui.fontviewer.data.entity.FontFileInfo;

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

    
}
