package org.acestream.engine;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;
import com.connectsdk.device.ConnectableDevice;

import org.acestream.sdk.SelectedPlayer;
import org.acestream.engine.acecast.client.AceStreamRemoteDevice;

import static org.acestream.sdk.Constants.OUR_PLAYER_NAME;

public class ResolveItem {
    private final static String TAG = "AceStream/RI";

    private Context mContext;
    private PackageManager mPm;
    private int mIconDpi;

    private int mType;
    private String mPackageName;
    private String mClassName;
    private CharSequence mLabel;
    private Drawable mIconDrawable;
    private ConnectableDevice mConnectableDevice;
    private AceStreamRemoteDevice mAceStreamRemoteDevice;
    private boolean mUseDarkIcons;

    public static ResolveItem getOurPlayer(Context context) {
        Resources res = context.getResources();
        ResolveItem item = new ResolveItem(context, false);
        item.mType = SelectedPlayer.OUR_PLAYER;
        item.mLabel = OUR_PLAYER_NAME;
        item.mIconDrawable = res.getDrawable(R.drawable.acestreamplayer);
        return item;
    }

    private ResolveItem(Context context, boolean useDarkIcons) {
        mContext = context;
        mUseDarkIcons = useDarkIcons;
        mPm = mContext.getPackageManager();

        final ActivityManager am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        if(am != null) {
            mIconDpi = am.getLauncherLargeIconDensity();
        }
    }

    ResolveItem(Context context, boolean useDarkIcons, ResolveInfo ri) {
        this(context, useDarkIcons);
        mType = SelectedPlayer.LOCAL_PLAYER;
        mPackageName = ri.activityInfo.packageName;
        mClassName = ri.activityInfo.name;
        mLabel = ri.loadLabel(mPm);
        mIconDrawable = loadIconForResolveInfo(ri);
    }

    ResolveItem(Context context, boolean useDarkIcons, ConnectableDevice device) {
        this(context, useDarkIcons);
        mType = SelectedPlayer.CONNECTABLE_DEVICE;
        mLabel = device.getFriendlyName();
        mConnectableDevice = device;
        int iconResourceId = PlaybackManager.getIconForDevice(device, mUseDarkIcons);
        if(iconResourceId != -1) {
            mIconDrawable = mContext.getResources().getDrawable(iconResourceId);
        }
    }

    ResolveItem(Context context, boolean useDarkIcons, AceStreamRemoteDevice device) {
        this(context, useDarkIcons);
        mType = SelectedPlayer.ACESTREAM_DEVICE;
        mLabel = device.getName();
        mAceStreamRemoteDevice = device;
        int iconResourceId = PlaybackManager.getIconForDevice(device, mUseDarkIcons);
        if(iconResourceId != -1) {
            mIconDrawable = mContext.getResources().getDrawable(iconResourceId);
        }
    }

    public int getType() {
        return mType;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public String getClassName() {
        return mClassName;
    }

    public CharSequence getLabel() {
        return mLabel;
    }

    public Drawable getIconDrawable() {
        return mIconDrawable;
    }

    public ConnectableDevice getConnectableDevice() {
        return mConnectableDevice;
    }

    public AceStreamRemoteDevice getAceStreamRemoteDevice() {
        return mAceStreamRemoteDevice;
    }

    private Drawable loadIconForResolveInfo(ResolveInfo ri) {
        Drawable dr;
        try {
            if (ri.resolvePackageName != null && ri.icon != 0) {
                dr = getIcon(mPm.getResourcesForApplication(ri.resolvePackageName), ri.icon);
                if (dr != null) {
                    return dr;
                }
            }
            final int iconRes = ri.getIconResource();
            if (iconRes != 0) {
                dr = getIcon(mPm.getResourcesForApplication(ri.activityInfo.packageName), iconRes);
                if (dr != null) {
                    return dr;
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Couldn't find resources for package", e);
        }
        return ri.loadIcon(mPm);
    }

    private Drawable getIcon(Resources res, int resId) {
        Drawable result;
        try {
            if(Build.VERSION.SDK_INT >= 15) {
                result = res.getDrawableForDensity(resId, mIconDpi);
            }
            else {
                result = res.getDrawable(resId);
            }
        } catch (Resources.NotFoundException e) {
            result = null;
        }
        return result;
    }
}