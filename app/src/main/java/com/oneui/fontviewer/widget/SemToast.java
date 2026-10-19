package com.oneui.fontviewer.widget;

import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.oneui.fontviewer.R;

/**
 * Custom {@link android.widget.Toast Toast} widget with no icon.
 */
public class SemToast extends Toast {

    public static final int LENGTH_SHORT = Toast.LENGTH_SHORT;
    public static final int LENGTH_LONG = Toast.LENGTH_LONG;

    public SemToast(@Nullable Context context) {
        super(context);
    }

    @NonNull
    public static SemToast makeText(@NonNull Context context,
                                    @Nullable CharSequence text,
                                    int duration) {
        SemToast toast = new SemToast(context);

        LayoutInflater inflater =
                (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.oui_des_transient_notification, null);
        ((TextView) view.findViewById(android.R.id.message)).setText(text);

        toast.setView(view);
        toast.setDuration(duration);
        return toast;
    }

    @NonNull
    public static SemToast makeText(@NonNull Context context,
                                    @StringRes int resId,
                                    int duration) throws Resources.NotFoundException {
        return makeText(context, context.getResources().getText(resId), duration);
    }
}
