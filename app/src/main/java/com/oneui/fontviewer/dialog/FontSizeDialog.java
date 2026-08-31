package com.oneui.fontviewer.dialog;

import android.content.Context;
import android.text.InputFilter;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SeslSeekBar;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import dev.oneuiproject.oneui.widget.TipPopup;

import com.oneui.fontviewer.R;

public class FontSizeDialog {

    public interface OnFontSizeChangedListener {
        void onFontSizeChanged(float newSize);
    }

    public interface OnDialogCancelledListener {
        void onDialogCancelled();
    }

    private final Context context;
    private final float currentSize;
    private final float minSize;
    private final float maxSize;
    private OnFontSizeChangedListener sizeListener;
    private OnDialogCancelledListener cancelListener;
    private EditText fontSizeValue;
    private SeslSeekBar seekBar;

    private AlertDialog dialog;
    private float tempSize;
    private float originalSize;
    private TipPopup fontSizeTipPopup;
    private boolean isNumericKeyboardVisible = false;
    private Toast invalidSizeRealtimeToast;

    public FontSizeDialog(Context context, float currentSize, float minSize, float maxSize) {
        this.context     = context;
        this.currentSize = currentSize;
        this.minSize     = minSize;
        this.maxSize     = maxSize;
        this.tempSize    = currentSize;
        this.originalSize = currentSize;
    }

    public void setOnFontSizeChangedListener(OnFontSizeChangedListener listener) {
        this.sizeListener = listener;
    }

    public void setOnDialogCancelledListener(OnDialogCancelledListener listener) {
        this.cancelListener = listener;
    }

