package com.oneui.fontviewer.fragment.settings;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.EditTextPreference;
import androidx.preference.DropDownPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.SwitchPreferenceCompat;

import dev.oneuiproject.oneui.preference.HorizontalRadioPreference;
import dev.oneuiproject.oneui.preference.internal.PreferenceRelatedCard;
import dev.oneuiproject.oneui.utils.PreferenceUtils;

import com.oneui.fontviewer.R;
import com.oneui.fontviewer.App;
import com.oneui.fontviewer.fragment.settings.about.AboutActivity;
import com.oneui.fontviewer.fragment.settings.utils.SettingsHelper;
import com.oneui.fontviewer.fragment.settings.viewmodel.SettingsViewModel;

public class SettingsFragment extends PreferenceFragmentCompat {

    private static final String TAG = "SettingsFragment";

    private Context mContext;
    private SettingsViewModel viewModel;

    private DropDownPreference languagePreference;
    private HorizontalRadioPreference themePreference;
    private SwitchPreferenceCompat themeAutoPreference;
    
    private SwitchPreferenceCompat fontPreviewPreference;
    private SwitchPreferenceCompat translationPreference;
    private EditTextPreference previewTextPreference;
    private Preference reportIssuePreference;

    private PreferenceRelatedCard mRelatedCard;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mContext = context;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        viewModel = new ViewModelProvider(this).get(SettingsViewModel.class);

