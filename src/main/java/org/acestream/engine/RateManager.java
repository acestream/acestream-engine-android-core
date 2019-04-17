package org.acestream.engine;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import androidx.appcompat.app.AlertDialog;
import android.util.Log;

import org.acestream.sdk.AceStream;

public class RateManager {

    private static final String TAG = "AceStream/RateManager";
    private static final int FIRST_DURATION = 3*3600;
    private static final int NEXT_DURATION_IGNORE = 6*3600;
    private static final int NEXT_DURATION_DISLIKE = 12*3600;

    public static void showRateDialog(final Context context, final int type) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        String message;

        if(type == 0) {
            message = context.getString(R.string.rate_give_feedback);
        }
        else {
            message = context.getString(R.string.rate_on_google);
        }

        builder.setMessage(message);
        builder.setPositiveButton(R.string.ok_sure, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Log.d(TAG, "Accept rate dialog: type=" + type);
                if(type == 0) {
                    AceStreamEngineBaseApplication.startBrowserIntent(context, AceStream.getBackendDomain() + "/support");
                    setNextLimit(NEXT_DURATION_DISLIKE);
                }
                else {
                    try {
                        String appPackage = context.getPackageName();
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackage));
                        context.startActivity(intent);
                    }
                    catch(Throwable e) {
                        Log.d(TAG, "showRateDialog", e);
                    }
                    setRated(true);
                }
            }
        });
        builder.setNegativeButton(R.string.no_thanks, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Log.d(TAG, "Dismiss rate dialog: type=" + type);
                setNextLimit(NEXT_DURATION_IGNORE);
            }
        });
        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                Log.d(TAG, "Cancel rate dialog");
            }
        });
        builder.create().show();
    }

    public static boolean shouldRate() {
        try {
            if(AceStreamEngineBaseApplication.getDebugBoolPref("debug_show_rate_bar", false)) {
                Log.d(TAG, "shouldRate: yes in developer mode");
                return true;
            }
            SharedPreferences prefs = AceStreamEngineBaseApplication.getAppPreferences();
            boolean rated = prefs.getBoolean("is_app_rated", false);
            if(rated) {
                // already rated
                Log.d(TAG, "shouldRate: already rated");
                return false;
            }

            long duration = AceStreamEngineBaseApplication.getTotalEngineSessionDuration();
            long limit = prefs.getLong("rate_next_duration", FIRST_DURATION);
            if(duration >= limit) {
                Log.d(TAG, "shouldRate: time to rate: duration=" + duration + " limit=" + limit);
                return true;
            }
            else {
                Log.d(TAG, "shouldRate: not time to rate: duration=" + duration + " limit=" + limit);
                return false;
            }
        }
        catch(Throwable e) {
            Log.e(TAG, "getTotalEngineSessionDuration", e);
            return false;
        }
    }

    private static void setRated(boolean value) {
        try {
            SharedPreferences prefs = AceStreamEngineBaseApplication.getAppPreferences();
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("is_app_rated", value);
            editor.apply();
        }
        catch(Throwable e) {
            Log.e(TAG, "setRated", e);
            return;
        }
    }

    private static void setNextLimit(long add) {
        try {
            SharedPreferences prefs = AceStreamEngineBaseApplication.getAppPreferences();
            SharedPreferences.Editor editor = prefs.edit();
            long currentDuration = prefs.getLong("total_engine_session_duration", 0);
            Log.d(TAG, "setNextLimit: add=" + add + " limit=" + (currentDuration + add));
            editor.putLong("rate_next_duration", currentDuration + add);
            editor.apply();
        }
        catch(Throwable e) {
            Log.e(TAG, "setRated", e);
            return;
        }
    }
}
