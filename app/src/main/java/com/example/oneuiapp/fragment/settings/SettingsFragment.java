package com.example.oneuiapp.fragment.settings;

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
import dev.oneuiproject.oneui.widget.Toast;

import com.example.oneuiapp.R;
import com.example.oneuiapp.MyApplication;
import com.example.oneuiapp.fragment.settings.about.AboutActivity;
import com.example.oneuiapp.fragment.settings.utils.LanguageHelper;
import com.example.oneuiapp.fragment.settings.utils.SettingsHelper;
import com.example.oneuiapp.fragment.settings.viewmodel.SettingsViewModel;

/**
 * SettingsFragment - نسخة محدّثة تعتمد بالكامل على DataStore
 * تم إزالة كل استخدام مباشر لـ SharedPreferences
 *
 * ملاحظة مهمة: تم حذف جميع دوال فرض اتجاه النص والتنسيق يدوياً
 * (applyCorrectTextDirection وما يتبعها)، لأن النظام يتكفل بذلك
 * تلقائياً بناءً على الـ Locale الصحيح المضبوط في Context.
 * فرضها يدوياً كان يتعارض مع "خيارات المطورين" ويسبب خللاً في الاتجاهات.
 *
 * ★ آلية تغيير اللغة بدون وميض (Android 13+): ★
 * - بما أن locale|layoutDirection مُضاف إلى configChanges في المانيفيست
 *   لـ SettingsActivity فقط، لن يُدمَّر هذا الـ Fragment عند تغيير اللغة.
 * - onConfigurationChanged يُستدعى بدلاً من ذلك، ويقوم بـ:
 *   1. إنشاء freshContext عبر LanguageHelper يحمل اللغة الجديدة.
 *   2. تحديث mContext لاستخدامه في العمليات اللاحقة.
 *   3. إجبار View الجذر على تغيير اتجاهه (RTL/LTR) فوراً عبر LanguageHelper.
 *   4. تحديث النصوص فقط دون المساس بالصور أو الرسومات أو البطاقات.
 *
 * ★ جوهر مشكلة النصوص وحلها: ★
 * attachBaseContext() في BaseActivity يُنشئ Context ملفوفاً باللغة القديمة
 * مرة واحدة عند بدء التطبيق. حتى بعد تغيير اللغة عبر LocaleManager،
 * يظل هذا الـ Context القديم هو مصدر getString() في الـ Fragment.
 * الحل: LanguageHelper.createFreshContext(newConfig) يُنشئ سياقاً مستقلاً
 * يقرأ الموارد باللغة الجديدة بشكل مضمون.
 *
 * ★ مزامنة اللغة مع إعدادات النظام: ★
 * - onResume يقرأ اللغة الفعلية من LocaleManager في كل مرة يُستأنَم فيها الـ Fragment،
 *   مما يضمن تحديث الـ Dropdown ليعكس أي تغيير جرى من إعدادات النظام.
 * - عند اكتشاف تغيير اللغة من إعدادات النظام، يُجدول إعادة بناء الأنشطة الخلفية
 *   التي لم يُعدها النظام تلقائياً بسبب اعتراض SettingsActivity لحدث locale.
 */
public class SettingsFragment extends PreferenceFragmentCompat {

    private static final String TAG = "SettingsFragment";

    private Context mContext;
    private SettingsViewModel viewModel;

    // ── عناصر الواجهة الرئيسية ──
    private DropDownPreference languagePreference;
    private HorizontalRadioPreference themePreference;
    private SwitchPreferenceCompat themeAutoPreference;
    // ★ الثيم الأسود: مستقل تماماً عن themePreference و themeAutoPreference ★
    private SwitchPreferenceCompat themeTransparentPreference;
    private DropDownPreference fontPreference;
    private SwitchPreferenceCompat fontPreviewPreference;
    private SwitchPreferenceCompat translationPreference;
    private EditTextPreference previewTextPreference;
    // ★ خيار الإبلاغ عن خطأ أو اقتراح تحسين — يفتح نافذة اختيار تطبيق بريد ★
    private Preference reportIssuePreference;

    // ★ مراجع لأقسام الإعدادات — ضرورية لتحديث عناوينها يدوياً في onConfigurationChanged ★
    

