package org.acestream.engine;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.collection.ArrayMap;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import com.connectsdk.device.ConnectableDevice;
import com.connectsdk.service.capability.MediaControl;
import com.connectsdk.service.capability.VolumeControl;
import com.connectsdk.service.capability.listeners.ResponseListener;
import com.connectsdk.service.command.ServiceCommandError;

import org.acestream.engine.aliases.App;
import org.acestream.engine.csdk.CsdkBridge;
import org.acestream.sdk.AceStream;
import org.acestream.sdk.ContentStream;
import org.acestream.sdk.EngineSession;
import org.acestream.sdk.EngineSessionStartListener;
import org.acestream.sdk.EngineStatus;
import org.acestream.sdk.SelectedPlayer;
import org.acestream.engine.acecast.client.AceStreamRemoteDevice;
import org.acestream.sdk.JsonRpcMessage;
import org.acestream.sdk.TrackDescription;
import org.acestream.engine.acecast.interfaces.AceStreamRemoteDeviceListener;
import org.acestream.engine.acecast.interfaces.DeviceDiscoveryListener;
import org.acestream.engine.acecast.interfaces.DeviceStatusListener;
import org.acestream.sdk.controller.EngineApi;
import org.acestream.sdk.controller.api.TransportFileDescriptor;
import org.acestream.sdk.controller.api.response.MediaFilesResponse;
import org.acestream.sdk.errors.TransportFileParsingException;
import org.acestream.sdk.interfaces.EngineSessionListener;
import org.acestream.sdk.interfaces.EngineStatusListener;
import org.acestream.sdk.interfaces.IAceStreamManager;
import org.acestream.sdk.interfaces.IRemoteDevice;
import org.acestream.sdk.interfaces.ConnectableDeviceListener;
import org.acestream.sdk.player.api.AceStreamPlayer;
import org.acestream.sdk.utils.Logger;
import org.acestream.sdk.utils.MiscUtils;
import org.acestream.sdk.utils.VlcBridge;
import org.acestream.sdk.utils.VlcConstants;
import org.acestream.sdk.utils.Workers;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.acestream.sdk.Constants.CONTENT_TYPE_LIVE;
import static org.acestream.sdk.Constants.CONTENT_TYPE_VOD;
import static org.acestream.sdk.Constants.MIME_HLS;

