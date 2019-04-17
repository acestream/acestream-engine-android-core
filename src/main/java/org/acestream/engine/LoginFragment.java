package org.acestream.engine;

import android.app.Activity;
import android.content.Context;
import androidx.annotation.NonNull;
import android.util.Log;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.acestream.sdk.AceStream;
import org.acestream.sdk.controller.api.response.AuthData;

/**
 * A login screen that offers login via email/password.
 */
public class LoginFragment extends Fragment
{
    private static final String TAG = "AceStream/Login";

    // UI references.
    private EditText mEmailView;
    private EditText mPasswordView;
    private View mProgressView;
    private View mLoginFormView;
    private Button mEmailSignInButton;

    private MainActivity mainActivity;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.login_fragment, container, false);
        // Set up the login form.
        mEmailView = view.findViewById(R.id.email);

        mPasswordView = view.findViewById(R.id.password);
        mPasswordView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
                Log.d(TAG, "setOnEditorActionListener: id=" + id);
                attemptLogin();
                return true;
            }
        });

        mEmailSignInButton = view.findViewById(R.id.email_sign_in_button);
        mEmailSignInButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                attemptLogin();
            }
        });

        mLoginFormView = view.findViewById(R.id.login_form);
        mProgressView = view.findViewById(R.id.login_progress);

        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mainActivity = (MainActivity) getActivity();
    }

    /**
     * Attempts to sign in or register the account specified by the login form.
     * If there are form errors (invalid email, missing fields, etc.), the
     * errors are presented and no actual login attempt is made.
     */
    private void attemptLogin() {
        // Reset errors.
        mEmailView.setError(null);
        mPasswordView.setError(null);

        if(mainActivity == null) {
            Log.e(TAG, "attemptLogin: missing main activity");
            return;
        }

        // Store values at the time of the login attempt.
        String email = mEmailView.getText().toString();
        String password = mPasswordView.getText().toString();

        boolean cancel = false;
        View focusView = null;

        // Check for a valid password, if the user entered one.
        if (TextUtils.isEmpty(password)) {
            mPasswordView.setError(getString(R.string.error_field_required));
            focusView = mPasswordView;
            cancel = true;
        }

        // Check for a valid email address.
        if (TextUtils.isEmpty(email)) {
            mEmailView.setError(getString(R.string.error_field_required));
            focusView = mEmailView;
            cancel = true;
        } else if (!isEmailValid(email)) {
            mEmailView.setError(getString(R.string.error_invalid_email));
            focusView = mEmailView;
            cancel = true;
        }

        if (cancel) {
            // There was an error; don't attempt login and focus the first
            // form field with an error.
            focusView.requestFocus();
        }
        else {
            // Show a progress spinner, and kick off a background task to
            // perform the user login attempt.

            PlaybackManager pm = null;
            if(mainActivity != null) {
                pm = mainActivity.getPlaybackManager();
            }

            if(pm == null) {
                AceStreamEngineBaseApplication.toast("Internal error (14)");
            }
            else {
                hideKeyboard();
                showProgress(true);
                pm.signInAceStream(email, password, true, new PlaybackManager.AuthCallback() {
                    @Override
                    public void onAuthUpdated(final AuthData authData) {
                        AceStreamEngineBaseApplication.runOnMainThread(new Runnable() {
                            @Override
                            public void run() {
                                showProgress(false);
                                String error = null;

                                if(authData == null) {
                                    error = "auth_internal_error";
                                }
                                else if(authData.auth_level == 0) {
                                    if(authData.got_error == 1) {
                                        error = "auth_network_error";
                                    }
                                    else if(authData.auth_error != null) {
                                        error = authData.auth_error;
                                    }
                                    else {
                                        error = "auth_failed_error";
                                    }
                                }

                                if(error == null) {
                                    mainActivity.restoreMainFragment();
                                }
                                else {
                                    if(TextUtils.equals(error, "auth_error_bad_password")) {
                                        mPasswordView.setError(AceStream.getErrorMessage(error));
                                        mPasswordView.requestFocus();
                                    }
                                    else {
                                        mEmailView.setError(AceStream.getErrorMessage(error));
                                        mEmailView.requestFocus();
                                    }
                                }
                            }
                        });

                    }
                });
            }
        }
    }

    private void hideKeyboard() {
        Activity activity = getActivity();
        if(activity == null) {
            return;
        }
        View focusedView = null;
        if(mEmailView.hasFocus()) {
            focusedView = mEmailView;
        }
        else if(mPasswordView.hasFocus()) {
            focusedView = mPasswordView;
        }
        else if(mEmailSignInButton.hasFocus()) {
            focusedView = mEmailSignInButton;
        }

        if (focusedView != null) {
            Log.d(TAG, "Hide soft keyboard");
            InputMethodManager imm = (InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if(imm != null) {
                imm.hideSoftInputFromWindow(focusedView.getWindowToken(), 0);
            }
        }
        else {
            Log.d(TAG, "Cannot hide keyboard, no focused view");
        }
    }

    private boolean isEmailValid(String email) {
        //TODO: Replace this with your own logic
        return email.contains("@");
    }

    /**
     * Shows the progress UI and hides the login form.
     */
    private void showProgress(final boolean show) {
        mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
        mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onResume() {
        super.onResume();

        try {
            mEmailView.requestFocus();
        }
        catch(Throwable e) {
            //pass
        }
    }
}

