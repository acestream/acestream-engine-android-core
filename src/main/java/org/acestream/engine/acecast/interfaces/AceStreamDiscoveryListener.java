package org.acestream.engine.acecast.interfaces;

import org.acestream.engine.acecast.client.AceStreamRemoteDevice;

public interface AceStreamDiscoveryListener {
    void onDeviceAdded(AceStreamRemoteDevice device);
    void onDeviceRemoved(AceStreamRemoteDevice device);
}
