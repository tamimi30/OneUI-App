package com.oneui.fontviewer.data.translation;

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

public class TranslationApi {

    private static final String TAG = "TranslationApi";
    private final Context context;

    public TranslationApi(Context context) {
        this.context = context;
    }

    // Translate text using Google Translate API
    public String translateText(String text, String sourceLang, String targetLang) {
        HttpURLConnection connection = null;
        try {
            String encodedText = URLEncoder.encode(text, "UTF-8");
            String urlString = String.format(
                "https://translate.googleapis.com/translate_a/single?client=dict-chrome-ex&sl=%s&tl=%s&dt=t&q=%s",
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
                Log.e(TAG, "Translation API returned error code: " + responseCode);
                return "ERROR_API";
            }
            
        } catch (java.io.IOException e) {
            // هذه تلتقط الحالة 2: متصل بالواي فاي ولكن لا يوجد انترنت فعلي
            Log.e(TAG, "Network error during translation: " + e.getMessage(), e);
            return "ERROR_NETWORK";
        } catch (Exception e) {
            // هذه تلتقط الحالة 3: أخطاء برمجية أو تغير في هيكل بيانات جوجل
            Log.e(TAG, "Failed to translate text: " + e.getMessage(), e);
            return "ERROR_API";
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public boolean isInternetAvailable() {
        try {
            ConnectivityManager connectivityManager =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivityManager == null) {
                return false;
            }
            NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnected();
        } catch (Exception e) {
            Log.e(TAG, "Error checking internet connection: " + e.getMessage());
            return false;
        }
    }
}
