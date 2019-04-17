package org.acestream.engine.player;

import org.acestream.engine.R;

public class SubtitleDelayPickerDialog extends DelayPickerDialog {
    @Override
    protected int getTitle() {
        return R.string.spu_delay;
    }

    @Override
    protected void updateControls() {
        setValue(mSettingsHandler.getSubtitleDelay());
    }

    @Override
    protected void increaseValue() {
        mSettingsHandler.setSubtitleDelay(mSettingsHandler.getSubtitleDelay() + 50000);
        updateControls();
    }

    @Override
    protected void decreaseValue() {
        mSettingsHandler.setSubtitleDelay(mSettingsHandler.getSubtitleDelay() - 50000);
        updateControls();
    }
}
