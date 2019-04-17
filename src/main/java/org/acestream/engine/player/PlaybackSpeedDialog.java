package org.acestream.engine.player;

import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import org.acestream.engine.R;

public class PlaybackSpeedDialog extends BasePlayerSettingsFragment {

    public final static String TAG = "AS/PlaybackSpeedDialog";

    private TextView mSpeedValue;
    private SeekBar mSeekSpeed;
    private ImageView mPlaybackSpeedIcon;
    private ImageView mPlaybackSpeedPlus;
    private ImageView mPlaybackSpeedMinus;

    protected int mTextColor;

    public PlaybackSpeedDialog() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.ace_dialog_playback_speed, container);
        mSpeedValue = view.findViewById(R.id.playback_speed_value);
        mSeekSpeed = view.findViewById(R.id.playback_speed_seek);
        mPlaybackSpeedIcon = view.findViewById(R.id.playback_speed_icon);
        mPlaybackSpeedPlus = view.findViewById(R.id.playback_speed_plus);
        mPlaybackSpeedMinus = view.findViewById(R.id.playback_speed_minus);

        mSeekSpeed.setOnSeekBarChangeListener(mSeekBarListener);
        mPlaybackSpeedIcon.setOnClickListener(mResetListener);
        mPlaybackSpeedPlus.setOnClickListener(mSpeedUpListener);
        mPlaybackSpeedMinus.setOnClickListener(mSpeedDownListener);
        mSpeedValue.setOnClickListener(mResetListener);

        mTextColor = mSpeedValue.getCurrentTextColor();

        getDialog().setCancelable(true);
        getDialog().setCanceledOnTouchOutside(true);
        Window window = getDialog().getWindow();
        if(window != null) {
            window.setBackgroundDrawable(new ColorDrawable(
                    getResources().getColor(R.color.blacktransparent)));
            window.setLayout(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);
        }

        updateControls();

        return view;
    }

    private void setRateProgress() {
        double speed = mSettingsHandler.getRate();
        speed = 100 * (1 + Math.log(speed) / Math.log(4));
        mSeekSpeed.setProgress((int) speed);
        updateControls();
    }

    private SeekBar.OnSeekBarChangeListener mSeekBarListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser) {
                float rate = (float) Math.pow(4, ((double) progress / (double) 100) - 1);
                mSettingsHandler.setRate(rate);
                updateControls();
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {}

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {}
    };

    private View.OnClickListener mResetListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mSettingsHandler.getRate() == 1.0f)
                return;

            mSettingsHandler.setRate(1.0f);
            setRateProgress();
        }
    };

    private View.OnClickListener mSpeedUpListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            changeSpeed(0.05f);
            setRateProgress();
        }
    };

    private View.OnClickListener mSpeedDownListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            changeSpeed(-0.05f);
            setRateProgress();
        }
    };

    public void changeSpeed(float delta){
        double initialRate = Math.round(mSettingsHandler.getRate() * 100d) / 100d;
        if (delta>0)
            initialRate = Math.floor((initialRate + 0.005d) / 0.05d) * 0.05d;
        else
            initialRate = Math.ceil((initialRate - 0.005d) / 0.05d) * 0.05d;
        float rate = Math.round((initialRate + delta) * 100f) / 100f;
        if (rate < 0.25f || rate > 4f)
            return;
        mSettingsHandler.setRate(rate);
    }

    protected void updateControls() {
        float rate = mSettingsHandler.getRate();
        mSpeedValue.setText(Utils.formatRateString(rate));
        if (rate != 1.0f) {
            mPlaybackSpeedIcon.setImageResource(R.drawable.ic_speed_reset_dark);
            mSpeedValue.setTextColor(getResources().getColor(R.color.controls_dark));
        } else {
            mPlaybackSpeedIcon.setImageResource(R.drawable.ic_speed_dark);
            mSpeedValue.setTextColor(mTextColor);
        }
    }
}
