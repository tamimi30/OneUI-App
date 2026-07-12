package com.example.oneuiapp.dialog;

import android.content.Context;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;

import com.example.oneuiapp.R;

public class FontErrorDialog {

    public static void show(Context context) {
        // 1. إنشاء الحاوية برمجياً لترتيب العناصر في المنتصف (كبديل لملف XML)
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(50, 80, 50, 50);

        // 2. إنشاء أيقونة الخطأ
        ImageView errorIcon = new ImageView(context);
        try {
            // جلب الأيقونة المطلوبة من مكتبة OneUI
            errorIcon.setImageResource(dev.oneuiproject.oneui.R.drawable.ic_oui_error);
        } catch (Exception e) {
            // تجاهل الخطأ بأمان
        }
        
        // تحديد حجم الأيقونة (70) ومسافة أسفلها
        float density = context.getResources().getDisplayMetrics().density;
        int iconSize = (int) (70 * density);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconParams.bottomMargin = (int) (20 * density);
        errorIcon.setLayoutParams(iconParams);

        // 3. إنشاء رسالة الخطأ
        TextView errorMessage = new TextView(context);
        
        // جلب النص من ملف strings
        errorMessage.setText(R.string.font_metadata_error);
        
        errorMessage.setGravity(Gravity.CENTER);
        errorMessage.setTextSize(14f);
        errorMessage.setTextColor(context.getColor(R.color.sesl_description_text_color));

        // 4. تجميع الأيقونة والنص داخل الحاوية
        layout.addView(errorIcon);
        layout.addView(errorMessage);

        // 5. إظهار الديالوج النهائي
        new AlertDialog.Builder(context)
                .setView(layout)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
