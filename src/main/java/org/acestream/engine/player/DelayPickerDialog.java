package org.acestream.engine.player;

import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import org.acestream.engine.R;

public abstract class DelayPickerDialog extends BasePlayerSettingsFragment {

    public final static String TAG = "AS/DelayPicker";

    private TextView mTextTitle;
    private TextView mTextValue;
    private ImageView mButtonPlus;
    private ImageView mButtonMinus;

    public DelayPickerDialog() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.ace_dialog_delay_picker, container);
        mTextTitle = view.findViewById(R.id.text_title);
        mTextValue = view.findViewById(R.id.text_value);

        mButtonPlus = view.findViewById(R.id.button_delay_plus);
        mButtonMinus = view.findViewById(R.id.button_delay_minus);

        mButtonPlus.setOnClickListener(mButtonPlusListener);
        mButtonMinus.setOnClickListener(mButtonMinusListener);

        mTextTitle.setText(getTitle());

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

    private View.OnClickListener mButtonPlusListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            increaseValue();
        }
    };

    private View.OnClickListener mButtonMinusListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            decreaseValue();
        }
    };

    protected void setValue(long value) {
        mTextValue.setText((value / 1000L) + "ms");
    }

    abstract protected int getTitle();
    abstract protected void updateControls();
    abstract protected void increaseValue();
    abstract protected void decreaseValue();
}
