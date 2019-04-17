package org.acestream.engine.controller;

import android.content.Context;

import org.acestream.engine.controller.Engine;

public class EngineFactoryImpl implements Engine.Factory {
    private Context mContext;

    public EngineFactoryImpl(Context context) {
        mContext = context;
    }

    @Override
    public Engine getInstance() {
        return new EngineImpl(mContext);
    }
}
