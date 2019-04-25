package org.acestream.engine;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.text.TextUtils;
import android.util.Log;

import org.acestream.sdk.AceStream;

public abstract class BaseService extends Service {

    private static final String TAG = "AS/BaseService";

    // broadcast receiver
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent == null) return;
            if(TextUtils.equals(intent.getAction(), AceStream.ACTION_STOP_APP)) {
                Log.d(TAG, "receiver: stop app: class=" + BaseService.this.getClass().getSimpleName());
                stopApp();
            }
        }
    };

    @Override
    public void onCreate() {
        AceStream.getBaseApplicationFactory().initialize(this);
        super.onCreate();

        final IntentFilter filter = new IntentFilter();
        filter.addAction(AceStream.ACTION_STOP_APP);
        registerReceiver(mBroadcastReceiver, filter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mBroadcastReceiver);
    }

    protected void stopApp() {
        stopSelf();
    }
}
