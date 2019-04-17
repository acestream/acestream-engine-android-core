package org.acestream.engine.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

import android.os.Build;
import android.util.Log;

import org.acestream.engine.AceStreamEngineBaseApplication;
import org.acestream.engine.BuildConfig;

public class LogcatOutputStreamWriter {

	private static final String TAG = "AS/LogcatOutput";
	
	private static final String LINE_SEPARATOR = System.getProperty("line.separator");

	private File mOutputFile = null;
	private volatile boolean mThreadStopping = false;
	private volatile boolean mThreadStopped = true;
	private volatile boolean mRestartFlag = false;

	private static class LogcatOutputHolder {
		private final static LogcatOutputStreamWriter instance = new LogcatOutputStreamWriter();
	}
	
	public static LogcatOutputStreamWriter getInstanse() {
		return LogcatOutputHolder.instance;
	}
	
	private LogcatOutputStreamWriter() {
	}
	
	public void setOutputFile(File file) {
		mOutputFile = file;
	}
	
	public LogcatOutputStreamWriter(File file) {
		mOutputFile = file;
	}
	
	private synchronized void setStopping() {
		mThreadStopping = true;
	}
	
	private synchronized boolean stopping() {
		return mThreadStopping;
	}

	private void rotateLogFile() {
		// rotate
		File rotatedFile = new File(mOutputFile.getAbsolutePath() + ".1");
		if(rotatedFile.exists()) {
			rotatedFile.delete();
		}
		mOutputFile.renameTo(rotatedFile);
		mOutputFile.delete();
	}
	
	Runnable cleaner = new Runnable() {
		public void run() {
			try {
				Runtime.getRuntime().exec("/system/bin/logcat -c");
			} catch (IOException e) {
				Log.w(TAG, "Cannot clear logcat");
			}
		}
	};
	
	Runnable logger = new Runnable() {
		public void run() {
			Process process;
			boolean debugLogging = AceStreamEngineBaseApplication.isDebugLoggingEnabled();
			int maxLogSizeMb = debugLogging ? 10 : 1;

			Log.d(TAG, "logcat thread started: debug=" + debugLogging + " max_mb=" + maxLogSizeMb);
			try {
				ArrayList<String> commandLine = new ArrayList<>();
				commandLine.add("/system/bin/logcat");
				commandLine.add("-v");
				commandLine.add("time");
				commandLine.add(debugLogging ? "*:V" : "*:E");
				process = Runtime.getRuntime().exec(commandLine.toArray(new String[0]));
				mThreadStopped = false;
			} catch (IOException e) {
				Log.w(TAG, "Cannot execute logcat");
				return;
			}
			
			BufferedReader reader;
			try {
				reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

				// rotate on start
				if(mOutputFile.exists() && mOutputFile.length() > maxLogSizeMb*1024*1024) {
					Log.d(TAG, "rotate on start: file_size=" + mOutputFile.length() + " max_mb=" + maxLogSizeMb);
					rotateLogFile();
				}
				
				FileOutputStream fs = new FileOutputStream(mOutputFile, true);
				String line = null;
				int bytesCount = 0;
				byte[] separatorBytes = LINE_SEPARATOR.getBytes();
				int separatorLen = separatorBytes.length;

				while(!stopping()) {
					if(bytesCount >= maxLogSizeMb*1024*1024) {
						Log.d(TAG, "rotate: bytes=" + bytesCount + " max_mb=" + maxLogSizeMb);
						fs.close();
						rotateLogFile();
						fs = new FileOutputStream(mOutputFile, true);
						bytesCount = 0;
					}

					while(!stopping()) {
						if(reader.ready()) {
							line = reader.readLine();
							break;
						}
						else {
							Thread.sleep(1000);
						}
					}

					if(line == null || stopping()) {
						break;
					}

					boolean skipLine = true;
					if(line.contains(" E/")) {
						// log all errors
						skipLine = false;
					}
					else if(line.contains("AceStream/")) {
						// log all tags with "AceStream/" prefix
						skipLine = false;
					}
					else if(line.contains("AS/")) {
						// log all tags with "AS/" prefix
						skipLine = false;
					}
					else if(line.contains("/Appodeal")) {
						// log all tags with "Appodeal/" prefix
						skipLine = false;
					}
					else if(line.contains("/Ads")) {
						skipLine = false;
					}
					else if(line.contains("/VLC")) {
						// log all tags with "VLC/" prefix
						skipLine = false;
					}
					else if(line.contains(com.connectsdk.core.Util.T)) {
						// log all ConnectSDK tags
						skipLine = false;
					}

					if(skipLine) {
						continue;
					}

					byte[] lineBytes = line.getBytes();
                    fs.write(lineBytes);
                    fs.write(separatorBytes);

                    bytesCount += lineBytes.length + separatorLen;
				}
				
				fs.close();
			}
			catch (Exception e) {
				Log.e(TAG, "Error in logcat thread", e);
			}
				
			Log.d(TAG, "Exiting thread...");
			mThreadStopped = true;

			if(mRestartFlag || !stopping()) {
                mRestartFlag = false;
				Log.d(TAG, "restart");
				start();
			}
		}
	};
	
	public void cleanLogcat() {
		Thread thread = new Thread(cleaner);
		thread.start();
	}
	
	public void start() {
		if(mThreadStopped) {
			Log.d(TAG, "Starting logcat thread");
			mThreadStopping = false;
			Thread thread = new Thread(logger);
			thread.start();
		}
		else {
			Log.d(TAG, "logcat thread already running");
		}
	}
	
	public void stop() {
        Log.d(TAG, "stop");
		setStopping();
	}

	public void restart() {
	    Log.d(TAG, "restart");
	    mRestartFlag = true;
        setStopping();
    }
}
