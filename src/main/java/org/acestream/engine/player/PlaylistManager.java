package org.acestream.engine.player;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import androidx.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import org.acestream.engine.AceStreamEngineBaseApplication;
import org.acestream.engine.PlaybackManager;
import org.acestream.engine.aliases.App;
import org.acestream.sdk.AceStream;
import org.acestream.sdk.EngineSession;
import org.acestream.sdk.EngineStatus;
import org.acestream.sdk.MediaItem;
import org.acestream.sdk.P2PItemStartListener;
import org.acestream.sdk.controller.EngineApi;
import org.acestream.sdk.controller.api.response.VastTag;
import org.acestream.sdk.interfaces.IAceStreamManager;
import org.acestream.sdk.player.api.AceStreamPlayer;
import org.acestream.sdk.player.api.AceStreamPlayer.PlaylistItem;
import org.acestream.sdk.utils.Logger;
import org.acestream.sdk.utils.MiscUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PlaylistManager {
    private final static String TAG = "AS/PlaylistManager";

    public final static int REPEAT_NONE = 0;
    public final static int REPEAT_ONE = 1;
    public final static int REPEAT_ALL = 2;

    private final static int NEXT_INDEXES_LOOK_AHEAD = 5;

    private Context mContext;
    private Player mPlayer;
    private List<MediaItem> mItems = new ArrayList<>();
    private int mPosition = -1;
    private int mHistoryPosition = -1;
    private int mRepeatType = REPEAT_NONE;
    private boolean mShuffle = false;
    private List<Integer> mHistory = new ArrayList<>();

    interface Player {
        void play();
        void play(MediaItem item);
        void pause();
        void stop();
        PlaybackManager getPlaybackManager();
        void onP2PSessionStarted(VastTag[] vastTags);
        void onP2PPlaybackStarted(Uri uri);
        void onP2PStarting();
        void onP2PFailed(String errorMessage);
        void setEngineStatus(EngineStatus status);
        void onCurrentItemChanged(int position);
        void onBeforePlaylistPositionChanged(int newPosition);
        void onPlaylistUpdated();
        String getProductKey();
    }

    private MediaItem.UpdateListener mMediaUpdateListener = new MediaItem.UpdateListener() {
        @Override
        public void onTitleChange(MediaItem item, String title) {
            if(item == getCurrentItem()) {
                mPlayer.onCurrentItemChanged(mPosition);
            }
        }

        @Override
        public void onP2PInfoChanged(MediaItem item, String infohash, int fileIndex) {
        }

        @Override
        public void onLiveChanged(MediaItem item, int live) {
        }
    };

    public PlaylistManager(Context context, Player player) {
        mContext = context;
        mPlayer = player;
    }

    public void loadPlaylistFromJson(String jsonData, int pos, boolean autoPlay, boolean save) {
        clear();

        AceStreamPlayer.PlaylistItem[] playlist = AceStreamPlayer.Playlist.fromJson(jsonData);
        for(int i = 0; i < playlist.length; i++) {
            if(TextUtils.isEmpty(playlist[i].uri)) {
                Logger.e(TAG, "loadPlaylistFromJson: skip item with empty URI: idx=" + i + " title=" + playlist[i].title + " id=" + playlist[i].id);
                continue;
            }
            addItem(Uri.parse(playlist[i].uri), playlist[i].title, playlist[i].id);
        }

        if(size() > 0) {
            if(pos < 0) {
                pos = 0;
            }
            else if(pos >= size()) {
                pos = size() - 1;
            }

            updateHistory();
            setCurrentPosition(pos);

            if(autoPlay) {
                playIndex(pos);
            }
        }

        if(save) {
            savePlaylist();
        }

        mPlayer.onPlaylistUpdated();
    }

    public void loadPlaybackIntent(@NonNull final Intent intent, final boolean autoPlay) {
        Uri uri = intent.getData();
        if(uri == null) {
            Log.e(TAG, "loadPlaybackIntent: empty uri");
            return;
        }

        String jsonData =  AceStreamPlayer.Playlist.fromSingleItem(uri.toString(), uri.toString(), 0);
        loadPlaylistFromJson(jsonData, 0, autoPlay, true);
    }

    public void loadPlaylistFromIntent(final Intent intent, final boolean autoPlay) {
        int pos = 0;
        String jsonData = null;
        if(intent != null) {
            pos = intent.getIntExtra(AceStreamPlayer.EXTRA_PLAYLIST_POSITION, 0);
            jsonData = intent.getStringExtra(AceStreamPlayer.EXTRA_PLAYLIST);
        }
        loadPlaylistFromJson(jsonData, pos, autoPlay, true);
    }

    public void clear() {
        resetP2PItems(mPlayer.getPlaybackManager(), -1);
        mItems.clear();
        mHistory.clear();
        setCurrentPosition(-1);
        mPlayer.onPlaylistUpdated();
    }

    private void addItem(Uri uri, String title, long id) {
        mItems.add(new MediaItem(mContext,
                uri,
                title,
                id,
                null,
                null,
                mMediaUpdateListener));
        mHistory.add(mItems.size() - 1);
    }

    public boolean hasMedia() {
        return size() > 0 && mPosition >= 0 && mPosition < size();
    }

    public void savePlaylist() {
        SharedPreferences prefs = AceStreamEngineBaseApplication.getPreferences();

        PlaylistItem[] playlist = new PlaylistItem[size()];
        for(int i = 0; i < mItems.size(); i++) {
            playlist[i] = new PlaylistItem(
                    mItems.get(i).getUri().toString(),
                    mItems.get(i).getTitle(),
                    mItems.get(i).getId());
        }

        prefs.edit()
            .putString("last_playlist_data", AceStreamPlayer.Playlist.toJson(playlist))
            .putInt("last_playlist_position", mPosition)
            .apply();
    }

    public void loadLastPlaylist(boolean autoPlay) {
        SharedPreferences prefs = AceStreamEngineBaseApplication.getPreferences();

        String jsonData = prefs.getString("last_playlist_data", null);
        int pos = prefs.getInt("last_playlist_position", 0);

        Log.v(TAG, "loadLastPlaylist: pos=" + pos + " data=" + jsonData);

        loadPlaylistFromJson(jsonData, pos, autoPlay, false);
    }

    public MediaItem getItem(int position) {
        if(position >= 0 && position < size()) {
            return mItems.get(position);
        }
        else {
            return null;
        }
    }

    public MediaItem getCurrentItem() {
        return getItem(mPosition);
    }

    public List<MediaItem> getPlaylist() {
        return mItems;
    }

    public void playIndex(int index) {
        final MediaItem media = getItem(index);
        if(media == null) {
            Log.v(TAG, "playIndex: invalid index: " + index);
            return;
        }

        if(index != mPosition) {
            mPlayer.onBeforePlaylistPositionChanged(index);
        }
        setCurrentPosition(index);

        // Handle situation when some external app started engine session and passed
        // playback url to our app or when direct HTTP API url is passed.
        // In such case we parse infohash from playback url and create TFD from it.
        boolean restartSessionWithOriginalInitiator = false;
        if(!media.isP2PItem()) {
            String acestreamLink = AceStream.parseAceStreamHttpApiUrl(media.getUri());
            if(acestreamLink == null) {
                String infohash = AceStream.parseAceStreamPlaybackUrl(media.getUri());
                if (infohash != null) {
                    acestreamLink = "acestream:?infohash=" + infohash;
                }
            }

            if(acestreamLink != null) {
                Uri newUri = Uri.parse(acestreamLink);
                Log.v(TAG, "playIndex: update uri: " + media.getUri() + "->" + newUri);
                media.setUri(newUri);
                restartSessionWithOriginalInitiator = true;
            }
        }
        final boolean fRestartSessionWithOriginalInitiator = restartSessionWithOriginalInitiator;

        if(media.isP2PItem() && media.getPlaybackUri() == null) {
            //TODO: handle stream index
            final int streamIndex = 0;

            final List<Integer> nextFileIndexes = new ArrayList<>(NEXT_INDEXES_LOOK_AHEAD);
            for(int i=0; i < NEXT_INDEXES_LOOK_AHEAD; i++) {
                int pos = getNextPosition(mHistoryPosition+i);
                if(pos == -1) {
                    break;
                }
                else {
                    MediaItem item = getItem(mHistory.get(pos));
                    if(item != null) {
                        nextFileIndexes.add(item.getP2PFileIndex());
                    }
                }
            }

            final PlaybackManager pm = mPlayer.getPlaybackManager();
            if(pm == null) {
                Log.w(TAG, "playIndex: missing pm");
                return;
            }

            // 1 - connect to engine
            // 2 - start session
            // 3 - start playback
            mPlayer.onP2PStarting();
            pm.getEngine(new IAceStreamManager.EngineStateCallback() {
                @Override
                public void onEngineConnected(@NonNull IAceStreamManager playbackManager, @NonNull EngineApi engineApi) {
                    int[] next = new int[nextFileIndexes.size()];
                    for(int i=0; i < nextFileIndexes.size(); i++) {
                        next[i] = nextFileIndexes.get(i);
                    }

                    media.startP2P(pm, next, streamIndex, fRestartSessionWithOriginalInitiator, mPlayer.getProductKey(), new P2PItemStartListener() {
                        @Override
                        public void onSessionStarted(EngineSession session) {
                            mPlayer.onP2PSessionStarted(session.vastTags);
                        }

                        @Override
                        public void onPrebufferingDone() {
                            mPlayer.onP2PPlaybackStarted(media.getPlaybackUri());
                            mPlayer.play(media);
                        }

                        @Override
                        public void onError(String error) {
                            mPlayer.onP2PFailed(error);
                        }
                    });
                }
            });
        }
        else {
            mPlayer.onP2PStarting();
            mPlayer.play(media);
        }
    }

    private int getNextPosition() {
        return getNextPosition(mHistoryPosition);
    }

    private int getNextPosition(int startFrom) {
        if(size() < 2) {
            return -1;
        }

        if(mRepeatType == REPEAT_ONE) {
            return -1;
        }

        int position = startFrom + 1;
        if(position >= mHistory.size()) {
            if(mRepeatType == REPEAT_ALL) {
                // wrap position
                position = position - mHistory.size();
                if(position >= mHistory.size()) {
                    // sanity check after wrapping
                    position = 0;
                }
            }
            else {
                return -1;
            }
        }

        return position;
    }

    public boolean next() {
        int position = getNextPosition();
        App.v(TAG, "next: nextpos=" + position + " history=" + MiscUtils.dump(mHistory));

        if(position == -1) {
            return false;
        }

        playIndex(mHistory.get(position));
        return true;
    }

    public boolean previous() {
        if(size() < 2) {
            return false;
        }

        if(mRepeatType == REPEAT_ONE) {
            return true;
        }

        int position = mHistoryPosition - 1;
        App.v(TAG, "previous: nextpos=" + position + " history=" + MiscUtils.dump(mHistory));
        if(position < 0) {
            if(mRepeatType == REPEAT_ALL) {
                position = mHistory.size() - 1;
            }
            else {
                return false;
            }
        }

        playIndex(mHistory.get(position));
        return true;
    }

    public int getCurrentMediaPosition() {
        return mPosition;
    }

    public int size() {
        return mItems.size();
    }

    public int getRepeatType() {
        return mRepeatType;
    }

    public void setShuffle(boolean enabled) {
        mShuffle = enabled;
    }

    public boolean getShuffle() {
        return mShuffle;
    }

    public void setRepeatType(int type) {
        mRepeatType = type;
    }

    private void setCurrentPosition(int position) {
        if(position != mPosition) {
            mPosition = position;
            // set uri to null for all p2p items except current
            resetP2PItems(mPlayer.getPlaybackManager(), position);
            mPlayer.onCurrentItemChanged(position);
            mHistoryPosition = mHistory.indexOf(position);
        }
    }

    public void resetP2PItems(PlaybackManager playbackManager, int currentIndex) {
        for(int i = 0; i < mItems.size(); i++) {
            if(i != currentIndex) {
                mItems.get(i).resetP2PItem(playbackManager);
            }
        }
    }

    public Player getPlayer() {
        return mPlayer;
    }

    public void moveItem(int from, int to) {
        Log.v(TAG, "moveItem: from=" + from + " to=" + to + " current=" + mPosition);
        Collections.swap(mItems, from, to);

        if(mPosition == from) {
            mPosition = to;
        }
        else if(from > mPosition && to > mPosition) {
            // position didn't change
        }
        else if(from < mPosition && to < mPosition) {
            // position didn't change
        }
        else if (to > from) {
            mPosition -= 1;
        }
        else {
            mPosition += 1;
        }

        if(mShuffle) {
            //TODO: optimize to avoid reshuffling
            updateHistory();
        }
        else {
            mHistoryPosition = mPosition;
        }
    }

    public boolean deleteItem(int pos) {
        Log.v(TAG, "deleteItem: pos=" + pos + " current=" + mPosition + " size=" + size());
        if(size() < 2) {
            // cannot delete single item
            return false;
        }

        int newPos;
        if(mPosition < pos) {
            // position didn't change
            newPos = mPosition;
        }
        else if(mPosition > pos) {
            // shift pos
            newPos = mPosition - 1;
        }
        else {
            // cannot remove current item
            return false;
        }

        mItems.remove(pos);
        mPosition = newPos;

        if(mShuffle) {
            //TODO: optimize to avoid reshuffling
            mHistory.remove(Integer.valueOf(pos));
            updateHistory();
        }
        else {
            mHistoryPosition = mPosition;
        }

        return true;
    }

    public void toggleRepeatType() {
        if(mRepeatType == REPEAT_NONE)
            setRepeatType(REPEAT_ONE);
        else if(mRepeatType == REPEAT_ONE)
            setRepeatType(REPEAT_ALL);
        else
            setRepeatType(REPEAT_NONE);
    }

    public void toggleShuffle() {
        setShuffle(!mShuffle);
        updateHistory();
    }

    private void updateHistory() {
        if(mShuffle) {
            Collections.shuffle(mHistory);

            // Find current position in shuffled list
            for(int i=0; i < mHistory.size(); i++) {
                if(mHistory.get(i) == mPosition) {
                    mHistoryPosition = i;
                }
            }
        }
        else {
            mHistory.clear();
            for(int i=0; i < mItems.size(); i++)
                mHistory.add(i);
            mHistoryPosition = mPosition;
        }
    }

    public boolean isSamePlaylist(String jsonData) {
        PlaylistItem[] playlist;
        try {
            playlist = AceStreamPlayer.Playlist.fromJson(jsonData);
        }
        catch(Throwable e) {
            Log.e(TAG, "isSamePlaylist: failed to parse playlist", e);
            return false;
        }

        if(playlist.length != mItems.size()) {
            return false;
        }

        // Compate URIs
        for(int i = 0; i < playlist.length; i++) {
            if(!TextUtils.equals(playlist[i].uri, mItems.get(i).getUri().toString())) {
                return false;
            }
        }

        return true;
    }
}
