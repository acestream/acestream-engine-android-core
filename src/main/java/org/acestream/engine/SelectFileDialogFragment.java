package org.acestream.engine;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.Arrays;
import java.util.Comparator;

public class SelectFileDialogFragment extends DialogFragment {

    private final static String TAG = "AceStream/SelectFile";

    public interface SelectFileDialogListener {
        void onFileSelected(int fileIndex);
        void onDialogCancelled();
    }

    private SelectFileDialogListener mListener;

    @SuppressWarnings("deprecation")
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mListener = (SelectFileDialogListener) activity;
    }

    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle args = getArguments();
        if(args == null) {
            throw new IllegalStateException("missing arguments");
        }

        String[] fileNames = args.getStringArray("fileNames");
        int[] fileIndexes = args.getIntArray("fileIndexes");

        if(fileNames == null) {
            throw new IllegalStateException("missing file names array");
        }

        if(fileIndexes == null) {
            throw new IllegalStateException("missing file indexes array");
        }

        if(fileIndexes.length != fileNames.length) {
            throw new IllegalStateException("array length mismatch");
        }

        ContentItem[] items = new ContentItem[fileIndexes.length];
        for (int i = 0; i < items.length; i++) {
            items[i] = new ContentItem();
            items[i].fileIndex = fileIndexes[i];
            items[i].fileName = fileNames[i];
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.select_file)
            .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    if (mListener != null) {
                        mListener.onDialogCancelled();
                    }
                }
            });

        try {
            Arrays.sort(items, new Comparator<ContentItem>() {
                @Override
                public int compare(ContentItem item1, ContentItem item2) {
                    return item1.fileName.compareToIgnoreCase(item2.fileName);
                }
            });

            ListView lw = new ListView(getActivity());
            lw.setAdapter(new MyAdapter(items));
            lw.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    view.performClick();
                }
            });
            builder.setView(lw);
        } catch (Exception e) {
            Log.e(TAG, "Failed to create adapter", e);
        }

        return builder.create();
    }

    private class ContentItem {
        public String fileName;
        public int fileIndex;
    }

    private class MyAdapter extends BaseAdapter {
        private final static String TAG = "AceStream/Adapter";
        private ContentItem[] mItems;

        MyAdapter(ContentItem[] items) {
            mItems = items;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public Object getItem(int position) {
            return mItems[position];
        }

        @Override
        public int getCount() {
            return mItems.length;
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public int getItemViewType (int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Context context = parent.getContext();
            LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            if(inflater == null) {
                throw new IllegalStateException("missing inflater");
            }

            final ContentItem item = mItems[position];

            if(convertView == null) {
                convertView = inflater.inflate(R.layout.select_file_item, parent, false);

                convertView.findViewById(R.id.row).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (mListener != null) {
                            try {
                                ContentItem item = (ContentItem)view.getTag(R.id.tag_content_item);
                                Log.d(TAG, "onClick:play: index=" + item.fileIndex);
                                mListener.onFileSelected(item.fileIndex);
                                dismiss();
                            } catch (Throwable e) {
                                Log.e(TAG, "Failed to select file", e);
                            }
                        }
                    }
                });
            }

            ((TextView)convertView.findViewById(R.id.title)).setText(item.fileName);
            convertView.findViewById(R.id.row).setTag(R.id.tag_content_item, item);

            return convertView;
        }
    }
}
