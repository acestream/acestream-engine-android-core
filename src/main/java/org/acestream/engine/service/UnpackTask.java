package org.acestream.engine.service;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.acestream.engine.AceStreamEngineBaseApplication;
import org.acestream.engine.BuildConfig;
import org.acestream.engine.util.Util;
import org.acestream.engine.python.PyEmbedded;

import android.content.res.AssetManager;
import android.content.res.Resources;
import android.util.Log;
import org.acestream.engine.R;
import org.acestream.sdk.AceStream;
import org.acestream.sdk.utils.MiscUtils;

public class UnpackTask extends AceStreamEngineAsyncTask {

	private static final String TAG = "AceStream/UnpackTask";
	
	private static final String PRIVATE_PY = "private_py.zip";
	private static final String PRIVATE_RES = "private_res.zip";
	private static final String PUBLIC_RES = "public_res.zip";
	
	private static final String PRIVATE_PY_HASH_FILE = ".ppy";
	private static final String PRIVATE_HASH_FILE = ".private";
	private static final String PUBLIC_HASH_FILE = ".public";
	
	private int mFullSize = 0;
	private int mProcessedSize = 0;
	private String mDeviceRealABI = null;
	private String mResourcePrefix = null;
	
	public UnpackTask(IAceStreamEngineAsyncTaskListener listener) {
		setListener(listener);
		mDeviceRealABI = PyEmbedded.getCompiledABI();
		mResourcePrefix = mDeviceRealABI;
	}
	
	@Override
	public void terminate() {
		cancel(true);
	}
	
	@Override
	protected Boolean doInBackground(Void... params) {
		//R.raw rawRes = new R.raw();
		//Field[] fields= R.raw.class.getFields();

		String privateFilesDir = AceStream.filesDir();
		String publicFilesDir = AceStream.externalFilesDir();
		if(publicFilesDir == null) {
			Log.e(TAG, "missing external files dir");
			return false;
		}

		boolean status = true;
		Resources resources = AceStreamEngineBaseApplication.resources();
		AssetManager assetManager = resources.getAssets();

		List<String> filesToUnpack = new ArrayList<>();
		try {
			String[] list = assetManager.list("engine");
			Log.v(TAG, "unpack: assets=" + MiscUtils.dump(list));
			if(list != null) {
				for (String filename : list) {
					filesToUnpack.add("engine/" + filename);
				}
			}
		}
		catch(IOException e) {
			Log.e(TAG, "Failed to unpack engine", e);
		}

		String fileName;
		InputStream content;
		for(String path: filesToUnpack) {
			try {
				content = assetManager.open(path);
				mFullSize += content.available();
			} catch (Exception e) {
			}
		}
		publishProgress((long) 0);

		for(String path: filesToUnpack) {
			try {
				Log.d(TAG, "unpack: path=" + path);

				fileName = path.substring(path.lastIndexOf('/') + 1);
				content = assetManager.open(path);

				if(fileName.endsWith(mResourcePrefix + "_" + PRIVATE_PY)) {
					Log.d(TAG, "###SELECT "+fileName);
					unpackIfNeeded(content, privateFilesDir, PRIVATE_PY_HASH_FILE);
				}
				else if(fileName.endsWith(mResourcePrefix + "_" + PRIVATE_RES)) {
					Log.d(TAG, "###SELECT "+fileName);
					unpackIfNeeded(content, privateFilesDir, PRIVATE_HASH_FILE);
				}
				else if (fileName.endsWith(PUBLIC_RES)) {
					if(AceStream.canWriteToExternalFilesDir())
						unpackIfNeeded(content, publicFilesDir, PUBLIC_HASH_FILE);
				}

				if(isCancelled()) {
					Log.w(TAG, "Unpack canceled");
					return false;
				}
			} catch (Exception e) {
				status = false;
				Log.e(TAG, "Unpack error", e);
			}
		}
		if(status) {
			Util.writePrivateFileLine(AceStreamEngineBaseApplication.VERSION_FILE, AceStreamEngineBaseApplication.versionName());
		}

		Log.d(TAG, "Unpack task finished: status=" + status);

		return status;
	}
	