        initPreferences();
        setupPreferenceListeners();
        observeViewModel();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && languagePreference != null) {
            int actualLang = SettingsHelper.getSystemAssignedLanguage(requireContext());
            String actualLangStr = String.valueOf(actualLang);

            if (!actualLangStr.equals(languagePreference.getValue())) {
                languagePreference.setValue(actualLangStr);
                Integer currentDataStoreLang = viewModel.getLanguageMode().getValue();
                if (currentDataStoreLang == null || currentDataStoreLang != actualLang) {
                    viewModel.syncLanguageModeFromSystem(actualLang);
                }
            }
        }
        setupRelatedCard();
    }


    private void initPreferences() {

        languagePreference = findPreference("language_mode");
        if (languagePreference != null) {
            languagePreference.seslSetSummaryColor(getColoredSummaryColor(true));
            languagePreference.setOnPreferenceChangeListener((preference, newValue) -> {
                int mode = Integer.parseInt((String) newValue);
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .postDelayed(() -> viewModel.setLanguageMode(mode), 250);
                return true;
            });
        }

        themePreference = findPreference("theme_mode");
        if (themePreference != null) {
            themePreference.setDividerEnabled(false);
            themePreference.setTouchEffectEnabled(false);
        }

        themeAutoPreference = findPreference("theme_auto");

        

        fontPreviewPreference = findPreference("font_preview_enabled");
        if (fontPreviewPreference != null) {
            fontPreviewPreference.seslSetSummaryColor(getColoredSummaryColor(false));
            fontPreviewPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean enabled = (Boolean) newValue;
                viewModel.setFontPreviewEnabled(enabled);
                return true;
            });
        }

        translationPreference = findPreference("enable_translation");
        if (translationPreference != null) {
            translationPreference.seslSetSummaryColor(getColoredSummaryColor(false));
            translationPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean enabled = (Boolean) newValue;
                viewModel.setTranslationEnabled(enabled);
                return true;
            });
        }

        previewTextPreference = findPreference("preview_text");
        if (previewTextPreference != null) {
            previewTextPreference.seslSetSummaryColor(getColoredSummaryColor(true));
            previewTextPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                String text = (String) newValue;
                viewModel.setPreviewText(text);
                return true;
            });
        }

        Preference aboutPreference = findPreference("about_app");
        if (aboutPreference != null) {
            aboutPreference.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(mContext, AboutActivity.class);
                startActivity(intent);
                return true;
            });
        }

        reportIssuePreference = findPreference("report_issue");
        if (reportIssuePreference != null) {
            reportIssuePreference.setOnPreferenceClickListener(preference -> {
                Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
                emailIntent.setData(Uri.parse(
                        "mailto:" + mContext.getString(R.string.feedback_email)));
                emailIntent.putExtra(
                        Intent.EXTRA_SUBJECT,
                        mContext.getString(R.string.settings_report_issue_email_subject));
                try {
                    startActivity(Intent.createChooser(emailIntent, null));
                } catch (ActivityNotFoundException e) {
                }
                return true;
            });
        }
    }

    private void setupPreferenceListeners() {
        if (themePreference != null) {
            themePreference.setOnPreferenceChangeListener((preference, newValue) -> {
                String value = (String) newValue;
                int mode = Integer.parseInt(value);

                if (mode == SettingsHelper.THEME_DARK) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                } else {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                }

                viewModel.setThemeMode(mode);

                return true;
            });
        }

        if (themeAutoPreference != null) {
            themeAutoPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean enabled = (Boolean) newValue;

                if (themePreference != null) {
                    themePreference.setEnabled(!enabled);
                }

                if (enabled) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                } else {
                    int savedMode = SettingsHelper.getThemeMode(mContext);
                    if (savedMode == SettingsHelper.THEME_DARK) {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                    } else {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                    }
                }

                viewModel.setThemeAuto(enabled);

                return true;
            });
        }
    }

    private void observeViewModel() {
        viewModel.getLanguageMode().observe(this, mode -> {
            if (languagePreference != null && mode != null) {
                languagePreference.setValue(String.valueOf(mode));
            }
        });

        viewModel.getThemeMode().observe(this, mode -> {
            if (themePreference != null && mode != null) {
                themePreference.setValue(String.valueOf(mode));
            }
        });

        viewModel.getThemeAuto().observe(this, enabled -> {
            if (themeAutoPreference != null && enabled != null) {
                themeAutoPreference.setChecked(enabled);

                if (themePreference != null) {
                    themePreference.setEnabled(!enabled);
                }
            }
        });

        

        viewModel.getFontPreviewEnabled().observe(this, enabled -> {
            if (fontPreviewPreference != null && enabled != null) {
                fontPreviewPreference.setChecked(enabled);
            }
        });

        viewModel.getTranslationEnabled().observe(this, enabled -> {
            if (translationPreference != null && enabled != null) {
                translationPreference.setChecked(enabled);
            }
        });

        viewModel.getPreviewText().observe(this, text -> {
            if (previewTextPreference != null && text != null) {
                previewTextPreference.setText(text);
            }
        });

        viewModel.getSettingsEvent().observe(this, event -> {
            if (event != null && event.getContentIfNotHandled()) {
                handleSettingsEvent(event);
            }
        });
    }

    private void handleSettingsEvent(SettingsViewModel.SettingsEvent event) {
        switch (event.getType()) {
            case SHOW_TOAST:
                if (event.getMessage() != null) {
                    Toast.makeText(mContext, event.getMessage(), Toast.LENGTH_SHORT).show();
                }
                break;

            case RECREATE_ACTIVITY:
                if (getActivity() != null) {
                    requireActivity().recreate();
                }
                break;

            case RECREATE_ALL_ACTIVITIES:
                App app = App.getInstance();
                if (app != null) {
                    app.recreateAllActivities();
                } else if (getActivity() != null) {
                    requireActivity().recreate();
                }
                break;

            }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        getView().setBackgroundColor(mContext.getColor(dev.oneuiproject.oneui.design.R.color.oui_background_color));
        getListView().seslSetLastRoundedCorner(false);
    }

    private void setupRelatedCard() {
        if (mRelatedCard == null) {
            mRelatedCard = PreferenceUtils.createRelatedCard(requireContext());

            updateRelatedCardText(mContext);

            mRelatedCard.show(this);
        }
    }

    private void updateRelatedCardText(Context context) {
        if (mRelatedCard != null) {
            mRelatedCard.setTitleText(context.getString(R.string.related_card_title));

            mRelatedCard.removeCardButtons();

            mRelatedCard.addButton(context.getString(R.string.share_app), v -> {
                        Intent shareIntent = new Intent(Intent.ACTION_SEND);
                        shareIntent.setType("text/plain");
                        shareIntent.putExtra(
                                Intent.EXTRA_TEXT,
                                "https://play.google.com/store/apps/details?id="
                                        + context.getPackageName()
                        );
                        startActivity(Intent.createChooser(shareIntent, null));
                    })
                    .addButton(context.getString(R.string.rate_app), v -> {
                        try {
                            startActivity(new Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("market://details?id=" + context.getPackageName())
                            ));
                        } catch (ActivityNotFoundException e) {
                            startActivity(new Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://play.google.com/store/apps/details?id="
                                            + context.getPackageName())
                            ));
                        }
                    });
        }
    }

    private ColorStateList getColoredSummaryColor(boolean enabled) {
        if (enabled) {
            TypedValue colorPrimaryDark = new TypedValue();
            mContext.getTheme().resolveAttribute(
                    dev.oneuiproject.oneui.design.R.attr.colorPrimaryDark, colorPrimaryDark, true);

            int[][] states = new int[][] {
                    new int[] {android.R.attr.state_enabled},
                    new int[] {-android.R.attr.state_enabled}
            };
            int[] colors = new int[] {
                    Color.argb(0xff,
                            Color.red(colorPrimaryDark.data),
                            Color.green(colorPrimaryDark.data),
                            Color.blue(colorPrimaryDark.data)),
                    Color.argb(0x4d,
                            Color.red(colorPrimaryDark.data),
                            Color.green(colorPrimaryDark.data),
                            Color.blue(colorPrimaryDark.data))
            };
            return new ColorStateList(states, colors);
        } else {
            TypedValue outValue = new TypedValue();
            mContext.getTheme().resolveAttribute(
                    dev.oneuiproject.oneui.design.R.attr.isLightTheme, outValue, true);
            return mContext.getColorStateList(outValue.data != 0
                    ? dev.oneuiproject.oneui.design.R.color.sesl_secondary_text_light
                    : dev.oneuiproject.oneui.design.R.color.sesl_secondary_text_dark);
        }
    }
    }
