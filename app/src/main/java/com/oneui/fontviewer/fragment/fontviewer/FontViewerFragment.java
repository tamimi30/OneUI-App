package com.oneui.fontviewer.fragment.fontviewer;

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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatSpinner;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.oneui.fontviewer.dialog.FontSizeDialog;
import com.oneui.fontviewer.R;
import com.oneui.fontviewer.fragment.fontviewer.utils.VariableFontHelper;
import com.oneui.fontviewer.fragment.settings.utils.SettingsHelper;
import com.oneui.fontviewer.fragment.fontviewer.manager.FontViewerStorageManager;
import com.oneui.fontviewer.fragment.fontviewer.manager.FontViewerPreferenceManager;
import com.oneui.fontviewer.fragment.systemfont.data.SystemFontCache;
import com.oneui.fontviewer.metadata.FontMetadataExtractor;
import com.oneui.fontviewer.fragment.settings.viewmodel.SettingsViewModel;
import com.oneui.fontviewer.metadata.FontWeightWidthExtractor;
import com.oneui.fontviewer.fragment.fontviewer.utils.BoldItalicFormatting;

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
    private static final float DEFAULT_FONT_WEIGHT = 400f;

    // القيم الافتراضية الاحتياطية لكل محور، تُستخدم فقط اذا تعذّرت قراءة القيمة الافتراضية الفعلية
    // من جدول fvar الخاص بالخط نفسه
    private static final Map<String, Float> AXIS_FALLBACK_DEFAULTS = new HashMap<>();
    static {
        AXIS_FALLBACK_DEFAULTS.put(VariableFontHelper.AXIS_WGHT, DEFAULT_FONT_WEIGHT);
        AXIS_FALLBACK_DEFAULTS.put(VariableFontHelper.AXIS_WDTH, 100f);
        AXIS_FALLBACK_DEFAULTS.put(VariableFontHelper.AXIS_ITAL, 0f);
        AXIS_FALLBACK_DEFAULTS.put(VariableFontHelper.AXIS_GRAD, 0f);
        AXIS_FALLBACK_DEFAULTS.put(VariableFontHelper.AXIS_ROND, 0f);
        AXIS_FALLBACK_DEFAULTS.put(VariableFontHelper.AXIS_MONO, 0f);
    }

    private static float sSessionFontSize = -1f;

    private TextView previewSentence;
    private TextView weightLabelText;

    private View variableAxesContainer;
    private View axesBottomDivider;
    private View[] axisDividers;
    private AxisSpinnerUi weightAxisUi;
    private AxisSpinnerUi widthAxisUi;
    private AxisSpinnerUi italicAxisUi;
    private AxisSpinnerUi gradeAxisUi;
    private AxisSpinnerUi roundnessAxisUi;
    private AxisSpinnerUi monoAxisUi;
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

    private final ExecutorService bgExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private FontViewerStorageManager storageManager;
    private FontViewerPreferenceManager preferenceManager;
    private SettingsViewModel settingsViewModel;
    private BoldItalicFormatting formattingHelper = new BoldItalicFormatting();

    /**
     * تمثل ربط عنصر واجهة واحد بمحور من محاور الخط المتغير (الحاوية + Spinner + القيم المتاحة له).
     */
    private static class AxisSpinnerUi {
        final String tag;
        final View container;
        final AppCompatSpinner spinner;
        List<VariableFontHelper.VariableInstance> instances = new ArrayList<>();

        AxisSpinnerUi(String tag, View container, AppCompatSpinner spinner) {
            this.tag = tag;
            this.container = container;
            this.spinner = spinner;
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

        storageManager    = new FontViewerStorageManager(context);
        preferenceManager = new FontViewerPreferenceManager(context);
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

        settingsViewModel = new ViewModelProvider(requireActivity()).get(SettingsViewModel.class);

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
        axesBottomDivider      = null;
        axisDividers           = null;
        weightAxisUi           = null;
        widthAxisUi             = null;
        italicAxisUi            = null;
        gradeAxisUi              = null;
        roundnessAxisUi          = null;
        monoAxisUi               = null;
        allAxisUis               = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        bgExecutor.shutdownNow();
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
     * يتم استدعاؤها عند تغيير قيمة أي محور من محاور الخط المتغير عبر Spinner الخاص به.
     * تحرّك المعاينة مباشرة (لأداء سلس)، ثم تعيد بناء الـ Typeface في الخلفية بعد انتهاء الحركة
     * لضمان استقرار خصائص الخط عند أي تعديل لاحق (نفس فلسفة التعامل مع الوزن سابقاً).
     */
    private void onAxisValueChanged(String axisTag, VariableFontHelper.VariableInstance instance) {
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

        preferenceManager.saveFontAxisValue(axisTag, newValue);

        ValueAnimator runningAnimator = axisAnimators.get(axisTag);
        if (runningAnimator != null && runningAnimator.isRunning()) {
            runningAnimator.cancel();
            Float refreshedOld = currentAxisValues.get(axisTag);
            if (refreshedOld != null) {
                oldValue = refreshedOld;
            }
        }

        ValueAnimator animator = ValueAnimator.ofFloat(oldValue, newValue);
        animator.setDuration(600);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());

        animator.addUpdateListener(animation -> {
            float animatedValue = (float) animation.getAnimatedValue();
            currentAxisValues.put(axisTag, animatedValue);

            if (previewSentence != null) {
                previewSentence.setFontVariationSettings(
                    VariableFontHelper.buildVariationSettingsString(currentAxisValues)
                );
            }
        });

        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                currentAxisValues.put(axisTag, newValue);

                // بناء Typeface النهائي في الخلفية لضمان استقرار خصائص الخط عند تغيير حجمه أو محاوره لاحقاً
                final Map<String, Float> snapshot = new LinkedHashMap<>(currentAxisValues);
                bgExecutor.execute(() -> {
                    Typeface finalTypeface;
                    if (isSystemFont) {
                        finalTypeface = SystemFontCache.getInstance()
                                .getTypefaceWithAxes(currentFontPath, snapshot, currentTtcIndex);
                    } else {
                        finalTypeface = VariableFontHelper.createTypefaceWithAxes(fontFile, snapshot, currentTtcIndex);
                    }
                    if (finalTypeface != null) {
                        mainHandler.post(() -> currentTypeface = finalTypeface);
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

        String previewText = SettingsHelper.getPreviewText(requireContext());
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

        weightAxisUi = new AxisSpinnerUi(
            VariableFontHelper.AXIS_WGHT,
            view.findViewById(R.id.weight_axis_container),
            view.findViewById(R.id.weight_spinner)
        );
        widthAxisUi = new AxisSpinnerUi(
            VariableFontHelper.AXIS_WDTH,
            view.findViewById(R.id.width_axis_container),
            view.findViewById(R.id.width_spinner)
        );
        italicAxisUi = new AxisSpinnerUi(
            VariableFontHelper.AXIS_ITAL,
            view.findViewById(R.id.italic_axis_container),
            view.findViewById(R.id.ital_spinner)
        );
        gradeAxisUi = new AxisSpinnerUi(
            VariableFontHelper.AXIS_GRAD,
            view.findViewById(R.id.grade_axis_container),
            view.findViewById(R.id.grad_spinner)
        );
        roundnessAxisUi = new AxisSpinnerUi(
            VariableFontHelper.AXIS_ROND,
            view.findViewById(R.id.roundness_axis_container),
            view.findViewById(R.id.rond_spinner)
        );
        monoAxisUi = new AxisSpinnerUi(
            VariableFontHelper.AXIS_MONO,
            view.findViewById(R.id.mono_axis_container),
            view.findViewById(R.id.mono_spinner)
        );

        allAxisUis = new ArrayList<>();
        allAxisUis.add(weightAxisUi);
        allAxisUis.add(widthAxisUi);
        allAxisUis.add(italicAxisUi);
        allAxisUis.add(gradeAxisUi);
        allAxisUis.add(roundnessAxisUi);
        allAxisUis.add(monoAxisUi);

        axesBottomDivider = view.findViewById(R.id.axes_bottom_divider);
        axisDividers = new View[] {
            view.findViewById(R.id.divider_weight_width),
            view.findViewById(R.id.divider_width_italic),
            view.findViewById(R.id.divider_italic_grade),
            view.findViewById(R.id.divider_grade_roundness),
            view.findViewById(R.id.divider_roundness_mono)
        };
    }

    /**
     * يُظهر الفاصل بين محورين فقط اذا كان كلاهما ظاهرين، حتى لا يبقى فاصل
     * معلّقاً بجانب محور مخفي.
     */
    private void updateAxisDividers() {
        if (axisDividers == null || allAxisUis == null) return;

        for (int i = 0; i < axisDividers.length && i + 1 < allAxisUis.size(); i++) {
            View divider = axisDividers[i];
            if (divider == null) continue;

            View before = allAxisUis.get(i).container;
            View after  = allAxisUis.get(i + 1).container;

            boolean bothVisible = before != null && after != null
                    && before.getVisibility() == View.VISIBLE
                    && after.getVisibility() == View.VISIBLE;

            divider.setVisibility(bothVisible ? View.VISIBLE : View.GONE);
        }
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

        preferenceManager.saveLastViewedFont(path, fileName, realName);
        preferenceManager.saveLastViewedFontOriginalPath(originalFontPath);

        notifyFontChangedImmediate();

        if (formattingHelper != null) {
            formattingHelper.reset();
        }

        // خط جديد تم اختياره: نعيد كل محاوره الى قيمها الافتراضية بدل حمل قيم الخط السابق
        loadFontFromPathWithAxes(path, null, true);
    }

    public void loadFontFromPath(String path, String fileName, String realName,
                                 int ttcIndex, boolean isSystemFont, String weightWidthLabel) {
        currentWeightWidthLabel = weightWidthLabel;
        loadFontFromPath(path, fileName, realName, ttcIndex, isSystemFont);
    }

    private String extractRealPathFromUri(String pathOrUri) {
        if (pathOrUri == null) return null;

        if (pathOrUri.startsWith("content://")) {
            Uri uri = Uri.parse(pathOrUri);
            String realPath = storageManager.getRealPathFromUri(uri);
            if (realPath != null && !realPath.isEmpty()) {
                return realPath;
            }
        }

        return pathOrUri;
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
        bgExecutor.execute(() -> {
            try {
                File fontFile = new File(path);
                if (!fontFile.exists()) {
                    mainHandler.post(this::resetFontDisplay);
                    return;
                }

                boolean isVar = VariableFontHelper.isVariableFont(fontFile, currentTtcIndex);

                // خريطة وسم المحور الى قائمة القيم المسمّاة المتاحة له
                Map<String, List<VariableFontHelper.VariableInstance>> axisInstancesMap = new LinkedHashMap<>();
                // القيم المختارة فعلياً لكل محور مدعوم
                Map<String, Float> resolvedAxisValues = new LinkedHashMap<>();

                if (isVar) {
                    axisInstancesMap.put(VariableFontHelper.AXIS_WGHT, VariableFontHelper.extractVariableInstances(fontFile, currentTtcIndex));
                    axisInstancesMap.put(VariableFontHelper.AXIS_WDTH, VariableFontHelper.extractWidthInstances(fontFile, currentTtcIndex));
                    axisInstancesMap.put(VariableFontHelper.AXIS_ITAL, VariableFontHelper.extractItalicInstances(fontFile, currentTtcIndex));
                    axisInstancesMap.put(VariableFontHelper.AXIS_GRAD, VariableFontHelper.extractGradeInstances(fontFile, currentTtcIndex));
                    axisInstancesMap.put(VariableFontHelper.AXIS_ROND, VariableFontHelper.extractRoundnessInstances(fontFile, currentTtcIndex));
                    axisInstancesMap.put(VariableFontHelper.AXIS_MONO, VariableFontHelper.extractMonoInstances(fontFile, currentTtcIndex));

                    for (Map.Entry<String, List<VariableFontHelper.VariableInstance>> entry : axisInstancesMap.entrySet()) {
                        String axisTag = entry.getKey();
                        List<VariableFontHelper.VariableInstance> instances = entry.getValue();

                        if (instances == null || instances.isEmpty()) {
                            continue;
                        }

                        Float fallbackBoxed = AXIS_FALLBACK_DEFAULTS.get(axisTag);
                        float fallback = fallbackBoxed != null ? fallbackBoxed : 0f;
                        float fontDefault = VariableFontHelper.readAxisDefaultValue(fontFile, currentTtcIndex, axisTag, fallback);

                        float resolvedValue;
                        if (restoreAxisValues != null && restoreAxisValues.containsKey(axisTag)) {
                            resolvedValue = restoreAxisValues.get(axisTag);
                        } else if (resetToDefaults) {
                            resolvedValue = fontDefault;
                            preferenceManager.saveFontAxisValue(axisTag, resolvedValue);
                        } else {
                            resolvedValue = preferenceManager.getFontAxisValue(axisTag, fontDefault);
                        }

                        resolvedAxisValues.put(axisTag, resolvedValue);
                    }
                }

                Typeface typeface;

                try {
                    if (isSystemFont) {
                        SystemFontCache cache = SystemFontCache.getInstance();
                        typeface = cache.getTypefaceWithAxes(path, resolvedAxisValues, currentTtcIndex);
                    } else {
                        typeface = VariableFontHelper.createTypefaceWithAxes(fontFile, resolvedAxisValues, currentTtcIndex);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "★ Typeface creation failed - font might be corrupted", e);
                    mainHandler.post(() -> {
                        currentFontRealName = null;
                        currentTypeface     = null;

                        if (fontChangedListener != null) {
                            fontChangedListener.onFontChanged(currentFontRealName, currentFontFileName);
                            Log.d(TAG, "★ Updated title to 'Unknown Font' for corrupted font");
                        }

                        hideAxisUi();

                        Typeface defaultTypeface = Typeface.DEFAULT;
                        if (previewSentence != null) {
                            previewSentence.setTypeface(defaultTypeface);
                        }

                        Toast.makeText(requireContext(),
                            getString(R.string.font_viewer_error_loading_font) +
                            " (" + getString(R.string.unknown_font) + ")",
                            Toast.LENGTH_LONG).show();
                    });
                    return;
                }

                if (typeface != null) {
                    final Typeface finalTypeface   = typeface;
                    final boolean finalIsVariable  = isVar;
                    final Map<String, List<VariableFontHelper.VariableInstance>> finalInstancesMap = axisInstancesMap;
                    final Map<String, Float> finalResolvedValues = resolvedAxisValues;

                    mainHandler.post(() -> {
                        currentTypeface = finalTypeface;
                        isVariableFont  = finalIsVariable;

                        currentAxisValues.clear();
                        currentAxisValues.putAll(finalResolvedValues);

                        if (finalIsVariable && !finalResolvedValues.isEmpty()) {
                            setupAxisSpinners(finalInstancesMap);
                        } else {
                            showWeightLabel(currentWeightWidthLabel);
                        }

                        applyFontToPreviewTexts();
                        Log.d(TAG, "Font typeface loaded and applied successfully");
                    });
                } else {
                    throw new Exception("Failed to create Typeface - returned null");
                }

            } catch (Exception e) {
                mainHandler.post(() -> {
                   
                    currentFontRealName = null;


                    if (fontChangedListener != null) {
                        fontChangedListener.onFontChanged(currentFontRealName, currentFontFileName);
                        Log.d(TAG, "★ Updated title to 'Unknown Font' after general error");
                    }

                    hideAxisUi();

                    currentTypeface = null;
                    Typeface defaultTypeface = Typeface.DEFAULT;
                    if (previewSentence != null) {
                        previewSentence.setTypeface(defaultTypeface);
                    }

                    Toast.makeText(requireContext(),
                        getString(R.string.font_viewer_error_loading_font) +
                        " (" + getString(R.string.unknown_font) + ")",
                        Toast.LENGTH_SHORT).show();

                    Log.e(TAG, "Error creating typeface from path: " + path, e);
                });
            }
        });
    }


    private void setupAxisSpinners(Map<String, List<VariableFontHelper.VariableInstance>> axisInstancesMap) {
        if (!isAdded() || variableAxesContainer == null || weightLabelText == null || allAxisUis == null) return;

        weightLabelText.setVisibility(View.GONE);
        variableAxesContainer.setVisibility(View.VISIBLE);
        if (axesBottomDivider != null) axesBottomDivider.setVisibility(View.VISIBLE);

        for (AxisSpinnerUi ui : allAxisUis) {
            List<VariableFontHelper.VariableInstance> instances = axisInstancesMap.get(ui.tag);
            setupSingleAxisSpinner(ui, instances);
        }

        updateAxisDividers();
    }

    private void setupSingleAxisSpinner(AxisSpinnerUi ui, List<VariableFontHelper.VariableInstance> instances) {
        if (ui == null || ui.container == null || ui.spinner == null) return;

        if (instances == null || instances.isEmpty()) {
            ui.container.setVisibility(View.GONE);
            ui.instances = new ArrayList<>();
            return;
        }

        ui.instances = instances;
        ui.container.setVisibility(View.VISIBLE);

        List<String> instanceNames = new ArrayList<>();
        for (VariableFontHelper.VariableInstance inst : instances) {
            instanceNames.add(inst.name);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            requireContext(),
            android.R.layout.simple_spinner_item,
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

        final List<VariableFontHelper.VariableInstance> finalInstances = instances;
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

    private void showWeightLabel(String label) {
        if (weightLabelText == null || variableAxesContainer == null) return;

        variableAxesContainer.setVisibility(View.GONE);
        if (axesBottomDivider != null) axesBottomDivider.setVisibility(View.GONE);

        if (label != null && !label.isEmpty()) {
            weightLabelText.setText(label);
            weightLabelText.setVisibility(View.VISIBLE);
        } else {
            weightLabelText.setVisibility(View.GONE);
        }
    }

    private void hideAxisUi() {
        if (weightLabelText != null) weightLabelText.setVisibility(View.GONE);
        if (variableAxesContainer != null) variableAxesContainer.setVisibility(View.GONE);
        if (axesBottomDivider != null) axesBottomDivider.setVisibility(View.GONE);
    }

    public void loadFontFromUri(Uri uri, String fileName) {
        originalFontPath = storageManager.getRealPathFromUri(uri);
        if (originalFontPath == null || originalFontPath.isEmpty()) {
            originalFontPath = android.net.Uri.decode(uri.toString());
        }
        isSystemFont     = false;

        bgExecutor.execute(() -> {
            File copiedFont = storageManager.copyFontForViewing(uri, fileName);

            if (copiedFont != null && copiedFont.exists()) {
                String realName = null;

                try {
                    realName = FontMetadataExtractor.extractFontName(copiedFont, 0);
                    currentWeightWidthLabel = FontWeightWidthExtractor.extract(copiedFont, 0);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to extract font metadata from URI", e);
                }

                if (realName == null || realName.isEmpty() || "Unknown Font".equals(realName) || getString(R.string.unknown_font).equals(realName)) {
                    String finalFileName = fileName != null ? fileName : copiedFont.getName();
                    realName = null;

                    final String finalRealName = realName;
                    mainHandler.post(() -> {
                        loadFontFromPath(copiedFont.getAbsolutePath(), finalFileName, finalRealName, 0, false);

                        Toast.makeText(requireContext(),
                            "Warning: Font name could not be extracted",
                            Toast.LENGTH_SHORT).show();
                    });
                } else {
                    final String finalFileName = fileName != null ? fileName : copiedFont.getName();
                    final String finalRealName = realName;


                    mainHandler.post(() -> {
                        loadFontFromPath(copiedFont.getAbsolutePath(), finalFileName, finalRealName, 0, false);
                    });
                }
            } else {
                mainHandler.post(() -> {
                    Toast.makeText(requireContext(),
                            getString(R.string.font_viewer_error_loading_font),
                            Toast.LENGTH_SHORT).show();
                });
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
        final Typeface originalTypeface = currentTypeface;

        FontSizeDialog fontSizeDialog = new FontSizeDialog(
            requireContext(),
            currentFontSize,
            MIN_FONT_SIZE,
            MAX_FONT_SIZE
        );

        fontSizeDialog.setOnFontSizeChangedListener(this::onFontSizeChanged);

        fontSizeDialog.setOnDialogCancelledListener(() -> {
            currentFontSize   = originalSize;
            currentTypeface   = originalTypeface;
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
        String lastPath     = preferenceManager.getLastViewedFontPath();
        String lastFileName = preferenceManager.getLastViewedFontFileName();
        String lastRealName = preferenceManager.getLastViewedFontRealName();

        if (lastPath != null && !lastPath.isEmpty()) {
            File localFile = new File(lastPath);
            if (localFile.exists()) {
                currentFontPath     = lastPath;
                currentFontFileName = lastFileName;
                currentFontRealName = lastRealName;
                currentTtcIndex     = 0;
                isSystemFont        = false;
                currentWeightWidthLabel = FontWeightWidthExtractor.extract(localFile, 0);

                if (currentFontRealName != null && (currentFontRealName.isEmpty() || currentFontRealName.equals(getString(R.string.unknown_font)))) {
                    currentFontRealName = null;
                }

                if (originalFontPath == null || originalFontPath.isEmpty()) {
                    String lastOriginalPath = preferenceManager.getLastViewedFontOriginalPath();
                    if (lastOriginalPath != null && !lastOriginalPath.isEmpty()) {
                        originalFontPath = lastOriginalPath;
            } else {
                        originalFontPath = extractRealPathFromUri(lastPath);
            }
        }

                notifyFontChangedImmediate();
                // اعادة فتح آخر خط تم عرضه: نستخدم آخر قيمة محفوظة لكل محور من محاوره
                loadFontFromPathWithAxes(lastPath, null, false);
            } else {
                preferenceManager.clearLastViewedFont();
            }
        }
    }

    public Map<String, String> getFontMetaData() {
        if (currentFontPath == null) {
            return new HashMap<>();
        }

        File fontFile = new File(currentFontPath);

        Map<String, String> metadata = FontMetadataExtractor.extractMetadataWithTtcIndex(fontFile, currentTtcIndex);

        if (metadata == null) {
            metadata = new HashMap<>();
        }

        String displayPath = (originalFontPath != null && !originalFontPath.isEmpty())
            ? originalFontPath
            : currentFontPath;

        metadata.put("Path", displayPath);
        metadata.put("FileName", currentFontFileName != null ? currentFontFileName : "");

        if (!metadata.containsKey("FullName")) {
            if (currentFontRealName == null || currentFontRealName.isEmpty() || currentFontRealName.equals(getString(R.string.unknown_font))) {
                metadata.put("FullName", getString(R.string.unknown_font));
                metadata.put("Warning", "Font metadata could not be extracted - file may be corrupted");
            } else {
                metadata.put("FullName", currentFontRealName);
            }
        }

        if (currentTtcIndex > 0) {
            metadata.put("TTC Index", String.valueOf(currentTtcIndex));
        }

        return metadata;
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
