package org.acestream.engine.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import android.text.TextUtils;
import android.util.Log;

import org.acestream.engine.AceStreamEngineBaseApplication;
import org.acestream.engine.Constants;
import org.acestream.engine.MainActivity;
import org.acestream.engine.PlaybackManager;
import org.acestream.engine.PlaybackManagerAppCompatActivity;
import org.acestream.engine.R;
import org.acestream.engine.WebViewActivity;
import org.acestream.engine.notification.LinkActivity;
import org.acestream.sdk.AceStream;
import org.acestream.sdk.controller.api.response.AuthData;
import org.acestream.sdk.utils.AuthUtils;

import static org.acestream.sdk.Constants.EXTRA_INFOHASH;

public class LoginActivity
	extends PlaybackManagerAppCompatActivity
	implements PlaybackManager.AuthCallback
{
	private final static String TAG = "AceStream/Login";
    private final static int RC_SIGN_IN = 0;

    protected String mTarget = null;
	protected String mTargetUrl = null;
	protected int mMissingOptionId = 0;
	protected String mTargetInfohash = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.auth_login_activity);

		Intent intent = getIntent();
		if(intent != null) {
			mTarget = intent.getStringExtra(AceStream.EXTRA_LOGIN_TARGET);
			mTargetUrl = intent.getStringExtra(Constants.EXTRA_WEBVIEW_URL);
			mMissingOptionId = intent.getIntExtra(Constants.EXTRA_MISSING_OPTION_ID, 0);
			mTargetInfohash = intent.getStringExtra(EXTRA_INFOHASH);
		}

		Log.v(TAG, "onCreate:"
				+ "target=" + mTarget
				+ " url=" + mTargetUrl
				+ " missingOptionId=" + mMissingOptionId
				+ " infohash=" + mTargetInfohash
		);
	}

	@Override
	public void onResumeConnected() {
		super.onResumeConnected();
		Log.d(TAG, "onResumeConnected");
		mPlaybackManager.startEngine();
		mPlaybackManager.addAuthCallback(this);
		showMainFragment();
	}

	@Override
	protected void onPause() {
		super.onPause();
		if(mPlaybackManager != null) {
			mPlaybackManager.removeAuthCallback(this);
		}
	}

	private void showMainFragment() {
		FragmentManager fragmentManager = getSupportFragmentManager();
		Fragment fragment = fragmentManager.findFragmentById(R.id.fragment_holder);

		if(fragment == null) {
			FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
			Log.d(TAG, "main:onCreate: show MainFragment");
			Fragment newFragment = new LoginMainFragment();
			fragmentTransaction.replace(R.id.fragment_holder, newFragment, "login_main_fragment");
			fragmentTransaction.commit();
		}
		else {
			Log.d(TAG, "main:onCreate: fragment already created");
		}
	}

	public void showEngineLoginFragment() {
		Fragment loginFragment = getSupportFragmentManager().findFragmentByTag("login_engine_fragment");
		if(loginFragment == null) {
			loginFragment = new LoginEngineFragment();
			Log.d(TAG, "showEngineLoginFragment: create new login fragment");
		}
		else {
			Log.d(TAG, "showEngineLoginFragment: use existing login fragment");
		}
		FragmentTransaction fragmentTransaction = createFragmentTransaction();
		fragmentTransaction.replace(R.id.fragment_holder, loginFragment, "login_engine_fragment");
		fragmentTransaction.addToBackStack(null);
		fragmentTransaction.commitAllowingStateLoss();
	}

	private FragmentTransaction createFragmentTransaction() {
		FragmentManager fragmentManager = getSupportFragmentManager();
		FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
		fragmentTransaction.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left, R.anim.slide_in_left, R.anim.slide_out_right);
		return fragmentTransaction;
	}

	public void googleSignIn() {
		Intent intent = mPlaybackManager.getGoogleSignInIntent(this);
		if(intent != null) {
			startActivityForResult(intent, RC_SIGN_IN);
		}
	}

	public boolean isGoogleSignInAvailable() {
		if(mPlaybackManager == null) {
			return false;
		}
		else {
			return mPlaybackManager.isGoogleApiAvailable();
		}
	}

	@Override
	public void onActivityResult(int requestCode, int responseCode, Intent intent) {
		super.onActivityResult(requestCode, responseCode, intent);
		Log.d(TAG, "onActivityResult: requestCode=" + requestCode + " responseCode=" + responseCode);
		if (requestCode == RC_SIGN_IN) {
			mPlaybackManager.signInGoogleFromIntent(intent);
		}
	}

	@Override
	public void onAuthUpdated(final AuthData authData) {
		int authLevel = authData != null ? authData.auth_level : 0;

		Log.v(TAG, "onAuthUpdated: authLevel=" + authLevel);
		if(authLevel > 0) {
			proceedToTarget(authLevel);
		}
	}

	private void proceedToTarget(int authLevel) {

		// Check whether the user has required auth level
		if(mMissingOptionId != 0) {
			if(AuthUtils.userLevelContainsOption(authLevel, mMissingOptionId)) {
				if(mTargetInfohash != null) {
					AceStreamEngineBaseApplication.startPlaybackByInfohash(mTargetInfohash, false);
				}
				finish();
				return;
			}
		}

		Intent intent = makeProceedIntent();
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(intent);
		finish();
	}

	protected Intent makeProceedIntent() {
		Intent intent = new Intent();
		if(TextUtils.equals(mTarget, "webview") && !TextUtils.isEmpty(mTargetUrl)) {
			if(AceStreamEngineBaseApplication.showTvUi()) {
				intent.setClass(this, AceStreamEngineBaseApplication.getLinkActivityClass());
				if(mMissingOptionId != 0) {
					intent.putExtra(Constants.EXTRA_MISSING_OPTION_ID, mMissingOptionId);
				}
			}
			else {
				intent.setClass(this, AceStreamEngineBaseApplication.getWebViewActivityClass());
				intent.putExtra(Constants.EXTRA_WEBVIEW_URL, mTargetUrl);
			}
		}
		else if(TextUtils.equals(mTarget, AceStream.LOGIN_TARGET_BONUS_ADS)) {
			intent.setClass(this, AceStreamEngineBaseApplication.getBonusAdsActivityClass());
		}
		else {
			intent.setClass(this, AceStreamEngineBaseApplication.getMainActivityClass());
		}

		return intent;
	}

	public PlaybackManager getPlaybackManager() {
		return mPlaybackManager;
	}
}
