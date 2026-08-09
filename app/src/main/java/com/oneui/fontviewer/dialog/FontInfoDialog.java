package com.oneui.fontviewer.dialog;

import android.content.Context;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.widget.NestedScrollView;

import java.util.Locale;
import java.util.Map;

import com.oneui.fontviewer.R;

public class FontInfoDialog {

    private final Context context;
    private final Map<String, String> metadata;
    private final String originalFileName;
    private final String originalPath;

    public FontInfoDialog(Context context, Map<String, String> metadata, String originalFileName, String originalPath) {
        this.context = context;
        this.metadata = metadata;
        this.originalFileName = originalFileName;
        this.originalPath = originalPath;
    }

    public void show() {
        if (metadata == null || metadata.isEmpty()) {
            showNoMetadataDialog();
            return;
        }

        String[] orderedKeys = {
                "FullName",
                "PostScriptName",
                "Family",
                "SubFamily",
                "Weight",
                "Width",
                "Hinted",
                "SupportedScripts",
                "GlyphCount",
                "UnitsPerEm",
                "VariableInstances",
                "Version",
                "ModifiedDate",
                "CreatedDate",
                "Designer",
                "DesignerURL",
                "Manufacturer",
                "Copyright",
                "VendorURL",
                "VendorID",
                "Trademark",
                "Description",
                "LicenseDescription",
                "LicenseURL",
                "FileName",
                "Path"
        };

        boolean isArabic = Locale.getDefault().getLanguage().equals("ar");

        String[] displayNames = isArabic ? new String[]{
                "الاسم الكامل",
                "اسم PostScript",
                "العائلة",
                "العائلة الفرعية",
                "الوزن",
                "العرض",
                "تحسين وضوح النص",
                "اللغات المدعومة",
                "عدد الرموز (Glyphs)",
                "دقة الخط (Resolution)",
                "المتغيرات (Instances)",
                "الإصدار",
                "تاريخ التعديل",
                "تاريخ الإنشاء",
                "المصمم",
                "رابط المصمم",
                "الشركة المصنعة",
                "حقوق النشر",
                "رابط البائع",
                "معرف البائع",
                "العلامة التجارية",
                "الوصف",
                "وصف الترخيص",
                "رابط الترخيص",
                "اسم الملف",
                "المسار"
        } : new String[]{
                "Full name",
                "Post script name",
                "Family",
                "Sub family",
                "Weight",
                "Width",
                "Improved text clarity",
                "Supported languages",
                "Glyph count",
                "Font resolution",
                "Variable instances",
                "Version",
                "Modified date",
                "Created date",
                "Designer",
                "Designer URL",
                "Manufacturer",
                "Copyright",
                "Vendor URL",
                "Vendor ID",
                "Trademark",
                "Description",
                "License description",
                "License URL",
                "File name",
                "Path"
        };

        LayoutInflater inflater = LayoutInflater.from(context);

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        boolean hasContent = false;

        for (int i = 0; i < orderedKeys.length; i++) {
            String key = orderedKeys[i];
            String displayName = displayNames[i];
            String value = metadata.get(key);

            if ("FileName".equals(key) && originalFileName != null) {
                value = originalFileName;
            } else if ("Path".equals(key) && originalPath != null) {
                value = originalPath;
            }

            if (value == null || value.isEmpty()) {
                continue;
            }

            // توحيد أي أسطر فارغة أو مسافات متعددة داخل القيمة إلى مسافة واحدة
            value = value.replaceAll("\\s+", " ").trim();

            if (value.isEmpty()) {
                continue;
            }

            if ("Version".equals(key)) {
                value = cleanVersionString(value);
            }

            if ("Hinted".equals(key)) {
                if ("Improved".equalsIgnoreCase(value)) {
                    value = isArabic ? "مُحسّن" : "Improved";
                } else {
                    value = isArabic ? "غير مُحسّن" : "Not Improved";
                }
            }

            View itemView = inflater.inflate(R.layout.font_info_dialog_item, container, false);
            TextView labelView = itemView.findViewById(R.id.font_info_label);
            TextView valueView = itemView.findViewById(R.id.font_info_value);
            labelView.setText(displayName);
            valueView.setText(value);

            container.addView(itemView);
            hasContent = true;
        }

        if (!hasContent) {
            showNoMetadataDialog();
            return;
        }

        int sidePadding = dpToPx(24);

        NestedScrollView scrollView = new NestedScrollView(context);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        scrollView.setFillViewport(true);
        scrollView.setPaddingRelative(sidePadding, 0, sidePadding, 0);
        scrollView.setVerticalScrollBarEnabled(true);
        scrollView.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
        scrollView.addView(container);

        // الخطان الفاصلان العلوي والسفلي، مبنيان يدوياً بنفس أسلوب AppCompat
        View topDivider = createDivider(sidePadding);
        View bottomDivider = createDivider(sidePadding);
        topDivider.setVisibility(View.GONE);
        bottomDivider.setVisibility(View.GONE);

        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        wrapper.setPadding(0, dpToPx(8), 0, dpToPx(8));
        wrapper.addView(topDivider);
        wrapper.addView(scrollView);
        wrapper.addView(bottomDivider);

        Runnable updateDividers = () -> {
            topDivider.setVisibility(scrollView.canScrollVertically(-1) ? View.VISIBLE : View.GONE);
            bottomDivider.setVisibility(scrollView.canScrollVertically(1) ? View.VISIBLE : View.GONE);
        };
        scrollView.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener)
                (v, scrollX, scrollY, oldScrollX, oldScrollY) -> updateDividers.run());
        scrollView.post(updateDividers);

        String dialogTitle = metadata.containsKey("FullName") && metadata.get("FullName") != null
                && !metadata.get("FullName").isEmpty() ?
                metadata.get("FullName") :
                (metadata.containsKey("Family") && metadata.get("Family") != null
                && !metadata.get("Family").isEmpty() ?
                metadata.get("Family") : context.getString(R.string.font_viewer_select_font));

        new AlertDialog.Builder(context)
                .setTitle(dialogTitle)
                .setView(wrapper)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private View createDivider(int sideMargin) {
        View divider = new View(context);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(1));
        params.setMarginStart(sideMargin);
        params.setMarginEnd(sideMargin);
        divider.setLayoutParams(params);
        divider.setBackgroundColor(resolveThemeColor(androidx.appcompat.R.attr.colorControlHighlight));
        return divider;
    }

    private int resolveThemeColor(int attrRes) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(attrRes, typedValue, true);
        return typedValue.data;
    }

    private void showNoMetadataDialog() {
        new AlertDialog.Builder(context)
                .setTitle("Font Information")
                .setMessage("No metadata available.")
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private String cleanVersionString(String version) {
        if (version == null || version.isEmpty()) {
            return version;
        }

        String lowerVersion = version.toLowerCase().trim();
        if (lowerVersion.startsWith("version ")) {
            return version.substring(8).trim();
        }

        return version;
    }

    private int dpToPx(int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
