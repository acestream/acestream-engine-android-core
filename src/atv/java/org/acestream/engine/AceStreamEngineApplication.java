package org.acestream.engine;

import android.content.Context;
import android.util.Log;

import org.acestream.engine.controller.EngineFactoryImpl;
import org.acestream.engine.interfaces.ChannelsSyncListener;
import org.acestream.livechannels.model.AppContext;

import androidx.annotation.NonNull;

public class AceStreamEngineApplication extends AceStreamEngineBaseApplication implements ChannelsSyncListener {
    public static void initialize(Context context) {
        if(sInstance == null) {
            sInstance = new AceStreamEngineApplication(context);
        }
    }

    protected AceStreamEngineApplication(@NonNull final Context context) {
        super(context);

        mEngineFactory = new EngineFactoryImpl(context());
        AppContext.setEngineFactory(mEngineFactory);
        AceStreamEngineBaseApplication.setChannelsSyncListener(this);
    }

    @Override
    public void onChannelsSync() {
        Log.d(TAG, "requestChannelsSync");
        org.acestream.livechannels.sync.SyncUtils.requestChannelsSync(context(), org.acestream.livechannels.utils.TVInputUtils.getInputId(context(), null), false);
    }

    @Override
    public void onEPGSync() {
        Log.d(TAG, "requestEPGSync");

        // sync EPG
        org.acestream.livechannels.sync.SyncUtils.requestEPGSync(context(), org.acestream.livechannels.utils.TVInputUtils.getInputId(context(), null));

        // sync channels (not forced) because icons may change when EPG is updated
        org.acestream.livechannels.sync.SyncUtils.requestChannelsSync(context(), org.acestream.livechannels.utils.TVInputUtils.getInputId(context(), null), false);
    }
}
