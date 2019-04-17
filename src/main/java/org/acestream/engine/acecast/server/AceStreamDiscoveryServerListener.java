package org.acestream.engine.acecast.server;

public interface AceStreamDiscoveryServerListener {
    void onConnected(AceStreamDiscoveryServerClient client);
    void onDisconnected(AceStreamDiscoveryServerClient client);
}
