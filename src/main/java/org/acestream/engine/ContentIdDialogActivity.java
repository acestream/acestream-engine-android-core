package org.acestream.engine;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class ContentIdDialogActivity
    extends
        BaseActivity
    implements
        View.OnClickListener
{
    private final static String TAG = "ContentIdDialog";

    private EditText mTxtCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        setContentView(R.layout.content_id_dialog_activity);

        mTxtCode = (EditText) findViewById(R.id.txt_code);
        Button btnOk = (Button) findViewById(R.id.btn_ok);
        Button btnCancel = (Button) findViewById(R.id.btn_cancel);

        btnOk.setOnClickListener(this);
        btnCancel.setOnClickListener(this);

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
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroy () {
        super.onDestroy();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onClick(View v) {
        int i = v.getId();
        if (i == R.id.btn_cancel) {
            finish();
        } else if (i == R.id.btn_ok) {
            String contentId = mTxtCode.getText().toString().toLowerCase();
            if (contentId.startsWith("acestream://")) {
                contentId = contentId.substring(12);
            }
            Uri contentUri = null;
            Pattern p = Pattern.compile("^[0-9a-f]{40}$");
            Matcher m = p.matcher(contentId);
            if (m.find()) {
                contentUri = Uri.parse("acestream://" + contentId);
            } else {
                //TODO: uri needs validation
                contentUri = Uri.parse(contentId);
            }
            if (contentUri == null) {
                Toast.makeText(
                        AceStreamEngineBaseApplication.context(),
                        getResources().getString(R.string.bad_content_id),
                        Toast.LENGTH_SHORT
                ).show();
            }
            Intent intent = new Intent(this, ContentStartActivity.class);
            intent.setAction(Intent.ACTION_VIEW);
            intent.setData(contentUri);
            startActivity(intent);
            finish();
        }
    }


}