    // ★ بطاقة الروابط ذات الصلة — تظهر أسفل الإعدادات وتضيف الفراغ السفلي تلقائياً ★
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

    /**
     * ★ مزامنة قيمة اللغة مع ما هو مطبق فعلياً في النظام ★
     *
     * يُحلّ مشكلتين:
     *
     * المشكلة الأولى: عند تغيير لغة التطبيق من إعدادات النظام والعودة
     * للتطبيق وهو لا يزال في الـ RAM، كان الـ Dropdown يعرض القيمة القديمة.
     *
     * المشكلة الثانية: SettingsActivity تعترض حدث locale في configChanges
     * وتتعامل معه بنفسها عبر onConfigurationChanged، مما يجعل نظام Android
     * يعتبر أن التطبيق قد تعامل مع التغيير بالكامل، فلا يُعيد بناء MainActivity
     * تلقائياً. عند العودة من SettingsActivity إلى MainActivity، تبقى
     * MainActivity على اللغة القديمة.
     *
     * الحل: في كل مرة يُستأنَم الـ Fragment، نستعلم من LocaleManager
     * عن اللغة الفعلية المطبقة ونحدّث الـ Dropdown وDataStore معاً.
     * ثم نُجدول إعادة بناء الأنشطة الخلفية بعد تأخير يضمن اكتمال
     * onConfigurationChanged وتحديثاته في SettingsActivity.
     *
     * ★ لماذا لا تحدث مشكلة التكرار عند التغيير من القائمة المنسدلة؟ ★
     * لأن setLanguageMode() في ViewModel يُحدّث languagePreference.getValue()
     * فوراً، فعند وصول onResume لاحقاً لا يجد أي تعارض ولا يُرسل RECREATE.
     */
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
        // ── تهيئة مراجع الأقسام (لتحديث عناوينها في onConfigurationChanged) ──

