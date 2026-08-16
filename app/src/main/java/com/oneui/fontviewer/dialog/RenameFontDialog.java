package com.oneui.fontviewer.dialog;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;

import java.io.File;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import com.oneui.fontviewer.R;
import com.oneui.fontviewer.utils.FileUtils;

public class RenameFontDialog {

    public interface OnRenameListener {
        void onRename(String oldPath, String newFileName);
    }

    public static void show(Context context, String currentPath, OnRenameListener listener) {
        if (context == null || currentPath == null || listener == null) return;

        File file = new File(currentPath);
        String currentFileName = file.getName();
        String nameWithoutExt  = FileUtils.removeExtension(currentFileName);
        String extension       = FileUtils.getExtension(currentFileName);

        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_rename_font, null);
        final TextInputLayout inputLayout = dialogView.findViewById(R.id.rename_input_layout);
        final TextInputEditText input = dialogView.findViewById(R.id.rename_edit_text);

        input.setText(nameWithoutExt);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.rename_dialog_title);
        builder.setView(dialogView);
        builder.setPositiveButton(R.string.action_rename, null);
        builder.setNegativeButton(android.R.string.cancel, null);

        final AlertDialog dialog = builder.create();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);

        dialog.setOnShowListener(d -> {
            input.requestFocus();
            input.selectAll();

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_FORCED);
            }, 100);

            Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positiveButton.setEnabled(false);

            input.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    String newText = s.toString().trim();

                    boolean isEmpty    = newText.isEmpty();
                    boolean isSameName = newText.equals(nameWithoutExt);
                    boolean isInvalid  = !isEmpty && !isValidFileName(newText);
                    boolean fileExists = false;

                    if (!isEmpty && !isSameName && !isInvalid) {
                        String fullNewName = extension.isEmpty() ? newText : newText + "." + extension;
                        fileExists = new File(file.getParent(), fullNewName).exists();
                    }

                    if (fileExists) {
                        inputLayout.setError(context.getString(R.string.error_file_exists));
                        positiveButton.setEnabled(false);
                    } else {
                        inputLayout.setErrorEnabled(false);

                        boolean isValid = !isEmpty && !isSameName && !isInvalid;
                        positiveButton.setEnabled(isValid);
                    }
                }
            });

            positiveButton.setOnClickListener(v -> {
                String newName = input.getText() != null ? input.getText().toString().trim() : "";

                if (newName.isEmpty() || !isValidFileName(newName)) {
                    inputLayout.setError(context.getString(R.string.error_invalid_name));
                    return;
                }

                if (newName.equals(nameWithoutExt)) {
                    dialog.dismiss();
                    return;
                }

                listener.onRename(currentPath, extension.isEmpty() ? newName : newName + "." + extension);

                InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);

                dialog.dismiss();
            });
        });

        dialog.show();
    }

    private static boolean isValidFileName(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        String[] forbiddenChars = {"/", "\\", ":", "*", "?", "\"", "<", ">", "|"};
        for (String forbidden : forbiddenChars) {
            if (name.contains(forbidden)) return false;
        }
        return true;
    }
}
