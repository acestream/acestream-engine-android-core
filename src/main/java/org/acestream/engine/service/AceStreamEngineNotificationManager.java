package org.acestream.engine.service;

import java.util.concurrent.atomic.AtomicInteger;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.acestream.engine.AceStreamEngineBaseApplication;
import org.acestream.engine.R;
import org.acestream.sdk.AceStream;
import org.acestream.sdk.utils.VlcBridge;

public class AceStreamEngineNotificationManager {
	
	private final static AtomicInteger NextId = new AtomicInteger(0);
	
	public static int GenerateId() {
		return NextId.incrementAndGet();
	}
	
	public final static int NOTIFICATION_ID = GenerateId();
	public final static String SERVICE_NOTIFICATION_CHANNEL_ID = "org.acestream.service_notification_channel";
	public final static String DEFAULT_NOTIFICATION_CHANNEL_ID = "org.acestream.default_notification_channel";

	private Context mContext;
	private final NotificationManagerCompat mNotificationManager;
	private final NotificationCompat.Builder mBuilder;
	private Notification mLastNotification = null;

	public static void createNotificationChannels(Context ctx) {
		// Create the NotificationChannel, but only on API 26+ because
		// the NotificationChannel class is new and not in the support library

		// Register the channel with the system; you can't change the importance
		// or other notification behaviors after this
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationManager notificationManager = ctx.getSystemService(NotificationManager.class);
			if(notificationManager != null) {
				// Default channel
				NotificationChannel channel = new NotificationChannel(
						DEFAULT_NOTIFICATION_CHANNEL_ID,
						ctx.getString(R.string.default_notification_channel_name),
						NotificationManager.IMPORTANCE_DEFAULT);
				channel.setDescription(ctx.getString(R.string.default_notification_channel_description));
				notificationManager.createNotificationChannel(channel);

				// service channel (lower priority and no sounds)
				channel = new NotificationChannel(
						SERVICE_NOTIFICATION_CHANNEL_ID,
						ctx.getString(R.string.service_notification_channel_name),
						NotificationManager.IMPORTANCE_LOW);
				channel.setSound(null, null);
				channel.setDescription(ctx.getString(R.string.service_notification_channel_description));
				notificationManager.createNotificationChannel(channel);
			}
		}
	}

	public AceStreamEngineNotificationManager(Context context) {
		this(context, true);
	}

	public AceStreamEngineNotificationManager(Context context, boolean addQuitButton) {
		mContext = context;
		mNotificationManager = NotificationManagerCompat.from(mContext);
		mBuilder = new NotificationCompat.Builder(context, SERVICE_NOTIFICATION_CHANNEL_ID);

		Intent intent;
		PendingIntent contentIntent;
		if(AceStreamEngineBaseApplication.useVlcBridge()) {
			intent = VlcBridge.getMainActivityIntent();
			contentIntent = PendingIntent.getBroadcast(mContext, 0, intent, 0);
		}
		else {
			intent = new Intent(mContext, AceStreamEngineBaseApplication.getMainActivityClass());
			contentIntent = PendingIntent.getActivity(mContext, 0, intent, 0);
		}

		final PendingIntent quitIntent = PendingIntent.getBroadcast(
				mContext, 0, AceStream.getStopAppIntent(), 0);

		mBuilder
			.setSmallIcon(R.drawable.ic_acestream)
			.setContentTitle(mContext.getString(R.string.app_name))
			.setPriority(NotificationCompat.PRIORITY_DEFAULT)
			.setContentIntent(contentIntent)
			.setAutoCancel(true);

		if(addQuitButton) {
			mBuilder.addAction(new NotificationCompat.Action(R.drawable.ace_ic_close_normal,
					mContext.getResources().getString(R.string.menu_quit), quitIntent));
		}
	}
	
	public void notify(Notification notification) {
		notify(NOTIFICATION_ID, notification);
	}

	public void notify(int notificationId, Notification notification) {
		mNotificationManager.notify(notificationId, notification);
	}
	
	public void cancel() {
		cancel(NOTIFICATION_ID);
	}

	public void cancel(int notificationId) {
		mNotificationManager.cancel(notificationId);
	}

    public Notification simpleNotification(int resourceId) {
	    return simpleNotification(resourceId, -1);
    }

	public Notification simpleNotification(int resourceId, int iconDrawableId) {
		return simpleNotification(mContext.getString(resourceId), iconDrawableId);
	}

    public Notification simpleNotification(String text) {
	    return simpleNotification(text, -1);
    }

	public Notification simpleNotification(String text, int iconDrawableId) {
		if(text != null) {
			mBuilder.setContentText(text);
		}
		if(iconDrawableId != -1) {
		    mBuilder.setSmallIcon(iconDrawableId);
        }
		mBuilder.setProgress(0, 0, false);
		mBuilder.setTicker(text);
		return buildNotification();
	}
	
	private Notification progressNotification(int strRes, int progress, int max) {
		String text = mContext.getString(strRes);
		
		mBuilder.setContentText(text);
		mBuilder.setProgress(max, progress, false);
		mBuilder.setTicker(text);
		return buildNotification();
	}

	private Notification indeterminateNotification(int strRes, boolean start) {
		String text = mContext.getString(strRes);
		
		mBuilder.setContentText(text);
		mBuilder.setProgress(0, 0, start);
		mBuilder.setTicker(text);
		return buildNotification();
	}

	public void notifySimple(int resourceId) {
		notify(simpleNotification(resourceId));
	}

	public void notifyProgress(int strRes, int progress, int max) {
		notify(progressNotification(strRes, progress, max));
	}
	
	public void notifyIndeterminate(int strRes, boolean start) {
		notify(indeterminateNotification(strRes, start));
	}

	private Notification buildNotification() {
		mLastNotification = mBuilder.build();
		return mLastNotification;
	}

	@Nullable
	protected Notification getLastNotification() {
		return mLastNotification;
	}
}