	@Override
	protected void onProgressUpdate(Long... progress) {
		mProcessedSize += progress[0];
		updateProgress(false);
    }
	
	@Override 
    protected void onPostExecute(Boolean status) {
		mProcessedSize = mFullSize;
		updateProgress(true);
		IAceStreamEngineAsyncTaskListener listener = getListener();
		if(listener != null) {
			listener.OnAceStreamAsyncTaskComplete(status);
		}
    }
	
	private void updateProgress(boolean finished) {
		AceStreamEngineNotificationManager nm = getNM();
		if(nm != null) {
			nm.notifyProgress(finished ? R.string.notify_unpack_complete : R.string.notify_unpack, mProcessedSize, mFullSize);
		}
	}
	
	private void unpackIfNeeded(InputStream content, String destination, String hashfile) throws Exception {
		String curHash = MiscUtils.hash(content);
		String savedHash = Util.readPrivateFileLine(hashfile);
		Log.d(TAG, "Res hash=" + curHash + " savedHash=" + savedHash);

		if(BuildConfig.DEBUG && BuildConfig.alwaysUnpackEngineInDevBuild) {
			Log.e(TAG, "FORCE UNPACK");
			savedHash = "";
		}

		if(curHash.compareTo(savedHash) != 0) {
			unzip(content, destination + "/", true);
			Util.writePrivateFileLine(hashfile, curHash);
		}
	}
	
	private void unzip(InputStream inputStream, String destination, boolean replace) throws Exception {
		final int BUFFER_SIZE = 4096;
		BufferedOutputStream bufferedOutputStream = null;
		
		File destDir = new File(destination);
		if(!destDir.exists()) {
			destDir.mkdir();
		}
		
		inputStream.reset();
		
		ZipInputStream zipInputStream = new ZipInputStream(new BufferedInputStream(inputStream));
	    ZipEntry zipEntry;
	    while((zipEntry = zipInputStream.getNextEntry()) != null) {
	    	String zipEntryName = zipEntry.getName();

	    	File fRem = new File(destination + zipEntryName);
	    	if(fRem.exists()) {
	    		if(replace) {
	    			boolean b = MiscUtils.deleteDir(fRem);
	    			if(!b) {
	    				Log.e(TAG, "Unpack failed to delete " + destination + zipEntryName);
	    			}
	    		}
	    	}
	    	
	    	File fUnzip = new File(destination + zipEntryName);
	    	if(!fUnzip.exists()) {
	    		if(zipEntry.isDirectory()) {
	    			fUnzip.mkdirs();
					MiscUtils.chmod(fUnzip, 0755);
	    		}
	    		else {
	    			if(!fUnzip.getParentFile().exists()) {
	    				fUnzip.getParentFile().mkdirs();
						MiscUtils.chmod(fUnzip.getParentFile(), 0755);
	    			}
				 	
			        byte buffer[] = new byte[BUFFER_SIZE];
			        bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(fUnzip), BUFFER_SIZE);
			        int count;

			        while( (count = zipInputStream.read(buffer, 0, BUFFER_SIZE)) != -1 ) {
			        	bufferedOutputStream.write(buffer, 0, count);
			        }
			        bufferedOutputStream.flush();
			        bufferedOutputStream.close();
	    		}
	    	}
	    	
	    	if(fUnzip.getName().endsWith("python") 
	    			|| fUnzip.getName().endsWith(".so") 
	    			|| fUnzip.getName().endsWith(".xml")
	    			|| fUnzip.getName().endsWith(".py")
	    			|| fUnzip.getName().endsWith(".zip")) {
				MiscUtils.chmod(fUnzip, 0755);
		    }
	    	Log.d(TAG,"Unpacked " + zipEntryName);
	    	publishProgress(zipEntry.getCompressedSize());
	    }
	    zipInputStream.close();
	}
	
}
