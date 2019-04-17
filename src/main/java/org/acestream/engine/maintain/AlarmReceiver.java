package org.acestream.engine.maintain;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import org.acestream.engine.BaseReceiver;
import org.acestream.sdk.utils.PermissionUtils;

public class AlarmReceiver extends BaseReceiver {
    private final static String TAG = "AceStream/AR";

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        String action = null;
        if(intent != null) {
            action = intent.getAction();
        }
        Log.d(TAG, "got intent: action=" + action);

        if(TextUtils.equals(action, "maintain")) {
            Log.d(TAG, "alarm: do maintain");

            if(PermissionUtils.hasStorageAccess()) {
                Intent serviceIntent = new Intent(context, AlarmService.class);
                serviceIntent.setAction("maintain");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                }
                else {
                    context.startService(serviceIntent);
                }
            }
            else {
                Log.w(TAG, "alarm: no storage access");
            }
        }
    }
}
