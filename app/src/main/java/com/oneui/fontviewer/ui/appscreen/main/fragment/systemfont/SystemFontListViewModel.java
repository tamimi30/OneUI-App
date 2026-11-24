package com.oneui.fontviewer.ui.appscreen.main.fragment.systemfont;

import android.app.Application;
import android.graphics.Typeface;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.oneui.fontviewer.data.common.model.FontItem;
import com.oneui.fontviewer.data.settings.SettingsRepository;
import com.oneui.fontviewer.data.systemfont.SystemFontCache;
import com.oneui.fontviewer.data.systemfont.SystemFontPreferenceManager;
import com.oneui.fontviewer.data.systemfont.SystemFontRepository;

import java.util.List;

public class SystemFontListViewModel extends AndroidViewModel {
    
    private static final String TAG = "SystemFontListViewModel";
    
    private final SystemFontRepository repository;
    // يُنشأ عند أول استخدام (وليس مع الـ ViewModel): قراءة آخر خط مفتوح تتم على الخيط الرئيسي،
    // ويجب أن تبقى في نفس توقيت إنشاء قائمة الخطوط كما كانت
    private SystemFontPreferenceManager preferenceManager;
    private final LiveData<List<FontItem>> fontsLiveData;
    private final MutableLiveData<Boolean> isLoadingLiveData;
    private final MutableLiveData<String> errorMessageLiveData;
    private final MutableLiveData<Boolean> isApiAvailableLiveData;
    
    public SystemFontListViewModel(@NonNull Application application) {
        super(application);
        
        repository = SystemFontRepository.getInstance(application);
        fontsLiveData = repository.getSystemFonts();
        isLoadingLiveData = new MutableLiveData<>(false);
        errorMessageLiveData = new MutableLiveData<>();
        isApiAvailableLiveData = new MutableLiveData<>(checkApiAvailability());
    }
    
    public LiveData<List<FontItem>> getFontsLiveData() {
        return fontsLiveData;
    }
     
    public LiveData<Boolean> getIsLoadingLiveData() {
        return isLoadingLiveData;
    } 
    
    public LiveData<Boolean> getIsApiAvailableLiveData() {
        return isApiAvailableLiveData;
    }
    
    private boolean checkApiAvailability() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q;
    }
    
    public void loadSystemFonts() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            errorMessageLiveData.postValue("SystemFonts API requires Android 10 (API 29) or higher");
            isApiAvailableLiveData.postValue(false);
            return;
        }
        
        boolean hasExistingData = fontsLiveData.getValue() != null && !fontsLiveData.getValue().isEmpty();
        
        if (!hasExistingData) {
            isLoadingLiveData.postValue(true);
        }
        
        repository.loadAndSyncSystemFonts(new SystemFontRepository.OnSyncCompleteListener() {
            @Override
            public void onSyncComplete(int added, int updated, int deleted) {
                if (!hasExistingData) {
                    isLoadingLiveData.postValue(false);
                }
                
                String message = String.format("Synced: %d added, %d updated, %d deleted", 
                    added, updated, deleted);
                Log.d(TAG, message);
                
                if (added == 0 && updated == 0 && deleted == 0) {
                    Log.d(TAG, "No changes in system fonts");
                }
            }
        });
    } 
    
    public void recordFontAccess(String fontPath) {
        if (fontPath != null && !fontPath.isEmpty()) {
            repository.recordAccess(fontPath);
        }
    }

    // ───────────────────────── آخر خط مفتوح ─────────────────────────

    private SystemFontPreferenceManager preferences() {
        if (preferenceManager == null) {
            preferenceManager = new SystemFontPreferenceManager(getApplication());
        }
        return preferenceManager;
    }

    public String getLastOpenedFont() {
        return preferences().getLastOpenedFont();
    }

    public void saveLastOpenedFont(String fontPath) {
        preferences().saveLastOpenedFont(fontPath);
    }

    public boolean isLastOpenedFont(String fontPath) {
        return preferences().isLastOpenedFont(fontPath);
    }

    // ───────────────────────── معاينة الخطوط ─────────────────────────

    public boolean isFontPreviewEnabled() {
        return SettingsRepository.isFontPreviewEnabled(getApplication());
    }

    public Typeface getCachedTypeface(String fontPath) {
        return SystemFontCache.getInstance().getIfCached(fontPath);
    }

    // قد تكون ثقيلة اذا لم يكن الخط في الكاش، فتُستدعى من خيط خلفي
    public Typeface loadTypeface(String fontPath) {
        return SystemFontCache.getInstance().getTypeface(fontPath);
    }

    public void preloadFonts(List<String> fontPaths) {
        SystemFontCache.getInstance().preloadFonts(fontPaths);
    }
    
}
