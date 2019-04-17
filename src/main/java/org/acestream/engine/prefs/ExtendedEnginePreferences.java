package org.acestream.engine.prefs;

import com.google.gson.Gson;

import org.acestream.sdk.controller.api.response.AndroidConfig;

import androidx.annotation.Keep;

@Keep
public class ExtendedEnginePreferences {
    public static ExtendedEnginePreferences fromJson(String json) {
        return new Gson().fromJson(json, ExtendedEnginePreferences.class);
    }

    public int vod_buffer;
    public int live_buffer;
    public long disk_cache_limit;
    public long memory_cache_limit;
    public int download_limit;
    public int upload_limit;
    public String cache_dir;
    public String output_format_live;
    public String output_format_vod;
    public String login;
    public boolean has_password = false;
    public int port;
    public int max_connections;
    public int max_peers;
    public String profile_gender;
    public String profile_age;
    public String live_cache_type;
    public String vod_cache_type;
    public boolean transcode_ac3;
    public boolean transcode_audio;
    public int allow_intranet_access;
    public int allow_remote_access;
    public String version;

    public int allow_external_players = 1;
    public int allow_our_player = 1;
    public AndroidConfig android_config;
}
