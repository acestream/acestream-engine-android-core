package org.acestream.engine.acecast.interfaces;

import org.acestream.engine.acecast.server.AceStreamDiscoveryServerClient;
import org.acestream.sdk.JsonRpcMessage;

public interface AceStreamRemoteClientListener {
    void onMessage(AceStreamDiscoveryServerClient client, JsonRpcMessage msg);
    void onDisconnected(AceStreamDiscoveryServerClient client);
}
