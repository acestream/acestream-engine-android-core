package org.acestream.engine.prefs;

import androidx.annotation.Keep;

@Keep
public class NotificationData {
    public String id;
    public String type;
    public String url;
    // true when was shown to the user
    public boolean shown;
}
