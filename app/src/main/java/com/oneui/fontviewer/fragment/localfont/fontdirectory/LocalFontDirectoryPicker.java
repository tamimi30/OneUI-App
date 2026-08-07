package com.oneui.fontviewer.fragment.localfont.fontdirectory;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.util.Log;
import androidx.fragment.app.Fragment;
import java.io.File;

public class LocalFontDirectoryPicker {
    
    private static final String TAG = "LocalFontDirectoryPicker";
    public static final int FOLDER_PICKER_REQUEST_CODE = 200;
    
    private final Fragment fragment;
    private DirectorySelectionListener listener;
    
    public interface DirectorySelectionListener {
        void onDirectorySelected(String directoryPath);
        void onDirectorySelectionCancelled();
        void onDirectorySelectionError(Exception error);
    }
    
    public LocalFontDirectoryPicker(Fragment fragment) {
        this.fragment = fragment;
        
    }
    
    public void setDirectorySelectionListener(DirectorySelectionListener listener) {
        this.listener = listener;
    }
    
    public void openDirectoryPicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            
            try {
                File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (downloadsDir != null && downloadsDir.exists()) {
                    intent.putExtra("android.provider.extra.INITIAL_URI", downloadsDir.getAbsolutePath());
                }
            } catch (Exception e) {
                Log.w(TAG, "Could not set initial directory", e);
            }
            
            if (fragment != null) {
                fragment.startActivityForResult(intent, FOLDER_PICKER_REQUEST_CODE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch directory picker", e);
            if (listener != null) {
                listener.onDirectorySelectionError(e);
            }
        }
    }
    
    public boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FOLDER_PICKER_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                try {
                    android.net.Uri treeUri = data.getData();
                    
                    String directoryPath = getRealPathFromTreeUri(treeUri);
                    
                    if (directoryPath != null) {
                        Log.d(TAG, "Directory selected: " + directoryPath);
                        if (listener != null) {
                            listener.onDirectorySelected(directoryPath);
                        }
                    } else {
                        Log.e(TAG, "Could not convert URI to direct path: " + treeUri);
                        if (listener != null) {
                            listener.onDirectorySelectionError(
                                new Exception("Could not convert selected folder to direct path. Please select a folder in internal or external storage.")
                            );
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing directory selection", e);
                    if (listener != null) {
                        listener.onDirectorySelectionError(e);
                    }
                }
            } else {
                Log.d(TAG, "Directory selection cancelled");
                if (listener != null) {
                    listener.onDirectorySelectionCancelled();
                }
            }
            return true;
        }
        return false;
    }
    
    private String getRealPathFromTreeUri(android.net.Uri treeUri) {
        if (treeUri == null) {
            return null;
        }
        
        try {
            String uriPath = treeUri.toString();
            
            if (uriPath.contains("primary:")) {
                String relativePath = uriPath.substring(uriPath.indexOf("primary:") + 8);
                return Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + relativePath;
            }
            
            if (uriPath.contains("/storage/")) {
                int storageIndex = uriPath.indexOf("/storage/");
                String potentialPath = uriPath.substring(storageIndex);
                
                if (potentialPath.contains("%3A")) {
                    potentialPath = potentialPath.replace("%3A", "/");
                }
                if (potentialPath.contains(":")) {
                    potentialPath = potentialPath.replace(":", "/");
                }
                
                if (potentialPath.startsWith("/storage/emulated/0/")) {
                    return potentialPath;
                } else if (potentialPath.contains("/storage/")) {
                    return potentialPath.substring(potentialPath.indexOf("/storage/"));
                }
            }
            
            String documentId = android.provider.DocumentsContract.getTreeDocumentId(treeUri);
            if (documentId != null) {
                if (documentId.startsWith("primary:")) {
                    String path = documentId.substring(8);
                    return Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + path;
                } else if (documentId.contains(":")) {
                    String[] parts = documentId.split(":");
                    if (parts.length > 1) {
                        String basePath = "/storage/" + parts[0];
                        File baseDir = new File(basePath);
                        if (baseDir.exists()) {
                            return basePath + "/" + parts[1];
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error converting URI to path", e);
        }
        
        Log.w(TAG, "Could not determine real path, using fallback");
        return null;
    }
    
    
                    }
