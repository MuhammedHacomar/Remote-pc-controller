package com.example.odev1;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;

public class NetworkReceiver extends BroadcastReceiver {

    public interface NetworkListener {
        void onNetworkChange(boolean isConnected);
    }

    private NetworkListener listener;

    public NetworkReceiver(NetworkListener listener) {
        this.listener = listener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        boolean isConnected = isInternetAvailable(context);
        listener.onNetworkChange(isConnected);
    }

    private boolean isInternetAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        return cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isConnected();
    }
}
