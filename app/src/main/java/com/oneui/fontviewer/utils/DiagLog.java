package com.oneui.fontviewer.utils;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Looper;
import android.os.Process;
import android.util.Log;
import android.view.View;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * DiagLog - أداة تشخيص مؤقتة لمشكلة اللغة بعد تغيير الثيم.
 *
 * تكتب سجلاً زمنياً مفصلاً (في ملف داخل مساحة التطبيق) لكل ما يخص اللغة والثيم وإعادة بناء
 * الأنشطة: دورة حياة كل نشاط، اللغة التي بُني بها، من استدعى recreate()، وتغيّر إعدادات
 * العملية (Application) حين يدفعها النظام. لا تغيّر أي سلوك في التطبيق، فقط تسجّل.
 *
 * يمكن مشاركة التقرير أو مسحه من أسفل شاشة الإعدادات (قسم "تشخيص مؤقت").
 * لإزالتها لاحقاً: احذف هذا الملف واستدعاءات DiagLog.* وقسم "تشخيص مؤقت" من preferences.xml.
 */
public final class DiagLog {

    private static final String TAG = "DiagLog";
    private static final String FILE_NAME = "language_diagnostics.txt";

    // الحد الأقصى لحجم الملف؛ عند تجاوزه يُحذف النصف الأقدم
    private static final long MAX_BYTES = 150 * 1024;

    private static final Object LOCK = new Object();
    private static final SimpleDateFormat TIME_FORMAT =
            new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    private static File sFile;
    private static Context sAppContext;

    private DiagLog() {}

    public static void init(Context context) {
        synchronized (LOCK) {
            if (sFile != null) {
                return;
            }
            sAppContext = context.getApplicationContext();
            sFile = new File(sAppContext.getFilesDir(), FILE_NAME);
        }

        log("PROCESS", "================ PROCESS START pid=" + Process.myPid() + " ================");
        log("PROCESS", "device=" + Build.MANUFACTURER + " " + Build.MODEL
                + " | android=" + Build.VERSION.RELEASE + " (sdk " + Build.VERSION.SDK_INT + ")");
        log("PROCESS", "systemLocales=" + Resources.getSystem().getConfiguration().getLocales().toLanguageTags()
                + " | appLocales(LocaleManager)=" + appLocales(sAppContext));
    }

    public static void log(String tag, String message) {
        if (sFile == null) {
            return;
        }

        String line;
        synchronized (LOCK) {
            line = TIME_FORMAT.format(new Date()) + " [" + threadName() + "] " + tag + ": " + message;
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(sFile, true), StandardCharsets.UTF_8)) {
                writer.write(line);
                writer.write('\n');
            } catch (IOException e) {
                Log.w(TAG, "Failed to write diagnostics line", e);
            }
            trimIfNeeded();
        }
        Log.d(TAG, tag + ": " + message);
    }

    /** يقرأ التقرير كاملاً كنص. */
    public static String read() {
        if (sFile == null) {
            return "(DiagLog not initialized)";
        }
        synchronized (LOCK) {
            return readFileLocked();
        }
    }

    /** يمسح التقرير ويبدأ تقريراً جديداً. */
    public static void clear() {
        if (sFile == null) {
            return;
        }
        synchronized (LOCK) {
            try (FileOutputStream out = new FileOutputStream(sFile, false)) {
                out.write(new byte[0]);
            } catch (IOException e) {
                Log.w(TAG, "Failed to clear diagnostics file", e);
            }
        }
        log("REPORT", "---- report cleared; start the scenario now ----");
    }

    /** وصف إعدادات سياق معيّن: اللغة التي يعرضها فعلاً، وضع الليل، اتجاه الواجهة. */
    public static String describe(Context context) {
        try {
            Configuration cfg = context.getResources().getConfiguration();
            return "shownLocale=" + cfg.getLocales().toLanguageTags()
                    + " night=" + nightName(cfg.uiMode)
                    + " dir=" + (cfg.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ? "RTL" : "LTR");
        } catch (Throwable t) {
            return "describe-failed:" + t;
        }
    }

    public static String describe(Configuration cfg) {
        if (cfg == null) {
            return "null";
        }
        return "locales=" + cfg.getLocales().toLanguageTags()
                + " night=" + nightName(cfg.uiMode)
                + " dir=" + (cfg.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL ? "RTL" : "LTR");
    }

    /** لغة التطبيق المسجّلة في LocaleManager (مصدر الحقيقة من أندرويد 13). */
    public static String appLocales(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                android.app.LocaleManager localeManager =
                        context.getSystemService(android.app.LocaleManager.class);
                if (localeManager == null) {
                    return "LocaleManager=null";
                }
                android.os.LocaleList locales = localeManager.getApplicationLocales();
                return locales.isEmpty() ? "[empty = follow system]" : locales.toLanguageTags();
            } catch (Throwable t) {
                return "LocaleManager-failed:" + t;
            }
        }
        return "n/a (sdk<33)";
    }

    public static String idOf(Object object) {
        if (object == null) {
            return "null";
        }
        return object.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(object));
    }

    /** من استدعى هذه الدالة: آخر عدد من الإطارات (بدون إطارات DiagLog نفسها). */
    public static String callers(int max) {
        StackTraceElement[] stack = new Throwable().getStackTrace();
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (StackTraceElement element : stack) {
            String className = element.getClassName();
            if (className.equals(DiagLog.class.getName())) {
                continue;
            }
            if (count > 0) {
                sb.append(" < ");
            }
            sb.append(className.substring(className.lastIndexOf('.') + 1))
                    .append('.').append(element.getMethodName());
            if (++count >= max) {
                break;
            }
        }
        return sb.toString();
    }

    private static String nightName(int uiMode) {
        switch (uiMode & Configuration.UI_MODE_NIGHT_MASK) {
            case Configuration.UI_MODE_NIGHT_YES:
                return "YES";
            case Configuration.UI_MODE_NIGHT_NO:
                return "NO";
            default:
                return "UNDEFINED";
        }
    }

    private static String threadName() {
        return Looper.myLooper() == Looper.getMainLooper() ? "main" : Thread.currentThread().getName();
    }

    private static String readFileLocked() {
        try (InputStream in = new FileInputStream(sFile)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toString("UTF-8");
        } catch (IOException e) {
            return "(failed to read report: " + e + ")";
        }
    }

    private static void trimIfNeeded() {
        if (sFile.length() <= MAX_BYTES) {
            return;
        }
        String all = readFileLocked();
        int cut = all.indexOf('\n', all.length() / 2);
        String rest = cut >= 0 ? all.substring(cut + 1) : "";
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(sFile, false), StandardCharsets.UTF_8)) {
            writer.write("...(older lines trimmed)...\n");
            writer.write(rest);
        } catch (IOException e) {
            Log.w(TAG, "Failed to trim diagnostics file", e);
        }
    }
}
