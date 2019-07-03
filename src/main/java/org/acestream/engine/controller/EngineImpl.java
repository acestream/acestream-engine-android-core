package org.acestream.engine.controller;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.OptionalPendingResult;
import com.google.android.gms.common.api.ResultCallback;

import org.acestream.engine.AceStreamEngineBaseApplication;
import org.acestream.engine.ServiceClient;
import org.acestream.engine.service.v0.IAceStreamEngine;
import org.acestream.sdk.controller.api.AuthCredentials;
import org.acestream.sdk.controller.api.response.AuthData;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.CopyOnWriteArrayList;

public class EngineImpl implements
        Engine,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        ServiceClient.Callback
{
    private final static String TAG = "AceStream/EngineImpl";

    private Context mContext;
    private final Queue<Callback<Boolean>> mEngineReadyCallbacks;
    private ExtendedEngineApi mEngineApi = null;

    private final ServiceClient mServiceClient;

    private CopyOnWriteArrayList<EventListener> mListeners = new CopyOnWriteArrayList<>();

    private GoogleApiClient mGoogleApiClient;
    private int mGoogleApiErrorCode = -1;
    private boolean mGoogleApiFinished = false;
    private String mGoogleIdToken = null;
    private int mEngineConnected = -1;
    private boolean mEngineWasStarted = false;

    public EngineImpl(Context ctx) {
        mContext = ctx;
        mEngineReadyCallbacks = new LinkedList<>();

        mServiceClient = new ServiceClient("EngineImpl", ctx, this, false);
        mGoogleApiClient = initGoogleApiClient();
    }

    @Override
    public void destroy() {
        Log.d(TAG, "destroy");
        if(mGoogleApiClient != null) {
            mGoogleApiClient.disconnect();
            mGoogleApiErrorCode = -1;
            mGoogleApiFinished = false;
        }

        mServiceClient.unbind();
    }

    @Override
    public void addListener(EventListener listener) {
        mListeners.add(listener);
    }

    @Override
    public void removeListener(EventListener listener) {
        mListeners.remove(listener);
    }

    private void notifySignIn(boolean success, boolean gotError) {
        Log.d(TAG, "notifySignIn: success=" + success + " gotError=" + gotError);
        for(EventListener listener: mListeners) {
            listener.onSignIn(success, gotError);
        }
    }

    private void notifyGoogleSignInAvailable(boolean available) {
        Log.d(TAG, "notifyGoogleSignInAvailable: available=" + available);
        for(EventListener listener: mListeners) {
            listener.onGoogleSignInAvaialble(available);
        }
    }

    private void checkSignIn() {
        Log.d(TAG, "checkSignIn: engine=" + mEngineConnected + " google=" + mGoogleApiFinished + " listeners=" + mListeners.size());

        if(mListeners.size() == 0) {
            return;
        }

        if(mEngineConnected == -1) {
            return;
        }

        if(!mGoogleApiFinished) {
            return;
        }

        String token = mGoogleIdToken;
        if(token == null) {
            token = "";
        }

        getEngineAuthData(null, null, token, new Callback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                notifySignIn(result, false);
            }

            @Override
            public void onError(String err) {
                Log.d(TAG, "checkSignIn: error: " + err);
                notifySignIn(false, true);
            }
        });
    }

    @Override
    public void startEngine() {
        Log.v(TAG, "startEngine");
        try {
            mServiceClient.startEngine();
            mServiceClient.enableAceCastServer();
        }
        catch(ServiceClient.ServiceMissingException e) {
            Log.e(TAG, "AceStream is not installed");
        }
    }

    private void connectGoogleApi() {
        if(mGoogleApiClient != null) {
            mGoogleApiClient.connect();
        }
    }

    @Override
    public void signIn() {
        // start connecting to both engine and google api
        // listeners will be notified when done
        startEngine();
        connectGoogleApi();
    }

    @Override
    public void signInAceStream(final String login, final String password, final Callback<Boolean> callback) {
        synchronized (mEngineReadyCallbacks) {
            mEngineReadyCallbacks.add(new Callback<Boolean>() {
                @Override
                public void onSuccess(Boolean result) {
                    Log.d(TAG, "signInAceStream: engine ready: connected=" + result);
                    if(result) {
                        getEngineAuthData(login, password, null, callback);
                    }
                    else {
                        callback.onError("failed to connect to engine");
                    }
                }

                @Override
                public void onError(String err) {
                    Log.d(TAG, "signInAceStream: error: " + err);
                    callback.onError(err);
                }
            });
        }
        startEngine();
    }

    @Override
    public void signInGoogleSilent(final Callback<Boolean> callback) {
        Log.d(TAG, "signInGoogleSilent");

        if(TextUtils.isEmpty(mGoogleIdToken)) {
            callback.onError("missing token");
            return;
        }

        synchronized (mEngineReadyCallbacks) {
            mEngineReadyCallbacks.add(new Callback<Boolean>() {
                @Override
                public void onSuccess(Boolean result) {
                    Log.d(TAG, "signInGoogle: engine ready: connected=" + result);
                    if(result) {
                        getEngineAuthData(null, null, mGoogleIdToken, callback);
                    }
                    else {
                        callback.onError("failed to connect to engine");
                    }
                }

                @Override
                public void onError(String err) {
                    Log.d(TAG, "signInGoogle: error: " + err);
                    callback.onError(err);
                }
            });
        }
        startEngine();
    }

    @Override
    public Intent getGoogleSignInIntent(Activity activity) {
        if(mGoogleApiClient == null) {
            if(mGoogleApiErrorCode != -1 && activity != null) {
                // Google API failed to init.
                // Show standard dialog with error explanation.
                GoogleApiAvailability.getInstance().getErrorDialog(activity, mGoogleApiErrorCode, 0).show();
            }
            return null;
        }
        return Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
    }

    @Override
    public void signInGoogleFromIntent(final Intent intent, final Callback<Boolean> callback) {
        Log.d(TAG, "signInGoogleFromIntent");

        GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(intent);
        if(result == null) {
            callback.onError("failed to get result from intent");
            return;
        }

        // this should set id token
        updateProfileInformation(result, false);

        synchronized (mEngineReadyCallbacks) {
            mEngineReadyCallbacks.add(new Callback<Boolean>() {
                @Override
                public void onSuccess(Boolean result) {
                    Log.d(TAG, "signInGoogle: engine ready: connected=" + result);
                    if(result) {
                        getEngineAuthData(null, null, mGoogleIdToken, callback);
                    }
                    else {
                        callback.onError("failed to connect to engine");
                    }
                }

                @Override
                public void onError(String err) {
                    Log.d(TAG, "signInGoogle: error: " + err);
                    callback.onError(err);
                }
            });
        }
        startEngine();
    }

    private void onEngineReady(boolean connected) {
        Log.d(TAG, "onEngineReady: connected=" + connected + " callbacks=" + mEngineReadyCallbacks.size());

        synchronized (mEngineReadyCallbacks) {
            while (mEngineReadyCallbacks.size() > 0) {
                Callback<Boolean> cb = mEngineReadyCallbacks.remove();
                cb.onSuccess(connected);
            }
        }

        setEngineConnected(connected ? 1 : 0);
    }

    private void getEngineAuthData(String login, String password, String googleIdToken, final Callback<Boolean> callback) {
        if(mEngineApi == null) {
            Log.e(TAG, "getEngineAuthData: missing engine api");
            return;
        }

        AuthCredentials.AuthMethod authType = null;
        if(!TextUtils.isEmpty(login)) {
            authType = AuthCredentials.AuthMethod.AUTH_ACESTREAM;
        }
        else if(!TextUtils.isEmpty(googleIdToken)) {
            authType = AuthCredentials.AuthMethod.AUTH_GOOGLE;
        }

        if(authType == null) {
            // no credentials, just get auth from engine
            mEngineApi.getAuth(new Callback<AuthData>() {
                @Override
                public void onSuccess(AuthData result) {
                    Log.d(TAG, "getEngineAuthData:get: done: level=" + result.auth_level);
                    if (result.auth_level > 0) {
                        callback.onSuccess(true);
                    } else {
                        callback.onSuccess(false);
                    }
                }

                @Override
                public void onError(String err) {
                    callback.onError(err);
                }
            });
        }
        else {
            AuthCredentials.Builder builder = new AuthCredentials.Builder(authType);
            if (!TextUtils.isEmpty(login)) {
                builder.setLogin(login);
            }
            if (!TextUtils.isEmpty(password)) {
                builder.setPassword(password);
            }
            if (!TextUtils.isEmpty(googleIdToken)) {
                builder.setToken(googleIdToken);
            }

            mEngineApi.updateAuth(builder.build(), new Callback<AuthData>() {
                @Override
                public void onSuccess(AuthData result) {
                    Log.d(TAG, "getEngineAuthData:update: done: level=" + result.auth_level);
                    if (result.auth_level > 0) {
                        callback.onSuccess(true);
                    } else {
                        callback.onSuccess(false);
                    }
                }

                @Override
                public void onError(String err) {
                    callback.onError(err);
                }
            });
        }
    }

    private void setEngineConnected(int status) {
        mEngineConnected = status;
        checkSignIn();
    }

    private void setGoogleApiFinished() {
        mGoogleApiFinished = true;
        checkSignIn();
    }

    private void setGoogleApiAvailable(boolean available) {
        notifyGoogleSignInAvailable(available);
    }

    private GoogleApiClient initGoogleApiClient() {
        final String webClientId = AceStreamEngineBaseApplication.getStringAppMetadata("webClientId");
        if(TextUtils.isEmpty(webClientId)) {
            Log.w(TAG, "initGoogleApiClient: missing web client id");
            return null;
        }

        int status = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(mContext);
        if(status != ConnectionResult.SUCCESS) {
            Log.d(TAG, "initGoogleApiClient: failed: status=" + status);
            mGoogleApiErrorCode = status;
            setGoogleApiAvailable(false);
            setGoogleApiFinished();
            return null;
        }

        // Configure sign-in to request the user's ID, email address, and basic
        // profile. ID and basic profile are included in DEFAULT_SIGN_IN.
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestIdToken(webClientId)
                .build();

        // Initializing google plus api client
        return new GoogleApiClient.Builder(mContext)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                .build();
    }

    @Override
    public void onConnected(Bundle bundle) {
        setGoogleApiAvailable(true);
        getProfileInformation();
    }

    @Override
    public void onConnectionSuspended(int i) {
        setGoogleApiAvailable(false);
        setGoogleApiFinished();
    }

    @Override
    public void onConnectionFailed(@NonNull  ConnectionResult connectionResult) {
        setGoogleApiAvailable(false);
        setGoogleApiFinished();
    }

    private void getProfileInformation() {
        try {
            if(mGoogleApiClient == null) {
                return;
            }

            if(!mGoogleApiClient.isConnected()) {
                setGoogleApiFinished();
                return;
            }
            OptionalPendingResult<GoogleSignInResult> pendingResult =
                    Auth.GoogleSignInApi.silentSignIn(mGoogleApiClient);

            if (pendingResult.isDone()) {
                Log.d(TAG, "getProfileInformation: got immediate result");
                updateProfileInformation(pendingResult.get());
            }
            else {
                pendingResult.setResultCallback(new ResultCallback<GoogleSignInResult>() {
                    @Override
                    public void onResult(GoogleSignInResult result) {
                        Log.d(TAG, "getProfileInformation: got delayed result");
                        updateProfileInformation(result);
                    }
                });
            }
        }
        catch (Exception e) {
            Log.e(TAG, "getProfileInformation: error", e);
            setGoogleApiFinished();
        }
    }

    private void updateProfileInformation(GoogleSignInResult result) {
        updateProfileInformation(result, true);
    }

    private void updateProfileInformation(GoogleSignInResult result, boolean notify) {
        Log.d(TAG, "updateProfileInformation: success=" + result.isSuccess() + " code=" + result.getStatus().getStatusCode() + " msg=" + result.getStatus().getStatusMessage() + " notify=" + notify);

        GoogleSignInAccount acct = result.getSignInAccount();
        if(acct == null) {
            Log.d(TAG, "updateProfileInformation: null account");
            mGoogleIdToken = "";
        }
        else {
            String personName = acct.getDisplayName();
            String personEmail = acct.getEmail();
            String personId = acct.getId();
            mGoogleIdToken = acct.getIdToken();
            Log.d(TAG, "updateProfileInformation: email=" + personEmail + " name=" + personName + " id=" + personId + " idToken=" + mGoogleIdToken);
        }

        if(notify) {
            setGoogleApiFinished();
        }
    }

    @Override
    public void onConnected(IAceStreamEngine service) {
        Log.d(TAG, "msg: engine connected");

        mEngineApi = new ExtendedEngineApi(service);
        mEngineWasStarted = true;
        onEngineReady(true);
    }

    @Override
    public void onFailed() {
        Log.d(TAG, "msg: engine failed");
        onStopped();
    }

    @Override
    public void onDisconnected() {
        Log.v(TAG, "engine service disconnected");
    }

    @Override
    public void onUnpacking() {
        Log.v(TAG, "msg: engine unpacking");
    }

    @Override
    public void onStarting() {
        Log.d(TAG, "msg: engine starting");
    }

    @Override
    public void onStopped() {
        Log.d(TAG, "onEngineStopped: wasStarted=" + mEngineWasStarted + " listeners=" + mListeners.size());
        mEngineConnected = -1;

        if(mEngineWasStarted && mListeners.size() > 0) {
            startEngine();
        }
    }

    @Override
    public void onPlaylistUpdated() {
    }

    @Override
    public void onEPGUpdated() {
    }

    @Override
    public void onSettingsUpdated() {
    }

    @Override
    public void onAuthUpdated() {
    }

    @Override
    public void onRestartPlayer() {
    }
}
