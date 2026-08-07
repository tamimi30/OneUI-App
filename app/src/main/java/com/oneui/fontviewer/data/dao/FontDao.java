package com.oneui.fontviewer.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.oneui.fontviewer.data.entity.FontEntity;

import java.util.List;

@Dao
public interface FontDao {
     
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(FontEntity font);
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    List<Long> insertAll(List<FontEntity> fonts);
    
    @Update
    int update(FontEntity font);
    
    @Delete
    int delete(FontEntity font);
    
    
    
    @Query("DELETE FROM fonts WHERE path = :path")
    int deleteByPath(String path);
    
    
    
    @Query("DELETE FROM fonts WHERE is_system_font = 1")
    int deleteAllSystemFonts();
    
    
    
    @Query("SELECT * FROM fonts WHERE path = :path LIMIT 1")
    LiveData<FontEntity> getFontByPath(String path);
    
    
    
    @Query("SELECT * FROM fonts")
    LiveData<List<FontEntity>> getAllFonts();
    
    @Query("SELECT * FROM fonts")
    List<FontEntity> getAllFontsSync();
    
    
    @Query("SELECT * FROM fonts WHERE is_system_font = 1 ORDER BY file_name ASC")
    LiveData<List<FontEntity>> getSystemFonts();
    
    @Query("SELECT * FROM fonts WHERE is_system_font = 1 ORDER BY file_name ASC")
    List<FontEntity> getSystemFontsSync();
    
    @Query("SELECT * FROM fonts WHERE is_system_font = 0 AND is_trashed = 0 ORDER BY file_name ASC")
    LiveData<List<FontEntity>> getLocalFonts();
    
    @Query("SELECT * FROM fonts WHERE is_system_font = 0 AND is_trashed = 0 ORDER BY file_name ASC")
    List<FontEntity> getLocalFontsSync();
    
    @Query("SELECT * FROM fonts WHERE is_system_font = :isSystem AND is_trashed = 0 ORDER BY file_name ASC")
    LiveData<List<FontEntity>> getFontsSortedByName(boolean isSystem);
    
    @Query("SELECT * FROM fonts WHERE is_system_font = :isSystem AND is_trashed = 0 ORDER BY file_name DESC")
    LiveData<List<FontEntity>> getFontsSortedByNameDesc(boolean isSystem);
    
    @Query("SELECT * FROM fonts WHERE is_system_font = :isSystem AND is_trashed = 0 ORDER BY last_modified ASC")
    LiveData<List<FontEntity>> getFontsSortedByDate(boolean isSystem);
    
    @Query("SELECT * FROM fonts WHERE is_system_font = :isSystem AND is_trashed = 0 ORDER BY last_modified DESC")
    LiveData<List<FontEntity>> getFontsSortedByDateDesc(boolean isSystem);
    
    @Query("SELECT * FROM fonts WHERE is_system_font = :isSystem AND is_trashed = 0 ORDER BY size ASC")
    LiveData<List<FontEntity>> getFontsSortedBySize(boolean isSystem);
    
    @Query("SELECT * FROM fonts WHERE is_system_font = :isSystem AND is_trashed = 0 ORDER BY size DESC")
    LiveData<List<FontEntity>> getFontsSortedBySizeDesc(boolean isSystem);
    
    @Query("SELECT * FROM fonts WHERE is_system_font = :isSystem AND is_trashed = 0 " +
           "AND (file_name LIKE '%' || :query || '%' " +
           "OR real_name LIKE '%' || :query || '%') " +
           "ORDER BY file_name ASC")
    LiveData<List<FontEntity>> searchFonts(boolean isSystem, String query);
    
    @Query("SELECT * FROM fonts WHERE is_system_font = :isSystem AND is_trashed = 0 " +
           "AND (file_name LIKE '%' || :query || '%' " +
           "OR real_name LIKE '%' || :query || '%') " +
           "ORDER BY file_name ASC")
    List<FontEntity> searchFontsSync(boolean isSystem, String query);
    
    
    @Query("UPDATE fonts SET is_cached = :isCached, updated_at = :timestamp WHERE path = :path")
    int updateCacheStatus(String path, boolean isCached, long timestamp);
    
    @Query("UPDATE fonts SET last_access_time = :accessTime, " +
           "access_count = access_count + 1, updated_at = :timestamp " +
           "WHERE path = :path")
    int recordAccess(String path, long accessTime, long timestamp);
    
    @Query("SELECT * FROM fonts WHERE is_cached = 1 ORDER BY last_access_time DESC")
    List<FontEntity> getCachedFonts();
    
    @Query("UPDATE fonts SET is_cached = 0, updated_at = :timestamp")
    int resetAllCacheStatus(long timestamp);
    
    @Query("UPDATE fonts SET is_cached = 0, updated_at = :timestamp WHERE is_system_font = 1")
    int resetSystemFontsCacheStatus(long timestamp);
    
    @Query("UPDATE fonts SET is_cached = 0, updated_at = :timestamp WHERE is_system_font = 0")
    int resetLocalFontsCacheStatus(long timestamp);
    
    
    @Query("SELECT COUNT(*) FROM fonts")
    LiveData<Integer> getTotalFontsCount();
    
    @Query("SELECT COUNT(*) FROM fonts")
    int getTotalFontsCountSync();
    
    @Query("SELECT COUNT(*) FROM fonts WHERE is_system_font = 1")
    LiveData<Integer> getSystemFontsCount();
    
    @Query("SELECT COUNT(*) FROM fonts WHERE is_system_font = 1")
    int getSystemFontsCountSync();
    
    @Query("SELECT COUNT(*) FROM fonts WHERE is_system_font = 0 AND is_trashed = 0")
    LiveData<Integer> getLocalFontsCount();
    
