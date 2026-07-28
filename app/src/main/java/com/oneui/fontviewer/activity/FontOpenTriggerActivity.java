package com.oneui.fontviewer.activity;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.oneui.fontviewer.fragment.fontviewer.FontViewerActivity;
import com.oneui.fontviewer.utils.ExternalFontIntentHandler;

public class FontOpenTriggerActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();

        if (intent != null && ExternalFontIntentHandler.isFontViewIntent(intent)) {
            Uri fontUri = intent.getData();

            if (fontUri != null) {
                String fileName = ExternalFontIntentHandler.getFileName(this, fontUri);

                Intent viewerIntent = new Intent(this, FontViewerActivity.class);
                viewerIntent.putExtra(FontViewerActivity.EXTRA_FONT_PATH, fontUri.toString());
                viewerIntent.putExtra(FontViewerActivity.EXTRA_FONT_FILE_NAME, fileName);
                viewerIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(viewerIntent);
            }
        }

        finish();
        overridePendingTransition(0, 0);
    }
}
