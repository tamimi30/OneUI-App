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
public class Toast extends android.widget.Toast {

    public static final int LENGTH_SHORT = Toast.LENGTH_SHORT;
    public static final int LENGTH_LONG = Toast.LENGTH_LONG;

    public Toast(@Nullable Context context) {
        super(context);
    }

    @NonNull
    public static Toast makeText(@NonNull Context context,
                                    @Nullable CharSequence text,
                                    int duration) {
        Toast toast = new Toast(context);

        LayoutInflater inflater =
                (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.oui_des_transient_notification, null);
        ((TextView) view.findViewById(android.R.id.message)).setText(text);

        toast.setView(view);
        toast.setDuration(duration);
        return toast;
    }

    @NonNull
    public static Toast makeText(@NonNull Context context,
                                    @StringRes int resId,
                                    int duration) throws Resources.NotFoundException {
        return makeText(context, context.getResources().getText(resId), duration);
    }
}
