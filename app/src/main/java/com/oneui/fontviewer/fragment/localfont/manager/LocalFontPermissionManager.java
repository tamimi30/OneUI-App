package com.oneui.fontviewer.fragment.localfont.manager;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import android.util.Log;

public class LocalFontPermissionManager {
    
    private static final String TAG = "LocalFontPermissionManager";
    public static final int STORAGE_PERMISSION_REQUEST_CODE = 100;
    public static final int MANAGE_STORAGE_REQUEST_CODE = 101;
    
    private final Context context;
    private final Fragment fragment;
    private PermissionResultListener listener;
    
    public interface PermissionResultListener {
        void onPermissionGranted();
        void onPermissionDenied();
        void onManageStoragePermissionRequired();
    }
    
    public LocalFontPermissionManager(Fragment fragment) {
        this.fragment = fragment;
        this.context = fragment.requireContext();
    } 
    
    public void setPermissionResultListener(PermissionResultListener listener) {
        this.listener = listener;
    }
    
    public boolean hasRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            return ContextCompat.checkSelfPermission(context, 
                Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }
    
    public void requestPermissions() {
        if (fragment == null) {
            throw new IllegalStateException("Fragment is required to request permissions");
        }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                if (listener != null) {
                    listener.onManageStoragePermissionRequired();
                }
                requestManageStoragePermission();
            }
        } else {
            fragment.requestPermissions(
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                STORAGE_PERMISSION_REQUEST_CODE
            );
        }
    }
    
    private void requestManageStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + context.getPackageName()));
                if (fragment != null) {
                    fragment.startActivityForResult(intent, MANAGE_STORAGE_REQUEST_CODE);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to open MANAGE_EXTERNAL_STORAGE settings", e);
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    if (fragment != null) {
                        fragment.startActivityForResult(intent, MANAGE_STORAGE_REQUEST_CODE);
                    }
                } catch (Exception ex) {
                    Log.e(TAG, "Failed to open settings fallback", ex);
                }
            }
        }
    }
    
    public boolean handlePermissionResult(int requestCode, int[] grantResults) {
        if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (listener != null) {
                    listener.onPermissionGranted();
                }
            } else {
                if (listener != null) {
                    listener.onPermissionDenied();
                }
            }
            return true;
        }
        return false;
    }
    
    public boolean handleActivityResult(int requestCode) {
        if (requestCode == MANAGE_STORAGE_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    if (listener != null) {
                        listener.onPermissionGranted();
                    }
                    return true;
                } else {
                    if (listener != null) {
                        listener.onPermissionDenied();
                    }
                    return true;
                }
            }
        }
        return false;
    }
    
    
}
