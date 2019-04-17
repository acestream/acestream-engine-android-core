package org.acestream.engine.acecast.server;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Messenger;
import androidx.annotation.NonNull;
import android.util.Log;

import org.acestream.engine.AceStreamEngineBaseApplication;
import org.acestream.engine.PlaybackManager;
import org.acestream.engine.acecast.client.AceStreamRemoteDevice;
import org.acestream.engine.acecast.interfaces.AceStreamRemoteClientListener;
import org.acestream.engine.controller.ExtendedEngineApi;
import org.acestream.sdk.AceStream;
import org.acestream.sdk.EngineSession;
import org.acestream.sdk.EngineSessionStartListener;
import org.acestream.sdk.JsonRpcMessage;
import org.acestream.sdk.PlaybackData;
import org.acestream.sdk.SelectedPlayer;
import org.acestream.engine.controller.Callback;
import org.acestream.sdk.controller.EngineApi;
import org.acestream.sdk.controller.api.response.MediaFilesResponse;
import org.acestream.sdk.errors.TransportFileParsingException;
import org.acestream.sdk.interfaces.IAceStreamManager;
import org.acestream.sdk.player.api.AceStreamPlayer;
import org.acestream.sdk.utils.VlcBridge;
import org.acestream.sdk.utils.Workers;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class AceStreamDiscoveryServerClient
        implements
        PlaybackManager.Client.Callback,
        PlaybackManager.Callback
{
    private final static String TAG = "AceStream/DSC";
    
    private AceStreamDiscoveryServer mServer;
    private Socket mSocket;
    private String mIpAddress;
    private int mPort;
    private final Set<AceStreamRemoteClientListener> mListeners = new CopyOnWriteArraySet<>();
    private final Set<Messenger> mMessengerListeners = new CopyOnWriteArraySet<>();
    private Thread mCommunicationThread;
    private Context mContext;
    private Handler mHandler;
    private HandlerThread mHandlerThread;
    private JsonRpcMessage mLastMessage = null;
    private int mRemoteVersion = 0;
    private final String mThisDeviceId;
    private String mRemoteDeviceId = null;
    private PlaybackManager mPlaybackManager = null;
    private PlaybackManager.Client mPlaybackManagerClient;
    private boolean mPlaybackManagerClientWasConnected = false;

    public AceStreamDiscoveryServerClient(Context context, AceStreamDiscoveryServer server, Socket socket) {
        mThisDeviceId = AceStream.getDeviceUuidString();
        mContext = context;
        mServer = server;
        mSocket = socket;
        mIpAddress = socket.getInetAddress().getHostAddress();
        mPort = socket.getPort();

        mHandlerThread = new HandlerThread(getClass().getSimpleName());
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());

        mCommunicationThread = new Thread(new CommunicationThread());
        mCommunicationThread.start();

        mPlaybackManagerClient = new PlaybackManager.Client(context, this);
    }

    public String getId() {
        return mIpAddress + ":" + mPort;
    }

    public String getDeviceId() {
        return mRemoteDeviceId;
    }

    public String toString() {
        return getId();
    }

    public void addListener(AceStreamRemoteClientListener listener) {
        Log.v(TAG, "addListener: this=" + this.hashCode() + " listener=" + listener.hashCode());
        mListeners.add(listener);
    }

    public void addListener(Messenger listener) {
        Log.v(TAG, "addListener: this=" + this.hashCode() + " listener=" + listener.hashCode());
        mMessengerListeners.add(listener);
    }

    public void removeListener(AceStreamRemoteClientListener listener) {
        Log.v(TAG, "removeListener: this=" + this.hashCode() + " listener=" + listener.hashCode());
        mListeners.remove(listener);
    }

    public void removeListener(Messenger listener) {
        Log.v(TAG, "removeListener: this=" + this.hashCode() + " listener=" + listener.hashCode());
        mMessengerListeners.remove(listener);
    }

    private void notifyMessage(JsonRpcMessage message) {
        Log.v(TAG, "notifyMessage: this=" + this.hashCode() + " method=" + message.getMethod() + " listeners=" + mListeners.size() + " listeners:messenger=" + mMessengerListeners.size());
        try {
            for (AceStreamRemoteClientListener listener : mListeners) {
                listener.onMessage(this, message);
            }

            for (Messenger listener : mMessengerListeners) {
                final Message msg = Message.obtain(null, AceStreamDiscoveryServerService.MSG_CLIENT_ON_MESSAGE);
                Bundle data = new Bundle(2);
                data.putString(AceStreamDiscoveryServerService.PARAM_CLIENT_ID, getId());
                data.putString(AceStreamDiscoveryServerService.PARAM_CLIENT_MESSAGE, message.toString());
                msg.setData(data);
                listener.send(msg);
            }
        }
        catch(Throwable e) {
            Log.e(TAG, "notifyMessage: error", e);
        }
    }

    private void notifyDisconnected() {
        try {
            for (AceStreamRemoteClientListener listener : mListeners) {
                listener.onDisconnected(this);
            }

            for (Messenger listener : mMessengerListeners) {
                final Message msg = Message.obtain(null, AceStreamDiscoveryServerService.MSG_CLIENT_ON_DISCONNECTED);
                Bundle data = new Bundle(2);
                data.putString(AceStreamDiscoveryServerService.PARAM_CLIENT_ID, getId());
                data.putString(AceStreamDiscoveryServerService.PARAM_DEVICE_ID, getDeviceId());
                msg.setData(data);
                listener.send(msg);
            }
        }
        catch(Throwable e) {
            Log.e(TAG, "notifyDisconnected: error", e);
        }
    }

    public void playerClosed(boolean shutdown) {
        sendMessage(new JsonRpcMessage(AceStreamRemoteDevice.Messages.PLAYER_CLOSED));
        if(shutdown) {
            sendMessage(new JsonRpcMessage("quit"));
        }
    }

    public void sendMessage(final JsonRpcMessage msg) {
        if(mHandler == null) {
            Log.v(TAG, "sendMessage: disconnected");
            return;
        }

        // hide some methods
        switch(msg.getMethod()) {
            case AceStreamRemoteDevice.Messages.PLAYER_STATUS:
            case AceStreamRemoteDevice.Messages.ENGINE_STATUS:
                Log.v(TAG, "sendMessage: client=" + this.toString() + " msg=" + msg.toString());
                break;
            default:
                Log.d(TAG, "sendMessage: client=" + this.toString() + " msg=" + msg.toString());
        }

        try {
            if (mHandler != null) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        sendMessageRaw(msg);
                    }
                });
            }
        }
        catch(Throwable e) {
            Log.e(TAG, "sendMessage: error", e);
        }
    }

    private void sendMessageRaw(JsonRpcMessage msg) {
        try {
            String rawMessage = msg.asString();
            PrintWriter out = new PrintWriter(new BufferedWriter(
                    new OutputStreamWriter(mSocket.getOutputStream())),
                    true);
            out.println(rawMessage);
            out.flush();
        }
        catch (Throwable e) {
            Log.e(TAG, "failed to send message", e);
        }
    }

    private void startPlayback(JsonRpcMessage msg) {

        if(mPlaybackManager == null) {
            Log.d(TAG, "startPlayback: no playback manager, save last message");
            mLastMessage = msg;
            Workers.runOnMainThread(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "startPlayback: check playback manager in main thread: bound=" + mPlaybackManagerClient.isConnected());
                    if(!mPlaybackManagerClient.isConnected()) {
                        mPlaybackManagerClient.connect();
                    }
                }
            });
            return;
        }

        final PlaybackData playbackData;
        try {
            playbackData = PlaybackData.fromJsonRpcMessage(msg);
        }
        catch(TransportFileParsingException e) {
            Log.e(TAG, "startPlayback: failed to parse transport file", e);
            return;
        }

        Log.d(TAG, "startPlayback:" +
                " descriptor=" + playbackData.descriptor.toString() +
                " type=" + playbackData.mediaFile.type +
                " index=" + playbackData.mediaFile.index +
                " streamIndex=" + playbackData.streamIndex +
                " mime=" + playbackData.mediaFile.mime +
                " seekOnStart=" + playbackData.seekOnStart +
                " selectedPlayer=" + playbackData.selectedPlayer +
                " directMediaUrl=" + playbackData.directMediaUrl +
                " videoSize=" + playbackData.mediaFile.size +
                " datalen=" + (playbackData.descriptor.getTransportFileData() == null ? 0 : playbackData.descriptor.getTransportFileData().length())
        );

        if(playbackData.selectedPlayer == null) {
            playbackData.selectedPlayer = AceStreamEngineBaseApplication.getSelectedPlayer();
            Log.d(TAG, "startPlayback: use selected player from prefs: " + playbackData.selectedPlayer);
        }

        startPlaybackInternal(playbackData);
    }

    private void startPlaybackInternal(final PlaybackData playbackData) {
        SharedPreferences prefs = AceStreamEngineBaseApplication.getPreferences();
        String packageName = null;
        boolean disableP2P = prefs.getBoolean("disable_p2p", false);

        if(mPlaybackManager == null) {
            Log.e(TAG, "startPlaybackInternal: missing playback manager");
            return;
        }

        SelectedPlayer selectedPlayer;
        if(playbackData.selectedPlayer == null) {
            // Use our player as default
            selectedPlayer = SelectedPlayer.getOurPlayer();
        }
        else {
            selectedPlayer = playbackData.selectedPlayer;
            if(selectedPlayer.type == SelectedPlayer.LOCAL_PLAYER) {
                packageName = selectedPlayer.id1;
            }
        }

        AceStream.setLastSelectedPlayer(selectedPlayer);

        if(selectedPlayer.isOurPlayer()) {
            mPlaybackManager.setRemoteSelectedPlayer(selectedPlayer);
            startOurPlayer(playbackData);
        }
        else {
            playbackData.outputFormat = mPlaybackManager.getOutputFormatForContent(
                    playbackData.mediaFile.type,
                    playbackData.mediaFile.mime,
                    packageName,
                    false,
                    false
            );;
            playbackData.disableP2P = disableP2P;
            playbackData.useFixedSid = true;
            playbackData.stopPrevReadThread = 0;

            mPlaybackManager.initEngineSession(
                    playbackData,
                    new EngineSessionStartListener() {
                        @Override
                        public void onSuccess(EngineSession session) {
                            Log.d(TAG, "engine session started");
                            mPlaybackManager.setCurrentRemoteClient(getId(), getDeviceId());
                        }

                        @Override
                        public void onError(String error) {
                            Log.d(TAG, "engine session failed: error=" + error);
                            mPlaybackManager.stopEngineSession(true);
                            JsonRpcMessage _msg = new JsonRpcMessage(AceStreamRemoteDevice.Messages.PLAYBACK_START_FAILED);
                            _msg.addParam("error", error);
                            sendMessage(_msg);
                        }
                    }
            );
        }
    }

    private void startOurPlayer(final PlaybackData playbackData) {
        AceStream.putTransportFileToCache(
                playbackData.descriptor.getDescriptorString(),
                playbackData.descriptor.getTransportFileData());

        if(mPlaybackManager != null) {
            mPlaybackManager.setLastSelectedDeviceId(null);
        }

        //TODO: refactor this: don't use PM to get engine api
        if(mPlaybackManager == null) {
            throw new IllegalStateException("missing pm");
        }
        mPlaybackManager.getEngine(new PlaybackManager.EngineStateCallback() {
            @Override
            public void onEngineConnected(@NonNull IAceStreamManager playbackManager, @NonNull EngineApi engineApi) {
                engineApi.getMediaFiles(playbackData.descriptor, new Callback<MediaFilesResponse>() {
                    @Override
                    public void onSuccess(MediaFilesResponse result) {
                        if(AceStreamEngineBaseApplication.useVlcBridge()) {
                            int playlistPosition = 0;
                            for (int i = 0; i < result.files.length; i++) {
                                if (result.files[i].index == playbackData.mediaFile.index) {
                                    playlistPosition = i;
                                    break;
                                }
                            }

                            new VlcBridge.LoadP2PPlaylistIntentBuilder(playbackData.descriptor)
                                    .setMetadata(result)
                                    .setMediaFiles(result.files)
                                    .setPlaylistPosition(playlistPosition)
                                    .setRemoteClientId(getId())
                                    .setSeekOnStart(playbackData.seekOnStart)
                                    .send();
                        }
                        else {
                            AceStreamPlayer.PlaylistItem[] playlist = new AceStreamPlayer.PlaylistItem[result.files.length];
                            int playlistPosition = 0;
                            for(int i = 0; i < result.files.length; i++) {
                                playlist[i] = new AceStreamPlayer.PlaylistItem(
                                        playbackData.descriptor.getMrl(result.files[i].index).toString(),
                                        result.files[i].filename);
                                if (result.files[i].index == playbackData.mediaFile.index) {
                                    playlistPosition = i;
                                }
                            }

                            Intent intent = AceStreamPlayer.getPlayerIntent();
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            intent.putExtra(AceStreamPlayer.EXTRA_PLAYLIST, AceStreamPlayer.Playlist.toJson(playlist));
                            intent.putExtra(AceStreamPlayer.EXTRA_PLAYLIST_POSITION, playlistPosition);
                            intent.putExtra(AceStreamPlayer.EXTRA_REMOTE_CLIENT_ID, getId());
                            intent.putExtra(AceStreamPlayer.EXTRA_PLAY_FROM_TIME, playbackData.seekOnStart);
                            mContext.startActivity(intent);
                        }
                    }

                    @Override
                    public void onError(String err) {
                        Log.e(TAG, "Failed to get media files: " + err);
                    }
                });
            }
        });
    }

    private void processMessage(JsonRpcMessage msg) {
        notifyMessage(msg);

        try {
            switch (msg.getMethod()) {
                case "startPlayback":
                    startPlayback(msg);
                    break;
            }
        }
        catch(Throwable e) {
            Log.e(TAG, "failed to process message", e);
        }
    }

    private void shutdown() {
        Log.d(TAG, "shutdown: id=" + getId() + " wasPmConnected=" + mPlaybackManagerClientWasConnected);

        try {
            if(!mSocket.isClosed()) {
                mSocket.close();
            }
        }
        catch(Throwable e) {
            Log.e(TAG, "failed to close client socket", e);
        }

        notifyDisconnected();
        mServer.removeClient(AceStreamDiscoveryServerClient.this);

        // stop handler thread
        mHandlerThread.quit();
        mHandlerThread = null;
        mHandler = null;

        if(mPlaybackManagerClientWasConnected) {
            Workers.runOnMainThread(new Runnable() {
                @Override
                public void run() {
                    mPlaybackManagerClient.disconnect();
                }
            });
        };
    }

    @Override
    public void onEngineConnected(ExtendedEngineApi service) {
        Log.d(TAG, "onEngineConnected");
        if(mLastMessage != null) {
            Log.d(TAG, "got last message on engine start: method=" + mLastMessage.getMethod());
            JsonRpcMessage msgCopy = mLastMessage;
            mLastMessage = null;
            processMessage(msgCopy);
        }
        else {
            Log.d(TAG, "no last message on engine start");
        }
    }

    @Override
    public void onEngineFailed() {
        Log.d(TAG, "onEngineFailed");
        sendMessage(new JsonRpcMessage("engineStartFailed"));
    }

    @Override
    public void onEngineUnpacking() {
        Log.d(TAG, "onEngineUnpacking");
    }

    @Override
    public void onEngineStarting() {
        Log.d(TAG, "onEngineStarting");
    }

    @Override
    public void onEngineStopped() {
        Log.d(TAG, "onEngineStopped");
        sendMessage(new JsonRpcMessage("engineStoped"));
    }

    @Override
    public void onConnected(PlaybackManager service) {
        Log.d(TAG, "playback manager connected");
        mPlaybackManagerClientWasConnected = true;
        mPlaybackManager = service;
        mPlaybackManager.addCallback(this);
        mPlaybackManager.startEngine();
    }

    @Override
    public void onDisconnected() {
        Log.d(TAG, "playback manager disconnected");
        sendMessage(new JsonRpcMessage("engineStoped"));
        mPlaybackManager.removeCallback(this);
        mPlaybackManager = null;
    }

    class CommunicationThread implements Runnable {
        private BufferedReader mInput;

        public CommunicationThread() {
            Log.d(TAG, "new connection: ip=" + mSocket.getInetAddress().getHostAddress() + " port=" + mSocket.getPort() + " local_port=" + mSocket.getLocalPort());

            try {
                mInput = new BufferedReader(new InputStreamReader(mSocket.getInputStream()));
            }
            catch (IOException e) {
                Log.e(TAG, "failed to init client socket", e);
            }
        }

        public void run() {
            boolean clientRegistered = false;

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    String message = mInput.readLine();

                    if(message == null) {
                        Log.v(TAG, "got null message from client, stop: id=" + getId());
                        Thread.currentThread().interrupt();
                    }
                    else {
                        try {
                            Log.v(TAG, "got message: id=" + getId() + " len=" + message.length() + " msg=" + message);
                            final JsonRpcMessage msg = JsonRpcMessage.fromString(message);

                            if("quit".equals(msg.getMethod())) {
                                Thread.currentThread().interrupt();
                                sendMessageRaw(new JsonRpcMessage("quit"));
                            }
                            else if("ping".equals(msg.getMethod())) {
                                mRemoteVersion = msg.getInt("version", 0);
                                mRemoteDeviceId = msg.getString("deviceId", null);
                                JsonRpcMessage _msg = new JsonRpcMessage("pong");

                                // send my version and id
                                _msg.addParam("version", AceStream.getSdkVersionCode());
                                if(mThisDeviceId != null) {
                                    _msg.addParam("deviceId", mThisDeviceId);
                                }

                                // send list of available players
                                JSONArray jsonAvailablePlayers= new JSONArray();
                                List<SelectedPlayer> availablePlayers = AceStream.getAvailablePlayers();
                                for(SelectedPlayer player: availablePlayers) {
                                    JSONObject jsonAvailablePlayer = new JSONObject();
                                    jsonAvailablePlayer.put("id", player.getId());
                                    jsonAvailablePlayer.put("name", player.getName());
                                    jsonAvailablePlayers.put(jsonAvailablePlayer);
                                }

                                _msg.addParam("availablePlayers", jsonAvailablePlayers);

                                sendMessageRaw(_msg);
                            }
                            else if("hello".equals(msg.getMethod())) {
                                mRemoteVersion = msg.getInt("version", 0);
                                mRemoteDeviceId = msg.getString("deviceId", null);

                                // send hello response
                                JsonRpcMessage _msg = new JsonRpcMessage("hello");
                                // send my version and id
                                _msg.addParam("version", AceStream.getSdkVersionCode());
                                if(mThisDeviceId != null) {
                                    _msg.addParam("deviceId", mThisDeviceId);
                                }
                                sendMessageRaw(_msg);

                                if(!clientRegistered) {
                                    mServer.addClient(AceStreamDiscoveryServerClient.this);
                                    clientRegistered = true;
                                }
                            }
                            else {
                                // Register client in the server when we receive any message
                                // except "quit" and "ping".
                                if(!clientRegistered) {
                                    mServer.addClient(AceStreamDiscoveryServerClient.this);
                                    clientRegistered = true;
                                }

                                mHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        processMessage(msg);
                                    }
                                });
                            }
                        }
                        catch(JSONException e) {
                            Log.e(TAG, "failed to parse message: id=" + getId(), e);
                            if(message.contains("\"startPlayback\"")) {
                                sendMessageRaw(new JsonRpcMessage("lastStartPlaybackMessageFailed"));
                            }
                        }
                    }
                }
                catch (IOException e) {
                    Log.e(TAG, "io error in client thread: id=" + getId() + " err=" + e.getMessage());
                    break;
                }
                catch (Throwable e) {
                    Log.e(TAG, "unexpected error in client thread: id=" + getId(), e);
                    break;
                }
            }

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    shutdown();
                }
            });

            Log.v(TAG, "client thread stopped: id=" + getId());
        }
    }
}
