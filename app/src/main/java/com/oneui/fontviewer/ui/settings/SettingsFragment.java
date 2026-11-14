package com.oneui.fontviewer.ui.settings;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.preference.EditTextPreference;
import androidx.preference.DropDownPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import dev.oneuiproject.oneui.preference.HorizontalRadioPreference;
import dev.oneuiproject.oneui.preference.internal.PreferenceRelatedCard;
import dev.oneuiproject.oneui.utils.PreferenceUtils;

import com.oneui.fontviewer.R;
import com.oneui.fontviewer.FontViewerApp;
import com.oneui.fontviewer.ui.settings.about.AboutActivity;
import com.oneui.fontviewer.ui.settings.utils.SettingsHelper;
import com.oneui.fontviewer.ui.settings.viewmodel.SettingsViewModel;
import com.oneui.fontviewer.ui.utils.anim.TransitionUtils;

public class SettingsFragment extends PreferenceFragmentCompat {

    // مدة التأخير (بالمللي ثانية) قبل تطبيق الثيم عند الضغط على switch "ثيم النظام"،
    // حتى ينهي الـ switch حركته أولاً. غيّر القيمة لزيادة أو تقليل التأخير.
    private static final long THEME_AUTO_SWITCH_DELAY_MS = 250L;

    private Context mContext;
    private SettingsViewModel viewModel;

    private DropDownPreference languagePreference;

    // لمزامنة أول قيمة تصل من DataStore مع لغة النظام الفعلية (مرة واحدة فقط)
    private boolean mLanguageSyncedAfterLoad = false;
    private HorizontalRadioPreference themePreference;
    private SwitchPreferenceCompat themeAutoPreference;
    
    private SwitchPreferenceCompat fontPreviewPreference;
    private SwitchPreferenceCompat translationPreference;
    private EditTextPreference previewTextPreference;
    private Preference reportIssuePreference;

    private PreferenceRelatedCard mRelatedCard;

    // ═══════════════════════════════════════════════════════════════════
    // استعادة موضع تمرير القائمة بعد إعادة بناء الشاشة (تغيير الثيم أو اللغة)
    //
    // السبب: عند إعادة البناء يعيد RecyclerView موضع التمرير المحفوظ في أول تخطيط (layout)،
    // لكن محتوى القائمة وقتها (وحجم منطقة العرض بعد انطواء الـ App bar) قد يكون أقصر مما كان عليه
    // قبل إعادة البناء. فإذا كان خط النظام صغيراً فالقائمة كلها تكاد تتسع في الشاشة، فيرى
    // RecyclerView أن الموضع المحفوظ يتجاوز نهاية المحتوى ويعيد القائمة إلى البداية، ثم يكتمل
    // المحتوى بعدها دون أن يعود التمرير. مع الخط الكبير تبقى القائمة أطول من الشاشة في كل الأحوال
    // فلا تظهر المشكلة (وهذا ما يحدث في تطبيق المثال لأن قائمته طويلة جداً).
    //
    // الحل: نحفظ أول عنصر ظاهر (ترتيبه + إزاحته) ونعيد تطبيقه قبل رسم كل إطار خلال مهلة قصيرة،
    // كلما اختلف الموضع الحالي عن المحفوظ وتغيّر حجم المحتوى أو القائمة.
    // ═══════════════════════════════════════════════════════════════════
    private static final String STATE_SCROLL_POSITION = "settings_scroll_position";
    private static final String STATE_SCROLL_OFFSET = "settings_scroll_offset";

    // المدة (بالمللي ثانية) التي نواصل فيها تثبيت موضع التمرير بعد إعادة البناء
    private static final long SCROLL_RESTORE_WINDOW_MS = 1500L;

    private int mSavedScrollPosition = RecyclerView.NO_POSITION;
    private int mSavedScrollOffset;

    // القائمة التي نستعيد موضعها حالياً (null = لا توجد استعادة جارية)
    private RecyclerView mRestoreList;
    private long mRestoreDeadline;

    // آخر حالة شاهدناها في الاستعادة، حتى لا نكرر نفس المحاولة إذا لم يتغير شيء (مثلاً لو كان
    // الموضع المطلوب أبعد من نهاية المحتوى الحالي)
    private boolean mRestoreHasPrev;
    private int mRestorePrevPosition;
    private int mRestorePrevOffset;
    private int mRestorePrevSignature;

    // إرجاع false من onPreDraw يلغي رسم هذا الإطار، فنعيد التخطيط بالموضع الصحيح قبل أن يراه المستخدم
    private final ViewTreeObserver.OnPreDrawListener mScrollRestorePreDraw =
            () -> !applyScrollRestore();

