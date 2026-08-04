package com.oneui.fontviewer.fragment.systemfont.data;

public class SystemFontInfo {
    
    private final String name;
    private final String path;
    private final long size;
    private final long lastModified;
    private final int weight;
    private final int slant;
    private final int ttcIndex;
    private final String axes;
    private String realName;
    private String weightWidthLabel;
    
    public SystemFontInfo(String name, String path, long size, long lastModified,
                         int weight, int slant, int ttcIndex, String axes) {
        this.name = name;
        this.path = path;
        this.size = size;
        this.lastModified = lastModified;
        this.weight = weight;
        this.slant = slant;
        this.ttcIndex = ttcIndex;
        this.axes = axes;
        this.realName = null;
        this.weightWidthLabel = null; 
    }
    
    public String getName() {
        return name;
    }
    
    public String getPath() {
        return path;
    }
    
    public long getSize() {
        return size;
    }
    
    public long getLastModified() {
        return lastModified;
    }
    
    public int getWeight() {
        return weight;
    }
    
    
    public int getTtcIndex() {
        return ttcIndex;
    }
    
    
    public boolean isVariableFont() {
        return axes != null && !axes.isEmpty();
    }
    
    public void setRealName(String realName) {
        this.realName = realName;
    }
    
    public String getRealName() {
        return realName;
    }


    public void setWeightWidthLabel(String weightWidthLabel) {
        this.weightWidthLabel = weightWidthLabel;
    }

    public String getWeightWidthLabel() {
        return weightWidthLabel;
    }
    
    
}
