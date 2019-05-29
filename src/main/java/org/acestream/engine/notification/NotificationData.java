package org.acestream.engine.notification;

import java.util.Map;
import androidx.annotation.Keep;

@Keep
public class NotificationData {
    public String action;
    public int flags;
    public String uri;
    public String mime;
    public Map<String,String> extras;
    public BrowserIntentData target_url;
}
