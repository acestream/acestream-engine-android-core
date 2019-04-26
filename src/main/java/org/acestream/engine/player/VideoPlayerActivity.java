package org.acestream.engine.player;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.app.PictureInPictureParams;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import androidx.databinding.BindingAdapter;
import androidx.databinding.ObservableField;
import androidx.databinding.ObservableInt;
import androidx.databinding.ObservableLong;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;
import androidx.core.view.GestureDetectorCompat;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.ViewStubCompat;
import androidx.recyclerview.widget.ItemTouchHelper;

import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import android.util.Rational;
import android.view.Display;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import com.appodeal.ads.Appodeal;
import com.appodeal.ads.InterstitialCallbacks;
import com.appodeal.ads.RewardedVideoCallbacks;
import com.google.ads.interactivemedia.v3.api.Ad;
import com.google.ads.interactivemedia.v3.api.AdDisplayContainer;
import com.google.ads.interactivemedia.v3.api.AdErrorEvent;
import com.google.ads.interactivemedia.v3.api.AdEvent;
import com.google.ads.interactivemedia.v3.api.AdPodInfo;
import com.google.ads.interactivemedia.v3.api.AdsLoader;
import com.google.ads.interactivemedia.v3.api.AdsManager;
import com.google.ads.interactivemedia.v3.api.AdsManagerLoadedEvent;
import com.google.ads.interactivemedia.v3.api.AdsRenderingSettings;
import com.google.ads.interactivemedia.v3.api.AdsRequest;
import com.google.ads.interactivemedia.v3.api.ImaSdkFactory;
import com.google.ads.interactivemedia.v3.api.ImaSdkSettings;
import com.google.ads.interactivemedia.v3.api.player.ContentProgressProvider;
import com.google.ads.interactivemedia.v3.api.player.VideoAdPlayer;
import com.google.ads.interactivemedia.v3.api.player.VideoProgressUpdate;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.reward.RewardItem;
import com.google.android.gms.ads.reward.RewardedVideoAdListener;

import org.acestream.engine.AceStreamManagerImpl;
import org.acestream.engine.BaseAppCompatActivity;
import org.acestream.engine.AceStreamEngineBaseApplication;
import org.acestream.engine.BuildConfig;
import org.acestream.engine.ContentStartActivity;
import org.acestream.engine.PlaybackManager;
import org.acestream.engine.R;
import org.acestream.engine.RemoteControlActivity;
import org.acestream.engine.ads.AdManager;
import org.acestream.engine.ads.AdsWaterfall;
import org.acestream.engine.aliases.App;
import org.acestream.engine.controller.Callback;
import org.acestream.engine.controller.ExtendedEngineApi;
import org.acestream.engine.databinding.AcePlayerHudBinding;
import org.acestream.sdk.AceStream;
import org.acestream.sdk.Constants;
import org.acestream.sdk.ContentStream;
import org.acestream.sdk.EngineSession;
import org.acestream.sdk.EngineStatus;
import org.acestream.sdk.MediaItem;
import org.acestream.engine.Playlist;
import org.acestream.sdk.SelectedPlayer;
import org.acestream.sdk.SystemUsageInfo;
import org.acestream.engine.acecast.client.AceStreamRemoteDevice;
import org.acestream.sdk.JsonRpcMessage;
import org.acestream.engine.acecast.server.AceStreamDiscoveryServerService;
import org.acestream.sdk.controller.api.TransportFileDescriptor;
import org.acestream.sdk.controller.api.response.AdConfig;
import org.acestream.sdk.controller.api.response.ClickThroughUrl;
import org.acestream.sdk.controller.api.response.MediaFilesResponse;
import org.acestream.sdk.controller.api.response.RequestAdsResponse;
import org.acestream.sdk.controller.api.response.VastTag;
import org.acestream.sdk.errors.TransportFileParsingException;
import org.acestream.sdk.interfaces.EngineCallbackListener;
import org.acestream.sdk.interfaces.EngineStatusListener;
import org.acestream.sdk.interfaces.IRemoteDevice;
import org.acestream.sdk.player.api.AceStreamPlayer;
import org.acestream.sdk.utils.AuthUtils;
import org.acestream.sdk.utils.Logger;
import org.acestream.sdk.utils.MiscUtils;
import org.acestream.sdk.utils.VlcBridge;
import org.acestream.sdk.utils.VlcConstants;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.videolan.libvlc.IVLCVout;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.AndroidUtil;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static org.acestream.engine.Constants.ADMOB_TEST_INTERSTITIAL;

