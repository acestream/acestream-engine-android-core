package org.acestream.engine;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.connectsdk.core.MediaInfo;
import com.connectsdk.device.ConnectableDevice;
import com.connectsdk.discovery.CapabilityFilter;
import com.connectsdk.discovery.DiscoveryManager;
import com.connectsdk.discovery.DiscoveryManagerListener;
import com.connectsdk.service.AirPlayService;
import com.connectsdk.service.DeviceService;
import com.connectsdk.service.capability.MediaControl;
import com.connectsdk.service.capability.MediaPlayer;
import com.connectsdk.service.capability.VolumeControl;
import com.connectsdk.service.capability.listeners.ResponseListener;
import com.connectsdk.service.command.ServiceCommandError;
import com.connectsdk.service.sessions.LaunchSession;

import org.acestream.engine.acecast.client.AceStreamDiscoveryClient;
import org.acestream.engine.acecast.client.AceStreamRemoteDevice;
import org.acestream.engine.acecast.interfaces.AceStreamDiscoveryListener;
import org.acestream.engine.acecast.interfaces.AceStreamRemoteDeviceListener;
import org.acestream.engine.acecast.interfaces.DeviceDiscoveryListener;
import org.acestream.engine.acecast.interfaces.DeviceStatusListener;
import org.acestream.engine.acecast.server.AceStreamDiscoveryServerService;
import org.acestream.engine.ads.AdManager;
import org.acestream.engine.controller.ExtendedEngineApi;
import org.acestream.engine.csdk.CsdkDeviceWrapper;
import org.acestream.engine.csdk.CsdkBridge;
import org.acestream.engine.prefs.ExtendedEnginePreferences;
import org.acestream.engine.prefs.NotificationData;
import org.acestream.engine.service.AceStreamEngineService;
import org.acestream.engine.util.LogcatOutputStreamWriter;
import org.acestream.sdk.AceStream;
import org.acestream.sdk.AceStreamManager;
import org.acestream.sdk.Constants;
import org.acestream.sdk.ContentStream;
import org.acestream.sdk.EngineSession;
import org.acestream.sdk.EngineSessionStartListener;
import org.acestream.sdk.EngineStatus;
import org.acestream.sdk.JsonRpcMessage;
import org.acestream.sdk.MediaItem;
import org.acestream.sdk.OutputFormat;
import org.acestream.sdk.PlaybackData;
import org.acestream.sdk.SelectedPlayer;
import org.acestream.sdk.SystemUsageInfo;
import org.acestream.sdk.TrackDescription;
import org.acestream.sdk.controller.EngineApi;
import org.acestream.sdk.controller.api.AceStreamPreferences;
import org.acestream.sdk.controller.api.DataWithMime;
import org.acestream.sdk.controller.api.response.AdConfig;
import org.acestream.sdk.controller.api.response.AndroidConfig;
import org.acestream.sdk.controller.api.response.AuthData;
import org.acestream.sdk.controller.api.response.MediaFilesResponse;
import org.acestream.sdk.controller.api.TransportFileDescriptor;
import org.acestream.sdk.controller.api.response.VastTag;
import org.acestream.sdk.controller.api.VastTags;
import org.acestream.sdk.errors.EngineSessionStoppedException;
import org.acestream.sdk.errors.TransportFileParsingException;
import org.acestream.engine.service.v0.IAceStreamEngine;
import org.acestream.sdk.interfaces.EngineCallbackListener;
import org.acestream.sdk.interfaces.EngineSessionListener;
import org.acestream.sdk.interfaces.EngineStatusListener;
import org.acestream.sdk.interfaces.IAceStreamManager;
import org.acestream.sdk.interfaces.IHttpAsyncTaskListener;
import org.acestream.sdk.interfaces.IRemoteDevice;
import org.acestream.sdk.interfaces.ConnectableDeviceListener;
import org.acestream.sdk.utils.AuthUtils;
import org.acestream.sdk.utils.HttpAsyncTask;
import org.acestream.sdk.utils.HttpRequest;
import org.acestream.sdk.utils.Logger;
import org.acestream.sdk.utils.MiscUtils;
import org.acestream.sdk.utils.RunnableWithParams;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import androidx.annotation.MainThread;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import static org.acestream.sdk.Constants.CONTENT_TYPE_VOD;
import static org.acestream.sdk.Constants.MIME_HLS;
import static org.acestream.sdk.Constants.PREF_KEY_SELECTED_PLAYER;

