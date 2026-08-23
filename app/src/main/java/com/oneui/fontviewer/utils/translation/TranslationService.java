package com.oneui.fontviewer.utils.translation;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.oneui.fontviewer.fragment.settings.datastore.SettingsDataStore;
import com.oneui.fontviewer.fragment.settings.utils.SettingsHelper;

public class TranslationService {
    
    private static final String TAG = "TranslationService";
    private final Context context;
    private final TranslationDataStore translationCache;
    private final SettingsDataStore settingsDataStore;
    
    public interface TranslationCallback {
        void onTranslationComplete(Map<String, String> translatedData);
        void onTranslationFailed(String error);
    }
    
    public TranslationService(Context context) {
        this.context = context;
        this.translationCache = TranslationDataStore.getInstance(context);
        this.settingsDataStore = SettingsDataStore.getInstance(context);
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
                boolean networkNeededButUnavailable = false;
                boolean translationApiFailed = false;
                
                String[] fieldsToTranslate = {
                    "Copyright", 
                    "Trademark", 
                    "Description", 
                    "LicenseDescription", 
                    "SupportedScripts"
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
                            } else if (!isInternetAvailable()) {
                                networkNeededButUnavailable = true;
                            } else {
                                boolean[] isConnectivityIssue = new boolean[]{false};
                                String translatedText = translateText(originalText, "en", targetLanguage, isConnectivityIssue);
                                
                                if (translatedText != null && !translatedText.isEmpty()) {
                                    translationCache.saveTranslation(cacheKey, translatedText);
                                    translatedData.put(field, translatedText);
                                    Log.d(TAG, "Translated and cached field: " + field);
                                } else if (isConnectivityIssue[0]) {
                                    Log.w(TAG, "Real connectivity issue detected for field: " + field);
                                    networkNeededButUnavailable = true;
                                    break;
                                } else {
                                    Log.e(TAG, "Translation API call failed for field: " + field);
                                    translationApiFailed = true;
                                    break;
                                }
                                
                                Thread.sleep(100);
                            }
                        }
                    }
                }
                
                if (networkNeededButUnavailable) {
                    callback.onTranslationFailed("NO_INTERNET");
                } else if (translationApiFailed) {
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

    // Translate text using Google Translate API
    private String translateText(String text, String sourceLang, String targetLang, boolean[] isConnectivityIssue) {
        HttpURLConnection connection = null;
        try {
            String encodedText = URLEncoder.encode(text, "UTF-8");
            String urlString = String.format(
                "https://translate.googleapis.com/translate_a/single?client=gtx&sl=%s&tl=%s&dt=t&q=%s",
                sourceLang, targetLang, encodedText
            );
            
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            
            int responseCode = connection.getResponseCode();
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader br = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), "UTF-8"));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
                br.close();
                
                JSONArray jsonArray = new JSONArray(response.toString());
                JSONArray translationsArray = jsonArray.getJSONArray(0);
                
                StringBuilder translatedText = new StringBuilder();
                for (int i = 0; i < translationsArray.length(); i++) {
                    JSONArray translation = translationsArray.getJSONArray(i);
                    translatedText.append(translation.getString(0));
                }
                
                return translatedText.toString();
            } else {
                String errorBody = "";
                try {
                    java.io.InputStream errStream = connection.getErrorStream();
                    if (errStream != null) {
                        BufferedReader ebr = new BufferedReader(new InputStreamReader(errStream, "UTF-8"));
                        StringBuilder sb = new StringBuilder();
                        String l;
                        while ((l = ebr.readLine()) != null) sb.append(l);
                        ebr.close();
                        errorBody = sb.toString();
                    }
                } catch (Exception ignored) {}
                Log.e(TAG, "Translation API returned error code: " + responseCode + " body: " + errorBody);
                return null;
            }
            
        } catch (java.net.UnknownHostException | java.net.ConnectException
                | java.net.NoRouteToHostException | java.net.SocketTimeoutException e) {
            // فشل الوصول للسيرفر أصلاً (لا يوجد إنترنت فعلي رغم أن الواي فاي/البيانات مفعّلة)
            Log.e(TAG, "Real connectivity failure while translating: " + e.getMessage(), e);
            if (isConnectivityIssue != null && isConnectivityIssue.length > 0) {
                isConnectivityIssue[0] = true;
            }
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Failed to translate text: " + e.getMessage(), e);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
    
    private String getCurrentLanguage() {
        try {
            Locale currentLocale = SettingsHelper.getLocale(context);
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

    private boolean isInternetAvailable() {
        try {
            ConnectivityManager connectivityManager =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager == null) {
                return false;
            }
            android.net.Network network = connectivityManager.getActiveNetwork();
            if (network == null) {
                return false;
            }
            android.net.NetworkCapabilities capabilities =
                    connectivityManager.getNetworkCapabilities(network);
            if (capabilities == null) {
                return false;
            }
            // NET_CAPABILITY_VALIDATED يعني أن النظام تحقق فعلياً من وجود إنترنت خارجي،
            // وليس فقط أن الواجهة (واي فاي/بيانات) متصلة بشكل محلي
            return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        } catch (Exception e) {
            Log.e(TAG, "Error checking internet connection: " + e.getMessage());
            return false;
        }
    }
    }
