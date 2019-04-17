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

public class AlarmService extends BaseService {
    private final static String TAG = "AceStream/Alarm";

    private AceStreamEngineNotificationManager mNotificationManager;
    private int mNotificationId;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.v(TAG, "onCreate");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mNotificationId = AceStreamEngineNotificationManager.GenerateId();
            mNotificationManager = new AceStreamEngineNotificationManager(this);
            Notification n = mNotificationManager.simpleNotification(getString(R.string.maintain_notification_message));
            startForeground(mNotificationId, n);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "onDestroy");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mNotificationManager.cancel(mNotificationId);
            stopForeground(true);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v(TAG, "onStartCommand: action=" + intent.getAction());

        if("maintain".equals(intent.getAction())) {
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
}
