package com.oneui.fontviewer.ui.features.settings.update;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceViewHolder;
import androidx.preference.PreferenceScreen;

import java.util.ArrayList;
import java.util.List;

import dev.oneuiproject.oneui.preference.TipsCardPreference;

import com.oneui.fontviewer.R;
import com.oneui.fontviewer.data.repository.UpdateRepository;
import com.oneui.fontviewer.ui.features.update.StorePageLauncher;

/**
 * بطاقة "يتوفر تحديث جديد" في أعلى قائمة الإعدادات (TipsCardPreference من مكتبة One UI).
 *
 * ★ يجب استدعاء showIfNeeded من onCreatePreferences (قبل ربط القائمة بالمحوّل) وليس لاحقاً. إضافتها بعد
 * الربط تجعل المحوّل يضيفها في رسالة مؤجلة بعد أول رسم، فتطول القائمة بعد أن استعاد RecyclerView موضع
 * التمرير، فيضيع الموضع عند تغيير اللغة أو الثيم إذا كانت القائمة قصيرة (نفس مشكلة بطاقة الروابط السفلية). ★
 */
public class UpdateAvailableCardPreference extends TipsCardPreference {

    private static final String KEY = "update_available_card";

    // أقل من ترتيب أي عنصر في preferences.xml (الذي يبدأ من 0)، فتكون البطاقة أول عنصر
    private static final int ORDER_FIRST = -1;

    private static final class CardButton {
        final CharSequence text;
        final View.OnClickListener listener;

        CardButton(CharSequence text, View.OnClickListener listener) {
            this.text = text;
            this.listener = listener;
        }
    }

    private final List<CardButton> mButtons = new ArrayList<>();

    private UpdateAvailableCardPreference(@NonNull Context context) {
        super(context);
    }

    /** يضيف البطاقة إلى الإعدادات إذا كان يوجد تحديث ولم يضغط المستخدم "ليس الآن" في هذه الزيارة. */
    public static void showIfNeeded(@NonNull PreferenceFragmentCompat fragment) {
        PreferenceScreen screen = fragment.getPreferenceScreen();
        if (screen == null) {
            return;
        }

        Context context = fragment.requireContext();
        UpdateRepository repository = UpdateRepository.getInstance(context);
        if (!repository.shouldShowSettingsUpdateCard()) {
            return;
        }

        UpdateAvailableCardPreference card = new UpdateAvailableCardPreference(screen.getContext());
        card.setKey(KEY);
        card.setOrder(ORDER_FIRST);
        card.setPersistent(false);
        card.setTitle(R.string.update_card_title);
        card.setSummary(R.string.update_card_description);

        card.mButtons.add(new CardButton(context.getString(R.string.update_card_button_not_now), v -> {
            screen.removePreference(card);
            repository.dismissSettingsUpdateCard();
        }));
        card.mButtons.add(new CardButton(context.getString(R.string.update_card_button_update), v -> {
            boolean opened = StorePageLauncher.openUpdatePage(v.getContext(), repository.getDistributionChannel());
            if (!opened) {
                Toast.makeText(v.getContext(), R.string.toast_cannot_open_link, Toast.LENGTH_SHORT).show();
            }
        }));

        screen.addPreference(card);
    }

    /**
     * TipsCardPreference يضيف الأزرار مرة واحدة فقط عند أول ربط ثم ينساها، فإذا أعاد RecyclerView بناء
     * عنصر البطاقة (عنصر جديد بدل المعاد استخدامه) تختفي الأزرار. لذلك نعيد إضافتها عند أي ربط
     * يجد شريط الأزرار فارغاً.
     */
    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        LinearLayout bottomBar = holder.itemView.findViewById(dev.oneuiproject.oneui.design.R.id.tips_bottom_bar);
        if (bottomBar != null && bottomBar.getChildCount() == 0) {
            for (CardButton button : mButtons) {
                TextView added = addButton(button.text, button.listener);
                added.setContentDescription(button.text);
            }
        }
    }
}
