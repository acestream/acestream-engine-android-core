package org.acestream.engine;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import androidx.annotation.NonNull;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.connectsdk.device.ConnectableDevice;

import org.acestream.engine.acecast.interfaces.DeviceDiscoveryListener;
import org.acestream.engine.aliases.App;
import org.acestream.sdk.AceStream;
import org.acestream.sdk.Constants;
import org.acestream.sdk.SelectedPlayer;
import org.acestream.engine.acecast.client.AceStreamRemoteDevice;
import org.acestream.sdk.utils.MiscUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ResolverActivity
    extends
        PlaybackManagerFragmentActivity
    implements
        View.OnClickListener,
        DeviceDiscoveryListener
{
    protected final static String TAG = "AceStream/Resolver";

    private ListView mListView;
    private CheckBox mChkRememberChoice;
    private MyAdapter mAdapter;

    // new
    private String mOutputFormat;
    private String mMime;
    protected String mInfohash;
    private boolean mShowAceStreamPlayer;
    private boolean mShowOnlyKnownPlayers;
    private boolean mAllowRememberPlayer;
    protected int mIsLive;
    private boolean mUseDarkIcons = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Theme must be applied before super.onCreate
        applyTheme();
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();

        setContentView(R.layout.resolver_activity);

        mListView = findViewById(R.id.list);
        mChkRememberChoice = findViewById(R.id.chk_remember_choice);
        findViewById(R.id.btn_close).setOnClickListener(this);
        mChkRememberChoice.setOnClickListener(this);

        if(AceStreamEngineBaseApplication.getSelectedPlayer() != null) {
            mChkRememberChoice.setChecked(true);
        }

        mInfohash = MiscUtils.getRequiredStringExtra(intent, Constants.EXTRA_INFOHASH);
        mMime = MiscUtils.getRequiredStringExtra(intent, Constants.EXTRA_MIME);
        mShowAceStreamPlayer = intent.getBooleanExtra(Constants.EXTRA_SHOW_ACESTREAM_PLAYER, true);
        mShowOnlyKnownPlayers = intent.getBooleanExtra(Constants.EXTRA_SHOW_ONLY_KNOWN_PLAYERS, false);
        mIsLive = MiscUtils.getRequiredIntExtra(intent, Constants.EXTRA_IS_LIVE);
        mAllowRememberPlayer = intent.getBooleanExtra(Constants.EXTRA_ALLOW_REMEMBER_PLAYER, true);

        if(mIsLive == 1) {
            mOutputFormat = AceStreamEngineBaseApplication.getLiveOutputFormat();
        }
        else {
            mOutputFormat = AceStreamEngineBaseApplication.getVodOutputFormat();
        }

        if(!mAllowRememberPlayer) {
            mChkRememberChoice.setVisibility(View.GONE);
        }

        Log.v(TAG, "onCreate: mime=" + mMime
                + " format=" + mOutputFormat
                + " isLive=" + mIsLive
                + " allowRememberPlayer=" + mAllowRememberPlayer
        );

        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                view.performClick();
            }
        });
    }

    private void applyTheme() {
        if (mPlaybackManager == null || mPlaybackManager.isBlackThemeEnabled()) {
            setTheme(R.style.Theme_AceStream_Dialog_Dark);
            mUseDarkIcons = false;
        }
        else {
            setTheme(R.style.Theme_AceStream_Dialog_Light);
            mUseDarkIcons = true;
        }
    }

    private void initPlayerList() {
        if(mPlaybackManager == null) {
            throw new IllegalStateException("missing playback manager");
        }

        List<ResolveItem> itemList = new ArrayList<>();

        List<ResolveInfo> riList = AceStream.getKnownPlayers();
        if(riList != null && riList.size() > 0) {
            for(ResolveInfo ri: riList) {
                App.v(TAG, "add known player: " + ri.activityInfo.packageName);
                itemList.add(new ResolveItem(this, mUseDarkIcons, ri));
            }
        }

        if(mShowOnlyKnownPlayers) {
            if(itemList.size() == 0) {
                // there are no known players
                Log.d(TAG, "no known players");
                finish();
                return;
            }
        }
        else {
            if(mShowAceStreamPlayer) {
                ResolveItem ourPlayer = ResolveItem.getOurPlayer(this);
                // Show at first position
                App.v(TAG, "add our player");
                itemList.add(0, ourPlayer);
            }

            riList = AceStream.getInstalledPlayers();
            if (riList != null && riList.size() > 0) {
                for (ResolveInfo ri : riList) {
                    App.v(TAG, "add installed player: " + ri.activityInfo.packageName);
                    itemList.add(new ResolveItem(this, mUseDarkIcons, ri));
                }
            }

            // add acestream remote devices
            Map<String, AceStreamRemoteDevice> aceStreamDevices = mPlaybackManager.getAceStreamRemoteDevices();
            for (AceStreamRemoteDevice device : aceStreamDevices.values()) {
                App.v(TAG, "add acecast device: " + device);
                // ping now to ensure that device is alive
                device.startPing();
                itemList.add(new ResolveItem(this, mUseDarkIcons, device));
            }

            // add remote devices (chromecast, airplay etc)
            if (PlaybackManager.shouldShowRemoteDevices(mOutputFormat, mMime)) {
                Map<String, ConnectableDevice> castDevices = mPlaybackManager.getConnectableDevices();
                if (castDevices != null && castDevices.size() > 0) {
                    for (ConnectableDevice device : castDevices.values()) {
                        App.v(TAG, "add csdk device: " + device);
                        itemList.add(new ResolveItem(this, mUseDarkIcons, device));
                    }
                }
            }
        }

        mAdapter = new MyAdapter(itemList);
        mListView.setAdapter(mAdapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: hash=" + hashCode());
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause: hash=" + hashCode());

        if(!mShowOnlyKnownPlayers && mPlaybackManager != null) {
            mPlaybackManager.removeDeviceDiscoveryListener(this);
        }
    }

    @Override
    public void onResumeConnected() {
        super.onResumeConnected();
        Log.d(TAG, "onResumeConnected");

        if(!mShowOnlyKnownPlayers) {
            // listen for devices add/removal
            mPlaybackManager.addDeviceDiscoveryListener(this);
        }

        // rescan devices
        mPlaybackManager.discoverDevices(false);

        // init
        initPlayerList();
    }

    @Override
    public void onDestroy () {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");
    }

    protected void exitPlayerSelected(@NonNull SelectedPlayer player) {
        Log.v(TAG, "exitPlayerSelected: isFinishing=" + isFinishing() + " player=" + player);

        if (isFinishing())
            return;

        Intent resultIntent = new Intent();
        resultIntent.putExtra(Constants.EXTRA_SELECTED_PLAYER, player.toJson());

        if (mAllowRememberPlayer && mChkRememberChoice.isChecked()) {
            AceStreamEngineBaseApplication.saveSelectedPlayer(player, true);
        }

        setResult(RESULT_OK, resultIntent);
        finish();
    }

    @Override
    public void onClick(View v) {
        int i = v.getId();
        if (i == R.id.btn_close) {
            finish();
        } else if (i == R.id.chk_remember_choice) {
            if (mPlaybackManager != null && !mChkRememberChoice.isChecked()) {
                mPlaybackManager.forgetPlayer();
            }
        }
    }

    @Override
    public void onDeviceAdded(ConnectableDevice device) {
        mAdapter.addDevice(device);
    }

    @Override
    public void onDeviceRemoved(ConnectableDevice device) {
        mAdapter.removeDevice(device);
    }

    @Override
    public void onDeviceAdded(final AceStreamRemoteDevice device) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mAdapter.addDevice(device);
            }
        });
    }

    @Override
    public void onDeviceRemoved(final AceStreamRemoteDevice device) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mAdapter.removeDevice(device);
            }
        });
    }

    @Override
    public void onCurrentDeviceChanged(AceStreamRemoteDevice device) {
    }

    @Override
    public boolean canStopDiscovery() {
        return false;
    }

    private class MyAdapter extends BaseAdapter {
        private final static String TAG = "AceStream/RAdapter";
        private List<ResolveItem> mItems;

        MyAdapter(List<ResolveItem> items) {
            mItems = items;
            filterItems();
        }

        void addDevice(ConnectableDevice device) {
            if(device == null) {
                return;
            }

            boolean exists = false;
            for(ResolveItem item: mItems) {
                if(item.getType() == SelectedPlayer.CONNECTABLE_DEVICE ) {
                    ConnectableDevice itemDevice = item.getConnectableDevice();
                    if(itemDevice != null && itemDevice.getId().equals(device.getId())) {
                        exists = true;
                        break;
                    }
                }
            }

            if(!exists) {
                mItems.add(new ResolveItem(ResolverActivity.this, mUseDarkIcons, device));
                if(!filterItems()) {
                    notifyDataSetChanged();
                }
            }
        }

        void removeDevice(ConnectableDevice device) {
            if(device == null) {
                return;
            }

            ResolveItem itemToRemove = null;
            for(ResolveItem item: mItems) {
                if(item.getType() == SelectedPlayer.CONNECTABLE_DEVICE ) {
                    ConnectableDevice itemDevice = item.getConnectableDevice();
                    if(itemDevice != null && itemDevice.getId().equals(device.getId())) {
                        itemToRemove = item;
                    }
                }
            }

            if(itemToRemove != null) {
                mItems.remove(itemToRemove);
                if(!filterItems()) {
                    notifyDataSetChanged();
                }
            }
        }

        void addDevice(AceStreamRemoteDevice device) {
            if(device == null) {
                return;
            }

            boolean exists = false;
            for(ResolveItem item: mItems) {
                if(item.getType() == SelectedPlayer.ACESTREAM_DEVICE ) {
                    AceStreamRemoteDevice itemDevice = item.getAceStreamRemoteDevice();
                    if(itemDevice != null && itemDevice.getId().equals(device.getId())) {
                        exists = true;
                        break;
                    }
                }
            }

            if(!exists) {
                mItems.add(new ResolveItem(ResolverActivity.this, mUseDarkIcons, device));
                if(!filterItems()) {
                    notifyDataSetChanged();
                }
            }
        }

        void removeDevice(AceStreamRemoteDevice device) {
            if(device == null) {
                return;
            }

            ResolveItem itemToRemove = null;
            for(ResolveItem item: mItems) {
                if(item.getType() == SelectedPlayer.ACESTREAM_DEVICE ) {
                    AceStreamRemoteDevice itemDevice = item.getAceStreamRemoteDevice();
                    if(itemDevice != null && itemDevice.getId().equals(device.getId())) {
                        itemToRemove = item;
                    }
                }
            }

            if(itemToRemove != null) {
                mItems.remove(itemToRemove);
                if(!filterItems()) {
                    notifyDataSetChanged();
                }
            }
        }

        /**
         * Filter items.
         * If we have to devices with the same ip address then we prefer AceCast device.
         * @return true if some items was filtered
         */
        private boolean filterItems() {
            // first pass: list all AceCast devices
            Set<String> playerPackageNames = new HashSet<>();
            List<ResolveItem> toRemove = new ArrayList<>();
            for(ResolveItem item: mItems) {
                if(item.getType() == SelectedPlayer.LOCAL_PLAYER) {
                    String packageName = item.getPackageName();
                    if(packageName != null) {
                        if(packageName.startsWith("org.acestream.")) {
                            Log.d(TAG, "filterItems: remove our player from resolved: packageName=" + packageName);
                            toRemove.add(item);
                        }
                        else if(playerPackageNames.contains(packageName)) {
                            Log.d(TAG, "filterItems: remove player: packageName=" + packageName);
                            toRemove.add(item);
                        }
                        else {
                            playerPackageNames.add(packageName);
                        }
                    }
                }
            }

            if(toRemove.size() > 0) {
                for (ResolveItem item : toRemove) {
                    mItems.remove(item);
                }
                notifyDataSetChanged();
                return true;
            }

            return false;
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
            return 1;
        }

        @Override
        public int getItemViewType (int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Context context = parent.getContext();
            LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            final ResolveItem item = mItems.get(position);

            if(convertView == null && inflater != null) {
                convertView = inflater.inflate(R.layout.resolver_item, parent, false);

                convertView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        ResolveItem item = (ResolveItem) view.getTag(R.id.tag_resolver_item);
                        if (item != null) {
                            onItemClicked(item);
                        }
                    }
                });
            }

            if(convertView != null) {
                convertView.setTag(R.id.tag_resolver_item, item);
                ((TextView) convertView.findViewById(R.id.title)).setText(item.getLabel());

                Drawable icon = item.getIconDrawable();
                if(icon != null) {
                    ((ImageView) convertView.findViewById(R.id.icon)).setImageDrawable(icon);
                }
            }

            return convertView;
        }
    }

    protected void onItemClicked(@NonNull ResolveItem item) {
        SelectedPlayer player;

        if(item.getType() == SelectedPlayer.LOCAL_PLAYER) {
            Log.d(TAG, "onClick: name=" + item.getClassName());
            player = new SelectedPlayer(item.getType(), item.getPackageName(), item.getClassName());
        }
        else if(item.getType() == SelectedPlayer.CONNECTABLE_DEVICE) {
            ConnectableDevice device = item.getConnectableDevice();
            player = new SelectedPlayer(
                    SelectedPlayer.CONNECTABLE_DEVICE,
                    device.getId(),
                    device.getFriendlyName());
        }
        else if(item.getType() == SelectedPlayer.ACESTREAM_DEVICE) {
            AceStreamRemoteDevice device = item.getAceStreamRemoteDevice();
            player = SelectedPlayer.fromDevice(device);
        }
        else if(item.getType() == SelectedPlayer.OUR_PLAYER) {
            Log.d(TAG, "onClick: our player");
            player = new SelectedPlayer(item.getType());
        }
        else {
            Log.e(TAG, "onClick: unknown item type: " + item.getType());
            return;
        }

        if(player != null) {
            AceStream.setLastSelectedPlayer(player);
            exitPlayerSelected(player);
        }
    }
}
