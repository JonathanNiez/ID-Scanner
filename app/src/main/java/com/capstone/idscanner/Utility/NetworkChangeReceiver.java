package com.capstone.idscanner.Utility;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class NetworkChangeReceiver extends BroadcastReceiver {
    public interface NetworkChangeListener {
        void onNetworkChanged(boolean isConnected);
    }

    private NetworkChangeListener networkChangeListener;

    public NetworkChangeReceiver(NetworkChangeListener networkChangeListener) {
        this.networkChangeListener = networkChangeListener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (networkChangeListener != null) {
            boolean isConnected = NetworkConnectivityChecker.isNetworkConnected(context);
            networkChangeListener.onNetworkChanged(isConnected);
        }
    }
}
