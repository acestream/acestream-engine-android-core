package org.acestream.engine;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.acestream.sdk.AceStream;

public class BaseReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        AceStream.getBaseApplicationFactory().initialize(context);
    }
}
