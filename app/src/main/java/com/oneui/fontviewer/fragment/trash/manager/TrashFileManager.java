package com.oneui.fontviewer.fragment.trash.manager;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.oneui.fontviewer.utils.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

public class TrashFileManager {

    private static final String TAG = "TrashFileManager";

    private static final String TRASH_DIR_NAME = ".Trash";

    private static final String FALLBACK_RESTORE_DIR_NAME = "RestoredFonts";


    @Nullable
    public static File getTrashDirectory(@NonNull Context context) {
        File trashDir = new File(context.getFilesDir(), TRASH_DIR_NAME);

        if (!trashDir.exists()) {
            boolean created = trashDir.mkdirs();
            if (!created) {
                Log.e(TAG, "Failed to create .Trash directory at: " + trashDir.getAbsolutePath());
                return null;
            }
            Log.d(TAG, ".Trash directory created at: " + trashDir.getAbsolutePath());
        }

        return trashDir;
    }


    @Nullable
    public static String moveToTrash(@NonNull Context context, @NonNull String originalPath) {
        File sourceFile = new File(originalPath);

        if (!sourceFile.exists()) {
            Log.w(TAG, "Source file does not exist: " + originalPath);
            return null;
        }
        if (!sourceFile.canRead()) {
            Log.w(TAG, "Source file is not readable: " + originalPath);
            return null;
        }

        File trashDir = getTrashDirectory(context);
        if (trashDir == null) {
            Log.e(TAG, "Cannot access or create .Trash directory");
            return null;
        }

        File destinationFile = resolveNamingConflict(new File(trashDir, sourceFile.getName()));

        boolean copied = copyFile(sourceFile, destinationFile);
        if (!copied) {
            Log.e(TAG, "Failed to copy file to .Trash: " + originalPath);
            return null;
        }

        boolean deleted = sourceFile.delete();
        if (!deleted) {
            destinationFile.delete();
            Log.e(TAG, "Failed to delete original file after copy, rolled back: " + originalPath);
            return null;
        }

        Log.d(TAG, "Moved to trash: " + originalPath + " → " + destinationFile.getAbsolutePath());
        return destinationFile.getAbsolutePath();
    }


    @Nullable
    public static String restoreFromTrash(@NonNull Context context,
                                          @NonNull String trashedPath,
                                          @NonNull String originalPath) {
        File trashedFile = new File(trashedPath);

        if (!trashedFile.exists()) {
            Log.w(TAG, "Trashed file does not exist: " + trashedPath);
            return null;
        }

        File destinationFile = new File(originalPath);
        File destinationDir  = destinationFile.getParentFile();

        if (destinationDir != null && !destinationDir.exists()) {
            boolean dirCreated = destinationDir.mkdirs();
            if (!dirCreated) {
                Log.w(TAG, "Cannot create destination directory: "
                        + destinationDir.getAbsolutePath()
                        + " — restoring to fallback location instead");
                return restoreToFallbackLocation(context, trashedFile);
            }
        }

        File resolvedDestination = resolveNamingConflict(destinationFile);

        boolean copied = copyFile(trashedFile, resolvedDestination);
        if (!copied) {
            Log.e(TAG, "Failed to restore file: "
                    + trashedPath + " → " + resolvedDestination.getAbsolutePath());
            return null;
        }

        boolean deleted = trashedFile.delete();
        if (!deleted) {
            Log.w(TAG, "Restored successfully but failed to delete from .Trash: " + trashedPath);
        }

        Log.d(TAG, "Restored from trash: " + trashedPath + " → " + resolvedDestination.getAbsolutePath());
        return resolvedDestination.getAbsolutePath();
    }


    public static boolean deletePermanently(@NonNull String trashedPath) {
        File file = new File(trashedPath);

        if (!file.exists()) {
            Log.w(TAG, "File to permanently delete does not exist (already gone?): " + trashedPath);
            return true;
        }

        boolean deleted = file.delete();

        if (deleted) {
            Log.d(TAG, "Permanently deleted: " + trashedPath);
        } else {
            Log.e(TAG, "Failed to permanently delete: " + trashedPath);
        }

        return deleted;
    }


    @NonNull
    private static File resolveNamingConflict(@NonNull File targetFile) {
        if (!targetFile.exists()) {
            return targetFile;
        }

        File   parentDir          = targetFile.getParentFile();
        String fileName           = targetFile.getName();
        String nameWithoutExt     = FileUtils.removeExtension(fileName);
        String extension          = FileUtils.getExtension(fileName);
        String dotExtension       = extension.isEmpty() ? "" : "." + extension;

        int  counter   = 1;
        File candidate;
        do {
            String newName = nameWithoutExt + "(" + counter + ")" + dotExtension;
            candidate = new File(parentDir, newName);
            counter++;
        } while (candidate.exists());

        Log.d(TAG, "Naming conflict resolved: " + fileName + " → " + candidate.getName());
        return candidate;
    }

    private static boolean copyFile(@NonNull File source, @NonNull File destination) {
        try (FileInputStream  fis           = new FileInputStream(source);
             FileOutputStream fos           = new FileOutputStream(destination);
             FileChannel      inputChannel  = fis.getChannel();
             FileChannel      outputChannel = fos.getChannel()) {

            outputChannel.transferFrom(inputChannel, 0, inputChannel.size());
            return true;

        } catch (IOException e) {
            Log.e(TAG, "Failed to copy file: "
                    + source.getAbsolutePath() + " → " + destination.getAbsolutePath(), e);
            if (destination.exists()) {
                destination.delete();
            }
            return false;
        }
    }

    @Nullable
    private static String restoreToFallbackLocation(@NonNull Context context,
                                                     @NonNull File trashedFile) {
        File fallbackDir = new File(context.getFilesDir(), FALLBACK_RESTORE_DIR_NAME);
        if (!fallbackDir.exists()) {
            fallbackDir.mkdirs();
        }

        File destinationFile = resolveNamingConflict(new File(fallbackDir, trashedFile.getName()));
        boolean copied = copyFile(trashedFile, destinationFile);

        if (copied) {
            trashedFile.delete();
            Log.w(TAG, "Restored to fallback location: " + destinationFile.getAbsolutePath());
            return destinationFile.getAbsolutePath();
        }

        Log.e(TAG, "Failed to restore even to fallback location for: " + trashedFile.getAbsolutePath());
        return null;
    }
}
