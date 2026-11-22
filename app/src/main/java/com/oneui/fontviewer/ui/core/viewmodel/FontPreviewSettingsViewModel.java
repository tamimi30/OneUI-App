package com.oneui.fontviewer.ui.core.viewmodel;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import com.oneui.fontviewer.data.AppDataStore;

/**
 * القيمتان المشتركتان بين عدة شاشات من إعدادات التطبيق (للقراءة فقط):
 *  - {@link #getFontPreviewEnabled()} : تستخدمها قوائم الخطوط (المحلية / النظام / المفضلة)
 *  - {@link #getPreviewText()}        : يستخدمها عارض الخط
 *
 * الكتابة تتم فقط من شاشة الإعدادات عبر SettingsViewModel، ومصدر الحقيقة هو AppDataStore،
 * فأي تغيير هناك يصل إلى هذا الـ ViewModel تلقائيًا دون ربط مباشر بين الشاشتين.
 */
public class FontPreviewSettingsViewModel extends AndroidViewModel {

    private static final String TAG = "FontPreviewSettingsVM";

    private final CompositeDisposable disposables = new CompositeDisposable();

    private final MutableLiveData<Boolean> fontPreviewEnabled = new MutableLiveData<>();
    private final MutableLiveData<String> previewText = new MutableLiveData<>();

    public FontPreviewSettingsViewModel(@NonNull Application application) {
        super(application);

        AppDataStore dataStore = AppDataStore.getInstance(application);

        disposables.add(
            dataStore.getFontPreviewEnabled()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    fontPreviewEnabled::setValue,
                    error -> Log.e(TAG, "Error observing font preview", error)
                )
        );

        disposables.add(
            dataStore.getPreviewText()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    previewText::setValue,
                    error -> Log.e(TAG, "Error observing preview text", error)
                )
        );
    }

    public LiveData<Boolean> getFontPreviewEnabled() {
        return fontPreviewEnabled;
    }

    public LiveData<String> getPreviewText() {
        return previewText;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        disposables.clear();
    }
}
