package org.acestream.engine;

import java.io.IOException;

import org.acestream.engine.ads.AdManager;
import org.acestream.engine.aliases.App;
import org.acestream.engine.controller.Callback;
import org.acestream.engine.controller.ExtendedEngineApi;
import org.acestream.engine.prefs.ExtendedEnginePreferences;
import org.acestream.engine.prefs.NotificationData;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import android.text.TextUtils;
import android.util.Log;
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.Configuration;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.appodeal.ads.Appodeal;
import com.connectsdk.device.ConnectableDevice;
import com.google.android.gms.ads.AdListener;

import org.acestream.sdk.AceStream;
import org.acestream.sdk.Constants;
import org.acestream.sdk.EngineSession;
import org.acestream.sdk.EngineSessionStartListener;
import org.acestream.sdk.EngineStatus;
import org.acestream.sdk.SelectedPlayer;
import org.acestream.engine.acecast.client.AceStreamRemoteDevice;
import org.acestream.sdk.controller.api.TransportFileDescriptor;
import org.acestream.sdk.controller.api.response.AdConfig;
import org.acestream.sdk.controller.api.response.MediaFilesResponse;
import org.acestream.sdk.errors.GenericValidationException;
import org.acestream.sdk.errors.TransportFileParsingException;
import org.acestream.sdk.interfaces.EngineStatusListener;
import org.acestream.sdk.interfaces.IRemoteDevice;
import org.acestream.sdk.player.api.AceStreamPlayer;
import org.acestream.sdk.utils.AuthUtils;
import org.acestream.sdk.utils.Logger;
import org.acestream.sdk.utils.MiscUtils;
import org.acestream.sdk.utils.PermissionUtils;
import org.acestream.sdk.utils.VlcBridge;
import org.acestream.sdk.utils.Workers;

import static org.acestream.engine.Constants.ADMOB_TEST_INTERSTITIAL;

