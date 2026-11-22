package com.oneui.fontviewer.data.localfont.model;

import com.oneui.fontviewer.data.common.model.FontItem;

public class FontFileInfoWithMetadata {
    private final String name;
    private final String path;
    private final long size;
    private final long lastModified;
    private final String realName;
    private final String weightWidthLabel;
    private final boolean isFavorite;
    
    public FontFileInfoWithMetadata(FontItem item) {
        this.name             = item.getFileName();
        this.path             = item.getPath();
        this.size             = item.getSize();
        this.lastModified     = item.getLastModified();
        this.realName         = item.getRealName();
        this.weightWidthLabel = item.getWeightWidthLabel(); 
        this.isFavorite       = item.isFavorite();          
    }
    
    
    
    public String  getName()             { return name; }
    public String  getPath()             { return path; }
    public long    getSize()             { return size; }
    public long    getLastModified()     { return lastModified; }
    public String  getRealName()         { return realName; }
    public String  getWeightWidthLabel() { return weightWidthLabel; }
    public boolean isFavorite()          { return isFavorite; }
    
    
}
