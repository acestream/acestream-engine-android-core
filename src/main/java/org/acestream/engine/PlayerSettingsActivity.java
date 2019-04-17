package org.acestream.engine;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.ActionBar;
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

import org.acestream.engine.acecast.client.AceStreamRemoteDevice;
import org.acestream.sdk.helpers.SettingDialogFragmentCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerSettingsActivity
        extends PlaybackManagerAppCompatActivity
        implements SettingDialogFragmentCompat.SettingDialogListener {
    private final static String TAG = "AceStream/PS";

    private ListView mSettingsList;
    private MyAdapter mAdapter;
    private SettingDialogFragmentCompat.SettingDialogListener mListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.player_settings_activity);

        // setup action bar
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeButtonEnabled(true);
            actionBar.setBackgroundDrawable(new ColorDrawable(ContextCompat.getColor(this, R.color.main_accent)));
        }

        mSettingsList = findViewById(R.id.settings_list);
        mSettingsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                view.performClick();
            }
        });
    }

    @Override
    public void onResumeConnected() {
        super.onResumeConnected();
        Log.d(TAG, "onResumeConnected");
        refresh();
    }

    public void refresh() {
        if(mSettingsList == null) {
            return;
        }

        List<Map<String, Object>> list = new ArrayList<>();
        Map<String, Object> item;

        /*
        // video size
        String[] videoSizeNames = {"Best Fit", "Fit Horizontal", "Fit Vertical", "Fill", "16:9", "4:3", "Original"};
        String[] videoSizeIds = {
            "best_fit",
            "fit_horizontal",
            "fit_vertical",
            "fill",
            "16:9",
            "4:3",
            "original"
        };

        item = new HashMap<>();
        item.put("name", "videoSize");
        item.put("type", "list");
        item.put("entries", 0);
        item.put("entryValues", 0);
        item.put("entriesList", videoSizeNames);
        item.put("entryValuesList", videoSizeIds);
        item.put("title", "Video Size");
        list.add(item);
        */

        // deinterlace
        String[] deinterlaceNames = {"Disable", "Discard", "Blend", "Mean", "Bob", "Linear", "X", "Yadif", "Yadif2x", "Phosphor", "Ivtc"};
        String[] deinterlaceIds = {"_disable_", "discard", "blend", "mean", "bob", "linear", "x", "yadif", "yadif2x", "phosphor", "ivtc"};

        item = new HashMap<>();
        item.put("name", "deinterlace");
        item.put("type", "list");
        item.put("entries", 0);
        item.put("entryValues", 0);
        item.put("entriesList", deinterlaceNames);
        item.put("entryValuesList", deinterlaceIds);
        item.put("title", "Deinterlace");
        list.add(item);

        if(mAdapter == null) {
            mAdapter = new MyAdapter(list);
            mSettingsList.setAdapter(mAdapter);
        }
        else {
            mAdapter.setList(list);
        }
    }

    @Override
    public void onSaveSetting(String type, String name, Object value, boolean sendToEngine) {
        Log.d(TAG, "onSaveSetting: type=" + type + " name=" + name + " value=" + value);

        if(mPlaybackManager == null) {
            return;
        }

        AceStreamRemoteDevice remoteDevice = mPlaybackManager.getCurrentRemoteDevice();
        if(remoteDevice != null){
            switch (name) {
                case "deinterlace":
                    remoteDevice.setDeinterlace(String.valueOf(value));
                    refresh();
                    break;
//                case "videoSize":
//                    remoteDevice.setVideoSize(String.valueOf(value));
//                    break;
            }
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        Log.d(TAG, "onSupportNavigateUp");
        super.onSupportNavigateUp();
        finish();
        return true;
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
            return 3;
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
            else {
                return 0;
            }
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Context context = parent.getContext();
            Resources res = context.getResources();
            LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            AceStreamRemoteDevice remoteDevice = null;

            if(mPlaybackManager != null) {
                remoteDevice = mPlaybackManager.getCurrentRemoteDevice();
            }

            Map<String, Object> item = (Map<String, Object>)getItem(position);
            String name = (String)item.get("name");
            String type = (String)item.get("type");

            boolean sendToEngine = true;
            Object sendToEngineObj = item.get("sendToEngine");
            if(sendToEngineObj != null) {
                sendToEngine = (boolean)sendToEngineObj;
            }

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
                if(type.equals("bool")) {
                    layoutId = R.layout.setting_item_checkbox;
                    openDialog = false;
                }
                v = inflater.inflate(layoutId, parent, false);

                if(type.equals("bool")) {
                    v.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            CheckBox cb = (CheckBox)view.findViewById(R.id.setting_value);
                            cb.performClick();
                        }
                    });

                    CheckBox cb = (CheckBox)v.findViewById(R.id.setting_value);
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

                if(openDialog) {
                    v.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            SettingDialogFragmentCompat dialogFragment = new SettingDialogFragmentCompat();
                            //dialogFragment.setPlaybackManager(mPlaybackManager);

                            Bundle bundle = new Bundle();
                            bundle.putString("name", (String)view.getTag(R.id.tag_name));
                            bundle.putString("type", (String)view.getTag(R.id.tag_type));
                            bundle.putString("title", (String) view.getTag(R.id.tag_title));
                            bundle.putBoolean("sendToEngine", (boolean) view.getTag(R.id.tag_send_to_engine));

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
                            dialogFragment.show(getSupportFragmentManager(), "setting_dialog");
                        }
                    });
                }
            }

            ((TextView)v.findViewById(R.id.setting_title)).setText(title);
            if(type.equals("bool")) {
                CheckBox cb = (CheckBox)v.findViewById(R.id.setting_value);
                cb.setTag(R.id.tag_name, name);
                cb.setTag(R.id.tag_type, type);
                cb.setTag(R.id.tag_send_to_engine, sendToEngine);
                Log.d(TAG, "setTag:bool: type=" + type + " name=" + name);
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

                String currentValue = "";

                if(name.equals("deinterlace") && remoteDevice != null) {
                    currentValue = remoteDevice.getDeinterlaceMode();
                    if(currentValue == null) {
                        currentValue = "";
                    }
                }

                for (int i = 0; i < entryValuesList.length; i++) {
                    if (currentValue.equals(entryValuesList[i])) {
                        ((TextView) v.findViewById(R.id.setting_value)).setText(entriesList[i]);
                        break;
                    }
                }

                v.setTag(R.id.tag_entries, entriesResourceId);
                v.setTag(R.id.tag_entry_values, entryValuesResourceId);
                if(entriesResourceId == 0) {
                    v.setTag(R.id.tag_entries_list, entriesList);
                    v.setTag(R.id.tag_entry_values_list, entryValuesList);
                }
                v.setTag(R.id.tag_name, name);
                v.setTag(R.id.tag_type, type);
                v.setTag(R.id.tag_title, title);
                v.setTag(R.id.tag_send_to_engine, sendToEngine);
            }
            else {
                v.setTag(R.id.tag_name, name);
                v.setTag(R.id.tag_type, type);
                v.setTag(R.id.tag_title, title);
                v.setTag(R.id.tag_send_to_engine, sendToEngine);
            }

            return v;
        }
    }
}
