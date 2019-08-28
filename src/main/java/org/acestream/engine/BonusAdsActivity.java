package org.acestream.engine;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.gms.ads.reward.RewardItem;
import com.google.android.gms.ads.reward.RewardedVideoAdListener;

import org.acestream.engine.ads.AdManager;
import org.acestream.engine.ads.AdsWaterfall;
import org.acestream.sdk.AceStream;
import org.acestream.sdk.controller.api.response.AdConfig;
import org.acestream.sdk.controller.api.response.AuthData;
import org.acestream.sdk.utils.Logger;
import org.acestream.sdk.utils.Workers;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.pollfish.classes.SurveyInfo;
import com.pollfish.interfaces.PollfishClosedListener;
import com.pollfish.interfaces.PollfishCompletedSurveyListener;
import com.pollfish.interfaces.PollfishOpenedListener;
import com.pollfish.interfaces.PollfishReceivedSurveyListener;
import com.pollfish.interfaces.PollfishSurveyNotAvailableListener;
import com.pollfish.interfaces.PollfishUserNotEligibleListener;
import com.pollfish.interfaces.PollfishUserRejectedSurveyListener;
import com.pollfish.main.PollFish;
import com.pollfish.main.PollFish.ParamsBuilder;

public class BonusAdsActivity
    extends
        PlaybackManagerAppCompatActivity
    implements
        OnClickListener,
        PlaybackManager.AuthCallback,
        PollfishCompletedSurveyListener,
        PollfishReceivedSurveyListener,
        PollfishOpenedListener,
        PollfishClosedListener,
        PollfishSurveyNotAvailableListener,
        PollfishUserNotEligibleListener,
        PollfishUserRejectedSurveyListener
{

    private final static String TAG = "AS/BonusAds";

    private static final int UPDATE_AUTH_INTERVAL = 300000;

    enum BonusAdsStatus {
        REQUESTING,
        AVAILABLE,
        NOT_AVAILABLE,
        LOADING,
    }

    private boolean mIsStarted = false;
    private Button mShowBonusAdsButton;
    private Button mNoBonusAdsButton;
    private Button mBonusesButton;
    private Button mSelectSegmentButton;
    private Button mStartSurveyButton;
    private Button mNoSurveyButton;
    private TextView mBonusesLabel;
    private Handler mHandler = new Handler();
    private BonusAdsStatus mBonusAdsStatus = BonusAdsStatus.REQUESTING;
    private BonusAdsStatus mSurveyStatus = BonusAdsStatus.REQUESTING;
    private boolean mSurveyCached = false;
    private boolean mPollfishEnabled = true;

    private Runnable mUpdateAuthTask = new Runnable() {
        @Override
        public void run() {
            if(mPlaybackManager != null) {
                if(!mPlaybackManager.isAuthInProgress()) {
                    mPlaybackManager.updateAuthIfExpired(UPDATE_AUTH_INTERVAL);
                }
            }
            mHandler.postDelayed(mUpdateAuthTask, UPDATE_AUTH_INTERVAL);
        }
    };

    private SharedPreferences.OnSharedPreferenceChangeListener mPrefsListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if(TextUtils.equals(key, Constants.PREF_KEY_AD_SEGMENT)) {
                updateUserSegment();
            }
        }
    };

    private RewardedVideoAdListener mAdMobRewardedVideoListener = new RewardedVideoAdListener() {
        @Override
        public void onRewardedVideoAdLoaded() {
            Log.d(TAG, "admob:onRewardedVideoLoaded");
            updateAdsStatus(false);
        }

        @Override
        public void onRewardedVideoAdOpened() {
            // we show "not available" when loading in background
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    updateAdsStatus(true);
                }
            }, 1000);
        }

        @Override
        public void onRewardedVideoStarted() {

        }

        @Override
        public void onRewardedVideoAdClosed() {
            updateAdsStatus(false);
        }

        @Override
        public void onRewarded(RewardItem rewardItem) {
            Log.d(TAG, "admob:onRewardedVideoFinished: amount=" + rewardItem.getAmount() + " name=" + rewardItem.getType());

            String source = AceStreamEngineBaseApplication.getBonusSource(rewardItem.getAmount());
            addCoins(
                source,
                rewardItem.getAmount(),
                false);

            Bundle params = new Bundle();
            params.putString("source", source);
            AceStreamEngineBaseApplication.getInstance().logAdImpression(
                    AdManager.ADS_PROVIDER_ADMOB,
                    AdsWaterfall.Placement.MAIN_SCREEN,
                    AdsWaterfall.AdType.REWARDED_VIDEO,
                    params);

            AceStreamEngineBaseApplication.getInstance().logAdImpressionBonusesScreen(
                    AdManager.ADS_PROVIDER_ADMOB,
                    AdsWaterfall.AdType.REWARDED_VIDEO);
        }

        @Override
        public void onRewardedVideoAdLeftApplication() {

        }

        @Override
        public void onRewardedVideoAdFailedToLoad(int i) {
        }

        @Override
        public void onRewardedVideoCompleted() {

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.bonus_ads_activity);

        // enable ActionBar app icon to behave as action to toggle nav drawer
        setSupportActionBar((Toolbar)findViewById(R.id.main_toolbar));

        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeButtonEnabled(true);
            actionBar.setBackgroundDrawable(new ColorDrawable(ContextCompat.getColor(
                    this, R.color.main_accent)));
        }

        mShowBonusAdsButton = findViewById(R.id.btn_show_bonus_ads);
        mNoBonusAdsButton = findViewById(R.id.btn_no_bonus_ads);
        mBonusesButton = findViewById(R.id.btn_bonuses);
        mBonusesLabel = findViewById(R.id.lbl_bonuses);
        mSelectSegmentButton = findViewById(R.id.btn_select_segment);
        mStartSurveyButton = findViewById(R.id.btn_start_survey);
        mNoSurveyButton = findViewById(R.id.btn_no_survey);

        mShowBonusAdsButton.setOnClickListener(this);
        mNoBonusAdsButton.setOnClickListener(this);
        mBonusesButton.setOnClickListener(this);
        mSelectSegmentButton.setOnClickListener(this);
        mStartSurveyButton.setOnClickListener(this);
        mNoSurveyButton.setOnClickListener(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mIsStarted = true;
        AceStreamEngineBaseApplication.getInstance().logEvent("bonus_ads_open", null);
        AceStreamEngineBaseApplication
                .getPreferences()
                .registerOnSharedPreferenceChangeListener(mPrefsListener);
        AdManager.registerRewardedVideoActivity(this);
    }

    @Override
    public void onConnected(PlaybackManager service) {
        super.onConnected(service);
        initAds();
        updateAdsStatus(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUserSegment();

        // Always request survey on resume.
        // Cannot use previously cached survey, it just doesn't open.
        requestSurvey();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mIsStarted = false;
        AceStreamEngineBaseApplication.getInstance().logEvent("bonus_ads_close", null);
        AceStreamEngineBaseApplication
                .getPreferences()
                .unregisterOnSharedPreferenceChangeListener(mPrefsListener);
        AdManager.unregisterRewardedVideoActivity(this);
        PollFish.hide();
    }

    private void initAds() {
        if(mPlaybackManager == null) {
            throw new IllegalStateException("missing manager");
        }

        mPlaybackManager.getAdConfigAsync(new AceStreamManagerImpl.AdConfigCallback() {
            @Override
            public void onSuccess(AdConfig config) {
                if(!config.show_bonus_ads_activity) {
                    Log.d(TAG, "bonus ads are disabled");
                    finish();
                    return;
                }

                if(config.isProviderEnabled(AdManager.ADS_PROVIDER_ADMOB)) {
                    AdManager adManager = getAdManager();
                    if(adManager != null) {
                        adManager.init(BonusAdsActivity.this);
                        adManager.initRewardedVideo(BonusAdsActivity.this, mAdMobRewardedVideoListener);
                    }
                    else {
                        Log.e(TAG, "initAds: missing ad manager");
                    }
                }

                if(!config.isProviderEnabled(AdManager.ADS_PROVIDER_POLLFISH)) {
                    disablePollfish();
                }
            }
        });
    }

    private void updateAdsStatus(boolean showLoading) {
        AdManager adManager = getAdManager();
        if(adManager != null && adManager.isRewardedVideoLoaded()) {
            Logger.v(TAG, "updateAdsStatus:admob: ads are loaded");
            onBonusAdsAvailable(BonusAdsStatus.AVAILABLE);
            return;
        }

        Logger.v(TAG, "updateAdsStatus: ads are not loaded: showLoading=" + showLoading);

        // Show "requesting" status for 5 seconds, than change it to "unavailable"
        if(showLoading) {
            onBonusAdsAvailable(BonusAdsStatus.REQUESTING);
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    updateAdsStatus(false);
                }
            }, 5000);
        }
        else {
            onBonusAdsAvailable(BonusAdsStatus.NOT_AVAILABLE);
        }
    }

    private void updateSurveyStatus(boolean showLoading) {
        if(!mPollfishEnabled) {
            return;
        }

        Logger.v(TAG, "updateSurveyStatus: showLoading=" + showLoading + " cached=" + mSurveyCached);
        if(mSurveyCached) {
            setSurveyStatus(BonusAdsStatus.AVAILABLE);
            return;
        }

        // Show "requesting" status for 5 seconds, than change it to "unavailable"
        if(showLoading) {
            setSurveyStatus(BonusAdsStatus.REQUESTING);
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    updateSurveyStatus(false);
                }
            }, 5000);
        }
        else {
            setSurveyStatus(BonusAdsStatus.NOT_AVAILABLE);
        }
    }

    private void onBonusAdsAvailable(BonusAdsStatus status) {
        Logger.v(TAG, "onBonusAdsAvailable: status=" + status + " started=" + mIsStarted);

        mBonusAdsStatus = status;

        // Do nothing is activity is stopped
        if(!mIsStarted) return;

        boolean available;
        String reason;

        switch(status) {
            case REQUESTING:
                available = false;
                reason = getResources().getString(R.string.ads_are_requested);
                break;
            case LOADING:
                available = false;
                reason = getResources().getString(R.string.ads_are_loaded);
                break;
            case AVAILABLE:
                available = true;
                reason = "";
                break;
            case NOT_AVAILABLE:
                available = false;
                reason = getResources().getString(R.string.ads_are_missing_please_wait);
                break;
            default:
                throw new IllegalStateException("unknown status: " + status);
        }

        mShowBonusAdsButton.setVisibility(available ? View.VISIBLE : View.GONE);
        mNoBonusAdsButton.setVisibility(available ? View.GONE : View.VISIBLE);
        mNoBonusAdsButton.setText(reason);

        mNoBonusAdsButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        if(mNoBonusAdsButton.getLineCount() > 1) {
            // Use smaller text if doesn't fit into one line.
            mNoBonusAdsButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        }
    }

    private void setSurveyStatus(BonusAdsStatus status) {
        if(!mPollfishEnabled) {
            return;
        }

        Logger.v(TAG, "setSurveyStatus: status=" + status + " started=" + mIsStarted);

        mSurveyStatus = status;

        // Do nothing is activity is stopped
        if(!mIsStarted) return;

        boolean available;
        String reason;

        switch(status) {
            case REQUESTING:
                available = false;
                reason = getResources().getString(R.string.survey_is_requested);
                break;
            case LOADING:
                available = false;
                reason = getResources().getString(R.string.survey_is_loaded);
                break;
            case AVAILABLE:
                available = true;
                reason = "";
                break;
            case NOT_AVAILABLE:
                available = false;
                reason = getResources().getString(R.string.survey_is_missing_please_wait);
                break;
            default:
                throw new IllegalStateException("unknown status: " + status);
        }

        mStartSurveyButton.setVisibility(available ? View.VISIBLE : View.GONE);
        mNoSurveyButton.setVisibility(available ? View.GONE : View.VISIBLE);
        mNoSurveyButton.setText(reason);

        mNoSurveyButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        if(mNoSurveyButton.getLineCount() > 1) {
            // Use smaller text if doesn't fit into one line.
            mNoSurveyButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        }
    }

    @Override
    public void onClick(View v) {
        int i = v.getId();
        if (i == R.id.btn_bonuses) {
            AceStreamEngineBaseApplication.startBrowserIntent(this,
                    AceStream.getBackendDomain() + "/shop/bonuses");
        }
        else if (i == R.id.btn_show_bonus_ads) {
            showBonusAds();
        }
        else if (i == R.id.btn_no_bonus_ads) {
            requestAds();
        }
        else if (i == R.id.btn_select_segment) {
            selectSegment();
        }
        else if (i == R.id.btn_start_survey) {
            showSurvey();
        }
        else if (i == R.id.btn_no_survey) {
            requestSurvey();
        }
    }

    @Override
    public void onAuthUpdated(final AuthData authData) {
        Log.v(TAG, "onAuthUpdated");
        updateUi();
    }

    @Override
    public void onResumeConnected() {
        super.onResumeConnected();
        mPlaybackManager.addAuthCallback(this);
        mHandler.post(mUpdateAuthTask);
        updateUi();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(mPlaybackManager != null) {
            mPlaybackManager.removeAuthCallback(this);
        }

        mHandler.removeCallbacks(mUpdateAuthTask);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean onSupportNavigateUp() {
        Log.d(TAG, "onSupportNavigateUp");
        if(!super.onSupportNavigateUp()) {
            finish();
        }
        return true;
    }

    private void updateUi() {
        Workers.runOnMainThread(new Runnable() {
            @Override
            public void run() {
                if(mPlaybackManager != null) {
                    if(!mPlaybackManager.isUserAuthenticated()) {
                        startActivity(new Intent(BonusAdsActivity.this,
                                AceStreamEngineBaseApplication.getMainActivityClass()));
                        finish();
                    }
                    else {
                        final AuthData authData = mPlaybackManager.getAuthData();
                        int bonuses = authData.bonus_amount;
                        if(bonuses == -1) {
                            // -1 means uninitialized auth data. Hide bonuses.
                            mBonusesLabel.setVisibility(View.GONE);
                            mBonusesButton.setVisibility(View.GONE);
                        }
                        else {
                            bonuses += mPlaybackManager.getPendingBonusesAmount();

                            mBonusesLabel.setVisibility(View.VISIBLE);
                            mBonusesButton.setVisibility(View.VISIBLE);
                            mBonusesButton.setText(getResources().getString(
                                    R.string.bonuses_button_title, bonuses / 100.0));
                        }
                    }
                }
            }
        });
    }

    protected void addCoins(String source, int amount, boolean needNoAds) {
        Log.v(TAG, "addCoins: source=" + source + " amount=" + amount + " needNoAds=" + needNoAds);
        if (mPlaybackManager != null) {
            mPlaybackManager.addCoins(source, amount, needNoAds);
            updateUi();
        }
    }

    private void showBonusAds() {
        AdManager adManager = getAdManager();
        if(adManager != null && adManager.isRewardedVideoLoaded()) {
            Logger.v(TAG, "showBonusAds: admob");
            adManager.showRewardedVideo();
        } else {
            onBonusAdsAvailable(BonusAdsStatus.NOT_AVAILABLE);
        }
    }

    private void requestAds() {
        if(mBonusAdsStatus != BonusAdsStatus.REQUESTING) {
            updateAdsStatus(true);
        }
    }

    private void selectSegment() {
        final int[] optionIds = AceStreamEngineBaseApplication.getAdSegmentIds();
        final String[] optionNames = AceStreamEngineBaseApplication.getAdSegmentNames();

        int listPosition = 0;
        int currentSegment = AceStreamEngineBaseApplication.getAdSegment();
        for(int i = 0; i < optionIds.length; i++) {
            if(optionIds[i] == currentSegment) {
                listPosition = i;
                break;
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.set_ads_price)
                .setSingleChoiceItems(optionNames, listPosition, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int listPosition) {
                        AceStreamEngineBaseApplication.setAdSegment(optionIds[listPosition]);
                        dialog.dismiss();
                    }
                })
                .create();
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOwnerActivity(this);
        dialog.show();
    }

    private void updateUserSegment() {
        mSelectSegmentButton.setText(AceStreamEngineBaseApplication.getAdSegmentName());
    }

    private AdManager getAdManager() {
        return mPlaybackManager == null ? null : mPlaybackManager.getAdManager();
    }

    // BEGIN pollfish
    private void initPollfish() {
        if(!mPollfishEnabled) {
            return;
        }

        String pollfishApiKey = AceStreamEngineBaseApplication.getStringAppMetadata("pollfishApiKey");
        if(TextUtils.isEmpty(pollfishApiKey)) {
            disablePollfish();
            return;
        }

        ParamsBuilder paramsBuilder = new ParamsBuilder(pollfishApiKey)
                .rewardMode(true)
                .offerWallMode(true)
                .build();
        PollFish.initWith(this, paramsBuilder);
    }

    @Override
    public void onPollfishClosed() {
        Log.d(TAG, "onPollfishClosed");
    }

    @Override
    public void onPollfishOpened() {
        Log.d(TAG, "onPollfishOpened");
        AceStreamEngineBaseApplication.getInstance().logEvent(
                AceStreamEngineBaseApplication.ANALYTICS_EVENT_POLLFISH_OPENED,
                null);
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateSurveyStatus(true);
            }
        }, 1000);
    }

    @Override
    public void onPollfishSurveyNotAvailable() {
        Log.d(TAG, "onPollfishSurveyNotAvailable");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setSurveyStatus(BonusAdsStatus.NOT_AVAILABLE);
            }
        });
    }

    @Override
    public void onUserNotEligible() {
        Log.d(TAG, "onUserNotEligible");
        AceStreamEngineBaseApplication.getInstance().logEvent(
                AceStreamEngineBaseApplication.ANALYTICS_EVENT_POLLFISH_NOT_ELIGIBLE,
                null);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                requestSurvey();
                //TODO: implement user notification
            }
        });

    }

    @Override
    public void onUserRejectedSurvey() {
        AceStreamEngineBaseApplication.getInstance().logEvent(
                AceStreamEngineBaseApplication.ANALYTICS_EVENT_POLLFISH_RECEIVED,
                null);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                requestSurvey();
            }
        });
    }

    @Override
    public void onPollfishSurveyCompleted(final SurveyInfo surveyInfo) {
        Log.v(TAG, "onPollfishSurveyCompleted: " + surveyInfo.toString());
        AceStreamEngineBaseApplication.getInstance().logEvent(
                AceStreamEngineBaseApplication.ANALYTICS_EVENT_POLLFISH_COMPLETED,
                null);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                int amount = surveyInfo.getRewardValue() * 1000;
                if(amount > 0) {
                    addCoins("pollfish", amount, false);
                }

                requestSurvey();
            }
        });
    }

    @Override
    public void onPollfishSurveyReceived(SurveyInfo surveyInfo) {
        Log.v(TAG, "onPollfishSurveyReceived");
        AceStreamEngineBaseApplication.getInstance().logEvent(
                AceStreamEngineBaseApplication.ANALYTICS_EVENT_POLLFISH_RECEIVED,
                null);
        mSurveyCached = true;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                setSurveyStatus(BonusAdsStatus.AVAILABLE);
            }
        });
    }

    private void showSurvey() {
        Logger.v(TAG, "showSurvey: present=" + PollFish.isPollfishPresent());
        if(PollFish.isPollfishPresent()) {
            setSurveyStatus(BonusAdsStatus.LOADING);
            PollFish.show();
            mSurveyCached = false;
        }
        else {
            setSurveyStatus(BonusAdsStatus.NOT_AVAILABLE);
        }
    }

    private void requestSurvey() {
        Logger.v(TAG, "requestSurvey");
        mSurveyCached = false;
        updateSurveyStatus(true);
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if(mIsStarted) {
                    initPollfish();
                }
            }
        }, 100);

    }

    private void disablePollfish() {
        Logger.v(TAG, "disablePollfish");

        mPollfishEnabled = false;
        mStartSurveyButton.setVisibility(View.GONE);
        mNoSurveyButton.setVisibility(View.GONE);
    }
    // END pollfish
}
