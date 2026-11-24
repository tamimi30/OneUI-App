package com.oneui.fontviewer.ui.appscreen.fontviewer;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import com.oneui.fontviewer.data.translation.TranslationRepository;

import java.util.Map;

/**
 * الواجهة الوحيدة بين FontViewerActivity ومسار ترجمة بيانات الخط الوصفية.
 */
public class FontTranslationViewModel extends AndroidViewModel {

    /**
     * نتيجة الترجمة. قد تصل من خيط خلفي، فعلى المستدعي الانتقال للخيط الرئيسي قبل لمس الواجهة.
     */
    public interface Callback {
        void onComplete(Map<String, String> translatedData);

        /** error هو NO_INTERNET أو API_ERROR أو رسالة خطأ أخرى. */
        void onFailed(String error);
    }

    private final TranslationRepository repository;

    public FontTranslationViewModel(@NonNull Application application) {
        super(application);
        repository = new TranslationRepository(application);
    }

    public boolean isTranslationEnabled() {
        return repository.isTranslationEnabled();
    }

    public void translateMetadata(Map<String, String> metadata, Callback callback) {
        repository.translateMetadata(metadata, new TranslationRepository.TranslationCallback() {
            @Override
            public void onTranslationComplete(Map<String, String> translatedData) {
                callback.onComplete(translatedData);
            }

            @Override
            public void onTranslationFailed(String error) {
                callback.onFailed(error);
            }
        });
    }
}