        // ── تهيئة عناصر الإعدادات الفردية ──
        languagePreference = findPreference("language_mode");
        if (languagePreference != null) {
            languagePreference.seslSetSummaryColor(getColoredSummaryColor(true));
            languagePreference.setOnPreferenceChangeListener((preference, newValue) -> {
                int mode = Integer.parseInt((String) newValue);
                // ★ تأخير 250ms قبل تطبيق اللغة ★
                // يمنح DropDownPreference وقتاً كافياً لإغلاق نافذته بسلاسة
                // قبل أن يبدأ النظام في تطبيق تغيير اللغة وتحديث الواجهة.
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

        // ★ تهيئة مفتاح الثيم الأسود ★
        themeTransparentPreference = findPreference("theme_transparent");
        if (themeTransparentPreference != null) {
            themeTransparentPreference.seslSetSummaryColor(getColoredSummaryColor(false));
            themeTransparentPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean enabled = (Boolean) newValue;
                // ★ تفعيل/إلغاء الثيم الأسود لا يؤثر على خيار الثيم الفاتح/الداكن ★
                // ★ تأخير قبل تطبيق الثيم الأسود ★
                // يمنح السويتش وقتاً كافياً لإكمال أنيميشنه قبل أن تبدأ
                // عملية إعادة تحميل الـ layouts المرتبطة بتغيير الثيم
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .postDelayed(() -> viewModel.setThemeTransparent(enabled), 300);
                return true;
            });
        }

        fontPreference = findPreference("font_mode");
        if (fontPreference != null) {
            fontPreference.seslSetSummaryColor(getColoredSummaryColor(true));
            fontPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                int mode = Integer.parseInt((String) newValue);
                viewModel.setFontMode(mode);
                return true;
            });
        }

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

        // ★ خيار الإبلاغ عن خطأ أو اقتراح تحسين ★
        // عند الضغط: يفتح نافذة اختيار تطبيق بريد إلكتروني مع موضوع جاهز
        reportIssuePreference = findPreference("report_issue");
        if (reportIssuePreference != null) {
            reportIssuePreference.setOnPreferenceClickListener(preference -> {
                // بناء intent بريد إلكتروني بموضوع جاهز
                // ★ غيّر قيمة feedback_email في strings.xml بعنوان بريدك الإلكتروني ★
                Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
                emailIntent.setData(Uri.parse(
                        "mailto:" + mContext.getString(R.string.feedback_email)));
                emailIntent.putExtra(
                        Intent.EXTRA_SUBJECT,
                        mContext.getString(R.string.settings_report_issue_email_subject));
                try {
                    // createChooser يُجبر نافذة الاختيار على الظهور حتى لو كان
                    // هناك تطبيق بريد افتراضي واحد فقط
                    startActivity(Intent.createChooser(emailIntent, null));
                } catch (ActivityNotFoundException e) {
                    // لا يوجد تطبيق بريد مثبت على الجهاز — نتجاهل الاستثناء بصمت
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

                // تطبيق الثيم مباشرة
                if (mode == SettingsHelper.THEME_DARK) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                } else {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                }

                // حفظ القيمة في ViewModel (الذي بدوره يحفظها في DataStore)
                viewModel.setThemeMode(mode);

                return true;
            });
        }

        if (themeAutoPreference != null) {
            themeAutoPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean enabled = (Boolean) newValue;

                // تعطيل/تفعيل اختيار الثيم اليدوي فوراً
                if (themePreference != null) {
                    themePreference.setEnabled(!enabled);
                }

                // تطبيق الوضع التلقائي مباشرة
                if (enabled) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                } else {
                    // عند تعطيل الوضع التلقائي، تطبيق الثيم المحفوظ
                    int savedMode = SettingsHelper.getThemeMode(mContext);
                    if (savedMode == SettingsHelper.THEME_DARK) {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                    } else {
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                    }
                }

                // تحديث الإعدادات في ViewModel
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

        // ★ مراقبة حالة الثيم الأسود وتحديث الزر ★
        viewModel.getThemeTransparent().observe(this, enabled -> {
            if (themeTransparentPreference != null && enabled != null) {
                themeTransparentPreference.setChecked(enabled);
            }
        });

        viewModel.getFontMode().observe(this, mode -> {
            if (fontPreference != null && mode != null) {
                fontPreference.setValue(String.valueOf(mode));
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
                MyApplication app = MyApplication.getInstance();
                if (app != null) {
                    app.recreateAllActivities();
                } else if (getActivity() != null) {
                    requireActivity().recreate();
                }
                break;

            case RECREATE_BACKGROUND_ACTIVITIES:
                // ★ إعادة بناء الأنشطة الخلفية باللغة الجديدة دون SettingsActivity ★
                //
                // السبب: SettingsActivity تتلقى تغيير اللغة عبر onConfigurationChanged
                // (لأن locale في configChanges)، وإعادة بناؤها ستكسر سلوك عدم الوميض.
                // أما الأنشطة الأخرى (كـ MainActivity) فتحتاج إعادة بناء صريحة
                // لأن النظام قد لا يُعيدها تلقائياً بعد تغيير الثيم + تغيير اللغة،
                // أو بعد تغيير اللغة من إعدادات النظام مع اعتراض locale في configChanges.
                //
                // إذا كان المستخدم قد خرج من SettingsActivity قبل انتهاء التأخير،
                // نُعيد بناء كل الأنشطة الحية لأن SettingsActivity ستكون isFinishing()
                // وستُستثنى تلقائياً من recreateAllActivities().
                MyApplication appBg = MyApplication.getInstance();
                Activity currentActivity = getActivity();
                if (appBg != null) {
                    if (currentActivity != null && !currentActivity.isFinishing()) {
                        // المستخدم لا يزال في SettingsActivity — نستثنيها صراحةً
                        appBg.recreateAllActivitiesExcept(currentActivity);
                    } else {
                        // المستخدم غادر SettingsActivity — تُستثنى تلقائياً عبر isFinishing()
                        appBg.recreateAllActivities();
                    }
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

    /**
     * ★ تُستدعى لمرة واحدة لإنشاء البطاقة وعرضها في أسفل القائمة ★
     *
     * - البطاقة تُنشأ باستخدام requireContext() لضمان وراثة الثيم الصحيح.
     * - بعد الإنشاء، تُطبَّق النصوص عليها عبر updateRelatedCardText().
     * - null-check يمنع إنشاء بطاقة مكررة عند العودة من شاشة أخرى.
     * - البطاقة تتكفل بإضافة الفراغ السفلي تلقائياً، لذا تم حذف footer_space
     *   من preferences.xml لتجنب الفراغ المزدوج.
     */
    private void setupRelatedCard() {
        if (mRelatedCard == null) {
            // 1. إنشاء البطاقة باستخدام requireContext() لضمان وراثة الثيم الصحيح
            mRelatedCard = PreferenceUtils.createRelatedCard(requireContext());

            // 2. تطبيق النصوص الحالية عليها
            updateRelatedCardText(mContext);

            // 3. عرض البطاقة
            mRelatedCard.show(this);
        }
    }

    /**
     * ★ تقوم بمسح الأزرار القديمة وتحديث جميع نصوص البطاقة بالسياق الممرر ★
     *
     * تُستدعى في موضعين:
     * - من setupRelatedCard() عند الإنشاء الأول لتعبئة البطاقة بالنصوص الابتدائية.
     * - من onConfigurationChanged() بعد تغيير اللغة لتحديث النصوص فقط،
     *   بينما تحتفظ البطاقة داخلياً بالثيم الصحيح الذي وُلدت به.
     *
     * ★ مهم: لا نلمس هيكل البطاقة ولا ثيمها ولا ألوانها — نصوص فقط. ★
     *
     * @param context سياق يحمل اللغة المطلوبة (mContext عند الإنشاء، freshContext عند التحديث)
     */
    private void updateRelatedCardText(Context context) {
        if (mRelatedCard != null) {
            // تحديث العنوان ("هل تبحث عن شيء آخر؟" / "Looking for something else?")
            mRelatedCard.setTitleText(context.getString(R.string.related_card_title));

            // مسح الأزرار الموجودة حالياً (باللغة القديمة)
            mRelatedCard.removeCardButtons();

            // إضافة الأزرار من جديد بالنصوص المترجمة (باللغة الجديدة)
            mRelatedCard.addButton(context.getString(R.string.share_app), v -> {
                        // ★ مشاركة رابط التطبيق عبر تطبيقات الجهاز ★
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
                        // ★ فتح صفحة التطبيق في Play Store ★
                        // إذا لم يكن تطبيق Play Store مثبتاً، يُفتح المتصفح بدلاً منه
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

    /**
     * ★ تحديث جميع النصوص في الإعدادات من سياق يحمل اللغة الجديدة بشكل مضمون ★
     *
     * @param freshContext سياق مُنشأ عبر LanguageHelper.createFreshContext(newConfig)
     *                     يحمل اللغة الجديدة بشكل مضمون، متجاوزاً السياق القديم
     *                     الذي أنشأته attachBaseContext() وقت بدء التطبيق.
     *
     * ★ مهم جداً: نحدّث النصوص والعناوين والقوائم فقط.
     *    لا نلمس الصور ولا الرسومات ولا الألوان ولا البطاقات،
     *    وهذا هو السبب الذي يمنع وميض الشاشة تماماً. ★
     *
     * هيكل الأقسام المُحدَّثة:
     *   1. قسم الثيم             (category_theme)
     *   2. قسم خلفية القوائم    (category_list_background)
     *   3. القسم العام           (category_general)
     *   4. قسم حول التطبيق      (category_about)
     */
    

    /**
     * ★ تحديث الاتجاه والنصوص فوراً عند تغيير اللغة — بدون تدمير الشاشة ★
     *
     * يُستدعى لأن locale|layoutDirection في configChanges يمنع تدمير الـ Activity.
     *
     * الترتيب مهم:
     * 1. إنشاء freshContext عبر LanguageHelper (مصدر النصوص الجديدة المضمون)
     * 2. تحديث mContext للعمليات اللاحقة
     * 3. تحديث الاتجاه عبر LanguageHelper (يطبّق فوراً على كل العناصر الداخلية)
     * 4. تحديث النصوص من freshContext
     * 5. تحديث نصوص بطاقة PreferenceRelatedCard فقط باللغة الجديدة
     *    (البطاقة تحتفظ بثيمها الصحيح، ونمرر إليها النصوص المترجمة فقط)
     * 6. إخبار القائمة بإعادة الرسم
     */
    

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
