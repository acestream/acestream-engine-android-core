package org.acestream.engine.ui;

import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import org.acestream.engine.AceStreamEngineBaseApplication;
import org.acestream.engine.BuildConfig;
import org.acestream.engine.R;
import org.acestream.sdk.AceStream;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AboutFragment extends Fragment {

    private ListView mSettingsList;
    private MyAdapter mAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        int layout = AceStreamEngineBaseApplication.showTvUi() ? R.layout.fragment_settings_tv : R.layout.fragment_settings;
        View view = inflater.inflate(layout, container, false);
        mSettingsList = view.findViewById(R.id.settings_list);

        mSettingsList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                view.performClick();
            }
        });

        refresh();
        return view;
    }

    public void refresh() {
        if(mSettingsList == null) {
            return;
        }

        List<Map<String, Object>> list = new ArrayList<>();
        Map<String, Object> item;

        // license
        item = new HashMap<>();
        item.put("name", "license");
        item.put("type", "link");
        item.put("url", "http://acestream.org/about/android/org.acestream.core/license.html");
        item.put("title", R.string.license);
        list.add(item);

        // user agreement
        item = new HashMap<>();
        item.put("name", "user_agreement");
        item.put("type", "link");
        item.put("url", "http://acestream.org/about/license");
        item.put("title", R.string.user_agreement);
        list.add(item);

        // private policy
        item = new HashMap<>();
        item.put("name", "privacy_policy");
        item.put("type", "link");
        item.put("url", "http://acestream.org/about/privacy-policy");
        item.put("title", R.string.privacy_policy);
        list.add(item);

        // app version
        String version = AceStreamEngineBaseApplication.versionName();
        if(BuildConfig.DEBUG) {
            version += " DEV (" + AceStreamEngineBaseApplication.getArch() + ")";
        }

        item = new HashMap<>();
        item.put("name", "app_version");
        item.put("type", "static");
        item.put("value", version);
        item.put("title", R.string.application_version);
        list.add(item);

        if(BuildConfig.DEBUG) {
            item = new HashMap<>();
            item.put("name", "app_version_code");
            item.put("type", "static");
            item.put("value", String.valueOf(AceStream.getApplicationVersionCode()));
            item.put("title", "Version Code");
            list.add(item);
        }

        if(getActivity() != null) {
            getActivity().setTitle(R.string.about);
        }

        if(mAdapter == null) {
            mAdapter = new MyAdapter(list);
            mSettingsList.setAdapter(mAdapter);
        }
        else {
            mAdapter.setList(list);
        }
    }

    private class MyAdapter extends BaseAdapter {
        private final static String TAG = "AceStream/Adapter";
        private List<Map<String,Object>> mItems;

        public MyAdapter(List<Map<String,Object>> items) {
            mItems = items;
        }

        public void setList(List<Map<String,Object>> items) {
            mItems = items;
            notifyDataSetChanged();
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public Object getItem(int position) {
            return mItems.get(position);
        }

        @Override
        public int getCount() {
            return mItems.size();
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType (int position) {
            Map<String, Object> item = (Map<String, Object>)getItem(position);
            final String type = (String)item.get("type");
            if(TextUtils.equals(type, "link")) {
                return 1;
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
            String url = (String)item.get("url");
            String value = (String)item.get("value");

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
                int layoutId;
                if(TextUtils.equals(type, "link")) {
                    layoutId = R.layout.setting_item_group;
                }
                else {
                    layoutId = R.layout.setting_item;
                }

                v = inflater.inflate(layoutId, parent, false);

                if(TextUtils.equals(type, "link")) {
                    v.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            String url = (String)view.getTag(R.id.tag_url);
                            AceStreamEngineBaseApplication.startBrowserIntent(getActivity(), url);
                        }
                    });
                }
            }

            ((TextView)v.findViewById(R.id.setting_title)).setText(title);
            v.setTag(R.id.tag_url, url);
            v.setTag(R.id.tag_name, name);

            if(TextUtils.equals(type, "link")) {
                v.setTag(R.id.tag_type, type);
            }
            else {
                ((TextView) v.findViewById(R.id.setting_value)).setText(value);
            }

            return v;
        }
    }
}
