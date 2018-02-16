package com.lapharcius.randomstudent;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

/**
 * Created by Christopher on 2/5/2018.
 */

public class NetworkReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        ConnectivityManager conn = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo;
        if (conn != null) {
            networkInfo = conn.getActiveNetworkInfo();
            if (networkInfo != null && networkInfo.isConnected()) {
                Log.i("LOGMESSAGE", "Network Connected!");
            } else {
                Log.i("LOGMESSAGE", "Network Not Connected!");

            }
        }
        else
        {
            Log.i("LOGMESSAGE", "Network Not Connected!");
        }
    }
}
