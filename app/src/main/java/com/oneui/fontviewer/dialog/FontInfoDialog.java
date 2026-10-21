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
            value = localizeMetadataValue(key, value);
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

    // تحويل القيم المحدودة (الوزن، العرض، تحسين الوضوح، اللغات المدعومة، المحاور) لسلاسل نصية حسب لغة التطبيق
    // بدون الاعتماد على خدمة الترجمة، وبقية الحقول تُعرض كما هي
    private String localizeMetadataValue(String key, String value) {
        switch (key) {
            case "Weight":
                return localizeWeight(value);
            case "Width":
                return localizeWidth(value);
            case "Hinted":
                return localizeHinted(value);
            case "SupportedScripts":
                return localizeScripts(value);
            case "VariableAxes":
                return localizeAxes(value);
            default:
                return value;
        }
    }

    private String localizeWeight(String value) {
        int weightClass;
        try {
            weightClass = Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return value;
        }

        switch (weightClass) {
            case 100: return context.getString(R.string.font_info_weight_thin);
            case 200: return context.getString(R.string.font_info_weight_extra_light);
            case 300: return context.getString(R.string.font_info_weight_light);
            case 400: return context.getString(R.string.font_info_weight_regular);
            case 500: return context.getString(R.string.font_info_weight_medium);
            case 600: return context.getString(R.string.font_info_weight_semi_bold);
            case 700: return context.getString(R.string.font_info_weight_bold);
            case 800: return context.getString(R.string.font_info_weight_extra_bold);
            case 900: return context.getString(R.string.font_info_weight_black);
            default: return context.getString(R.string.font_info_weight_other, weightClass);
        }
    }

    private String localizeWidth(String value) {
        int widthClass;
        try {
            widthClass = Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return value;
        }

        switch (widthClass) {
            case 1: return context.getString(R.string.font_info_width_ultra_condensed);
            case 2: return context.getString(R.string.font_info_width_extra_condensed);
            case 3: return context.getString(R.string.font_info_width_condensed);
            case 4: return context.getString(R.string.font_info_width_semi_condensed);
            case 5: return context.getString(R.string.font_info_width_normal);
            case 6: return context.getString(R.string.font_info_width_semi_expanded);
            case 7: return context.getString(R.string.font_info_width_expanded);
            case 8: return context.getString(R.string.font_info_width_extra_expanded);
            case 9: return context.getString(R.string.font_info_width_ultra_expanded);
            default: return context.getString(R.string.font_info_width_other, widthClass);
        }
    }

    private String localizeHinted(String value) {
        if ("Improved".equalsIgnoreCase(value)) {
            return context.getString(R.string.font_info_hinted_improved);
        } else if ("Not improved".equalsIgnoreCase(value)) {
            return context.getString(R.string.font_info_hinted_not_improved);
        }
        return value;
    }

    private String localizeScripts(String value) {
        String separator = context.getString(R.string.font_info_list_separator);
        StringBuilder result = new StringBuilder();

        for (String script : value.split(",")) {
            script = script.trim();
            if (script.isEmpty()) {
                continue;
            }

            int resId = getScriptNameResId(script);
            if (result.length() > 0) {
                result.append(separator);
            }
            result.append(resId != 0 ? context.getString(resId) : script);
        }
        return result.toString();
    }

    // كل سطر بصيغة "tag<TAB>اسم المحور في الخط": نستخدم السلسلة الثابتة إن وُجدت،
    // وإلا اسم الخط نفسه (بدون ترجمة)، وإلا الرمز
    private String localizeAxes(String value) {
        String separator = context.getString(R.string.font_info_list_separator);
        StringBuilder result = new StringBuilder();

        for (String line : value.split("\n")) {
            String[] parts = line.split("\t", -1);
            String tag = parts[0].trim();
            if (tag.isEmpty()) {
                continue;
            }

            String fontName = parts.length > 1 ? parts[1].trim() : "";
            int resId = getAxisNameResId(tag);

            String name;
            if (resId != 0) {
                name = context.getString(resId);
            } else if (!fontName.isEmpty()) {
                name = fontName;
            } else {
                name = tag;
            }

            if (result.length() > 0) {
                result.append(separator);
            }
            result.append(name);
        }
        return result.toString();
    }

    private static int getScriptNameResId(String script) {
        switch (script) {
            case "Latin": return R.string.font_info_script_latin;
            case "Arabic": return R.string.font_info_script_arabic;
            case "Cyrillic": return R.string.font_info_script_cyrillic;
            case "Greek": return R.string.font_info_script_greek;
            case "Hebrew": return R.string.font_info_script_hebrew;
            case "CJK": return R.string.font_info_script_cjk;
            case "Devanagari": return R.string.font_info_script_devanagari;
            case "Thai": return R.string.font_info_script_thai;
            case "Hangul": return R.string.font_info_script_hangul;
            case "Hiragana": return R.string.font_info_script_hiragana;
            case "Katakana": return R.string.font_info_script_katakana;
            default: return 0;
        }
    }

    private static int getAxisNameResId(String tag) {
        switch (tag) {
            case "wght": return R.string.font_info_axis_weight;
            case "wdth": return R.string.font_info_axis_width;
            case "ital": return R.string.font_info_axis_italic;
            case "slnt": return R.string.font_info_axis_slant;
            case "opsz": return R.string.font_info_axis_optical_size;
            case "GRAD": return R.string.font_info_axis_grade;
            case "XTRA": return R.string.font_info_axis_counter_width;
            case "XOPQ": return R.string.font_info_axis_thick_stroke;
            case "YOPQ": return R.string.font_info_axis_thin_stroke;
            case "YTLC": return R.string.font_info_axis_lowercase_height;
            case "YTUC": return R.string.font_info_axis_uppercase_height;
            case "YTAS": return R.string.font_info_axis_ascender_height;
            case "YTDE": return R.string.font_info_axis_descender_depth;
            case "YTFI": return R.string.font_info_axis_figure_height;
            case "CASL": return R.string.font_info_axis_casual;
            case "CRSV": return R.string.font_info_axis_cursive;
            case "MONO": return R.string.font_info_axis_monospace;
            case "ROND": return R.string.font_info_axis_roundness;
            case "SOFT": return R.string.font_info_axis_softness;
            case "WONK": return R.string.font_info_axis_wonky;
            case "FILL": return R.string.font_info_axis_fill;
            default: return 0;
        }
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
            case "FileName":
            case "Path":
                return true;
            default:
                return false;
        }
    }
}
