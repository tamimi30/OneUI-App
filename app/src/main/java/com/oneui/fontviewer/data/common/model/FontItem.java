package com.oneui.fontviewer.data.common.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

// نموذج الخط الذي تتعامل معه الواجهة. لا يحمل أي تبعية لـ Room؛ التحويل من/إلى كيان قاعدة البيانات يتم في FontMapper.
public class FontItem {
    
    private long id;
    
    @NonNull
    private String path;
    
    @NonNull
    private String fileName;
    
    @Nullable
    private String realName;
    
    private long size;
    
    private long lastModified;
    
    @Nullable
    private String fontType;
    
    private int ttcIndex;
    
    private boolean isSystemFont;
    
    private boolean isVariableFont;
    
    private boolean isCached;
    
    private long lastAccessTime;
    
    private int accessCount;
    
    private long createdAt;
    
    private long updatedAt;

    @Nullable
    private String weightWidthLabel;

    private boolean isFavorite;


    private boolean isTrashed;

    private long deletedAt;

    @Nullable
    private String originalPath;
    
    public FontItem(@NonNull String path, @NonNull String fileName) {
        this.path = path;
        this.fileName = fileName;
        this.size = 0;
        this.lastModified = 0;
        this.ttcIndex = 0;
        this.isSystemFont = false;
        this.isVariableFont = false;
        this.isCached = false;
        this.lastAccessTime = 0;
        this.accessCount = 0;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
        this.isFavorite = false;   
        this.isTrashed = false;    
        this.deletedAt = 0;        
        this.originalPath = null;  
    }
    
    public long getId() {
        return id;
    }
    
    public void setId(long id) {
        this.id = id;
    }
    
    @NonNull
    public String getPath() {
        return path;
    }
    
    public void setPath(@NonNull String path) {
        this.path = path;
    }
    
    @NonNull
    public String getFileName() {
        return fileName;
    }
    
    public void setFileName(@NonNull String fileName) {
        this.fileName = fileName;
    }
    
    @Nullable
    public String getRealName() {
        return realName;
    }
    
    public void setRealName(@Nullable String realName) {
        this.realName = realName;
    }
    
    public long getSize() {
        return size;
    }
    
    public void setSize(long size) {
        this.size = size;
    }
    
    public long getLastModified() {
        return lastModified;
    }
    
    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }
    
    @Nullable
    public String getFontType() {
        return fontType;
    }
    
    public void setFontType(@Nullable String fontType) {
        this.fontType = fontType;
    }
    
    public int getTtcIndex() {
        return ttcIndex;
    }
    
    public void setTtcIndex(int ttcIndex) {
        this.ttcIndex = ttcIndex;
    }
    
    public boolean isSystemFont() {
        return isSystemFont;
    }
    
    public void setSystemFont(boolean systemFont) {
        isSystemFont = systemFont;
    }
    
    public boolean isVariableFont() {
        return isVariableFont;
    }
    
    public void setVariableFont(boolean variableFont) {
        isVariableFont = variableFont;
    }
    
    public boolean isCached() {
        return isCached;
    }
    
    public void setCached(boolean cached) {
        isCached = cached;
    }
    
    public long getLastAccessTime() {
        return lastAccessTime;
    }
    
    public void setLastAccessTime(long lastAccessTime) {
        this.lastAccessTime = lastAccessTime;
    }
    
    public int getAccessCount() {
        return accessCount;
    }
    
    public void setAccessCount(int accessCount) {
        this.accessCount = accessCount;
    }
    
    public long getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
    
    public long getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }


    @Nullable
    public String getWeightWidthLabel() {
        return weightWidthLabel;
    }

    public void setWeightWidthLabel(@Nullable String weightWidthLabel) {
        this.weightWidthLabel = weightWidthLabel;
    }


    public boolean isFavorite() {
        return isFavorite;
    }

    public void setFavorite(boolean favorite) {
        isFavorite = favorite;
    }


    public boolean isTrashed() {
        return isTrashed;
    }

    public void setTrashed(boolean trashed) {
        isTrashed = trashed;
    }

    public long getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(long deletedAt) {
        this.deletedAt = deletedAt;
    }

    @Nullable
    public String getOriginalPath() {
        return originalPath;
    }

    public void setOriginalPath(@Nullable String originalPath) {
        this.originalPath = originalPath;
    }
    
    public String getDisplayName() {
        if (realName != null && !realName.isEmpty()) {
            return realName;
        }
        
        String name = fileName;
        if (name.toLowerCase().endsWith(".ttf") || 
            name.toLowerCase().endsWith(".otf") ||
            name.toLowerCase().endsWith(".ttc")) {
            return name.substring(0, name.length() - 4);
        }
        return name;
    }
    
    
}
