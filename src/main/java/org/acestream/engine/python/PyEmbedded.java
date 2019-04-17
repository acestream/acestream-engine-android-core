package org.acestream.engine.python;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.acestream.sdk.AceStream;
import org.jetbrains.annotations.Nullable;

import android.content.Context;
import android.util.Log;
import android.os.Process;

public class PyEmbedded {
	
	private static final String TAG = "AceStream/PyAndroid";

	private static boolean isLibraryLoaded = false;
	static {
		try {
			System.loadLibrary("pyembedded");
			isLibraryLoaded = true;
			Log.d(TAG, "libpyembedded loaded");
		}
		catch(UnsatisfiedLinkError e) {
			isLibraryLoaded = false;
			Log.e(TAG, "Cannot load libpyembedded", e);
		}
	}

	private final List<String> mArgs;

	private final List<String> mEnv;
	private final AtomicInteger mPid;
	private final Context mContext ;
	private final Callback mCallback;
	private final String mAccessToken;
	
	private PyRpcProxy mProxy = null;
	
	private IPyFinishedListener mFinishListener = null;
	
	private FileDescriptor mFd;
	private OutputStream mOut;
	private InputStream mIn;

	private File mPythonBinary;
	private File mScript;

	private File mMaintainScript;
	private final List<String> mMaintainArgs;
	private final AtomicInteger mMaintainPid;
	private PyRpcProxy mMaintainProxy = null;
	private FileDescriptor mMaintainFd;
	private OutputStream mMaintainOut;
	private InputStream mMaintainIn;
	private IPyFinishedListener mMaintainFinishListener = null;

	public interface Callback {
		void onShowDialog(String title, String text);
	}

	public PyEmbedded(Context context, Callback callback, int apiPort, int httpPort, String accessToken) throws Exception {
		mArgs = new ArrayList<String>();
		mEnv = new ArrayList<String>();
		mPid = new AtomicInteger(-1);
		mContext = context;
		mCallback = callback;

		mMaintainArgs = new ArrayList<>();
		mMaintainPid = new AtomicInteger(-1);
		mAccessToken = accessToken;

		String externalPath = AceStream.externalFilesDir();
		if(externalPath == null) {
			throw new Exception("Missing external dir");
		}
		String internalPath = AceStream.filesDir();

		mPythonBinary = new File(internalPath + "/python/bin/python");
		mScript = new File(internalPath + "/main.py");
		mMaintainScript = new File(internalPath + "/maintain/maintain.py");
		
		// args
		mArgs.add("--log-file");
		mArgs.add(externalPath + "/acestream.log");

		if(mAccessToken != null) {
			mArgs.add("--access-token");
			mArgs.add(mAccessToken);
		}

		mArgs.add("--api-port");
		mArgs.add(String.valueOf(apiPort));

		mArgs.add("--http-port");
		mArgs.add(String.valueOf(httpPort));
		
		// env vars
		mEnv.add("PYTHONHOME=" + internalPath + "/python");
		mEnv.add("PYTHONPATH=" + internalPath + "/python/lib/python2.7/lib-dynload:" +
				internalPath + "/python/lib/python27.zip:" +
				internalPath + "/python/lib/python2.7");

		mEnv.add("LD_LIBRARY_PATH=" + internalPath + "/python/lib:" + 
				internalPath + "/python/lib/python2.7/lib-dynload");
		mEnv.add("TEMP=" + externalPath + "/tmp");
		mEnv.add("ACESTREAM_HOME=" + externalPath);

		// HACK!
		// Need to set this env vars to prevent crashing engine right after start in
		// system libc.so when calling gmtime/gmtime_r.
		// see: https://code.briarproject.org/akwizgran/briar/issues/903
		mEnv.add("ANDROID_ROOT=/system");
		mEnv.add("ANDROID_DATA=/data");
	}

	private String join(List<String> list, String delim) {
		StringBuilder sb = new StringBuilder();
		for(int i = 0; i < list.size(); i++) {
			sb.append(list.get(i));
			if(i != list.size() - 1)
				sb.append(delim);
		}
		return sb.toString();
	}
	
	public void setOnPythonProcessFinishedListener(IPyFinishedListener listener) {
		mFinishListener = listener;
	}

	public void setOnMaintainProcessFinishedListener(IPyFinishedListener listener) {
		mMaintainFinishListener = listener;
	}
	
