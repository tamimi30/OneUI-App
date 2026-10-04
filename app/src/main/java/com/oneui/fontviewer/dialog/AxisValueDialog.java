package com.oneui.fontviewer.dialog;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SeslSeekBar;

import com.oneui.fontviewer.R;

/**
 * ديالوج عام لتعديل قيمة أي محور من محاور الخط المتغير بواسطة SeekBar.
 * العنوان يُمرَّر من الخارج، فيُعاد استخدامه لكل المحاور.
 */
public class AxisValueDialog {

    public interface Listener {
        /** أثناء السحب: للمعاينة المباشرة فقط، بلا حفظ. */
        void onPreview(float value);

        /** عند الضغط على OK. */
        void onConfirmed(float value);

        /** عند Cancel أو الضغط خارج الديالوج. */
        void onCancelled();
    }

    private final Context context;
    private final String title;
    private final float minValue;
    private final float maxValue;
    private final float initialValue;
    private float tempValue;
    private Listener listener;

    public AxisValueDialog(Context context, String title,
                           float initialValue, float minValue, float maxValue) {
        this.context = context;
        this.title = title;
        this.initialValue = initialValue;
        this.tempValue = initialValue;
        this.minValue = minValue;
        this.maxValue = maxValue;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void show() {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_axis_value, null);
        TextView titleText = view.findViewById(R.id.axis_dialog_title);
        TextView valueText = view.findViewById(R.id.axis_dialog_value);
        SeslSeekBar seekBar = view.findViewById(R.id.axis_dialog_seekbar);

        titleText.setText(title);
        valueText.setText(String.valueOf(Math.round(initialValue)));

        seekBar.setMax(Math.round(maxValue - minValue));
        seekBar.setProgress(Math.round(initialValue - minValue));

        seekBar.setOnSeekBarChangeListener(new SeslSeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeslSeekBar bar, int progress, boolean fromUser) {
                tempValue = minValue + progress;
                valueText.setText(String.valueOf(Math.round(tempValue)));
                if (fromUser && listener != null) {
                    listener.onPreview(tempValue);
                }
            }

            @Override
            public void onStartTrackingTouch(SeslSeekBar bar) {}

            @Override
            public void onStopTrackingTouch(SeslSeekBar bar) {}
        });

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(view)
                .setPositiveButton(android.R.string.ok, (d, which) -> {
                    if (listener != null) listener.onConfirmed(tempValue);
                })
                .setNegativeButton(android.R.string.cancel, (d, which) -> {
                    if (listener != null) listener.onCancelled();
                })
                .create();

        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnCancelListener(d -> {
            if (listener != null) listener.onCancelled();
        });
        dialog.show();
    }
  }
