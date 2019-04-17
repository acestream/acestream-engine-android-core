package org.acestream.engine.acecast.interfaces;

import com.connectsdk.device.ConnectableDevice;

import org.acestream.engine.acecast.client.AceStreamRemoteDevice;

public interface DeviceDiscoveryListener {
    void onDeviceAdded(ConnectableDevice device);
    void onDeviceRemoved(ConnectableDevice device);
    void onDeviceAdded(AceStreamRemoteDevice device);
    void onDeviceRemoved(AceStreamRemoteDevice device);
    void onCurrentDeviceChanged(AceStreamRemoteDevice device);
    boolean canStopDiscovery();
}
