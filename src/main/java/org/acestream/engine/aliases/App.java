package org.acestream.engine.aliases;

import android.util.Log;

import org.acestream.engine.AceStreamEngineBaseApplication;
import org.acestream.engine.BuildConfig;

public class App {
    private static final String DEBUG_TAG = "AS/DEBUG";

    public static AceStreamEngineBaseApplication getInstance() {
        return AceStreamEngineBaseApplication.getInstance();
    }

    public static boolean verbose() {
        return AceStreamEngineBaseApplication.isDebugLoggingEnabled();
    }

    // Log when verbose mode is on
    public static void v(String tag, String msg) {
        v(tag, msg, null);
    }

    public static void v(String tag, String msg, Throwable error) {
        if(verbose()) {
            Log.v(tag, msg, error);
        }
    }

    // Log in debug versions only
    public static void vv(String tag, String msg) {
        vv(tag, msg, null);
    }

    public static void vv(String tag, String msg, Throwable error) {
        if(BuildConfig.DEBUG) {
            Log.v(tag, msg, error);
        }
    }

    // Log with special debug flag
    public static void vvv(String msg) {
        vvv(msg, null);
    }

    public static void vvv(String msg, Throwable error) {
        if(BuildConfig.DEBUG) {
            Log.v(DEBUG_TAG, msg, error);
        }
    }

    public static void debugAssert(boolean value, String tag, String message) {
        if(!value) {
            if(BuildConfig.DEBUG) {
                throw new IllegalStateException(message);
            }
            else {
                Log.e(tag, message);
            }
        }
    }
}
