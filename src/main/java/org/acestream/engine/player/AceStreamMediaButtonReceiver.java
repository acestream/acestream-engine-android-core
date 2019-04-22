package org.acestream.engine.player;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.media.session.MediaButtonReceiver;

public class AceStreamMediaButtonReceiver extends MediaButtonReceiver {
    private static final String TAG = "AS/MBReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            super.onReceive(context, intent);
        }
        catch(IllegalStateException e) {
            // MediaButtonReceiver throws an error when didn't find any media browser service
            Log.e(TAG, "onReceive: error", e);
        }
    }
}
