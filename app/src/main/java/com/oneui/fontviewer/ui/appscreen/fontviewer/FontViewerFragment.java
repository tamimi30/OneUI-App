package com.oneui.fontviewer.ui.appscreen.fontviewer;

import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.animation.AccelerateDecelerateInterpolator;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.oneui.fontviewer.ui.core.dialog.FontSizeDialog;
import com.oneui.fontviewer.ui.core.dialog.AxisInfoDialog;
import com.oneui.fontviewer.R;
import com.oneui.fontviewer.utils.variable.VariableFontAxisExtractor;
import com.oneui.fontviewer.data.fontviewer.FontPrefetcher;
import com.oneui.fontviewer.data.fontviewer.FontViewerRepository;
import com.oneui.fontviewer.ui.core.utils.AxisLabelProvider;
import com.oneui.fontviewer.ui.core.utils.FontLabelFormatter;
import com.oneui.fontviewer.ui.core.viewmodel.FontPreviewSettingsViewModel;
import com.oneui.fontviewer.ui.appscreen.fontviewer.utils.BoldItalicFormatting;
import com.oneui.fontviewer.utils.variable.InstanceLabelResolver;
import com.oneui.fontviewer.utils.variable.VariableTypefaceFactory;

public class FontViewerFragment extends Fragment {

    private static final String KEY_FONT_PATH          = "font_path";
    private static final String KEY_FONT_FILE_NAME     = "font_file_name";
    private static final String KEY_FONT_REAL_NAME     = "font_real_name";
    private static final String KEY_ORIGINAL_FONT_PATH = "original_font_path";
    private static final String KEY_FONT_SIZE          = "font_size";
    // خريطة قيم كل محاور الخط المتغير الحالية (wght, wdth, ital, GRAD, ROND, MONO) تُحفظ دفعة واحدة
    private static final String KEY_AXIS_VALUES        = "axis_values";
    private static final String KEY_IS_VARIABLE_FONT   = "is_variable_font";
    private static final String KEY_TTC_INDEX          = "ttc_index";
    private static final String KEY_IS_SYSTEM_FONT     = "is_system_font";
    private static final String KEY_WEIGHT_WIDTH_LABEL = "weight_width_label";
    private static final String TAG = "FontViewerFragment";

    private static final float DEFAULT_FONT_SIZE   = 34f;
    private static final float MIN_FONT_SIZE       = 12f;
    private static final float MAX_FONT_SIZE       = 520f;

    private static float sSessionFontSize = -1f;

    private TextView previewSentence;
    private TextView weightLabelText;

    // ★ بيانات خط جاهزة مسبقاً (FontPrefetcher) طُبِّقت قبل إنشاء الواجهة، وتنتظر إنشاءها لتُعرض (انظر applyLoadedFontUi) ★
    private boolean prefetchedUiPending = false;
    private Map<String, List<VariableFontAxisExtractor.VariableInstance>> prefetchedAxisInstancesMap;

    private View variableAxesContainer;
    private AxisSpinnerUi weightAxisUi;
    private AxisSpinnerUi widthAxisUi;
    // محور الميل (ital) ثنائي القيمة، لذلك يُعرض بمفتاح SwitchCompat بدل Spinner (ضغطة واحدة بدل ضغطتين)
    private AxisSwitchUi italicAxisUi;
    private AxisSpinnerUi gradeAxisUi;
    private AxisSpinnerUi roundnessAxisUi;
    private AxisSpinnerUi monoAxisUi;
    // قائمة المحاور التي تُعرض بـ Spinner فقط (محور الميل مستثنى لأنه يُعرض بمفتاح)
    private List<AxisSpinnerUi> allAxisUis;

    private String currentFontPath;
    private String currentFontFileName;
    private String currentFontRealName;
    public String originalFontPath;
    private Typeface currentTypeface;
    private float currentFontSize   = DEFAULT_FONT_SIZE;
    private boolean isVariableFont  = false;
    private int currentTtcIndex     = 0;
    private boolean isSystemFont    = false;

    // القيم الحالية المُطبّقة فعلياً لكل محور مدعوم (المفتاح هو وسم المحور مثل wght أو GRAD)
    private final Map<String, Float> currentAxisValues = new LinkedHashMap<>();
    // أنيميشن منفصل لكل محور، حتى يمكن تحريك أكثر من محور في آنٍ واحد دون أن يتعارض أحدهما مع الآخر
    private final Map<String, ValueAnimator> axisAnimators = new HashMap<>();

    private String currentWeightWidthLabel;

    private OnFontChangedListener fontChangedListener;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private FontViewerViewModel viewModel;
    private FontPreviewSettingsViewModel settingsViewModel;
    private BoldItalicFormatting formattingHelper = new BoldItalicFormatting();

    /**
     * تمثل ربط عنصر واجهة واحد بمحور من محاور الخط المتغير (الحاوية + Spinner).
     * الحاوية نفسها هي جذر vf_axis_spinner_item.xml المنفوخ لهذا المحور.
     */
    private static class AxisSpinnerUi {
        final String tag;
        final View container;
        final AppCompatSpinner spinner;
        List<VariableFontAxisExtractor.VariableInstance> instances = new ArrayList<>();

