package com.oneui.fontviewer.ui.appscreen.fontviewer;

import android.app.Application;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import com.oneui.fontviewer.data.fontviewer.FontPrefetcher;
import com.oneui.fontviewer.data.fontviewer.FontViewerRepository;
import com.oneui.fontviewer.utils.variable.InstanceLabelResolver;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * الواجهة الوحيدة بين FontViewerFragment وطبقة البيانات. يملك الخيط الخلفي الذي تُنفَّذ عليه العمليات الثقيلة
 * (تحميل الخط، نسخه من Uri، قراءة آخر خط)، ويسلّم نتائجها الى الـ Fragment على الخيط الرئيسي عبر {@link Callback}.
 *
 * الـ ViewModel يبقى حياً عند تدوير الشاشة بينما يُعاد إنشاء الـ Fragment، لذلك على كل Callback
 * أن يتأكد أن الـ Fragment ما زال مرتبطاً (isAdded) قبل لمس الواجهة.
 */
public class FontViewerViewModel extends AndroidViewModel {

    private static final String TAG = "FontViewerViewModel";

    /** يُستدعى دائماً على الخيط الرئيسي بنتيجة العملية الخلفية. */
    public interface Callback<T> {
        void onResult(T result);
    }

    private final FontViewerRepository repository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public FontViewerViewModel(@NonNull Application application) {
        super(application);
        repository = new FontViewerRepository(application);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executor.shutdownNow();
        mainHandler.removeCallbacksAndMessages(null);
    }

    // ───────────────────────── عمليات خلفية ─────────────────────────

    /** يحمّل الخط بمحاوره (انظر FontViewerRepository.loadFont لحالات تحديد قيم المحاور). */
    public void loadFont(String path, int ttcIndex, boolean systemFont,
                         Map<String, Float> restoreAxisValues, boolean resetToDefaults,
                         InstanceLabelResolver labels, Callback<FontViewerRepository.LoadResult> callback) {
        // نسخة مستقلة: الخريطة الأصلية يعدّلها الـ Fragment على الخيط الرئيسي بينما نقرؤها نحن في الخلفية
        final Map<String, Float> restore = restoreAxisValues != null ? new LinkedHashMap<>(restoreAxisValues) : null;
        runAsync(() -> repository.loadFont(path, ttcIndex, systemFont, restore, resetToDefaults, labels), callback);
    }

    /** يعيد بناء Typeface الخط بقيم المحاور المعطاة. تصل null اذا تعذّر البناء. */
    public void rebuildTypeface(String path, Map<String, Float> axisValues, int ttcIndex,
                                boolean systemFont, Callback<Typeface> callback) {
        final Map<String, Float> snapshot = new LinkedHashMap<>(axisValues);
        runAsync(() -> FontViewerRepository.createTypeface(path, new File(path), snapshot, ttcIndex, systemFont), callback);
    }

    /** ينسخ الخط القادم من Uri ويستخرج بياناته. تصل null اذا فشل النسخ. */
    public void importFont(Uri uri, String fileName, Callback<FontViewerRepository.ImportedFont> callback) {
        runAsync(() -> repository.importFont(uri, fileName), callback);
    }

    /** يقرأ آخر خط عُرض. تصل null اذا لم يوجد. */
    public void loadLastViewedFont(Callback<FontViewerRepository.LastViewedFont> callback) {
        runAsync(repository::loadLastViewedFont, callback);
    }

    private <T> void runAsync(Supplier<T> work, Callback<T> callback) {
        executor.execute(() -> {
            T value;
            try {
                value = work.get();
            } catch (Throwable t) {
                Log.e(TAG, "Background font operation failed", t);
                return;
            }
            final T result = value;
            mainHandler.post(() -> callback.onResult(result));
        });
    }

    // ───────────────────────── عمليات سريعة على الخيط الرئيسي ─────────────────────────

    public String getRealPathFromUri(Uri uri) {
        return repository.getRealPathFromUri(uri);
    }

    public String resolveOriginalPath(String pathOrUri) {
        return repository.resolveOriginalPath(pathOrUri);
    }

    public void saveLastViewedFont(String path, String fileName, String realName, String originalPath) {
        repository.saveLastViewedFont(path, fileName, realName, originalPath);
    }

    public void saveFontAxisValue(String axisTag, float value) {
        repository.saveFontAxisValue(axisTag, value);
    }

    public void saveFontAxisValues(Map<String, Float> axisValues) {
        repository.saveFontAxisValues(axisValues);
    }

    public String getPreviewText() {
        return repository.getPreviewText();
    }

    public Map<String, String> buildMetadata(String fontPath, String originalFontPath, String fileName,
                                             String realName, int ttcIndex, String unknownFontLabel) {
        return repository.buildMetadata(fontPath, originalFontPath, fileName, realName, ttcIndex, unknownFontLabel);
    }

    // ───────────────────────── التجهيز المسبق ─────────────────────────

    public FontPrefetcher.PrefetchedFont takePrefetched(String localeTags, String path, int ttcIndex, boolean systemFont) {
        return FontPrefetcher.take(localeTags, path, ttcIndex, systemFont);
    }

    public Typeface getCachedTypeface(String path, int ttcIndex, boolean systemFont) {
        return FontPrefetcher.getCachedTypeface(path, ttcIndex, systemFont);
    }
}
