package org.acestream.engine;

import org.acestream.engine.aliases.App;
import org.acestream.engine.controller.ExtendedEngineApi;
import org.acestream.engine.maintain.MaintainTask;
import org.acestream.engine.prefs.ExtendedEnginePreferences;
import org.acestream.engine.prefs.NotificationData;
import org.acestream.engine.python.PyEmbedded;
import org.acestream.engine.ui.AboutFragment;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.os.RemoteException;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.content.Intent;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.internal.NavigationMenuView;
import com.google.android.material.navigation.NavigationView;

import org.acestream.sdk.AceStream;
import org.acestream.sdk.controller.EngineApi;
import org.acestream.sdk.controller.api.AceStreamPreferences;
import org.acestream.sdk.controller.api.response.AuthData;
import org.acestream.sdk.helpers.SettingDialogFragmentCompat;
import org.acestream.sdk.interfaces.IHttpAsyncTaskListener;
import org.acestream.sdk.utils.AuthUtils;
import org.acestream.sdk.utils.HttpAsyncTask;
import org.acestream.sdk.utils.Logger;
import org.acestream.sdk.utils.MiscUtils;
import org.acestream.sdk.utils.PermissionUtils;
import org.acestream.sdk.utils.VlcBridge;
import org.acestream.sdk.utils.Workers;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class MainActivity
        extends PlaybackManagerAppCompatActivity
        implements
        FragmentManager.OnBackStackChangedListener,
        IHttpAsyncTaskListener,
        PlaybackManager.AuthCallback,
        PlaybackManager.EngineSettingsCallback,
        SettingDialogFragmentCompat.SettingDialogListener,
        PlaybackManager.Callback,
        NavigationView.OnNavigationItemSelectedListener
{

	private final static String TAG = "AS/Main";

    public final static int REQUEST_CODE_SIGN_IN = 1;
    public final static int REQUEST_CODE_ADS_NOTIFICATION = 2;
    public final static int REQUEST_CODE_PERMISSIONS = 3;

	// update frequently when in main form
    private static final int UPDATE_AUTH_INTERVAL = 300000;
    private static final int UPGRADE_BUTTON_ROTATE_INTERVAL = 10000;

    private boolean mEngineStarted = false;
    private boolean mEngineWasStarted = false;
    private boolean mEnginePrefsReceived = false;
    private int mGotStorageAccess = -1;

    private String mEngineVersion = null;

    private HttpAsyncTask.Factory mHttpAsyncTaskFactory = null;
    protected EngineApi mEngineService = null;
    private boolean mStarted = false;
    private boolean mNotificationShown = false;

    private Handler mHandler = new Handler();

    private Runnable mRotateUpgradeButton = new Runnable() {
        @Override
        public void run() {
            // Rotate: upgrade/disable_ads
            if(mAccountUpgradeText != null) {
                String name = (String) mAccountUpgradeText.getTag(R.id.tag_name);
                if (TextUtils.equals(name, "upgrade")) {
                    mAccountUpgradeText.setText(R.string.disable_ads);
                    mAccountUpgradeText.setTag(R.id.tag_name, "disable_ads");
                    mAccountUpgradeButton.setImageDrawable(
                            getResources().getDrawable(R.drawable.ace_ic_no_ads));
                } else {
                    mAccountUpgradeText.setText(R.string.upgrade);
                    mAccountUpgradeText.setTag(R.id.tag_name, "upgrade");
                    mAccountUpgradeButton.setImageDrawable(
                            getResources().getDrawable(R.drawable.ace_ic_upgrade));
                }
            }
            mHandler.removeCallbacks(mRotateUpgradeButton);
            mHandler.postDelayed(mRotateUpgradeButton, UPGRADE_BUTTON_ROTATE_INTERVAL);
        }
    };

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

    @MainThread
    private boolean checkRedirect(Intent callingIntent) {
        if(AceStream.isAndroidTv()) {
            return false;
        }

        if(mPlaybackManager == null) {
            return false;
        }

        if(!mPlaybackManager.isEngineSessionStarted()) {
            return false;
        }

        if(!mPlaybackManager.shouldShowRemoteControl()) {
            return false;
        }

        if(callingIntent == null) {
            return false;
        }

        if(callingIntent.getBooleanExtra("skip_redirect", false)) {
            return false;
        }

        Log.d(TAG, "checkRedirect: redirect to remote control");
        Intent intent = new Intent(this, RemoteControlActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
        finish();

        return true;
    }

    @Override
	protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AceStreamEngineBaseApplication.detectVlcBridge();

        String targetApp = AceStream.getTargetApp();
        if (targetApp != null) {
            // redirect intent
            if(AceStreamEngineBaseApplication.redirectIntent(this, getIntent(), targetApp)) {
                finish();
                return;
            }
        }

        final Toolbar toolbar;
        if (AceStreamEngineBaseApplication.showTvUi()) {
            setTheme(R.style.AppThemeTv);
            setContentView(R.layout.l_activity_main_tv);
            toolbar = findViewById(R.id.actionBar);
        } else {
            setTheme(R.style.AppTheme);
            setContentView(R.layout.l_activity_main);
            toolbar = findViewById(R.id.main_toolbar);
        }

        setSupportActionBar(toolbar);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            if (showDrawer()) {
                actionBar.setDisplayHomeAsUpEnabled(true);
                actionBar.setHomeButtonEnabled(true);
            } else {
                actionBar.setHomeButtonEnabled(false);
            }
        }

        if (showDrawer()) {
            setupNavigationView();
        }
        else {
            DrawerLayout drawerLayout = findViewById(R.id.drawer_layout);
            if(drawerLayout != null) {
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
            }
        }

        printDebugInfo();
        showMainFragment();

        getSupportFragmentManager().addOnBackStackChangedListener(this);
	}

	private boolean showDrawer() {
        return !AceStreamEngineBaseApplication.showTvUi() && AceStreamEngineBaseApplication.useVlcBridge();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        if(mDrawerToggle != null) {
            // Sync the toggle state after onRestoreInstanceState has occurred.
            mDrawerToggle.syncState();
        }
    }

    public boolean gotPrevVersion() {
        return AceStream.isAppInstalled("org.acestream.engine");
    }

    @Override
    public boolean onSupportNavigateUp() {
        Log.d(TAG, "onSupportNavigateUp");
        super.onSupportNavigateUp();
        if(!goBack()) {
            getSupportFragmentManager().popBackStack();
        }
        return true;
    }

    @Override
    public void onBackStackChanged() {
        ActionBar actionBar = getSupportActionBar();
        if(actionBar == null) {
            return;
        }

        int stackSize = getSupportFragmentManager().getBackStackEntryCount();
        Log.d(TAG, "onBackStackChanged: size=" + stackSize);

        if(stackSize == 0){
            actionBar.setDisplayHomeAsUpEnabled(false);
            actionBar.setHomeButtonEnabled(false);
            actionBar.setDisplayShowHomeEnabled(true);
            setTitle(R.string.app_title);
        }
        else {
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(false);
        }
    }

    @Override
    public void onBackPressed() {
        if(!goBack()) {
            super.onBackPressed();
        }
    }

    /**
     * Perform "go back" action.
     *
     * @return true if default action should be prevented
     */
    private boolean goBack() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_holder);
        if (fragment != null) {
            if (fragment instanceof SettingsFragment) {
                if(((SettingsFragment) fragment).goBack()) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "main:onNewIntent: action=" + intent.getAction() + " type=" + intent.getType() + " uri=" + String.valueOf(intent.getData()));
        setIntent(intent);
        if(checkRedirect(intent)) {
            return;
        }
    }

    @Override
	public void onPause() {
	    Log.d(TAG, "main:onPause");
		super.onPause();
		if(mPlaybackManager != null) {
            mPlaybackManager.removeCallback(this);
            mPlaybackManager.removeAuthCallback(this);
            mPlaybackManager.removeEngineSettingsCallback(this);
        }

        mHandler.removeCallbacks(mUpdateAuthTask);
        mHandler.removeCallbacks(mRotateUpgradeButton);
	}

	@Override
	public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: engineStarted=" + mEngineStarted);

        // Additional check on resume: access can be granted from system settings
        if(mGotStorageAccess != 1 && PermissionUtils.hasStorageAccess()) {
            onStorageAccessGranted();
        }
        else if(mGotStorageAccess != 0 && !PermissionUtils.hasStorageAccess()) {
            onStorageAccessDenied();
        }
	}

    @Override
    public void onStart() {
        Log.d(TAG, "onStart");

        if(!PermissionUtils.hasStorageAccess()) {
            Log.v(TAG, "onStart: request storage access");
            mStartPlaybackManager = false;
            PermissionUtils.requestStoragePermissions(this, REQUEST_CODE_PERMISSIONS);
        }
        else {
            mGotStorageAccess = 1;
            mStartPlaybackManager = true;
        }

        super.onStart();
        mStarted = true;
        if(mNavigationView != null) {
            mNavigationView.setNavigationItemSelectedListener(this);
        }
    }

	@Override
	public void onStop() {
		super.onStop();
		Log.d(TAG, "onStop");
        mStarted = false;
        if(mNavigationView != null) {
            mNavigationView.setNavigationItemSelectedListener(null);
        }
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
        Log.d(TAG, "onDestroy");
	}

    @Override
    public void onActivityResult(int requestCode, int responseCode, Intent intent) {
        super.onActivityResult(requestCode, responseCode, intent);
        Log.d(TAG, "onActivityResult: requestCode=" + requestCode + " responseCode=" + responseCode);

        if (requestCode == REQUEST_CODE_SIGN_IN) {
            if(mPlaybackManager != null) {
                mPlaybackManager.signInGoogleFromIntent(intent);
            }
        }
        else if(requestCode == REQUEST_CODE_ADS_NOTIFICATION) {
            if(mEngineService != null) {
                try {
                    int httpApiPort = mEngineService.getService().getHttpApiPort();
                    String accessToken = mEngineService.getService().getAccessToken();
                    onEngineConnected(httpApiPort, accessToken);
                }
                catch(RemoteException e) {
                    Log.e(TAG, "onActivityResult: error", e);
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        App.v(TAG, "onRequestPermissionsResult: requestCode=" + requestCode);
        if(requestCode == REQUEST_CODE_PERMISSIONS) {
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
            }
        }
    }

    private MainFragment getMainFragment() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_holder);
        if (fragment != null && fragment instanceof MainFragment) {
            return (MainFragment)fragment;
        }
        else {
            return null;
        }
    }

    private void onStorageAccessGranted() {
        Log.v(TAG, "onStorageAccessGranted");
        AceStreamEngineBaseApplication.onStorageAccessGranted();
        mGotStorageAccess = 1;
        mStartPlaybackManager = true;
        mActivityHelper.onStart();

        MainFragment f = getMainFragment();
        if(f != null) {
            f.onStorageAccessGranted();
        }
    }

    private void onStorageAccessDenied() {
        Log.v(TAG, "onStorageAccessDenied");
        mGotStorageAccess = 0;
        MainFragment f = getMainFragment();
        if(f != null) {
            f.onStorageAccessDenied();
        }
    }

    public void signOut() {
        if(mPlaybackManager == null) {
            throw new IllegalStateException("missing playback manager");
        }
        mPlaybackManager.signOut();
    }

    public void googleSignIn() {
        if(mPlaybackManager == null) {
            throw new IllegalStateException("missing playback manager");
        }
        Intent signInIntent = mPlaybackManager.getGoogleSignInIntent(this);
        startActivityForResult(signInIntent, REQUEST_CODE_SIGN_IN);
    }

    public void googleSignOut() {
        if(mPlaybackManager == null) {
            throw new IllegalStateException("missing playback manager");
        }

        mPlaybackManager.signOut();
    }

    public void googleRevokeAccess() {
        if(mPlaybackManager == null) {
            throw new IllegalStateException("missing playback manager");
        }

        mPlaybackManager.googleRevokeAccess();
    }

    private void printDebugInfo() {
        try {
            Log.d("acestream/debug", "compiled ABI: " + PyEmbedded.getCompiledABI());
        }
        catch(Exception e) {
            Log.d("acestream/debug", "got exception", e);
        }
        catch(UnsatisfiedLinkError e) {
            Log.d("acestream/debug", "got fatal error", e);
        }
        Log.d("acestream/debug", ">>> START DEVICE INFO <<<");
        Log.d("acestream/debug", "Device: " + Build.DEVICE);
        Log.d("acestream/debug", "Model: " + Build.MODEL);
        Log.d("acestream/debug", "Abi: " + Build.CPU_ABI);
        Log.d("acestream/debug", "Abi2: " + Build.CPU_ABI2);
        Log.d("acestream/debug", "Product: " + Build.PRODUCT);
        try {
            Process processx = Runtime.getRuntime().exec(new String[] {"cat", "/proc/cpuinfo"});
            BufferedReader in = new BufferedReader(new InputStreamReader(processx.getInputStream()));
            String line;
            while ((line = in.readLine()) != null) {
                Log.d("acestream/debug", "/proc/cpuinfo: " + line);
            }
            processx.waitFor();
        }
        catch(Exception e) {
            Log.d("acestream/debug", "error cat /proc/cpuinfo", e);
        }
        Log.d("acestream/debug", ">>> END DEVICE INFO <<<");
    }

    public void restoreMainFragment() {
        FragmentManager fm = getSupportFragmentManager();
        if(fm.getBackStackEntryCount() > 0) {
            fm.popBackStack();
        }
        else {
            Fragment mainFragment = new MainFragment();
            FragmentTransaction fragmentTransaction = createFragmentTransaction();
            fragmentTransaction.replace(R.id.fragment_holder, mainFragment, "main_fragment");
            fragmentTransaction.commit();
        }
    }

    private void showMainFragment() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        Fragment fragment = fragmentManager.findFragmentById(R.id.fragment_holder);

        if(fragment == null) {
            FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
            Log.d(TAG, "main:onCreate: show MainFragment");
            Fragment newFragment = new MainFragment();
            fragmentTransaction.replace(R.id.fragment_holder, newFragment, "main_fragment");
            fragmentTransaction.commit();
        }
        else {
            Log.d(TAG, "main:onCreate: fragment already created");
        }
    }

    public String getEngineVersion() {
        return mEngineVersion;
    }

    public void engineClearCache() {
        if(mHttpAsyncTaskFactory != null) {
            mHttpAsyncTaskFactory.build(HttpAsyncTask.HTTPTASK_CLEAN_CACHE, this).execute2("GET");
        }
        else {
            Toast.makeText(AceStreamEngineBaseApplication.context(), getResources().getString(R.string.menu_action_fail), Toast.LENGTH_SHORT).show();
        }
    }

    public void showProfile() {
        if(mHttpAsyncTaskFactory != null) {
            if(mEnginePrefsReceived)
                showPreferencesFragment(SettingsFragment.SETTINGS_PROFILE);
            else
                mHttpAsyncTaskFactory.build(HttpAsyncTask.HTTPTASK_GET_PROFILE, this).execute2("GET");
        }
        else {
            Toast.makeText(AceStreamEngineBaseApplication.context(), getResources().getString(R.string.menu_action_fail), Toast.LENGTH_SHORT).show();
        }
    }

    public void engineSignIn() {
        Fragment loginFragment = getSupportFragmentManager().findFragmentByTag("engine_login");
        if(loginFragment == null) {
            loginFragment = new LoginFragment();
            Log.d(TAG, "engineSignIn: create new login fragment");
        }
        else {
            Log.d(TAG, "engineSignIn: use existing login fragment");
        }
        FragmentTransaction fragmentTransaction = createFragmentTransaction();
        fragmentTransaction.replace(R.id.fragment_holder, loginFragment, "engine_login");
        fragmentTransaction.addToBackStack(null);
        fragmentTransaction.commitAllowingStateLoss();
    }

    public void showContentIdForm() {
        Intent intent = new Intent(this, ContentIdDialogActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
    }

    public void showPreferences() {
        if(mHttpAsyncTaskFactory != null) {
            if(mEnginePrefsReceived) {
                showPreferencesFragment(SettingsFragment.SETTINGS_MAIN);
            }
            else {
                startGettingPreferences(true);
            }
        }
        else {
            showPreferencesFragment(SettingsFragment.SETTINGS_MAIN);
        }
    }

    public void showServices() {
        if(mHttpAsyncTaskFactory != null) {
            mHttpAsyncTaskFactory.build(HttpAsyncTask.HTTPTASK_GET_EXTENSIONS, this).execute2("GET");
        }
        else {
            Toast.makeText(AceStreamEngineBaseApplication.context(), getResources().getString(R.string.menu_action_fail), Toast.LENGTH_SHORT).show();
        }
    }

    private void startGettingPreferences(boolean startPrefsActivityOnFinish) {
        Log.v(TAG, "startGettingPreferences");
        Map<String, Object> extraData = new HashMap<>();
        extraData.put("startPrefsActivityOnFinish", startPrefsActivityOnFinish ? "true" : "false");
        mHttpAsyncTaskFactory.build(HttpAsyncTask.HTTPTASK_GET_SETTINGS, this, null, extraData).execute2("GET");
    }

    @Override
    public void onHttpAsyncTaskStart(int type) {
        Log.v(TAG, "onHttpAsyncTaskStart: type=" + type);
    }

    @Override
    public void onHttpAsyncTaskFinish(int type, String result, Map<String, Object> extraData) {
        Log.v(TAG, "onHttpAsyncTaskFinish: type=" + type);
        switch(type) {
            case HttpAsyncTask.HTTPTASK_GET_SETTINGS:
                String startPrefsActivityOnFinishString = (String)extraData.get("startPrefsActivityOnFinish");
                boolean startPrefsActivityOnFinish = false;
                if(startPrefsActivityOnFinishString != null && startPrefsActivityOnFinishString.equals("true")) {
                    startPrefsActivityOnFinish = true;
                }
                processSettings(SettingsFragment.SETTINGS_MAIN, result, startPrefsActivityOnFinish);
                break;
            case HttpAsyncTask.HTTPTASK_GET_PROFILE:
                processSettings(SettingsFragment.SETTINGS_PROFILE, result, true);
                break;
            case HttpAsyncTask.HTTPTASK_GET_EXTENSIONS:
                processExtensions(result);
                break;
            case HttpAsyncTask.HTTPTASK_CLEAN_CACHE:
                break;
            default:
                break;
        }
    }

    private void processSettings(String type, String value, boolean startPrefsActivityOnFinish) {
        ExtendedEnginePreferences preferences = ExtendedEnginePreferences.fromJson(value);

        mEnginePrefsReceived = true;
        mEngineVersion = MiscUtils.ifNull(preferences.version, "?");

        updateUI();

        if(startPrefsActivityOnFinish) {
            showPreferencesFragment(type);
        }
    }

    private void showPreferencesFragment(String type) {
        Fragment prefsFragment = new SettingsFragment();

        Bundle bundle = new Bundle(2);
        bundle.putBoolean("engine_started", mEngineStarted);
        bundle.putString("type", type);
        prefsFragment.setArguments(bundle);

        FragmentTransaction fragmentTransaction = createFragmentTransaction();

        fragmentTransaction.replace(R.id.fragment_holder, prefsFragment, "prefs");
        fragmentTransaction.addToBackStack(null);
        fragmentTransaction.commitAllowingStateLoss();
    }

    public void showAboutFragment() {
        Fragment f = new AboutFragment();
        FragmentTransaction fragmentTransaction = createFragmentTransaction();
        fragmentTransaction.replace(R.id.fragment_holder, f, "about");
        fragmentTransaction.addToBackStack(null);
        fragmentTransaction.commitAllowingStateLoss();
    }

    private FragmentTransaction createFragmentTransaction() {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left, R.anim.slide_in_left, R.anim.slide_out_right);
        return fragmentTransaction;
    }

    private void processExtensions(String value) {
        try {
            JSONArray json = new JSONArray(value);
            List<Extension> exList = new ArrayList<>();

            for (int i = 0; i < json.length(); i++) {
                JSONObject o = json.getJSONObject(i);
                Extension e = new Extension();
                e.Name = o.getString("name");
                e.Description = o.getString("description");
                e.IssuedBy = o.getString("issued_by");
                e.Url = o.getString("url");
                e.Enabled = o.getBoolean("enabled");
                e.ValidFrom = o.getLong("valid_from");
                e.ValidTo = o.getLong("valid_to");
                exList.add(e);
            }
            FragmentTransaction fragmentTransaction = createFragmentTransaction();
            ExtensionsFragment exFragment = new ExtensionsFragment();
            Bundle bundle = new Bundle();
            bundle.putParcelableArrayList("list", (ArrayList<? extends Parcelable>) exList);
            exFragment.setArguments(bundle);

            fragmentTransaction.replace(R.id.fragment_holder, exFragment, "extensions");
            fragmentTransaction.addToBackStack(null);
            fragmentTransaction.commit();
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(AceStreamEngineBaseApplication.context(), R.string.task_services_fail, Toast.LENGTH_SHORT).show();
        }
    }

    public boolean canUpdateUI() {
        return mPlaybackManager != null && !mPlaybackManager.isAuthInProgress();
    }

    private void updateUI() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (canUpdateUI() && mPlaybackManager != null) {

                    if(showDrawer()) {
                        updateNavigationHeader(mPlaybackManager.getAuthData(), mPlaybackManager.getAuthLogin());
                    }

                    Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_holder);
                    if (fragment != null) {
                        if (fragment instanceof MainFragment) {
                            ((MainFragment) fragment).updateUI(
                                    mPlaybackManager.getAuthData(),
                                    mPlaybackManager.getEngineLogin(),
                                    mPlaybackManager.getGoogleSignedIn(),
                                    mPlaybackManager.getGoogleLogin(),
                                    mPlaybackManager.isGoogleApiAvailable(),
                                    mEngineVersion
                            );
                        }
                    }
                }
            }
        });
    }

    @Override
    public void onSaveSetting(String type, String name, Object value, boolean sendToEngine) {
        Log.d(TAG, "onSaveSetting: type=" + type + " name=" + name + " value=" + value + " sendToEngine=" + sendToEngine);

        if(mPlaybackManager != null) {
            mPlaybackManager.setPreference(name, value);
        }
        else {
            Log.e(TAG, "onSaveSetting: missing manager");
        }

        if(name.equals("language")) {
            recreate();
        }

        // update settings fragment
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_holder);
        if (fragment != null) {
            if (fragment instanceof SettingsFragment) {
                ((SettingsFragment) fragment).refresh();
            }
        }
    }

    protected void onEngineConnected(int httpApiPort, String accessToken) {
        Log.d(TAG, "onEngineConnected");

        if(mEngineStarted) {
            return;
        }

        doMaintain();

        mHttpAsyncTaskFactory = new HttpAsyncTask.Factory(
            httpApiPort,
            accessToken
        );

        mEngineStarted = true;
        startGettingPreferences(false);
    }

    @Override
    public void onResumeConnected() {
        super.onResumeConnected();
        String action = getIntent().getAction();
        Log.d(TAG, "onResumeConnected: action=" + action);
        mPlaybackManager.addCallback(this);
        mPlaybackManager.addAuthCallback(this);
        mPlaybackManager.discoverDevices(false);
        mPlaybackManager.addEngineSettingsCallback(this);

        if(!mPlaybackManager.isAuthInProgress()) {
            Log.d(TAG, "onResumeConnected: auth finished, update UI");
            updateUI();
        }

        mHandler.post(mUpdateAuthTask);

        if(checkRedirect(getIntent())) {
            return;
        }

        if(TextUtils.equals(getIntent().getStringExtra(Constants.EXTRA_ACTION), Constants.EXTRA_ACTION_SIGN_IN_ACESTREAM)) {
            getIntent().removeExtra(Constants.EXTRA_ACTION);
            engineSignIn();
        }
        else if(TextUtils.equals(getIntent().getStringExtra(Constants.EXTRA_ACTION), Constants.EXTRA_ACTION_SIGN_IN_GOOGLE)) {
            getIntent().removeExtra(Constants.EXTRA_ACTION);
            googleSignIn();
        }
    }

    // PlaybackManager.Callback interface
    @Override
    public void onEngineConnected(ExtendedEngineApi service) {
        try {
            if(mEngineService == null) {
                mEngineService = service;
                if(!checkPendingNotification()) {
                    int httpApiPort = service.getService().getHttpApiPort();
                    String accessToken = service.getService().getAccessToken();

                    Log.d(TAG, "onEngineConnected: port=" + httpApiPort);

                    onEngineConnected(httpApiPort, accessToken);
                }
            }
        }
        catch(RemoteException e) {
            Log.e(TAG, "onEngineConnected: failed to get engine info: " + e.getMessage());
            Toast.makeText(this, getResources().getString(R.string.start_fail), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onEngineFailed() {
        Log.d(TAG, "onEngineFailed");
        Toast.makeText(this, getResources().getString(R.string.start_fail), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onEngineUnpacking() {
        Log.d(TAG, "onEngineUnpacking");
        AceStreamEngineBaseApplication.toast(R.string.dialog_unpack);
    }

    @Override
    public void onEngineStarting() {
        Log.d(TAG, "onEngineStarting");
        AceStreamEngineBaseApplication.toast(R.string.dialog_start);
    }

    @Override
    public void onEngineStopped() {
        Log.d(TAG, "onEngineStopped: wasStarted=" + mEngineWasStarted);
        mEngineStarted = false;
        mEngineService = null;

        if(mEngineWasStarted) {
            startEngine();
        }
    }

    @MainThread
    public void startEngine() {
        if(mPlaybackManager == null) {
            Log.e(TAG, "startEngine: missing playback manager");
            return;
        }

        if(!PermissionUtils.hasStorageAccess()) {
            Log.w(TAG, "startEngine: no storage access");
            return;
        }

        mPlaybackManager.startEngine();
        mPlaybackManager.enableAceCastServer();
    }

    @MainThread
    public PlaybackManager getPlaybackManager() {
        return mPlaybackManager;
    }

    @MainThread
    public EngineApi getEngineService() {
        return mEngineService;
    }

    @MainThread
    public HttpAsyncTask.Factory getHttpAsyncTaskFactory() {
        return mHttpAsyncTaskFactory;
    }

    @Override
    public void onConnected(PlaybackManager service) {
        super.onConnected(service);
        Log.v(TAG, "playback manager connected");
        startEngine();

        MainFragment f = getMainFragment();
        if(f != null) {
            f.onPlaybackManagerConnected();
        }
    }

    @Override
    public void onDisconnected() {
        super.onDisconnected();
        App.v(TAG, "playback manager disconnected");
    }

    private void doMaintain() {
        new MaintainTask("check", this, new PyEmbedded.Callback() {
            @Override
            public void onShowDialog(final String title, final String text) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(!mStarted) {
                            Log.d(TAG, "doMaintain: activity stopped");
                            return;
                        }

                        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(MainActivity.this)
                                .setTitle(title)
                                .setMessage(text)
                                .setIcon(android.R.drawable.ic_dialog_alert)
                                .setPositiveButton("ok", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int whichButton) {
                                        Log.d(TAG, "Dialog confirmed");
                                        AceStreamEngineBaseApplication.doMaintain("apply");
                                    }
                                });

                        dialogBuilder.setNegativeButton("cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Log.d(TAG, "Dialog cancelled");
                            }
                        })
                                .setCancelable(false);
                        dialogBuilder.show();
                    }
                });
            }
        }, null).start();
    }

    @Override
    public void onAuthUpdated(final AuthData authData) {
        Log.v(TAG, "onAuthUpdated");
        updateUI();
    }

    @Override
    public void onEngineSettingsUpdated(@Nullable AceStreamPreferences preferences) {
        checkPendingNotification();

        MainFragment f = getMainFragment();
        if(f != null) {
            f.onEngineSettingsUpdated();
        }
    }

    private boolean checkPendingNotification() {
        if(mNotificationShown) {
            return false;
        }

        NotificationData notification = AceStreamEngineBaseApplication.getPendingNotification("main");
        if(notification != null) {
            mNotificationShown = AceStreamEngineBaseApplication.showNotification(
                    notification,
                    this,
                    true,
                    REQUEST_CODE_ADS_NOTIFICATION);
            return mNotificationShown;
        }

        return false;
    }

    // BEGIN navigation drawer
    private DrawerLayout mDrawerLayout;
    private NavigationView mNavigationView;
    private ActionBarDrawerToggle mDrawerToggle;
    private Drawable mDrawerDrawable;
    private ImageView mAccountBalanceButton = null;
    private TextView mAccountBalanceText = null;
    private ImageView mAccountProfileButton = null;
    private TextView mAccountProfileText = null;
    private ImageView mAccountUpgradeButton = null;
    private TextView mAccountUpgradeText = null;

    private void setupNavigationView() {
        mDrawerLayout = findViewById(R.id.drawer_layout);

        /* Set up the sidebar click listener
         * no need to invalidate menu for now */
        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, R.string.drawer_open, R.string.drawer_close) {
            @Override
            public void onDrawerClosed(View drawerView) {
                super.onDrawerClosed(drawerView);

                // If account submenu is visible when drawer is closed - collapse it.
                if(isAccountDropDownVisible()) {
                    switchAccountDropDown();
                }
            }

            // Hack to make navigation drawer browsable with DPAD.
            // see https://code.google.com/p/android/issues/detail?id=190975
            // and http://stackoverflow.com/a/34658002/3485324
            @Override
            public void onDrawerOpened(View drawerView) {
                super.onDrawerOpened(drawerView);
                if (mNavigationView.requestFocus())
                    ((NavigationMenuView) mNavigationView.getFocusedChild()).setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
            }
        };

        // Set the drawer toggle as the DrawerListener
        mDrawerLayout.setDrawerListener(mDrawerToggle);

        mNavigationView = findViewById(R.id.navigation);

        final View headerLayout = mNavigationView.getHeaderView(0); // 0-index header

        final LinearLayout dropDownSwitch = headerLayout.findViewById(R.id.account_dropdown_switch);
        if(dropDownSwitch != null) {
            dropDownSwitch.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    switchAccountDropDown();
                }
            });
        }

        final LinearLayout signInButton = headerLayout.findViewById(R.id.account_sign_in);
        if(signInButton != null) {
            signInButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    AceStream.openProfileActivity(MainActivity.this);
                    closeDrawer();
                }
            });
        }

        mAccountBalanceButton = headerLayout.findViewById(R.id.nav_header_balance_button);
        mAccountBalanceText = headerLayout.findViewById(R.id.nav_header_balance_text);
        if(mAccountBalanceButton != null) {
            mAccountBalanceButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (mPlaybackManager.isUserLoggedIn()) {
                        AceStream.openTopupActivity(MainActivity.this);
                    } else {
                        AceStream.openProfileActivity(MainActivity.this);
                    }
                    closeDrawer();
                }
            });
        }

        mAccountProfileButton = headerLayout.findViewById(R.id.nav_header_account_button);
        mAccountProfileText = headerLayout.findViewById(R.id.nav_header_account_text);
        if(mAccountProfileButton != null) {
            mAccountProfileButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    AceStream.openProfileActivity(MainActivity.this);
                    closeDrawer();
                }
            });
        }

        mAccountUpgradeButton = headerLayout.findViewById(R.id.nav_header_upgrade_button);
        mAccountUpgradeText = headerLayout.findViewById(R.id.nav_header_upgrade_text);
        if(mAccountUpgradeButton != null) {
            mAccountUpgradeButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (mPlaybackManager.isUserLoggedIn()) {
                        AceStream.openUpgradeActivity(MainActivity.this);
                    } else {
                        AceStream.openProfileActivity(MainActivity.this);
                    }
                    closeDrawer();
                }
            });
        }

        View getBonusAdsButton = headerLayout.findViewById(R.id.nav_header_bonus_ads_button);
        if(getBonusAdsButton != null) {
            getBonusAdsButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(mPlaybackManager != null) {
                        mPlaybackManager.openBonusAdsActivity(MainActivity.this);
                    }
                    closeDrawer();
                }
            });
        }
    }

    private void closeDrawer() {
        if(mDrawerLayout != null && mDrawerLayout.isDrawerOpen(mNavigationView)) {
            mDrawerLayout.closeDrawer(mNavigationView);
        }
    }

    private void switchAccountDropDown() {
        final View headerLayout = mNavigationView.getHeaderView(0); // 0-index header
        final LinearLayout dropDownSwitch = headerLayout.findViewById(R.id.account_dropdown_switch);

        ImageView dropDownSwitchImage =
                headerLayout.findViewById(R.id.account_dropdown_image);

        int drawableId;
        if(dropDownSwitch.getTag() == null) {
            drawableId = R.drawable.ic_arrow_drop_up_black_24dp;
            dropDownSwitch.setTag(true);
            showAccountDrawerMenu();
        }
        else {
            drawableId = R.drawable.ic_arrow_drop_down_black_24dp;
            dropDownSwitch.setTag(null);
            showTopLevelDrawerMenu();
        }

        if(dropDownSwitchImage != null) {
            dropDownSwitchImage.setImageDrawable(getResources().getDrawable(drawableId));
        }
    }

    private void showTopLevelDrawerMenu() {
        for (int i = 0; i < mNavigationView.getMenu().size(); i++) {
            MenuItem mi = mNavigationView.getMenu().getItem(i);
            mi.setVisible(mi.getGroupId() == R.id.scrollable_group
                    || mi.getGroupId() == R.id.fixed_group
                    || mi.getGroupId() == R.id.remote_control_group
                    || mi.getItemId() == R.id.extensions_group);
        }
    }

    private void showAccountDrawerMenu() {
        int groupId = R.id.submenu_account_signed_in;
        for (int i = 0; i < mNavigationView.getMenu().size(); i++) {
            MenuItem mi = mNavigationView.getMenu().getItem(i);
            mi.setVisible(mi.getGroupId() == groupId);
        }
    }

    private boolean isAccountDropDownVisible() {
        final View headerLayout = mNavigationView.getHeaderView(0); // 0-index header
        final LinearLayout dropDownSwitch = headerLayout.findViewById(R.id.account_dropdown_switch);
        return dropDownSwitch.getTag() != null;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        closeDrawer();

        // Handle item selection
        switch (item.getItemId()) {
            case android.R.id.home:
                if(mDrawerToggle != null) {
                    // Toggle the sidebar
                    return mDrawerToggle.onOptionsItemSelected(item);
                }
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        if(item == null)
            return false;

        int id = item.getItemId();
        if (id == R.id.nav_submenu_video) {
            for (int i = 0; i < mNavigationView.getMenu().size(); i++) {
                MenuItem mi = mNavigationView.getMenu().getItem(i);
                mi.setVisible(mi.getGroupId() == R.id.submenu_video || mi.getItemId() == R.id.nav_return);

                if (mi.getItemId() == R.id.nav_return) {
                    mi.setTitle(getString(R.string.video));
                }
            }
            return false;
        } else if (id == R.id.nav_submenu_audio) {
            for (int i = 0; i < mNavigationView.getMenu().size(); i++) {
                MenuItem mi = mNavigationView.getMenu().getItem(i);
                mi.setVisible(mi.getGroupId() == R.id.submenu_audio || mi.getItemId() == R.id.nav_return);

                if (mi.getItemId() == R.id.nav_return) {
                    mi.setTitle(getString(R.string.audio));
                }
            }
            return false;
        } else if (id == R.id.nav_submenu_browsing) {
            for (int i = 0; i < mNavigationView.getMenu().size(); i++) {
                MenuItem mi = mNavigationView.getMenu().getItem(i);
                mi.setVisible(mi.getGroupId() == R.id.submenu_browsing || mi.getItemId() == R.id.nav_return);

                if (mi.getItemId() == R.id.nav_return) {
                    mi.setTitle(getString(R.string.browsing));
                }
            }
            return false;
        } else if (id == R.id.nav_submenu_settings) {
            for (int i = 0; i < mNavigationView.getMenu().size(); i++) {
                MenuItem mi = mNavigationView.getMenu().getItem(i);
                mi.setVisible(mi.getGroupId() == R.id.submenu_settings || mi.getItemId() == R.id.nav_return);

                if (mi.getItemId() == R.id.nav_return) {
                    mi.setTitle(getString(R.string.preferences));
                }
            }
            return false;
        } else if (id == R.id.nav_return) {
            showTopLevelDrawerMenu();
            return false;
        } else if (id == R.id.nav_sign_out) {
            signOut();
            switchAccountDropDown();
            closeDrawer();
            return false;
        } else if (id == R.id.nav_remote_control) {
            Intent intent = new Intent(this, RemoteControlActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);
        } else if (id == R.id.nav_shutdown_engine) {
            Logger.v(TAG, "stopApp");
            AceStream.stopApp();
        } else if (id == R.id.nav_clear_cache) {
            if(mPlaybackManager != null) {
                mPlaybackManager.clearCache();
            }
            else {
                // old style
                engineClearCache();
            }
        } else if (id == R.id.nav_report_problem) {
            startActivity(new Intent(this, ReportProblemActivity.class));
        } else if (id == R.id.nav_restart) {
            restartApp();
        } else if (id == R.id.nav_video_local) {
            VlcBridge.openVideoLocal();
            finish();
        } else if (id == R.id.nav_video_torrents) {
            VlcBridge.openVideoTorrents();
            finish();
        } else if (id == R.id.nav_video_live_streams) {
            VlcBridge.openVideoLiveStreams();
            finish();
        } else if (id == R.id.nav_audio_local) {
            VlcBridge.openAudioLocal();
            finish();
        } else if (id == R.id.nav_audio_torrents) {
            VlcBridge.openAudioTorrents();
            finish();
        }  else if (id == R.id.nav_directories) {
            VlcBridge.openBrowsingDirectories();
            finish();
        } else if (id == R.id.nav_network) {
            VlcBridge.openBrowsingLocalNetworks();
            finish();
        } else if (id == R.id.nav_mrl) {
            VlcBridge.openBrowsingStream();
            finish();
        } else if (id == R.id.nav_history) {
            VlcBridge.openHistory();
            finish();
        } else if (id == R.id.nav_about) {
            VlcBridge.openAbout();
            finish();
        } else if (id == R.id.nav_settings_ads) {
            VlcBridge.openSettingsAds();
            finish();
        } else if (id == R.id.nav_settings_engine) {
            VlcBridge.openSettingsEngine();
            finish();
        } else if (id == R.id.nav_settings_player) {
            VlcBridge.openSettingsPlayer();
            finish();
        }

        closeDrawer();
        return false;
    }

    private void updateNavigationHeader(final AuthData authData, String text) {
        final boolean userSignedIn;

        if(TextUtils.isEmpty(text)) {
            text = "";
            userSignedIn = false;
        }
        else {
            userSignedIn = true;
        }

        final String fText = text;
        Workers.runOnMainThread(new Runnable() {
            @Override
            public void run() {
                int balance = -1;
                int color = R.color.bluegrey100;
                int icon = R.drawable.ic_account_circle_24dp_bluegrey100;
                String accountText = getResources().getString(R.string.user_profile);
                if(authData != null && authData.auth_level > 0 && authData.package_color != null) {
                    accountText = authData.package_name;
                    balance = authData.purse_amount;
                    switch (authData.package_color) {
                        case "red":
                            icon = R.drawable.ic_account_circle_24dp_red;
                            color = R.color.ace_red;
                            break;
                        case "yellow":
                            icon = R.drawable.ic_account_circle_24dp_yellow;
                            color = R.color.ace_yellow;
                            break;
                        case "green":
                            icon = R.drawable.ic_account_circle_24dp_green;
                            color = R.color.ace_green;
                            break;
                        case "blue":
                            icon = R.drawable.ic_account_circle_24dp_blue;
                            color = R.color.ace_blue;
                            break;
                    }
                }

                if(mAccountProfileButton != null) {
                    mAccountProfileButton.setImageDrawable(getResources().getDrawable(icon));
                }

                if(mAccountProfileText != null) {
                    mAccountProfileText.setText(accountText);
                    mAccountProfileText.setTextColor(getResources().getColor(color));
                }

                if(mAccountBalanceText != null) {
                    String balanceText;
                    if(balance == -1) {
                        balanceText = getResources().getString(R.string.account_balance);
                    }
                    else {
                        balanceText = getResources().getString(
                                R.string.topup_button_title_short, balance / 100.0);
                    }
                    mAccountBalanceText.setText(balanceText);
                }

                if(mAccountUpgradeButton != null && mAccountUpgradeText != null) {
                    if(authData != null && AuthUtils.hasNoAds(authData.auth_level)) {
                        mAccountUpgradeText.setText(R.string.upgrade);
                        mAccountUpgradeText.setTag(R.id.tag_name, "upgrade");
                        mAccountUpgradeButton.setImageDrawable(
                                getResources().getDrawable(R.drawable.ace_ic_upgrade));
                        mHandler.removeCallbacks(mRotateUpgradeButton);
                    }
                    else {
                        // Initial value 50/50
                        if (new Random().nextInt(100) > 50) {
                            mAccountUpgradeText.setText(R.string.disable_ads);
                            mAccountUpgradeText.setTag(R.id.tag_name, "disable_ads");
                            mAccountUpgradeButton.setImageDrawable(
                                    getResources().getDrawable(R.drawable.ace_ic_no_ads));
                        } else {
                            mAccountUpgradeText.setText(R.string.upgrade);
                            mAccountUpgradeText.setTag(R.id.tag_name, "upgrade");
                            mAccountUpgradeButton.setImageDrawable(
                                    getResources().getDrawable(R.drawable.ace_ic_upgrade));
                        }
                        mHandler.removeCallbacks(mRotateUpgradeButton);
                        mHandler.postDelayed(mRotateUpgradeButton, UPGRADE_BUTTON_ROTATE_INTERVAL);
                    }
                }

                final View headerLayout = mNavigationView.getHeaderView(0); // 0-index header
                if(userSignedIn) {
                    headerLayout.findViewById(R.id.account_dropdown_switch).setVisibility(View.VISIBLE);
                    headerLayout.findViewById(R.id.account_sign_in).setVisibility(View.GONE);
                }
                else {
                    headerLayout.findViewById(R.id.account_dropdown_switch).setVisibility(View.GONE);
                    headerLayout.findViewById(R.id.account_sign_in).setVisibility(View.VISIBLE);
                }

                TextView v = headerLayout.findViewById(R.id.account_dropdown_text);
                if (v != null) {
                    v.setText(fText);
                }
            }
        });
    }
    // END navigation drawer

    public void restartApp() {
        new android.app.AlertDialog.Builder(this)
                .setMessage(R.string.restart_confirmation)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int whichButton) {
                        Context ctx = getApplicationContext();
                        Intent intent = new Intent(ctx, MainActivity.class);
                        int pendingIntentId = new Random().nextInt();
                        PendingIntent pendingIntent = PendingIntent.getActivity(
                                ctx,
                                pendingIntentId,
                                intent,
                                PendingIntent.FLAG_CANCEL_CURRENT);
                        AlarmManager mgr = (AlarmManager)ctx.getSystemService(android.content.Context.ALARM_SERVICE);
                        if(mgr != null) {
                            mgr.set(AlarmManager.RTC,
                                    System.currentTimeMillis() + 100,
                                    pendingIntent);
                        }
                        AceStream.restartApp();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    public void addCoins(String source, int amount, boolean needNoAds) {
        if (mPlaybackManager != null) {
            mPlaybackManager.addCoins(source, amount, needNoAds);
        }
    }
}
