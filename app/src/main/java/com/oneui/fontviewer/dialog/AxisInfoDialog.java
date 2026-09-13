package com.oneui.fontviewer.dialog;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import com.oneui.fontviewer.R;

public class AxisInfoDialog {

    public static void show(Context context, String axisTitle, @DrawableRes int imageRes, @StringRes int descriptionRes) {
        View layout = LayoutInflater.from(context).inflate(R.layout.dialog_axis_info, null);

        ImageView imageView = layout.findViewById(R.id.axis_info_image);
        TextView descriptionView = layout.findViewById(R.id.axis_info_description);

        if (imageView != null) {
            imageView.setImageResource(imageRes);
        }
        if (descriptionView != null) {
            descriptionView.setText(descriptionRes);
        }

        new AlertDialog.Builder(context)
                .setTitle(axisTitle)
                .setMessage(layout)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