	public void start() throws Exception {
		if(mAccessToken == null) {
			throw new IllegalArgumentException("access token is null");
		}
		if(!isLibraryLoaded) {
            throw new Exception("Cannot load library.");
		}
		if(isAlive()) {
			throw new Exception("Process already running.");
		}
		if(!mPythonBinary.exists()) {
			throw new Exception("Python binary does not exist: " + mPythonBinary.getAbsolutePath());
		}
		if(!mScript.exists()) {
			throw new Exception("Script file does not exist: " + mScript.getAbsolutePath());
		}
		
		mProxy = new PyRpcProxy(mContext, mCallback,true);
		mProxy.startLocal();

		mEnv.add("AP_HOST=" + mProxy.getHost());
		mEnv.add("AP_PORT=" + String.valueOf(mProxy.getPort()));
		if(mProxy.getSecret() != null) {
			mEnv.add("AP_HANDSHAKE=" + mProxy.getSecret());
		}
		
		int[] pid = new int[1];
		String pythonBinaryPath = mPythonBinary.getAbsolutePath();
		String scriptPath = mScript.getAbsolutePath();
		
		List<String> argsArray = new ArrayList<String>();
		argsArray.add(scriptPath);
		argsArray.addAll(mArgs);
		
		String[] argv = argsArray.toArray(new String[argsArray.size()]);
		String[] env = mEnv.toArray(new String[mEnv.size()]);
		
		mFd = runScript(pythonBinaryPath, argv, env, mScript.getParent(), pid);
		
		mPid.set(pid[0]);
		Log.d(TAG, "Start python process: " + String.valueOf(mPid.get()));
		
		mOut = new FileOutputStream(mFd);
		mIn = new FileInputStream(mFd);

		new Thread(new Runnable() {
			@Override
			public void run() {
				int result = waitPid(mPid.get());
				Log.d(TAG, "Ended python process: pid=" + mPid.get() + " exitcode=" + result);

				mPid.set(-1);
				try {
					mIn.close();
		        } catch (IOException e) {
		        	e.printStackTrace();
		        }
				try {
					mOut.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
				if(mFinishListener != null) {
					mFinishListener.run();
				}
			}
		}).start();
	}

	public void startMaintain(@Nullable List<String> args) throws Exception {
		if(!isLibraryLoaded) {
			throw new Exception("Cannot load library.");
		}
		if(isMaintainAlive()) {
			throw new Exception("Process already running.");
		}
		if(!mPythonBinary.exists()) {
			throw new Exception("Python binary does not exist: " + mPythonBinary.getAbsolutePath());
		}
		if(!mMaintainScript.exists()) {
			throw new Exception("Script file does not exist: " + mMaintainScript.getAbsolutePath());
		}

		mMaintainProxy = new PyRpcProxy(mContext, mCallback,true);
		mMaintainProxy.startLocal();

		mEnv.add("AP_HOST=" + mMaintainProxy.getHost());
		mEnv.add("AP_PORT=" + String.valueOf(mMaintainProxy.getPort()));
		if(mMaintainProxy.getSecret() != null) {
			mEnv.add("AP_HANDSHAKE=" + mMaintainProxy.getSecret());
		}

		int[] pid = new int[1];
		String pythonBinaryPath = mPythonBinary.getAbsolutePath();
		String scriptPath = mMaintainScript.getAbsolutePath();

		List<String> argsArray = new ArrayList<>();
		argsArray.add(scriptPath);
		argsArray.addAll(mMaintainArgs);
		if(args != null) {
			argsArray.addAll(args);
		}

		String[] argv = argsArray.toArray(new String[argsArray.size()]);
		String[] env = mEnv.toArray(new String[mEnv.size()]);

		mMaintainFd = runScript(pythonBinaryPath, argv, env, mMaintainScript.getParent(), pid);

		mMaintainPid.set(pid[0]);
		Log.d(TAG, "Start maintain process: " + String.valueOf(mMaintainPid.get()));

		mMaintainOut = new FileOutputStream(mMaintainFd);
		mMaintainIn = new FileInputStream(mMaintainFd);

		new Thread(new Runnable() {
			@Override
			public void run() {
				int result = waitPid(mMaintainPid.get());
				Log.d(TAG, "Ended maintain process: " + String.valueOf(mMaintainPid.get()) + ". Exit code = " + String.valueOf(result));

				mMaintainPid.set(-1);
				try {
					mMaintainIn.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
				try {
					mMaintainOut.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
				onMaintainFinished();

			}
		}).start();
	}

	private void onMaintainFinished() {
		if(mMaintainFinishListener != null) {
			mMaintainFinishListener.run();
		}
	}

	public void kill() {
		kill(true);
	}
	
	public void kill(boolean resetListener) {
		if(isAlive()) {
			if(resetListener) {
				setOnPythonProcessFinishedListener(null);
			}
			
			Process.killProcess(mPid.get());
			Log.d(TAG, "Killed python process:" + String.valueOf(mPid.get()));
		}
		if(mProxy != null)
			mProxy.shutdown();
	}
	
	public boolean isAlive() {
		return mPid.get() != -1 && (mFd != null && mFd.valid());
	}

	public boolean isMaintainAlive() {
		return mMaintainPid.get() != -1 && (mMaintainFd != null && mMaintainFd.valid());
	}

	private native FileDescriptor runScript(String binary, String[] argv, String[] env, String workingDir, int[] pid);
	private native int waitPid(int pid);
	public static native String getCompiledABI();
}
