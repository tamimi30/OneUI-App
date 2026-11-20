package com.oneui.fontviewer.data.common.variable;

import android.graphics.Typeface;
import android.graphics.fonts.Font;
import java.io.File;
import java.util.Map;

/**
 * Creates Typeface instances for variable fonts by applying axis values
 * (font-variation-settings).
 */
public class VariableTypefaceFactory {

    private static final String TAG = "VariableTypefaceFactory";

    public static Typeface createTypefaceWithWeight(File fontFile, float weight, int ttcIndex) {
        
        
        try {
            Font.Builder fontBuilder = new Font.Builder(fontFile);
            
            if (ttcIndex > 0) {
                fontBuilder.setTtcIndex(ttcIndex);
                android.util.Log.d(TAG, "Set TTC index: " + ttcIndex);
            }
            
            if (weight > 0) {
                String variationSettings = "'wght' " + weight;
                fontBuilder.setFontVariationSettings(variationSettings);
                android.util.Log.d(TAG, "Set variation settings: " + variationSettings);
            }
            
            Font font = fontBuilder.build();
            
            Typeface.CustomFallbackBuilder fallbackBuilder = 
                new Typeface.CustomFallbackBuilder(
                    new android.graphics.fonts.FontFamily.Builder(font).build()
                );
            
            Typeface result = fallbackBuilder.build();
            android.util.Log.d(TAG, "Successfully created variable Typeface with weight: " + weight + 
                                   ", ttcIndex: " + ttcIndex);
            
            return result;
            
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to create variable Typeface with weight: " + weight + 
                                    ", ttcIndex: " + ttcIndex, e);
            
            try {
                return Typeface.createFromFile(fontFile);
            } catch (Exception ex) {
                return null;
            }
        }
    }

    /**
     * يبني نص إعدادات تنويع الخط (font-variation-settings) من خريطة قيم المحاور،
     * بالصيغة التي يتوقعها Font.Builder.setFontVariationSettings في اندرويد، مثل:
     * "'wght' 700, 'wdth' 100, 'GRAD' 50, 'ROND' 100, 'ital' 1, 'MONO' 1"
     */
    public static String buildVariationSettingsString(Map<String, Float> axisValues) {
        if (axisValues == null || axisValues.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Float> entry : axisValues.entrySet()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append("'").append(entry.getKey()).append("' ").append(entry.getValue());
        }

        return builder.toString();
    }

    /**
     * ينشئ Typeface بتطبيق عدة محاور دفعة واحدة (وزن، عرض، ميل، تدرج، استدارة، تباعد أحادي...)،
     * بنفس فلسفة createTypefaceWithWeight لكن بشكل عام يدعم أي عدد من المحاور معاً.
     */
    public static Typeface createTypefaceWithAxes(File fontFile, Map<String, Float> axisValues, int ttcIndex) {
        try {
            Font.Builder fontBuilder = new Font.Builder(fontFile);

            if (ttcIndex > 0) {
                fontBuilder.setTtcIndex(ttcIndex);
                android.util.Log.d(TAG, "Set TTC index: " + ttcIndex);
            }

            String variationSettings = buildVariationSettingsString(axisValues);
            if (!variationSettings.isEmpty()) {
                fontBuilder.setFontVariationSettings(variationSettings);
                android.util.Log.d(TAG, "Set variation settings: " + variationSettings);
            }

            Font font = fontBuilder.build();

            Typeface.CustomFallbackBuilder fallbackBuilder =
                new Typeface.CustomFallbackBuilder(
                    new android.graphics.fonts.FontFamily.Builder(font).build()
                );

            Typeface result = fallbackBuilder.build();
            android.util.Log.d(TAG, "Successfully created variable Typeface with axes: " + axisValues +
                                   ", ttcIndex: " + ttcIndex);

            return result;

        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to create variable Typeface with axes: " + axisValues +
                                    ", ttcIndex: " + ttcIndex, e);

            try {
                return Typeface.createFromFile(fontFile);
            } catch (Exception ex) {
                return null;
            }
        }
    }
}
