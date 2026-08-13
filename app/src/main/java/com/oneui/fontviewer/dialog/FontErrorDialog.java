package com.oneui.fontviewer.dialog;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import androidx.appcompat.app.AlertDialog;

import com.oneui.fontviewer.R;

public class FontErrorDialog {

    public static void show(Context context) {
        View layout = LayoutInflater.from(context).inflate(R.layout.dialog_font_error, null);

        new AlertDialog.Builder(context)
                .setView(layout)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
