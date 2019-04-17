package org.acestream.engine;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import org.acestream.engine.acecast.server.AceStreamDiscoveryServerService;
import org.acestream.sdk.utils.PermissionUtils;

/**
 * Start AceCast server after reboot
 */
public class BootReceiver extends BaseReceiver {
    private final static String TAG = "AceStream/BR";

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        String action = null;
        if(intent != null) {
            action = intent.getAction();
        }
        Log.d(TAG, "boot completed: action=" + action);

        if(!TextUtils.equals(action, Intent.ACTION_BOOT_COMPLETED)) {
            return;
        }

        if(AceStreamEngineBaseApplication.shouldStartAceCastServer()) {
            Log.d(TAG, "boot completed: start AceCast server");

            if(PermissionUtils.hasStorageAccess()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        Intent engineServiceIntent = ServiceClient.getServiceIntent(context);
                        engineServiceIntent.putExtra("skipEngineStart", true);
                        engineServiceIntent.putExtra("startAceCastServer", true);
                        context.startForegroundService(engineServiceIntent);
                    }
                    catch(ServiceClient.ServiceMissingException e) {
                        Log.e(TAG, "AceStream is not installed");
                    }
                }
                else {
                    AceStreamDiscoveryServerService.Client.startService(AceStreamEngineBaseApplication.context());
                }
            }
            else {
                Log.w(TAG, "boot: no storage access");
            }
        }
    }
}
