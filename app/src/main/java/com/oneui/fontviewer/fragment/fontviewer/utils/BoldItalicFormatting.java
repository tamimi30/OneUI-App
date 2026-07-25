package com.oneui.fontviewer.fragment.fontviewer.utils;

import android.os.Bundle;

import androidx.appcompat.widget.AppCompatImageView;

public class BoldItalicFormatting {

    private static final String KEY_IS_BOLD_ACTIVE   = "is_bold_active";
    private static final String KEY_IS_ITALIC_ACTIVE = "is_italic_active";

    private boolean isBoldActive   = false;
    private boolean isItalicActive = false;

    private AppCompatImageView btnBold;
    private AppCompatImageView btnItalic;
    private OnStyleChangedListener listener;

    public interface OnStyleChangedListener {
        void onStyleChanged(boolean isFakeBold, boolean isFakeItalic);
    }

    public void setup(AppCompatImageView btnBold, AppCompatImageView btnItalic, OnStyleChangedListener listener) {
        this.btnBold   = btnBold;
        this.btnItalic = btnItalic;
        this.listener  = listener;

        if (btnBold != null) {
            btnBold.setOnClickListener(v -> {
                isBoldActive = !isBoldActive;
                btnBold.setSelected(isBoldActive);
                notifyStyleChanged();
            });
        }

        if (btnItalic != null) {
            btnItalic.setOnClickListener(v -> {
                isItalicActive = !isItalicActive;
                btnItalic.setSelected(isItalicActive);
                notifyStyleChanged();
            });
        }
    }

    private void notifyStyleChanged() {
        if (listener != null) {
            listener.onStyleChanged(isBoldActive, isItalicActive);
        }
    }

    public void reset() {
        isBoldActive   = false;
        isItalicActive = false;
        if (btnBold   != null) btnBold.setSelected(false);
        if (btnItalic != null) btnItalic.setSelected(false);
        notifyStyleChanged();
    }

    public void unbind() {
        if (btnBold   != null) btnBold.setOnClickListener(null);
        if (btnItalic != null) btnItalic.setOnClickListener(null);
        btnBold   = null;
        btnItalic = null;
        listener  = null;
    }

    public void syncViewState() {
        if (btnBold   != null) btnBold.setSelected(isBoldActive);
        if (btnItalic != null) btnItalic.setSelected(isItalicActive);
    }

    public void saveState(Bundle outState) {
        outState.putBoolean(KEY_IS_BOLD_ACTIVE,   isBoldActive);
        outState.putBoolean(KEY_IS_ITALIC_ACTIVE, isItalicActive);
    }

    public void restoreState(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            isBoldActive   = savedInstanceState.getBoolean(KEY_IS_BOLD_ACTIVE,   false);
            isItalicActive = savedInstanceState.getBoolean(KEY_IS_ITALIC_ACTIVE, false);
        }
    }

    public boolean isBoldActive()   { return isBoldActive; }
    public boolean isItalicActive() { return isItalicActive; }
}
