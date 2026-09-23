package com.oneui.fontviewer.activity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;

import com.oneui.fontviewer.fragment.fontviewer.FontViewerActivity;

public class FontOpenTriggerActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent sourceIntent = getIntent();

        if (isFontViewIntent(sourceIntent)) {
            Uri fontUri = sourceIntent.getData();
            String fileName = getFileName(this, fontUri);

            Intent viewerIntent = new Intent(this, FontViewerActivity.class);
            viewerIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            viewerIntent.putExtra(FontViewerActivity.EXTRA_FONT_PATH, fontUri.toString());
            viewerIntent.putExtra(FontViewerActivity.EXTRA_FONT_FILE_NAME, fileName);
            startActivity(viewerIntent);
        }

        finish();
    }

    private static boolean isFontViewIntent(Intent intent) {
        return intent != null && Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null;
    }

    private static String getFileName(Context context, Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index != -1) {
                        result = cursor.getString(index);
                    }
                }
            } catch (Exception ignored) {}
        }
        if (result == null) {
            result = uri.getPath();
            if (result != null) {
                int cut = result.lastIndexOf('/');
                if (cut != -1) {
                    result = result.substring(cut + 1);
                }
            }
        }
        return result != null ? result : "Unknown Font";
    }
}
