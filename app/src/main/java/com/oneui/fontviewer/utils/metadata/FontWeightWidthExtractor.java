package com.oneui.fontviewer.utils.metadata;

import android.util.Log;

import java.io.File;
import java.io.RandomAccessFile;

public class FontWeightWidthExtractor {

    private static final String TAG = "FontWeightWidthExtractor";


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

    /**
     * الصيغة المخزّنة بعد تحليلها: فئة الوزن وفئة العرض (من جدول OS/2) وهل الخط متغير.
     * تحويلها لنص معروض حسب لغة التطبيق يتم في طبقة الواجهة (FontLabelFormatter).
     */
    public static final class ParsedLabel {
        public final int weightClass;
        public final int widthClass;
        public final boolean variable;

        ParsedLabel(int weightClass, int widthClass, boolean variable) {
            this.weightClass = weightClass;
            this.widthClass = widthClass;
            this.variable = variable;
        }
    }

    // تحليل الصيغة المخزّنة. أي نص لا يطابق الصيغة (مثل النصوص القديمة المخزّنة سابقاً) يُعاد null
    public static ParsedLabel parse(String raw) {
        if (raw == null || raw.isEmpty()) return null;

        String[] parts = raw.split("\\|", -1);
        if (parts.length != 3) return null;

        int weightClass;
        int widthClass;
        try {
            weightClass = Integer.parseInt(parts[0]);
            widthClass  = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return null;
        }

        return new ParsedLabel(weightClass, widthClass, "1".equals(parts[2]));
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

    private static boolean hasWidthName(int d) {
        return d >= 1 && d <= 9 && d != 5;
    }
}
