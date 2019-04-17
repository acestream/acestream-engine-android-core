package org.acestream.engine.notification;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import androidx.core.app.NotificationManagerCompat;
import android.util.Log;

import org.acestream.engine.AceStreamEngineBaseApplication;
import org.acestream.engine.BaseReceiver;
import org.acestream.engine.Constants;

/**
 * Receive pending intents from notifications.
 */
public class NotificationBroadcastReceiver extends BaseReceiver {
    private final static String TAG = "AceStream/NBR";

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        String action = intent.getAction();
        if(action == null) {
            return;
        }

        int notificationId = intent.getIntExtra(Constants.EXTRA_NOTIFICATION_ID, -1);
        Log.d(TAG, "got msg: id=" + notificationId + " action=" + action);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

        switch(action) {
            case Constants.ACTION_NOTIFICATION_MAINTAIN_SNOOZE:
                notificationManager.cancel(notificationId);
                SharedPreferences.Editor editor = AceStreamEngineBaseApplication.getPreferences().edit();
                editor.putLong(
                        Constants.PREF_MAINTAIN_INTENT_SNOOZE_UNTIL,
                        System.currentTimeMillis() + Constants.MAINTAIN_INTENT_SNOOZE_INTERVAL
                );
                editor.apply();
                break;
            case Constants.ACTION_NOTIFICATION_MAINTAIN_APPLY:
                notificationManager.cancel(notificationId);
                AceStreamEngineBaseApplication.doMaintain("apply");
                break;
        }
    }
}
