package org.acestream.engine.receivers;

import android.content.Context;
import android.content.Intent;

import org.acestream.engine.AceStreamEngineBaseApplication;
import org.acestream.engine.BaseReceiver;
import org.acestream.sdk.AceStream;

public class DispatcherReceiver extends BaseReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        if(intent == null) return;
        if(intent.getAction() == null) return;

        switch(intent.getAction()) {
            case AceStream.ACTION_OPEN_TOPUP_ACTIVITY:
                AceStreamEngineBaseApplication.showTopupForm(context);
                break;
            case AceStream.ACTION_OPEN_UPGRADE_ACTIVITY:
                AceStreamEngineBaseApplication.showUpgradeForm(context);
                break;
        }
    }
}
