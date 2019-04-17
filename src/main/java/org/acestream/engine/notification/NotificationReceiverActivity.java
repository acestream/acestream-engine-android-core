package org.acestream.engine.notification;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.core.app.NotificationManagerCompat;
import android.util.Log;

import org.acestream.engine.AceStreamEngineBaseApplication;
import org.acestream.engine.BaseActivity;
import org.acestream.engine.Constants;

/**
 * Receive pending intents from notifications.
 *
 * Use Activity instead of BroadcastReceiver to close notification drawer when action button is
 * clicked.
 *
 * This activity has no GUI. It's used only to dispatch messages.
 *
 */
public class NotificationReceiverActivity extends BaseActivity {
    private final static String TAG = "AceStream/NRA";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if(intent == null) {
            finish();
            return;
        }

        String action = intent.getAction();
        if(action == null) {
            finish();
            return;
        }

        int notificationId = intent.getIntExtra(Constants.EXTRA_NOTIFICATION_ID, -1);
        Log.d(TAG, "got msg: id=" + notificationId + " action=" + action);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);

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

        finish();
    }
}
