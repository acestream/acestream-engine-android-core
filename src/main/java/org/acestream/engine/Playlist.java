package org.acestream.engine;

import org.acestream.sdk.ContentStream;
import org.acestream.sdk.controller.api.TransportFileDescriptor;
import org.acestream.sdk.controller.api.response.MediaFilesResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class Playlist {
    private TransportFileDescriptor mDescriptor;
    private MediaFilesResponse mMetadata;
	private int mCurrent = -1;
    private List<PlaylistItem> mItems;
    private List<ContentStream> mStreams;
    private int mCurrentStreamIndex = -1;

    public Playlist() {
        mItems = new ArrayList<>();
        mStreams = new ArrayList<>();
    }

    public int getCurrentIndex() {
        return mCurrent;
    }

    public PlaylistItem getCurrentItem() {
        if(mCurrent != -1) {
            return mItems.get(mCurrent);
        }
        else {
            return null;
        }
    }

    public PlaylistItem getPrevItem() {
        if(mCurrent > 0) {
            return mItems.get(mCurrent-1);
        }
        else {
            return null;
        }
    }

    public PlaylistItem getNextItem() {
        if(mCurrent < mItems.size()-1) {
            return mItems.get(mCurrent+1);
        }
        else {
            return null;
        }
    }

    public void setCurrent(int value) {
        if(value < 0) {
            value = 0;
        }
        else if(value >= mItems.size()) {
            value = mItems.size() - 1;
        }

        mCurrent = value;
    }

    public void setCurrentByFileIndex(int value) {
        for(int i = 0; i < mItems.size(); i++) {
            if(mItems.get(i).getFileIndex() == value) {
                mCurrent = i;
            }
        }
    }

    public int getSize() {
        return mItems.size();
    }

    public PlaylistItem getItem(int i) {
        return mItems.get(i);
    }

    public void addItem(PlaylistItem item) {
        if(mCurrent == -1) {
            mCurrent = 0;
        }
        mItems.add(item);
    }

    public List<ContentStream> getStreams() {
        return mStreams;
    }

    public void setStreams(List<ContentStream> streams) {
        mStreams = streams;
    }

    public void addStream(ContentStream stream) {
        mStreams.add(stream);
    }

    public int getCurrentStreamIndex() {
        return mCurrentStreamIndex;
    }

    public void setCurrentStreamIndex(int index) {
        mCurrentStreamIndex = index;
    }

    public void sort() {
        Collections.sort(mItems, new Comparator<PlaylistItem>() {
            @Override
            public int compare(PlaylistItem item1, PlaylistItem item2) {
                return item1.getTitle().compareToIgnoreCase(item2.getTitle());
            }
        });
    }

    public TransportFileDescriptor getContentDescriptor() {
        return mDescriptor;
    }

    public void setContentDescriptor(TransportFileDescriptor descriptor) {
        mDescriptor = descriptor;
    }

    public PlaylistItem findByFileIndex(int fileIndex) {
        for(PlaylistItem item: mItems) {
            if(item.getFileIndex() == fileIndex) {
                return item;
            }
        }
        return null;
    }

    public MediaFilesResponse getMetadata() {
        return mMetadata;
    }

    public void setMetadata(MediaFilesResponse metadata) {
        mMetadata = metadata;
    }

	@Override
	public String toString() {
		return "playlist";
	}
}

