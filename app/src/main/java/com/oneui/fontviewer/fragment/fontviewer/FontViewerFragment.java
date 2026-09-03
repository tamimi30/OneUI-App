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
    private static final String KEY_FONT_WEIGHT        = "font_weight";
    private static final String KEY_IS_VARIABLE_FONT   = "is_variable_font";
    private static final String KEY_TTC_INDEX          = "ttc_index";
    private static final String KEY_IS_SYSTEM_FONT     = "is_system_font";
    private static final String KEY_WEIGHT_WIDTH_LABEL = "weight_width_label";
    private static final String TAG = "FontViewerFragment";

    private static final float DEFAULT_FONT_SIZE   = 34f;
    private static final float MIN_FONT_SIZE       = 12f;
    private static final float MAX_FONT_SIZE       = 520f;
    private static final float DEFAULT_FONT_WEIGHT = 400f;

    private static float sSessionFontSize = -1f;

    private TextView previewSentence;
    private TextView weightLabelText;
    private AppCompatSpinner axisValueSpinner; // (سابقاً: weightSpinner) يعرض القيم الجاهزة للمحور المختار حالياً
    private AppCompatSpinner axisTypeSpinner;  // منتقي نوع المحور: الوزن / العرض / الدرجة / الاستدارة / المائل / الحجم البصري / أحادي المسافة

    private String currentFontPath;
    private String currentFontFileName;
    private String currentFontRealName;
    public String originalFontPath;
    private Typeface currentTypeface;
    private float currentFontSize   = DEFAULT_FONT_SIZE;
    private float currentFontWeight = DEFAULT_FONT_WEIGHT;
    private boolean isVariableFont  = false;
    private int currentTtcIndex     = 0;
    private boolean isSystemFont    = false;

    private String currentWeightWidthLabel;

    // ★ المحاور المتغيرة المدعومة والمتوفرة في الخط الحالي، ومحور القيمة المختار حالياً في منتقي نوع المحور ★
    private List<VariableFontHelper.AxisInfo> currentSupportedAxes = new ArrayList<>();
    private VariableFontHelper.AxisInfo currentSelectedAxis;
    // ★ قيم كل المحاور الأخرى (عدا الوزن) حسب وسمها الأصلي كما ورد من الخط، لتبقى محفوظة عند التنقل بين المحاور ★
    private final Map<String, Float> currentAxisValues = new HashMap<>();

    private OnFontChangedListener fontChangedListener;

    private final ExecutorService bgExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private FontViewerStorageManager storageManager;
    private FontViewerPreferenceManager preferenceManager;
    private SettingsViewModel settingsViewModel;
    private BoldItalicFormatting formattingHelper = new BoldItalicFormatting();
    private ValueAnimator weightAnimator; // متغير الأنيميشن


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

        currentFontSize   = (sSessionFontSize > 0f) ? sSessionFontSize : DEFAULT_FONT_SIZE;
        currentFontWeight = preferenceManager.getFontWeight(DEFAULT_FONT_WEIGHT);
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
            currentFontWeight      = savedInstanceState.getFloat(KEY_FONT_WEIGHT, DEFAULT_FONT_WEIGHT);
            isVariableFont         = savedInstanceState.getBoolean(KEY_IS_VARIABLE_FONT, false);
            currentTtcIndex        = savedInstanceState.getInt(KEY_TTC_INDEX, 0);
            isSystemFont           = savedInstanceState.getBoolean(KEY_IS_SYSTEM_FONT, false);
            currentWeightWidthLabel = savedInstanceState.getString(KEY_WEIGHT_WIDTH_LABEL);

            if (currentFontPath != null && !currentFontPath.isEmpty()) {
                notifyFontChangedImmediate();
                loadFontFromPathWithWeight(currentFontPath, currentFontWeight);
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
        // إيقاف الأنيميشن إذا كان يعمل لمنع تسريب الذاكرة (Memory Leak)
        if (weightAnimator != null && weightAnimator.isRunning()) {
            weightAnimator.cancel();
        }
    	formattingHelper.unbind();
        super.onDestroyView();
        previewSentence  = null;
        weightLabelText  = null;
        axisValueSpinner = null;
        axisTypeSpinner  = null;
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

    // ★ إرجاع القيمة الحالية لمحور معيّن (من متغير الوزن الأساسي أو من خريطة المحاور الأخرى) ★
    private float getAxisCurrentValue(VariableFontHelper.AxisInfo axis) {
        if (axis == null) return 0f;
        if ("WGHT".equals(axis.canonicalKey)) {
            return currentFontWeight;
        }
        Float stored = currentAxisValues.get(axis.tag);
        return stored != null ? stored : axis.defaultValue;
    }

    // ★ بناء خريطة (وسم المحور -> القيمة) بكل المحاور المدعومة حالياً، مع إمكانية تجاوز قيمة محور واحد أثناء الأنيميشن ★
    private Map<String, Float> buildAxisValuesMap(String overrideTag, float overrideValue) {
        Map<String, Float> map = new HashMap<>();
        for (VariableFontHelper.AxisInfo axis : currentSupportedAxes) {
            float value = axis.tag.equals(overrideTag) ? overrideValue : getAxisCurrentValue(axis);
            map.put(axis.tag, value);
        }
        return map;
    }

    private void onAxisValueChanged(VariableFontHelper.AxisInfo axis, VariableFontHelper.VariableInstance instance) {
        if (axis == null || instance == null || currentFontPath == null) {
            return;
        }

        File fontFile = new File(currentFontPath);
        if (!fontFile.exists()) {
            return;
        }

        float oldValue = getAxisCurrentValue(axis);
        final float newValue = instance.value;

        if (oldValue == newValue) {
            return;
        }

        final boolean isWeightAxis    = "WGHT".equals(axis.canonicalKey);
        final String axisTag          = axis.tag;
        final String axisCanonicalKey = axis.canonicalKey;

        if (isWeightAxis) {
            preferenceManager.saveFontWeight(newValue);
        } else {
            preferenceManager.saveAxisValue(axisCanonicalKey, newValue);
        }

        if (weightAnimator != null && weightAnimator.isRunning()) {
            weightAnimator.cancel();
            oldValue = getAxisCurrentValue(axis);
        }

        weightAnimator = ValueAnimator.ofFloat(oldValue, newValue);
        weightAnimator.setDuration(600);
        weightAnimator.setInterpolator(new AccelerateDecelerateInterpolator());

        weightAnimator.addUpdateListener(animation -> {
            float animatedValue = (float) animation.getAnimatedValue();

            if (isWeightAxis) {
                currentFontWeight = animatedValue;
            } else {
                currentAxisValues.put(axisTag, animatedValue);
            }

            if (previewSentence != null) {
                String liveSettings = VariableFontHelper.buildVariationSettingsString(
                        buildAxisValuesMap(axisTag, animatedValue));
                if (!liveSettings.isEmpty()) {
                    previewSentence.setFontVariationSettings(liveSettings);
                }
            }
        });

        weightAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (isWeightAxis) {
                    currentFontWeight = newValue;
                } else {
                    currentAxisValues.put(axisTag, newValue);
                }

                final Map<String, Float> finalAxisValues = buildAxisValuesMap(axisTag, newValue);

                // بناء Typeface النهائي في الخلفية لضمان استقرار خصائص الخط عند تغيير حجمه لاحقاً
                bgExecutor.execute(() -> {
                    Typeface finalTypeface;
                    if (isSystemFont) {
                        finalTypeface = SystemFontCache.getInstance().getTypefaceWithAxes(currentFontPath, finalAxisValues, currentTtcIndex);
                    } else {
                        finalTypeface = VariableFontHelper.createTypefaceWithAxes(fontFile, finalAxisValues, currentTtcIndex);
                    }
                    if (finalTypeface != null) {
                        mainHandler.post(() -> currentTypeface = finalTypeface);
                    }
                });
            }
        });

        weightAnimator.start();
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
        axisValueSpinner = view.findViewById(R.id.axis_value_spinner); // (سابقاً: weight_spinner)
        axisTypeSpinner  = view.findViewById(R.id.axis_type_spinner);
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

        loadFontFromPathWithWeight(path, DEFAULT_FONT_WEIGHT);
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

    private void loadFontFromPathWithWeight(String path, float weight) {
        bgExecutor.execute(() -> {
            try {
                File fontFile = new File(path);
                if (!fontFile.exists()) {
                    mainHandler.post(this::resetFontDisplay);
                    return;
                }

                boolean isVar = VariableFontHelper.isVariableFont(fontFile, currentTtcIndex);
                float finalWeight = weight;

                if (finalWeight == DEFAULT_FONT_WEIGHT && isVar) {
                    finalWeight = 400f;
                    preferenceManager.saveFontWeight(400f);
                }

                if (!isVar) {
                    finalWeight = 0f;
                }

                List<VariableFontHelper.AxisInfo> supportedAxes = null;
                Map<String, Float> loadedAxisValues = new HashMap<>();

                if (isVar) {
                    // ★ اكتشاف كل المحاور المدعومة (وزن، عرض، درجة، استدارة، مائل، حجم بصري، أحادي المسافة) ★
                    supportedAxes = VariableFontHelper.getSupportedAxes(fontFile, currentTtcIndex);

                    for (VariableFontHelper.AxisInfo axis : supportedAxes) {
                        if ("WGHT".equals(axis.canonicalKey)) {
                            loadedAxisValues.put(axis.tag, finalWeight);
                        } else {
                            // نحمّل القيمة المحفوظة لآخر خط تم فتحه إن وجدت، وإلا القيمة الافتراضية للمحور
                            Float savedValue = preferenceManager.getAxisValue(axis.canonicalKey);
                            float value = (savedValue != null) ? savedValue : axis.defaultValue;
                            value = Math.max(axis.min, Math.min(axis.max, value)); // نتأكد أن القيمة ضمن مدى هذا الخط تحديداً
                            loadedAxisValues.put(axis.tag, value);
                        }
                    }
                }

                Typeface typeface;

                try {
                    if (isSystemFont) {
                        SystemFontCache cache = SystemFontCache.getInstance();
                        typeface = isVar
                                ? cache.getTypefaceWithAxes(path, loadedAxisValues, currentTtcIndex)
                                : cache.getTypefaceWithWeight(path, finalWeight, currentTtcIndex);
                    } else {
                        typeface = isVar
                                ? VariableFontHelper.createTypefaceWithAxes(fontFile, loadedAxisValues, currentTtcIndex)
                                : VariableFontHelper.createTypefaceWithWeight(fontFile, finalWeight, currentTtcIndex);
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

                        hideWeightUI();

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
                    final Typeface finalTypeface                                  = typeface;
                    final float finalWeightForHandler                             = finalWeight;
                    final boolean finalIsVariable                                 = isVar;
                    final List<VariableFontHelper.AxisInfo> finalSupportedAxes    = supportedAxes;
                    final Map<String, Float> finalLoadedAxisValues                = loadedAxisValues;

                    mainHandler.post(() -> {
                        currentTypeface   = finalTypeface;
                        currentFontWeight = finalWeightForHandler;
                        isVariableFont    = finalIsVariable;

                        // ★ تحديث خريطة قيم المحاور الأخرى بما تم تحميله لهذا الخط تحديداً ★
                        currentAxisValues.clear();
                        if (finalSupportedAxes != null) {
                            for (VariableFontHelper.AxisInfo axis : finalSupportedAxes) {
                                if (!"WGHT".equals(axis.canonicalKey)) {
                         


    // ★ تهيئة منتقي نوع المحور ومنتقي القيمة معاً بناءً على المحاور المدعومة في الخط الحالي ★
    private void setupAxisUI(List<VariableFontHelper.AxisInfo> axes) {
        if (axisValueSpinner == null || weightLabelText == null || !isAdded()) return;

        currentSupportedAxes = axes;

        if (axes == null || axes.isEmpty()) {
            hideWeightUI();
            return;
        }

        weightLabelText.setVisibility(View.GONE);
        axisValueSpinner.setVisibility(View.VISIBLE);

        // إن كان هناك أكثر من محور مدعوم، نعرض منتقي نوع المحور بجانب منتقي القيمة
        if (axes.size() > 1 && axisTypeSpinner != null) {
            axisTypeSpinner.setVisibility(View.VISIBLE);

            List<String> axisNames = new ArrayList<>();
            for (VariableFontHelper.AxisInfo axis : axes) {
                axisNames.add(axis.displayName);
            }

            ArrayAdapter<String> axisAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                axisNames
            );
            axisAdapter.setDropDownViewResource(R.layout.support_simple_spinner_dropdown_item);
            axisTypeSpinner.setOnItemSelectedListener(null);
            axisTypeSpinner.setAdapter(axisAdapter);

            // اختيار محور الوزن افتراضياً إن كان مدعوماً، وإلا أول محور متوفر
            int defaultAxisIndex = 0;
            for (int i = 0; i < axes.size(); i++) {
                if ("WGHT".equals(axes.get(i).canonicalKey)) {
                    defaultAxisIndex = i;
                    break;
                }
            }
            axisTypeSpinner.setSelection(defaultAxisIndex);
            currentSelectedAxis = axes.get(defaultAxisIndex);

            axisTypeSpinner.post(() -> {
                if (axisTypeSpinner == null || !isAdded()) return;
                axisTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        if (position >= 0 && position < axes.size()) {
                            currentSelectedAxis = axes.get(position);
                            populateAxisValueSpinner(currentSelectedAxis);
                        }
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {}
                });
            });
        } else {
            // محور واحد فقط: لا حاجة لإظهار منتقي نوع المحور (نفس السلوك القديم)
            if (axisTypeSpinner != null) {
                axisTypeSpinner.setOnItemSelectedListener(null);
                axisTypeSpinner.setVisibility(View.GONE);
            }
            currentSelectedAxis = axes.get(0);
        }

        populateAxisValueSpinner(currentSelectedAxis);
    }

    // ★ تعبئة منتقي القيمة بالقيم الجاهزة الخاصة بالمحور المختار حالياً ★
    private void populateAxisValueSpinner(VariableFontHelper.AxisInfo axis) {
        if (axisValueSpinner == null || axis == null || !isAdded()) return;

        List<VariableFontHelper.VariableInstance> instances = VariableFontHelper.extractInstancesForAxis(axis);
        if (instances.isEmpty()) return;

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

        axisValueSpinner.setOnItemSelectedListener(null);
        axisValueSpinner.setAdapter(adapter);

        float currentValue = getAxisCurrentValue(axis);
        int selectedIndex = 0;
        for (int i = 0; i < instances.size(); i++) {
            if (Math.abs(instances.get(i).value - currentValue) < 1f) {
                selectedIndex = i;
                break;
            }
        }
        axisValueSpinner.setSelection(selectedIndex);

        final List<VariableFontHelper.VariableInstance> finalInstances = instances;
        final VariableFontHelper.AxisInfo finalAxis = axis;
        axisValueSpinner.post(() -> {
            if (axisValueSpinner == null || !isAdded()) return;
            axisValueSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (position >= 0 && position < finalInstances.size()) {
                        onAxisValueChanged(finalAxis, finalInstances.get(position));
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        });
    }

    private void showWeightLabel(String label) {
        if (weightLabelText == null || axisValueSpinner == null) return;

        axisValueSpinner.setVisibility(View.GONE);
        if (axisTypeSpinner != null) axisTypeSpinner.setVisibility(View.GONE);

        if (label != null && !label.isEmpty()) {
            weightLabelText.setText(label);
            weightLabelText.setVisibility(View.VISIBLE);
        } else {
            weightLabelText.setVisibility(View.GONE);
        }
    }

    private void hideWeightUI() {
        if (weightLabelText != null)  weightLabelText.setVisibility(View.GONE);
        if (axisValueSpinner != null) axisValueSpinner.setVisibility(View.GONE);
        if (axisTypeSpinner != null)  axisTypeSpinner.setVisibility(View.GONE);
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
        currentFontWeight       = DEFAULT_FONT_WEIGHT;
        currentTtcIndex         = 0;
        isSystemFont            = false;
        currentWeightWidthLabel  = null;

        // ★ تصفير حالة المحاور الأخرى عند مسح الخط الحالي ★
        currentAxisValues.clear();
        currentSupportedAxes = new ArrayList<>();
        currentSelectedAxis  = null;

        Typeface defaultTypeface = Typeface.DEFAULT;
        if (previewSentence != null) previewSentence.setTypeface(defaultTypeface);

        hideWeightUI();

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
        float lastWeight    = preferenceManager.getFontWeight(DEFAULT_FONT_WEIGHT);

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
                loadFontFromPathWithWeight(lastPath, lastWeight);
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
        outState.putFloat(KEY_FONT_WEIGHT, currentFontWeight);
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
