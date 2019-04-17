package org.acestream.engine;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;

import org.acestream.sdk.AceStream;

public class BaseActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AceStream.getBaseApplicationFactory().initialize(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void attachBaseContext(Context base) {
        AceStream.getBaseApplicationFactory().initialize(base);
        super.attachBaseContext(AceStreamEngineBaseApplication.updateBaseContextLocale(base));
    }
}
