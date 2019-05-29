package org.acestream.engine;

import com.appodeal.ads.Appodeal;
import com.google.ads.mediation.admob.AdMobAdapter;
import com.google.android.gms.ads.AdRequest;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import androidx.annotation.Nullable;
import androidx.annotation.Keep;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.acestream.engine.aliases.App;
import org.acestream.engine.controller.Engine;
import org.acestream.engine.interfaces.ChannelsSyncListener;
import org.acestream.engine.maintain.AlarmReceiver;
import org.acestream.engine.maintain.AlarmService;
import org.acestream.engine.notification.LinkActivity;
import org.acestream.engine.notification.WebViewNotificationActivity;
import org.acestream.engine.player.VideoPlayerActivity;
import org.acestream.engine.prefs.ExtendedEnginePreferences;
import org.acestream.engine.prefs.NotificationData;
import org.acestream.engine.python.PyEmbedded;
import org.acestream.engine.service.AceStreamEngineNotificationManager;
import org.acestream.engine.service.AceStreamEngineService;
import org.acestream.engine.ui.auth.LoginActivity;
import org.acestream.engine.util.AndroidUtil;
import org.acestream.engine.util.DeviceUuidFactory;
import org.acestream.sdk.AceStream;
import org.acestream.sdk.Constants;
import org.acestream.sdk.SelectedPlayer;
import org.acestream.sdk.controller.api.AceStreamPreferences;
import org.acestream.sdk.controller.api.response.AdConfig;
import org.acestream.sdk.controller.api.response.AndroidNotification;
import org.acestream.sdk.utils.Logger;
import org.acestream.sdk.utils.MiscUtils;
import org.acestream.sdk.utils.PermissionUtils;
import org.acestream.sdk.utils.VlcBridge;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import androidx.annotation.NonNull;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.webkit.CookieManager;
import android.widget.Toast;

import static org.acestream.engine.Constants.EXTRA_WEBVIEW_NOTIFICATION_ID;
import static org.acestream.engine.Constants.EXTRA_WEBVIEW_REQUIRE_ENGINE;
import static org.acestream.engine.Constants.EXTRA_WEBVIEW_URL;
import static org.acestream.engine.Constants.PREF_DEFAULT_AD_SEGMENT;
import static org.acestream.engine.Constants.PREF_KEY_AD_SEGMENT;
import static org.acestream.engine.Constants.PREF_KEY_NOTIFICATIONS;
import static org.acestream.sdk.Constants.PREFS_DEFAULT_OUTPUT_FORMAT_LIVE;
import static org.acestream.sdk.Constants.PREFS_DEFAULT_OUTPUT_FORMAT_VOD;
import static org.acestream.sdk.Constants.PREF_KEY_SHOW_DEBUG_INFO;

@SuppressWarnings({"WeakerAccess", "unused"})
@Keep
public class AceStreamEngineBaseApplication {

	protected static AceStreamEngineBaseApplication sInstance = null;

	public static final String DEFAULT_SCRIPT = "main.py";
	public static final String VERSION_FILE = ".version";
    public static final String TAG = "AS/App";
    public static final boolean ENABLE_MAINTAIN = false;

	public final static String ANALYTICS_EVENT_PLAY_REQUEST = "as_play_request";
	public final static String ANALYTICS_EVENT_AD_IMPRESSION = "custom_ad_impression";
	public final static String ANALYTICS_EVENT_AD_REQUEST = "custom_ad_request";

	public final static String ANALYTICS_EVENT_AD_IMPRESSION_BONUSES_SCREEN = "as_ad_impression_bonuses_screen";
	public final static String ANALYTICS_EVENT_AD_IMPRESSION_PREROLL = "as_ad_impression_preroll";
	public final static String ANALYTICS_EVENT_AD_IMPRESSION_PAUSE = "as_ad_impression_pause";
	public final static String ANALYTICS_EVENT_AD_IMPRESSION_UNPAUSE = "as_ad_impression_unpause";
	public final static String ANALYTICS_EVENT_AD_IMPRESSION_CLOSE = "as_ad_impression_close";

	public final static String ANALYTICS_EVENT_AD_REQUEST_PREROLL = "as_ad_request_preroll";
	public final static String ANALYTICS_EVENT_AD_REQUEST_PAUSE = "as_ad_request_pause";
	public final static String ANALYTICS_EVENT_AD_REQUEST_UNPAUSE = "as_ad_request_unpause";
	public final static String ANALYTICS_EVENT_AD_REQUEST_CLOSE = "as_ad_request_close";

	public final static String BROADCAST_DO_INTERNAL_MAINTAIN = "do_internal_maintain";

	private static Context appContext = null;
	private static String appPackageName = null;
	private static SharedPreferences sPreferences = null;

	private static String appVersionName = null;
	private static String appFilesDir = null;
	private static int appVersionCode = 0;
	private static Resources appResources = null;
	private static DeviceUuidFactory mUuidFactory = null;
    private static boolean sUseVlcBridge = false;
    private static boolean sWebViewAvailable = true;

	private static Class<? extends WebViewActivity> sWebViewActivityClass = WebViewActivity.class;
	private static Class<? extends WebViewActivity> sWebViewNotificationActivityClass = WebViewNotificationActivity.class;
	private static Class<? extends Activity> sMainActivityClass = MainActivity.class;
	private static Class<? extends Activity> sLinkActivityClass = LinkActivity.class;
	private static Class<? extends Activity> sVideoPlayerActivityClass = VideoPlayerActivity.class;
	private static Class<? extends Activity> sLoginActivityClass = LoginActivity.class;
	private static Class<? extends Activity> sResolverActivityClass = ResolverActivity.class;
	private static Class<? extends Activity> sBonusAdsActivityClass = BonusAdsActivity.class;

	private static Map<String,Object> mValues = new HashMap<>();

	// Used in Android TV version
	protected static Engine.Factory mEngineFactory = null;
	protected static ChannelsSyncListener mChannelsSyncListener = null;

	private static final Handler sMainThreadHandler = new Handler(Looper.getMainLooper());

	private static SparseArray<String> sAdSegments = new SparseArray<>();
	private static int[] sAdSegmentIds;
	private static String[] sAdSegmentNames;
	static {
		sAdSegments.put(0, "Auto");
		sAdSegments.put(25, "0.25");
		sAdSegments.put(50, "0.50");
		sAdSegments.put(75, "0.75");
		sAdSegments.put(100, "1.00");
		sAdSegments.put(150, "1.50");
		sAdSegments.put(200, "2.00");
		sAdSegments.put(250, "2.50");
		sAdSegments.put(300, "3.00");
		sAdSegments.put(350, "3.50");
		sAdSegments.put(400, "4.00");
		sAdSegments.put(450, "4.50");
		sAdSegments.put(500, "5.00");

		sAdSegmentIds = new int[sAdSegments.size()];
		sAdSegmentNames = new String[sAdSegments.size()];
		for(int i = 0; i < sAdSegments.size(); i++) {
			sAdSegmentIds[i] = sAdSegments.keyAt(i);
			sAdSegmentNames[i] = sAdSegments.valueAt(i);
		}
	}

