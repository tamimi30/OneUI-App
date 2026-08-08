package com.oneui.fontviewer.utils.notification;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.oneui.fontviewer.activity.AppScreen;

import java.util.concurrent.atomic.AtomicBoolean;

public class BatchOperationState {

    private static final String TAG = "BatchOperationState";

    private static final MutableLiveData<Boolean> _isProcessing =
            new MutableLiveData<>(false);

    private static volatile AppScreen _sourceScreen = null;

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

    public static AppScreen getSourceScreen() {
        return _sourceScreen;
    }

    public static void setProcessing(boolean isProcessing) {
        if (!isProcessing) {
            _sourceScreen = null;
        }
        _isProcessing.postValue(isProcessing);
    }

    @Deprecated
    public static void setProcessing(boolean isProcessing, int sourceFragmentIndex) {
        if (isProcessing) {
            setSourceFragmentIndex(sourceFragmentIndex);
        } else {
            _sourceScreen = null;
        }
        _isProcessing.postValue(isProcessing);
        Log.d(TAG, "setProcessing=" + isProcessing + " | sourceScreen=" + _sourceScreen);
    }

    public static void setSourceScreen(AppScreen screen) {
        _sourceScreen = screen;
        Log.d(TAG, "setSourceScreen=" + screen);
    }

    @Deprecated
    public static void setSourceFragmentIndex(int index) {
        switch (index) {
            case 2:  _sourceScreen = AppScreen.LOCAL_FONTS;  break;
            case 3:  _sourceScreen = AppScreen.SYSTEM_FONTS; break;
            case 4:  _sourceScreen = AppScreen.FAVORITES;    break;
            case 5:  _sourceScreen = AppScreen.TRASH;        break;
            default: _sourceScreen = null;                   break;
        }
        Log.d(TAG, "setSourceFragmentIndex=" + index + " → _sourceScreen=" + _sourceScreen);
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
