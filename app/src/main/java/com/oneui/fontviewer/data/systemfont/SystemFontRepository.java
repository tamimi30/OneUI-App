package com.oneui.fontviewer.data.systemfont;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import androidx.lifecycle.LiveData;

import com.oneui.fontviewer.data.common.room.FontDao;
import com.oneui.fontviewer.data.common.room.FontDatabase;
import com.oneui.fontviewer.data.common.room.FontEntity;
import com.oneui.fontviewer.data.common.model.FontItem;
import com.oneui.fontviewer.data.common.room.FontMapper;
import com.oneui.fontviewer.utils.metadata.FontMetadataReader;
import com.oneui.fontviewer.utils.metadata.FontWeightWidthExtractor;

public class SystemFontRepository {

    private static final String TAG = "SystemFontRepository";
    private static volatile SystemFontRepository INSTANCE;

    private final FontDao fontDao;
    private final ExecutorService executorService;
    private final Context context;

    private SystemFontRepository(Context context) {
        this.context = context.getApplicationContext();
        FontDatabase database = FontDatabase.getInstance(this.context);
        fontDao = database.fontDao();
        executorService = FontDatabase.databaseWriteExecutor;
    }

    public static SystemFontRepository getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (SystemFontRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new SystemFontRepository(context.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }

    public LiveData<List<FontItem>> getSystemFonts() {
        return FontMapper.toItemsLive(fontDao.getSystemFonts());
    }

    public LiveData<Integer> getSystemFontsCount() {
        return fontDao.getSystemFontsCount();
    }

    public void loadAndSyncSystemFonts(OnSyncCompleteListener listener) {
        executorService.execute(() -> {
            try {
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
                    if (listener != null) {
                        listener.onSyncComplete(0, 0, 0);
                    }
                    return;
                }

                SystemFontManager manager = SystemFontManager.getInstance();
                List<SystemFontInfo> systemFonts = manager.getSystemFonts();

                if (systemFonts == null || systemFonts.isEmpty()) {
                    if (listener != null) {
                        listener.onSyncComplete(0, 0, 0);
                    }
                    return;
                }

                List<FontEntity> existingFonts = fontDao.getSystemFontsSync();
                List<FontEntity> fontsToAdd = new ArrayList<>();
                Set<String> currentPaths = new HashSet<>();
                int updated = 0;
                int deleted = 0;

                for (SystemFontInfo fontInfo : systemFonts) {
                    currentPaths.add(fontInfo.getPath());
                    FontEntity existing = findByPath(existingFonts, fontInfo.getPath());

                    if (existing == null) {
                        FontEntity newFont = createFontEntityFromSystemFont(fontInfo);
                        fontsToAdd.add(newFont);

                        if (newFont.getRealName() != null && !newFont.getRealName().isEmpty()) {
                            Log.d(TAG, "✓ System font with ready name: " + newFont.getRealName());
                        }
                    } else {
                        boolean needsUpdate = false;

                        if (existing.getLastModified() != fontInfo.getLastModified()) {
                            existing.setLastModified(fontInfo.getLastModified());
                            needsUpdate = true;
                        }

                        if (existing.getSize() != fontInfo.getSize()) {
                            existing.setSize(fontInfo.getSize());
                            needsUpdate = true;
                        }

                        String infoRealName = fontInfo.getRealName();
                        if (infoRealName != null && !infoRealName.isEmpty()) {
                            String currentRealName = existing.getRealName();
                            if (currentRealName == null || currentRealName.isEmpty() ||
                                !currentRealName.equals(infoRealName)) {
                                existing.setRealName(infoRealName);
                                needsUpdate = true;
                            }
                        }

                        if (needsUpdate) {
                            existing.setUpdatedAt(System.currentTimeMillis());
                            fontDao.update(existing);
                            updated++;
                        }
                    }
                }

                // حذف الخطوط التي كانت محفوظة في قاعدة البيانات ولم تعد موجودة في النظام
                // (مثلاً خط حُذف بتحديث للنظام)، وإلا تبقى ظاهرة في القائمة بملفات غير موجودة.
                // نصل الى هنا فقط اذا أعاد النظام قائمة غير فارغة (انظر الفحص أعلاه)، فلا نمسح القائمة كلها بسبب خطأ عابر.
                List<FontEntity> staleFonts = new ArrayList<>();
                List<String> stalePaths = new ArrayList<>();
                for (FontEntity existing : existingFonts) {
                    if (!currentPaths.contains(existing.getPath())) {
                        staleFonts.add(existing);
                        stalePaths.add(existing.getPath());
                    }
                }

                if (!staleFonts.isEmpty()) {
                    deleted = fontDao.deleteAll(staleFonts);
                    SystemFontCache.getInstance().evict(stalePaths);
                    Log.d(TAG, "Removed " + deleted + " system fonts that no longer exist");
                }

                if (!fontsToAdd.isEmpty()) {
                    fontDao.insertAll(fontsToAdd);
                }

                // === كود اختبار مؤقت: احذفه بعد الاختبار ===
                android.content.SharedPreferences testPrefs =
                        context.getSharedPreferences("sysfont_delete_test", Context.MODE_PRIVATE);
                if (!testPrefs.getBoolean("fake_inserted", false)) {
                    FontEntity fake = new FontEntity("/system/fonts/FakeGoogleSans.ttf", "FakeGoogleSans.ttf");
                    fake.setSystemFont(true);
                    fontDao.insert(fake);
                    testPrefs.edit().putBoolean("fake_inserted", true).apply();
                }
                // === نهاية كود الاختبار ===

                if (listener != null) {
                    listener.onSyncComplete(fontsToAdd.size(), updated, deleted);
                }

                extractSystemFontsRealNamesInBackgroundPrioritized();

                extractSystemFontsWeightWidthInBackground();

            } catch (Exception e) {
                Log.e(TAG, "Failed to sync system fonts", e);
                if (listener != null) {
                    listener.onSyncComplete(0, 0, 0);
                }
            }
        });
    }