    // إذا بدأ المستخدم السحب بنفسه نتوقف فوراً ولا ننازعه على التمرير
    private final RecyclerView.OnScrollListener mScrollRestoreDragListener =
            new RecyclerView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                        stopScrollRestore();
                    }
                }
            };

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

        if (savedInstanceState != null) {
            mSavedScrollPosition = savedInstanceState.getInt(
                    STATE_SCROLL_POSITION, RecyclerView.NO_POSITION);
            mSavedScrollOffset = savedInstanceState.getInt(STATE_SCROLL_OFFSET, 0);
        }

        initPreferences();
        setupPreferenceListeners();
        observeViewModel();
    }

    @Override
    public void onResume() {
        super.onResume();
        syncLanguageFromSystem();
        setupRelatedCard();
    }

    /**
     * يزامن DataStore وقائمة اللغة مع لغة التطبيق الفعلية في النظام (من أندرويد 13).
     *
     * لغة التطبيق قد تتغير من إعدادات النظام دون علم DataStore، فنقارن قيمة DataStore نفسها
     * (وليس قيمة الـ preference، لأنها تبدأ بالقيمة الافتراضية "0" وهي نفس System default،
     * فتبدو متطابقة مع النظام قبل أن تصل قيمة DataStore القديمة وتُعرض خطأً).
     */
    private void syncLanguageFromSystem() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || languagePreference == null) {
            return;
        }

        Integer dataStoreLang = viewModel.getLanguageMode().getValue();
        if (dataStoreLang == null) {
            // لم تصل القيمة من DataStore بعد، سيستدعي المراقب هذه الدالة عند وصولها
            return;
        }

        int actualLang = SettingsHelper.getSystemAssignedLanguage(requireContext());
        if (dataStoreLang != actualLang) {
            languagePreference.setValue(String.valueOf(actualLang));
            viewModel.syncLanguageModeFromSystem(actualLang);
        }
    }


    private void initPreferences() {

        languagePreference = findPreference("language_mode");
        if (languagePreference != null) {
            languagePreference.seslSetSummaryColor(getColoredSummaryColor(true));
            languagePreference.setOnPreferenceChangeListener((preference, newValue) -> {
                int mode = Integer.parseInt((String) newValue);
                new android.os.Handler(android.os.Looper.getMainLooper())
                        .postDelayed(() -> {
                            // التقاط صورة للشاشة باللغة الحالية قبل تغييرها (لأنيميشن التغيير)
                            TransitionUtils.prepareForLanguageChange(getActivity());
                            viewModel.setLanguageMode(mode);
                        }, 250);
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

                // التقاط صورة للثيم الحالي قبل تغييره (لأنيميشن تغيير الثيم)
                TransitionUtils.prepareForThemeChange(requireActivity());

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

                // تأخير تطبيق الثيم حتى ينهي الـ switch حركته.
                // ملاحظة: viewModel.setThemeAuto() يستدعي applyTheme() بنفسه، لذلك يجب أن يكون
                // داخل التأخير أيضاً وإلا سيُطبَّق الثيم فوراً.
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    // التقاط صورة للثيم الحالي قبل تغييره (لأنيميشن تغيير الثيم)
                    TransitionUtils.prepareForThemeChange(getActivity());

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
                }, THEME_AUTO_SWITCH_DELAY_MS);

                return true;
            });
        }
    }

    private void observeViewModel() {
        viewModel.getLanguageMode().observe(this, mode -> {
            if (languagePreference != null && mode != null) {
                languagePreference.setValue(String.valueOf(mode));

                // أول قيمة تصل قد تكون قديمة (اللغة تغيّرت من إعدادات النظام)، نصححها مرة واحدة.
                // لا نكررها بعد ذلك حتى لا نتعارض مع تغيير اللغة من هذه الشاشة.
                if (!mLanguageSyncedAfterLoad) {
                    mLanguageSyncedAfterLoad = true;
                    syncLanguageFromSystem();
                }
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
                if (event.getMessageResId() != 0) {
                    Toast.makeText(mContext, event.getMessageResId(), Toast.LENGTH_SHORT).show();
                } else if (event.getMessage() != null) {
                    Toast.makeText(mContext, event.getMessage(), Toast.LENGTH_SHORT).show();
                }
                break;

            case RECREATE_ACTIVITY:
                if (getActivity() != null) {
                    requireActivity().recreate();
                }
                break;

            case RECREATE_ALL_ACTIVITIES:
                FontViewerApp app = FontViewerApp.getInstance();
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

        startScrollRestore();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        int position = RecyclerView.NO_POSITION;
        int offset = 0;

        if (mRestoreList != null) {
            // الاستعادة لم تنتهِ بعد (إعادة بناء متتالية): نحفظ الموضع المطلوب لا الموضع اللحظي
            position = mSavedScrollPosition;
            offset = mSavedScrollOffset;
        } else {
            RecyclerView list = getView() != null ? getListView() : null;
            int[] anchor = list != null ? getListAnchor(list) : null;
            // لا حاجة للحفظ إذا كانت القائمة في أعلاها
            if (anchor != null && !(anchor[0] == 0 && anchor[1] >= 0)) {
                position = anchor[0];
                offset = anchor[1];
            }
        }

        outState.putInt(STATE_SCROLL_POSITION, position);
        outState.putInt(STATE_SCROLL_OFFSET, offset);
    }

    @Override
    public void onDestroyView() {
        stopScrollRestore();
        super.onDestroyView();
    }

    /**
     * يرجع {ترتيب أول عنصر ظاهر، إزاحة أعلاه عن بداية القائمة}، أو null إذا لم تكن القائمة جاهزة.
     */
    @Nullable
    private int[] getListAnchor(@NonNull RecyclerView list) {
        RecyclerView.LayoutManager layoutManager = list.getLayoutManager();
        if (!(layoutManager instanceof LinearLayoutManager) || list.getChildCount() == 0) {
            return null;
        }

        LinearLayoutManager linearLayoutManager = (LinearLayoutManager) layoutManager;
        int position = linearLayoutManager.findFirstVisibleItemPosition();
        if (position == RecyclerView.NO_POSITION) {
            return null;
        }

        View child = linearLayoutManager.findViewByPosition(position);
        if (child == null) {
            return null;
        }

        return new int[] {
                position,
                linearLayoutManager.getDecoratedTop(child) - list.getPaddingTop()
        };
    }

    private void startScrollRestore() {
        if (mSavedScrollPosition == RecyclerView.NO_POSITION) {
            return;
        }

        RecyclerView list = getListView();
        if (list == null) {
            return;
        }

        mRestoreList = list;
        mRestoreHasPrev = false;
        mRestoreDeadline = SystemClock.uptimeMillis() + SCROLL_RESTORE_WINDOW_MS;

        list.getViewTreeObserver().addOnPreDrawListener(mScrollRestorePreDraw);
        list.addOnScrollListener(mScrollRestoreDragListener);
    }

    private void stopScrollRestore() {
        RecyclerView list = mRestoreList;
        mRestoreList = null;
        mSavedScrollPosition = RecyclerView.NO_POSITION;

        if (list != null) {
            ViewTreeObserver observer = list.getViewTreeObserver();
            if (observer.isAlive()) {
                observer.removeOnPreDrawListener(mScrollRestorePreDraw);
            }
            list.removeOnScrollListener(mScrollRestoreDragListener);
        }
    }

    /**
     * يُستدعى قبل رسم كل إطار أثناء مهلة الاستعادة.
     *
     * @return true إذا غيّرنا موضع التمرير (فيُلغى رسم هذا الإطار ويُعاد التخطيط)، وإلا false
     */
    private boolean applyScrollRestore() {
        RecyclerView list = mRestoreList;
        if (list == null) {
            return false;
        }

        if (SystemClock.uptimeMillis() > mRestoreDeadline) {
            stopScrollRestore();
            return false;
        }

        RecyclerView.Adapter<?> adapter = list.getAdapter();
        RecyclerView.LayoutManager layoutManager = list.getLayoutManager();
        if (adapter == null
                || !(layoutManager instanceof LinearLayoutManager)
                || mSavedScrollPosition >= adapter.getItemCount()) {
            // المحتوى لم يكتمل بعد، ننتظر الإطار التالي
            return false;
        }

        int[] current = getListAnchor(list);
        if (current == null) {
            return false;
        }

        int signature = list.computeVerticalScrollRange() * 31 + list.getHeight();

        if (current[0] == mSavedScrollPosition && current[1] == mSavedScrollOffset) {
            // الموضع صحيح حالياً. نتذكره لنكتشف إن عبث به أحد لاحقاً خلال المهلة
            mRestoreHasPrev = true;
            mRestorePrevPosition = current[0];
            mRestorePrevOffset = current[1];
            mRestorePrevSignature = signature;
            return false;
        }

        if (mRestoreHasPrev
                && current[0] == mRestorePrevPosition
                && current[1] == mRestorePrevOffset
                && signature == mRestorePrevSignature) {
            // لا شيء تغيّر منذ آخر محاولة (الموضع المطلوب غير ممكن حالياً)، لا داعي لتكرارها
            return false;
        }

        mRestoreHasPrev = true;
        mRestorePrevPosition = current[0];
        mRestorePrevOffset = current[1];
        mRestorePrevSignature = signature;

        ((LinearLayoutManager) layoutManager)
                .scrollToPositionWithOffset(mSavedScrollPosition, mSavedScrollOffset);
        return true;
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
            mRelatedCard.setTitleText(context.getString(R.string.oui_relative_description));

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
