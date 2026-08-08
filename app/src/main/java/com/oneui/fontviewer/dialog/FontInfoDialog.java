package com.oneui.fontviewer.dialog;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

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

        boolean isArabic = java.util.Locale.getDefault().getLanguage().equals("ar");
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

        View contentView = inflater.inflate(R.layout.font_info_dialog_content, null, false);
        LinearLayout container = contentView.findViewById(R.id.font_info_items_container);

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

            if (value != null && !value.isEmpty()) {
                if ("Version".equals(key)) {
                    value = cleanVersionString(value);
                }

                if ("Hinted".equals(key)) {
                    boolean isAr = java.util.Locale.getDefault().getLanguage().equals("ar");
                    if ("Improved".equalsIgnoreCase(value)) {
                        value = isAr ? "مُحسّن" : "Improved";
                    } else {
                        value = isAr ? "غير مُحسّن" : "Not Improved";
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
        }

        if (!hasContent) {
            TextView emptyView = new TextView(context);
            emptyView.setText("No metadata available.");
            container.addView(emptyView);
        }

        String dialogTitle = metadata.containsKey("FullName") && metadata.get("FullName") != null
                && !metadata.get("FullName").isEmpty() ?
                metadata.get("FullName") :
                (metadata.containsKey("Family") && metadata.get("Family") != null
                && !metadata.get("Family").isEmpty() ?
                metadata.get("Family") : context.getString(R.string.font_viewer_select_font));

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(dialogTitle)
                .setView(contentView)
                .setPositiveButton(android.R.string.ok, null)
                .create();

        dialog.show();
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
}
