package org.acestream.engine.player;

import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.acestream.engine.R;

public abstract class PickTimeFragment
        extends BasePlayerSettingsFragment
        implements View.OnClickListener, View.OnFocusChangeListener {

    public final static String TAG = "AS/PickTimeFragment";

    public static final int ACTION_JUMP_TO_TIME = 0;
    public static final int ACTION_SLEEP_TIMER = 1;

    protected int mTextColor;

    protected static long MILLIS_IN_MICROS = 1000;
    protected static long SECONDS_IN_MICROS = 1000 * MILLIS_IN_MICROS;
    protected static long MINUTES_IN_MICROS = 60 * SECONDS_IN_MICROS;
    protected static long HOURS_IN_MICROS = 60 * MINUTES_IN_MICROS;

    protected String mHours = "", mMinutes = "", mSeconds = "", mFormatTime, mRawTime = "";
    protected int mMaxTimeSize = 6;
    protected TextView mTVTimeToJump;

    public PickTimeFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.ace_dialog_time_picker, container);
        mTVTimeToJump = (TextView) view.findViewById(R.id.tim_pic_timetojump);
        ((TextView)view.findViewById(R.id.tim_pic_title)).setText(getTitle());
        ((ImageView) view.findViewById(R.id.tim_pic_icon)).setImageResource(getIcon());

        view.findViewById(R.id.tim_pic_1).setOnClickListener(this);
        view.findViewById(R.id.tim_pic_1).setOnFocusChangeListener(this);
        view.findViewById(R.id.tim_pic_2).setOnClickListener(this);
        view.findViewById(R.id.tim_pic_2).setOnFocusChangeListener(this);
        view.findViewById(R.id.tim_pic_3).setOnClickListener(this);
        view.findViewById(R.id.tim_pic_3).setOnFocusChangeListener(this);
        view.findViewById(R.id.tim_pic_4).setOnClickListener(this);
        view.findViewById(R.id.tim_pic_4).setOnFocusChangeListener(this);
        view.findViewById(R.id.tim_pic_5).setOnClickListener(this);
        view.findViewById(R.id.tim_pic_5).setOnFocusChangeListener(this);
        view.findViewById(R.id.tim_pic_6).setOnClickListener(this);
        view.findViewById(R.id.tim_pic_6).setOnFocusChangeListener(this);
        view.findViewById(R.id.tim_pic_7).setOnClickListener(this);
        view.findViewById(R.id.tim_pic_7).setOnFocusChangeListener(this);
        view.findViewById(R.id.tim_pic_8).setOnClickListener(this);
        view.findViewById(R.id.tim_pic_8).setOnFocusChangeListener(this);
        view.findViewById(R.id.tim_pic_9).setOnClickListener(this);
        view.findViewById(R.id.tim_pic_9).setOnFocusChangeListener(this);
        view.findViewById(R.id.tim_pic_0).setOnClickListener(this);
        view.findViewById(R.id.tim_pic_0).setOnFocusChangeListener(this);
        view.findViewById(R.id.tim_pic_00).setOnClickListener(this);
        view.findViewById(R.id.tim_pic_00).setOnFocusChangeListener(this);
        view.findViewById(R.id.tim_pic_30).setOnClickListener(this);
        view.findViewById(R.id.tim_pic_30).setOnFocusChangeListener(this);
        view.findViewById(R.id.tim_pic_cancel).setOnClickListener(this);
        view.findViewById(R.id.tim_pic_cancel).setOnFocusChangeListener(this);
        view.findViewById(R.id.tim_pic_delete).setOnClickListener(this);
        view.findViewById(R.id.tim_pic_delete).setOnFocusChangeListener(this);
        view.findViewById(R.id.tim_pic_ok).setOnClickListener(this);
        view.findViewById(R.id.tim_pic_ok).setOnFocusChangeListener(this);

        mTextColor = mTVTimeToJump.getCurrentTextColor();

        getDialog().setCancelable(true);
        getDialog().setCanceledOnTouchOutside(true);
        if (getDialog() != null) {
            int dialogWidth = getResources().getDimensionPixelSize(R.dimen.dialog_time_picker_width);
            int dialogHeight = ViewGroup.LayoutParams.WRAP_CONTENT;
            getDialog().getWindow().setLayout(dialogWidth, dialogHeight);
            getDialog().getWindow().setBackgroundDrawable(new ColorDrawable(
                    getResources().getColor(R.color.blacktransparent)));
        }
        return view;
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        ((TextView)v).setTextColor(hasFocus ? getResources().getColor(R.color.controls_dark) : mTextColor);
    }

    @Override
    public void onClick(View v) {
        int i = v.getId();
        if (i == R.id.tim_pic_1) {
            updateValue("1");

        } else if (i == R.id.tim_pic_2) {
            updateValue("2");

        } else if (i == R.id.tim_pic_3) {
            updateValue("3");

        } else if (i == R.id.tim_pic_4) {
            updateValue("4");

        } else if (i == R.id.tim_pic_5) {
            updateValue("5");

        } else if (i == R.id.tim_pic_6) {
            updateValue("6");

        } else if (i == R.id.tim_pic_7) {
            updateValue("7");

        } else if (i == R.id.tim_pic_8) {
            updateValue("8");

        } else if (i == R.id.tim_pic_9) {
            updateValue("9");

        } else if (i == R.id.tim_pic_0) {
            updateValue("0");

        } else if (i == R.id.tim_pic_00) {
            updateValue("00");

        } else if (i == R.id.tim_pic_30) {
            updateValue("30");

        } else if (i == R.id.tim_pic_cancel) {
            dismiss();

        } else if (i == R.id.tim_pic_delete) {
            deleteLastNumber();

        } else if (i == R.id.tim_pic_ok) {
            executeAction();

        }
    }

    private String getLastNumbers(String rawTime){
        if (rawTime.length() == 0)
            return "";
        return (rawTime.length() == 1) ?
                rawTime:
                rawTime.substring(rawTime.length()-2);
    }

    private String removeLastNumbers(String rawTime){
        return rawTime.length() <= 1 ? "" : rawTime.substring(0, rawTime.length()-2);
    }

    private void deleteLastNumber(){
        if (mRawTime != "") {
            mRawTime = mRawTime.substring(0, mRawTime.length()-1);
            updateValue("");
        }
    }

    private void updateValue(String value) {
        if (mRawTime.length() >= mMaxTimeSize)
            return;
        mRawTime = mRawTime.concat(value);
        String tempRawTime = mRawTime;
        mFormatTime = "";

        if (mMaxTimeSize > 4) {
            mSeconds = getLastNumbers(tempRawTime);
            if (mSeconds != "")
                mFormatTime = mSeconds + "s";
            tempRawTime = removeLastNumbers(tempRawTime);
        } else
            mSeconds = "";

        mMinutes = getLastNumbers(tempRawTime);
        if (mMinutes != "")
            mFormatTime = mMinutes + "m " + mFormatTime;
        tempRawTime = removeLastNumbers(tempRawTime);

        mHours = getLastNumbers(tempRawTime);
        if (mHours != "")
            mFormatTime = mHours + "h " + mFormatTime;

        mTVTimeToJump.setText(mFormatTime);
    }

    abstract protected int getTitle();
    abstract protected int getIcon();
    abstract protected void executeAction();
}
