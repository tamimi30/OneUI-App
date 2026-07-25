package com.oneui.fontviewer.fragment.fontviewer;

import android.app.Activity;
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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.appcompat.widget.AppCompatTextView;

import java.io.File;
import java.util.ArrayList;
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

    private static final float DEFAULT_FONT_SIZE   = 18f;
    private static final float MIN_FONT_SIZE       = 11f;
    private static final float MAX_FONT_SIZE       = 99f;
    private static final float DEFAULT_FONT_WEIGHT = 400f;

    private static float sSessionFontSize = -1f;

    private AppCompatTextView previewSentence;
    private AppCompatTextView weightLabelText;
    private AppCompatSpinner weightSpinner;

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

    private List<VariableFontHelper.VariableInstance> currentVariableInstances;

    private OnFontChangedListener fontChangedListener;

    private final ExecutorService bgExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private FontViewerStorageManager storageManager;
    private FontViewerPreferenceManager preferenceManager;
    private SettingsViewModel settingsViewModel;
    private BoldItalicFormatting formattingHelper = new BoldItalicFormatting();


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
                loadFontFromPathWithWeight(currentFontPath, currentFontFileName, currentFontRealName, currentFontWeight);
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
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroyView() {
    	formattingHelper.unbind();
        super.onDestroyView();
        previewSentence = null;
        weightLabelText = null;
        weightSpinner   = null;
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

    private void onFontWeightChanged(VariableFontHelper.VariableInstance instance) {
        if (instance == null || currentFontPath == null) {
            return;
        }

        currentFontWeight = instance.value;
        preferenceManager.saveFontWeight(instance.value);

        File fontFile = new File(currentFontPath);
        if (!fontFile.exists()) {
            return;
        }

        Typeface newTypeface = null;

        if (isSystemFont) {
            SystemFontCache cache = SystemFontCache.getInstance();
            newTypeface = cache.getTypefaceWithWeight(currentFontPath, instance.value, currentTtcIndex);
        } else {
            newTypeface = VariableFontHelper.createTypefaceWithWeight(fontFile, instance.value, currentTtcIndex);
        }

        if (newTypeface != null) {
            currentTypeface = newTypeface;
            applyFontToPreviewTexts();
        } else {
            Toast.makeText(requireContext(),
                "Failed to apply weight: " + instance.name,
                Toast.LENGTH_SHORT).show();
        }
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
        weightSpinner   = view.findViewById(R.id.weight_spinner);
    }


    public void loadFontFromPath(String path, String fileName, String realName) {
        currentWeightWidthLabel = null;
        loadFontFromPath(path, fileName, realName, 0, false);
    }

    public void loadFontFromPath(String path, String fileName, String realName, int ttcIndex) {
        currentWeightWidthLabel = null;
        loadFontFromPath(path, fileName, realName, ttcIndex, false);
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

        notifyFontChangedImmediate();

        if (formattingHelper != null) {
            formattingHelper.reset();
        }

        loadFontFromPathWithWeight(path, fileName, realName, DEFAULT_FONT_WEIGHT);
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

    private void loadFontFromPathWithWeight(String path, String fileName, String realName, float weight) {
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

                List<VariableFontHelper.VariableInstance> variableInstances = null;
                if (isVar) {
                    variableInstances = VariableFontHelper.extractVariableInstances(fontFile, currentTtcIndex);
                }

                Typeface typeface = null;

                try {
                    if (isSystemFont) {
                        SystemFontCache cache = SystemFontCache.getInstance();
                        typeface = cache.getTypefaceWithWeight(path, finalWeight, currentTtcIndex);
                    } else {
                        typeface = VariableFontHelper.createTypefaceWithWeight(fontFile, finalWeight, currentTtcIndex);
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
                    final Typeface finalTypeface             = typeface;
                    final float finalWeightForHandler        = finalWeight;
                    final boolean finalIsVariable            = isVar;
                    final List<VariableFontHelper.VariableInstance> finalInstances = variableInstances;

                    mainHandler.post(() -> {
                        currentTypeface   = finalTypeface;
                        currentFontWeight = finalWeightForHandler;
                        isVariableFont    = finalIsVariable;

                        if (finalIsVariable && finalInstances != null && !finalInstances.isEmpty()) {
                            setupWeightSpinner(finalInstances);
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

                    hideWeightUI();

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


    private void setupWeightSpinner(List<VariableFontHelper.VariableInstance> instances) {
        if (weightSpinner == null || weightLabelText == null || !isAdded()) return;

        currentVariableInstances = instances;
        weightLabelText.setVisibility(View.GONE);
        weightSpinner.setVisibility(View.VISIBLE);

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

        weightSpinner.setAdapter(adapter);

        int selectedIndex = 0;
        for (int i = 0; i < instances.size(); i++) {
            if (Math.abs(instances.get(i).value - currentFontWeight) < 1f) {
                selectedIndex = i;
                break;
            }
        }
        weightSpinner.setSelection(selectedIndex);

        final List<VariableFontHelper.VariableInstance> finalInstances = instances;
        weightSpinner.post(() -> {
            if (weightSpinner == null || !isAdded()) return;
            weightSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (position >= 0 && position < finalInstances.size()) {
                        onFontWeightChanged(finalInstances.get(position));
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
        });
    }

    private void showWeightLabel(String label) {
        if (weightLabelText == null || weightSpinner == null) return;

        weightSpinner.setVisibility(View.GONE);

        if (label != null && !label.isEmpty()) {
            weightLabelText.setText(label);
            weightLabelText.setVisibility(View.VISIBLE);
        } else {
            weightLabelText.setVisibility(View.GONE);
        }
    }

    private void hideWeightUI() {
        if (weightLabelText != null) weightLabelText.setVisibility(View.GONE);
        if (weightSpinner != null)   weightSpinner.setVisibility(View.GONE);
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
        currentVariableInstances = null;

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
                    originalFontPath = extractRealPathFromUri(lastPath);
                }

                notifyFontChangedImmediate();
                loadFontFromPathWithWeight(lastPath, lastFileName, lastRealName, lastWeight);
            } else {
                preferenceManager.clearLastViewedFont();
            }
        }
    }

    public Map<String, String> getFontMetaData() {
        if (currentFontPath == null) {
            return new java.util.HashMap<>();
        }

        File fontFile = new File(currentFontPath);

        Map<String, String> metadata = FontMetadataExtractor.extractMetadataWithTtcIndex(fontFile, currentTtcIndex);

        if (metadata == null) {
            metadata = new java.util.HashMap<>();
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

    public String getCurrentFontRealName() {
        return currentFontRealName;
    }

    public String getCurrentFontFileName() {
        return currentFontFileName;
    }

    public boolean hasFontSelected() {
        return currentFontPath != null && !currentFontPath.isEmpty();
    }
            }
