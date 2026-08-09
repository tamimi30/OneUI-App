package com.oneui.fontviewer.dialog;

import android.content.Context;
import android.content.res.TypedArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.widget.NestedScrollView;

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

        // ننشئ الـ Builder أولاً، ونأخذ منه "كونتكست" الديالوج الخاص بالثيم
        // (بدل كونتكست الشاشة العادي) لبناء كل عناصر الديالوج المخصصة عليه
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        Context dialogContext = builder.getContext();
        LayoutInflater inflater = LayoutInflater.from(dialogContext);

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

        // === الأرقام القابلة للتعديل: المسافة العمودية حول الخطوط، والمسافة الجانبية للنص ===
        int verticalGap = dpToPx(20);
        int sidePadding = dpToPx(24);
        int dividerColor = resolveColorControlHighlight(dialogContext);

        LinearLayout root = new LinearLayout(dialogContext);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        // مسافة فوق الخط العلوي (بينه وبين العنوان) ومسافة تحت الخط السفلي (بينه وبين زر حسناً)
        root.setPadding(0, verticalGap, 0, verticalGap);

        View topDivider = new View(dialogContext);
        topDivider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(1)));
        topDivider.setBackgroundColor(dividerColor);
        topDivider.setVisibility(View.GONE);
        root.addView(topDivider);

        NestedScrollView scrollView = new NestedScrollView(dialogContext);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        scrollView.setFillViewport(true);
        scrollView.setVerticalScrollBarEnabled(true);
        scrollView.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
        scrollView.setPaddingRelative(sidePadding, dpToPx(8), sidePadding, dpToPx(8));
        root.addView(scrollView);

        View bottomDivider = new View(dialogContext);
        bottomDivider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(1)));
        bottomDivider.setBackgroundColor(dividerColor);
        bottomDivider.setVisibility(View.GONE);
        root.addView(bottomDivider);

        LinearLayout container = new LinearLayout(dialogContext);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scrollView.addView(container);

        // إظهار/إخفاء الخطين الفاصلين حسب موضع التمرير الحالي
        ViewTreeObserver.OnScrollChangedListener dividerScrollListener = () -> {
            topDivider.setVisibility(scrollView.canScrollVertically(-1) ? View.VISIBLE : View.GONE);
            bottomDivider.setVisibility(scrollView.canScrollVertically(1) ? View.VISIBLE : View.GONE);
        };
        scrollView.getViewTreeObserver().addOnScrollChangedListener(dividerScrollListener);
        scrollView.post(dividerScrollListener::onScrollChanged);

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

            addItemView(inflater, container, displayName, value);
            hasContent = true;
        }

        if (!hasContent) {
            showNoMetadataDialog();
            return;
        }

        String dialogTitle = metadata.containsKey("FullName") && metadata.get("FullName") != null
                && !metadata.get("FullName").isEmpty() ?
                metadata.get("FullName") :
                (metadata.containsKey("Family") && metadata.get("Family") != null
                && !metadata.get("Family").isEmpty() ?
                metadata.get("Family") : dialogContext.getString(R.string.font_viewer_select_font));

        AlertDialog dialog = builder
                .setTitle(dialogTitle)
                .setView(root)
                .setPositiveButton(android.R.string.ok, null)
                .create();

        dialog.show();
    }

    private void addItemView(LayoutInflater inflater, LinearLayout container, String label, String value) {
        View itemView = inflater.inflate(R.layout.font_info_dialog_item, container, false);

        TextView labelView = itemView.findViewById(R.id.font_info_label);
        TextView valueView = itemView.findViewById(R.id.font_info_value);

        labelView.setText(label);
        valueView.setText(value);

        container.addView(itemView);
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

    private int resolveColorControlHighlight(Context ctx) {
        TypedArray ta = ctx.obtainStyledAttributes(new int[]{R.attr.colorControlHighlight});
        int color = ta.getColor(0, 0x1F000000);
        ta.recycle();
        return color;
    }

    private int dpToPx(int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
            }
