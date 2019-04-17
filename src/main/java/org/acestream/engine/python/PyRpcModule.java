package org.acestream.engine.python;

import java.util.Locale;

import org.acestream.engine.AceStreamEngineBaseApplication;
import org.acestream.engine.BuildConfig;
import org.acestream.engine.Constants;
import org.acestream.engine.R;
import org.acestream.engine.helpers.IntentHelper;
import org.acestream.engine.notification.NotificationData;
import org.acestream.engine.notification.NotificationReceiverActivity;
import org.acestream.engine.python.PyRpcProxy.PyRpcManager;
import org.acestream.engine.service.AceStreamEngineNotificationManager;
import org.acestream.engine.service.AceStreamEngineService;
import org.acestream.sdk.AceStream;
import org.acestream.sdk.utils.MiscUtils;

import android.app.ActivityManager;
import android.app.ActivityManager.MemoryInfo;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.StatFs;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import android.widget.Toast;
import android.util.Log;

import com.google.gson.Gson;
import com.googlecode.android_scripting.jsonrpc.RpcReceiver;
import com.googlecode.android_scripting.rpc.Rpc;
import com.googlecode.android_scripting.rpc.RpcParameter;

public class PyRpcModule extends RpcReceiver {
	protected final static String TAG = "AceStream/PyRpcModule";

	private final Context mContext;
	private final PyEmbedded.Callback mCallback;
	private final Handler mHandler;
	private final ActivityManager mAm;

	public PyRpcModule(PyRpcManager manager) {
		super(manager);
		mContext = manager.getContext();
		mCallback = manager.getCallback();
		mHandler = new Handler(mContext.getMainLooper());
		mAm = (ActivityManager)mContext.getSystemService(Context.ACTIVITY_SERVICE);
	}

	@Override
	public void shutdown() {
	}

	@SuppressWarnings("unused")
	@Rpc(description = "Displays a short-duration Toast notification.")
	public void makeToast(@RpcParameter(name = "message") final String message) {
	    Log.d(TAG, "makeToast: message=" + message);
		mHandler.post(new Runnable() {
		public void run() {
			Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show();
		}
	    });
	}

	@SuppressWarnings("unused")
	@Rpc(description = "return ACESTREAM_HOME")
	public String getAceStreamHome() {
		return AceStream.externalFilesDir();
	}

	@SuppressWarnings("unused")
	@Rpc(description = "Returns the unique device ID, for example, the IMEI for GSM and the MEID for CDMA phones. Return null if device ID is not available.")
	public String getDeviceId() {
		return AceStreamEngineBaseApplication.getDeviceUuidString();
	}

	@SuppressWarnings("unused")
	@Rpc(description = "Returns the device display language")
	public String getDisplayLanguage() {
		return Locale.getDefault().getDisplayLanguage();
	}

