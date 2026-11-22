package com.oneui.fontviewer.data.localfont.model;

public class FontRenameResult {

    private final boolean success;
    private final String errorMessage;
    private final String newPath;
    private final long newLastModified;

    private FontRenameResult(boolean success, String errorMessage, String newPath, long newLastModified) {
        this.success         = success;
        this.errorMessage    = errorMessage;
        this.newPath         = newPath;
        this.newLastModified = newLastModified;
    }

    public static FontRenameResult success(String newPath, long newLastModified) {
        return new FontRenameResult(true, null, newPath, newLastModified);
    }

    public static FontRenameResult failure(String errorMessage) {
        return new FontRenameResult(false, errorMessage, null, 0L);
    }

    public boolean isSuccess()        { return success; }
    public String  getErrorMessage()  { return errorMessage; }
    public String  getNewPath()       { return newPath; }
    public long    getNewLastModified() { return newLastModified; }
}
