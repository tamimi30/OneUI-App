package com.oneui.fontviewer.ui.dialog;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import dev.oneuiproject.oneui.dialog.ProgressDialog;

import com.oneui.fontviewer.R;

public final class ActionDialogs {

    private ActionDialogs() {}


    public interface OnConfirmListener {
        void onConfirmed();
    }

    public interface OnProgressCancelListener {
        void onCancel();
    }

    public interface OnProgressHideListener {
        void onHide();
    }

    

    public static void showDeletePermanentlyDialog(
            @NonNull Context context,
            int count,
            @NonNull OnConfirmListener listener) {

        if (count <= 0) return;

        String message = context.getResources()
                .getQuantityString(R.plurals.dialog_delete_permanently_question, count, count);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setMessage(message)
                .setPositiveButton(R.string.action_delete,
                        (d, w) -> listener.onConfirmed())
                .setNegativeButton(android.R.string.cancel, null)
                .show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(ContextCompat.getColor(context,
                        R.color.oui_functional_red_color));
    }

    

    @NonNull
    public static ProgressDialog createDeleteProgressDialog(
            @NonNull Context context,
            int total,
            @NonNull OnProgressCancelListener cancelListener,
            @NonNull OnProgressHideListener hideListener) {

        String title = context.getResources()
                .getQuantityString(R.plurals.progress_deleting, total);

        return buildProgressDialog(context, title, total, cancelListener, hideListener);
    }

    @NonNull
    public static ProgressDialog createRestoreProgressDialog(
            @NonNull Context context,
            int total,
            @NonNull OnProgressCancelListener cancelListener,
            @NonNull OnProgressHideListener hideListener) {

        String title = context.getResources()
                .getQuantityString(R.plurals.progress_restoring, total);

        return buildProgressDialog(context, title, total, cancelListener, hideListener);
    }

    @NonNull
    private static ProgressDialog buildProgressDialog(
            @NonNull Context context,
            @NonNull String title,
            int total,
            @NonNull OnProgressCancelListener cancelListener,
            @NonNull OnProgressHideListener hideListener) {

        ProgressDialog dialog = new ProgressDialog(context);
        dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        dialog.setTitle(title);

        dialog.setMax(total);

        dialog.setCancelable(false);

        dialog.setButton(
                ProgressDialog.BUTTON_NEGATIVE,
                context.getString(android.R.string.cancel),
                (d, which) -> {
                    cancelListener.onCancel();
                    d.dismiss();
                });

        dialog.setButton(
                ProgressDialog.BUTTON_POSITIVE,
                context.getString(R.string.action_hide_dialog),
                (d, which) -> {
                    hideListener.onHide();
                    d.dismiss();
                });

        return dialog;
    }
}
