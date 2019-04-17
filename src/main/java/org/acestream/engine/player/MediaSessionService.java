package org.acestream.engine.player;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaSessionCompat;

import org.acestream.sdk.utils.Logger;

import java.util.List;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.MediaBrowserServiceCompat;
import androidx.media.session.MediaButtonReceiver;

public class MediaSessionService extends MediaBrowserServiceCompat {

    private final static String TAG = "AS/MediaSession";

    private MediaSessionCompat mMediaSession = null;
    private PlaylistManager.Player mPlayer = null;

    private Binder mLocalBinder = new LocalBinder();

    private class LocalBinder extends Binder {
        MediaSessionService getService() {
            return MediaSessionService.this;
        }
    }

    public static MediaSessionService getService(IBinder iBinder) {
        final LocalBinder binder = (LocalBinder) iBinder;
        return binder.getService();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mLocalBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Logger.v(TAG, "onCreate");

        final Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
        mediaButtonIntent.setClass(this, MediaButtonReceiver.class);
        final PendingIntent mbrIntent = PendingIntent.getBroadcast(this, 0, mediaButtonIntent, 0);
        final ComponentName mbrName = new ComponentName(this, MediaButtonReceiver.class);

        mMediaSession = new MediaSessionCompat(
                this,
                getClass().getName(),
                mbrName,
                mbrIntent);
        setSessionToken(mMediaSession.getSessionToken());
        mMediaSession.setActive(true);
        mMediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mMediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                Logger.v(TAG, "onPlay");
                if(mPlayer != null) {
                    mPlayer.play();
                }
            }

            @Override
            public void onPause() {
                if(mPlayer != null) {
                    Logger.v(TAG, "onPause");
                    mPlayer.pause();
                }
                else {
                    Logger.v(TAG, "onPause: no player");
                }
            }

            @Override
            public void onStop() {
                Logger.v(TAG, "onStop");
                if(mPlayer != null) {
                    mPlayer.stop();
                }
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Logger.v(TAG, "onDestroy");

        mMediaSession.setActive(false);
        mMediaSession.release();
    }

    public MediaSessionCompat getMediaSession() {
        return mMediaSession;
    }

    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
        return null;
    }

    @Override
    public void onLoadChildren(@NonNull String parentId, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
    }

    public void setPlayer(@Nullable PlaylistManager.Player player) {
        mPlayer = player;
    }

    public static class Client {
        @MainThread
        public interface Callback {
            void onConnected(MediaSessionService service);
            void onDisconnected();
        }

        private boolean mBound = false;
        private final Callback mCallback;
        private final Context mContext;

        private final ServiceConnection mServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder iBinder) {
                if (!mBound)
                    return;

                mCallback.onConnected(MediaSessionService.getService(iBinder));
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                mBound = false;
                mCallback.onDisconnected();
            }
        };

        private static Intent getServiceIntent(Context context) {
            return new Intent(context, MediaSessionService.class);
        }

        public Client(Context context, Callback callback) {
            if (context == null || callback == null) throw new IllegalArgumentException("Context and callback can't be null");
            mContext = context;
            mCallback = callback;
        }

        @MainThread
        public void connect() {
            if (mBound) throw new IllegalStateException("already connected");
            final Intent serviceIntent = getServiceIntent(mContext);
            mBound = mContext.bindService(serviceIntent, mServiceConnection, BIND_AUTO_CREATE);
        }

        @MainThread
        public void disconnect() {
            if (mBound) {
                mBound = false;
                mContext.unbindService(mServiceConnection);
            }
        }
    }
}
