package com.oneui.fontviewer.dialog;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SeslSeekBar;

import com.oneui.fontviewer.R;

import dev.oneuiproject.oneui.widget.TipPopup;

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
    private TipPopup fontSizeTipPopup;

    public FontSizeDialog(Context context, float currentSize, float minSize, float maxSize) {
        this.context     = context;
        this.currentSize = currentSize;
        this.minSize     = minSize;
        this.maxSize     = maxSize;
        this.tempSize    = currentSize;
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
            fontSizeTipPopup = new TipPopup(helpIcon, TipPopup.MODE_TRANSLUCENT);
            fontSizeTipPopup.setMessage(context.getString(R.string.font_size_dialog_tip));
            fontSizeTipPopup.setExpanded(true);
            boolean isRtl = context.getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            fontSizeTipPopup.show(isRtl ? TipPopup.DIRECTION_BOTTOM_RIGHT : TipPopup.DIRECTION_BOTTOM_LEFT);
        });

        fontSizeValue.setLongClickable(false);

        fontSizeValue.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                fontSizeValue.selectAll();
                fontSizeValue.requestFocus();
                InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.viewClicked(fontSizeValue);
                    imm.showSoftInput(fontSizeValue, 0);
                }
                return true;
            }
            return false;
        });

        fontSizeValue.setCustomSelectionActionModeCallback(new android.view.ActionMode.Callback() {
            @Override public boolean onCreateActionMode(android.view.ActionMode mode, android.view.Menu menu) { return true; }
            @Override public boolean onPrepareActionMode(android.view.ActionMode mode, android.view.Menu menu) { menu.clear(); return true; }
            @Override public boolean onActionItemClicked(android.view.ActionMode mode, android.view.MenuItem item) { return false; }
            @Override public void onDestroyActionMode(android.view.ActionMode mode) {}
        });

        fontSizeValue.setCustomInsertionActionModeCallback(new android.view.ActionMode.Callback() {
            @Override public boolean onCreateActionMode(android.view.ActionMode mode, android.view.Menu menu) { return false; }
            @Override public boolean onPrepareActionMode(android.view.ActionMode mode, android.view.Menu menu) { return false; }
            @Override public boolean onActionItemClicked(android.view.ActionMode mode, android.view.MenuItem item) { return false; }
            @Override public void onDestroyActionMode(android.view.ActionMode mode) {}
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
                        seekBar.setProgress(newProgress);
                        updateFontSizeText(enteredSize);
                        tempSize = enteredSize;
                        
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
                return true;
            }
            return false;
        });
        seekBar       = dialogView.findViewById(R.id.font_size_seekbar);

        setupSeekBar();

        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
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
            
            if (sizeListener != null) {
                sizeListener.onFontSizeChanged(tempSize);
            }
            
            fontSizeValue.clearFocus();
            InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(fontSizeValue.getWindowToken(), 0);
            }
        });

        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> {
            if (cancelListener != null) {
                cancelListener.onDialogCancelled();
            }
            dialog.dismiss();
        });

        dialog = builder.create();
        dialog.setOnDismissListener(d -> {
            if (fontSizeTipPopup != null) {
                fontSizeTipPopup.dismiss(false);
            }
        });
        
        dialog.setOnCancelListener(d -> {
            String inputText = fontSizeValue.getText().toString();
            if (!inputText.isEmpty()) {
                try {
                    float enteredSize = Float.parseFloat(inputText);
                    if (enteredSize > maxSize || enteredSize < minSize) {
                        Toast.makeText(context, R.string.toast_invalid_font_size, Toast.LENGTH_SHORT).show();
                    }
                } catch (NumberFormatException ignored) {}
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

                if (sizeListener != null && fromUser) {
                    sizeListener.onFontSizeChanged(tempSize);
                }
            }

            @Override
            public void onStartTrackingTouch(SeslSeekBar seekBar) {
                fontSizeValue.clearFocus();
                InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(fontSizeValue.getWindowToken(), 0);
                }
            }

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
