package org.acestream.engine.player;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.view.View;

import org.acestream.engine.AceStreamEngineBaseApplication;
import org.acestream.engine.BuildConfig;
import org.videolan.libvlc.util.AndroidUtil;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;

public class Utils {
    public final static boolean hasTsp;
    public static final boolean hasPiP;
    public final static boolean isAndroidTv;
    public final static boolean isPhone;
    public final static boolean hasNavBar;
    public final static boolean hasCombBar;

    static {
        hasNavBar = true;
        final Context ctx = AceStreamEngineBaseApplication.context();
        final PackageManager pm = ctx != null ? ctx.getPackageManager() : null;
        hasTsp = pm == null || pm.hasSystemFeature("android.hardware.touchscreen");
        isAndroidTv = pm != null && pm.hasSystemFeature("android.software.leanback");
        hasPiP = AndroidUtil.isOOrLater || AndroidUtil.isNougatOrLater && isAndroidTv;
        final TelephonyManager tm = ctx != null ? ((TelephonyManager) ctx.getSystemService(Context.TELEPHONY_SERVICE)) : null;
        isPhone = tm == null || tm.getPhoneType() != TelephonyManager.PHONE_TYPE_NONE;
        hasCombBar = false;
    }

    public static boolean canWriteSystemSettings(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.System.canWrite(context);
        }
        else {
            return true;
        }
    }

    public static String millisToString(long millis) {
        DecimalFormat format = (DecimalFormat) NumberFormat.getInstance(Locale.US);
        format.applyPattern("00");

        StringBuilder sb = new StringBuilder();
        if (millis < 0) {
            millis = -millis;
            sb.append("-");
        }

        millis /= 1000;
        int sec = (int) (millis % 60);
        millis /= 60;
        int min = (int) (millis % 60);
        millis /= 60;
        int hours = (int) millis;

        if (hours > 0)
            sb.append(hours).append(':').append(format.format(min)).append(':').append(format.format(sec));
        else
            sb.append(min).append(':').append(format.format(sec));

        return sb.toString();
    }

    public static void setBackgroundWithPadding(View v, int drawableId) {
        int left = v.getPaddingLeft();
        int right = v.getPaddingRight();
        int top = v.getPaddingTop();
        int bottom = v.getPaddingBottom();

        v.setBackgroundResource(drawableId);
        v.setPadding(left, top, right, bottom);
    }

    public static void setViewVisibility(View v, int visibility) {
        if (v != null)
            v.setVisibility(visibility);
    }

    public static String formatRateString(float rate) {
        return String.format(java.util.Locale.US, "%.2fx", rate);
    }

    public static String buildPkgString(String string) {
        return BuildConfig.APPLICATION_ID + "." + string;
    }
}