    private FontEntity createFontEntityFromSystemFont(SystemFontInfo fontInfo) {
        FontEntity entity = new FontEntity(fontInfo.getPath(), fontInfo.getName());
        entity.setSize(fontInfo.getSize());
        entity.setLastModified(fontInfo.getLastModified());
        entity.setSystemFont(true);
        entity.setTtcIndex(fontInfo.getTtcIndex());
        entity.setVariableFont(fontInfo.isVariableFont());

        String realName = fontInfo.getRealName();
        if (realName != null && !realName.isEmpty()) {
            entity.setRealName(realName);
        }

        if (realName == null || realName.isEmpty()) {
            try {
                File fontFile = new File(fontInfo.getPath());
                if (fontFile.exists()) {
                    String extractedName = FontMetadataReader.extractFontName(
                        fontFile, fontInfo.getTtcIndex());

                    if (extractedName != null && !extractedName.isEmpty() &&
                        !extractedName.equals("Unknown Font")) {
                        entity.setRealName(extractedName);
                        Log.d(TAG, "✓ Instantly extracted system font name: " + extractedName);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Quick extraction failed for system font: " + e.getMessage());
            }
        }

        try {
            File fontFile = new File(fontInfo.getPath());
            if (fontFile.exists()) {
                String weightWidthLabel = FontWeightWidthExtractor.extract(
                    fontFile, fontInfo.getTtcIndex());
                entity.setWeightWidthLabel(weightWidthLabel);
                Log.d(TAG, "✓ Instantly extracted weight/width: " + weightWidthLabel);
            }
        } catch (Exception e) {
            Log.w(TAG, "Quick weight/width extraction failed for system font: " + e.getMessage());
        }

        String fileName = fontInfo.getName().toLowerCase();
        if (fileName.endsWith(".ttc")) {
            entity.setFontType("TTC");
        } else if (fileName.endsWith(".otf")) {
            entity.setFontType("OTF");
        } else {
            entity.setFontType("TTF");
        }

        if (fontInfo.isVariableFont()) {
            entity.setFontType("Variable");
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

    private void extractSystemFontsRealNamesInBackgroundPrioritized() {
        executorService.execute(() -> {
            try {
                List<FontEntity> fontsWithoutNames = fontDao.getFontsWithoutRealName(500);

                fontsWithoutNames.sort((f1, f2) -> Long.compare(f1.getSize(), f2.getSize()));

                int extracted = 0;
                for (FontEntity font : fontsWithoutNames) {
                    if (font.isSystemFont()) {
                        try {
                            File fontFile = new File(font.getPath());
                            if (fontFile.exists()) {
                                String realName = FontMetadataReader.extractFontName(
                                    fontFile, font.getTtcIndex());

                                if (realName != null && !realName.isEmpty() &&
                                    !realName.equals("Unknown Font")) {
                                    fontDao.updateRealName(font.getPath(), realName,
                                        System.currentTimeMillis());
                                    extracted++;

                                    if (extracted % 10 == 0) {
                                        Log.d(TAG, "Background extraction: " + extracted + " system font names");
                                    }
                                }
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Failed to extract name in background: " + e.getMessage());
                        }
                    }
                }

                Log.d(TAG, "★ Background extraction complete: " + extracted + " system font names extracted");

            } catch (Exception e) {
                Log.e(TAG, "Failed to extract system fonts real names", e);
            }
        });
    }

    private void extractSystemFontsWeightWidthInBackground() {
        executorService.execute(() -> {
            try {
                fontDao.resetLegacyWeightWidthLabels();
                List<FontEntity> fontsWithoutLabel = fontDao.getFontsWithoutWeightWidth(500);

                fontsWithoutLabel.sort((f1, f2) -> Long.compare(f1.getSize(), f2.getSize()));

                int extracted = 0;
                for (FontEntity font : fontsWithoutLabel) {
                    if (font.isSystemFont()) {
                        try {
                            File fontFile = new File(font.getPath());
                            if (fontFile.exists()) {
                                String label = FontWeightWidthExtractor.extract(
                                    fontFile, font.getTtcIndex());
                                fontDao.updateWeightWidthLabel(
                                    font.getPath(), label, System.currentTimeMillis());
                                extracted++;

                                if (extracted % 10 == 0) {
                                    Log.d(TAG, "System font weight/width extraction: " + extracted + " labels");
                                }
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Failed to extract weight/width for system font: " + e.getMessage());
                        }
                    }
                }

                Log.d(TAG, "★ System font weight/width extraction complete: " + extracted + " labels extracted");

            } catch (Exception e) {
                Log.e(TAG, "Failed to extract weight/width for system fonts in background", e);
            }
        });
    }

    public void updateRealName(String path, String realName) {
        executorService.execute(() -> {
            try {
                fontDao.updateRealName(path, realName, System.currentTimeMillis());
            } catch (Exception e) {
                Log.e(TAG, "Failed to update real name", e);
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

    public void updateCacheStatus(String path, boolean isCached) {
        executorService.execute(() -> {
            try {
                fontDao.updateCacheStatus(path, isCached, System.currentTimeMillis());
            } catch (Exception e) {
                Log.e(TAG, "Failed to update cache status", e);
            }
        });
    }

    public LiveData<FontItem> getFontByPath(String path) {
        return FontMapper.toItemLive(fontDao.getFontByPath(path));
    }

    public LiveData<List<FontItem>> searchSystemFonts(String query) {
        return FontMapper.toItemsLive(fontDao.searchFonts(true, query));
    }

    public LiveData<List<FontItem>> getSystemFontsSorted(SortType sortType, boolean ascending) {
        return FontMapper.toItemsLive(getSystemFontsSortedEntities(sortType, ascending));
    }

    private LiveData<List<FontEntity>> getSystemFontsSortedEntities(SortType sortType, boolean ascending) {
        switch (sortType) {
            case DATE:
                return ascending ? fontDao.getFontsSortedByDate(true)
                                : fontDao.getFontsSortedByDateDesc(true);
            case SIZE:
                return ascending ? fontDao.getFontsSortedBySize(true)
                                : fontDao.getFontsSortedBySizeDesc(true);
            case NAME:
            default:
                return ascending ? fontDao.getFontsSortedByName(true)
                                : fontDao.getFontsSortedByNameDesc(true);
        }
    }

    public void deleteAllSystemFonts(OnCompleteListener listener) {
        executorService.execute(() -> {
            try {
                int rows = fontDao.deleteAllSystemFonts();
                if (listener != null) {
                    listener.onComplete(rows > 0);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to delete system fonts", e);
                if (listener != null) {
                    listener.onComplete(false);
                }
            }
        });
    }

    public void getVariableFontsCount(OnCountListener listener) {
        executorService.execute(() -> {
            try {
                int count = fontDao.getVariableFontsCount();
                if (listener != null) {
                    listener.onCount(count);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to get variable fonts count", e);
                if (listener != null) {
                    listener.onCount(0);
                }
            }
        });
    }

    public enum SortType {
        NAME, DATE, SIZE
    }

    public interface OnSyncCompleteListener {
        void onSyncComplete(int added, int updated, int deleted);
    }

    public interface OnCompleteListener {
        void onComplete(boolean success);
    }

    public interface OnCountListener {
        void onCount(int count);
    }
                                }
