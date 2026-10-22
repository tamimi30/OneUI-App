package com.oneui.fontviewer.metadata;

import android.content.Context;
import android.util.Log;

import com.oneui.fontviewer.R;

import java.io.File;
import java.io.RandomAccessFile;

public class FontWeightWidthExtractor {

    private static final String TAG = "FontWeightWidthExtractor";


    private static final String VF_PREFIX = "VF";

    // الصيغة المخزّنة: "وزن|عرض|متغير" (مثل 400|5|1)، ولا تتغير بتغير لغة التطبيق
    private static final String RAW_SEPARATOR = "|";


    public static String extract(File fontFile, int ttcIndex) {
        if (fontFile == null || !fontFile.exists() || !fontFile.canRead()) {
            return null;
        }
        try {
            int[] ww = readWeightWidth(fontFile, ttcIndex);
            if (ww == null) return null;

            boolean hasWeight = ww[0] > 0;
            boolean hasWidth  = hasWidthName(ww[1]);
            if (!hasWeight && !hasWidth) return null;

            boolean isVF = isVariableFont(fontFile, ttcIndex);

            return ww[0] + RAW_SEPARATOR + ww[1] + RAW_SEPARATOR + (isVF ? "1" : "0");

        } catch (Exception e) {
            Log.w(TAG, "extract() failed for " + fontFile.getName() + ": " + e.getMessage());
            return null;
        }
    }

    // تحويل الصيغة المخزّنة لنص يُعرض حسب لغة التطبيق. أي نص لا يطابق الصيغة
    // (مثل النصوص القديمة المخزّنة سابقاً) يُعاد كما هو
    public static String formatLabel(Context context, String raw) {
        if (raw == null || raw.isEmpty()) return raw;

        String[] parts = raw.split("\\|", -1);
        if (parts.length != 3) return raw;

        int weightClass;
        int widthClass;
        try {
            weightClass = Integer.parseInt(parts[0]);
            widthClass  = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return raw;
        }

        String label = buildLabel(getWeightName(context, weightClass), getWidthName(context, widthClass));
        if (label == null) return null;

        return "1".equals(parts[2]) ? VF_PREFIX + " • " + label : label;
    }

    private static int[] readWeightWidth(File fontFile, int ttcIndex) {
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(fontFile, "r");
            long fontOffset = resolveFontOffset(raf, ttcIndex);
            if (fontOffset < 0) return null;

            raf.seek(fontOffset + 4); 
            int numTables = raf.readUnsignedShort();
            raf.skipBytes(6); 

            long os2Offset = -1;
            for (int i = 0; i < numTables; i++) {
                byte[] tag = new byte[4];
                raf.read(tag);
                String t = new String(tag, "ISO-8859-1");
                raf.skipBytes(4); 
                long off = raf.readInt() & 0xFFFFFFFFL;
                raf.skipBytes(4); 

                if ("OS/2".equals(t)) {
                    os2Offset = off;
                    break;
                }
            }

            if (os2Offset < 0) return null;

            raf.seek(os2Offset + 2); 
            raf.skipBytes(2);        
            int weightClass = raf.readUnsignedShort();
            int widthClass  = raf.readUnsignedShort();

            return new int[]{weightClass, widthClass};

        } catch (Exception e) {
            Log.w(TAG, "readWeightWidth failed: " + e.getMessage());
            return null;
        } finally {
            if (raf != null) try { raf.close(); } catch (Exception ignored) {}
        }
    }


    private static boolean isVariableFont(File fontFile, int ttcIndex) {
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(fontFile, "r");
            long fontOffset = resolveFontOffset(raf, ttcIndex);
            if (fontOffset < 0) return false;

            raf.seek(fontOffset + 4);
            int numTables = raf.readUnsignedShort();
            raf.skipBytes(6);

            for (int i = 0; i < numTables; i++) {
                byte[] tag = new byte[4];
                raf.read(tag);
                String t = new String(tag, "ISO-8859-1");
                raf.skipBytes(12); 

                if ("fvar".equals(t)) return true;
            }
        } catch (Exception e) {
            Log.w(TAG, "isVariableFont check failed: " + e.getMessage());
        } finally {
            if (raf != null) try { raf.close(); } catch (Exception ignored) {}
        }
        return false;
    }


    private static long resolveFontOffset(RandomAccessFile raf, int ttcIndex) throws Exception {
        raf.seek(0);
        byte[] hdr = new byte[4];
        raf.read(hdr);
        String tag = new String(hdr, "US-ASCII");

        if ("ttcf".equals(tag)) {
            raf.skipBytes(4); 
            long numFonts = readUInt32(raf);
            if (ttcIndex < 0 || ttcIndex >= numFonts) return -1;
            raf.seek(12 + (long) ttcIndex * 4);
            return readUInt32(raf);
        }
        return 0L;
    }

    private static long readUInt32(RandomAccessFile raf) throws Exception {
        byte[] b = new byte[4];
        raf.read(b);
        return ((long)(b[0] & 0xFF) << 24) | ((long)(b[1] & 0xFF) << 16)
             | ((long)(b[2] & 0xFF) <<  8) |  (long)(b[3] & 0xFF);
    }

    private static String buildLabel(String weight, String width) {
        boolean hasWeight = weight != null && !weight.isEmpty();
        boolean hasWidth  = width  != null && !width.isEmpty();
        if (!hasWeight && !hasWidth) return null;
        if (!hasWeight) return width;
        if (!hasWidth)  return weight;
        return weight + " • " + width;
    }


    public static String getWeightName(Context context, int w) {
        if (w <= 0) return null;

        switch (w) {
            case 100: return context.getString(R.string.font_info_weight_thin);
            case 200: return context.getString(R.string.font_info_weight_extra_light);
            case 300: return context.getString(R.string.font_info_weight_light);
            case 400: return context.getString(R.string.font_info_weight_regular);
            case 500: return context.getString(R.string.font_info_weight_medium);
            case 600: return context.getString(R.string.font_info_weight_semi_bold);
            case 700: return context.getString(R.string.font_info_weight_bold);
            case 800: return context.getString(R.string.font_info_weight_extra_bold);
            case 900: return context.getString(R.string.font_info_weight_black);
            default: return context.getString(R.string.font_info_weight_other, w);
        }
    }

    public static String getWidthName(Context context, int d) {
        switch (d) {
            case 1: return context.getString(R.string.font_info_width_ultra_condensed);
            case 2: return context.getString(R.string.font_info_width_extra_condensed);
            case 3: return context.getString(R.string.font_info_width_condensed);
            case 4: return context.getString(R.string.font_info_width_semi_condensed);
            case 5: return null; 
            case 6: return context.getString(R.string.font_info_width_semi_expanded);
            case 7: return context.getString(R.string.font_info_width_expanded);
            case 8: return context.getString(R.string.font_info_width_extra_expanded);
            case 9: return context.getString(R.string.font_info_width_ultra_expanded);
            default: return null;
        }
    }

    private static boolean hasWidthName(int d) {
        return d >= 1 && d <= 9 && d != 5;
    }
}
