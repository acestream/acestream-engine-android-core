package org.acestream.engine;

public abstract class Constants {
    // How long to skip maintain notifications (milliseconds)
    public static final int MAINTAIN_INTENT_SNOOZE_INTERVAL = 86400000;

    public static final String PREF_MAINTAIN_INTENT_SNOOZE_UNTIL = "org.acestream.PREF_MAINTAIN_INTENT_SNOOZE_UNTIL";

    public static final String EXTRA_NOTIFICATION_ID = "android.intent.extra.NOTIFICATION_ID";
    public static final String EXTRA_WEBVIEW_URL = "org.acestream.EXTRA_WEBVIEW_URL";
    public static final String EXTRA_WEBVIEW_NOTIFICATION_ID = "org.acestream.EXTRA_WEBVIEW_NOTIFICATION_ID";
    public static final String EXTRA_WEBVIEW_REQUIRE_ENGINE = "org.acestream.EXTRA_WEBVIEW_REQUIRE_ENGINE";
    public static final String EXTRA_MISSING_OPTION_ID = "org.acestream.EXTRA_MISSING_OPTION_ID";


    public static final String EXTRA_ACTION = "org.acestream.EXTRA_ACTION";
    public static final String EXTRA_ACTION_SIGN_IN_ACESTREAM = "org.acestream.EXTRA_ACTION_SIGN_IN_ACESTREAM";
    public static final String EXTRA_ACTION_SIGN_IN_GOOGLE = "org.acestream.EXTRA_ACTION_SIGN_IN_GOOGLE";

    public static final String ACTION_MOBILE_NETWORK_DIALOG_RESULT = "org.acestream.MOBILE_NETWORK_DIALOG_RESULT";
    public static final String MOBILE_NETWORK_DIALOG_RESULT_PARAM_ENABLED = "org.acestream.MOBILE_NETWORK_DIALOG_RESULT_ENABLED";
    public static final String ACTION_NOTIFICATION_MAINTAIN_SNOOZE = "org.acestream.ACTION_NOTIFICATION_MAINTAIN_SNOOZE";
    public static final String ACTION_NOTIFICATION_MAINTAIN_APPLY = "org.acestream.ACTION_NOTIFICATION_MAINTAIN_APPLY";

    public final static String PREF_KEY_NOTIFICATIONS = "notifications";

    public final static String ADMOB_TEST_APP_ID = "ca-app-pub-3940256099942544~3347511713";
    public final static String ADMOB_TEST_BANNER = "ca-app-pub-3940256099942544/6300978111";
    public final static String ADMOB_TEST_REWARDED_VIDEO = "ca-app-pub-3940256099942544/5224354917";
    public final static String ADMOB_TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712";

    public final static String PREF_KEY_AD_SEGMENT = "ad_segment";
    public final static int PREF_DEFAULT_AD_SEGMENT = 50; // 50=0.5
}
