package com.oneui.fontviewer.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import androidx.annotation.NonNull;

public final class NetworkUtils {

    private NetworkUtils() {
    }

    /** هل الجهاز متصل بشبكة تصل إلى الإنترنت؟ (لا يضمن نجاح الطلب، لكنه يمنع محاولة فاشلة مسبقاً) */
    public static boolean isOnline(@NonNull Context context) {
        ConnectivityManager manager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) {
            return false;
        }

        Network network = manager.getActiveNetwork();
        if (network == null) {
            return false;
        }

        NetworkCapabilities capabilities = manager.getNetworkCapabilities(network);
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }
}
