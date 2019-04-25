package org.acestream.engine;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import androidx.appcompat.app.ActionBar;
import androidx.core.content.FileProvider;

import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.acestream.engine.aliases.App;
import org.acestream.sdk.AceStream;
import org.acestream.sdk.utils.UiUtils;

import java.io.File;

public class WebViewActivity
    extends PlaybackManagerAppCompatActivity
    implements View.OnClickListener
    {

	private final static String TAG = "AS/WV";
	private final static String USER_AGENT_SUFFIX = "AceStreamAndroid/" + AceStream.getApplicationVersionName() + "/" + AceStreamEngineBaseApplication.getStringAppMetadata("arch") + "/" + (AceStream.isAndroidTv() ? "ATV" : "R");
    protected WebView mWebView;
    private ProgressBar mProgressBar;
    private View mErrorOverlay;
    private TextView mErrorText;
    private Menu mMenu;
    private String mNotificationId = null;
    private boolean mRequireEngine = true;
    private boolean mNavigationStarted = false;
    private String mTargetUrl = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            setContentView(R.layout.l_webview_activity);
        }
        catch(Throwable e) {
            AceStream.toast("Failed to open WebView");
            AceStreamEngineBaseApplication.setWebViewAvailable(false);
            Log.e(TAG, "Failed to create activity", e);
            finish();
            return;
        }
        mWebView = findViewById(R.id.webview);
        mProgressBar = findViewById(R.id.progress);
        mErrorOverlay = findViewById(R.id.error_overlay);
        mErrorText = findViewById(R.id.error_text);

        findViewById(R.id.button_retry).setOnClickListener(this);
        findViewById(R.id.button_cancel).setOnClickListener(this);

        mNotificationId = getIntent().getStringExtra(Constants.EXTRA_WEBVIEW_NOTIFICATION_ID);
        mRequireEngine = getIntent().getBooleanExtra(Constants.EXTRA_WEBVIEW_REQUIRE_ENGINE, true);

        // Log js console messages
        mWebView.setWebChromeClient(new WebChromeClient()
        {
            @Override
            public boolean onConsoleMessage(ConsoleMessage cm) {
                Log.v("AS/WV/console", cm.message() + " at " + cm.sourceId() + ":" + cm.lineNumber());
                return true;
            }
        });

        showProgress(true);
        initActionBar();

        if(!mRequireEngine) {
            startNavigation();
        }

        if(AceStreamEngineBaseApplication.showTvUi()) {
            UiUtils.applyOverscanMargin(this);
        }
    }

    private void initActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null) {
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(false);
        }
    }

    private void showProgress(boolean visible) {
        if(visible) {
            mProgressBar.setVisibility(View.VISIBLE);
        }
        else {
            mProgressBar.setVisibility(View.GONE);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void startNavigation() {
        if(mNavigationStarted) {
            App.v(TAG, "navigation already started");
            return;
        }
        mNavigationStarted = true;

        Intent intent = getIntent();
        Uri uri = intent.getData();
        mTargetUrl = intent.getStringExtra(Constants.EXTRA_WEBVIEW_URL);

        if(mTargetUrl == null && uri != null) {
            mTargetUrl = uri.toString();
        }

        Log.d(TAG, "startNavigation: targetUrl=" + mTargetUrl);

        if(mTargetUrl == null) {
            return;
        }

        // enable javascript
        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setUserAgentString(webSettings.getUserAgentString() + " " + USER_AGENT_SUFFIX);

        // open all links inside webview
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                updateUI();
                showProgress(false);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                Log.w(TAG, "error: code=" + errorCode
                    + " description=" + description
                    + " url=" + failingUrl
                );
                handleError(failingUrl, description);
            }

            @TargetApi(Build.VERSION_CODES.M)
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                Uri url = request.getUrl();
                Log.w(TAG, "error: code=" + error.getErrorCode()
                        + " description=" + error.getDescription()
                        + " url=" + url
                );

                if(url != null) {
                    handleError(url.toString(), error.getDescription());
                }
            }

            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                Log.w(TAG, "error:ssl: description=" + error.toString() + " url=" + error.getUrl());
                handleError(error.getUrl(), "SSL error");
            }

            @Override
            public void onReceivedHttpError(final WebView view, final WebResourceRequest request, WebResourceResponse errorResponse) {
                final int statusCode;
                final String errorDescription;
                final Uri url;

                // SDK < 21 does not provide statusCode
                if (Build.VERSION.SDK_INT < 21) {
                    statusCode = -1;
                    url = null;
                    errorDescription = null;
                } else {
                    statusCode = errorResponse.getStatusCode();
                    errorDescription = errorResponse.getReasonPhrase();
                    url = request.getUrl();
                }

                Log.w(TAG, "error: code=" + statusCode + " url=" + url);

                if(url != null) {
                    handleError(url.toString(), errorDescription);
                }
            }

            private void handleError(String url, CharSequence error) {
                if(TextUtils.equals(mWebView.getUrl(), url)) {
                    showErrorOverlay(error);
                }
            }
        });

        initJavascriptInterface();

        // init download listener
        initDownloadListener();

        loadUrl(mTargetUrl);
    }

    protected void initJavascriptInterface() {
        mWebView.addJavascriptInterface(new JsObject(), "acestreamAppHost");
    }

    protected void loadUrl(String targetUrl) {
        // load initial url
        mWebView.loadUrl(targetUrl);
    }

    private void showErrorOverlay(CharSequence error) {
        mErrorOverlay.setVisibility(View.VISIBLE);
        if(TextUtils.isEmpty(error)) {
            mErrorText.setVisibility(View.GONE);
        }
        else {
            mErrorText.setText(error);
            mErrorText.setVisibility(View.VISIBLE);
        }
    }

	private void initDownloadListener() {
	    mWebView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                Log.d(TAG, "onDownloadStart:"
                  + " url=" + url
                  + " userAgent=" + userAgent
                  + " contentDisposition=" + contentDisposition
                  + " mimeType=" + mimeType
                  + " contentLength=" + contentLength
                );

                if(url != null && url.endsWith(".apk")) {
                    mimeType = "application/vnd.android.package-archive";
                }

                try {
                    final Context ctx = AceStreamEngineBaseApplication.context();

                    Uri downloadUri = Uri.parse(url);
                    DownloadManager.Request request = new DownloadManager.Request(downloadUri);

                    String filename = downloadUri.getLastPathSegment();
                    final String destPath = Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS) + "/" + filename;

                    Log.d(TAG, "onDownloadStart: save to " + destPath);

                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED); //Notify client once download is completed!
                    request.setDestinationUri(Uri.parse("file://" + destPath));
                    request.setMimeType(mimeType);

                    final DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                    if(dm == null) {
                        return;
                    }
                    final long downloadId = dm.enqueue(request);
                    Toast.makeText(getApplicationContext(), "Downloading File", //To notify the Client that the file is being downloaded
                            Toast.LENGTH_LONG).show();

                    // Setup receiver to run installation when .apk is downloaded.
                    //
                    // This code allows user to install some APK from WebView,
                    // but it will work only if app was compiled with REQUEST_INSTALL_PACKAGES
                    // permission in manifest.
                    BroadcastReceiver onComplete = new BroadcastReceiver() {
                        public void onReceive(Context ctx, Intent intent) {
                            Log.d(TAG, "download completed");

                            Long dwnId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0);
                            if(dwnId == downloadId) {
                                final Uri contentUri = FileProvider.getUriForFile(
                                        ctx,
                                        ctx.getPackageName() + ".fileprovider",
                                        new File(destPath));

                                Log.d(TAG, "start intent: uri=" + contentUri);

                                Intent install = new Intent(Intent.ACTION_VIEW);
                                install.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                install.setDataAndType(contentUri, "application/vnd.android.package-archive");
                                startActivity(install);

                                ctx.unregisterReceiver(this);
                            }
                        }
                    };
                    //register receiver for when .apk download is compete
                    ctx.registerReceiver(onComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
                }
                catch(Throwable e) {
                    Log.e(TAG, "Failed to download file", e);
                }

            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.d(TAG, "onCreateOptionsMenu");
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.webview, menu);
        mMenu = menu;
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int i = item.getItemId();
        if (i == R.id.action_backward) {
            if (mWebView.canGoBack()) {
                mWebView.goBack();
            } else {
                finish();
            }
        } else if (i == R.id.action_forward) {
            if (mWebView.canGoForward()) {
                mWebView.goForward();
            }
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Check if the key event was the Back button and if there's history
        if ((keyCode == KeyEvent.KEYCODE_BACK) && mWebView.canGoBack()) {
            mWebView.goBack();
            return true;
        }
        // If it wasn't the Back key or there's no web page history, bubble up to the default
        // system behavior (probably exit the activity)
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onSupportNavigateUp() {
        Log.d(TAG, "onSupportNavigateUp");
        super.onSupportNavigateUp();
        finish();
        return true;
    }

    private void updateUI() {
        if(mMenu != null) {
            MenuItem item = mMenu.findItem(R.id.action_forward);
            if (item != null) {
                item.setEnabled(mWebView.canGoForward());
            }
        }
    }

    @Override
    public void onConnected(PlaybackManager service) {
        super.onConnected(service);

        if(!mNavigationStarted) {
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    startNavigation();
                }
            };
            mPlaybackManager.runOnAuth(runnable, false);
        }
    }

    @Override
    public void onClick(View view) {
        if(view == null)
            return;

        int i = view.getId();
        if (i == R.id.button_retry) {
            if (mTargetUrl != null) {
                loadUrl(mTargetUrl);
                mErrorOverlay.setVisibility(View.GONE);
            }

        } else if (i == R.id.button_cancel) {
            finish();

        }
    }

    protected class JsObject {
        @JavascriptInterface
        public int getHostVersionCode() {
            return AceStream.getApplicationVersionCode();
        }

        @JavascriptInterface
        public void setTitle(final String title) {
            Log.v(TAG, "setTitle: title=" + title);
            if (!TextUtils.isEmpty(title)) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        WebViewActivity.this.setTitle(title);
                    }
                });

            }
        }

        @JavascriptInterface
        public void setGdprConsent(boolean value) {
            Log.v(TAG, "setGdprConsent: value=" + value);
            AceStreamEngineBaseApplication.setGdprConsent(value);
        }

        @JavascriptInterface
        public void close() {
            Log.v(TAG, "close");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    WebViewActivity.this.finish();
                }
            });
        }

        @JavascriptInterface
        public void toast(final String message) {
            Log.v(TAG, "toast");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    AceStreamEngineBaseApplication.toast(message);
                }
            });
        }

        @JavascriptInterface
        public void updateAuth() {
            if(mPlaybackManager != null) {
                Log.v(TAG, "updateAuth");
                mPlaybackManager.updateAuth();
            }
            else {
                Log.v(TAG, "updateAuth: no playback manager");
            }
        }

        @JavascriptInterface
        public void startPlayback(String infohash) {
            Log.v(TAG, "startPlayback: infohash=" + infohash);
            AceStreamEngineBaseApplication.startPlaybackByInfohash(infohash, false);
            WebViewActivity.this.finish();
        }

        @JavascriptInterface
        public void dismissNotification() {
            Log.v(TAG, "dismissNotification: id=" + mNotificationId);
            if(mNotificationId != null) {
                AceStreamEngineBaseApplication.dismissNotification(mNotificationId);
            }
        }
    }
}
