package com.oneui.fontviewer.ui.main.fragment.localfont.manager;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;
import androidx.fragment.app.Fragment;

import com.oneui.fontviewer.R;
import com.oneui.fontviewer.data.localfont.file.LocalFontDirectory;

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
                String downloadsPath = LocalFontDirectory.getDownloadsDirectoryPath();
                if (downloadsPath != null) {
                    intent.putExtra("android.provider.extra.INITIAL_URI", downloadsPath);
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
                    
                    String directoryPath = LocalFontDirectory.getRealPathFromTreeUri(treeUri);
                    
                    if (directoryPath != null) {
                        Log.d(TAG, "Directory selected: " + directoryPath);
                        if (listener != null) {
                            listener.onDirectorySelected(directoryPath);
                        }
                    } else {
                        Log.e(TAG, "Could not convert URI to direct path: " + treeUri);
                        if (listener != null) {
                            listener.onDirectorySelectionError(
                                new Exception(fragment.getString(R.string.error_folder_path_conversion_failed))
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
    
    
    
                    }
