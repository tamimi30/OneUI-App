package com.oneui.fontviewer.dialog;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;
import android.content.res.Configuration;
import android.util.TypedValue;

import java.io.File;

import androidx.appcompat.widget.AppCompatEditText;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import com.oneui.fontviewer.R;
import com.oneui.fontviewer.utils.FileUtils;

public class FontActionDialogs {

    public interface OnRenameListener {
        void onRename(String oldPath, String newFileName);
    }

    public interface OnDeleteListener {
        void onDeleteConfirmed();
    }

    public static void showRenameDialog(Context context, String currentPath, OnRenameListener listener) {
        if (context == null || currentPath == null || listener == null) return;

        File file = new File(currentPath);
        String currentFileName = file.getName();
        String nameWithoutExt  = FileUtils.removeExtension(currentFileName);
        String extension       = FileUtils.getExtension(currentFileName);

        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_rename_font, null);
        final AppCompatEditText input     = dialogView.findViewById(R.id.rename_edit_text);
        final TextView          errorText = dialogView.findViewById(R.id.rename_error_text);

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

            int redColor   = ContextCompat.getColor(context, androidx.appcompat.R.color.sesl_functional_red_dark);
            ColorStateList redTint = ColorStateList.valueOf(redColor);

            boolean isDarkMode = (context.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            int defaultTintColor = ContextCompat.getColor(context, isDarkMode
                    ? androidx.appcompat.R.color.sesl_edit_text_tint_color_dark
                    : androidx.appcompat.R.color.sesl_edit_text_tint_color_light);
            ColorStateList defaultTint = ColorStateList.valueOf(defaultTintColor);

            input.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    String newText = s.toString().trim();

                    boolean isEmpty       = newText.isEmpty();
                    boolean isSameName    = newText.equals(nameWithoutExt);
                    boolean isInvalid     = !isEmpty && !isValidFileName(newText);
                    boolean fileExists    = false;

                    if (!isEmpty && !isSameName && !isInvalid) {
                        String fullNewName = extension.isEmpty() ? newText : newText + "." + extension;
                        fileExists = new File(file.getParent(), fullNewName).exists();
                    }

                    if (fileExists) {
                        ViewCompat.setBackgroundTintList(input, redTint);
                        showError(errorText, context.getString(R.string.error_file_exists));
                        positiveButton.setEnabled(false);
                    } else {
                        ViewCompat.setBackgroundTintList(input, defaultTint);
                        errorText.setVisibility(View.GONE);

                        boolean isValid = !isEmpty && !isSameName && !isInvalid;
                        positiveButton.setEnabled(isValid);
                    }
                }
            });

            positiveButton.setOnClickListener(v -> {
                String newName = input.getText() != null ? input.getText().toString().trim() : "";

                if (newName.isEmpty() || !isValidFileName(newName)) {
                    showError(errorText, context.getString(R.string.error_invalid_name));
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

    private static void showError(TextView errorText, String message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
    }

    public static void showDeleteDialog(Context context, int count, int totalCount, OnDeleteListener listener) {
        if (context == null || listener == null || count <= 0) return;

        String message;
        if (count == 1) {
            message = context.getString(R.string.delete_single_confirmation);
        } else if (count == totalCount && count > 1) {
            message = context.getString(R.string.delete_all_confirmation);
        } else {
            message = context.getString(R.string.delete_multiple_confirmation, count);
        }

        new AlertDialog.Builder(context)
                .setMessage(message)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> listener.onDeleteConfirmed())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
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
