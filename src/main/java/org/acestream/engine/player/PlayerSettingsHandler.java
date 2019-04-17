package org.acestream.engine.player;

import java.util.Calendar;

public interface PlayerSettingsHandler {
    void setSleepTime(Calendar time);
    float getRate();
    void setRate(float rate);
    int getRepeatType();
    void toggleRepeatType();
    boolean getShuffle();
    void toggleShuffle();
    long getSubtitleDelay();
    void setSubtitleDelay(long delay);
    long getAudioDelay();
    void setAudioDelay(long delay);
    void seekToTime(long time);
    boolean hasPlaylist();
}
