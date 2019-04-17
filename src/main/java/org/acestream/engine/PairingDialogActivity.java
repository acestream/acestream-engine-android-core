package org.acestream.engine;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class PairingDialogActivity
    extends
        PlaybackManagerFragmentActivity
    implements
        View.OnClickListener
{
    private final static String TAG = "PairingDialog";

    private boolean mActive = false;
    private boolean mOkClicked = false;

    private EditText mTxtCode;
    private TextView mLblMessage;
    private Button mBtnCancel;
    private Button mBtnOk;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        setContentView(R.layout.pairing_dialog_activity);

        mLblMessage = (TextView) findViewById(R.id.lbl_message);
        mTxtCode = (EditText) findViewById(R.id.txt_code);
        mBtnOk = (Button) findViewById(R.id.btn_ok);
        mBtnCancel = (Button) findViewById(R.id.btn_cancel);

        mBtnOk.setOnClickListener(this);
        mBtnCancel.setOnClickListener(this);

        int type = getIntent().getIntExtra("type", 0);
        if(type == 0) {
            mLblMessage.setText("Please confirm the connection on your TV");
            mTxtCode.setVisibility(View.GONE);
        }
        else {
            mLblMessage.setText("Enter Pairing Code");
            mTxtCode.setVisibility(View.VISIBLE);
        }

        try {
            // show keyboard
            mTxtCode.requestFocus();
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        catch(Throwable e) {
            // pass
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: hash=" + hashCode());
        mActive = true;
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause: hash=" + hashCode());
        mActive = false;
    }

    @Override
    public void onDestroy () {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
    }

    @Override
    public void onStop() {
        if(!mOkClicked && mPlaybackManager != null) {
            mPlaybackManager.cancelPairing();
        }

        super.onStop();
        Log.d(TAG, "onStop");
    }

    @Override
    public void onClick(View v) {
        int i = v.getId();
        if (i == R.id.btn_cancel) {
            finish();
        } else if (i == R.id.btn_ok) {
            mOkClicked = true;
            if (mPlaybackManager != null) {
                mPlaybackManager.sendPairingCode(mTxtCode.getText().toString());
            }
            finish();
        }
    }
}
