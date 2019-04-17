package org.acestream.engine.notification;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import org.acestream.engine.AceStreamEngineBaseApplication;
import org.acestream.engine.Constants;
import org.acestream.engine.PlaybackManagerAppCompatActivity;
import org.acestream.engine.R;
import org.acestream.sdk.AceStream;

public class LinkActivity
    extends PlaybackManagerAppCompatActivity
    implements OnClickListener {

	protected final static String TAG = "AceStream/Link";
	private boolean mInitialized = false;
	protected String mTargetUrl = null;
	private Button mButtonOk;
	private View mProgressView;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.link_activity);

		mButtonOk = findViewById(R.id.button_ok);
		mProgressView = findViewById(R.id.progress);

		mButtonOk.setOnClickListener(this);
	}

	@Override
    public void onClick(View v) {
		int i = v.getId();
		if (i == R.id.button_ok) {
			Log.v(TAG, "button_ok clicked");
			finish();

		}
    }

    @Override
	public void onResumeConnected() {
		super.onResumeConnected();

		if(!mInitialized) {
			updateUI();
			mInitialized = true;
		}
	}

	protected void updateUI() {
		mTargetUrl = AceStream.getBackendDomain() + "/activate";
		showTargetLink("http://acestream.org/activate", null);
	}

	protected void showTargetLink(final String url, final String pin) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mProgressView.setVisibility(View.GONE);

				TextView textInfo = findViewById(R.id.text_info);
				TextView targetUrl = findViewById(R.id.target_url);
				TextView textPin = findViewById(R.id.text_pin);
				TextView pinCode = findViewById(R.id.pin_code);

				targetUrl.setText(url);
				textInfo.setVisibility(View.VISIBLE);
				targetUrl.setVisibility(View.VISIBLE);

				if(pin == null) {
					textPin.setVisibility(View.GONE);
					pinCode.setVisibility(View.GONE);
				}
				else {
					pinCode.setText(pin);
					textPin.setVisibility(View.VISIBLE);
					pinCode.setVisibility(View.VISIBLE);
				}

				makeQrCode(mTargetUrl);
				mButtonOk.setVisibility(View.VISIBLE);
			}
		});
	}

    private void makeQrCode(String url) {

		try {
			BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
			Bitmap bitmap = barcodeEncoder.encodeBitmap(url, BarcodeFormat.QR_CODE, 200, 200);
			ImageView qrCode = findViewById(R.id.qr_code);
			qrCode.setImageBitmap(bitmap);
			qrCode.setVisibility(View.VISIBLE);
		} catch(Exception e) {
			Log.e(TAG, "failed to generate QR-code", e);
		}
	}
}
