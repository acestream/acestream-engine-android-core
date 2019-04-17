package org.acestream.engine;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import org.acestream.sdk.AceStream;

import androidx.fragment.app.FragmentActivity;

public class BaseFragmentActivity extends FragmentActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AceStream.getBaseApplicationFactory().initialize(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void attachBaseContext(Context base) {
        // attachBaseContext is called before onCreate() so need to initialize here
        AceStream.getBaseApplicationFactory().initialize(base);
        super.attachBaseContext(AceStreamEngineBaseApplication.updateBaseContextLocale(base));
    }
}
