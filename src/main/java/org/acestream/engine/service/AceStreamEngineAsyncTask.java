package org.acestream.engine.service;

import java.lang.ref.WeakReference;

import android.os.AsyncTask;

public abstract class AceStreamEngineAsyncTask extends
		AsyncTask<Void, Long, Boolean> {
	
	private WeakReference<IAceStreamEngineAsyncTaskListener> mListener = null;
	private WeakReference<AceStreamEngineNotificationManager> mNotificationManager = null;
	
	public void setNotificationManager(AceStreamEngineNotificationManager nm) {
		mNotificationManager = new WeakReference<AceStreamEngineNotificationManager>(nm);
	}
	
	public AceStreamEngineNotificationManager getNM() {
		return mNotificationManager.get();
	}
	
	public void setListener(IAceStreamEngineAsyncTaskListener listener) {
		mListener = new WeakReference<IAceStreamEngineAsyncTaskListener>(listener);
	}
	
	public IAceStreamEngineAsyncTaskListener getListener() {
		return mListener.get();
	}
	
	public abstract void terminate();

}
