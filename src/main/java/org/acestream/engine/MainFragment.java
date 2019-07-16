package org.acestream.engine;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.appodeal.ads.Appodeal;
import com.appodeal.ads.RewardedVideoCallbacks;
import com.google.android.gms.ads.reward.RewardItem;
import com.google.android.gms.ads.reward.RewardedVideoAdListener;

import org.acestream.engine.ads.AdManager;
import org.acestream.engine.ads.AdsWaterfall;
import org.acestream.engine.aliases.App;
import org.acestream.sdk.AceStream;
import org.acestream.sdk.controller.api.response.AdConfig;
import org.acestream.sdk.controller.api.response.AuthData;
import org.acestream.sdk.utils.AuthUtils;
import org.acestream.sdk.utils.Logger;
import org.acestream.sdk.utils.PermissionUtils;
import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MainFragment extends Fragment implements OnClickListener
{
	private final static String TAG = "AS/MainFragment";
    private static final int RC_UNINSTALL = 0;
    private static final int UPGRADE_BUTTON_ROTATE_INTERVAL = 10000;

    enum UserAdsStatus {
        NOT_LOGGED_IN,
        SHOW_ADS,
        SKIP_ADS,
    }

    enum BonusAdsStatus {
        LOADING,
        AVAILABLE,
        NOT_AVAILABLE,
    }

    private FrameLayout btnGoogleSignIn;
    private Button btnGoogleSignOut;
    private Button btnEngineSignIn;
    private Button btnEngineSignOut;
    private Button btnUpgrade;
    private Button btnTopup;
    private Button btnBonuses;
    private Button mBtnGrantPermissions;
    private Button mShowBonusAdsButton;
    private TextView txtUserLogin;
    private TextView txtUserLevel;
    private TextView txtSignInPrompt;
    private TextView txtPackageDaysLeft;
    private View userLevelCircle;
    private View layoutUserLevel;
    private TextView txtUninstallWarning;
    private TextView txtLicenseAgreement;
    private TextView txtRateText;
    private View bottomContainer;
    private Button btnRateYes;
    private Button btnRateNo;
    private Button btnUninstall;
    private MainActivity mainActivity;
    private boolean mHasBrowser = false;
    private boolean mMenuVisible = true;
    private boolean mIsStarted = false;
    private Menu mMenu;
    private Handler mHandler;
    private boolean mShowBonusAdsActivity = false;

    private UserAdsStatus mUserAdsStatus = UserAdsStatus.SHOW_ADS;
    private BonusAdsStatus mBonusAdsStatus = BonusAdsStatus.NOT_AVAILABLE;

    private double mLastLoadedAppodealRewardedVideoCpm = 0;
    private double mLastStartedAppodealRewardedVideoCpm = 0;

    private Runnable mRotateUpgradeButton = new Runnable() {
        @Override
        public void run() {
            // Rotate: upgrade/disable_ads
            String name = (String)btnUpgrade.getTag(R.id.tag_name);
            if(TextUtils.equals(name, "upgrade")) {
                btnUpgrade.setText(R.string.disable_ads);
                btnUpgrade.setTag(R.id.tag_name, "disable_ads");
            }
            else {
                btnUpgrade.setText(R.string.upgrade);
                btnUpgrade.setTag(R.id.tag_name, "upgrade");
            }
            mHandler.removeCallbacks(mRotateUpgradeButton);
            mHandler.postDelayed(mRotateUpgradeButton, UPGRADE_BUTTON_ROTATE_INTERVAL);
        }
    };

    private RewardedVideoAdListener mAdMobRewardedVideoListener = new RewardedVideoAdListener() {
        @Override
        public void onRewardedVideoAdLoaded() {
            Log.d(TAG, "admob:onRewardedVideoLoaded");
            updateBonusAdsStatus();
        }

        @Override
        public void onRewardedVideoAdOpened() {
        }

        @Override
        public void onRewardedVideoStarted() {
        }

        @Override
        public void onRewardedVideoAdClosed() {
            Activity activity = getActivity();
            if(activity != null) {
                AceStream.openBonusAdsActivity(activity);
            }
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
            updateBonusAdsStatus();
        }

        @Override
        public void onRewardedVideoCompleted() {

        }
    };

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mainActivity = (MainActivity) getActivity();
    }

    @Override
    public void onActivityResult(int requestCode, int responseCode, Intent intent) {
        super.onActivityResult(requestCode, responseCode, intent);
        Log.d(TAG, "onActivityResult: requestCode=" + requestCode + " responseCode=" + responseCode);
        if (requestCode == RC_UNINSTALL) {
            if(mainActivity != null) {
                if(!mainActivity.gotPrevVersion()) {
                    mainActivity.startEngine();
                }
            }
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.l_fragment_main, container, false);
		initLayoutElements(view);
		setHasOptionsMenu(true);

        mHandler = new Handler();

        // don't show by default in "core"
        mShowBonusAdsActivity = AceStreamEngineBaseApplication.useVlcBridge();

		return view;
	}

    @Override
    public void onStart() {
        super.onStart();
        mIsStarted = true;
        AdManager.registerRewardedVideoActivity(getActivity());
    }

    @Override
    public void onStop() {
        super.onStop();
        mIsStarted = false;
        AdManager.unregisterRewardedVideoActivity(getActivity());
    }

    @Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        Log.d(TAG, "onCreateOptionsMenu");
        if(!AceStreamEngineBaseApplication.useVlcBridge()) {
            inflater.inflate(R.menu.main, menu);
            super.onCreateOptionsMenu(menu, inflater);
            mMenu = menu;
            showMenu(true);
        }
	}

	private void restoreMenu() {
        if(mMenuVisible) {
            showMenu(true);
        }
    }

	private void showMenu(boolean visible) {
        mMenuVisible = visible;
        if(mMenu != null) {
            for (int i = 0; i < mMenu.size(); i++) {
                mMenu.getItem(i).setVisible(visible);
            }

            if(visible) {
                // There menu items are shown only on some conditions.
                boolean showRemoteControl = false;
                boolean showBonuses = false;
                PlaybackManager pm = getPlaybackManager();
                if (pm != null) {
                    showRemoteControl = pm.shouldShowRemoteControl();
                    showBonuses = !AceStreamEngineBaseApplication.showTvUi() && pm.isUserAuthenticated();
                }

                showBonusesMenu(showBonuses);

                MenuItem menuItem = mMenu.findItem(R.id.action_remote_control);
                if(menuItem != null) {
                    menuItem.setVisible(showRemoteControl);
                }
            }
        }
    }

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
        int i = item.getItemId();
        if (i == R.id.action_remote_control) {
            Intent intent = new Intent(getActivity(), RemoteControlActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
        } else if (i == R.id.action_get_bonuses) {
            PlaybackManager pm = getPlaybackManager();
            if(pm != null) {
                pm.openBonusAdsActivity(getActivity());
            }

        } else if (i == R.id.action_prefs) {
            mainActivity.showPreferences();

        } else if (i == R.id.action_about) {
            mainActivity.showAboutFragment();

        } else if (i == R.id.action_content_id) {
            mainActivity.showContentIdForm();

        } else if (i == R.id.action_profile) {
            mainActivity.showProfile();

        } else if (i == R.id.action_extensions) {
            mainActivity.showServices();

        } else if (i == R.id.action_clear_cache) {
            mainActivity.engineClearCache();

        } else if (i == R.id.action_report_problem) {
            startActivity(new Intent(getActivity(), ReportProblemActivity.class));
        } else if (i == R.id.action_exit) {
            Logger.v(TAG, "stopApp");
            AceStream.stopApp();
        } else {
            return super.onOptionsItemSelected(item);
        }
		return true;
	}

    @Override
    public void onPause() {
        super.onPause();
        Log.v(TAG, "onPause");
        mHandler.removeCallbacks(mRotateUpgradeButton);
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.v(TAG, "onResume");

        restoreMenu();
        updateUI();
        updateBonusAdsStatus();
    }

	@SuppressLint({"SetTextI18n"})
	private void initLayoutElements(View v) {
        mShowBonusAdsButton = v.findViewById(R.id.button_show_bonus_ads);
        btnGoogleSignIn = v.findViewById(R.id.btn_google_sign_in);
        btnGoogleSignOut = v.findViewById(R.id.btn_google_sign_out);
        btnEngineSignIn = v.findViewById(R.id.btn_engine_sign_in);
        btnEngineSignOut = v.findViewById(R.id.btn_engine_sign_out);
        btnUpgrade = v.findViewById(R.id.btn_upgrage);
        btnTopup = v.findViewById(R.id.btn_topup);
        btnBonuses = v.findViewById(R.id.btn_bonuses);
        txtUserLogin = v.findViewById(R.id.txt_user_login);
        txtUserLevel = v.findViewById(R.id.txt_user_level);
        txtSignInPrompt = v.findViewById(R.id.txt_sign_in_prompt);
        txtPackageDaysLeft = v.findViewById(R.id.txt_package_days_left);
        userLevelCircle = v.findViewById(R.id.user_level_circle);
        layoutUserLevel = v.findViewById(R.id.layout_user_level);
        txtUninstallWarning= v.findViewById(R.id.txt_uninstall_warning);
        btnUninstall= v.findViewById(R.id.btn_uninstall);
        btnRateNo = v.findViewById(R.id.btn_rate_no);
        btnRateYes = v.findViewById(R.id.btn_rate_yes);
        txtRateText = v.findViewById(R.id.rate_text);
        txtLicenseAgreement = v.findViewById(R.id.license_agreement);
        bottomContainer = v.findViewById(R.id.bottom_container);
        mBtnGrantPermissions = v.findViewById(R.id.btn_grant_permissions);

        // Button click listeners
        mShowBonusAdsButton.setOnClickListener(this);
        btnGoogleSignIn.setOnClickListener(this);
        btnGoogleSignOut.setOnClickListener(this);
        btnEngineSignIn.setOnClickListener(this);
        btnEngineSignOut.setOnClickListener(this);
        btnUpgrade.setOnClickListener(this);
        btnTopup.setOnClickListener(this);
        btnBonuses.setOnClickListener(this);
        btnUninstall.setOnClickListener(this);
        mBtnGrantPermissions.setOnClickListener(this);

        btnRateNo.setOnClickListener(this);
        btnRateYes.setOnClickListener(this);

        v.findViewById(R.id.logo).setOnClickListener(this);

        // Make links inside agreement clickable
        // (see http://stackoverflow.com/questions/2734270/how-do-i-make-links-in-a-textview-clickable)
        if(mHasBrowser) {
            txtLicenseAgreement.setMovementMethod(LinkMovementMethod.getInstance());
        }
        else {
            txtLicenseAgreement.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    startBrowserIntent("http://acestream.org/about/license");
                }
            });
        }
	}

	/**
     * Button on click listener
     * */
    @Override
    public void onClick(View v) {
        int i1 = v.getId();
        if (i1 == R.id.logo) {
            if (BuildConfig.DEBUG) {
                updateAuth();
            }
            //:debug
            //AceStreamEngineBaseApplication.resetNotification();
            ///debug

        } else if (i1 == R.id.button_show_bonus_ads) {
            showBonusAds();
        } else if (i1 == R.id.btn_grant_permissions) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                PermissionUtils.requestStoragePermissions(mainActivity, MainActivity.REQUEST_CODE_PERMISSIONS);
            } else {
                final Intent i = new Intent("android.settings.APPLICATION_DETAILS_SETTINGS");
                i.addCategory(Intent.CATEGORY_DEFAULT);
                i.setData(Uri.parse("package:" + AceStreamEngineBaseApplication.context().getPackageName()));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    Context context = getActivity();
                    if (context != null) {
                        context.startActivity(i);
                    }
                } catch (Exception ignored) {
                }
            }

        } else if (i1 == R.id.btn_google_sign_in) {
            mainActivity.googleSignIn();

        } else if (i1 == R.id.btn_google_sign_out) {
            mainActivity.signOut();

        } else if (i1 == R.id.btn_engine_sign_in) {
            mainActivity.engineSignIn();

        } else if (i1 == R.id.btn_engine_sign_out) {
            mainActivity.signOut();

        } else if (i1 == R.id.btn_upgrage) {
            AceStreamEngineBaseApplication.showUpgradeForm(getActivity());

        } else if (i1 == R.id.btn_topup) {
            AceStreamEngineBaseApplication.showTopupForm(getActivity());
        } else if (i1 == R.id.btn_bonuses) {
            startBrowserIntent(AceStream.getBackendDomain() + "/shop/bonuses");

        } else if (i1 == R.id.btn_uninstall) {
            Intent intent = AceStreamEngineBaseApplication.getUninstallIntent("org.acestream.engine");
            try {
                startActivityForResult(intent, RC_UNINSTALL);
            } catch (Exception e) {
                Log.e(TAG, "error", e);
            }
        } else if (i1 == R.id.btn_rate_no) {
            RateManager.showRateDialog(getActivity(), 0);
            bottomContainer.setVisibility(View.GONE);
        } else if (i1 == R.id.btn_rate_yes) {
            RateManager.showRateDialog(getActivity(), 1);
            bottomContainer.setVisibility(View.GONE);
        }
    }

    public void onStorageAccessGranted() {
        Log.v(TAG, "onStorageAccessGranted");
        mBtnGrantPermissions.setVisibility(View.GONE);
        showMenu(true);
    }

    public void onStorageAccessDenied() {
        Log.v(TAG, "onStorageAccessDenied");
        mBtnGrantPermissions.setVisibility(View.VISIBLE);
        showMenu(false);
    }

    public void onPlaybackManagerConnected() {
        initAds();
        restoreMenu();
    }

    public void onEngineSettingsUpdated() {
        App.v(TAG, "onEngineSettingsUpdated");
    }

    private void initAds() {
        final Activity activity = getActivity();
        if(activity == null) {
            Log.e(TAG, "initAds: missing activity");
            return;
        }

        if(AceStreamEngineBaseApplication.shouldShowAdMobAds()) {
            PlaybackManager pm = getPlaybackManager();
            if(pm == null) {
                Log.e(TAG, "initAds: missing manager");
                return;
            }

            pm.getAdConfigAsync(new AceStreamManagerImpl.AdConfigCallback() {
                @Override
                public void onSuccess(AdConfig config) {
                    mShowBonusAdsActivity = config.show_bonus_ads_activity;

                    if(config.isProviderEnabled(AdManager.ADS_PROVIDER_ADMOB)) {
                        AdManager adManager = getAdManager();
                        if(adManager != null) {
                            adManager.init(activity);
                            adManager.initRewardedVideo(activity, mAdMobRewardedVideoListener);
                        }
                        else {
                            Log.e(TAG, "initAds: missing ad manager");
                        }
                    }

                    if(config.isProviderEnabled(AdManager.ADS_PROVIDER_APPODEAL)) {
                        appodealInitRewardedVideo();
                        AceStreamEngineBaseApplication.initAppodeal(
                                AceStreamEngineBaseApplication.getAdSegment(),
                                activity,
                                Appodeal.REWARDED_VIDEO,
                                true,
                                config);
                    }
                }
            });
        }
    }

    private void updateUI() {
        if(mainActivity != null) {

            Log.d(TAG, "updateUI: got prev version: " + mainActivity.gotPrevVersion());

            if(mainActivity.gotPrevVersion()) {
                txtUninstallWarning.setVisibility(View.VISIBLE);
                btnUninstall.setVisibility(View.VISIBLE);
            }
            else {
                txtUninstallWarning.setVisibility(View.GONE);
                btnUninstall.setVisibility(View.GONE);

                if(mainActivity.canUpdateUI()) {
                    PlaybackManager pm = getPlaybackManager();
                    if(pm != null) {
                        updateUI(
                                pm.getAuthData(),
                                pm.getEngineLogin(),
                                pm.getGoogleSignedIn(),
                                pm.getGoogleLogin(),
                                pm.isGoogleApiAvailable(),
                                mainActivity.getEngineVersion()
                        );
                    }
                }
            }
        }
    }

    /**
     * Updating the UI, showing/hiding buttons and profile layout
     * */
    public void updateUI(AuthData engineAuthData, String engineLogin, boolean googleSignedIn, String googleLogin, boolean googleApiAvailable, String engineVersion) {
        int authLevel = 0;
        if(engineAuthData != null) {
            authLevel = engineAuthData.auth_level;
        }

        Log.d(TAG, "updateUI: visible=" + isVisible() + " resumed=" + isResumed() + " engine=" + authLevel + "(" + engineLogin + ") googleAvailable=" + googleApiAvailable + " googleSignedIn=" + googleSignedIn + "(" + googleLogin + ")");

        if(!isVisible() || !isResumed()) {
            return;
        }

        if(AceStreamEngineBaseApplication.shouldShowAdMobAds()) {
            if(authLevel == 0) {
                setUserAdsStatus(UserAdsStatus.NOT_LOGGED_IN);
            }
            else {
                if (AuthUtils.hasNoAds(authLevel)) {
                    if(AceStreamEngineBaseApplication.showAdsOnMainScreen()) {
                        setUserAdsStatus(UserAdsStatus.SHOW_ADS);
                    }
                    else {
                        setUserAdsStatus(UserAdsStatus.SKIP_ADS);
                    }
                }
                else {
                    setUserAdsStatus(UserAdsStatus.SHOW_ADS);
                }
            }
        }

        Resources res = getResources();
        if (googleSignedIn) {
            btnGoogleSignIn.setVisibility(View.GONE);
            btnGoogleSignOut.setVisibility(View.VISIBLE);
            btnEngineSignIn.setVisibility(View.GONE);
            btnEngineSignOut.setVisibility(View.GONE);
            txtUserLogin.setText(googleLogin);
            txtUserLogin.setVisibility(View.VISIBLE);
            txtSignInPrompt.setVisibility(View.GONE);
        } else if (authLevel > 0) {
            btnGoogleSignIn.setVisibility(View.GONE);
            btnGoogleSignOut.setVisibility(View.GONE);
            btnEngineSignIn.setVisibility(View.GONE);
            btnEngineSignOut.setVisibility(View.VISIBLE);
            txtUserLogin.setText(engineLogin);
            txtUserLogin.setVisibility(View.VISIBLE);
            txtSignInPrompt.setVisibility(View.GONE);
        } else {
            btnGoogleSignIn.setVisibility(googleApiAvailable ? View.VISIBLE : View.GONE);
            btnGoogleSignOut.setVisibility(View.GONE);
            btnEngineSignIn.setVisibility(View.VISIBLE);
            btnEngineSignOut.setVisibility(View.GONE);
            txtUserLogin.setVisibility(View.GONE);
            txtUserLogin.setText("");
            txtSignInPrompt.setVisibility(View.VISIBLE);

            btnEngineSignIn.requestFocus();
        }

        showBonusesMenu(!AceStreamEngineBaseApplication.showTvUi() && authLevel > 0);

        if(authLevel > 0) {
            if(!TextUtils.isEmpty(engineAuthData.package_name)) {
                txtUserLevel.setText(engineAuthData.package_name);
            }
            else {
                txtUserLevel.setText("");
            }

            // Parse known colors
            int circleBackground = 0;
            if(engineAuthData.package_color != null) {
                switch (engineAuthData.package_color) {
                    case "red":
                        circleBackground = R.drawable.circle_red;
                        break;
                    case "yellow":
                        circleBackground = R.drawable.circle_yellow;
                        break;
                    case "green":
                        circleBackground = R.drawable.circle_green;
                        break;
                    case "blue":
                        circleBackground = R.drawable.circle_blue;
                        break;
                }
            }

            if(circleBackground == 0) {
                // Unknown color. Hide circle.
                userLevelCircle.setVisibility(View.GONE);
            }
            else {
                userLevelCircle.setVisibility(View.VISIBLE);
                userLevelCircle.setBackgroundResource(circleBackground);
            }

            layoutUserLevel.setVisibility(View.VISIBLE);

            if(engineAuthData.package_days_left >= 0) {
                txtPackageDaysLeft.setText(res.getQuantityString(R.plurals.daysLeft, engineAuthData.package_days_left, engineAuthData.package_days_left));
                txtPackageDaysLeft.setVisibility(View.VISIBLE);
            }
            else {
                txtPackageDaysLeft.setText("");
                txtPackageDaysLeft.setVisibility(View.GONE);
            }

            btnUpgrade.setVisibility(View.VISIBLE);

            // Show topup button if the user has non-null balance or one of the paid plans.
            // purse_amount==-1 means that auth data is not initialized yet, so hide button.
            if(engineAuthData.purse_amount != -1 &&
                    (engineAuthData.purse_amount > 0 || engineAuthData.auth_level > 1)) {
                btnTopup.setVisibility(View.VISIBLE);
                btnTopup.setText(res.getString(R.string.topup_button_title, engineAuthData.purse_amount / 100.0));
                btnTopup.requestFocus();
            }
            else {
                btnTopup.setVisibility(View.GONE);
                btnTopup.setText("");
                btnUpgrade.requestFocus();
            }

            int bonuses = engineAuthData.bonus_amount;
            if(bonuses == -1) {
                // -1 means uninitialized auth data. Hide bonuses.
                btnBonuses.setVisibility(View.GONE);
            }
            else {
                PlaybackManager pm = getPlaybackManager();
                if (pm != null) {
                    bonuses += pm.getPendingBonusesAmount();
                }

                btnBonuses.setVisibility(View.VISIBLE);
                btnBonuses.setText(res.getString(R.string.bonuses_button_title, bonuses / 100.0));
            }

            if(AuthUtils.hasNoAds(authLevel)) {
                btnUpgrade.setText(R.string.upgrade);
                btnUpgrade.setTag(R.id.tag_name, "upgrade");
                //btnUpgrade.setTag(R.id.tag_mode, "upgrade");
                mHandler.removeCallbacks(mRotateUpgradeButton);
            }
            else {
                //btnUpgrade.setTag(R.id.tag_mode, "select_option");

                // Initial value 50/50
                if(new Random().nextInt(100) > 50) {
                    btnUpgrade.setText(R.string.disable_ads);
                    btnUpgrade.setTag(R.id.tag_name, "disable_ads");
                }
                else {
                    btnUpgrade.setText(R.string.upgrade);
                    btnUpgrade.setTag(R.id.tag_name, "upgrade");
                }
                mHandler.removeCallbacks(mRotateUpgradeButton);
                mHandler.postDelayed(mRotateUpgradeButton, UPGRADE_BUTTON_ROTATE_INTERVAL);
            }
        }
        else {
            layoutUserLevel.setVisibility(View.GONE);
            txtUserLevel.setText("");
            userLevelCircle.setBackgroundResource(R.drawable.circle_red);

            txtPackageDaysLeft.setText("");
            txtPackageDaysLeft.setVisibility(View.GONE);

            btnUpgrade.setVisibility(View.GONE);
            btnTopup.setVisibility(View.GONE);
            btnTopup.setText("");
            btnBonuses.setVisibility(View.GONE);
            btnBonuses.setText("");
        }

        if(RateManager.shouldRate()) {
            txtLicenseAgreement.setVisibility(View.GONE);
            bottomContainer.setVisibility(View.VISIBLE);
            txtRateText.setVisibility(View.VISIBLE);
            btnRateNo.setVisibility(View.VISIBLE);
            btnRateYes.setVisibility(View.VISIBLE);
        }
        else {
            txtRateText.setVisibility(View.GONE);
            btnRateNo.setVisibility(View.GONE);
            btnRateYes.setVisibility(View.GONE);
            if (googleSignedIn || authLevel > 0) {
                txtLicenseAgreement.setVisibility(View.GONE);
                bottomContainer.setVisibility(View.GONE);
            }
            else {
                txtLicenseAgreement.setVisibility(View.VISIBLE);
                bottomContainer.setVisibility(View.VISIBLE);
            }
        }
    }

    private void startBrowserIntent(String url) {
        AceStreamEngineBaseApplication.startBrowserIntent(getActivity(), url);
    }

    @SuppressWarnings("unused")
    private static class UpdateStaticContentTask extends AsyncTask<Void, Void, Void> {

        private List<String> getFileList() {
            List<String> items = new ArrayList<>();

            HttpURLConnection connection = null;
            try {
                URL url = new URL("http", "acestream.org", 80, "/test/android/static_content.json");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setDoInput(true);

                BufferedReader reader = new BufferedReader(new InputStreamReader( connection.getInputStream() ));
                StringBuilder builder = new StringBuilder();
                String buffer;
                while((buffer = reader.readLine()) != null) {
                    builder.append(buffer);
                }
                reader.close();
                String response = builder.toString();

                // parse
                JSONArray json = new JSONArray(response);
                for(int i = 0; i < json.length(); i++) {
                    items.add(json.getString(i));
                }

            }
            catch(Throwable e) {
                Log.e(TAG, "getFileList", e);
            }
            finally {
                if(connection != null) {
                    connection.disconnect();
                }
            }

            return items;
        }

        private void downloadFile(String sUrl) {
            String baseUrl = "http://acestream.org/test/android/static_content";
            InputStream input = null;
            OutputStream output = null;
            HttpURLConnection connection = null;
            try {
                URL url = new URL(baseUrl + sUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.connect();

                // expect HTTP 200 OK, so we don't mistakenly save error report
                // instead of the file
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    Log.d(TAG, "failed to load files: code=" + connection.getResponseCode());
                    return;
                }

                // download the file
                input = connection.getInputStream();
                String outputPath = AceStream.externalFilesDir();
                if(outputPath == null) {
                    Log.d(TAG, "failed to get external files dir");
                    return;
                }
                outputPath += "/static_content" + sUrl;
                File outputFile = new File(outputPath);
                //noinspection ResultOfMethodCallIgnored
                outputFile.getParentFile().mkdirs();
                output = new FileOutputStream(outputPath);

                byte data[] = new byte[4096];
                long total = 0;
                int count;
                while ((count = input.read(data)) != -1) {
                    // allow canceling with back button
                    if (isCancelled()) {
                        input.close();
                        return;
                    }
                    total += count;
                    output.write(data, 0, count);
                }

                Log.d(TAG, String.format("file downloaded: url=%s path=%s bytes=%d",
                        sUrl,
                        outputPath,
                        total));
            }
            catch (Exception e) {
                Log.e(TAG, "downloadFile", e);
            }
            finally {
                try {
                    if (output != null)
                        output.close();
                    if (input != null)
                        input.close();
                }
                catch (java.io.IOException ignored) {
                }

                if (connection != null)
                    connection.disconnect();
            }
        }

        @Override
        protected Void doInBackground(Void... params) {

            List<String> items = getFileList();
            for(String url: items) {
                downloadFile(url);
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            AceStreamEngineBaseApplication.toast("Static content updated");
        }

    }

    private void appodealInitRewardedVideo() {
        Appodeal.setRewardedVideoCallbacks(new RewardedVideoCallbacks() {
            @Override
            public void onRewardedVideoLoaded(boolean isPrecache) {
                mLastLoadedAppodealRewardedVideoCpm = Appodeal.getPredictedEcpm(Appodeal.REWARDED_VIDEO);
                Log.d(TAG, "appodeal:onRewardedVideoLoaded: isPrecache=" + isPrecache + " cpm=" + mLastLoadedAppodealRewardedVideoCpm);
                updateBonusAdsStatus();
            }

            @Override
            public void onRewardedVideoFailedToLoad() {
                Log.d(TAG, "appodeal:onRewardedVideoFailedToLoad");
                updateBonusAdsStatus();
            }

            @Override
            public void onRewardedVideoShown() {
                mLastStartedAppodealRewardedVideoCpm = mLastLoadedAppodealRewardedVideoCpm;
                Log.d(TAG, "appodeal:onRewardedVideoShown: cpm=" + mLastStartedAppodealRewardedVideoCpm);
            }

            @Override
            public void onRewardedVideoFinished(double amount, String name) {
                int realAmount = (int)Math.round(mLastStartedAppodealRewardedVideoCpm * 100);
                Log.d(TAG, "appodeal:onRewardedVideoFinished: cpm=" + mLastStartedAppodealRewardedVideoCpm + " amount=" + amount + " real=" + realAmount + " name=" + name);

                String source = AceStreamEngineBaseApplication.getBonusSource(realAmount);
                addCoins(
                        source,
                        realAmount,
                        false);

                Bundle params = new Bundle();
                params.putString("source", source);
                AceStreamEngineBaseApplication.getInstance().logAdImpression(
                        AdManager.ADS_PROVIDER_APPODEAL,
                        AdsWaterfall.Placement.MAIN_SCREEN,
                        AdsWaterfall.AdType.REWARDED_VIDEO,
                        params);

                AceStreamEngineBaseApplication.getInstance().logAdImpressionBonusesScreen(
                        AdManager.ADS_PROVIDER_APPODEAL,
                        AdsWaterfall.AdType.REWARDED_VIDEO);
            }

            @Override
            public void onRewardedVideoClosed(boolean finished) {
                Log.d(TAG, "appodeal:onRewardedVideoClosed");
                Activity activity = getActivity();
                if(activity != null) {
                    AceStream.openBonusAdsActivity(activity);
                }
            }

            @Override
            public void onRewardedVideoExpired() {
                Log.d(TAG, "appodeal:onRewardedVideoExpired");
                updateBonusAdsStatus();
            }
        });
    }

    private void showBonusAds() {
        Log.v(TAG, "showBonusAds: userStatus=" + mUserAdsStatus);

        if(mUserAdsStatus == UserAdsStatus.NOT_LOGGED_IN) {
            PlaybackManager pm = getPlaybackManager();
            if (pm != null) {
                pm.openBonusAdsActivity(mainActivity);
            }
        }
        else {
            AdManager adManager = getAdManager();
            if(adManager != null && adManager.isRewardedVideoLoaded()) {
                Logger.v(TAG, "showBonusAds: admob");
                adManager.showRewardedVideo();
            }
            else if (Appodeal.isLoaded(Appodeal.REWARDED_VIDEO) && Appodeal.canShow(Appodeal.REWARDED_VIDEO, AdsWaterfall.Placement.MAIN_SCREEN)) {
                Logger.v(TAG, "showBonusAds: appodeal");
                Activity activity = getActivity();
                if(activity != null) {
                    Appodeal.show(activity, Appodeal.REWARDED_VIDEO, AdsWaterfall.Placement.MAIN_SCREEN);
                }
                else {
                    Logger.v(TAG, "showBonusAds: appodeal: missing activity");
                }
            }
            else {
                Logger.v(TAG, "showBonusAds: ad is not loaded");
            }
            updateBonusAdsStatus();
        }
    }

    private void updateAuth() {
        PlaybackManager pm = getPlaybackManager();
        if(pm == null) {
            return;
        }
        pm.updateAuthIfExpired(1000);
    }

    private void addCoins(String source, int amount, boolean needNoAds) {
        if(mainActivity != null) {
            mainActivity.addCoins(source, amount, needNoAds);
        }
    }

    private void setUserAdsStatus(UserAdsStatus status) {
        Logger.v(TAG, "setUserAdsStatus: status=" + status);
        mUserAdsStatus = status;
        updateAds();
    }

    private void updateBonusAdsStatus() {
        Logger.v(TAG, "updateBonusAdsStatus: started=" + mIsStarted);

        // Do nothing is activity is stopped
        if(!mIsStarted) return;

        if (!AceStreamEngineBaseApplication.showTvUi()) {
            AdManager adManager = getAdManager();
            if (adManager != null && adManager.isRewardedVideoLoaded()) {
                Logger.v(TAG, "updateBonusAdsStatus:admob: ads are loaded");
                mBonusAdsStatus = BonusAdsStatus.AVAILABLE;
            } else if (Appodeal.isLoaded(Appodeal.REWARDED_VIDEO)) {
                if (Appodeal.canShow(Appodeal.REWARDED_VIDEO, AdsWaterfall.Placement.MAIN_SCREEN)) {
                    Logger.v(TAG, "updateBonusAdsStatus:appodeal: ads are loaded and can show");
                    mBonusAdsStatus = BonusAdsStatus.AVAILABLE;
                } else {
                    Logger.v(TAG, "updateBonusAdsStatus:appodeal: ads are loaded, but cannot show");
                    mBonusAdsStatus = BonusAdsStatus.NOT_AVAILABLE;
                }
            } else {
                Logger.v(TAG, "updateBonusAdsStatus: ads are not loaded");
                mBonusAdsStatus = BonusAdsStatus.NOT_AVAILABLE;
            }
        }

        updateAds();
    }

    private void updateAds() {
        boolean showButton;

        if(!mShowBonusAdsActivity) {
            showButton = false;
        }
        else if(AceStreamEngineBaseApplication.showTvUi()) {
            showButton = false;
        }
        else if(mUserAdsStatus == UserAdsStatus.NOT_LOGGED_IN) {
            showButton = true;
        }
        else if(mUserAdsStatus == UserAdsStatus.SKIP_ADS) {
            showButton = false;
        }
        else {
            switch (mBonusAdsStatus) {
                case LOADING:
                    showButton = false;
                    break;
                case AVAILABLE:
                    showButton = true;
                    break;
                case NOT_AVAILABLE:
                    showButton = false;
                    break;
                default:
                    throw new IllegalStateException("unknown status: " + mBonusAdsStatus);
            }
        }

        mShowBonusAdsButton.setVisibility(showButton ? View.VISIBLE : View.GONE);
    }

    private void showBonusesMenu(boolean visible) {
        if(!mShowBonusAdsActivity) {
            visible = false;
        }

        if(mMenu != null) {
            MenuItem menuItem = mMenu.findItem(R.id.action_get_bonuses);
            if (menuItem != null) {
                menuItem.setVisible(visible);
            }
        }
    }

    private PlaybackManager getPlaybackManager() {
        return mainActivity == null ? null : mainActivity.getPlaybackManager();
    }

    private AdManager getAdManager() {
        PlaybackManager pm = getPlaybackManager();
        return pm == null ? null : pm.getAdManager();
    }
}
