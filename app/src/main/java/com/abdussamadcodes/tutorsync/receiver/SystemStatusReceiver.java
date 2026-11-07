package com.abdussamadcodes.tutorsync.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.widget.Toast;

public class SystemStatusReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        switch (intent.getAction()) {
            case Intent.ACTION_BATTERY_LOW:
                Toast.makeText(context, "Low Battery: Background operations paused.", Toast.LENGTH_LONG).show();
                break;
            case Intent.ACTION_BATTERY_OKAY:
                Toast.makeText(context, "Battery Okay: App restored to normal state.", Toast.LENGTH_SHORT).show();
                break;
            case Intent.ACTION_AIRPLANE_MODE_CHANGED:
                boolean isAirplaneModeOn = intent.getBooleanExtra("state", false);
                if (isAirplaneModeOn) {
                    Toast.makeText(context, "Airplane Mode On: Working fully offline.", Toast.LENGTH_LONG).show();
                } else {
                    // Accurately verify network capability, do not just assume it is back
                    if (isNetworkConnected(context)) {
                        Toast.makeText(context, "Network Available: Ready to sync.", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(context, "Airplane Mode Off: Waiting for network connection...", Toast.LENGTH_SHORT).show();
                    }
                }
                break;
        }
    }

    /**
     * Accurately checks if the device currently has an active internet transport.
     */
    private boolean isNetworkConnected(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        return caps != null && (
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        );
    }
}