	/* Up to 2 threads maximum, inactive threads are killed after 2 seconds */
	private static final int maxThreads = Math.max(AndroidUtil.isJellyBeanMR1OrLater ? Runtime.getRuntime().availableProcessors() : 2, 1);
	public static final ThreadFactory THREAD_FACTORY = new ThreadFactory() {
		@Override
		public Thread newThread(@NonNull  Runnable runnable) {
			final Thread thread = new Thread(runnable);
			thread.setPriority(Process.THREAD_PRIORITY_DEFAULT+Process.THREAD_PRIORITY_LESS_FAVORABLE);
			return thread;
		}
	};
	private static final ThreadPoolExecutor threadPool = new ThreadPoolExecutor(
			Math.min(2, maxThreads),
			maxThreads,
			30,
			TimeUnit.SECONDS,
			new LinkedBlockingQueue<Runnable>(),
			THREAD_FACTORY);

	public static void initialize(Context context) {
		if(sInstance == null) {
			sInstance = new AceStreamEngineBaseApplication(context);
		}
	}

	public static AceStreamEngineBaseApplication getInstance() {
		if(sInstance == null) {
			throw new IllegalStateException("not initialized");
		}
		return sInstance;
	}

	protected AceStreamEngineBaseApplication(@NonNull final Context context) {
		if(appContext != null) {
			// Already initialized
			return;
		}

		appContext = context.getApplicationContext();
		sPreferences = PreferenceManager.getDefaultSharedPreferences(appContext);
		appPackageName = appContext.getPackageName();
		appFilesDir = appContext.getFilesDir().getAbsolutePath();

		mUuidFactory = new DeviceUuidFactory(context());

		try {
			PackageInfo pkg = appContext.getPackageManager().getPackageInfo(appContext.getPackageName(), 0);
			appVersionName = pkg.versionName;
			appVersionCode = pkg.versionCode;
		} catch (NameNotFoundException e) {
			appVersionName = "";
		}

		appResources = appContext.getResources();

		// restore locale from prefs
		SharedPreferences sp = getPreferences();
		String language = sp.getString("language", null);
		if(language != null) {
			setLocale(language);
		}

		AceStreamEngineNotificationManager.createNotificationChannels(appContext);

		IntentFilter filter = new IntentFilter(AceStream.ACTION_RESTART_APP);
		appContext.registerReceiver(mBroadcastReceiver, filter);

		// Init AceStream SDK
		AceStream.init(appContext, appFilesDir, getDeviceName(), getDeviceUuidString());
		AceStream.setHttpApiProductKey("40e9ba380752b7b4feb7c6616e0eb3949e6d1412");
		detectVlcBridge();

		Log.i(TAG, "startup: package=" + AceStream.getApplicationId());
		Log.i(TAG, "startup: version=" + AceStream.getApplicationVersionCode());

		if(BuildConfig.enableDebugLogging) {
			getPreferences().edit().putBoolean("enable_debug_logging", true).apply();
			Logger.enableDebugLogging(true);
		}

		if(PermissionUtils.hasStorageAccess()) {
			onStorageAccessGranted();
		}
	}

	public static void detectVlcBridge() {
		// Find first package by service intent
		String targetPackage = null;
		Intent intent = new Intent(VlcBridge.ACTION_START_PLAYBACK_SERVICE);
		List<ResolveInfo> services = MiscUtils.resolveBroadcastIntent(context(), intent);
		if(services != null) {
			//noinspection LoopStatementThatDoesntLoop
			for (ResolveInfo ri : services) {
				targetPackage = ri.activityInfo.packageName;
				break;
			}
		}

		initVlcBridge(targetPackage);
    }

	public static void onStorageAccessGranted() {
		Log.v(TAG, "onStorageAccessGranted");
		AceStream.onStorageAccessGranted();
		initMaintainAlarm(10000);
	}

	public static void clearWebViewCookies() {
		Log.v(TAG, "clearWebViewCookies");
		if(Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
			try {
				CookieManager.getInstance().removeAllCookies(null);
				CookieManager.getInstance().flush();
			}
			catch(Throwable e) {
				// Can throw exceptions when WebView is not installed
				Logger.e(TAG, "clearWebViewCookies", e);
			}
		}
		else {
			//TODO: implement for older versions
		}
	}

    public static String getDeviceName() {
		return getPreferences().getString(Constants.PREF_KEY_DEVICE_NAME, Build.MODEL);
    }

	public static Context context() {
		return appContext;
	}

	public static String getVersionName() {
		return appVersionName;
	}

	public static int getVersionCode() {
		return appVersionCode;
	}

	public static String versionName() {
		return appVersionName;
	}

	public static Resources resources() {
		return appResources;
	}

	public static boolean isMobileNetworkingEnabled() {
		SharedPreferences sp = getPreferences();
		return sp.getBoolean("mobile_network_available", false);
	}

	public static void setMobileNetworkingEnabled(boolean value) {
		SharedPreferences sp = getPreferences();
		SharedPreferences.Editor editor = sp.edit();
		editor.putBoolean("mobile_network_available", value);
		editor.apply();

		Intent intent = new Intent();
		intent.setAction(AceStreamEngineService.ACTION_CONNECTION_AVAILABILITY_CHANGED);
		intent.putExtra("new_mobile_network_available", value);
		context().sendBroadcast(intent);
	}

	public static Intent getBrowserIntent(Context context, String url) {
		return getBrowserIntent(context, url, false);
	}

	public static Intent getBrowserIntent(Context context, String url, boolean skipWebView) {
		boolean useWebView = true;

		// Don't use WebView in versions prior to KitKat because it can cause error
		// "Error inflating class android.widget.ZoomControls"
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
			useWebView = false;
		}
		else if(skipWebView) {
			useWebView = false;
		}

		if(useWebView) {
			try {
				return new WebViewIntent(context, url);
			}
			catch(Throwable e) {
				Log.e(TAG, "failed to start webview activity", e);
			}
		}

