package com.oneui.fontviewer.ui.appscreen.settings.update;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import com.oneui.fontviewer.R;
import com.oneui.fontviewer.ui.core.update.UpdateLauncher;
import com.oneui.fontviewer.ui.core.viewmodel.UpdateViewModel;
import com.oneui.fontviewer.ui.core.widget.preference.UpdateCardPreference;

/**
 * يتحكم ببطاقة "يتوفر تحديث جديد" في أعلى الإعدادات.
 *
 * البطاقة معرّفة في preferences.xml وتُحذف هنا إذا لم يكن هناك تحديث
 * أو إذا ضغط المستخدم "ليس الآن" في جلسة الإعدادات الحالية.
 *
 * مهم: {@link #attach()} يجب أن تُستدعى من onCreatePreferences (قبل ربط القائمة بالمحوّل)،
 * وليس من onStart أو onResume. إضافة عنصر أو حذفه بعد الربط يجعل المحوّل يحدّث القائمة في
 * رسالة مؤجلة بعد أول رسم، فيتغير طول القائمة بعد أن استعاد RecyclerView موضع التمرير، ويضيع
 * الموضع عند تغيير اللغة أو الثيم إذا كانت القائمة قصيرة (نفس مشكلة بطاقة الروابط السفلية).
 * لذلك لا نُظهر البطاقة أبداً بعد بناء الشاشة، ونكتفي بحذفها (بسبب ضغط المستخدم أو اختفاء التحديث).
 */
public final class UpdateCardController {

    private static final String KEY_UPDATE_CARD = "update_card";

    private final PreferenceFragmentCompat mFragment;
    private final UpdateViewModel mViewModel;

    public UpdateCardController(@NonNull PreferenceFragmentCompat fragment) {
        mFragment = fragment;
        mViewModel = new ViewModelProvider(fragment.requireActivity()).get(UpdateViewModel.class);
    }

    /** تُستدعى من onCreatePreferences بعد setPreferencesFromResource. */
    public void attach() {
        boolean shouldShow = mViewModel.isUpdateAvailable() && !mViewModel.isUpdateCardDismissed();
        if (!shouldShow) {
            removeCard();
            return;
        }

        UpdateCardPreference card = mFragment.findPreference(KEY_UPDATE_CARD);
        if (card == null) {
            return;
        }

        Context context = mFragment.requireContext();
        card.setSummary(context.getString(
                R.string.update_card_summary, context.getString(R.string.app_name)));
        card.setButtons(
                context.getString(R.string.update_card_not_now),
                v -> onNotNowClicked(),
                context.getString(R.string.update_card_update),
                v -> onUpdateClicked());

        // إذا اختفى التحديث أثناء فتح الإعدادات (مثلاً أكد التحقق في "حول التطبيق" أنه غير متوفر)
        // نحذف البطاقة فقط. لا نضيفها أبداً بعد بناء الشاشة، انظر تعليق الكلاس.
        mViewModel.getAvailableUpdate().observe(mFragment, update -> {
            if (update == null) {
                removeCard();
            }
        });
    }

    private void onNotNowClicked() {
        mViewModel.dismissUpdateCard();
        removeCard();
    }

    private void onUpdateClicked() {
        UpdateLauncher.open(mFragment.requireContext(), mViewModel.getInstallSource(),
                mViewModel.getAvailableUpdateNow());
    }

    private void removeCard() {
        PreferenceScreen screen = mFragment.getPreferenceScreen();
        if (screen == null) {
            return;
        }
        Preference card = screen.findPreference(KEY_UPDATE_CARD);
        if (card != null) {
            screen.removePreference(card);
        }
    }
}
