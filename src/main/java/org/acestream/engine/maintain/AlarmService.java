package org.acestream.engine.maintain;

import android.app.Notification;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import androidx.annotation.Nullable;
import android.util.Log;

import org.acestream.engine.BaseService;
import org.acestream.engine.R;
import org.acestream.engine.service.AceStreamEngineNotificationManager;
import org.acestream.engine.service.AceStreamEngineService;

public class AlarmService extends BaseService {
    private final static String TAG = "AceStream/Alarm";

    private AceStreamEngineNotificationManager mNotificationManager;
    private int mNotificationId = -1;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.v(TAG, "onCreate");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "onDestroy");
        hideNotification();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v(TAG, "onStartCommand: action=" + intent.getAction());

        if("maintain".equals(intent.getAction())) {
            if(!AceStreamEngineService.isCreated()) {
                showNotification();
            }

            String mode = intent.getStringExtra("mode");
            new MaintainTask(mode, this, null, new MaintainRunnable.FinishedCallback() {
                @Override
                public void onFinished() {
                    Log.v(TAG, "task finished");
                    stopSelf();
                }
            }).start();
        }

        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void showNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && mNotificationId == -1) {
            mNotificationId = AceStreamEngineNotificationManager.GenerateId();
            mNotificationManager = new AceStreamEngineNotificationManager(this, false);
            Notification n = mNotificationManager.simpleNotification(
                    getString(R.string.maintain_notification_message),
                    R.drawable.ace_ic_menu_preferences);
            startForeground(mNotificationId, n);
        }
    }

    private void hideNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && mNotificationId != -1) {
            mNotificationManager.cancel(mNotificationId);
            stopForeground(true);
        }
    }
}
