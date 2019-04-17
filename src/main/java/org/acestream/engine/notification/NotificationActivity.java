package org.acestream.engine.notification;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.content.Intent;
import android.content.res.Resources;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import org.acestream.engine.AceStreamEngineBaseApplication;
import org.acestream.engine.PlaybackManagerAppCompatActivity;
import org.acestream.engine.R;
import org.acestream.sdk.AceStream;

import static org.acestream.sdk.Constants.USER_LEVEL_OPTION_PROXY_SERVER;

public class NotificationActivity
    extends PlaybackManagerAppCompatActivity
    implements OnClickListener {

	private final static String TAG = "AceStream/Notification";
	private String mMissingOption;
	private String mNotificationUrl;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.l_activity_notification);

		Intent intent = getIntent();
		String notificationReason = intent.getStringExtra("org.acestream.engine.notificationReason");
		mNotificationUrl = intent.getStringExtra("org.acestream.engine.notificationUrl");
		mMissingOption = intent.getStringExtra("org.acestream.engine.missingOption");
		Log.d(TAG, "onCreate: reason=" + notificationReason + " missingOption=" + mMissingOption + " url=" + mNotificationUrl);

		if(notificationReason == null || !notificationReason.equals("missing_option")) {
		    this.finish();
		    return;
		}

		if(TextUtils.isEmpty(mMissingOption)) {
		    this.finish();
		    return;
		}

		int missingOptionId;
		String defaultUrl;
        if("proxyServer".equals(mMissingOption)) {
            // https://m.acestream.net/options/get/proxyServer?use_internal_auth=1
            defaultUrl = "https://goo.gl/KGjLU1";
			missingOptionId = R.string.option_proxy_server;
        }
        else {
            defaultUrl = AceStream.getBackendDomain() + "/options/get/" + mMissingOption;
			missingOptionId = R.string.option_generic_premium;
		}

		if(mNotificationUrl == null || mNotificationUrl.isEmpty()) {
		    // use default url
		    mNotificationUrl = defaultUrl;
		}

		Resources resources = getResources();
		TextView textInfo = findViewById(R.id.text_info);
		String optionName = resources.getString(missingOptionId);
		textInfo.setText(resources.getString(R.string.notification_missing_option, optionName));
	}

	private void updateUI() {
		Resources resources = getResources();
		TextView textNotificationUrl = findViewById(R.id.text_notification_url);
		Button btn = findViewById(R.id.button_activate);
		btn.setOnClickListener(this);

		Intent browserIntent = AceStreamEngineBaseApplication.getBrowserIntent(this, mNotificationUrl);
		if(browserIntent == null) {
			// no browser, just show url to go
			btn.setText(resources.getString(R.string.ok));
			textNotificationUrl.setText(resources.getString(R.string.notification_visit_site, mNotificationUrl));
			textNotificationUrl.setVisibility(View.VISIBLE);
			// reset url to avoid opening browser on button click
			mNotificationUrl = null;
		}
		else {
			textNotificationUrl.setVisibility(View.GONE);
		}
		btn.setVisibility(View.VISIBLE);
	}

	@Override
	public void onResumeConnected() {
		super.onResumeConnected();
		// update UI when playback manager is connected
		updateUI();
	}

	@Override
    public void onClick(View v) {
		int i = v.getId();
		if (i == R.id.button_activate) {
			Log.d(TAG, "button_activate clicked: url=" + mNotificationUrl);
			if (mNotificationUrl != null) {
				openUrlInBrowser(mNotificationUrl);
			}
			this.finish();

		}
    }

    private void openUrlInBrowser(String url) {
		if(mPlaybackManager != null) {
			int optionId = 0;
			if(TextUtils.equals(mMissingOption, "proxyServer")) {
				optionId = USER_LEVEL_OPTION_PROXY_SERVER;
			}
			mPlaybackManager.openWebViewOnAuth(url, optionId, true);
		}
    }
}
