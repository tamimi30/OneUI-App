package com.oneui.fontviewer.fragment.fontviewer.manager;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;
import android.provider.DocumentsContract;

import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.UUID;

public class FontViewerStorageManager {

    private static final String TAG = "FontViewerStorageManager";
    private static final String VIEWER_FONTS_DIR = "fonts";
    private static final int BUFFER_SIZE = 8192;

    private final Context context;

    public FontViewerStorageManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public File copyFontForViewing(Uri fontUri, String suggestedFileName) {
        if (fontUri == null) {
            Log.w(TAG, "Font URI is null");
            return null;
        }

        try {
            String fileName = suggestedFileName != null ? suggestedFileName : getFileNameFromUri(fontUri);
            if (fileName == null) {
                fileName = "font_" + System.currentTimeMillis() + ".ttf";
            }

            String extension = ".ttf";
            int lastDotIndex = fileName.lastIndexOf('.');
            if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
                extension = fileName.substring(lastDotIndex);
            }

            File fontsDirectory = getFontsDirectory();
            if (fontsDirectory == null) {
                Log.e(TAG, "Failed to get or create fonts directory");
                return null;
            }

            File outputFile = new File(fontsDirectory, "viewer_font_" + UUID.randomUUID().toString() + extension);

            try (InputStream inputStream = context.getContentResolver().openInputStream(fontUri);
                 FileOutputStream outputStream = new FileOutputStream(outputFile)) {

                if (inputStream == null) {
                    Log.e(TAG, "Failed to open input stream for URI: " + fontUri);
                    return null;
                }

                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }

                outputStream.flush();
                Log.d(TAG, "Successfully copied font to: " + outputFile.getAbsolutePath());
                return outputFile;

            } catch (Exception e) {
                Log.e(TAG, "Error copying font file", e);
                if (outputFile.exists()) {
                    outputFile.delete();
                }
                return null;
            }

        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in copyFontForViewing", e);
            return null;
        }
    }

    public String getFileNameFromUri(Uri uri) {
        if (uri == null) {
            return null;
        }

        String fileName = null;

        try {
            Cursor cursor = null;
            try {
                cursor = context.getContentResolver().query(
                    uri,
                    new String[]{OpenableColumns.DISPLAY_NAME},
                    null,
                    null,
                    null
                );

                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex);
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to get filename from ContentResolver", e);
        }

        if (fileName == null) {
            try {
                DocumentFile documentFile = DocumentFile.fromSingleUri(context, uri);
                if (documentFile != null && documentFile.getName() != null) {
                    fileName = documentFile.getName();
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to get filename from DocumentFile", e);
            }
        }

        if (fileName == null) {
            String path = uri.getPath();
            if (path != null) {
                int lastSlashIndex = path.lastIndexOf('/');
                if (lastSlashIndex >= 0 && lastSlashIndex < path.length() - 1) {
                    fileName = path.substring(lastSlashIndex + 1);
                }
            }
        }

        if (fileName == null) {
            fileName = "font_" + System.currentTimeMillis() + ".ttf";
        }

        return fileName;
    }

    public File getFontsDirectory() {
        File fontsDirectory = new File(context.getFilesDir(), VIEWER_FONTS_DIR);

        if (!fontsDirectory.exists()) {
            if (!fontsDirectory.mkdirs()) {
                Log.e(TAG, "Failed to create fonts directory");
                return null;
            }
        }

        return fontsDirectory;
    }


    public String getRealPathFromUri(Uri uri) {
        if (uri == null) return null;

        String realPath = null;

        if (DocumentsContract.isDocumentUri(context, uri)) {
            String documentId = DocumentsContract.getDocumentId(uri);
            if (documentId != null && documentId.startsWith("primary:")) {
                realPath = "/storage/emulated/0/" + documentId.substring(8);
            }
        }

        if (realPath == null && "com.sec.android.app.myfiles.FileProvider".equals(uri.getAuthority())) {
            realPath = resolveSamsungMyFilesPath(uri);
        }

        if (realPath == null && "content".equalsIgnoreCase(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver().query(uri, new String[]{"_data"}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int columnIndex = cursor.getColumnIndexOrThrow("_data");
                    realPath = cursor.getString(columnIndex);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to get real path from content resolver", e);
            }
        }

        if (realPath == null) {
            realPath = uri.getPath();
        }

        if (realPath != null && new File(realPath).exists()) {
            return realPath;
        }

        return null;
    }

    private String resolveSamsungMyFilesPath(Uri uri) {
        java.util.List<String> segments = uri.getPathSegments();
        if (segments.size() < 2) return null;

        String storageType = segments.get(0);
        String storageId    = segments.get(1);

        StringBuilder relativePath = new StringBuilder();
        for (int i = 2; i < segments.size(); i++) {
            relativePath.append("/").append(segments.get(i));
        }

        String basePath;
        if ("device_storage".equals(storageType)) {
            basePath = "/storage/emulated/" + storageId;
        } else if ("external_storage".equals(storageType)) {
            basePath = "/storage/" + storageId;
        } else {
            return null;
        }

        return basePath + relativePath.toString();
    }
}
