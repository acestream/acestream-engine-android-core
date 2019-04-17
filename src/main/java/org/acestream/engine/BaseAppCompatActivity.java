package org.acestream.engine;

import android.content.Context;
import android.os.Bundle;

import org.acestream.sdk.AceStream;

import androidx.appcompat.app.AppCompatActivity;

public class BaseAppCompatActivity extends AppCompatActivity {
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
