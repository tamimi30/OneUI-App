package com.example.oneuiapp.dialog;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.EditText;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SeslSeekBar;

import com.example.oneuiapp.R;

/**
 * FontSizeDialog - ديالوج ضبط حجم الخط
 *
 * ★ التعديل: حُذف كل ما يتعلق بمنتقي الوزن (Spinner) من هذا الديالوج بالكامل.
 *   انتقلت هذه الوظيفة إلى FontViewerFragment لتكون في متناول المستخدم
 *   مباشرةً على شاشة العارض دون الحاجة لفتح أي ديالوج.
 *   يختص هذا الكلاس الآن بضبط حجم الخط (Font Size) فقط.
 */
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
    private float lastAppliedSize;

    public FontSizeDialog(Context context, float currentSize, float minSize, float maxSize) {
        this.context     = context;
        this.currentSize = currentSize;
        this.minSize     = minSize;
        this.maxSize     = maxSize;
        this.tempSize    = currentSize;
        this.lastAppliedSize = currentSize;
    }

    public void setOnFontSizeChangedListener(OnFontSizeChangedListener listener) {
        this.sizeListener = listener;
    }

    public void setOnDialogCancelledListener(OnDialogCancelledListener listener) {
        this.cancelListener = listener;
    }

    public void show() {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.font_size_dialog_title);

        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_font_size, null);
        builder.setView(dialogView);
        fontSizeValue = dialogView.findViewById(R.id.font_size_value);

                fontSizeValue.setLongClickable(false);

        // 1. الحفاظ على التظليل عند النقر (المفرد أو المزدوج) ويسمح بالكتابة
        fontSizeValue.setOnClickListener(v -> fontSizeValue.selectAll());

        // 2. منع قوائم النسخ واللصق
        fontSizeValue.setCustomSelectionActionModeCallback(new android.view.ActionMode.Callback() {
            @Override public boolean onCreateActionMode(android.view.ActionMode mode, android.view.Menu menu) { return false; }
            @Override public boolean onPrepareActionMode(android.view.ActionMode mode, android.view.Menu menu) { return false; }
            @Override public boolean onActionItemClicked(android.view.ActionMode mode, android.view.MenuItem item) { return false; }
            @Override public void onDestroyActionMode(android.view.ActionMode mode) {}
        });

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            fontSizeValue.setCustomInsertionActionModeCallback(new android.view.ActionMode.Callback() {
                @Override public boolean onCreateActionMode(android.view.ActionMode mode, android.view.Menu menu) { return false; }
                @Override public boolean onPrepareActionMode(android.view.ActionMode mode, android.view.Menu menu) { return false; }
                @Override public boolean onActionItemClicked(android.view.ActionMode mode, android.view.MenuItem item) { return false; }
                @Override public void onDestroyActionMode(android.view.ActionMode mode) {}
            });
        }

        // 3. معالجة التركيز والحقل الفارغ
        fontSizeValue.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                fontSizeValue.post(() -> fontSizeValue.selectAll());
            } else {
                String text = fontSizeValue.getText().toString();
                if (text.isEmpty()) {
                    // إذا كان فارغاً، استرجع آخر حجم مطبق فعلياً
                    tempSize = lastAppliedSize;
                    updateFontSizeText(tempSize);
                    seekBar.setProgress((int) (tempSize - minSize));
                } else {
                    try {
                        float enteredSize = Float.parseFloat(text);
                        if (enteredSize > maxSize) enteredSize = maxSize;
                        if (enteredSize < minSize) enteredSize = minSize;
                        tempSize = enteredSize;
                        lastAppliedSize = tempSize; // حفظ الرقم المطبق الجديد
                        seekBar.setProgress((int) (tempSize - minSize));
                        updateFontSizeText(tempSize);
                        if (sizeListener != null) {
                            sizeListener.onFontSizeChanged(tempSize);
                        }
                    } catch (NumberFormatException e) {
                        updateFontSizeText(tempSize);
                    }
                }
            }
        });

        // 4. مراقبة حركة الكيبورد لإزالة التظليل عند إغلاقه بزر الرجوع
        dialogView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            android.graphics.Rect r = new android.graphics.Rect();
            dialogView.getWindowVisibleDisplayFrame(r);
            int screenHeight = dialogView.getRootView().getHeight();
            int keypadHeight = screenHeight - r.bottom;

            if (keypadHeight < screenHeight * 0.15) {
                if (fontSizeValue.hasFocus()) {
                    fontSizeValue.clearFocus();
                }
            }
        });

        // 5. معالجة زر "تم" من الكيبورد
        fontSizeValue.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
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

        builder.setPositiveButton("OK", (dialog, which) -> {
            // سحب الرقم المكتوب في الحقل قبل الإغلاق وتطبيقه
            String inputText = fontSizeValue.getText().toString();
            if (!inputText.isEmpty()) {
                try {
                    float enteredSize = Float.parseFloat(inputText);
                    if (enteredSize > maxSize) enteredSize = maxSize;
                    if (enteredSize < minSize) enteredSize = minSize;
                    tempSize = enteredSize;
                } catch (NumberFormatException ignored) {}
            }
            
            if (sizeListener != null) {
                sizeListener.onFontSizeChanged(tempSize);
            }
            
            // إغلاق الكيبورد لمنعه من البقاء عالقاً في الشاشة
            fontSizeValue.clearFocus();
            InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(fontSizeValue.getWindowToken(), 0);
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> {
            if (cancelListener != null) {
                cancelListener.onDialogCancelled();
            }
            dialog.dismiss();
        });

        dialog = builder.create();
        dialog.show();
    }

    private void setupSeekBar() {
        if (seekBar == null) {
            return;
        }

        int range = (int) (maxSize - minSize);
        seekBar.setMax(range);
        seekBar.setMode(SeslSeekBar.MODE_EXPAND);

        int currentProgress = (int) (currentSize - minSize);
        seekBar.setProgress(currentProgress);
        updateFontSizeText(currentSize);

        seekBar.setOnSeekBarChangeListener(new SeslSeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeslSeekBar seekBar, int progress, boolean fromUser) {
                tempSize = minSize + progress;
                if (fromUser) {
                    lastAppliedSize = tempSize; // حفظ الحجم عند تغييره بالشريط
                }
                updateFontSizeText(tempSize);

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
            // تحديث النص فقط إذا كان مختلفاً لمنع إعادة تعيين المؤشر (Cursor)
            if (!fontSizeValue.getText().toString().equals(newText)) {
                fontSizeValue.setText(newText);
                // وضع المؤشر في نهاية الرقم تلقائياً لراحة المستخدم
                fontSizeValue.setSelection(newText.length());
            }
        }
    }

    public void dismiss() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }
                }