public abstract class AceStreamManagerImpl
    extends BaseService
    implements
        IAceStreamManager,
        IHttpAsyncTaskListener,
        DiscoveryManagerListener,
        com.connectsdk.device.ConnectableDeviceListener,
        AceStreamDiscoveryListener,
        AceStreamRemoteDeviceListener,
        ServiceClient.Callback
{
    private static final String TAG = "AS/Manager";
    private final int ENGINE_STATUS_UPDATE_INTERVAL = 1000;
    private final int DEVICE_STATUS_UPDATE_INTERVAL = 1000;

    private final int LOCAL_PACKAGE_INFO_TTL = 3600000;

    private DiscoveryManager mDiscoveryManager = null;
    private AceStreamDiscoveryClient mDiscoveryClient = null;

    private final List<Callback> mCallbacks = new ArrayList<>();
    private final List<PlaybackStateCallback> mPlaybackStateCallbacks = new ArrayList<>();
    private final List<EngineSettingsCallback> mEngineSettingsCallbacks = new ArrayList<>();
    protected final List<AuthCallback> mAuthCallbacks = new ArrayList<>();

    protected IBinder mLocalBinder = null;

    // Messenger service
    protected Handler mRemoteMessengerHandler = new RemoteMessengerHandler();
    protected Messenger mRemoteMessenger = new Messenger(mRemoteMessengerHandler);
    protected List<Messenger> mRemoteClients = new ArrayList<>();

    // AdManager
    private AdManager mAdManager = null;

    private long mLastNotificationAt = 0;
    protected AuthData mCurrentAuthData = null;
    // Local info about user package (obtained from Google Play Billing)
    protected UserPackageInfo mLocalPackageInfo = null;
    private EngineSession mEngineSession;
    private final Object mEngineSessionLock = new Object();
    private EngineStatus mLastEngineStatus = null;
    private LaunchSession mLaunchSession;
    private MediaControl mMediaControl;
    private VolumeControl mVolumeControl;
    protected CsdkDeviceWrapper mCurrentDevice;
    private Playlist mCurrentPlaylist;
    protected CastResultListener mCastResultListener;
    private PowerManager.WakeLock mWakeLock;
    private MediaInfo mMediaInfo= null;
    private boolean mWaitReconnect = false;
    private MediaControl.PlayStateStatus mLastPlayState = MediaControl.PlayStateStatus.Unknown;
    private long mLastDuration = -1;
    private long mLastPosition = -1;
    private boolean mRestartFromLastPosition = false;
    private long mStartFrom = 0;
    private boolean mPlaybackStarted = false;
    private boolean mPlaybackRestarted = false;
    private long mLastPlayingAt = -1;
    private long mDeviceLastSeenAt = -1;
    private String mLastSelectedDeviceId = null;
    private AceStreamRemoteDevice mCurrentRemoteDevice = null;
    private String mLastRemoteDeviceId = null;
    private String mRemoteClientId = null;
    private String mLastRemoteClientDeviceId = null;
    private boolean mPlayerStarted = false;
    private SelectedPlayer mCurrentSelectedPlayer = null;
    private boolean mEngineConnected = false;
    private boolean mFreezeBonusAdsAvailable = false;
    private int mRemoteEngineStatusListeners = 0;

    // Last engine preferences
    protected ExtendedEnginePreferences mEnginePreferences = null;
    protected AceStreamPreferences mAppPreferences = new AceStreamPreferences();
    protected AdConfig mAdConfig = null;

    private EngineStatusHandler mEngineStatusHandler = new EngineStatusHandler();

    // Discovery service client
    private AceStreamDiscoveryServerService.Client mDiscoveryServerServiceClient = null;

    // map content (infohash and fileindex) to last position and duration
    private Map<Pair<String,Integer>, Pair<Long,Long>> mContentSettings;
    private boolean mContentSettingsChanged = false;
    private final Object mContentSettingsLock = new Object();

    private Set<ConnectableDeviceListener> mPlaybackStatusListeners;
    private Set<EngineStatusListener> mEngineStatusListeners;
    private Set<EngineSessionListener> mEngineSessionListeners;
    private Set<EngineCallbackListener> mEngineCallbackListeners;
    private Set<DeviceStatusListener> mDeviceStatusListeners;
    private Set<AceStreamRemoteDeviceListener> mRemoteDeviceListeners;
    private Set<EngineStateCallback> mEngineStateCallbacks = new CopyOnWriteArraySet<>();
    private Set<AdConfigCallback> mAdConfigCallbacks = new CopyOnWriteArraySet<>();

    private Set<DeviceDiscoveryListener> mDeviceDiscoveryListeners;

    private ServiceClient mEngineServiceClient = null;
    private HttpAsyncTask.Factory mHttpAsyncTaskFactory = null;
    protected ExtendedEngineApi mEngineApi = null;
    private boolean mIsOurPlayerActive = false;

    protected int mPendingBonuses = 0;
    protected long mLastBonusesUpdatedAt = 0;

    protected final Handler mHandler = new Handler();

    private static final int DISCOVERY_PAUSE_TIMEOUT = 60000;
    private static final int DISCOVERY_STOP_TIMEOUT = 300000;

    public interface Callback {
        void onEngineConnected(ExtendedEngineApi service);
        void onEngineFailed();
        void onEngineUnpacking();
        void onEngineStarting();
        void onEngineStopped();
    }

    private static class UserPackageInfo {
        int authLevel;
        String packageName = "?";
        String packageColor = "green";
        int packageDaysLeft;
        long expiresAt;
    }

    public interface AdConfigCallback {
        void onSuccess(AdConfig config);
    }

    private AceStreamDiscoveryServerService.Client.RemoteCallback mDSSCallback = new AceStreamDiscoveryServerService.Client.RemoteCallback() {
        @Override
        public void onConnected() {
            mDiscoveryServerServiceClient.addServerListener(mDSSServerCallback);
            if(!TextUtils.isEmpty(mRemoteClientId)) {
                mDiscoveryServerServiceClient.getClientInfo(mRemoteClientId);
            }
        }

        @Override
        public void onDisconnected() {
            Log.v(TAG, "DiscoveryServerService disconnected");
            mDiscoveryServerServiceClient.removeServerListener(mDSSServerCallback);
        }
    };

    protected void onNetworkStateChanged(final boolean connected) {
        Log.v(TAG, "network state changed: connected=" + connected);
        if(mDiscoveryClient != null) {
            mDiscoveryClient.reset();
        }

        if(connected) {
            Log.v(TAG, "network connected, restart discovery");
            discoverDevices(true);
        }
        else {
            Log.v(TAG, "network disconnected, stop discovery");
            stopDiscovery(false);
        }
    }

    private BroadcastReceiver mLocalBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent == null) return;
            if(intent.getAction() == null) return;

            switch(intent.getAction()) {
                case AceStreamEngineBaseApplication.BROADCAST_DO_INTERNAL_MAINTAIN:
                    mHandler.removeCallbacks(mInternalMaintainTask);
                    mHandler.postDelayed(mInternalMaintainTask, 60000);
                    break;
            }
        }
    };

    private BroadcastReceiver mNetworkStatusListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            onNetworkStateChanged(MiscUtils.isNetworkConnected(context));
        }
    };

    private Runnable mInternalMaintainTask = new Runnable() {
        @Override
        public void run() {
            AceStreamEngineBaseApplication app = AceStreamEngineBaseApplication.getInstance();
            app.doInternalMaintain(AceStreamManagerImpl.this);
            mHandler.postDelayed(mInternalMaintainTask, app.internalMaintainInterval());
        }
    };

    private Runnable persistContentSettingsTask = new Runnable() {
        @Override
        public void run() {
            synchronized (mContentSettingsLock) {
                if(mContentSettingsChanged) {
                    mContentSettingsChanged = false;

                    if(mContentSettings.size() > 0) {
                        SharedPreferences prefs = AceStreamEngineBaseApplication.getContentPreferences();
                        SharedPreferences.Editor editor = prefs.edit();

                        for (Map.Entry<Pair<String, Integer>, Pair<Long, Long>> entry : mContentSettings.entrySet()) {
                            String infohash = entry.getKey().first;
                            int fileindex = entry.getKey().second;
                            long pos = entry.getValue().first;
                            long duration = entry.getValue().second;

                            // Don't allow -1 because we use "-" as separator
                            if(pos < 0)
                                pos = 0;
                            if(duration < 0)
                                duration = 0;

                            String key = infohash + "-" + fileindex;
                            String value = pos + "-" + duration;
                            editor.putString(key, value);
                        }

                        editor.apply();
                    }
                }
            }
            mHandler.postDelayed(persistContentSettingsTask, 60000);
        }
    };

    private Runnable updateEngineStatusTask = new Runnable() {
        @Override
        public void run() {
            synchronized (mEngineSessionLock) {
                if (mEngineSession != null && mEngineSession.statUrl != null && mEngineApi != null) {
                    String url = "http://" + mEngineApi.getHost() + ":" + mEngineApi.getPort() + mEngineSession.statUrl;
                    if(!shouldUpdatePlayerActivity()) {
                        url += "?skip_player_activity=1";
                    }
                    final String playbackSessionId = mEngineSession.playbackSessionId;
                    HttpRequest.get(Uri.parse(url), null, new HttpRequest.Callback() {
                        @Override
                        public void onResponse(HttpRequest.Response response) {
                            if(response == null) {
                                Log.e(TAG, "status request failed");
                                return;
                            }

                            synchronized (mEngineSessionLock) {
                                if (mEngineSession != null) {
                                    try {
                                        EngineStatus status = parseEngineStatus(response.body);
                                        mLastEngineStatus = status;
                                        if (status != null) {
                                            mEngineSession.isLive = status.isLive;
                                            status.outputFormat = mEngineSession.playbackData.outputFormat.format;
                                            notifyEngineStatus(status, null);
                                        }
                                        mHandler.postDelayed(updateEngineStatusTask, ENGINE_STATUS_UPDATE_INTERVAL);
                                    } catch (EngineSessionStoppedException e) {
                                        if(BuildConfig.DEBUG) {
                                            Log.v(TAG, "estatus:error: orig=" + playbackSessionId + " curr=" + mEngineSession.playbackSessionId);
                                        }
                                        stopEngineSession(false);
                                    }
                                }
                            }
                        }
                    });
                }
            }

            //TODO: don't check every second
            //checkSelectDeviceButtonVisibility();
        }
    };

    private Runnable updatePlaybackStatusTask = new Runnable() {
        private int mErrorCount = 10;

        @Override
        public void run() {
            if(mMediaControl != null) {

                if(!mMediaControl.isMediaControlConnected()) {
                    if(mErrorCount >= 10) {
                        Log.w(TAG, "PM: media control is disconnected");
                        mErrorCount = 0;
                    }
                    else {
                        ++mErrorCount;
                    }
                    mLastPlayState = MediaControl.PlayStateStatus.Unknown;
                    notifyPlaybackStatus(mLastPlayState);
                    mHandler.postDelayed(updatePlaybackStatusTask, DEVICE_STATUS_UPDATE_INTERVAL);
                }
                else {
                    mMediaControl.getPlayState(new MediaControl.PlayStateListener() {
                        @Override
                        public void onSuccess(MediaControl.PlayStateStatus status) {
                            mDeviceLastSeenAt = System.currentTimeMillis();
                            notifyPlaybackStatus(status);
                            mHandler.postDelayed(updatePlaybackStatusTask, DEVICE_STATUS_UPDATE_INTERVAL);
                        }

                        @Override
                        public void onError(ServiceCommandError error) {
                            mLastPlayState = MediaControl.PlayStateStatus.Unknown;

                            if(mErrorCount >= 10) {
                                Log.w(TAG, "control: failed to get status: code=" + error.getCode() + " msg=" + error.getMessage());
                                mErrorCount = 0;
                            }
                            else {
                                ++mErrorCount;
                            }

                            if (error.getCode() == 500 && getCurrentServiceName().equals("AirPlay")) {
                                // try to restart AirPlay playback
                                long age = System.currentTimeMillis() - mLastPlayingAt;
                                if(!mPlaybackStarted) {
                                    Log.d(TAG, "skip restart: not started");
                                }
                                else if(mPlaybackRestarted) {
                                    Log.d(TAG, "skip restart: already restarted");

                                }
                                else if(mCurrentDevice == null) {
                                    Log.d(TAG, "skip restart: missing current device");
                                }
                                else if(age > 35000) {
                                    Log.d(TAG, "skip restart: outdated: age=" + age);
                                }
                                else {
                                    mPlaybackRestarted = true;
                                    MediaPlayer mediaPlayer = mCurrentDevice.getDevice().getCapability(MediaPlayer.class);
                                    Pair<String, String> mediaDescriptor = getMediaUrlForDevice();
                                    if (mediaDescriptor == null) {
                                        Log.d(TAG, "skip restart: no media descriptor");
                                    }
                                    else {
                                        String mime = mediaDescriptor.first;
                                        String url = mediaDescriptor.second;

                                        float startPosition = 0.0f;
                                        if (getCurrentItemType().equals("vod") && mLastDuration != -1 && mLastPosition != -1) {
                                            startPosition = mLastPosition / (float) mLastDuration;
                                        }

                                        Log.d(TAG, String.format("restart from position %.2f (age=%d type=%s pos=%d duration=%d)",
                                                startPosition,
                                                age,
                                                getCurrentItemType(),
                                                mLastPosition,
                                                mLastDuration));

                                        MediaInfo mediaInfo = new MediaInfo.Builder(url, mime).setStartPosition(startPosition).build();
                                        mediaPlayer.playMedia(mediaInfo, false, new MediaPlayer.LaunchListener() {
                                            @Override
                                            public void onSuccess(MediaPlayer.MediaLaunchObject object) {
                                                Log.d(TAG, "airplay reconnect: success");
                                            }

                                            @Override
                                            public void onError(ServiceCommandError error) {
                                                Log.e(TAG, "airplay reconnect: failed", error);
                                            }
                                        });
                                    }
                                }
                            }

                            notifyPlaybackStatus(mLastPlayState);
                            mHandler.postDelayed(updatePlaybackStatusTask, DEVICE_STATUS_UPDATE_INTERVAL);
                        }
                    });

                    if(mMediaControl instanceof AirPlayService) {
                        ((AirPlayService) mMediaControl).getPositionAndDuration(new MediaControl.PositionAndDurationListener() {
                            @Override
                            public void onSuccess(Pair<Long, Long> object) {
                                notifyPlaybackDuration(object.first);
                                notifyPlaybackPosition(object.second);
                            }

                            @Override
                            public void onError(ServiceCommandError error) {
                                String msg = error.getMessage();
                                if (error.getCode() == 0 && msg != null && msg.equals("Unable to get position and duration")) {
                                    //pass
                                } else {
                                    Log.e(TAG, "control: failed to get position and duration: code=" + error.getCode() + " msg=" + msg, error);
                                }
                            }
                        });
                    }
                    else {

                        mMediaControl.getPosition(new MediaControl.PositionListener() {
                            @Override
                            public void onSuccess(Long object) {
                                notifyPlaybackPosition(object);
                            }

                            @Override
                            public void onError(ServiceCommandError error) {
                                String msg = error.getMessage();
                                if (msg != null && msg.equals("There is no media currently available")) {
                                    //pass
                                } else if (error.getCode() == 0 && msg != null && msg.equals("Unable to get position")) {
                                    //pass
                                } else {
                                    Log.e(TAG, "control: failed to get position: code=" + error.getCode() + " msg=" + msg, error);
                                }
                            }
                        });

                        mMediaControl.getDuration(new MediaControl.DurationListener() {
                            @Override
                            public void onSuccess(Long object) {
                                notifyPlaybackDuration(object);
                            }

                            @Override
                            public void onError(ServiceCommandError error) {
                                String msg = error.getMessage();
                                if (msg != null && msg.equals("There is no media currently available")) {
                                    //pass
                                } else if (error.getCode() == 0 && msg != null && msg.equals("Unable to get duration")) {
                                    //pass
                                } else {
                                    Log.e(TAG, "control: failed to get duration: code=" + error.getCode() + " msg=" + msg, error);
                                }
                            }
                        });
                    }
                }
            }

            if(mVolumeControl != null) {
                mVolumeControl.getVolume(new VolumeControl.VolumeListener() {
                    @Override
                    public void onSuccess(Float object) {
                        notifyPlaybackVolume(object);
                    }

                    @Override
                    public void onError(ServiceCommandError error) {
                        Log.e(TAG, "control: failed to get volume", error);
                    }
                });
            }
        }
    };

    private Runnable mStopDiscoveryTask = new Runnable() {
        @Override
        public void run() {
            if(canStopDeviceDiscovery()) {
                Log.d(TAG, "don't stop discovery, got active device or listeners");
                updateDiscoveryTimeout();
            }
            else {
                Log.d(TAG, "stop discovery");
                stopDiscovery(true);
            }
        }
    };

    private Runnable mPauseDiscoveryTask = new Runnable() {
        @Override
        public void run() {
            if(!canStopDeviceDiscovery()) {
                Log.d(TAG, "don't pause discovery, got active device or listeners");
                updateDiscoveryTimeout();
            }
            else {
                Log.d(TAG, "pause discovery");
                stopDiscovery(false);
            }
        }
    };

    private AceStreamDiscoveryServerService.Client.ServerCallback mDSSServerCallback = new AceStreamDiscoveryServerService.Client.ServerCallback() {
        @Override
        public void onClientConnected(String clientId, String deviceId) {
            Log.d(TAG, "remote control connected: id=" + clientId + " deviceId=" + deviceId);
            if(TextUtils.equals(deviceId, mLastRemoteClientDeviceId)) {
                setCurrentRemoteClient(clientId, deviceId);
            }
        }

        @Override
        public void onClientDisconnected(String clientId, String deviceId) {
            Log.d(TAG, "remote control disconnected: id=" + clientId + " deviceId=" + deviceId + " current=" + mRemoteClientId);
            if(TextUtils.equals(clientId, mRemoteClientId)) {
                setCurrentRemoteClient(null, null);
            }
        }

        @Override
        public void onClientInfo(String clientId, String deviceId) {
            Logger.v(TAG, "dss:server:onClientInfo: clientId=" + clientId + " deviceId=" + deviceId);
            if(TextUtils.equals(clientId, mRemoteClientId) && !TextUtils.isEmpty(deviceId)) {
                initRemoteClient(deviceId);
            }
        }
    };

    private AceStreamDiscoveryServerService.Client.ClientCallback mDSSClientCallback = new AceStreamDiscoveryServerService.Client.ClientCallback() {
        @Override
        public void onMessage(String clientId, JsonRpcMessage message) {
            switch(message.getMethod()) {
                case "stopEngineSession":
                    stopEngineSession(true);
                    break;
                case "stop":
                    if(mEngineSession != null
                            && mEngineSession.playbackData.selectedPlayer != null
                            && !mEngineSession.playbackData.selectedPlayer.isOurPlayer()) {
                        // got stop and we're using external player - stop engine session
                        stopEngineSession(true);
                    }
                    break;
                case "setHlsStream":
                    int streamIndex = message.getInt("streamIndex");
                    Log.d(TAG, "onMessage:setHlsStream: streamIndex=" + streamIndex);
                    setHlsStream(streamIndex);
                    break;
            }
        }

        @Override
        public void onDisconnected(String clientId, String deviceId) {
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.v(TAG, "onCreate");

        // Listen for network status changes
        registerReceiver(mNetworkStatusListener, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        IntentFilter filter = new IntentFilter(AceStreamEngineBaseApplication.BROADCAST_DO_INTERNAL_MAINTAIN);
        LocalBroadcastManager.getInstance(this).registerReceiver(mLocalBroadcastReceiver, filter);

        mDiscoveryServerServiceClient = new AceStreamDiscoveryServerService.Client(this, mDSSCallback);
        mDiscoveryServerServiceClient.connect();

        mPlaybackStatusListeners = new HashSet<>();
        mEngineStatusListeners = new HashSet<>();
        mEngineCallbackListeners = new HashSet<>();
        mEngineSessionListeners = new CopyOnWriteArraySet<>();
        mDeviceStatusListeners = new CopyOnWriteArraySet<>();
        mDeviceDiscoveryListeners = new CopyOnWriteArraySet<>();
        mRemoteDeviceListeners = new CopyOnWriteArraySet<>();
        mContentSettings = new HashMap<>();

        try {
            initServiceClient();
        }
        catch(ServiceClient.ServiceMissingException e) {
        }

        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if(powerManager != null) {
            mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                    "acestream:pm:wakelock");
        }

        initContentSettings();
        mHandler.post(persistContentSettingsTask);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "onDestroy");

        stopDiscovery(true);

        if(mDiscoveryClient != null) {
            mDiscoveryClient.removeDeviceDiscoveryListener(this);
            mDiscoveryClient.destroy();
            mDiscoveryClient = null;
        }

        unregisterReceiver(mNetworkStatusListener);

        mDiscoveryServerServiceClient.removeServerListener(mDSSServerCallback);

        mHandler.removeCallbacksAndMessages(null);

        if(mEngineServiceClient != null) {
            mEngineServiceClient.unbind();
            mEngineServiceClient = null;
            mEngineApi = null;
        }
        mDiscoveryServerServiceClient.disconnect();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        String action = (intent == null) ? null : intent.getAction();

        if(TextUtils.equals(action, AceStreamManager.REMOTE_BIND_ACTION)) {
            return mRemoteMessenger.getBinder();
        }
        else {
            return mLocalBinder;
        }
    }

    protected void updateEnginePreferences() {
        if(mEngineApi == null) return;

        mEngineApi.getPreferences(new org.acestream.engine.controller.Callback<ExtendedEnginePreferences>() {
            @Override
            public void onSuccess(ExtendedEnginePreferences result) {
                if(result == null) {
                    Log.e(TAG, "Failed to get engine prefs");
                    return;
                }

                onGotEnginePreferences(result);
            }

            @Override
            public void onError(String err) {
                Log.e(TAG, "failed to get prefs: error=" + err);
            }
        });
    }

    protected void onGotEnginePreferences(@NonNull ExtendedEnginePreferences preferences) {
        mEnginePreferences = preferences;
        mAppPreferences = AceStreamEngineBaseApplication.processSettings(preferences);
        AceStreamEngineBaseApplication.updateNotifications(preferences);
        checkAdConfig();

        notifyEngineSettingsUpdated(mAppPreferences);
    }

    public ExtendedEnginePreferences getEnginePreferences() {
        return mEnginePreferences;
    }

    public AceStreamPreferences getAppPreferences() {
        return mAppPreferences;
    }

    private void initContentSettings() {
        try {
            SharedPreferences prefs = AceStreamEngineBaseApplication.getContentPreferences();
            for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
                String[] key = entry.getKey().split("-");
                String[] value = ((String) entry.getValue()).split("-");

                // dirty hack to handle "-1"
                if(value.length == 3) {
                    value[1] = "-" + value[2];
                }
                else if(value.length != 2) {
                    return;
                }

                if(key.length != 2) return;
                if(key[0].length() == 0 || key[1].length() == 0) return;
                if(value[0].length() == 0 || value[1].length() == 0) return;

                Pair<String,Integer> k = new Pair<>(key[0], Integer.valueOf(key[1]));
                Pair<Long,Long> v = new Pair<>(Long.valueOf(value[0]), Long.valueOf(value[1]));

                mContentSettings.put(k, v);
            }
        }
        catch(Throwable e) {
            Log.e(TAG, "initContentSettings", e);
        }
    }

    public void discoverDevices(boolean forceInit) {
        if(!AceStream.shouldDiscoverDevices()) {
            Log.v(TAG, "discoverDevices: disabled");
            return;
        }

        Log.d(TAG, "discoverDevices: force=" + forceInit);

        updateDiscoveryTimeout();

        if(forceInit || mDiscoveryManager == null) {
            if(mDiscoveryManager != null) {
                mDiscoveryManager.stop();
                mDiscoveryManager.removeListener(this);
                DiscoveryManager.destroy();
                mDiscoveryManager = null;
            }

            mDiscoveryManager = createDiscoveryManager();
            mDiscoveryManager.start();
        }
        else {
            mDiscoveryManager.start();
        }

        if(mDiscoveryClient == null) {
            // Create and init
            mDiscoveryClient = new AceStreamDiscoveryClient(this);
            mDiscoveryClient.addDeviceDiscoveryListener(this);
        }
        else if(forceInit) {
            // Reinit
            mDiscoveryClient.shutdown();
        }
        mDiscoveryClient.start();
    }

    private void stopDiscovery(boolean destroy) {
        Log.d(TAG, "stopDiscovery: destroy=" + destroy);

        if(mDiscoveryManager != null) {
            mDiscoveryManager.stop();
            if (destroy) {
                mDiscoveryManager.removeListener(this);
                DiscoveryManager.destroy();
                mDiscoveryManager = null;
            }
        }

        if(mDiscoveryClient != null) {
            mDiscoveryClient.shutdown();
        }
    }

    private DiscoveryManager createDiscoveryManager() {
        CapabilityFilter videoFilter = new CapabilityFilter(
                MediaPlayer.Play_Video
        );
        // Need to use static methods because ConnectSDK expects this
        DiscoveryManager.init(this);
        DiscoveryManager dm = DiscoveryManager.getInstance();
        dm.registerDefaultDeviceTypes();
        dm.setPairingLevel(DiscoveryManager.PairingLevel.ON);
        dm.setCapabilityFilters(videoFilter);
        dm.addListener(this);

        return dm;
    }

    public static boolean shouldShowRemoteDevices(String outputFormat, String mime) {
        // use filter for "auto" only
        if(!TextUtils.equals(outputFormat, Constants.OUTPUT_FORMAT_AUTO)) {
            return true;
        }

        if(mime != null && (mime.equals("video/avi") || mime.equals("video/x-msvideo"))) {
            Log.d(TAG, "hide remote devices for mime " + mime);
            return false;
        }
        else {
            return true;
        }
    }

    public Map<String, ConnectableDevice> getConnectableDevices() {
        Map<String, ConnectableDevice> devices = new HashMap<>();
        if(mDiscoveryManager != null) {
            for (Map.Entry<String, ConnectableDevice> entry : mDiscoveryManager.getCompatibleDevices().entrySet()) {
                if (entry.getValue().isConnectable()) {
                    devices.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return devices;
    }

    @NonNull
    public Map<String, AceStreamRemoteDevice> getAceStreamRemoteDevices() {
        if(mDiscoveryClient == null) {
            return new HashMap<>();
        }
        else {
            return mDiscoveryClient.getAvailableDevices();
        }
    }

    public ConnectableDevice findDeviceById(String id) {
        if(mDiscoveryManager != null) {
            for (ConnectableDevice device : mDiscoveryManager.getCompatibleDevices().values()) {
                if (device.getId().equals(id)) {
                    return device;
                }
            }
        }
        return null;
    }

    public ConnectableDevice findDeviceByIp(String ip) {
        if(mDiscoveryManager != null) {
            for (ConnectableDevice device : mDiscoveryManager.getCompatibleDevices().values()) {
                if (TextUtils.equals(device.getIpAddress(), ip)) {
                    Logger.v(TAG, "findDeviceByIp: found: ip=" + ip);
                    return device;
                }
            }
            Logger.v(TAG, "findDeviceByIp: not found: ip=" + ip);
        }
        else {
            Logger.v(TAG, "findDeviceByIp: missing discovery manager");
        }

        return null;
    }

    public AceStreamRemoteDevice findAceStreamRemoteDeviceById(String id) {
        if(mDiscoveryClient == null) {
            return null;
        }
        else {
            return mDiscoveryClient.findDeviceById(id);
        }
    }

    public static int getIconForDevice(ConnectableDevice device, boolean dark) {
        if(device.getServiceByName("AirPlay") != null) {
            return dark ? R.drawable.ic_airplay_24dp_757575 : R.drawable.ic_airplay_24dp_white;
        }
        else if(device.getServiceByName("Chromecast") != null) {
            return dark ? R.drawable.ic_cast_24dp_757575 : R.drawable.ic_cast_24dp_white;
        }
        else {
            return -1;
        }
    }

    public static int getIconForDevice(AceStreamRemoteDevice device, boolean dark) {
        return dark ? R.drawable.acecast_dark_48dp : R.drawable.acecast_48dp;
    }

    public void setCurrentPlaylist(Playlist playlist) {
        Log.v(TAG, "setCurrentPlaylist: playlist=" + playlist);
        mCurrentPlaylist = playlist;
    }

    public void shutdown() {
        Log.d(TAG, "shutdown");
        disconnectDevice();
        stopSelf();
    }

    @Override
    protected void stopApp() {
        Log.d(TAG, "stopApp");
        stopEngine();
    }

    public void setCurrentDevice(ConnectableDevice device, boolean disconnectOther) {
        Log.d(TAG, "set current device: current="
                + (mCurrentDevice == null ? "null" : mCurrentDevice.getName())
                + " new="
                + (device == null ? "null" : device.getFriendlyName())
                + " disconnectOther=" + disconnectOther
        );

        if(disconnectOther) {
            // stop acecast device if connected
            setCurrentRemoteDevice(null, true, false);
        }

        if(mCurrentDevice != null) {
            if(device != null && mCurrentDevice.getId().equals(device.getId())) {
                Log.d(TAG, "set current device: same device: name=" + mCurrentDevice.getName());
                return;
            }

            try {
                mHandler.removeCallbacks(updatePlaybackStatusTask);
                if(mMediaControl != null) {
                    Log.d(TAG, "stop prev playback: name=" + mCurrentDevice.getName());
                    mMediaControl.stop(null);
                }

                Log.d(TAG, "disconnect prev device: name=" + mCurrentDevice.getName());
                mCurrentDevice.getDevice().disconnect();
                mCurrentDevice.getDevice().removeListener(this);
                mCurrentDevice = null;
            }
            catch(Exception e) {
                Log.e(TAG, "Failed to disconnect prev device", e);
            }
        }

        // reset session and control
        if(device == null) {
            mMediaControl = null;
            mVolumeControl = null;
            mLaunchSession = null;
        }
        else {
            mMediaControl = device.getCapability(MediaControl.class);
            mVolumeControl = device.getCapability(VolumeControl.class);
        }

        mCurrentDevice = (device == null) ? null : new CsdkDeviceWrapper(device);
    }

    private void resetPlayState() {
        mLastPlayState = MediaControl.PlayStateStatus.Unknown;
        mLastDuration = -1;
        mLastPosition = -1;
        mDeviceLastSeenAt = -1;
        mLastPlayingAt = -1;
        notifyPlaybackStatus(mLastPlayState);
    }

    @SuppressWarnings("unused")
    public void enableP2PUpload() {
        Log.d(TAG, "enableP2PUpload");
        if(mHttpAsyncTaskFactory != null) {
            mHttpAsyncTaskFactory.build(HttpAsyncTask.HTTPTASK_SET_SETTINGS, null).execute2("GET", "_pref_disable_p2p_upload=0");
        }
    }

    @SuppressWarnings("unused")
    public void disableP2PUpload() {
        Log.d(TAG, "disableP2PUpload");
        if(mHttpAsyncTaskFactory != null) {
            mHttpAsyncTaskFactory.build(HttpAsyncTask.HTTPTASK_SET_SETTINGS, null).execute2("GET", "_pref_disable_p2p_upload=1");
        }
    }

    public MediaControl getMediaControl() {
        return mMediaControl;
    }

    public VolumeControl getVolumeControl() {
        return mVolumeControl;
    }

    @SuppressWarnings("unused")
    public boolean hasPlaylist() {
        if(mCurrentPlaylist == null) {
            return false;
        }
        return mCurrentPlaylist.getSize() > 1;
    }

    public Playlist getCurrentPlaylist() {
        return mCurrentPlaylist;
    }

    public ConnectableDevice getCurrentDevice() {
        return (mCurrentDevice == null) ? null : mCurrentDevice.getDevice();
    }

    public PlaylistItem getCurrentPlaylistItem() {
        return mCurrentPlaylist == null ? null : mCurrentPlaylist.getCurrentItem();
    }

    // DiscoveryManagerListener interface
    @Override
    public void onDeviceAdded(DiscoveryManager manager, ConnectableDevice device) {
        Log.d(TAG, String.format("onDeviceAdded: name=%s id=%s services=%s reconnect=%s current=%s",
                device.getFriendlyName(),
                device.getId(),
                device.getConnectedServiceNames(),
                mWaitReconnect,
                mCurrentDevice == null ? "null" : mCurrentDevice.getId()
                ));
        notifyDeviceAdded(device);
        reconnectServicedIfNeeded(device);
    }

    @Override
    public void onDeviceUpdated(DiscoveryManager manager, ConnectableDevice device) {
        Log.d(TAG, String.format("onDeviceUpdated: name=%s id=%s services=%s reconnect=%s current=%s",
                device.getFriendlyName(),
                device.getId(),
                device.getConnectedServiceNames(),
                mWaitReconnect,
                mCurrentDevice == null ? "null" : mCurrentDevice.getId()
        ));
        reconnectServicedIfNeeded(device);
    }

    private void reconnectServicedIfNeeded(ConnectableDevice device) {
        if(mWaitReconnect && mCurrentDevice != null && mCurrentDevice.getId().equals(device.getId())) {
            for(DeviceService service: device.getServices()) {
                if(!service.isConnected()) {
                    Log.d(TAG, "reconnectServicedIfNeeded: reconnect service: name=" + service.getServiceName());
                    service.connect();
                }
            }
        }
        else {
            Log.d(TAG, String.format("reconnectServicedIfNeeded: skip: name=%s id=%s reconnect=%s current=%s",
                    device.getFriendlyName(),
                    device.getId(),
                    mWaitReconnect,
                    mCurrentDevice == null ? "null" : mCurrentDevice.getId()
            ));
        }
    }

    @Override
    public void onDeviceRemoved(DiscoveryManager manager, ConnectableDevice device) {
        Log.d(TAG, "onDeviceRemoved: name=" + device.getFriendlyName() + " id=" + device.getId());
        notifyDeviceRemoved(device);
    }

    @Override
    public void onDiscoveryFailed(DiscoveryManager manager, ServiceCommandError error) {
        Log.d(TAG, "onDiscoveryFailed");
    }

    // AceStreamDiscoveryListener interface
    @Override
    public void onDeviceAdded(AceStreamRemoteDevice device) {
        Log.d(TAG, "onDeviceAdded:acestream: device=" + device.toString() + " lastDeviceId=" + mLastRemoteDeviceId);
        notifyDeviceAdded(device);

        if(TextUtils.equals(device.getDeviceId(), mLastRemoteDeviceId)) {
            Log.d(TAG, "onDeviceAdded:acestream: reconnect to last device");
            setCurrentRemoteDevice(device, false, true);
            device.connect();
            notifyCurrentDeviceChanged(device);
        }
    }

    @Override
    public void onDeviceRemoved(AceStreamRemoteDevice device) {
        Log.d(TAG, "onDeviceRemoved:acestream: device=" + device.toString());
        notifyDeviceRemoved(device);
        if (mCastResultListener != null) {
            mCastResultListener.onError(getString(R.string.device_disconnected));
            mCastResultListener.onDeviceDisconnected(device);
        }
    }

    // ConnectableDeviceListener interface
    @Override
    public void onDeviceReady (final ConnectableDevice device) {
        if(device == null) {
            Log.e(TAG, "onDeviceReady: null device");
            return;
        }
        Log.d(TAG, "onDeviceReady: name=" + device.getFriendlyName()
                + " reconnect=" + mWaitReconnect
                + " restartFromLastPosition=" + mRestartFromLastPosition
                + " startFrom=" + mStartFrom
        );

        if(mWaitReconnect) {
            notifyDeviceConnected(device);
            return;
        }

        Pair<String, String> mediaDescriptor = getMediaUrlForDevice();
        if(mediaDescriptor == null) {
            Log.e(TAG, "failed to get media url");
            if(mCastResultListener != null) {
                mCastResultListener.onError(getString(R.string.cannot_start_playback));
            }
            return;
        }

        // do this before resetting state
        final long seekOnStart;
        if(mRestartFromLastPosition) {
            mRestartFromLastPosition = false;
            if(mStartFrom > 0) {
                seekOnStart = mStartFrom;
                mStartFrom = 0;
            }
            else {
                Pair<Long, Long> contentSettings = getContentSettingsForCurrentItem();
                if (contentSettings != null) {
                    seekOnStart = contentSettings.first;
                    Log.d(TAG, "onDeviceReady: set seek on start: seek=" + seekOnStart);
                } else {
                    seekOnStart = 0;
                }
            }
        }
        else {
            seekOnStart = 0;
        }

        setCurrentDevice(device, true);
        resetPlayState();
        MediaPlayer mediaPlayer = device.getCapability(MediaPlayer.class);

        if(mediaPlayer == null) {
            Log.d(TAG, "onDeviceReady: no media player");
            if(mCastResultListener != null) {
                mCastResultListener.onError(getString(R.string.cannot_start_playback));
            }
            return;
        }

        String mime = mediaDescriptor.first;
        String url = mediaDescriptor.second;

        Log.d(TAG, "onDeviceReady: play media: mime=" + mime + " url=" + url + " listener=" + (mCastResultListener == null ? "null" : mCastResultListener.hashCode()));

        final CastResultListener fCastResultListener = mCastResultListener;
        MediaInfo mediaInfo = new MediaInfo.Builder(url, mime).build();

        // set timeout for playback starting
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (fCastResultListener != null && fCastResultListener.isWaiting()) {
                    Log.d(TAG, "onDeviceReady: playback start timeout");
                    fCastResultListener.onError(getString(R.string.cannot_start_playback));
                } else {
                    Log.d(TAG, "onDeviceReady: playback start timeout, skip: listener=" + (fCastResultListener == null ? "null" : fCastResultListener.hashCode()));
                }
            }
        }, Constants.REMOTE_DEVICE_START_TIMEOUT * 1000);

        mediaPlayer.playMedia(mediaInfo, false, new MediaPlayer.LaunchListener() {
            @Override
            public void onSuccess(MediaPlayer.MediaLaunchObject object) {
                Log.d(TAG, "onDeviceReady: playback started: listener=" + (fCastResultListener == null ? "null" : fCastResultListener.hashCode()));

                if (fCastResultListener != null) {
                    fCastResultListener.onSuccess();
                }

                final MediaControl mediaControl = object.mediaControl;
                mLaunchSession = object.launchSession;

                mediaControl.getPlayState(new MediaControl.PlayStateListener() {
                    @Override
                    public void onSuccess(MediaControl.PlayStateStatus status) {
                        Log.d(TAG, "onDeviceReady: play state: " + status.toString());
                        if (status != MediaControl.PlayStateStatus.Playing) {
                            Log.d(TAG, "onDeviceReady: request initial play");
                            mediaControl.play(new ResponseListener<Object>() {
                                @Override
                                public void onSuccess(Object object) {
                                    Log.d(TAG, "onDeviceReady: initial play success");
                                }

                                @Override
                                public void onError(ServiceCommandError error) {
                                    Log.e(TAG, "onDeviceReady: initial play failed", error);
                                }
                            });
                        }
                    }

                    @Override
                    public void onError(ServiceCommandError error) {
                        Log.d(TAG, "onDeviceReady: failed to get initial play state", error);
                    }
                });

                if (seekOnStart > 0) {
                    Log.d(TAG, "onDeviceReady: seek on start: " + seekOnStart);
                    mediaControl.seek(seekOnStart, new ResponseListener<Object>() {
                        @Override
                        public void onSuccess(Object object) {
                            Log.d(TAG, "onDeviceReady: seek on start success");
                        }

                        @Override
                        public void onError(ServiceCommandError error) {
                            Log.d(TAG, "onDeviceReady: seek on start failed", error);
                        }
                    });
                }

                mWaitReconnect = true;
                notifyDeviceConnected(device);
                mHandler.postDelayed(updatePlaybackStatusTask, 0);
            }

            @Override
            public void onError(ServiceCommandError error) {
                Log.d(TAG, "onDeviceReady: playback failed: error=" + error.getMessage() + " listener=" + fCastResultListener, error);
                if (fCastResultListener != null) {
                    fCastResultListener.onError(error.getMessage());
                }
            }
        });
    }

    @Override
    public void onDeviceDisconnected(ConnectableDevice device, boolean cleanShutdown) {
        Log.d(TAG, "onDeviceDisconnected: clean=" + cleanShutdown + " name=" + device.getFriendlyName() + " id=" + device.getId() + " reconnect=" + mWaitReconnect);

        notifyDeviceDisconnected(device, cleanShutdown);
    }

    @Override
    public void onPairingRequired(ConnectableDevice device, DeviceService service, DeviceService.PairingType pairingType) {
    }

    @Override
    public void onCapabilityUpdated(ConnectableDevice device, List<String> added, List<String> removed) {
        Log.d(TAG, "onCapabilityUpdated");
    }

    @Override
    public void onConnectionFailed(ConnectableDevice device, ServiceCommandError error) {
        Log.d(TAG, "onConnectionFailed");
        if(mCastResultListener != null) {
            mCastResultListener.onError(getString(R.string.connection_failed));
        }
        setCurrentDevice(null, false);
    }

    public void onServiceConnected(ConnectableDevice device, DeviceService service) {
        Log.d(TAG, String.format("onServiceConnected: device=%s service=%s",
                device.getFriendlyName(),
                service.getServiceName()));
    }

    public void onServiceDisconnected(ConnectableDevice device, DeviceService service) {
        Log.d(TAG, String.format("onServiceDisconnected: device=%s service=%s",
                device.getFriendlyName(),
                service.getServiceName()));
    }

    @Override
    public void onHttpAsyncTaskStart(int type) {
    }

    @Override
    public void onHttpAsyncTaskFinish(int type, String result, Map<String, Object> extraData) {
        switch(type) {
            case HttpAsyncTask.HTTPTASK_START_CONTENT:
                finishedStartingEngineSession(result, extraData);
                break;
            default:
                break;
        }
    }

    public void addPlaybackStatusListener(ConnectableDeviceListener listener) {
        mPlaybackStatusListeners.add(listener);
    }

    public void removePlaybackStatusListener(ConnectableDeviceListener listener) {
        mPlaybackStatusListeners.remove(listener);
    }

    private void notifyPlaybackStatus(MediaControl.PlayStateStatus status) {
        if(mCurrentDevice == null) {
            Logger.v(TAG, "notifyPlaybackStatus: missing current device");
            return;
        }
        mCurrentDevice.getDevice().setLastStatus(status);

        if (mLastPlayState != status) {
            Log.d(TAG, String.format(
                    "control: status changed: %s->%s",
                    mLastPlayState,
                    status));
            mLastPlayState = status;

            if(status == MediaControl.PlayStateStatus.Playing) {
                mPlaybackStarted = true;
                mPlaybackRestarted = false;
                mLastPlayingAt = System.currentTimeMillis();
            }
        }

        for(ConnectableDeviceListener listener: mPlaybackStatusListeners) {
            listener.onStatus(mCurrentDevice, CsdkBridge.convertStatus(status));
        }

        int statusPayload = CsdkBridge.convertStatus(status);
        for(Messenger target: mRemoteClients) {
            Message msg = obtainMessage(AceStreamManager.MSG_DEVICE_ON_PLAYBACK_STATUS);
            Bundle data = new Bundle(2);
            data.putString(AceStreamManager.MSG_PARAM_REMOTE_DEVICE, serializeRemoteDevice(mCurrentDevice));
            data.putInt(AceStreamManager.MSG_PARAM_PLAYBACK_STATUS, statusPayload);
            msg.setData(data);
            sendMessage(target, msg);
        }
    }

    private void notifyPlaybackPosition(Long position) {
        if(mCurrentDevice == null) {
            Logger.v(TAG, "notifyPlaybackStatus: missing current device");
            return;
        }
        mCurrentDevice.getDevice().setLastPosition(position);
        savePlaybackPosition(position);

        for(ConnectableDeviceListener listener: mPlaybackStatusListeners) {
            listener.onPosition(mCurrentDevice, position);
        }

        for(Messenger target: mRemoteClients) {
            Message msg = obtainMessage(AceStreamManager.MSG_DEVICE_ON_PLAYBACK_POSITION);
            Bundle data = new Bundle(2);
            data.putString(AceStreamManager.MSG_PARAM_REMOTE_DEVICE, serializeRemoteDevice(mCurrentDevice));
            data.putLong(AceStreamManager.MSG_PARAM_POSITION, position);
            msg.setData(data);
            sendMessage(target, msg);
        }
    }

    private void savePlaybackPosition(Long position) {
        // AppleTV returns 0 when not playing
        if(position > 0) {
            mLastPosition = position;
            updateContentSettings();
        }
    }

    private void notifyPlaybackDuration(Long duration) {
        if(mCurrentDevice == null) {
            Logger.v(TAG, "notifyPlaybackStatus: missing current device");
            return;
        }
        mCurrentDevice.getDevice().setLastDuration(duration);
        savePlaybackDuration(duration);

        for(ConnectableDeviceListener listener: mPlaybackStatusListeners) {
            listener.onDuration(mCurrentDevice, duration);
        }

        for(Messenger target: mRemoteClients) {
            Message msg = obtainMessage(AceStreamManager.MSG_DEVICE_ON_PLAYBACK_DURATION);
            Bundle data = new Bundle(2);
            data.putString(AceStreamManager.MSG_PARAM_REMOTE_DEVICE, serializeRemoteDevice(mCurrentDevice));
            data.putLong(AceStreamManager.MSG_PARAM_DURATION, duration);
            msg.setData(data);
            sendMessage(target, msg);
        }
    }

    private void savePlaybackDuration(Long duration) {
        // AppleTV returns 0 when not playing
        if(duration > 0) {
            mLastDuration = duration;
            updateContentSettings();
        }
    }

    private void updateContentSettings() {
        PlaylistItem item = getCurrentPlaylistItem();
        if(item == null) {
            return;
        }

        if(item.getInfohash() == null) {
            return;
        }

        if(!item.getContentType().equals(Constants.CONTENT_TYPE_VOD)) {
            return;
        }

        synchronized (mContentSettingsLock) {
            Pair<String, Integer> key = new Pair<>(item.getInfohash(), item.getFileIndex());
            mContentSettings.put(key, new Pair<>(mLastPosition, mLastDuration));
            mContentSettingsChanged = true;
        }
    }

    public Pair<Long,Long> getContentSettingsForCurrentItem() {
        PlaylistItem item = getCurrentPlaylistItem();
        if (item == null) {
            Log.d(TAG, "getContentSettingsForCurrentItem: no item");
            return null;
        }

        if (item.getInfohash() == null) {
            Log.d(TAG, "getContentSettingsForCurrentItem: no infohash");
            return null;
        }

        if (!item.getContentType().equals(Constants.CONTENT_TYPE_VOD)) {
            Log.d(TAG, "getContentSettingsForCurrentItem: not VOD");
            return null;
        }

        return getContentSettingsForItem(item.getInfohash(), item.getFileIndex());
    }

    public Pair<Long,Long> getContentSettingsForItem(String infohash, int fileIndex) {
        Pair<String,Integer> key = new Pair<>(infohash, fileIndex);
        Pair<Long,Long> settings = mContentSettings.get(key);
        if(settings == null) {
            Log.d(TAG, "getContentSettingsForCurrentItem: no data: infohash=" + infohash + " idx=" + fileIndex);
            return null;
        }

        if(settings.first <= 0 || settings.second <= 0) {
            Log.d(TAG, "getContentSettingsForCurrentItem: empty data: infohash=" + infohash + " idx=" + fileIndex + " pos=" + settings.first + " duration=" + settings.second);
            return null;
        }

        float pos = settings.first / (float)settings.second;
        if(pos >= 0.95) {
            Log.d(TAG, "getContentSettingsForCurrentItem: late pos: infohash=" + infohash + " idx=" + fileIndex + " pos=" + settings.first + " duration=" + settings.second + " pos=" + pos);
            return null;
        }

        return settings;
    }

    private void notifyPlaybackVolume(float volume) {
        if(mCurrentDevice == null) {
            Logger.v(TAG, "notifyPlaybackStatus: missing current device");
            return;
        }
        mCurrentDevice.getDevice().setLastVolume(volume);

        for(ConnectableDeviceListener listener: mPlaybackStatusListeners) {
            listener.onVolume(mCurrentDevice, volume);
        }

        for(Messenger target: mRemoteClients) {
            Message msg = obtainMessage(AceStreamManager.MSG_DEVICE_ON_PLAYBACK_VOLUME);
            Bundle data = new Bundle(2);
            data.putString(AceStreamManager.MSG_PARAM_REMOTE_DEVICE, serializeRemoteDevice(mCurrentDevice));
            data.putFloat(AceStreamManager.MSG_PARAM_VOLUME, volume);
            msg.setData(data);
            sendMessage(target, msg);
        }
    }

    public void addEngineStatusListener(EngineStatusListener listener) {
        mEngineStatusListeners.add(listener);
    }

    public void removeEngineStatusListener(EngineStatusListener listener) {
        mEngineStatusListeners.remove(listener);
    }

    public void addEngineCallbackListener(EngineCallbackListener listener) {
        mEngineCallbackListeners.add(listener);
    }

    public void removeEngineCallbackListener(EngineCallbackListener listener) {
        mEngineCallbackListeners.remove(listener);
    }

    public void notifyRestartPlayer() {
        for(EngineCallbackListener listener: mEngineCallbackListeners) {
            listener.onRestartPlayer();
        }
    }

    private void notifyEngineStatus(EngineStatus status, @Nullable AceStreamRemoteDevice remoteDevice) {
        // save streams to playlist
        if(mCurrentPlaylist != null) {
            mCurrentPlaylist.setCurrentStreamIndex(status.currentStreamIndex);
            if(mCurrentPlaylist.getStreams().size() != status.streams.size()) {
                Log.d(TAG, "notifyEngineStatus: set streams: count=" + status.streams.size());
                mCurrentPlaylist.setStreams(status.streams);
            }
        }

        // send engine status to remote client
        if(mRemoteClientId != null) {
            JsonRpcMessage msg = new JsonRpcMessage(AceStreamRemoteDevice.Messages.ENGINE_STATUS);
            msg.addParam("status", status.toJson());
            if(mCurrentSelectedPlayer != null) {
                msg.addParam("selectedPlayer", mCurrentSelectedPlayer.getId());
            }
            if(mEngineSession != null) {
                msg.addParam("outputFormat", mEngineSession.playbackData.outputFormat.format);
                msg.addParam("fileIndex", mEngineSession.playbackData.mediaFile.index);
            }

            SharedPreferences sp = AceStreamEngineBaseApplication.getPreferences();
            boolean showDebugInfo = sp.getBoolean(Constants.PREF_KEY_SHOW_DEBUG_INFO, false);
            if(showDebugInfo) {
                SystemUsageInfo systemUsageInfo = MiscUtils.getSystemUsage(this);
                msg.addParam("system_usage", systemUsageInfo.toJson());
            }

            mDiscoveryServerServiceClient.sendClientMessage(mRemoteClientId, msg);

            if(!mPlayerStarted
                    && status.status.equals("dl")
                    && mEngineSession.playbackData.selectedPlayer != null
                    && !mEngineSession.playbackData.selectedPlayer.isOurPlayer()) {
                Log.d(TAG, "notifyEngineStatus: start local player");
                startLocalPlayer();
            }
        }

        for(EngineStatusListener listener: mEngineStatusListeners) {
            listener.onEngineStatus(status, remoteDevice);
        }

        String statusPayload = status.toJson();
        String devicePayload = null;
        if(remoteDevice != null) {
            devicePayload = serializeRemoteDevice(remoteDevice);
        }
        for(Messenger target: mRemoteClients) {
            Message msg = obtainMessage(AceStreamManager.MSG_ENGINE_STATUS);
            Bundle data = new Bundle(2);
            data.putString(AceStreamManager.MSG_PARAM_ENGINE_STATUS, statusPayload);
            if(devicePayload != null) {
                data.putString(AceStreamManager.MSG_PARAM_REMOTE_DEVICE, devicePayload);
            }
            msg.setData(data);
            sendMessage(target, msg);
        }

        mEngineStatusHandler.setStatus(status, remoteDevice);
    }

    /**
     * Returns true if at least one of the listeners wants to update player activity
     * @return boolean
     */
    private boolean shouldUpdatePlayerActivity() {
        if(mLastPlayState == MediaControl.PlayStateStatus.Paused) {
            return true;
        }
        else if(mLastPlayState == MediaControl.PlayStateStatus.Playing) {
            return true;
        }
        else {
            if(mRemoteEngineStatusListeners > 0) {
                return true;
            }

            // update player activity if at least one listener wants so
            for (EngineStatusListener listener : mEngineStatusListeners) {
                if(listener.updatePlayerActivity()) {
                    return true;
                }
            }

            // update activity for 3 minutes if we're playing VOD and we've got inactive air device
            if(getCurrentItemType().equals(Constants.CONTENT_TYPE_VOD)) {
                if(mCurrentDevice != null) {
                    long age = getDeviceSeenTimeout();
                    if(age >= 15000 && age <= 180000) {
                        return true;
                    }
                }
            }

            return false;
        }
    }

    public void addDeviceStatusListener(DeviceStatusListener listener) {
        mDeviceStatusListeners.add(listener);
    }

    public void removeDeviceStatusListener(DeviceStatusListener listener) {
        mDeviceStatusListeners.remove(listener);
    }

    private void notifyDeviceConnected(ConnectableDevice device) {
        if(mCastResultListener != null) {
            mCastResultListener.onDeviceConnected(device);
        }

        for(DeviceStatusListener listener: mDeviceStatusListeners) {
            listener.onDeviceConnected(device);
        }

        String devicePayload = serializeRemoteDevice(device);
        for(Messenger target: mRemoteClients) {
            Message msg = obtainMessage(AceStreamManager.MSG_DEVICE_ON_CONNECTED);
            Bundle data = new Bundle(1);
            data.putString(AceStreamManager.MSG_PARAM_REMOTE_DEVICE, devicePayload);
            msg.setData(data);
            sendMessage(target, msg);
        }
    }

    private void notifyDeviceDisconnected(ConnectableDevice device, boolean cleanShutdown) {
        if(mCastResultListener != null) {
            mCastResultListener.onDeviceDisconnected(device);
        }

        for(DeviceStatusListener listener: mDeviceStatusListeners) {
            listener.onDeviceDisconnected(device, cleanShutdown);
        }

        String devicePayload = serializeRemoteDevice(device);
        for(Messenger target: mRemoteClients) {
            Message msg = obtainMessage(AceStreamManager.MSG_DEVICE_ON_DISCONNECTED);
            Bundle data = new Bundle(2);
            data.putString(AceStreamManager.MSG_PARAM_REMOTE_DEVICE, devicePayload);
            data.putBoolean(AceStreamManager.MSG_PARAM_CLEAN_SHUTDOWN, cleanShutdown);
            msg.setData(data);
            sendMessage(target, msg);
        }
    }

    @SuppressWarnings("unused")
    public void addRemoteDeviceListener(AceStreamRemoteDeviceListener listener) {
        mRemoteDeviceListeners.add(listener);
    }

    @SuppressWarnings("unused")
    public void removeRemoteDeviceListener(AceStreamRemoteDeviceListener listener) {
        mRemoteDeviceListeners.remove(listener);
    }

    private void notifyRemoteDeviceMessage(AceStreamRemoteDevice device, JsonRpcMessage message) {
        for(AceStreamRemoteDeviceListener listener: mRemoteDeviceListeners) {
            listener.onMessage(device, message);
        }

        String devicePayload = serializeRemoteDevice(device);
        String messagePayload = message.toString();
        for(Messenger target: mRemoteClients) {
            Message msg = obtainMessage(AceStreamManager.MSG_DEVICE_ON_MESSAGE);
            Bundle data = new Bundle(2);
            data.putString(AceStreamManager.MSG_PARAM_REMOTE_DEVICE, devicePayload);
            data.putString(AceStreamManager.MSG_PARAM_JSON_RPC_MESSAGE, messagePayload);
            msg.setData(data);
            sendMessage(target, msg);
        }
    }

    private void notifyRemoteDeviceConnected(AceStreamRemoteDevice device) {
        if(mCastResultListener != null) {
            mCastResultListener.onDeviceConnected(device);
        }

        for(AceStreamRemoteDeviceListener listener: mRemoteDeviceListeners) {
            listener.onConnected(device);
        }

        String devicePayload = serializeRemoteDevice(device);
        for(Messenger target: mRemoteClients) {
            Message msg = obtainMessage(AceStreamManager.MSG_DEVICE_ON_CONNECTED);
            Bundle data = new Bundle(1);
            data.putString(AceStreamManager.MSG_PARAM_REMOTE_DEVICE, devicePayload);
            msg.setData(data);
            sendMessage(target, msg);
        }
    }

    private void notifyRemoteDeviceDisconnected(AceStreamRemoteDevice device, boolean cleanShutdown) {
        if(mCastResultListener != null) {
            mCastResultListener.onDeviceDisconnected(device);
        }

        for(AceStreamRemoteDeviceListener listener: mRemoteDeviceListeners) {
            listener.onDisconnected(device, cleanShutdown);
        }

        String devicePayload = serializeRemoteDevice(device);
        for(Messenger target: mRemoteClients) {
            Message msg = obtainMessage(AceStreamManager.MSG_DEVICE_ON_DISCONNECTED);
            Bundle data = new Bundle(2);
            data.putString(AceStreamManager.MSG_PARAM_REMOTE_DEVICE, devicePayload);
            data.putBoolean(AceStreamManager.MSG_PARAM_CLEAN_SHUTDOWN, cleanShutdown);
            msg.setData(data);
            sendMessage(target, msg);
        }
    }

    @SuppressWarnings("unused")
    public void notifyAvailable(AceStreamRemoteDevice device) {
        for(AceStreamRemoteDeviceListener listener: mRemoteDeviceListeners) {
            listener.onAvailable(device);
        }

        String devicePayload = serializeRemoteDevice(device);
        for(Messenger target: mRemoteClients) {
            Message msg = obtainMessage(AceStreamManager.MSG_DEVICE_ON_AVAILABLE);
            Bundle data = new Bundle(1);
            data.putString(AceStreamManager.MSG_PARAM_REMOTE_DEVICE, devicePayload);
            msg.setData(data);
            sendMessage(target, msg);
        }
    }

    @SuppressWarnings("unused")
    public void notifyUnavailable(AceStreamRemoteDevice device) {
        for(AceStreamRemoteDeviceListener listener: mRemoteDeviceListeners) {
            listener.onUnavailable(device);
        }

        String devicePayload = serializeRemoteDevice(device);
        for(Messenger target: mRemoteClients) {
            Message msg = obtainMessage(AceStreamManager.MSG_DEVICE_ON_UNAVAILABLE);
            Bundle data = new Bundle(1);
            data.putString(AceStreamManager.MSG_PARAM_REMOTE_DEVICE, devicePayload);
            msg.setData(data);
            sendMessage(target, msg);
        }
    }

    @SuppressWarnings("unused")
    public void notifyPingFailed(AceStreamRemoteDevice device) {
        for(AceStreamRemoteDeviceListener listener: mRemoteDeviceListeners) {
            listener.onPingFailed(device);
        }

        String devicePayload = serializeRemoteDevice(device);
        for(Messenger target: mRemoteClients) {
            Message msg = obtainMessage(AceStreamManager.MSG_DEVICE_ON_PING_FAILED);
            Bundle data = new Bundle(1);
            data.putString(AceStreamManager.MSG_PARAM_REMOTE_DEVICE, devicePayload);
            msg.setData(data);
            sendMessage(target, msg);
        }
    }

    @SuppressWarnings("unused")
    public void notifyOutputFormatChanged(AceStreamRemoteDevice device, String outputFormat) {
        for(AceStreamRemoteDeviceListener listener: mRemoteDeviceListeners) {
            listener.onOutputFormatChanged(device, outputFormat);
        }

        String devicePayload = serializeRemoteDevice(device);
        for(Messenger target: mRemoteClients) {
            Message msg = obtainMessage(AceStreamManager.MSG_DEVICE_ON_OUTPUT_FORMAT_CHANGED);
            Bundle data = new Bundle(1);
            data.putString(AceStreamManager.MSG_PARAM_REMOTE_DEVICE, devicePayload);
            data.putString(AceStreamManager.MSG_PARAM_OUTPUT_FORMAT, outputFormat);
            msg.setData(data);
            sendMessage(target, msg);
        }
    }

    public void addDeviceDiscoveryListener(DeviceDiscoveryListener listener) {
        mDeviceDiscoveryListeners.add(listener);
    }

    public void removeDeviceDiscoveryListener(DeviceDiscoveryListener listener) {
        mDeviceDiscoveryListeners.remove(listener);
    }

    private void notifyDeviceAdded(ConnectableDevice device) {
        for (DeviceDiscoveryListener listener : mDeviceDiscoveryListeners) {
            listener.onDeviceAdded(device);
        }

        String devicePayload = serializeRemoteDevice(device);
        for(Messenger target: mRemoteClients) {
            Message msg = obtainMessage(AceStreamManager.MSG_DEVICE_ADDED);
            Bundle data = new Bundle(1);
            data.putString(AceStreamManager.MSG_PARAM_REMOTE_DEVICE, devicePayload);
            msg.setData(data);
            sendMessage(target, msg);
        }
    }

    private void notifyDeviceAdded(AceStreamRemoteDevice device) {
        for(DeviceDiscoveryListener listener: mDeviceDiscoveryListeners) {
            listener.onDeviceAdded(device);
        }

        String devicePayload = serializeRemoteDevice(device);
        for(Messenger target: mRemoteClients) {
            Message msg = obtainMessage(AceStreamManager.MSG_DEVICE_ADDED);
            Bundle data = new Bundle(1);
            data.putString(AceStreamManager.MSG_PARAM_REMOTE_DEVICE, devicePayload);
            msg.setData(data);
            sendMessage(target, msg);
        }
    }

    private void notifyCurrentDeviceChanged(AceStreamRemoteDevice device) {
        for(DeviceDiscoveryListener listener: mDeviceDiscoveryListeners) {
            listener.onCurrentDeviceChanged(device);
        }

        String devicePayload = serializeRemoteDevice(device);
        for(Messenger target: mRemoteClients) {
            Message msg = obtainMessage(AceStreamManager.MSG_DEVICE_CHANGED);
            Bundle data = new Bundle(1);
            data.putString(AceStreamManager.MSG_PARAM_REMOTE_DEVICE, devicePayload);
            msg.setData(data);
            sendMessage(target, msg);
        }
    }

    private void notifyDeviceRemoved(ConnectableDevice device) {
        for (DeviceDiscoveryListener listener : mDeviceDiscoveryListeners) {
            listener.onDeviceRemoved(device);
        }

        String devicePayload = serializeRemoteDevice(device);
        for(Messenger target: mRemoteClients) {
            Message msg = obtainMessage(AceStreamManager.MSG_DEVICE_REMOVED);
            Bundle data = new Bundle(1);
            data.putString(AceStreamManager.MSG_PARAM_REMOTE_DEVICE, devicePayload);
            msg.setData(data);
            sendMessage(target, msg);
        }
    }

    private void notifyDeviceRemoved(AceStreamRemoteDevice device) {
        for (DeviceDiscoveryListener listener : mDeviceDiscoveryListeners) {
            listener.onDeviceRemoved(device);
        }

        String devicePayload = serializeRemoteDevice(device);
        for(Messenger target: mRemoteClients) {
            Message msg = obtainMessage(AceStreamManager.MSG_DEVICE_REMOVED);
            Bundle data = new Bundle(1);
            data.putString(AceStreamManager.MSG_PARAM_REMOTE_DEVICE, devicePayload);
            msg.setData(data);
            sendMessage(target, msg);
        }
    }

    public void addEngineSessionListener(EngineSessionListener listener) {
        mEngineSessionListeners.add(listener);
    }

    public void removeEngineSessionListener(EngineSessionListener listener) {
        mEngineSessionListeners.remove(listener);
    }

    private void notifyEngineSessionStarted(AceStreamRemoteDevice device) {
        for(EngineSessionListener listener: mEngineSessionListeners) {
            listener.onEngineSessionStarted();
        }

        for(Messenger target: mRemoteClients) {
            Message msg = obtainMessage(AceStreamManager.MSG_ENGINE_SESSION_STARTED);
            Bundle data = new Bundle(1);
            data.putString(AceStreamManager.MSG_PARAM_ENGINE_SESSION, EngineSession.toJson(mEngineSession));
            msg.setData(data);
            sendMessage(target, msg);
        }

        if(mRemoteClientId != null) {
            mDiscoveryServerServiceClient.sendClientMessage(
                    mRemoteClientId,
                    new JsonRpcMessage(AceStreamRemoteDevice.Messages.ENGINE_SESSION_STARTED));
        }

        mEngineStatusHandler.sessionStarted(mEngineSession, device);
    }

    private void notifyEngineSessionStopped() {
        for(EngineSessionListener listener: mEngineSessionListeners) {
            listener.onEngineSessionStopped();
        }

        for(Messenger target: mRemoteClients) {
            sendMessage(target, obtainMessage(AceStreamManager.MSG_ENGINE_SESSION_STOPPED));
        }

        if(mRemoteClientId != null) {
            mDiscoveryServerServiceClient.sendClientMessage(
                    mRemoteClientId,
                    new JsonRpcMessage(AceStreamRemoteDevice.Messages.ENGINE_SESSION_STOPPED));
        }

        mEngineStatusHandler.sessionStopped();
    }

    public boolean canStopDeviceDiscovery() {
        for (DeviceDiscoveryListener listener : mDeviceDiscoveryListeners) {
            if(!listener.canStopDiscovery()) {
                return false;
            }
        }

        return true;
    }

    public boolean isDeviceConnected() {
        if(mCurrentDevice != null) {
            return true;
        }
        else if(mCurrentRemoteDevice != null && mCurrentRemoteDevice.isConnected()) {
            return true;
        }

        return false;
    }

    public boolean isEngineSessionStarted() {
        return mEngineSession != null;
    }

    public boolean hasRestorableEngineSession() {
        return mCurrentPlaylist != null;
    }

    public void setOurPlayerActive(boolean value) {
        mIsOurPlayerActive = value;
    }

    public boolean shouldShowRemoteControl() {
        return hasRestorableEngineSession()
                && !AceStream.isAndroidTv()
                && !mIsOurPlayerActive;
    }

    public EngineSession getEngineSession() {
        return mEngineSession;
    }

    public String getCurrentInfohash() {
        return mEngineSession == null ? null : mEngineSession.infohash;
    }

    public static EngineStatus parseEngineStatus(String jsonString) throws EngineSessionStoppedException {
        return parseEngineStatus(jsonString, false);
    }

    public static EngineStatus parseEngineStatus(String jsonString, boolean throwRawError) throws EngineSessionStoppedException {
        try {
            //Log.d(TAG, "parseEngineStatus: jsonString=" + jsonString);

            if(jsonString == null) {
                return null;
            }

            JSONObject root = new JSONObject(jsonString);
            if(!root.isNull("error")) {
                String error = root.getString("error");
                if(throwRawError) {
                    throw new EngineSessionStoppedException(error);
                }
                else {
                    if (error.equals("unknown playback session id")) {
                        Log.d(TAG, "parseEngineStatus: session stopped");
                        throw new EngineSessionStoppedException();
                    } else {
                        return null;
                    }
                }
            }

            JSONObject response = root.optJSONObject("response");
            if(response == null) {
                Log.d(TAG, "parseEngineStatus: missing response");
                return null;
            }

            String status = response.optString("status", null);
            if(status == null) {
                return null;
            }

            EngineStatus engineStatus = new EngineStatus();

            engineStatus.status = status;
            engineStatus.playbackSessionId = response.optString("playback_session_id", null);
            engineStatus.progress = response.optInt("progress", 0);
            engineStatus.peers = response.optInt("peers", 0);
            engineStatus.speedDown = response.optInt("speed_down", 0);
            engineStatus.speedUp = response.optInt("speed_up", 0);
            engineStatus.errorMessage = response.optString("error_message");
            engineStatus.currentStreamIndex = response.optInt("selected_stream_index", 0);
            engineStatus.isLive = response.optInt("is_live", -1);

            // livepos
            JSONObject livepos = response.optJSONObject("livepos");
            if(livepos == null) {
                engineStatus.livePos = null;
            }
            else {
                engineStatus.livePos = new EngineStatus.LivePosition();
                engineStatus.livePos.first = livepos.optInt("live_first", -1);
                engineStatus.livePos.last = livepos.optInt("live_last", -1);
                engineStatus.livePos.firstTimestamp = livepos.optInt("first_ts", -1);
                engineStatus.livePos.lastTimestamp = livepos.optInt("last_ts", -1);
                engineStatus.livePos.pos = livepos.optInt("pos", -1);
                engineStatus.livePos.isLive = (1 == livepos.optInt("is_live", 0));
                engineStatus.livePos.bufferPieces = livepos.optInt("buffer_pieces", -1);
            }

            JSONArray streams = null;
            int streamType = ContentStream.StreamType.UNKNOWN;

            if(response.has("hls_streams")) {
                streams = response.getJSONArray("hls_streams");
                streamType = ContentStream.StreamType.HLS;
            }
            else if(response.has("video_streams")) {
                streams = response.getJSONArray("video_streams");
                streamType = ContentStream.StreamType.DIRECT;
            }

            if(streams != null) {
                for(int i = 0; i < streams.length(); i++) {
                    JSONObject jsonStream = streams.getJSONObject(i);
                    ContentStream stream = new ContentStream();
                    stream.streamType = streamType;
                    stream.index = i;
                    stream.name = jsonStream.optString("name", null);
                    stream.quality = jsonStream.optInt("quality", 0);
                    stream.bitrate = jsonStream.optInt("bitrate", 0);
                    stream.bandwidth = jsonStream.optInt("bandwidth", 0);
                    stream.codecs = jsonStream.optString("codecs", null);
                    stream.resolution = jsonStream.optString("resolution", null);

                    if(jsonStream.has("type")) {
                        switch(jsonStream.getString("type")) {
                            case "audio":
                                stream.contentType = ContentStream.ContentType.AUDIO;
                                break;
                            case "video":
                                stream.contentType = ContentStream.ContentType.VIDEO;
                                break;
                        }
                    }

                    engineStatus.streams.add(stream);
                }
            }

            if(AceStream.isTestMode()) {
                JSONObject internalSession = response.getJSONObject("internal_session");
                engineStatus.initiatorType = internalSession.optInt("initiator_type", -1);
                engineStatus.initiatorId = internalSession.optString("initiator_id");
                engineStatus.contentKey = internalSession.optString("content_key");
                engineStatus.isOurPlayer = internalSession.optInt("is_our_player", -1);
            }

            return engineStatus;
        }
        catch(JSONException e) {
            Log.e(TAG, "parseEngineStatus: failed to parse response", e);
            return null;
        }
    }

    public void castToDevice(final ConnectableDevice device, MediaInfo mediaInfo, CastResultListener listener) {
        castToDevice(device, mediaInfo, false, 0, listener);
    }

    public void castToDevice(
            final ConnectableDevice device,
            MediaInfo mediaInfo,
            boolean restartFromLastPosition,
            long startFrom,
            CastResultListener listener) {
        if(mCastResultListener != null && mCastResultListener != listener) {
            // cancel prev listener
            Log.d(TAG, "castToDevice: cancel prev listener: prev=" + mCastResultListener + " new=" + listener);
            mCastResultListener.onCancel();
        }
        mRestartFromLastPosition = restartFromLastPosition;
        mStartFrom = startFrom;
        mCastResultListener = listener;
        mMediaInfo = mediaInfo;

        if(device == null) {
            if(listener != null) {
                listener.onError("null device");
            }
            return;
        }

        if(mEngineSession == null && mediaInfo == null) {
            if(listener != null) {
                listener.onError("missing engine session");
            }
            return;
        }

        if(mCurrentDevice != null && !mCurrentDevice.getId().equals(device.getId())) {
            Log.d(TAG, "castToDevice: disconnect prev device: new=" + device.getId() + " prev=" + mCurrentDevice.getId());
            disconnectDevice();
        }

        if(device.isConnected()) {
            Log.d(TAG, "castToDevice: already connected to the device: name=" + device.getFriendlyName());
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    // force reconnecting
                    mWaitReconnect = false;
                    onDeviceReady(device);
                }
            });
        }
        else {
            Log.d(TAG, "castToDevice: connect to device and wait: name=" + device.getFriendlyName());

            device.addListener(this);
            device.connect();

            // check device state after some timeout
            final CastResultListener fCastResultListener = mCastResultListener;
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (fCastResultListener != null && !device.isConnected() && fCastResultListener.isWaiting()) {
                        Log.d(TAG, "castToDevice: check device state, notify listener: name=" + device.getFriendlyName() + " connected=" + device.isConnected());
                        fCastResultListener.onError(getString(R.string.please_try_again));
                        fCastResultListener.onDeviceDisconnected(device);
                    } else {
                        Log.d(TAG, "castToDevice: check device state, no listener: name=" + device.getFriendlyName() + " connected=" + device.isConnected());
                    }
                }
            }, Constants.REMOTE_DEVICE_CONNECT_TIMEOUT * 1000);
        }
    }

    public void stopRemotePlayback(boolean disconnectDevice) {
        Log.d(TAG, "stopRemotePlayback: disconnectDevice=" + disconnectDevice);
        mHandler.removeCallbacks(updatePlaybackStatusTask);
        mLastPlayState = null;
        if(mMediaControl != null) {
            mMediaControl.stop(null);
        }
        if(mCurrentRemoteDevice != null) {
            if(disconnectDevice) {
                mCurrentRemoteDevice.stop(true);

                // remote device will not send "engineSessionStopped" event because socket is closed
                notifyEngineSessionStopped();
            }
            else {
                mCurrentRemoteDevice.stop();
            }
        }
        if(disconnectDevice) {
            disconnectDevice();
        }
    }

    public void disconnectDevice() {
        Log.d(TAG, "disconnectDevice");
        mWaitReconnect = false;

        if(mCurrentDevice != null) {
            setCurrentDevice(null, false);
        }

        if(mCurrentRemoteDevice != null) {
            setCurrentRemoteDevice(null, false, false);
        }
    }

    public void setEngineSession(EngineSession session) {
        Log.d(TAG, "setEngineSession: session=" + session.toString());
        synchronized (mEngineSessionLock) {
            // reset flag
            mPlayerStarted = false;

            // remember session
            mEngineSession = session;
            mEngineSession.startedAt = System.currentTimeMillis();

            // start updating session status
            mHandler.removeCallbacks(updateEngineStatusTask);
            mHandler.postDelayed(updateEngineStatusTask, 0);
        }
        notifyEngineSessionStarted(null);
        if(mWakeLock != null) {
            mWakeLock.acquire(600000);
        }
    }

    public void setPreferences(@Nullable Bundle preferences) {
        if(preferences == null) return;
        for(String key: preferences.keySet()) {
            setPreference(key, preferences.get(key));
        }
    }

    private void updateRemoteEngineStatusListeners(int count) {
        Logger.v(TAG, "updateRemoteEngineStatusListeners: count=" + count);
        mRemoteEngineStatusListeners = count;
    }

    public void setPreference(String name, Object value) {
        boolean updatePrefs = true;
        boolean sendToEngine = AceStreamPreferences.ENGINE_PREFS.contains(name);

        // Remove "acestream_" prefix
        if(name.startsWith("acestream_")) {
            name = name.substring(10);
        }

        if(AceStreamPreferences.INTEGER_PREFS.contains(name)) {
            value = MiscUtils.getIntegerValue((String)value);
        }

        if(name.equals("mobile_network_available")) {
            Intent intent = new Intent();
            intent.setAction(AceStreamEngineService.ACTION_CONNECTION_AVAILABILITY_CHANGED);
            intent.putExtra("new_mobile_network_available", (boolean)value);
            AceStreamEngineBaseApplication.context().sendBroadcast(intent);
        }
        else if(name.equals("language")) {
            AceStreamEngineBaseApplication.setLocale((String)value);
        }
        else if ("disk_cache_limit".equals(name)) {
            try {
                long valMb = Long.parseLong((String)value);
                if (valMb < 100) {
                    // modify value
                    value = String.valueOf(100);
                }
            }
            catch(NumberFormatException e) {
                Log.e(TAG, "setPreference: failed to parse disk cache limit", e);
                return;
            }
        }
        else if ("memory_cache_limit".equals(name)) {
            try {
                long valMb = Long.parseLong((String)value);
                if (valMb < 25) {
                    // modify value
                    value = String.valueOf(25);
                }
            }
            catch(NumberFormatException e) {
                Log.e(TAG, "setPreference: failed to parse memory cache limit", e);
                return;
            }
        }
        else if("start_acecast_server_on_boot".equals(name)) {
            boolean startServer = (boolean)value;
            if(startServer) {
                Log.d(TAG, "start acecast server");
                AceStreamDiscoveryServerService.Client.startService(AceStreamEngineBaseApplication.context());
            }
            else {
                Log.d(TAG, "stop acecast server");
                AceStreamDiscoveryServerService.Client.stopService(AceStreamEngineBaseApplication.context());
            }
        }
        else if("enable_debug_logging".equals(name)) {
            LogcatOutputStreamWriter.getInstanse().restart();
            Logger.enableDebugLogging((boolean)value);
        }
        else if (TextUtils.equals(name, PREF_KEY_SELECTED_PLAYER)) {
            SelectedPlayer player;
            try {
                player = SelectedPlayer.fromJson((String)value);
            }
            catch(JSONException e) {
                player = null;
            }
            AceStreamEngineBaseApplication.saveSelectedPlayer(player, true);
            updatePrefs = false;
        }

        if(sendToEngine) {
            String engineValue;
            if ("live_cache_type".equals(name)) {
                engineValue = (boolean)value ? "disk" : "memory";
            }
            else if ("vod_cache_type".equals(name)) {
                engineValue = (boolean)value ? "disk" : "memory";
            }
            else if (value instanceof Boolean) {
                engineValue = (boolean)value ? "1" : "0";
            }
            else {
                engineValue = (String)value;
            }

            if ("disk_cache_limit".equals(name) || "memory_cache_limit".equals(name)) {
                try {
                    // convert to bytes
                    long lValMb = Long.parseLong(engineValue);
                    long lVal = lValMb * 1024 * 1024;
                    engineValue = String.valueOf(lVal);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "setPreference: failed to parse cache size", e);
                    return;
                }
            }

            if(mEngineApi != null) {
                mEngineApi.setPreference(name, engineValue, null);
            }
            else {
                Log.w(TAG, "setPreference: missing engine api");
            }
        }

        if(updatePrefs) {
            // save to shared preferences
            SharedPreferences.Editor edit = AceStreamEngineBaseApplication.getPreferences().edit();

            if (value instanceof Boolean) {
                edit.putBoolean(name, (boolean) value);
            } else {
                edit.putString(name, (String) value);
            }
            edit.apply();
        }

        // update cached prefs
        if(mAppPreferences != null) {
            mAppPreferences.put(name, value);
        }

        // These are checked after update
        if(TextUtils.equals(name, Constants.PREF_KEY_SHOW_ADS_ON_MAIN_SCREEN)) {
            notifyBonusAdsAvailable(true);
        }
    }

    public void clearCache() {
        if(mEngineApi != null) {
            AceStream.toast(R.string.clearing_cache_etc);
            mEngineApi.clearCache(new org.acestream.engine.controller.Callback<Boolean>() {
                @Override
                public void onSuccess(Boolean result) {
                    AceStream.toast(R.string.cache_cleared);
                }

                @Override
                public void onError(String err) {
                    // Currently clearEngineCache() method always produces an error because response from
                    // engine is not a valid JSON-RPC response.
                    // So assume that request has always succeeded.
                    AceStream.toast(R.string.cache_cleared);
                }
            });
        }
        else {
            AceStream.toast(R.string.not_connected_to_engine);
        }
    }

    public void stopEngine() {
        Log.v(TAG, "stopEngine: engineApi=" + mEngineApi);
        if(mEngineApi != null) {
            setEngineServiceStopFlag();
            mEngineApi.shutdown(new org.acestream.engine.controller.Callback<Boolean>() {
                @Override
                public void onSuccess(Boolean result) {
                    processShutdown(false);
                }

                @Override
                public void onError(String err) {
                    processShutdown(false);
                }
            });
        }
        else {
            processShutdown(true);
        }
    }

    private void processShutdown(boolean stopNow) {
        Log.d(TAG, "processShutdown: stopNow=" + stopNow);

        if(stopNow) {
            stopEngineService();
        }
        shutdown();
    }

    public void stopEngineSession(boolean sendStopCommand) {
        Log.d(TAG, "stopEngineSession: sendStopCommand=" + sendStopCommand);

        final boolean notifyStopped;
        synchronized (mEngineSessionLock) {
            if (mEngineSession != null) {
                long sessionDuration = System.currentTimeMillis() - mEngineSession.startedAt;
                updateTotalEngineSessionDuration(sessionDuration);

                if(sendStopCommand && mEngineApi != null) {
                    mEngineApi.stopSession(mEngineSession, null);
                }

                mEngineSession = null;
                mHandler.removeCallbacks(updateEngineStatusTask);
                notifyStopped = true;
            }
            else {
                notifyStopped = false;
            }
        }

        if(notifyStopped) {
            notifyEngineSessionStopped();
        }

        try {
            if (mWakeLock.isHeld()) {
                mWakeLock.release();
            }
        }
        catch(Throwable e) {
            Log.e(TAG, "failed to release wake lock", e);
        }

        if(mCurrentDevice != null) {
            long age = getDeviceSeenTimeout();
            if(age >= 15000) {
                Log.d(TAG, "stopEngineSession: disconnect air device: age=" + age);
                // stop current air device
                disconnectDevice();
            }
        }

        // disable upload when engine session stopped
        //disableP2PUpload();
    }

    public void setPlayerActivityTimeout(int timeout) {
        synchronized (mEngineSessionLock) {
            if(mEngineSession != null && mEngineSession.commandUrl != null && mHttpAsyncTaskFactory != null) {
                String url = mEngineSession.commandUrl + "?method=set_player_activity_timeout&timeout=" + timeout;
                mHttpAsyncTaskFactory
                        .build(HttpAsyncTask.HTTPTASK_STOP_DOWNLOAD, null, url)
                        .execute2("GET");
            }
        }
    }

    public void liveSeek(int position) {
        Log.d(TAG, "liveSeek: position=" + position);

        if(mCurrentRemoteDevice != null) {
            mCurrentRemoteDevice.liveSeek(position);
        }
        else {
            synchronized (mEngineSessionLock) {
                if (mEngineSession != null) {
                    if (mEngineSession.commandUrl != null && mHttpAsyncTaskFactory != null) {
                        String url = mEngineSession.commandUrl + "?method=liveseek&pos=" + position;
                        mHttpAsyncTaskFactory.build(HttpAsyncTask.HTTPTASK_LIVE_SEEK, null, url).execute2("GET");
                    }
                }
            }
        }
    }

    private Pair<String, String> getMediaUrlForDevice() {
        if(mMediaInfo != null) {
            // got direct content url
            return new Pair<>(mMediaInfo.getMimeType(), mMediaInfo.getUrl());
        }

        if(mEngineSession == null) {
            Log.e(TAG, "getMediaUrlForDevice: missing engine session");
            return null;
        }

        String myIp = MiscUtils.getMyIp(this);
        if(myIp == null) {
            Log.d(TAG, "getMediaUrlForDevice: failed to get my ip");
            return null;
        }

        PlaylistItem item = null;
        if(mCurrentPlaylist != null) {
            item = mCurrentPlaylist.getCurrentItem();
        }

        String url;
        String mime;
        if(mEngineSession.playbackData.outputFormat.format.equals("hls")) {
            mime = Constants.MIME_HLS;
        }
        else {
            mime = item == null ? "video/mp4" : item.getMimeType();
        }
        url = mEngineSession.playbackUrl.replace("127.0.0.1", myIp);
        return new Pair<>(mime, url);
    }

    /**
     * Set HLS stream for current engine session.
     * @param streamIndex new string index
     */
    public void setHlsStream(int streamIndex) {
        if(mEngineSession == null) {
            Log.d(TAG, "setHlsStream: missing engine session");
            return;
        }

        if(mEngineSession.commandUrl == null) {
            Log.d(TAG, "setHlsStream: missing command url");
            return;
        }

        if(mHttpAsyncTaskFactory == null) {
            Log.d(TAG, "setHlsStream: missing http async task factory");
            return;
        }

        String url = mEngineSession.commandUrl + "?method=set_stream&stream_index=" + streamIndex;
        mHttpAsyncTaskFactory.build(HttpAsyncTask.HTTPTASK_SET_STREAM, null, url).execute2("GET");
    }

    // AceStreamRemoteDeviceListener interface
    @Override
    public void onConnected(AceStreamRemoteDevice device) {
        notifyRemoteDeviceConnected(device);
    }

    @Override
    public void onDisconnected(AceStreamRemoteDevice device, boolean cleanShutdown) {
        notifyRemoteDeviceDisconnected(device, cleanShutdown);
    }

    @Override
    public void onMessage(AceStreamRemoteDevice device, JsonRpcMessage msg) {
        if(!isCurrentDevice(device)) {
            return;
        }

        try {
            switch (msg.getMethod()) {
                case AceStreamRemoteDevice.Messages.PLAYBACK_STARTED:
                    SelectedPlayer selectedPlayer = SelectedPlayer.fromId(msg.getString("selectedPlayer"));
                    Log.d(TAG, "playback started: device=" + device.toString() + " selectedPlayer=" + selectedPlayer);
                    if(mCastResultListener != null) {
                        mCastResultListener.onSuccess(device, selectedPlayer);
                    }
                    break;
                case AceStreamRemoteDevice.Messages.PLAYBACK_START_FAILED:
                    String error = msg.getString("error");
                    Log.d(TAG, "playback failed: error=" + error + " device=" + device.toString());
                    if(TextUtils.isEmpty(error)) {
                        error = "Start failed";
                    }
                    if(mCastResultListener != null) {
                        mCastResultListener.onError(error);
                    }
                    break;
                case AceStreamRemoteDevice.Messages.PLAYER_END_REACHED: {
                    //TODO: switch to the next item in the playlist if available?
                    Log.d(TAG, "onMessage:playerEndReached: stop remote engine session");
                    device.stopEngineSession();
                    break;
                }
                case AceStreamRemoteDevice.Messages.PLAYER_STATUS:
                    Long currentTime = msg.getLong("time");
                    Long duration = msg.getLong("duration");

                    savePlaybackPosition(currentTime);
                    savePlaybackDuration(duration);

                    PlaylistItem playlistItem = null;
                    if(mCurrentPlaylist != null) {
                        playlistItem = mCurrentPlaylist.getCurrentItem();
                    }

                    if(playlistItem != null) {
                        JSONArray jsonAudioTracks = msg.getJSONArray("audioTracks");
                        if (jsonAudioTracks != null) {
                            playlistItem.currentAudioTrack = msg.getInt("selectedAudioTrack", -1);
                            if(jsonAudioTracks.length() != playlistItem.getAudioTracksCount()) {
                                playlistItem.clearAudioTracks();
                                for (int i = 0; i < jsonAudioTracks.length(); i++) {
                                    JSONObject jsonTrack = jsonAudioTracks.getJSONObject(i);
                                    TrackDescription track = new TrackDescription(
                                            jsonTrack.getInt("id"),
                                            jsonTrack.getString("name"));
                                    playlistItem.addAudioTrack(track);
                                }
                            }
                        }

                        JSONArray jsonSubtitleTracks = msg.getJSONArray("subtitleTracks");
                        if (jsonSubtitleTracks != null) {
                            playlistItem.currentSubtitleTrack = msg.getInt("selectedSubtitleTrack", -1);
                            if(jsonSubtitleTracks.length() != playlistItem.getSubtitleTracksCount()) {
                                playlistItem.clearSubtitleTracks();
                                for (int i = 0; i < jsonSubtitleTracks.length(); i++) {
                                    JSONObject jsonTrack = jsonSubtitleTracks.getJSONObject(i);
                                    TrackDescription track = new TrackDescription(
                                            jsonTrack.getInt("id"),
                                            jsonTrack.getString("name"));
                                    playlistItem.addSubtitleTrack(track);
                                }
                            }
                        }
                    }
                    break;
                case AceStreamRemoteDevice.Messages.ENGINE_SESSION_STARTED:
                    notifyEngineSessionStarted(device);
                    break;
                case AceStreamRemoteDevice.Messages.ENGINE_SESSION_STOPPED:
                    notifyEngineSessionStopped();
                    break;
                case AceStreamRemoteDevice.Messages.ENGINE_STATUS:
                    EngineStatus status = EngineStatus.fromJson(msg.getString("status"));
                    if(status != null) {
                        status.selectedPlayer = SelectedPlayer.fromId(msg.getString("selectedPlayer"));
                        status.outputFormat = msg.getString("outputFormat");
                        status.fileIndex = msg.getInt("fileIndex", -1);
                        String systemUsageInfo = msg.getString("system_usage");
                        if (systemUsageInfo != null) {
                            status.systemInfo = SystemUsageInfo.fromJson(systemUsageInfo);
                        }
                        notifyEngineStatus(status, device);

                        if(mCurrentPlaylist != null && status.fileIndex != -1) {
                            PlaylistItem item = mCurrentPlaylist.getCurrentItem();
                            if(item != null && item.getFileIndex() != status.fileIndex) {
                                Log.v(TAG, "remote file index changed: " + item.getFileIndex() + "->" + status.fileIndex);
                                mCurrentPlaylist.setCurrentByFileIndex(status.fileIndex);
                            }
                        }
                    }
                    break;
            }
        }
        catch(Throwable e) {
            Log.e(TAG, "onMessage: error", e);
        }

        notifyRemoteDeviceMessage(device, msg);
    }

    @Override
    public void onAvailable(AceStreamRemoteDevice device) {
    }

    @Override
    public void onUnavailable(AceStreamRemoteDevice device) {
    }

    @Override
    public void onPingFailed(AceStreamRemoteDevice device) {

    }

    @Override
    public void onOutputFormatChanged(AceStreamRemoteDevice device, String outputFormat) {
    }

    @Override
    public void onSelectedPlayerChanged(AceStreamRemoteDevice device, SelectedPlayer player) {
    }

    public interface CastResultListener {
        void onSuccess();
        void onSuccess(AceStreamRemoteDevice device, SelectedPlayer selectedPlayer);
        void onError(String error);
        void onDeviceConnected(AceStreamRemoteDevice device);
        void onDeviceConnected(ConnectableDevice device);
        void onDeviceDisconnected(AceStreamRemoteDevice device);
        void onDeviceDisconnected(ConnectableDevice device);
        void onCancel();
        boolean isWaiting();
    }

    public MediaInfo getDirectMediaInfo() {
        return mMediaInfo;
    }

    public void initEngineSession(PlaybackData playbackData, EngineSessionStartListener listener) {
        Log.d(TAG, "initEngineSession: descriptor=" + playbackData.descriptor.toString() +
                " output=" + playbackData.outputFormat +
                " mime=" + playbackData.mediaFile.mime +
                " index=" + playbackData.mediaFile.index +
                " next=" + MiscUtils.dump(playbackData.nextFileIndexes) +
                " directMediaUrl=" + playbackData.directMediaUrl +
                " streamIndex=" + playbackData.streamIndex +
                " disableP2P=" + playbackData.disableP2P
        );

        if(mHttpAsyncTaskFactory == null) {
            Log.e(TAG, "initEngineSession: missing http async task factory");
            return;
        }

        String requestUrl;
        String playerId;

        if(playbackData.useFixedSid) {
            // Use fixed player id when working as remote client.
            // We need to always stop prev session when new is started.
            playerId = Constants.ACESTREAM_PLAYER_SID;
        }
        else {
            playerId = AceStream.getEnginePlayerId();
        }

        if(!playbackData.disableP2P && playbackData.outputFormat.format.equals("hls")) {
            requestUrl = String.format(
                    "/ace/manifest.m3u8?format=json&hlc=0&sid=%s&transcode_audio=%d&transcode_mp3=%d&transcode_ac3=%d&_idx=%d&stream_id=%d&%s",
                    Uri.encode(playerId),
                    playbackData.outputFormat.transcodeAudio ? 1 : 0,
                    playbackData.outputFormat.transcodeMP3 ? 1 : 0,
                    playbackData.outputFormat.transcodeAC3 ? 1 : 0,
                    playbackData.mediaFile.index,
                    playbackData.streamIndex,
                    playbackData.descriptor.getQueryString());
            // override mime type in case if differs
            playbackData.mediaFile.mime = Constants.MIME_HLS;
        }
        else {
            requestUrl = String.format(
                    "/ace/getstream?format=json&sid=%s&_idx=%d&stream_id=%d&%s",
                    Uri.encode(playerId),
                    playbackData.mediaFile.index,
                    playbackData.streamIndex,
                    playbackData.descriptor.getQueryString());
        }

        requestUrl += "&use_timeshift=" + (playbackData.useTimeshift ? 1 : 0);
        requestUrl += "&manifest_p2p_wait_timeout=10";
        requestUrl += "&proxy_vast_response=1";
        requestUrl += "&force_ads=" + (AceStreamEngineBaseApplication.showAdsOnPreroll() ? 1 : 0);
        String productKey = playbackData.productKey;
        if(TextUtils.isEmpty(productKey)) {
            productKey = AceStream.getHttpApiProductKey();
        }
        if(!TextUtils.isEmpty(productKey)) {
            if(playbackData.keepOriginalSessionInitiator) {
                requestUrl += "&secondary_product_key=" + productKey;
                requestUrl += "&is_restarted_session=1";
            }
            else {
                requestUrl += "&product_key=" + productKey;
            }
        }
        requestUrl += "&gdpr_consent=" + (AceStreamEngineBaseApplication.getGdprConsent() ? 1 : 0);

        if(playbackData.allowMultipleThreadsReading != -1) {
            requestUrl += "&allow_multiple_threads_reading=" + playbackData.allowMultipleThreadsReading;
        }
        if(playbackData.stopPrevReadThread != -1) {
            requestUrl += "&stop_prev_read_thread=" + playbackData.stopPrevReadThread;
        }
        if(playbackData.disableP2P) {
            requestUrl += "&disable_p2p=1";
        }

        if(playbackData.nextFileIndexes != null && playbackData.nextFileIndexes.length > 0) {
            StringBuilder sb = new StringBuilder(50);
            sb.append(playbackData.nextFileIndexes[0]);
            for(int i = 1; i < playbackData.nextFileIndexes.length; i++) {
                sb.append(",").append(playbackData.nextFileIndexes[i]);
            }
            requestUrl += "&next_file_indexes=" + sb.toString();
        }

        Map<String, Object> extraData = new HashMap<>();
        extraData.put("playbackData", playbackData);
        extraData.put("sessionStartListener", listener);

        if(playbackData.descriptor.shouldPost()) {
            Log.d(TAG, "initEngineSession:post: mime=" + playbackData.mediaFile.mime + " output=" + playbackData.outputFormat + " url=" + requestUrl);

            DataWithMime payload = playbackData.descriptor.getPostPayload();
            mHttpAsyncTaskFactory.build(HttpAsyncTask.HTTPTASK_START_CONTENT, this, requestUrl, extraData)
                    .execute2("POST", payload.getData(), payload.getMime());
        }
        else {
            requestUrl += "&" + playbackData.descriptor.getQueryString();
            Log.d(TAG, "initEngineSession:get: mime=" + playbackData.mediaFile.mime + " output=" + playbackData.outputFormat + " url=" + requestUrl);
            mHttpAsyncTaskFactory.build(HttpAsyncTask.HTTPTASK_START_CONTENT, this, requestUrl, extraData)
                    .execute2("GET");
        }
    }

    private void finishedStartingEngineSession(String responseText, Map<String, Object> extraData) {
        EngineSessionStartListener listener = (EngineSessionStartListener)extraData.get("sessionStartListener");
        try {
            PlaybackData playbackData = (PlaybackData)extraData.get("playbackData");
            Log.d(TAG, "finishedStartingContent: response=" + responseText +
                    " mime=" + playbackData.mediaFile.mime +
                    " outputFormat=" + playbackData.outputFormat);

            JSONObject root = new JSONObject(responseText);
            if(!root.isNull("error")) {
                String errmsg = root.optString("error");
                Log.d(TAG, "finishedStartingContent: got error: " + errmsg);
                if(listener != null) {
                    listener.onError(errmsg);
                }
                return;
            }

            JSONObject response = root.optJSONObject("response");
            if(response == null) {
                Log.d(TAG, "finishedStartingContent: missing response");
                if(listener != null) {
                    listener.onError(getString(R.string.failed_to_start));
                }
                return;
            }

            String playbackUrl = response.optString("playback_url", null);
            if(playbackUrl == null) {
                Log.d(TAG, "finishedStartingContent: missing playback url");
                if(listener != null) {
                    listener.onError(getString(R.string.failed_to_start));
                }
                return;
            }

            boolean isDirect = response.optBoolean("is_direct", false);
            int isLive = response.optInt("is_live", -1);
            int isEncrypted = response.optInt("is_encrypted", -1);

            String statUrl = response.optString("stat_url", null);
            if(!isDirect && statUrl == null) {
                Log.d(TAG, "finishedStartingContent: missing stat url");
                if(listener != null) {
                    listener.onError(getString(R.string.failed_to_start));
                }
                return;
            }

            String commandUrl = response.optString("command_url", null);
            if(!isDirect && commandUrl == null) {
                Log.d(TAG, "finishedStartingContent: missing command url");
                if(listener != null) {
                    listener.onError(getString(R.string.failed_to_start));
                }
                return;
            }

            Log.d(TAG, "finishedStartingContent: output=" + playbackData.outputFormat.format + " isLive=" + isLive + " isEncrypted=" + isEncrypted);

            if(isEncrypted == 1 && playbackData.outputFormat.format.equals("http")) {
                // restart in HLS
                Log.d(TAG, "finishedStartingContent: restart in HLS");
                playbackData.outputFormat.format = "hls";
                initEngineSession(playbackData, listener);
                return;
            }

            String vastTagsString = response.optString("vast_tags");
            VastTag[] vastTags = VastTags.fromJson(vastTagsString);

            String eventUrl = response.optString("event_url", null);

            EngineSession engineSession = new EngineSession();
            engineSession.playbackData = playbackData;
            engineSession.infohash = response.optString("infohash");
            engineSession.playbackSessionId = response.optString("playback_session_id", null);
            engineSession.playbackUrl = playbackUrl;
            engineSession.vastTags = vastTags;
            engineSession.isLive = isLive;
            if(statUrl != null) {
                engineSession.statUrl = Uri.parse(statUrl).getPath();
            }
            if(commandUrl != null) {
                engineSession.commandUrl = Uri.parse(commandUrl).getPath();
            }
            engineSession.isDirect = isDirect;
            if(eventUrl != null) {
                engineSession.eventUrl = Uri.parse(eventUrl).getPath();
            }

            setEngineSession(engineSession);

            if(listener != null) {
                listener.onSuccess(engineSession);
            }

            onEngineSessionStarted(engineSession);
        }
        catch(JSONException e) {
            Log.e(TAG, "finishedStartingContent: failed to parse response", e);
            if(listener != null) {
                listener.onError(getString(R.string.failed_to_start));
            }
        }
        catch(Exception e) {
            Log.e(TAG, "finishedStartingContent: unexpected error", e);
            if(listener != null) {
                listener.onError(getString(R.string.failed_to_start));
            }
        }
    }

    protected void onEngineSessionStarted(EngineSession session) {
    }

    private void updateTotalEngineSessionDuration(long duration) {
        if(duration <= 0) {
            return;
        }
        try {
            // convert to seconds
            duration = duration / 1000;

            SharedPreferences prefs = AceStreamEngineBaseApplication.getAppPreferences();
            SharedPreferences.Editor editor = prefs.edit();
            long currentDuration = prefs.getLong("total_engine_session_duration", 0);
            long newDuration = currentDuration + duration;
            editor.putLong("total_engine_session_duration", newDuration);
            editor.apply();
            Log.d(TAG, String.format("updateTotalEngineSessionDuration: %d->%d",
                    currentDuration,
                    newDuration));
        }
        catch(Throwable e) {
            Log.e(TAG, "Failed to forget player", e);
        }
    }

    @NonNull
    private String getCurrentServiceName() {
        if(mLaunchSession == null) {
            return "";
        }
        else {
            return mLaunchSession.getService().getServiceName();
        }
    }

    @NonNull
    private String getCurrentItemType() {
        String type = "";
        if(mCurrentPlaylist != null) {
            PlaylistItem item = mCurrentPlaylist.getCurrentItem();
            if(item != null) {
                type = item.getContentType();
            }
        }
        return type;
    }

    public void setLastSelectedDeviceId(String deviceId) {
        Log.d(TAG, "setLastSelectedDeviceId: device=" + deviceId);
        mLastSelectedDeviceId = deviceId;
    }

    public String getLastSelectedDeviceId() {
        return mLastSelectedDeviceId;
    }

    public ConnectableDevice getDeviceById(String deviceId) {
        ConnectableDevice foundDevice = null;
        if(mDiscoveryManager != null) {
            for (Map.Entry<String, ConnectableDevice> entry : mDiscoveryManager.getCompatibleDevices().entrySet()) {
                if (entry.getValue().isConnectable() && entry.getValue().getId().equals(deviceId)) {
                    foundDevice = entry.getValue();
                    break;
                }
            }
        }

        return foundDevice;
    }

    public long getDeviceSeenTimeout() {
        return System.currentTimeMillis() - mDeviceLastSeenAt;
    }

    public void setCurrentRemoteDevice(AceStreamRemoteDevice device, boolean stopPrevPlayback, boolean disconnectOtherDevices) {
        Log.d(TAG, "setCurrentRemoteDevice: stopPrevPlayback=" + stopPrevPlayback
            + " device=" + (device == null ? "null" : device.getInternalName())
            + " current=" + (mCurrentRemoteDevice == null ? "null" : mCurrentRemoteDevice.getId())
        );

        if(disconnectOtherDevices) {
            // disconnect from CSDK device if connected
            setCurrentDevice(null, false);
        }

        if(mCurrentRemoteDevice != null) {
            if(mCurrentRemoteDevice == device) {
                // the same device, nothing to do
                Log.v(TAG, "setCurrentRemoteDevice: already current, nothing to do");
                return;
            }

            if(stopPrevPlayback) {
                mCurrentRemoteDevice.stop(true);
            }
            mCurrentRemoteDevice.removeListener(this);
        }

        mCurrentRemoteDevice = device;

        if(mCurrentRemoteDevice != null) {
            mLastRemoteDeviceId = mCurrentRemoteDevice.getDeviceId();
            mCurrentRemoteDevice.addListener(this);
        }
        else {
            mLastRemoteDeviceId = null;
        }
    }

    private boolean isCurrentDevice(AceStreamRemoteDevice device) {
        return areDevicesSame(device, mCurrentRemoteDevice);
    }

    private boolean areDevicesSame(AceStreamRemoteDevice device1, AceStreamRemoteDevice device2) {
        if(device1 == null && device2 == null) {
            //Log.v(TAG, "areDevicesSame:: yes, both null");
            return true;
        }

        if(device1 == null) {
            //Log.v(TAG, "areDevicesSame:: no: current=" + device2.toString() + " device=null");
            return false;
        }

        if(device2 == null) {
            //Log.v(TAG, "areDevicesSame:: no: current=null device=" + device1.toString());
            return false;
        }

        if(device1.equals(device2)) {
            //Log.v(TAG, "areDevicesSame:: yes: current=" + device2.toString() + " device=" + device1.toString());
            return true;
        }
        else {
            //Log.v(TAG, "areDevicesSame:: no: current=" + device2.toString() + " device=" + device1.toString());
            return false;
        }
    }

    public AceStreamRemoteDevice getCurrentRemoteDevice() {
        return mCurrentRemoteDevice;
    }

    public void setCurrentRemoteClient(String clientId, String deviceId) {
        if(TextUtils.equals(clientId, mRemoteClientId)) {
            return;
        }

        Logger.v(TAG, "setCurrentRemoteClient: id=" + clientId + " current=" + mRemoteClientId);

        if(mRemoteClientId != null) {
            // remove listener from prev client
            mDiscoveryServerServiceClient.removeClientListener(mRemoteClientId, mDSSClientCallback);
        }

        mRemoteClientId = clientId;
        initRemoteClient(deviceId);
    }

    private void initRemoteClient(String deviceId) {
        if(mRemoteClientId != null) {
            mLastRemoteClientDeviceId = deviceId;
            mDiscoveryServerServiceClient.addClientListener(mRemoteClientId, mDSSClientCallback);
        }
    }

    private void startLocalPlayer() {
        if(mEngineSession == null) {
            Log.e(TAG, "startLocalPlayer: missing engine session");
            return;
        }

        SelectedPlayer selectedPlayer = mEngineSession.playbackData.selectedPlayer;
        if(selectedPlayer == null) {
            Log.e(TAG, "startLocalPlayer: missing selected player");
            return;
        }

        if(selectedPlayer.isOurPlayer()) {
            throw new IllegalStateException("external player expected");
        }

        Log.d(TAG, "startLocalPlayer: playbackUrl=" + mEngineSession.playbackUrl + " selectedPlayer=" + selectedPlayer + " remoteClientId=" + mRemoteClientId);

        // flag to avoid starting player activity again
        mPlayerStarted = true;
        setRemoteSelectedPlayer(selectedPlayer);

        if(mRemoteClientId != null) {
            JsonRpcMessage _msg = new JsonRpcMessage(AceStreamRemoteDevice.Messages.PLAYBACK_STARTED);
            _msg.addParam("selectedPlayer", selectedPlayer.getId());
            mDiscoveryServerServiceClient.sendClientMessage(mRemoteClientId, _msg);
        }

        Intent intent = new Intent();
        String packageName = selectedPlayer.id1;
        String className = selectedPlayer.id2;

        intent.setAction(Intent.ACTION_VIEW);
        intent.setDataAndTypeAndNormalize(Uri.parse(mEngineSession.playbackUrl), "video/*");
        intent.setComponent(new ComponentName(packageName, className));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        startActivity(intent);
    }

    @MainThread
    public void addCallback(Callback cb) {
        synchronized (mCallbacks) {
            if (!mCallbacks.contains(cb)) {
                mCallbacks.add(cb);
            }
        }
    }

    @MainThread
    public void removeCallback(Callback cb) {
        synchronized (mCallbacks) {
            mCallbacks.remove(cb);
        }
    }

    @MainThread
    public void addPlaybackStateCallback(PlaybackStateCallback cb) {
        synchronized (mPlaybackStateCallbacks) {
            if (!mPlaybackStateCallbacks.contains(cb)) {
                mPlaybackStateCallbacks.add(cb);
            }
        }
    }

    @MainThread
    public void removePlaybackStateCallback(PlaybackStateCallback cb) {
        synchronized (mPlaybackStateCallbacks) {
            mPlaybackStateCallbacks.remove(cb);
        }
    }

    @MainThread
    public void addEngineSettingsCallback(EngineSettingsCallback cb) {
        synchronized (mEngineSettingsCallbacks) {
            if (!mEngineSettingsCallbacks.contains(cb)) {
                mEngineSettingsCallbacks.add(cb);
            }
        }
    }

    @MainThread
    public void removeEngineSettingsCallback(EngineSettingsCallback cb) {
        synchronized (mEngineSettingsCallbacks) {
            mEngineSettingsCallbacks.remove(cb);
        }
    }

    protected void onEngineServiceConnected(IAceStreamEngine service) {
        mEngineApi = new ExtendedEngineApi(service);
        mHttpAsyncTaskFactory = new HttpAsyncTask.Factory(
                mEngineServiceClient.getHttpApiPort(),
                mEngineServiceClient.getAccessToken());
        notifyEngineConnected();
    }

    // ServiceClient.Callback interface
    @Override
    public void onConnected(IAceStreamEngine service) {
        Log.d(TAG, "onConnected: wasConnected=" + mEngineConnected);

        mEngineConnected = true;

        if(mEngineApi == null) {
            onEngineServiceConnected(service);

            mHandler.removeCallbacks(mInternalMaintainTask);
            mHandler.postDelayed(mInternalMaintainTask, 60000);
        }

        synchronized(mCallbacks) {
            for (Callback callback : mCallbacks) {
                callback.onEngineConnected(mEngineApi);
            }
        }
    }

    @Override
    public void onFailed() {
        Log.d(TAG, "onFailed");
        mEngineConnected = false;
        synchronized(mCallbacks) {
            for (Callback callback : mCallbacks) callback.onEngineFailed();
        }
    }

    @Override
    public void onDisconnected() {
        Log.d(TAG, "onDisconnected");
        mEngineConnected = false;
        synchronized(mCallbacks) {
            for (Callback callback : mCallbacks) callback.onEngineFailed();
        }
    }

    @Override
    public void onUnpacking() {
        synchronized(mCallbacks) {
            for (Callback callback : mCallbacks) callback.onEngineUnpacking();
        }
        Log.d(TAG, "onUnpacking");
    }

    @Override
    public void onStarting() {
        Log.d(TAG, "onStarting");
        synchronized(mCallbacks) {
            for (Callback callback : mCallbacks) callback.onEngineStarting();
        }
    }

    @Override
    public void onStopped() {
        Log.d(TAG, "onStopped");
        mEngineConnected = false;
        synchronized(mCallbacks) {
            for (Callback callback : mCallbacks) callback.onEngineStopped();
        }
        if(mEngineApi != null) {
            mEngineApi = null;
        }
        shutdown();
    }

    @Override
    public void onPlaylistUpdated() {
    }

    @Override
    public void onEPGUpdated() {
    }

    @Override
    public void onSettingsUpdated() {
    }

    @Override
    public void onRestartPlayer() {
        notifyRestartPlayer();
    }

    private void initServiceClient() throws ServiceClient.ServiceMissingException {
        if(mEngineServiceClient == null) {
            mEngineServiceClient = new ServiceClient("PlaybackManager", this, this, false);
            mEngineServiceClient.bind();
        }
    }

    public void startEngine() {
        Log.d(TAG, "startEngine");
        try {
            mEngineServiceClient.startEngine();
        }
        catch(ServiceClient.ServiceMissingException e) {
        }
    }

    public void enableAceCastServer() {
        Log.d(TAG, "enableAceCastServer");
        try {
            mEngineServiceClient.enableAceCastServer();
        }
        catch(ServiceClient.ServiceMissingException e) {
        }
    }

    public void stopEngineService() {
        Log.d(TAG, "stopEngineService");
        try {
            stopService(ServiceClient.getServiceIntent(this));
        }
        catch(ServiceClient.ServiceMissingException e) {
        }
    }

    public void setEngineServiceStopFlag() {
        Log.d(TAG, "setEngineServiceStopFlag");
        try {
            Intent intent = ServiceClient.getServiceIntent(this);
            intent.putExtra("setStopFlag", true);
            startService(intent);
        }
        catch(ServiceClient.ServiceMissingException e) {
        }
    }

    private void updateDiscoveryTimeout() {
        Log.v(TAG, "update discovery timeout");
        mHandler.removeCallbacks(mStopDiscoveryTask);
        mHandler.removeCallbacks(mPauseDiscoveryTask);
        mHandler.postDelayed(mPauseDiscoveryTask, DISCOVERY_PAUSE_TIMEOUT);
        mHandler.postDelayed(mStopDiscoveryTask, DISCOVERY_STOP_TIMEOUT);
    }

    protected void notifyEngineSettingsUpdated(@NonNull AceStreamPreferences preferences) {
        synchronized(mEngineSettingsCallbacks) {
            for (EngineSettingsCallback callback : mEngineSettingsCallbacks) {
                callback.onEngineSettingsUpdated(preferences);
            }
        }

        // Use non-extended version because remote part understands only it
        for(Messenger target: mRemoteClients) {
            Message msg = obtainMessage(AceStreamManager.MSG_ENGINE_SETTINGS_UPDATED);
            Bundle data = new Bundle(1);
            data.putBundle(AceStreamManager.MSG_PARAM_PREFERENCES, preferences.getAll());
            msg.setData(data);
            sendMessage(target, msg);
        }
    }

    protected void remoteNotifyEngineSettings(Messenger target, AceStreamPreferences preferences) {
        Message msg = obtainMessage(AceStreamManager.MSG_ENGINE_SETTINGS_UPDATED);
        Bundle data = new Bundle(1);
        data.putBundle(AceStreamManager.MSG_PARAM_PREFERENCES, preferences.getAll());
        msg.setData(data);
        sendMessage(target, msg);
    }

    private void notifyPlaybackStateStart(@Nullable EngineSession session) {
        for(PlaybackStateCallback cb: mPlaybackStateCallbacks) {
            cb.onStart(session);
        }

        String sessionPayload = (session == null) ? null : session.toJson();
        for(Messenger target: mRemoteClients) {
            Message msg = obtainMessage(AceStreamManager.MSG_PLAYBACK_STATE_START);
            Bundle data = new Bundle(1);
            data.putString(AceStreamManager.MSG_PARAM_ENGINE_SESSION, sessionPayload);
            msg.setData(data);
            sendMessage(target, msg);
        }
    }

    private void notifyPlaybackStatePrebuffering(@Nullable EngineSession session, int progress) {
        for(PlaybackStateCallback cb: mPlaybackStateCallbacks) {
            cb.onPrebuffering(session, progress);
        }

        String sessionPayload = (session == null) ? null : session.toJson();
        for(Messenger target: mRemoteClients) {
            Message msg = obtainMessage(AceStreamManager.MSG_PLAYBACK_STATE_PREBUFFERING);
            Bundle data = new Bundle(2);
            data.putString(AceStreamManager.MSG_PARAM_ENGINE_SESSION, sessionPayload);
            data.putInt(AceStreamManager.MSG_PARAM_PROGRESS, progress);
            msg.setData(data);
            sendMessage(target, msg);
        }
    }

    private void notifyPlaybackStatePlay(@Nullable EngineSession session) {
        for(PlaybackStateCallback cb: mPlaybackStateCallbacks) {
            cb.onPlay(session);
        }

        String sessionPayload = (session == null) ? null : session.toJson();
        for(Messenger target: mRemoteClients) {
            Message msg = obtainMessage(AceStreamManager.MSG_PLAYBACK_STATE_PLAY);
            Bundle data = new Bundle(1);
            data.putString(AceStreamManager.MSG_PARAM_ENGINE_SESSION, sessionPayload);
            msg.setData(data);
            sendMessage(target, msg);
        }
    }

    private void notifyPlaybackStateStop() {
        for(PlaybackStateCallback cb: mPlaybackStateCallbacks) {
            cb.onStop();
        }

        for(Messenger target: mRemoteClients) {
            sendMessage(target, obtainMessage(AceStreamManager.MSG_PLAYBACK_STATE_STOP));
        }
    }

    private class EngineStatusHandler {
        private final static String TAG = "AS/Manager/SH";
        private boolean mPlaybackStarted = false;
        private EngineSession mSession = null;
        private AceStreamRemoteDevice mCurrentRemoteDevice = null;

        void sessionStarted(@Nullable EngineSession session, @Nullable AceStreamRemoteDevice device) {
            Logger.v(TAG, "sessionStarted: session=" + session + " device=" + device);
            mPlaybackStarted = false;
            mSession = session;
            mCurrentRemoteDevice = device;
            notifyPlaybackStateStart(session);
        }

        void sessionStopped() {
            Logger.v(TAG, "sessionStopped");
            mSession = null;
            mCurrentRemoteDevice = null;
            notifyPlaybackStateStop();
        }

        void setStatus(EngineStatus status, @Nullable AceStreamRemoteDevice device) {
            if(!areDevicesSame(device, mCurrentRemoteDevice)) {
                Logger.v(TAG, "setStatus:skip: device=" + device + " current=" + mCurrentRemoteDevice);
                return;
            }

            if(status != null) {
                switch(status.status) {
                    case "prebuf":
                        notifyPlaybackStatePrebuffering(mSession, status.progress);
                        break;
                    case "dl":
                        if(!mPlaybackStarted) {
                            if(BuildConfig.DEBUG) {
                                Log.v(TAG, "setStatus:notifyPlay: status=" + status + " device=" + device);
                            }
                            mPlaybackStarted = true;
                            notifyPlaybackStatePlay(mSession);
                        }
                        break;
                }
            }
        }
    }

    public SelectedPlayer getSelectedPlayer() {
        return getSelectedPlayer(false);
    }

    public SelectedPlayer getSelectedPlayer(boolean check) {
        SelectedPlayer player = AceStreamEngineBaseApplication.getSelectedPlayer();

        if(player != null && check) {
            boolean canUsePlayer = false;
            if (player.type == SelectedPlayer.LOCAL_PLAYER) {
                //TODO: check className too?
                canUsePlayer = AceStream.isAppInstalled(player.id1);
            } else if (player.type == SelectedPlayer.CONNECTABLE_DEVICE) {
                ConnectableDevice device = findDeviceById(player.id1);
                canUsePlayer = (device != null && device.isConnectable());
            } else if (player.type == SelectedPlayer.ACESTREAM_DEVICE) {
                AceStreamRemoteDevice device = findAceStreamRemoteDeviceById(player.id1);
                canUsePlayer = (device != null && device.isConnectable());
            } else if (player.type == SelectedPlayer.OUR_PLAYER) {
                canUsePlayer = true;
            }

            if (!canUsePlayer) {
                forgetPlayer();
                return null;
            }
        }

        return player;
    }

    public void forgetPlayer() {
        AceStreamEngineBaseApplication.forgetSelectedPlayer();
    }

    public void getMediaFileAsync(
            @NonNull final TransportFileDescriptor descriptor,
            @NonNull final MediaItem media,
            @NonNull final org.acestream.engine.controller.Callback<Pair<String, MediaFilesResponse.MediaFile>> callback
    ) {
        if(media.getUri() == null) {
            throw new IllegalStateException("missing uri");
        }

        if(mEngineApi == null) {
            Log.e(TAG, "getMediaFileAsync: missing engine api");
            callback.onError("Engine is not connected");
            return;
        }

        int index;
        try {
            index = Integer.parseInt(MiscUtils.getQueryParameter(media.getUri(), "index"));
        }
        catch(NumberFormatException|UnsupportedEncodingException e) {
            index = 0;
        }
        final int fileIndex = index;

        mEngineApi.getMediaFiles(descriptor, new org.acestream.engine.controller.Callback<MediaFilesResponse>() {
            @Override
            public void onSuccess(MediaFilesResponse result) {
                for(MediaFilesResponse.MediaFile mf: result.files) {
                    if(mf.index == fileIndex) {
                        media.setLive(mf.isLive());
                        media.setP2PInfo(mf.infohash, mf.index);
                        callback.onSuccess(new Pair<>(result.transport_file_data, mf));
                        return;
                    }
                }
                Log.e(TAG, "Bad file index: index=" + fileIndex);
            }

            @Override
            public void onError(String err) {
                callback.onError(err);
            }
        });
    }

    public void startPlayer(
            final Context context,
            final SelectedPlayer player,
            final MediaItem media,
            final int streamIndex,
            final CastResultListener castResultListener,
            final EngineSessionStartListener engineSessionStartListener,
            final int forceResume,
            final String productKey) {

        TransportFileDescriptor descriptor;
        try {
            descriptor = media.getDescriptor();
        }
        catch(TransportFileParsingException e) {
            Log.e(TAG, "Failed to read transport file", e);
            castResultListener.onError(e.getMessage());
            return;
        }

        // Need transport file data when casting to AceCast.
        // Remote device cannot start descriptor points to local file.
        // In other cases it can start, but passing transport file data will cause faster start.
        boolean needTransportFileData = false;
        if(player.type == SelectedPlayer.ACESTREAM_DEVICE && descriptor.getTransportFileData() == null) {
            needTransportFileData = true;
        }

        final long savedTime = media.getSavedTime();

        MediaFilesResponse.MediaFile mediaFile = media.getMediaFile();
        if(mediaFile == null || needTransportFileData) {
            final TransportFileDescriptor fDescriptor = descriptor;
            Log.v(TAG, "startPlayer: no media file, get from engine: descriptor=" + descriptor);
            try {
                descriptor.fetchTransportFileData(getContentResolver());
                getMediaFileAsync(descriptor, media, new org.acestream.engine.controller.Callback<Pair<String, MediaFilesResponse.MediaFile>>() {
                    @Override
                    public void onSuccess(Pair<String, MediaFilesResponse.MediaFile> result) {
                        fDescriptor.setTransportFileData(result.first);
                        startPlayer(context, player, fDescriptor, result.second, streamIndex,
                                castResultListener, engineSessionStartListener, forceResume,
                                savedTime, productKey);
                    }

                    @Override
                    public void onError(String err) {
                        castResultListener.onError(err);
                    }
                });
            }
            catch(TransportFileParsingException e) {
                Log.e(TAG, "Failed to read transport file data", e);
                castResultListener.onError(e.getMessage());
            }
            return;
        }

        // Got descriptor and media file. Start now.
        startPlayer(context, player, descriptor, mediaFile, streamIndex, castResultListener,
                engineSessionStartListener, forceResume, savedTime, productKey);
    }

    public void startPlayer(
            final Context context,
            final SelectedPlayer player,
            final TransportFileDescriptor descriptor,
            final MediaFilesResponse.MediaFile mediaFile,
            int streamIndex,
            final CastResultListener castResultListener,
            final EngineSessionStartListener sessionStartListener,
            int forceResume,
            long savedTime,
            String productKey) {
        Logger.v(TAG, "startPlayer: player=" + player
                + " descriptor=" + descriptor
                + " mediaFile=" + mediaFile
                + " forceResume=" + forceResume
                + " savedTime=" + savedTime
                + " productKey=" + productKey
        );
        final PlaybackData playbackData = new PlaybackData();
        playbackData.descriptor = descriptor;
        playbackData.mediaFile = mediaFile;
        playbackData.streamIndex = streamIndex;
        playbackData.productKey = productKey;

        setCastResultListener(castResultListener);

        if(player.type == SelectedPlayer.CONNECTABLE_DEVICE) {
            RunnableWithParams<Pair<Boolean, Long>> runnable = new RunnableWithParams<Pair<Boolean, Long>>() {
                @Override
                public void run(Pair<Boolean,Long> data) {
                    playbackData.outputFormat = getOutputFormatForContent(
                            mediaFile.type,
                            mediaFile.mime,
                            player.id1,
                            true,
                            false);
                    playbackData.useFixedSid = false;
                    playbackData.stopPrevReadThread = 0;
                    playbackData.resumePlayback = data.first;
                    playbackData.seekOnStart = data.second;
                    initEngineSession(playbackData, sessionStartListener);
                }
            };
            if(forceResume == 1) {
                runnable.run(new Pair<>(true, savedTime));
            }
            else if(forceResume == 0) {
                runnable.run(new Pair<>(false, 0L));
            }
            else {
                checkResumeOptions(context, mediaFile.infohash, mediaFile.index, savedTime, runnable);
            }
        }
        else if(player.type == SelectedPlayer.ACESTREAM_DEVICE) {
            RunnableWithParams<Pair<Boolean, Long>> runnable = new RunnableWithParams<Pair<Boolean, Long>>() {
                @Override
                public void run(Pair<Boolean, Long> data) {
                    playbackData.useTimeshift = true;
                    startAceCast(playbackData, player.id1, data.second, castResultListener);
                }
            };
            if(forceResume == 1) {
                runnable.run(new Pair<>(true, savedTime));
            }
            else if(forceResume == 0) {
                runnable.run(new Pair<>(false, 0L));
            }
            else {
                checkResumeOptions(context, mediaFile.infohash, mediaFile.index, savedTime, runnable);
            }
        }
        else if(player.type == SelectedPlayer.LOCAL_PLAYER) {
            playbackData.outputFormat = getOutputFormatForContent(
                    mediaFile.type,
                    mediaFile.mime,
                    player.id1,
                    false,
                    false);
            playbackData.useFixedSid = false;
            playbackData.stopPrevReadThread = 0;
            initEngineSession(playbackData, sessionStartListener);
        }
        else {
            throw new IllegalStateException("unexpected player type: " + player.type);
        }
    }

    private void checkResumeOptions(Context context, String infohash, int fileIndex, final long savedTime, final RunnableWithParams<Pair<Boolean,Long>> runnable) {
        if(savedTime == 0 && getContentSettingsForItem(infohash, fileIndex) == null) {
            // no saved position
            runnable.run(new Pair<>(false, 0L));
            return;
        }

        // got saved position, prompt user what to do
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(context);
        builder.setMessage(R.string.want_restart);
        builder.setPositiveButton(R.string.restart_from_beginning, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                runnable.run(new Pair<>(false, 0L));
            }
        });
        builder.setNegativeButton(R.string.resume, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                runnable.run(new Pair<>(true, savedTime));
            }
        });
        builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                runnable.run(new Pair<>(false, 0L));
            }
        });
        builder.create().show();
    }

    public void startLocalPlayer(@NonNull Context context, @NonNull SelectedPlayer player, @NonNull String url, @NonNull String mime) {
        //TODO: check output format?
        Logger.v(TAG, "startLocalPlayer: player=" + player + " url=" + url);

        stopRemotePlayback(true);
        AceStreamEngineBaseApplication.getInstance().logPlayRequest(player);

        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.parse(url), mime);
        intent.addCategory(Intent.CATEGORY_BROWSABLE);

        intent.setComponent(new ComponentName(player.id1, player.id2));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            context.startActivity(intent);
        }
        catch(ActivityNotFoundException|SecurityException e) {
            Logger.wtf(TAG, "Failed to start local player: player" + player, e);
            AceStream.toast(R.string.failed_to_start);
        }
        setLastSelectedDeviceId(null);
    }

    public void startCastDevice(
            String deviceId,
            boolean restartFromLastPosition,
            long startFrom,
            @Nullable CastResultListener listener) {
        ConnectableDevice device = findDeviceById(deviceId);

        if (device == null) {
            Log.w(TAG, "cannot find saved device");
            if(listener != null) {
                listener.onError("Cannot connect. Try once more.");
            }
            return;
        }
        else if (!device.isConnectable()) {
            Log.w(TAG, "saved device is not connectable");
            if(listener != null) {
                listener.onError("Cannot connect. Try once more.");
            }
            return;
        }

        Log.d(TAG, "cast to device: name=" + device.getFriendlyName()
                + " restart=" + restartFromLastPosition
                + " startFrom=" + startFrom
                + " (this=" + hashCode() + ")");
        mWaitReconnect = false;

        AceStreamEngineBaseApplication.getInstance().logPlayRequest(SelectedPlayer.CONNECTABLE_DEVICE);
        setLastSelectedDeviceId(device.getId());
        castToDevice(device, null, restartFromLastPosition, startFrom, listener);
    }

    private void startAceCast(
            @NonNull PlaybackData playbackData,
            @NonNull String deviceId,
            long savedTime,
            @Nullable final CastResultListener listener) {
        final AceStreamRemoteDevice device = findAceStreamRemoteDeviceById(deviceId);

        if (device == null) {
            Log.w(TAG, "startAceCast: cannot find saved acestream device: deviceId=" + deviceId);
            if(listener != null) {
                listener.onError("Cannot connect. Try once more.");
            }
            return;
        }
        else if (!device.isConnectable()) {
            Log.w(TAG, "startAceCast: saved acestream device is not connectable: device=" + device);
            if(listener != null) {
                listener.onError("Cannot connect. Try once more.");
            }
            return;
        }

        Logger.v(TAG, "startAceCast: playbackData=" + playbackData.toJson() + " device=" + device);

        // stop local engine session
        stopEngineSession(true);

        setCastResultListener(listener);
        setCurrentRemoteDevice(device, true, true);

        try {
            device.connect();
            device.startPlayback(playbackData, savedTime);
        }
        catch(TransportFileParsingException e) {
            Log.e(TAG, "Failed to parse transport file");
            if(listener != null) {
                listener.onError("Failed to parse transport file");
            }
            return;
        }

        AceStreamEngineBaseApplication.getInstance().logPlayRequest(SelectedPlayer.ACESTREAM_DEVICE);

        // check device state after some timeout
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!device.isConnected() && listener != null && listener.isWaiting()) {
                    Log.d(TAG, "startAceCast: check device state, notify listener: device=" + device + " connected=" + device.isConnected());
                    listener.onError(getString(R.string.please_try_again));
                    listener.onDeviceDisconnected(device);
                }
            }
        }, Constants.REMOTE_DEVICE_CONNECT_TIMEOUT * 1000);
    }

    public void registerCastResultListener(CastResultListener listener) {
        mCastResultListener = listener;
    }

    public void unregisterCastResultListener(CastResultListener listener) {
        if(listener == mCastResultListener) {
            mCastResultListener = null;
        }
    }

    public void initPlaylist(
            @NonNull TransportFileDescriptor descriptor,
            @NonNull MediaFilesResponse mediaFiles,
            int fileIndex) {
        Log.v(TAG, "initPlaylist: fileIndex=" + fileIndex);
        Playlist playlist = new Playlist();
        playlist.setContentDescriptor(descriptor);
        playlist.setMetadata(mediaFiles);

        for(MediaFilesResponse.MediaFile mf: mediaFiles.files) {
            PlaylistItem playlistItem = new PlaylistItem(playlist, mf);
            playlist.addItem(playlistItem);
        }
        playlist.sort();
        if(fileIndex == -1) {
            playlist.setCurrent(0);
        }
        else {
            playlist.setCurrentByFileIndex(fileIndex);
        }

        setCurrentPlaylist(playlist);
    }

    public void setRemoteSelectedPlayer(SelectedPlayer player) {
        mCurrentSelectedPlayer = player;
    }

    public EngineApi getEngineApi() {
        return mEngineApi;
    }

    @RestrictTo(RestrictTo.Scope.TESTS)
    public EngineStatus getLastEngineStatus() {
        return mLastEngineStatus;
    }

    private void setCastResultListener(final CastResultListener listener) {
        if(mCastResultListener != null && mCastResultListener != listener) {
            // cancel prev listener
            Log.d(TAG, "setCastResultListener: cancel prev listener: prev=" + mCastResultListener + " new=" + listener);
            mCastResultListener.onCancel();
        }

        mCastResultListener = listener;
    }

    /**
     * Get engine api in async way
     * @param callback
     */
    public void getEngine(@NonNull EngineStateCallback callback) {
        if(mEngineApi == null) {
            mEngineStateCallbacks.add(callback);
            startEngine();
        }
        else {
            callback.onEngineConnected(this, mEngineApi);
        }
    }

    /**
     * Notify all clients who called getEngine()
     */
    private void notifyEngineConnected() {
        for (EngineStateCallback callback : mEngineStateCallbacks) {
            callback.onEngineConnected(this, mEngineApi);
        }
        mEngineStateCallbacks.clear();
    }

    @MainThread
    public void addAuthCallback(AuthCallback cb) {
        synchronized (mAuthCallbacks) {
            if (!mAuthCallbacks.contains(cb)) {
                mAuthCallbacks.add(cb);
            }
        }
    }

    @MainThread
    public void removeAuthCallback(AuthCallback cb) {
        synchronized (mAuthCallbacks) {
            mAuthCallbacks.remove(cb);
        }
    }

    @SuppressLint("HandlerLeak")
    private class RemoteMessengerHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            IRemoteDevice device;
            Bundle d = msg.getData();
            switch (msg.what) {
                case AceStreamManager.MSG_REGISTER_CLIENT:
                    mRemoteClients.add(msg.replyTo);
                    // Send auth data to new client
                    remoteNotifyAuthUpdated(msg.replyTo, getRemoteAuthUpdatedPayload());
                    remoteSendDevices(msg.replyTo);
                    remoteSendReady(msg.replyTo);
                    notifyBonusAdsAvailable(msg.replyTo, true);
                    break;
                case AceStreamManager.MSG_UNREGISTER_CLIENT:
                    mRemoteClients.remove(msg.replyTo);
                    break;
                case AceStreamManager.MSG_SIGN_OUT:
                    signOut();
                    break;
                case AceStreamManager.MSG_DISCONNECT_DEVICE:
                    disconnectDevice();
                    break;
                case AceStreamManager.MSG_DISCOVER_DEVICES:
                    discoverDevices(msg.getData().getBoolean(AceStreamManager.MSG_PARAM_FORCE_INIT));
                    break;
                case AceStreamManager.MSG_CHECK_PENDING_NOTIFICATIONS:
                    checkPendingNotification();
                    break;
                case AceStreamManager.MSG_FORGET_SELECTED_PLAYER:
                    AceStreamEngineBaseApplication.forgetSelectedPlayer();
                    AceStreamEngineBaseApplication.updateAppSettings(mAppPreferences);
                    break;
                case AceStreamManager.MSG_SAVE_SELECTED_PLAYER:
                    SelectedPlayer player;
                    try {
                        player = SelectedPlayer.fromJson(d.getString(AceStreamManager.MSG_PARAM_SELECTED_PLAYER));
                    }
                    catch(JSONException e) {
                        player = null;
                    }
                    AceStreamEngineBaseApplication.saveSelectedPlayer(
                            player,
                            d.getBoolean(AceStreamManager.MSG_PARAM_FROM_USER));
                    AceStreamEngineBaseApplication.updateAppSettings(mAppPreferences);
                    break;
                case AceStreamManager.MSG_START_ACECAST: {
                    RemoteCastResultListener listener = null;
                    String deviceId = d.getString(AceStreamManager.MSG_PARAM_REMOTE_DEVICE_ID);

                    if(deviceId == null) {
                        Log.e(TAG, "handleMessage:MSG_START_ACECAST: missing device id");
                        break;
                    }

                    if (d.containsKey(AceStreamManager.MSG_PARAM_CAST_RESULT_LISTENER)) {
                        listener = new RemoteCastResultListener(
                                msg.getData().getInt(AceStreamManager.MSG_PARAM_CAST_RESULT_LISTENER),
                                msg.replyTo);
                    }
                    startAceCast(
                            PlaybackData.fromJson(msg.getData().getString(AceStreamManager.MSG_PARAM_PLAYBACK_DATA)),
                            deviceId,
                            d.getLong(AceStreamManager.MSG_PARAM_SAVED_TIME),
                            listener);
                    break;
                }
                case AceStreamManager.MSG_INIT_ENGINE_SESSION: {
                    RemoteEngineSessionStartListener listener = null;
                    if (d.containsKey(AceStreamManager.MSG_PARAM_ENGINE_SESSION_START_LISTENER)) {
                        listener = new RemoteEngineSessionStartListener(
                                msg.getData().getInt(AceStreamManager.MSG_PARAM_ENGINE_SESSION_START_LISTENER),
                                msg.replyTo);
                    }
                    initEngineSession(
                            PlaybackData.fromJson(msg.getData().getString(AceStreamManager.MSG_PARAM_PLAYBACK_DATA)),
                            listener);
                    break;
                }
                case AceStreamManager.MSG_DEVICE_PLAY:
                    device = getGenericRemoteDevice(d);
                    if(device != null)
                        device.play();
                    break;
                case AceStreamManager.MSG_DEVICE_PAUSE:
                    device = getGenericRemoteDevice(d);
                    if(device != null)
                        device.pause();
                    break;
                case AceStreamManager.MSG_DEVICE_STOP:
                    device = getGenericRemoteDevice(d);
                    if(device != null)
                        device.stop(d.getBoolean(AceStreamManager.MSG_PARAM_DISCONNECT));
                    break;
                case AceStreamManager.MSG_DEVICE_SET_TIME:
                    device = getGenericRemoteDevice(d);
                    if(device != null)
                        device.setTime(d.getLong(AceStreamManager.MSG_PARAM_TIME));
                    break;
                case AceStreamManager.MSG_DEVICE_SET_VOLUME:
                    device = getGenericRemoteDevice(d);
                    if(device != null)
                        device.setVolume(d.getInt(AceStreamManager.MSG_PARAM_VOLUME));
                    break;
                case AceStreamManager.MSG_DEVICE_SET_POSITION:
                    device = getGenericRemoteDevice(d);
                    if(device != null)
                        device.setPosition(d.getFloat(AceStreamManager.MSG_PARAM_POSITION));
                    break;
                case AceStreamManager.MSG_DEVICE_SET_AUDIO_TRACK:
                    device = getGenericRemoteDevice(d);
                    if(device != null)
                        device.setAudioTrack(d.getInt(AceStreamManager.MSG_PARAM_TRACK));
                    break;
                case AceStreamManager.MSG_DEVICE_SET_SPU_TRACK:
                    device = getGenericRemoteDevice(d);
                    if(device != null)
                        device.setSpuTrack(d.getInt(AceStreamManager.MSG_PARAM_TRACK));
                    break;
                case AceStreamManager.MSG_DEVICE_SET_VIDEO_SIZE:
                    device = getGenericRemoteDevice(d);
                    if(device != null)
                        device.setVideoSize(d.getString(AceStreamManager.MSG_PARAM_VIDEO_SIZE));
                    break;
                case AceStreamManager.MSG_DEVICE_SET_AUDIO_OUTPUT:
                    device = getGenericRemoteDevice(d);
                    if(device != null)
                        device.setAudioOutput(d.getString(AceStreamManager.MSG_PARAM_AOUT));
                    break;
                case AceStreamManager.MSG_DEVICE_SET_AUDIO_DIGITAL_OUTPUT_ENABLED:
                    device = getGenericRemoteDevice(d);
                    if(device != null)
                        device.setAudioDigitalOutputEnabled(d.getBoolean(AceStreamManager.MSG_PARAM_ENABLED));
                    break;
                case AceStreamManager.MSG_START_CAST_DEVICE: {
                    RemoteCastResultListener listener = null;
                    if (d.containsKey(AceStreamManager.MSG_PARAM_CAST_RESULT_LISTENER)) {
                        listener = new RemoteCastResultListener(
                                msg.getData().getInt(AceStreamManager.MSG_PARAM_CAST_RESULT_LISTENER),
                                msg.replyTo);
                    }
                    startCastDevice(
                            d.getString(AceStreamManager.MSG_PARAM_REMOTE_DEVICE_ID),
                            d.getBoolean(AceStreamManager.MSG_PARAM_RESTART_FROM_LAST_POSITION),
                            d.getLong(AceStreamManager.MSG_PARAM_START_FROM),
                            listener);
                    break;
                }
                case AceStreamManager.MSG_STOP_ENGINE_SESSION:
                    stopEngineSession(d.getBoolean(AceStreamManager.MSG_PARAM_SEND_STOP_COMMAND));
                    break;
                case AceStreamManager.MSG_CLEAR_CACHE:
                    clearCache();
                    break;
                case AceStreamManager.MSG_GET_PREFERENCES:
                    if(mAppPreferences == null) {
                        updateEnginePreferences();
                    }
                    else {
                        AceStreamEngineBaseApplication.updateAppSettings(mAppPreferences);
                        remoteNotifyEngineSettings(msg.replyTo, mAppPreferences);
                    }
                    break;
                case AceStreamManager.MSG_SET_PREFERENCES:
                    setPreferences(d.getBundle(AceStreamManager.MSG_PARAM_PREFERENCES));
                    break;
                case AceStreamManager.MSG_SET_ENGINE_STATUS_LISTENERS:
                    updateRemoteEngineStatusListeners(d.getInt(AceStreamManager.MSG_PARAM_COUNT));
                    break;
                case AceStreamManager.MSG_SET_PLAYER_ACTIVITY_TIMEOUT:
                    setPlayerActivityTimeout(d.getInt(AceStreamManager.MSG_PARAM_TIMEOUT));
                    break;
                case AceStreamManager.MSG_SET_HLS_STREAM:
                    setHlsStream(d.getInt(AceStreamManager.MSG_PARAM_STREAM_INDEX));
                    break;
                case AceStreamManager.MSG_STOP_REMOTE_PLAYBACK:
                    stopRemotePlayback(d.getBoolean(AceStreamManager.MSG_PARAM_DISCONNECT_DEVICE));
                    break;
                case AceStreamManager.MSG_LIVE_SEEK:
                    liveSeek(d.getInt(AceStreamManager.MSG_PARAM_POSITION));
                    break;
                case AceStreamManager.MSG_STOP_ENGINE:
                    stopEngine();
                    break;
                case AceStreamManager.MSG_SHOW_BONUS_ADS:
                    showBonusAds();
                default:
                    super.handleMessage(msg);
            }
        }
    }

    protected String getRemoteAuthUpdatedPayload() {
        final String payload;
        AuthData authData = getAuthData();
        if(authData == null) {
            payload = null;
        }
        else {
            // Populate login because it's normally not contained in auth data.
            authData.login = getAuthLogin();
            payload = authData.toJson();
        }

        return payload;
    }

    private static Message obtainMessage(int what) {
        return Message.obtain(null, what);
    }

    private static void sendMessage(Messenger target, Message msg) {
        try {
            target.send(msg);
        }
        catch(RemoteException e) {
            // ignore, client is possibly dead
        }
    }

    protected void remoteNotifyAuthUpdated(Messenger target, String payload) {
        Message msg = obtainMessage(AceStreamManager.MSG_AUTH_UPDATED);
        Bundle data = new Bundle(1);
        data.putString(AceStreamManager.MSG_PARAM_AUTH_DATA, payload);
        msg.setData(data);
        sendMessage(target, msg);
    }

    protected void remoteSendDevices(Messenger target) {
        //TODO: send SET_DEVICES to update list completely
        for (AceStreamRemoteDevice device : getAceStreamRemoteDevices().values()) {
            String devicePayload = serializeRemoteDevice(device);
            Message msg = obtainMessage(AceStreamManager.MSG_DEVICE_ADDED);
            Bundle data = new Bundle(1);
            data.putString(AceStreamManager.MSG_PARAM_REMOTE_DEVICE, devicePayload);
            msg.setData(data);
            sendMessage(target, msg);
        }

        for (ConnectableDevice device : getConnectableDevices().values()) {
            String devicePayload = serializeRemoteDevice(device);
            Message msg = obtainMessage(AceStreamManager.MSG_DEVICE_ADDED);
            Bundle data = new Bundle(1);
            data.putString(AceStreamManager.MSG_PARAM_REMOTE_DEVICE, devicePayload);
            msg.setData(data);
            sendMessage(target, msg);
        }
    }

    protected void remoteSendReady(Messenger target) {
        sendMessage(target, obtainMessage(AceStreamManager.MSG_SERVICE_READY));
    }

    protected IRemoteDevice getGenericRemoteDevice(Bundle remoteMessageParams) {
        boolean isAceCast = remoteMessageParams.getBoolean(AceStreamManager.MSG_PARAM_IS_ACECAST);
        String deviceId = remoteMessageParams.getString(AceStreamManager.MSG_PARAM_REMOTE_DEVICE_ID);
        if(isAceCast) {
            return findAceStreamRemoteDeviceById(deviceId);
        }
        else {
            ConnectableDevice device = findDeviceById(deviceId);
            return (device == null) ? null : new CsdkDeviceWrapper(device);
        }
    }

    protected static String serializeRemoteDevice(AceStreamRemoteDevice device) {
        try {
            JSONObject root = new JSONObject();
            root.put("id", device.getId());
            root.put("name", device.getName());
            root.put("ipAddress", device.getIpAddress());
            root.put("isAceCast", true);

            return root.toString();
        }
        catch(JSONException e) {
            throw new IllegalStateException("Failed to serialize remote device", e);
        }
    }

    protected static String serializeRemoteDevice(CsdkDeviceWrapper wrapper) {
        return serializeRemoteDevice(wrapper.getDevice());
    }

    protected static String serializeRemoteDevice(ConnectableDevice device) {
        try {
            JSONObject root = new JSONObject();
            root.put("id", device.getId());
            root.put("name", device.getFriendlyName());
            root.put("ipAddress", device.getIpAddress());
            root.put("isAceCast", false);

            return root.toString();
        }
        catch(JSONException e) {
            throw new IllegalStateException("Failed to serialize remote device", e);
        }
    }

    // Auth methods are implemented in subclass
    public abstract void signOut();
    public abstract String getAuthLogin();

    private static class RemoteCastResultListener implements CastResultListener {
        private final int mHashCode;
        private final Messenger mTarget;

        RemoteCastResultListener(int hashCode, Messenger target) {
            mHashCode = hashCode;
            mTarget = target;
        }

        @Override
        public int hashCode() {
            return mHashCode;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof RemoteCastResultListener && mHashCode == other.hashCode();
        }

        private void sendMessage(int what) {
            sendMessage(what, null);
        }

        private void sendMessage(int what, Bundle data) {
            Message msg = obtainMessage(what);
            if(data == null) {
                data = new Bundle();
            }
            data.putInt(AceStreamManager.MSG_PARAM_CAST_RESULT_LISTENER, mHashCode);
            msg.setData(data);
            AceStreamManagerImpl.sendMessage(mTarget, msg);
        }

        @Override
        public void onSuccess() {
            sendMessage(AceStreamManager.MSG_CAST_RESULT_LISTENER_SUCCESS);
        }

        @Override
        public void onSuccess(AceStreamRemoteDevice device, SelectedPlayer selectedPlayer) {
            Bundle data = new Bundle();
            data.putString(AceStreamManager.MSG_PARAM_REMOTE_DEVICE, serializeRemoteDevice(device));
            data.putString(AceStreamManager.MSG_PARAM_SELECTED_PLAYER, SelectedPlayer.toJson(selectedPlayer));
            sendMessage(AceStreamManager.MSG_CAST_RESULT_LISTENER_SUCCESS, data);
        }

        @Override
        public void onError(String error) {
            Bundle data = new Bundle();
            data.putString(AceStreamManager.MSG_PARAM_ERROR, error);
            sendMessage(AceStreamManager.MSG_CAST_RESULT_LISTENER_ERROR, data);
        }

        @Override
        public void onDeviceConnected(AceStreamRemoteDevice device) {
            Bundle data = new Bundle();
            data.putString(AceStreamManager.MSG_PARAM_REMOTE_DEVICE, serializeRemoteDevice(device));
            sendMessage(AceStreamManager.MSG_CAST_RESULT_LISTENER_DEVICE_CONNECTED, data);
        }

        @Override
        public void onDeviceConnected(ConnectableDevice device) {
            Bundle data = new Bundle();
            data.putString(AceStreamManager.MSG_PARAM_REMOTE_DEVICE, serializeRemoteDevice(device));
            sendMessage(AceStreamManager.MSG_CAST_RESULT_LISTENER_DEVICE_CONNECTED, data);
        }

        @Override
        public void onDeviceDisconnected(AceStreamRemoteDevice device) {
            Bundle data = new Bundle();
            data.putString(AceStreamManager.MSG_PARAM_REMOTE_DEVICE, serializeRemoteDevice(device));
            sendMessage(AceStreamManager.MSG_CAST_RESULT_LISTENER_DEVICE_DISCONNECTED, data);
        }

        @Override
        public void onDeviceDisconnected(ConnectableDevice device) {
            Bundle data = new Bundle();
            data.putString(AceStreamManager.MSG_PARAM_REMOTE_DEVICE, serializeRemoteDevice(device));
            sendMessage(AceStreamManager.MSG_CAST_RESULT_LISTENER_DEVICE_DISCONNECTED, data);
        }

        @Override
        public void onCancel() {
            sendMessage(AceStreamManager.MSG_CAST_RESULT_LISTENER_CANCEL);
        }

        @Override
        public boolean isWaiting() {
            return false;
        }
    }

    private static class RemoteEngineSessionStartListener implements EngineSessionStartListener {
        private final int mHashCode;
        private final Messenger mTarget;

        RemoteEngineSessionStartListener(int hashCode, Messenger target) {
            mHashCode = hashCode;
            mTarget = target;
        }

        @Override
        public int hashCode() {
            return mHashCode;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof RemoteCastResultListener && mHashCode == other.hashCode();
        }

        private void sendMessage(int what) {
            sendMessage(what, null);
        }

        private void sendMessage(int what, Bundle data) {
            Message msg = obtainMessage(what);
            if(data == null) {
                data = new Bundle();
            }
            data.putInt(AceStreamManager.MSG_PARAM_ENGINE_SESSION_START_LISTENER, mHashCode);
            msg.setData(data);
            AceStreamManagerImpl.sendMessage(mTarget, msg);
        }

        @Override
        public void onSuccess(EngineSession session) {
            Bundle data = new Bundle();
            data.putString(AceStreamManager.MSG_PARAM_ENGINE_SESSION, EngineSession.toJson(session));
            sendMessage(AceStreamManager.MSG_ENGINE_SESSION_START_LISTENER_SUCCESS, data);
        }

        @Override
        public void onError(String error) {
            Bundle data = new Bundle();
            data.putString(AceStreamManager.MSG_PARAM_ERROR, error);
            sendMessage(AceStreamManager.MSG_ENGINE_SESSION_START_LISTENER_ERROR, data);
        }
    }

    @Override
    public OutputFormat getOutputFormatForContent(String type, String mime, String playerPackageName, boolean isAirCast, boolean isOurPlayer) {
        boolean transcodeAudio = false;
        boolean transcodeMP3 = false;
        boolean transcodeAC3 = false;

        String outputFormat;
        String prefsOutputFormat;
        if(type.equals(CONTENT_TYPE_VOD)) {
            prefsOutputFormat = AceStreamEngineBaseApplication.getVodOutputFormat();
        }
        else {
            prefsOutputFormat = AceStreamEngineBaseApplication.getLiveOutputFormat();
        }

        outputFormat = prefsOutputFormat;
        //noinspection IfCanBeSwitch
        if(prefsOutputFormat.equals("original")) {
            if(mime.equals(MIME_HLS)) {
                outputFormat = "hls";
            }
            else {
                outputFormat = "http";
            }
        }
        else if(prefsOutputFormat.equals("hls")) {
            transcodeAudio = AceStreamEngineBaseApplication.getTranscodeAudio();
            transcodeAC3 = AceStreamEngineBaseApplication.getTranscodeAC3();
        }
        else if(prefsOutputFormat.equals("auto")) {
            // auto selection based on content and player

            if(isOurPlayer) {
                outputFormat = "http";
            }
            else {
                boolean isVLC = false;
                boolean isMX = false;
                if (playerPackageName != null) {
                    switch (playerPackageName) {
                        case Constants.VLC_PACKAGE_NAME:
                        case Constants.VLC_BETA_PACKAGE_NAME:
                        case Constants.VLC_DEBUG_PACKAGE_NAME:
                            isVLC = true;
                            break;
                        case Constants.MX_FREE_PACKAGE_NAME:
                        case Constants.MX_PRO_PACKAGE_NAME:
                            isMX = true;
                            break;
                    }
                }

                if (type.equals(CONTENT_TYPE_VOD)) {
                    if (mime.startsWith("audio/")) {
                        // audio, http
                        outputFormat = "http";
                    } else {
                        // video
                        if (isVLC) {
                            // VLC, http
                            outputFormat = "http";
                        } else if (isMX) {
                            // MX Player
                            // mkv - HLS with AC3 transcoding
                            // other containers - http
                            if (mime.equals("video/x-matroska")) {
                                outputFormat = "hls";
                                transcodeAC3 = true;
                            } else {
                                outputFormat = "http";
                            }
                        } else if (isAirCast) {
                            // chromecast, airplay: HLS with AC3 transcoding
                            outputFormat = "hls";
                            transcodeAC3 = true;
                        } else {
                            // other players
                            // mkv - HLS with AC3 transcoding
                            // other containers - http
                            if (mime.equals("video/x-matroska")) {
                                outputFormat = "hls";
                                transcodeAC3 = true;
                            } else {
                                outputFormat = "http";
                            }
                        }
                    }
                } else {
                    // live, HLS
                    if (isVLC) {
                        outputFormat = "http";
                    } else if (isMX) {
                        // MX - HLS
                        outputFormat = "hls";
                    } else if (isAirCast) {
                        // aircast - always HLS
                        outputFormat = "hls";
                    } else {
                        // other players - HLS
                        outputFormat = "hls";
                    }
                    transcodeMP3 = false;
                    //noinspection RedundantIfStatement
                    if (isMX || isVLC) {
                        // MX and VLC - don't transcode
                        transcodeAudio = false;
                    } else {
                        // other players - transcode all audio codecs except AAC and MP3
                        transcodeAudio = true;
                    }
                }
            }
        }

        Log.d(TAG, String.format(
                "getOutputFormatForContent: prefs=%s format=%s ta=%s mp3=%s ac3=%s type=%s mime=%s player=%s isAirCast=%s",
                prefsOutputFormat,
                outputFormat,
                transcodeAudio,
                transcodeMP3,
                transcodeAC3,
                type,
                mime,
                playerPackageName,
                isAirCast));

        OutputFormat of = new OutputFormat();
        of.format = outputFormat;
        of.transcodeAudio = transcodeAudio;
        of.transcodeMP3 = transcodeMP3;
        of.transcodeAC3 = transcodeAC3;

        return of;
    }

    private void checkPendingNotification() {
        long age = System.currentTimeMillis() - mLastNotificationAt;
        if(age < 300000) {
            return;
        }

        NotificationData notification = AceStreamEngineBaseApplication.getPendingNotification("main");
        if(notification != null) {
            AceStreamEngineBaseApplication.showNotification(
                    notification,
                    this);
            mLastNotificationAt = System.currentTimeMillis();
        }
    }

    public AuthData getAuthData() {
        if(mLocalPackageInfo != null
                && mCurrentAuthData != null
                && mCurrentAuthData.auth_level != 0
                && mLocalPackageInfo.expiresAt > System.currentTimeMillis()
                && mCurrentAuthData.auth_level != mLocalPackageInfo.authLevel) {
            // Update auth data with local info
            AuthData authData = new AuthData(mCurrentAuthData);
            authData.auth_level = mLocalPackageInfo.authLevel;
            authData.package_name = mLocalPackageInfo.packageName;
            authData.package_color = mLocalPackageInfo.packageColor;
            authData.package_days_left = mLocalPackageInfo.packageDaysLeft;
            return authData;
        }
        return mCurrentAuthData;
    }

    public int getAuthLevel() {
        AuthData authData = getAuthData();
        return authData == null ? 0 : authData.auth_level;
    }

    public boolean isUserLoggedIn() {
        return getAuthLevel() > 0;
    }

    public String getAuthMethod() {
        return mCurrentAuthData == null ? "none" : mCurrentAuthData.method;
    }

    @SuppressWarnings("unused")
    public void addCoins(String source, int amount, boolean needNoAds) {
        int authLevel = getAuthLevel();
        Logger.v(TAG, "addCoins: source=" + source + " amount=" + amount + " authLevel=" + authLevel + " needNoAds=" + needNoAds);
        if(authLevel == 0) {
            Log.v(TAG, "addCoins: user not logged in");
            return;
        }

        if(needNoAds && !AuthUtils.hasNoAds(authLevel)) {
            Log.v(TAG, "addCoins: need no ads");
            return;
        }

        addPendingBonuses(amount);
    }

    public void addPendingBonuses(int amount) {
        if(mCurrentAuthData == null) {
            Log.e(TAG, "bonus:addPendingBonuses: missing auth data");
            return;
        }

        mPendingBonuses += amount;
        mLastBonusesUpdatedAt = mCurrentAuthData.bonuses_updated_at;
        notifyAuthUpdated();

        Log.v(TAG, "bonus:addPendingBonuses:"
                + " add=" + amount
                + " amount=" + mPendingBonuses
                + " updatedAt=" + mLastBonusesUpdatedAt
        );
    }

    public void resetPendingBonuses() {
        Logger.v(TAG, "resetPendingBonuses");
        mPendingBonuses = 0;
        mLastBonusesUpdatedAt = 0;
    }

    public int getPendingBonusesAmount() {
        return mPendingBonuses;
    }

    private void showBonusAds() {
        Log.v(TAG, "showBonusAds");
        // Called when bonus ads button was clicked on client app
        // Hide ads button now and show it again after 60 seconds
        notifyBonusAdsAvailable(false);
        mFreezeBonusAdsAvailable = true;
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mFreezeBonusAdsAvailable = false;
                notifyBonusAdsAvailable(true);
            }
        }, 60000);
    }

    public void notifyBonusAdsAvailable(boolean available) {
        notifyBonusAdsAvailable(null, available);
    }

    public void notifyBonusAdsAvailable(Messenger target, boolean available) {
        if(mFreezeBonusAdsAvailable) {
            return;
        }

        if (AuthUtils.hasNoAds(getAuthLevel()) && !AceStreamEngineBaseApplication.showAdsOnMainScreen()) {
            available = false;
        }

        if(target != null) {
            // send to one client
            sendRemoteBonusAdsAvailable(target, available);
        }
        else {
            // send to all client
            for (Messenger dst: mRemoteClients) {
                sendRemoteBonusAdsAvailable(dst, available);
            }
        }
    }

    public void sendRemoteBonusAdsAvailable(Messenger target, boolean available) {
        Message msg = obtainMessage(AceStreamManager.MSG_BONUS_ADS_AVAILABLE);
        Bundle data = new Bundle(1);
        data.putBoolean(AceStreamManager.MSG_PARAM_AVAILABLE, available);
        msg.setData(data);
        sendMessage(target, msg);
    }

    protected void notifyAuthUpdated() {
        // Notify in-process listeners
        synchronized(mAuthCallbacks) {
            for (AuthCallback callback : mAuthCallbacks) {
                callback.onAuthUpdated(getAuthData());
            }
        }

        // Notify remote clients connected via messenger
        String payload = getRemoteAuthUpdatedPayload();
        for(Messenger client: mRemoteClients) {
            remoteNotifyAuthUpdated(client, payload);
        }

        notifyBonusAdsAvailable(true);
    }

    public void openBonusAdsActivity(final Context context) {
        Logger.vv(TAG, "openBonusAdsActivity");
        int authLevel = getAuthLevel();
        if(authLevel == 0) {
            // user is not registered
            new AlertDialog.Builder(context)
                    .setMessage(R.string.sign_in_to_get_bonuses)
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int whichButton) {
                            AceStream.openLoginActivity(context, AceStream.LOGIN_TARGET_BONUS_ADS);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    })
                    .show();
        }
        else {
            // user is registered
            AceStream.openBonusAdsActivity(context);
        }
    }

    @SuppressWarnings("unused")
    @Nullable
    public AndroidConfig getConfig() {
        return (mEnginePreferences == null) ? null : mEnginePreferences.android_config;
    }

    @Nullable
    public AdConfig getAdConfig() {
        return mAdConfig;

    }

    public void getAdConfigAsync(AdConfigCallback callback) {
        AdConfig config = getAdConfig();
        if(config != null) {
            callback.onSuccess(config);
        }
        else {
            mAdConfigCallbacks.add(callback);
        }
    }

    private void notifyGotAdConfig(AdConfig config) {
        initAdManager();

        for(AdConfigCallback callback: mAdConfigCallbacks) {
            callback.onSuccess(config);
        }
        mAdConfigCallbacks.clear();
    }

    private void checkAdConfig() {
        if(mEnginePreferences != null
            && mEnginePreferences.android_config != null
            && mEnginePreferences.android_config.ad_config != null) {
            boolean hadConfig = (mAdConfig != null);
            mAdConfig = mEnginePreferences.android_config.ad_config;
            if(!hadConfig) {
                // Notify when we receive config first time
                notifyGotAdConfig(mAdConfig);
            }
        }
    }

    private void initAdManager() {
        AdConfig config = getAdConfig();
        if(config == null) {
            Logger.v(TAG, "initAdManager: missing config");
            return;
        }

        if(mAdManager == null) {
            mAdManager = new AdManager(config);
        }
        else {
            mAdManager.setAdConfig(config);
        }
    }

    public AdManager getAdManager() {
        return mAdManager;
    }

    public void setLocalPackageInfo(String sku, long startTime) {
        mLocalPackageInfo = null;

        long expiresAt = startTime + LOCAL_PACKAGE_INFO_TTL;
        long ttl = expiresAt - System.currentTimeMillis();
        if(ttl <= 0) {
            notifyAuthUpdated();
            return;
        }

        if(TextUtils.isEmpty(sku)) {
            notifyAuthUpdated();
            return;
        }

        // format: packagesmartandroid.m1.v2
        String[] parts = TextUtils.split(sku, "\\.");
        if(parts.length != 3) {
            notifyAuthUpdated();
            return;
        }

        String packageId = parts[0];
        String periodId = parts[1];
        String packageName;
        String packageColor;
        int daysLeft;

        switch(packageId) {
            case "packagesmartandroid":
                packageName = "Smart";
                packageColor = "green";
                break;
            case "packagesmart":
                packageName = "Smart";
                packageColor = "green";
                break;
            case "packagestandard":
                packageName = "Standard";
                packageColor = "green";
                break;
            case "packagepremium":
                packageName = "Premium";
                packageColor = "blue";
                break;
            default:
                return;
        }

        // No need to calculate how many days left because local subscription will
        // be active for a short period of time from its start.
        switch(periodId) {
            case "m1":
                daysLeft = 30;
                break;
            case "y1":
                daysLeft = 365;
                break;
            default:
                return;
        }

        mLocalPackageInfo = new UserPackageInfo();
        mLocalPackageInfo.authLevel = 645;
        mLocalPackageInfo.packageName = packageName;
        mLocalPackageInfo.packageColor = packageColor;
        mLocalPackageInfo.packageDaysLeft = daysLeft;
        mLocalPackageInfo.expiresAt = expiresAt;
        notifyAuthUpdated();

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                notifyAuthUpdated();
            }
        }, ttl);
    }
}
