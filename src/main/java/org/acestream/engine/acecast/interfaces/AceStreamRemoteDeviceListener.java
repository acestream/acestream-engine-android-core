package org.acestream.engine.acecast.interfaces;

import org.acestream.engine.acecast.client.AceStreamRemoteDevice;
import org.acestream.sdk.JsonRpcMessage;
import org.acestream.sdk.SelectedPlayer;

public interface AceStreamRemoteDeviceListener {
    void onConnected(AceStreamRemoteDevice device);
    void onDisconnected(AceStreamRemoteDevice device, boolean cleanShutdown);
    void onMessage(AceStreamRemoteDevice device, JsonRpcMessage msg);
    void onAvailable(AceStreamRemoteDevice device);
    void onUnavailable(AceStreamRemoteDevice device);
    void onPingFailed(AceStreamRemoteDevice device);
    void onOutputFormatChanged(AceStreamRemoteDevice device, String outputFormat);
    void onSelectedPlayerChanged(AceStreamRemoteDevice device, SelectedPlayer player);
}