        AxisSpinnerUi(String tag, View container, AppCompatSpinner spinner) {
            this.tag = tag;
            this.container = container;
            this.spinner = spinner;
        }
    }

    /**
     * تمثل ربط عنصر واجهة واحد بمحور ثنائي القيمة (الحاوية + SwitchCompat).
     * الحاوية نفسها هي جذر vf_axis_switch_item.xml المنفوخ لهذا المحور.
     */
    private static class AxisSwitchUi {
        final String tag;
        final View container;
        final SwitchCompat switchView;

        AxisSwitchUi(String tag, View container, SwitchCompat switchView) {
            this.tag = tag;
            this.container = container;
            this.switchView = switchView;
        }
    }


    public interface OnFontChangedListener {
        void onFontChanged(String fontRealName, String fontFileName);
        void onFontCleared();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnFontChangedListener) {
            fontChangedListener = (OnFontChangedListener) context;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        fontChangedListener = null;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);

        viewModel = new ViewModelProvider(this).get(FontViewerViewModel.class);
        settingsViewModel = new ViewModelProvider(requireActivity()).get(FontPreviewSettingsViewModel.class);

        currentFontSize = (sSessionFontSize > 0f) ? sSessionFontSize : DEFAULT_FONT_SIZE;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        menu.clear(); 
        inflater.inflate(R.menu.menu_main_font_meta, menu);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_font_meta) {
            if (getActivity() instanceof FontViewerActivity) {
                ((FontViewerActivity) getActivity()).showFontMetaFromFragment();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);

        setMenuVisibility(!hidden);
        if (!hidden && getActivity() != null) {
            getActivity().invalidateOptionsMenu(); 
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                                @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_font_viewer, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initViews(view);

        // ★ اذا وصلت بيانات الخط الجاهزة مسبقاً قبل إنشاء الواجهة، نعرضها الآن قبل رسم الإطار الأول ★
        if (prefetchedUiPending) {
            applyLoadedFontUi();
        }

        settingsViewModel.getPreviewText().observe(getViewLifecycleOwner(), previewText -> {
            if (previewSentence != null && previewText != null) {
                previewSentence.setText(previewText);
                if (currentTypeface != null) {
                    applyFontToPreviewTexts();
                }
                Log.d(TAG, "Preview text updated from settings");
            }
        });

        if (savedInstanceState != null) {
            currentFontPath        = savedInstanceState.getString(KEY_FONT_PATH);
            currentFontFileName    = savedInstanceState.getString(KEY_FONT_FILE_NAME);
            currentFontRealName    = savedInstanceState.getString(KEY_FONT_REAL_NAME);
            originalFontPath       = savedInstanceState.getString(KEY_ORIGINAL_FONT_PATH);
            currentFontSize        = savedInstanceState.getFloat(KEY_FONT_SIZE, DEFAULT_FONT_SIZE);
            isVariableFont         = savedInstanceState.getBoolean(KEY_IS_VARIABLE_FONT, false);
            currentTtcIndex        = savedInstanceState.getInt(KEY_TTC_INDEX, 0);
            isSystemFont           = savedInstanceState.getBoolean(KEY_IS_SYSTEM_FONT, false);
            currentWeightWidthLabel = savedInstanceState.getString(KEY_WEIGHT_WIDTH_LABEL);

            @SuppressWarnings("unchecked")
            HashMap<String, Float> savedAxisValues =
                    (HashMap<String, Float>) savedInstanceState.getSerializable(KEY_AXIS_VALUES);
            currentAxisValues.clear();
            if (savedAxisValues != null) {
                currentAxisValues.putAll(savedAxisValues);
            }

            if (currentFontPath != null && !currentFontPath.isEmpty()) {
                notifyFontChangedImmediate();
                loadFontFromPathWithAxes(currentFontPath, currentAxisValues, false);
            }
        } else {
            Intent hostIntent = requireActivity().getIntent();
            boolean hasFontFromIntent = hostIntent != null
                    && hostIntent.getStringExtra(FontViewerActivity.EXTRA_FONT_PATH) != null;
            if (!hasFontFromIntent) {
                loadLastUsedFont();
            }
        }


        updatePreviewTexts();
        
        if (getActivity() instanceof FontViewerActivity) {
            ((FontViewerActivity) getActivity()).updateFabFontSizeText(currentFontSize);
        }
        
        if (getActivity() instanceof FontViewerActivity) {
            FontViewerActivity main = (FontViewerActivity) getActivity();
            formattingHelper.setup(main.getBtnBold(), main.getBtnItalic(), (isFakeBold, isFakeItalic) -> {
                if (previewSentence != null) {
                    previewSentence.getPaint().setFakeBoldText(isFakeBold);
                    previewSentence.getPaint().setTextSkewX(isFakeItalic ? -0.25f : 0f);
                    previewSentence.invalidate(); 
                }
            });
            formattingHelper.restoreState(savedInstanceState);
            formattingHelper.syncViewState();
        }

    }


    @Override
    public void onDestroyView() {
        // إيقاف كل أنيميشن محاور الخط المتغير الجارية لمنع تسريب الذاكرة (Memory Leak)
        for (ValueAnimator animator : axisAnimators.values()) {
            if (animator != null && animator.isRunning()) {
                animator.cancel();
            }
        }
        axisAnimators.clear();

    	formattingHelper.unbind();
        super.onDestroyView();
        previewSentence        = null;
        weightLabelText        = null;
        variableAxesContainer  = null;
        weightAxisUi           = null;
        widthAxisUi             = null;
        italicAxisUi            = null;
        gradeAxisUi              = null;
        roundnessAxisUi          = null;
        monoAxisUi               = null;
        allAxisUis               = null;
    }

    private void onFontSizeChanged(float newSize) {
        currentFontSize = newSize;
        applyFontSize();
        sSessionFontSize = newSize;
        if (getActivity() instanceof FontViewerActivity) {
            ((FontViewerActivity) getActivity()).updateFabFontSizeText(newSize);
        }
    }

    /**
     * يتم استدعاؤها عند تغيير قيمة أي محور من محاور الخط المتغير عبر Spinner الخاص به
     * (أو عبر المفتاح في حالة محور الميل).
     * تحرّك المعاينة مباشرة (لأداء سلس)، ثم تعيد بناء الـ Typeface في الخلفية بعد انتهاء الحركة
     * لضمان استقرار خصائص الخط عند أي تعديل لاحق (نفس فلسفة التعامل مع الوزن سابقاً).
     */
    private void onAxisValueChanged(String axisTag, VariableFontAxisExtractor.VariableInstance instance) {
        if (instance == null || currentFontPath == null) {
            return;
        }

        File fontFile = new File(currentFontPath);
        if (!fontFile.exists()) {
            return;
        }

        Float storedOldValue = currentAxisValues.get(axisTag);
        float oldValue = storedOldValue != null ? storedOldValue : instance.value;
        final float newValue = instance.value;

        if (oldValue == newValue) {
            return;
        }

        viewModel.saveFontAxisValue(axisTag, newValue);

        ValueAnimator runningAnimator = axisAnimators.get(axisTag);
        if (runningAnimator != null && runningAnimator.isRunning()) {
            // إزالة المستمعين قبل الإلغاء: cancel() يستدعي onAnimationEnd القديم الذي يقفز بالقيمة الى هدفها السابق
            runningAnimator.removeAllListeners();
            runningAnimator.cancel();
            Float refreshedOld = currentAxisValues.get(axisTag);
            if (refreshedOld != null) {
                oldValue = refreshedOld;
            }
        }

        ValueAnimator animator = ValueAnimator.ofFloat(oldValue, newValue);
        animator.setDuration(800);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());

        animator.addUpdateListener(animation -> {
            float animatedValue = (float) animation.getAnimatedValue();
            currentAxisValues.put(axisTag, animatedValue);

            if (previewSentence != null) {
                previewSentence.setFontVariationSettings(
                    VariableTypefaceFactory.buildVariationSettingsString(currentAxisValues)
                );
            }
        });

        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                currentAxisValues.put(axisTag, newValue);

                // بناء Typeface النهائي في الخلفية لضمان استقرار خصائص الخط عند تغيير حجمه أو محاوره لاحقاً
                final String rebuildPath = currentFontPath;
                if (rebuildPath == null) {
                    return;
                }
                viewModel.rebuildTypeface(rebuildPath, currentAxisValues, currentTtcIndex, isSystemFont, finalTypeface -> {
                    if (finalTypeface != null) {
                        currentTypeface = finalTypeface;
                    }
                });
            }
        });

        axisAnimators.put(axisTag, animator);
        animator.start();
    }

    private void updatePreviewTexts() {
        if (previewSentence == null) {
            return;
        }

        String previewText = viewModel.getPreviewText();
        previewSentence.setText(previewText);

        if (currentTypeface != null) {
            applyFontToPreviewTexts();
        }

        applyFontSize();
    }

    private void initViews(View view) {
        previewSentence = view.findViewById(R.id.preview_sentence);
        weightLabelText = view.findViewById(R.id.weight_label_text);

        variableAxesContainer = view.findViewById(R.id.variable_axes_container);
        ViewGroup axesRow = view.findViewById(R.id.variable_axes_row);

        weightAxisUi = inflateAxisItem(axesRow, VariableFontAxisExtractor.AXIS_WGHT,
                R.string.font_info_axis_weight, R.string.axis_info_weight_dialog_title, R.drawable.axis_weight_info_img, R.string.axis_info_weight_description);
        widthAxisUi = inflateAxisItem(axesRow, VariableFontAxisExtractor.AXIS_WDTH,
                R.string.font_info_axis_width, R.string.axis_info_width_dialog_title, R.drawable.axis_width_info_img, R.string.axis_info_width_description);
        italicAxisUi = inflateAxisSwitchItem(axesRow, VariableFontAxisExtractor.AXIS_ITAL,
                R.string.font_info_axis_italic, R.string.axis_info_italic_dialog_title, R.drawable.axis_italic_info_img, R.string.axis_info_italic_description);
        gradeAxisUi = inflateAxisItem(axesRow, VariableFontAxisExtractor.AXIS_GRAD,
                R.string.font_info_axis_grade, R.string.axis_info_grade_dialog_title, R.drawable.axis_grade_info_img, R.string.axis_info_grade_description);
        roundnessAxisUi = inflateAxisItem(axesRow, VariableFontAxisExtractor.AXIS_ROND,
                R.string.font_info_axis_roundness, R.string.axis_info_roundness_dialog_title, R.drawable.axis_roundness_info_img, R.string.axis_info_roundness_description);
        monoAxisUi = inflateAxisItem(axesRow, VariableFontAxisExtractor.AXIS_MONO,
                R.string.font_info_axis_monospace, R.string.axis_info_monospace_dialog_title, R.drawable.axis_monospace_info_img, R.string.axis_info_monospace_description);

        allAxisUis = new ArrayList<>();
        allAxisUis.add(weightAxisUi);
        allAxisUis.add(widthAxisUi);
        allAxisUis.add(gradeAxisUi);
        allAxisUis.add(roundnessAxisUi);
        allAxisUis.add(monoAxisUi);
    }

    /**
     * ★ ينفخ (Inflate) نسخة واحدة من قالب البطاقة الموحّد vf_axis_spinner_item لهذا المحور، ويربط
     *   اسم المحور (الذي يفتح ديالوج معلومات المحور عند الضغط عليه)، ثم يضيفها الى صف المحاور. هذا يحدث مرة واحدة فقط
     *   عند إنشاء الـ Fragment (initViews)، وليس عند كل فتح خط، فلا توجد أي كلفة أداء إضافية
     *   عند فتح الخطوط لاحقاً — نفس عدد الـ Views ونفس منطق الإظهار/الإخفاء كما كان سابقاً ★
     */
    private AxisSpinnerUi inflateAxisItem(ViewGroup parent, String axisTag, @StringRes int labelRes,
                                           @StringRes int infoTitleRes, @DrawableRes int infoImageRes,
                                           @StringRes int infoDescriptionRes) {
        View item = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.vf_axis_spinner_item, parent, false);

        TextView nameText = item.findViewById(R.id.axis_name_text);
        if (nameText != null) {
            nameText.setText(labelRes);
            nameText.setOnClickListener(v ->
                    AxisInfoDialog.show(requireContext(), infoTitleRes, infoImageRes, infoDescriptionRes));
        }

        AppCompatSpinner spinner = item.findViewById(R.id.axis_spinner);

        parent.addView(item);

        return new AxisSpinnerUi(axisTag, item, spinner);
    }

    /**
     * نفس فكرة inflateAxisItem تماماً، لكن للمحاور الثنائية القيمة: ينفخ بطاقة vf_axis_switch_item
     * (الاسم القابل للضغط لفتح معلومات المحور في الأعلى والمفتاح تحته) ويضيفها الى صف المحاور مرة واحدة فقط عند الإنشاء.
     */
    private AxisSwitchUi inflateAxisSwitchItem(ViewGroup parent, String axisTag, @StringRes int labelRes,
                                                @StringRes int infoTitleRes, @DrawableRes int infoImageRes,
                                                @StringRes int infoDescriptionRes) {
        View item = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.vf_axis_switch_item, parent, false);

        TextView nameText = item.findViewById(R.id.axis_name_text);
        if (nameText != null) {
            nameText.setText(labelRes);
            nameText.setOnClickListener(v ->
                    AxisInfoDialog.show(requireContext(), infoTitleRes, infoImageRes, infoDescriptionRes));
        }

        SwitchCompat switchView = item.findViewById(R.id.axis_switch);

        parent.addView(item);

        return new AxisSwitchUi(axisTag, item, switchView);
    }


    

    public void loadFontFromPath(String path, String fileName, String realName, int ttcIndex, boolean isSystemFont) {
        Log.d(TAG, "loadFontFromPath - Received data:");
        Log.d(TAG, "  realName: " + realName);
        Log.d(TAG, "  fileName: " + fileName);
        Log.d(TAG, "  ttcIndex: " + ttcIndex);
        Log.d(TAG, "  isSystemFont: " + isSystemFont);

        currentFontPath     = path;
        currentFontFileName = fileName;
        currentFontRealName = realName;
        currentTtcIndex     = ttcIndex;
        this.isSystemFont   = isSystemFont;

        if (originalFontPath == null || originalFontPath.isEmpty()) {
            originalFontPath = extractRealPathFromUri(path);
        }

        viewModel.saveLastViewedFont(path, fileName, realName, originalFontPath);

        notifyFontChangedImmediate();

        if (formattingHelper != null) {
            formattingHelper.reset();
        }

        // ★ البيانات المجهّزة مسبقاً (FontPrefetcher): اذا جُهّز الخط وتحليل محاوره أثناء فتح الشاشة، نطبّق كل شيء فوراً
        //   (الخط + بطاقات المحاور أو تسمية الوزن) قبل رسم الإطار الأول، ولا نحتاج الى التحميل الخلفي إطلاقاً ★
        if (applyPrefetchedFont(path, ttcIndex, isSystemFont)) {
            return;
        }

        // ★ لم تكن البيانات جاهزة: نطبّق الخط فوراً من الذاكرة المؤقتة (Instant Cache Hit) إن وُجد، فلا يظهر الخط الافتراضي،
        //   ثم نتابع التحميل الكامل في الخلفية كالمعتاد. اذا لم تُنشأ الواجهة بعد يُطبَّق في onViewCreated عبر updatePreviewTexts ★
        Typeface cachedTypeface = viewModel.getCachedTypeface(path, ttcIndex, isSystemFont);
        if (cachedTypeface != null) {
            currentTypeface = cachedTypeface;
            if (previewSentence != null) {
                applyFontToPreviewTexts();
            }
        }

        // خط جديد تم اختياره: نعيد كل محاوره الى قيمها الافتراضية بدل حمل قيم الخط السابق
        loadFontFromPathWithAxes(path, null, true);
    }

    /**
     * يطبّق نتيجة التجهيز المسبق (إن وُجدت وصالحة) بنفس ما كان سيفعله التحميل الخلفي عند النجاح، لكن فوراً وعلى الخيط الرئيسي
     * ودون أي قراءة ملفات (كل ما فيها محسوب مسبقاً). يعيد false اذا لم تكن جاهزة، فيستمر المسار القديم كما هو.
     */
    private boolean applyPrefetchedFont(String path, int ttcIndex, boolean systemFont) {
        Context context = getContext();
        if (context == null) {
            return false;
        }

        FontPrefetcher.PrefetchedFont data = viewModel.takePrefetched(AxisLabelProvider.localeTagsOf(context), path, ttcIndex, systemFont);
        if (data == null) {
            Log.d(TAG, "Prefetch miss - falling back to background load: " + path);
            return false;
        }
        Log.d(TAG, "Prefetch hit - applying font synchronously: " + path);

        // خط جديد: كل محاوره تعود الى قيمها الافتراضية ونحفظها (نفس ما يفعله التحميل الخلفي عند resetToDefaults)
        viewModel.saveFontAxisValues(data.axisDefaults);

        currentTypeface = data.typeface;
        isVariableFont  = data.isVariable;
        currentAxisValues.clear();
        currentAxisValues.putAll(data.axisDefaults);
        prefetchedAxisInstancesMap = data.axisInstancesMap;
        prefetchedUiPending = true;

        // اذا لم تُنشأ الواجهة بعد، تُطبَّق الحالة في onViewCreated قبل رسم الإطار الأول
        applyLoadedFontUi();
        return true;
    }

    /**
     * يعرض حالة الخط المحمّل (بطاقات المحاور للخط المتغير أو تسمية الوزن للثابت + الخط على المعاينة) على الواجهة،
     * بنفس منطق النجاح في loadFontFromPathWithAxes. لا تفعل شيئاً اذا لم تُنشأ الواجهة بعد (يبقى prefetchedUiPending).
     */
    private void applyLoadedFontUi() {
        if (variableAxesContainer == null || previewSentence == null) {
            return;
        }

        if (isVariableFont && !currentAxisValues.isEmpty() && prefetchedAxisInstancesMap != null) {
            setupAxisSpinners(prefetchedAxisInstancesMap);
        } else {
            showWeightLabel(currentWeightWidthLabel);
        }

        applyFontToPreviewTexts();
        prefetchedUiPending = false;
    }

    public void loadFontFromPath(String path, String fileName, String realName,
                                 int ttcIndex, boolean isSystemFont, String weightWidthLabel) {
        currentWeightWidthLabel = weightWidthLabel;
        loadFontFromPath(path, fileName, realName, ttcIndex, isSystemFont);
    }

    private String extractRealPathFromUri(String pathOrUri) {
        return viewModel.resolveOriginalPath(pathOrUri);
    }

    private void notifyFontChangedImmediate() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            performNotification();
        } else {
            mainHandler.post(this::performNotification);
        }
    }

    private void performNotification() {
        if (fontChangedListener != null && currentFontFileName != null) {
            fontChangedListener.onFontChanged(currentFontRealName, currentFontFileName);
            Log.d(TAG, "MainActivity notified with realName: " + currentFontRealName + ", fileName: " + currentFontFileName);
        }
    }

    /**
     * يحمّل الخط من مساره، ويحدد لكل محور مدعوم (wght, wdth, ital, GRAD, ROND, MONO) القيمة التي
     * سيُفتح بها، وفق ثلاث حالات:
     * 1) restoreAxisValues غير فارغة لمحور معيّن: يُستخدم بعد تدوير الشاشة، نستعيد القيمة كما كانت
     *    معروضة تماماً قبل التدوير.
     * 2) resetToDefaults = true: خط جديد تم اختياره، فنعيد هذا المحور الى قيمته الافتراضية الفعلية
     *    في الخط ونحفظها.
     * 3) غير ذلك (فتح آخر خط تم عرضه عند فتح التطبيق): نستخدم آخر قيمة محفوظة لهذا المحور.
     */
    private void loadFontFromPathWithAxes(String path, Map<String, Float> restoreAxisValues, boolean resetToDefaults) {
        Context axisValuesContext = getContext();
        if (axisValuesContext == null) {
            return;
        }
        // Context يُستخدم لقراءة أسماء قيم المحاور من strings.xml حسب لغة التطبيق
        InstanceLabelResolver axisLabels = AxisLabelProvider.of(axisValuesContext);

        viewModel.loadFont(path, currentTtcIndex, isSystemFont, restoreAxisValues, resetToDefaults, axisLabels, result -> {
            switch (result.status) {
                case FILE_MISSING:
                    resetFontDisplay();
                    break;

                case SUCCESS:
                    currentTypeface = result.typeface;
                    isVariableFont  = result.isVariable;

                    currentAxisValues.clear();
                    currentAxisValues.putAll(result.resolvedAxisValues);

                    if (result.isVariable && !result.resolvedAxisValues.isEmpty()) {
                        setupAxisSpinners(result.axisInstancesMap);
                    } else {
                        showWeightLabel(currentWeightWidthLabel);
                    }

                    applyFontToPreviewTexts();
                    Log.d(TAG, "Font typeface loaded and applied successfully");
                    break;

                default:
                    onFontLoadFailed(path, result);
                    break;
            }
        });
    }

    /**
     * يعرض حالة الفشل: الخط تالف (TYPEFACE_ERROR) أو خطأ عام (ERROR). في الحالتين يصبح اسم الخط "غير معروف"
     * وتُخفى بطاقات المحاور ويُعرض الخط الافتراضي، مع رسالة طويلة للخط التالف وقصيرة للخطأ العام.
     */
    private void onFontLoadFailed(String path, FontViewerRepository.LoadResult result) {
        boolean corruptedFont = result.status == FontViewerRepository.LoadStatus.TYPEFACE_ERROR;

        currentFontRealName = null;
        currentTypeface     = null;

        if (fontChangedListener != null) {
            fontChangedListener.onFontChanged(currentFontRealName, currentFontFileName);
            Log.d(TAG, corruptedFont
                    ? "★ Updated title to 'Unknown Font' for corrupted font"
                    : "★ Updated title to 'Unknown Font' after general error");
        }

        hideAxisUi();

        Typeface defaultTypeface = Typeface.DEFAULT;
        if (previewSentence != null) {
            previewSentence.setTypeface(defaultTypeface);
        }

        Context context = getContext();
        if (context != null) {
            Toast.makeText(context,
                context.getString(R.string.font_viewer_error_loading_font) +
                " (" + context.getString(R.string.unknown_font) + ")",
                corruptedFont ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
        }

        if (!corruptedFont) {
            Log.e(TAG, "Error creating typeface from path: " + path, result.error);
        }
    }


    private void setupAxisSpinners(Map<String, List<VariableFontAxisExtractor.VariableInstance>> axisInstancesMap) {
        if (!isAdded() || variableAxesContainer == null || weightLabelText == null || allAxisUis == null) return;

        weightLabelText.setVisibility(View.GONE);
        variableAxesContainer.setVisibility(View.VISIBLE);

        for (AxisSpinnerUi ui : allAxisUis) {
            List<VariableFontAxisExtractor.VariableInstance> instances = axisInstancesMap.get(ui.tag);
            setupSingleAxisSpinner(ui, instances);
        }

        // محور الميل له بطاقة مفتاح مستقلة، ويُخفى تلقائياً اذا لم يدعمه الخط
        setupItalicSwitch(italicAxisUi, axisInstancesMap.get(VariableFontAxisExtractor.AXIS_ITAL));
    }

    private void setupSingleAxisSpinner(AxisSpinnerUi ui, List<VariableFontAxisExtractor.VariableInstance> instances) {
        if (ui == null || ui.container == null || ui.spinner == null) return;

        if (instances == null || instances.isEmpty()) {
            ui.container.setVisibility(View.GONE);
            ui.instances = new ArrayList<>();
            return;
        }

        ui.instances = instances;
        ui.container.setVisibility(View.VISIBLE);

        List<String> instanceNames = new ArrayList<>();
        for (VariableFontAxisExtractor.VariableInstance inst : instances) {
            instanceNames.add(inst.name);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            requireContext(),
            R.layout.vf_axis_spinner_selected,
            instanceNames
        );
        adapter.setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item);

        ui.spinner.setAdapter(adapter);

        Float currentValue = currentAxisValues.get(ui.tag);
        int selectedIndex = 0;
        if (currentValue != null) {
            float closestDiff = Float.MAX_VALUE;
            for (int i = 0; i < instances.size(); i++) {
                float diff = Math.abs(instances.get(i).value - currentValue);
                if (diff < closestDiff) {
                    closestDiff = diff;
                    selectedIndex = i;
                }
            }
        }
        ui.spinner.setSelection(selectedIndex);

        final List<VariableFontAxisExtractor.VariableInstance> finalInstances = instances;
        ui.spinner.post(() -> {
            if (ui.spinner == null || !isAdded()) return;
            ui.spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (position >= 0 && position < finalInstances.size()) {
                        onAxisValueChanged(ui.tag, finalInstances.get(position));
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        });
    }

    /**
     * يضبط بطاقة مفتاح محور الميل: تُخفى اذا لم يدعم الخط المحور،
     * وإلا يُضبط المفتاح على أقرب قيمة للقيمة الحالية دون إطلاق المستمع، ثم يُربط المستمع الذي يحرّك المحور.
     * نص المفتاح يعرض اسم القيمة الحالية (Upright / Italic) من نفس أسماء القيم المستخرجة.
     */
    private void setupItalicSwitch(AxisSwitchUi ui, List<VariableFontAxisExtractor.VariableInstance> instances) {
        if (ui == null || ui.container == null || ui.switchView == null) return;

        if (instances == null || instances.isEmpty()) {
            ui.switchView.setOnCheckedChangeListener(null);
            ui.container.setVisibility(View.GONE);
            return;
        }

        final VariableFontAxisExtractor.VariableInstance offInstance = instances.get(0);
        final VariableFontAxisExtractor.VariableInstance onInstance  = instances.get(instances.size() - 1);
        ui.container.setVisibility(View.VISIBLE);

        Float currentValue = currentAxisValues.get(ui.tag);
        boolean checked = currentValue != null
                && Math.abs(onInstance.value - currentValue) < Math.abs(offInstance.value - currentValue);

        // نزيل المستمع أولاً حتى لا يطلق ضبط الحالة الابتدائية حركة المحور
        ui.switchView.setOnCheckedChangeListener(null);
        ui.switchView.setChecked(checked);
        ui.switchView.jumpDrawablesToCurrentState();
        ui.switchView.setText(checked ? onInstance.name : offInstance.name);

        ui.switchView.setOnCheckedChangeListener((button, isChecked) -> {
            VariableFontAxisExtractor.VariableInstance target = isChecked ? onInstance : offInstance;
            button.setText(target.name);
            onAxisValueChanged(ui.tag, target);
        });
    }

    private void showWeightLabel(String label) {
        if (weightLabelText == null || variableAxesContainer == null) return;

        variableAxesContainer.setVisibility(View.GONE);

        String displayLabel = FontLabelFormatter.formatLabel(weightLabelText.getContext(), label);

        if (displayLabel != null && !displayLabel.isEmpty()) {
            weightLabelText.setText(displayLabel);
            weightLabelText.setVisibility(View.VISIBLE);
        } else {
            weightLabelText.setVisibility(View.GONE);
        }
    }

    private void hideAxisUi() {
        if (weightLabelText != null) weightLabelText.setVisibility(View.GONE);
        if (variableAxesContainer != null) variableAxesContainer.setVisibility(View.GONE);
    }

    public void loadFontFromUri(Uri uri, String fileName) {
        originalFontPath = viewModel.getRealPathFromUri(uri);
        if (originalFontPath == null || originalFontPath.isEmpty()) {
            originalFontPath = android.net.Uri.decode(uri.toString());
        }
        isSystemFont     = false;

        viewModel.importFont(uri, fileName, imported -> {
            if (!isAdded()) {
                return;
            }

            if (imported != null) {
                File copiedFont = imported.file;
                String realName = imported.realName;
                currentWeightWidthLabel = imported.weightWidthLabel;

                if (realName == null || realName.isEmpty() || "Unknown Font".equals(realName) || getString(R.string.unknown_font).equals(realName)) {
                    String finalFileName = fileName != null ? fileName : copiedFont.getName();

                    loadFontFromPath(copiedFont.getAbsolutePath(), finalFileName, null, 0, false);

                    Toast.makeText(requireContext(),
                        R.string.toast_font_name_extraction_warning,
                        Toast.LENGTH_SHORT).show();
                } else {
                    final String finalFileName = fileName != null ? fileName : copiedFont.getName();

                    loadFontFromPath(copiedFont.getAbsolutePath(), finalFileName, realName, 0, false);
                }
            } else {
                Toast.makeText(requireContext(),
                        getString(R.string.font_viewer_error_loading_font),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void applyFontToPreviewTexts() {
        if (currentTypeface != null && previewSentence != null) {
            previewSentence.setTypeface(currentTypeface);
            previewSentence.getPaint().setFakeBoldText(formattingHelper.isBoldActive());
            previewSentence.getPaint().setTextSkewX(formattingHelper.isItalicActive() ? -0.25f : 0f);
        }
        applyFontSize();
    }


    private void applyFontSize() {
        if (previewSentence != null) {
            previewSentence.setTextSize(TypedValue.COMPLEX_UNIT_DIP, currentFontSize);
        }
    }

    public void showFontSizeDialogPublic() {
        final float originalSize      = currentFontSize;

        FontSizeDialog fontSizeDialog = new FontSizeDialog(
            requireContext(),
            currentFontSize,
            MIN_FONT_SIZE,
            MAX_FONT_SIZE
        );

        fontSizeDialog.setOnFontSizeChangedListener(this::onFontSizeChanged);

        fontSizeDialog.setOnDialogCancelledListener(() -> {
            // الحوار يغيّر الحجم فقط، فلا نستعيد Typeface محفوظاً قبل فتحه: قد يكون قديماً إن فُتح الحوار أثناء حركة المحور
            currentFontSize   = originalSize;
            applyFontToPreviewTexts();
            applyFontSize();
            
            if (getActivity() instanceof FontViewerActivity) {
                ((FontViewerActivity) getActivity()).updateFabFontSizeText(originalSize);
            }
        });

        fontSizeDialog.show();
    }

    private void resetFontDisplay() {
        currentTypeface         = null;
        currentFontPath         = null;
        currentFontFileName     = null;
        currentFontRealName     = null;
        originalFontPath        = null;
        isVariableFont          = false;
        currentAxisValues.clear();
        currentTtcIndex         = 0;
        isSystemFont            = false;
        currentWeightWidthLabel  = null;

        Typeface defaultTypeface = Typeface.DEFAULT;
        if (previewSentence != null) previewSentence.setTypeface(defaultTypeface);

        hideAxisUi();

        if (fontChangedListener != null) {
            fontChangedListener.onFontCleared();
        }
        
        if (formattingHelper != null) {
            formattingHelper.reset();
        }

    }

    private void loadLastUsedFont() {
        // قراءة آخر خط عُرض (الإعدادات + فحص الملف + استخراج وزنه وعرضه من الملف) كانت تتم على الخيط الرئيسي أثناء
        // إنشاء الشاشة. بعد إعادة تشغيل الجهاز تكون ملفات الخطوط خارج ذاكرة النظام المؤقتة، فتتأخر القراءة وتتجمد
        // الواجهة. لذلك تُنفَّذ الآن في الخلفية، ثم تُطبَّق النتيجة على الخيط الرئيسي.
        viewModel.loadLastViewedFont(last -> {
            if (last == null) {
                return;
            }

            // الشاشة أُغلقت، أو اختار المستخدم خطاً آخر أثناء القراءة في الخلفية: لا نكتب فوق اختياره
            if (!isAdded() || (currentFontPath != null && !currentFontPath.isEmpty())) {
                return;
            }

            currentFontPath     = last.path;
            currentFontFileName = last.fileName;
            currentFontRealName = last.realName;
            currentTtcIndex     = 0;
            isSystemFont        = false;
            currentWeightWidthLabel = last.weightWidthLabel;

            if (currentFontRealName != null && (currentFontRealName.isEmpty() || currentFontRealName.equals(getString(R.string.unknown_font)))) {
                currentFontRealName = null;
            }

            if (originalFontPath == null || originalFontPath.isEmpty()) {
                originalFontPath = last.originalPath;
            }

            notifyFontChangedImmediate();
            // اعادة فتح آخر خط تم عرضه: نستخدم آخر قيمة محفوظة لكل محور من محاوره
            loadFontFromPathWithAxes(last.path, null, false);
        });
    }

    public Map<String, String> getFontMetadata() {
        if (currentFontPath == null) {
            return new HashMap<>();
        }

        return viewModel.buildMetadata(currentFontPath, originalFontPath, currentFontFileName,
                currentFontRealName, currentTtcIndex, getString(R.string.unknown_font));
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
    	formattingHelper.saveState(outState);
        super.onSaveInstanceState(outState);
        if (currentFontPath != null) {
            outState.putString(KEY_FONT_PATH, currentFontPath);
        }
        if (currentFontFileName != null) {
            outState.putString(KEY_FONT_FILE_NAME, currentFontFileName);
        }
        if (currentFontRealName != null) {
            outState.putString(KEY_FONT_REAL_NAME, currentFontRealName);
        }
        if (originalFontPath != null) {
            outState.putString(KEY_ORIGINAL_FONT_PATH, originalFontPath);
        }
        if (currentWeightWidthLabel != null) {
            outState.putString(KEY_WEIGHT_WIDTH_LABEL, currentWeightWidthLabel);
        }
        outState.putFloat(KEY_FONT_SIZE, currentFontSize);
        // حفظ قيم كل محاور الخط المتغير الحالية (الوزن، العرض، الميل، التدرج، الاستدارة، والتباعد الأحادي) دفعة واحدة
        outState.putSerializable(KEY_AXIS_VALUES, new HashMap<>(currentAxisValues));
        outState.putBoolean(KEY_IS_VARIABLE_FONT, isVariableFont);
        outState.putInt(KEY_TTC_INDEX, currentTtcIndex);
        outState.putBoolean(KEY_IS_SYSTEM_FONT, isSystemFont);
    }

    public String getCurrentFontFileName() {
        return currentFontFileName;
    }

    public boolean hasFontSelected() {
        return currentFontPath != null && !currentFontPath.isEmpty();
    }
}
