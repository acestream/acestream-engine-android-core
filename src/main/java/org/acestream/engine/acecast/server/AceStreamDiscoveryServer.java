package org.acestream.engine.acecast.server;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Messenger;
import android.text.TextUtils;
import android.util.Log;

import com.connectsdk.core.Util;

import org.acestream.engine.AceStreamEngineBaseApplication;
import org.acestream.sdk.AceStream;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

public class AceStreamDiscoveryServer {
    private final static String TAG = "AceStream/DS";
    private final long MIN_RESTART_AGE = 600000;
    private final long MIN_CONNECTION_AGE = 600000;

    private JmDNS mJmDNS;
    private Context mContext;

    private int mLocalPort;
    private Handler mHandler;
    private HandlerThread mHandlerThread;
    private ServerThread mServerThread;
    private long mLastMDNSRestartAt = 0;
    private long mLastConnectionAt = 0;
    private final Map<String, AceStreamDiscoveryServerClient> mClients = new HashMap<>();
    private final Set<AceStreamDiscoveryServerListener> mListeners = new CopyOnWriteArraySet<>();
    private final Set<Messenger> mMessengerListeners = new HashSet<>();

    private final static int MAX_REGISTER_ERRORS = 10;
    private final static int REGISTER_RETRY_INTERVAL = 60000;
    private int mRegisterErrors = 0;

    AceStreamDiscoveryServer(Context context) {
        Log.d(TAG, "create");

        mContext = context;

        mHandlerThread = new HandlerThread(getClass().getSimpleName());
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
    }

    public void addListener(AceStreamDiscoveryServerListener listener) {
        Log.v(TAG, "addListener: this=" + this.hashCode() + " listener=" + listener.hashCode());
        mListeners.add(listener);
    }

    public void removeListener(AceStreamDiscoveryServerListener listener) {
        Log.v(TAG, "removeListener: this=" + this.hashCode() + " listener=" + listener.hashCode());
        mListeners.remove(listener);
    }

    public void addListener(Messenger listener) {
        mMessengerListeners.add(listener);
    }

    public void removeListener(Messenger listener) {
        mMessengerListeners.remove(listener);
    }

    private void notifyConnected(AceStreamDiscoveryServerClient client) {
        try {
            for (AceStreamDiscoveryServerListener listener : mListeners) {
                listener.onConnected(client);
            }

            for(Messenger listener: mMessengerListeners) {
                final Message msg = Message.obtain(null, AceStreamDiscoveryServerService.MSG_SERVER_ON_CLIENT_CONNECTED);
                Bundle data = new Bundle(2);
                data.putString(AceStreamDiscoveryServerService.PARAM_CLIENT_ID, client.getId());
                data.putString(AceStreamDiscoveryServerService.PARAM_DEVICE_ID, client.getDeviceId());
                msg.setData(data);
                listener.send(msg);
            }
        }
        catch(Throwable e) {
            Log.e(TAG, "notifyConnected: error", e);
        }
    }

    private void notifyDisconnected(AceStreamDiscoveryServerClient client) {
        try {
            for (AceStreamDiscoveryServerListener listener : mListeners) {
                listener.onDisconnected(client);
            }

            for(Messenger listener: mMessengerListeners) {
                final Message msg = Message.obtain(null, AceStreamDiscoveryServerService.MSG_SERVER_ON_CLIENT_DISCONNECTED);
                Bundle data = new Bundle(2);
                data.putString(AceStreamDiscoveryServerService.PARAM_CLIENT_ID, client.getId());
                data.putString(AceStreamDiscoveryServerService.PARAM_DEVICE_ID, client.getDeviceId());
                msg.setData(data);
                listener.send(msg);
            }
        }
        catch(Throwable e) {
            Log.e(TAG, "notifyDisconnected: error", e);
        }
    }

    public void start() {
        restartServerThread(true);
    }

