package org.acestream.engine.acecast.client;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.util.Log;

import com.connectsdk.core.Util;

import org.acestream.engine.acecast.interfaces.AceStreamDiscoveryListener;
import org.acestream.engine.acecast.interfaces.AceStreamRemoteDeviceListener;
import org.acestream.sdk.AceStream;
import org.acestream.sdk.JsonRpcMessage;
import org.acestream.sdk.SelectedPlayer;
import org.acestream.sdk.utils.Logger;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceListener;

public class AceStreamDiscoveryClient implements AceStreamRemoteDeviceListener {
    private final static String TAG = "AceStream/DC";
    
    private Context mContext;
    private JmDNS mJmDNS;
    private Handler mHandler;
    private HandlerThread mHandlerThread;
    private WifiManager.MulticastLock mMulticastLock;
    private Set<AceStreamDiscoveryListener> mDiscoveryListeners;
    private Map<String, AceStreamRemoteDevice> mKnownDevices;
    private Map<String, AceStreamRemoteDevice> mAvailableDevices;

    public AceStreamDiscoveryClient(Context context) {
        mContext = context;
        mDiscoveryListeners = new CopyOnWriteArraySet<>();
        mKnownDevices = new ConcurrentHashMap<>();
        mAvailableDevices = new ConcurrentHashMap<>();
    }

    public void init() {
        Log.v(TAG, "init");
        mHandlerThread = new HandlerThread(getClass().getSimpleName());
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());

