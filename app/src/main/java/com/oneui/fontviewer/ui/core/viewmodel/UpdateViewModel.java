package com.oneui.fontviewer.ui.core.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.oneui.fontviewer.data.update.InstallSourceResolver;
import com.oneui.fontviewer.data.update.UpdateRepository;
import com.oneui.fontviewer.data.update.model.InstallSource;
import com.oneui.fontviewer.data.update.model.UpdateCheckState;
import com.oneui.fontviewer.data.update.model.UpdateInfo;

/**
 * الواجهة الوحيدة بين شاشات التطبيق (الرئيسية، الإعدادات، حول التطبيق) ومسار التحديثات.
 *
 * حالة التحديث نفسها (التحديث المتوفر، حالة التحقق، إخفاء البطاقة، جلسة الإعدادات) مشتركة على مستوى التطبيق
 * ومحفوظة في UpdateRepository، لذلك لا يحتفظ هذا الـ ViewModel بأي حالة خاصة به ويكتفي بتمرير الاستدعاءات.
 */
public class UpdateViewModel extends AndroidViewModel {

    private final UpdateRepository repository;

    public UpdateViewModel(@NonNull Application application) {
        super(application);
        repository = UpdateRepository.getInstance(application);
    }

    // ───────────────────────── الحالة المراقَبة ─────────────────────────

    /** التحديث المتوفر حالياً، أو null. */
    @NonNull
    public LiveData<UpdateInfo> getAvailableUpdate() {
        return repository.getAvailableUpdate();
    }

    @NonNull
    public LiveData<UpdateCheckState> getCheckState() {
        return repository.getCheckState();
    }

    @Nullable
    public UpdateInfo getAvailableUpdateNow() {
        return repository.getAvailableUpdateNow();
    }

    public boolean isUpdateAvailable() {
        return repository.isUpdateAvailable();
    }

    /** المتجر الذي ثُبّت منه التطبيق، وعليه تعتمد وجهة زر التحديث. */
    @NonNull
    public InstallSource getInstallSource() {
        return InstallSourceResolver.resolve(getApplication());
    }

    // ───────────────────────── طلبات التحقق ─────────────────────────

    public void checkForUpdateOnLaunch() {
        repository.checkForUpdateOnLaunch();
    }

    public void requestAboutPageCheck() {
        repository.requestAboutPageCheck();
    }

    public void retryCheck() {
        repository.retryCheck();
    }

    // ───────────────────────── جلسة الإعدادات وبطاقة التحديث ─────────────────────────

    public void beginSettingsSession() {
        repository.beginSettingsSession();
    }

    public boolean isUpdateCardDismissed() {
        return repository.isUpdateCardDismissed();
    }

    public void dismissUpdateCard() {
        repository.dismissUpdateCard();
    }
}
