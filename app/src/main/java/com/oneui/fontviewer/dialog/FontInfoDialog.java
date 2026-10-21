package com.oneui.fontviewer.dialog;

import android.content.Context;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
                "VariableAxes",
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

        int[] displayNameResIds = {
                R.string.font_info_label_full_name,
                R.string.font_info_label_post_script_name,
                R.string.font_info_label_family,
                R.string.font_info_label_sub_family,
                R.string.font_info_label_weight,
                R.string.font_info_label_width,
                R.string.font_info_label_hinted,
                R.string.font_info_label_supported_scripts,
                R.string.font_info_label_glyph_count,
                R.string.font_info_label_units_per_em,
                R.string.font_info_label_variable_axes,
                R.string.font_info_label_version,
                R.string.font_info_label_modified_date,
                R.string.font_info_label_created_date,
                R.string.font_info_label_designer,
                R.string.font_info_label_designer_url,
                R.string.font_info_label_manufacturer,
                R.string.font_info_label_copyright,
                R.string.font_info_label_vendor_url,
                R.string.font_info_label_vendor_id,
                R.string.font_info_label_trademark,
                R.string.font_info_label_description,
                R.string.font_info_label_license_description,
                R.string.font_info_label_license_url,
                R.string.font_info_label_file_name,
                R.string.font_info_label_path
        };

        String dialogTitle = metadata.containsKey("FullName") && metadata.get("FullName") != null
                && !metadata.get("FullName").isEmpty() ?
                metadata.get("FullName") :
                (metadata.containsKey("Family") && metadata.get("Family") != null
                        && !metadata.get("Family").isEmpty() ?
                        metadata.get("Family") : context.getString(R.string.font_viewer_select_font));

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(dialogTitle)
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

        LinearLayout itemsContainer = new LinearLayout(dialogContext);
        itemsContainer.setOrientation(LinearLayout.VERTICAL);
        itemsContainer.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        boolean hasContent = false;

        for (int i = 0; i < orderedKeys.length; i++) {
            String key = orderedKeys[i];
            String displayName = context.getString(displayNameResIds[i]);
            String value = metadata.get(key);

            if ("FileName".equals(key) && originalFileName != null) {
                value = originalFileName;
            } else if ("Path".equals(key) && originalPath != null) {
                value = originalPath;
            }

            if (value == null || value.isEmpty()) {
                continue;
            }

            // ننظّف القيمة: نشيل فواصل الأسطر، ونستبدل الرموز النادرة
            // (شرطة طويلة، علامات تنصيص مُقوّسة...) برموز عادية يدعمها أي خط
            value = sanitizeMetadataValue(value);

            if ("Version".equals(key)) {
                value = cleanVersionString(value);
            }

            View itemView = inflater.inflate(R.layout.dialog_font_info_item, itemsContainer, false);
            TextView labelView = itemView.findViewById(R.id.font_info_label);
            TextView valueView = itemView.findViewById(R.id.font_info_value);

            labelView.setText(displayName);

            if (isLinklessField(key)) {
                valueView.setAutoLinkMask(0);
            }
            valueView.setText(value);
            valueView.setMovementMethod(LinkMovementMethod.getInstance());

            itemsContainer.addView(itemView);
            hasContent = true;
        }

        if (!hasContent) {
            TextView emptyView = new TextView(dialogContext);
            emptyView.setText("No metadata available.");
            int pad = (int) (16 * dialogContext.getResources().getDisplayMetrics().density);
            emptyView.setPadding(pad, pad, pad, pad);
            itemsContainer.addView(emptyView);
        }

        scrollHost.addView(itemsContainer);
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

    private static String sanitizeMetadataValue(String value) {
        if (value == null) {
            return value;
        }

        return value
                .replace("\r\n", " ")
                .replace("\r", " ")
                .replace("\n", " ")
                .replace("\u2014", "-")
                .replace("\u2013", "-")
                .replace("\u2018", "'")
                .replace("\u2019", "'")
                .replace("\u201C", "\"")
                .replace("\u201D", "\"")
                .replace("\u2026", "...")
                .replace("\u2022", "-")
                .replace("\u00A0", " ")
                .replace("\u200B", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static boolean isLinklessField(String key) {
        switch (key) {
            case "FullName":
            case "PostScriptName":
            case "Family":
            case "SubFamily":
            case "Weight":
            case "Width":
            case "Hinted":
            case "SupportedScripts":
            case "GlyphCount":
            case "UnitsPerEm":
            case "VariableAxes":
            case "Version":
            case "ModifiedDate":
            case "CreatedDate":
            case "VendorID":
            case "FileName":
            case "Path":
                return true;
            default:
                return false;
        }
    }
}
