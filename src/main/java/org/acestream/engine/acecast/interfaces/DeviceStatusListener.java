package org.acestream.engine.acecast.interfaces;

import com.connectsdk.device.ConnectableDevice;

public interface DeviceStatusListener {
    void onDeviceConnected(ConnectableDevice device);
    void onDeviceDisconnected(ConnectableDevice device, boolean cleanShutdown);
}
