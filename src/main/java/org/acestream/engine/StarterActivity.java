package org.acestream.engine;

import android.os.Bundle;
import android.util.Log;

public class StarterActivity extends BaseActivity
{
    private final static String TAG = "AS/Starter";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            startService(ServiceClient.getServiceIntent(getApplicationContext()));
        }
        catch(ServiceClient.ServiceMissingException e) {
            Log.e(TAG, "AceStream is not installed");
        }
        finish();
    }
}