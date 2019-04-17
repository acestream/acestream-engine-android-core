package org.acestream.engine.player;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.acestream.engine.R;

import java.util.Calendar;

public class SleepTimerDialog extends PickTimeFragment {

    protected static long ONE_DAY_IN_MILLIS = 24 * 60 * 60 * 1000;

    public SleepTimerDialog() {
        super();
    }

    public static SleepTimerDialog newInstance() {
        return new SleepTimerDialog();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        mMaxTimeSize = 4;
        return view;
    }

    protected void executeAction() {
        long hours = !mHours.equals("") ? Long.parseLong(mHours) * HOURS_IN_MICROS : 0l;
        long minutes = !mMinutes.equals("") ? Long.parseLong(mMinutes) * MINUTES_IN_MICROS : 0l;
        long interval = (hours + minutes) / MILLIS_IN_MICROS; //Interval in ms

        if (interval < ONE_DAY_IN_MILLIS) {
            Calendar sleepTime = Calendar.getInstance();
            sleepTime.setTimeInMillis(sleepTime.getTimeInMillis() + interval);
            sleepTime.set(Calendar.SECOND, 0);
            if(mSettingsHandler != null) {
                mSettingsHandler.setSleepTime(sleepTime);
            }
        }

        dismiss();
    }

    @Override
    protected int getTitle() {
        return R.string.sleep_in;
    }

    @Override
    protected int getIcon() {
        return R.drawable.ic_sleep_dark;
    }
}