    public void show() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        

        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_font_size, null);
        builder.setView(dialogView);
        fontSizeValue = dialogView.findViewById(R.id.font_size_value);
        View helpIcon = dialogView.findViewById(R.id.font_size_help_icon);
        helpIcon.setOnClickListener(v -> {
            if (fontSizeTipPopup != null && fontSizeTipPopup.isShowing()) {
                fontSizeTipPopup.dismiss(true);
                return;
            }
            fontSizeValue.clearFocus();
            InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(fontSizeValue.getWindowToken(), 0);
            }
            fontSizeTipPopup = new TipPopup(helpIcon, TipPopup.MODE_NORMAL);
            fontSizeTipPopup.setMessage(context.getString(R.string.font_size_dialog_tip));
            // set custom background color with alpha using project color
            fontSizeTipPopup.setBackgroundColorWithAlpha(ContextCompat.getColor(context, R.color.orange));
            fontSizeTipPopup.setExpanded(true);
            boolean isRtl = context.getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            fontSizeTipPopup.show(isRtl ? TipPopup.DIRECTION_BOTTOM_RIGHT : TipPopup.DIRECTION_BOTTOM_LEFT);
        });

        // يرصد ظهور/اختفاء الكيبورد مباشرة، بغض النظر عن السبب (رجوع، Done، إلخ)
        ViewCompat.setOnApplyWindowInsetsListener(dialogView, (v, insets) -> {
            boolean isVisibleNow = insets.isVisible(WindowInsetsCompat.Type.ime());
            if (isNumericKeyboardVisible && !isVisibleNow) {
                if (fontSizeValue != null) {
                    fontSizeValue.clearFocus();
                }
            }
            isNumericKeyboardVisible = isVisibleNow;
            return insets;
        });

        fontSizeValue.setLongClickable(false);

        // ننتظر رفع الإصبع (ACTION_UP) قبل التنفيذ، كسلوك النقر القياسي،
        // بدل التنفيذ الفوري عند أول لمسة (ACTION_DOWN).
        fontSizeValue.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    return true;
                case MotionEvent.ACTION_UP:
                    fontSizeValue.selectAll();
                    fontSizeValue.requestFocus();
                    InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.viewClicked(fontSizeValue);
                        imm.showSoftInput(fontSizeValue, 0);
                    }
                    if (seekBar != null) {
                        seekBar.setEnabled(false);
                        seekBar.setAlpha(0.4f);
                    }
                    return true;
                default:
                    return false;
            }
        });

        // Also track focus changes to re-enable/disable the seekBar when keyboard
        // is shown/hidden by other means.
        fontSizeValue.setOnFocusChangeListener((v, hasFocus) -> {
            if (seekBar == null) return;
            if (hasFocus) {
                seekBar.setEnabled(false);
                seekBar.setAlpha(0.4f);
            } else {
                seekBar.setEnabled(true);
                seekBar.setAlpha(1f);
            }
        });

        // نفس نمط سامسونج: رفض فوري للرقم الزائد أثناء الكتابة + Toast واحد يُعاد استخدامه.
        InputFilter maxValueRealtimeFilter = (source, start, end, dest, dstart, dend) -> {
            String result = dest.subSequence(0, dstart).toString()
                    + source.subSequence(start, end)
                    + dest.subSequence(dend, dest.length());

            if (result.isEmpty()) {
                return null;
            }

            try {
                int enteredValue = Integer.parseInt(result);
                if (enteredValue > (int) maxSize) {
                    if (invalidSizeRealtimeToast == null) {
                        invalidSizeRealtimeToast = Toast.makeText(context, R.string.toast_invalid_font_size, Toast.LENGTH_SHORT);
                    }
                    invalidSizeRealtimeToast.show();
                    return "";
                }
            } catch (NumberFormatException e) {
                return "";
            }

            return null;
        };
        InputFilter[] existingFilters = fontSizeValue.getFilters();
        InputFilter[] combinedFilters = new InputFilter[existingFilters.length + 1];
        System.arraycopy(existingFilters, 0, combinedFilters, 0, existingFilters.length);
        combinedFilters[existingFilters.length] = maxValueRealtimeFilter;
        fontSizeValue.setFilters(combinedFilters);

        fontSizeValue.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            @Override public boolean onCreateActionMode(ActionMode mode, Menu menu) { return true; }
            @Override public boolean onPrepareActionMode(ActionMode mode, Menu menu) { menu.clear(); return true; }
            @Override public boolean onActionItemClicked(ActionMode mode, MenuItem item) { return false; }
            @Override public void onDestroyActionMode(ActionMode mode) {}
        });

        fontSizeValue.setCustomInsertionActionModeCallback(new ActionMode.Callback() {
            @Override public boolean onCreateActionMode(ActionMode mode, Menu menu) { return false; }
            @Override public boolean onPrepareActionMode(ActionMode mode, Menu menu) { return false; }
            @Override public boolean onActionItemClicked(ActionMode mode, MenuItem item) { return false; }
            @Override public void onDestroyActionMode(ActionMode mode) {}
        });

        fontSizeValue.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                String inputText = fontSizeValue.getText().toString();
                if (!inputText.isEmpty()) {
                    try {
                        float enteredSize = Float.parseFloat(inputText);
                        if (enteredSize > maxSize || enteredSize < minSize) {
                            Toast.makeText(context, R.string.toast_invalid_font_size, Toast.LENGTH_SHORT).show();
                        }
                        if (enteredSize > maxSize) enteredSize = maxSize;
                        if (enteredSize < minSize) enteredSize = minSize;

                        int newProgress = (int) (enteredSize - minSize);
                        if (seekBar != null) seekBar.setProgress(newProgress);
                        updateFontSizeText(enteredSize);
                        tempSize = enteredSize;
                        
                        // For immediate preview we inform the listener, but we will
                        // restore original size if the dialog is cancelled.
                        if (sizeListener != null) {
                            sizeListener.onFontSizeChanged(enteredSize);
                        }
                    } catch (NumberFormatException e) {
                        updateFontSizeText(tempSize);
                    }
                }
                fontSizeValue.clearFocus();
                InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(fontSizeValue.getWindowToken(), 0);
                }
                // Re-enable seekBar after keyboard closed
                if (seekBar != null) {
                    seekBar.setEnabled(true);
                    seekBar.setAlpha(1f);
                }
                return true;
            }
            return false;
        });
        seekBar       = dialogView.findViewById(R.id.font_size_seekbar);

        setupSeekBar();

        builder.setPositiveButton(android.R.string.ok, (d, which) -> {
            String inputText = fontSizeValue.getText().toString();
            if (!inputText.isEmpty()) {
                try {
                    float enteredSize = Float.parseFloat(inputText);
                    if (enteredSize > maxSize || enteredSize < minSize) {
                        Toast.makeText(context, R.string.toast_invalid_font_size, Toast.LENGTH_SHORT).show();
                    }
                    if (enteredSize > maxSize) enteredSize = maxSize;
                    if (enteredSize < minSize) enteredSize = minSize;
                    tempSize = enteredSize;
                } catch (NumberFormatException ignored) {}
            }
            
            // Commit the final size
            if (sizeListener != null) {
                sizeListener.onFontSizeChanged(tempSize);
            }
            
            // Hide keyboard and re-enable seekBar
            fontSizeValue.clearFocus();
            InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(fontSizeValue.getWindowToken(), 0);
            }
            if (seekBar != null) {
                seekBar.setEnabled(true);
                seekBar.setAlpha(1f);
            }
        });

        builder.setNegativeButton(android.R.string.cancel, (d, which) -> {
            // Restore original size when cancelling
            if (sizeListener != null) {
                sizeListener.onFontSizeChanged(originalSize);
            }
            if (cancelListener != null) {
                cancelListener.onDialogCancelled();
            }
            // Dialog will be dismissed automatically
        });

        dialog = builder.create();
        // Allow tapping outside to cancel and restore size
        dialog.setCanceledOnTouchOutside(true);

        dialog.setOnDismissListener(d -> {
            if (fontSizeTipPopup != null) {
                fontSizeTipPopup.dismiss(false);
            }
            ViewCompat.setOnApplyWindowInsetsListener(dialogView, null);
        });
        
        dialog.setOnCancelListener(d -> {
            // Restore original size when cancelled (including tapping outside)
            if (sizeListener != null) {
                sizeListener.onFontSizeChanged(originalSize);
            }
            if (cancelListener != null) {
                cancelListener.onDialogCancelled();
            }

            // Hide keyboard and re-enable seekBar
            fontSizeValue.clearFocus();
            InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(fontSizeValue.getWindowToken(), 0);
            }
            if (seekBar != null) {
                seekBar.setEnabled(true);
                seekBar.setAlpha(1f);
            }
        });
        
        dialog.show();
        
    }

    private void setupSeekBar() {
        if (seekBar == null) {
            return;
        }

        int range = (int) (maxSize - minSize);
        seekBar.setMax(range);

        int currentProgress = (int) (currentSize - minSize);
        seekBar.setProgress(currentProgress);
        updateFontSizeText(currentSize);

        seekBar.setOnSeekBarChangeListener(new SeslSeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeslSeekBar seekBar, int progress, boolean fromUser) {
                tempSize = minSize + progress;
                updateFontSizeText(tempSize);

                // Live preview while dragging; we keep originalSize to allow restore on cancel
                if (sizeListener != null && fromUser) {
                    sizeListener.onFontSizeChanged(tempSize);
                }
            }

            @Override
            public void onStartTrackingTouch(SeslSeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeslSeekBar seekBar) {}
        });
    }

    private void updateFontSizeText(float size) {
        if (fontSizeValue != null) {
            String newText = String.format("%.0f", size);
            if (!fontSizeValue.getText().toString().equals(newText)) {
                fontSizeValue.setText(newText);
                fontSizeValue.setSelection(newText.length());
            }
        }
    }

    
                                                         }
                                                         
