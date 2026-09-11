package com.oneui.fontviewer.data.entity;

public class FontFileInfo {

    private final String name;
    private final String path;
    private final long size;
    private final long lastModified;
    private final String realName;

    public FontFileInfo(String name, String path, long size, long lastModified) {
        this(name, path, size, lastModified, null);
    }

    public FontFileInfo(String name, String path, long size, long lastModified, String realName) {
        this.name         = name;
        this.path         = path;
        this.size         = size;
        this.lastModified = lastModified;
        this.realName     = realName;
    }

    public String getName()        { return name; }
    public String getPath()        { return path; }
    public long   getSize()        { return size; }
    public long   getLastModified(){ return lastModified; }
    public String getRealName()    { return realName; }

    public String getDisplayName() {
        if (realName != null && !realName.isEmpty()) {
            return realName;
        }
        String n = name;
        if (n != null) {
            String lower = n.toLowerCase();
            if (lower.endsWith(".ttf") || lower.endsWith(".otf") || lower.endsWith(".ttc")) {
                return n.substring(0, n.length() - 4);
            }
        }
        return n;
    }
}
