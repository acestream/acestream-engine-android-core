package org.acestream.engine;

import android.net.Uri;
import androidx.annotation.NonNull;

import org.acestream.sdk.AceStream;
import org.acestream.sdk.Constants;
import org.acestream.sdk.TrackDescription;
import org.acestream.sdk.controller.api.TransportFileDescriptor;
import org.acestream.sdk.controller.api.response.MediaFilesResponse;

import java.util.ArrayList;
import java.util.List;

public class PlaylistItem {
    public String url;
    private List<TrackDescription> mAudioTracks;
    public int currentAudioTrack = 0;
    private List<TrackDescription> mSubtitleTracks;
    public int currentSubtitleTrack = 0;
    private Playlist mPlaylist;

    private MediaFilesResponse.MediaFile mMediaFile;

    public PlaylistItem(@NonNull Playlist playlist, @NonNull MediaFilesResponse.MediaFile mediaFile) {
        mPlaylist = playlist;
        mMediaFile = mediaFile;
        mAudioTracks = new ArrayList<>();
        mSubtitleTracks = new ArrayList<>();
    }

    public Playlist getPlaylist() {
        return mPlaylist;
    }

    public TransportFileDescriptor getDescriptor() {
        return mPlaylist.getContentDescriptor();
    }

    @NonNull
    public MediaFilesResponse.MediaFile getMediaFile() {
        return mMediaFile;
    }

    public String getTitle() {
        return mMediaFile.filename;
    }

    public int getFileIndex() {
        return mMediaFile.index;
    }

    public String getInfohash() {
        return mMediaFile.infohash;
    }

    public String getContentType() {
        return mMediaFile.type;
    }

    public void setContentType(String type) {
        mMediaFile.type = type;
    }

    public void clearAudioTracks() {
        mAudioTracks.clear();
    }

    public void addAudioTrack(TrackDescription description) {
        mAudioTracks.add(description);
    }

    public int getAudioTracksCount() {
        return mAudioTracks.size();
    }

    public List<TrackDescription> getAudioTracks() {
        return mAudioTracks;
    }

    public void clearSubtitleTracks() {
        mAudioTracks.clear();
    }

    public void addSubtitleTrack(TrackDescription description) {
        mSubtitleTracks.add(description);
    }

    public int getSubtitleTracksCount() {
        return mSubtitleTracks.size();
    }

    public List<TrackDescription> getSubtitleTracks() {
        return mSubtitleTracks;
    }

    public String getMimeType() {
        return getMimeType(false);
    }

    public String getMimeType(boolean forceHlsForLive) {
        String outputFormat;
        String mime = mMediaFile.mime;

        if(forceHlsForLive && mMediaFile.type.equals(org.acestream.sdk.Constants.CONTENT_TYPE_LIVE)) {
            outputFormat = "hls";
        }
        else if(mMediaFile.type.equals(org.acestream.sdk.Constants.CONTENT_TYPE_VOD)) {
            outputFormat = AceStreamEngineBaseApplication.getVodOutputFormat();
        }
        else {
            outputFormat = AceStreamEngineBaseApplication.getLiveOutputFormat();
        }

        if(outputFormat.equals("hls")) {
            // overwrite original mime for HLS output
            mime = Constants.MIME_HLS;
        }

        return mime;
    }

    public Uri getMrl() {
        return getDescriptor().getMrl(getFileIndex());
    }

    @Override
    public String toString() {
        return "PlaylistItem(infohash=" + mMediaFile.infohash + " type=" + mMediaFile.type + " mime=" + mMediaFile.mime + ")";
    }
}
