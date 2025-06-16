package esel.esel.esel.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.content.ContextCompat;

import esel.esel.esel.services.DataMonitorService;
import esel.esel.esel.util.EselLog;

/**
 * Created by adrian on 04/08/17.
 */

public class AutostartReceiver extends BroadcastReceiver {

    private static final String TAG = "AutostartReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            EselLog.LogI(TAG, "Received BOOT_COMPLETED. Attempting to start DataMonitorService.");
            ContextCompat.startForegroundService(context, new Intent(context, DataMonitorService.class));
        }
    }
}