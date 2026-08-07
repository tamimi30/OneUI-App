package com.oneui.fontviewer.fragment.localfont.data;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import androidx.lifecycle.LiveData;

import com.oneui.fontviewer.data.dao.FontDao;
import com.oneui.fontviewer.data.database.AppDatabase;
import com.oneui.fontviewer.data.entity.FontEntity;
import com.oneui.fontviewer.data.entity.FontFileInfo;
import com.oneui.fontviewer.fragment.localfont.fontdirectory.LocalFontDirectory;
import com.oneui.fontviewer.metadata.FontMetadataExtractor;
import com.oneui.fontviewer.metadata.FontWeightWidthExtractor;

public class LocalFontRepository {

    private static final String TAG = "LocalFontRepository";
    private static volatile LocalFontRepository INSTANCE;

    private final FontDao fontDao;
    private final ExecutorService executorService;
    private final AppDatabase database;

    public enum SortType {
        NAME,
        DATE,
        SIZE
    }

    private LocalFontRepository(Context context) {
        database = AppDatabase.getInstance(context);
        fontDao = database.fontDao();
        executorService = AppDatabase.databaseWriteExecutor;
    }

    public static LocalFontRepository getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (LocalFontRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new LocalFontRepository(context.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }

    public LiveData<List<FontEntity>> getAllFonts() {
        return fontDao.getAllFonts();
    }

    public LiveData<List<FontEntity>> getLocalFonts() {
        return fontDao.getLocalFonts();
    }

    public LiveData<FontEntity> getFontByPath(String path) {
        return fontDao.getFontByPath(path);
    }

    public LiveData<Integer> getTotalFontsCount() {
        return fontDao.getTotalFontsCount();
    }

    public LiveData<Integer> getLocalFontsCount() {
        return fontDao.getLocalFontsCount();
    }

    public LiveData<List<FontEntity>> getFontsSortedByName(boolean isSystem, boolean ascending) {
        if (ascending) {
            return fontDao.getFontsSortedByName(isSystem);
        } else {
            return fontDao.getFontsSortedByNameDesc(isSystem);
        }
    }

    public LiveData<List<FontEntity>> getFontsSortedByDate(boolean isSystem, boolean ascending) {
        if (ascending) {
            return fontDao.getFontsSortedByDate(isSystem);
        } else {
            return fontDao.getFontsSortedByDateDesc(isSystem);
        }
    }

    public LiveData<List<FontEntity>> getFontsSortedBySize(boolean isSystem, boolean ascending) {
        if (ascending) {
            return fontDao.getFontsSortedBySize(isSystem);
        } else {
            return fontDao.getFontsSortedBySizeDesc(isSystem);
        }
    }

    public LiveData<List<FontEntity>> searchFonts(boolean isSystem, String query) {
        return fontDao.searchFonts(isSystem, query);
    }


    public LiveData<List<FontEntity>> getFavoriteFonts() {
        return fontDao.getFavoriteFonts();
    }

    public LiveData<Integer> getFavoriteFontsCount() {
        return fontDao.getFavoriteFontsCount();
    }

    public LiveData<List<FontEntity>> getFavoritesSortedByName(boolean ascending) {
        if (ascending) {
            return fontDao.getFavoriteFontsSortedByName();
        } else {
            return fontDao.getFavoriteFontsSortedByNameDesc();
        }
    }

    public LiveData<List<FontEntity>> getFavoritesSortedByDate(boolean ascending) {
        if (ascending) {
            return fontDao.getFavoriteFontsSortedByDate();
        } else {
            return fontDao.getFavoriteFontsSortedByDateDesc();
        }
    }

    public LiveData<List<FontEntity>> getFavoritesSortedBySize(boolean ascending) {
        if (ascending) {
            return fontDao.getFavoriteFontsSortedBySize();
        } else {
            return fontDao.getFavoriteFontsSortedBySizeDesc();
        }
    }

    public LiveData<List<FontEntity>> searchFavoriteFonts(String query) {
        return fontDao.searchFavoriteFonts(query);
    }

    public void updateFavoriteStatus(String path, boolean isFavorite, OnCompleteListener listener) {
        executorService.execute(() -> {
            try {
                int rows = fontDao.updateFavoriteStatus(path, isFavorite, System.currentTimeMillis());
                Log.d(TAG, "★ Favorite status updated: " + path + " → " + isFavorite);
                if (listener != null) {
                    listener.onComplete(rows > 0);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to update favorite status", e);
                if (listener != null) {
                    listener.onComplete(false);
                }
            }
        });
    }

    public void updateFavoriteStatusBatch(List<String> paths, boolean isFavorite,
                                          OnCompleteListener listener) {
        executorService.execute(() -> {
            try {
                long now = System.currentTimeMillis();
                    final int[] totalRows = {0};

                    database.runInTransaction(() -> {
                        for (String path : paths) {
                            totalRows[0] += fontDao.updateFavoriteStatus(path, isFavorite, now);
                        }
                    });

                    Log.d(TAG, "★ Batch favorite status updated: " + paths.size()
                        + " fonts → " + isFavorite);
                    if (listener != null) {
                        listener.onComplete(totalRows[0] > 0);
                    }

            } catch (Exception e) {
                Log.e(TAG, "Failed to batch update favorite status", e);
                if (listener != null) {
                    listener.onComplete(false);
                }
            }
        });
    }


    public void insert(FontEntity font, OnCompleteListener listener) {
        executorService.execute(() -> {
            try {
                long id = fontDao.insert(font);
                if (listener != null) {
                    listener.onComplete(id > 0);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to insert font", e);
                if (listener != null) {
                    listener.onComplete(false);
                }
            }
        });
    }

    public void insertAll(List<FontEntity> fonts, OnCompleteListener listener) {
        executorService.execute(() -> {
            try {
                List<Long> ids = fontDao.insertAll(fonts);
                if (listener != null) {
                    listener.onComplete(ids != null && ids.size() == fonts.size());
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to insert fonts", e);
                if (listener != null) {
                    listener.onComplete(false);
                }
            }
        });
    }

    public void update(FontEntity font, OnCompleteListener listener) {
        executorService.execute(() -> {
            try {
                int rows = fontDao.update(font);
                if (listener != null) {
                    listener.onComplete(rows > 0);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to update font", e);
                if (listener != null) {
                    listener.onComplete(false);
                }
            }
        });
    }

    public void delete(FontEntity font, OnCompleteListener listener) {
        executorService.execute(() -> {
            try {
                int rows = fontDao.delete(font);
                if (listener != null) {
                    listener.onComplete(rows > 0);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to delete font", e);
                if (listener != null) {
                    listener.onComplete(false);
                }
            }
        });
    }

    public void deleteByPath(String path, OnCompleteListener listener) {
        executorService.execute(() -> {
            try {
                int rows = fontDao.deleteByPath(path);
                if (listener != null) {
                    listener.onComplete(rows > 0);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to delete font by path", e);
                if (listener != null) {
                    listener.onComplete(false);
                }
            }
        });
    }

    public void updateCacheStatus(String path, boolean isCached) {
        executorService.execute(() -> {
            try {
                fontDao.updateCacheStatus(path, isCached, System.currentTimeMillis());
            } catch (Exception e) {
                Log.e(TAG, "Failed to update cache status", e);
            }
        });
    }

    public void recordAccess(String path) {
        executorService.execute(() -> {
            try {
                long now = System.currentTimeMillis();
                fontDao.recordAccess(path, now, now);
            } catch (Exception e) {
                Log.e(TAG, "Failed to record access", e);
            }
        });
    }

    public void updateRealName(String path, String realName) {
        executorService.execute(() -> {
            try {
                fontDao.updateRealName(path, realName, System.currentTimeMillis());
                Log.d(TAG, "Updated real name for: " + path + " = " + realName);
            } catch (Exception e) {
                Log.e(TAG, "Failed to update real name", e);
            }
        });
    }

    public void updatePath(String oldPath, String newPath, String newFileName) {
        executorService.execute(() -> {
            try {
                fontDao.updatePath(oldPath, newPath, newFileName, System.currentTimeMillis());
                Log.d(TAG, "Updated font path: " + oldPath + " -> " + newPath);
            } catch (Exception e) {
                Log.e(TAG, "Failed to update path", e);
            }
        });
    }

    public void loadAndSyncLocalFonts(String folderPath, OnSyncCompleteListener listener) {
        executorService.execute(() -> {
            try {
                List<FontFileInfo> filesInFolder =
                    LocalFontDirectory.getFontsInDirectory(folderPath);

                if (filesInFolder == null || filesInFolder.isEmpty()) {
                    List<FontEntity> existingFonts = fontDao.getLocalFontsSync();
                    int deletedCount = 0;
                    if (existingFonts != null && !existingFonts.isEmpty()) {
                        for (FontEntity existing : existingFonts) {
                            fontDao.delete(existing);
                            deletedCount++;
                        }
                    }

                    if (listener != null) {
                        listener.onSyncComplete(0, 0, deletedCount);
                        listener.onFullExtractionComplete();
                    }
                    return;
                }

                List<FontEntity> existingFonts = fontDao.getLocalFontsSync();
                List<FontEntity> fontsToAdd = new ArrayList<>();
                int updated = 0;
                int deleted = 0;

                for (FontFileInfo fileInfo : filesInFolder) {
                    FontEntity existing = findByPath(existingFonts, fileInfo.getPath());

                    if (existing == null) {
                        FontEntity newFont = createFontEntityFromFile(fileInfo, false);

                        if (fileInfo.getSize() < 2 * 1024 * 1024) {
                            try {
                                File fontFile = new File(fileInfo.getPath());
                                String realName = FontMetadataExtractor.extractFontName(fontFile, 0);
                                if (realName != null && !realName.isEmpty() &&
                                    !realName.equals("Unknown Font")) {
                                    newFont.setRealName(realName);
                                    Log.d(TAG, "★ Instantly extracted name: " + realName);
                                }
                            } catch (Exception e) {
                                Log.w(TAG, "Quick extraction failed: " + e.getMessage());
                            }
                        }

                        try {
                            File fontFile = new File(fileInfo.getPath());
                            String weightWidthLabel = FontWeightWidthExtractor.extract(fontFile, 0);
                            newFont.setWeightWidthLabel(weightWidthLabel);
                            Log.d(TAG, "★ Instantly extracted weight/width: " + weightWidthLabel);
                        } catch (Exception e) {
                            Log.w(TAG, "Quick weight/width extraction failed: " + e.getMessage());
                        }

                        fontsToAdd.add(newFont);
                    } else if (existing.getLastModified() != fileInfo.getLastModified()) {
                        existing.setLastModified(fileInfo.getLastModified());
                        existing.setSize(fileInfo.getSize());
                        existing.setUpdatedAt(System.currentTimeMillis());
                        fontDao.update(existing);
                        updated++;
                    }
                }

                for (FontEntity existing : existingFonts) {
                    if (!fileExistsInList(filesInFolder, existing.getPath())) {
                        fontDao.delete(existing);
                        deleted++;
                    }
                }

                if (!fontsToAdd.isEmpty()) {
                    fontDao.insertAll(fontsToAdd);
                }

                if (listener != null) {
                    listener.onSyncComplete(fontsToAdd.size(), updated, deleted);
                }

                executorService.execute(() -> {
                    extractRealNamesSync();
                    extractWeightWidthSync();

                    if (listener != null) {
                        listener.onFullExtractionComplete();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Failed to sync local fonts", e);
                if (listener != null) {
                    listener.onSyncComplete(0, 0, 0);
                    listener.onFullExtractionComplete();
                }
            }
        });
    }

    private FontEntity createFontEntityFromFile(FontFileInfo fileInfo, boolean isSystem) {
        FontEntity entity = new FontEntity(fileInfo.getPath(), fileInfo.getName());
        entity.setSize(fileInfo.getSize());
        entity.setLastModified(fileInfo.getLastModified());
        entity.setSystemFont(isSystem);
        entity.setTtcIndex(0);

        String fileName = fileInfo.getName().toLowerCase();
        if (fileName.endsWith(".ttc")) {
            entity.setFontType("TTC");
        } else if (fileName.endsWith(".otf")) {
            entity.setFontType("OTF");
        } else {
            entity.setFontType("TTF");
        }

        return entity;
    }

    private FontEntity findByPath(List<FontEntity> fonts, String path) {
        for (FontEntity font : fonts) {
            if (font.getPath().equals(path)) {
                return font;
            }
        }
        return null;
    }

    private boolean fileExistsInList(List<FontFileInfo> files, String path) {
        for (FontFileInfo file : files) {
            if (file.getPath().equals(path)) {
                return true;
            }
        }
        return false;
    }



    private void extractRealNamesSync() {
        try {
            List<FontEntity> fontsWithoutNames = fontDao.getFontsWithoutRealName(500);

            fontsWithoutNames.sort((f1, f2) -> Long.compare(f1.getSize(), f2.getSize()));

            class NameUpdate {
                String path; String realName;
                NameUpdate(String path, String realName) { this.path = path; this.realName = realName; }
            }
            List<NameUpdate> updates = new ArrayList<>();

            for (FontEntity font : fontsWithoutNames) {
                if (!font.isSystemFont()) {
                    try {
                        File fontFile = new File(font.getPath());
                        if (fontFile.exists() && fontFile.length() < 10 * 1024 * 1024) {
                            String realName = FontMetadataExtractor.extractFontName(
                                fontFile, font.getTtcIndex());
                            if (realName != null && !realName.isEmpty() &&
                                !realName.equals("Unknown Font")) {
                                updates.add(new NameUpdate(font.getPath(), realName));
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to extract name: " + e.getMessage());
                    }
                }
            }

            if (!updates.isEmpty()) {
                database.runInTransaction(() -> {
                    long now = System.currentTimeMillis();
                    for (NameUpdate update : updates) {
                        fontDao.updateRealName(update.path, update.realName, now);
                    }
                });
            }

            Log.d(TAG, "★ extractRealNamesSync complete: " + updates.size() + " names extracted");

        } catch (Exception e) {
            Log.e(TAG, "Failed to extract real names", e);
        }
    }

    private void extractWeightWidthSync() {
        try {
            List<FontEntity> fontsWithoutLabel = fontDao.getFontsWithoutWeightWidth(500);

            fontsWithoutLabel.sort((f1, f2) -> Long.compare(f1.getSize(), f2.getSize()));

            class LabelUpdate {
                String path; String label;
                LabelUpdate(String path, String label) { this.path = path; this.label = label; }
            }
            List<LabelUpdate> updates = new ArrayList<>();

            for (FontEntity font : fontsWithoutLabel) {
                if (!font.isSystemFont()) {
                    try {
                        File fontFile = new File(font.getPath());
                        if (fontFile.exists()) {
                            String label = FontWeightWidthExtractor.extract(
                                fontFile, font.getTtcIndex());
                            updates.add(new LabelUpdate(font.getPath(), label));
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to extract weight/width: " + e.getMessage());
                    }
                }
            }

            if (!updates.isEmpty()) {
                database.runInTransaction(() -> {
                    long now = System.currentTimeMillis();
                    for (LabelUpdate update : updates) {
                        fontDao.updateWeightWidthLabel(update.path, update.label, now);
                    }
                });
            }

            Log.d(TAG, "★ extractWeightWidthSync complete: " + updates.size() + " labels extracted");

        } catch (Exception e) {
            Log.e(TAG, "Failed to extract weight/width labels", e);
        }
    }

    public interface OnCompleteListener {
        void onComplete(boolean success);
    }

    public interface OnSyncCompleteListener {
        void onSyncComplete(int added, int updated, int deleted);
        void onFullExtractionComplete();
    }
    }
