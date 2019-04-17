package org.acestream.engine.player;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.acestream.engine.R;
import org.acestream.sdk.MediaItem;

public class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.ViewHolder> implements View.OnClickListener {
    private final static String TAG = "AS/PlaylistAdapter";

    private final RecyclerView mRecyclerView;
    private final PlaylistManager mPlaylist;
    private final Context mContext;
    private int mCurrentIndex;
    private boolean mDpadNavigation = false;

    public PlaylistAdapter(Context context, PlaylistManager manager, RecyclerView recyclerView) {
        mContext = context;
        mPlaylist = manager;
        mRecyclerView = recyclerView;
        mCurrentIndex = mPlaylist.getCurrentMediaPosition();
    }

    public void moveItem(int from, int to) {
        Log.v(TAG, "moveItem: from=" + from + " to=" + to);
        mPlaylist.moveItem(from, to);
        notifyItemMoved(from, to);
    }

    public boolean deleteItem(int pos) {
        Log.v(TAG, "deleteItem: pos=" + pos);
        if(mPlaylist.deleteItem(pos)) {
            notifyItemRemoved(pos);
            return true;
        }
        else {
            // restore swiped out item
            notifyItemChanged(pos);
            return false;
        }
    }

    public void moveFocusUp() {
        setCurrentIndex(mCurrentIndex - 1);
    }

    public void moveFocusDown() {
        setCurrentIndex(mCurrentIndex + 1);
    }

    public void setCurrentIndex(int index) {
        if(index < 0 || index >= mPlaylist.size()) {
            return;
        }

        mDpadNavigation = true;

        int prev = mCurrentIndex;
        mCurrentIndex = index;
        notifyItemChanged(prev);
        notifyItemChanged(index);
    }

    public void resetCurrentIndex() {
        mCurrentIndex = mPlaylist.getCurrentMediaPosition();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.ace_playlist_item, parent, false);
        v.setOnClickListener(this);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        boolean isCurrent = (position == mCurrentIndex);
        MediaItem item = mPlaylist.getItem(position);
        if(item != null) {
            holder.setTitle(item.getTitle());
            holder.setCurrent(isCurrent);
        }
        else {
            holder.setTitle("");
        }
    }

    @Override
    public int getItemCount() {
        return mPlaylist.size();
    }

    @Override
    public void onClick(View view) {
        int pos = mDpadNavigation ? mCurrentIndex : mRecyclerView.getChildLayoutPosition(view);
        mPlaylist.playIndex(pos);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private TextView mTitle;

        public ViewHolder(View v) {
            super(v);
            mTitle = v.findViewById(R.id.title);
        }

        public void onClick(View v) {
        }

        public void setTitle(String title) {
            mTitle.setText(title);
        }

        public void setCurrent(boolean isCurrent) {
            mTitle.setTextColor(mContext.getResources().getColor(isCurrent
                    ? R.color.controls_dark
                    : R.color.bluegrey100));
        }
    }
}