	@SuppressWarnings("deprecation,unused")
	@Rpc(description = "statfs available blocks")
	public long getAvailableBlocks(@RpcParameter(name = "path") final String path) {
		StatFs stat = new StatFs(path);
		if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR1)
			return (long)stat.getAvailableBlocks();
		else
			return stat.getAvailableBlocksLong();
	}

	@SuppressWarnings("unused")
	@Rpc(description = "statfs available bytes")
	public long getAvailableBytes(@RpcParameter(name = "path") final String path) {
		StatFs stat = new StatFs(path);
		if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR1)
			return -1;
		else
			return stat.getAvailableBytes();
	}

	@SuppressWarnings("unused")
	@Rpc(description = "statfs free bytes")
	public long getFreeBytes(@RpcParameter(name = "path") final String path) {
		StatFs stat = new StatFs(path);
		if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR1)
			return -1;
		else
			return stat.getFreeBytes();
	}

	@SuppressWarnings("unused")
	@Rpc(description = "statfs total bytes")
	public long getTotalBytes(@RpcParameter(name = "path") final String path) {
		StatFs stat = new StatFs(path);
		if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR1)
			return -1;
		else
			return stat.getTotalBytes();
	}

	@SuppressWarnings("deprecation,unused")
	@Rpc(description = "statfs block count")
	public long getBlockCount(@RpcParameter(name = "path") final String path) {
		StatFs stat = new StatFs(path);
		if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR1)
			return (long)stat.getBlockCount();
		else
			return stat.getBlockCountLong();
	}

	@SuppressWarnings("deprecation,unused")
	@Rpc(description = "statfs block size")
	public long getBlockSize(@RpcParameter(name = "path") final String path) {
		StatFs stat = new StatFs(path);
		if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR1)
			return (long)stat.getBlockSize();
		else
			return stat.getBlockSizeLong();
	}

	@SuppressWarnings("deprecation,unused")
	@Rpc(description = "statfs free blocks")
	public long getFreeBlocks(@RpcParameter(name = "path") final String path) {
		StatFs stat = new StatFs(path);
		if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN_MR1)
			return (long)stat.getFreeBlocks();
		else
			return stat.getFreeBlocksLong();
	}

	@SuppressWarnings("unused")
	@Rpc(description = "Returns the total amount of memory in the Java virtual machine")
	public long getTotalMemory() {
		Runtime rt = Runtime.getRuntime();
		return rt.totalMemory();
	}

	@SuppressWarnings("unused")
	@Rpc(description = "Returns the maximum amount of memory that the Java virtual machine will attempt to use")
	public long getMaxMemory() {
		Runtime rt = Runtime.getRuntime();
		return rt.maxMemory();
	}

	@SuppressWarnings("unused")
	@Rpc(description = "Returns the class of memory")
	public int getMemoryClass() {
		return mAm.getMemoryClass();
	}

	@SuppressWarnings("unused")
	@Rpc(description = "Returns the total RAM size")
	public long getRAMSize() {
		MemoryInfo memInfo = new ActivityManager.MemoryInfo();
		mAm.getMemoryInfo(memInfo);
		return memInfo.totalMem;
	}

	@SuppressWarnings("unused")
	@Rpc(description = "Open given URL in browser")
	public void openUrlInBrowser(@RpcParameter(name = "url") final String url) {
	    Log.d(TAG, "openUrlInBrowser: url=" + url);
	    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
	    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	    if(intent.resolveActivity(AceStreamEngineBaseApplication.context().getPackageManager()) != null) {
			mContext.startActivity(intent);
        }
	}

	@SuppressWarnings("unused")
	@Rpc(description = "Write debug message to logcat")
	public void logDebug(@RpcParameter(name = "message") final String message) {
	    Log.d("AceStream/engine", message);
	}

	@SuppressWarnings("unused")
	@Rpc(description = "Show notification")
	public void showNotification(
			@RpcParameter(name="notificationId") final String notificationIdString,
			@RpcParameter(name="title") final String title,
			@RpcParameter(name="text") final String text
	) {
		Log.d(TAG, "showNotification: id=" + notificationIdString + " title=" + title + " text=" + text);
        final int notificationId = Integer.valueOf(notificationIdString);

        if(notificationId == 100) {
			SharedPreferences sp = AceStreamEngineBaseApplication.getPreferences();
			long now = System.currentTimeMillis();
			long snoozeUntil = sp.getLong(Constants.PREF_MAINTAIN_INTENT_SNOOZE_UNTIL, -1);
			if(snoozeUntil != -1 && snoozeUntil > now) {
				Log.v(TAG, "showNotification: skip: wait " + (snoozeUntil - now));
				return;
			}
		}

		Intent snoozeIntent = new Intent(mContext, NotificationReceiverActivity.class);
		snoozeIntent.setAction(Constants.ACTION_NOTIFICATION_MAINTAIN_SNOOZE);
		snoozeIntent.putExtra(Constants.EXTRA_NOTIFICATION_ID, notificationId);
		snoozeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
		PendingIntent snoozePendingIntent =
				PendingIntent.getActivity(mContext, 0, snoozeIntent, PendingIntent.FLAG_UPDATE_CURRENT);

		Intent applyIntent = new Intent(mContext, NotificationReceiverActivity.class);
		applyIntent.setAction(Constants.ACTION_NOTIFICATION_MAINTAIN_APPLY);
		applyIntent.putExtra(Constants.EXTRA_NOTIFICATION_ID, notificationId);
		applyIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
		PendingIntent applyPendingIntent =
				PendingIntent.getActivity(mContext, 0, applyIntent, PendingIntent.FLAG_UPDATE_CURRENT);

		NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext, AceStreamEngineNotificationManager.DEFAULT_NOTIFICATION_CHANNEL_ID)
				.setSmallIcon(R.drawable.ic_acestream)
				.setContentTitle(title)
				.setContentText(text)
				.setPriority(NotificationCompat.PRIORITY_DEFAULT)
				.setContentIntent(applyPendingIntent)
				.setAutoCancel(true)
				.addAction(0, "Update", applyPendingIntent)
				.addAction(0, "Later", snoozePendingIntent);

		NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mContext);

		// notificationId is a unique int for each notification that you must define
		// We pass string id because RPC failed to use int (probably some bug, need to investigate)
		notificationManager.notify(notificationId, builder.build());
	}

	@SuppressWarnings("unused")
	@Rpc(description = "Show dialog")
	public void showDialog(
			@RpcParameter(name="notificationId") final String notificationIdString,
			@RpcParameter(name="title") final String title,
			@RpcParameter(name="text") final String text
	) {
		Log.d(TAG, "showDialog: id=" + notificationIdString + " title=" + title + " text=" + text);
		final int notificationId = Integer.valueOf(notificationIdString);

		if(notificationId == 100) {
			SharedPreferences sp = AceStreamEngineBaseApplication.getPreferences();
			long now = System.currentTimeMillis();
			long snoozeUntil = sp.getLong(Constants.PREF_MAINTAIN_INTENT_SNOOZE_UNTIL, -1);
			if(snoozeUntil != -1 && snoozeUntil > now) {
				Log.v(TAG, "showNotification: skip: wait " + (snoozeUntil - now));
				return;
			}
		}

		if(mCallback != null) {
			mCallback.onShowDialog(title, text);
		}
	}

	@SuppressWarnings("unused")
	@Rpc(description = "Start given activity by class name")
	public void startInternalActivity(
	    @RpcParameter(name="className") final String className,
	    @RpcParameter(name="data") final String data) {
	    Log.d(TAG, "startInternalActivity: class=" + className + " data=" + data);

		Gson gson = new Gson();
		NotificationData notificationData = gson.fromJson(data, NotificationData.class);

		if(BuildConfig.DEBUG) {
			Log.v(TAG, "got notification: " + gson.toJson(notificationData));
		}

		boolean fallbackToBrowserIntent = false;
		switch(className) {
			case "_open_browser":
				IntentHelper.sendBrowserIntent(mContext, notificationData);
				break;
			case "_send_intent":
				if(!IntentHelper.sendImplicitActivityIntent(mContext, notificationData)) {
					fallbackToBrowserIntent = true;
				}
				break;
			default:
				if(!IntentHelper.sendExplicitActivityIntent(mContext, className, notificationData)) {
					fallbackToBrowserIntent = true;
				}
				break;
		}

		if(fallbackToBrowserIntent && notificationData.target_url != null) {
			IntentHelper.sendBrowserIntent(mContext, notificationData);
		}
	}

	@SuppressWarnings("unused")
    @Rpc(description = "Returns the device name")
	public String getDeviceName() {
		return Build.DEVICE;
	}

	@SuppressWarnings("unused")
	@Rpc(description = "Returns the device manufacturer")
	public String getDeviceManufacturer() {
		return Build.MANUFACTURER;
	}

	@SuppressWarnings("unused")
	@Rpc(description = "Returns the device model")
	public String getDeviceModel() {
		return Build.MODEL;
	}

	@SuppressWarnings("unused")
	@Rpc(description = "Returns the device ABI")
	public String getDeviceABI() {
		return PyEmbedded.getCompiledABI();
	}

	@SuppressWarnings("unused")
	@Rpc(description = "Returns the device product name")
	public String getDeviceProductName() {
		return Build.PRODUCT;
	}

	@SuppressWarnings("unused")
	@Rpc(description = "Restart player")
	public void restartPlayer() {
		Log.d(TAG, "restart player");
		if(mContext instanceof AceStreamEngineService) {
			((AceStreamEngineService)mContext).notifyRestartPlayer();
		}
	}

	@SuppressWarnings("unused")
	@Rpc(description = "Playlist was updated")
	public void onPlaylistUpdated() {
		if(mContext instanceof AceStreamEngineService) {
			((AceStreamEngineService) mContext).notifyPlaylistUpdated();
		}
	}

	@SuppressWarnings("unused")
	@Rpc(description = "EPG was updated")
	public void onEPGUpdated() {
		if(mContext instanceof AceStreamEngineService) {
			((AceStreamEngineService) mContext).notifyEPGUpdated();
		}
	}

	@SuppressWarnings("unused")
	@Rpc(description = "Settings were updated")
	public void onSettingsUpdated() {
		Log.d(TAG, "onSettingsUpdated");
		if(mContext instanceof AceStreamEngineService) {
			((AceStreamEngineService)mContext).notifySettingsUpdated();
		}
	}

	@SuppressWarnings("unused")
	@Rpc(description="Get app dir")
	public String getAppDir() {
		return AceStream.filesDir();
	}

	@SuppressWarnings("unused")
	@Rpc(description="Get app id")
	public String getAppId() {
		return AceStream.getApplicationId();
	}

	@SuppressWarnings("unused")
	@Rpc(description="Get app version code")
	public int getAppVersionCode() {
		return AceStream.getApplicationVersionCode();
	}

	@SuppressWarnings("unused")
	@Rpc(description="Get app version name")
	public String getAppVersionName() {
		return AceStream.getApplicationVersionName();
	}

	@SuppressWarnings("unused")
	@Rpc(description="Get engine version code")
	public int getEngineVersionCode() {
		return AceStreamEngineBaseApplication.getEngineVersionCode();
	}

	@SuppressWarnings("unused")
	@Rpc(description="Get arch")
	public String getArch() {
		return AceStreamEngineBaseApplication.getArch();
	}

	@SuppressWarnings("unused")
	@Rpc(description="Unzip file")
	public boolean unzipFile(
			@RpcParameter(name="src") final String src,
			@RpcParameter(name="dest") final String dest
	) {
		try {
			MiscUtils.unzip(src, dest, true, true);
			return true;
		}
		catch(Exception e) {
			Log.e(TAG, "Failed to unzip file", e);
			return false;
		}
	}
}
