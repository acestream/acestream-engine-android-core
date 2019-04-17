package org.acestream.engine.service;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

import android.util.Log;
import org.acestream.engine.R;

public class ConnectTask extends AceStreamEngineAsyncTask {

	private static final String TAG = "AceStream/ConnectTask";
	
	private static final int CONNECT_ATTEMPTS = 1200;
	private static final int CONNECT_SLEEP = 1000;
	private static final String CONNECT_HOST = "127.0.0.1";
	private static final int CONNECT_PORT = 62062;
	
	public ConnectTask(IAceStreamEngineAsyncTaskListener listener) {
		setListener(listener);
	}
	
	@Override
	public void terminate() {
		cancel(true);
	}

	@Override
	protected Boolean doInBackground(Void... params) {
		boolean status = false;
		
		int attempts = 0;
		String last_error = "";
		updateProgress(false);
		while( attempts < CONNECT_ATTEMPTS && !status ) {
			if(isCancelled()) {
				Log.w(TAG, "Connect canceled");
				return false;
			}
			try {
				Socket socket = new Socket();
				socket.connect(new InetSocketAddress(CONNECT_HOST, CONNECT_PORT), 
						CONNECT_ATTEMPTS * CONNECT_SLEEP - (attempts * CONNECT_SLEEP));
				Log.d(TAG, "Connected attempts: " + String.valueOf(attempts));
				status = true;
				socket.close();
			}
			catch (SocketTimeoutException e) {
				attempts = CONNECT_ATTEMPTS;
			}
			catch (IOException e) {
				attempts += 1;
				last_error = e.toString();
				
				try {
					Thread.sleep(CONNECT_SLEEP);
				} catch (InterruptedException e1) {
					//e1.printStackTrace();
				}
			}
		}
		if(!status) {
			Log.w(TAG, "Connect error: " + last_error);
		}
		return status;
	}
	
	@Override 
    protected void onPostExecute(Boolean status) {
		updateProgress(true);
		IAceStreamEngineAsyncTaskListener listener = getListener();
		if(listener != null) {
			listener.OnAceStreamAsyncTaskComplete(status);
		}
    }
	
	private void updateProgress(boolean finished) {
		AceStreamEngineNotificationManager nm = getNM();
		if(nm != null) {
			nm.notifyIndeterminate(finished ? R.string.notify_connect_complete : R.string.notify_connect, !finished);
		}
	}

}
