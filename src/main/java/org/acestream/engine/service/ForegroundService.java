package org.acestream.engine.service;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import android.app.Notification;
import android.util.Log;

import org.acestream.engine.BaseService;
import org.acestream.engine.R;
import org.acestream.sdk.AceStream;

public abstract class ForegroundService extends BaseService {

	private static String TAG = "AS/Service";
	
	private static final Class<?>[] mSetForegroundSignature = new Class[] { boolean.class };
	private static final Class<?>[] mStartForegroundSignature = new Class[] { int.class, Notification.class };
    private static final Class<?>[] mStopForegroundSignature = new Class[] { boolean.class };

    protected boolean mIsDelegatedService = false;
    private Method mSetForeground;
    private Method mStartForeground;
    private Method mStopForeground;
    
    private Object[] mSetForegroundArgs = new Object[1];
    private Object[] mStartForegroundArgs = new Object[2];
    private Object[] mStopForegroundArgs = new Object[1];
    
    private AceStreamEngineNotificationManager mNotificationManager;

    @Override
    public void onCreate() {
        super.onCreate();
        mIsDelegatedService = AceStream.getTargetApp() != null;
        if(!mIsDelegatedService) {
            mNotificationManager = new AceStreamEngineNotificationManager(this);
            try {
                mStartForeground = getClass().getMethod("startForeground", mStartForegroundSignature);
                mStopForeground = getClass().getMethod("stopForeground", mStopForegroundSignature);
            } catch (NoSuchMethodException e) {
                mStartForeground = mStopForeground = null;
            }
            try {
                mSetForeground = getClass().getMethod("setForeground", mSetForegroundSignature);
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException("OS doesn't have Service.startForeground OR Service.setForeground!");
            }
            Notification n = mNotificationManager.simpleNotification(R.string.notify_starting);
            startForegroundCompat(n);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(mNotificationManager != null) {
            stopForegroundCompat();
        }
    }
    
    public void showNotification(int strRes) {
        if(mNotificationManager != null) {
            mNotificationManager.notifySimple(strRes);
        }
    }
    
    public final AceStreamEngineNotificationManager getNotificationManager() {
    	return mNotificationManager;
    }
    
    private void invokeMethod(Method method, Object[] args) {
        try {
            method.invoke(this, args);
        } catch (InvocationTargetException e) {
            Log.e(TAG, "Unable to invoke method", e);
        } catch (IllegalAccessException e) {
            Log.e(TAG, "Unable to invoke method", e);
        }
    }
    
    protected void startForegroundCompat(Notification notification) {
        if (mStartForeground != null) {
        	mStartForegroundArgs[0] = Integer.valueOf(AceStreamEngineNotificationManager.NOTIFICATION_ID);
            mStartForegroundArgs[1] = notification;
            invokeMethod(mStartForeground, mStartForegroundArgs);
            return;
        }

        mSetForegroundArgs[0] = Boolean.TRUE;
        invokeMethod(mSetForeground, mSetForegroundArgs);
        mNotificationManager.notify(notification);
    }

    private void stopForegroundCompat() {
        if (mStopForeground != null) {
            mStopForegroundArgs[0] = Boolean.TRUE;
            invokeMethod(mStopForeground, mStopForegroundArgs);
            return;
        }

        mNotificationManager.cancel();
        mNotificationManager = null;
        mSetForegroundArgs[0] = Boolean.FALSE;
        invokeMethod(mSetForeground, mSetForegroundArgs);
    }
}
