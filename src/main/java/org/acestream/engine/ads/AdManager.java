package org.acestream.engine.ads;

import android.app.Activity;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;

import com.adcolony.sdk.AdColonyAppOptions;
import com.google.ads.mediation.adcolony.AdColonyMediationAdapter;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.reward.RewardItem;
import com.google.android.gms.ads.reward.RewardedVideoAd;
import com.google.android.gms.ads.reward.RewardedVideoAdListener;
import com.mopub.common.MoPub;
import com.mopub.common.SdkConfiguration;
import com.tapjoy.Tapjoy;

import org.acestream.engine.AceStreamEngineBaseApplication;
import org.acestream.engine.BuildConfig;
import org.acestream.engine.Constants;
import org.acestream.engine.aliases.App;
import org.acestream.sdk.controller.api.response.AdConfig;
import org.acestream.sdk.utils.Logger;
import org.acestream.sdk.utils.MiscUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class AdManager {
    private final static String TAG = "AS/AdManager";

    private static AdManager sInstance = null;

    public final static String ADS_PROVIDER_ADMOB = "admob";
    public final static String ADS_PROVIDER_APPODEAL = "appodeal";
    public final static String ADS_PROVIDER_VAST = "vast";
    public final static String ADS_PROVIDER_POLLFISH = "pollfish";

    private Activity mActivity = null;
    private Map<String, InterstitialAd> mInterstitialAd;
    private RewardedVideoAd mRewardedVideoAd;
    private Handler mHandler = new Handler();

    private AdConfig mAdConfig;
    private int mCurrentAdSegment = -1;
    private int mNextAdSegment = -1;
    private int mRewardedVideoSegmentId = -1;
    private RewardedVideoAdListener mRewardedVideoAdListener = null;
    private static Set<Activity> sRewardedVideoActivities = new CopyOnWriteArraySet<>();

    public static AdManager getInstance() {
        return sInstance;
    }

    public AdManager(AdConfig config) {
        setAdConfig(config);
        sInstance = this;
    }

    public void init(Activity activity) {
        if(activity == mActivity) {
            return;
        }

        mActivity = activity;
        if(mAdConfig != null && mAdConfig.isProviderEnabled("admob")) {
            // TapJoy
            Tapjoy.setUserConsent(AceStreamEngineBaseApplication.getGdprConsent()
                    ? "1"
                    : "0");

            // AdColony
            AdColonyAppOptions appOptions = AdColonyMediationAdapter.getAppOptions();
            appOptions.setGDPRConsentString(AceStreamEngineBaseApplication.getGdprConsent()
                    ? "1"
                    : "0");
            appOptions.setGDPRRequired(true);

            // MoPub
            String moPubAdUnitId = AceStreamEngineBaseApplication.getStringAppMetadata("moPubAdUnitId");
            if(!TextUtils.isEmpty(moPubAdUnitId)) {
                SdkConfiguration sdkConfiguration =
                        new SdkConfiguration.Builder(moPubAdUnitId).build();
                MoPub.initializeSdk(activity, sdkConfiguration, null);
            }

            // AdMob
            MobileAds.initialize(mActivity,
                    BuildConfig.admobUseTestAds
                            ? org.acestream.engine.Constants.ADMOB_TEST_APP_ID
                            : AceStreamEngineBaseApplication.getStringAppMetadata("adMobAppId"));
        }
    }

    public void setAdConfig(AdConfig config) {
        mAdConfig = config;

        if(mInterstitialAd == null && config.isProviderEnabled("admob")) {
            mInterstitialAd = new HashMap<>();
        }
    }

    public void destroy() {
        App.v(TAG, "destroy");
    }

    public boolean isInterstitialLoaded(String tag) {
        if(mInterstitialAd == null) {
            return false;
        }
        InterstitialAd ad = mInterstitialAd.get(tag);
        return ad != null && ad.isLoaded();
    }

    public void initInterstitial(final @NonNull Activity activity, final String tag, String adUnitId, AdListener listener) {
        if(mInterstitialAd == null) {
            return;
        }
        InterstitialAd ad = mInterstitialAd.get(tag);
        if(ad == null) {
            ad = new InterstitialAd(activity);
            ad.setAdUnitId(adUnitId);
            mInterstitialAd.put(tag, ad);
        }
        ad.setAdListener(listener);
    }

    public void resetInterstitial(String tag) {
        if(mInterstitialAd == null) {
            return;
        }
        InterstitialAd ad = mInterstitialAd.get(tag);
        if(ad != null) {
            ad.setAdListener(null);
        }
    }

    public boolean loadInterstitial(String tag) {
        return loadInterstitial(tag, false);
    }

    public boolean loadInterstitial(String tag, boolean force) {
        App.v(TAG, "loadInterstitial: tag=" + tag + " force=" + force);

        if(mInterstitialAd == null) {
            throw new IllegalStateException("interstitial is not initialized");
        }

        InterstitialAd ad = mInterstitialAd.get(tag);
        if(ad == null) {
            throw new IllegalStateException("interstitial is not initialized");
        }

        if(ad.isLoaded() && !force) {
            App.v(TAG, "loadInterstitial: already loaded: tag=" + tag);
            return false;
        }

        ad.loadAd(AceStreamEngineBaseApplication
                .createAdRequestBuilder()
                .build());

        return true;
    }

    public boolean showInterstitial(String tag) {
        App.v(TAG, "showInterstitial: tag=" + tag);

        if(mInterstitialAd == null) {
            return false;
        }

        InterstitialAd ad = mInterstitialAd.get(tag);
        if(ad != null && ad.isLoaded()) {
            ad.show();
            return true;
        }

        return false;
    }

    public static void registerRewardedVideoActivity(Activity activity) {
        sRewardedVideoActivities.add(activity);
    }

    public static void unregisterRewardedVideoActivity(Activity activity) {
        sRewardedVideoActivities.remove(activity);
    }

    public void initRewardedVideo(final @NonNull Activity activity, @NonNull final RewardedVideoAdListener listener) {
        initRewardedVideo(activity, -1, listener);
    }

    /**
     * Init RV
     *
     * @param activity
     * @param segmentId
     * @param listener
     * @return true if already loaded
     */
    public boolean initRewardedVideo(final @NonNull Activity activity, int segmentId, @Nullable final RewardedVideoAdListener listener) {
        mRewardedVideoAdListener = listener;
        mRewardedVideoSegmentId = segmentId;

        if(mRewardedVideoAd == null) {
            mRewardedVideoAd = MobileAds.getRewardedVideoAdInstance(activity);

            mRewardedVideoAd.setRewardedVideoAdListener(new RewardedVideoAdListener() {
                @Override
                public void onRewardedVideoAdLoaded() {
                    Log.v(TAG, "adevent:rv:onRewardedVideoAdLoaded");
                    if(mRewardedVideoAdListener != null) {
                        mRewardedVideoAdListener.onRewardedVideoAdLoaded();
                    }
                }

                @Override
                public void onRewardedVideoAdOpened() {
                    Log.v(TAG, "adevent:rv:onRewardedVideoAdOpened: load next");
                    if(mRewardedVideoAdListener != null) {
                        mRewardedVideoAdListener.onRewardedVideoAdOpened();
                    }
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            loadRewardedVideoAd(true);
                        }
                    }, 1000);
                }

                @Override
                public void onRewardedVideoStarted() {
                    Log.v(TAG, "adevent:rv:onRewardedVideoStarted");
                    if(mRewardedVideoAdListener != null) {
                        mRewardedVideoAdListener.onRewardedVideoStarted();
                    }
                }

                @Override
                public void onRewardedVideoAdClosed() {
                    Log.v(TAG, "adevent:rv:onRewardedVideoAdClosed");

                    if(mRewardedVideoAdListener != null) {
                        mRewardedVideoAdListener.onRewardedVideoAdClosed();
                    }
                }

                @Override
                public void onRewarded(RewardItem reward) {
                    Log.v(TAG, "adevent:rv:onRewarded: currency: " + reward.getType() + "  amount: " + reward.getAmount());
                    if(mRewardedVideoAdListener != null) {
                        mRewardedVideoAdListener.onRewarded(reward);
                    }
                }

                @Override
                public void onRewardedVideoAdLeftApplication() {
                    Log.v(TAG, "adevent:rv:onRewardedVideoAdLeftApplication");
                    if(mRewardedVideoAdListener != null) {
                        mRewardedVideoAdListener.onRewardedVideoAdLeftApplication();
                    }
                }

                @Override
                public void onRewardedVideoAdFailedToLoad(int i) {
                    boolean retry = gotRewardedVideoListeners();
                    int interval;
                    if(mNextAdSegment == -1) {
                        interval = MiscUtils.randomIntRange(5000, 10000);
                    }
                    else {
                        interval = MiscUtils.randomIntRange(500, 1500);
                    }

                    Log.v(TAG, "adevent:rv:onRewardedVideoAdFailedToLoad: retry=" + retry + " interval=" + interval);

                    if(mRewardedVideoAdListener != null) {
                        mRewardedVideoAdListener.onRewardedVideoAdFailedToLoad(i);
                    }

                    if(retry) {
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                loadRewardedVideoAd(false);
                            }
                        }, interval);
                    }
                }

                @Override
                public void onRewardedVideoCompleted() {
                    Log.v(TAG, "adevent:rv:onRewardedVideoCompleted");
                    if(mRewardedVideoAdListener != null) {
                        mRewardedVideoAdListener.onRewardedVideoCompleted();
                    }
                }
            });
        }

        if(!isRewardedVideoLoaded()) {
            loadRewardedVideoAd(true);
            return false;
        }
        else {
            Logger.v(TAG, "initRewardedVideo: already loaded");
            return true;
        }
    }

    private void loadRewardedVideoAd(boolean resetSegment) {
        AdRequest request = AceStreamEngineBaseApplication
                .createAdRequestBuilder()
                .build();

        if(mRewardedVideoSegmentId != -1) {
            Logger.v(TAG, "loadRewardedVideoAd: use fixed ad segment: segment=" + mRewardedVideoSegmentId);
            mCurrentAdSegment = mRewardedVideoSegmentId;
        }
        else if(mCurrentAdSegment == -1 || resetSegment) {
            mCurrentAdSegment = AceStreamEngineBaseApplication.getHighestAdSegment();
            Logger.v(TAG, "loadRewardedVideoAd: use highest ad segment: segment=" + mCurrentAdSegment);
        }
        else {
            int prev = mCurrentAdSegment;
            mCurrentAdSegment = AceStreamEngineBaseApplication.getNextAdSegment(mCurrentAdSegment, getAdSegment());
            Logger.v(TAG, "loadRewardedVideoAd: use next segment: segment=" + prev + "->" + mCurrentAdSegment);
            if(mCurrentAdSegment == -1) {
                mCurrentAdSegment = AceStreamEngineBaseApplication.getHighestAdSegment();
                Logger.v(TAG, "loadRewardedVideoAd: fallback to highest ad segment: segment=" + mCurrentAdSegment);
            }
        }

        String adBlockId = getAdBlockId(mCurrentAdSegment);
        if(adBlockId == null) {
            Log.e(TAG, "loadRewardedVideoAd: failed to get ad block id: segment=" + mCurrentAdSegment);
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    loadRewardedVideoAd(false);
                }
            }, 1000);
            return;
        }

        if(mRewardedVideoSegmentId == -1) {
            mNextAdSegment = AceStreamEngineBaseApplication.getNextAdSegment(mCurrentAdSegment, getAdSegment());
        }
        else {
            // -1 means that there is no other segment to try within current request
            mNextAdSegment = -1;
        }

        mRewardedVideoAd.loadAd(
                BuildConfig.admobUseTestAds
                        ? Constants.ADMOB_TEST_REWARDED_VIDEO
                        : adBlockId,
                request
        );
    }

    private String getAdBlockId(int segment) {
        if(mAdConfig == null) {
            Log.e(TAG, "getAdBlockId: missing ad config");
            return null;
        }

        if(mAdConfig.admob_rewarded_video_segments == null) {
            Log.e(TAG, "getAdBlockId: missing admob rv config");
            return null;
        }

        return mAdConfig.admob_rewarded_video_segments.get(segment);
    }

    public boolean isRewardedVideoLoaded() {
        return mRewardedVideoAd != null && mRewardedVideoAd.isLoaded();
    }

    public boolean showRewardedVideo() {
        if(mRewardedVideoAd == null) {
            Logger.v(TAG, "showRewardedVideo: not initialized");
            return false;
        }
        if(!mRewardedVideoAd.isLoaded()) {
            Logger.v(TAG, "showRewardedVideo: not loaded");
            return false;
        }
        mRewardedVideoAd.show();
        return true;
    }

    private static boolean gotRewardedVideoListeners() {
        return sRewardedVideoActivities.size() > 0;
    }

    private int getAdSegment() {
        int segment = AceStreamEngineBaseApplication.getAdSegment();
        return segment == 0 ? getDefaultAdSegment() : segment;
    }

    public int getDefaultAdSegment() {
        return mAdConfig.admob_rewarded_video_default_segment;
    }

    public int getAutoAdSegment() {
        return mAdConfig.admob_rewarded_video_auto_segment;
    }

    public boolean isProviderEnabled(String name) {
        return mAdConfig.isProviderEnabled(name);
    }
}
