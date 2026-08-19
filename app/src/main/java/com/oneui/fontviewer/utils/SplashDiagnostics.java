package com.oneui.fontviewer.utils;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SplashDiagnostics {

    private static final List<String> sEvents = new ArrayList<>();
    private static final long sStartNanos = System.nanoTime();

    public static synchronized void log(String message) {
        long msSinceStart = (System.nanoTime() - sStartNanos) / 1_000_000L;
        sEvents.add("+" + msSinceStart + "ms  " + message);
        Log.d("SplashDiagnostics", message);
    }

    public static synchronized void flush(Context context) {
        if (context == null || sEvents.isEmpty()) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;

        try {
            StringBuilder report = new StringBuilder();
            String ts = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date());
            report.append("Splash timeline: ").append(ts).append("\n\n");
            for (String e : sEvents) {
                report.append(e).append("\n");
            }

            String fileName = "splash_log_" + ts + ".txt";

            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/OneUIApp");

            Context appContext = context.getApplicationContext();
            Uri uri = appContext.getContentResolver()
                    .insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);

            if (uri != null) {
                try (OutputStream os = appContext.getContentResolver().openOutputStream(uri)) {
                    if (os != null) {
                        OutputStreamWriter ow = new OutputStreamWriter(os);
                        ow.write(report.toString());
                        ow.flush();
                    }
                }
            }

            sEvents.clear();
        } catch (Exception e) {
            Log.e("SplashDiagnostics", "Failed to write splash log", e);
        }
    }
    }
