package org.acestream.engine;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.fragment.app.Fragment;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;

import org.acestream.sdk.AceStream;
import org.acestream.sdk.CacheDirLocation;
import org.acestream.sdk.SelectedPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.acestream.sdk.Constants;
import org.acestream.sdk.helpers.SettingDialogFragmentCompat;
import org.acestream.sdk.utils.AuthUtils;

public class SettingsFragment extends Fragment {
    public final static String SETTINGS_MAIN = "main";
    public final static String SETTINGS_ENGINE = "engine";
    public final static String SETTINGS_PLAYER = "player";
    public final static String SETTINGS_ADS = "ads";
    public final static String SETTINGS_PROFILE = "profile";

    private ListView mSettingsList;
    private MyAdapter mAdapter;
    private SettingDialogFragmentCompat.SettingDialogListener mListener;
    private MainActivity mMainActivity;
    private String mType;
    private boolean mEngineStarted;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        int layout = AceStreamEngineBaseApplication.showTvUi() ? R.layout.fragment_settings_tv : R.layout.fragment_settings;
        View view = inflater.inflate(layout, container, false);
        mSettingsList = view.findViewById(R.id.settings_list);

        Bundle args = getArguments();
        if(args == null) {
            throw new IllegalStateException("missing args");
        }
        mType = args.getString("type");
        mEngineStarted = args.getBoolean("engine_started");

        mSettingsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                view.performClick();
            }
        });

        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mListener = (SettingDialogFragmentCompat.SettingDialogListener) getActivity();
        mMainActivity = (MainActivity) getActivity();
        refresh();
    }

    /**
     * Called when back key is pressed.
     * Return true to prevent default back action.
     */
    public boolean goBack() {
        if(TextUtils.equals(mType, SETTINGS_ENGINE)) {
            setType(SETTINGS_MAIN);
            return true;
        }
        else if(TextUtils.equals(mType, SETTINGS_PLAYER)) {
            setType(SETTINGS_MAIN);
            return true;
        }
        else if(TextUtils.equals(mType, SETTINGS_ADS)) {
            setType(SETTINGS_MAIN);
            return true;
        }

        return false;
    }

    private void setType(String type) {
        mType = type;
        refresh();
    }

    public void refresh() {
        if(mSettingsList == null) {
            return;
        }

        int titleId;
        List<Map<String, Object>> list;

        switch(mType) {
            case SETTINGS_MAIN:
                list = getMainSettings();
                titleId = R.string.preferences;
                break;
            case SETTINGS_ENGINE:
                list = getEngineSettings();
                titleId = R.string.engine_preferences;
                break;
            case SETTINGS_PLAYER:
                list = getPlayerSettings();
                titleId = R.string.player_preferences;
                break;
            case SETTINGS_ADS:
                list = getAdsSettings();
                titleId = R.string.ads_preferences;
                break;
            case SETTINGS_PROFILE:
                list = getProfileSettings();
                titleId = R.string.menu_profile;
                break;
            default:
                throw new IllegalStateException("unknown type: " + mType);
        }

        if(getActivity() != null) {
            getActivity().setTitle(titleId);
        }

        if(mAdapter == null) {
            mAdapter = new MyAdapter(list);
            mSettingsList.setAdapter(mAdapter);
        }
        else {
            mAdapter.setList(list);
        }
    }

    private List<Map<String,Object>> getMainSettings() {
        List<Map<String, Object>> list = new ArrayList<>();
        Map<String, Object> item;

        // language
        String[] langNames = {"English", "Русский", "Українська"};
        String[] langIds = {"en", "ru", "uk"};

        item = new HashMap<>();
        item.put("name", "language");
        item.put("type", "list");
        item.put("sendToEngine", false);
        item.put("entries", 0);
        item.put("entryValues", 0);
        item.put("entriesList", langNames);
        item.put("entryValuesList", langIds);
        item.put("title", R.string.language);
        list.add(item);

        // engine settings
        item = new HashMap<>();
        item.put("name", SETTINGS_ENGINE);
        item.put("type", "prefs");
        item.put("title", R.string.engine_preferences);
        list.add(item);

        // player settings
        item = new HashMap<>();
        item.put("name", SETTINGS_PLAYER);
        item.put("type", "prefs");
        item.put("title", R.string.player_preferences);
        list.add(item);

        // ads settings
        item = new HashMap<>();
        item.put("name", SETTINGS_ADS);
        item.put("type", "prefs");
        item.put("title", R.string.ads_preferences);
        list.add(item);

        return list;
    }

    private List<Map<String,Object>> getEngineSettings() {
        List<Map<String, Object>> list = new ArrayList<>();
        Map<String, Object> item;

        // main
        if(!AceStream.isAndroidTv()) {
            item = new HashMap<>();
            item.put("name", "mobile_network_available");
            item.put("type", "bool");
            item.put("sendToEngine", false);
            item.put("title", R.string.prefs_item_mobile);
            list.add(item);
        }

        if(mEngineStarted) {
            item = new HashMap<>();
            item.put("name", "vod_buffer");
            item.put("type", "int");
            item.put("title", R.string.prefs_item_vod_buffer);
            list.add(item);

            item = new HashMap<>();
            item.put("name", "live_buffer");
            item.put("type", "int");
            item.put("title", R.string.prefs_item_live_buffer);
            list.add(item);

            item = new HashMap<>();
            item.put("name", "download_limit");
            item.put("type", "int");
            item.put("title", R.string.prefs_item_download_limit);
            list.add(item);

            item = new HashMap<>();
            item.put("name", "upload_limit");
            item.put("type", "int");
            item.put("title", R.string.prefs_item_upload_limit);
            list.add(item);

            item = new HashMap<>();
            item.put("name", "output_format_live");
            item.put("type", "list");
            item.put("entries", R.array.output_format);
            item.put("entryValues", R.array.output_format_id);
            item.put("title", R.string.prefs_output_format_live);
            list.add(item);

            item = new HashMap<>();
            item.put("name", "output_format_vod");
            item.put("type", "list");
            item.put("entries", R.array.output_format);
            item.put("entryValues", R.array.output_format_id);
            item.put("title", R.string.prefs_output_format_vod);
            list.add(item);

            item = new HashMap<>();
            item.put("name", "transcode_audio");
            item.put("type", "bool");
            item.put("title", R.string.prefs_transcode_audio);
            list.add(item);

            item = new HashMap<>();
            item.put("name", "transcode_ac3");
            item.put("type", "bool");
            item.put("title", R.string.prefs_transcode_ac3);
            list.add(item);

            // live cache type (disk or memory)
            item = new HashMap<>();
            item.put("name", "live_cache_type");
            item.put("type", "bool");
            item.put("title", R.string.prefs_cache_live_use_disk);
            list.add(item);

            // VOD cache type (disk or memory)
            item = new HashMap<>();
            item.put("name", "vod_cache_type");
            item.put("type", "bool");
            item.put("title", R.string.prefs_cache_vod_use_disk);
            list.add(item);

            // disk cache limit
            item = new HashMap<>();
            item.put("name", "disk_cache_limit");
            item.put("type", "int");
            item.put("title", R.string.prefs_item_disk_cache_limit);
            list.add(item);

            // memory cache limit
            item = new HashMap<>();
            item.put("name", "memory_cache_limit");
            item.put("type", "int");
            item.put("title", R.string.prefs_item_memory_cache_limit);
            list.add(item);

            List<CacheDirLocation> cacheDirLocations = AceStream.getCacheDirLocations();
            String[] types = new String[cacheDirLocations.size()+1];
            String[] paths = new String[cacheDirLocations.size()+1];
            for(int i = 0; i < cacheDirLocations.size(); i++) {
                types[i] = cacheDirLocations.get(i).type;
                paths[i] = cacheDirLocations.get(i).path;
            }
            types[types.length-1] = getResources().getString(R.string.select_directory);
            paths[paths.length-1] = "";

            item = new HashMap<>();
            item.put("name", "cache_dir");
            item.put("type", "list");
            item.put("entries", 0);
            item.put("entryValues", 0);
            item.put("entriesList", types);
            item.put("entryValuesList", paths);
            item.put("title", R.string.prefs_item_cache_dir);
            list.add(item);


            // advanced
            item = new HashMap<>();
            item.put("name", "allow_intranet_access");
            item.put("type", "bool");
            item.put("title", R.string.prefs_allow_intranet_access);
            list.add(item);

            item = new HashMap<>();
            item.put("name", "allow_remote_access");
            item.put("type", "bool");
            item.put("title", R.string.prefs_allow_remote_access);
            list.add(item);

            item = new HashMap<>();
            item.put("name", "port");
            item.put("type", "int");
            item.put("title", R.string.prefs_item_port);
            list.add(item);

            item = new HashMap<>();
            item.put("name", "max_connections");
            item.put("type", "int");
            item.put("title", R.string.prefs_item_total_max_connects);
            list.add(item);

            item = new HashMap<>();
            item.put("name", "max_peers");
            item.put("type", "int");
            item.put("title", R.string.prefs_item_download_max_connects);
            list.add(item);
        }

        if(!AceStream.isAndroidTv()) {
            // AceCast server
            item = new HashMap<>();
            item.put("name", "start_acecast_server_on_boot");
            item.put("type", "bool");
            item.put("sendToEngine", false);
            item.put("defaultValue", AceStreamEngineBaseApplication.shouldStartAceCastServerByDefault());
            item.put("title", R.string.start_acecast_server);
            list.add(item);
        }

        // available players
        List<String> availablePlayerNames = new ArrayList<>();
        List<String> availablePlayerIds = new ArrayList<>();
        List<SelectedPlayer> availablePlayers = AceStream.getAvailablePlayers();
        for(SelectedPlayer player: availablePlayers) {
            availablePlayerNames.add(player.getName());
            availablePlayerIds.add(player.getId());
        }

        String[] availablePlayerNamesArray = new String[availablePlayerNames.size()];
        String[] availablePlayerIdsArray = new String[availablePlayerIds.size()];
        availablePlayerNames.toArray(availablePlayerNamesArray);
        availablePlayerIds.toArray(availablePlayerIdsArray);

        item = new HashMap<>();
        item.put("name", Constants.PREF_KEY_SELECTED_PLAYER);
        item.put("type", "list");
        item.put("sendToEngine", false);
        item.put("entries", 0);
        item.put("entryValues", 0);
        item.put("entriesList", availablePlayerNamesArray);
        item.put("entryValuesList", availablePlayerIdsArray);
        item.put("title", R.string.selected_player);
        item.put("defaultValue", Constants.OUR_PLAYER_ID);
        list.add(item);

//            // disable HW for avi
//            item = new HashMap<>();
//            item.put("name", "disable_hw_avi");
//            item.put("type", "bool");
//            item.put("sendToEngine", false);
//            item.put("defaultValue", true);
//            item.put("title", "Disable HW for AVI");
//            list.add(item);

//            // disable P2P
//            item = new HashMap<>();
//            item.put("name", "disable_p2p");
//            item.put("type", "bool");
//            item.put("sendToEngine", false);
//            item.put("title", "Disable P2P");
//            list.add(item);

        // enable debug logging
        item = new HashMap<>();
        item.put("name", "enable_debug_logging");
        item.put("type", "bool");
        item.put("sendToEngine", false);
        item.put("title", R.string.enable_debug_logging);
        item.put("defaultValue", false);
        list.add(item);

        // show debug info
        item = new HashMap<>();
        item.put("name", Constants.PREF_KEY_SHOW_DEBUG_INFO);
        item.put("type", "bool");
        item.put("sendToEngine", false);
        item.put("title", R.string.show_debug_info);
        list.add(item);

        return list;
    }

    private List<Map<String,Object>> getPlayerSettings() {
        Resources res = getResources();
        List<Map<String, Object>> list = new ArrayList<>();
        Map<String, Object> item;

        // opengl
        item = new HashMap<>();
        item.put("name", "opengl");
        item.put("type", "list");
        item.put("sendToEngine", false);
        item.put("entries", R.array.opengl_list);
        item.put("entryValues", R.array.opengl_values);
        item.put("title", R.string.opengl_usage);
        item.put("defaultValue", "-1");
        list.add(item);

        // hardware acceleration
        item = new HashMap<>();
        item.put("name", "hardware_acceleration");
        item.put("type", "list");
        item.put("sendToEngine", false);
        item.put("entries", R.array.hardware_acceleration_list);
        item.put("entryValues", R.array.hardware_acceleration_values);
        item.put("title", R.string.video_hardware_acceleration);
        item.put("defaultValue", "-1");
        list.add(item);

        // dialog_confirm_resume
        item = new HashMap<>();
        item.put("name", "dialog_confirm_resume");
        item.put("type", "bool");
        item.put("sendToEngine", false);
        item.put("title", R.string.confirm_resume_title);
        item.put("defaultValue", false);
        list.add(item);

        // bool enable_time_stretching_audio
        item = new HashMap<>();
        item.put("name", "enable_time_stretching_audio");
        item.put("type", "bool");
        item.put("sendToEngine", false);
        item.put("title", R.string.enable_time_stretching_audio);
        item.put("defaultValue", false);
        list.add(item);

        // string chroma_format
        item = new HashMap<>();
        item.put("name", "chroma_format");
        item.put("type", "list");
        item.put("sendToEngine", false);
        item.put("entries", R.array.chroma_formats);
        item.put("entryValues", R.array.chroma_formats_values);
        item.put("title", R.string.chroma_format);
        item.put("defaultValue", res.getString(R.string.chroma_format_default));
        list.add(item);

        // string int deblocking
        item = new HashMap<>();
        item.put("name", "deblocking");
        item.put("type", "list");
        item.put("sendToEngine", false);
        item.put("entries", R.array.deblocking_list);
        item.put("entryValues", R.array.deblocking_values);
        item.put("title", R.string.deblocking);
        item.put("defaultValue", "-1");
        list.add(item);

        // bool enable_frame_skip
        item = new HashMap<>();
        item.put("name", "enable_frame_skip");
        item.put("type", "bool");
        item.put("sendToEngine", false);
        item.put("title", R.string.enable_frame_skip);
        item.put("defaultValue", false);
        list.add(item);

        // bool enable_verbose_mode
        item = new HashMap<>();
        item.put("name", "enable_verbose_mode");
        item.put("type", "bool");
        item.put("sendToEngine", false);
        item.put("title", R.string.enable_verbose_mode);
        item.put("defaultValue", false);
        list.add(item);

        // int network_caching_value
        item = new HashMap<>();
        item.put("name", "network_caching_value");
        item.put("type", "int");
        item.put("title", R.string.network_caching);
        item.put("defaultValue", 2000);
        list.add(item);

        // bool audio_digital_output
        item = new HashMap<>();
        item.put("name", "audio_digital_output");
        item.put("type", "bool");
        item.put("sendToEngine", false);
        item.put("title", R.string.audio_digital_title);
        item.put("defaultValue", false);
        list.add(item);

        // bool show_lock_button
        item = new HashMap<>();
        item.put("name", "show_lock_button");
        item.put("type", "bool");
        item.put("sendToEngine", false);
        item.put("title", R.string.show_lock_button);
        item.put("defaultValue", false);
        list.add(item);

        // string aout
        item = new HashMap<>();
        item.put("name", "aout");
        item.put("type", "list");
        item.put("sendToEngine", false);
        item.put("entries", R.array.aouts);
        item.put("entryValues", R.array.aouts_values);
        item.put("title", R.string.aout);
        item.put("defaultValue", "0");
        list.add(item);

        // bool pause_on_audiofocus_loss
        item = new HashMap<>();
        item.put("name", Constants.PREF_KEY_PAUSE_ON_AUDIOFOCUS_LOSS);
        item.put("type", "bool");
        item.put("sendToEngine", false);
        item.put("title", R.string.pause_on_audiofocus_loss);
        item.put("defaultValue", Constants.PREF_DEFAULT_PAUSE_ON_AUDIOFOCUS_LOSS);
        list.add(item);

        // string subtitle_text_encoding
        // string subtitles_size
        // bool subtitles_bold
        // string subtitles_color
        // bool subtitles_background


        // bool disable_hw_avi
        // bool equalizer_enabled
        // string equalizer_set

        return list;
    }

    private List<Map<String,Object>> getAdsSettings() {
        Resources res = getResources();
        List<Map<String, Object>> list = new ArrayList<>();
        Map<String, Object> item;

        item = new HashMap<>();
        item.put("name", Constants.PREF_KEY_GDPR_CONSENT);
        item.put("type", "bool");
        item.put("sendToEngine", false);
        item.put("title", R.string.gdpr_consent);
        item.put("defaultValue", false);
        list.add(item);

        if(getAuthLevel() > 0) {
            item = new HashMap<>();
            item.put("name", Constants.PREF_KEY_SHOW_REWARDED_ADS);
            item.put("type", "bool");
            item.put("sendToEngine", false);
            item.put("title", R.string.show_rewarded_ads);
            item.put("defaultValue", Constants.PREF_DEFAULT_SHOW_REWARDED_ADS);
            list.add(item);

            if (AuthUtils.hasNoAds(getAuthLevel())) {
                item = new HashMap<>();
                item.put("name", Constants.PREF_KEY_SHOW_ADS_ON_MAIN_SCREEN);
                item.put("type", "bool");
                item.put("sendToEngine", false);
                item.put("title", R.string.show_ads_on_main_screen);
                item.put("defaultValue", Constants.PREF_DEFAULT_SHOW_ADS_ON_MAIN_SCREEN);
                list.add(item);

                item = new HashMap<>();
                item.put("name", Constants.PREF_KEY_SHOW_ADS_ON_PREROLL);
                item.put("type", "bool");
                item.put("sendToEngine", false);
                item.put("title", R.string.show_ads_on_preroll);
                item.put("defaultValue", Constants.PREF_DEFAULT_SHOW_ADS_ON_PREROLL);
                list.add(item);

                item = new HashMap<>();
                item.put("name", Constants.PREF_KEY_SHOW_ADS_ON_PAUSE);
                item.put("type", "bool");
                item.put("sendToEngine", false);
                item.put("title", R.string.show_ads_on_pause);
                item.put("defaultValue", Constants.PREF_DEFAULT_SHOW_ADS_ON_PAUSE);
                list.add(item);

                item = new HashMap<>();
                item.put("name", Constants.PREF_KEY_SHOW_ADS_ON_CLOSE);
                item.put("type", "bool");
                item.put("sendToEngine", false);
                item.put("title", R.string.show_ads_on_close);
                item.put("defaultValue", Constants.PREF_DEFAULT_SHOW_ADS_ON_CLOSE);
                list.add(item);
            }
        }

        return list;
    }

    private List<Map<String,Object>> getProfileSettings() {
        List<Map<String, Object>> list = new ArrayList<>();
        Map<String, Object> item;

        item = new HashMap<>();
        item.put("name", "profile_gender");
        item.put("type", "list");
        item.put("entries", R.array.gender);
        item.put("entryValues", R.array.gender_id);
        item.put("title", R.string.prefs_item_gender);
        list.add(item);

        item = new HashMap<>();
        item.put("name", "profile_age");
        item.put("type", "list");
        item.put("entries", R.array.age);
        item.put("entryValues", R.array.age_id);
        item.put("title", R.string.prefs_item_age);
        list.add(item);

        return list;
    }

    private class MyAdapter extends BaseAdapter {
        private final static String TAG = "AceStream/Adapter";
        private List<Map<String,Object>> mSettings;

        public MyAdapter(List<Map<String,Object>> settings) {
            mSettings = settings;
        }

        public void setList(List<Map<String,Object>> settings) {
            mSettings = settings;
            notifyDataSetChanged();
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public Object getItem(int position) {
            return mSettings.get(position);
        }

        @Override
        public int getCount() {
            return mSettings.size();
        }

        @Override
        public int getViewTypeCount() {
            return 4;
        }

        @Override
        public int getItemViewType (int position) {
            Map<String, Object> item = (Map<String, Object>)getItem(position);
            final String type = (String)item.get("type");
            if(type.equals("bool")) {
                return 1;
            }
            else if(type.equals("folder")) {
                return 2;
            }
            else if(type.equals("prefs")) {
                return 3;
            }
            else {
                return 0;
            }
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Context context = parent.getContext();
            Resources res = context.getResources();
            LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            if(inflater == null) {
                Log.e(TAG, "getView: missing inflater");
                return null;
            }

            Map<String, Object> item = (Map<String, Object>)getItem(position);
            String name = (String)item.get("name");
            String type = (String)item.get("type");
            Object defaultValue = item.get("defaultValue");

            boolean sendToEngine = true;
            Object sendToEngineObj = item.get("sendToEngine");
            if(sendToEngineObj != null) {
                sendToEngine = (boolean)sendToEngineObj;
            }

            SharedPreferences sp = AceStreamEngineBaseApplication.getPreferences();

            String title;
            Object _title = item.get("title");
            if(_title instanceof String) {
                title = (String)_title;
            }
            else {
                title = res.getString((int)_title);
            }

            View v = convertView;
            if(v == null) {
                int layoutId = R.layout.setting_item;
                boolean openDialog = true;
                if(TextUtils.equals(type, "bool")) {
                    layoutId = R.layout.setting_item_checkbox;
                    openDialog = false;
                }
                else if(TextUtils.equals(type, "prefs")) {
                    layoutId = R.layout.setting_item_group;
                    openDialog = false;
                }

                v = inflater.inflate(layoutId, parent, false);

                if(TextUtils.equals(type, "bool")) {
                    v.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            CheckBox cb = view.findViewById(R.id.setting_value);
                            cb.performClick();
                        }
                    });

                    CheckBox cb = v.findViewById(R.id.setting_value);
                    cb.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            Log.d(TAG, "onClick:bool: type=" + view.getTag(R.id.tag_type) + " name=" + view.getTag(R.id.tag_name));
                            if (mListener != null) {
                                mListener.onSaveSetting(
                                        (String) view.getTag(R.id.tag_type),
                                        (String) view.getTag(R.id.tag_name),
                                        ((CheckBox)view).isChecked(),
                                        (boolean) view.getTag(R.id.tag_send_to_engine)
                                );
                            }
                        }
                    });
                }
                else if(TextUtils.equals(type, "prefs")) {
                    v.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            String name = (String)view.getTag(R.id.tag_name);
                            setType(name);
                        }
                    });
                }

                if(openDialog) {
                    v.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            SettingDialogFragmentCompat dialogFragment = new SettingDialogFragmentCompat();
                            Bundle bundle = new Bundle();

                            String name = (String)view.getTag(R.id.tag_name);
                            bundle.putString("name", name);
                            bundle.putString("type", (String)view.getTag(R.id.tag_type));
                            bundle.putString("title", (String) view.getTag(R.id.tag_title));

                            Object defaultValue = view.getTag(R.id.tag_default_value);
                            if(defaultValue != null && defaultValue instanceof String) {
                                bundle.putString("defaultValue", (String)defaultValue);
                            }
                            bundle.putBoolean("sendToEngine", (boolean)view.getTag(R.id.tag_send_to_engine));

                            Object entries = view.getTag(R.id.tag_entries);
                            Object entryValues = view.getTag(R.id.tag_entry_values);
                            Object entriesList = view.getTag(R.id.tag_entries_list);
                            Object entryValuesList = view.getTag(R.id.tag_entry_values_list);

                            if(entries != null) {
                                bundle.putInt("entries", (int)entries);
                            }
                            if(entryValues != null) {
                                bundle.putInt("entryValues", (int) entryValues);
                            }
                            if(entriesList != null) {
                                bundle.putStringArray("entriesList", (String[])entriesList);
                            }
                            if(entryValuesList != null) {
                                bundle.putStringArray("entryValuesList", (String[])entryValuesList);
                            }
                            dialogFragment.setArguments(bundle);
                            dialogFragment.show(getActivity().getSupportFragmentManager(), "setting_dialog");
                        }
                    });
                }
            }

            ((TextView)v.findViewById(R.id.setting_title)).setText(title);
            if(type.equals("bool")) {
                boolean defaultValueBool = false;
                if(defaultValue instanceof Boolean) {
                    defaultValueBool = (boolean)defaultValue;
                }
                boolean value = sp.getBoolean(name, defaultValueBool);
                CheckBox cb = v.findViewById(R.id.setting_value);
                cb.setChecked(value);
                cb.setTag(R.id.tag_name, name);
                cb.setTag(R.id.tag_type, type);
                cb.setTag(R.id.tag_send_to_engine, sendToEngine);
                cb.setTag(R.id.tag_default_value, defaultValue);
                Log.d(TAG, "setTag:bool: type=" + type + " name=" + name + " value=" + value + " sendToEngine=" + sendToEngine);
            }
            else if(type.equals("list")) {
                int entriesResourceId = (int)item.get("entries");
                int entryValuesResourceId = (int)item.get("entryValues");
                String[] entriesList;
                String[] entryValuesList;

                if(entriesResourceId == 0) {
                    entriesList = (String[]) item.get("entriesList");
                    entryValuesList = (String[]) item.get("entryValuesList");
                }
                else {
                    entriesList = res.getStringArray(entriesResourceId);
                    entryValuesList = res.getStringArray(entryValuesResourceId);
                }

                String defaultValueString;
                if (defaultValue == null) {
                    defaultValueString = "";
                }
                else if(defaultValue instanceof String) {
                    defaultValueString = (String)defaultValue;
                }
                else {
                    defaultValueString = "";
                }

                String currentValue = sp.getString(name, defaultValueString);
                boolean gotCurrentValue = false;

                // get default system language if not found in settings
                if(name.equals("language") && currentValue.length() == 0) {
                    currentValue = Locale.getDefault().getLanguage();
                }

                for (int i = 0; i < entryValuesList.length; i++) {
                    if (currentValue.equals(entryValuesList[i])) {
                        ((TextView) v.findViewById(R.id.setting_value)).setText(entriesList[i]);
                        gotCurrentValue = true;
                        break;
                    }
                }

                if(!gotCurrentValue && name.equals("cache_dir") && entriesList.length > 0) {
                    // show custom cache path
                    ((TextView) v.findViewById(R.id.setting_value)).setText(currentValue);
                }

                v.setTag(R.id.tag_entries, entriesResourceId);
                v.setTag(R.id.tag_entry_values, entryValuesResourceId);
                if(entriesResourceId == 0) {
                    v.setTag(R.id.tag_entries_list, entriesList);
                    v.setTag(R.id.tag_entry_values_list, entryValuesList);
                }
                else {
                    v.setTag(R.id.tag_entries_list, null);
                    v.setTag(R.id.tag_entry_values_list, null);
                }
                v.setTag(R.id.tag_name, name);
                v.setTag(R.id.tag_type, type);
                v.setTag(R.id.tag_title, title);
                v.setTag(R.id.tag_send_to_engine, sendToEngine);
                v.setTag(R.id.tag_default_value, defaultValue);
            }
            else if(type.equals("prefs")) {
                v.setTag(R.id.tag_name, name);
                v.setTag(R.id.tag_type, type);
            }
            else {
                ((TextView) v.findViewById(R.id.setting_value)).setText(sp.getString(name, String.valueOf(defaultValue)));
                v.setTag(R.id.tag_name, name);
                v.setTag(R.id.tag_type, type);
                v.setTag(R.id.tag_title, title);
                v.setTag(R.id.tag_send_to_engine, sendToEngine);
                v.setTag(R.id.tag_default_value, defaultValue);
            }

            return v;
        }
    }

    private int getAuthLevel() {
        if(mMainActivity != null && mMainActivity.getPlaybackManager() != null) {
            return mMainActivity.getPlaybackManager().getAuthLevel();
        }
        return 0;
    }
}
