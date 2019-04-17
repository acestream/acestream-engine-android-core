package org.acestream.engine.player;

import org.acestream.engine.R;

public class AudioDelayPickerDialog extends DelayPickerDialog {
    @Override
    protected int getTitle() {
        return R.string.audio_delay;
    }

    @Override
    protected void updateControls() {
        setValue(mSettingsHandler.getAudioDelay());
    }

    @Override
    protected void increaseValue() {
        mSettingsHandler.setAudioDelay(mSettingsHandler.getAudioDelay() + 50000);
        updateControls();
    }

    @Override
    protected void decreaseValue() {
        mSettingsHandler.setAudioDelay(mSettingsHandler.getAudioDelay() - 50000);
        updateControls();
    }
}
