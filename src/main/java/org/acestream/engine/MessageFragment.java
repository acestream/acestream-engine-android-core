package org.acestream.engine;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class MessageFragment extends Fragment {

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		int message = getArguments().getInt("message_res", R.string.message_empty);
		
		View view = inflater.inflate(R.layout.l_fragment_message, container, false);
		
		TextView tv = (TextView)view.findViewById(android.R.id.message);
		tv.setText(message);
		
		return view;
	}
	
}
