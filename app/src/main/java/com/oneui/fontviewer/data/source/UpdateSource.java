package com.oneui.fontviewer.data.source;

import androidx.annotation.NonNull;

import com.oneui.fontviewer.data.model.UpdateInfo;

import java.util.Optional;

/**
 * مصدر معرفة وجود تحديث. لكل مكان تثبيت مصدره الخاص:
 * Play Store ← Play، و Galaxy Store ← Galaxy Store، والتثبيت اليدوي ← GitHub.
 *
 * تُستدعى {@link #checkForUpdate()} من خيط خلفي (قد تحجب الخيط حتى انتهاء الطلب).
 */
public interface UpdateSource {

    /**
     * @return التحديث المتوفر للنسخة المثبتة حالياً، أو Optional.empty() إذا كانت هي الأحدث.
     * @throws Exception عند تعذر التحقق (لا اتصال، استجابة غير مفهومة، التطبيق غير منشور في هذا المتجر ...)
     */
    @NonNull
    Optional<UpdateInfo> checkForUpdate() throws Exception;
}
