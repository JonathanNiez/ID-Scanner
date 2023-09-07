package com.capstone.idscanner.Utility;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

public class NetworkConnectivityChecker {
    public static boolean isNetworkConnected(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager != null) {
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();

            // Check if there is a network connection and it is connected or connecting
            if (activeNetworkInfo != null && activeNetworkInfo.isConnectedOrConnecting()) {
                return true;
            }
        }

        return false;
    }
}