public class RemoteControlActivity
    extends
        PlaybackManagerAppCompatActivity
    implements
        OnClickListener,
        DeviceStatusListener,
        EngineStatusListener,
        EngineSessionListener,
        ConnectableDeviceListener,
        DeviceDiscoveryListener,
        AceStreamRemoteDeviceListener,
        SelectFileDialogFragment.SelectFileDialogListener
{

    private final static String TAG = "AS/RC";

    private final static int REQUEST_CODE_SELECT_PLAYER = 1;

    private Handler mHandler;

    private View contentImageOverlay;
    private ImageView contentImage;
    private ImageButton btnPlay;
    private ImageButton btnStop;
    private ImageButton btnPrev;
    private ImageButton btnNext;
    private ImageButton btnSelectSubtitleTrack;
    private ImageButton btnSelectAudioTrack;
    private ImageButton btnSwitchVideoSize;
    private ImageButton btnShowPlaylist;
    private View mButtonsContainer;
    private View mBottomButtonsContainer;
    private View mProgressContainer;
    private View mDebugInfoContainer;
    private TextView mDebugInfoText;
    private View mTopInfoContainer;
    private TextView mTopInfoText;
    private View mLiveContainer;
    private View mLiveStatus;
    private View mStreamSelectorContainer;
    private boolean mShowStreamSelectorContainer = false;
    private boolean mShowLiveContainer = false;
    private Button btnGoLive;
    private Button btnSelectStream;
    private TextView txtStatus;
    private TextView txtDuration;
    private TextView txtCurrentTime;
    private LinearLayout progressInfo;
    private ProgressBar progressBuffering;
    private TextView txtRateText;
    private View bottomContainer;
    private View volumeBarContainer;
    private Button btnRateYes;
    private Button btnRateNo;
    private int mDuration = -1;
    private boolean mDraggingVolume = false;
    private boolean mDraggingProgress = false;
    private boolean mEngineSessionStarted = false;
    private AceStreamRemoteDevice mCurrentRemoteDevice = null;
    private ContentType mCurrentContentType = ContentType.UNKNOWN;
    private boolean mDeviceSelectorVisible = false;
    private boolean mPlayerSettingsVisible = false;
    private SelectedPlayer mLastRemoteSelectedPlayer = null;
    private PlayState mPlayState = PlayState.IDLE;

    private SeekBar progressBar;
    private SeekBar volumeBar;
    private Menu mMenu = null;

    private TextView txtTitle;
    private TextView txtHelping;
    private TextView txtDownloadRate;
    private TextView txtUploadRate;
    private boolean mActive = false;
    private boolean mPrebuffering = false;
    private boolean mRestarting = false;
    private ConnectableDevice mCurrentDevice = null;
    private Map<String, Message> mMessages;
    private float mDefaultMessageTextSize = 21;
    private boolean mDeviceConnected = false;
    private boolean mAskResume = false;

    private int mAvailableVideoSizes[] = {
            VlcConstants.SURFACE_BEST_FIT,
            VlcConstants.SURFACE_FIT_SCREEN,
            VlcConstants.SURFACE_FILL,
            VlcConstants.SURFACE_16_9,
            VlcConstants.SURFACE_4_3,
            VlcConstants.SURFACE_ORIGINAL
    };
    private int mPlayerVideoSize = -1;
    private String mPlayerDeinterlaceMode = null;

    private long freezeLiveStatusAt = 0;
    private final static int FREEZE_LIVE_STATUS_FOR = 5000;

    private long freezeLivePosAt = 0;
    private final static int FREEZE_LIVE_POS_FOR = 5000;
    private SelectedPlayer mSelectedPlayer = null;
    private int mLastFileIndex = -1;

    private Runnable mHideVideoSizeMessageTask = new Runnable() {
        @Override
        public void run() {
            hideMessage("videoSize");
        }
    };

    private PlaybackManager.PlaybackStateCallback mPlaybackStateCallback = new PlaybackManager.PlaybackStateCallback() {
        @Override
        public void onPlaylistUpdated() {
            Log.v(TAG, "pstate:onPlaylistUpdated");
        }

        @Override
        public void onStart(@Nullable EngineSession session) {
            Log.v(TAG, "pstate:onStart: session=" + session);
            if(session != null) {
                mLastFileIndex = session.playbackData.mediaFile.index;
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updatePlayButton();
                }
            });
        }

        @Override
        public void onPrebuffering(@Nullable EngineSession session, int progress) {
            Log.v(TAG, "pstate:onPrebuffering: progress=" + progress);
        }

        @Override
        public void onPlay(@Nullable EngineSession session) {
            Log.v(TAG, "pstate:onPlay: session=" + session);

            if(mPlaybackManager == null) {
                Log.v(TAG, "pstate:onPlay: missing playback manager");
                return;
            }

            if(mSelectedPlayer == null) {
                mSelectedPlayer = AceStream.getLastSelectedPlayer();
            }
            if(mSelectedPlayer == null) {
                Log.v(TAG, "pstate:onPlay: missing selected player");
                return;
            }

            if(mSelectedPlayer.type == SelectedPlayer.LOCAL_PLAYER) {
                if(session == null) {
                    throw new IllegalStateException("missing engine session");
                }
                mPlaybackManager.startLocalPlayer(
                        RemoteControlActivity.this,
                        mSelectedPlayer,
                        session.playbackUrl,
                        session.playbackData.mediaFile.mime);
            }
            else if(mSelectedPlayer.type == SelectedPlayer.CONNECTABLE_DEVICE) {
                if(session == null) {
                    throw new IllegalStateException("missing engine session");
                }
                showMessage("playerStatus", 10, R.string.starting_player, (float)1.0);
                mPlaybackManager.startCastDevice(mSelectedPlayer.id1,
                        session.playbackData.resumePlayback,
                        session.playbackData.seekOnStart,
                        mCastResultListener);
            }
            else if(mSelectedPlayer.type == SelectedPlayer.ACESTREAM_DEVICE) {
                // Do nothing.
                // Remote control will be started from cast result listener.
            }
            else {
                // Do nothing
                //throw new IllegalStateException("unexpected player type: " + mSelectedPlayer.type);
            }
        }

        @Override
        public void onStop() {
            Log.v(TAG, "pstate:onStop");
        }
    };

    private PlaybackManager.CastResultListener mCastResultListener = new PlaybackManager.CastResultListener() {
        private boolean mCancelled = false;
        private boolean mWaiting = true;

        @Override
        public void onSuccess() {
            Log.d(TAG, "onEngineStatus:start new content: success: cancelled=" + mCancelled + " hash=" + hashCode());
            mWaiting = false;
            if (!mCancelled) {
                updateCurrentRemoteDevice();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        initControls();
                    }
                });
            }
        }

        @Override
        public void onSuccess(AceStreamRemoteDevice device, SelectedPlayer selectedPlayer) {
            mLastRemoteSelectedPlayer = selectedPlayer;
            onSuccess();
        }

        @Override
        public void onError(final String error) {
            Log.d(TAG, "onEngineStatus:start new content: failed: cancelled=" + mCancelled + " hash=" + hashCode() + " error=" + error);
            mWaiting = false;
            if (!mCancelled) {
                if(mPlaybackManager != null) {
                    mPlaybackManager.stopEngineSession(false);
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        showMessage("engineStatus", 20, error, 1);
                    }
                });
            }
        }

        @Override
        public void onDeviceConnected(AceStreamRemoteDevice device) {
        }

        @Override
        public void onDeviceConnected(ConnectableDevice device) {
        }

        @Override
        public void onDeviceDisconnected(AceStreamRemoteDevice device) {
        }

        @Override
        public boolean isWaiting() {
            return mWaiting;
        }

        @Override
        public void onDeviceDisconnected(ConnectableDevice device) {
        }

        @Override
        public void onCancel() {
            Log.d(TAG, "onEngineStatus:start new content: cancelled: hash=" + hashCode());
            mWaiting = false;
            mCancelled = true;
        }
    };

    @Override
    public void onOutputFormatChanged(AceStreamRemoteDevice device, String outputFormat) {
        Log.d(TAG, "onOutputFormatChanged: format=" + outputFormat);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateProgressBar();
            }
        });
    }

    @Override
    public void onSelectedPlayerChanged(AceStreamRemoteDevice device, SelectedPlayer player) {
        updateUI();
    }

    @Override
    public void onConnected(AceStreamRemoteDevice device) {
        Log.d(TAG, "onConnected: device=" + device);

        if(device != null && !device.equals(mCurrentRemoteDevice)) {
            return;
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                btnSwitchVideoSize.setEnabled(true);
            }
        });
    }

    @Override
    public void onDisconnected(AceStreamRemoteDevice device, boolean cleanShutdown) {
        Log.d(TAG, "onDisconnected: clean=" + cleanShutdown + " device=" + device.toString());

        if(device != null && !device.equals(mCurrentRemoteDevice)) {
            return;
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                btnSwitchVideoSize.setEnabled(false);
                btnSelectAudioTrack.setEnabled(false);
                btnSelectSubtitleTrack.setEnabled(false);
                setCurrentPosition(0L);
                onEngineSessionStopped();
                deviceDisconnected();
            }
        });
    }

    @Override
    public void onMessage(final AceStreamRemoteDevice device, final JsonRpcMessage msg) {
        Log.v(TAG, "onMessage: device=" + (device == null ? "null" : device.toString()) + " msg=" + msg.toString());

        if(device != null && !device.equals(mCurrentRemoteDevice)) {
            return;
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                switch(msg.getMethod()) {
                    case AceStreamRemoteDevice.Messages.PLAYER_PLAYING:
                        setCurrentStatus(MediaControl.PlayStateStatus.Playing);
                        break;
                    case AceStreamRemoteDevice.Messages.PLAYER_PAUSED:
                        setCurrentStatus(MediaControl.PlayStateStatus.Paused);
                        break;
                    case AceStreamRemoteDevice.Messages.PLAYER_BUFFERING:
                        int progress = Math.round(msg.getFloat("progress"));
                        if(progress >= 100) {
                            setCurrentStatus(MediaControl.PlayStateStatus.Playing);
                        }
                        else {
                            setCurrentStatus(MediaControl.PlayStateStatus.Buffering);
                        }
                        break;
                    case AceStreamRemoteDevice.Messages.PLAYBACK_STARTED:
                        setCurrentStatus(MediaControl.PlayStateStatus.Idle);
                        break;
                    case AceStreamRemoteDevice.Messages.PLAYER_CLOSED:
                        setCurrentStatus(MediaControl.PlayStateStatus.Idle);
                        setCurrentPosition(0L);
                        break;
                    case AceStreamRemoteDevice.Messages.PLAYER_END_REACHED:
                        setCurrentStatus(MediaControl.PlayStateStatus.Finished);
                        break;
                    case AceStreamRemoteDevice.Messages.PLAYER_STATUS:
                        Long currentTime = msg.getLong("time");
                        Long duration = msg.getLong("duration");
                        int volume = msg.getInt("volume");
                        int state = msg.getInt("state");
                        mPlayerVideoSize = msg.getInt("videoSize");
                        mPlayerDeinterlaceMode = msg.getString("deinterlaceMode");

                        switch(state) {
                            case VlcConstants.VlcState.OPENING:
                                setCurrentStatus(MediaControl.PlayStateStatus.Buffering);
                                break;
                            case VlcConstants.VlcState.PLAYING:
                                setCurrentStatus(MediaControl.PlayStateStatus.Playing);
                                break;
                            case VlcConstants.VlcState.PAUSED:
                                setCurrentStatus(MediaControl.PlayStateStatus.Paused);
                                break;
                            case VlcConstants.VlcState.STOPPING:
                            case VlcConstants.VlcState.IDLE:
                            case VlcConstants.VlcState.ERROR:
                                setCurrentStatus(MediaControl.PlayStateStatus.Idle);
                                break;
                            case VlcConstants.VlcState.ENDED:
                                setCurrentStatus(MediaControl.PlayStateStatus.Finished);
                                break;
                        }

                        setCurrentDuration(duration);
                        setCurrentPosition(currentTime);
                        setCurrentVolume(volume / 100.0f);

                        JSONArray jsonAudioTracks = msg.getJSONArray("audioTracks");
                        if(jsonAudioTracks != null && jsonAudioTracks.length() > 1) {
                            btnSelectAudioTrack.setEnabled(true);
                        }
                        else {
                            btnSelectAudioTrack.setEnabled(false);
                        }

                        JSONArray jsonSubtitleTracks = msg.getJSONArray("subtitleTracks");
                        if(jsonSubtitleTracks != null && jsonSubtitleTracks.length() > 1) {
                            btnSelectSubtitleTrack.setEnabled(true);
                        }
                        else {
                            btnSelectSubtitleTrack.setEnabled(false);
                        }

                        // enable when player is alive
                        btnSwitchVideoSize.setEnabled(true);

                        break;
                }
            }
        });
    }

    @Override
    public void onAvailable(AceStreamRemoteDevice device) {
    }

    @Override
    public void onUnavailable(AceStreamRemoteDevice device) {
    }

    @Override
    public void onPingFailed(AceStreamRemoteDevice device) {
    }

    @Override
    public void onFileSelected(int fileIndex) {
        Log.d(TAG, "onFileSelected: fileIndex=" + fileIndex);

        Playlist playlist = getCurrentPlaylist();
        if(playlist != null) {
            playlist.setCurrentByFileIndex(fileIndex);
            startCurrentPlaylistItem(null, false);
        }
    }

    @Override
    public void onDialogCancelled() {
    }

    @Override
    public void onDeviceAdded(ConnectableDevice device) {

    }

    @Override
    public void onDeviceRemoved(ConnectableDevice device) {

    }

    @Override
    public void onDeviceAdded(AceStreamRemoteDevice device) {

    }

    @Override
    public void onDeviceRemoved(AceStreamRemoteDevice device) {

    }

    @Override
    public void onCurrentDeviceChanged(AceStreamRemoteDevice device) {
        Log.d(TAG, "onCurrentDeviceChanged: active=" + mActive + " device=" + device.toString());
        if(mActive) {
            setCurrentRemoteDevice(device);
        }
    }

    @Override
    public boolean canStopDiscovery() {
        return true;
    }

    private static class Message {
        String type;
        int priority;
        String text;
        float textSize;
        Message(String aType, int aPriority, String aText, float aTextSize) {
            type = aType;
            priority = aPriority;
            text = aText;
            textSize = aTextSize;
        }

        @Override
        @NonNull
        public String toString() {
            return String.format(Locale.getDefault(),
                    "<Message: type=%s priority=%d size=%.2f text=%s>",
                    type, priority, textSize, text);
        }
    }

    enum PlayState {
        IDLE,
        PLAYING,
        PAUSED
    }

    @Override
    public void onEngineSessionStarted() {
        Log.d(TAG, "onEngineSessionStarted");
        mEngineSessionStarted = true;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
            AceStreamRemoteDevice remoteDevice = null;
            if(mPlaybackManager != null) {
                remoteDevice = mPlaybackManager.getCurrentRemoteDevice();
            }
            updateUI();
            showDeviceSelector(true);
            showPlayerSettings(remoteDevice != null && remoteDevice.isOurPlayer() && mEngineSessionStarted);
            updateProgressBar();
            }
        });
    }

    @Override
    public void onEngineSessionStopped() {
        Log.d(TAG, "onEngineSessionStopped");
        mEngineSessionStarted = false;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(mPlaybackManager != null && mPlaybackManager.getDirectMediaInfo() == null) {
                    showDeviceSelector(false);
                }
                else {
                    showDeviceSelector(true);
                }

                updateUI();

                mShowStreamSelectorContainer = false;
                showLiveContainer(false, false);

                updateProgressBar();
                setPrebuffering(false);
                clearMessages();

                Resources res = getResources();
                txtHelping.setText(res.getString(R.string.helping, 0));
                txtDownloadRate.setText(res.getString(R.string.download_rate, 0));
                txtUploadRate.setText(res.getString(R.string.upload_rate, 0));
            }
        });
    }

    enum ContentType {
        UNKNOWN,
        VIDEO,
        AUDIO,
        LIVE
    }

    @Override
    public void onStatus(IRemoteDevice device, int status) {
        setCurrentStatus(CsdkBridge.convertStatus(status));
    }

    @Override
    public void onPosition(IRemoteDevice device, Long position) {
        setCurrentPosition(position);
    }

    @Override
    public void onDuration(IRemoteDevice device, Long duration) {
        setCurrentDuration(duration);
    }

    @Override
    public void onVolume(IRemoteDevice device, Float volume) {
        setCurrentVolume(volume);
    }

    @Override
    public boolean updatePlayerActivity() {
        return mPrebuffering || mActive;
    }

    @Override
    public void onEngineStatus(final EngineStatus status, final IRemoteDevice remoteDevice) {
        if(!mEngineSessionStarted) {
            onEngineSessionStarted();
        }

        if (remoteDevice == null) {
            EngineSession engineSession = null;
            if(mPlaybackManager != null) {
                engineSession = mPlaybackManager.getEngineSession();
            }

            if (engineSession == null) {
                Log.d(TAG, "onEngineStatus: missing engine session");
                return;
            }

            if (status.playbackSessionId != null && engineSession.playbackSessionId != null) {
                if (!status.playbackSessionId.equals(engineSession.playbackSessionId)) {
                    Log.d(TAG, "onEngineStatus: playback session mismatch, skip status: status=" + status.status + " sess=" + status.playbackSessionId + " curr=" + engineSession.playbackSessionId);
                    return;
                }
            }
        }

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                boolean isOurPlayer = false;
                if(status.selectedPlayer != null) {
                    isOurPlayer = status.selectedPlayer.isOurPlayer();

                    if (mLastRemoteSelectedPlayer == null || !mLastRemoteSelectedPlayer.equals(status.selectedPlayer)) {
                        mLastRemoteSelectedPlayer = status.selectedPlayer;
                        updateUI();
                    }
                }

                if(status.systemInfo != null) {
                    // show debug info
                    mDebugInfoContainer.setVisibility(View.VISIBLE);
                    long usedMemoryPercent = Math.round(100 - status.systemInfo.memoryAvailable / status.systemInfo.memoryTotal * 100);
                    mDebugInfoText.setText(String.format(
                            Locale.getDefault(),
                            "RAM: %d%%    Output: %s",
                            usedMemoryPercent,
                            status.outputFormat
                            ));
                }
                else {
                    mDebugInfoContainer.setVisibility(View.GONE);
                    mDebugInfoText.setText("");
                }

                Resources res = getResources();
                txtHelping.setText(res.getString(R.string.helping, status.peers));
                txtDownloadRate.setText(res.getString(R.string.download_rate, status.speedDown));
                txtUploadRate.setText(res.getString(R.string.upload_rate, status.speedUp));

                //noinspection IfCanBeSwitch
                if(status.status.equals("prebuf")) {
                    showMessage("engineStatus", 20, res.getString(R.string.status_prebuffering_short, status.progress), 1);
                }
                else if(status.status.equals("checking")) {
                    showMessage("engineStatus", 20, res.getString(R.string.status_checking_short, status.progress), 1);
                }
                else {
                    hideMessage("engineStatus");
                }

                // livepos
                progressBar.setTag(status.livePos);
                btnGoLive.setTag(status.livePos);
                if (status.livePos == null) {
                    showLiveContainer(false);
                } else {
                    showLiveContainer(true);

                    if (status.livePos.first == -1) {
                        //pass
                    } else if (status.livePos.last == -1) {
                        //pass
                    } else if (status.livePos.pos == -1) {
                        //pass
                    } else if (status.livePos.lastTimestamp == -1) {
                        //pass
                    } else if (status.livePos.firstTimestamp == -1) {
                        //pass
                    } else {
                        int duration = status.livePos.lastTimestamp - status.livePos.firstTimestamp;
                        int pieces = status.livePos.last - status.livePos.first;
                        int offset = status.livePos.pos - status.livePos.first;

                        long posAge = new Date().getTime() - freezeLivePosAt;
                        if (!mDraggingProgress && (posAge > FREEZE_LIVE_POS_FOR)) {
                            progressBar.setMax(pieces);
                            progressBar.setProgress(offset);
                            txtDuration.setText("00:00");
                            txtCurrentTime.setText("-" + MiscUtils.durationStringFromMilliseconds(duration * 1000));
                        }

                        long statusAge = new Date().getTime() - freezeLiveStatusAt;
                        if (statusAge > FREEZE_LIVE_STATUS_FOR) {
                            if (status.livePos.isLive) {
                                mLiveStatus.setBackgroundResource(R.drawable.circle_blue);
                            } else {
                                mLiveStatus.setBackgroundResource(R.drawable.circle_yellow);
                            }
                        }
                    }
                }

                // list of streams
                // show only for remote device, our player and http output
                if (remoteDevice != null && isOurPlayer && status.streams.size() > 0 && TextUtils.equals(status.outputFormat, "http")) {
                    if(status.currentStreamIndex < 0 || status.currentStreamIndex >= status.streams.size()) {
                        Log.w(TAG, "bad remote stream index: index=" + status.currentStreamIndex + " streams=" + status.streams.size());
                        showStreamSelectorContainer(false);
                    }
                    else {
                        showStreamSelectorContainer(true);

                        String streamName;
                        streamName = status.streams.get(status.currentStreamIndex).getName();

                        btnSelectStream.setText(streamName);

                        if (streamName.length() > 6) {
                            btnSelectStream.setTextSize(8);
                        } else {
                            btnSelectStream.setTextSize(12);
                        }
                    }
                } else {
                    showStreamSelectorContainer(false);
                }

                if(status.isLive == 0) {
                    PlaylistItem playlistItem = getCurrentPlaylistItem();
                    if(playlistItem != null) {
                        playlistItem.setContentType(CONTENT_TYPE_VOD);
                    }
                    setContentType(ContentType.VIDEO);
                }
                else if(status.isLive == 1) {
                    PlaylistItem playlistItem = getCurrentPlaylistItem();
                    if(playlistItem != null) {
                        playlistItem.setContentType(CONTENT_TYPE_LIVE);
                    }
                    setContentType(ContentType.LIVE);
                }

                if(status.fileIndex != -1 && status.fileIndex != mLastFileIndex) {
                    mLastFileIndex = status.fileIndex;
                    initPlaylistControls();
                }
            }
        });

    }

    @Override
    public void onDeviceConnected(ConnectableDevice device) {
        Log.d(TAG, "onDeviceConnected");
    }

    @Override
    public void onDeviceDisconnected(ConnectableDevice device, boolean cleanShutdown) {
        Log.d(TAG, "onDeviceDisconnected: clean=" + cleanShutdown);
        deviceDisconnected();
    }

    private void deviceDisconnected() {
        showPlayerSettings(false);
        setPlayState(PlayState.PAUSED);

        mDeviceConnected = false;
        btnPrev.setEnabled(false);
        btnNext.setEnabled(false);
        volumeBar.setEnabled(false);
        progressBar.setEnabled(false);
        mBottomButtonsContainer.setVisibility(View.GONE);

        updatePlayButton();
    }

    @SuppressWarnings("deprecation")
    private void setPlayState(PlayState state) {
        mPlayState = state;
        Resources res = getResources();
        switch(state) {
            case PAUSED:
                if(contentImageOverlay.getVisibility() == View.VISIBLE) {
                    btnPlay.setVisibility(View.GONE);
                }
                else {
                    if (btnPlay.getVisibility() == View.VISIBLE) {
                        contentImage.setImageDrawable(res.getDrawable(R.drawable.rc_image_blank));
                    } else {
                        ContentType contentType = (ContentType) contentImage.getTag();
                        if (contentType != null) {
                            setContentTypeImage(contentType);
                        }
                    }
                }

                btnPlay.setImageDrawable(getResources().getDrawable(R.drawable.ic_play_arrow_white_48dp));
                btnPlay.setTag("play");

                btnStop.setImageDrawable(getResources().getDrawable(R.drawable.rc_stop_selector));
                btnStop.setTag("stop");

                break;
            case PLAYING:
                if(contentImageOverlay.getVisibility() == View.VISIBLE) {
                    btnPlay.setVisibility(View.GONE);
                }
                else {
                    if (btnPlay.getVisibility() == View.VISIBLE) {
                        contentImage.setImageDrawable(res.getDrawable(R.drawable.rc_image_blank));
                    } else {
                        ContentType contentType = (ContentType) contentImage.getTag();
                        if (contentType != null) {
                            setContentTypeImage(contentType);
                        }
                    }
                }

                btnPlay.setImageDrawable(getResources().getDrawable(R.drawable.ic_pause_white_48dp));
                btnPlay.setTag("pause");

                btnStop.setImageDrawable(getResources().getDrawable(R.drawable.rc_stop_selector));
                btnStop.setTag("stop");

                break;
            case IDLE:
                btnPlay.setVisibility(View.GONE);
                ContentType contentType = (ContentType)contentImage.getTag();
                if(contentType != null) {
                    setContentTypeImage(contentType);
                }

                btnStop.setImageDrawable(getResources().getDrawable(R.drawable.rc_restart_selector));
                btnStop.setTag("restart");

                break;
        }
    }

    private boolean shouldExit(Intent callingIntent) {
        return callingIntent != null && callingIntent.getBooleanExtra("shutdown", false);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");

        if(shouldExit(getIntent())) {
            Log.d(TAG, "onCreate: should exit now");
            finish();
            return;
        }

        String targetApp = AceStream.getTargetApp();
        if(targetApp != null) {
            // redirect intent
            AceStreamEngineBaseApplication.redirectIntent(this, getIntent(), targetApp);
            finish();
            return;
        }

        mAskResume = AceStreamEngineBaseApplication.getPreferences().getBoolean("dialog_confirm_resume", false);

        mHandler = new Handler();
        mMessages = new ArrayMap<>();
        setContentView(R.layout.remote_control_activity);

        // enable ActionBar app icon to behave as action to toggle nav drawer
        setSupportActionBar((Toolbar)findViewById(R.id.main_toolbar));

        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeButtonEnabled(true);
            actionBar.setBackgroundDrawable(new ColorDrawable(ContextCompat.getColor(
                    this, R.color.main_accent)));
        }

        // setup controls
        volumeBarContainer = findViewById(R.id.volume_bar_container);
        txtTitle = (TextView) findViewById(R.id.title);
        txtStatus = (TextView) findViewById(R.id.status);
        txtDuration = (TextView) findViewById(R.id.duration);
        txtCurrentTime = (TextView) findViewById(R.id.current_time);
        progressBuffering = (ProgressBar) findViewById(R.id.progress_buffering);

        txtHelping = (TextView) findViewById(R.id.helping);
        txtDownloadRate = (TextView) findViewById(R.id.download_rate);
        txtUploadRate = (TextView) findViewById(R.id.upload_rate);

        progressBar = (SeekBar) findViewById(R.id.progress_bar);
        volumeBar = (SeekBar) findViewById(R.id.volume_bar);

        progressBar.setOnSeekBarChangeListener(onProgressBarChanged);
        volumeBar.setOnSeekBarChangeListener(onVolumeBarChanged);

        contentImage = (ImageView) findViewById(R.id.content_image);
        contentImageOverlay = findViewById(R.id.content_image_overlay);

        btnRateNo = (Button) findViewById(R.id.btn_rate_no);
        btnRateYes = (Button) findViewById(R.id.btn_rate_yes);
        txtRateText = (TextView) findViewById(R.id.rate_text);
        bottomContainer = findViewById(R.id.bottom_container);

        btnPlay = (ImageButton) findViewById(R.id.play);
        btnStop = (ImageButton) findViewById(R.id.stop);
        btnPrev = (ImageButton) findViewById(R.id.prev);
        btnNext = (ImageButton) findViewById(R.id.next);
        btnSelectSubtitleTrack = (ImageButton) findViewById(R.id.select_subtitle_track);
        btnSelectAudioTrack = (ImageButton) findViewById(R.id.select_audio_track);
        btnSwitchVideoSize = (ImageButton) findViewById(R.id.switch_video_size);
        btnShowPlaylist = (ImageButton) findViewById(R.id.show_playlist);
        btnGoLive = (Button) findViewById(R.id.btn_go_live);
        mLiveContainer = findViewById(R.id.live_container);
        mLiveStatus = findViewById(R.id.live_status);
        mStreamSelectorContainer = findViewById(R.id.stream_selector_container);
        btnSelectStream = (Button) findViewById(R.id.btn_select_stream);
        progressInfo = (LinearLayout) findViewById(R.id.progress_info);
        mButtonsContainer = findViewById(R.id.buttons_container);
        mBottomButtonsContainer = findViewById(R.id.bottom_buttons_container);
        mProgressContainer = findViewById(R.id.progress_container);
        mDebugInfoContainer = findViewById(R.id.debug_info_container);
        mDebugInfoText = (TextView)findViewById(R.id.debug_info_text);
        mTopInfoContainer = findViewById(R.id.top_info_container);
        mTopInfoText = (TextView)findViewById(R.id.top_info_text);

        btnPlay.setOnClickListener(this);
        btnStop.setOnClickListener(this);
        btnPrev.setOnClickListener(this);
        btnNext.setOnClickListener(this);
        btnSelectSubtitleTrack.setOnClickListener(this);
        btnSelectAudioTrack.setOnClickListener(this);
        btnSwitchVideoSize.setOnClickListener(this);
        btnGoLive.setOnClickListener(this);
        btnSelectStream.setOnClickListener(this);
        btnShowPlaylist.setOnClickListener(this);

        btnRateNo.setOnClickListener(this);
        btnRateYes.setOnClickListener(this);

        // getTextSize() returns size in pixels, but we want SP
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        mDefaultMessageTextSize = txtStatus.getTextSize() / metrics.density;
        Log.d(TAG, "default message text size: " + mDefaultMessageTextSize);

        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if(extras != null) {
            String selectedPlayer = extras.getString("selectedPlayer");
            if(selectedPlayer != null) {
                try {
                    mLastRemoteSelectedPlayer = SelectedPlayer.fromJson(selectedPlayer);
                }
                catch(JSONException e) {
                    Log.e(TAG, "onCreate: failed to deserialize selected player: " + e.getMessage());
                }
            }
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        Log.d(TAG, "onSupportNavigateUp");
        super.onSupportNavigateUp();
        if(!AceStreamEngineBaseApplication.useVlcBridge()) {
            startMainActivity();
        }
        finish();
        return true;
    }

    private void resetControls() {
        resetControls(true);
    }

    private void resetControls(boolean disable) {
        Log.d(TAG, "reset controls");
        volumeBar.setProgress(0);
        progressBar.setProgress(0);
        progressBar.setMax(0);

        mDuration = -1;
        txtCurrentTime.setText("0:00");
        txtDuration.setText("0:00");
        txtTitle.setText("");

        if(disable) {
            btnPrev.setEnabled(false);
            btnNext.setEnabled(false);
            showDeviceSelector(false);
            volumeBar.setEnabled(false);
            progressBar.setEnabled(false);
            progressBar.setVisibility(View.GONE);
            progressInfo.setVisibility(View.GONE);
            mLiveContainer.setVisibility(View.GONE);
            showStreamSelectorContainer(false, false);
            btnSelectAudioTrack.setEnabled(false);
            btnSelectSubtitleTrack.setEnabled(false);
            btnSwitchVideoSize.setEnabled(false);
            mBottomButtonsContainer.setVisibility(View.GONE);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        mActive = false;

        if(mPlaybackManager != null) {
            mPlaybackManager.removePlaybackStatusListener(this);
            mPlaybackManager.removeEngineStatusListener(this);
            mPlaybackManager.removeEngineSessionListener(this);
            mPlaybackManager.removeDeviceStatusListener(this);
            mPlaybackManager.removeDeviceDiscoveryListener(this);
            mPlaybackManager.removePlaybackStateCallback(mPlaybackStateCallback);
        }

        if(mCurrentRemoteDevice != null) {
            mCurrentRemoteDevice.removeListener(this);
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "onNewIntent: shutdown=" + intent.getBooleanExtra("shutdown", false));
        if(shouldExit(intent)) {
            finish();
        }
    }

    @Override
    public void onStop() {
        if(mPlaybackManager != null) {
            mPlaybackManager.unregisterCastResultListener(mCastResultListener);
        }

        // Call super.onStop() in the end because playback manager is set to null there
        super.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        mActive = true;
    }

    @Override
    public void onClick(View v) {
        int i1 = v.getId();//TODO: translate
//TODO: translate
// filter audio streams because of bug in engine (cannot select audio stream)
//TODO: translate
// rotate
        if (i1 == R.id.play) {
            if (!mPrebuffering && mPlaybackManager != null) {
                MediaControl control = mPlaybackManager.getMediaControl();
                AceStreamRemoteDevice remoteDevice = mPlaybackManager.getCurrentRemoteDevice();
                Log.d(TAG, "play: cmd=" + v.getTag().toString());

                switch (v.getTag().toString()) {
                    case "play":
                        if (control != null) {
                            control.play(null);
                        } else if (remoteDevice != null) {
                            remoteDevice.play();
                        } else {
                            Log.d(TAG, "play: no control");
                        }
                        break;
                    case "pause":
                        if (control != null) {
                            control.pause(null);
                        } else if (remoteDevice != null) {
                            remoteDevice.pause();
                        } else {
                            Log.d(TAG, "play: no control");
                        }
                        break;
                }

                if (control != null) {
                    // update status ASAP
                    control.getPlayState(new MediaControl.PlayStateListener() {
                        @Override
                        public void onSuccess(MediaControl.PlayStateStatus status) {
                            setCurrentStatus(status);
                        }

                        @Override
                        public void onError(ServiceCommandError error) {
                        }
                    });
                }
            }
        } else if (i1 == R.id.stop) {
            if (v.getTag() != null) {
                Log.d(TAG, "stop clicked: tag=" + v.getTag().toString());
                switch (v.getTag().toString()) {
                    case "stop": {
                        setCurrentPosition(0L);
                        showStreamSelectorContainer(false);
                        showLiveContainer(false);
                        btnSelectAudioTrack.setEnabled(false);
                        btnSelectSubtitleTrack.setEnabled(false);
                        btnSwitchVideoSize.setEnabled(false);
                        updatePlayButton();

                        if (AceStreamEngineBaseApplication.useVlcBridge()) {
                            VlcBridge.stopPlayback(false, true, true);
                        }

                        if (mPlaybackManager != null) {
                            mPlaybackManager.stopRemotePlayback(true);
                            mPlaybackManager.stopEngineSession(true);
                        }
                        break;
                    }
                    case "restart": {
                        restartPlayback();
                        break;
                    }
                }
            }
        } else if (i1 == R.id.prev) {
            switchPlaylistItem(-1);
        } else if (i1 == R.id.next) {
            switchPlaylistItem(1);
        } else if (i1 == R.id.btn_rate_no) {
            RateManager.showRateDialog(this, 0);
            bottomContainer.setVisibility(View.GONE);
        } else if (i1 == R.id.btn_rate_yes) {
            RateManager.showRateDialog(this, 1);
            bottomContainer.setVisibility(View.GONE);
        } else if (i1 == R.id.btn_go_live) {
            boolean isLive = true;
            Object tag = btnGoLive.getTag();
            if (tag != null && tag instanceof EngineStatus.LivePosition) {
                EngineStatus.LivePosition livePos = (EngineStatus.LivePosition) tag;
                isLive = livePos.isLive;
            }
            if (!isLive) {
                if (mPlaybackManager != null) {
                    mPlaybackManager.liveSeek(-1);
                }
                mLiveStatus.setBackgroundResource(R.drawable.circle_blue);

                progressBar.setProgress(progressBar.getMax());
                txtDuration.setText("00:00");

                // to freeze status and pos for some time
                freezeLiveStatusAt = new Date().getTime();
                freezeLivePosAt = new Date().getTime();
            }
        } else if (i1 == R.id.select_subtitle_track) {
            Playlist playlist = getCurrentPlaylist();
            if (playlist == null) {
                Log.d(TAG, "click:select_subtitle_track: no playlist");
                return;
            }
            PlaylistItem playlistItem = playlist.getCurrentItem();
            if (playlistItem == null) {
                Log.d(TAG, "click:select_subtitle_track: no playlist item");
                return;
            }
            String[] entries = new String[playlistItem.getSubtitleTracksCount()];
            final int[] entryValues = new int[playlistItem.getSubtitleTracksCount()];
            int selectedId = -1;
            List<TrackDescription> tracks = playlistItem.getSubtitleTracks();
            for (int i = 0; i < tracks.size(); i++) {
                entries[i] = tracks.get(i).name;
                entryValues[i] = tracks.get(i).id;

                if (entryValues[i] == playlistItem.currentSubtitleTrack) {
                    selectedId = i;
                }
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Select subtitles");
            builder.setSingleChoiceItems(entries, selectedId, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    if (mPlaybackManager != null) {
                        AceStreamRemoteDevice remoteDevice = mPlaybackManager.getCurrentRemoteDevice();
                        if (remoteDevice != null) {
                            remoteDevice.setSpuTrack(entryValues[i]);
                        }
                    }
                    dialogInterface.dismiss();
                }
            });
            Dialog dialog = builder.create();
            dialog.show();
        } else if (i1 == R.id.select_audio_track) {
            Playlist playlist = getCurrentPlaylist();
            if (playlist == null) {
                Log.d(TAG, "click:select_audio_track: no playlist");
                return;
            }
            PlaylistItem playlistItem = playlist.getCurrentItem();
            if (playlistItem == null) {
                Log.d(TAG, "click:select_audio_track: no playlist item");
                return;
            }
            String[] entries = new String[playlistItem.getAudioTracksCount()];
            final int[] entryValues = new int[playlistItem.getAudioTracksCount()];
            int selectedId = -1;
            List<TrackDescription> tracks = playlistItem.getAudioTracks();
            for (int i = 0; i < tracks.size(); i++) {
                entries[i] = tracks.get(i).name;
                entryValues[i] = tracks.get(i).id;

                if (entryValues[i] == playlistItem.currentAudioTrack) {
                    selectedId = i;
                }
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Select audio track");
            builder.setSingleChoiceItems(entries, selectedId, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    if (mPlaybackManager != null) {
                        AceStreamRemoteDevice remoteDevice = mPlaybackManager.getCurrentRemoteDevice();
                        if (remoteDevice != null) {
                            remoteDevice.setAudioTrack(entryValues[i]);
                        }
                    }
                    dialogInterface.dismiss();
                }
            });
            Dialog dialog = builder.create();
            dialog.show();
        } else if (i1 == R.id.btn_select_stream) {
            Playlist playlist = getCurrentPlaylist();
            if (playlist == null) {
                Log.d(TAG, "click:select_stream: no playlist");
                return;
            }
            final List<ContentStream> originalStreams = playlist.getStreams();
            final List<ContentStream> streams = new ArrayList<>();
            for (ContentStream stream : originalStreams) {
                if (!stream.getName().startsWith("Audio")) {
                    streams.add(stream);
                }
            }
            if (streams.size() == 0) {
                Log.d(TAG, "click:select_stream: no streams");
                return;
            }
            String[] entries = new String[streams.size()];
            for (int i = 0; i < streams.size(); i++) {
                entries[i] = streams.get(i).getName();
            }
            int selectedId = playlist.getCurrentStreamIndex();
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Select stream");
            builder.setSingleChoiceItems(entries, selectedId, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    switchStream(i, streams.get(i).streamType);
                    dialogInterface.dismiss();
                }
            });
            Dialog dialog = builder.create();
            dialog.show();

        } else if (i1 == R.id.switch_video_size) {
            if (mPlayerVideoSize == -1) {
                Log.d(TAG, "switchVideoSize: current video size is not set");
            }
            else {
                int newVideoSize = mPlayerVideoSize + 1;
                if (newVideoSize >= mAvailableVideoSizes.length) {
                    newVideoSize = 0;
                }
                if (mPlaybackManager != null) {
                    AceStreamRemoteDevice remoteDevice = mPlaybackManager.getCurrentRemoteDevice();
                    if (remoteDevice != null) {
                        String name = getVideoSizeName(newVideoSize);
                        String title = getVideoSizeTitle(newVideoSize);
                        if (name != null) {
                            showMessage("videoSize", 30, title, 2.0f);
                            remoteDevice.setVideoSize(name);

                            //TODO: freeze local value to prevent updating from "playerStatus" in which
                            // we can receive old value on the nearest update.
                            mPlayerVideoSize = newVideoSize;

                            // hide message later
                            mHandler.removeCallbacks(mHideVideoSizeMessageTask);
                            mHandler.postDelayed(mHideVideoSizeMessageTask, 2500);
                        }
                    }
                }
            }
        } else if (i1 == R.id.show_playlist) {
            Log.d(TAG, "show playlist");
            Playlist playlist = getCurrentPlaylist();
            if (playlist == null) {
                Log.e(TAG, "show_playlist: missing current playlist");
            }
            else {
                int size = playlist.getSize();
                String[] fileNames = new String[size];
                int[] fileIndexes = new int[size];
                for (int i = 0; i < size; i++) {
                    PlaylistItem item = playlist.getItem(i);
                    fileNames[i] = item.getTitle();
                    fileIndexes[i] = item.getFileIndex();
                }
                SelectFileDialogFragment dialogFragment = new SelectFileDialogFragment();
                Bundle bundle = new Bundle();
                bundle.putStringArray("fileNames", fileNames);
                bundle.putIntArray("fileIndexes", fileIndexes);
                dialogFragment.setArguments(bundle);
                dialogFragment.show(getSupportFragmentManager(), "select_file_dialog");
            }
        }
    }

    @MainThread
    private void switchStream(int streamIndex, int streamType) {
        Log.d(TAG, "switchStream: type=" + streamType + " index=" + streamIndex);

        if(mPlaybackManager == null) {
            Log.e(TAG, "switchStream: missing playback manager");
            return;
        }

        ConnectableDevice currentDevice = mPlaybackManager.getCurrentDevice();
        AceStreamRemoteDevice remoteDevice = mPlaybackManager.getCurrentRemoteDevice();

        if(currentDevice == null && remoteDevice == null) {
            Log.d(TAG, "switchStream: missing current device");
            return;
        }

        if(streamType == ContentStream.StreamType.HLS) {
            if(remoteDevice != null) {
                remoteDevice.setHlsStream(streamIndex);
            }
        }
        else if(streamType == ContentStream.StreamType.DIRECT) {
            Playlist playlist = getCurrentPlaylist();
            if (playlist == null) {
                Log.d(TAG, "switchStream: missing current playlist");
                return;
            }

            Log.d(TAG, "switchStream: current=" + playlist.getCurrentStreamIndex() + " new=" + streamIndex);
            playlist.setCurrentStreamIndex(streamIndex);

            // start in current selected player
            startCurrentPlaylistItem(null, true);
        }
        else {
            Log.e(TAG, "switchStream: unknown stream type: index=" + streamIndex + " type=" + streamType);
        }
    }

    @MainThread
    private void switchPlaylistItem(int direction) {
        if(mPlaybackManager == null) {
            Log.e(TAG, "switchPlaylistItem: missing playback manager");
            return;
        }

        Playlist playlist = getCurrentPlaylist();
        if(playlist == null) {
            Log.d(TAG, "switchPlaylistItem: missing current playlist");
            return;
        }

        Log.d(TAG, "switchPlaylistItem: index=" + playlist.getCurrentIndex() + " direction=" + direction);

        //PlaylistItem item;
        if(direction > 0) {
            //item = playlist.getNextItem();
            playlist.setCurrent(playlist.getCurrentIndex()+1);
        }
        else {
            //item = playlist.getPrevItem();
            playlist.setCurrent(playlist.getCurrentIndex()-1);
        }

        startCurrentPlaylistItem(null, false);
    }

    private void showResolver() {
        PlaylistItem item = getCurrentPlaylistItem();
        if(item == null) {
            Log.e(TAG, "showResolver: missing current playlist item");
            AceStreamEngineBaseApplication.toast(R.string.empty_playlist);
            return;
        }

        Log.d(TAG, "showResolver");

        Intent intent = new AceStream.Resolver.IntentBuilder(
                this,
                item.getInfohash(),
                item.getContentType(),
                item.getMimeType()).build();
        startActivityForResult(intent, REQUEST_CODE_SELECT_PLAYER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_SELECT_PLAYER) {
            if(resultCode == Activity.RESULT_OK){
                SelectedPlayer player = SelectedPlayer.fromIntentExtra(data);
                if(mSelectedPlayer != null && mSelectedPlayer.equals(player)) {
                    if(player.type == SelectedPlayer.ACESTREAM_DEVICE
                            && mCurrentRemoteDevice != null
                            && mCurrentRemoteDevice.isConnected()) {
                        App.v(TAG, "onActivityResult:player-selected: skip same player");
                        return;
                    }
                }
                boolean switchingPlayer = (mPlayState != PlayState.IDLE);
                startCurrentPlaylistItem(player, switchingPlayer);
            }
            else if(resultCode == AceStream.Resolver.RESULT_CLOSE_CALLER) {
                finish();
            }
        }
    }

    private void startCurrentPlaylistItem(SelectedPlayer player, boolean forceResume) {
        Log.v(TAG, "startCurrentPlaylistItem: player=" + player);
        boolean playerChanged = false;

        if(player == null) {
            // start in current player
            if(mSelectedPlayer == null) {
                mSelectedPlayer = AceStream.getLastSelectedPlayer();
                if(mSelectedPlayer == null) {
                    Log.e(TAG, "startCurrentPlaylistItem: missing selected player");
                    return;
                }
            }
        }
        else {
            if(mSelectedPlayer == null) {
                playerChanged = true;
            }
            else if(!mSelectedPlayer.equals(player)) {
                playerChanged = true;
            }
            mSelectedPlayer = player;
        }

        // stop current playback
        if(AceStreamEngineBaseApplication.useVlcBridge()) {
            VlcBridge.saveMetadata();
        }
        mPlaybackManager.stopRemotePlayback(playerChanged);
        mPlaybackManager.stopEngineSession(false);
        resetControls(false);
        initPlaylistControls();
        updateProgressBar();

        final Playlist playlist = getCurrentPlaylist();
        if(playlist == null) {
            Log.e(TAG, "startCurrentPlaylistItem: missing current playlist");
            return;
        }

        final PlaylistItem item = playlist.getCurrentItem();
        if(item == null) {
            Log.e(TAG, "startCurrentPlaylistItem: missing current playlist item");
            return;
        }

        if(mSelectedPlayer.type == SelectedPlayer.OUR_PLAYER) {
            startOurPlayer(playlist, forceResume);
        }
        else {
            final long savedTime = getSavedTime(item);

            Log.v(TAG, "startCurrentPlaylistItem: savedTime=" + savedTime);

            if(mAskResume && mSelectedPlayer.canResume() && !forceResume && savedTime > 0) {
                AlertDialog dialog = new AlertDialog.Builder(this)
                        .setMessage(R.string.confirm_resume)
                        .setPositiveButton(R.string.resume_from_position, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                startPlayer(mSelectedPlayer, playlist, item, false, savedTime);
                            }
                        })
                        .setNegativeButton(R.string.play_from_start, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                startPlayer(mSelectedPlayer, playlist, item, true, savedTime);
                            }
                        })
                        .create();
                dialog.setCancelable(false);
                dialog.show();
            }
            else {
                startPlayer(mSelectedPlayer, playlist, item, false, savedTime);
            }
        }
    }

    private void startPlayer(SelectedPlayer player, Playlist playlist, PlaylistItem item, boolean fromStart, long savedTime) {
        showMessage("engineStatus", 20, R.string.starting, 1);
        mPlaybackManager.startPlayer(
                this,
                player,
                playlist.getContentDescriptor(),
                item.getMediaFile(),
                playlist.getCurrentStreamIndex(),
                mCastResultListener,
                new EngineSessionStartListener() {
                    @Override
                    public void onSuccess(EngineSession session) {
                        Logger.v(TAG, "engine session started");
                    }

                    @Override
                    public void onError(final String error) {
                        Logger.v(TAG, "engine session failed: error=" + error);
                        Workers.runOnMainThread(new Runnable() {
                            @Override
                            public void run() {
                                showMessage("engineStatus", 20, error, 1);
                            }
                        });
                    }
                },
                fromStart ? 0 : 1,
                savedTime
        );

        if(AceStreamEngineBaseApplication.useVlcBridge() && player.isRemote()) {
            // Create VLC playback session without starting RC
            new VlcBridge.LoadP2PPlaylistIntentBuilder(playlist.getContentDescriptor())
                    .setPlayer(player)
                    .setMetadata(playlist.getMetadata())
                    .setMediaFile(item.getMediaFile())
                    .setStart(true)
                    .setSkipPlayer(true)
                    .setSkipResettingDevices(true)
                    .send();
        }
    }

    private long getSavedTime(PlaylistItem item) {
        //TODO: how can we get saved time from medialibrary?
        return 0;
    }

    private void startOurPlayer(@NonNull Playlist playlist, boolean forceResume) {
        if(AceStreamEngineBaseApplication.useVlcBridge()) {
            VlcBridge.LoadP2PPlaylistIntentBuilder builder = new VlcBridge.LoadP2PPlaylistIntentBuilder(playlist.getContentDescriptor())
                    .setPlayer(SelectedPlayer.getOurPlayer())
                    .setPlaylistPosition(playlist.getCurrentIndex())
                    .setMetadata(playlist.getMetadata())
                    .setMediaFiles(playlist.getMetadata().files);
            if (forceResume) {
                builder.setAskResume(false);
            }
            builder.send();
        }
        else {
            MediaFilesResponse metadata = playlist.getMetadata();
            AceStreamPlayer.PlaylistItem[] targetPlaylist = new AceStreamPlayer.PlaylistItem[playlist.getSize()];
            for(int i = 0; i < metadata.files.length; i++) {
                targetPlaylist[i] = new AceStreamPlayer.PlaylistItem(
                        playlist.getContentDescriptor().getMrl(metadata.files[i].index).toString(),
                        metadata.files[i].filename);
            }

            Intent intent = AceStreamPlayer.getPlayerIntent();
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(AceStreamPlayer.EXTRA_PLAYLIST, AceStreamPlayer.Playlist.toJson(targetPlaylist));
            intent.putExtra(AceStreamPlayer.EXTRA_PLAYLIST_POSITION, playlist.getCurrentIndex());
            startActivity(intent);
        }

        mPlaybackManager.setLastSelectedDeviceId(null);
    }

    @MainThread
    private void restartPlayback() {
        Log.d(TAG, "restartPlayback");
        showResolver();
    }

    private void showMessage(String type, int priority, int resourceId, float textSize) {
        try {
            String message = getResources().getString(resourceId);
            showMessage(type, priority, message, textSize);
        }
        catch(Exception e) {
            Log.e(TAG, "showMessage() failed", e);
        }
    }

    private void showMessage(String type, int priority, String message, float textSize) {
        if(message == null) {
            Logger.v(TAG, "showMessage: empty text");
            return;
        }

        Message msg = new Message(type, priority, message, textSize);
        mMessages.put(type, msg);

        Message currentMsg = (Message)txtStatus.getTag();
        Logger.v(TAG, "showMessage: msg=" + msg + " current=" + currentMsg);
        if(currentMsg == null || currentMsg.type.equals(msg.type) ||  msg.priority > currentMsg.priority) {
            contentImageOverlay.setVisibility(View.VISIBLE);
            btnPlay.setVisibility(View.GONE);
            if(message.equals("_buffering_")) {
                progressBuffering.setVisibility(View.VISIBLE);
                txtStatus.setVisibility(View.GONE);
            }
            else {
                progressBuffering.setVisibility(View.GONE);
                txtStatus.setTextSize(mDefaultMessageTextSize * textSize);
                txtStatus.setVisibility(View.VISIBLE);
                txtStatus.setText(message);
                txtStatus.setTag(msg);
            }
        }
    }

    private void hideMessage(String type) {
        if(!mMessages.containsKey(type)) {
            return;
        }
        mMessages.remove(type);

        Message selectedMessage = null;

        // select message with the highest priority
        if(mMessages.size() > 0) {
            for (Message msg : mMessages.values()) {
                if (selectedMessage == null) {
                    selectedMessage = msg;
                } else if (msg.priority > selectedMessage.priority) {
                    selectedMessage = msg;
                }
            }
        }

        if(selectedMessage == null) {
            // no more messages
            contentImageOverlay.setVisibility(View.GONE);
            btnPlay.setVisibility(View.VISIBLE);
            txtStatus.setVisibility(View.GONE);
            txtStatus.setText("");
            txtStatus.setTag(null);
            txtStatus.setTextSize(mDefaultMessageTextSize);
            progressBuffering.setVisibility(View.GONE);
        }
        else {
            contentImageOverlay.setVisibility(View.VISIBLE);
            btnPlay.setVisibility(View.GONE);
            if(selectedMessage.text.equals("_buffering_")) {
                progressBuffering.setVisibility(View.VISIBLE);
                txtStatus.setVisibility(View.GONE);
            }
            else {
                progressBuffering.setVisibility(View.GONE);
                txtStatus.setVisibility(View.VISIBLE);
                txtStatus.setText(selectedMessage.text);
                txtStatus.setTag(selectedMessage);
                txtStatus.setTextSize(mDefaultMessageTextSize * selectedMessage.textSize);
            }
        }
    }

    private void clearMessages() {
        mMessages.clear();
        contentImageOverlay.setVisibility(View.GONE);
        txtStatus.setVisibility(View.GONE);
        txtStatus.setText("");
        txtStatus.setTag(null);
        txtStatus.setTextSize(mDefaultMessageTextSize);
        progressBuffering.setVisibility(View.GONE);
    }

    private void initControls() {
        if(mPlaybackManager == null) {
            Log.e(TAG, "initControls: missing playback manager");
            return;
        }

        Resources res = getResources();
        ConnectableDevice device = mPlaybackManager.getCurrentDevice();
        AceStreamRemoteDevice remoteDevice = mPlaybackManager.getCurrentRemoteDevice();

        if(mPlaybackManager.getDirectMediaInfo() == null) {
            if(mEngineSessionStarted) {
                Log.v(TAG, "initControls: engine session is started, show device selector");
                showDeviceSelector(true);
            }
            else {
                Log.v(TAG, "initControls: engine session is not started, hide device selector");
                showDeviceSelector(false);
                txtHelping.setText(res.getString(R.string.helping, 0));
                txtDownloadRate.setText(res.getString(R.string.download_rate, 0));
                txtUploadRate.setText(res.getString(R.string.upload_rate, 0));
            }
        }
        else {
            showDeviceSelector(true);
        }

        showPlayerSettings(remoteDevice != null && remoteDevice.isOurPlayer() && mEngineSessionStarted);

        if(remoteDevice != null) {
            volumeBar.setEnabled(true);
        }
        else if (device != null) {
            Log.d(TAG, "initControls: capability: Volume_Set=" + device.hasCapability(VolumeControl.Volume_Set));
            volumeBar.setEnabled(device.hasCapability(VolumeControl.Volume_Set));
        }

        initPlaylistControls();
        updateProgressBar();
    }

    private void initPlaylistControls() {
        if(mPlaybackManager == null) {
            Log.e(TAG, "initPlaylistControls: missing playback manager");
            return;
        }

        Playlist playlist = getCurrentPlaylist();
        if (playlist != null) {
            PlaylistItem currentPlaylistItem = playlist.getCurrentItem();
            if (currentPlaylistItem != null) {
                txtTitle.setText(currentPlaylistItem.getTitle());
                if (currentPlaylistItem.getContentType().equals(CONTENT_TYPE_LIVE)) {
                    setContentType(ContentType.LIVE);
                } else if (currentPlaylistItem.getMimeType().startsWith("audio/")) {
                    setContentType(ContentType.AUDIO);
                } else {
                    setContentType(ContentType.VIDEO);
                }
            } else {
                txtTitle.setText("");
                setContentType(ContentType.VIDEO);
            }

            if (playlist.getSize() > 1) {
                mBottomButtonsContainer.setVisibility(View.VISIBLE);
                btnPrev.setVisibility(View.VISIBLE);
                btnNext.setVisibility(View.VISIBLE);

                if (playlist.getCurrentIndex() > 0) {
                    btnPrev.setEnabled(true);
                } else {
                    btnPrev.setEnabled(false);
                }

                if (playlist.getCurrentIndex() < playlist.getSize() - 1) {
                    btnNext.setEnabled(true);
                } else {
                    btnNext.setEnabled(false);
                }
            } else {
                btnPrev.setVisibility(View.GONE);
                btnNext.setVisibility(View.GONE);
                mBottomButtonsContainer.setVisibility(View.GONE);
            }
        }
    }

    private void updateUI() {
        if(mPlaybackManager == null) {
            Log.e(TAG, "updateUI: missing playback manager");
            return;
        }

        AceStreamRemoteDevice remoteDevice = mPlaybackManager.getCurrentRemoteDevice();

        if(RateManager.shouldRate()) {
            bottomContainer.setVisibility(View.VISIBLE);
            txtRateText.setVisibility(View.VISIBLE);
            btnRateNo.setVisibility(View.VISIBLE);
            btnRateYes.setVisibility(View.VISIBLE);
        }
        else {
            txtRateText.setVisibility(View.GONE);
            btnRateNo.setVisibility(View.GONE);
            btnRateYes.setVisibility(View.GONE);
            bottomContainer.setVisibility(View.GONE);
        }

        if(mEngineSessionStarted && remoteDevice != null && remoteDevice.isOurPlayer()) {
            btnSelectAudioTrack.setVisibility(View.VISIBLE);
            btnSelectSubtitleTrack.setVisibility(View.VISIBLE);
            btnSwitchVideoSize.setVisibility(View.VISIBLE);
        }
        else {
            btnSelectAudioTrack.setVisibility(View.GONE);
            btnSelectSubtitleTrack.setVisibility(View.GONE);
            btnSwitchVideoSize.setVisibility(View.GONE);
        }

        if(!mEngineSessionStarted) {
            mDebugInfoContainer.setVisibility(View.GONE);
            mDebugInfoText.setText("");
        }

        if(AceStream.isAndroidTv()) {
            volumeBarContainer.setVisibility(View.GONE);
        }
        else {
            if(mEngineSessionStarted && remoteDevice != null && !remoteDevice.isOurPlayer()) {
                volumeBarContainer.setVisibility(View.GONE);
            }
            else {
                volumeBarContainer.setVisibility(View.VISIBLE);
            }
        }
    }

    private void updatePlayButton() {
        if(mPlaybackManager == null) {
            Log.e(TAG, "updatePlayButton: missing playback manager");
            return;
        }

        boolean deviceConnected = mDeviceConnected && mPlaybackManager.isDeviceConnected();
        AceStreamRemoteDevice remoteDevice = mPlaybackManager.getCurrentRemoteDevice();

        if(mPrebuffering) {
            btnPlay.setVisibility(View.GONE);
            setPlayState(PlayState.PAUSED);
        }
        else if(deviceConnected) {
            if(contentImageOverlay.getVisibility() == View.GONE) {
                btnPlay.setVisibility(View.VISIBLE);
            }
        }
        else if(remoteDevice != null && !remoteDevice.isOurPlayer()) {
            // remote external player: hide "play" and show "stop" button
            btnPlay.setVisibility(View.GONE);
            setPlayState(PlayState.PAUSED);
        }
        else if(mPlaybackManager.isEngineSessionStarted()) {
            btnPlay.setVisibility(View.GONE);
            setPlayState(PlayState.PAUSED);
        }
        else {
            btnPlay.setVisibility(View.GONE);
            setPlayState(PlayState.IDLE);
        }
    }

    private void updateProgressBar() {
        if(mPlaybackManager == null) {
            Log.e(TAG, "updateProgressBar: missing playback manager");
            return;
        }

        boolean showProgressBar = false;
        boolean showLive = false;

        EngineSession session = mPlaybackManager.getEngineSession();
        PlaylistItem item = getCurrentPlaylistItem();
        ConnectableDevice device = mPlaybackManager.getCurrentDevice();
        AceStreamRemoteDevice remoteDevice = mPlaybackManager.getCurrentRemoteDevice();

        if(mEngineSessionStarted && remoteDevice != null && !remoteDevice.isOurPlayer()) {
            // do not show progress bar for external remote players
        }
        else if(item != null && !AceStream.isAndroidTv()) {
            ContentType type = getContentTypeFromPlaylistItem(item);

            if(mPlaybackManager.getDirectMediaInfo() != null) {
                showProgressBar = true;
            }
            else if(type == ContentType.LIVE) {
                // show live progress bar for P2P broadcasts with HTTP output
                if(remoteDevice != null) {
                    if(TextUtils.equals("http", remoteDevice.getOutputFormat())) {
                        showProgressBar = true;
                        showLive = true;
                    }
                    else {
                        showProgressBar = false;
                        showLive = false;
                    }
                }
                else if(session == null) {
                    showProgressBar = false;
                }
                else if(session.playbackData.outputFormat.format.equals("http") && !item.getMimeType().equals(MIME_HLS)) {
                    showProgressBar = true;
                    showLive = true;
                }
                else {
                    showProgressBar = false;
                }
            }
            else {
                if(remoteDevice != null) {
                    showProgressBar = true;
                }
                else if(session != null && device != null) {
                    showProgressBar = true;
                }
                else {
                    showProgressBar = false;
                }
            }
        }

        if(showProgressBar) {
            int progressBarWidth;
            int progressInfoWidth;
            int progressInfoMarginRight = 0;
            int progressInfoMarginLeft = 0;
            boolean showLiveContainer = false;
            ViewGroup.LayoutParams lp;

            // show progress bar
            progressBar.setVisibility(View.VISIBLE);
            progressInfo.setVisibility(View.VISIBLE);

            showLive = (showLive && mShowLiveContainer);

            if(showLive && mShowStreamSelectorContainer) {
                progressBarWidth = 157;
                showLiveContainer = true;
                progressInfoWidth = 135;
                progressInfoMarginRight = 10;

                // enable on live only
                // for VOD enable/disable is handled based on device status
                progressBar.setEnabled(true);
            }
            else if(showLive) {
                progressBarWidth = 210;
                showLiveContainer = true;
                progressInfoWidth = 190;
                progressInfoMarginRight = 25;

                // enable on live only
                // for VOD enable/disable is handled based on device status
                progressBar.setEnabled(true);
            }
            else if(showLive) {
                progressBarWidth = 210;
                progressInfoWidth = 190;
                progressInfoMarginLeft = 25;
            }
            else {
                progressBarWidth = 263;
                progressInfoWidth = 235;
            }

            mLiveContainer.setVisibility(showLiveContainer ? View.VISIBLE : View.GONE);
            mStreamSelectorContainer.setVisibility(mShowStreamSelectorContainer ? View.VISIBLE : View.GONE);

            lp = progressBar.getLayoutParams();
            lp.width = dpToPixels(progressBarWidth);
            progressBar.setLayoutParams(lp);

            LinearLayout.LayoutParams lp2 = (LinearLayout.LayoutParams)progressInfo.getLayoutParams();
            lp2.width = dpToPixels(progressInfoWidth);
            if(progressInfoMarginRight != -1) {
                lp2.rightMargin = dpToPixels(progressInfoMarginRight);
            }
            if(progressInfoMarginLeft != -1) {
                lp2.leftMargin = dpToPixels(progressInfoMarginLeft);
            }
            progressInfo.setLayoutParams(lp2);
        }
        else {
            // hide progress bar
            progressBar.setVisibility(View.GONE);
            progressInfo.setVisibility(View.GONE);
            mLiveContainer.setVisibility(View.GONE);
            mStreamSelectorContainer.setVisibility(View.GONE);
        }
    }

    private int dpToPixels(int dp) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        return (int)(dp * metrics.density);
    }

    private void setCurrentStatus(MediaControl.PlayStateStatus status) {
        if(status == null) {
            status = MediaControl.PlayStateStatus.Unknown;
        }

        switch (status) {
            case Playing:
            case Buffering:
                setPlayState(PlayState.PLAYING);

                mDeviceConnected = true;
                progressBar.setEnabled(true);
                break;
            case Paused:
                setPlayState(PlayState.PAUSED);

                mDeviceConnected = true;
                progressBar.setEnabled(true);
                break;
            case Idle:
            case Unknown:
            case Finished:
                setPlayState(PlayState.PAUSED);

                mDeviceConnected = false;
                progressBar.setEnabled(false);

                break;
        }

        updatePlayButton();

        if(status == MediaControl.PlayStateStatus.Buffering) {
            showMessage("playerStatus", 10, "_buffering_", (float)1.0);
        }
        else {
            hideMessage("playerStatus");
        }
    }

    private void setCurrentDuration(Long duration) {
        ContentType type = getCurrentContentType();
        if(type == ContentType.LIVE) {
            return;
        }

        int int_duration = 0;
        if(duration > 1000) {
            int_duration = (int) (duration / 1000);
        }

        if(int_duration != mDuration) {
            Log.d(TAG, "setCurrentDuration: duration=" + duration);
            mDuration = int_duration;
            txtDuration.setText(MiscUtils.durationStringFromMilliseconds(duration));
            progressBar.setMax(int_duration);
        }
    }

    private void setCurrentPosition(Long position) {
        ContentType type = getCurrentContentType();
        if(type == ContentType.LIVE) {
            return;
        }

        if(!mDraggingProgress) {
            int value = (int) (position / 1000);
            if(value != progressBar.getProgress()) {
                txtCurrentTime.setText(MiscUtils.durationStringFromMilliseconds(position));
                progressBar.setProgress(value);
            }
        }
    }

    private void setCurrentVolume(float volume) {
        if(!mDraggingVolume) {
            int value = (int) (volume * volumeBar.getMax());
            if(value != volumeBar.getProgress()) {
                Log.d(TAG, "set volume: volume=" + volume);
                volumeBar.setProgress(value);
            }
        }
    }

    SeekBar.OnSeekBarChangeListener onProgressBarChanged = new SeekBar.OnSeekBarChangeListener() {
        int seekValue = -1;

        // called when the scrubber changes value either by touch or by code manipulation
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            seekValue = progress;

            // fromUser means we're responding to user touch specifically
            if (fromUser && progressBar.getMax() > 0) {
                ContentType type = getCurrentContentType();
                String timeString = "00:00";

                if(type != null && type == ContentType.LIVE) {
                    // live
                    Object tag = seekBar.getTag();
                    if(tag != null && tag instanceof EngineStatus.LivePosition) {
                        EngineStatus.LivePosition livePos = (EngineStatus.LivePosition)tag;
                        int duration = livePos.lastTimestamp - livePos.firstTimestamp;
                        int pieces = livePos.last - livePos.first;

                        if(duration > 0 && pieces > 0) {
                            float secondsPerPiece = duration / pieces;
                            timeString = "-" + MiscUtils.durationStringFromMilliseconds(Math.round((progressBar.getMax() - seekValue) * secondsPerPiece * 1000));
                        }
                    }
                }
                else {
                    // vod
                    timeString = MiscUtils.durationStringFromMilliseconds(seekValue * 1000);
                    txtCurrentTime.setText(timeString);
                }

                showMessage("progress", 100, timeString, (float)2.0);
            }
        }

        // called when the user starts touching the scrubber
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            mDraggingProgress = true;
        }

        // called when the user lifts their finger from the scrubber
        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            try {
                hideMessage("progress");
                if(progressBar.getMax() > 0) {
                    ContentType type = getCurrentContentType();
                    int seekTo;

                    if(type != null && type == ContentType.LIVE) {
                        // live
                        Object tag = seekBar.getTag();
                        if(tag != null && tag instanceof EngineStatus.LivePosition) {
                            EngineStatus.LivePosition livePos = (EngineStatus.LivePosition)tag;
                            seekTo = livePos.first + seekValue;
                            Log.d(TAG, "progress:live: seek to: " + seekTo + " (value=" + seekValue + " first=" + livePos.first + ")");

                            if(mPlaybackManager != null) {
                                mPlaybackManager.liveSeek(seekTo);
                            }

                            mLiveStatus.setBackgroundResource(R.drawable.circle_yellow);

                            // to freeze live pos for some time
                            freezeLiveStatusAt = new Date().getTime();
                            freezeLivePosAt = new Date().getTime();
                        }
                    }
                    else {
                        // vod
                        seekTo = seekValue;
                        Log.d(TAG, "progress:vod: seek to: " + seekTo);

                        if(mPlaybackManager != null) {
                            AceStreamRemoteDevice remoteDevice = mPlaybackManager.getCurrentRemoteDevice();
                            MediaControl control = mPlaybackManager.getMediaControl();
                            if (remoteDevice != null) {
                                remoteDevice.setTime(seekValue * 1000);
                            } else if (control != null) {
                                control.seek(seekValue * 1000, null);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "progress seek error", e);
            }
            mDraggingProgress = false;
        }
    };

    SeekBar.OnSeekBarChangeListener onVolumeBarChanged = new SeekBar.OnSeekBarChangeListener() {
        int seekValue = -1;

        // called when the scrubber changes value either by touch or by code manipulation
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            seekValue = progress;
            if(fromUser) {
                showMessage("volume", 100, progress + "%", (float)2.0);
            }
        }

        // called when the user starts touching the scrubber
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            mDraggingVolume = true;
        }

        // called when the user lifts their finger from the scrubber
        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            try {
                hideMessage("volume");
                float val = ((float) seekValue) / volumeBar.getMax();
                Log.d(TAG, "volume: change: seek=" + seekValue + " val=" + val);

                if(mPlaybackManager != null) {
                    AceStreamRemoteDevice remoteDevice = mPlaybackManager.getCurrentRemoteDevice();
                    VolumeControl volumeControl = mPlaybackManager.getVolumeControl();
                    if (remoteDevice != null) {
                        remoteDevice.setVolume(val);
                    } else if (volumeControl != null) {
                        volumeControl.setVolume(val, new ResponseListener<Object>() {
                            @Override
                            public void onSuccess(Object object) {
                                Log.d(TAG, "set volume success");
                            }

                            @Override
                            public void onError(ServiceCommandError error) {
                                Log.e(TAG, "set volume failed", error);
                            }
                        });
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "set volume error", e);
            }
            mDraggingVolume = false;
        }
    };

    private void setContentType(ContentType type) {
        if(mCurrentContentType == type) {
            return;
        }

        if(type == ContentType.VIDEO) {
            Playlist playlist = getCurrentPlaylist();
            if (playlist != null) {
                PlaylistItem currentPlaylistItem = playlist.getCurrentItem();
                if (currentPlaylistItem != null && currentPlaylistItem.getMimeType().startsWith("audio/")) {
                    setContentType(ContentType.AUDIO);
                }
            }
        }

        mCurrentContentType = type;
        contentImage.setTag(type);

        setContentTypeImage(type);

        if(type == ContentType.LIVE) {
            mLiveContainer.setVisibility(View.VISIBLE);
        }
        else {
            mLiveContainer.setVisibility(View.GONE);
        }
    }

    private void setContentTypeImage(ContentType type) {
        Resources res = getResources();
        switch (type) {
            case VIDEO:
                contentImage.setImageDrawable(res.getDrawable(R.drawable.rc_image_video));
                break;
            case AUDIO:
                contentImage.setImageDrawable(res.getDrawable(R.drawable.rc_image_audio));
                break;
            case LIVE:
                contentImage.setImageDrawable(res.getDrawable(R.drawable.rc_image_live));
                break;
        }
    }

    private void startMainActivity() {
        Intent intent = new Intent(this, AceStreamEngineBaseApplication.getMainActivityClass());
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        intent.putExtra("skip_redirect", true);
        startActivity(intent);
    }

    private void setPrebuffering(boolean prebufering) {
        setPrebuffering(prebufering, false);
    }

    private void setPrebuffering(boolean prebufering, boolean restarting) {
        mPrebuffering = prebufering;
        mRestarting = restarting;
        updatePlayButton();
    }

    private void initPlaylist(@NonNull final Runnable runnable) {
        if(mPlaybackManager == null) {
            throw new IllegalStateException("missing PM");
        }

        if(!AceStreamEngineBaseApplication.useVlcBridge()) {
            runnable.run();
            return;
        }

        final Uri currentMediaUri = getIntent().getParcelableExtra(AceStream.EXTRA_CURRENT_MEDIA_URI);
        App.v(TAG, "initPlaylist: from media: currentMediaUri=" + currentMediaUri);

        if(currentMediaUri == null) {
            Logger.v(TAG, "initPlaylist: no current media");
            mPlaybackManager.setCurrentPlaylist(null);
            runnable.run();
            return;
        }

        if(!TextUtils.equals(currentMediaUri.getScheme(), "acestream")) {
            Logger.v(TAG, "initPlaylist: not a p2p item");
            mPlaybackManager.setCurrentPlaylist(null);
            runnable.run();
            return;
        }

        final TransportFileDescriptor descriptor;
        try {
            descriptor = TransportFileDescriptor.fromMrl(getContentResolver(), currentMediaUri);
        }
        catch (TransportFileParsingException e) {
            Log.e(TAG, "initPlaylist: failed to get descriptor: " + e.getMessage());
            mPlaybackManager.setCurrentPlaylist(null);
            runnable.run();
            return;
        }

        Playlist currentPlaylist = getCurrentPlaylist();
        if(currentPlaylist != null && currentPlaylist.getContentDescriptor().equals(descriptor)) {
            App.v(TAG, "initPlaylist: descriptor has not changed");
            runnable.run();
            return;
        }

        mPlaybackManager.getEngine(new PlaybackManager.EngineStateCallback() {
            @Override
            public void onEngineConnected(@NonNull IAceStreamManager playbackManager, @NonNull EngineApi engineApi) {
                engineApi.getMediaFiles(descriptor, new org.acestream.engine.controller.Callback<MediaFilesResponse>() {
                    @Override
                    public void onSuccess(MediaFilesResponse result) {
                        if(mPlaybackManager == null) return;
                        descriptor.setTransportFileData(result.transport_file_data);
                        mPlaybackManager.initPlaylist(descriptor, result, MiscUtils.getFileIndex(currentMediaUri));
                        if(runnable != null) {
                            runnable.run();
                        }
                    }

                    @Override
                    public void onError(String err) {
                        if(mPlaybackManager == null) return;
                        Log.e(TAG, "initPlaylist: failed: " + err);
                        runnable.run();
                    }
                });
            }
        });

    }

    private Playlist getCurrentPlaylist() {
        if(mPlaybackManager == null) {
            return null;
        }

        return mPlaybackManager.getCurrentPlaylist();
    }

    private PlaylistItem getCurrentPlaylistItem() {
        Playlist playlist = getCurrentPlaylist();
        if(playlist == null) {
            return null;
        }
        return playlist.getCurrentItem();
    }

    private ContentType getCurrentContentType() {
        PlaylistItem currentPlaylistItem = getCurrentPlaylistItem();
        if (currentPlaylistItem == null) {
            return null;
        }

        return getContentTypeFromPlaylistItem(currentPlaylistItem);
    }

    private ContentType getContentTypeFromPlaylistItem(PlaylistItem item) {
        if(item == null) {
            return null;
        }

        if (item.getContentType().equals(CONTENT_TYPE_LIVE)) {
            return ContentType.LIVE;
        }
        else if (item.getMimeType().startsWith("audio/")) {
            return ContentType.AUDIO;
        }
        else {
            return ContentType.VIDEO;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.d(TAG, "onCreateOptionsMenu");
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.remote_control, menu);
        super.onCreateOptionsMenu(menu);
        mMenu = menu;

        // update menu items visibility
        showDeviceSelector(mDeviceSelectorVisible);
        showPlayerSettings(mPlayerSettingsVisible);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int i = item.getItemId();
        if (i == R.id.action_select_device) {
            showResolver();
            //if(mPlaybackManager != null) {
            //    mPlaybackManager.selectDevice(this, true, false);
            //}

        } else if (i == R.id.action_show_player_settings) {
            Intent intent = new Intent(this, PlayerSettingsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(intent);

        } else {
            return super.onOptionsItemSelected(item);
        }

        return true;
    }

    private void showDeviceSelector(boolean visible) {
        mDeviceSelectorVisible = visible;
        if(mMenu != null) {
            MenuItem item = mMenu.findItem(R.id.action_select_device);
            if (item != null) {
                item.setVisible(visible);
            }
        }
    }

    private void showPlayerSettings(boolean visible) {
        // disable deinterlace selection on the fly for now
        visible = false;
        mPlayerSettingsVisible = visible;
        if(mMenu != null) {
            MenuItem item = mMenu.findItem(R.id.action_show_player_settings);
            if (item != null) {
                item.setVisible(visible);
            }
        }
    }

    private void setCurrentRemoteDevice(AceStreamRemoteDevice device) {
        if(BuildConfig.DEBUG) {
            Log.v(TAG, "setCurrentRemoteDevice: device=" + device);
        }
        if(mCurrentRemoteDevice != null) {
            // remove listener from prev device
            mCurrentRemoteDevice.removeListener(this);
            mCurrentRemoteDevice = null;
        }

        mCurrentRemoteDevice = device;

        if(mCurrentRemoteDevice != null) {
            mCurrentRemoteDevice.addListener(this);
        }
    }

    private void showStreamSelectorContainer(boolean visible) {
        showStreamSelectorContainer(visible, true);
    }

    private void showStreamSelectorContainer(boolean visible, boolean updateProgressBar) {
        if(mShowStreamSelectorContainer == visible) {
            return;
        }

        mShowStreamSelectorContainer = visible;
        if(updateProgressBar) {
            updateProgressBar();
        }
        else {
            mStreamSelectorContainer.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void showLiveContainer(boolean visible) {
        showLiveContainer(visible, true);
    }

    private void showLiveContainer(boolean visible, boolean updateProgressBar) {
        if(mShowLiveContainer == visible) {
            return;
        }

        mShowLiveContainer = visible;
        if(updateProgressBar) {
            updateProgressBar();
        }
    }

    private void updateCurrentRemoteDevice() {
        if(mPlaybackManager != null) {
            setCurrentRemoteDevice(mPlaybackManager.getCurrentRemoteDevice());
        }
    }

    @Override
    public void onResumeConnected() {
        super.onResumeConnected();

        updateCurrentRemoteDevice();

        if(mEngineSessionStarted
                && mCurrentRemoteDevice == null
                && !mPlaybackManager.isEngineSessionStarted()) {
            Log.d(TAG, "engine session is stopped on resume");
            onEngineSessionStopped();
        }

        mPlaybackManager.addPlaybackStatusListener(this);
        mPlaybackManager.addEngineStatusListener(this);
        mPlaybackManager.addEngineSessionListener(this);
        mPlaybackManager.addDeviceStatusListener(this);
        mPlaybackManager.addDeviceDiscoveryListener(this);
        mPlaybackManager.addPlaybackStateCallback(mPlaybackStateCallback);

        resetControls();
        initControls();
        mPlaybackManager.discoverDevices(false);
        updateUI();
        updatePlayButton();
    }

    @Override
    public void onConnected(PlaybackManager service) {
        super.onConnected(service);
        initPlaylist(new Runnable() {
            @Override
            public void run() {
                App.v(TAG, "reinit controls after playlist update");
                resetControls();
                initControls();
            }
        });
    }

    public static String getVideoSizeName(int id) {
        String name = null;
        switch(id) {
            case VlcConstants.SURFACE_BEST_FIT:
                name = "best_fit";
                break;
            case VlcConstants.SURFACE_FIT_SCREEN:
                name = "fit_screen";
                break;
            case VlcConstants.SURFACE_FILL:
                name = "fill";
                break;
            case VlcConstants.SURFACE_16_9:
                name = "16:9";
                break;
            case VlcConstants.SURFACE_4_3:
                name = "4:3";
                break;
            case VlcConstants.SURFACE_ORIGINAL:
                name = "original";
                break;
        }

        return name;
    }

    public static String getVideoSizeTitle(int id) {
        String title = null;
        switch(id) {
            case VlcConstants.SURFACE_BEST_FIT:
                title = "Best fit";
                break;
            case VlcConstants.SURFACE_FIT_SCREEN:
                title = "Fit screen";
                break;
            case VlcConstants.SURFACE_FILL:
                title = "Fill";
                break;
            case VlcConstants.SURFACE_16_9:
                title = "16:9";
                break;
            case VlcConstants.SURFACE_4_3:
                title = "4:3";
                break;
            case VlcConstants.SURFACE_ORIGINAL:
                title = "Original";
                break;
        }

        return title;
    }
}
