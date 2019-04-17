package org.acestream.engine.acecast.client;

import android.os.Handler;
import android.os.HandlerThread;
import androidx.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import org.acestream.engine.acecast.interfaces.AceStreamRemoteDeviceListener;
import org.acestream.sdk.AceStream;
import org.acestream.sdk.JsonRpcMessage;
import org.acestream.sdk.PlaybackData;
import org.acestream.sdk.SelectedPlayer;
import org.acestream.sdk.BaseRemoteDevice;
import org.acestream.sdk.errors.TransportFileParsingException;
import org.acestream.sdk.utils.Logger;
import org.acestream.sdk.utils.Workers;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.locks.ReentrantLock;

@SuppressWarnings({"UnusedReturnValue", "WeakerAccess", "unused"})
public class AceStreamRemoteDevice extends BaseRemoteDevice {

    private int mPort;
    private Socket mSocket;
    private boolean mConnected = false;
    private Queue<JsonRpcMessage> mMessageQueue;
    private Set<AceStreamRemoteDeviceListener> mListeners;
    private Handler mHandler;
    private HandlerThread mHandlerThread;
    private Handler mPingHandler;
    private HandlerThread mPingHandlerThread;
    private boolean mLastConnectStatus = false;
    private int mRemoteVersion = 0;

    private final ReentrantLock mConnectedLock = new ReentrantLock();

    private int mPingErrors = 0;
    private static final int PING_BASE_INTERVAL = 5000;
    private static final int PING_SUCCESS_INTERVAL = 60000;
    private static final int PING_MAX_INTERVAL = 20000;
    private static final int PING_MAX_ERRORS = 10;
    private static final int PING_CONNECTED_INTERVAL = 5000;
    private final String mThisDeviceId;
    private String mRemoteDeviceId = null;

    private JsonRpcMessage mLastStartPlaybackMessage = null;
    private int mStartPlaybackRetries = 3;

    private Runnable mPingTask = new Runnable() {
        @Override
        public void run() {
            boolean pingSuccess = false;
            try {
                // open socket
                InetAddress serverAddr = InetAddress.getByName(mIpAddress);
                Socket socket = new Socket(serverAddr, mPort);
                socket.setSoTimeout(5000);

                // send "ping" message
                JsonRpcMessage msg = new JsonRpcMessage("ping");
                msg.addParam("version", AceStream.getSdkVersionCode());
                if(mThisDeviceId != null) {
                    msg.addParam("deviceId", mThisDeviceId);
                }

                Log.v(TAG, "device:" + getInternalName() + ":ping: send message: msg=" + msg.toString());

                PrintWriter out = new PrintWriter(new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream())),
                        true);
                out.println(msg.asString());
                out.flush();

                // read response
                BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                try {
                    String message = input.readLine();

                    if(message == null) {
                        Log.v(TAG, "device:" + getInternalName() + ":ping: got null message");
                    }
                    else {
                        try {
                            msg = JsonRpcMessage.fromString(message);

                            if("pong".equals(msg.getMethod())) {
                                mRemoteVersion = msg.getInt("version", 0);
                                mRemoteDeviceId = msg.getString("deviceId", null);
                                Log.v(TAG, "device:" + getInternalName() + ": got pong message: remote_version=" + mRemoteVersion + " remote_device_id=" + mRemoteDeviceId);

                                if(TextUtils.equals(mRemoteDeviceId, mThisDeviceId)) {
                                    Logger.v(TAG, "device:" + getInternalName() + ": drop connection to myself");
                                    //noinspection ConstantConditions
                                    pingSuccess = false;
                                    mPingErrors = PING_MAX_ERRORS;
                                }
                                else {
                                    pingSuccess = true;
                                }
                            }
                            else {
                                Log.d(TAG, "device:" + getInternalName() + ": got unexpected message: method=" + msg.getMethod());
                            }
                        }
                        catch(JSONException e) {
                            Log.e(TAG, "device:" + getInternalName() + ": failed to parse message", e);
                        }
                    }
                }
                catch (IOException e) {
                    Log.e(TAG, "device:" + getInternalName() + ": error in ping thread", e);
                }

                if(!socket.isClosed()) {
                    socket.close();
                }
            }
            catch(SocketTimeoutException e) {
                Log.i(TAG, "device:" + getInternalName() + ": ping timed out");
            }
            catch(ConnectException e) {
                Log.i(TAG, "device:" + getInternalName() + ": ping failed to connect");
            }
            catch(SocketException e) {
                Log.i(TAG, "device:" + getInternalName() + ": ping failed: " + e.getMessage());
            }
            catch (Throwable e) {
                Log.e(TAG, "device:" + getInternalName() + ": error in ping thread", e);
            }

