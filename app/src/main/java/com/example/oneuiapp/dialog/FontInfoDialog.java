package com.example.oneuiapp.dialog;

import android.content.Context;
import android.os.Build;
import android.text.Html;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
//import androidx.appcompat.R.color;

import java.util.Map;

import com.example.oneuiapp.R;

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

    // للتوافق مع الكود القديم
    public FontInfoDialog(Context context, Map<String, String> metadata) {
        this(context, metadata, null, null);
    }

    public void show() {
        if (metadata == null || metadata.isEmpty()) {
            showNoMetadataDialog();
            return;
        }

        android.content.res.TypedArray ta = context.obtainStyledAttributes(new int[]{android.R.attr.textColorSecondary, android.R.attr.textColorPrimary});
        int secondaryColor = ta.getColor(0, 0);
        int primaryColor = ta.getColor(1, 0);
        ta.recycle();
        String secondaryColorHex = String.format("#%06X", (0xFFFFFF & secondaryColor));
        String primaryColorHex = String.format("#%06X", (0xFFFFFF & primaryColor));
        StringBuilder htmlBuilder = new StringBuilder();

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
                
                // تحديد النص الثابت لقيمة تحسين وضوح النص بناءً على لغة التطبيق الحالية
                if ("Hinted".equals(key)) {
                    boolean isAr = java.util.Locale.getDefault().getLanguage().equals("ar");
                    if ("Improved".equalsIgnoreCase(value)) {
                        value = isAr ? "مُحسّن" : "Improved";
                    } else {
                        value = isAr ? "غير مُحسّن" : "Not Improved";
                    }
                }

                if (hasContent) {
                    htmlBuilder.append("<br><br>");
                }
                htmlBuilder.append("<small><font color='").append(secondaryColorHex).append("'>")
                        .append(displayName)
                        .append("</font></small><br>");

                if (isUrl(value)) {
                    htmlBuilder.append("<a href='").append(value).append("'>")
                            .append(android.text.TextUtils.htmlEncode(value))
                            .append("</a>");
                } else {
                    String escapedValue = android.text.TextUtils.htmlEncode(value);
                    htmlBuilder.append("<font color='").append(primaryColorHex).append("'>")
                            .append(escapedValue)
                            .append("</font>");
                }

                hasContent = true;
            }
        }

        if (!hasContent) {
            htmlBuilder.append("No metadata available.");
        }
        Spanned formattedText = Html.fromHtml(htmlBuilder.toString(), Html.FROM_HTML_MODE_LEGACY);

        String dialogTitle = metadata.containsKey("FullName") && metadata.get("FullName") != null 
                && !metadata.get("FullName").isEmpty() ?
                metadata.get("FullName") : 
                (metadata.containsKey("Family") && metadata.get("Family") != null 
                && !metadata.get("Family").isEmpty() ?
                metadata.get("Family") : context.getString(R.string.font_viewer_select_font));

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(dialogTitle)
                .setMessage(formattedText)
                .setPositiveButton(android.R.string.ok, null)
                .create();

        dialog.show();

        TextView messageView = dialog.findViewById(android.R.id.message);
        if (messageView != null) {
            messageView.setTextSize(17);
            messageView.setMovementMethod(LinkMovementMethod.getInstance());
            Linkify.addLinks(messageView, Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES);
            messageView.setLinkTextColor(context.getResources().getColor(R.color.sesl_primary_color_light, context.getTheme()));
        }
    }

    private void showNoMetadataDialog() {
        new AlertDialog.Builder(context)
                .setTitle("Font Information")
                .setMessage("No metadata available.")
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private boolean isUrl(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        String lowerText = text.toLowerCase().trim();
        return lowerText.startsWith("http://") || lowerText.startsWith("https://") || lowerText.startsWith("www.");
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
