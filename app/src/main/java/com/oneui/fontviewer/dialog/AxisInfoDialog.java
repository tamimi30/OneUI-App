package com.oneui.fontviewer.dialog;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;


import com.oneui.fontviewer.R;

public class AxisInfoDialog {

    public static void show(Context context, String axisTitle, @DrawableRes int imageRes, @StringRes int descriptionRes) {
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(axisTitle)
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

        View content = inflater.inflate(R.layout.dialog_axis_info, scrollHost, false);

        ImageView imageView = content.findViewById(R.id.axis_info_image);
        TextView descriptionView = content.findViewById(R.id.axis_info_description);

        if (imageView != null) {
            imageView.setImageResource(imageRes);
        }
        if (descriptionView != null) {
            descriptionView.setText(descriptionRes);
        }

        scrollHost.addView(content);
    }
}
