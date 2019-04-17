package org.acestream.engine;

import android.content.Intent;
import android.os.Bundle;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;

public class MobileNetworksDialogActivity
    extends
        BaseActivity
    implements
        View.OnClickListener
{
    private final static String TAG = "MNDialog";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        setContentView(R.layout.mobile_networks_dialog_activity);

        Button btnYes = findViewById(R.id.btn_yes);
        Button btnNo = findViewById(R.id.btn_no);

        btnYes.setOnClickListener(this);
        btnNo.setOnClickListener(this);
    }

    private void finishDialog(boolean result) {
        Log.d(TAG, "finishDialog: result=" + result);
        finish();

        Intent intent = new Intent(Constants.ACTION_MOBILE_NETWORK_DIALOG_RESULT);
        intent.putExtra(
            Constants.MOBILE_NETWORK_DIALOG_RESULT_PARAM_ENABLED,
            result);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @Override
    public void onClick(View v) {
        int i = v.getId();
        if (i == R.id.btn_no) {
            finishDialog(false);
        } else if (i == R.id.btn_yes) {
            finishDialog(true);
        }
    }
}
