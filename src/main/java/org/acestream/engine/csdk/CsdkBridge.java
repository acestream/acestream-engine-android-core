package org.acestream.engine.csdk;

import com.connectsdk.service.capability.MediaControl;

public class CsdkBridge {
    public static MediaControl.PlayStateStatus convertStatus(int status) {
        return MediaControl.PlayStateStatus.values()[status];
    }

    public static int convertStatus(MediaControl.PlayStateStatus status) {
        return status.ordinal();
    }
}