        WifiManager wifiManager = (WifiManager)mContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if(wifiManager != null) {
            mMulticastLock = wifiManager.createMulticastLock(TAG);
            mMulticastLock.setReferenceCounted(true);
            mMulticastLock.acquire();
        }
    }

    public void start() {
        Log.d(TAG, "start");

        if(mHandler == null) {
            init();
        }

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                startListener();
            }
        });
    }

    public void shutdown() {
        Log.d(TAG, "shutdown");

        if(mHandler != null) {
            // Stop in handler thread because it blocks for a while.
            final HandlerThread handlerThread = mHandlerThread;
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    stopListener();
                    if (handlerThread != null) {
                        handlerThread.quit();
                    }
                }
            });
        }

        // release multicast lock
        if(mMulticastLock != null && mMulticastLock.isHeld()) {
            mMulticastLock.release();
        }

        if(mHandlerThread != null) {
            mHandlerThread = null;
            mHandler = null;
        }
    }

    public void destroy() {
        Log.d(TAG, "destroy");

        for(AceStreamRemoteDevice device: mKnownDevices.values()) {
            device.removeListener(this);
            device.destroy();
        }
    }

    public void reset() {
        Log.v(TAG, "reset");
        for(AceStreamRemoteDevice device: mAvailableDevices.values()) {
            onPingFailed(device);
        }
    }

    public Map<String, AceStreamRemoteDevice> getKnownDevices() {
        return mKnownDevices;
    }

    public Map<String, AceStreamRemoteDevice> getAvailableDevices() {
        return mAvailableDevices;
    }

    public AceStreamRemoteDevice findDeviceById(String id) {
        return mAvailableDevices.get(id);
    }

    public void addDeviceDiscoveryListener(AceStreamDiscoveryListener listener) {
        mDiscoveryListeners.add(listener);
    }

    public void removeDeviceDiscoveryListener(AceStreamDiscoveryListener listener) {
        mDiscoveryListeners.remove(listener);
    }

    private void notifyDeviceAdded(AceStreamRemoteDevice device) {
        Logger.v(TAG, "notifyDeviceAdded: listeners=" + mDiscoveryListeners.size() + " device=" + device);
        for(AceStreamDiscoveryListener listener: mDiscoveryListeners) {
            listener.onDeviceAdded(device);
        }
    }

    private void notifyDeviceRemoved(AceStreamRemoteDevice device) {
        Logger.v(TAG, "notifyDeviceRemoved: listeners=" + mDiscoveryListeners.size() + " device=" + device);
        for(AceStreamDiscoveryListener listener: mDiscoveryListeners) {
            listener.onDeviceRemoved(device);
        }
    }

    private void startListener() {
        try {
            long t;

            if(mJmDNS == null) {
                InetAddress addr = Util.getIpAddress(AceStream.context());
                if(addr == null) {
                    Log.d(TAG, "cannot start listener: no addr");
                    return;
                }

                t = new Date().getTime();
                mJmDNS = JmDNS.create(addr);
                Log.d(TAG, "start new listener: time=" + (new Date().getTime() - t) + " addr=" + addr.toString());
            }
            else {
                Log.d(TAG, "start existing listener");
            }

            mJmDNS.removeServiceListener("_acestreamcast._tcp.local.", mServiceListener);
            mJmDNS.addServiceListener("_acestreamcast._tcp.local.", mServiceListener);
        }
        catch(Throwable e) {
            Log.e(TAG, "failed to start listener", e);
        }
    }

    private void stopListener() {
        try {
            Log.d(TAG, "stopListener");

            if(mJmDNS != null) {
                mJmDNS.removeServiceListener("_acestreamcast._tcp.local.", mServiceListener);
                mJmDNS.close();
                mJmDNS = null;
            }
        }
        catch(Throwable e) {
            Log.e(TAG, "failed to stop listener", e);
        }
    }

    @Override
    public void onConnected(AceStreamRemoteDevice device) {
    }

    @Override
    public void onDisconnected(AceStreamRemoteDevice device, boolean cleanShutdown) {
        Log.d(TAG, "device disconnected: clean=" + cleanShutdown + " device=" + device.toString());
    }

    @Override
    public void onMessage(AceStreamRemoteDevice device, JsonRpcMessage msg) {
    }

    @Override
    public void onAvailable(AceStreamRemoteDevice device) {
        boolean known = mKnownDevices.containsKey(device.getId());
        boolean available = mAvailableDevices.containsKey(device.getId());
        Log.d(TAG, "device available: known=" + known + " available=" + available + " device=" + device.toString());

        // Check for duplicates by device id.
        // Cannot do this until this moment because we got device id only after ping.
        for(AceStreamRemoteDevice _device: mKnownDevices.values()) {
            if(!TextUtils.equals(device.getId(), _device.getId()) && TextUtils.equals(device.getDeviceId(), _device.getDeviceId())) {
                Log.d(TAG, "onAvailable: got duplicate by id: id=" + device.getDeviceId());
                return;
            }
        }

        if(!known) {
            mKnownDevices.put(device.getId(), device);
        }
        if(!available) {
            mAvailableDevices.put(device.getId(), device);
            notifyDeviceAdded(device);
        }
    }

    @Override
    public void onUnavailable(AceStreamRemoteDevice device) {
        boolean known = mKnownDevices.containsKey(device.getId());
        boolean available = mAvailableDevices.containsKey(device.getId());
        Logger.v(TAG, "onUnavailable: device unavailable: known=" + known + " available=" + available + " device=" + device.toString());
        if(known) {
            mKnownDevices.remove(device.getId());
        }
        if(available) {
            mAvailableDevices.remove(device.getId());
            notifyDeviceRemoved(device);
        }
    }

    @Override
    public void onPingFailed(AceStreamRemoteDevice device) {
        // remove from available but not from known
        boolean known = mKnownDevices.containsKey(device.getId());
        boolean available = mAvailableDevices.containsKey(device.getId());
        Logger.v(TAG, "onPingFailed: device unavailable: known=" + known + " available=" + available + " device=" + device.toString());
        if(available) {
            mAvailableDevices.remove(device.getId());
            notifyDeviceRemoved(device);
        }
    }

    @Override
    public void onOutputFormatChanged(AceStreamRemoteDevice device, String outputFormat) {
    }

    @Override
    public void onSelectedPlayerChanged(AceStreamRemoteDevice device, SelectedPlayer player) {
    }

    private ServiceListener mServiceListener = new ServiceListener() {
        @Override
        public void serviceAdded(ServiceEvent event) {
            Log.d(TAG, "Service added: type=" + event.getType() + " name=" + event.getName());
            if(mJmDNS != null) {
                // Required to force serviceResolved to be called again (after the first search)
                mJmDNS.requestServiceInfo(event.getType(), event.getName(), 1);
            }
        }

        @Override
        public void serviceRemoved(ServiceEvent event) {
            //TODO: notify listeners
            Log.d(TAG, "Service removed: event=" + event.getInfo());
        }

        @Override
        public void serviceResolved(ServiceEvent event) {
            String ipAddress = event.getInfo().getHostAddress();
            if (!Util.isIPv4Address(ipAddress)) {
                // Currently, we only support ipv4
                return;
            }

            try {
                InetAddress addr = Util.getIpAddress(AceStream.context());
                if(addr != null && ipAddress.equals(addr.getHostAddress())) {
                    Log.d(TAG, "skip connection to myself");
                    return;
                }
            }
            catch(UnknownHostException e) {
                Log.e(TAG, "failed to get my ip");
            }

            String friendlyName = event.getInfo().getName();
            String qualifiedName = event.getInfo().getQualifiedName();
            int port = event.getInfo().getPort();

            final AceStreamRemoteDevice device = new AceStreamRemoteDevice(ipAddress, port, qualifiedName);
            device.setName(friendlyName);

            Log.v(TAG, "serviceResolved: ip=" + ipAddress + " port=" + port + " name=" + friendlyName + " id=" + device.getId());

            AceStreamRemoteDevice existingDevice = mKnownDevices.get(device.getId());
            if(existingDevice != null) {
                Log.d(TAG, "serviceResolved: device already exists: id=" + device.getId());
                existingDevice.startPing();
            }
            else {
                // check duplicates by ip:port
                boolean addDevice = true;
                for(AceStreamRemoteDevice _device: mKnownDevices.values()) {
                    if(device.equals(_device)) {
                        Log.d(TAG, "serviceResolved: got duplicate by ip/port: ip=" + ipAddress + " port=" + port);
                        addDevice = false;
                        break;
                    }
                }

                if(addDevice) {
                    // Add new device to "known devices".
                    // Device will be added to "available devices" after ping success.
                    Log.d(TAG, "serviceResolved: add new device: id=" + device.getId());
                    mKnownDevices.put(device.getId(), device);
                    device.addListener(AceStreamDiscoveryClient.this);
                    device.startPing();
                }
            }
        }
    };
}