package org.acestream.engine.player;

import org.acestream.engine.R;

public class JumpToTimeDialog extends PickTimeFragment {

    public JumpToTimeDialog(){
        super();
    }

    public static JumpToTimeDialog newInstance() {
        return new JumpToTimeDialog();
    }

    protected void executeAction() {
        long hours = !mHours.equals("") ? Long.parseLong(mHours) * HOURS_IN_MICROS : 0l;
        long minutes = !mMinutes.equals("") ? Long.parseLong(mMinutes) * MINUTES_IN_MICROS : 0l;
        long seconds = !mSeconds.equals("") ? Long.parseLong(mSeconds) * SECONDS_IN_MICROS : 0l;
        mSettingsHandler.seekToTime((hours +  minutes + seconds)/1000l); //Time in ms
        dismiss();
    }

    @Override
    protected int getTitle() {
        return R.string.jump_to_time;
    }

    protected int getIcon() {
        return R.drawable.ic_jumpto_dark;
    }
}
