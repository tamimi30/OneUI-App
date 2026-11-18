package com.oneui.fontviewer.data.cache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.oneui.fontviewer.data.model.UpdateCheckStatus;

/**
 * حالة "زيارة الإعدادات" الحالية، محفوظة في الذاكرة فقط (تضيع بإغلاق التطبيق عن قصد).
 *
 * الزيارة تبدأ عند فتح SettingsActivity من جديد وتنتهي بالخروج منها. تغيير اللغة أو الثيم يعيد بناء
 * الشاشة لكنه لا يبدأ زيارة جديدة، فتبقى هذه الحالة كما هي. تُستخدم لأمرين:
 * 1) نتيجة فحص صفحة "حول": تُعرض مؤشر التحميل مرة واحدة في الزيارة ثم تُعرض النتيجة المحفوظة
 *    عند كل دخول لاحق، تماماً كتطبيقات Samsung.
 * 2) بطاقة التحديث: قرار ظهورها يُثبَّت مرة واحدة في الزيارة، وزر "ليس الآن" يخفيها حتى نهاية الزيارة.
 *
 * كل الدوال تُستدعى من الخيط الرئيسي فقط (بسبب LiveData.setValue).
 */
public class UpdateSessionState {

    // null = لم يبدأ فحص صفحة "حول" في هذه الزيارة
    private final MutableLiveData<UpdateCheckStatus> mAboutPageStatus = new MutableLiveData<>();

    // null = لم يُحسم بعد هل تظهر البطاقة في هذه الزيارة
    @Nullable
    private Boolean mCardVisibleDecision;

    private boolean mCardDismissed;

    public void reset() {
        mAboutPageStatus.setValue(null);
        mCardVisibleDecision = null;
        mCardDismissed = false;
    }

    @NonNull
    public LiveData<UpdateCheckStatus> getAboutPageStatus() {
        return mAboutPageStatus;
    }

    public void setAboutPageStatus(@NonNull UpdateCheckStatus status) {
        mAboutPageStatus.setValue(status);
    }

    @Nullable
    public Boolean getCardVisibleDecision() {
        return mCardVisibleDecision;
    }

    public void setCardVisibleDecision(boolean visible) {
        mCardVisibleDecision = visible;
    }

    public boolean isCardDismissed() {
        return mCardDismissed;
    }

    public void setCardDismissed(boolean dismissed) {
        mCardDismissed = dismissed;
    }
}
