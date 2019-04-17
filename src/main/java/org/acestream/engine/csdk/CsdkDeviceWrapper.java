package org.acestream.engine.csdk;

import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.support.v4.media.session.PlaybackStateCompat;

import com.connectsdk.device.ConnectableDevice;
import com.connectsdk.service.capability.MediaControl;
import com.connectsdk.service.capability.VolumeControl;

import org.acestream.sdk.Constants;
import org.acestream.sdk.interfaces.IRemoteDevice;
import org.acestream.sdk.utils.Logger;
import org.acestream.sdk.utils.VlcConstants;
import org.acestream.sdk.utils.VlcConstants.VlcState;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;

@SuppressWarnings({"unused", "WeakerAccess"})
public class CsdkDeviceWrapper implements IRemoteDevice {
    private final static String TAG = "AS/CsdkDeviceWrapper";
    private ConnectableDevice mDevice;

    public CsdkDeviceWrapper(@NonNull ConnectableDevice device) {
        mDevice = device;
    }

    public ConnectableDevice getDevice() {
        return mDevice;
    }

    public String getName() {
        return mDevice.getFriendlyName();
    }

    public String getId() {
        return mDevice.getId();
    }

    public String getIp() {
        return mDevice.getIpAddress();
    }

    @Override
    public boolean isAceCast() {
        return false;
    }

    public void play() {
        MediaControl mediaControl = mDevice.getCapability(MediaControl.class);
        if(mediaControl != null) {
            mediaControl.play(null);
        }
    }

    public void pause() {
        MediaControl mediaControl = mDevice.getCapability(MediaControl.class);
        if(mediaControl != null) {
            mediaControl.pause(null);
        }
    }

    public void stop() {
        stop(false);
    }

    public void stop(boolean disconnect) {
        Logger.v(TAG, "stop: disconnect=" + disconnect);
        MediaControl mediaControl = mDevice.getCapability(MediaControl.class);
        if(mediaControl != null) {
            mediaControl.stop(null);
        }
        if(disconnect) {
            mDevice.disconnect();
        }
    }

    public boolean isPlaying() {
        return getPlaybackState() == PlaybackStateCompat.STATE_PLAYING;
    }

    public boolean isConnected() {
        return mDevice.isConnected();
    }

    @Override
    @NonNull
    public String toString() {
        return "<CsdkDeviceWrapper: device=" + mDevice + ">";
    }

    public boolean isVideoPlaying() {
        // this means that video is not playing in local player
        return false;
    }

    public float getRate() {
        return 1.0f;
    }

    public void setRate(float rate, boolean save) {
    }

    public void navigate(int where) {
    }

    public MediaPlayer.Chapter[] getChapters(int title) {

        return null;
    }

    public MediaPlayer.Title[] getTitles() {
        return null;
    }

    public int getChapterIdx() {
        return 0;
    }

    public void setChapterIdx(int chapter) {
    }

    public int getTitleIdx() {
        return 0;
    }

    public void setTitleIdx(int title) {
    }

    public boolean updateViewpoint(float yaw, float pitch, float roll, float fov, boolean absolute) {
        return true;
    }

    @Override
    public boolean setAudioDigitalOutputEnabled(boolean enabled) {
        return true;
    }

    @Override
    public boolean setAudioOutput(String aout) {
        return false;
    }

    public boolean setVideoTrack(int index) {
        return true;
    }

    public boolean setAudioOutput(String aout, String device) {
        return true;
    }

    public Media.VideoTrack getCurrentVideoTrack() {
        return null;
    }

    public int getVideoTrack() {
        return 0;
    }

    public boolean addSubtitleTrack(String path, boolean select) {
        return true;
    }

    public boolean addSubtitleTrack(Uri uri, boolean select) {
        return true;
    }

    public boolean setAudioDelay(long delay) {
        return true;
    }

    public long getAudioDelay() {
        return 0;
    }

    public boolean setSpuDelay(long delay) {
        return true;
    }

    public long getSpuDelay() {
        return 0;
    }

    public void setEqualizer(MediaPlayer.Equalizer equalizer) {
    }

    public void setVideoScale(float scale) {
    }

    public void setVideoAspectRatio(@Nullable String aspect) {
    }

    public boolean isSeekable() {
        return true;
    }

    public boolean isPausable() {
        return true;
    }

    String getDeinterlaceMode() {
        return Constants.DEINTERLACE_MODE_DISABLED;
    }

    public int getVideoSize() {
        return VlcConstants.SURFACE_BEST_FIT;
    }

    public int getVolume() {
        return (int)(mDevice.getLastVolume() * 100);
    }

    public long getTime() {
        return mDevice.getLastPosition();
    }

    public long getLength() {
        return mDevice.getLastDuration();
    }

    public void setTime(long value) {
        MediaControl mediaControl = mDevice.getCapability(MediaControl.class);
        if(mediaControl != null) {
            mediaControl.seek(value, null);
        }
    }

    public int setVolume(int value) {
        VolumeControl control = mDevice.getCapability(VolumeControl.class);
        if(control!= null) {
            control.setVolume(value / 100.0f, null);
        }
        return value;
    }

    public void setPosition(float pos) {
        if(getLength() > 0) {
            long time = (long)(getLength() * pos);
            setTime(time);
        }
    }

    public int getAudioTracksCount() {
        return 0;
    }

    public MediaPlayer.TrackDescription[] getAudioTracks() {
        return null;
    }

    public int getAudioTrack() {
        return -1;
    }

    public int getSpuTracksCount() {
        return 0;
    }

    public MediaPlayer.TrackDescription[] getSpuTracks() {
        return null;
    }

    public int getSpuTrack() {
        return -1;
    }

    public boolean setAudioTrack(int trackId) {
        return true;
    }

    public boolean setSpuTrack(int trackId) {
        return true;
    }

    @Override
    public void setVideoSize(String size) {
    }

    public float getPosition() {
        if(getLength() > 0) {
            return (float)(getTime()) / getLength();
        }
        else {
            return 0;
        }
    }

    public int getPlaybackState() {
        if(!mDevice.isConnected()) {
            return PlaybackStateCompat.STATE_NONE;
        }

        MediaControl.PlayStateStatus status = mDevice.getLastStatus();
        if(status == null) {
            return PlaybackStateCompat.STATE_NONE;
        }
        else {
            switch(status) {
                case Playing:
                    return PlaybackStateCompat.STATE_PLAYING;
                case Buffering:
                    return PlaybackStateCompat.STATE_BUFFERING;
                case Paused:
                    return PlaybackStateCompat.STATE_PAUSED;
                case Idle:
                    return PlaybackStateCompat.STATE_NONE;
                case Unknown:
                    return PlaybackStateCompat.STATE_NONE;
                case Finished:
                    return PlaybackStateCompat.STATE_STOPPED;
                default:
                    return PlaybackStateCompat.STATE_NONE;
            }
        }
    }

    public int getState() {
        MediaControl.PlayStateStatus status = mDevice.getLastStatus();
        if(status == null) {
            return VlcState.IDLE;
        }
        else {
            switch(status) {
                case Playing:
                    return VlcConstants.VlcState.PLAYING;
                case Buffering:
                    return VlcState.PLAYING;
                case Paused:
                    return VlcState.PAUSED;
                case Idle:
                    return VlcState.IDLE;
                case Unknown:
                    return VlcState.IDLE;
                case Finished:
                    return VlcState.ENDED;
                default:
                    return VlcState.IDLE;
            }
        }
    }

    public boolean equals(ConnectableDevice device) {
        return mDevice != null && mDevice.equals(device);
    }
}
