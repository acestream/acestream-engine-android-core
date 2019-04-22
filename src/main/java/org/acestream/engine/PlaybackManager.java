package org.acestream.engine;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.text.TextUtils;
import android.util.Log;

import com.connectsdk.device.ConnectableDevice;
import com.connectsdk.service.DeviceService;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.OptionalPendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;

import org.acestream.engine.service.v0.IAceStreamEngine;
import org.acestream.sdk.AceStream;
import org.acestream.sdk.EngineSession;
import org.acestream.sdk.controller.EngineApi;
import org.acestream.sdk.controller.api.AuthCredentials;
import org.acestream.sdk.controller.api.response.AdConfig;
import org.acestream.sdk.controller.api.response.AndroidConfig;
import org.acestream.sdk.controller.api.response.AuthData;
import org.acestream.sdk.interfaces.IAceStreamManager;
import org.acestream.sdk.utils.AuthUtils;
import org.acestream.sdk.utils.Logger;
import org.acestream.sdk.utils.MiscUtils;
import org.acestream.sdk.utils.Workers;
import org.jetbrains.annotations.NotNull;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import static org.acestream.sdk.Constants.EXTRA_INFOHASH;

public class PlaybackManager extends AceStreamManagerImpl {
    private static final String TAG = "AS/PM";

    private static PlaybackManager sInstance = null;

    @SuppressWarnings("FieldCanBeLocal")
    private final int UPDATE_AUTH_INTERVAL = 600000;

    // Auth
    private boolean mGotEngineAuth = false;
    private long mLastAuthUpdateAt = -1;
    private final List<Runnable> mOnAuthQueue = new ArrayList<>();

    // Google sign in
    private GoogleApiClient mGoogleApiClient;
    private GoogleSignInAccount mGoogleAccount = null;
    private boolean mGoogleApiFinished = false;
    private int mGoogleApiErrorCode = -1;
    private boolean mGoogleApiAvailable = true;

    private class LocalBinder extends Binder {
        PlaybackManager getService() {
            return PlaybackManager.this;
        }
    }

    public static PlaybackManager getService(IBinder iBinder) {
        final PlaybackManager.LocalBinder binder = (PlaybackManager.LocalBinder) iBinder;
        return binder.getService();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        sInstance = this;
        mLocalBinder = new LocalBinder();
        mGoogleApiClient = initGoogleApiClient();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        sInstance = null;
        if(mGoogleApiClient != null) {
            mGoogleApiClient.disconnect();
            mGoogleApiErrorCode = -1;
            mGoogleApiFinished = false;
        }
    }

    public static class Client {
        public static final String TAG = "AS/PM/Client";

        @MainThread
        public interface Callback {
            void onConnected(PlaybackManager service);
            void onDisconnected();
        }

        private boolean mBound = false;

        private final PlaybackManager.Client.Callback mCallback;
        private final Context mContext;

        private final ServiceConnection mServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder iBinder) {
                Log.d(TAG, "onServiceConnected: bound=" + mBound + " context=" + mContext);

                if (!mBound)
                    return;

                final PlaybackManager service = PlaybackManager.getService(iBinder);
                if (service != null) {
                    mCallback.onConnected(service);
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.d(TAG, "onServiceDisconnected: context=" + mContext);

                mBound = false;
                mCallback.onDisconnected();
            }
        };

        private static Intent getServiceIntent(Context context) {
            return new Intent(context, PlaybackManager.class);
        }

        private static void startService(Context context) {
            context.startService(getServiceIntent(context));
        }

        private static void stopService(Context context) {
            context.stopService(getServiceIntent(context));
        }

        public Client(Context context, PlaybackManager.Client.Callback callback) {
            if (context == null || callback == null) throw new IllegalArgumentException("Context and callback can't be null");
            mContext = context;
            mCallback = callback;
        }

        @MainThread
        public void connect() {
            Log.d(TAG, "connect: bound=" + mBound + " context=" + mContext);
            if (mBound) {
                if(BuildConfig.DEBUG) {
                    throw new IllegalStateException("already connected");
                }
                else {
                    Log.w(TAG, "connect: already connected: context=" + mContext);
                    return;
                }
            }
            final Intent serviceIntent = getServiceIntent(mContext);
            mContext.startService(serviceIntent);
            mBound = mContext.bindService(serviceIntent, mServiceConnection, BIND_AUTO_CREATE);
        }

