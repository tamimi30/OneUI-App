package com.oneui.fontviewer.data.source;

import java.io.File;

import com.oneui.fontviewer.data.model.FontRenameResult;

public class FontFileManager {

    public static FontRenameResult renameFile(String oldPath, String newFileName) {
        File oldFile = new File(oldPath);
        
        if (!oldFile.exists()) {
            return FontRenameResult.failure("الملف غير موجود");
        }
        
        File parentDir = oldFile.getParentFile();
        if (parentDir == null) {
            return FontRenameResult.failure("خطأ في المسار");
        }
        
        File newFile = new File(parentDir, newFileName);
        
        if (newFile.exists()) {
            return FontRenameResult.failure("الاسم موجود بالفعل");
        }
        
        if (!oldFile.renameTo(newFile)) {
            return FontRenameResult.failure("فشلت إعادة التسمية");
        }
        
        return FontRenameResult.success(newFile.getAbsolutePath(), newFile.lastModified());
    }
}
