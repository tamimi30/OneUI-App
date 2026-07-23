package com.oneui.fontviewer.dialog;

import android.content.Context;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;

import com.oneui.fontviewer.R;

public class FontErrorDialog {

    public static void show(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(50, 80, 50, 50);

        ImageView errorIcon = new ImageView(context);
        try {
            errorIcon.setImageResource(dev.oneuiproject.oneui.R.drawable.ic_oui_error);
        } catch (Exception e) {
        }
        
        float density = context.getResources().getDisplayMetrics().density;
        int iconSize = (int) (70 * density);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconParams.bottomMargin = (int) (20 * density);
        errorIcon.setLayoutParams(iconParams);

        TextView errorMessage = new TextView(context);
        
        errorMessage.setText(R.string.font_metadata_error);
        
        errorMessage.setGravity(Gravity.CENTER);
        errorMessage.setTextSize(16f);
        errorMessage.setTextColor(context.getColor(R.color.sort_bar_text_color));

        layout.addView(errorIcon);
        layout.addView(errorMessage);

        new AlertDialog.Builder(context)
                .setView(layout)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