            int interval = -1;
            try {
                updateLastConnected(pingSuccess);

                if(pingSuccess) {
                    // reset errors
                    mPingErrors = 0;
                    interval = PING_SUCCESS_INTERVAL;
                    notifyAvailable();
                }
                else {
                    ++mPingErrors;

                    if (mPingErrors >= PING_MAX_ERRORS) {
                        Log.d(TAG, "device:" + getInternalName() + ":ping: got max errors, device is unavailable: errors=" + mPingErrors + " max=" + PING_MAX_ERRORS);
                        notifyUnavailable();
                    } else {
                        interval = Math.min(PING_MAX_INTERVAL, mPingErrors * PING_BASE_INTERVAL);
                        Log.d(TAG, "device:" + getInternalName() + ":ping: got error: errors=" + mPingErrors + "/" + PING_MAX_ERRORS + " interval=" + interval);
                        notifyPingFailed();
                    }
                }
            }
            catch (Throwable e) {
                Log.e(TAG, "device:" + getInternalName() + ": failed to process ping results: errors=" + mPingErrors, e);
                interval = PING_BASE_INTERVAL;
            }
            finally {
                if(interval != -1 && mPingHandler != null) {
                    mPingHandler.postDelayed(mPingTask, interval);
                }
            }
        }
    };

    private Runnable mPingConnectedTask = new Runnable() {
        @Override
        public void run() {
            if(!mConnected) {
                return;
            }

            try {
                // send "ping" message
                JsonRpcMessage msg = new JsonRpcMessage("ping");
                msg.addParam("version", AceStream.getSdkVersionCode());
                if (mThisDeviceId != null) {
                    msg.addParam("deviceId", mThisDeviceId);
                }

                sendMessageRaw(msg);
            }
            catch(Throwable e) {
                Log.e(TAG, "device:" + getInternalName() + ": failed to ping connected", e);
            }
            finally {
                if(mHandler != null) {
                    mHandler.postDelayed(mPingConnectedTask, PING_CONNECTED_INTERVAL);
                }
            }
        }
    };

    public AceStreamRemoteDevice(String ipAddress, int port, String qualifiedName) {
        mIpAddress = ipAddress;
        mPort = port;

        mId = mIpAddress + ":" + mPort + ":" + qualifiedName;
        mThisDeviceId = AceStream.getDeviceUuidString();

        mMessageQueue = new ArrayDeque<>();
        mListeners = new CopyOnWriteArraySet<>();
    }

    public void setName(String name) {
        mName = name;
    }

    @Override
    public boolean isAceCast() {
        return true;
    }

    public String getInternalName() {
        return mIpAddress + ":" + mRemoteDeviceId;
    }

    public int getPort() {
        return mPort;
    }

    public String getDeviceId() {
        return mRemoteDeviceId;
    }

    public boolean isConnectable() {
        return mLastConnectStatus;
    }

    public boolean isConnected() {
        return mConnected;
    }

    @NonNull
    @Override
    public String toString() {
        return "AceStreamRemoteDevice(addr=" + mIpAddress + ":" + mPort + " id=" + mId + " name=" + mName + " deviceId=" + mRemoteDeviceId + ")";
    }

    public void startPing() {
        Log.d(TAG, "device:" + mIpAddress + ": start ping");

        if (mPingHandlerThread == null) {
            mPingHandlerThread = new HandlerThread(getClass().getSimpleName());
            mPingHandlerThread.start();
            mPingHandler = new Handler(mPingHandlerThread .getLooper());
        }

        mPingHandler.removeCallbacks(mPingTask);
        mPingHandler.post(mPingTask);
    }

    public void connect() {
        mConnectedLock.lock();
        try {
            if (mConnected) {
                Log.v(TAG, "device:" + getInternalName() + ":connect: already connected");
                notifyConnected();
            } else {
                Log.v(TAG, "device:" + getInternalName() + ":connect: start communication thread");

                // thread to connect and receive messages
                new Thread(new ClientThread()).start();

                // thread to send messages
                if (mHandlerThread == null) {
                    mHandlerThread = new HandlerThread(getClass().getSimpleName());
                    mHandlerThread.start();
                    mHandler = new Handler(mHandlerThread.getLooper());
                }
            }
        }
        finally {
            mConnectedLock.unlock();
        }
    }

    public void disconnect() {
        Log.d(TAG, "disconnect");
        closeSocket();

        // stop handler thread
        if(mHandlerThread != null) {
            mHandler.removeCallbacks(mPingConnectedTask);

            mHandlerThread.quit();
            mHandlerThread = null;
            mHandler = null;
        }
    }

    public void destroy() {
        Log.d(TAG, "destroy");
        disconnect();

        // stop ping handler thread
        if(mPingHandlerThread != null) {
            mPingHandler.removeCallbacks(mPingTask);

            mPingHandlerThread.quit();
            mPingHandlerThread = null;
            mPingHandler = null;
        }
    }

    private void closeSocket() {
        try {
            if(mSocket != null) {
                if (!mSocket.isClosed()) {
                    mSocket.close();
                }
            }
        }
        catch(Throwable e) {
            Log.e(TAG, "device:" + getInternalName() + ": failed to close socket", e);
        }
    }

    public void startPlayback(@NonNull PlaybackData playbackData, long savedTime) throws TransportFileParsingException {
        Log.d(TAG, "device:" + getInternalName() + ": startPlayback:" +
            " descriptor=" + playbackData.descriptor.toString() +
            " type=" + playbackData.mediaFile.type +
            " index=" + playbackData.mediaFile.index +
            " streamIndex=" + playbackData.streamIndex +
            " mime=" + playbackData.mediaFile.mime +
            " videoSize=" + playbackData.mediaFile.size +
            " savedTime=" + savedTime
        );

        // Need transport file data when casting to AceCast.
        // Remote device cannot start descriptor points to local file.
        // In other cases it can start, but passing transport file data will cause faster start.
        if(!playbackData.descriptor.hasTransportFileData()) {
            Logger.v(TAG, "startPlayback: fetch transport file data");
            playbackData.descriptor.fetchTransportFileData(AceStream.context().getContentResolver());
        }

        JsonRpcMessage msg = new JsonRpcMessage("startPlayback");
        playbackData.descriptor.toJsonRpcMessage(msg);
        msg.addParam("contentType", playbackData.mediaFile.type);
        msg.addParam("fileIndex", playbackData.mediaFile.index);
        msg.addParam("mime", playbackData.mediaFile.mime);
        msg.addParam("streamIndex", playbackData.streamIndex);
        msg.addParam("videoSize", playbackData.mediaFile.size);
        msg.addParam("seekOnStart", savedTime);

        mLastStartPlaybackMessage = msg;
        mStartPlaybackRetries = 3;

        sendMessage(msg, true);
    }

    public void play() {
        Log.d(TAG, "device:" + getInternalName() + ": play");
        sendMessage(new JsonRpcMessage("play"), false);
    }

    public void pause() {
        Log.d(TAG, "device:" + getInternalName() + ": pause");
        sendMessage(new JsonRpcMessage("pause"), false);
    }

    public void stop(boolean disconnect) {
        Log.d(TAG, "device:" + getInternalName() + ": stop: disconnect=" + disconnect);
        JsonRpcMessage msg = new JsonRpcMessage("stop");
        msg.addParam("disconnect", disconnect);
        sendMessage(msg, false);
        if(disconnect) {
            sendMessage(new JsonRpcMessage("quit"), false);
        }
        mPlayerState.reset();
    }

    public void stopEngineSession() {
        Log.d(TAG, "device:" + getInternalName() + ": stopEngineSession");
        sendMessage(new JsonRpcMessage("stopEngineSession"), false);
    }

    public int setVolume(float value) {
        return setVolume(Math.round(value * 100));
    }

    public int setVolume(int value) {
        Log.d(TAG, "device:" + getInternalName() + ": setVolume: value=" + value);
        JsonRpcMessage msg = new JsonRpcMessage("setVolume");
        msg.addParam("value", value);
        sendMessage(msg, false);
        return value;
    }

    public void setTime(long value) {
        Log.d(TAG, "device:" + getInternalName() + ": setTime: value=" + value);
        JsonRpcMessage msg = new JsonRpcMessage("setTime");
        msg.addParam("value", value);
        sendMessage(msg, false);
    }

    public void setVideoSize(String value) {
        Log.d(TAG, "device:" + getInternalName() + ": setVideoSize: value=" + value);
        JsonRpcMessage msg = new JsonRpcMessage("setVideoSize");
        msg.addParam("value", value);
        sendMessage(msg, false);
    }

    public void setDeinterlace(String value) {
        Log.d(TAG, "device: setDeinterlace: value=" + value);
        JsonRpcMessage msg = new JsonRpcMessage("setDeinterlace");
        msg.addParam("value", value);
        sendMessage(msg, false);

        // update local value
        mPlayerState.setDeinterlaceMode(value);
    }

    public void liveSeek(int value) {
        Log.d(TAG, "device:" + getInternalName() + ": liveSeek: value=" + value);
        JsonRpcMessage msg = new JsonRpcMessage("liveSeek");
        msg.addParam("value", value);
        sendMessage(msg, false);
    }

    public boolean setAudioTrack(int trackId) {
        Log.d(TAG, "device:" + getInternalName() + ":setAudioTrack: trackId=" + trackId);
        JsonRpcMessage msg = new JsonRpcMessage("setAudioTrack");
        msg.addParam("trackId", trackId);
        sendMessage(msg, false);
        return true;
    }

    public boolean setAudioDigitalOutputEnabled(boolean enabled) {
        Log.d(TAG, "device:" + getInternalName() + ":setAudioDigitalOutputEnabled: enabled=" + enabled);
        JsonRpcMessage msg = new JsonRpcMessage("setAudioDigitalOutputEnabled");
        msg.addParam("enabled", enabled);
        sendMessage(msg, false);
        return true;
    }

    public boolean setAudioOutput(String aout) {
        Log.d(TAG, "device:" + getInternalName() + ":setAudioOutput: aout=" + aout);
        JsonRpcMessage msg = new JsonRpcMessage("setAudioOutput");
        msg.addParam("aout", aout);
        sendMessage(msg, false);
        return true;
    }

    public boolean setSpuTrack(int trackId) {
        Log.d(TAG, "device:" + getInternalName() + ":setSubtitleTrack: trackId=" + trackId);
        JsonRpcMessage msg = new JsonRpcMessage("setSubtitleTrack");
        msg.addParam("trackId", trackId);
        sendMessage(msg, false);
        return true;
    }

    public void setHlsStream(int streamIndex) {
        Log.d(TAG, "device:" + getInternalName() + ":setHlsStream: streamIndex=" + streamIndex);
        JsonRpcMessage msg = new JsonRpcMessage("setHlsStream");
        msg.addParam("streamIndex", streamIndex);
        sendMessage(msg, false);
    }

    private void sendMessage(final JsonRpcMessage message, boolean queueMessage) {
        mConnectedLock.lock();
        try {
            if(!mConnected) {
                if(queueMessage) {
                    // put message in a queue
                    Log.v(TAG, "device:" + getInternalName() + ": put message in a queue: " + message.asString());
                    mMessageQueue.add(message);
                }
                else {
                    Log.v(TAG, "device:" + getInternalName() + ": disconnected, skip message: " + message.asString());
                }
                return;
            }

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    sendMessageRaw(message);
                }
            });
        }
        catch(Throwable e) {
            Log.e(TAG, "device:" + getInternalName() + ": failed to send message", e);
        }
        finally {
            mConnectedLock.unlock();
        }
    }

    private void sendMessageRaw(JsonRpcMessage message) {
        try {
            String rawMessage = message.asString();
            Log.v(TAG, "device:" + getInternalName() + ": send message: len=" + rawMessage.length() + " data=" + rawMessage);

            PrintWriter out = new PrintWriter(new BufferedWriter(
                    new OutputStreamWriter(mSocket.getOutputStream())),
                    true);
            out.println(rawMessage);
            out.flush();
        }
        catch(Throwable e) {
            Log.e(TAG, "device:" + mIpAddress + ": failed to send message", e);
        }
    }

    private void resendLastStartPlaybackMessage() {
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if(--mStartPlaybackRetries >= 0) {
                    Logger.v(TAG, "resendLastStartPlaybackMessage: retry " + mStartPlaybackRetries);
                    sendMessageRaw(mLastStartPlaybackMessage);
                }
                else {
                    Logger.v(TAG, "resendLastStartPlaybackMessage: give up");
                }
            }
        }, 1000);
    }

    private void onConnected() {
        mConnectedLock.lock();
        try {
            Log.d(TAG, "device:" + getInternalName() + ": connected: queue size is " + mMessageQueue.size());
            mConnected = true;
            updateLastConnected(true);
            notifyConnected();

            // send from queue
            while (!mMessageQueue.isEmpty()) {
                JsonRpcMessage msg = mMessageQueue.poll();
                if (msg != null) {
                    Log.d(TAG, "device:" + getInternalName() + ": send message from queue: method=" + msg.getMethod());
                    sendMessage(msg, false);
                }
            }

            // start pinging
            mHandler.postDelayed(mPingConnectedTask, PING_CONNECTED_INTERVAL);
        }
        finally {
            mConnectedLock.unlock();
        }
    }
    private void onDisconnected(boolean cleanShutdown) {
        mConnectedLock.lock();
        try {
            Log.d(TAG, "device:" + getInternalName() + ": disconnected: cleanShutdown=" + cleanShutdown);
            mConnected = false;
            mPlayerState.reset();
            notifyDisconnected(cleanShutdown);

            if (!cleanShutdown) {
                startPing();
            }
        }
        finally {
            mConnectedLock.unlock();
        }
    }

    private void onMessage(JsonRpcMessage msg) {
        // Secondary message can be generated here (we fire events on state change).
        List<JsonRpcMessage> extraMessages = super.handleMessage(msg, true);
        notifyMessage(msg);
        for(JsonRpcMessage extraMsg: extraMessages) {
            notifyMessage(extraMsg);
        }
    }

    @Override
    protected boolean setSelectedPlayer(SelectedPlayer player) {
        // notify when changed
        if(super.setSelectedPlayer(player)) {
            notifySelectedPlayerChanged(player);
            return true;
        }
        return false;
    }

    @Override
    protected boolean setOutputFormat(String outputFormat) {
        // notify when changed
        if(super.setOutputFormat(outputFormat)) {
            notifyOutputFormatChanged(outputFormat);
            return true;
        }
        return false;
    }

    private void notifyOutputFormatChanged(final String value) {
        Workers.runOnMainThread(new Runnable() {
            @Override
            public void run() {
                for (AceStreamRemoteDeviceListener listener : mListeners) {
                    listener.onOutputFormatChanged(AceStreamRemoteDevice.this, value);
                }
            }
        });
    }

    private void notifySelectedPlayerChanged(final SelectedPlayer player) {
        Workers.runOnMainThread(new Runnable() {
            @Override
            public void run() {
                for (AceStreamRemoteDeviceListener listener : mListeners) {
                    listener.onSelectedPlayerChanged(AceStreamRemoteDevice.this, player);
                }
            }
        });

    }

    private void notifyConnected() {
        Workers.runOnMainThread(new Runnable() {
            @Override
            public void run() {
                for (AceStreamRemoteDeviceListener listener : mListeners) {
                    listener.onConnected(AceStreamRemoteDevice.this);
                }
            }
        });
    }

    private void notifyDisconnected(final boolean cleanShutdown) {
        Workers.runOnMainThread(new Runnable() {
            @Override
            public void run() {
                for (AceStreamRemoteDeviceListener listener : mListeners) {
                    listener.onDisconnected(AceStreamRemoteDevice.this, cleanShutdown);
                }
            }
        });
    }

    private void notifyMessage(final JsonRpcMessage msg) {
        Workers.runOnMainThread(new Runnable() {
            @Override
            public void run() {
                for (AceStreamRemoteDeviceListener listener : mListeners) {
                    listener.onMessage(AceStreamRemoteDevice.this, msg);
                }
            }
        });
    }

    private void notifyAvailable() {
        Workers.runOnMainThread(new Runnable() {
            @Override
            public void run() {
                for (AceStreamRemoteDeviceListener listener : mListeners) {
                    listener.onAvailable(AceStreamRemoteDevice.this);
                }
            }
        });
    }

    private void notifyUnavailable() {
        Workers.runOnMainThread(new Runnable() {
            @Override
            public void run() {
                for (AceStreamRemoteDeviceListener listener : mListeners) {
                    listener.onUnavailable(AceStreamRemoteDevice.this);
                }
            }
        });
    }

    private void notifyPingFailed() {
        Workers.runOnMainThread(new Runnable() {
            @Override
            public void run() {
                for (AceStreamRemoteDeviceListener listener : mListeners) {
                    listener.onPingFailed(AceStreamRemoteDevice.this);
                }
            }
        });
    }

    public void addListener(AceStreamRemoteDeviceListener listener) {
        mListeners.add(listener);
        Log.v(TAG, "device:" + getInternalName() + ":addListener: listener=" + listener + " count=" + mListeners.size());
    }

    public void removeListener(AceStreamRemoteDeviceListener listener) {
        mListeners.remove(listener);
        Log.v(TAG, "device:" + getInternalName() + ":removeListener: listener=" + listener + " count=" + mListeners.size());
    }

    private void updateLastConnected(boolean isConnected) {
        mLastConnectStatus = isConnected;
    }

    private class ClientThread implements Runnable {
        @Override
        public void run() {
            boolean cleanShutdown = false;
            try {
                Log.v(TAG, "device:" + mIpAddress + ":client_thread: open socket: addr=" + mIpAddress + ":" + mPort);
                InetAddress serverAddr = InetAddress.getByName(mIpAddress);
                mSocket = new Socket(serverAddr, mPort);
                onConnected();

                // send hello
                JsonRpcMessage helloMsg = new JsonRpcMessage("hello");
                helloMsg.addParam("version", AceStream.getSdkVersionCode());
                if(mThisDeviceId != null) {
                    helloMsg.addParam("deviceId", mThisDeviceId);
                }
                sendMessageRaw(helloMsg);

                BufferedReader input = new BufferedReader(new InputStreamReader(mSocket.getInputStream()));
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        String message = input.readLine();

                        if(message == null) {
                            Log.v(TAG, "device:" + mIpAddress + ": got null message, stop");
                            Thread.currentThread().interrupt();
                        }
                        else {
                            try {
                                JsonRpcMessage msg = JsonRpcMessage.fromString(message);

                                if("quit".equals(msg.getMethod())) {
                                    Log.d(TAG, "device:" + mIpAddress + ": got quit message");
                                    cleanShutdown = true;
                                    Thread.currentThread().interrupt();
                                    sendMessageRaw(new JsonRpcMessage("quit"));
                                }
                                else if("hello".equals(msg.getMethod())) {
                                    Log.d(TAG, "device:" + mIpAddress + ": got hello message");
                                    mRemoteVersion = msg.getInt("version", 0);
                                    mRemoteDeviceId = msg.getString("deviceId", null);

                                    if(TextUtils.equals(mRemoteDeviceId, mThisDeviceId)) {
                                        Logger.v(TAG, "device:" + mIpAddress + ": disconnect form myself");
                                        notifyPingFailed();
                                        cleanShutdown = true;
                                        break;
                                    }
                                }
                                else //noinspection StatementWithEmptyBody
                                    if("pong".equals(msg.getMethod())) {
                                    // got pong, do nothing
                                }
                                else if("lastStartPlaybackMessageFailed".equals(msg.getMethod())) {
                                    Log.d(TAG, "device:" + mIpAddress + ": last start playback message failed");
                                    resendLastStartPlaybackMessage();
                                }
                                else {
                                    onMessage(msg);
                                }
                            }
                            catch(JSONException e) {
                                Log.e(TAG, "device:" + mIpAddress + ": failed to parse message", e);
                            }
                        }
                    }
                    catch (IOException e) {
                        Log.e(TAG, "device:" + mIpAddress + ": error in client thread", e);
                        notifyPingFailed();
                        break;
                    }
                }

                closeSocket();
            }
            catch (Throwable e) {
                Log.e(TAG, "device:" + mIpAddress + ": error in network thread", e);
            }
            finally {
                onDisconnected(cleanShutdown);
            }
        }
    }

    public boolean equals(AceStreamRemoteDevice other) {
        if(other == null) {
            return false;
        }
        if(mIpAddress == null || mPort == 0) {
            return false;
        }
        if(other.getIpAddress() == null || other.getPort() == 0) {
            return false;
        }

        return mIpAddress.equals(other.getIpAddress()) && mPort == other.getPort();
    }

    public void setPosition(float pos) {
        if(mPlayerState.getLength() > 0) {
            long time = (long)(mPlayerState.getLength() * pos);
            setTime(time);
        }
    }
}