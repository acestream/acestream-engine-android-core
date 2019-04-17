package org.acestream.engine;

import android.app.Service;

import org.acestream.sdk.AceStream;

public abstract class BaseService extends Service {
    @Override
    public void onCreate() {
        AceStream.getBaseApplicationFactory().initialize(this);
        super.onCreate();
    }
}