public class ContentStartActivity
    extends
        PlaybackManagerFragmentActivity
    implements
        OnClickListener,
        SelectFileDialogFragment.SelectFileDialogListener,
        EngineStatusListener,
        PlaybackManager.Callback
{

	private final static String TAG = "AS/ContentStart";

    private final static int REQUEST_CODE_SELECT_PLAYER = 1;
    private final static int REQUEST_CODE_ADS_NOTIFICATION = 2;

	private boolean mPlayerStarted = false;
	private boolean mStartEngineWhenConnected = false;

    private boolean mActive = false;
    private boolean mRunning = false;
    private boolean mIsWaiting = false;
    private boolean mStartingPlayback = false;
    private boolean mStartingLocalPlayback = false;
    private boolean mStartedFromExternalRequest = true;
    private boolean mSkipRememberedPlayer = false;
    private int mGotStorageAccess = -1;
    private Button mButtonGrantPermissions;
    private boolean mShowingNotification = false;
    private String mProductKey = null;
    private boolean mKeepOriginalSessionInitiator = false;

    private MediaFilesResponse mMediaFiles = null;
    private SelectedPlayer mSelectedPlayer = null;
    private TransportFileDescriptor mDescriptor = null;
    private int mSelectedFileIndex = -1;

    private PowerManager.WakeLock mWakeLock;
    private ExtendedEngineApi mEngineService = null;

    private PlaybackManager.PlaybackStateCallback mPlaybackStateCallback = new PlaybackManager.PlaybackStateCallback() {
        @Override
        public void onPlaylistUpdated() {
            Log.v(TAG, "pstate:onPlaylistUpdated");
        }

        @Override
        public void onStart(@Nullable EngineSession session) {
            Log.v(TAG, "pstate:onStart: session=" + session);
        }

        @Override
        public void onPrebuffering(@Nullable EngineSession session, int progress) {
            Log.v(TAG, "pstate:onPrebuffering: progress=" + progress);
        }

        @Override
        public void onPlay(@Nullable EngineSession session) {
            Log.v(TAG, "pstate:onPlay: session=" + session + " player=" + mSelectedPlayer);

            if(mSelectedPlayer == null) {
                // This happens in such case:
                // - start content A
                // - init engine session
                // - close ContentStartActivity before session is started
                // - session is not stopped because there is not command URL
                // - start content B
                // - previous session generates "onPlay" event before player is selected
                //
                // Solution is just to ignore this 'onPlay' event.
                Log.d(TAG, "onPlay: missing selected player");
                return;
            }

            updateInfoText(R.string.starting_player);

            if(mSelectedPlayer.type == SelectedPlayer.LOCAL_PLAYER) {
                if(session == null) {
                    throw new IllegalStateException("missing engine session");
                }

                mPlaybackManager.startLocalPlayer(
                        ContentStartActivity.this,
                        mSelectedPlayer,
                        session.playbackUrl,
                        session.playbackData.mediaFile.mime);
                mPlayerStarted = true;
                exit();
            }
            else if(mSelectedPlayer.type == SelectedPlayer.CONNECTABLE_DEVICE) {
                if(session == null) {
                    throw new IllegalStateException("missing engine session");
                }
                mPlaybackManager.startCastDevice(mSelectedPlayer.id1,
                        session.playbackData.resumePlayback,
                        session.playbackData.seekOnStart,
                        mCastResultListener);
            }
            else if(mSelectedPlayer.type == SelectedPlayer.ACESTREAM_DEVICE) {
                // Do nothing.
                // Remote control will be started from cast result listener.
            }
            else {
                throw new IllegalStateException("unexpected player type: " + mSelectedPlayer.type);
            }
        }

        @Override
        public void onStop() {
            Log.v(TAG, "pstate:onStop");
        }
    };

    private PlaybackManager.CastResultListener mCastResultListener = new PlaybackManager.CastResultListener() {
        @Override
        public void onSuccess() {
            startRemoteControl(null);
        }

        @Override
        public void onSuccess(AceStreamRemoteDevice device, SelectedPlayer selectedPlayer) {
            startRemoteControl(selectedPlayer);
        }

        @Override
        public void onError(String error) {
            gotCastError(error);
        }

        @Override
        public void onDeviceConnected(AceStreamRemoteDevice device) {
            App.v(TAG, "device connected");
        }

        @Override
        public void onDeviceConnected(ConnectableDevice device) {
            App.v(TAG, "device connected");
        }

        @Override
        public void onDeviceDisconnected(AceStreamRemoteDevice device) {
            App.v(TAG, "device disconnected");
        }

        @Override
        public void onDeviceDisconnected(ConnectableDevice device) {
            App.v(TAG, "device disconnected");
        }

        @Override
        public boolean isWaiting() {
            return mIsWaiting;
        }

        @Override
        public void onCancel() {
            Log.d(TAG, "cast cancelled: active=" + mActive + " hash=" + hashCode());
            if(mActive) {
                gotCastError("Cancelled");
            }
        }
    };

	@Override
	protected void onCreate(Bundle savedInstanceState) {
        applyTheme();
		super.onCreate(savedInstanceState);
		Logger.v(TAG, "onCreate: this=" + this);

		setContentView(R.layout.l_activity_start_content);

		Button btnCancel = findViewById(R.id.cancel_btn_id);
		btnCancel.setOnClickListener(this);

        mButtonGrantPermissions = findViewById(R.id.button_grant_permissions);
        mButtonGrantPermissions.setOnClickListener(this);

        try {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if(powerManager != null) {
                mWakeLock = powerManager.newWakeLock(
                        PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                        "acestream:ContentStartWakeLock");
                if (mWakeLock != null) {
                    // Acquire lock for 10 minutes
                    mWakeLock.acquire(600000);
                }
            }
        }
        catch(Exception e) {
            Log.e(TAG, "Failed to init wake lock: " + e.getMessage());
        }

        if(!PermissionUtils.hasStorageAccess()) {
            Log.v(TAG, "onStart: request storage access");
            PermissionUtils.requestStoragePermissions(this, MainActivity.REQUEST_CODE_PERMISSIONS);
        }
        else {
            Logger.v(TAG, "onStart: got storage access");
            mGotStorageAccess = 1;
            onStorageAccessGranted();
        }
	}

	private void onStorageAccessGranted() {
	    Logger.v(TAG, "onStorageAccessGranted");

        mButtonGrantPermissions.setVisibility(View.GONE);
        ((Button)findViewById(R.id.cancel_btn_id)).setText(getResources().getString(R.string.cancel));
        findViewById(R.id.progress_bar).setVisibility(View.VISIBLE);

        mShowingNotification = false;
        NotificationData notification = AceStreamEngineBaseApplication.getPendingNotification("content-start");
        if(notification != null) {
            mShowingNotification = AceStreamEngineBaseApplication.showNotification(
                    notification,
                    this,
                    true,
                    REQUEST_CODE_ADS_NOTIFICATION);
        }

        if(!mShowingNotification) {
            startEngineWhenConnected();
        }
    }

    private void onStorageAccessDenied() {
        Logger.v(TAG, "onStorageAccessDenied");
        showError(R.string.need_storage_access);
        mButtonGrantPermissions.setVisibility(View.VISIBLE);
    }

    private void applyTheme() {
        if (mPlaybackManager == null || mPlaybackManager.isBlackThemeEnabled()) {
            setTheme(R.style.Theme_AceStream_Dialog_Dark);
        }
        else {
            setTheme(R.style.Theme_AceStream_Dialog_Light);
        }
    }

    @Override
    protected void onStart() {
	    Logger.v(TAG, "onStart");
        super.onStart();
        mRunning = true;
    }

    @Override
	public void onPause() {
		super.onPause();
		Logger.v(TAG, "onPause: this=" + this);
        mActive = false;
        if(mPlaybackManager != null) {
            mPlaybackManager.removeEngineStatusListener(this);
            mPlaybackManager.removePlaybackStateCallback(mPlaybackStateCallback);
        }
	}

	@Override
	public void onResume() {
		super.onResume();
		Logger.v(TAG, "onResume: this=" + this);
        mActive = true;
        mStartedFromExternalRequest = getIntent().getBooleanExtra(Constants.EXTRA_STARTED_FROM_EXTERNAL_REQUEST, true);
        mSkipRememberedPlayer = getIntent().getBooleanExtra(Constants.EXTRA_SKIP_REMEMBERED_PLAYER, false);
        mProductKey = getIntent().getStringExtra(Constants.EXTRA_PRODUCT_KEY);

        if(mGotStorageAccess != 1 && PermissionUtils.hasStorageAccess()) {
            onStorageAccessGranted();
        }
        else if(mGotStorageAccess != 0 && !PermissionUtils.hasStorageAccess()) {
            onStorageAccessDenied();
        }
	}

	@Override
    public void onConnected(PlaybackManager service) {
	    super.onConnected(service);
        mPlaybackManager.enableAceCastServer();
        mPlaybackManager.discoverDevices(false);
        mPlaybackManager.addCallback(this);
    }

	@Override
	public void onResumeConnected() {
	    super.onResumeConnected();
	    Log.d(TAG, "onResumeConnected: mStartEngineWhenConnected=" + mStartEngineWhenConnected);
	    if(mStartEngineWhenConnected) {
            mStartEngineWhenConnected = false;
            mPlaybackManager.startEngine();
        }
        mPlaybackManager.addEngineStatusListener(this);
        mPlaybackManager.addPlaybackStateCallback(mPlaybackStateCallback);

        if(AceStreamEngineBaseApplication.shouldShowAdMobAds()) {
            mPlaybackManager.getAdConfigAsync(new AceStreamManagerImpl.AdConfigCallback() {
                @Override
                public void onSuccess(AdConfig adConfig) {
                    if (!mRunning) return;
                    AdManager adManager = mPlaybackManager.getAdManager();
                    if (adConfig != null && adManager != null) {
                        adManager.init(ContentStartActivity.this);
                        if(adConfig.isProviderEnabled(AdManager.ADS_PROVIDER_ADMOB)) {
                            initInterstitialAd(adManager);
                        }

                        if(adConfig.isProviderEnabled(AdManager.ADS_PROVIDER_APPODEAL)) {
                            initAppodeal(adConfig);
                        }
                    }
                }
            });
        }
    }

	@Override
	public void onConfigurationChanged(Configuration newConfig)
	{
	    super.onConfigurationChanged(newConfig);
	    Log.d(TAG, "onConfigurationChanged");
	}

	@Override
	public void onStop() {
        Logger.v(TAG, "onStop: this=" + this + " player_started=" + mPlayerStarted + " pm=" + mPlaybackManager);
        mRunning = false;
        if(mWakeLock != null && mWakeLock.isHeld()) {
            mWakeLock.release();
        }

        // Always finish this activity because it's used like modal dialog
        // But don't finish when we're showing notification.
        if(!isFinishing() && !mShowingNotification) {
            exit();
        }

        if(mPlaybackManager != null) {
            mPlaybackManager.removeCallback(this);
            mPlaybackManager.unregisterCastResultListener(mCastResultListener);
        }

        if(!mPlayerStarted) {
            // stop local engine session
            if(mPlaybackManager != null) {
                mPlaybackManager.stopEngineSession(true);
            }

            // stop remote engine session
            if(mSelectedPlayer != null && mPlaybackManager != null && mSelectedPlayer.type == SelectedPlayer.ACESTREAM_DEVICE) {
                AceStreamRemoteDevice device = mPlaybackManager.findAceStreamRemoteDeviceById(mSelectedPlayer.id1);
                if(device != null) {
                    device.stopEngineSession();
                    device.stop(true);
                }
            }
        }

        // Call super.onStop() in the end because playback manager is set to null there
        super.onStop();
	}

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Logger.v(TAG, "onDestroy: this=" + this);
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "onNewIntent: action=" + intent.getAction() + " type=" + intent.getType() + " uri=" + String.valueOf(intent.getData()));
    }

	@Override
    public void onClick(View v) {
        int i = v.getId();
        if (i == R.id.cancel_btn_id) {
            exit();
        }
        else if (i == R.id.button_grant_permissions) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    PermissionUtils.requestStoragePermissions(this, MainActivity.REQUEST_CODE_PERMISSIONS);
                }
                else {
                    final Intent intent = new Intent("android.settings.APPLICATION_DETAILS_SETTINGS");
                    intent.addCategory(Intent.CATEGORY_DEFAULT);
                    intent.setData(Uri.parse("package:" + AceStreamEngineBaseApplication.context().getPackageName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        startActivity(intent);
                    }
                    catch (Exception ignored) {
                    }
                }
            }
        }
    }

    @MainThread
    private void updateInfoText(String message) {
        updateInfoText(0, message);
    }

    @MainThread
    private void updateInfoText(int resourceId) {
        updateInfoText(resourceId, null);
    }

    @MainThread
    private void updateInfoText(final int resourceId, final String message) {
	    final String fMessage;
        if(message == null) {
            fMessage = getResources().getString(resourceId);
        }
        else {
            fMessage = message;
        }

	    runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView text = findViewById(R.id.text_info);
                text.setText(fMessage);
            }
        });

    }

    private void exit() {
        Logger.v(TAG, "exit");
	    finish();
    }

    private void showError(int resourceId) {
        showError(resourceId, null);
    }

    private void showError(String message) {
        showError(0, message);
    }

    private void showError(final int resourceId, final String message) {
        mStartingLocalPlayback = false;
        Workers.runOnMainThread(new Runnable() {
            @Override
            public void run() {
                updateInfoText(resourceId, message);
                ((Button)findViewById(R.id.cancel_btn_id)).setText(getResources().getString(R.string.close));
                findViewById(R.id.progress_bar).setVisibility(View.INVISIBLE);
            }
        });
    }

    private void startGettingPreferences() {
        ensureEngineService();

        mEngineService.getPreferences(new Callback<ExtendedEnginePreferences>() {
            @Override
            public void onSuccess(ExtendedEnginePreferences result) {
                processSettings(result);
                startLoadingFiles(getIntent());
            }

            @Override
            public void onError(String err) {
                Log.e(TAG, "failed to get prefs: error" + err);
                showError(err);
            }
        });
	}

    private void ensurePlaybackManager() {
        if(mPlaybackManager == null) {
            throw new IllegalStateException("missing playback manager");
        }
    }

	private void ensureEngineService() {
        if(mEngineService == null) {
            throw new IllegalStateException("missing engine service");
        }
    }

    private boolean checkMobileNetworkConnection(final Runnable runnable) {
        boolean isConnectedToMobileNetwork = MiscUtils.isConnectedToMobileNetwork(this);
        boolean askedAboutMobileNetworking = AceStreamEngineBaseApplication.isMobileNetworkingEnabled();
        if(isConnectedToMobileNetwork && !askedAboutMobileNetworking) {
            Log.d(TAG, "startLoadingFiles: ask about mobile network: connected=" + isConnectedToMobileNetwork + " asked=" + askedAboutMobileNetworking);

            AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppCompatAlertDialogStyle);
            builder.setMessage(R.string.allow_mobile_networks);
            builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    AceStreamEngineBaseApplication.setMobileNetworkingEnabled(true);
                    runnable.run();
                }
            });
            builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    AceStreamEngineBaseApplication.setMobileNetworkingEnabled(false);
                    exit();
                }
            });
            builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    AceStreamEngineBaseApplication.setMobileNetworkingEnabled(false);
                    exit();
                }
            });
            builder.create().show();
            return false;
        }

        return true;
    }

    private void initTransportFileDescriptorFromIntent(final Intent intent)
            throws GenericValidationException {
	    if(intent.hasExtra(Constants.EXTRA_TRANSPORT_DESCRIPTOR)) {
	        Log.v(TAG, "got descriptor from extras");
	        mDescriptor = TransportFileDescriptor.fromJson(intent.getStringExtra(
	                Constants.EXTRA_TRANSPORT_DESCRIPTOR));
            mDescriptor.setTransportFileData(AceStream.getTransportFileFromCache(
                    mDescriptor.getDescriptorString()));
            return;
        }

        Uri uri = intent.getData();

        if(uri == null) {
            throw new GenericValidationException("Missing URI");
        }

        TransportFileDescriptor.Builder builder = new TransportFileDescriptor.Builder();
        Log.v(TAG, "initTransportFileDescriptorFromIntent: got uri: uri=" + uri.toString());

        mSelectedFileIndex = MiscUtils.getIntQueryParameter(uri, "index", -1);
        Logger.v(TAG, "initTransportFileDescriptorFromIntent: fileIndex=" + mSelectedFileIndex);

        if(TextUtils.equals(uri.getScheme(), "http")) {
            String acestreamLink = AceStream.parseAceStreamHttpApiUrl(uri);
            if(acestreamLink == null) {
                String infohash = AceStream.parseAceStreamPlaybackUrl(uri);
                if (infohash != null) {
                    acestreamLink = "acestream:?infohash=" + infohash;
                }
            }

            if(acestreamLink != null) {
                Uri newUri = Uri.parse(acestreamLink);
                Log.v(TAG, "initTransportFileDescriptorFromIntent: update uri: " + uri + "->" + newUri);
                uri = newUri;
                mKeepOriginalSessionInitiator = true;
            }
        }

        String scheme = uri.getScheme();
        if(scheme == null) {
            scheme = "";
        }

        switch(scheme) {
            case "content":
                try {
                    builder.setContentUri(getContentResolver(), uri);
                }
                catch(IOException e) {
                    Log.e(TAG, "error", e);
                    throw new GenericValidationException("Failed to read file", e);
                }
                break;
            case "acestream":
                try {
                    mDescriptor = TransportFileDescriptor.fromMrl(getContentResolver(), uri);
                    return;
                }
                catch(TransportFileParsingException e) {
                    Log.e(TAG, "error", e);
                    throw new GenericValidationException("Failed to parse URI", e);
                }
            case "magnet":
                builder.setMagnet(uri.toString());
                break;
            case "http":
            case "https":
            case "ftp":
                builder.setUrl(uri.toString());
                break;
            case "file":
                try {
                    builder.setContentUri(getContentResolver(), uri);
                }
                catch(IOException e) {
                    Log.e(TAG, "error", e);
                    throw new GenericValidationException("Failed to read file", e);
                }
                break;
            default:
                throw new GenericValidationException("Unsupported scheme");
        }

        mDescriptor = builder.build();
    }

    private void startLoadingFiles(final Intent intent) {
        if(!mRunning) {
            Logger.v(TAG, "startLoadingFiles: activity is stopped");
            return;
        }

	    ensureEngineService();

	    // Check mobile network connection
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                startLoadingFiles(intent);
            }
        };
        if(!checkMobileNetworkConnection(runnable)) {
            return;
        }

        try {
            initTransportFileDescriptorFromIntent(intent);
        }
        catch(GenericValidationException e) {
            Log.e(TAG, "Failed to get transport descriptor: " + e.getMessage());
            showError(e.getMessage());
            return;
        }

        // Load transport file
        updateInfoText(R.string.starting);
        mEngineService.getMediaFiles(mDescriptor, new Callback<MediaFilesResponse>() {
            @Override
            public void onSuccess(MediaFilesResponse result) {
                finishedLoadingFiles(result);
            }

            @Override
            public void onError(String err) {
                showError(err);
            }
        });
    }

    private void finishedLoadingFiles(@NonNull MediaFilesResponse response) {
        if(mPlaybackManager == null) {
            Logger.v(TAG, "finishedLoadingFiles: missing playback manager");
            return;
        }
        Logger.vv(TAG, "finishedLoadingFiles: response=" + response.toString());

	    if(response.files.length == 0) {
            showError("Missing media files");
            return;
        }

        mDescriptor.setTransportFileData(response.transport_file_data);
	    mDescriptor.setCacheKey(response.transport_file_cache_key);
	    mDescriptor.setInfohash(response.infohash);

        mMediaFiles = response;
        SelectedPlayer player = null;

        if(getIntent().hasExtra(Constants.EXTRA_SELECTED_PLAYER)) {
            Log.v(TAG, "finishedLoadingFiles: got selected player from extras");
            player = SelectedPlayer.fromIntentExtra(getIntent());
        }
        else if(!mSkipRememberedPlayer) {
            player = mPlaybackManager.getSelectedPlayer(true);
        }

	    if(player == null) {
	        if(BuildConfig.DEBUG) {
	            Log.v(TAG, "finishedLoadingFiles: no selected player, show resolver");
            }
	        showResolver();
        }
        else {
            if(BuildConfig.DEBUG) {
                Log.v(TAG, "finishedLoadingFiles: got selected player: player=" + player.toString());
            }
	        onPlayerSelected(player);
        }
    }

    @Override
    public void onFileSelected(int fileIndex) {
        Log.d(TAG, "onFileSelected: fileIndex=" + fileIndex);

        if(mSelectedPlayer == null) {
            throw new IllegalStateException("missing selected player");
        }

        boolean startOurPlayer = false;
        if(mSelectedPlayer.isOurPlayer()) {
            startOurPlayer = true;
        }
        else if(AceStreamEngineBaseApplication.useVlcBridge()
                && (mSelectedPlayer.type == SelectedPlayer.ACESTREAM_DEVICE
                || mSelectedPlayer.type == SelectedPlayer.CONNECTABLE_DEVICE)) {
            // In media app remote control is in VideoPlayerActivity
            startOurPlayer = true;
        }

        if(startOurPlayer) {
            startOurPlayer(mSelectedPlayer, fileIndex);
        }
        else {
            startExternalPlayer(fileIndex);
        }
    }

    private void startExternalPlayer(int fileIndex) {
	    Logger.v(TAG, "startExternalPlayer: fileIndex=" + fileIndex);

        final MediaFilesResponse.MediaFile mediaFile = mMediaFiles.getMediaFileByIndex(fileIndex);
        if(mediaFile == null) {
            Log.e(TAG, "onFileSelected: cannot select file: fileIndex=" + fileIndex);
            showError(R.string.failed_to_start);
            return;
        }

        if(mPlaybackManager == null) {
            Log.e(TAG, "onFileSelected: missing playback manager");
            showError(R.string.failed_to_start);
            return;
        }

        if(AceStreamEngineBaseApplication.useVlcBridge()) {
            VlcBridge.saveP2PPlaylist(mDescriptor, mMediaFiles, mediaFile);
        }

        mPlaybackManager.initPlaylist(mDescriptor, mMediaFiles, fileIndex);
        mPlaybackManager.startPlayer(
                this,
                mSelectedPlayer,
                mDescriptor,
                mediaFile,
                -1,
                mCastResultListener,
                new EngineSessionStartListener() {
                    @Override
                    public void onSuccess(EngineSession session) {
                        Logger.v(TAG, "engine session started");
                    }

                    @Override
                    public void onError(String error) {
                        Logger.v(TAG, "engine session failed: error=" + error);
                        showError(error);
                    }
                },
                -1,
                0,
                mProductKey,
                mKeepOriginalSessionInitiator
        );
    }

    @Override
    public void onDialogCancelled() {
        Log.d(TAG, "onDialogCancelled");
        exit();
    }

    private void showResolver() {
        Log.d(TAG, "showResolver");

        if(mMediaFiles == null) {
            throw new IllegalStateException("missing media files");
        }

        if(mMediaFiles.files.length == 0) {
            throw new IllegalStateException("empty media files");
        }

        MediaFilesResponse.MediaFile mf = mMediaFiles.files[0];
        Intent intent = new AceStream.Resolver.IntentBuilder(
                this,
                mf.infohash,
                mf.type,
                mf.mime)
                .allowRememberPlayer(mStartedFromExternalRequest)
                .build();
        startActivityForResult(intent, REQUEST_CODE_SELECT_PLAYER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.v(TAG, "onActivityResult: requestCode=" + requestCode + " resultCode=" + resultCode + " intent=" + data);
        if (requestCode == REQUEST_CODE_SELECT_PLAYER) {
            if(resultCode == Activity.RESULT_OK){
                SelectedPlayer player = SelectedPlayer.fromIntentExtra(data);
                Log.d(TAG, "onActivityResult: selected player: " + player.toString());
                onPlayerSelected(player);
            }
            else if(resultCode == AceStream.Resolver.RESULT_CLOSE_CALLER) {
                exit();
            }
            else {
                Log.i(TAG, "onActivityResult: resolver cancelled");
                exit();
            }
        }
        else if(requestCode == REQUEST_CODE_ADS_NOTIFICATION) {
            mShowingNotification = false;
            startEngineWhenConnected();
        }
    }

    private void startEngineWhenConnected() {
	    if(mPlaybackManager == null) {
	        mStartEngineWhenConnected = true;
        }
        else {
            mPlaybackManager.startEngine();
        }
    }

    private void onPlayerSelected(@NonNull SelectedPlayer player) {
        Log.v(TAG, "onPlayerSelected: player=" + player.toString());

        if(mPlaybackManager == null) {
            Log.e(TAG, "onPlayerSelected: missing playback manager");
            showError(R.string.failed_to_start);
            return;
        }

        mSelectedPlayer = player;
        AceStream.setLastSelectedPlayer(player);

        if(mMediaFiles.files.length > 1) {
            if(mSelectedFileIndex == -1) {
                // choose file before starting player
                showFileSelector();
            }
            else {
                // start selected file
                onFileSelected(mSelectedFileIndex);
            }
        }
        else {
            // start first file
            onFileSelected(mMediaFiles.files[0].index);
        }
    }

    private void showFileSelector() {
        int len = mMediaFiles.files.length;
        String[] fileNames = new String[len];
        int[] fileIndexes = new int[len];
        for(int i = 0; i < len; i++) {
            fileIndexes[i] = mMediaFiles.files[i].index;
            fileNames[i] = mMediaFiles.files[i].filename;
        }

        SelectFileDialogFragment dialogFragment = new SelectFileDialogFragment();
        Bundle bundle = new Bundle();
        bundle.putStringArray("fileNames", fileNames);
        bundle.putIntArray("fileIndexes", fileIndexes);
        dialogFragment.setArguments(bundle);

        try {
            dialogFragment.show(getSupportFragmentManager(), "select_file_dialog");
        }
        catch(IllegalStateException e) {
            // Catch possible error: "Can not perform this action after onSaveInstanceState"
            Log.e(TAG, "showFileSelector: error", e);
            // start first file
            onFileSelected(mMediaFiles.files[0].index);
        }
    }

    private void showEngineStatus(EngineStatus status) {
        String statusString = "";
        Resources res = getResources();
        switch(status.status) {
            case "err":
                showError(status.errorMessage == null ? "Unknown error" : status.errorMessage);
                return;
            case "prebuf":
                statusString = res.getString(R.string.status_prebuffering, status.progress, status.peers, status.speedDown);
                break;
            case "idle":
                statusString = res.getString(R.string.starting);
                break;
            case "check":
                statusString = res.getString(R.string.status_checking, status.progress);
                break;
            case "buf":
                statusString = res.getString(R.string.status_buffering, status.progress, status.peers, status.speedDown);
                break;
            case "dl":
                statusString = res.getString(R.string.starting_player);
                break;
        }

        updateInfoText(statusString);
    }

    private void startOurPlayer(@NonNull SelectedPlayer player, int fileIndex) {
        App.v(TAG, "startOurPlayer: player=" + player + " fileIndex=" + fileIndex);

        int playlistPosition = 0;
        if(fileIndex != -1) {
            for (int i = 0; i < mMediaFiles.files.length; i++) {
                if (mMediaFiles.files[i].index == fileIndex) {
                    playlistPosition = i;
                    break;
                }
            }
        }

        //NOTE: currently we don't use VLC bridge when got product key (which means that
        // playback was initiated by third-party app which passed this key).
        // Reason: there are no methods to pass product key through the bridge.
        // This is a fast temporary workaround.
        if(AceStreamEngineBaseApplication.useVlcBridge() && TextUtils.isEmpty(mProductKey)) {
            //TODO: how to pass mStartedFromExternalRequest?
            new VlcBridge.LoadP2PPlaylistIntentBuilder(mDescriptor)
                    .setPlayer(player)
                    .setMetadata(mMediaFiles)
                    .setMediaFiles(mMediaFiles.files)
                    .setPlaylistPosition(playlistPosition)
                    .send();
        }
        else {
            // Add to internal playlist
            if (mMediaFiles.files.length > 0) {
                mPlaybackManager.initPlaylist(mDescriptor, mMediaFiles, -1);
            }

            AceStreamPlayer.PlaylistItem[] playlist = new AceStreamPlayer.PlaylistItem[mMediaFiles.files.length];
            for(int i = 0; i < mMediaFiles.files.length; i++) {
                playlist[i] = new AceStreamPlayer.PlaylistItem(
                        mDescriptor.getMrl(mMediaFiles.files[i].index).toString(),
                        mMediaFiles.files[i].filename);
            }

            Intent intent = AceStreamPlayer.getPlayerIntent();
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(AceStreamPlayer.EXTRA_PLAYLIST, AceStreamPlayer.Playlist.toJson(playlist));
            intent.putExtra(AceStreamPlayer.EXTRA_PLAYLIST_POSITION, playlistPosition);
            if(!TextUtils.isEmpty(mProductKey)) {
                intent.putExtra(AceStreamPlayer.EXTRA_PRODUCT_KEY, mProductKey);
            }
            startActivity(intent);
        }

        mPlaybackManager.setLastSelectedDeviceId(null);
        mPlayerStarted = true;
        exit();
    }

    private void gotCastError(final String errorMessage) {
        Log.d(TAG, "Got error, show list of players: error=" + errorMessage);
        mIsWaiting = false;

        if(mActive) {
            if (errorMessage != null && errorMessage.length() > 0) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(AceStreamEngineBaseApplication.context(), errorMessage, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            showResolver();
        }
    }

    private void processSettings(ExtendedEnginePreferences prefs) {
		Editor edit = AceStreamEngineBaseApplication.getPreferences().edit();

        String outputFormatLive = MiscUtils.ifNull(prefs.output_format_live, Constants.PREFS_DEFAULT_OUTPUT_FORMAT_LIVE);
        String outputFormatVod = MiscUtils.ifNull(prefs.output_format_vod, Constants.PREFS_DEFAULT_OUTPUT_FORMAT_VOD);
        edit.putString("output_format_live", outputFormatLive);
        edit.putString("output_format_vod", outputFormatVod);
        edit.apply();

        Log.d(TAG, "got output formats from settings: live=" + outputFormatLive + " vod=" + outputFormatVod);
	}

    // PlaybackManager.Callback interface
    @Override
    public void onEngineConnected(ExtendedEngineApi service) {
        Log.d(TAG, "onEngineConnected: running=" + mRunning + " service=" + mEngineService);

        if(!mRunning) return;

        if(mEngineService == null) {
            mEngineService = service;

            // we can start content now, do it after we receive prefs
            startGettingPreferences();
        }
    }

    @Override
    public void onEngineFailed() {
        Log.d(TAG, "onEngineFailed");
        updateInfoText(R.string.start_fail);
    }

    @Override
    public void onEngineUnpacking() {
        Log.d(TAG, "onEngineUnpacking");
        updateInfoText(R.string.dialog_unpack);
    }

    @Override
    public void onEngineStarting() {
        Log.d(TAG, "onEngineStarting");
        updateInfoText(R.string.dialog_start);
    }

    @Override
    public void onEngineStopped() {
        Log.d(TAG, "onEngineStopped");
        exit();
    }
    //

    private void startRemoteControl(SelectedPlayer selectedPlayer) {
        Log.d(TAG, "start remote control: selectedPlayer=" + selectedPlayer);
        mIsWaiting = false;
        mPlayerStarted = true;
        Intent intent = new Intent(this, RemoteControlActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        if(selectedPlayer != null) {
            intent.putExtra("selectedPlayer", selectedPlayer.toJson());
        }
        startActivity(intent);
        exit();
    }

    // EngineStatusListener
    @Override
    public void onEngineStatus(final EngineStatus status, final IRemoteDevice device) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                showEngineStatus(status);
            }
        });

    }

    @Override
    public boolean updatePlayerActivity() {
        return mActive;
    }

    public static Intent makeIntentFromDescriptor(
            @NonNull Context context,
            @NonNull TransportFileDescriptor descriptor,
            @Nullable SelectedPlayer player) {
        Intent intent = new Intent(context, ContentStartActivity.class);
        intent.putExtra(Constants.EXTRA_TRANSPORT_DESCRIPTOR, descriptor.toJson());
        if(player != null) {
            intent.putExtra(Constants.EXTRA_SELECTED_PLAYER, player.toJson());
        }
        AceStream.putTransportFileToCache(
                descriptor.getDescriptorString(),
                descriptor.getTransportFileData());
        return intent;
    }

    public static Intent makeIntentFromInfohash(
            @NonNull Context context,
            @NonNull String infohash,
            @Nullable SelectedPlayer player) {
        Intent intent = new Intent(context, ContentStartActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("magnet:?xt=urn:btih:" + infohash));
        if(player != null) {
            intent.putExtra(Constants.EXTRA_SELECTED_PLAYER, player.toJson());
        }
        if(!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        return intent;
    }

    public static Intent makeIntentFromContentId(
            @NonNull Context context,
            @NonNull String contentId,
            @Nullable SelectedPlayer player) {
        Intent intent = new Intent(context, ContentStartActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(Uri.parse("acestream://" + contentId));
        if(player != null) {
            intent.putExtra(Constants.EXTRA_SELECTED_PLAYER, player.toJson());
        }
        return intent;
    }

    public static Intent makeIntentFromTransportFileUrl(
            @NonNull Context context,
            @NonNull String transportFileUrl,
            @Nullable SelectedPlayer player) {
        Intent intent = new Intent(context, ContentStartActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(transportFileUrl));
        if(player != null) {
            intent.putExtra(Constants.EXTRA_SELECTED_PLAYER, player.toJson());
        }
        return intent;
    }

    /**
     * Make intent from acestream: URI
     * @param context
     * @param uri
     * @param player
     * @return
     */
    public static Intent makeIntentFromUri(
            @NonNull Context context,
            @NonNull Uri uri,
            @Nullable SelectedPlayer player,
            boolean startedFromExternalRequest,
            boolean skipRememberedPlayer) {

        if(!TextUtils.equals(uri.getScheme(), "acestream")) {
            throw new IllegalStateException("acestream: scheme expected");
        }

        Intent intent = new Intent(context, ContentStartActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(uri);
        intent.putExtra(Constants.EXTRA_STARTED_FROM_EXTERNAL_REQUEST, startedFromExternalRequest);
        intent.putExtra(Constants.EXTRA_SKIP_REMEMBERED_PLAYER, skipRememberedPlayer);
        if(player != null) {
            intent.putExtra(Constants.EXTRA_SELECTED_PLAYER, player.toJson());
        }
        return intent;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        App.v(TAG, "onRequestPermissionsResult: requestCode=" + requestCode);
        if(requestCode == MainActivity.REQUEST_CODE_PERMISSIONS) {
            // If request is cancelled, the result arrays are empty.

            int i;
            for(i=0; i<permissions.length; i++) {
                Log.d(TAG, "grant: i=" + i + " permission=" + permissions[i]);
            }
            for(i=0; i<grantResults.length; i++) {
                Log.d(TAG, "grant: i=" + i + " result=" + grantResults[i]);
            }

            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                Log.d(TAG, "user granted permission");

            } else {
                Log.d(TAG, "user denied permission");
                mGotStorageAccess = 0;
            }
        }
    }

    private void initInterstitialAd(@NonNull AdManager adManager) {
        final boolean loadPreroll;
        final boolean loadPause;
        final boolean loadClose;
        boolean useTestAds = BuildConfig.admobUseTestAds;

        if(hasNoAds()) {
            // Users with NoAds can control ad placement
            loadPreroll = AceStreamEngineBaseApplication.showAdsOnPreroll();
            loadPause = AceStreamEngineBaseApplication.showAdsOnPause();
            loadClose = AceStreamEngineBaseApplication.showAdsOnClose();
        }
        else {
            loadPreroll = true;
            loadPause = true;
            loadClose = true;
        }

        App.v(TAG, "ads:initInterstitialAd:"
                + " preroll=" + loadPreroll
                + " pause=" + loadPause
                + " close=" + loadClose
        );

        if(loadPreroll) {
            adManager.initInterstitial(
                this,
                "preroll",
                useTestAds
                    ? ADMOB_TEST_INTERSTITIAL
                    : AceStreamEngineBaseApplication.getStringAppMetadata("adMobInterstitialPrerollId"),
                null
                );
            adManager.loadInterstitial("preroll");
        }

        if(loadPause) {
            adManager.initInterstitial(
                this,
                "pause",
                BuildConfig.admobUseTestAds
                    ? ADMOB_TEST_INTERSTITIAL
                    : AceStreamEngineBaseApplication.getStringAppMetadata("adMobInterstitialPauseId"),
                null);
            adManager.loadInterstitial("pause");
        }

        if(loadClose) {
            adManager.initInterstitial(
                this,
                "close",
                BuildConfig.admobUseTestAds
                    ? ADMOB_TEST_INTERSTITIAL
                    : AceStreamEngineBaseApplication.getStringAppMetadata("adMobInterstitialCloseId"),
                null);
            adManager.loadInterstitial("close");
        }
    }

    private void initAppodeal(@NonNull AdConfig adConfig) {
        boolean loadInterstitial = true;
        boolean loadRv = isUserLoggedIn();

        if(hasNoAds()) {
            // Users with NoAds can control ad placement
            loadRv = AceStreamEngineBaseApplication.showAdsOnPreroll();
            loadInterstitial = AceStreamEngineBaseApplication.showAdsOnPreroll()
                    || AceStreamEngineBaseApplication.showAdsOnPause()
                    || AceStreamEngineBaseApplication.showAdsOnClose();
        }

        int adTypes = 0;
        if(loadInterstitial) {
            adTypes |= Appodeal.INTERSTITIAL;
        }
        if(loadRv) {
            adTypes |= Appodeal.REWARDED_VIDEO;
        }

        App.v(TAG, "initAppodeal: interstitial=" + loadInterstitial + " rv=" + loadRv);

        if(adTypes > 0) {
            AceStreamEngineBaseApplication.initAppodeal(
                    -1,
                    this,
                    adTypes,
                    true,
                    adConfig);
        }
    }

    private boolean isUserLoggedIn() {
        if(mPlaybackManager == null) {
            return false;
        }

        return mPlaybackManager.getAuthLevel() > 0;
    }

    private boolean hasNoAds() {
        if(mPlaybackManager == null) {
            return false;
        }

        return AuthUtils.hasNoAds(mPlaybackManager.getAuthLevel());
    }
}
