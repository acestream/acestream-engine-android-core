package org.acestream.engine.util;

import org.acestream.sdk.AceStream;
import org.acestream.sdk.utils.MiscUtils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

public class NetworkUtil {

	public static boolean isConnected2AvailableNetwork(boolean enableMobile) {
		if(AceStream.isAndroidTv()) {
			return true;
		}
		else {
			ConnectivityManager cm = (ConnectivityManager) AceStream.context().getSystemService(Context.CONNECTIVITY_SERVICE);
			if(cm == null) {
				return true;
			}
			else {
				NetworkInfo ni = cm.getActiveNetworkInfo();
				if (ni != null && ni.isConnectedOrConnecting()) {
					return enableMobile || !MiscUtils.isMobileNetwork(ni);
				} else {
					return false;
				}
			}
		}
	}
}
