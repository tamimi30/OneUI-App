package com.oneui.fontviewer.data.model;

import com.oneui.fontviewer.data.database.FontEntity;

public class FontFileInfoWithMetadata {
    private final String name;
    private final String path;
    private final long size;
    private final long lastModified;
    private final String realName;
    private final String weightWidthLabel;
    private final boolean isFavorite;
    
    public FontFileInfoWithMetadata(FontEntity entity) {
        this.name             = entity.getFileName();
        this.path             = entity.getPath();
        this.size             = entity.getSize();
        this.lastModified     = entity.getLastModified();
        this.realName         = entity.getRealName();
        this.weightWidthLabel = entity.getWeightWidthLabel(); 
        this.isFavorite       = entity.isFavorite();          
    }
    
    
    
    public String  getName()             { return name; }
    public String  getPath()             { return path; }
    public long    getSize()             { return size; }
    public long    getLastModified()     { return lastModified; }
    public String  getRealName()         { return realName; }
    public String  getWeightWidthLabel() { return weightWidthLabel; }
    public boolean isFavorite()          { return isFavorite; }
    
    
}
