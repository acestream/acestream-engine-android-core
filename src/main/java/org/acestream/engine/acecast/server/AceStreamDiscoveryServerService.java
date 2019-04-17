package org.acestream.engine.acecast.server;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import androidx.annotation.MainThread;
import android.text.TextUtils;
import android.util.Log;

import org.acestream.engine.AceStreamEngineBaseApplication;
import org.acestream.engine.BaseService;
import org.acestream.sdk.JsonRpcMessage;
import org.acestream.sdk.utils.Logger;
import org.acestream.sdk.utils.Workers;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class AceStreamDiscoveryServerService extends BaseService {
    private final static String TAG = "AS/DSS";
    private final static String ACTION_BIND_LOCAL = "ACTION_BIND_LOCAL";
    private final static String ACTION_BIND_REMOTE = "ACTION_BIND_REMOTE";
    private final static String ACTION_RESTART = "ACTION_RESTART";
    private AceStreamDiscoveryServer mServer = null;
    private final IBinder mLocalBinder = new LocalBinder();
    private Messenger mMessenger;

    // client -> service
    public static final int MSG_REGISTER_CLIENT = 0;
    public static final int MSG_UNREGISTER_CLIENT = 1;
    public static final int MSG_ADD_SERVER_LISTENER = 2;
    public static final int MSG_REMOVE_SERVER_LISTENER = 3;
    public static final int MSG_ADD_CLIENT_LISTENER = 4;
    public static final int MSG_REMOVE_CLIENT_LISTENER = 5;
    public static final int MSG_SEND_PLAYER_CLOSED = 6;
    public static final int MSG_SEND_MESSAGE = 7;
    public static final int MSG_GET_CLIENT_INFO = 8;

    // service:server -> client
    public static final int MSG_SERVER_ON_CLIENT_CONNECTED = 9;
    public static final int MSG_SERVER_ON_CLIENT_DISCONNECTED = 10;
    public static final int MSG_SERVER_ON_CLIENT_INFO = 11;

    // service:client -> client
    public static final int MSG_CLIENT_ON_MESSAGE = 12;
    public static final int MSG_CLIENT_ON_DISCONNECTED = 13;

    // params
    public static final String PARAM_CLIENT_ID = "clientId";
    public static final String PARAM_DEVICE_ID = "deviceId";
    public static final String PARAM_SHUTDOWN = "shutdown";
    public static final String PARAM_CLIENT_MESSAGE = "clientMessage";

    private List<Messenger> mClients = new ArrayList<>();

    private class LocalBinder extends Binder {
        AceStreamDiscoveryServerService getService() {
            return AceStreamDiscoveryServerService.this;
        }
    }

    private class ServiceHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            Log.v(TAG, "incoming_message: id=" + msg.what + " sender=" + msg.replyTo);
            switch (msg.what) {
                case MSG_REGISTER_CLIENT:
                    mClients.add(msg.replyTo);
                    break;
                case MSG_UNREGISTER_CLIENT:
                    mClients.remove(msg.replyTo);
                    break;
                case MSG_ADD_SERVER_LISTENER:
                    mServer.addListener(msg.replyTo);
                    break;
                case MSG_REMOVE_SERVER_LISTENER:
                    mServer.removeListener(msg.replyTo);
                    break;
                case MSG_GET_CLIENT_INFO: {
                    Bundle data = msg.getData();
                    if(data != null) {
                        String clientId = data.getString(PARAM_CLIENT_ID);
                        String deviceId = null;

                        final AceStreamDiscoveryServerClient client = mServer.getRemoteClient(clientId);
                        if(client != null) {
                            deviceId = client.getDeviceId();
                        }

                        Message response = Message.obtain(null, MSG_SERVER_ON_CLIENT_INFO);
                        Bundle responseData = new Bundle();
                        responseData.putString(PARAM_CLIENT_ID, clientId);
                        responseData.putString(PARAM_DEVICE_ID, deviceId);

                        response.setData(responseData);
                        try {
                            msg.replyTo.send(response);
                        }
                        catch(RemoteException e) {
                            Log.w(TAG, "Failed to send message: " + e.getMessage());
                        }
                    }
                    break;
                }
                case MSG_ADD_CLIENT_LISTENER: {
                    Bundle data = msg.getData();
                    if(data != null) {
                        final AceStreamDiscoveryServerClient client = mServer.getRemoteClient(data.getString(PARAM_CLIENT_ID));
                        if(client != null) {
                            client.addListener(msg.replyTo);
                        }
                    }
                    break;
                }
                case MSG_REMOVE_CLIENT_LISTENER: {
                    Bundle data = msg.getData();
                    if(data != null) {
                        final AceStreamDiscoveryServerClient client = mServer.getRemoteClient(data.getString(PARAM_CLIENT_ID));
                        if(client != null) {
                            client.removeListener(msg.replyTo);
                        }
                    }
                    break;
                }
                case MSG_SEND_PLAYER_CLOSED: {
                    Bundle data = msg.getData();
                    if(data != null) {
                        final AceStreamDiscoveryServerClient client = mServer.getRemoteClient(data.getString(PARAM_CLIENT_ID));
                        if(client != null) {
                            client.playerClosed(data.getBoolean(PARAM_SHUTDOWN, false));
                        }
                    }
                    break;
                }
                case MSG_SEND_MESSAGE: {
                    Bundle data = msg.getData();
                    if(data != null) {
                        final AceStreamDiscoveryServerClient client = mServer.getRemoteClient(data.getString(PARAM_CLIENT_ID));
                        if(client != null) {
                            try {
                                JsonRpcMessage clientMsg = JsonRpcMessage.fromString(data.getString(PARAM_CLIENT_MESSAGE));
                                client.sendMessage(clientMsg);
                            }
                            catch(JSONException e) {
                                Log.e(TAG, "handleMessage: failed to parse client message", e);
                            }
                        }
                    }
                    break;
                }
                default:
                    super.handleMessage(msg);
            }
        }
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        mServer = new AceStreamDiscoveryServer(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        Log.d(TAG, "onStartCommand: action=" + action);

        boolean force = false;
        if(TextUtils.equals(action, ACTION_RESTART))  {
            force = true;
        }

        // Check that server is running
        mServer.restart(force);

        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        String action = (intent == null) ? null : intent.getAction();

        if(TextUtils.equals(action, ACTION_BIND_REMOTE)) {
            mMessenger = new Messenger(new ServiceHandler());
            return mMessenger.getBinder();
        }
        else {
            return mLocalBinder;
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        mServer.shutdown();
    }

    public AceStreamDiscoveryServer getServer() {
        return mServer;
    }

    public static class Client {
        public static final String TAG = "AS/DSSC";

        public interface Callback {
            void onConnected(AceStreamDiscoveryServerService service);
            void onDisconnected();
        }

        public interface RemoteCallback {
            void onConnected();
            void onDisconnected();
        }

        public interface ServerCallback {
            void onClientConnected(String clientId, String deviceId);
            void onClientDisconnected(String clientId, String deviceId);
            void onClientInfo(String clientId, String deviceId);
        }

        public interface ClientCallback {
            void onMessage(String clientId, JsonRpcMessage message);
            void onDisconnected(String clientId, String deviceId);
        }

        private boolean mBound = false;
        private boolean mConnected = false;
        private final Callback mLocalCallback;
        private final RemoteCallback mRemoteCallback;
        private final Context mContext;
        private Messenger mServiceMessenger = null;
        private final Messenger mClientMessenger = new Messenger(new ClientHandler());
        private final Set<ServerCallback> mServerCallbacks = new CopyOnWriteArraySet<>();
        private final Map<String,Set<ClientCallback>> mClientCallbacks = new HashMap<>();

        class ClientHandler extends Handler {
            @Override
            public void handleMessage(Message msg) {
                Log.v(TAG, "handleMessage: id=" + msg.what);
                switch(msg.what) {
                    case MSG_SERVER_ON_CLIENT_CONNECTED: {
                        Bundle data = msg.getData();
                        if(data != null) {
                            notifyServerClientConnected(
                                    data.getString(PARAM_CLIENT_ID),
                                    data.getString(PARAM_DEVICE_ID));
                        }
                        break;
                    }
                    case MSG_SERVER_ON_CLIENT_DISCONNECTED: {
                        Bundle data = msg.getData();
                        if(data != null) {
                            notifyServerClientDisconnected(
                                    data.getString(PARAM_CLIENT_ID),
                                    data.getString(PARAM_DEVICE_ID));
                        }
                        break;
                    }
                    case MSG_SERVER_ON_CLIENT_INFO: {
                        Bundle data = msg.getData();
                        if(data != null) {
                            notifyServerClientInfo(
                                    data.getString(PARAM_CLIENT_ID),
                                    data.getString(PARAM_DEVICE_ID));
                        }
                        break;
                    }
                    case MSG_CLIENT_ON_MESSAGE: {
                        Bundle data = msg.getData();
                        if(data != null) {
                            try {
                                JsonRpcMessage message = JsonRpcMessage.fromString(data.getString(PARAM_CLIENT_MESSAGE));
                                notifyClientMessage(data.getString(PARAM_CLIENT_ID), message);
                            }
                            catch(JSONException e) {
                                Log.e(TAG, "failed to parse message", e);
                            }
                        }
                        break;
                    }
                    case MSG_CLIENT_ON_DISCONNECTED: {
                        Bundle data = msg.getData();
                        if(data != null) {
                            notifyClientDisconnected(
                                    data.getString(PARAM_CLIENT_ID),
                                    data.getString(PARAM_DEVICE_ID));
                        }
                        break;
                    }
                    default:
                        super.handleMessage(msg);
                }
            }
        }

        private void notifyServerClientConnected(String clientId, String deviceId) {
            for(ServerCallback callback: mServerCallbacks) {
                callback.onClientConnected(clientId, deviceId);
            }
        }

        private void notifyServerClientDisconnected(String clientId, String deviceId) {
            for(ServerCallback callback: mServerCallbacks) {
                callback.onClientDisconnected(clientId, deviceId);
            }
        }

        private void notifyServerClientInfo(String clientId, String deviceId) {
            for(ServerCallback callback: mServerCallbacks) {
                callback.onClientInfo(clientId, deviceId);
            }
        }

        private void notifyClientMessage(String clientId, JsonRpcMessage message) {
            synchronized (mClientCallbacks) {
                if(mClientCallbacks.containsKey(clientId)) {
                    for(ClientCallback callback: mClientCallbacks.get(clientId)) {
                        callback.onMessage(clientId, message);
                    }
                }
            }
        }

        private void notifyClientDisconnected(String clientId, String deviceId) {
            synchronized (mClientCallbacks) {
                if(mClientCallbacks.containsKey(clientId)) {
                    for(ClientCallback callback: mClientCallbacks.get(clientId)) {
                        callback.onDisconnected(clientId, deviceId);
                    }
                }
            }
        }

        private final ServiceConnection mServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                Log.d(TAG, "onServiceConnected: bound=" + mBound + " connected=" + mConnected);

                if(mConnected) {
                    return;
                }

                mConnected = true;

                if(mLocalCallback != null) {
                    LocalBinder localBinder = (LocalBinder) binder;
                    final AceStreamDiscoveryServerService service = localBinder.getService();
                    if (service != null) {
                        mLocalCallback.onConnected(service);
                    }
                }
                else {
                    mServiceMessenger = new Messenger(binder);
                    try {
                        mServiceMessenger.send(obtainMessage(MSG_REGISTER_CLIENT));
                        mRemoteCallback.onConnected();
                    }
                    catch(RemoteException e) {
                        // do nothing
                    }
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.d(TAG, "onServiceDisconnected");

                mServiceMessenger = null;
                mBound = false;
                mConnected = false;

                if(mLocalCallback != null) {
                    mLocalCallback.onDisconnected();
                }
                else {
                    mRemoteCallback.onDisconnected();
                }
            }
        };

        public Client(Context context, Callback callback) {
            if (context == null || callback == null) {
                throw new IllegalArgumentException("Context and callback can't be null");
            }
            mContext = context;
            mLocalCallback = callback;
            mRemoteCallback = null;
        }

        public Client(Context context, RemoteCallback callback) {
            if (context == null || callback == null) {
                throw new IllegalArgumentException("Context and callback can't be null");
            }
            mContext = context;
            mRemoteCallback = callback;
            mLocalCallback = null;
        }

        private Message obtainMessage(int messageId) {
            Message msg = Message.obtain(null, messageId);
            msg.replyTo = mClientMessenger;
            return msg;
        }

        public void addServerListener(ServerCallback callback) {
            sendMessage(obtainMessage(MSG_ADD_SERVER_LISTENER));
            mServerCallbacks.add(callback);
        }

        public void removeServerListener(ServerCallback callback) {
            sendMessage(obtainMessage(MSG_REMOVE_SERVER_LISTENER));
            mServerCallbacks.remove(callback);
        }

        public void getClientInfo(String clientId) {
            Message msg = obtainMessage(MSG_GET_CLIENT_INFO);
            Bundle data = new Bundle(1);
            data.putString(PARAM_CLIENT_ID, clientId);
            msg.setData(data);
            sendMessage(msg);
        }

        public void addClientListener(String clientId, ClientCallback callback) {
            Logger.v(TAG, "addClientListener: clientId=" + clientId + " callback=" + callback);
            Message msg = obtainMessage(MSG_ADD_CLIENT_LISTENER);
            Bundle data = new Bundle(1);
            data.putString(PARAM_CLIENT_ID, clientId);
            msg.setData(data);
            sendMessage(msg);

            synchronized (mClientCallbacks) {
                if(!mClientCallbacks.containsKey(clientId)) {
                    mClientCallbacks.put(clientId, new HashSet<ClientCallback>());
                }
                mClientCallbacks.get(clientId).add(callback);
            }
        }

        public void removeClientListener(String clientId, ClientCallback callback) {
            Message msg = obtainMessage(MSG_ADD_CLIENT_LISTENER);
            Bundle data = new Bundle(1);
            data.putString(PARAM_CLIENT_ID, clientId);
            msg.setData(data);
            sendMessage(msg);

            synchronized (mClientCallbacks) {
                if(mClientCallbacks.containsKey(clientId)) {
                    mClientCallbacks.get(clientId).remove(callback);
                }
            }
        }

        public void sendPlayerClosed(String clientId, boolean shutdown) {
            Message msg = obtainMessage(MSG_SEND_PLAYER_CLOSED);
            Bundle data = new Bundle(2);
            data.putString(PARAM_CLIENT_ID, clientId);
            data.putBoolean(PARAM_SHUTDOWN, shutdown);
            msg.setData(data);
            sendMessage(msg);
        }

        public void sendClientMessage(String clientId, JsonRpcMessage clientMessage) {
            Message msg = obtainMessage(MSG_SEND_MESSAGE);
            Bundle data = new Bundle(2);
            data.putString(PARAM_CLIENT_ID, clientId);
            data.putString(PARAM_CLIENT_MESSAGE, clientMessage.toString());
            msg.setData(data);
            sendMessage(msg);
        }

        private void sendMessage(Message msg) {
            if(mServiceMessenger == null) return;
            try {
                mServiceMessenger.send(msg);
            }
            catch(RemoteException e) {
                Log.e(TAG, "sendMessage: failed", e);
            }
        }

        @MainThread
        public boolean connect() {
            if(!AceStreamEngineBaseApplication.shouldStartAceCastServer()) {
                Log.d(TAG, "connect: AceCast server is disabled");
                return false;
            }

            if(!Workers.isOnMainThread()) {
                throw new IllegalStateException("Must be run on main thread");
            }

            if (mBound) {
                Log.v(TAG, "connect: already connected");
                return false;
            }

            boolean bindLocal = (mLocalCallback != null);

            Log.d(TAG, "connect: bindLocal=" + bindLocal + " connected=" + mConnected);
            final Intent serviceIntent = getServiceIntent(mContext);
            serviceIntent.setAction(bindLocal ? ACTION_BIND_LOCAL : ACTION_BIND_REMOTE);
            mContext.startService(serviceIntent);
            mBound = mContext.bindService(serviceIntent, mServiceConnection, BIND_AUTO_CREATE);

            return true;
        }

        @MainThread
        public void disconnect() {
            if(!Workers.isOnMainThread()) {
                throw new IllegalStateException("Must be run on main thread");
            }

            Log.d(TAG, "disconnect: bound=" + mBound + " connected=" + mConnected);
            if (mBound) {
                if(mServiceMessenger != null) {
                    try {
                        mServiceMessenger.send(obtainMessage(MSG_UNREGISTER_CLIENT));
                    }
                    catch(RemoteException e) {
                        // do nothing
                    }
                }
                mBound = false;
                mConnected = false;
                mContext.unbindService(mServiceConnection);
            }
        }

        private static Intent getServiceIntent(Context context) {
            return new Intent(context, AceStreamDiscoveryServerService.class);
        }

        public static void startService(Context context) {
            Log.v(TAG, "startService");
            context.startService(getServiceIntent(context));
        }

        public static void stopService(Context context) {
            Log.v(TAG, "stopService");
            context.stopService(getServiceIntent(context));
        }

        public static void restartService(Context context) {
            Log.v(TAG, "restartService");
            Intent intent = getServiceIntent(context);
            intent.setAction(ACTION_RESTART);
            context.startService(intent);
        }
    }
}