		// We got here if we don't use WebView or it failed
		PackageManager pm = context().getPackageManager();
		Intent implicitIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));

		// first, try to resolve an intent
		ComponentName component = implicitIntent.resolveActivity(pm);
		if (component == null) {
			// cannot resolve any browser
			return null;
		}

		// use resolved activity if it's not a resolver
		if (!component.getClassName().contains("ResolverActivity")) {
			Log.d(TAG, "getBrowserIntent: use default: package=" + component.getPackageName() + " class=" + component.getClassName());
			Intent explicitIntent = new Intent(implicitIntent);
			explicitIntent.setComponent(component);
			return explicitIntent;
		}

		// Use manual selection when there are multiple choices.
		// Select chrome or the first browser in the list if there is no chrome.
		List<ResolveInfo> resolveInfo = pm.queryIntentActivities(implicitIntent, PackageManager.MATCH_DEFAULT_ONLY);
		if (resolveInfo == null || resolveInfo.size() == 0) {
			return null;
		}

		String packageName = null;
		String className = null;

		for (int i = 0; i < resolveInfo.size(); i++) {
			ResolveInfo ri = resolveInfo.get(i);
			if (ri.activityInfo != null) {
				Log.d(TAG, "getBrowserIntent: test: package=" + ri.activityInfo.packageName + " class=" + ri.activityInfo.name);
				if (packageName == null) {
					// remember first one
					packageName = ri.activityInfo.packageName;
					className = ri.activityInfo.name;
				}

				// stop if we've found chrome
				if ("com.google.android.apps.chrome.Main".equals(ri.activityInfo.name)) {
					packageName = ri.activityInfo.packageName;
					className = ri.activityInfo.name;
					break;
				}
			}
		}

		Log.d(TAG, "getBrowserIntent: choice: package=" + packageName + " class=" + className);

		if (packageName == null) {
			// Nothing found. Generally this shouldn't happen.
			return null;
		}

		Intent explicitIntent = new Intent(implicitIntent);
		explicitIntent.setComponent(new ComponentName(packageName, className));
		return explicitIntent;
	}

	public static void startBrowserIntent(Context context, String url) {
		Intent intent = getBrowserIntent(context, url);
		startBrowserIntent(context, intent);
	}

	public static void startBrowserIntent(final Context context, final Intent intent) {
		if(intent != null) {
			try {
				if(!MiscUtils.isNetworkConnected(context)) {
					toast("No internet connection");
					return;
				}
				context.startActivity(intent);
			}
			catch(Throwable e) {
				Log.e(TAG, "startBrowserIntent", e);
				try {
					// last chance
					// this can fail on amazon tv
					// see: https://fabric.io/ace-stream/android/apps/org.acestream.media/issues/570d2069ffcdc04250a5a47e
					context.startActivity(new Intent(Intent.ACTION_VIEW, intent.getData()));
				}
				catch(Throwable e2) {
					Log.e(TAG, "startBrowserIntent", e2);
				}
			}
		}
	}

    public static Intent getUninstallIntent(String packageName) {
        Intent intent = new Intent(Intent.ACTION_DELETE);
        intent.setData(Uri.parse("package:" + packageName));
        return intent;
    }

	public static Intent makePlayIntent(String url, int httpApiPort, String mime) {
		if(!url.startsWith("http:")) {
			url = "http://127.0.0.1:" + httpApiPort + url;
		}

		Uri uri = Uri.parse(url);
		Intent playIntent = new Intent();
		playIntent.setAction(Intent.ACTION_VIEW);
		playIntent.setDataAndType(uri, mime);

		Log.d(TAG, "makePlayIntent: mime=" + mime + " uri=" + uri.toString());

		playIntent.addCategory(Intent.CATEGORY_BROWSABLE);
		return playIntent;
	}

	public static SharedPreferences getPreferences() {
		return sPreferences;
	}

	public static boolean getDebugBoolPref(String name, boolean defaultValue) {
		if(BuildConfig.DEBUG) {
			return defaultValue;
		}
	   	return getPreferences().getBoolean(name, defaultValue);
   	}

	public static long getTotalEngineSessionDuration() {
		try {
			SharedPreferences prefs = getAppPreferences();
			return prefs.getLong("total_engine_session_duration", 0);
		}
		catch(Throwable e) {
			Log.e(TAG, "getTotalEngineSessionDuration", e);
			return 0;
		}
	}

	public static void setLocale(String lang) {
		Log.d(TAG, "set locale: " + lang);

		if(appResources == null) {
			return;
		}

		Locale myLocale = new Locale(lang);
		Locale.setDefault(myLocale);
		DisplayMetrics dm = appResources.getDisplayMetrics();
		Configuration conf = appResources.getConfiguration();
		conf.locale = myLocale;
		appResources.updateConfiguration(conf, dm);
	}

	public static String getSavedLanguage() {
		return getPreferences().getString("language", null);
	}

	public static Context updateBaseContextLocale(Context context) {
		String language = getSavedLanguage();
		if(TextUtils.isEmpty(language)) {
			return context;
		}
		Locale locale = new Locale(language);
		Locale.setDefault(locale);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
			return updateResourcesLocale(context, locale);
		}

		return updateResourcesLocaleLegacy(context, locale);
	}

	@TargetApi(Build.VERSION_CODES.N)
	private static Context updateResourcesLocale(Context context, Locale locale) {
		Configuration configuration = context.getResources().getConfiguration();
		configuration.setLocale(locale);
		return context.createConfigurationContext(configuration);
	}

	@SuppressWarnings("deprecation")
	private static Context updateResourcesLocaleLegacy(Context context, Locale locale) {
		Resources resources = context.getResources();
		Configuration configuration = resources.getConfiguration();
		configuration.locale = locale;
		resources.updateConfiguration(configuration, resources.getDisplayMetrics());
		return context;
	}

	public static void requestChannelsSync() {
		if(mChannelsSyncListener != null) {
			mChannelsSyncListener.onChannelsSync();
		}
	}

	public static void requestEPGSync() {
		if(mChannelsSyncListener != null) {
			mChannelsSyncListener.onEPGSync();
		}
	}

	public static String getCompiledABI() {
		try {
			return PyEmbedded.getCompiledABI();
		}
		catch(Throwable e) {
			Log.e(TAG, "failed to get compiled abi: " + e.getMessage());
			return null;
		}
	}

	public static boolean showTvUi() {
		return AceStream.isAndroidTv();
	}

	@Keep
	public static UUID getDeviceUuid() {
		if(mUuidFactory == null) {
			return null;
		}
		return mUuidFactory.getDeviceUuid();
	}

	public static String getDeviceUuidString() {
		UUID uuid = getDeviceUuid();
		if(uuid == null) {
			return null;
		}
		return uuid.toString();
	}

	public static boolean isOnMainThread() {
		return Looper.myLooper() == Looper.getMainLooper();
	}

	public static void runOnMainThread(Runnable runnable) {
		if (isOnMainThread()) {
			runnable.run();
		}
		else {
			sMainThreadHandler.post(runnable);
		}
	}

	public static void runBackground(Runnable runnable) {
		if (Looper.myLooper() != Looper.getMainLooper()) runnable.run();
		else threadPool.execute(runnable);
	}

	public static boolean redirectIntent(Activity activity, Intent intent, String targetApp) {
		if(intent == null) {
			intent = new Intent();
			intent.setAction(Intent.ACTION_VIEW);
			intent.addCategory(Intent.CATEGORY_DEFAULT);
		}
		String className = activity.getClass().getCanonicalName();
		intent.setClassName(targetApp, className);

		Log.v(TAG, "redirectIntent:"
				+ " package=" + targetApp
				+ " class=" + className
				+ " action=" + intent.getAction());

		try {
			activity.startActivity(intent);
		}
		catch(ActivityNotFoundException e) {
			PackageManager pm = context().getPackageManager();
			Intent query = new Intent();
			query.setPackage(targetApp);
			query.setAction(Intent.ACTION_MAIN);
			query.addCategory(Intent.CATEGORY_LAUNCHER);
			ResolveInfo ri = pm.resolveActivity(query, 0);
			if(ri != null) {
				Log.d(TAG, "redirectIntent: target activity resolved: targetApp=" + targetApp + " name=" + ri.activityInfo.name);
				intent.setClassName(targetApp, ri.activityInfo.name);
				activity.startActivity(intent);
			}
			else {
				Log.e(TAG, "Cannot resolve target activity: targetApp=" + targetApp);
				return false;
			}
		}

		return true;
	}

	public static String getArch() {
		try {
			return PyEmbedded.getCompiledABI();
		}
		catch(UnsatisfiedLinkError e) {
			return null;
		}
	}

	/**
	 * Read engine version code from version file.
	 */
	public static int getEngineVersionCode() {
		if (appFilesDir == null) {
			return 0;
		}

		File versionFile = new File(appFilesDir, "engine_version.json");
		if (!versionFile.exists()) {
			return 0;
		}

		int versionCode = 0;
		StringBuilder data = new StringBuilder();
		try {
			BufferedReader br = new BufferedReader(new FileReader(versionFile));
			String line;

			while((line = br.readLine()) != null) {
				data.append(line);
				data.append("\n");
			}
			br.close();

			JSONObject root = new JSONObject(data.toString());
			versionCode = root.optInt("version_code", 0);
		}
		catch (IOException e) {
			Log.e(TAG, "failed to read engine version file", e);
		}
		catch (JSONException e) {
			Log.e(TAG, "failed to parse engine version file", e);
		}

		return versionCode;
	}

	public static String getEngineVersionName() {
		if (appFilesDir == null) {
			return null;
		}

		File versionFile = new File(appFilesDir, "engine_version.json");
		if (!versionFile.exists()) {
			return null;
		}

		String versionName = null;
		StringBuilder data = new StringBuilder();
		try {
			BufferedReader br = new BufferedReader(new FileReader(versionFile));
			String line;

			while((line = br.readLine()) != null) {
				data.append(line);
				data.append("\n");
			}
			br.close();

			JSONObject root = new JSONObject(data.toString());
			versionName = root.optString("version_name");
		}
		catch (IOException e) {
			Log.e(TAG, "failed to read engine version file", e);
		}
		catch (JSONException e) {
			Log.e(TAG, "failed to parse engine version file", e);
		}

		return versionName;
	}

	public static void initMaintainAlarm(long triggerInterval) {
		Log.v(TAG, "init maintain alarm: enabled=" + ENABLE_MAINTAIN);

		if(!ENABLE_MAINTAIN) {
			return;
		}

		AlarmManager alarmMgr = (AlarmManager)appContext.getSystemService(Context.ALARM_SERVICE);
		if(alarmMgr == null) {
			return;
		}

		Intent intent = new Intent(appContext, AlarmReceiver.class);
		intent.setAction("maintain");
		PendingIntent pendingIntent = PendingIntent.getBroadcast(appContext, 0, intent, 0);

		alarmMgr.setInexactRepeating(
				AlarmManager.ELAPSED_REALTIME,
				SystemClock.elapsedRealtime() + triggerInterval,
				AlarmManager.INTERVAL_DAY,
				pendingIntent
		);
	}

	public static void doMaintain(String mode) {
		Intent intent = new Intent(appContext, AlarmService.class);
		intent.setAction("maintain");

		if(mode != null) {
			intent.putExtra("mode", mode);
		}
		appContext.startService(intent);
	}

	public static class WebViewIntent extends Intent {
		WebViewIntent(Context context, String url) {
			super(context, sWebViewActivityClass);
			setAction(Intent.ACTION_VIEW);
			setData(Uri.parse(url));
			putExtra(EXTRA_WEBVIEW_URL, url);
		}
	}

	public static void toast(int resId) {
		toast(context().getString(resId));
	}

	public static void toast(String message) {
		Toast.makeText(context(), message, Toast.LENGTH_LONG).show();
	}

    public static void startPlaybackByInfohash(@NonNull String infohash) {
        startPlaybackByInfohash(infohash, false);
    }

	public static void startPlaybackByInfohash(@NonNull String infohash, boolean forceOurPlayer) {
		Log.v(TAG, "startPlaybackByInfohash: infohash=" + infohash + " force=" + forceOurPlayer);
		SelectedPlayer player;
		if(forceOurPlayer) {
		    player = SelectedPlayer.getOurPlayer();
        }
        else {
			player = AceStream.getLastSelectedPlayer();
		}
		context().startActivity(ContentStartActivity.makeIntentFromInfohash(context(), infohash, player));
	}

	public static boolean isDebugLoggingEnabled() {
		return getPreferences().getBoolean("enable_debug_logging", BuildConfig.enableDebugLogging);
	}

	public static void setValue(String key, Object value) {
		mValues.put(key, value);
	}

	public static Object getValue(String key) {
		return mValues.get(key);
	}

	public static boolean hasValue(String key) {
		return mValues.containsKey(key);
	}

	public static long getLongValue(String key, long defaultValue) {
		return mValues.containsKey(key) ? (long)mValues.get(key) : defaultValue;
	}

	public static boolean shouldShowAdMobAds() {
		return !showTvUi();
	}

	// Notifications
	public static Map<String, NotificationData> getNotifications() {
		Map<String, NotificationData> notifications = new HashMap<>();
		SharedPreferences prefs = getPreferences();
		String raw = prefs.getString(PREF_KEY_NOTIFICATIONS, null);
		if(TextUtils.isEmpty(raw)) {
			return notifications;
		}

		try {
			return new Gson().fromJson(
				raw,
				new TypeToken<HashMap<String, NotificationData>>(){}.getType());
		}
		catch(Throwable e) {
			Log.e(TAG, "getNotifications: failed to parse notification: raw=" + raw, e);
			return notifications;
		}
	}

	public static void saveNotifications(Map<String, NotificationData> notifications) {
		String jsonString = new Gson().toJson(notifications);
		getPreferences()
				.edit()
				.putString(PREF_KEY_NOTIFICATIONS, jsonString)
				.apply();
	}

	public static void updateNotifications(@NonNull ExtendedEnginePreferences preferences) {
		if(preferences.android_config == null || preferences.android_config.notifications == null)
			return;

		boolean hasNew = false;
		Map<String, NotificationData> currentNotifications = getNotifications();
		for(AndroidNotification notification: preferences.android_config.notifications) {
			if(!currentNotifications.containsKey(notification.id)) {
				App.v(TAG, "notifications: add new:"
						+ " id=" + notification.id
						+ " type=" + notification.type
						+ " url=" + notification.url
					);
				NotificationData data = new NotificationData();
				data.id = notification.id;
				data.type = notification.type;
				data.url = notification.url;
				data.shown = false;
				currentNotifications.put(notification.id, data);
				hasNew = true;
			}
		}

		if(hasNew) {
			saveNotifications(currentNotifications);
		}
	}

	public static NotificationData getPendingNotification(String source) {
		Map<String, NotificationData> notifications = getNotifications();
		for(NotificationData notification: notifications.values()) {
			if(!notification.shown) {
				App.v(TAG, "notifications:get_pending: got: source=" + source + " id=" + notification.id);
				return notification;
			}
		}

		App.v(TAG, "notifications:get_pending: no pending: source=" + source);

		return null;
    }

    //:debug
	public static void resetNotification() {
		if(!BuildConfig.DEBUG) {
			return;
		}
		Map<String, NotificationData> notifications = getNotifications();
		for(NotificationData notification: notifications.values()) {
			if(notification.shown) {
				notification.shown = false;
				Log.v(TAG, "notifications:reset: id=" + notification.id);
			}
		}

		saveNotifications(notifications);
	}
	///debug

    public static void dismissNotification(String id) {
		boolean updated = false;

		Map<String, NotificationData> currentNotifications = getNotifications();
		NotificationData notification = currentNotifications.get(id);
		if(notification != null) {
			notification.shown = true;
			updated = true;
		}

        App.v(TAG, "notifications:dismiss:"
                + " id=" + id
                + " updated=" + updated
                + " shown=" + (notification == null ? null : notification.shown)
        );

		if(updated) {
			saveNotifications(currentNotifications);
		}
	}

	public static boolean showNotification(NotificationData notification, Context context) {
		return showNotification(notification, context, false, -1);
	}

	public static boolean showNotification(NotificationData notification, Context context, boolean startForResult, int requestCode) {
		if(!isWebViewAvailable()) {
			Log.e(TAG, "showNotification: webview is not available");
			return false;
		}

		if(!TextUtils.equals(notification.type, "webview")) {
			Log.e(TAG, "unsupported notification type: " + notification.type);
			return false;
		}

		if(TextUtils.isEmpty(notification.url)) {
			Log.e(TAG, "empty notification url");
			return false;
		}

		// add "tv" flag when on TV interface
		String url = notification.url;
		if(AceStreamEngineBaseApplication.showTvUi()) {
			url += url.contains("?") ? "&" : "?";
			url += "tv=1";
		}

		Intent intent = new Intent(context, getWebViewNotificationActivityClass());
		intent.putExtra(EXTRA_WEBVIEW_REQUIRE_ENGINE, false);
		intent.putExtra(EXTRA_WEBVIEW_NOTIFICATION_ID, notification.id);
		intent.putExtra(EXTRA_WEBVIEW_URL, url);
		if(!(context instanceof Activity)) {
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		}

		if(startForResult) {
			if(!(context instanceof Activity)) {
				throw new IllegalStateException("Cannot start for result from non-activity context");
			}
			((Activity)context).startActivityForResult(
					intent,
					requestCode);
		}
		else {
			context.startActivity(intent);
		}

		return true;
	}

	public static void showAdblockNotification(Context context) {
		NotificationData notification = new NotificationData();
		notification.type = "webview";
		notification.id = "adblock-detected";
		notification.url = AceStream.getBackendDomain() + "/notification/adblock-detected";
		AceStreamEngineBaseApplication.showNotification(notification, context);
	}

	public static AdRequest.Builder createAdRequestBuilder() {
		AdRequest.Builder builder = new AdRequest.Builder();

		if(!getGdprConsent()) {
			Bundle extras = new Bundle();
			extras.putString("npa", "1");
			builder.addNetworkExtrasBundle(AdMobAdapter.class, extras);
		}

		if(BuildConfig.admobUseTestDevices) {
			//TODO: add test devices here
			//builder.addTestDevice("XXXXXX");
		}
		return builder;
	}

	public static void initAppodeal(int segment, Activity activity, int adTypes, boolean autoCache, AdConfig config) {
		if(BuildConfig.DEBUG) {
			Appodeal.setTesting(BuildConfig.appodealUseTestAds);
		}

		if(Logger.verbose()) {
			Appodeal.setLogLevel(com.appodeal.ads.utils.Log.LogLevel.debug);
		}

		String appKey = getStringAppMetadata("appodealAppKey");
		boolean gdprConsent = getGdprConsent();
		App.v(TAG, "initAppodeal: gdprConsent=" + gdprConsent + " segment=" + segment + " key=" + appKey);

		if(config != null && config.appodeal_disable_networks != null) {
			for(String network: config.appodeal_disable_networks) {
				Logger.v(TAG, "initAppodeal: disable network: name=" + network);
				Appodeal.disableNetwork(activity, network);
			}
		}

		Appodeal.setAutoCache(adTypes, autoCache);
		Appodeal.disableLocationPermissionCheck();
		if(BuildConfig.DEBUG && BuildConfig.appodealUseTestAds) {
			// Use segment without price floor (because test ads have eCPM=0)
			Appodeal.setSegmentFilter("user_segment", 1001);
		}
		else {
			Appodeal.setSegmentFilter("user_segment", segment);
		}
		Appodeal.initialize(activity, appKey, adTypes, gdprConsent);
	}

	public static AceStreamPreferences processSettings(ExtendedEnginePreferences preferences) {
		SharedPreferences sp = getPreferences();
		AceStreamPreferences prefs = new AceStreamPreferences();
		updateAppSettings(prefs);

		// engine prefs
		prefs.putString("vod_buffer", String.valueOf(preferences.vod_buffer));
		prefs.putString("vod_buffer", String.valueOf(preferences.vod_buffer));
		prefs.putString("live_buffer", String.valueOf(preferences.live_buffer));
		prefs.putString("disk_cache_limit", String.valueOf(MiscUtils.bytesToMegabytes(preferences.disk_cache_limit)));
		prefs.putString("memory_cache_limit", String.valueOf(MiscUtils.bytesToMegabytes(preferences.memory_cache_limit)));
		prefs.putString("download_limit", String.valueOf(preferences.download_limit));
		prefs.putString("upload_limit", String.valueOf(preferences.upload_limit));
		prefs.putString("login", MiscUtils.ifNull(preferences.login, ""));
		prefs.putString("password", preferences.has_password ? "*****" : "");
		prefs.putString("port", String.valueOf(preferences.port));
		prefs.putString("max_connections", String.valueOf(preferences.max_connections));
		prefs.putString("max_peers", String.valueOf(preferences.max_peers));
		prefs.putString("profile_gender", MiscUtils.ifNull(preferences.profile_gender, "0"));
		prefs.putString("profile_age", MiscUtils.ifNull(preferences.profile_age, "0"));
		prefs.putString("output_format_live", MiscUtils.ifNull(preferences.output_format_live, PREFS_DEFAULT_OUTPUT_FORMAT_LIVE));
		prefs.putString("output_format_vod", MiscUtils.ifNull(preferences.output_format_vod, PREFS_DEFAULT_OUTPUT_FORMAT_VOD));
		prefs.putBoolean("transcode_ac3", preferences.transcode_ac3);
		prefs.putBoolean("transcode_audio", preferences.transcode_audio);
		prefs.putBoolean("allow_intranet_access", preferences.allow_intranet_access != 0);
		prefs.putBoolean("allow_remote_access", preferences.allow_remote_access != 0);

		//TODO: check this
		prefs.putBoolean("live_cache_type", TextUtils.equals(preferences.live_cache_type, "disk"));
		prefs.putBoolean("vod_cache_type", TextUtils.equals(preferences.vod_cache_type, "disk"));

		String cacheDir = MiscUtils.ifNull(preferences.cache_dir, AceStream.externalFilesDir());
		if(!new File(cacheDir).exists())
			cacheDir = AceStream.externalFilesDir();
		prefs.putString("cache_dir", cacheDir);

		prefs.fill(sp.edit()).apply();

		return prefs;
	}

	public static void updateAppSettings(AceStreamPreferences prefs) {
		SharedPreferences sp = getPreferences();
		SharedPreferences resolverPrefs = getResolverPreferences();

		// app prefs
		prefs.putBoolean("mobile_network_available", sp.getBoolean("mobile_network_available", false));
		prefs.putBoolean("acestream_start_acecast_server_on_boot", sp.getBoolean("start_acecast_server_on_boot", shouldStartAceCastServerByDefault()));
		prefs.putBoolean("enable_debug_logging", sp.getBoolean("enable_debug_logging", false));
		prefs.putBoolean(Constants.PREF_KEY_SHOW_DEBUG_INFO, sp.getBoolean(Constants.PREF_KEY_SHOW_DEBUG_INFO, false));
		prefs.putString(Constants.PREF_KEY_SELECTED_PLAYER, resolverPrefs.getString(Constants.PREF_KEY_SELECTED_PLAYER, null));
		prefs.putString("device_name", getDeviceName());
		prefs.putString(PREF_KEY_NOTIFICATIONS, sp.getString(PREF_KEY_NOTIFICATIONS, null));

		// ad prefs
		prefs.putBoolean("gdpr_consent", getGdprConsent());
		prefs.putBoolean("show_rewarded_ads", showRewardedAds());
		prefs.putBoolean("show_ads_on_main_screen", showAdsOnMainScreen());
		prefs.putBoolean("show_ads_on_preroll", showAdsOnPreroll());
		prefs.putBoolean("show_ads_on_pause", showAdsOnPause());
		prefs.putBoolean("show_ads_on_close", showAdsOnClose());
	}

	public static void showTopupForm(Context context) {
		startBrowserIntent(context,
				AceStream.getBackendDomain() + "/user/topup");
	}

	public static void showUpgradeForm(Context context) {
		startBrowserIntent(context,
				AceStream.getBackendDomain()
						+ "/user/upgrade?target_platform=android");
	}

	private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
		String action = intent.getAction();
		App.v(TAG, "receiver: action=" + action);

		if(TextUtils.equals(action, AceStream.ACTION_RESTART_APP)) {
			android.os.Process.killProcess(android.os.Process.myPid());
		}
		}
	};

	public static String getStringAppMetadata(String key) {
		try {
			// Prepend with prefix
			key = "org.acestream." + key;
			ApplicationInfo appInfo = context().getPackageManager()
					.getApplicationInfo(context().getPackageName(), PackageManager.GET_META_DATA);
			Bundle metadata = appInfo.metaData;
			if(!metadata.containsKey(key)) {
				throw new IllegalStateException("Missing metadata in manifest: " + key);
			}
			return metadata.getString(key);
		}
		catch(NameNotFoundException e) {
			throw new IllegalStateException("Failed to read metadata from manifest: " + key, e);
		}
	}

	public static Class<? extends Activity> getMainActivityClass() {
		return sMainActivityClass;
	}

	protected static void setMainActivityClass(Class<? extends Activity> clazz) {
		sMainActivityClass = clazz;
	}

	protected static void setWebViewActivityClass(Class<? extends WebViewActivity> clazz) {
		sWebViewActivityClass = clazz;
	}

	public static Class<? extends WebViewActivity> getWebViewActivityClass() {
		return sWebViewActivityClass;
	}

	protected static void setWebViewNotificationActivityClass(Class<? extends WebViewActivity> clazz) {
		sWebViewNotificationActivityClass = clazz;
	}

	public static Class<? extends WebViewActivity> getWebViewNotificationActivityClass() {
		return sWebViewNotificationActivityClass;
	}

	public static Class<? extends Activity> getLinkActivityClass() {
		return sLinkActivityClass;
	}

	protected static void setLinkActivityClass(Class<? extends Activity> clazz) {
		sLinkActivityClass = clazz;
	}

	public static Class<? extends Activity> getVideoPlayerActivityClass() {
		return sVideoPlayerActivityClass;
	}

	protected static void setVideoPlayerActivityClass(Class<? extends Activity> clazz) {
		sVideoPlayerActivityClass = clazz;
	}

	public static Class<? extends Activity> getLoginActivityClass() {
		return sLoginActivityClass;
	}

	protected static void setLoginActivityClass(Class<? extends Activity> clazz) {
		sLoginActivityClass = clazz;
	}

	public static Class<? extends Activity> getResolverActivityClass() {
		return sResolverActivityClass;
	}

	protected static void setResolverActivityClass(Class<? extends Activity> clazz) {
		sResolverActivityClass = clazz;
	}

	public static Class<? extends Activity> getBonusAdsActivityClass() {
		return sBonusAdsActivityClass;
	}

	protected static void setBonusAdsActivityClass(Class<? extends Activity> clazz) {
		sBonusAdsActivityClass = clazz;
	}

	protected static void setChannelsSyncListener(ChannelsSyncListener listener) {
		mChannelsSyncListener = listener;
	}

    private static void initVlcBridge(@Nullable String targetPackage) {
		VlcBridge.setTargetPackage(targetPackage);
        sUseVlcBridge = (targetPackage != null);
    }

    public static boolean useVlcBridge() {
        return sUseVlcBridge;
    }

	public static boolean shouldStartAceCastServer() {
		if(AceStream.getTargetApp() != null) {
			// Don't start AceCast server in secondary app.
			// Server is started in main app.
			return false;
		}

		if(AceStream.isAndroidTv()) {
			return true;
		}

		final boolean shouldStartByDefault = shouldStartAceCastServerByDefault();

		SharedPreferences sp = getPreferences();
		return sp.getBoolean("start_acecast_server_on_boot", shouldStartByDefault);
	}

	public static boolean shouldStartAceCastServerByDefault() {
		return showTvUi();
	}

	public static boolean getGdprConsent() {
		return getPreferences().getBoolean(Constants.PREF_KEY_GDPR_CONSENT, false);
	}

	public static void setGdprConsent(boolean value) {
		Logger.v(TAG, "setGdprConsent: value=" + value);
		getPreferences()
				.edit()
				.putBoolean(Constants.PREF_KEY_GDPR_CONSENT, value)
				.apply();
	}

	public static int getAdSegment() {
		int value = getPreferences().getInt(PREF_KEY_AD_SEGMENT, PREF_DEFAULT_AD_SEGMENT);
		// Sanity check
		if(sAdSegments.indexOfKey(value) < 0)
			value = PREF_DEFAULT_AD_SEGMENT;
		return value;
	}

	/**
	 * Find segment with minimum pricefloor for the given amount.
	 *
	 * @param amount
	 * @return Segment id or -1 if not found
	 */
	public static int getAdSegmentByAmount(int amount) {
		int segment = -1;
		for(int i = sAdSegmentIds.length-1; i >= 0; i--) {
			if(sAdSegmentIds[i] <= amount) {
				segment = sAdSegmentIds[i];
				break;
			}
		}
		return segment;
	}

	public static int getHighestAdSegment() {
		return 500;
	}

	/**
	 * Get ad segment with lower price than @current with bottom limit @bottomLimit
	 *
	 * @param current
	 * @param bottomLimit
	 * @return ID of segment or -1 if not found
	 */
	public static int getNextAdSegment(int current, int bottomLimit) {
		int bottomLimitIndex = sAdSegments.indexOfKey(bottomLimit);
		if(bottomLimitIndex == -1) {
			return -1;
		}

		int currentIndex = sAdSegments.indexOfKey(current);
		if(currentIndex <= bottomLimitIndex) {
			return -1;
		}

		return sAdSegments.keyAt(currentIndex - 1);
	}

	public static String getBonusSource(int amount) {
		int segment = getAdSegmentByAmount(amount);
		int currentSegment = getAdSegment();
		if(currentSegment > 0) {
			segment = Math.min(segment, currentSegment);
		}
		return "bonuses:segment:" + segment;
	}

	public static String getAdSegmentName() {
		return sAdSegments.get(getAdSegment());
	}

	public static String[] getAdSegmentNames() {
		return sAdSegmentNames;
	}

	public static int[] getAdSegmentIds() {
		return sAdSegmentIds;
	}

	public static void setAdSegment(int value) {
		Logger.v(TAG, "setAdSegment: value=" + value);
		getPreferences()
				.edit()
				.putInt(PREF_KEY_AD_SEGMENT, value)
				.apply();
		Appodeal.setSegmentFilter("user_segment", value);
	}

	public static void setShowRewardedAds(boolean value) {
		Logger.v(TAG, "setShowRewardedAds: value=" + value);
		getPreferences()
				.edit()
				.putBoolean(Constants.PREF_KEY_SHOW_REWARDED_ADS, value)
				.apply();
	}

	public static boolean showRewardedAds() {
		return getBoolSharedPref(
				Constants.PREF_KEY_SHOW_REWARDED_ADS,
				Constants.PREF_DEFAULT_SHOW_REWARDED_ADS);
	}

	public static boolean showAdsOnMainScreen() {
		return getBoolSharedPref(
				Constants.PREF_KEY_SHOW_ADS_ON_MAIN_SCREEN,
				Constants.PREF_DEFAULT_SHOW_ADS_ON_MAIN_SCREEN);
	}

	public static boolean showAdsOnPreroll() {
		return getBoolSharedPref(
				Constants.PREF_KEY_SHOW_ADS_ON_PREROLL,
				Constants.PREF_DEFAULT_SHOW_ADS_ON_PREROLL);
	}

	public static boolean showAdsOnPause() {
		return getBoolSharedPref(
				Constants.PREF_KEY_SHOW_ADS_ON_PAUSE,
				Constants.PREF_DEFAULT_SHOW_ADS_ON_PAUSE);
	}

	public static boolean showAdsOnClose() {
		return getBoolSharedPref(
				Constants.PREF_KEY_SHOW_ADS_ON_CLOSE,
				Constants.PREF_DEFAULT_SHOW_ADS_ON_CLOSE);
	}

	public static String getSharedPref(String name, String defaultValue) {
		try {
			return getPreferences().getString(name, defaultValue);
		}
		catch(Throwable e) {
			Log.e(TAG, "failed to get shared preference", e);
			return null;
		}
	}

	public static boolean getBoolSharedPref(String name, boolean defaultValue) {
		try {
			return getPreferences().getBoolean(name, defaultValue);
		}
		catch(Throwable e) {
			Log.e(TAG, "failed to get shared preference", e);
			return defaultValue;
		}
	}

	public static SelectedPlayer getSelectedPlayer() {
		String data = getResolverPreferences().getString(Constants.PREF_KEY_SELECTED_PLAYER, null);
		if(data == null) {
			return null;
		}

		SelectedPlayer player = null;
		try {
			player = SelectedPlayer.fromJson(data);
		}
		catch(JSONException e) {
			Log.e(TAG, "failed to deserialize player", e);
		}

		return player;
	}

	public static SelectedPlayer getLastSelectedPlayerFromPrefs() {
		String data = getResolverPreferences().getString(Constants.PREF_KEY_LAST_SELECTED_PLAYER, null);
		if(data == null) {
			return null;
		}

		SelectedPlayer player = null;
		try {
			player = SelectedPlayer.fromJson(data);
		}
		catch(JSONException e) {
			Log.e(TAG, "failed to deserialize player", e);
		}

		return player;
	}

	public static void saveSelectedPlayer(@Nullable SelectedPlayer player, boolean fromUser) {
		Logger.v(TAG, "saveSelectedPlayer: player=" + player + " fromUser=" + fromUser);

		if(player == null) {
			forgetSelectedPlayer();
			return;
		}

		String payload = player.toJson();
		SharedPreferences.Editor editor = getResolverPreferences().edit();
		editor.putString(Constants.PREF_KEY_SELECTED_PLAYER, payload);
		if(fromUser) {
			editor.putString(Constants.PREF_KEY_LAST_SELECTED_PLAYER, payload);
		}
		editor.apply();
	}

	public static void forgetSelectedPlayer() {
		Logger.v(TAG, "forgetSelectedPlayer");
		SharedPreferences.Editor editor = getResolverPreferences().edit();
		editor.remove(Constants.PREF_KEY_SELECTED_PLAYER);
		editor.apply();
	}

	public static SharedPreferences getResolverPreferences() {
		return context().getSharedPreferences("resolver", Context.MODE_PRIVATE);
	}

	public static SharedPreferences getDiscoveryServerPreferences() {
		return context().getSharedPreferences("discoveryServer", Context.MODE_PRIVATE);
	}

	public static SharedPreferences getContentPreferences() {
		return context().getSharedPreferences("content", Context.MODE_PRIVATE);
	}

	public static SharedPreferences getAppPreferences() {
		return context().getSharedPreferences("app", Context.MODE_PRIVATE);
	}

	public static SharedPreferences getPlayerPreferences() {
		return context().getSharedPreferences("player", Context.MODE_PRIVATE);
	}

	public static String getLiveOutputFormat() {
		return getPreferences().getString("output_format_live", Constants.PREFS_DEFAULT_OUTPUT_FORMAT_LIVE);
	}

	public static String getVodOutputFormat() {
		return getPreferences().getString("output_format_vod", Constants.PREFS_DEFAULT_OUTPUT_FORMAT_VOD);
	}

	public static boolean getTranscodeAudio() {
		return getPreferences().getBoolean("transcode_audio", Constants.PREFS_DEFAULT_TRANSCODE_AUDIO);
	}

	public static boolean getTranscodeAC3() {
		return getPreferences().getBoolean("transcode_ac3", Constants.PREFS_DEFAULT_TRANSCODE_AC3);
	}

	public static boolean showDebugInfo() {
		return getPreferences().getBoolean(PREF_KEY_SHOW_DEBUG_INFO, false);
	}

	public static void dumpPreferences() {
		for(Map.Entry<String, ?> item: getPreferences().getAll().entrySet()) {
			Log.v(TAG, "dump_prefs: name=" + item.getKey() + " value=" + item.getValue());
		}
	}

	// This method can be overridden to use some external analytics
	public void logEvent(@NonNull String name, @Nullable Bundle params) {
	}

	// Helpers
	public void logAdImpression(String provider, String placement, String type) {
		logAdImpression(provider, placement, type, null);
	}

	public void logAdImpression(String provider, String placement, String type, @Nullable Bundle params) {
		if(params == null) {
			params = new Bundle();
		}
		params.putString("provider", provider);
		params.putString("placement", placement);
		params.putString("type", type);
		logEvent(ANALYTICS_EVENT_AD_IMPRESSION, params);
	}

	public void logAdImpressionBonusesScreen(String provider, String type) {
		Bundle params = new Bundle();
		params.putString("provider", provider);
		params.putString("type", type);
		logEvent(ANALYTICS_EVENT_AD_IMPRESSION_BONUSES_SCREEN, params);
	}

	public void logAdImpressionPreroll(String provider, String type) {
		Bundle params = new Bundle();
		params.putString("provider", provider);
		params.putString("type", type);
		logEvent(ANALYTICS_EVENT_AD_IMPRESSION_PREROLL, params);
	}

	public void logAdImpressionPause(String provider, String type) {
		Bundle params = new Bundle();
		params.putString("provider", provider);
		params.putString("type", type);
		logEvent(ANALYTICS_EVENT_AD_IMPRESSION_PAUSE, params);
	}

	public void logAdImpressionUnpause(String provider, String type) {
		Bundle params = new Bundle();
		params.putString("provider", provider);
		params.putString("type", type);
		logEvent(ANALYTICS_EVENT_AD_IMPRESSION_UNPAUSE, params);
	}

	public void logAdImpressionClose(String provider, String type) {
		Bundle params = new Bundle();
		params.putString("provider", provider);
		params.putString("type", type);
		logEvent(ANALYTICS_EVENT_AD_IMPRESSION_CLOSE, params);
	}

	public void logPlayRequest(SelectedPlayer player) {
		logPlayRequest(player.getTypeName(), player.id1);
	}

	public void logPlayRequest(int playerType) {
		logPlayRequest(SelectedPlayer.getTypeName(playerType), null);
	}

	public void logPlayRequest(String playerType, String playerId) {
		Bundle params = new Bundle();
		params.putString("player", playerType);
		if(playerId != null && TextUtils.equals(playerType, "external")) {
			params.putString("id", playerId);
		}

		logEvent(ANALYTICS_EVENT_PLAY_REQUEST, params);
	}

	public void logAdRequest(String placement) {
		logAdRequest(placement, null);
	}

	public void logAdRequest(String placement, @Nullable Bundle params) {
		if(params == null) {
			params = new Bundle();
		}
		params.putString("placement", placement);
		logEvent(ANALYTICS_EVENT_AD_REQUEST, params);
	}

	public void logAdRequestPreroll() {
		logEvent(ANALYTICS_EVENT_AD_REQUEST_PREROLL, null);
	}

	public void logAdRequestPause() {
		logEvent(ANALYTICS_EVENT_AD_REQUEST_PAUSE, null);
	}

	public void logAdRequestUnpause() {
		logEvent(ANALYTICS_EVENT_AD_REQUEST_UNPAUSE, null);
	}

	public void logAdRequestClose() {
		logEvent(ANALYTICS_EVENT_AD_REQUEST_CLOSE, null);
	}

	public static void setWebViewAvailable(boolean available) {
		Log.d(TAG, "setWebViewAvailable: " + available);
		sWebViewAvailable = available;
	}

	public static boolean isWebViewAvailable() {
		return sWebViewAvailable;
	}

	public long internalMaintainInterval() {
		return 900000;
	}

    public void doInternalMaintain(@NonNull final AceStreamManagerImpl pm) {
	}
}
