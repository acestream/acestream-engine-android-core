package org.acestream.engine;

import android.content.Context;
import androidx.annotation.MainThread;
import android.util.Log;

public class PlaybackManagerActivityHelper implements PlaybackManager.Client.Callback {
    final private Helper mHelper;
    final protected ActivityCallback mActivity;
    protected boolean mPaused = true;
    protected PlaybackManager mPlaybackManager;

    public interface ActivityCallback {
        void onResumeConnected();
        void onConnected(PlaybackManager service);
        void onDisconnected();
    }

    public PlaybackManagerActivityHelper(Context ctx, ActivityCallback activity) {
        mActivity = activity;
        mHelper = new Helper(ctx, this);
    }

    public void onStart() {
        mHelper.onStart();
    }

    public void onStop() {
        mHelper.onStop();
    }

    public void onResume() {
        mPaused = false;
        if(mPlaybackManager != null) {
            mActivity.onResumeConnected();
        }
    }

    public void onPause() {
        mPaused = true;
    }

    public Helper getHelper() {
        return mHelper;
    }

    @Override
    public void onConnected(PlaybackManager service) {
        mPlaybackManager = service;
        mActivity.onConnected(service);
        if(!mPaused) {
            mActivity.onResumeConnected();
        }
    }

    @Override
    public void onDisconnected() {
        mPlaybackManager = null;
        mActivity.onDisconnected();
    }

    public static class Helper {
        private final static String TAG = "AceStream/Helper";

        //private List<PlaybackService.Client.Callback> mFragmentCallbacks = new ArrayList<PlaybackService.Client.Callback>();
        final private PlaybackManager.Client.Callback mActivityCallback;
        private Context mContext;
        private PlaybackManager.Client mClient;
        protected PlaybackManager mService;

        public Helper(Context context, PlaybackManager.Client.Callback activityCallback) {
            mContext = context;
            mClient = new PlaybackManager.Client(context, mClientCallback);
            mActivityCallback = activityCallback;
        }

        @MainThread
        public void onStart() {
            Log.d(TAG, "onStart: context=" + mContext);
            mClient.connect();
        }

        @MainThread
        public void onStop() {
            Log.d(TAG, "onStop: context=" + mContext);
            mClientCallback.onDisconnected();
            mClient.disconnect();
        }

        private final  PlaybackManager.Client.Callback mClientCallback = new PlaybackManager.Client.Callback() {
            @Override
            public void onConnected(PlaybackManager service) {
                mService = service;
                mActivityCallback.onConnected(service);
            }

            @Override
            public void onDisconnected() {
                mService = null;
                mActivityCallback.onDisconnected();
            }
        };
    }
}
