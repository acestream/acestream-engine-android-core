package org.acestream.engine;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

public class AceStreamEngineApplication extends AceStreamEngineBaseApplication {
    public static void initialize(Context context) {
        if(sInstance == null) {
            sInstance = new AceStreamEngineApplication(context);
        }
    }

    protected AceStreamEngineApplication(@NonNull final Context context) {
        super(context);
    }
}