    @Query("SELECT COUNT(*) FROM fonts WHERE is_system_font = 0 AND is_trashed = 0")
    int getLocalFontsCountSync();
    
    @Query("SELECT COUNT(*) FROM fonts WHERE is_variable_font = 1")
    int getVariableFontsCount();
    
    @Query("SELECT COUNT(*) FROM fonts WHERE is_cached = 1")
    int getCachedFontsCount();
    
    @Query("SELECT SUM(size) FROM fonts WHERE is_system_font = 0 AND is_trashed = 0")
    long getTotalLocalFontsSize();
    
    @Query("SELECT EXISTS(SELECT 1 FROM fonts WHERE path = :path)")
    boolean fontExists(String path);
    
    @Query("SELECT * FROM fonts WHERE is_variable_font = 1 ORDER BY file_name ASC")
    List<FontEntity> getVariableFonts();
    
    @Query("UPDATE fonts SET real_name = :realName, updated_at = :timestamp WHERE path = :path")
    int updateRealName(String path, String realName, long timestamp);
    
    @Query("SELECT * FROM fonts WHERE real_name IS NULL OR real_name = '' LIMIT :limit")
    List<FontEntity> getFontsWithoutRealName(int limit);
    
    
    @Query("UPDATE fonts SET path = :newPath, file_name = :newFileName, updated_at = :updatedAt WHERE path = :oldPath")
    void updatePath(String oldPath, String newPath, String newFileName, long updatedAt);


    @Query("UPDATE fonts SET weight_width_label = :label, updated_at = :timestamp WHERE path = :path")
    int updateWeightWidthLabel(String path, String label, long timestamp);

    @Query("SELECT * FROM fonts WHERE weight_width_label IS NULL LIMIT :limit")
    List<FontEntity> getFontsWithoutWeightWidth(int limit);

    @Query("UPDATE fonts SET is_favorite = :isFavorite, updated_at = :timestamp WHERE path = :path")
    int updateFavoriteStatus(String path, boolean isFavorite, long timestamp);

    @Query("SELECT * FROM fonts WHERE is_favorite = 1 AND is_trashed = 0 ORDER BY file_name ASC")
    LiveData<List<FontEntity>> getFavoriteFonts();

    @Query("SELECT COUNT(*) FROM fonts WHERE is_favorite = 1 AND is_trashed = 0")
    LiveData<Integer> getFavoriteFontsCount();

    @Query("SELECT * FROM fonts WHERE is_favorite = 1 AND is_trashed = 0 " +
           "AND (file_name LIKE '%' || :query || '%' " +
           "OR real_name LIKE '%' || :query || '%') " +
           "ORDER BY file_name ASC")
    LiveData<List<FontEntity>> searchFavoriteFonts(String query);

    @Query("SELECT * FROM fonts WHERE is_favorite = 1 AND is_trashed = 0 ORDER BY file_name ASC")
    LiveData<List<FontEntity>> getFavoriteFontsSortedByName();

    @Query("SELECT * FROM fonts WHERE is_favorite = 1 AND is_trashed = 0 ORDER BY file_name DESC")
    LiveData<List<FontEntity>> getFavoriteFontsSortedByNameDesc();

    @Query("SELECT * FROM fonts WHERE is_favorite = 1 AND is_trashed = 0 ORDER BY last_modified ASC")
    LiveData<List<FontEntity>> getFavoriteFontsSortedByDate();

    @Query("SELECT * FROM fonts WHERE is_favorite = 1 AND is_trashed = 0 ORDER BY last_modified DESC")
    LiveData<List<FontEntity>> getFavoriteFontsSortedByDateDesc();

    @Query("SELECT * FROM fonts WHERE is_favorite = 1 AND is_trashed = 0 ORDER BY size ASC")
    LiveData<List<FontEntity>> getFavoriteFontsSortedBySize();

    @Query("SELECT * FROM fonts WHERE is_favorite = 1 AND is_trashed = 0 ORDER BY size DESC")
    LiveData<List<FontEntity>> getFavoriteFontsSortedBySizeDesc();


    @Query("SELECT * FROM fonts WHERE is_trashed = 1 ORDER BY deleted_at DESC")
    LiveData<List<FontEntity>> getTrashFonts();

    @Query("SELECT * FROM fonts WHERE is_trashed = 1 ORDER BY deleted_at DESC")
    List<FontEntity> getTrashFontsSync();

    @Query("SELECT COUNT(*) FROM fonts WHERE is_trashed = 1")
    LiveData<Integer> getTrashFontsCount();

    @Query("SELECT COUNT(*) FROM fonts WHERE is_trashed = 1")
    int getTrashFontsCountSync();

    @Query("UPDATE fonts SET is_trashed = 1, deleted_at = :deletedAt, " +
           "original_path = :originalPath, path = :path, updated_at = :timestamp " +
           "WHERE path = :originalPath")
    int moveToTrash(String originalPath, String path, long deletedAt, long timestamp);

    @Query("UPDATE fonts SET is_trashed = 0, deleted_at = 0, original_path = NULL, " +
           "path = :restoredPath, file_name = :fileName, updated_at = :timestamp " +
           "WHERE path = :trashedPath")
    int restoreFromTrash(String trashedPath, String restoredPath, String fileName, long timestamp);

    @Query("DELETE FROM fonts WHERE path = :path AND is_trashed = 1")
    int deleteFromTrash(String path);

    @Query("DELETE FROM fonts WHERE is_trashed = 1")
    int emptyTrash();

    @Query("SELECT * FROM fonts WHERE is_trashed = 1 AND deleted_at <= :expiryTimestamp")
    List<FontEntity> getExpiredTrashFonts(long expiryTimestamp);
                       }
