package com.oneui.fontviewer.fragment.settings.viewmodel;

import android.app.Application;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

import com.oneui.fontviewer.fragment.settings.datastore.SettingsDataStore;
import com.oneui.fontviewer.fragment.settings.utils.SettingsHelper;

public class SettingsViewModel extends AndroidViewModel {

    private static final String TAG = "SettingsViewModel";
    
    private final SettingsDataStore dataStore;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private final MutableLiveData<Integer> languageMode = new MutableLiveData<>();
    private final MutableLiveData<Integer> themeMode = new MutableLiveData<>();
    private final MutableLiveData<Boolean> themeAuto = new MutableLiveData<>();
    
    private final MutableLiveData<Boolean> fontPreviewEnabled = new MutableLiveData<>();
    private final MutableLiveData<Boolean> translationEnabled = new MutableLiveData<>();
    private final MutableLiveData<Boolean> notificationsEnabled = new MutableLiveData<>();

    private final MutableLiveData<String> previewText = new MutableLiveData<>();
    
    private final MutableLiveData<SettingsEvent> settingsEvent = new MutableLiveData<>();

    public SettingsViewModel(@NonNull Application application) {
        super(application);
        
        dataStore = SettingsDataStore.getInstance(application);
        
        observeDataStore();
    }

    private void observeDataStore() {
        disposables.add(
            dataStore.getLanguageMode()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    languageMode::setValue,
                    error -> Log.e(TAG, "Error observing language mode", error)
                )
        );

