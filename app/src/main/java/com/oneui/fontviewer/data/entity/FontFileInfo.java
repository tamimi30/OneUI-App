package com.oneui.fontviewer.data.entity;

public class FontFileInfo {

    private final String name;
    private final String path;
    private final long size;
    private final long lastModified;

    public FontFileInfo(String name, String path, long size, long lastModified) {
        this.name         = name;
        this.path         = path;
        this.size         = size;
        this.lastModified = lastModified;
    }

    public String getName()        { return name; }
    public String getPath()        { return path; }
    public long   getSize()        { return size; }
    public long   getLastModified(){ return lastModified; }
}