public class VideoPlayerActivity extends BaseAppCompatActivity
        implements
        PlayerSettingsHandler,
        AdsWaterfall.InventoryHolder,
        AdErrorEvent.AdErrorListener,
        IVLCVout.Callback,
        IVLCVout.OnNewVideoLayoutListener,
        OnClickListener,
        EngineCallbackListener,
        PlaylistManager.Player
{
    protected final static String TAG = "AS/Player";

    private final static int REQUEST_CODE_SELECT_PLAYER = 1;
    public final static String SLEEP_INTENT = Utils.buildPkgString("SleepIntent");

    // 99=sensor
    private final static int DEFAULT_SCREEN_ORIENTATION = 99;

    private static final boolean ADS_ENABLE_PRELOAD = false;
    private static final int AD_PLAYER_PLAYBACK_TIMEOUT = 8000;

    // hardcoded debug flags
    private static final boolean DEBUG_LOG_ENGINE_STATUS = false;
    private static final boolean DEBUG_AUDIO_OUTPUT_SWITCHER = false;

    private LibVLC mLibVlc = null;
    private String mUserAgent = null;
    private ArrayList<String> mLibVlcOptions = null;

    private boolean mShowTvUi;
    private boolean mAudioDigitalOutputEnabled;
    private int mHardwareAcceleration;
    private String mAout;
    private String mBroadcastAction;
    private MetaDataManager mMetaDataManager;
    private SurfaceView mSurfaceView = null;
    private SurfaceView mSubtitlesSurfaceView = null;
    private View mRootView;
    private FrameLayout mSurfaceFrame;
    private boolean mStartedFromExternalRequest = false;
    private final List<Runnable> mPlaybackManagerOnReadyQueue = new ArrayList<>();
    private boolean mRenderAds = false;
    private boolean mShowUnpauseAdsOnResume = false;
    private boolean mAppodealInitialized = false;
    private AdsWaterfall mAdsWaterfall;
    private AdManager mAdManager;
    //private RewardedVideoAd mRewardedVideoAdPreroll;
    protected ExtendedEngineApi mEngineService = null;
    private VastTag[] mAdTags = null;
    private int mCurrentAdTagIndex = -1;
    private int mCountAdsLoaded = 0;
    // The container for the ad's UI.
    private ViewGroup mAdUiContainer;
    protected boolean mMidrollAdsRequested = false;
    protected boolean mIsAdDisplayed = false;
    protected AdSource mAdSource = null;
    protected ImaSdkFactory mSdkFactory;
    protected AdsLoader mAdsLoader;
    protected List<AdsManager> mAdsManagers;

    protected boolean mRemotePlayckStarted = false;
    private PowerManager.WakeLock mWakeLock;
    protected AceStreamDiscoveryServerService.Client mDiscoveryServerServiceClient = null;
    protected String mRemoteClientId = null;
    protected String mLastRemoteClientDeviceId = null;
    protected EngineStatus mLastEngineStatus = null;
    protected boolean mIsLive;
    protected long freezeEngineStatusAt = 0;
    protected long freezeEngineStatusFor = 0;
    protected long freezeLiveStatusAt = 0;
    protected long freezeLivePosAt = 0;
    protected EngineStatus.LivePosition mLastLivePos = null;
    protected PlaybackManager mPlaybackManager = null;
    protected boolean mPictureInPictureMode = false;
    protected boolean mSwitchingToAnotherPlayer = false;
    protected boolean mSwitchingToAnotherRenderer = false;
    protected boolean mStoppingOnDeviceDisconnect = false;
    // exit activity when player stops
    protected boolean mExitOnStop = true;
    // we have stopped to restart player
    protected boolean mRestartingPlayer = false;
    protected boolean mWasStopped = false;
    protected TextView mEngineStatus;
    protected TextView mDebugInfo;

    protected boolean mIsStarted = false;
    protected boolean mIsPaused = true;
    protected boolean mIsInBackground = false;

    private MediaPlayer mMediaPlayer;

    // player ui
    private RelativeLayout mPlayerUiContainer;

    // custom ad player
    private FrameLayout mAdPlayerContainer;
    private RelativeLayout mAdPlayerUiContainer;
    private FrameLayout mAdPlayerSurfaceFrame;
    private SurfaceView mAdPlayerSurfaceView;
    private SurfaceView mAdPlayerSubtitlesSurfaceView;
    private MediaPlayer mAdPlayer;
    private OnLayoutChangeListener mAdPlayerOnLayoutChangeListener;
    private boolean mUseCustomAdPlayer = true;
    private TextView mAdPlayerTimeLeft;
    private TextView mAdPlayerButtonClick;

    // Skip button elements
    private LinearLayout mAdPlayerSkipContainer;
    private TextView mAdPlayerSkipText;

    // IMA SDK custom player
    private VideoAdPlayer mVideoAdPlayer;
    private List<VideoAdPlayer.VideoAdPlayerCallback> mImaSdkAdCallbacks = new ArrayList<>(1);

    // custom ads
    private LinearLayout mCustomAdsContainer;
    private Button mButtonShowBonusAds;
    private Button mButtonSkipBonusAds;
    private CheckBox mCheckboxShowRewardedAds;

    // ad settings
    private AdSettings mAdSettings = new AdSettings();

    private final static int FREEZE_LIVE_STATUS_FOR = 5000;
    private final static int FREEZE_LIVE_POS_FOR = 5000;
    private static final int PLAYER_STATUS_UPDATE_INTERVAL = 1000;
    protected long mSeekOnStart = -1;
    private TransportFileDescriptor mDescriptor = null;
    private boolean mAskResume = true;
    private boolean mPlayFromStart = true;
    private boolean mIsRtl;
    private GestureDetectorCompat mDetector = null;

    private PlaylistManager mPlaylist;
    private ImageView mPlaylistToggle;
    private ImageView mPipToggle;
    protected ImageView mSwitchPlayer;
    private ImageView mAdvOptionsButton;
    private RecyclerView mPlaylistView;
    private PlaylistAdapter mPlaylistAdapter;

    protected int mCurrentSize;

    protected MediaSessionCompat mMediaSession = null;
    protected MediaSessionService.Client mMediaSessionServiceClient = null;

    @Override
    public boolean allowCustomAds() {
        return isUserLoggedIn();
    }

    @Override
    public boolean loadInventory(final String inventory) {
        App.v(TAG, "ads:loadInventory: inventory=" + inventory);

        if(TextUtils.equals(inventory, AdsWaterfall.Inventory.VAST)) {
            if(TextUtils.equals(mAdsWaterfall.getPlacement(), AdsWaterfall.Placement.PREROLL)) {
                mAdsWaterfall.onLoading(AdsWaterfall.Inventory.VAST);
                if (!requestVastAds()) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mAdsWaterfall.onFailed(AdsWaterfall.Inventory.VAST);
                        }
                    });
                }
            }
        }
        else if(TextUtils.equals(inventory, AdsWaterfall.Inventory.APPODEAL_INTERSTITIAL)) {
            if(Appodeal.isLoaded(Appodeal.INTERSTITIAL)) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (Appodeal.isLoaded(Appodeal.INTERSTITIAL)) {
                            App.v(TAG, "loadInventory: interstitial was loaded");
                            mAdsWaterfall.onLoaded(AdsWaterfall.Inventory.APPODEAL_INTERSTITIAL);
                        }
                    }
                });
            }
        }
        else if(TextUtils.equals(inventory, AdsWaterfall.Inventory.APPODEAL_REWARDED_VIDEO)) {
            if(mAdManager != null && mAdManager.isProviderEnabled(AdManager.ADS_PROVIDER_APPODEAL)) {
                if (Appodeal.isLoaded(Appodeal.REWARDED_VIDEO)) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (Appodeal.isLoaded(Appodeal.REWARDED_VIDEO)) {
                                App.v(TAG, "loadInventory: rv was loaded");
                                mAdsWaterfall.onLoaded(inventory);
                            }
                        }
                    });
                }
            }
            else {
                Logger.v(TAG, "ads:loadInventory: appodeal disabled: inventory=" + inventory);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mAdsWaterfall.onFailed(inventory);
                    }
                });
            }
        }
        else if(TextUtils.equals(inventory, AdsWaterfall.Inventory.ADMOB_REWARDED_VIDEO)) {
            if(mAdManager != null && mAdManager.isProviderEnabled(AdManager.ADS_PROVIDER_ADMOB)) {
                if (mAdManager.isRewardedVideoLoaded()) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (mAdManager.isRewardedVideoLoaded()) {
                                App.v(TAG, "loadInventory: was loaded: inventory=" + inventory);
                                mAdsWaterfall.onLoaded(inventory);
                            }
                        }
                    });
                }
            }
            else {
                Logger.v(TAG, "ads:loadInventory: admob disabled: inventory=" + inventory);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mAdsWaterfall.onFailed(inventory);
                    }
                });
            }
        }
        else if(TextUtils.equals(inventory, AdsWaterfall.Inventory.ADMOB_INTERSTITIAL_PREROLL)) {
            if(mAdManager != null) {
                if (mAdManager.isInterstitialLoaded("preroll")) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (mAdManager.isInterstitialLoaded("preroll")) {
                                App.v(TAG, "loadInventory: was loaded: inventory=" + inventory);
                                mAdsWaterfall.onLoaded(AdsWaterfall.Inventory.ADMOB_INTERSTITIAL_PREROLL);
                            }
                        }
                    });
                }
            }
            else {
                Log.e(TAG, "ads:loadInventory: missing ad manager: inventory=" + inventory);
            }
        }
        else if(TextUtils.equals(inventory, AdsWaterfall.Inventory.ADMOB_INTERSTITIAL_PAUSE)) {
            if(mAdManager != null) {
                if (mAdManager.isInterstitialLoaded("pause")) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (mAdManager.isInterstitialLoaded("pause")) {
                                App.v(TAG, "loadInventory: was loaded: inventory=" + inventory);
                                mAdsWaterfall.onLoaded(AdsWaterfall.Inventory.ADMOB_INTERSTITIAL_PAUSE);
                            }
                        }
                    });
                }
            }
            else {
                Log.e(TAG, "ads:loadInventory: missing ad manager: inventory=" + inventory);
            }
        }
        else if(TextUtils.equals(inventory, AdsWaterfall.Inventory.ADMOB_INTERSTITIAL_CLOSE)) {
            if(mAdManager != null) {
                if (mAdManager.isInterstitialLoaded("close")) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (mAdManager.isInterstitialLoaded("close")) {
                                App.v(TAG, "loadInventory: was loaded: inventory=" + inventory);
                                mAdsWaterfall.onLoaded(AdsWaterfall.Inventory.ADMOB_INTERSTITIAL_CLOSE);
                            }
                        }
                    });
                }
            }
            else {
                Log.e(TAG, "ads:loadInventory: missing ad manager: inventory=" + inventory);
            }
        }

        return false;
    }

    @Override
    public boolean showInventory(String placement, String inventory) {
        if(!canShowAds(placement, inventory)) {
            App.v(TAG, "ads:showInventory: cannot show now: placement=" + placement + " inventory=" + inventory);
            return false;
        }

        App.v(TAG, "ads:showInventory: placement=" + placement + " inventory=" + inventory);

        if(TextUtils.equals(inventory, AdsWaterfall.Inventory.VAST)) {
            if(TextUtils.equals(placement, AdsWaterfall.Placement.UNPAUSE)) {
                if (mMidrollAdsRequested) {
                    if (mAdsManagers.size() > 0) {
                        mAdsManagers.get(0).start();
                        return true;
                    }
                }
            }
        }
        else if(TextUtils.equals(inventory, AdsWaterfall.Inventory.ADMOB_INTERSTITIAL_PREROLL)) {
            if(mAdManager != null) {
                if (mAdManager.showInterstitial("preroll")) {
                    return true;
                }
            }
            else {
                Log.e(TAG, "ads:showInventory: missing ad manager: inventory=" + inventory);
            }
        }
        else if(TextUtils.equals(inventory, AdsWaterfall.Inventory.ADMOB_INTERSTITIAL_PAUSE)) {
            if(mAdManager != null) {
                if (mAdManager.showInterstitial("pause")) {
                    return true;
                }
            }
            else {
                Log.e(TAG, "ads:showInventory: missing ad manager: inventory=" + inventory);
            }
        }
        else if(TextUtils.equals(inventory, AdsWaterfall.Inventory.ADMOB_INTERSTITIAL_CLOSE)) {
            if(mAdManager != null) {
                if (mAdManager.showInterstitial("close")) {
                    return true;
                }
            }
            else {
                Log.e(TAG, "ads:showInventory: missing ad manager: inventory=" + inventory);
            }
        }
        else if(TextUtils.equals(inventory, AdsWaterfall.Inventory.ADMOB_REWARDED_VIDEO)) {
            if (mAdManager != null && mAdManager.isRewardedVideoLoaded()) {
                mAdManager.showRewardedVideo();
                return true;
            }
        }
        else if(TextUtils.equals(inventory, AdsWaterfall.Inventory.APPODEAL_REWARDED_VIDEO)) {
            mAdsWaterfall.resetInventoryStatus(AdsWaterfall.Inventory.APPODEAL_REWARDED_VIDEO);
            Appodeal.show(this, Appodeal.REWARDED_VIDEO, placement);
            return true;
        }
        else if(TextUtils.equals(inventory, AdsWaterfall.Inventory.APPODEAL_INTERSTITIAL)) {
            mAdsWaterfall.resetInventoryStatus(AdsWaterfall.Inventory.APPODEAL_INTERSTITIAL);
            Appodeal.show(this, Appodeal.INTERSTITIAL, placement);
            return true;
        }
        else if(TextUtils.equals(inventory, AdsWaterfall.Inventory.CUSTOM)) {
            if(AceStreamEngineBaseApplication.showRewardedAds()) {
                return mAdsWaterfall.showCustomRewardedVideo();
            }
            else {
                return showCustomAds();
            }
        }
        else {
            App.v(TAG, "ads:showInventory: unknown inventory: " + inventory);
        }

        return false;
    }

    @Override
    public void stop() {
        exit();
    }

    @Override
    public void play(final MediaItem item) {
        if(!mIsStarted) {
            Log.v(TAG, "play: activity is stopped");
            return;
        }

        Logger.v(TAG, "play: item=" + item);

        if(item != null && item.getPlaybackUri() != null) {

            Media media = new Media(getLibVlc(), item.getPlaybackUri());
            setMediaOptions(media);
            media.setEventListener(mMediaEventListener);
            if(item.getUserAgent() != null) {
                setUserAgent(item.getUserAgent());
            }

            if(TextUtils.isEmpty(mTitle.get())) {
                mTitle.set(item.getTitle());
                updateMediaSessionMetadata();
            }

            long savedTime = mSeekOnStart == -1
                    ? mMetaDataManager.getLong(item, MetaDataManager.META_SAVED_TIME, -1)
                    : mSeekOnStart;
            App.v(TAG, "play: savedTime=" + savedTime + " seekOnStart=" + mSeekOnStart + " ask=" + mAskResume + " fromStart=" + mPlayFromStart);
            if(savedTime > 0 && !mPlayFromStart) {
                if(mAskResume) {
                    // This will reset mAskResume and call play() with corresponding mPlayFromStart
                    showConfirmResumeDialog();
                    return;
                }
                else {
                    mSeekOnStart = savedTime;
                    saveMediaTime(item, savedTime);
                }
            }
            else {
                mSeekOnStart = -1;
            }

            // Consume this flag only once
            mPlayFromStart = true;

            mIsLive = item.isLive();
            updateSwitchPlayerButton();
            updateSeekable(isSeekable());

            mMediaPlayer.setEventListener(null);
            mMediaPlayer.setMedia(media);
            mMediaPlayer.setEventListener(mMediaPlayerEventListener);
            mMediaPlayer.play();
            media.release();
        }
    }

    @Override
    public PlaybackManager getPlaybackManager() {
        return mPlaybackManager;
    }

    enum AdSource {
        IMA_SDK,
        INTERSTITIAL_AD,
        REWARDED_VIDEO,
        CUSTOM_ADS,
    }

    @Override
    public void onAdError(AdErrorEvent adErrorEvent) {
        // Failed to load ads, ad manager was not created
        Log.e(TAG, "ads:event:error: " + adErrorEvent.getError().getMessage());

        // Request next ad tag
        if(!requestVastAds()) {
            if (mAdsWaterfall != null) {
                mAdsWaterfall.onFailed(AdsWaterfall.Inventory.VAST);
            }
        }
    }

    public void onAdError(@NonNull AdsManager manager, AdErrorEvent adErrorEvent) {
        int adManagerIndex = mAdsManagers.indexOf(manager);

        Log.e(TAG, "ads:event:error: manager=" + adManagerIndex + "/" + mAdsManagers.size() + " error=" + adErrorEvent.getError().getMessage());

        mAdsManagers.remove(manager);
        if(mAdsManagers.size() == 0) {
            Log.e(TAG, "ads:event:error: all managers failed");
            if (mAdsWaterfall != null) {
                mAdsWaterfall.onFailed(AdsWaterfall.Inventory.VAST);
            }
        }
        else if(adManagerIndex == 0) {
            // First manager failed. Move to next.
            Log.e(TAG, "ads:event:error: request next manager");
            requestVastAds();
        }
    }

    public void onAdEvent(AdsManager manager, AdEvent adEvent) {
        int adPosition = -1;
        int totalAds = 0;
        int adManagerIndex = mAdsManagers.indexOf(manager);

        Log.v(TAG, "ads:event: " + adEvent.getType() + " ads_loaded=" + mCountAdsLoaded + " manager=" + adManagerIndex + "/" + mAdsManagers.size());

        if(adEvent.getAd() != null) {
            AdPodInfo i = adEvent.getAd().getAdPodInfo();
            if(i != null) {
                adPosition = i.getAdPosition();
                totalAds = i.getTotalAds();
                App.v(TAG, "ads:event:adpod: pos=" + i.getAdPosition()
                        + " pod_index=" + i.getPodIndex()
                        + " total=" + i.getTotalAds());
            }
        }

        // These are the suggested event types to handle. For full list of all ad event
        // types, see the documentation for AdEvent.AdEventType.
        switch (adEvent.getType()) {
            case LOG:
                App.v(TAG, "ads:ima_sdk_log: " + adEvent.toString());
                break;
            case LOADED:
                // AdEventType.LOADED will be fired when ads are ready to be played.
                // AdsManager.start() begins ad playback. This method is ignored for VMAP or
                // ad rules playlists, as the SDK will automatically start executing the
                // playlist.

                if(adEvent.getAd() != null) {
                    App.v(TAG, "ads:loaded:"
                            + " skippable=" + adEvent.getAd().isSkippable()
                            + " offset=" + adEvent.getAd().getSkipTimeOffset()
                            + " survey=" + adEvent.getAd().getSurveyUrl()
                            + " tp=" + adEvent.getAd().getTraffickingParameters()
                        );
                }

                if(mAdsWaterfall != null) {
                    mAdsWaterfall.onLoaded(AdsWaterfall.Inventory.VAST);
                }

                if(!mMidrollAdsRequested) {
                    // Notify waterfall that ads are loaded
                    notifyAdsLoaded();
                }

                mCountAdsLoaded += 1;
                if(!mMidrollAdsRequested && adManagerIndex == 0) {
                    App.v(TAG, "ads: start ad manager");
                    manager.start();
                }

                if(adPosition != -1 && adPosition >= totalAds) {
                    requestVastAds();
                }
                break;
            case SKIPPED:
            case COMPLETED:
                String source = mMidrollAdsRequested ? "vast:player:midroll" : "vast:player:preroll";
                addCoins(source, 0, true);
                AceStreamEngineBaseApplication.getInstance().logAdImpression(
                        AdManager.ADS_PROVIDER_VAST,
                        AdsWaterfall.Placement.PREROLL,
                        AdsWaterfall.AdType.VAST);
                break;
            case CONTENT_PAUSE_REQUESTED:
                // AdEventType.CONTENT_PAUSE_REQUESTED is fired immediately before a video
                // ad is played.
                onContentPauseRequested(AdSource.IMA_SDK);
                break;
            case CONTENT_RESUME_REQUESTED:
                // AdEventType.CONTENT_RESUME_REQUESTED is fired when the ad is completed
                // and you should start playing your content.
                onContentResumeRequested(AdSource.IMA_SDK);
                break;
            case ALL_ADS_COMPLETED:
                manager.destroy();
                mAdsManagers.remove(manager);

                if(mAdsManagers.size() > 0) {
                    if(mAdsManagers.get(0).getCurrentAd() != null) {
                        mAdsManagers.get(0).start();
                    }
                }
                break;
            default:
                break;
        }
    }

    private void showAds(AdSource source) {
        Log.v(TAG, "ads:event:showAds");

        mIsAdDisplayed = true;
        mAdSource = source;

        if(source == AdSource.IMA_SDK) {
            if(!mUseCustomAdPlayer) {
                mAdUiContainer.setVisibility(View.VISIBLE);
            }
            mSurfaceFrame.setPadding(9999, 0, 0, 0);
        }
    }

    private void hideAds(AdSource source) {
        Log.v(TAG, "ads:event:hideAds");
        mIsAdDisplayed = false;
        mAdSource = null;

        if(source == AdSource.IMA_SDK) {
            if(mUseCustomAdPlayer) {
                hideAdPlayer();
            }
            else {
                mAdUiContainer.setVisibility(View.GONE);
            }
            mSurfaceFrame.setPadding(0, 0, 0, 0);
        }
    }

    private static final int TOUCH_FLAG_AUDIO_VOLUME = 1;
    private static final int TOUCH_FLAG_BRIGHTNESS = 1 << 1;
    private static final int TOUCH_FLAG_SEEK = 1 << 2;
    private int mTouchControls = 0;

    /** Overlay */
    private ActionBar mActionBar;
    private ViewGroup mActionBarView;
    private static final int OVERLAY_TIMEOUT = 4000;
    protected static final int OVERLAY_INFINITE = -1;
    private static final int FADE_OUT = 1;
    private static final int SHOW_PROGRESS = 2;
    private static final int FADE_OUT_INFO = 3;
    private static final int INIT_PLAYBACK = 4;
    private static final int RESET_BACK_LOCK = 6;
    private static final int SHOW_INFO = 9;
    private static final int HIDE_INFO = 10;

    private boolean mDragging;
    private boolean mShowing;
    private boolean mShowingDialog;
    private TextView mInfo;
    private View mOverlayInfo;
    private View mVerticalBar;
    private View mVerticalBarProgress;
    private View mVerticalBarBoostProgress;
    private boolean mIsLoading;
    private boolean mIsPlaying = false;
    private boolean mIsBuffering = false;
    private boolean mMediaStartedPlaying = false;
    private ProgressBar mLoading;
    private int mScreenOrientation = DEFAULT_SCREEN_ORIENTATION;
    private int mScreenOrientationLock;
    private int mCurrentScreenOrientation;
    private String KEY_REMAINING_TIME_DISPLAY = "remaining_time_display";
    private String KEY_BLUETOOTH_DELAY = "key_bluetooth_delay";
    private long mSpuDelay = 0L;
    private long mAudioDelay = 0L;

    private boolean mIsLocked = false;
    /* -1 is a valid track (Disable) */
    private int mLastAudioTrack = -2;
    private int mLastSpuTrack = -2;
    private int mOverlayTimeout = 0;
    private boolean mLockBackButton = false;
    boolean mWasPaused = false;

    // size of the ad player video
    private int mAdPlayerVideoHeight;
    private int mAdPlayerVideoWidth;
    private int mAdPlayerVideoVisibleHeight;
    private int mAdPlayerVideoVisibleWidth;
    private int mAdPlayerSarNum;
    private int mAdPlayerSarDen;

    // size of the video
    private int mVideoHeight;
    private int mVideoWidth;
    private int mVideoVisibleHeight;
    private int mVideoVisibleWidth;
    private int mSarNum;
    private int mSarDen;

    //Volume
    private AudioManager mAudioManager;
    private int mAudioMax;
    private boolean mMute = false;
    private int mVolSave;
    private float mVol;
    private float mOriginalVol;
    private Toast warningToast;

    //Touch Events
    private static final int TOUCH_NONE = 0;
    private static final int TOUCH_VOLUME = 1;
    private static final int TOUCH_BRIGHTNESS = 2;
    private static final int TOUCH_MOVE = 3;
    private static final int TOUCH_SEEK = 4;
    private int mTouchAction = TOUCH_NONE;
    private int mSurfaceYDisplayRange, mSurfaceXDisplayRange;
    private float mInitTouchY, mTouchY =-1f, mTouchX=-1f;
    private boolean mSkipTouch = false;

    //stick event
    private static final int JOYSTICK_INPUT_DELAY = 300;
    private long mLastMove;

    // Brightness
    private boolean mIsFirstBrightnessGesture = true;
    private float mRestoreAutoBrightness = -1f;

    // Tracks & Subtitles
    private MediaPlayer.TrackDescription[] mAudioTracksList;
    private MediaPlayer.TrackDescription[] mVideoTracksList;
    private MediaPlayer.TrackDescription[] mSubtitleTracksList;

    /**
     * Flag to indicate whether the media should be paused once loaded
     * (e.g. lock screen, or to restore the pause state)
     */
    private boolean mPlaybackStarted = false;

    /* for getTime and seek */
    private long mForcedTime = -1;
    private long mLastTime = -1;

    private OnLayoutChangeListener mOnLayoutChangeListener;
    private AlertDialog mAlertDialog;

    private final DisplayMetrics mScreen = new DisplayMetrics();

    private AceStreamDiscoveryServerService.Client.ServerCallback mDSSServerCallback = new AceStreamDiscoveryServerService.Client.ServerCallback() {
        @Override
        public void onClientConnected(String clientId, String deviceId) {
            App.v(TAG, "dss:server:onClientConnected: clientId=" + clientId + " deviceId=" + deviceId);
            if(TextUtils.equals(deviceId, mLastRemoteClientDeviceId)) {
                setCurrentRemoteClient(clientId, deviceId);
            }
        }

        @Override
        public void onClientDisconnected(String clientId, String deviceId) {
            App.v(TAG, "dss:server:onClientDisconnected: clientId=" + clientId + " deviceId=" + deviceId);
            onServerClientDisconnected(clientId, deviceId);
        }

        @Override
        public void onClientInfo(String clientId, String deviceId) {
            App.v(TAG, "dss:server:onClientInfo: clientId=" + clientId + " deviceId=" + deviceId);
            if(TextUtils.equals(clientId, mRemoteClientId) && !TextUtils.isEmpty(deviceId)) {
                initRemoteClient(deviceId);
            }
        }
    };

    private AceStreamDiscoveryServerService.Client.ClientCallback mDSSClientCallback = new AceStreamDiscoveryServerService.Client.ClientCallback() {
        @Override
        public void onMessage(final String clientId, final JsonRpcMessage msg) {
            App.v(TAG, "dss:client:onMessage: clientId=" + clientId + " message=" + msg);
            Log.d(TAG, "onMessage: msg=" + msg.toString());
            switch(msg.getMethod()) {
                case "pause":
                    if (mMediaPlayer.isPlaying()) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                showOverlayTimeout(OVERLAY_INFINITE);
                                pause();
                            }
                        });
                    }
                    break;
                case "play":
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            hideOverlay(true);
                            play();
                        }
                    });
                    break;
                case "stop":
                    boolean disconnect = msg.getBoolean("disconnect");

                    if(disconnect) {
                        exit();
                    }
                    else {
                        // Do nothing.
                        // In previous versions "stop" without disconnect
                        // was used to switch between playlist items over
                        // AceCast. Currently switching in done in other way.
                    }
                    break;
                case "setVolume":  {
                    int value = msg.getInt("value");
                    mMediaPlayer.setVolume(value);
                    break;
                }
                case "setTime": {
                    long value = msg.getLong("value");
                    mMediaPlayer.setTime(value);
                    break;
                }
                case "liveSeek":
                    int position = msg.getInt("value");
                    if(mPlaybackManager != null) {
                        mPlaybackManager.liveSeek(position);
                    }
                    break;
                case "setAudioTrack": {
                    int trackId = msg.getInt("trackId");
                    mMediaPlayer.setAudioTrack(trackId);
                    break;
                }
                case "setAudioDigitalOutputEnabled": {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            boolean enabled = msg.getBoolean("enabled");
                            mAudioDigitalOutputEnabled = enabled;
                            mMediaPlayer.setAudioDigitalOutputEnabled(enabled);
                        }
                    });
                    break;
                }
                case "setAudioOutput": {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setAudioOutput(msg.getString("aout"), true);
                        }
                    });

                    break;
                }
                case "setSubtitleTrack": {
                    int trackId = msg.getInt("trackId");
                    mMediaPlayer.setSpuTrack(trackId);
                    break;
                }
                case "setVideoSize":
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            String value = msg.getString("value");
                            switch(value) {
                                case "best_fit":
                                    setCurrentSize(VlcConstants.SURFACE_BEST_FIT);
                                    break;
                                case "fit_screen":
                                    setCurrentSize(VlcConstants.SURFACE_FIT_SCREEN);
                                    break;
                                case "fill":
                                    setCurrentSize(VlcConstants.SURFACE_FILL);
                                    break;
                                case "16:9":
                                    setCurrentSize(VlcConstants.SURFACE_16_9);
                                    break;
                                case "4:3":
                                    setCurrentSize(VlcConstants.SURFACE_4_3);
                                    break;
                                case "original":
                                    setCurrentSize(VlcConstants.SURFACE_ORIGINAL);
                                    break;
                            }
                        }
                    });
                    break;
                case "setDeinterlace":
                    Log.v(TAG, "setDeinterlace message is disabled");
                    break;
            }
        }

        @Override
        public void onDisconnected(String clientId, String deviceId) {
            onServerClientDisconnected(clientId, deviceId);
        }
    };

    private AceStreamDiscoveryServerService.Client.RemoteCallback mDiscoveryServerServiceClientCallback = new AceStreamDiscoveryServerService.Client.RemoteCallback() {
        @Override
        public void onConnected() {
            Log.v(TAG, "DiscoveryServerService connected");
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

    private void onResumeConnected() {
        Log.v(TAG, "onResumeConnected");

        mPlaybackManager.addEngineStatusListener(mEngineStatusListener);
        mPlaybackManager.addEngineCallbackListener(VideoPlayerActivity.this);
    }

    private final PlaybackManager.Client.Callback mPlaybackManagerClientCallback = new PlaybackManager.Client.Callback() {
        @Override
        public void onConnected(PlaybackManager pm) {
            Log.v(TAG, "connected playback manager");

            // Check whether mobile networks are allowed
            checkMobileNetworkConnection(new Runnable() {
                @Override
                public void run() {
                    mHandler.sendEmptyMessage(INIT_PLAYBACK);
                }
            });

            mPlaybackManager = pm;
            mPlaybackManager.addCallback(mPlaybackManagerCallback);
            mPlaybackManager.startEngine();
            mPlaybackManager.setOurPlayerActive(true);
            mPlaybackManager.setRemoteSelectedPlayer(SelectedPlayer.getOurPlayer());

            mPlaybackManager.getAdConfigAsync(new AceStreamManagerImpl.AdConfigCallback() {
                @Override
                public void onSuccess(AdConfig config) {
                    Logger.v(TAG, "init ad manager");
                    mAdManager = new AdManager(VideoPlayerActivity.this, config);
                }
            });

            initAds();

            for(Runnable runnable: mPlaybackManagerOnReadyQueue) {
                runnable.run();
            }
            mPlaybackManagerOnReadyQueue.clear();

            if(!mIsPaused) {
                onResumeConnected();
            }
        }

        @Override
        public void onDisconnected() {
            Log.v(TAG, "disconnected playback manager");
            if(mPlaybackManager != null) {
                mPlaybackManager.removeCallback(mPlaybackManagerCallback);
                mPlaybackManager.setOurPlayerActive(false);
                mPlaybackManager = null;
            }
        }
    };

    private PlaybackManager.Client mPlaybackManagerClient = new PlaybackManager.Client(this, mPlaybackManagerClientCallback);

    private PlaybackManager.Callback mPlaybackManagerCallback = new PlaybackManager.Callback() {
        @Override
        public void onEngineConnected(ExtendedEngineApi service) {
            App.v(TAG, "onEngineConnected: paused=" + mIsPaused + " service=" + mEngineService);
            if(mEngineService == null) {
                mEngineService = service;
            }
        }

        @Override
        public void onEngineFailed() {
            App.v(TAG, "onEngineFailed");
            setEngineStatus(EngineStatus.fromString("engine_failed"));
        }

        @Override
        public void onEngineUnpacking() {
            App.v(TAG, "onEngineUnpacking");
            setEngineStatus(EngineStatus.fromString("engine_unpacking"));
        }

        @Override
        public void onEngineStarting() {
            App.v(TAG, "onEngineStarting");
            setEngineStatus(EngineStatus.fromString("engine_starting"));
        }

        @Override
        public void onEngineStopped() {
            App.v(TAG, "onEngineStopped");
            exit();
        }
    };

    private EngineStatusListener mEngineStatusListener = new EngineStatusListener() {
        @Override
        public void onEngineStatus(final EngineStatus status, final IRemoteDevice remoteDevice) {
            boolean freeze = false;
            if(freezeEngineStatusAt > 0 && freezeEngineStatusFor > 0) {
                long age = System.currentTimeMillis() - freezeEngineStatusAt;
                if (age < freezeEngineStatusFor) {
                    freeze = true;
                }
            }

            if(DEBUG_LOG_ENGINE_STATUS) {
                Log.v(TAG, "engine_status:"
                        + " freeze=" + freeze
                        + " this=" + status.playbackSessionId
                        + " curr=" + (mPlaybackManager.getEngineSession() == null ? "null" : mPlaybackManager.getEngineSession().playbackSessionId)
                        + " status=" + status.toString()
                );
            }

            if(!freeze) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            processEngineStatus(status);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to process engine status");
                        }
                    }
                });
            }
        }

        @Override
        public boolean updatePlayerActivity() {
            return !mIsPaused;
        }
    };

    private Runnable mEnsurePlayerIsPlayingTask = new Runnable() {
        @Override
        public void run() {
            int state = mMediaPlayer.getPlayerState();
            Log.v(TAG, "ensure playing: state=" + state);
            if (state != VlcConstants.VlcState.PLAYING && state != VlcConstants.VlcState.PAUSED) {
                Log.d(TAG, "ensure playing: do play");
                mMediaPlayer.play();
            }
        }
    };

    private Runnable mUpdatePlayerStatusTask = new Runnable() {
        @Override
        public void run() {
            JsonRpcMessage msg = new JsonRpcMessage(AceStreamRemoteDevice.Messages.PLAYER_STATUS);
            msg.addParam("state", mMediaPlayer.getPlayerState());
            msg.addParam("position", mMediaPlayer.getPosition());
            msg.addParam("time", mMediaPlayer.getTime());
            msg.addParam("duration", mMediaPlayer.getLength());
            msg.addParam("volume", mMediaPlayer.getVolume());
            msg.addParam("videoSize", mCurrentSize);
            msg.addParam("deinterlaceMode", Constants.DEINTERLACE_MODE_DISABLED);

            msg.addParam("audioDigitalOutputEnabled", mAudioDigitalOutputEnabled);
            msg.addParam("aout", mAout);

            if(!mRemotePlayckStarted && mMediaPlayer.getPlayerState() == VlcConstants.VlcState.PLAYING) {
                sendRemotePlaybackStarted();
            }

            //TODO: send this only once per video
            // audio tracks
            try {
                MediaPlayer.TrackDescription[] audioTracks = mMediaPlayer.getAudioTracks();
                if(audioTracks != null) {
                    JSONArray jsonAudioTracks = new JSONArray();
                    for(MediaPlayer.TrackDescription track: audioTracks) {
                        JSONObject jsonAudioTrack = new JSONObject();
                        jsonAudioTrack.put("id", track.id);
                        jsonAudioTrack.put("name", track.name);
                        jsonAudioTracks.put(jsonAudioTrack);
                    }

                    msg.addParam("audioTracks", jsonAudioTracks);
                    msg.addParam("selectedAudioTrack", mMediaPlayer.getAudioTrack());
                }
            }
            catch(JSONException e) {
                Log.e(TAG, "failed to encode audio tracks", e);
            }

            // subtitle tracks
            try {
                MediaPlayer.TrackDescription[] subtitleTracks = mMediaPlayer.getSpuTracks();
                if(subtitleTracks != null) {
                    JSONArray jsonSubtitleTracks = new JSONArray();
                    for(MediaPlayer.TrackDescription track: subtitleTracks) {
                        JSONObject jsonSubtitleTrack = new JSONObject();
                        jsonSubtitleTrack.put("id", track.id);
                        jsonSubtitleTrack.put("name", track.name);
                        jsonSubtitleTracks.put(jsonSubtitleTrack);
                    }

                    msg.addParam("subtitleTracks", jsonSubtitleTracks);
                    msg.addParam("selectedSubtitleTrack", mMediaPlayer.getSpuTrack());
                }
            }
            catch(JSONException e) {
                Log.e(TAG, "failed to encode audio tracks", e);
            }
            sendRemoteMessage(msg);
            if(!mIsPaused) {
                mHandler.postDelayed(mUpdatePlayerStatusTask, PLAYER_STATUS_UPDATE_INTERVAL);
            }
        }
    };

    private GestureDetector.SimpleOnGestureListener mGestureListener = new GestureDetector.SimpleOnGestureListener() {

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            mHandler.sendEmptyMessageDelayed(mShowing ? HIDE_INFO : SHOW_INFO, 200);
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            mHandler.removeMessages(HIDE_INFO);
            mHandler.removeMessages(SHOW_INFO);
            float range = mCurrentScreenOrientation == Configuration.ORIENTATION_LANDSCAPE ? mSurfaceXDisplayRange : mSurfaceYDisplayRange;
            if (!mIsLocked) {
                if ((mTouchControls & TOUCH_FLAG_SEEK) == 0) {
                    doPlayPause();
                    return true;
                }
                float x = e.getX();
                if (x < range/4f)
                    seekDelta(-10000);
                else if (x > range*0.75)
                    seekDelta(10000);
                else
                    doPlayPause();
                return true;
            }
            return false;
        }
    };

    private MediaPlayer.EventListener mMediaPlayerEventListener = new MediaPlayer.EventListener() {
        @Override
        public void onEvent(MediaPlayer.Event event) {
            switch (event.type) {
                case MediaPlayer.Event.Opening:
                    App.v(TAG, "vlc:event: opening");
                    if(mIsAdDisplayed) {
                        Log.v(TAG, "ads:event:vlc:opening: mute because ads are displayed");
                        mute(true);
                    }
                    break;
                case MediaPlayer.Event.Playing:
                    App.v(TAG, "vlc:event: playing");
                    setBuffering(false);
                    onPlaying();
                    if(mIsAdDisplayed) {
                        App.v(TAG, "Pause on play because ads are displayed");
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                // Additional check.
                                // If we didn't receive "ad closed" event from SDK for some reason
                                // then mIsAdDisplayed will never be reset and content will always be
                                // paused on play. But we can assume that ads are closed if activity is
                                // not paused.
                                if(mIsAdDisplayed
                                        && !mIsPaused
                                        && mAdSource != null
                                        && mAdSource != AdSource.IMA_SDK) {
                                    App.v(TAG, "Force ads hide because player is resumed");
                                    hideAds(mAdSource);
                                }
                                else {
                                    pause();
                                }
                            }
                        }, 1000);
                    }
                    break;
                case MediaPlayer.Event.Paused:
                    App.v(TAG, "vlc:event: paused");
                    updateOverlayPausePlay();
                    onPlaybackPaused();
                    break;
                case MediaPlayer.Event.Stopped:
                    onPlaybackStopped();
                    App.v(TAG, "vlc:event: stopped");
                    App.v(TAG, "vlc:event:Stopped: restarting=" + mRestartingPlayer + " exitOnStop=" + mExitOnStop);
                    if(mRestartingPlayer) {
                        mRestartingPlayer = false;
                        mMediaPlayer.play();
                    }
                    break;
                case MediaPlayer.Event.EndReached:
                    App.v(TAG, "vlc:event:EndReached: exitOnStop=" + mExitOnStop);
                    if(mExitOnStop) {
                        endReached();
                    }
                    break;
                case MediaPlayer.Event.EncounteredError:
                    encounteredError();
                    break;
                case MediaPlayer.Event.TimeChanged:
                    if(!mIsLive) {
                        boolean dragging = false;
                        if(mDragging) {
                            dragging = true;
                        }
                        else if(mHudBinding != null && mHudBinding.playerOverlaySeekbar.hasFocus()) {
                            dragging = true;
                        }
                        if(!dragging) {
                            mProgress.set((int) event.getTimeChanged());
                        }
                        mCurrentTime.set((int) event.getTimeChanged());
                    }
                    break;
                case MediaPlayer.Event.LengthChanged:
                    if(!mIsLive) {
                        mMediaLength.set(event.getLengthChanged());
                    }
                    break;
                case MediaPlayer.Event.ESAdded:
                    if (event.getEsChangedType() == Media.Track.Type.Audio) {
                        setESTrackLists();
                        int audioTrack = mMetaDataManager.getInt(getCurrentMedia(), MetaDataManager.META_AUDIO_TRACK, 0);
                        if (audioTrack != 0)
                            mMediaPlayer.setAudioTrack(audioTrack);
                    } else if (event.getEsChangedType() == Media.Track.Type.Text) {
                        setESTrackLists();
                        int spuTrack = mMetaDataManager.getInt(getCurrentMedia(), MetaDataManager.META_SUBTITLE_TRACK, 0);
                        if (spuTrack != 0)
                            mMediaPlayer.setSpuTrack(spuTrack);
                    }
                case MediaPlayer.Event.ESDeleted:
                    invalidateESTracks(event.getEsChangedType());
                    break;
                case MediaPlayer.Event.ESSelected:
                    if (event.getEsChangedType() == Media.VideoTrack.Type.Video) {
                        changeSurfaceLayout();
                    }
                    break;
                case MediaPlayer.Event.SeekableChanged:
                    if(!mIsLive) {
                        updateSeekable(event.getSeekable());
                    }
                    break;
                case MediaPlayer.Event.PausableChanged:
                    updatePausable(event.getPausable());
                    break;
                case MediaPlayer.Event.Buffering:
                    if (!mIsPlaying)
                        break;
                    if (event.getBuffering() == 100f) {
                        setBuffering(false);
                    }
                    else {
                        setBuffering(true);
                    }
                    break;
            }
        }
    };

    private Media.EventListener mMediaEventListener = new Media.EventListener() {
        @Override
        public void onEvent(Media.Event event) {
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.v(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        // Use media session to show metadata and playback controls while in PIP
        mMediaSessionServiceClient = new MediaSessionService.Client(this, new MediaSessionService.Client.Callback() {
            @Override
            public void onConnected(MediaSessionService service) {
                Logger.v(TAG, "media session connected");
                mMediaSession = service.getMediaSession();
                service.setPlayer(VideoPlayerActivity.this);
                updateMediaSessionMetadata();
            }

            @Override
            public void onDisconnected() {
                Logger.v(TAG, "media session disconnected");
                mMediaSession = null;
            }
        });

        parseIntent();

        if (!showTvUi()) {
            mTouchControls = TOUCH_FLAG_AUDIO_VOLUME + TOUCH_FLAG_BRIGHTNESS;
        }

        /* Services and miscellaneous */
        mAudioManager = (AudioManager) getApplicationContext().getSystemService(AUDIO_SERVICE);
        mAudioMax = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

        setContentView(R.layout.ace_player);

        mRenderAds = true;

        mActionBar = getSupportActionBar();
        mActionBar.setDisplayShowHomeEnabled(false);
        mActionBar.setDisplayShowTitleEnabled(false);
        mActionBar.setBackgroundDrawable(null);
        mActionBar.setDisplayShowCustomEnabled(true);
        mActionBar.setCustomView(R.layout.player_action_bar);

        mRootView = findViewById(R.id.player_root);
        mActionBarView = (ViewGroup) mActionBar.getCustomView();

        mPlaylistToggle = findViewById(R.id.playlist_toggle);
        mPlaylistView = findViewById(R.id.video_playlist);
        mPipToggle = findViewById(R.id.pip_toggle);

        mSwitchPlayer = findViewById(R.id.switch_player);
        mSwitchPlayer.setOnClickListener(this);

        mAdvOptionsButton = findViewById(R.id.player_overlay_adv_function);
        mAdvOptionsButton.setOnClickListener(this);

        if (Utils.hasPiP) {
            mPipToggle.setOnClickListener(this);
            mPipToggle.setVisibility(View.VISIBLE);
        }

        mEngineStatus = findViewById(R.id.engine_status);
        mDebugInfo = findViewById(R.id.debug_info);

        // player UI
        mPlayerUiContainer = findViewById(R.id.player_ui_container);

        if(mRenderAds) {
            // ad player
            mAdPlayerUiContainer = findViewById(R.id.ad_player_ui_container);
            mAdPlayerContainer = findViewById(R.id.ad_player_container);
            mAdPlayerSurfaceView = findViewById(R.id.ad_player_surface);
            mAdPlayerSubtitlesSurfaceView = findViewById(R.id.ad_player_subtitles_surface);
            mAdPlayerSurfaceFrame = findViewById(R.id.ad_player_surface_frame);
            mAdPlayerButtonClick = findViewById(R.id.ad_player_button_click);
            mAdPlayerButtonClick.setOnClickListener(this);
            mAdPlayerTimeLeft = findViewById(R.id.ad_player_time_left);

            // skip button
            mAdPlayerSkipContainer = findViewById(R.id.ad_player_skip_container);
            mAdPlayerSkipText = findViewById(R.id.ad_player_skip_text);
            mAdPlayerSkipContainer.setOnClickListener(this);

            mCustomAdsContainer = findViewById(R.id.custom_ads_container);
            mButtonShowBonusAds = findViewById(R.id.button_show_bonus_ads);
            mButtonSkipBonusAds = findViewById(R.id.button_skip_bonus_ads);
            mCheckboxShowRewardedAds = findViewById(R.id.checkbox_show_rewarded_ads);
            mButtonShowBonusAds.setOnClickListener(this);
            mButtonSkipBonusAds.setOnClickListener(this);

            // underline on focus
            mAdPlayerButtonClick.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View view, boolean gotFocus) {
                    if(view instanceof TextView) {
                        int flags = ((TextView) view).getPaintFlags();
                        if(gotFocus) {
                            ((TextView) view).setPaintFlags(flags | Paint.UNDERLINE_TEXT_FLAG);
                        }
                        else {
                            ((TextView) view).setPaintFlags(flags & (~Paint.UNDERLINE_TEXT_FLAG));
                        }
                    }
                }
            });
        }

        mSurfaceView = findViewById(R.id.player_surface);
        mSubtitlesSurfaceView = findViewById(R.id.subtitles_surface);

        mSubtitlesSurfaceView.setZOrderMediaOverlay(true);
        mSubtitlesSurfaceView.getHolder().setFormat(PixelFormat.TRANSLUCENT);

        mSurfaceFrame = findViewById(R.id.player_surface_frame);

        /* Loading view */
        mLoading = findViewById(R.id.player_overlay_loading);
        dimStatusBar(true);

        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        // 100 is the value for screen_orientation_start_lock
        setRequestedOrientation(getScreenOrientation(mScreenOrientation));

        //Set margins for TV overscan
        if (showTvUi()) {
            int hm = getResources().getDimensionPixelSize(R.dimen.tv_overscan_horizontal);
            int vm = getResources().getDimensionPixelSize(R.dimen.tv_overscan_vertical);

            final RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) mPlayerUiContainer.getLayoutParams();
            lp.setMargins(hm, 0, hm, vm);
            mPlayerUiContainer.setLayoutParams(lp);
        }

        getWindowManager().getDefaultDisplay().getMetrics(mScreen);
        mSurfaceYDisplayRange = Math.min(mScreen.widthPixels, mScreen.heightPixels);
        mSurfaceXDisplayRange = Math.max(mScreen.widthPixels, mScreen.heightPixels);
        mCurrentScreenOrientation = getResources().getConfiguration().orientation;
        mCurrentSize = VlcConstants.SURFACE_BEST_FIT;
        mIsRtl = TextUtils.getLayoutDirectionFromLocale(Locale.getDefault()) == View.LAYOUT_DIRECTION_RTL;

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if(powerManager != null) {
            mWakeLock = powerManager.newWakeLock(
                    PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "acestream:player_wake_lock");
            if (mWakeLock != null) {
                // Acquire lock for 5 seconds
                // Need this to wake up.
                // After wake up FLAG_KEEP_SCREEN_ON will prevent sleeping.
                mWakeLock.acquire(5000);
            }
        }

        if(mRenderAds) {
            mAdUiContainer = findViewById(R.id.ad_container);
            mAdsManagers = new ArrayList<>();
        }

        mDiscoveryServerServiceClient = new AceStreamDiscoveryServerService.Client(this, mDiscoveryServerServiceClientCallback);

        if(AceStreamEngineBaseApplication.showDebugInfo()) {
            mDebugInfo.setVisibility(View.VISIBLE);
        }

        mMetaDataManager = new MetaDataManager(this);

        // init playlist
        mPlaylist = new PlaylistManager(this, this);
    }

    private void initAdsLoader() {
        // Create an AdsLoader.
        mSdkFactory = ImaSdkFactory.getInstance();

        ImaSdkSettings imaSdkSettings = mSdkFactory.createImaSdkSettings();
        imaSdkSettings.setAutoPlayAdBreaks(true);
        imaSdkSettings.setDebugMode(App.verbose());

        final AdsRenderingSettings adsRenderingSettings = mSdkFactory.createAdsRenderingSettings();
        adsRenderingSettings.setEnablePreloading(ADS_ENABLE_PRELOAD);
        adsRenderingSettings.setFocusSkipButtonWhenAvailable(true);

        mAdsLoader = mSdkFactory.createAdsLoader(this, imaSdkSettings);
        // Add listeners for when ads are loaded and for errors.
        mAdsLoader.addAdErrorListener(this);
        mAdsLoader.addAdsLoadedListener(new AdsLoader.AdsLoadedListener() {
            @Override
            public void onAdsManagerLoaded(AdsManagerLoadedEvent adsManagerLoadedEvent) {
                Log.v(TAG, "ads loaded, create ad manager");

                // Ads were successfully loaded, so get the AdsManager instance. AdsManager has
                // events for ad playback and errors.
                final AdsManager manager = adsManagerLoadedEvent.getAdsManager();

                // Attach event and error event listeners.
                manager.addAdErrorListener(new AdErrorEvent.AdErrorListener() {
                    @Override
                    public void onAdError(AdErrorEvent adErrorEvent) {
                        VideoPlayerActivity.this.onAdError(manager, adErrorEvent);
                    }
                });
                manager.addAdEventListener(new AdEvent.AdEventListener() {
                    @Override
                    public void onAdEvent(AdEvent adEvent) {
                        VideoPlayerActivity.this.onAdEvent(manager, adEvent);
                    }
                });
                manager.init(adsRenderingSettings);

                mAdsManagers.add(manager);
            }
        });
    }

    @Override
    protected void onResume() {
        Log.v(TAG, "onResume");
        overridePendingTransition(0,0);
        super.onResume();
        mShowingDialog = false;
        /*
         * Set listeners here to avoid NPE when activity is closing
         */
        setListeners(true);

        if (mIsLocked && mScreenOrientation == 99)
            setRequestedOrientation(mScreenOrientationLock);

        if(getIntent().hasExtra(Constants.EXTRA_STARTED_FROM_EXTERNAL_REQUEST)) {
            mStartedFromExternalRequest = getIntent().getBooleanExtra(Constants.EXTRA_STARTED_FROM_EXTERNAL_REQUEST, false);
        }
        mIsPaused = false;

        if(mPlaybackManager != null) {
            onResumeConnected();
        }

        mDiscoveryServerServiceClient.addServerListener(mDSSServerCallback);

        String remoteClientId = getIntent().getStringExtra(AceStreamPlayer.EXTRA_REMOTE_CLIENT_ID);
        if(remoteClientId != null) {
            Log.v(TAG, "onResume: got remote client id from intent: " + remoteClientId);
            setRemoteClientId(remoteClientId);
        }

        if(mRemoteClientId != null) {
            mHandler.postDelayed(mUpdatePlayerStatusTask, 0);
            mDiscoveryServerServiceClient.connect();
        }

        if(mShowUnpauseAdsOnResume) {
            App.v(TAG, "onResume: request ads");
            mShowUnpauseAdsOnResume = false;
            onContentUnpaused();
        }
    }

    private void setListeners(boolean enabled) {
        if (mHudBinding != null)
            mHudBinding.playerOverlaySeekbar.setOnSeekBarChangeListener(enabled ? mSeekListener : null);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        App.v(TAG, "onNewIntent: started=" + mIsStarted + " action=" + intent.getAction());

        if(TextUtils.equals(intent.getAction(), AceStream.ACTION_START_PLAYER)) {
            if(intent.hasExtra(AceStreamPlayer.EXTRA_PLAYLIST)) {
                // Start new playback or switch to another playlist item
                setIntent(intent);
                parseIntent();

                // Handle new playlist only when started.
                // We may receive new intent when player is stopped (in background).
                // In such cast new playlist will be handled via normal start.
                if(mIsStarted) {
                    if (mPlaylist.isSamePlaylist(intent.getStringExtra(AceStreamPlayer.EXTRA_PLAYLIST))) {
                        int pos = intent.getIntExtra(AceStreamPlayer.EXTRA_PLAYLIST_POSITION, 0);
                        if (pos != mPlaylist.getCurrentMediaPosition()) {
                            App.v(TAG, "onNewIntent: chane pos in current playlist: newpos=" + pos);
                            mPlaylist.playIndex(pos);
                        }
                    } else {
                        App.v(TAG, "onNewIntent: load new playlist");
                        stopPlayback(true);
                        initPlayback();
                    }
                }
            }
        }
    }

    private void newItemSelected() {
        App.vv(TAG, "newItemSelected");

        if (mPlaylistView.getVisibility() == View.VISIBLE) {
            mPlaylistView.setVisibility(View.GONE);
        }
        showOverlay();
        initUI();
    }

    protected void newPlayback() {
        boolean viewsAttached = areViewsAttached();
        Log.v(TAG, "newPlayback: viewsAttached=" + viewsAttached);

        mExitOnStop = true;
        mMediaStartedPlaying = false;

        newItemSelected();
        setPlaybackParameters();
        mForcedTime = mLastTime = -1;
        updateTimeValues();
        initAds();

        if(!viewsAttached) {
            initPlayback();
        }
    }

    private void updateTimeValues() {
        if(!mIsLive) {
            int time = (int) getTime();
            mProgress.set(time);
            mCurrentTime.set(time);
            mMediaLength.set(mMediaPlayer.getLength());
        }
    }

    @SuppressLint("NewApi")
    @Override
    protected void onPause() {
        Log.v(TAG, "onPause: finishing=" + isFinishing());
        if (isFinishing())
            overridePendingTransition(0, 0);
        else
            hideOverlay(true);

        super.onPause();
        setListeners(false);

        /* Stop the earliest possible to avoid vout error */
        if (!isInPictureInPictureMode()) {
            if (isFinishing() ||
                    (AndroidUtil.isNougatOrLater && !AndroidUtil.isOOrLater //Video on background on Nougat Android TVs
                            && Utils.isAndroidTv && !requestVisibleBehind(true))) {
                stopPlayback(true);
            }
        }

        if(!isInPictureInPictureMode()) {
            mIsPaused = true;
            if(mPlaybackManager != null) {
                mPlaybackManager.removeEngineStatusListener(mEngineStatusListener);
                mPlaybackManager.removeEngineCallbackListener(this);
            }
            mDiscoveryServerServiceClient.removeServerListener(mDSSServerCallback);
            mDiscoveryServerServiceClient.disconnect();

            // stop updating status
            mHandler.removeCallbacks(mUpdatePlayerStatusTask);
        }
    }

    @SuppressLint("NewApi")
    public void switchToPopup() {
        App.v(TAG, "switchToPopup");
        if (Utils.hasPiP) {
            if (AndroidUtil.isOOrLater)
                try {
                    final int height = mVideoHeight;
                    final int width = Math.min(mVideoWidth, (int) (height*2.39f));
                    enterPictureInPictureMode(new PictureInPictureParams.Builder().setAspectRatio(new Rational(width, height)).build());
                } catch (IllegalArgumentException e) { // Fallback with default parameters
                    //noinspection deprecation
                    enterPictureInPictureMode();
                }
            else {
                //noinspection deprecation
                enterPictureInPictureMode();
            }
        }
    }

    @Override
    public void onVisibleBehindCanceled() {
        super.onVisibleBehindCanceled();
        stopPlayback(true);
        exit();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        getWindowManager().getDefaultDisplay().getMetrics(mScreen);
        mCurrentScreenOrientation = newConfig.orientation;
        mSurfaceYDisplayRange = Math.min(mScreen.widthPixels, mScreen.heightPixels);
        mSurfaceXDisplayRange = Math.max(mScreen.widthPixels, mScreen.heightPixels);
        resetHudLayout();
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public void resetHudLayout() {
        if (mHudBinding == null) return;
        final RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams)mHudBinding.playerOverlayButtons.getLayoutParams();
        final int orientation = getScreenOrientation(100);
        final boolean portrait = orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT ||
                orientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
        final int endOf = RelativeLayout.END_OF;
        final int startOf = RelativeLayout.START_OF;
        final int endAlign = RelativeLayout.ALIGN_PARENT_END;
        final int startAlign = RelativeLayout.ALIGN_PARENT_START;
        layoutParams.addRule(startAlign, portrait ? 1 : 0);
        layoutParams.addRule(endAlign, portrait ? 1 : 0);
        layoutParams.addRule(RelativeLayout.BELOW, portrait ? R.id.player_overlay_length : R.id.progress_container);
        layoutParams.addRule(endOf, portrait ? 0 : R.id.player_overlay_time);
        layoutParams.addRule(startOf, portrait ? 0 : R.id.player_overlay_length);
        mHudBinding.playerOverlayButtons.setLayoutParams(layoutParams);
    }

    @Override
    protected void onStart() {
        Log.v(TAG, "onStart: started=" + mIsStarted + " wasPaused=" + mWasPaused + " ads=" + mIsAdDisplayed);
        super.onStart();
        mHandler.removeCallbacks(mDelayedInteralStop);
        setInBackground(false);

        if(mRenderAds) {
            for (AdsManager manager : mAdsManagers) {
                manager.resume();
            }
        }

        if(!mIsStarted) {
            internalStart();
        }
        else {
            attachViews();
            attachAdPlayerViews();

            if(!mIsAdDisplayed) {
                if (!mWasPaused) {
                    mMediaPlayer.play();
                } else {
                    showOverlay();
                }
            }
        }
    }

    private void internalStart() {
        App.v(TAG, "internalStart");
        mIsStarted = true;
        notifyPlayerStarted();

        // init LibVLC
        initLibVlc();

        // init media player
        createMediaPlayer();

        mPlaybackManagerClient.connect();
        mMediaSessionServiceClient.connect();

        final IntentFilter filter = new IntentFilter(AceStream.BROADCAST_APP_IN_BACKGROUND);
        filter.addAction(SLEEP_INTENT);
        registerReceiver(mBroadcastReceiver, filter);

        Utils.setViewVisibility(mOverlayInfo, View.INVISIBLE);

        if(mRenderAds) {
            AdManager.registerRewardedVideoActivity(this);
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    @Override
    protected void onStop() {
        boolean isPlaying;
        if(mPlaybackStarted) {
            isPlaying = true;
        }
        else {
            isPlaying = areViewsAttached();
        }
        Log.v(TAG, "onStop: finishing=" + isFinishing() + " pip=" + mPictureInPictureMode + " ads=" + mIsAdDisplayed + " playing=" + isPlaying);

        super.onStop();

        if(mWakeLock != null && mWakeLock.isHeld()) {
            mWakeLock.release();
        }

        if(mRenderAds) {
            for (AdsManager manager : mAdsManagers) {
                manager.pause();
            }
        }

        boolean isFinishing = isFinishing();
        if(!isFinishing) {
            if(mPictureInPictureMode) {
                // onStop on PIP mode means that user has pressed "close" button in PiP window.
                // Finish activity in such case.
                isFinishing = true;
                exit();
            }
        }

        setInBackground(true);
        if(areViewsAttached()) {
            mWasPaused = !mMediaPlayer.isPlaying();
        }
        App.v(TAG, "onStop: mWasPaused=" + mWasPaused);

        if (mAlertDialog != null && mAlertDialog.isShowing())
            mAlertDialog.dismiss();

        cleanUI();

        int delayInterval = 0;
        if(mIsAdDisplayed) {
            if (mMediaPlayer != null) {
                if (isPlaying) {
                    final IVLCVout vlcVout = mMediaPlayer.getVLCVout();
                    if(vlcVout.areViewsAttached()) {
                        App.v(TAG, "onStop: got ads, view are attached, detach now");
                        vlcVout.detachViews();
                    }
                    else {
                        App.v(TAG, "onStop: got ads, view are not attached");
                    }
                    mMediaPlayer.pause();
                    delayInterval = 120000;
                }
            }
        }

        if(delayInterval > 0) {
            App.v(TAG, "schedule delayed internal stop in " + delayInterval + " ms");

            if(mAdPlayer != null) {
                final IVLCVout vlcVout = mAdPlayer.getVLCVout();
                if (vlcVout.areViewsAttached()) {
                    App.v(TAG, "onStop: ad player views are attached, detach now");
                    vlcVout.detachViews();
                } else {
                    App.v(TAG, "onStop: ad player views are not attached");
                }
            }

            mHandler.postDelayed(mDelayedInteralStop, delayInterval);
        }
        else {
            internalStop();
        }
    }

    private Runnable mDelayedInteralStop = new Runnable() {
        @Override
        public void run() {
            App.v(TAG, "run delayed internal stop");
            internalStop();
        }
    };

    private void internalStop() {
        boolean isFinishing = isFinishing();
        App.v(TAG, "internalStop: started=" + mIsStarted + " isFinishing=" + isFinishing);
        if(!mIsStarted) {
            return;
        }

        mIsStarted = false;
        notifyPlayerStopped();

        if(mRenderAds) {
            for (AdsManager manager : mAdsManagers) {
                manager.destroy();
            }
            mAdsManagers.clear();

            // Always deinit player on stop
            hideAdPlayer();

            AdManager.unregisterRewardedVideoActivity(this);
        }

        unregisterReceiver(mBroadcastReceiver);

        mWasStopped = !isFinishing;
        boolean stopEngineSession = true;
        if(!isFinishing) {
            stopEngineSession = false;
            if(mPlaybackManager != null) {
                mPlaybackManager.setPlayerActivityTimeout(60);
            }
        }

        if(mPlaybackStarted) {
            // Clear Intent to restore playlist on activity restart
            setIntent(new Intent());
        }

        stopPlayback(stopEngineSession);

        if(mPlaybackManager != null) {
            mPlaybackManager.setOurPlayerActive(false);
        }

        if(mRemoteClientId != null) {
            mDiscoveryServerServiceClient.sendPlayerClosed(mRemoteClientId, true);
        }

        // Need to reset to correctly reconnect after stop
        mEngineService = null;

        if(isFinishing && mRenderAds) {
            mAppodealInitialized = false;
            App.v(TAG, "internalStop: deinit appodeal");
            Appodeal.hide(this, Appodeal.INTERSTITIAL | Appodeal.REWARDED_VIDEO);

            if(mAdManager != null) {
                mAdManager.resetInterstitial("preroll");
                mAdManager.resetInterstitial("pause");
                mAdManager.resetInterstitial("close");
            }
        }

        shutdown();

        mDiscoveryServerServiceClient.disconnect();
        mPlaybackManagerClientCallback.onDisconnected();
        mPlaybackManagerClient.disconnect();
        mMediaSessionServiceClient.disconnect();
    }

    @Override
    protected void onDestroy() {
        Log.v(TAG, "onDestroy: started=" + mIsStarted);
        super.onDestroy();

        // Dismiss the presentation when the activity is not visible.
        mAudioManager = null;

        // Ensure that activity is stopped.
        if(mIsStarted) {
            internalStop();
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void surfaceFrameAddLayoutListener(boolean add) {
        if (mSurfaceFrame == null
                || add == (mOnLayoutChangeListener != null))
            return;

        if (add) {
            mOnLayoutChangeListener = new OnLayoutChangeListener() {
                private final Runnable mRunnable = new Runnable() {
                    @Override
                    public void run() {
                        changeSurfaceLayout();
                    }
                };
                @Override
                public void onLayoutChange(View v, int left, int top, int right,
                                           int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
                        /* changeSurfaceLayout need to be called after the layout changed */
                        mHandler.removeCallbacks(mRunnable);
                        mHandler.post(mRunnable);
                    }
                }
            };
            mSurfaceFrame.addOnLayoutChangeListener(mOnLayoutChangeListener);
            changeSurfaceLayout();
        }
        else {
            mSurfaceFrame.removeOnLayoutChangeListener(mOnLayoutChangeListener);
            mOnLayoutChangeListener = null;
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void adPlayerSurfaceFrameAddLayoutListener(boolean add) {
        if (mAdPlayerSurfaceFrame == null
                || add == (mAdPlayerOnLayoutChangeListener != null))
            return;

        if (add) {
            mAdPlayerOnLayoutChangeListener = new OnLayoutChangeListener() {
                private final Runnable mRunnable = new Runnable() {
                    @Override
                    public void run() {
                        changeAdPlayerSurfaceLayout();
                    }
                };
                @Override
                public void onLayoutChange(View v, int left, int top, int right,
                                           int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
                        /* changeAdPlayerSurfaceLayout need to be called after the layout changed */
                        mHandler.removeCallbacks(mRunnable);
                        mHandler.post(mRunnable);
                    }
                }
            };
            mAdPlayerSurfaceFrame.addOnLayoutChangeListener(mAdPlayerOnLayoutChangeListener);
            changeAdPlayerSurfaceLayout();
        }
        else {
            mAdPlayerSurfaceFrame.removeOnLayoutChangeListener(mAdPlayerOnLayoutChangeListener);
            mAdPlayerOnLayoutChangeListener = null;
        }
    }

    protected void initPlayback() {
        Log.v(TAG, "initPlayback: activityStarted=" + mIsStarted + " playbackStarted=" + mPlaybackStarted);
        if(!mIsStarted)
            return;
        if (mPlaybackStarted)
            return;

        mPlaybackStarted = true;

        attachViews();
        initUI();

        if(getIntent().hasExtra(AceStreamPlayer.EXTRA_PLAYLIST)) {
            mPlaylist.loadPlaylistFromIntent(getIntent(), true);
        }
        else {
            // Never ask user when loading last playlist.
            // Usually this happens when activity is recreated, not started explicitly.
            mAskResume = false;
            mPlayFromStart = false;
            mPlaylist.loadLastPlaylist(true);
        }
    }

    private void initVastAds(@Nullable VastTag[] tags) {
        mCountAdsLoaded = 0;
        mAdTags = tags;
        mCurrentAdTagIndex = -1;

        // Destroy any pending ads managers
        for(AdsManager manager: mAdsManagers) {
            manager.destroy();
        }
        mAdsManagers.clear();
    }

    private boolean requestVastAds() {
        if(mAdTags == null) {
            Log.v(TAG, "ads:event:requestVastAds: no tags");
            return false;
        }

        int nextTagIndex = mCurrentAdTagIndex + 1;

        if(nextTagIndex >= mAdTags.length) {
            Log.v(TAG, "ads:event:requestVastAds: index out of range: index=" + nextTagIndex + " count=" + mAdTags.length);
            return false;
        }

        if(mCountAdsLoaded >= mAdSettings.maxAds) {
            Log.v(TAG, "ads:event:requestVastAds: max ads: count=" + mCountAdsLoaded);
            return false;
        }

        mCurrentAdTagIndex = nextTagIndex;
        String adTagUrl = mAdTags[mCurrentAdTagIndex].url;
        mUseCustomAdPlayer = !TextUtils.equals("ima_sdk", mAdTags[mCurrentAdTagIndex].targetPlayer);

        Log.v(TAG, "ads:event:requestVastAds:"
                + " index=" + mCurrentAdTagIndex
                + " customPlayer=" + mUseCustomAdPlayer
                + " tag=" + adTagUrl
        );

        try {
            initAdsLoader();
        }
        catch(Throwable e) {
            Log.e(TAG, "Failed to create ads loader", e);
            return false;
        }

        AdDisplayContainer adDisplayContainer = mSdkFactory.createAdDisplayContainer();
        adDisplayContainer.setAdContainer(mAdUiContainer);

        if(mUseCustomAdPlayer) {
            initAdPlayer();
            adDisplayContainer.setPlayer(mVideoAdPlayer);
        }

        // Create the ads request.
        AdsRequest request = mSdkFactory.createAdsRequest();
        request.setAdTagUrl(adTagUrl);
        request.setAdDisplayContainer(adDisplayContainer);
        request.setContentProgressProvider(new ContentProgressProvider() {
            @Override
            public VideoProgressUpdate getContentProgress() {
                if (mIsAdDisplayed || mMediaPlayer == null || !mMediaPlayer.isPlaying()) {
                    App.vv(TAG, "ads:event:progress: n/a");
                    return VideoProgressUpdate.VIDEO_TIME_NOT_READY;
                }
                long duration = mMediaPlayer.getLength();
                App.vv(TAG, "ads:event:progress: time=" + mMediaPlayer.getTime() + " duration=" + duration);
                return new VideoProgressUpdate(mMediaPlayer.getTime(), duration);
            }
        });

        // Request the ad. After the ad is loaded, onAdsManagerLoaded() will be called.
        mAdsLoader.requestAds(request);

        return true;
    }

    private String makeVmap(String[] tags) {
        StringBuilder vmap = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        vmap.append("<vmap:VMAP xmlns:vmap=\"http://www.iab.net/videosuite/vmap\" version=\"1.0\">\n");

        for(int i=0; i < tags.length; i++) {
            int seq = i+1;
            String offset = i + ":00:00";
            String breakId = "break-" + seq;
            String adId = "ad-" + seq;
            vmap.append("<vmap:AdBreak timeOffset=\"" + offset + "\" breakType=\"linear\" breakId=\"" + breakId + "\">\n" +
                    "  <vmap:AdSource id=\"" + adId + "\" allowMultipleAds=\"true\" followRedirects=\"true\">\n" +
                    "   <vmap:AdTagURI templateType=\"vast3\"><![CDATA[" + tags[i] + "]]></vmap:AdTagURI>\n" +
                    "  </vmap:AdSource>\n" +
                    " </vmap:AdBreak>\n");
        }

        vmap.append("</vmap:VMAP>\n");
        return vmap.toString();
    }

    protected void attachViews() {
        IVLCVout vlcVout = mMediaPlayer.getVLCVout();
        if (vlcVout.areViewsAttached()) {
            App.v(TAG, "attachViews: already attached, detach before reattaching");
            vlcVout.detachViews();
        }
        else {
            App.v(TAG, "attachViews: currently not attached");
        }
        vlcVout.setVideoView(mSurfaceView);
        vlcVout.setSubtitlesView(mSubtitlesSurfaceView);
        vlcVout.addCallback(this);
        vlcVout.attachViews(this);
        mMediaPlayer.setVideoTrackEnabled(true);
    }

    private void attachAdPlayerViews() {
        if(mAdPlayer != null) {
            IVLCVout vlcVout = mAdPlayer.getVLCVout();
            if (vlcVout.areViewsAttached()) {
                App.v(TAG, "attachAdPlayerViews: ad player views already attached, detach before reattaching");
                vlcVout.detachViews();
            }
            else {
                App.v(TAG, "attachAdPlayerViews: ad player views currently not attached");
            }
            vlcVout.setVideoView(mAdPlayerSurfaceView);
            vlcVout.setSubtitlesView(mAdPlayerSubtitlesSurfaceView);
            vlcVout.removeCallback(mAdPlayerVlcOutCallback);
            vlcVout.addCallback(mAdPlayerVlcOutCallback);
            vlcVout.attachViews(mAdPlayerOnNewVideoLayoutListener);
        }
    }

    private void initPlaylistUi() {
        if (mPlaylist.size() > 1) {
            mHasPlaylist = true;
            mPlaylistAdapter = new PlaylistAdapter(this, mPlaylist, mPlaylistView);

            final LinearLayoutManager layoutManager = new LinearLayoutManager(this);
            layoutManager.setOrientation(RecyclerView.VERTICAL);
            mPlaylistView.setLayoutManager(layoutManager);
            mPlaylistView.setHasFixedSize(true);
            mPlaylistToggle.setVisibility(View.VISIBLE);
            mHudBinding.playlistPrevious.setVisibility(View.VISIBLE);
            mHudBinding.playlistNext.setVisibility(View.VISIBLE);
            mPlaylistToggle.setOnClickListener(VideoPlayerActivity.this);

            final ItemTouchHelper touchHelper = new ItemTouchHelper(new ItemTouchHelper.Callback() {
                @Override
                public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
                    int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
                    int swipeFlags = ItemTouchHelper.START | ItemTouchHelper.END;
                    return makeMovementFlags(dragFlags, swipeFlags);
                }

                @Override
                public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                    mPlaylistAdapter.moveItem(viewHolder.getLayoutPosition(), target.getLayoutPosition());
                    return true;
                }

                @Override
                public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                    mPlaylistAdapter.deleteItem(viewHolder.getLayoutPosition());
                }
            });
            touchHelper.attachToRecyclerView(mPlaylistView);
        }
    }

    private void initUI() {

        /* Dispatch ActionBar touch events to the Activity */
        mActionBarView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                onTouchEvent(event);
                return true;
            }
        });

        surfaceFrameAddLayoutListener(true);
        adPlayerSurfaceFrameAddLayoutListener(true);

        if (mRootView != null) mRootView.setKeepScreenOn(true);
    }

    private void setPlaybackParameters() {
        if (mAudioDelay != 0L && mAudioDelay != mMediaPlayer.getAudioDelay())
            mMediaPlayer.setAudioDelay(mAudioDelay);
        if (mSpuDelay != 0L && mSpuDelay != mMediaPlayer.getSpuDelay())
            mMediaPlayer.setSpuDelay(mSpuDelay);
    }

    private void stopPlayback(boolean stopEngineSession) {
        Log.v(TAG, "stopPlayback: started=" + mPlaybackStarted
                + " stopEngineSession=" + stopEngineSession
                + " switchingToAnotherPlayer=" + mSwitchingToAnotherPlayer
                + " switchingToAnotherRenderer=" + mSwitchingToAnotherRenderer
                + " stoppingOnDeviceDisconnect=" + mStoppingOnDeviceDisconnect
        );

        MediaItem currentMedia = mPlaylist.getCurrentItem();
        setPlaying(false);

        if(stopEngineSession) {
            if(mSwitchingToAnotherPlayer) {
                mSwitchingToAnotherPlayer = false;
                Log.v(TAG, "stopPlayback: skip stop engine session, switching to another player");
            }
            else if(mStoppingOnDeviceDisconnect) {
                mStoppingOnDeviceDisconnect = false;
                Log.v(TAG, "stopPlayback: skip stop engine session, stopping after device disconnect");
            }
            else if(mPlaybackManager == null) {
                Log.v(TAG, "stopPlayback: skip stop engine session, no PM");
            }
            else {
                Log.v(TAG, "stopPlayback: stop engine session");
                mPlaybackManager.stopEngineSession(true);
            }
        }

        if(mPlaybackStarted) {
            if (mMute) mute(false);

            mPlaybackStarted = false;

            mMediaPlayer.setVideoTrackEnabled(false);
            mMediaPlayer.setEventListener(null);

            mHandler.removeCallbacksAndMessages(null);

            final IVLCVout vlcVout = mMediaPlayer.getVLCVout();
            vlcVout.removeCallback(this);
            if (vlcVout.areViewsAttached()) {
                App.v(TAG, "stopPlayback: view are attached, detach now");
                vlcVout.detachViews();
            }
            else {
                App.v(TAG, "stopPlayback: view are not attached");
            }

            if (isSeekable()) {
                saveCurrentTime(currentMedia);
            }
        }

        mMediaPlayer.stop();
        mPlaylist.clear();
    }

    private void saveCurrentTime(MediaItem mediaItem) {
        if(mediaItem == null) return;

        long time = getTime();
        long length = mMediaPlayer.getLength();

        //remove saved position if in the last 5 seconds
        if (length - time < 5000)
            time = 0;
        else
            time -= 2000; // go back 2 seconds, to compensate loading time

        saveMediaTime(mediaItem, time);
    }

    private void saveMediaTime(MediaItem media, long time) {
        if(media == null) return;

        App.v(TAG, "saveMediaTime: time=" + time + " media=" + media);
        media.setSavedTime(time);
        mMetaDataManager.putLong(media, MetaDataManager.META_SAVED_TIME, time);
        notifySaveMetadata(media);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void cleanUI() {

        if (mRootView != null) mRootView.setKeepScreenOn(false);

        if (mDetector != null) {
            mDetector.setOnDoubleTapListener(null);
            mDetector = null;
        }

        surfaceFrameAddLayoutListener(false);
        adPlayerSurfaceFrameAddLayoutListener(false);

        mActionBarView.setOnTouchListener(null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        App.vv(TAG, "onActivityResult: requestCode=" + requestCode + " resultCode=" + resultCode + " data=" + data);

        if (requestCode == REQUEST_CODE_SELECT_PLAYER) {
            if(resultCode == Activity.RESULT_OK){
                SelectedPlayer player = SelectedPlayer.fromIntentExtra(data);

                // Check for same player
                if (player.type == SelectedPlayer.OUR_PLAYER) {
                    App.v(TAG, "onActivityResult: skip same device");
                    return;
                }

                // Need to stop remote playback and reset engine session (remote) before
                // new session is started, otherwise PlaybackManager.EngineStatusHandler
                // may be confused (session can be stopped right after start because handler
                // understands only one session and don't distinguish local and remote session).
                if(mPlaybackManager != null) {
                    mPlaybackManager.stopRemotePlayback(true);
                }

                if (AceStreamEngineBaseApplication.useVlcBridge() && player.isRemote()) {
                    if (mPlaybackManager == null) {
                        Log.e(TAG, "onActivityResult: missing pm");
                        return;
                    }

                    // stop local session
                    mPlaybackManager.stopEngineSession(true);

                    if(isSeekable()) {
                        saveCurrentTime(mPlaylist.getCurrentItem());
                    }

                    notifySaveMetadata(mPlaylist.getCurrentItem());
                    notifyChangeRenderer(player);
                    exit();
                }
                else {
                    TransportFileDescriptor descriptor = mDescriptor;
                    if(descriptor == null) {
                        MediaItem item = mPlaylist.getCurrentItem();
                        if(item == null) {
                            Log.e(TAG, "onActivityResult: missing current item");
                            return;
                        }

                        try {
                            descriptor = item.getDescriptor();
                        }
                        catch(TransportFileParsingException e) {
                            Log.e(TAG, "onActivityResult: failed to get descriptor: " + e.getMessage());
                            return;
                        }
                    }

                    startActivity(ContentStartActivity.makeIntentFromDescriptor(this, descriptor, player));
                    mSwitchingToAnotherPlayer = true;
                    exit();
                }
            }
            else if(resultCode == AceStream.Resolver.RESULT_CLOSE_CALLER) {
                exit();
            }
        }
    }

    protected void exit(){
        if (isFinishing())
            return;
        finish();
    }

    @Override
    public void onBackPressed() {
        if (mLockBackButton) {
            mLockBackButton = false;
            mHandler.sendEmptyMessageDelayed(RESET_BACK_LOCK, 2000);
            Toast.makeText(getApplicationContext(), getString(R.string.back_quit_lock), Toast.LENGTH_SHORT).show();
        } else if(mPlaylistView.getVisibility() == View.VISIBLE) {
            togglePlaylist();
        } else if (showTvUi() && mShowing && !mIsLocked) {
            hideOverlay(true);
        } else {
            if(shouldShowAds() && mAdsWaterfall != null) {
                mAdsWaterfall.setPlacement(AdsWaterfall.Placement.CLOSE);
                requestNextAds();
            }

            exit();
            super.onBackPressed();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mPlaylist == null
                || keyCode == KeyEvent.KEYCODE_BACK
                || keyCode == KeyEvent.KEYCODE_BUTTON_B
                || mIsAdDisplayed)
            return super.onKeyDown(keyCode, event);
        if (mIsLoading) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_S:
                case KeyEvent.KEYCODE_MEDIA_STOP:
                    exit();
                    return true;
            }
            return false;
        }

        //Handle playlist d-pad navigation
        if (mPlaylistView.hasFocus()) {
            Log.v(TAG, "playIndex:keydown: code=" + keyCode);
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    mPlaylistAdapter.moveFocusUp();
                    break;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    mPlaylistAdapter.moveFocusDown();
                    break;
            }
            return true;
        }
        if (mShowing || (keyCode == KeyEvent.KEYCODE_DPAD_DOWN)) {
            showOverlay();
        }

        // Progress bar d-pad navigation
        if(mHudBinding != null && mHudBinding.playerOverlaySeekbar.hasFocus()) {
            if(keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                doSeek(mHudBinding.playerOverlaySeekbar.getProgress());
                return true;
            }
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_F:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                seekDelta(10000);
                return true;
            case KeyEvent.KEYCODE_R:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
                seekDelta(-10000);
                return true;
            case KeyEvent.KEYCODE_BUTTON_R1:
                seekDelta(60000);
                return true;
            case KeyEvent.KEYCODE_BUTTON_L1:
                seekDelta(-60000);
                return true;
            case KeyEvent.KEYCODE_BUTTON_A:
                if (mHudBinding != null && mHudBinding.progressOverlay.getVisibility() == View.VISIBLE)
                    return false;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_SPACE:
                if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) //prevent conflict with remote control
                    return super.onKeyDown(keyCode, event);
                else
                    doPlayPause();
                return true;
            case KeyEvent.KEYCODE_O:
            case KeyEvent.KEYCODE_BUTTON_Y:
            case KeyEvent.KEYCODE_MENU:
                showAdvancedOptions();
                return true;
            case KeyEvent.KEYCODE_A:
                resizeVideo();
                return true;
            case KeyEvent.KEYCODE_M:
            case KeyEvent.KEYCODE_VOLUME_MUTE:
                updateMute();
                return true;
            case KeyEvent.KEYCODE_S:
            case KeyEvent.KEYCODE_MEDIA_STOP:
                exit();
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (!mShowing) {
                    seekDelta(-10000);
                    return true;
                }
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (!mShowing) {
                    seekDelta(10000);
                    return true;
                }
            case KeyEvent.KEYCODE_DPAD_UP:
                if (event.isCtrlPressed()) {
                    volumeUp();
                    return true;
                } else if (!mShowing) {
                    showAdvancedOptions();
                    return true;
                }
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (event.isCtrlPressed()) {
                    volumeDown();
                    return true;
                }
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (!mShowing) {
                    doPlayPause();
                    return true;
                }
            case KeyEvent.KEYCODE_ENTER:
                return super.onKeyDown(keyCode, event);
            case KeyEvent.KEYCODE_J:
                delayAudio(-50000L);
                return true;
            case KeyEvent.KEYCODE_K:
                delayAudio(50000L);
                return true;
            case KeyEvent.KEYCODE_G:
                delaySubs(-50000L);
                return true;
            case KeyEvent.KEYCODE_H:
                delaySubs(50000L);
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                volumeDown();
                return true;
            case KeyEvent.KEYCODE_VOLUME_UP:
                volumeUp();
                return true;
            case KeyEvent.KEYCODE_CAPTIONS:
                selectSubtitles();
                return true;
            case KeyEvent.KEYCODE_PLUS:
                mMediaPlayer.setRate(mMediaPlayer.getRate()*1.2f);
                return true;
            case KeyEvent.KEYCODE_EQUALS:
                if (event.isShiftPressed()) {
                    mMediaPlayer.setRate(mMediaPlayer.getRate() * 1.2f);
                    return true;
                }
                return false;
            case KeyEvent.KEYCODE_MINUS:
                mMediaPlayer.setRate(mMediaPlayer.getRate()/1.2f);
                return true;
            case KeyEvent.KEYCODE_C:
                resizeVideo();
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void volumeUp() {
        if (mMute) {
            updateMute();
        } else {
            int volume;
            if (mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC) < mAudioMax)
                volume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC) + 1;
            else
                volume = Math.round(((float)mMediaPlayer.getVolume())*mAudioMax/100 + 1);
            volume = Math.min(Math.max(volume, 0), mAudioMax);
            setAudioVolume(volume);
        }
    }

    private void volumeDown() {
        int vol;
        if (mMediaPlayer.getVolume() > 100)
            vol = Math.round(((float)mMediaPlayer.getVolume())*mAudioMax/100 - 1);
        else
            vol = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC) - 1;
        vol = Math.min(Math.max(vol, 0), mAudioMax);
        mOriginalVol = vol;
        setAudioVolume(vol);
    }

    public void delayAudio(long delta) {
        long delay = mMediaPlayer.getAudioDelay()+delta;
        mMediaPlayer.setAudioDelay(delay);
    }

    public void delaySubs(long delta) {
        long delay = mMediaPlayer.getSpuDelay()+delta;
        mMediaPlayer.setSpuDelay(delay);
        mSpuDelay = delay;
    }

    /**
     * Lock screen rotation
     */
    private void lockScreen() {
        if (mScreenOrientation != 100) {
            mScreenOrientationLock = getRequestedOrientation();
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
            else
                setRequestedOrientation(getScreenOrientation(100));
        }
        showInfo(R.string.locked, 1000);
        if (mHudBinding != null) {
            mHudBinding.lockOverlayButton.setImageResource(R.drawable.rci_lock_selector);
            mHudBinding.playerOverlayTime.setEnabled(false);
            mHudBinding.playerOverlaySeekbar.setEnabled(false);
            mHudBinding.playerOverlayLength.setEnabled(false);
            mHudBinding.playerOverlaySize.setEnabled(false);
            mHudBinding.playlistNext.setEnabled(false);
            mHudBinding.playlistPrevious.setEnabled(false);
            mHudBinding.selectAudioTrack.setVisibility(View.GONE);
        }
        hideOverlay(true);
        mLockBackButton = true;
        mIsLocked = true;
    }

    /**
     * Remove screen lock
     */
    private void unlockScreen() {
        if(mScreenOrientation != 100)
            setRequestedOrientation(mScreenOrientationLock);
        showInfo(R.string.unlocked, 1000);
        if (mHudBinding != null) {
            mHudBinding.lockOverlayButton.setImageResource(R.drawable.rci_lock_open_selector);
            mHudBinding.playerOverlayTime.setEnabled(true);
            mHudBinding.playerOverlaySeekbar.setEnabled(isSeekable());
            mHudBinding.playerOverlayLength.setEnabled(true);
            mHudBinding.playerOverlaySize.setEnabled(true);
            mHudBinding.playlistNext.setEnabled(true);
            mHudBinding.playlistPrevious.setEnabled(true);
        }
        mShowing = false;
        mIsLocked = false;
        showOverlay();
        updateTracksSelectors();
        mLockBackButton = false;
    }

    /**
     * Show text in the info view and vertical progress bar for "duration" milliseconds
     * @param text
     * @param duration
     * @param barNewValue new volume/brightness value (range: 0 - 15)
     */
    private void showInfoWithVerticalBar(String text, int duration, int barNewValue, int max) {
        showInfo(text, duration);
        if (mVerticalBarProgress == null)
            return;
        LinearLayout.LayoutParams layoutParams;
        if (barNewValue <= 100) {
            layoutParams = (LinearLayout.LayoutParams) mVerticalBarProgress.getLayoutParams();
            layoutParams.weight = barNewValue * 100 / max;
            mVerticalBarProgress.setLayoutParams(layoutParams);
            layoutParams = (LinearLayout.LayoutParams) mVerticalBarBoostProgress.getLayoutParams();
            layoutParams.weight = 0;
            mVerticalBarBoostProgress.setLayoutParams(layoutParams);
        } else {
            layoutParams = (LinearLayout.LayoutParams) mVerticalBarProgress.getLayoutParams();
            layoutParams.weight = 100 * 100 / max;
            mVerticalBarProgress.setLayoutParams(layoutParams);
            layoutParams = (LinearLayout.LayoutParams) mVerticalBarBoostProgress.getLayoutParams();
            layoutParams.weight = (barNewValue - 100) * 100 / max;
            mVerticalBarBoostProgress.setLayoutParams(layoutParams);
        }
        mVerticalBar.setVisibility(View.VISIBLE);
    }

    /**
     * Show text in the info view for "duration" milliseconds
     * @param text
     * @param duration
     */
    private void showInfo(String text, int duration) {
        initInfoOverlay();
        Utils.setViewVisibility(mVerticalBar, View.GONE);
        Utils.setViewVisibility(mOverlayInfo, View.VISIBLE);
        mInfo.setText(text);
        mHandler.removeMessages(FADE_OUT_INFO);
        mHandler.sendEmptyMessageDelayed(FADE_OUT_INFO, duration);
    }

    @SuppressLint("RestrictedApi")
    private void initInfoOverlay() {
        ViewStubCompat vsc = (ViewStubCompat) findViewById(R.id.player_info_stub);
        if (vsc != null) {
            vsc.inflate();
            // the info textView is not on the overlay
            mInfo = (TextView) findViewById(R.id.player_overlay_textinfo);
            mOverlayInfo = findViewById(R.id.player_overlay_info);
            mVerticalBar = findViewById(R.id.verticalbar);
            mVerticalBarProgress = findViewById(R.id.verticalbar_progress);
            mVerticalBarBoostProgress = findViewById(R.id.verticalbar_boost_progress);
        }
    }

    private void showInfo(int textid, int duration) {
        initInfoOverlay();
        Utils.setViewVisibility(mVerticalBar, View.GONE);
        Utils.setViewVisibility(mOverlayInfo, View.VISIBLE);
        mInfo.setText(textid);
        mHandler.removeMessages(FADE_OUT_INFO);
        mHandler.sendEmptyMessageDelayed(FADE_OUT_INFO, duration);
    }

    /**
     * hide the info view with "delay" milliseconds delay
     * @param delay
     */
    private void hideInfo(int delay) {
        mHandler.sendEmptyMessageDelayed(FADE_OUT_INFO, delay);
    }

    /**
     * hide the info view
     */
    private void hideInfo() {
        hideInfo(0);
    }

    private void fadeOutInfo() {
        if (mOverlayInfo != null && mOverlayInfo.getVisibility() == View.VISIBLE) {
            mOverlayInfo.startAnimation(AnimationUtils.loadAnimation(
                    VideoPlayerActivity.this, android.R.anim.fade_out));
            Utils.setViewVisibility(mOverlayInfo, View.INVISIBLE);
        }
    }

    private final Handler mHandler = new Handler(Looper.getMainLooper(), new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case FADE_OUT:
                    hideOverlay(false);
                    break;
                case FADE_OUT_INFO:
                    fadeOutInfo();
                    break;
                case INIT_PLAYBACK:
                    initPlayback();
                    break;
                case RESET_BACK_LOCK:
                    mLockBackButton = true;
                    break;
                case HIDE_INFO:
                    hideOverlay(true);
                    break;
                case SHOW_INFO:
                    showOverlay();
                    break;
            }
            return true;
        }
    });

    private void onPlaying() {
        setPlaying(true);
        setPlaybackParameters();
        stopLoading();
        updateOverlayPausePlay();

        if(mMediaSession != null) {
            mMediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_PLAYING, getTime(), getRate())
                    .setActions(PlaybackStateCompat.ACTION_PAUSE | PlaybackStateCompat.ACTION_STOP)
                    .build());
        }

        MediaItem currentMedia = getCurrentMedia();

        if(!mIsLive) {
            mMediaLength.set(mMediaPlayer.getLength());
            if(currentMedia != null) {
                currentMedia.setDuration(mMediaPlayer.getLength());
            }
        }

        if(mSeekOnStart != -1) {
            App.v(TAG, "seek on start: time=" + mSeekOnStart);
            seek(mSeekOnStart);
            mSeekOnStart = -1;
        }

        // hide overlay
        mHandler.sendEmptyMessageDelayed(FADE_OUT, OVERLAY_TIMEOUT);
        setESTracks();

        if(!mMediaStartedPlaying) {
            mMediaStartedPlaying = true;
            onMediaStarted();
        }

        if(currentMedia != null) {
            notifyPlaybackStarted(currentMedia);
        }
    }

    private void onPlaybackPaused() {
        if(mMediaSession != null) {
            mMediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_PAUSED, getTime(), getRate())
                    .setActions(PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_STOP)
                    .build());
        }
    }

    private void onPlaybackStopped() {
        if(mMediaSession != null) {
            mMediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_STOPPED, getTime(), getRate())
                    .setActions(PlaybackStateCompat.ACTION_PLAY)
                    .build());
        }
    }

    private void endReached() {
        if (isFinishing())
            return;

        if (mPlaylist.getRepeatType() == PlaylistManager.REPEAT_ONE) {
            seek(0);
            return;
        }

        if(!mPlaylist.next()) {
            // no more items in playlist
            exit();
        }
    }

    private void encounteredError() {
        if (isFinishing())
            return;

        if(!mPlaylist.next()) {
            AceStreamEngineBaseApplication.toast(R.string.playback_error);
        }
    }

    private void changeMediaPlayerLayout(int displayW, int displayH) {
        /* Change the video placement using MediaPlayer API */
        switch (mCurrentSize) {
            case VlcConstants.SURFACE_BEST_FIT:
                mMediaPlayer.setAspectRatio(null);
                mMediaPlayer.setScale(0);
                break;
            case VlcConstants.SURFACE_FIT_SCREEN:
            case VlcConstants.SURFACE_FILL: {
                Media.VideoTrack vtrack = mMediaPlayer.getCurrentVideoTrack();
                if (vtrack == null)
                    return;
                final boolean videoSwapped = vtrack.orientation == Media.VideoTrack.Orientation.LeftBottom
                        || vtrack.orientation == Media.VideoTrack.Orientation.RightTop;
                if (mCurrentSize == VlcConstants.SURFACE_FIT_SCREEN) {
                    int videoW = vtrack.width;
                    int videoH = vtrack.height;

                    if (videoSwapped) {
                        int swap = videoW;
                        videoW = videoH;
                        videoH = swap;
                    }
                    if (vtrack.sarNum != vtrack.sarDen)
                        videoW = videoW * vtrack.sarNum / vtrack.sarDen;

                    float ar = videoW / (float) videoH;
                    float dar = displayW / (float) displayH;

                    float scale;
                    if (dar >= ar)
                        scale = displayW / (float) videoW; /* horizontal */
                    else
                        scale = displayH / (float) videoH; /* vertical */
                    mMediaPlayer.setScale(scale);
                    mMediaPlayer.setAspectRatio(null);
                } else {
                    mMediaPlayer.setScale(0);
                    mMediaPlayer.setAspectRatio(!videoSwapped ? ""+displayW+":"+displayH
                            : ""+displayH+":"+displayW);
                }
                break;
            }
            case VlcConstants.SURFACE_16_9:
                mMediaPlayer.setAspectRatio("16:9");
                mMediaPlayer.setScale(0);
                break;
            case VlcConstants.SURFACE_4_3:
                mMediaPlayer.setAspectRatio("4:3");
                mMediaPlayer.setScale(0);
                break;
            case VlcConstants.SURFACE_ORIGINAL:
                mMediaPlayer.setAspectRatio(null);
                mMediaPlayer.setScale(1);
                break;
        }
    }

    @Override
    public boolean isInPictureInPictureMode() {
        return AndroidUtil.isNougatOrLater && super.isInPictureInPictureMode();
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode);
        Log.v(TAG, "onPictureInPictureModeChanged: is_pip=" + isInPictureInPictureMode);
        mPictureInPictureMode = isInPictureInPictureMode;
        changeSurfaceLayout();
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void changeSurfaceLayout() {
        // get screen size
        int sw = getWindow().getDecorView().getWidth();
        int sh = getWindow().getDecorView().getHeight();

        // sanity check
        if (sw * sh == 0) {
            Log.e(TAG, "Invalid surface size");
            return;
        }

        if (mMediaPlayer != null) {
            final IVLCVout vlcVout = mMediaPlayer.getVLCVout();
            vlcVout.setWindowSize(sw, sh);
        }

        SurfaceView surface;
        SurfaceView subtitlesSurface;
        FrameLayout surfaceFrame;
        surface = mSurfaceView;
        subtitlesSurface = mSubtitlesSurfaceView;
        surfaceFrame = mSurfaceFrame;
        LayoutParams lp = surface.getLayoutParams();

        if (mVideoWidth * mVideoHeight == 0 || isInPictureInPictureMode()) {
            /* Case of OpenGL vouts: handles the placement of the video using MediaPlayer API */
            lp.width = LayoutParams.MATCH_PARENT;
            lp.height = LayoutParams.MATCH_PARENT;
            surface.setLayoutParams(lp);
            lp = surfaceFrame.getLayoutParams();
            lp.width = LayoutParams.MATCH_PARENT;
            lp.height = LayoutParams.MATCH_PARENT;
            surfaceFrame.setLayoutParams(lp);
            if (mMediaPlayer != null && mVideoWidth * mVideoHeight == 0)
                changeMediaPlayerLayout(sw, sh);
            return;
        }

        if (mMediaPlayer != null && lp.width == lp.height && lp.width == LayoutParams.MATCH_PARENT) {
            /* We handle the placement of the video using Android View LayoutParams */
            mMediaPlayer.setAspectRatio(null);
            mMediaPlayer.setScale(0);
        }

        double dw = sw, dh = sh;
        boolean isPortrait;

        // getWindow().getDecorView() doesn't always take orientation into account, we have to correct the values
        isPortrait = mCurrentScreenOrientation == Configuration.ORIENTATION_PORTRAIT;

        if (sw > sh && isPortrait || sw < sh && !isPortrait) {
            dw = sh;
            dh = sw;
        }

        // compute the aspect ratio
        double ar, vw;
        if (mSarDen == mSarNum) {
            /* No indication about the density, assuming 1:1 */
            vw = mVideoVisibleWidth;
            ar = (double) mVideoVisibleWidth / (double) mVideoVisibleHeight;
        } else {
            /* Use the specified aspect ratio */
            vw = mVideoVisibleWidth * (double) mSarNum / mSarDen;
            ar = vw / mVideoVisibleHeight;
        }

        // compute the display aspect ratio
        double dar = dw / dh;

        switch (mCurrentSize) {
            case VlcConstants.SURFACE_BEST_FIT:
                if (dar < ar)
                    dh = dw / ar;
                else
                    dw = dh * ar;
                break;
            case VlcConstants.SURFACE_FIT_SCREEN:
                if (dar >= ar)
                    dh = dw / ar; /* horizontal */
                else
                    dw = dh * ar; /* vertical */
                break;
            case VlcConstants.SURFACE_FILL:
                break;
            case VlcConstants.SURFACE_16_9:
                ar = 16.0 / 9.0;
                if (dar < ar)
                    dh = dw / ar;
                else
                    dw = dh * ar;
                break;
            case VlcConstants.SURFACE_4_3:
                ar = 4.0 / 3.0;
                if (dar < ar)
                    dh = dw / ar;
                else
                    dw = dh * ar;
                break;
            case VlcConstants.SURFACE_ORIGINAL:
                dh = mVideoVisibleHeight;
                dw = vw;
                break;
        }

        // set display size
        lp.width = (int) Math.ceil(dw * mVideoWidth / mVideoVisibleWidth);
        lp.height = (int) Math.ceil(dh * mVideoHeight / mVideoVisibleHeight);
        surface.setLayoutParams(lp);
        subtitlesSurface.setLayoutParams(lp);

        // set frame size (crop if necessary)
        lp = surfaceFrame.getLayoutParams();
        lp.width = (int) Math.floor(dw);
        lp.height = (int) Math.floor(dh);
        surfaceFrame.setLayoutParams(lp);

        surface.invalidate();
        subtitlesSurface.invalidate();
    }

    private void sendMouseEvent(int action, int x, int y) {
        if (mMediaPlayer == null)
            return;
        final IVLCVout vlcVout = mMediaPlayer.getVLCVout();
        vlcVout.sendMouseEvent(action, 0, x, y);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mDetector == null) {
            mDetector = new GestureDetectorCompat(this, mGestureListener);
            mDetector.setOnDoubleTapListener(mGestureListener);
        }
        if (mPlaylistView.getVisibility() == View.VISIBLE) {
            // Playlist is hidden on ACTION_DOWN outside playlist. After this ACTION_MOVE and
            // ACTION_UP follow for unknown reason, which causes unwanted seek.
            // Solution is to skip all events until we receive ACTION_DOWN.
            mSkipTouch = true;
            togglePlaylist();
            return true;
        }
        if(mSkipTouch && event.getAction() != MotionEvent.ACTION_DOWN) {
            return true;
        }
        if (mTouchControls == 0 || mIsLocked) {
            // locked or swipe disabled, only handle show/hide & ignore all actions
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (!mShowing) {
                    showOverlay();
                } else {
                    hideOverlay(true);
                }
            }
            return false;
        }
        if (mDetector != null && mDetector.onTouchEvent(event))
            return true;

        final float x_changed = mTouchX != -1f && mTouchY != -1f ? event.getRawX() - mTouchX : 0f;
        final float y_changed = x_changed != 0f ? event.getRawY() - mTouchY : 0f;

        // coef is the gradient's move to determine a neutral zone
        final float coef = Math.abs (y_changed / x_changed);
        final float xgesturesize = ((x_changed / mScreen.xdpi) * 2.54f);
        final float delta_y = Math.max(1f, (Math.abs(mInitTouchY - event.getRawY()) / mScreen.xdpi + 0.5f) * 2f);

        final int xTouch = Math.round(event.getRawX());
        final int yTouch = Math.round(event.getRawY());

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // Reset flag
                mSkipTouch = false;
                // Audio
                mTouchY = mInitTouchY = event.getRawY();
                if (mMediaPlayer.getVolume() <= 100) {
                    mVol = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                    mOriginalVol = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                }
                else {
                    mVol = ((float)mMediaPlayer.getVolume()) * mAudioMax / 100;
                }
                mTouchAction = TOUCH_NONE;
                // Seek
                mTouchX = event.getRawX();
                // Mouse events for the core
                sendMouseEvent(MotionEvent.ACTION_DOWN, xTouch, yTouch);
                break;
            case MotionEvent.ACTION_MOVE:
                // Mouse events for the core
                sendMouseEvent(MotionEvent.ACTION_MOVE, xTouch, yTouch);

                // No volume/brightness action if coef < 2 or a secondary display is connected
                //TODO : Volume action when a secondary display is connected
                if (mTouchAction != TOUCH_SEEK && coef > 2) {
                    if (Math.abs(y_changed/mSurfaceYDisplayRange) < 0.05)
                        return false;
                    mTouchY = event.getRawY();
                    mTouchX = event.getRawX();
                    doVerticalTouchAction(y_changed);
                } else {
                    // Seek (Right or Left move)
                    doSeekTouch(Math.round(delta_y), mIsRtl ? -xgesturesize : xgesturesize , false);
                }
                break;
            case MotionEvent.ACTION_UP:
                // Mouse events for the core
                sendMouseEvent(MotionEvent.ACTION_UP, xTouch, yTouch);
                // Seek
                if (mTouchAction == TOUCH_SEEK)
                    doSeekTouch(Math.round(delta_y), mIsRtl ? -xgesturesize : xgesturesize , true);
                mTouchX = -1f;
                mTouchY = -1f;
                break;
        }
        return mTouchAction != TOUCH_NONE;
    }

    private void doVerticalTouchAction(float y_changed) {
        final boolean rightAction = (int) mTouchX > (4 * mScreen.widthPixels / 7f);
        final boolean leftAction = !rightAction && (int) mTouchX < (3 * mScreen.widthPixels / 7f);
        if (!leftAction && !rightAction)
            return;
        final boolean audio = (mTouchControls & TOUCH_FLAG_AUDIO_VOLUME) != 0;
        final boolean brightness = (mTouchControls & TOUCH_FLAG_BRIGHTNESS) != 0;
        if (!audio && !brightness)
            return;
        if (rightAction ^ mIsRtl) {
            if (audio)
                doVolumeTouch(y_changed);
            else
                doBrightnessTouch(y_changed);
        } else {
            if (brightness)
                doBrightnessTouch(y_changed);
            else
                doVolumeTouch(y_changed);
        }
        hideOverlay(true);
    }

    private void doSeekTouch(int coef, float gesturesize, boolean seek) {
        if (coef == 0)
            coef = 1;
        // No seek action if coef > 0.5 and gesturesize < 1cm
        if (Math.abs(gesturesize) < 1 || !isSeekable())
            return;

        if (mTouchAction != TOUCH_NONE && mTouchAction != TOUCH_SEEK)
            return;
        mTouchAction = TOUCH_SEEK;

        long length = mMediaPlayer.getLength();
        long time = getTime();

        // Size of the jump, 10 minutes max (600000), with a bi-cubic progression, for a 8cm gesture
        int jump = (int) ((Math.signum(gesturesize) * ((600000 * Math.pow((gesturesize / 8), 4)) + 3000)) / coef);

        // Adjust the jump
        if ((jump > 0) && ((time + jump) > length))
            jump = (int) (length - time);
        if ((jump < 0) && ((time + jump) < 0))
            jump = (int) -time;

        //Jump !
        if (seek && length > 0)
            seek(time + jump, length);

        if (length > 0)
            //Show the jump's size
            showInfo(String.format("%s%s (%s)%s",
                    jump >= 0 ? "+" : "",
                    Utils.millisToString(jump),
                    Utils.millisToString(time + jump),
                    coef > 1 ? String.format(" x%.1g", 1.0/coef) : ""), 50);
        else
            showInfo(R.string.unseekable_stream, 1000);
    }

    private void doVolumeTouch(float y_changed) {
        if (mTouchAction != TOUCH_NONE && mTouchAction != TOUCH_VOLUME)
            return;
        float delta = - ((y_changed / (float) mScreen.heightPixels) * mAudioMax);
        mVol += delta;
        int vol = (int) Math.min(Math.max(mVol, 0), mAudioMax);
        if (delta < 0)
            mOriginalVol = vol;
        if (delta != 0f) {
            if (vol <= mAudioMax) {
                setAudioVolume(vol);
            }
        }
    }

    //Toast that appears only once
    public void displayWarningToast() {
        if(warningToast != null)
            warningToast.cancel();
        warningToast = Toast.makeText(getApplication(), R.string.audio_boost_warning, Toast.LENGTH_SHORT);
        warningToast.show();
    }

    private void setAudioVolume(int vol) {
        if (AndroidUtil.isNougatOrLater && (vol <= 0 ^ mMute)) {
            mute(!mMute);
            return; //Android N+ throws "SecurityException: Not allowed to change Do Not Disturb state"
        }

        /* Since android 4.3, the safe volume warning dialog is displayed only with the FLAG_SHOW_UI flag.
         * We don't want to always show the default UI volume, so show it only when volume is not set. */
        if (vol <= mAudioMax) {
            mMediaPlayer.setVolume(100);
            if (vol !=  mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) {
                try {
                    mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0);
                    // High Volume warning can block volume setting
                    if (mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC) != vol)
                        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, AudioManager.FLAG_SHOW_UI);
                } catch (SecurityException ignored) {} //Some device won't allow us to change volume
            }
            vol = Math.round(vol * 100 / mAudioMax);
        } else {
            vol = Math.round(vol * 100 / mAudioMax);
            mMediaPlayer.setVolume(Math.round(vol));
        }
        mTouchAction = TOUCH_VOLUME;
        showInfoWithVerticalBar(getString(R.string.volume) + "\n" + Integer.toString(vol) + '%', 1000, vol, 100);
    }

    private void mute(boolean mute) {
        if(mMute == mute) return;

        mMute = mute;
        if (mMute)
            mVolSave = mMediaPlayer.getVolume();

        // Sometimes getVolume() returns 0, fix it
        if(mVolSave == 0) {
            mVolSave = 100;
        }

        mMediaPlayer.setVolume(mMute ? 0 : mVolSave);
    }

    private void updateMute () {
        mute(!mMute);
        showInfo(mMute ? R.string.sound_off : R.string.sound_on, 1000);
    }

    private void initBrightnessTouch() {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        float brightnesstemp = lp.screenBrightness != -1f ? lp.screenBrightness : 0.6f;
        // Initialize the layoutParams screen brightness
        try {
            if (Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
                if(!Utils.canWriteSystemSettings(this)) {
                    //TODO: ask for permissions?
                    return;
                }
                Settings.System.putInt(getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                mRestoreAutoBrightness = Settings.System.getInt(getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS) / 255.0f;
            } else if (brightnesstemp == 0.6f) {
                brightnesstemp = Settings.System.getInt(getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS) / 255.0f;
            }
        } catch (SettingNotFoundException e) {
            e.printStackTrace();
        }
        lp.screenBrightness = brightnesstemp;
        getWindow().setAttributes(lp);
        mIsFirstBrightnessGesture = false;
    }

    private void doBrightnessTouch(float y_changed) {
        if (mTouchAction != TOUCH_NONE && mTouchAction != TOUCH_BRIGHTNESS)
            return;
        if (mIsFirstBrightnessGesture) initBrightnessTouch();
        mTouchAction = TOUCH_BRIGHTNESS;

        // Set delta : 2f is arbitrary for now, it possibly will change in the future
        float delta = - y_changed / mSurfaceYDisplayRange;

        changeBrightness(delta);
    }

    private void changeBrightness(float delta) {
        // Estimate and adjust Brightness
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        float brightness =  Math.min(Math.max(lp.screenBrightness + delta, 0.01f), 1f);
        setWindowBrightness(brightness);
        brightness = Math.round(brightness * 100);
        showInfoWithVerticalBar(getString(R.string.brightness) + "\n" + (int) brightness + '%', 1000, (int) brightness, 100);
    }

    private void setWindowBrightness(float brightness) {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness =  brightness;
        // Set Brightness
        getWindow().setAttributes(lp);
    }

    /**
     * handle changes of the seekbar (slicer)
     */
    private final OnSeekBarChangeListener mSeekListener = new OnSeekBarChangeListener() {
        int seekValue = -1;

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            mDragging = true;
            showOverlayTimeout(OVERLAY_INFINITE);
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            mDragging = false;
            showOverlay(true);
            try {
                App.vv(TAG, "onStopTrackingTouch: live=" + mIsLive + " length=" + mMediaLength.get());
                doSeek(seekValue);
            } catch (Exception e) {
                Log.e(TAG, "progress seek error", e);
            }
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            //TODO: seek now if playing direct content (vlc is seeking now, but we cannot do it for p2p sessions)
            seekValue = progress;
            int currentTime = 0;

            if(fromUser) {
                showOverlay();
            }

            if (!isFinishing() && fromUser && isSeekable() && mMediaLength.get() > 0) {
                if(mIsLive) {
                    // live
                    if(mLastLivePos != null) {
                        int duration = mLastLivePos.lastTimestamp - mLastLivePos.firstTimestamp;
                        int pieces = mLastLivePos.last - mLastLivePos.first;

                        if (duration > 0 && pieces > 0) {
                            float secondsPerPiece = duration / pieces;
                            currentTime = Math.round((mMediaLength.get() - seekValue) * secondsPerPiece * 1000);
                        }
                    }
                }
                else {
                    // vod
                    currentTime = progress;
                }

                showInfo(Utils.millisToString(currentTime), 1000);
            }
        }
    };

    @MainThread
    private void doSeek(int seekValue) {
        Log.v(TAG, "doSeek: live=" + mIsLive + " seekTo=" + seekValue + " length=" + mMediaLength.get());
        if(mMediaLength.get() == 0) return;

        int seekTo;

        if(mIsLive) {
            // live
            if(mLastLivePos != null) {
                boolean skipSeek = false;

                seekTo = mLastLivePos.first + seekValue;
                int len = mLastLivePos.last - mLastLivePos.first;
                if(len > 0) {
                    float percentToCurrent = Math.abs(seekTo - mLastLivePos.pos) / (float)len;
                    float percentToLive = Math.abs(seekTo - mLastLivePos.last) / (float)len;

                    if(percentToCurrent < 0.05) {
                        skipSeek = true;
                    }

                    if(percentToLive < 0.05) {
                        seekTo = -1;
                    }
                }

                if(!skipSeek) {
                    Log.d(TAG, "progress:live: seek to: " + seekTo + " (value=" + seekValue + " first=" + mLastLivePos.first + " last=" + mLastLivePos.last + " pos=" + mLastLivePos.pos + ")");

                    if (mPlaybackManager != null) {
                        mPlaybackManager.liveSeek(seekTo);
                    }

                    if (mHudBinding != null) {

                        if(seekTo == -1) {
                            Utils.setBackgroundWithPadding(mHudBinding.goLiveButton, R.drawable.button_live_blue);
                            mHudBinding.goLiveButton.setTextColor(getResources().getColor(R.color.live_status_yes));
                        }
                        else {
                            Utils.setBackgroundWithPadding(mHudBinding.goLiveButton, R.drawable.button_live_yellow);
                            mHudBinding.goLiveButton.setTextColor(getResources().getColor(R.color.live_status_no));
                        }
                    }

                    // to freeze live pos for some time
                    freezeLiveStatusAt = new Date().getTime();
                    freezeLivePosAt = new Date().getTime();
                }
            }
        }
        else {
            // vod
            seekTo = seekValue;
            Log.d(TAG, "progress:vod: seek to: " + seekTo);
            seek(seekTo);
        }
    }

    @SuppressWarnings("unused")
    public void onAudioOptionsClick(View anchor){
        if (anchor == null) {
            initOverlay();
            anchor = mHudBinding.selectAudioTrack;
        }
        final AppCompatActivity context = this;
        final PopupMenu popupMenu = new PopupMenu(this, anchor);
        final Menu menu = popupMenu.getMenu();
        popupMenu.getMenuInflater().inflate(R.menu.audio_options, menu);
        menu.findItem(R.id.menu_audio_track).setEnabled(mMediaPlayer.getAudioTracksCount() > 0);
        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == R.id.menu_audio_track) {
                    selectAudioTrack();
                    return true;
                } else if (item.getItemId() == R.id.menu_audio_output) {
                    selectAudioOutput();
                    return true;
                }
                hideOverlay(true);
                return false;
            }
        });
        popupMenu.show();
        showOverlay();
    }

    @Override
    public void onClick(View v) {
        int i = v.getId();
        if (i == R.id.ad_player_skip_container) {
            adPlayerSkip();

        } else if (i == R.id.ad_player_button_click) {
            adPlayerClick();

        } else if (i == R.id.button_show_bonus_ads) {
            showBonusAds();

        } else if (i == R.id.button_skip_bonus_ads) {
            skipBonusAds();

        } else if (i == R.id.playlist_toggle) {
            togglePlaylist();

        } else if (i == R.id.pip_toggle) {
            switchToPopup();

        } else if (i == R.id.switch_player) {
            showResolver();

        } else if (i == R.id.player_overlay_adv_function) {
            showAdvancedOptions();

        }
    }

    private void showResolver() {
        Log.d(TAG, "showResolver: startedFromExternalRequest=" + mStartedFromExternalRequest);

        if (mPlaylist == null) {
            App.v(TAG, "showResolver: no service");
            return;
        }

        final MediaItem item = mPlaylist.getCurrentItem();
        if (item == null) {
            App.v(TAG, "showResolver: no current media");
            return;
        }

        if (!item.isP2PItem()) {
            App.v(TAG, "showResolver: not p2p item");
            return;
        }

        MediaFilesResponse.MediaFile mf = item.getMediaFile();
        if (mf == null) {
            try {
                mDescriptor = item.getDescriptor();
            } catch (TransportFileParsingException e) {
                App.v(TAG, "showResolver: failed to get descriptor: " + e.getMessage());
                return;
            }

            if (mPlaybackManager == null) {
                App.v(TAG, "showResolver: missing current media file, no playback manager");
            } else {
                App.v(TAG, "showResolver: missing current media file, get from engine");
                mPlaybackManager.getMediaFileAsync(mDescriptor, item, new Callback<Pair<String, MediaFilesResponse.MediaFile>>() {
                    @Override
                    public void onSuccess(Pair<String, MediaFilesResponse.MediaFile> result) {
                        showResolver(result.second);
                    }

                    @Override
                    public void onError(String err) {
                        App.v(TAG, "showResolver: missing current media file, failed to get from engine: " + err);
                        AceStreamEngineBaseApplication.toast(err);
                    }
                });
            }
            return;
        }

        showResolver(mf);
    }

    private void showResolver(MediaFilesResponse.MediaFile mf) {
        Intent intent = new AceStream.Resolver.IntentBuilder(
                this,
                mf.infohash,
                mf.type,
                mf.mime)
                .showAceStreamPlayer(false)
                .allowRememberPlayer(mStartedFromExternalRequest)
                .build();
        startActivityForResult(intent, REQUEST_CODE_SELECT_PLAYER);
    }

    public void toggleLock() {
        if (mIsLocked)
            unlockScreen();
        else
            lockScreen();
    }

    public boolean toggleLoop(View v) {
        if (mPlaylist == null) return false;
        if (mPlaylist.getRepeatType() == PlaylistManager.REPEAT_ONE) {
            showInfo(getString(R.string.repeat), 1000);
            mPlaylist.setRepeatType(PlaylistManager.REPEAT_NONE);
        } else {
            mPlaylist.setRepeatType(PlaylistManager.REPEAT_ONE);
            showInfo(getString(R.string.repeat_single), 1000);
        }
        return true;
    }

    private interface TrackSelectedListener {
        void onTrackSelected(int trackID);
    }

    private void setAudioOutput(String aout, boolean pause) {
        if(pause) {
            App.v(TAG, "setAudioOutput: set pause");
            mMediaPlayer.pause();
            final String fAout = aout;
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    setAudioOutput(fAout, false);
                }
            }, 500);
            return;
        }

        boolean success;
        String device = null;
        if(aout == null) {
            aout = "android_audiotrack";
        }
        mAout = aout;

        if(TextUtils.equals(aout, "android_audiotrack")) {
            device = "encoded";
        }

        App.v(TAG, "selectAudioOutput: aout=" + aout + " device=" + device);

        success = mMediaPlayer.setAudioOutput(aout)
            && (device == null || mMediaPlayer.setAudioOutputDevice(device));
        App.v(TAG, "selectAudioOutput: done: success=" + success);

        int otherTrack = -1;
        int audioTrack = mMediaPlayer.getAudioTrack();
        int audioTrackCount = mMediaPlayer.getAudioTracksCount();

        if(audioTrackCount > 1) {
            otherTrack = (audioTrack + 1) % audioTrackCount;
        }

        if(otherTrack != -1) {
            App.v(TAG, "selectAudioOutput: set new track: track=" + otherTrack);
            mMediaPlayer.setAudioTrack(otherTrack);
            App.v(TAG, "selectAudioOutput: set current track: track=" + audioTrack);
            mMediaPlayer.setAudioTrack(audioTrack);
            App.v(TAG, "selectAudioOutput: set track done");
        }

        mMediaPlayer.play();

        if(DEBUG_AUDIO_OUTPUT_SWITCHER) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (TextUtils.equals(mAout, "android_audiotrack")) {
                        setAudioOutput("opensles_android", true);
                    } else {
                        setAudioOutput("android_audiotrack", true);
                    }
                }
            }, 5000);
        }
    }

    public void selectAudioOutput() {
        if (!isFinishing()) {
            final String[] aoutIds = {"android_audiotrack", "opensles_android"};
            final String[] aoutNames = {"AudioTrack", "OpenSL ES"};
            String currentAout = mAout;

            if(currentAout == null) {
                // Default is 'android_audiotrack'
                currentAout = "android_audiotrack";
            }

            int listPosition = -1;
            for(int i = 0; i < aoutIds.length; i++) {
                if(TextUtils.equals(aoutIds[i], currentAout)) {
                    listPosition = i;
                    break;
                }
            }

            if(DEBUG_AUDIO_OUTPUT_SWITCHER) {
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        setAudioOutput("opensles_android", true);
                    }
                }, 10);
                return;
            }

            mAlertDialog = new AlertDialog.Builder(VideoPlayerActivity.this)
                    .setTitle(R.string.aout)
                    .setSingleChoiceItems(aoutNames, listPosition, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int listPosition) {
                            final String id = aoutIds[listPosition];
                            setAudioOutput(id, true);
                            dialog.dismiss();
                        }
                    })
                    .create();
            mAlertDialog.setCanceledOnTouchOutside(true);
            mAlertDialog.setOwnerActivity(VideoPlayerActivity.this);
            mAlertDialog.show();
        }
    }

    private void selectTrack(final MediaPlayer.TrackDescription[] tracks, int currentTrack, int titleId,
                             final TrackSelectedListener listener) {
        if (listener == null)
            throw new IllegalArgumentException("listener must not be null");
        if (tracks == null)
            return;
        final String[] nameList = new String[tracks.length];
        final int[] idList = new int[tracks.length];
        int i = 0;
        int listPosition = 0;
        for (MediaPlayer.TrackDescription track : tracks) {
            idList[i] = track.id;
            nameList[i] = track.name;
            // map the track position to the list position
            if (track.id == currentTrack)
                listPosition = i;
            i++;
        }

        if (!isFinishing()) {
            mAlertDialog = new AlertDialog.Builder(VideoPlayerActivity.this)
                    .setTitle(titleId)
                    .setSingleChoiceItems(nameList, listPosition, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int listPosition) {
                            int trackID = -1;
                            // Reverse map search...
                            for (MediaPlayer.TrackDescription track : tracks) {
                                if (idList[listPosition] == track.id) {
                                    trackID = track.id;
                                    break;
                                }
                            }
                            listener.onTrackSelected(trackID);
                            dialog.dismiss();
                        }
                    })
                    .create();
            mAlertDialog.setCanceledOnTouchOutside(true);
            mAlertDialog.setOwnerActivity(VideoPlayerActivity.this);
            mAlertDialog.show();
        }
    }

    public void selectAudioTrack() {
        setESTrackLists();
        selectTrack(mAudioTracksList, mMediaPlayer.getAudioTrack(), R.string.track_audio,
                new TrackSelectedListener() {
                    @Override
                    public void onTrackSelected(int trackID) {
                        if (trackID < -1 || mMediaPlayer == null) return;
                        mMediaPlayer.setAudioTrack(trackID);
                        mMetaDataManager.putInt(getCurrentMedia(), MetaDataManager.META_AUDIO_TRACK, trackID);
                    }
                });
    }

    public void selectSubtitles() {
        setESTrackLists();
        selectTrack(mSubtitleTracksList, mMediaPlayer.getSpuTrack(), R.string.track_text,
                new TrackSelectedListener() {
                    @Override
                    public void onTrackSelected(int trackID) {
                        if (trackID < -1 || mMediaPlayer == null)
                            return;
                        mMediaPlayer.setSpuTrack(trackID);
                        mMetaDataManager.putInt(getCurrentMedia(), MetaDataManager.META_SUBTITLE_TRACK, trackID);
                    }
                });
    }

    private void updateSeekable(boolean seekable) {
        App.vv(TAG, "updateSeekable: seekable=" + seekable);
        if (mHudBinding == null) return;
        if (!mIsLocked)
            mHudBinding.playerOverlaySeekbar.setEnabled(seekable);
    }

    private void updatePausable(boolean pausable) {
        App.vv(TAG, "updatePausable: pausable=" + pausable);
        if (mHudBinding == null) return;
        mHudBinding.playerOverlayPlay.setEnabled(pausable);
    }

    public void doPlayPause() {
        if (!isPausable()) return;
        if (mMediaPlayer.isPlaying()) {
            showOverlayTimeout(OVERLAY_INFINITE);
            pause();
            onContentPaused();
        } else {
            hideOverlay(true);
            if(!onContentUnpaused()) {
                App.v(TAG, "doPlayPause: play now");
                play();
            }
            else {
                App.v(TAG, "doPlayPause: skip play because ads are going to be displayed");
            }
        }
    }

    private long getTime() {
        long time = mMediaPlayer.getTime();
        if (mForcedTime != -1 && mLastTime != -1) {
            /* XXX: After a seek, mService.getTime can return the position before or after
             * the seek position. Therefore we return mForcedTime in order to avoid the seekBar
             * to move between seek position and the actual position.
             * We have to wait for a valid position (that is after the seek position).
             * to re-init mLastTime and mForcedTime to -1 and return the actual position.
             */
            if (mLastTime > mForcedTime) {
                if (time <= mLastTime && time > mForcedTime || time > mLastTime)
                    mLastTime = mForcedTime = -1;
            } else {
                if (time > mForcedTime)
                    mLastTime = mForcedTime = -1;
            }
        }
        return mForcedTime == -1 ? time : mForcedTime;
    }

    protected void seek(long position) {
        seek(position, mMediaPlayer.getLength());
    }

    private void seek(long position, long length) {
        mForcedTime = position;
        mLastTime = mMediaPlayer.getTime();
        if (length > 0) {
            mMediaPlayer.setPosition((float)position/length);
        }
        else {
            mMediaPlayer.setTime(position);
        }
        mProgress.set((int) position);
        mCurrentTime.set((int) position);
    }

    private void seekDelta(int delta) {
        // unseekable stream
        if (mMediaPlayer.getLength() <= 0 || !isSeekable()) return;

        long position = getTime() + delta;
        if (position < 0) position = 0;
        seek(position);
        StringBuilder sb = new StringBuilder();
        if (delta > 0f)
            sb.append('+');
        sb.append((int)(delta/1000f))
                .append("s (")
                .append(Utils.millisToString(mMediaPlayer.getTime()))
                .append(')');
        showInfo(sb.toString(), 1000);
    }

    public void resizeVideo() {
        int newSize;
        if (mCurrentSize < VlcConstants.SURFACE_ORIGINAL) {
            newSize = mCurrentSize+1;
        } else {
            newSize = 0;
        }
        setCurrentSize(newSize);
    }

    protected void setCurrentSize(int size) {
        mCurrentSize = size;
        changeSurfaceLayout();
        switch (mCurrentSize) {
            case VlcConstants.SURFACE_BEST_FIT:
                showInfo(R.string.surface_best_fit, 1000);
                break;
            case VlcConstants.SURFACE_FIT_SCREEN:
                showInfo(R.string.surface_fit_screen, 1000);
                break;
            case VlcConstants.SURFACE_FILL:
                showInfo(R.string.surface_fill, 1000);
                break;
            case VlcConstants.SURFACE_16_9:
                showInfo("16:9", 1000);
                break;
            case VlcConstants.SURFACE_4_3:
                showInfo("4:3", 1000);
                break;
            case VlcConstants.SURFACE_ORIGINAL:
                showInfo(R.string.surface_original, 1000);
                break;
        }
        showOverlay();
    }

    /**
     * show overlay
     * @param forceCheck: adjust the timeout in function of playing state
     */
    private void showOverlay(boolean forceCheck) {
        if (forceCheck)
            mOverlayTimeout = 0;
        showOverlayTimeout(0);
    }

    /**
     * show overlay with the previous timeout value
     */
    private void showOverlay() {
        showOverlay(false);
    }

    /**
     * show overlay
     */
    protected void showOverlayTimeout(int timeout) {
        if(isFinishing())
            return;
        if(isInPictureInPictureMode())
            return;
        initOverlay();
        if (timeout != 0)
            mOverlayTimeout = timeout;
        else
            mOverlayTimeout = (mMediaPlayer != null && mMediaPlayer.isPlaying())
                    ? OVERLAY_TIMEOUT
                    : OVERLAY_INFINITE;
        if (!mShowing) {
            mShowing = true;
            if (!mIsLocked) {
                showControls(true);
            }
            else {
                // show lock button
                mHudBinding.lockOverlayButton.setVisibility(View.VISIBLE);
            }
            dimStatusBar(false);
            mHudBinding.progressOverlay.setVisibility(View.VISIBLE);
            updateOverlayPausePlay();
        }
        mHandler.removeMessages(FADE_OUT);
        if (mOverlayTimeout != OVERLAY_INFINITE)
            mHandler.sendMessageDelayed(mHandler.obtainMessage(FADE_OUT), mOverlayTimeout);
    }

    private void showControls(boolean show) {
        if (mHudBinding != null) {
            mHudBinding.playerOverlayPlay.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
            mHudBinding.playerOverlaySize.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
            if(mShowLockButton) {
                mHudBinding.lockOverlayButton.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
            }
            if (mHasPlaylist) {
                mHudBinding.playlistPrevious.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
                mHudBinding.playlistNext.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
            }
        }
    }

    protected AcePlayerHudBinding mHudBinding;
    protected ObservableInt mProgress = new ObservableInt(0);
    protected ObservableInt mCurrentTime = new ObservableInt(0);
    protected ObservableLong mMediaLength = new ObservableLong(0L);
    protected ObservableField<String> mTitle = new ObservableField<>();
    private boolean mHasPlaylist;
    private boolean mShowLockButton = false;

    @SuppressLint("RestrictedApi")
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void initOverlay() {
        final ViewStubCompat vsc = (ViewStubCompat) findViewById(R.id.player_hud_stub);
        if (vsc != null) {
            vsc.inflate();
            mHudBinding = AcePlayerHudBinding.bind(findViewById(R.id.progress_overlay));
            mHudBinding.setPlayer(this);
            updateTimeValues();
            mHudBinding.setProgress(mProgress);
            //:ace
            mHudBinding.setCurrentTime(mCurrentTime);
            mHudBinding.setTitle(mTitle);
            ///ace
            mHudBinding.setLength(mMediaLength);
            final RelativeLayout.LayoutParams layoutParams =
                    (RelativeLayout.LayoutParams)mHudBinding.progressOverlay.getLayoutParams();
            if (Utils.isPhone || !Utils.hasNavBar)
                layoutParams.width = LayoutParams.MATCH_PARENT;
            else
                layoutParams.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);
            mHudBinding.progressOverlay.setLayoutParams(layoutParams);
            resetHudLayout();
            updateOverlayPausePlay();
            updateSeekable(isSeekable());
            updatePausable(isPausable());
            setListeners(true);
            initPlaylistUi();
            updateTracksSelectors();
        }
    }


    /**
     * hider overlay
     */
    protected void hideOverlay(boolean fromUser) {
        if (mShowing) {
            mHandler.removeMessages(FADE_OUT);
            mHandler.removeMessages(SHOW_PROGRESS);
            mHudBinding.progressOverlay.setVisibility(View.INVISIBLE);
            showControls(false);
            mShowing = false;
            dimStatusBar(true);
        } else if (!fromUser) {
            /*
             * Try to hide the Nav Bar again.
             * It seems that you can't hide the Nav Bar if you previously
             * showed it in the last 1-2 seconds.
             */
            dimStatusBar(true);
        }
    }

    /**
     * Dim the status bar and/or navigation icons when needed on Android 3.x.
     * Hide it on Android 4.0 and later
     */
    @TargetApi(Build.VERSION_CODES.KITKAT)
    private void dimStatusBar(boolean dim) {
        if (dim || mIsLocked)
            hideActionBar();
        else
            showActionBar();
        int visibility = 0;
        int navbar = 0;

        visibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        navbar = View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if (dim || mIsLocked) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            navbar |= View.SYSTEM_UI_FLAG_LOW_PROFILE;
            if (!Utils.hasCombBar) {
                navbar |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
                if (AndroidUtil.isKitKatOrLater)
                    visibility |= View.SYSTEM_UI_FLAG_IMMERSIVE;
                visibility |= View.SYSTEM_UI_FLAG_FULLSCREEN;
            }
        } else {
            showActionBar();
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            visibility |= View.SYSTEM_UI_FLAG_VISIBLE;
        }

        if (Utils.hasNavBar)
            visibility |= navbar;
        getWindow().getDecorView().setSystemUiVisibility(visibility);
    }

    private void showActionBar() {
        if(!mIsAdDisplayed) {
            mActionBar.show();
        }
    }

    private void hideActionBar() {
        mActionBar.hide();
    }

    private void updateOverlayPausePlay() {
        if (mHudBinding == null)
            return;
        if (isPausable())
            mHudBinding.playerOverlayPlay.setImageResource(mMediaPlayer != null && mMediaPlayer.isPlaying()
                    ? R.drawable.rci_pause_selector
                    : R.drawable.rci_play_selector);
        if(!mIsAdDisplayed) {
            mHudBinding.playerOverlayPlay.requestFocus();
        }
    }

    private void invalidateESTracks(int type) {
        switch (type) {
            case Media.Track.Type.Audio:
                mAudioTracksList = null;
                break;
            case Media.Track.Type.Text:
                mSubtitleTracksList = null;
                break;
        }

        updateTracksSelectors();
    }

    private void updateTracksSelectors() {
        if(mHudBinding != null) {
            mHudBinding.selectAudioTrack.setVisibility(mIsLocked ? View.GONE : View.VISIBLE);
            mHudBinding.selectAudioTrack.setEnabled(mMediaPlayer.getAudioTracksCount() > 0);
            mHudBinding.selectSubtitles.setVisibility(mMediaPlayer.getSpuTracksCount() > 0 ? View.VISIBLE : View.GONE);
        }
    }

    private void setESTracks() {
        if (mLastAudioTrack >= -1) {
            mMediaPlayer.setAudioTrack(mLastAudioTrack);
            mLastAudioTrack = -2;
        }
        if (mLastSpuTrack >= -1) {
            mMediaPlayer.setSpuTrack(mLastSpuTrack);
            mLastSpuTrack = -2;
        }
    }

    private void setESTrackLists() {
        if (mAudioTracksList == null && mMediaPlayer.getAudioTracksCount() > 0)
            mAudioTracksList = mMediaPlayer.getAudioTracks();
        if (mSubtitleTracksList == null && mMediaPlayer.getSpuTracksCount() > 0)
            mSubtitleTracksList = mMediaPlayer.getSpuTracks();
        if (mVideoTracksList == null && mMediaPlayer.getVideoTracksCount() > 0)
            mVideoTracksList = mMediaPlayer.getVideoTracks();
    }

    @Override
    public void play() {
        if(mMediaPlayer != null)
            mMediaPlayer.play();
        if (mRootView != null)
            mRootView.setKeepScreenOn(true);
    }

    @Override
    public void pause() {
        if(mMediaPlayer != null)
            mMediaPlayer.pause();
        if (mRootView != null)
            mRootView.setKeepScreenOn(false);
    }

    public void next() {
        if (mPlaylist != null) mPlaylist.next();
    }

    public void previous() {
        if (mPlaylist != null) mPlaylist.previous();
    }

    @SuppressWarnings("deprecation")
    private int getScreenRotation(){
        final WindowManager wm = (WindowManager) getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return Surface.ROTATION_0;
        final Display display = wm.getDefaultDisplay();
        try {
            final Method m = display.getClass().getDeclaredMethod("getRotation");
            return (Integer) m.invoke(display);
        } catch (Exception e) {
            return Surface.ROTATION_0;
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private int getScreenOrientation(int mode){
        switch(mode) {
            case 99: //screen orientation user
                return AndroidUtil.isJellyBeanMR2OrLater ?
                        ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR :
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR;
            case 101: //screen orientation landscape
                return ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
            case 102: //screen orientation portrait
                return ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT;
        }
        /*
         mScreenOrientation = 100, we lock screen at its current orientation
         */
        final WindowManager wm = (WindowManager) getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return 0;
        final Display display = wm.getDefaultDisplay();
        int rot = getScreenRotation();
        /*
         * Since getRotation() returns the screen's "natural" orientation,
         * which is not guaranteed to be SCREEN_ORIENTATION_PORTRAIT,
         * we have to invert the SCREEN_ORIENTATION value if it is "naturally"
         * landscape.
         */
        @SuppressWarnings("deprecation")
        boolean defaultWide = display.getWidth() > display.getHeight();
        if(rot == Surface.ROTATION_90 || rot == Surface.ROTATION_270)
            defaultWide = !defaultWide;
        if(defaultWide) {
            switch (rot) {
                case Surface.ROTATION_0:
                    return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                case Surface.ROTATION_90:
                    return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                case Surface.ROTATION_180:
                    // SCREEN_ORIENTATION_REVERSE_PORTRAIT only available since API
                    // Level 9+
                    return ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                case Surface.ROTATION_270:
                    // SCREEN_ORIENTATION_REVERSE_LANDSCAPE only available since API
                    // Level 9+
                    return ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                default:
                    return 0;
            }
        } else {
            switch (rot) {
                case Surface.ROTATION_0:
                    return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                case Surface.ROTATION_90:
                    return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                case Surface.ROTATION_180:
                    // SCREEN_ORIENTATION_REVERSE_PORTRAIT only available since API
                    // Level 9+
                    return ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                case Surface.ROTATION_270:
                    // SCREEN_ORIENTATION_REVERSE_LANDSCAPE only available since API
                    // Level 9+
                    return ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                default:
                    return 0;
            }
        }
    }

    public void showConfirmResumeDialog() {
        if (isFinishing()) {
            return;
        }

        final MediaItem currentMedia = mPlaylist.getCurrentItem();
        if(currentMedia == null) {
            return;
        }

        // Reset because we want to ask only once
        mAskResume = false;

        mMediaPlayer.pause();
        mAlertDialog = new AlertDialog.Builder(VideoPlayerActivity.this)
                .setMessage(R.string.confirm_resume)
                .setPositiveButton(R.string.resume_from_position, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        mPlayFromStart = false;
                        play(currentMedia);
                    }
                })
                .setNegativeButton(R.string.play_from_start, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        mPlayFromStart = true;
                        play(currentMedia);
                    }
                })
                .create();
        mAlertDialog.setCancelable(false);
        mAlertDialog.show();
    }

    public void showAdvancedOptions() {
        final FragmentManager fm = getSupportFragmentManager();
        final PlayerOptionsFragment dialog = new PlayerOptionsFragment();
        dialog.show(fm, "player_options");
        hideOverlay(false);
    }

    private void togglePlaylist() {
        if (mPlaylistView.getVisibility() == View.VISIBLE) {
            mPlaylistView.setVisibility(View.GONE);
            mPlaylistView.setOnClickListener(null);
            return;
        }
        hideOverlay(true);
        mPlaylistAdapter.resetCurrentIndex();
        mPlaylistView.setVisibility(View.VISIBLE);
        mPlaylistView.setAdapter(mPlaylistAdapter);
        mPlaylistView.scrollToPosition(mPlaylist.getCurrentMediaPosition());
    }

    private void startLoading() {
        if (mIsLoading)
            return;
        mIsLoading = true;
        mLoading.setVisibility(View.VISIBLE);
    }

    private void stopLoading() {
        if (!mIsLoading) return;
        mIsLoading = false;
        mLoading.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onNewVideoLayout(IVLCVout vlcVout, int width, int height, int visibleWidth, int visibleHeight, int sarNum, int sarDen) {
        // store video size
        mVideoWidth = width;
        mVideoHeight = height;
        mVideoVisibleWidth  = visibleWidth;
        mVideoVisibleHeight = visibleHeight;
        mSarNum = sarNum;
        mSarDen = sarDen;
        changeSurfaceLayout();
    }

    @Override
    public void onSurfacesCreated(IVLCVout vlcVout) {
    }

    @Override
    public void onSurfacesDestroyed(IVLCVout vlcVout) {
    }

    protected void sendRemotePlaybackStarted() {
        if(mRemoteClientId != null) {
            Log.v(TAG, "sendRemotePlaybackStarted");
            JsonRpcMessage msg = new JsonRpcMessage(AceStreamRemoteDevice.Messages.PLAYBACK_STARTED);
            msg.addParam("selectedPlayer", SelectedPlayer.getOurPlayer().getId());
            mDiscoveryServerServiceClient.sendClientMessage(mRemoteClientId, msg);
            mRemotePlayckStarted = true;
        }
    }

    @Override
    public void onP2PSessionStarted(VastTag[] vastTags) {
        if(!mIsStarted) {
            Log.v(TAG, "receiver: p2p session started: activity stopped");
            return;
        }

        unfreezeEngineStatus();
        if(vastTags == null) {
            Log.v(TAG, "receiver: p2p session started: no vast tags");
        }
        else {
            Log.v(TAG, "receiver: p2p session started: vastTags=" + vastTags.length);
        }

        if(shouldShowAds()) {
            initAds();
            initVastAds(vastTags);
            requestNextAds();
        }
    }

    @Override
    public void onP2PPlaybackStarted(Uri uri) {
        if(!mIsStarted) {
            Log.v(TAG, "receiver: p2p playback started: activity stopped");
            return;
        }

        Log.v(TAG, "receiver: p2p playback started: mRemoteClientId=" + mRemoteClientId);
        newPlayback();
        sendRemotePlaybackStarted();
    }

    @Override
    public void onP2PStarting() {
        if(!mIsStarted) {
            Log.v(TAG, "receiver: p2p starting: activity stopped");
            return;
        }

        Log.v(TAG, "receiver: p2p starting");
        AceStreamEngineBaseApplication.getInstance().logPlayRequest(SelectedPlayer.getOurPlayer());
        updatePausable(false);
        mProgress.set(0);
        mCurrentTime.set(0);
        mMediaLength.set(0);
        mIsLive = false;

        if(mRenderAds && mAdsWaterfall != null) {
            mAdsWaterfall.setPlacement(AdsWaterfall.Placement.PREROLL, true);
            mAdsWaterfall.resetInventoryStatus(AdsWaterfall.Inventory.VAST);
        }

        showLiveContainer(false);
        showStreamSelectorContainer(false);
        freezeEngineStatus(5000);
        newItemSelected();
        setEngineStatus(EngineStatus.fromString("starting"));
        mExitOnStop = false;
        mMediaPlayer.stop();
    }

    @Override
    public void onCurrentItemChanged(int position) {
        String title = null;
        MediaItem item = mPlaylist.getCurrentItem();

        Log.v(TAG, "onCurrentItemChanged: position=" + position + " item=" + item);
        if(item != null) {
            title = item.getTitle();
        }

        mTitle.set(title);
        updateMediaSessionMetadata();
    }

    @Override
    public void onBeforePlaylistPositionChanged(int newPosition) {
        MediaItem currentMedia = mPlaylist.getCurrentItem();
        if(currentMedia != null) {
            saveCurrentTime(currentMedia);
            notifySaveMetadata(currentMedia);
        }
    }

    @Override
    public void onPlaylistUpdated() {
        if(mPlaylistAdapter == null) {
            initPlaylistUi();
        }
        else {
            mHasPlaylist = (mPlaylist.size() > 1);
            mPlaylistToggle.setVisibility(mHasPlaylist ? View.VISIBLE : View.GONE);
            if (mHudBinding != null) {
                mHudBinding.playlistPrevious.setVisibility(mHasPlaylist ? View.VISIBLE : View.GONE);
                mHudBinding.playlistNext.setVisibility(mHasPlaylist ? View.VISIBLE : View.GONE);
            }
        }
    }

    @Override
    public void onP2PFailed(String errorMessage) {
        if(!mIsStarted) {
            Log.v(TAG, "receiver: p2p failed: activity stopped");
            return;
        }

        unfreezeEngineStatus();
        App.v(TAG, "receiver: p2p failed: " + errorMessage);
        setEngineStatus(EngineStatus.error(errorMessage));
        if(mRemoteClientId != null) {
            JsonRpcMessage msg = new JsonRpcMessage(AceStreamRemoteDevice.Messages.PLAYBACK_START_FAILED);
            msg.addParam("error", errorMessage);
            mDiscoveryServerServiceClient.sendClientMessage(mRemoteClientId, msg);
        }

        if(TextUtils.equals(errorMessage, "adblock detected")) {
            AceStreamEngineBaseApplication.showAdblockNotification(VideoPlayerActivity.this);
            exit();
        }
    }

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            App.v(TAG, "receiver: action=" + action);

            if (TextUtils.equals(action, AceStream.BROADCAST_APP_IN_BACKGROUND)) {
                int pid = intent.getIntExtra("pid", -1);
                if(pid == android.os.Process.myPid()) {
                    mStoppingOnDeviceDisconnect = intent.getBooleanExtra("org.acestream.extra.stop_after_device_disconnect", false);
                    App.v(TAG, "receiver: exit player: stoppingOnDeviceDisconnect=" + mStoppingOnDeviceDisconnect);
                    exit();
                }
            }
            else if(TextUtils.equals(action, SLEEP_INTENT)) {
                exit();
            }
        }
    };

    public boolean getIsLive() {
        return mIsLive;
    }

    @BindingAdapter({"player", "length", "time"})
    public static void setPlaybackTime(TextView view, VideoPlayerActivity player, long length, int time) {
        String text;
        if(player.getIsLive()) {
            text = Utils.millisToString(0);
        }
        else {
            text = Utils.millisToString(length);
        }
        view.setText(text);
    }

    @BindingAdapter({"mediamax"})
    public static void setProgressMax(SeekBar view, long length) {
        view.setMax((int) length);
    }

    protected boolean isSeekable() {
        if(mIsLive) {
            return true;
        }
        else {
            return mMediaPlayer.isSeekable();
        }
    }

    private void showLiveContainer(boolean visible) {
        if(mHudBinding != null) {
            int marginRight;
            int nextFocusUpId;

            if(visible) {
                mHudBinding.liveContainer.setVisibility(View.VISIBLE);
                marginRight = getResources().getDimensionPixelSize(R.dimen.time_margin_with_live_button);
                nextFocusUpId = R.id.go_live_button;
            }
            else {
                mHudBinding.liveContainer.setVisibility(View.GONE);
                marginRight = getResources().getDimensionPixelSize(R.dimen.time_margin_sides);
                nextFocusUpId = R.id.player_overlay_seekbar;
            }

            mHudBinding.lockOverlayButton.setNextFocusUpId(nextFocusUpId);
            mHudBinding.selectSubtitles.setNextFocusUpId(nextFocusUpId);
            mHudBinding.selectAudioTrack.setNextFocusUpId(nextFocusUpId);
            mHudBinding.playlistPrevious.setNextFocusUpId(nextFocusUpId);
            mHudBinding.playerOverlayPlay.setNextFocusUpId(nextFocusUpId);
            mHudBinding.playlistNext.setNextFocusUpId(nextFocusUpId);
            mHudBinding.playerOverlaySize.setNextFocusUpId(nextFocusUpId);

            final RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) mHudBinding.playerOverlayLength.getLayoutParams();
            lp.setMargins(0, 0, marginRight, 0);
            mHudBinding.playerOverlayLength.setLayoutParams(lp);
        }
    }

    private void showStreamSelectorContainer(boolean visible) {
        if(mHudBinding != null) {
            int marginLeft;
            mHudBinding.streamSelectorContainer.setVisibility(visible ? View.VISIBLE : View.GONE);

            if(visible) {
                mHudBinding.streamSelectorContainer.setVisibility(View.VISIBLE);
                marginLeft = getResources().getDimensionPixelSize(R.dimen.time_margin_with_stream_selector);
            }
            else {
                mHudBinding.streamSelectorContainer.setVisibility(View.GONE);
                marginLeft = getResources().getDimensionPixelSize(R.dimen.time_margin_sides);
            }

            final RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) mHudBinding.playerOverlayTime.getLayoutParams();
            lp.setMargins(marginLeft, 0, 0, 0);
            mHudBinding.playerOverlayTime.setLayoutParams(lp);
        }
    }

    private void processEngineStatus(final EngineStatus status) {
        setEngineStatus(status);

        // livepos
        mLastLivePos = status.livePos;
        if (status.livePos == null) {
            showLiveContainer(false);
        } else if(!TextUtils.equals(status.outputFormat, "http")) {
            // We can seek only for http output format
            showLiveContainer(false);
        } else {
            showLiveContainer(true);

            if (status.livePos.first == -1) {
                //pass
            } else if (status.livePos.last == -1) {
                //pass
            } else if (status.livePos.pos == -1) {
                //pass
            } else if (status.livePos.lastTimestamp == -1) {
                //pass
            } else if (status.livePos.firstTimestamp == -1) {
                //pass
            } else {
                int duration = status.livePos.lastTimestamp - status.livePos.firstTimestamp;
                int pieces = status.livePos.last - status.livePos.first;
                int offset = status.livePos.pos - status.livePos.first;

                long posAge = new Date().getTime() - freezeLivePosAt;
                boolean isUserSeeking = mDragging || (mHudBinding != null && mHudBinding.playerOverlaySeekbar.hasFocus());
                if (!isUserSeeking && (posAge > FREEZE_LIVE_POS_FOR)) {
                    mMediaLength.set(pieces);
                    mProgress.set(offset);

                    if(mHudBinding != null) {
                        // Fix strange binding behavior: sometimes after media length and progress
                        // change seek bar uses old progress value.
                        // To fix this need to emit additional onPropertyChanged event.
                        if(mProgress.get() != mHudBinding.playerOverlaySeekbar.getProgress()) {
                            mProgress.set(offset-1);
                            mProgress.set(offset);
                        }
                    }

                    mCurrentTime.set(-duration * 1000);
                }

                if(mHudBinding != null) {
                    long statusAge = new Date().getTime() - freezeLiveStatusAt;
                    if (statusAge > FREEZE_LIVE_STATUS_FOR) {
                        if(status.livePos.isLive) {
                            Utils.setBackgroundWithPadding(mHudBinding.goLiveButton, R.drawable.button_live_blue);
                            mHudBinding.goLiveButton.setTextColor(getResources().getColor(R.color.live_status_yes));
                        }
                        else {
                            Utils.setBackgroundWithPadding(mHudBinding.goLiveButton, R.drawable.button_live_yellow);
                            mHudBinding.goLiveButton.setTextColor(getResources().getColor(R.color.live_status_no));
                        }
                    }
                }
            }
        }

        // Stream selector
        if(mHudBinding != null) {
            // list of streams
            // show only for remote device, our player and http output
            if (status.streams.size() > 0 && TextUtils.equals(status.outputFormat, "http")) {
                if (status.currentStreamIndex < 0 || status.currentStreamIndex >= status.streams.size()) {
                    Log.w(TAG, "processEngineStatus: bad remote stream index: index=" + status.currentStreamIndex + " streams=" + status.streams.size());
                    showStreamSelectorContainer(false);
                } else {
                    showStreamSelectorContainer(true);

                    String streamName;
                    streamName = status.streams.get(status.currentStreamIndex).getName();

                    mHudBinding.selectStreamButton.setText(streamName);

                    if (streamName.length() > 6) {
                        mHudBinding.selectStreamButton.setTextSize(8);
                    } else {
                        mHudBinding.selectStreamButton.setTextSize(12);
                    }
                }
            } else {
                showStreamSelectorContainer(false);
            }
        }

        // Debug info
        if(AceStreamEngineBaseApplication.showDebugInfo()) {
            StringBuilder sb = new StringBuilder(100);
            sb.append("status: ").append(status.status);
            sb.append("\npeers: ").append(status.peers);
            sb.append("\ndl: ").append(status.speedDown);
            sb.append("\nul: ").append(status.speedUp);
            sb.append("\nlive: ").append(status.isLive);
            sb.append("\nof: ").append(status.outputFormat);

            SystemUsageInfo si = status.systemInfo;
            if(si == null) {
                si = MiscUtils.getSystemUsage(this);
            }

            if(si != null) {
                long p = -1;
                if(si.memoryTotal != 0)
                    p = Math.round(si.memoryAvailable / si.memoryTotal * 100);
                sb.append("\nram: ").append(p).append("%");
            }

            mDebugInfo.setText(sb.toString());
        }
    }

    public void goLive() {
        Log.d(TAG, "goLive");

        boolean isLive = true;
        if(mLastLivePos != null) {
            isLive = mLastLivePos.isLive;
        }

        if(!isLive) {
            if(mPlaybackManager != null) {
                mPlaybackManager.liveSeek(-1);
            }
            if(mHudBinding != null) {
                Utils.setBackgroundWithPadding(mHudBinding.goLiveButton, R.drawable.button_live_blue);
                mHudBinding.goLiveButton.setTextColor(getResources().getColor(R.color.live_status_yes));
            }

            mProgress.set((int)mMediaLength.get());

            // to freeze status and pos for some time
            freezeLiveStatusAt = new Date().getTime();
            freezeLivePosAt = new Date().getTime();
        }
    }

    public void selectStream() {
        Log.d(TAG, "selectStream");
        Playlist playlist = null;

        if(mPlaybackManager != null) {
            playlist = mPlaybackManager.getCurrentPlaylist();
        }

        if(playlist == null) {
            Log.d(TAG, "click:select_stream: no playlist");
            return;
        }

        final List<ContentStream> originalStreams = playlist.getStreams();

        // filter audio streams because of bug in engine (cannot select audio stream)
        final List<ContentStream> streams = new ArrayList<>();
        for(ContentStream stream: originalStreams) {
            if(!stream.getName().startsWith("Audio")) {
                streams.add(stream);
            }
        }

        if(streams.size() == 0) {
            Log.d(TAG, "click:select_stream: no streams");
            return;
        }

        String[] entries = new String[streams.size()];
        for(int i = 0; i < streams.size(); i++) {
            entries[i] = streams.get(i).getName();
        }
        int selectedId = playlist.getCurrentStreamIndex();

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        //TODO: translate
        builder.setTitle("Select stream");
        builder.setSingleChoiceItems(entries, selectedId, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                switchStream(i, streams.get(i).streamType);
                dialogInterface.dismiss();
            }
        });

        Dialog dialog = builder.create();
        dialog.show();
    }

    private void switchStream(int streamIndex, int streamType) {
        Log.d(TAG, "switchStream: type=" + streamType + " index=" + streamIndex);

        if(mPlaybackManager == null) {
            Log.e(TAG, "switchStream: missing playback manager");
            return;
        }

        if(streamType == ContentStream.StreamType.HLS) {
            mPlaybackManager.setHlsStream(streamIndex);
        }
        else if(streamType == ContentStream.StreamType.DIRECT) {
            Playlist playlist = mPlaybackManager.getCurrentPlaylist();
            if (playlist == null) {
                Log.d(TAG, "switchStream: missing current playlist");
                return;
            }

            Log.d(TAG, "switchStream: current=" + playlist.getCurrentStreamIndex() + " new=" + streamIndex);
            playlist.setCurrentStreamIndex(streamIndex);

            //TODO: copy code from PlaylistManager
        }
        else {
            Log.e(TAG, "switchStream: unknown stream type: index=" + streamIndex + " type=" + streamType);
        }
    }

    @Override
    public void onRestartPlayer() {
        if(mMediaPlayer == null) {
            Log.d(TAG, "onRestartPlayer: no media player");
            return;
        }

        Log.d(TAG, "onRestartPlayer");
        mRestartingPlayer = true;
        mMediaPlayer.stop();

        mHandler.postDelayed(mEnsurePlayerIsPlayingTask, 5000);
    }

    private void setCurrentRemoteClient(String clientId, String deviceId) {
        App.v(TAG, "setCurrentRemoteClient: current=" + mRemoteClientId + " new=" + clientId);
        if(TextUtils.equals(clientId, mRemoteClientId)) {
            return;
        }

        if(mRemoteClientId != null) {
            // remove listener from prev client
            mDiscoveryServerServiceClient.removeClientListener(mRemoteClientId, mDSSClientCallback);
        }

        mRemoteClientId = clientId;

        if(mPlaybackManager != null) {
            mPlaybackManager.setCurrentRemoteClient(clientId, deviceId);
        }

        initRemoteClient(deviceId);
    }

    private void initRemoteClient(String deviceId) {
        Log.v(TAG, "initRemoteClient: clientId=" + mRemoteClientId + " deviceId=" + deviceId);
        if(mRemoteClientId != null) {
            mLastRemoteClientDeviceId = deviceId;
            mDiscoveryServerServiceClient.addClientListener(mRemoteClientId, mDSSClientCallback);
        }

        if(mPlaybackManager != null) {
            mPlaybackManager.setCurrentRemoteClient(mRemoteClientId, deviceId);
        }
    }

    private void onServerClientDisconnected(String clientId, String deviceId) {
        Log.d(TAG, "remote control disconnected: id=" + clientId + " device_id=" + deviceId + " current=" + mRemoteClientId);
        if(TextUtils.equals(clientId, mRemoteClientId)) {
            setCurrentRemoteClient(null, null);
        }
    }

    private void sendRemoteMessage(JsonRpcMessage msg) {
        if(mRemoteClientId != null) {
            mDiscoveryServerServiceClient.sendClientMessage(mRemoteClientId, msg);
        }
    }

    public void setEngineStatus(EngineStatus status) {
        mLastEngineStatus = status;
        updatePlaybackStatus();
    }

    protected void setPlaying(boolean playing) {
        mIsPlaying = playing;
        updatePlaybackStatus();
    }

    protected void setBuffering(boolean buffering) {
        mIsBuffering = buffering;
        updatePlaybackStatus();
    }

    protected void updatePlaybackStatus() {
        boolean showOverlay;
        boolean showProgress;
        boolean p2p = isCurrentMediaP2P();
        String message = null;

        if(p2p && mLastEngineStatus != null) {
            switch(mLastEngineStatus.status) {
                case "engine_unpacking":
                    message = getResources().getString(R.string.dialog_unpack);
                    break;
                case "engine_starting":
                    message = getResources().getString(R.string.dialog_start);
                    break;
                case "engine_failed":
                    message = getResources().getString(R.string.start_fail);
                    break;
                case "loading":
                    message = getResources().getString(R.string.loading);
                    break;
                case "starting":
                    message = getResources().getString(R.string.starting);
                    break;
                case "checking":
                    message = getResources().getString(R.string.status_checking_short, mLastEngineStatus.progress);
                    break;
                case "prebuf":
                    message = getResources().getString(R.string.status_prebuffering, mLastEngineStatus.progress, mLastEngineStatus.peers, mLastEngineStatus.speedDown);
                    break;
                case "error":
                    message = mLastEngineStatus.errorMessage;
                    break;
                case "dl":
                    break;
            }
        }

        if(!p2p) {
            showOverlay = false;
            showProgress = false;
        }
        else if(TextUtils.isEmpty(message)) {
            showOverlay = !mIsPlaying;
            showProgress = !mIsPlaying || mIsBuffering;
        }
        else {
            showOverlay = true;
            showProgress = false;
        }

        if(DEBUG_LOG_ENGINE_STATUS) {
            Log.v(TAG, "show_status: engine=" + (mLastEngineStatus == null ? null : mLastEngineStatus.status) + " playing=" + mIsPlaying + " buffering=" + mIsBuffering + " overlay=" + showOverlay + " progress=" + showProgress + " msg=" + message);
        }

        showStatusOverlay(showOverlay, message);

        if(showProgress)
            startLoading();
        else
            stopLoading();
    }

    protected void showStatusOverlay(final boolean visible, final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mEngineStatus.setVisibility(visible ? View.VISIBLE : View.GONE);
                if(message == null) {
                    mEngineStatus.setText("");
                }
                else {
                    mEngineStatus.setText(message);
                }
            }
        });
    }

    private void onContentPaused() {
        boolean showAdsOnPause = true;
        boolean preloadVastAds = false;

        if(!shouldShowAds()) {
            return;
        }

        if(hasNoAds()) {
            showAdsOnPause = AceStreamEngineBaseApplication.showAdsOnPause();
        }

        App.v(TAG, "onContentPaused: showAds=" + showAdsOnPause);

        if(mAdsWaterfall != null) {
            mAdsWaterfall.setPlacement(AdsWaterfall.Placement.PAUSE, true);
            preloadVastAds = mAdsWaterfall.has(
                    AdsWaterfall.Placement.UNPAUSE,
                    AdsWaterfall.Inventory.VAST);
        }

        if(!showAdsOnPause) {
            return;
        }

        requestNextAds();

        if(preloadVastAds && mEngineService != null) {
            mAdsWaterfall.resetInventoryStatus(AdsWaterfall.Inventory.VAST);
            String infohash = null;
            int isLive = -1;

            if(mPlaybackManager != null) {
                EngineSession session = mPlaybackManager.getEngineSession();
                if(session != null) {
                    infohash = session.infohash;
                    isLive = session.isLive;
                }
            }

            mEngineService.requestAds(
                    "unpause",
                    infohash,
                    isLive,
                    AceStreamEngineBaseApplication.showAdsOnPause(),
                    null,
                    new Callback<RequestAdsResponse>() {
                        @Override
                        public void onSuccess(RequestAdsResponse result) {
                            if(result.vast_tags == null) {
                                Log.v(TAG, "ads:vast_midroll: missing tags");
                                return;
                            }

                            Log.v(TAG, "ads:vast_midroll: got tags: count=" + result.vast_tags.length);
                            mMidrollAdsRequested = true;
                            initVastAds(result.vast_tags);
                            // Ads will be preloaded but not started
                            requestVastAds();
                        }

                        @Override
                        public void onError(String err) {
                            Log.e(TAG, "ads:vast_midroll: error: " + err);
                        }
                    }
            );
        }
    }

    /**
     * Called when content is unpaused
     *
     * @return boolean True when ads will be shown immedialtelly
     */
    private boolean onContentUnpaused() {
        App.v(TAG, "onContentUnpaused");

        if(!shouldShowAds()) {
            return false;
        }

        if (mAdsWaterfall != null) {
            mAdsWaterfall.setPlacement(AdsWaterfall.Placement.UNPAUSE);
            return requestNextAds();
        }

        return false;
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

    protected void initAds() {
        if(mAdsWaterfall != null) {
            App.vv(TAG, "initAds: already initialized");
            return;
        }

        if(!shouldShowAds()) {
            App.vv(TAG, "initAds: skip");
            return;
        }

        // This must be called after PM is connected.
        if(mPlaybackManager == null) {
            Log.w(TAG, "initAds: missing pm");
            return;
        }

        final AdConfig adConfig = mPlaybackManager.getAdConfig();
        if(adConfig == null) {
            Log.w(TAG, "initAds: missing config");
            return;
        }

        mAdSettings.initFromConfig(adConfig);
        mAdsWaterfall = new AdsWaterfall(
                adConfig,
                mHandler,
                this);

        mAdsWaterfall.setPlacement(AdsWaterfall.Placement.PREROLL);

        if(!AceStreamEngineBaseApplication.shouldShowAdMobAds()) {
            App.v(TAG, "initAds: non-vast ads are disabled for this device");
            return;
        }

        Log.v(TAG, "initAds: auth_level=" + mPlaybackManager.getAuthLevel());

        if(adConfig.isProviderEnabled(AdManager.ADS_PROVIDER_ADMOB)) {
            initInterstitialAd();
            initRewardedVideo();
        }

        if(adConfig.isProviderEnabled(AdManager.ADS_PROVIDER_APPODEAL)) {
            initAppodeal();
        }
    }

    private void initInterstitialAd() {
        if(mAdManager == null) {
            throw new IllegalStateException("missing ad manager");
        }

        if(mAdsWaterfall == null) {
            throw new IllegalStateException("missing waterfall");
        }

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
            mAdManager.initInterstitial(
                    "preroll",
                    useTestAds
                        ? ADMOB_TEST_INTERSTITIAL
                        : AceStreamEngineBaseApplication.getStringAppMetadata("adMobInterstitialPrerollId"),
                    new AdListener() {
                @Override
                public void onAdLoaded() {
                    // Code to be executed when an ad finishes loading.
                    App.v(TAG, "ads:event:interstitial:preroll:onAdLoaded");
                    mAdsWaterfall.onLoaded(AdsWaterfall.Inventory.ADMOB_INTERSTITIAL_PREROLL);
                }

                @Override
                public void onAdFailedToLoad(int errorCode) {
                    // Code to be executed when an ad request fails.
                    App.v(TAG, "ads:event:interstitial:preroll:onAdFailedToLoad: errorCode=" + errorCode);
                    mAdsWaterfall.onFailed(AdsWaterfall.Inventory.ADMOB_INTERSTITIAL_PREROLL);
                }

                @Override
                public void onAdOpened() {
                    // Code to be executed when the ad is displayed.
                    App.v(TAG, "ads:event:interstitial:preroll:onAdOpened");
                    notifyAdsLoaded();
                    onContentPauseRequested(AdSource.INTERSTITIAL_AD);
                    addCoins("interstitial:player:preroll", 0, true);
                    AceStreamEngineBaseApplication.getInstance().logAdImpression(
                            AdManager.ADS_PROVIDER_ADMOB,
                            AdsWaterfall.Placement.PREROLL,
                            AdsWaterfall.AdType.INTERSTITIAL);
                }

                @Override
                public void onAdLeftApplication() {
                    // Code to be executed when the user has left the app.
                    App.v(TAG, "ads:event:interstitial:preroll:onAdLeftApplication");
                }

                @Override
                public void onAdClosed() {
                    // Code to be executed when when the interstitial ad is closed.
                    App.v(TAG, "ads:event:interstitial:preroll:onAdClosed: finishing=" + isFinishing());

                    if (!isFinishing()) {
                        onContentResumeRequested(AdSource.INTERSTITIAL_AD);
                    }
                }
            });
        }

        if(loadPause) {
            mAdManager.initInterstitial(
                    "pause",
                    BuildConfig.admobUseTestAds
                        ? ADMOB_TEST_INTERSTITIAL
                        : AceStreamEngineBaseApplication.getStringAppMetadata("adMobInterstitialPauseId"),
                    new AdListener() {
                        @Override
                        public void onAdLoaded() {
                            // Code to be executed when an ad finishes loading.
                            App.v(TAG, "ads:event:interstitial:pause:onAdLoaded");
                            mAdsWaterfall.onLoaded(AdsWaterfall.Inventory.ADMOB_INTERSTITIAL_PAUSE);
                        }

                        @Override
                        public void onAdFailedToLoad(int errorCode) {
                            // Code to be executed when an ad request fails.
                            App.v(TAG, "ads:event:interstitial:pause:onAdFailedToLoad: errorCode=" + errorCode);
                            mAdsWaterfall.onFailed(AdsWaterfall.Inventory.ADMOB_INTERSTITIAL_PAUSE);
                        }

                        @Override
                        public void onAdOpened() {
                            // Code to be executed when the ad is displayed.
                            App.v(TAG, "ads:event:interstitial:pause:onAdOpened");
                            onContentPauseRequested(AdSource.INTERSTITIAL_AD);
                            addCoins("interstitial:player:pause", 0, true);
                            AceStreamEngineBaseApplication.getInstance().logAdImpression(
                                    AdManager.ADS_PROVIDER_ADMOB,
                                    AdsWaterfall.Placement.PAUSE,
                                    AdsWaterfall.AdType.INTERSTITIAL);
                        }

                        @Override
                        public void onAdLeftApplication() {
                            // Code to be executed when the user has left the app.
                            App.v(TAG, "ads:event:interstitial:pause:onAdLeftApplication");
                        }

                        @Override
                        public void onAdClosed() {
                            // Code to be executed when when the interstitial ad is closed.
                            App.v(TAG, "ads:event:interstitial:pause:onAdClosed: finishing=" + isFinishing());

                            if (!isFinishing()) {
                                onContentResumeRequested(AdSource.INTERSTITIAL_AD);
                                // Load next ad
                                loadInterstitialAdPause();
                            }
                        }
                    });
        }

        if(loadClose) {
            mAdManager.initInterstitial(
                    "close",
                    BuildConfig.admobUseTestAds
                        ? ADMOB_TEST_INTERSTITIAL
                        : AceStreamEngineBaseApplication.getStringAppMetadata("adMobInterstitialCloseId"),
                    new AdListener() {
                        @Override
                        public void onAdLoaded() {
                            // Code to be executed when an ad finishes loading.
                            App.v(TAG, "ads:event:interstitial:close:onAdLoaded");
                            mAdsWaterfall.onLoaded(AdsWaterfall.Inventory.ADMOB_INTERSTITIAL_CLOSE);
                        }

                        @Override
                        public void onAdFailedToLoad(int errorCode) {
                            // Code to be executed when an ad request fails.
                            App.v(TAG, "ads:event:interstitial:close:onAdFailedToLoad: errorCode=" + errorCode);
                            mAdsWaterfall.onFailed(AdsWaterfall.Inventory.ADMOB_INTERSTITIAL_CLOSE);
                        }

                        @Override
                        public void onAdOpened() {
                            // Code to be executed when the ad is displayed.
                            App.v(TAG, "ads:event:interstitial:close:onAdOpened");
                            notifyAdsLoaded();
                            addCoins("interstitial:player:close", 0, true);
                            AceStreamEngineBaseApplication.getInstance().logAdImpression(
                                    AdManager.ADS_PROVIDER_ADMOB,
                                    AdsWaterfall.Placement.CLOSE,
                                    AdsWaterfall.AdType.INTERSTITIAL);
                        }

                        @Override
                        public void onAdLeftApplication() {
                            // Code to be executed when the user has left the app.
                            App.v(TAG, "ads:event:interstitial:close:onAdLeftApplication");
                        }

                        @Override
                        public void onAdClosed() {
                            // Code to be executed when when the interstitial ad is closed.
                            App.v(TAG, "ads:event:interstitial:close:onAdClosed");
                            onExitAdClosed();
                        }
                    });
        }

        if(loadPreroll && mAdsWaterfall.has(
                AdsWaterfall.Placement.PREROLL,
                AdsWaterfall.Inventory.ADMOB_INTERSTITIAL_PREROLL)) {
            loadInterstitialAdPreroll();
        }

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if(loadPause && mAdsWaterfall.has(AdsWaterfall.Placement.PAUSE, AdsWaterfall.Inventory.ADMOB_INTERSTITIAL_PAUSE)) {
                    loadInterstitialAdPause();
                }

                if(loadClose && mAdsWaterfall.has(AdsWaterfall.Placement.CLOSE, AdsWaterfall.Inventory.ADMOB_INTERSTITIAL_CLOSE)) {
                    loadInterstitialAdClose();
                }
            }
        }, 2000);
    }

    private void loadInterstitialAdPreroll() {
        if(mAdManager == null) {
            throw new IllegalStateException("missing ad manager");
        }

        App.v(TAG, "loadInterstitialAdPreroll");
        if(mAdManager.loadInterstitial("preroll")) {
            mAdsWaterfall.onLoading(AdsWaterfall.Inventory.ADMOB_INTERSTITIAL_PREROLL);
        }
    }

    private void loadInterstitialAdPause() {
        if(mAdManager == null) {
            throw new IllegalStateException("missing ad manager");
        }

        if(mAdManager.loadInterstitial("pause")) {
            mAdsWaterfall.onLoading(AdsWaterfall.Inventory.ADMOB_INTERSTITIAL_PAUSE);
        }
    }

    private void loadInterstitialAdClose() {
        if(mAdManager == null) {
            throw new IllegalStateException("missing ad manager");
        }

        if(mAdManager.loadInterstitial("close")) {
            mAdsWaterfall.onLoading(AdsWaterfall.Inventory.ADMOB_INTERSTITIAL_CLOSE);
        }
    }

    private void initRewardedVideo() {
        if(mAdManager == null) {
            throw new IllegalStateException("missing ad manager");
        }

        if(!isUserLoggedIn()) {
            return;
        }

        if(hasNoAds() && !AceStreamEngineBaseApplication.showAdsOnPreroll()) {
            return;
        }

        // Use an activity context to get the rewarded video instance.
        mAdManager.initRewardedVideo(mAdManager.getAutoAdSegment(), new RewardedVideoAdListener() {
            @Override
            public void onRewardedVideoAdLoaded() {
                App.v(TAG, "ads:event:rv:preroll:onRewardedVideoAdLoaded");
                mAdsWaterfall.onLoaded(AdsWaterfall.Inventory.ADMOB_REWARDED_VIDEO);
            }

            @Override
            public void onRewardedVideoAdOpened() {
                App.v(TAG, "ads:event:rv:preroll:onRewardedVideoAdOpened");
                notifyAdsLoaded();
                onContentPauseRequested(AdSource.REWARDED_VIDEO);
            }

            @Override
            public void onRewardedVideoStarted() {
                Log.v(TAG, "ads:event:rv:preroll:onRewardedVideoStarted");
            }

            @Override
            public void onRewardedVideoAdClosed() {
                App.v(TAG, "ads:event:rv:preroll:onRewardedVideoAdClosed: finishing=" + isFinishing());
                if(!isFinishing()) {
                    onContentResumeRequested(AdSource.REWARDED_VIDEO);
                }
            }

            @Override
            public void onRewarded(RewardItem reward) {
                App.v(TAG, "ads:event:rv:preroll:onRewarded: currency: " + reward.getType() + "  amount: " + reward.getAmount());

                String source = "rv:player:preroll";
                addCoins(source, 0, false);

                Bundle params = new Bundle();
                params.putString("source", source);
                AceStreamEngineBaseApplication.getInstance().logAdImpression(
                        AdManager.ADS_PROVIDER_ADMOB,
                        AdsWaterfall.Placement.PREROLL,
                        AdsWaterfall.AdType.REWARDED_VIDEO,
                        params);
            }

            @Override
            public void onRewardedVideoAdLeftApplication() {
                App.v(TAG, "ads:event:rv:preroll:onRewardedVideoAdLeftApplication");
            }

            @Override
            public void onRewardedVideoAdFailedToLoad(int i) {
                boolean noads = hasNoAds();
                App.v(TAG, "ads:event:rv:preroll:onRewardedVideoAdFailedToLoad"
                        + " noads=" + noads
                        + " finishing=" + isFinishing()
                );
                mAdsWaterfall.onFailed(AdsWaterfall.Inventory.ADMOB_REWARDED_VIDEO);
            }

            @Override
            public void onRewardedVideoCompleted() {
                App.v(TAG, "ads:event:rv:preroll:onRewardedVideoCompleted");
            }
        });
    }

    private boolean showCustomAds() {
        if(!canShowAds(null, null)) {
            return false;
        }

        onContentPauseRequested(AdSource.CUSTOM_ADS);

        // Show checkbox only for users with NoAds
        if(hasNoAds()) {
            mCheckboxShowRewardedAds.setVisibility(View.VISIBLE);
        }
        else {
            mCheckboxShowRewardedAds.setVisibility(View.GONE);
        }

        mCustomAdsContainer.setVisibility(View.VISIBLE);

        return true;
    }

    private void hideCustomAds(boolean resume) {
        mCustomAdsContainer.setVisibility(View.GONE);
        if(resume) {
            onContentResumeRequested(AdSource.CUSTOM_ADS);
        }
    }

    private void onContentPauseRequested(AdSource source) {
        App.v(TAG, "onContentPauseRequested: source=" + source);
        showAds(source);
        pause();
        hideOverlay(false);
    }

    private void onContentResumeRequested(AdSource source) {
        String placement = mAdsWaterfall == null ? null : mAdsWaterfall.getPlacement();
        App.v(TAG, "onContentResumeRequested: source=" + source + " placement=" + placement);
        boolean doHideAds;

        if(source == AdSource.IMA_SDK) {
            doHideAds = (mAdsManagers.size() <= 1);
        }
        else if(source == AdSource.REWARDED_VIDEO) {
            doHideAds = true;
        }
        else if(source == AdSource.INTERSTITIAL_AD) {
            doHideAds = true;
        }
        else if(source == AdSource.CUSTOM_ADS) {
            doHideAds = true;
        }
        else {
            throw new IllegalStateException("unknown source: " + source);
        }

        if(doHideAds) {
            hideAds(source);

            if (mMute && mMediaPlayer != null) {
                // Restore volume if was muted
                Log.v(TAG, "ads:event: restore volume: " + mVolSave);
                mute(false);
            }

            boolean resume = true;
            if(TextUtils.equals(placement, AdsWaterfall.Placement.PAUSE)) {
                if(mIsStarted && !mIsInBackground) {
                    resume = onContentUnpaused();
                }
                else {
                    mShowUnpauseAdsOnResume = true;
                }
            }

            if(resume) {
                play();
            }
        }
    }

    private void showBonusAds() {
        Log.v(TAG, "showBonusAds");

        if(mCheckboxShowRewardedAds.getVisibility() == View.VISIBLE
            && mCheckboxShowRewardedAds.isChecked()) {
            AceStreamEngineBaseApplication.setShowRewardedAds(true);
        }
        boolean show = mAdsWaterfall.showCustomRewardedVideo();
        hideCustomAds(!show);
    }

    private void skipBonusAds() {
        Log.v(TAG, "skipBonusAds");
        boolean show = false;
        if(!hasNoAds()) {
            show = requestNextAds(true);
        }
        hideCustomAds(!show);
    }

    /**
     * Request ads for current placement
     *
     * @return boolean True if ads will be shown immediately
     */
    private boolean requestNextAds() {
        return requestNextAds(false);
    }

    private boolean requestNextAds(boolean skipFrequencyCapping) {
        if(!shouldShowAds()) {
            return false;
        }

        if(mAdsWaterfall == null) {
            Log.w(TAG, "requestNextAds: missing waterfall");
            return false;
        }

        boolean hasNoads = hasNoAds();
        boolean showAds = true;

        if(hasNoads) {
            if(TextUtils.equals(mAdsWaterfall.getPlacement(), "preroll")) {
                if(!AceStreamEngineBaseApplication.showAdsOnPreroll()) {
                    App.v(TAG, "requestNextAds: skip preroll");
                    showAds = false;
                }
            }
            else if(TextUtils.equals(mAdsWaterfall.getPlacement(), "pause")
                    || TextUtils.equals(mAdsWaterfall.getPlacement(), "unpause")) {
                if(!AceStreamEngineBaseApplication.showAdsOnPause()) {
                    App.v(TAG, "requestNextAds: skip pause");
                    showAds = false;
                }
            }
            else if(TextUtils.equals(mAdsWaterfall.getPlacement(), "close")) {
                if(!AceStreamEngineBaseApplication.showAdsOnClose()) {
                    App.v(TAG, "requestNextAds: skip close");
                    showAds = false;
                }
            }
        }

        Bundle params = new Bundle();
        try {
            if(!showAds) {
                params.putBoolean("show_ads", false);
                params.putString("skip_reason", "noads");
                return false;
            }

            params.putBoolean("show_ads", true);
            return mAdsWaterfall.showNext(skipFrequencyCapping);
        }
        catch(AdsWaterfall.FrequencyCapError e) {
            params.putBoolean("show_ads", false);
            params.putString("skip_reason", "frequency_cap");
            return false;
        }
        finally {
            params.putBoolean("has_noads", hasNoads);
            AceStreamEngineBaseApplication.getInstance().logAdRequest(
                    mAdsWaterfall.getPlacement(),
                    params);
        }
    }

    private void notifyAdsLoaded() {
        if(mAdsWaterfall == null) {
            throw new IllegalStateException("missing waterfall");
        }

        mAdsWaterfall.done();
    }

    // Ad Player
    protected IVLCVout.Callback mAdPlayerVlcOutCallback = new IVLCVout.Callback() {
        @Override
        public void onSurfacesCreated(IVLCVout ivlcVout) {
        }

        @Override
        public void onSurfacesDestroyed(IVLCVout ivlcVout) {
        }
    };

    protected IVLCVout.OnNewVideoLayoutListener mAdPlayerOnNewVideoLayoutListener = new IVLCVout.OnNewVideoLayoutListener() {
        @Override
        public void onNewVideoLayout(IVLCVout vlcVout, int width, int height, int visibleWidth, int visibleHeight, int sarNum, int sarDen) {
            mAdPlayerVideoWidth = width;
            mAdPlayerVideoHeight = height;
            mAdPlayerVideoVisibleWidth  = visibleWidth;
            mAdPlayerVideoVisibleHeight = visibleHeight;
            mAdPlayerSarNum = sarNum;
            mAdPlayerSarDen = sarDen;
            changeAdPlayerSurfaceLayout();
        }
    };

    private void initImaSdkVideoAdPlayer() {
        if(mVideoAdPlayer != null) {
            return;
        }

        mVideoAdPlayer = new VideoAdPlayer() {
            private int mCurrentAdPosition = -1;
            private String mAdUri;

            private Runnable doPlay = new Runnable() {
                @Override
                public void run() {
                    App.v(TAG, "ads:ima_ad_player:doPlay: uri=" + mAdUri);
                    if(mAdUri != null && mAdPlayer != null) {
                        mAdPlayer.play();
                        notifyImaSdkPlay();
                    }
                }
            };

            private Runnable doLoad = new Runnable() {
                @Override
                public void run() {
                    String clickThroughUrl = null;

                    int adPosition = -1;
                    Ad currentAd = getCurrentAd();
                    if(currentAd != null) {
                        if(currentAd.getAdPodInfo() != null) {
                            adPosition = currentAd.getAdPodInfo().getAdPosition();
                            VastTag currentTag = getCurrentVastTag();
                            if(currentTag != null) {
                                if(currentTag.clickThroughUrls != null) {
                                    for(ClickThroughUrl url: currentTag.clickThroughUrls) {
                                        // Ad position starts from 1, but index starts from 0
                                        if(url.index == adPosition-1) {
                                            clickThroughUrl = url.url;
                                            break;
                                        }
                                    }
                                }
                            }

                            if(App.verbose()) {
                                if(clickThroughUrl == null) {
                                    App.v(TAG, "ads:ima_ad_player:loadAd: clickthrough not found: pos=" + adPosition);
                                }
                                else {
                                    App.v(TAG, "ads:ima_ad_player:loadAd: clickthrough found: pos=" + adPosition + " url=" + clickThroughUrl);
                                }
                            }
                        }
                    }

                    App.v(TAG, "ads:ima_ad_player:doLoad: pos=" + adPosition + " uri=" + mAdUri);
                    startAdPlayer(
                            Uri.parse(mAdUri),
                            clickThroughUrl
                    );
                    notifyImaSdkLoaded();
                }
            };

            @Override
            public void playAd() {
                App.v(TAG, "ads:ima_ad_player:playAd");
                mHandler.removeCallbacks(doPlay);
                mHandler.postDelayed(doPlay, mAdSettings.imaSdkHandlerDelay);
            }

            @Override
            public void loadAd(String uri) {
                App.v(TAG, "ads:ima_ad_player:loadAd: uri=" + uri);

                mAdUri = uri;
                mHandler.removeCallbacks(doLoad);
                mHandler.postDelayed(doLoad, mAdSettings.imaSdkHandlerDelay);
            }

            @Override
            public void stopAd() {
                App.v(TAG, "ads:ima_ad_player:stopAd");
                if(mAdPlayer != null) {
                    mAdPlayer.stop();
                    notifyImaSdkEnded();
                }
            }

            @Override
            public void pauseAd() {
                App.v(TAG, "ads:ima_ad_player:pauseAd");
                if(mAdPlayer != null) {
                    mAdPlayer.pause();
                    notifyImaSdkPause();
                }
            }

            @Override
            public void resumeAd() {
                App.v(TAG, "ads:ima_ad_player:resumeAd");
                if(mAdPlayer != null) {
                    mAdPlayer.play();
                    notifyImaSdkPlay();
                }
            }

            @Override
            public void addCallback(VideoAdPlayerCallback callback) {
                App.v(TAG, "ads:ima_ad_player:addCallback: callback=" + callback);
                if(!mImaSdkAdCallbacks.contains(callback)) {
                    mImaSdkAdCallbacks.add(callback);
                }
            }

            @Override
            public void removeCallback(VideoAdPlayerCallback callback) {
                App.v(TAG, "ads:ima_ad_player:removeCallback: callback=" + callback);
                mImaSdkAdCallbacks.remove(callback);
            }

            @Override
            public VideoProgressUpdate getAdProgress() {
                if (mAdPlayer == null || mAdPlayer.getLength() <= 0) {
                    return VideoProgressUpdate.VIDEO_TIME_NOT_READY;
                }
                long time = mAdPlayer.getTime();
                long duration = mAdPlayer.getLength();
                long timeLeft = duration - time;

                Ad currentAd = getCurrentAd();
                int adPosition = 0;
                int totalAds = 0;
                boolean skippable = false;
                double skipTimeOffset = 0.0;
                if(currentAd != null) {
                    skippable = currentAd.isSkippable();
                    skipTimeOffset = currentAd.getSkipTimeOffset();
                    if(currentAd.getAdPodInfo() != null) {
                        adPosition = currentAd.getAdPodInfo().getAdPosition();
                        totalAds = currentAd.getAdPodInfo().getTotalAds();
                    }
                }
                double secondsLeftToSkip = skipTimeOffset - time / 1000.0;

                String text = Utils.millisToString(timeLeft);
                if(totalAds > 1) {
                    text = "Ad " + adPosition + " of " + totalAds + ": " + text;
                }
                mAdPlayerTimeLeft.setText(text);
                mAdPlayerTimeLeft.setVisibility(View.VISIBLE);

                if(skippable) {
                    if (secondsLeftToSkip > 0) {
                        String s = getResources().getString(R.string.you_can_skip_this_ad_in, (long)Math.ceil(secondsLeftToSkip));
                        mAdPlayerSkipText.setText(s);
                        mAdPlayerSkipText.setVisibility(View.VISIBLE);
                        mAdPlayerSkipContainer.setVisibility(View.GONE);
                    }
                    else {
                        mAdPlayerSkipText.setText("");
                        mAdPlayerSkipText.setVisibility(View.GONE);

                        if(mAdPlayerSkipContainer.getVisibility() != View.VISIBLE) {
                            mAdPlayerSkipContainer.setVisibility(View.VISIBLE);
                            if (AceStreamEngineBaseApplication.showTvUi()) {
                                mAdPlayerSkipContainer.requestFocus();
                            }
                        }
                    }
                }

                return new VideoProgressUpdate(time, duration);
            }

            @Override
            public int getVolume() {
                return mAdPlayer == null ? 0 : mAdPlayer.getVolume();
            }
        };
    }

    protected void initAdPlayer() {
        initImaSdkVideoAdPlayer();

        if(mAdPlayer != null) {
            App.v(TAG, "ads:player:initAdPlayer: already initialized");
            return;
        }

        App.v(TAG, "ads:player: init");

        if(mAdPlayerSurfaceView == null) {
            App.v(TAG, "ads:player: init: no surface");
            return;
        }

        mAdPlayer = new MediaPlayer(getLibVlc());
        mAdPlayer.setEventListener(new AdPlayerEventListener());

        // explicitly set audio device
        mAdPlayer.setAudioOutput("android_audiotrack");
        mAdPlayer.setAudioOutputDevice("pcm");

        attachAdPlayerViews();
    }

    protected void startAdPlayer(Uri uri, String clickThroughUrl) {
        if(mAdPlayer == null) {
            return;
        }

        App.v(TAG, "ads:player:start:"
                + " uri=" + uri
                + " clickThroughUrl=" + clickThroughUrl
        );

        // Hide initially. Will be shown on progress update.
        mAdPlayerTimeLeft.setVisibility(View.GONE);
        mAdPlayerSkipContainer.setVisibility(View.GONE);
        mAdPlayerSkipText.setVisibility(View.GONE);

        mAdPlayerUiContainer.setVisibility(View.VISIBLE);
        mAdPlayerContainer.setVisibility(View.VISIBLE);
        mAdPlayerSurfaceView.setVisibility(View.VISIBLE);

        // hide player UI
        mPlayerUiContainer.setVisibility(View.GONE);

        if(clickThroughUrl != null) {
            mAdPlayerButtonClick.setTag(clickThroughUrl);
            mAdPlayerButtonClick.setVisibility(View.VISIBLE);
        }
        else {
            mAdPlayerButtonClick.setTag(null);
            mAdPlayerButtonClick.setVisibility(View.GONE);
        }

        Media media = new Media(getLibVlc(), uri);
        mAdPlayer.setMedia(media);
        mAdPlayer.setVolume(100);
        media.release();
    }

    private void hideAdPlayer() {
        App.v(TAG, "ads:hideAdPlayer");

        disableAdPlayerTimeout();

        mAdPlayerContainer.setVisibility(View.GONE);
        mAdPlayerUiContainer.setVisibility(View.GONE);
        mAdPlayerSurfaceView.setVisibility(View.GONE);

        // show player UI
        mPlayerUiContainer.setVisibility(View.VISIBLE);

        if(mAdPlayer != null) {
            IVLCVout vout = mAdPlayer.getVLCVout();
            vout.detachViews();
            mAdPlayer.stop();
            mAdPlayer.release();
            mAdPlayer = null;
        }

        mVideoAdPlayer = null;
    }

    private Runnable mAdPlayerTimeoutTask = new Runnable() {
        @Override
        public void run() {
            Logger.v(TAG, "ads:player: got playback timeout");
            notifyImaSdkError();
        }
    };

    private void touchAdPlayerTimeout() {
        mHandler.removeCallbacks(mAdPlayerTimeoutTask);
        mHandler.postDelayed(mAdPlayerTimeoutTask, AD_PLAYER_PLAYBACK_TIMEOUT);
    }

    private void disableAdPlayerTimeout() {
        mHandler.removeCallbacks(mAdPlayerTimeoutTask);
    }

    private class AdPlayerEventListener implements MediaPlayer.EventListener {
        @Override
        public void onEvent(MediaPlayer.Event event) {
            switch(event.type) {
                case MediaPlayer.Event.Playing:
                    if(mAdPlayer != null) {
                        App.v(TAG, "ads:player:event:Playing: volume=" + mAdPlayer.getVolume());
                        mAdPlayer.setVolume(100);
                    }
                    break;
                case MediaPlayer.Event.EndReached:
                    App.v(TAG, "ads:player:event:EndReached");
                    disableAdPlayerTimeout();
                    notifyImaSdkEnded();
                    break;
                case MediaPlayer.Event.Paused:
                    App.v(TAG, "ads:player:event:Paused");
                    disableAdPlayerTimeout();
                    break;
                case MediaPlayer.Event.Stopped:
                    App.v(TAG, "ads:player:event:Stopped");
                    disableAdPlayerTimeout();
                    break;
                case MediaPlayer.Event.EncounteredError:
                    App.v(TAG, "ads:player:event:EncounteredError");
                    disableAdPlayerTimeout();
                    notifyImaSdkError();
                    break;
                case MediaPlayer.Event.Opening:
                    App.v(TAG, "ads:player:event:Opening");
                    break;
                case MediaPlayer.Event.MediaChanged:
                    break;
                case MediaPlayer.Event.Buffering:
                    break;
                case MediaPlayer.Event.TimeChanged:
                    touchAdPlayerTimeout();
                    break;
                default:
                    break;
            }
        }
    }

    private void notifyImaSdkLoaded() {
        App.v(TAG, "ads:notifyImaSdkLoaded: callbacks=" + mImaSdkAdCallbacks.size());
        for(VideoAdPlayer.VideoAdPlayerCallback callback: mImaSdkAdCallbacks) {
            callback.onLoaded();
        }
    }

    private void notifyImaSdkPlay() {
        App.v(TAG, "ads:notifyImaSdkPlay: callbacks=" + mImaSdkAdCallbacks.size());
        for(VideoAdPlayer.VideoAdPlayerCallback callback: mImaSdkAdCallbacks) {
            callback.onPlay();
        }
    }

    private void notifyImaSdkPause() {
        App.v(TAG, "ads:notifyImaSdkPause: callbacks=" + mImaSdkAdCallbacks.size());
        for(VideoAdPlayer.VideoAdPlayerCallback callback: mImaSdkAdCallbacks) {
            callback.onPause();
        }
    }

    private void notifyImaSdkEnded() {
        App.v(TAG, "ads:notifyImaSdkEnded: callbacks=" + mImaSdkAdCallbacks.size());
        for(VideoAdPlayer.VideoAdPlayerCallback callback: mImaSdkAdCallbacks) {
            callback.onEnded();
        }
    }

    private void notifyImaSdkError() {
        App.v(TAG, "ads:notifyImaSdkError: callbacks=" + mImaSdkAdCallbacks.size());
        for(VideoAdPlayer.VideoAdPlayerCallback callback: mImaSdkAdCallbacks) {
            callback.onError();
        }
    }

    private void changeAdPlayerSurfaceLayout() {
        int sw;
        int sh;

        if(mAdPlayer == null) {
            return;
        }

        // get screen size
        sw = getWindow().getDecorView().getWidth();
        sh = getWindow().getDecorView().getHeight();

        // sanity check
        if (sw * sh == 0) {
            Log.e(TAG, "Invalid surface size");
            return;
        }

        final IVLCVout vlcVout = mAdPlayer.getVLCVout();
        vlcVout.setWindowSize(sw, sh);

        SurfaceView surface;
        SurfaceView subtitlesSurface;
        FrameLayout surfaceFrame;

        surface = mAdPlayerSurfaceView;
        subtitlesSurface = mAdPlayerSubtitlesSurfaceView;
        surfaceFrame = mAdPlayerSurfaceFrame;

        LayoutParams lp = surface.getLayoutParams();

        if (mAdPlayerVideoWidth * mAdPlayerVideoHeight == 0 || isInPictureInPictureMode()) {
            /* Case of OpenGL vouts: handles the placement of the video using MediaPlayer API */
            lp.width = LayoutParams.MATCH_PARENT;
            lp.height = LayoutParams.MATCH_PARENT;
            surface.setLayoutParams(lp);
            lp = surfaceFrame.getLayoutParams();
            lp.width = LayoutParams.MATCH_PARENT;
            lp.height = LayoutParams.MATCH_PARENT;
            surfaceFrame.setLayoutParams(lp);
            if (mAdPlayerVideoWidth * mAdPlayerVideoHeight == 0)
                changeAdPlayerLayout(sw, sh);
            return;
        }

        if (lp.width == lp.height && lp.width == LayoutParams.MATCH_PARENT) {
            /* We handle the placement of the video using Android View LayoutParams */
            mAdPlayer.setAspectRatio(null);
            mAdPlayer.setScale(0);
        }

        double dw = sw, dh = sh;
        boolean isPortrait;

        // getWindow().getDecorView() doesn't always take orientation into account, we have to correct the values
        isPortrait = mCurrentScreenOrientation == Configuration.ORIENTATION_PORTRAIT;

        if (sw > sh && isPortrait || sw < sh && !isPortrait) {
            dw = sh;
            dh = sw;
        }

        // compute the aspect ratio
        double ar, vw;
        if (mAdPlayerSarDen == mAdPlayerSarNum) {
            /* No indication about the density, assuming 1:1 */
            vw = mAdPlayerVideoVisibleWidth;
            ar = (double) mAdPlayerVideoVisibleWidth / (double) mAdPlayerVideoVisibleHeight;
        } else {
            /* Use the specified aspect ratio */
            vw = mAdPlayerVideoVisibleWidth * (double) mAdPlayerSarNum / mAdPlayerSarDen;
            ar = vw / mAdPlayerVideoVisibleHeight;
        }

        // compute the display aspect ratio
        double dar = dw / dh;

        // best fit
        if (dar < ar)
            dh = dw / ar;
        else
            dw = dh * ar;

        // set display size
        lp.width = (int) Math.ceil(dw * mAdPlayerVideoWidth / mAdPlayerVideoVisibleWidth);
        lp.height = (int) Math.ceil(dh * mAdPlayerVideoHeight / mAdPlayerVideoVisibleHeight);
        surface.setLayoutParams(lp);
        //subtitlesSurface.setLayoutParams(lp);

        // set frame size (crop if necessary)
        lp = surfaceFrame.getLayoutParams();
        lp.width = (int) Math.floor(dw);
        lp.height = (int) Math.floor(dh);
        surfaceFrame.setLayoutParams(lp);

        surface.invalidate();
        //subtitlesSurface.invalidate();
    }

    private void changeAdPlayerLayout(int displayW, int displayH) {
        if(mAdPlayer != null) {
            mAdPlayer.setAspectRatio(null);
            mAdPlayer.setScale(0);
        }
    }

    private void adPlayerSkip() {
        // Skip current ad
        AdsManager manager = getCurrentAdsManager();
        if(manager != null) {
            App.v(TAG, "ads: trigger skip");
            manager.skip();
        }
    }

    private void adPlayerClick() {
        App.v(TAG, "ads: click");
        String url = (String)mAdPlayerButtonClick.getTag();
        if(url != null) {
            Intent intent = AceStreamEngineBaseApplication.getBrowserIntent(this, url, true);
            if(intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                AceStreamEngineBaseApplication.startBrowserIntent(this, intent);

                // Close all video ads
                for(AdsManager manager: mAdsManagers) {
                    manager.destroy();
                }
                mAdsManagers.clear();
                hideAds(AdSource.IMA_SDK);
            }
        }
    }

    private AdsManager getCurrentAdsManager() {
        if(mAdsManagers.size() > 0) {
            if(mAdsManagers.get(0).getCurrentAd() != null) {
                return mAdsManagers.get(0);
            }
        }
        return null;
    }

    private Ad getCurrentAd() {
        if(mAdsManagers.size() > 0) {
            return mAdsManagers.get(0).getCurrentAd();
        }
        return null;
    }

    private int getCurrentAdPosition() {
        Ad currentAd = getCurrentAd();
        if(currentAd != null) {
            if(currentAd.getAdPodInfo() != null) {
                return currentAd.getAdPodInfo().getAdPosition();
            }
        }

        return -1;
    }

    private VastTag getCurrentVastTag() {
        if(mAdTags == null || mAdTags.length == 0) {
            return null;
        }

        if(mCurrentAdTagIndex >= 0 && mCurrentAdTagIndex < mAdTags.length) {
            return mAdTags[mCurrentAdTagIndex];
        }

        return null;
    }

    private void initAppodeal() {
        if(mAppodealInitialized) {
            return;
        }

        mAppodealInitialized = true;
        boolean loadInterstitial = true;
        boolean loadRv = isUserLoggedIn();

        if(hasNoAds()) {
            // Users with NoAds can control ad placement
            loadRv = AceStreamEngineBaseApplication.showAdsOnPreroll();
            loadInterstitial = AceStreamEngineBaseApplication.showAdsOnPreroll()
                    || AceStreamEngineBaseApplication.showAdsOnPause()
                    || AceStreamEngineBaseApplication.showAdsOnClose();
        }

        if (loadInterstitial
                && !mAdsWaterfall.has(AdsWaterfall.Placement.PREROLL, AdsWaterfall.Inventory.APPODEAL_INTERSTITIAL)
                && !mAdsWaterfall.has(AdsWaterfall.Placement.PAUSE, AdsWaterfall.Inventory.APPODEAL_INTERSTITIAL)
                && !mAdsWaterfall.has(AdsWaterfall.Placement.CLOSE, AdsWaterfall.Inventory.APPODEAL_INTERSTITIAL)
                ) {
            loadInterstitial = false;
        }

        if(loadRv && !mAdsWaterfall.has(
                AdsWaterfall.Placement.PREROLL,
                new String[]{AdsWaterfall.Inventory.ADMOB_REWARDED_VIDEO, AdsWaterfall.Inventory.CUSTOM})) {
            loadRv = false;
        }

        appodealInitInterstitial();
        appodealInitRewardedVideo();

        int adTypes = 0;
        if(loadInterstitial) {
            adTypes |= Appodeal.INTERSTITIAL;
            mAdsWaterfall.onLoading(AdsWaterfall.Inventory.APPODEAL_INTERSTITIAL);
            if(Appodeal.isLoaded(Appodeal.INTERSTITIAL)) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (Appodeal.isLoaded(Appodeal.INTERSTITIAL)) {
                            App.v(TAG, "initAppodeal: interstitial was loaded");
                            mAdsWaterfall.onLoaded(AdsWaterfall.Inventory.APPODEAL_INTERSTITIAL);
                        }
                    }
                });
            }
        }
        if(loadRv) {
            adTypes |= Appodeal.REWARDED_VIDEO;
            mAdsWaterfall.onLoading(AdsWaterfall.Inventory.APPODEAL_REWARDED_VIDEO);
            if(Appodeal.isLoaded(Appodeal.REWARDED_VIDEO)) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (Appodeal.isLoaded(Appodeal.REWARDED_VIDEO)) {
                            App.v(TAG, "initAppodeal: rv was loaded");
                            mAdsWaterfall.onLoaded(AdsWaterfall.Inventory.APPODEAL_REWARDED_VIDEO);
                        }
                    }
                });
            }
        }

        App.v(TAG, "initAppodeal: interstitial=" + loadInterstitial + " rv=" + loadRv);

        if(adTypes > 0) {
            AceStreamEngineBaseApplication.initAppodeal(
                    -1,
                    this,
                    adTypes,
                    true,
                    mPlaybackManager.getAdConfig());
        }
    }

    private void appodealInitInterstitial() {
        Appodeal.setInterstitialCallbacks(new InterstitialCallbacks() {
            @Override
            public void onInterstitialLoaded(boolean isPrecache) {
                App.v(TAG, "ads:appodeal:onInterstitialLoaded");
                if(mAdsWaterfall != null) {
                    mAdsWaterfall.onLoaded(AdsWaterfall.Inventory.APPODEAL_INTERSTITIAL);
                }
            }
            @Override
            public void onInterstitialFailedToLoad() {
                App.v(TAG, "ads:appodeal:onInterstitialFailedToLoad");
            }
            @Override
            public void onInterstitialShown() {
                String placement = mAdsWaterfall.getPlacement();
                App.v(TAG, "ads:appodeal:onInterstitialShown: placement=" + placement);
                if(!TextUtils.equals(placement, AdsWaterfall.Placement.PAUSE)) {
                    notifyAdsLoaded();
                }
                onContentPauseRequested(AdSource.INTERSTITIAL_AD);
                addCoins("interstitial:player", 0, true);
                AceStreamEngineBaseApplication.getInstance().logAdImpression(
                        AdManager.ADS_PROVIDER_APPODEAL,
                        placement,
                        AdsWaterfall.AdType.INTERSTITIAL);
            }
            @Override
            public void onInterstitialClicked() {
                App.v(TAG, "ads:appodeal:onInterstitialClicked");
            }
            @Override
            public void onInterstitialClosed() {
                String placement = mAdsWaterfall.getPlacement();
                App.v(TAG, "ads:appodeal:onInterstitialClosed: placement=" + placement);
                if(TextUtils.equals(placement, AdsWaterfall.Placement.CLOSE)) {
                    onExitAdClosed();
                }
                else if (!isFinishing()) {
                    onContentResumeRequested(AdSource.INTERSTITIAL_AD);
                }

            }
            @Override
            public void onInterstitialExpired() {
                App.v(TAG, "ads:appodeal:onInterstitialExpired");
            }
        });
    }

    private void appodealInitRewardedVideo() {
        Appodeal.setRewardedVideoCallbacks(new RewardedVideoCallbacks() {
            @Override
            public void onRewardedVideoLoaded(boolean isPrecache) {
                Log.d(TAG, "appodeal:onRewardedVideoLoaded: isPrecache=" + isPrecache);
                if(mAdsWaterfall != null) {
                    mAdsWaterfall.onLoaded(AdsWaterfall.Inventory.APPODEAL_REWARDED_VIDEO);
                }
            }

            @Override
            public void onRewardedVideoFailedToLoad() {
                Log.d(TAG, "appodeal:onRewardedVideoFailedToLoad");
            }

            @Override
            public void onRewardedVideoShown() {
                Log.d(TAG, "appodeal:onRewardedVideoShown");
                notifyAdsLoaded();
                onContentPauseRequested(AdSource.REWARDED_VIDEO);
            }

            @Override
            public void onRewardedVideoFinished(double amount, String name) {
                Log.d(TAG, "appodeal:onRewardedVideoFinished: amount=" + amount + " name=" + name);

                String source = "rv:player:preroll";
                addCoins(source, 0, false);

                Bundle params = new Bundle();
                params.putString("source", source);
                AceStreamEngineBaseApplication.getInstance().logAdImpression(
                        AdManager.ADS_PROVIDER_APPODEAL,
                        AdsWaterfall.Placement.PREROLL,
                        AdsWaterfall.AdType.REWARDED_VIDEO,
                        params);
            }

            @Override
            public void onRewardedVideoClosed(boolean finished) {
                Log.d(TAG, "appodeal:onRewardedVideoClosed");
                if (!isFinishing()) {
                    onContentResumeRequested(AdSource.REWARDED_VIDEO);
                }
            }

            @Override
            public void onRewardedVideoExpired() {
                Log.d(TAG, "appodeal:onRewardedVideoExpired");
            }
        });
    }

    private boolean canShowAds(String placement, String inventory) {
        App.v(TAG, "canShowAds:"
                + " started=" + mIsStarted
                + " bg=" + mIsInBackground
                + " placement=" + placement
                + " inventory=" + inventory
        );

        if(TextUtils.equals(inventory, AdsWaterfall.Inventory.VAST) && TextUtils.equals(placement, AdsWaterfall.Placement.UNPAUSE)) {
            // Unpause can be initiated when pause interstitial is closing.
            // In such case player can be in background but will be resumed soon.
            return true;
        }

        return mIsStarted && !mIsInBackground;
    }

    private void setInBackground(boolean value) {
        mIsInBackground = value;
    }

    private void onExitAdClosed() {
        // Show some activity because we cannot show ads when user is leaving our app.
        if(AceStreamEngineBaseApplication.useVlcBridge()) {
            VlcBridge.openMainActivity();
        }
        else {
            startActivity(new Intent(this, RemoteControlActivity.class));
        }
    }

    private class AdSettings {
        public int maxAds = 2;
        public int imaSdkHandlerDelay = 250;

        public void initFromConfig(AdConfig config) {
            maxAds = config.max_ads;
            imaSdkHandlerDelay = config.ima_sdk_handler_delay;
        }
    }

    private void freezeEngineStatus(long delay) {
        freezeEngineStatusAt = System.currentTimeMillis();
        freezeEngineStatusFor = delay;
    }

    private void unfreezeEngineStatus() {
        freezeEngineStatusAt = 0;
        freezeEngineStatusFor = 0;
    }

    private boolean areViewsAttached() {
        return mMediaPlayer != null && mMediaPlayer.getVLCVout().areViewsAttached();
    }

    private void runWhenPlaybackManagerReady(Runnable runnable, boolean alwaysSchedule) {
        if(mPlaybackManager != null) {
            if(alwaysSchedule) {
                mHandler.post(runnable);
            }
            else {
                runnable.run();
            }
        }
        else {
            mPlaybackManagerOnReadyQueue.add(runnable);
        }
    }

    private boolean shouldShowAds() {
        if(!mRenderAds) {
            return false;
        }

        if(mPlaylist == null) {
            App.vv(TAG, "shouldShowAds: skip ads: no ps");
            return false;
        }

        MediaItem item = mPlaylist.getCurrentItem();
        if(item == null) {
            App.vv(TAG, "shouldShowAds: skip ads: no current media");
            return false;
        }

        if(item.isP2PItem()) {
            App.vv(TAG, "shouldShowAds: show ads for p2p item");
            return true;
        }

        App.vv(TAG, "shouldShowAds: skip ads for regular item");

        return false;
    }

    private boolean isCurrentMediaP2P() {
        return mPlaylist != null
                && mPlaylist.getCurrentItem() != null
                && mPlaylist.getCurrentItem().isP2PItem();
    }

    /**
     * This is called once on each media.
     */
    private void onMediaStarted() {
        runWhenPlaybackManagerReady(new Runnable() {
            @Override
            public void run() {
                if(!isCurrentMediaP2P() && shouldShowAds()) {
                    if(mRenderAds && mAdsWaterfall != null) {
                        mAdsWaterfall.setPlacement(AdsWaterfall.Placement.PREROLL, true);
                        mAdsWaterfall.resetInventoryStatus(AdsWaterfall.Inventory.VAST);
                    }
                    requestNextAds();
                }
            }
        }, true);
    }

    private void updateSwitchPlayerButton() {
        boolean visible;
        if(mRemoteClientId != null) {
            // Don't show 'switch player' button on remote device
            visible = false;
        }
        else {
            visible = isCurrentMediaP2P();
        }

        mSwitchPlayer.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void setRemoteClientId(String id) {
        mRemoteClientId = id;
        updateSwitchPlayerButton();
    }

    private boolean showTvUi() {
        return mShowTvUi;
    }

    private void createMediaPlayer() {
        if(mMediaPlayer != null) {
            return;
        }

        App.v(TAG, "createMediaPlayer: aout=" + mAout + " digital=" + mAudioDigitalOutputEnabled);
        mMediaPlayer = new MediaPlayer(getLibVlc());
        mMediaPlayer.setEventListener(mMediaPlayerEventListener);

        // explicitly set audio device
        mMediaPlayer.setAudioDigitalOutputEnabled(mAudioDigitalOutputEnabled);
        if(mAout != null) {
            mMediaPlayer.setAudioOutput(mAout);
        }
    }

    private LibVLC getLibVlc() {
        if(mLibVlc == null) {
            initLibVlc();
        }

        return mLibVlc;
    }

    private void setUserAgent(String userAgent) {
        mUserAgent = userAgent;
        if(mLibVlc != null) {
            mLibVlc.setUserAgent(mUserAgent, mUserAgent);
        }
    }

    private void initLibVlc() {
        if(mLibVlc == null) {
            App.v(TAG, "initLibVlc: ua=" + mUserAgent);
            mLibVlc = new LibVLC(this, getLibVlcOptions());

            if (mUserAgent != null) {
                mLibVlc.setUserAgent(mUserAgent, mUserAgent);
            }
        }
    }

    private void shutdown() {
        // Cleanup all player resources
        mPlaylist.clear();
        releaseMediaPlayer();
        releaseLibVlc();
    }

    private void releaseMediaPlayer() {
        App.v(TAG, "releaseMediaPlayer");
        if(mMediaPlayer != null) {
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }

    private void releaseLibVlc() {
        App.v(TAG, "releaseLibVlc");
        if(mLibVlc != null) {
            mLibVlc.release();
            mLibVlc = null;
        }
    }

    private ArrayList<String> getLibVlcOptions() {
        if(mLibVlcOptions != null) {
            return mLibVlcOptions;
        }
        mLibVlcOptions = new ArrayList<>(50);

        Intent intent = getIntent();
        Bundle extras = null;
        SharedPreferences prefs = AceStreamEngineBaseApplication.getPreferences();
        if(intent != null) {
            extras = intent.getBundleExtra(AceStreamPlayer.EXTRA_LIBVLC_OPTIONS);
        }

        final boolean timeStreching;
        final String subtitlesEncoding;
        final boolean frameSkip;
        final String chroma;
        int deblocking;
        int networkCaching;
        final String freetypeRelFontsize;
        final boolean freetypeBold;
        final String freetypeColor;
        final boolean freetypeBackground;
        final int opengl;
        final String resampler;
        final String deinterlaceMode;

        if(extras != null) {
            mHardwareAcceleration = extras.getInt("hardware_acceleration", VlcConstants.HW_ACCELERATION_DISABLED);

            timeStreching = extras.getBoolean("enable_time_stretching_audio", false);
            subtitlesEncoding = extras.getString("subtitle_text_encoding", "");
            frameSkip = extras.getBoolean("enable_frame_skip", false);
            chroma = extras.getString("chroma_format", "RV16");
            deblocking = extras.getInt("deblocking", -1);
            networkCaching = extras.getInt("network_caching_value", 0);
            freetypeRelFontsize = extras.getString("subtitles_size", "16");
            freetypeBold = extras.getBoolean("subtitles_bold", false);
            freetypeColor = extras.getString("subtitles_color", "16777215");
            freetypeBackground = extras.getBoolean("subtitles_background", false);
            opengl = Integer.parseInt(extras.getString("opengl", "-1"));
            resampler = extras.getString("resampler", null);
            deinterlaceMode = extras.getString("deinterlace_mode", null);
        }
        else {
            mHardwareAcceleration = MiscUtils.getIntFromStringPreference(prefs,
                    "hardware_acceleration",
                    VlcConstants.HW_ACCELERATION_DISABLED);

            timeStreching = prefs.getBoolean("enable_time_stretching_audio", false);
            subtitlesEncoding = prefs.getString("subtitle_text_encoding", "");
            frameSkip = prefs.getBoolean("enable_frame_skip", false);
            chroma = prefs.getString("chroma_format", "RV16");
            deblocking = MiscUtils.getIntFromStringPreference(prefs,"deblocking", -1);
            networkCaching = MiscUtils.getIntFromStringPreference(prefs,"network_caching_value", 0);
            freetypeRelFontsize = prefs.getString("subtitles_size", "16");
            freetypeBold = prefs.getBoolean("subtitles_bold", false);
            freetypeColor = prefs.getString("subtitles_color", "16777215");
            freetypeBackground = prefs.getBoolean("subtitles_background", false);
            opengl = MiscUtils.getIntFromStringPreference(prefs,"opengl", -1);
            resampler = prefs.getString("resampler", null);
            deinterlaceMode = prefs.getString("deinterlace_mode", null);
        }

        mLibVlcOptions.add(timeStreching ? "--audio-time-stretch" : "--no-audio-time-stretch");
        mLibVlcOptions.add("--avcodec-skiploopfilter");
        mLibVlcOptions.add("" + deblocking);
        mLibVlcOptions.add("--avcodec-skip-frame");
        mLibVlcOptions.add(frameSkip ? "2" : "0");
        mLibVlcOptions.add("--avcodec-skip-idct");
        mLibVlcOptions.add(frameSkip ? "2" : "0");
        mLibVlcOptions.add("--subsdec-encoding");
        mLibVlcOptions.add(subtitlesEncoding);
        mLibVlcOptions.add("--stats");
        if (networkCaching > 0)
            mLibVlcOptions.add("--network-caching=" + networkCaching);
        mLibVlcOptions.add("--android-display-chroma");
        mLibVlcOptions.add(chroma);
        if(!TextUtils.isEmpty(resampler)) {
            mLibVlcOptions.add("--audio-resampler");
            mLibVlcOptions.add(resampler);
        }

        mLibVlcOptions.add(AceStreamEngineBaseApplication.isDebugLoggingEnabled() ? "-vv" : "-v");
        mLibVlcOptions.add("--http-reconnect");

        // deinterlace
        if(TextUtils.isEmpty(deinterlaceMode)) {
            // disable
            mLibVlcOptions.add("--deinterlace=0");
        }
        else {
            mLibVlcOptions.add("--deinterlace=-1");
            mLibVlcOptions.add("--deinterlace-mode=" + deinterlaceMode); // discard,blend,mean,bob,linear,x,yadif,yadif2x,phosphor,ivtc
            mLibVlcOptions.add("--video-filter=deinterlace");
        }

        mLibVlcOptions.add("--freetype-rel-fontsize=" + freetypeRelFontsize);
        if (freetypeBold)
            mLibVlcOptions.add("--freetype-bold");
        mLibVlcOptions.add("--freetype-color=" + freetypeColor);
        if (freetypeBackground)
            mLibVlcOptions.add("--freetype-background-opacity=128");
        else
            mLibVlcOptions.add("--freetype-background-opacity=0");

        if (opengl == 1)
            mLibVlcOptions.add("--vout=gles2,none");
        else if (opengl == 0)
            mLibVlcOptions.add("--vout=android_display,none");

        return mLibVlcOptions;
    }

    private void setMediaOptions(Media media) {
        if (mHardwareAcceleration == VlcConstants.HW_ACCELERATION_DISABLED)
            media.setHWDecoderEnabled(false, false);
        else if (mHardwareAcceleration == VlcConstants.HW_ACCELERATION_FULL
                || mHardwareAcceleration == VlcConstants.HW_ACCELERATION_DECODING) {
            media.setHWDecoderEnabled(true, true);
            if (mHardwareAcceleration == VlcConstants.HW_ACCELERATION_DECODING) {
                media.addOption(":no-mediacodec-dr");
                media.addOption(":no-omxil-dr");
            }
        }
    }

    private boolean isPausable() {
        return true;
    }

    private void notifyPlaybackStarted(@Nullable MediaItem media) {
        if(mBroadcastAction == null || media == null) return;

        Intent intent = new Intent(mBroadcastAction);
        intent.putExtra(AceStreamPlayer.BROADCAST_EXTRA_EVENT, AceStreamPlayer.BROADCAST_EVENT_PLAYBACK_STARTED);
        intent.putExtra(AceStreamPlayer.BROADCAST_EXTRA_MEDIA_URI, media.getUri().toString());
        intent.putExtra(AceStreamPlayer.BROADCAST_EXTRA_MEDIA_ID, media.getId());
        intent.putExtra(AceStreamPlayer.BROADCAST_EXTRA_MEDIA_TIME, media.getSavedTime());
        intent.putExtra(AceStreamPlayer.BROADCAST_EXTRA_MEDIA_DURATION, media.getDuration());

        MediaFilesResponse.MediaFile mf = media.getMediaFile();
        if(mf != null) {
            intent.putExtra(AceStreamPlayer.BROADCAST_EXTRA_MEDIA_FILE, mf.toJson());
        }

        sendBroadcast(intent);
    }

    private void notifyPlayerStarted() {
        if(mBroadcastAction == null) return;
        Intent intent = new Intent(mBroadcastAction);
        intent.putExtra(AceStreamPlayer.BROADCAST_EXTRA_EVENT, AceStreamPlayer.BROADCAST_EVENT_PLAYER_STARTED);
        sendBroadcast(intent);
    }

    private void notifyPlayerStopped() {
        if(mBroadcastAction == null) return;
        Intent intent = new Intent(mBroadcastAction);
        intent.putExtra(AceStreamPlayer.BROADCAST_EXTRA_EVENT, AceStreamPlayer.BROADCAST_EVENT_PLAYER_STOPPED);
        sendBroadcast(intent);
    }

    private void notifySaveMetadata(@Nullable MediaItem media) {
        if(mBroadcastAction == null || media == null) return;

        Intent intent = new Intent(mBroadcastAction);
        intent.putExtra(AceStreamPlayer.BROADCAST_EXTRA_EVENT, AceStreamPlayer.BROADCAST_EVENT_SAVE_METADATA);
        intent.putExtra(AceStreamPlayer.BROADCAST_EXTRA_PLAYLIST_POSITION, mPlaylist.getCurrentMediaPosition());
        intent.putExtra(AceStreamPlayer.BROADCAST_EXTRA_MEDIA_URI, media.getUri().toString());
        intent.putExtra(AceStreamPlayer.BROADCAST_EXTRA_MEDIA_ID, media.getId());
        intent.putExtra(AceStreamPlayer.BROADCAST_EXTRA_MEDIA_TIME, media.getSavedTime());
        intent.putExtra(AceStreamPlayer.BROADCAST_EXTRA_MEDIA_DURATION, media.getDuration());

        sendBroadcast(intent);
    }

    private void notifyChangeRenderer(@NonNull SelectedPlayer player) {
        if(mBroadcastAction == null) return;

        Intent intent = new Intent(mBroadcastAction);
        intent.putExtra(AceStreamPlayer.BROADCAST_EXTRA_EVENT, AceStreamPlayer.BROADCAST_EVENT_CHANGE_RENDERER);
        intent.putExtra(AceStreamPlayer.BROADCAST_EXTRA_RENDERER, player.toJson());

        sendBroadcast(intent);
    }

    private void notifyUpdatePreference(String name, boolean value) {
        if(mBroadcastAction == null) return;

        Intent intent = new Intent(mBroadcastAction);
        intent.putExtra(AceStreamPlayer.BROADCAST_EXTRA_EVENT, AceStreamPlayer.BROADCAST_EVENT_UPDATE_PREFERENCE);
        intent.putExtra(AceStreamPlayer.BROADCAST_EXTRA_PREF_NAME, name);
        intent.putExtra(AceStreamPlayer.BROADCAST_EXTRA_PREF_VALUE, value);

        sendBroadcast(intent);
    }

    private void notifySetRepeatType(int mode) {
        if(mBroadcastAction == null) return;

        Intent intent = new Intent(mBroadcastAction);
        intent.putExtra(AceStreamPlayer.BROADCAST_EXTRA_EVENT, AceStreamPlayer.BROADCAST_EVENT_SET_REPEAT_TYPE);
        intent.putExtra(AceStreamPlayer.BROADCAST_EXTRA_REPEAT_TYPE, mode);

        sendBroadcast(intent);
    }

    private void notifySetShuffle(boolean enabled) {
        if(mBroadcastAction == null) return;

        Intent intent = new Intent(mBroadcastAction);
        intent.putExtra(AceStreamPlayer.BROADCAST_EXTRA_EVENT, AceStreamPlayer.BROADCAST_EVENT_SET_SHUFFLE);
        intent.putExtra(AceStreamPlayer.BROADCAST_EXTRA_SHUFFLE, enabled);

        sendBroadcast(intent);
    }

    private void notifySetSleepTimer(Calendar time) {
        if(mBroadcastAction == null) return;

        Intent intent = new Intent(mBroadcastAction);
        intent.putExtra(AceStreamPlayer.BROADCAST_EXTRA_EVENT, AceStreamPlayer.BROADCAST_EVENT_SET_SLEEP_TIMER);
        intent.putExtra(AceStreamPlayer.BROADCAST_EXTRA_SLEEP_TIME, time);

        sendBroadcast(intent);
    }

    private void parseIntent() {
        Intent intent = getIntent();
        if(intent == null) return;
        mShowTvUi = intent.getBooleanExtra(AceStreamPlayer.EXTRA_SHOW_TV_UI, false);
        mPlayFromStart = intent.getBooleanExtra(AceStreamPlayer.EXTRA_PLAY_FROM_START, false);
        mSeekOnStart = intent.getLongExtra(AceStreamPlayer.EXTRA_PLAY_FROM_TIME, -1);
        mBroadcastAction = intent.getStringExtra(AceStreamPlayer.EXTRA_BROADCAST_ACTION);
        if(AceStreamEngineBaseApplication.useVlcBridge()) {
            mAskResume = intent.getBooleanExtra(AceStreamPlayer.EXTRA_ASK_RESUME, false);
            mAudioDigitalOutputEnabled = intent.getBooleanExtra(AceStreamPlayer.EXTRA_AUDIO_DIGITAL_OUTPUT_ENABLED, false);
            mAout = intent.getStringExtra(AceStreamPlayer.EXTRA_AUDIO_OUTPUT);
        }
        else {
            SharedPreferences prefs = AceStreamEngineBaseApplication.getPreferences();
            mAskResume = prefs.getBoolean("dialog_confirm_resume", false);
            mAudioDigitalOutputEnabled = prefs.getBoolean("audio_digital_output", false);
            mAout = prefs.getString("aout", null);
        }

        String screenOrientation = intent.getStringExtra(AceStreamPlayer.EXTRA_SCREEN_ORIENTATION);
        if(screenOrientation != null) {
            try {
                mScreenOrientation = Integer.valueOf(screenOrientation);
            }
            catch(NumberFormatException e) {
                mScreenOrientation = DEFAULT_SCREEN_ORIENTATION;
            }
        }

        if(BuildConfig.DEBUG) {
            Log.v(TAG, "parseIntent: mShowTvUi=" + mShowTvUi);
            Log.v(TAG, "parseIntent: mAskResume=" + mAskResume);
            Log.v(TAG, "parseIntent: mPlayFromStart=" + mPlayFromStart);
            Log.v(TAG, "parseIntent: mSeekOnStart=" + mSeekOnStart);
            Log.v(TAG, "parseIntent: mAudioDigitalOutputEnabled=" + mAudioDigitalOutputEnabled);
            Log.v(TAG, "parseIntent: mAout=" + mAout);
            Log.v(TAG, "parseIntent: mBroadcastAction=" + mBroadcastAction);
            Log.v(TAG, "parseIntent: remoteClientId=" + intent.getStringExtra(AceStreamPlayer.EXTRA_REMOTE_CLIENT_ID));
            Log.v(TAG, "parseIntent: screenOrientation=" + screenOrientation);
        }
    }

    private MediaItem getCurrentMedia() {
        return mPlaylist.getCurrentItem();
    }

    // PlayerSettingsHandler
    @Override
    public void setSleepTime(Calendar time) {
        if(AceStreamEngineBaseApplication.useVlcBridge()) {
            // Ask media app to set sleep intent
            notifySetSleepTimer(time);
        }
        else {
            //TODO: init sleep intent here
        }
    }

    @Override
    public float getRate() {
        return mMediaPlayer.getRate();
    }

    @Override
    public void setRate(float rate) {
        mMediaPlayer.setRate(rate);
    }

    @Override
    public int getRepeatType() {
        return mPlaylist.getRepeatType();
    }

    @Override
    public void toggleRepeatType() {
        mPlaylist.toggleRepeatType();
        notifySetRepeatType(getRepeatType());
    }

    @Override
    public boolean getShuffle() {
        return mPlaylist.getShuffle();
    }

    @Override
    public void toggleShuffle() {
        mPlaylist.toggleShuffle();
        notifySetShuffle(getShuffle());
    }

    @Override
    public long getSubtitleDelay() {
        return mMediaPlayer.getSpuDelay();
    }

    @Override
    public void setSubtitleDelay(long delay) {
        mMediaPlayer.setSpuDelay(delay);
    }

    @Override
    public long getAudioDelay() {
        return mMediaPlayer.getAudioDelay();
    }

    @Override
    public void setAudioDelay(long delay) {
        mMediaPlayer.setAudioDelay(delay);
    }

    @Override
    public void seekToTime(long time) {
        seek(time);
    }

    @Override
    public boolean hasPlaylist() {
        return mPlaylist.size() > 1;
    }

    private void checkMobileNetworkConnection(final Runnable runnable) {
        boolean isConnectedToMobileNetwork = MiscUtils.isConnectedToMobileNetwork(this);
        boolean askedAboutMobileNetworking = isMobileNetworkingEnabled();

        if(isConnectedToMobileNetwork && !askedAboutMobileNetworking) {
            Log.d(TAG, "checkMobileNetworkConnection: ask about mobile network: connected=" + isConnectedToMobileNetwork + " asked=" + askedAboutMobileNetworking);

            AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppCompatAlertDialogStyle);
            builder.setMessage(R.string.allow_mobile_networks);
            builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    setMobileNetworkingEnabled(true);
                    runnable.run();
                }
            });
            builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    setMobileNetworkingEnabled(false);
                    exit();
                }
            });
            builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    setMobileNetworkingEnabled(false);
                    exit();
                }
            });
            builder.create().show();
        }
        else {
            runnable.run();
        }
    }

    public boolean isMobileNetworkingEnabled() {
        SharedPreferences sp = AceStreamEngineBaseApplication.getPreferences();
        return sp.getBoolean("mobile_network_available", false);
    }

    public void setMobileNetworkingEnabled(boolean value) {
        SharedPreferences sp = AceStreamEngineBaseApplication.getPreferences();
        sp.edit().putBoolean("mobile_network_available", value).apply();

        notifyUpdatePreference("mobile_network_available", value);
    }

    private void updateMediaSessionMetadata() {
        if(mMediaSession != null) {
            MediaItem item = mPlaylist.getCurrentItem();
            if(item != null) {
                MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
                        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, item.getTitle())
                        .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, item.getTitle())
                        .build();
                mMediaSession.setMetadata(metadata);
            }
        }
    }

    protected void addCoins(String source, int amount, boolean needNoAds) {
        if (mPlaybackManager != null) {
            mPlaybackManager.addCoins(source, amount, needNoAds);
        }
    }
}