        @MainThread
        public boolean isConnected() {
            if(!Workers.isOnMainThread()) {
                throw new IllegalStateException("Must be run on main thread");
            }
            return mBound;
        }

        @MainThread
        public void disconnect() {
            Log.d(TAG, "disconnect: bound=" + mBound + " context=" + mContext);

            if(!Workers.isOnMainThread()) {
                throw new IllegalStateException("Must be run on main thread");
            }

            if (mBound) {
                mBound = false;
                mContext.unbindService(mServiceConnection);
                mCallback.onDisconnected();
            }
        }

        @SuppressWarnings("unused")
        public static void restartService(Context context) {
            stopService(context);
            startService(context);
        }
    }

    @Override
    protected void onEngineServiceConnected(IAceStreamEngine service) {
        super.onEngineServiceConnected(service);
        updateEnginePreferences();
        startAuthUpdate();
        mEngineApi.getAuth(new org.acestream.engine.controller.Callback<AuthData>() {
            @Override
            public void onSuccess(AuthData result) {
                Log.v(TAG, "auth:get: result=" + result);

                // For "none" and "acestream" auth methods auth is finished and need to notify auth callbacks.
                // For external auth (google, fb) auth is not yet finished, need to update tokens first.
                boolean notify = true;

                if(result != null) {
                    switch (result.getAuthMethod()) {
                        case AUTH_GOOGLE:
                            googleSilentSignInOrConnect();
                            notify = false;
                            break;
                    }
                }

                setCurrentAuthData(result, notify);
            }

            @Override
            public void onError(String err) {
                Log.v(TAG, "auth:get: error=" + err);
                setCurrentAuthData(null, true);
            }
        });
    }

    @Override
    protected void onNetworkStateChanged(final boolean connected) {
        super.onNetworkStateChanged(connected);
        if(connected) {
            if(mCurrentAuthData != null && mCurrentAuthData.got_error == 1) {
                // Got error on last auth.
                // Probably due to missing network connection.
                // Retry now.
                updateAuth();
            }
        }
    }

    @Override
    public void onStopped() {
        super.onStopped();
        stopAuthUpdate();
    }

    @Override
    public void onFailed() {
        super.onFailed();
        stopAuthUpdate();
    }

    @Override
    public void onDisconnected() {
        super.onDisconnected();
        stopAuthUpdate();
    }

    ///////////////////
    // START AUTH
    private Runnable mUpdateAuthTask = new Runnable() {
        @Override
        public void run() {
            if(shouldUpdateAuth()) {
                updateAuth();
            }
            long interval = getAuthUpdateInterval();
            if(BuildConfig.DEBUG) {
                Log.v(TAG, "updateAuthTask: next update in " + MiscUtils.formatInterval(interval));
            }
            mHandler.postDelayed(mUpdateAuthTask, interval);
        }
    };
    
    private GoogleApiClient.ConnectionCallbacks mGoogleApiConnectionCallback = new GoogleApiClient.ConnectionCallbacks() {
        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Log.v(TAG, "googleapi:onConnected");
            setGoogleApiAvailable(true);
            googleSilentSignIn();
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.v(TAG, "googleapi:onConnectionSuspended");
            setGoogleApiAvailable(false);
            setGoogleApiFinished();
        }
    };

    private GoogleApiClient.OnConnectionFailedListener mGoogleApiConnectionFailedListener = new GoogleApiClient.OnConnectionFailedListener() {
        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            Log.v(TAG, "googleapi:onConnectionFailed");
            setGoogleApiAvailable(false);
            setGoogleApiFinished();
        }
    };

    public void signInAceStream(final String login,
                                final String password,
                                final boolean autoRegister,
                                @Nullable final AuthCallback callback) {
        getEngine(new EngineStateCallback() {
            @Override
            public void onEngineConnected(@NonNull IAceStreamManager playbackManager, @NonNull EngineApi engineApi) {
                AuthCredentials.Builder builder = new AuthCredentials.Builder(AuthCredentials.AuthMethod.AUTH_ACESTREAM);
                builder.setLogin(login);
                builder.setPassword(password);
                mEngineApi.signIn(builder.build(), autoRegister, new org.acestream.engine.controller.Callback<AuthData>() {
                    @Override
                    public void onSuccess(AuthData result) {
                        Log.v(TAG, "auth:update: result=" + result.toString());
                        setCurrentAuthData(result, true);
                        if(callback != null) {
                            callback.onAuthUpdated(result);
                        }
                    }

                    @Override
                    public void onError(String err) {
                        Log.v(TAG, "auth:update: error=" + err);
                        setCurrentAuthData(null, true);
                        if(callback != null) {
                            callback.onAuthUpdated(null);
                        }
                    }
                });
            }
        });
    }

    @Override
    public void signOut() {
        if(mGoogleAccount != null) {
            googleSignOut();
            googleRevokeAccess();
        }
        else {
            engineSignOut();
        }

        AceStreamEngineBaseApplication.clearWebViewCookies();
    }

    private void googleSignOut() {
        if(mGoogleApiClient == null) return;

        if (mGoogleApiClient.isConnected()) {
            Log.d(TAG, "googleSignOut: connected, sign out");
            Auth.GoogleSignInApi.signOut(mGoogleApiClient).setResultCallback(
                    new ResultCallback<Status>() {
                        @Override
                        public void onResult(@NonNull Status status) {
                            Log.d(TAG, "User disconnected");

                            // reset token
                            mGoogleAccount = null;

                            engineSignOut();
                        }
                    });
        }
        else {
            Log.w(TAG, "googleSignOut: not connected");
            engineSignOut();
        }
    }

    private void engineSignOut() {
        // Logout on engine
        if(mEngineApi == null) {
            throw new IllegalStateException("missing engine api");
        }

        Log.v(TAG, "engineSignOut");

        mEngineApi.logout(new org.acestream.engine.controller.Callback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                Log.v(TAG, "auth:logout: done: result=" + result);
                setCurrentAuthData(AuthData.getEmpty(), true);
            }

            @Override
            public void onError(String err) {
                Log.v(TAG, "auth:logout: error: " + err);
                setCurrentAuthData(AuthData.getEmpty(), true);
            }
        });
    }

    public void googleRevokeAccess() {
        if(mGoogleApiClient == null) return;

        if (mGoogleApiClient.isConnected()) {
            Auth.GoogleSignInApi.revokeAccess(mGoogleApiClient).setResultCallback(
                    new ResultCallback<Status>() {
                        @Override
                        public void onResult(@NotNull Status status) {
                            Log.d(TAG, "User access revoked");
                        }
                    });
        }
        else {
            Log.w(TAG, "googleRevokeAccess: not connected");
        }
    }

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

    public void signInGoogleFromIntent(final Intent intent) {
        Log.d(TAG, "signInGoogleFromIntent");

        GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(intent);
        if(result == null) {
            if(BuildConfig.DEBUG) {
                Log.v(TAG, "signInGoogleFromIntent: null result");
            }
            return;
        }

        if(result.getSignInAccount() == null) {
            // No need to update profile with null account.
            // Null account from sign-in intent generally means that dialog was cancelled.
            if(BuildConfig.DEBUG) {
                Log.v(TAG, "signInGoogleFromIntent: null account");
            }
            return;
        }

        if(mGoogleApiClient != null) {
            if (mGoogleApiClient.isConnected()) {
                Log.v(TAG, "signInGoogleFromIntent: api connected, proceed");
                googleUpdateProfileInformation(result);
            } else {
                Log.v(TAG, "signInGoogleFromIntent: not connected");
                mGoogleApiClient.connect();
            }
        }
    }

    private void setGoogleApiFinished() {
        if(BuildConfig.DEBUG) {
            Log.v(TAG, "setGoogleApiFinished");
        }
        mGoogleApiFinished = true;
        notifyAuthFinished();
    }

    private void setGoogleApiAvailable(boolean available) {
        mGoogleApiAvailable = available;
    }

    private void googleUpdateProfileInformation(GoogleSignInResult result) {
        Log.d(TAG, "googleUpdateProfileInformation: success=" + result.isSuccess() + " code=" + result.getStatus().getStatusCode() + " msg=" + result.getStatus().getStatusMessage());

        //NOTE: call this prior to calling updateGoogleAccount(), which may notify auth listeners.
        // Need to set correct auth status before notifying.
        setGoogleApiFinished();

        GoogleSignInAccount acct = result.getSignInAccount();
        if(acct == null) {
            Log.d(TAG, "googleUpdateProfileInformation: null account");
        }
        else {
            String personName = acct.getDisplayName();
            String personEmail = acct.getEmail();
            String personId = acct.getId();
            String idToken = acct.getIdToken();
            Log.d(TAG, "googleUpdateProfileInformation: email=" + personEmail + " name=" + personName + " id=" + personId + " idToken=" + idToken);
        }
        updateGoogleAccount(acct);
    }

    private void updateGoogleAccount(GoogleSignInAccount account) {
        String prevToken = mGoogleAccount != null ? mGoogleAccount.getIdToken() : null;
        String newToken = account != null ? account.getIdToken() : null;
        boolean tokenChanged = !TextUtils.equals(newToken, prevToken);
        mGoogleAccount = account;

        if(!tokenChanged) {
            if (mCurrentAuthData == null) {
                if(newToken != null) {
                    // Got new token but missing auth data from engine
                    tokenChanged = true;
                }
            }
            else {
                if(mCurrentAuthData.getAuthMethod() != AuthCredentials.AuthMethod.AUTH_GOOGLE) {
                    // auth method changed
                    tokenChanged = true;
                }
                else {
                    try {
                        String tokenHash = MiscUtils.sha1Hash(newToken);
                        if(!TextUtils.equals(mCurrentAuthData.token, tokenHash)) {
                            tokenChanged = true;
                        }
                    }
                    catch(UnsupportedEncodingException | NoSuchAlgorithmException e) {
                        Log.w(TAG, "updateGoogleAccount: failed to make token hash");
                    }
                }
            }
        }

        Log.v(TAG, "updateGoogleAccount: changed=" + tokenChanged + " token=" + newToken + " currentAuthData=" + mCurrentAuthData);

        if(tokenChanged && mEngineApi != null) {
            AuthCredentials.Builder builder = new AuthCredentials.Builder(AuthCredentials.AuthMethod.AUTH_GOOGLE);
            builder.setToken(newToken);
            mEngineApi.updateAuth(builder.build(), new org.acestream.engine.controller.Callback<AuthData>() {
                @Override
                public void onSuccess(AuthData result) {
                    Log.v(TAG, "auth:update: result=" + result.toString());
                    setCurrentAuthData(result, true);
                }

                @Override
                public void onError(String err) {
                    Log.v(TAG, "auth:update: error=" + err);
                    setCurrentAuthData(null, true);
                }
            });
        }
        else {
            // Always notify listeners.
            // For example, on first app start token may not change (prev and current is null), but
            // listeners can wait for auth to update UI.
            notifyAuthUpdated();
        }
    }

    private GoogleApiClient initGoogleApiClient() {
        final String webClientId = AceStreamEngineBaseApplication.getStringAppMetadata("webClientId");
        if(TextUtils.isEmpty(webClientId)) {
            Log.w(TAG, "initGoogleApiClient: missing web client id");
            return null;
        }

        int status = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this);
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
        return new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(mGoogleApiConnectionCallback)
                .addOnConnectionFailedListener(mGoogleApiConnectionFailedListener)
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                .build();
    }

    private void googleSilentSignInOrConnect() {
        if(mGoogleApiClient == null) return;

        if(mGoogleApiClient.isConnected()) {
            googleSilentSignIn();
        }
        else {
            mGoogleApiClient.connect();
        }
    }

    private void googleSilentSignIn() {
        try {
            if(mGoogleApiClient == null) {
                Log.v(TAG, "googleSilentSignIn: missing client");
                return;
            }

            if(!mGoogleApiClient.isConnected()) {
                setGoogleApiFinished();
                Log.v(TAG, "googleSilentSignIn: not connected");
                return;
            }

            Log.v(TAG, "googleSilentSignIn: connected=" + mGoogleApiClient.isConnected());

            OptionalPendingResult<GoogleSignInResult> pendingResult =
                    Auth.GoogleSignInApi.silentSignIn(mGoogleApiClient);

            if (pendingResult.isDone()) {
                Log.d(TAG, "googleSilentSignIn: got immediate result");
                googleUpdateProfileInformation(pendingResult.get());
            }
            else {
                pendingResult.setResultCallback(new ResultCallback<GoogleSignInResult>() {
                    @Override
                    public void onResult(@NotNull GoogleSignInResult result) {
                        Log.d(TAG, "googleSilentSignIn: got delayed result");
                        googleUpdateProfileInformation(result);
                    }
                });
            }
        }
        catch (Exception e) {
            Log.e(TAG, "googleSilentSignIn: error", e);
            setGoogleApiFinished();
        }
    }

    protected void setCurrentAuthData(AuthData authData, boolean notify) {
        if(!AuthData.equals(mCurrentAuthData, authData)) {
            Logger.v(TAG, "setCurrentAuthData: auth data changed, reset pending bonuses");
            resetPendingBonuses();
        }

        mCurrentAuthData = authData;
        mGotEngineAuth = true;

        if(mCurrentAuthData != null
                && mPendingBonuses > 0
                && mCurrentAuthData.bonuses_updated_at > mLastBonusesUpdatedAt) {
            Log.v(TAG, "bonus: reset pending bonuses:"
                    + " amount=" + mPendingBonuses
                    + " age=" + (mCurrentAuthData.bonuses_updated_at - mLastBonusesUpdatedAt)
            );
            mPendingBonuses = 0;
            mLastBonusesUpdatedAt = mCurrentAuthData.bonuses_updated_at;
        }

        if(notify) {
            notifyAuthUpdated();
        }
    }

    private void notifyAuthFinished() {
        if(!isAuthInProgress()) {
            synchronized (mOnAuthQueue) {
                Logger.v(TAG, "notifyAuthFinished: queue=" + mOnAuthQueue.size());
                for (Runnable runnable : mOnAuthQueue) {
                    runnable.run();
                }
                mOnAuthQueue.clear();
            }
        }
        else {
            Logger.v(TAG, "notifyAuthFinished: still in progress");
        }
    }

    private boolean isGoogleAccountExpired() {
        return mGoogleAccount != null && mGoogleAccount.isExpired();
    }

    public void updateAuthIfExpired(long maxAgeMillis) {
        boolean expired = false;
        long age = System.currentTimeMillis() - mLastAuthUpdateAt;

        if(mLastAuthUpdateAt == -1) {
            if(BuildConfig.DEBUG) {
                Log.v(TAG, "updateAuthIfExpired: yes, no last update");
            }
            expired = true;
        }
        else if(age > maxAgeMillis) {
            if(BuildConfig.DEBUG) {
                Log.v(TAG, "updateAuthIfExpired: yes: age=" + age + " max=" + maxAgeMillis);
            }
            expired = true;
        }
        else {
            if(BuildConfig.DEBUG) {
                Log.v(TAG, "updateAuthIfExpired: no: age=" + age + " max=" + maxAgeMillis);
            }
        }

        if(expired) {
            updateAuth();
        }
    }

    public void updateAuth() {
        updateAuth(null);
    }

    public void updateAuth(final @Nullable Runnable callback) {
        Log.v(TAG, "updateAuth");
        if (mEngineApi != null) {
            mLastAuthUpdateAt = System.currentTimeMillis();

            boolean updateAuthOnEngine = true;

            // If google auth is used then check token expiration time.
            // If token is expired - update token (engine auth will be updated in process).
            // If token is not expired - just update engine auth with current credentials.
            if(mCurrentAuthData != null && mCurrentAuthData.getAuthMethod() == AuthCredentials.AuthMethod.AUTH_GOOGLE) {
                if(mGoogleAccount == null || isGoogleAccountExpired()) {
                    // Save callback to queue because we won't call it now.
                    if(callback != null) {
                        mOnAuthQueue.add(callback);
                    }
                    googleSilentSignInOrConnect();
                    updateAuthOnEngine = false;
                }
            }

            if(updateAuthOnEngine) {
                mEngineApi.updateAuth(null, new org.acestream.engine.controller.Callback<AuthData>() {
                    @Override
                    public void onSuccess(AuthData result) {
                        Log.v(TAG, "auth:update: result=" + result.toString());
                        setCurrentAuthData(result, true);
                        if(callback != null) {
                            callback.run();
                        }
                    }

                    @Override
                    public void onError(String err) {
                        Log.v(TAG, "auth:update: error=" + err);
                        setCurrentAuthData(null, true);
                        if(callback != null) {
                            callback.run();
                        }
                    }
                });
            }
        }
    }

    public boolean isGoogleApiAvailable() {
        return mGoogleApiAvailable;
    }

    public String getEngineLogin() {
        if(mCurrentAuthData == null) {
            return null;
        }

        if(mCurrentAuthData.getAuthMethod() != AuthCredentials.AuthMethod.AUTH_ACESTREAM) {
            return null;
        }

        return mCurrentAuthData.token;
    }

    public String getGoogleLogin() {
        return mGoogleAccount != null ? mGoogleAccount.getEmail() : null;
    }

    public boolean getGoogleSignedIn() {
        return mGoogleAccount != null;
    }

    private long getAuthUpdateInterval() {
        return UPDATE_AUTH_INTERVAL;
    }

    private void startAuthUpdate() {
        long interval = getAuthUpdateInterval();
        if(BuildConfig.DEBUG) {
            Log.v(TAG, "startAuthUpdate: interval=" + MiscUtils.formatInterval(interval));
        }
        mHandler.postDelayed(mUpdateAuthTask, interval);
    }

    private void stopAuthUpdate() {
        if(BuildConfig.DEBUG) {
            Log.v(TAG, "stopAuthUpdate");
        }
        mHandler.removeCallbacks(mUpdateAuthTask);
    }

    private boolean shouldUpdateAuth() {
        if(!MiscUtils.isNetworkConnected(this)) {
            if(BuildConfig.DEBUG) {
                Log.v(TAG, "shouldUpdateAuth: no, network disconnected");
            }
            return false;
        }

        if(mCurrentAuthData == null) {
            if(BuildConfig.DEBUG) {
                Log.v(TAG, "shouldUpdateAuth: yes, no auth data");
            }
            return true;
        }

        if(mCurrentAuthData.getAuthMethod() == AuthCredentials.AuthMethod.AUTH_NONE) {
            if(BuildConfig.DEBUG) {
                Log.v(TAG, "shouldUpdateAuth: no, auth_method==none");
            }
            return false;
        }

        long now = System.currentTimeMillis();
        long age = now - mLastAuthUpdateAt;

        // Update every 4 hours
        if(age > 14400 * 1000) {
            if(BuildConfig.DEBUG) {
                Log.v(TAG, "shouldUpdateAuth: yes: age=" + MiscUtils.formatInterval(age));
            }
            return true;
        }

        if(mCurrentAuthData.getAuthMethod() == AuthCredentials.AuthMethod.AUTH_GOOGLE) {
            if(mGoogleAccount == null) {
                // Null account means that google auth failed.
                // No need to update auth in background. User should manually try to sign in again.
                if(BuildConfig.DEBUG) {
                    Log.v(TAG, "shouldUpdateAuth: no, null google account");
                }
                return false;
            }

            if(isGoogleAccountExpired()) {
                if(BuildConfig.DEBUG) {
                    Log.v(TAG, "shouldUpdateAuth: yes, google account expired");
                }
                return true;
            }
        }

        if(BuildConfig.DEBUG) {
            Log.v(TAG, "shouldUpdateAuth: no: age=" + age);
        }

        return false;
    }

    // END AUTH
    //////////////////

    public String getGoogleAuthToken() {
        return mGoogleAccount != null ? mGoogleAccount.getIdToken() : null;
    }

    private String getGoogleAuthEmail() {
        return mGoogleAccount != null ? mGoogleAccount.getEmail() : null;
    }

    public String getAuthLogin() {
        if(mCurrentAuthData == null) {
            return null;
        }

        String login = null;
        switch(mCurrentAuthData.getAuthMethod()) {
            case AUTH_ACESTREAM:
                login = mCurrentAuthData.token;
                break;
            case AUTH_NONE:
                break;
            case AUTH_GOOGLE:
                login = getGoogleAuthEmail();
                break;
        }

        return login;
    }

    public boolean isAuthInProgress() {
        boolean ret = false;

        if(!mGotEngineAuth) {
            ret = true;
        }
        else if(mCurrentAuthData != null) {
            if(mCurrentAuthData.getAuthMethod() == AuthCredentials.AuthMethod.AUTH_GOOGLE) {
                ret = !mGoogleApiFinished;
            }
        }

        if(BuildConfig.DEBUG) {
            Log.v(TAG, "isAuthInProgress:"
                    + " ret=" + ret
                    + " engine=" + mGotEngineAuth
                    + " google=" + mGoogleApiFinished
                    + " current=" + mCurrentAuthData
            );
        }

        return ret;
    }

    public boolean isUserAuthenticated() {
        if(mCurrentAuthData == null) {
            return false;
        }

        return mCurrentAuthData.auth_level > 0;
    }

    public void runOnAuth(Runnable runnable, boolean forceUpdate) {
        if(isAuthInProgress()) {
            Log.v(TAG, "runOnAuth: auth in progress, add to queue");
            mOnAuthQueue.add(runnable);
        }
        else {
            if(forceUpdate) {
                Log.v(TAG, "runOnAuth: run after update");
                updateAuth(runnable);
            }
            else {
                Log.v(TAG, "runOnAuth: run now");
                runnable.run();
            }
        }
    }

    @SuppressWarnings("unused")
    public void openWebViewOnAuth(String url, int missingOptionId) {
        openWebViewOnAuth(url, missingOptionId, false, null);
    }

    public void openWebViewOnAuth(String url, int missingOptionId, boolean forceAuthUpdate) {
        openWebViewOnAuth(url, missingOptionId, forceAuthUpdate, null);
    }

    public void openWebViewOnAuth(final String url, final int missingOptionId, boolean forceAuthUpdate, @Nullable final String targetInfohash) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if(isUserAuthenticated()) {
                    Log.v(TAG, "openWebViewOnAuth: user authenticated");
                    if(missingOptionId != 0 && AuthUtils.userLevelContainsOption(getAuthLevel(), missingOptionId)) {
                        if(targetInfohash != null) {
                            AceStreamEngineBaseApplication.startPlaybackByInfohash(targetInfohash);
                        }
                    }
                    else {
                        if(AceStreamEngineBaseApplication.showTvUi()) {
                            Intent intent = new Intent(PlaybackManager.this, AceStreamEngineBaseApplication.getLinkActivityClass());
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            intent.putExtra(Constants.EXTRA_MISSING_OPTION_ID, missingOptionId);
                            startActivity(intent);
                        }
                        else {
                            AceStreamEngineBaseApplication.startBrowserIntent(PlaybackManager.this, url);
                        }
                    }
                }
                else {
                    Log.v(TAG, "openWebViewOnAuth: need to authenticate");
                    // show login activity
                    Intent intent = new Intent(PlaybackManager.this, AceStreamEngineBaseApplication.getLoginActivityClass());
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.putExtra(AceStream.EXTRA_LOGIN_TARGET, "webview");
                    intent.putExtra(Constants.EXTRA_WEBVIEW_URL, url);
                    if(missingOptionId != 0) {
                        intent.putExtra(Constants.EXTRA_MISSING_OPTION_ID, missingOptionId);
                    }
                    if(targetInfohash != null) {
                        intent.putExtra(EXTRA_INFOHASH, targetInfohash);
                    }

                    startActivity(intent);
                }
            }
        };

        runOnAuth(runnable, forceAuthUpdate);
    }

    @Override
    protected void onEngineSessionStarted(EngineSession session) {
        // Update prefs because engine can get new ones from backend on session start.
        updateEnginePreferences();
    }

    @Override
    public void onSettingsUpdated() {
        updateEnginePreferences();
    }

    public void sendPairingCode(String code) {
        Log.d(TAG, "sendPairingCode");
        if(mCurrentDevice != null) {
            mCurrentDevice.getDevice().sendPairingKey(code);
        }
    }

    public void cancelPairing() {
        Log.d(TAG, "cancelPairing");
        if(mCastResultListener != null) {
            mCastResultListener.onError(getString(R.string.cannot_start_playback));
        }
        disconnectDevice();
    }

    private void showPairingCodeDialog(int type) {
        Context context = AceStream.context();
        Intent intent = new Intent(context, PairingDialogActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("type", type);
        context.startActivity(intent);
    }

    @Override
    public void onPairingRequired(ConnectableDevice device, DeviceService service, DeviceService.PairingType pairingType) {
        Log.d(TAG, "onPairingRequired: type=" + pairingType);

        switch (pairingType) {
            case FIRST_SCREEN:
                showPairingCodeDialog(0);
                break;

            case PIN_CODE:
            case MIXED:
                showPairingCodeDialog(1);
                break;

            case NONE:
            default:
                break;
        }
    }

    public static PlaybackManager getInstance() {
        return sInstance;
    }

    public boolean isBlackThemeEnabled() {
        //TODO: implement (get from settings)
        return true;
    }
}
