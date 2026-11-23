package com.oneui.fontviewer.service;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.concurrent.atomic.AtomicBoolean;

public class BatchOperationState {

    private static final String TAG = "BatchOperationState";

    private static final MutableLiveData<Boolean> _isProcessing =
            new MutableLiveData<>(false);

    // اسم الشاشة المصدر للعملية (مفتاح نصي محايد). تضبطه الواجهة وتقرأه الخدمة/الإشعار دون معرفة أي نوع من الواجهة
    private static volatile String _sourceScreen = null;

    public static class ProgressData {
        public final int    current;
        public final int    total;
        public final String title;
        public final int    operationCode; 

        public ProgressData(int current, int total, String title) {
            this(current, total, title, 1);
        }

        public ProgressData(int current, int total, String title, int operationCode) {
            this.current = current;
            this.total   = total;
            this.title   = title;
            this.operationCode = operationCode;
        }
    }

    private static final MutableLiveData<ProgressData> _progress =
            new MutableLiveData<>();
    
    private static volatile AtomicBoolean _currentCancelFlag = null;

    private static volatile boolean _shouldReopenDialog = false;

    public static void setShouldReopenDialog(boolean shouldReopen) {
        _shouldReopenDialog = shouldReopen;
    }

    public static boolean consumeShouldReopenDialog() {
        boolean value = _shouldReopenDialog;
        _shouldReopenDialog = false;
        return value;
    }


    public static LiveData<Boolean> getIsProcessing() {
        return _isProcessing;
    }

    public static LiveData<ProgressData> getProgress() {
        return _progress;
    }

    public static String getSourceScreen() {
        return _sourceScreen;
    }
    
    public static void setProcessing(boolean isProcessing) {
        if (!isProcessing) {
            _sourceScreen = null;
        }
        _isProcessing.postValue(isProcessing);
    }

    public static void setSourceScreen(String screenName) {
        _sourceScreen = screenName;
        Log.d(TAG, "setSourceScreen=" + screenName);
    }

    public static void updateProgress(int current, int total, String title) {
        _progress.postValue(new ProgressData(current, total, title, 1));
    }

    public static void updateProgress(int current, int total, String title, int operationCode) {
        _progress.postValue(new ProgressData(current, total, title, operationCode));
    }


    public static void setCancelFlag(AtomicBoolean flag) {
        _currentCancelFlag = flag;
        Log.d(TAG, "Cancel flag registered for current operation");
    }

    public static void requestCancel() {
        if (_currentCancelFlag != null) {
            _currentCancelFlag.set(true);
            Log.d(TAG, "Cancel requested via notification — flag set to true");
        } else {
            Log.w(TAG, "requestCancel() called but no cancel flag is registered");
        }
    }
}
