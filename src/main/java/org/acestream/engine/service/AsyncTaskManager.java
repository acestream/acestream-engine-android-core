package org.acestream.engine.service;

import java.lang.ref.WeakReference;


import android.os.Build;
import android.util.Log;

public class AsyncTaskManager
	implements Runnable {

	private static String TAG = "AceStream/Service";
	
	public static final int TASK_NONE = -1;
	public static final int TASK_UNPACK = 0;
	public static final int TASK_TRY_TO_CONNECT = 1;
	
	public int mTaskType = TASK_NONE;
	public WeakReference<IAceStreamEngineAsyncTaskListener> mTaskListener = null;
	private WeakReference<AceStreamEngineNotificationManager> mNotificationManager = null;
	public AceStreamEngineAsyncTask mTask = null;
	
	public AsyncTaskManager() {
	}
	
	public AsyncTaskManager(int type, IAceStreamEngineAsyncTaskListener listener) {
		mTaskType = type;
		mTaskListener = new WeakReference<IAceStreamEngineAsyncTaskListener>(listener);
	}
	
	public void setTaskType(int type) {
		mTaskType = type;
	}
	
	public int getTaskType() {
		return mTaskType;
	}
	
	public void setListener(IAceStreamEngineAsyncTaskListener listener) {
		mTaskListener = new WeakReference<IAceStreamEngineAsyncTaskListener>(listener);
	}
	
	public void setNotificationManager(AceStreamEngineNotificationManager nm) {
		mNotificationManager = new WeakReference<AceStreamEngineNotificationManager>(nm);
	}
	
	public void stop() {
		if(mTask != null) {
			mTask.terminate();
		}
	}
	
	public boolean isTerminated() {
		if(mTask != null) {
			return mTask.isCancelled();
		}
		return false;
	}
	
	@Override
	public void run() {
		if(mTaskListener == null) {
			Log.w(TAG, "Listener not specified");
			notifyError();
			return;
		}
		
		IAceStreamEngineAsyncTaskListener listener = mTaskListener.get();
		if(listener != null) {
			if(mTaskType == TASK_UNPACK) {
				mTask = new UnpackTask(listener);
			}
			else if(mTaskType == TASK_TRY_TO_CONNECT) {
				mTask = new ConnectTask(listener);
			}
			else {
				Log.w(TAG, "Trying to run unknown task");
				notifyError();
				return;
			}
			
			if(mNotificationManager != null) {
				mTask.setNotificationManager(mNotificationManager.get());
			}
	
			if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
				mTask.executeOnExecutor(AceStreamEngineAsyncTask.THREAD_POOL_EXECUTOR);
			}
			else {
				mTask.execute();
			}
		}
	}
	
	private void notifyError() {
		IAceStreamEngineAsyncTaskListener listener = mTaskListener.get();
		if(listener != null) {
			listener.OnAceStreamAsyncTaskComplete(false);
		}
	}

}
