package org.acestream.engine.player;


import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import androidx.annotation.NonNull;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;

import org.acestream.engine.R;

public class PlayerOptionsFragment extends BasePlayerSettingsFragment
        implements View.OnClickListener {

    private static final String TAG = "AS/PlayerOptions";

    private ImageView mButtonSleep;
    private TextView mTextSleep;
    private ImageView mButtonSpeed;
    private TextView mTextSpeed;
    private ImageView mButtonSubtitleDelay;
    private TextView mTextSubtitleDelay;
    private ImageView mButtonAudioDelay;
    private TextView mTextAudioDelay;
    private ImageView mButtonJumpTo;
    private ImageView mButtonRepeat;
    private ImageView mButtonShuffle;

    public PlayerOptionsFragment() {

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        Window window = getDialog().getWindow();
        if(window != null) {
            window.setBackgroundDrawable(new ColorDrawable(
                    getResources().getColor(R.color.blacktransparent)));
        }
        View v = inflater.inflate(R.layout.ace_fragment_player_options, container, false);

        mButtonSleep = v.findViewById(R.id.button_sleep);
        mTextSleep = v.findViewById(R.id.text_sleep);
        mButtonSpeed = v.findViewById(R.id.button_speed);
        mTextSpeed = v.findViewById(R.id.text_speed);
        mButtonSubtitleDelay = v.findViewById(R.id.button_subtitledelay);
        mTextSubtitleDelay = v.findViewById(R.id.text_subtitledelay);
        mButtonAudioDelay = v.findViewById(R.id.button_audiodelay);
        mTextAudioDelay = v.findViewById(R.id.text_audiodelay);
        mButtonJumpTo = v.findViewById(R.id.button_jumpto);
        mButtonRepeat = v.findViewById(R.id.button_repeat);
        mButtonShuffle = v.findViewById(R.id.button_shuffle);

        mButtonSleep.setOnClickListener(this);
        mButtonSpeed.setOnClickListener(this);
        mButtonSubtitleDelay.setOnClickListener(this);
        mButtonAudioDelay.setOnClickListener(this);
        mButtonJumpTo.setOnClickListener(this);
        mButtonRepeat.setOnClickListener(this);
        mButtonShuffle.setOnClickListener(this);

        updateControls();

        return v;
    }

    private void updateControls() {
        //TODO: init sleep time

        // Playback speed
        if(mSettingsHandler.getRate() != 1.0) {
            mButtonSpeed.setImageResource(R.drawable.ic_speed_on_dark);
            mTextSpeed.setText(Utils.formatRateString(mSettingsHandler.getRate()));
            mTextSpeed.setVisibility(View.VISIBLE);
        }
        else {
            mButtonSpeed.setImageResource(R.drawable.ic_speed_dark);
            mTextSpeed.setText("");
            mTextSpeed.setVisibility(View.INVISIBLE);
        }

        // Subtitle delay
        if(mSettingsHandler.getSubtitleDelay() != 0) {
            mButtonSubtitleDelay.setImageResource(R.drawable.ic_subtitledelay_on_dark);
            mTextSubtitleDelay.setText((mSettingsHandler.getSubtitleDelay() / 1000L) + "ms");
            mTextSubtitleDelay.setVisibility(View.VISIBLE);
        }
        else {
            mButtonSubtitleDelay.setImageResource(R.drawable.ic_subtitledelay_dark);
            mTextSubtitleDelay.setText("");
            mTextSubtitleDelay.setVisibility(View.INVISIBLE);
        }

        // Audio delay
        if(mSettingsHandler.getAudioDelay() != 0) {
            mButtonAudioDelay.setImageResource(R.drawable.ic_audiodelay_on_dark);
            mTextAudioDelay.setText((mSettingsHandler.getAudioDelay() / 1000L) + "ms");
            mTextAudioDelay.setVisibility(View.VISIBLE);
        }
        else {
            mButtonAudioDelay.setImageResource(R.drawable.ic_audiodelay_dark);
            mTextAudioDelay.setText("");
            mTextAudioDelay.setVisibility(View.INVISIBLE);
        }

        updateRepeatType();
        updateShuffle();
    }

    private void updateRepeatType() {
        if(mSettingsHandler.getRepeatType() == PlaylistManager.REPEAT_ONE) {
            mButtonRepeat.setImageResource(R.drawable.ic_repeat_one_dark);
        }
        else if(mSettingsHandler.getRepeatType() == PlaylistManager.REPEAT_ALL) {
            mButtonRepeat.setImageResource(R.drawable.ic_repeat_all_dark);
        }
        else {
            mButtonRepeat.setImageResource(R.drawable.ic_repeat_dark);
        }
    }

    private void updateShuffle() {
        if(mSettingsHandler.hasPlaylist()) {
            if (mSettingsHandler.getShuffle()) {
                mButtonShuffle.setImageResource(R.drawable.ic_shuffle_on_dark);
            } else {
                mButtonShuffle.setImageResource(R.drawable.ic_shuffle_dark);
            }
            mButtonShuffle.setVisibility(View.VISIBLE);
        }
        else {
            mButtonShuffle.setVisibility(View.GONE);
        }
    }

    @Override
    public void onClick(View view) {
        int i = view.getId();
        if (i == R.id.button_sleep) {
            showSleepTimerDialog();

        } else if (i == R.id.button_speed) {
            showPlaybackSpeedDialog();

        } else if (i == R.id.button_subtitledelay) {
            showSubtitleDelayDialog();

        } else if (i == R.id.button_audiodelay) {
            showAudioDelayDialog();

        } else if (i == R.id.button_jumpto) {
            showJumpToTimeDialog();

        } else if (i == R.id.button_repeat) {
            mSettingsHandler.toggleRepeatType();
            updateRepeatType();

        } else if (i == R.id.button_shuffle) {
            mSettingsHandler.toggleShuffle();
            updateShuffle();
        }
    }

    private void showSleepTimerDialog() {
        SleepTimerDialog dialog = new SleepTimerDialog();
        dialog.show(getActivity().getSupportFragmentManager(), "sleep_timer_dialog");
        dismiss();
    }

    private void showJumpToTimeDialog() {
        JumpToTimeDialog dialog = new JumpToTimeDialog();
        dialog.show(getActivity().getSupportFragmentManager(), "jump_to_time_dialog");
        dismiss();
    }

    private void showPlaybackSpeedDialog() {
        PlaybackSpeedDialog dialog = new PlaybackSpeedDialog();
        dialog.show(getActivity().getSupportFragmentManager(), "playback_dialog");
        dismiss();
    }

    private void showSubtitleDelayDialog() {
        SubtitleDelayPickerDialog dialog = new SubtitleDelayPickerDialog();
        dialog.show(getActivity().getSupportFragmentManager(), "subtitle_delay_dialog");
        dismiss();
    }

    private void showAudioDelayDialog() {
        AudioDelayPickerDialog dialog = new AudioDelayPickerDialog();
        dialog.show(getActivity().getSupportFragmentManager(), "audio_delay_dialog");
        dismiss();
    }
}
