package com.oneui.fontviewer.metadata;

import android.util.Log;

import java.io.File;
import java.io.RandomAccessFile;

public class FontWeightWidthExtractor {

    private static final String TAG = "FontWeightWidthExtractor";


    private static final String VF_PREFIX = "VF";


    public static String extract(File fontFile, int ttcIndex) {
        if (fontFile == null || !fontFile.exists() || !fontFile.canRead()) {
            return null;
        }
        try {
            int[] ww = readWeightWidth(fontFile, ttcIndex);
            if (ww == null) return null;

            boolean isVF  = isVariableFont(fontFile, ttcIndex);
            String weight = getWeightName(ww[0]);
            String width  = getWidthName(ww[1]);
            String label  = buildLabel(weight, width);
            
            if (label == null) return null;

            return isVF ? VF_PREFIX + " • " + label : label;

        } catch (Exception e) {
            Log.w(TAG, "extract() failed for " + fontFile.getName() + ": " + e.getMessage());
            return null;
        }
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


    public static String getWeightName(int w) {
        if (w <= 0)   return null;
        if (w <= 150) return "Thin";
        if (w <= 250) return "Extra Light";
        if (w <= 350) return "Light";
        if (w <= 450) return "Regular";
        if (w <= 550) return "Medium";
        if (w <= 650) return "Semi Bold";
        if (w <= 750) return "Bold";
        if (w <= 850) return "Extra Bold";
        return "Black";
    }

    public static String getWidthName(int d) {
        switch (d) {
            case 1: return "Ultra Condensed";
            case 2: return "Extra Condensed";
            case 3: return "Condensed";
            case 4: return "Semi Condensed";
            case 5: return null; 
            case 6: return "Semi Expanded";
            case 7: return "Expanded";
            case 8: return "Extra Expanded";
            case 9: return "Ultra Expanded";
            default: return null;
        }
    }
}
