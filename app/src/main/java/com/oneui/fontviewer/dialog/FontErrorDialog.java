package com.oneui.fontviewer.dialog;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.oneui.fontviewer.R;

public class FontErrorDialog {

    public static void show(Context context) {
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.font_metadata_error_title)
                .setMessage(" ")
                .setPositiveButton(android.R.string.ok, null)
                .create();

        dialog.show();

        TextView messageView = dialog.findViewById(android.R.id.message);
        if (messageView == null || !(messageView.getParent() instanceof ViewGroup)) {
            return;
        }

        ViewGroup scrollHost = (ViewGroup) messageView.getParent();
        scrollHost.removeView(messageView);

        Context dialogContext = dialog.getContext();
        LayoutInflater inflater = LayoutInflater.from(dialogContext);

        View content = inflater.inflate(R.layout.dialog_font_error, scrollHost, false);

        scrollHost.addView(content);
    }
}
