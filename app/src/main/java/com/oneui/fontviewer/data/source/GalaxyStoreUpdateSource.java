package com.oneui.fontviewer.data.source;

import android.content.Context;

import androidx.annotation.NonNull;

import com.oneui.fontviewer.BuildConfig;
import com.oneui.fontviewer.data.model.UpdateInfo;
import com.oneui.fontviewer.data.network.GalaxyStoreUpdateApi;
import com.oneui.fontviewer.utils.AppLinks;

import java.io.IOException;
import java.util.Optional;

/** مصدر التحديث للنسخة المثبتة من Galaxy Store: يسأل خادم المتجر مثل تطبيقات سامسونج. */
public class GalaxyStoreUpdateSource implements UpdateSource {

    private final Context appContext;
    private final GalaxyStoreUpdateApi api = new GalaxyStoreUpdateApi();

    public GalaxyStoreUpdateSource(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    @NonNull
    @Override
    public Optional<UpdateInfo> checkForUpdate() throws IOException {
        GalaxyStoreUpdateApi.Result result = api.check(appContext);

        switch (result.resultCode) {
            case GalaxyStoreUpdateApi.RESULT_CAN_UPDATE:
                // إن لم يرجع الخادم رقم الإصدار نعتبره أعلى بواحد من المثبّت، ليُمسح المؤشر بعد التحديث
                long versionCode = result.versionCode > 0
                        ? result.versionCode
                        : BuildConfig.VERSION_CODE + 1L;
                String versionName = result.versionName != null ? result.versionName : "";
                return Optional.of(new UpdateInfo(versionName,
                        AppLinks.galaxyStoreProductUrl(appContext.getPackageName()), versionCode));

            case GalaxyStoreUpdateApi.RESULT_NO_UPDATE:
            case GalaxyStoreUpdateApi.RESULT_NO_UPDATE_ALT:
                return Optional.empty();

            default:
                // مثلاً: التطبيق غير موجود في Galaxy Store بعد
                throw new IOException("Unexpected Galaxy Store result code: " + result.resultCode);
        }
    }
}
