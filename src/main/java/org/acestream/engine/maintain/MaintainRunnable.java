package org.acestream.engine.maintain;

import android.content.Context;
import androidx.annotation.NonNull;
import android.util.Log;

import org.acestream.engine.python.IPyFinishedListener;
import org.acestream.engine.python.PyEmbedded;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MaintainRunnable implements Runnable {
    private final static String TAG = "AceStream/Maintain";

    private Context mContext;
    private PyEmbedded.Callback mCallback;
    private FinishedCallback mFinishedCallback;
    private List<String> mArgs;

    public interface FinishedCallback {
        void onFinished();
    }

    MaintainRunnable(@NonNull Context context,
                     @Nullable List<String> args,
                     @NonNull PyEmbedded.Callback callback,
                     @Nullable FinishedCallback finishedCallback
                     ) {
        mContext = context;
        mCallback = callback;
        mFinishedCallback = finishedCallback;
        mArgs = args;
    }

    @Override
    public void run() {
        try {
            PyEmbedded pyEmbedded = new PyEmbedded(mContext, mCallback, 0, 0, null);
            pyEmbedded.setOnMaintainProcessFinishedListener(new IPyFinishedListener() {
                @Override
                public void run() {
                    Log.d(TAG, "Maintain task finished");
                    onFinished();
                }
            });
            pyEmbedded.startMaintain(mArgs);
        }
        catch(Exception e) {
            Log.e(TAG, "Failed to start maintain script", e);
            onFinished();
        }
    }

    private void onFinished() {
        if(mFinishedCallback != null) {
            mFinishedCallback.onFinished();
        }
    }
}
