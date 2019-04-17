package org.acestream.engine.maintain;

import android.content.Context;

import org.acestream.engine.python.PyEmbedded;

import java.util.ArrayList;
import java.util.List;

public class MaintainTask {
    private final static String TAG = "AceStream/MaintainT";

    private final Context mContext;
    private final String mMode;
    private final PyEmbedded.Callback mCallback;
    private final MaintainRunnable.FinishedCallback mFinishedCallback;

    public MaintainTask(String mode, Context context, PyEmbedded.Callback callback,
                        MaintainRunnable.FinishedCallback finishedCallback) {
        mMode = mode;
        mContext = context;
        mCallback = callback;
        mFinishedCallback = finishedCallback;
    }

    public void start() {
        new MaintainThread().start();
    }

    class MaintainThread extends Thread {
        @Override
        public void run() {
            List<String> args = null;
            if(mMode != null) {
                args = new ArrayList<>();
                args.add("--mode");
                args.add(mMode);
            }
            new MaintainRunnable(mContext, args, mCallback, mFinishedCallback).run();
        }
    }


}
