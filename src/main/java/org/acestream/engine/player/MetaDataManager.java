package org.acestream.engine.player;

import android.content.Context;
import android.content.SharedPreferences;

import org.acestream.sdk.MediaItem;

public class MetaDataManager {
    public final static String META_AUDIO_TRACK = "audio_track";
    public final static String META_SUBTITLE_TRACK = "subtitle_track";
    public final static String META_SAVED_TIME = "saved_time";

    private final static String TAG = "AS/MetaDataManager";

    private Context mContext;

    public MetaDataManager(Context context) {
        mContext = context;
    }

    public long getLong(MediaItem item, String name, long defaultValue) {
        if(item == null) return defaultValue;
        return getPrefs().getLong(getPrefKey(item, name), defaultValue);
    }

    public int getInt(MediaItem item, String name, int defaultValue) {
        if(item == null) return defaultValue;
        return getPrefs().getInt(getPrefKey(item, name), defaultValue);
    }

    public String getString(MediaItem item, String name) {
        if(item == null) return null;
        return getPrefs().getString(getPrefKey(item, name), null);
    }

    public void putInt(MediaItem item, String name, int value) {
        if(item == null) return;
        getPrefs().edit().putInt(getPrefKey(item, name), value).apply();
    }

    public void putLong(MediaItem item, String name, long value) {
        if(item == null) return;
        getPrefs().edit().putLong(getPrefKey(item, name), value).apply();
    }

    private SharedPreferences getPrefs() {
        return mContext.getApplicationContext()
                .getSharedPreferences("media_metadata", Context.MODE_PRIVATE);
    }

    private String getPrefKey(MediaItem item, String name) {
        return item.getUri().toString() + ":" + name;
    }
}
