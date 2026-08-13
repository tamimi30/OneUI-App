package com.oneui.fontviewer.dialog;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Typeface;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

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

        // العنوان أسفل الأيقونة
        TextView errorTitle = new TextView(context);
        errorTitle.setText(R.string.font_metadata_error_title);
        errorTitle.setGravity(Gravity.CENTER);
        errorTitle.setTextSize(17f);
        errorTitle.setTextColor(resolveThemeColor(context, android.R.attr.textColorPrimary));

        // إضافة الخط المخصص (sec-roboto-light) بنمط عادي (NORMAL)
        Typeface titleTypeface = Typeface.create("sec-roboto-light", Typeface.BOLD);
        errorTitle.setTypeface(titleTypeface);

        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = (int) (6 * density);
        errorTitle.setLayoutParams(titleParams);

        TextView errorMessage = new TextView(context);

        errorMessage.setText(R.string.font_metadata_error);

        errorMessage.setGravity(Gravity.CENTER);
        errorMessage.setTextSize(13f);
        errorMessage.setTextColor(resolveThemeColor(context, android.R.attr.textColorSecondary));
        errorMessage.setLineSpacing(
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 2f,
                        context.getResources().getDisplayMetrics()),
                1f);

        // إضافة الخط المخصص (sec-roboto-light) بنمط عادي (NORMAL)
        Typeface messageTypeface = Typeface.create("sec-roboto-light", Typeface.NORMAL);
        errorMessage.setTypeface(messageTypeface);

        layout.addView(errorIcon);
        layout.addView(errorTitle);
        layout.addView(errorMessage);

        new AlertDialog.Builder(context)
                .setView(layout)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    // يحل لون النص الأساسي/الثانوي من الثيم الحالي، فيتوافق تلقائيًا مع الوضع الفاتح/الداكن
    private static int resolveThemeColor(Context context, int attr) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(attr, typedValue, true);
        if (typedValue.resourceId != 0) {
            ColorStateList colorStateList = ContextCompat.getColorStateList(context, typedValue.resourceId);
            if (colorStateList != null) {
                return colorStateList.getDefaultColor();
            }
        }
        return typedValue.data;
    }
}
