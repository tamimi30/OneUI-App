package com.oneui.fontviewer.data.repository;

import android.content.Context;
import android.util.Log;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.oneui.fontviewer.data.datastore.SettingsDataStore;
import com.oneui.fontviewer.data.datastore.TranslationDataStore;
import com.oneui.fontviewer.data.network.TranslationApi;

public class TranslationRepository {
    
    private static final String TAG = "TranslationRepository";
    private final Context context;
    private final TranslationDataStore translationCache;
    private final SettingsDataStore settingsDataStore;
    private final TranslationApi translationApi;
    
    public interface TranslationCallback {
        void onTranslationComplete(Map<String, String> translatedData);
        void onTranslationFailed(String error);
    }
    
    public TranslationRepository(Context context) {
        this.context = context;
        this.translationCache = TranslationDataStore.getInstance(context);
        this.settingsDataStore = SettingsDataStore.getInstance(context);
        this.translationApi = new TranslationApi(context);
    }
    
    public void translateMetadata(Map<String, String> metadata, TranslationCallback callback) {
        if (!isTranslationEnabled()) {
            callback.onTranslationComplete(metadata);
            return;
        }
        
        String targetLanguage = getCurrentLanguage();
        
        if (targetLanguage.equals("en")) {
            callback.onTranslationComplete(metadata);
            return;
        }
        
        new Thread(() -> {
            try {
                Map<String, String> translatedData = new HashMap<>(metadata);
                String translationErrorType = null;
                
                String[] fieldsToTranslate = {
                    "Copyright", 
                    "Trademark", 
                    "Description", 
                    "LicenseDescription"
                };
                
                for (String field : fieldsToTranslate) {
                    if (metadata.containsKey(field)) {
                        String originalText = metadata.get(field);
                        
                        if (originalText != null && !originalText.isEmpty() && originalText.length() < 5000) {
                            String cacheKey = generateCacheKey(originalText, targetLanguage);
                            
                            if (cacheKey == null) {
                                continue;
                            }
                            
                            String cachedTranslation = "";
                            try {
                                cachedTranslation = translationCache.getTranslation(cacheKey).blockingGet();
                            } catch (Exception e) {
                                Log.e(TAG, "Error reading from cache: " + e.getMessage());
                            }
                            
                            if (cachedTranslation != null && !cachedTranslation.isEmpty()) {
                                translatedData.put(field, cachedTranslation);
                                Log.d(TAG, "Using cached translation for field: " + field);
                            } else if (!translationApi.isInternetAvailable()) {
                                translationErrorType = "NO_INTERNET";
                                break;
                            } else {
                                String translatedText = translationApi.translateText(originalText, "en", targetLanguage);
                                
                                if ("ERROR_NETWORK".equals(translatedText)) {
                                    translationErrorType = "NO_INTERNET";
                                    break;
                                } else if ("ERROR_API".equals(translatedText) || translatedText == null || translatedText.isEmpty()) {
                                    translationErrorType = "API_ERROR";
                                    break;
                                } else {
                                    translationCache.saveTranslation(cacheKey, translatedText);
                                    translatedData.put(field, translatedText);
                                    Log.d(TAG, "Translated and cached field: " + field);
                                }
                                
                                Thread.sleep(100);
                            }
                        }
                    }
                }
                
                if ("NO_INTERNET".equals(translationErrorType)) {
                    callback.onTranslationFailed("NO_INTERNET");
                } else if ("API_ERROR".equals(translationErrorType)) {
                    callback.onTranslationFailed("API_ERROR");
                } else {
                    callback.onTranslationComplete(translatedData);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Translation failed: " + e.getMessage(), e);
                callback.onTranslationFailed(e.getMessage());
            }
        }).start();
    }

    private String getCurrentLanguage() {
        try {
            Locale currentLocale = SettingsRepository.getLocale(context);
            String language = currentLocale.getLanguage();
            
            if (language.equals("ar")) {
                return "ar";
            } else {
                return "en";
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting current language: " + e.getMessage());
            return "en";
        }
    }
    
    private String generateCacheKey(String text, String targetLang) {
        try {
            String combined = text.substring(0, Math.min(text.length(), 100)) + "_" + targetLang;
            return String.valueOf(combined.hashCode());
        } catch (Exception e) {
            Log.e(TAG, "Error generating cache key: " + e.getMessage());
            return null;
        }
    }
    
    public boolean isTranslationEnabled() {
        try {
            return settingsDataStore.getTranslationEnabled().blockingFirst();
        } catch (Exception e) {
            Log.e(TAG, "Error checking translation enabled: " + e.getMessage());
            return false;
        }
    }
}