        disposables.add(
            dataStore.getThemeMode()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    themeMode::setValue,
                    error -> Log.e(TAG, "Error observing theme mode", error)
                )
        );

        disposables.add(
            dataStore.getThemeAuto()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    themeAuto::setValue,
                    error -> Log.e(TAG, "Error observing theme auto", error)
                )
        );

        

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
            dataStore.getTranslationEnabled()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    translationEnabled::setValue,
                    error -> Log.e(TAG, "Error observing translation", error)
                )
        );

        disposables.add(
            dataStore.getNotificationsEnabled()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    notificationsEnabled::setValue,
                    error -> Log.e(TAG, "Error observing notifications", error)
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


    public LiveData<Integer> getLanguageMode() {
        return languageMode;
    }

    public LiveData<Integer> getThemeMode() {
        return themeMode;
    }

    public LiveData<Boolean> getThemeAuto() {
        return themeAuto;
    }

    public LiveData<Boolean> getFontPreviewEnabled() {
        return fontPreviewEnabled;
    }

    public LiveData<Boolean> getTranslationEnabled() {
        return translationEnabled;
    }

    public LiveData<Boolean> getNotificationsEnabled() {
        return notificationsEnabled;
    }

    

    public LiveData<String> getPreviewText() {
        return previewText;
    }

    public LiveData<SettingsEvent> getSettingsEvent() {
        return settingsEvent;
    }


    public void setLanguageMode(int mode) {
        if (languageMode.getValue() != null && languageMode.getValue() == mode) {
            return;
        }

        disposables.add(
            dataStore.setLanguageMode(mode)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    preferences -> {
                        Log.d(TAG, "Language mode saved to DataStore: " + mode);

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            android.app.LocaleManager localeManager = getApplication().getSystemService(android.app.LocaleManager.class);
                            if (localeManager != null) {
                                if (mode == SettingsHelper.LANGUAGE_ARABIC) {
                                    localeManager.setApplicationLocales(android.os.LocaleList.forLanguageTags("ar"));
                                } else if (mode == SettingsHelper.LANGUAGE_ENGLISH) {
                                    localeManager.setApplicationLocales(android.os.LocaleList.forLanguageTags("en"));
                                } else {
                                    localeManager.setApplicationLocales(android.os.LocaleList.getEmptyLocaleList());
                                }
                            }
                        } else {
                            java.util.Locale locale;
                            if (mode == SettingsHelper.LANGUAGE_ARABIC) {
                                locale = new java.util.Locale("ar");
                            } else if (mode == SettingsHelper.LANGUAGE_ENGLISH) {
                                locale = new java.util.Locale("en");
                            } else {
                                locale = android.content.res.Resources.getSystem().getConfiguration().getLocales().get(0);
                            }
                            java.util.Locale.setDefault(locale);
                        }

                        settingsEvent.setValue(new SettingsEvent(SettingsEventType.RECREATE_ALL_ACTIVITIES));
                    },
                    error -> Log.e(TAG, "Error setting language mode", error)
                )
        );
    }

    public void syncLanguageModeFromSystem(int mode) {
        disposables.add(
            dataStore.setLanguageMode(mode)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    preferences -> Log.d(TAG, "Language mode synced from system to DataStore: " + mode),
                    error -> Log.e(TAG, "Error syncing language mode from system", error)
                )
        );
    }

    

    public void setThemeMode(int mode) {
        if (themeMode.getValue() != null && themeMode.getValue() == mode) {
            return;
        }
        
        Log.d(TAG, "Setting theme mode to: " + mode);
        
        disposables.add(
            dataStore.setThemeAuto(false)
                .flatMap(prefs -> dataStore.setThemeMode(mode))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    preferences -> {
                        Log.d(TAG, "Theme mode updated and auto disabled");
                        SettingsHelper.applyTheme(getApplication());
                    },
                    error -> Log.e(TAG, "Error setting theme mode", error)
                )
        );
    }

    public void setThemeAuto(boolean enabled) {
        if (themeAuto.getValue() != null && themeAuto.getValue() == enabled) {
            return;
        }
        
        Log.d(TAG, "Setting theme auto to: " + enabled);
        
        disposables.add(
            dataStore.setThemeAuto(enabled)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    preferences -> {
                        Log.d(TAG, "Theme auto updated");
                        SettingsHelper.applyTheme(getApplication());
                    },
                    error -> Log.e(TAG, "Error setting theme auto", error)
                )
        );
    }

    

    public void setFontPreviewEnabled(boolean enabled) {
        if (fontPreviewEnabled.getValue() != null && fontPreviewEnabled.getValue() == enabled) {
            return;
        }
        
        disposables.add(
            dataStore.setFontPreviewEnabled(enabled)
                .subscribeOn(Schedulers.io())
                .subscribe(
                    preferences -> Log.d(TAG, "Font preview updated to: " + enabled),
                    error -> Log.e(TAG, "Error setting font preview", error)
                )
        );
    }

    public void setTranslationEnabled(boolean enabled) {
        if (translationEnabled.getValue() != null && translationEnabled.getValue() == enabled) {
            return;
        }
        
        disposables.add(
            dataStore.setTranslationEnabled(enabled)
                .subscribeOn(Schedulers.io())
                .subscribe(
                    preferences -> Log.d(TAG, "Translation updated to: " + enabled),
                    error -> Log.e(TAG, "Error setting translation", error)
                )
        );
    }

    public void setNotificationsEnabled(boolean enabled) {
        if (notificationsEnabled.getValue() != null && notificationsEnabled.getValue() == enabled) {
            return;
        }
        
        disposables.add(
            dataStore.setNotificationsEnabled(enabled)
                .subscribeOn(Schedulers.io())
                .subscribe(
                    preferences -> Log.d(TAG, "Notifications updated to: " + enabled),
                    error -> Log.e(TAG, "Error setting notifications", error)
                )
        );
    }

    public void setPreviewText(String text) {
        if (previewText.getValue() != null && previewText.getValue().equals(text)) {
            return;
        }
        
        disposables.add(
            dataStore.setPreviewText(text)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    preferences -> {
                        Log.d(TAG, "Preview text updated");
                        settingsEvent.setValue(new SettingsEvent(
                            SettingsEventType.SHOW_TOAST,
                            "Preview text updated"
                        ));
                    },
                    error -> Log.e(TAG, "Error setting preview text", error)
                )
        );
    }

    public void resetAllSettings() {
        disposables.add(
            dataStore.clearAll()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    preferences -> {
                        Log.d(TAG, "All settings reset to defaults");
                        SettingsHelper.applyTheme(getApplication());
                        settingsEvent.setValue(new SettingsEvent(
                            SettingsEventType.RECREATE_ALL_ACTIVITIES));
                    },
                    error -> Log.e(TAG, "Error resetting settings", error)
                )
        );
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        disposables.clear();
        Log.d(TAG, "ViewModel cleared");
    }


    public static class SettingsEvent {
        private final SettingsEventType type;
        private final String message;
        private boolean handled = false;

        public SettingsEvent(SettingsEventType type) {
            this(type, null);
        }

        public SettingsEvent(SettingsEventType type, String message) {
            this.type = type;
            this.message = message;
        }

        public SettingsEventType getType() {
            return type;
        }

        public String getMessage() {
            return message;
        }

        public boolean getContentIfNotHandled() {
            if (handled) {
                return false;
            }
            handled = true;
            return true;
        }
    }

    public enum SettingsEventType {
        SHOW_TOAST,
        RECREATE_ACTIVITY,
        RECREATE_ALL_ACTIVITIES
    }
                    }
