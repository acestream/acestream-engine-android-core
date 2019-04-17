package org.acestream.engine.player;

import android.content.Context;
import androidx.fragment.app.DialogFragment;

public abstract class BasePlayerSettingsFragment extends DialogFragment {
    protected PlayerSettingsHandler mSettingsHandler = null;

    public BasePlayerSettingsFragment() {
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if(context instanceof PlayerSettingsHandler) {
            mSettingsHandler = (PlayerSettingsHandler) context;
        }
    }
}
