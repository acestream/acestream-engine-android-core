package org.acestream.engine.controller;

import android.os.RemoteException;
import android.text.TextUtils;

import com.google.gson.reflect.TypeToken;

import org.acestream.engine.AceStreamEngineBaseApplication;
import org.acestream.engine.prefs.ExtendedEnginePreferences;
import org.acestream.engine.service.v0.IAceStreamEngine;
import org.acestream.sdk.AceStream;
import org.acestream.sdk.controller.EngineApi;
import org.acestream.sdk.controller.api.AuthCredentials;
import org.acestream.sdk.controller.api.response.AuthData;
import org.acestream.sdk.controller.api.response.EngineApiResponse;
import org.acestream.sdk.controller.api.response.RequestAdsResponse;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ExtendedEngineApi extends EngineApi {

    public ExtendedEngineApi(IAceStreamEngine service) {
        super(service);

        try {
            mAccessToken = service.getAccessToken();
        }
        catch(RemoteException e) {
            throw new IllegalStateException(e);
        }
    }

    public void getAuth(@NonNull final Callback<AuthData> callback) {
        String url = "/webui/app/" + mAccessToken + "/user/auth/get";
        apiCall(url, null, callback, new TypeToken<EngineApiResponse<AuthData>>(){});
    }

    public void updateAuth(
            @Nullable final AuthCredentials credentials,
            @NonNull final Callback<AuthData> callback
    ) {
        String url = "/webui/app/" + mAccessToken + "/user/auth/update";
        Map<String, String> params = new HashMap<>();

        if(credentials == null) {
            // Refresh with current auth data
            params.put("refresh", "1");
        }
        else {
            params.put("auth_method", credentials.getTypeString());
            if (credentials.getType() == AuthCredentials.AuthMethod.AUTH_ACESTREAM) {
                if (!TextUtils.isEmpty(credentials.getLogin())) {
                    params.put("login", credentials.getLogin());
                }
                if (!TextUtils.isEmpty(credentials.getPassword())) {
                    params.put("password", credentials.getPassword());
                }
            } else {
                if (!TextUtils.isEmpty(credentials.getToken())) {
                    params.put("token", credentials.getToken());
                }
            }
        }

        apiCall(url, params, callback, new TypeToken<EngineApiResponse<AuthData>>(){});
    }

    public void signIn(
            @NonNull final AuthCredentials credentials,
            boolean autoRegister,
            @NonNull final Callback<AuthData> callback
    ) {
        String url = "/webui/app/" + mAccessToken + "/user/auth/signin";
        Map<String, String> params = new HashMap<>();

        params.put("auth_method", credentials.getTypeString());
        if(autoRegister) {
            params.put("auto_register", "1");
        }
        if (credentials.getType() == AuthCredentials.AuthMethod.AUTH_ACESTREAM) {
            if (!TextUtils.isEmpty(credentials.getLogin())) {
                params.put("login", credentials.getLogin());
            }
            if (!TextUtils.isEmpty(credentials.getPassword())) {
                params.put("password", credentials.getPassword());
            }
        } else {
            if (!TextUtils.isEmpty(credentials.getToken())) {
                params.put("token", credentials.getToken());
            }
        }

        apiCall(url, params, callback, new TypeToken<EngineApiResponse<AuthData>>(){});
    }

    public void logout(@Nullable final Callback<Boolean> callback) {
        String url = "/webui/app/" + mAccessToken + "/user/auth/logout";
        apiCall(url, null, callback, new TypeToken<EngineApiResponse<Boolean>>(){});
    }

    public void getPreferences(@NonNull final Callback<ExtendedEnginePreferences> callback) {
        String url = "/webui/app/" + mAccessToken + "/settings/get?api_version=2";
        apiCall(url, null, callback, new TypeToken<EngineApiResponse<ExtendedEnginePreferences>>(){});
    }

    public void getRawPreferences(@NonNull final Callback<String> callback) {
        String url = "/webui/app/" + mAccessToken + "/settings/get?api_version=1";
        apiCall(url, null, callback, null);
    }

    public void shutdown(@Nullable final Callback<Boolean> callback) {
        String url = "/webui/app/" + mAccessToken + "/cmd/shutdown";
        apiCall(url, null, callback, new TypeToken<EngineApiResponse<Boolean>>(){});
    }

    public void clearCache(@Nullable final Callback<Boolean> callback) {
        String url = "/webui/app/" + mAccessToken + "/cmd/clearcache";
        apiCall(url, null, callback, new TypeToken<EngineApiResponse<Boolean>>(){});
    }

    public void setPreference(String key, String value, @Nullable final Callback<Boolean> callback) {
        String url = "/webui/app/" + mAccessToken + "/settings/set";

        Map<String, String> params = new HashMap<>();
        params.put(key, value);

        apiCall(url, params, callback, new TypeToken<EngineApiResponse<Boolean>>(){});
    }

    public void requestAds(
            String placement,
            String infohash,
            int isLive,
            boolean forceAds,
            String debugOptions,
            @NonNull final Callback<RequestAdsResponse> callback) {
        String url = "/server/api?method=request_ads";

        Map<String, String> params = new HashMap<>();
        params.put("proxy_vast_response", "1");
        params.put("gdpr_consent", AceStreamEngineBaseApplication.getGdprConsent() ? "1" : "0");
        params.put("is_live", String.valueOf(isLive));
        params.put("force_ads", forceAds ? "1" : "0");

        if(!TextUtils.isEmpty(placement)) {
            params.put("placement", placement);
        }
        if(!TextUtils.isEmpty(infohash)) {
            params.put("infohash", infohash);
        }
        if(!TextUtils.isEmpty(debugOptions)) {
            params.put("debug_options", debugOptions);
        }

        apiCall(url,
                params,
                callback,
                new TypeToken<EngineApiResponse<RequestAdsResponse>>() {});
    }
}
