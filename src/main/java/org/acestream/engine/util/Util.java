package org.acestream.engine.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import org.acestream.engine.AceStreamEngineBaseApplication;
import org.acestream.sdk.AceStream;

public class Util {

	public static String readPrivateFileLine(String filename) {
		File f = new File(AceStream.filesDir(), filename);
		if(!f.exists()) {
			return "";
		}
		
		String data = "";
		try {
			BufferedReader reader = new BufferedReader(new FileReader(f.getAbsolutePath()));
	        data = reader.readLine();
	        reader.close();

			if(data == null) {
				// can be null but this function must return nonnull
				data = "";
			}
		}
		catch (IOException e) {
			e.printStackTrace();
		}
		return data;
	}
	
	public static void writePrivateFileLine(String filename, String data) {
		File f = new File(AceStream.filesDir(), filename);
		try {
			if(!f.exists()) {
				f.createNewFile();
			}
			
			BufferedWriter writer = new BufferedWriter(new FileWriter(f.getAbsolutePath(), false));
			writer.write(data);
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static boolean isUnpackRequired() {
		File mainPy = new File(AceStream.filesDir(), AceStreamEngineBaseApplication.DEFAULT_SCRIPT);
		if(!mainPy.exists()) {
			return true;
		}
		
		String vdata = readPrivateFileLine(AceStreamEngineBaseApplication.VERSION_FILE);
		if(vdata == null
			|| vdata.compareTo("") == 0
			|| vdata.compareTo(AceStreamEngineBaseApplication.versionName()) != 0) {
			return true;
		}
		return false;
	}

}
