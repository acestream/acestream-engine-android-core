package org.acestream.engine;

import androidx.annotation.MainThread;

public class PlaybackManagerAppCompatActivity extends BaseAppCompatActivity implements PlaybackManagerActivityHelper.ActivityCallback {
    final protected PlaybackManagerActivityHelper mActivityHelper = new PlaybackManagerActivityHelper(this,this);
    protected PlaybackManager mPlaybackManager;
    protected boolean mStartPlaybackManager = true;


    @Override
    protected void onStart() {
        super.onStart();
        if(mStartPlaybackManager) {
            mActivityHelper.onStart();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        mActivityHelper.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mActivityHelper.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mActivityHelper.onPause();
    }

    // These methods are called by activity helper
    @MainThread
    public void onResumeConnected() {
    }

    public void onConnected(PlaybackManager service) {
        mPlaybackManager = service;
    }

    public void onDisconnected() {
        mPlaybackManager = null;
    }
}