    public void restart(boolean force) {
        if(mServerThread == null) {
            Log.d(TAG, "restart: no server thread");
            restartServerThread(true);
        }
        else if(!mServerThread.isAlive()) {
            Log.d(TAG, "restart: server thread is not alive");
            restartServerThread(true);
        }
        else if(force) {
            long lastConnectionAge = System.currentTimeMillis() - mLastConnectionAt;
            Log.d(TAG, "restart: force server thread restart: clients=" + mClients.size() + " lastConnectionAge=" + lastConnectionAge);
            if(mClients.size() == 0 && mLastConnectionAt != 0 && lastConnectionAge > MIN_CONNECTION_AGE) {
                restartServerThread(true);
            }
        }

        // restart only when server is restarted
//        mHandler.post(new Runnable() {
//            @Override
//            public void run() {
//                try {
//                    restartMDNS();
//                }
//                finally {
//                    Log.v(TAG, "restartMDNS: done");
//                }
//            }
//        });
    }

    private void restartServerThread(boolean postToHandlerThread) {
        Log.d(TAG, "restart server thread: postToHandlerThread=" + postToHandlerThread);

        if(postToHandlerThread) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        restartServerThread(false);
                    }
                    finally {
                        Log.v(TAG, "restartServerThread: done");
                    }
                }
            });
            return;
        }

        if(mServerThread != null) {
            try {
                stopServer();

                Log.d(TAG, "restart: join prev thread");
                mServerThread.join();
                Log.d(TAG, "restart: join done");
            } catch (InterruptedException e) {
                Log.d(TAG, "restart: got InterruptedException on join");
            }
        }

        mServerThread = new ServerThread();
        mServerThread.start();
    }

    private void restartMDNS() {
        long age = -1;
        if(mLastMDNSRestartAt > 0) {
            age = System.currentTimeMillis() - mLastMDNSRestartAt;
        }

        Log.d(TAG, "restartMDNS: port=" + mLocalPort + " age=" + age);
        if(mLocalPort != 0 && age > MIN_RESTART_AGE) {
            if(mJmDNS != null) {
                mJmDNS.unregisterAllServices();
            }
            registerService(mLocalPort);
        }
    }

    public void shutdown() {
        Log.d(TAG, "shutdown");

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    unregisterService();
                    stopServer();
                }
                finally {
                    Log.v(TAG, "shutdown: done");
                }
            }
        });
    }

    private void registerService(int port) {
        try {
            Log.v(TAG, "registerService: port=" + port);
            if(mJmDNS == null) {
                InetAddress addr = Util.getIpAddress(AceStream.context());
                if(addr == null) {
                    //TODO: retry registration of failed
                    mRegisterErrors += 1;
                    Log.e(TAG, "registerService: failed to get addr: errors=" + mRegisterErrors + "/" + MAX_REGISTER_ERRORS);
                    if(mRegisterErrors < MAX_REGISTER_ERRORS) {
                        mHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    if (mLocalPort != 0) {
                                        registerService(mLocalPort);
                                    }
                                }
                                finally {
                                    Log.v(TAG, "registerService:delayed: done");
                                }
                            }
                        }, REGISTER_RETRY_INTERVAL);
                    }
                    return;
                }
                Log.d(TAG, "start: port=" + port + " addr=" + addr.toString());
                mJmDNS = JmDNS.create(addr);
                // reset error counter
                mRegisterErrors = 0;
            }
            else {
                Log.v(TAG, "registerService: unregister prev services");
                mJmDNS.unregisterAllServices();
            }

            // Register a service
            String deviceName = AceStreamEngineBaseApplication.getDeviceName();
            if(TextUtils.isEmpty(deviceName)) {
                deviceName = "AceCast";
            }
            else {
                deviceName += " (AceCast)";
            }
            ServiceInfo serviceInfo = ServiceInfo.create("_acestreamcast._tcp.local.", deviceName, port, "version=1");
            mJmDNS.registerService(serviceInfo);
            mLastMDNSRestartAt = System.currentTimeMillis();
        }
        catch(Throwable e) {
            Log.e(TAG, "failed to init jmDNS", e);
        }
        finally {
            Log.v(TAG, "registerService: done");
        }
    }

    private void unregisterService() {
        try {
            if(mJmDNS != null) {
                mJmDNS.unregisterAllServices();
                mJmDNS.close();
                mJmDNS = null;
                mLastMDNSRestartAt = 0;
            }
        }
        catch(Throwable e) {
            Log.e(TAG, "failed to deinit jmDNS", e);
        }
    }

    private void stopServer() {
        if(mServerThread != null) {
            mServerThread.shutdown();
            mServerThread.interrupt();
        }
    }

    public void addClient(AceStreamDiscoveryServerClient client) {
        Log.d(TAG, "add client: id=" + client.getId());
        mClients.put(client.getId(), client);
        notifyConnected(client);
    }

    public void removeClient(AceStreamDiscoveryServerClient client) {
        if(mClients.containsKey(client.getId())) {
            Log.d(TAG, "remove client: id=" + client.getId());
            notifyDisconnected(client);
            mClients.remove(client.getId());
        }
    }

    public AceStreamDiscoveryServerClient getRemoteClient(String id) {
        if(mClients.containsKey(id)) {
            return mClients.get(id);
        }

        Log.d(TAG, "client not found: id=" + id);
        return null;
    }

    private void savePort(int port) {
        try {
            Log.d(TAG, "save port: port=" + port);

            SharedPreferences prefs = AceStreamEngineBaseApplication.getDiscoveryServerPreferences();
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt("port", port);
            editor.apply();
        }
        catch (Throwable e) {
            Log.e(TAG, "failed to save port", e);
        }
    }

    private int getSavedPort() {
        try {
            SharedPreferences prefs = AceStreamEngineBaseApplication.getDiscoveryServerPreferences();
            int port = prefs.getInt("port", 0);

            Log.d(TAG, "got saved port: port=" + port);

            return port;
        }
        catch (Throwable e) {
            Log.e(TAG, "failed to get saved port", e);
            return 0;
        }
    }

    class ServerThread extends Thread {

        private volatile boolean mShutdownFlag = false;
        private ServerSocket mServerSocket = null;

        @Override
        public void run() {
            Socket socket = null;
            try {
                int port = getSavedPort();

                int retries = 5;
                int interval = 1000;

                while(true) {
                    // first try saved port
                    try {
                        mServerSocket = new ServerSocket(port);
                        break;
                    } catch (IOException e) {
                        // if failed use system port
                        if(retries > 0) {
                            Log.d(TAG, "failed to use explicit port: port=" + port + " retries=" + retries + " err=" + e.getMessage());
                            retries -= 1;
                            try {
                                Thread.sleep(interval);
                            }
                            catch(InterruptedException ee) {
                                Log.d(TAG, "sleep interrupted");
                            }
                        }
                        else {
                            Log.d(TAG, "failed to use explicit port, use system: port=" + port + " err=" + e.getMessage());
                            mServerSocket = new ServerSocket(0);
                            break;
                        }
                    }
                }

                mLocalPort = mServerSocket.getLocalPort();
                savePort(mLocalPort);

                registerService(mLocalPort);
            }
            catch (IOException e) {
                Log.e(TAG, "failed to init server socket", e);
            }

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    if(mServerSocket == null) {
                        break;
                    }
                    if(mServerSocket.isClosed()) {
                        Log.d(TAG, "server socket is closed, stop thread");
                        break;
                    }
                    if(mShutdownFlag) {
                        Log.d(TAG, "got shutdown flag, stop thread");
                        break;
                    }
                    socket = mServerSocket.accept();
                    // client will register itself in the server
                    new AceStreamDiscoveryServerClient(mContext, AceStreamDiscoveryServer.this, socket);
                    mLastConnectionAt = System.currentTimeMillis();
                }
                catch(IOException e) {
                    Log.e(TAG, "error in server thread: port=" + mLocalPort + " err=" + e.getMessage());
                }
                catch(Exception e) {
                    Log.e(TAG, "exception in server thread", e);
                }
            }

            if(mServerSocket != null && !mServerSocket.isClosed()) {
                Log.d(TAG, "close server socket on thread shutdown");
                try {
                    mServerSocket.close();
                }
                catch(IOException e) {
                    Log.w(TAG, "failed to close server socket: " + e.getMessage());
                }
            }

            Log.d(TAG, "server thread stopped");
            mServerSocket = null;
            mLocalPort = 0;
        }

        void shutdown() {
            Log.d(TAG, "shutdown server thread");
            try {
                mShutdownFlag = true;
                if(mServerSocket != null) {
                    mServerSocket.close();
                }
            }
            catch(IOException e) {
                Log.w(TAG, "failed to close server socket: " + e.getMessage());
            }
        }
    }


}