package org.acestream.engine;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.fragment.app.ListFragment;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.URLSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

public class ExtensionsFragment extends ListFragment {

	@SuppressLint("SimpleDateFormat")
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		List<Extension> exList = getArguments().getParcelableArrayList("list");
		
		List<Map<String, ?>> list = new ArrayList<>();
		SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy");
		if(exList != null) {
			for (Extension e : exList) {
				Map<String, Object> item = new HashMap<>();
				item.put("name", e.Name);
				item.put("description", e.Description);
				item.put("issued_by", e.IssuedBy);
				item.put("url", e.Url);
				item.put("enabled", e.Enabled ? "Yes" : "No");
				item.put("valid_from", sdf.format(new Date(e.ValidFrom * 1000)));
				item.put("valid_to", sdf.format(new Date(e.ValidTo * 1000)));
				list.add(item);
			}
		}

		SimpleAdapter adapter = new SimpleAdapter(inflater.getContext(), list, R.layout.l_extension_item,
				new String[] { 
					"name", 
					"description", 
					"issued_by", 
					"url", 
					"enabled", 
					"valid_from", 
					"valid_to" },
				new int[] { 
					R.id.extension_item_name, 
					R.id.extension_item_desc_value, 
					R.id.extension_item_issued_by_value, 
					R.id.extension_item_url_value, 
					R.id.extension_item_enabled_value, 
					R.id.extension_item_valid_from_value, 
					R.id.extension_item_valid_to_value }) {
			
			@Override
			public View getView(int position, View convertView, ViewGroup parent) {
				View v = super.getView(position, convertView, parent);

                // description
                TextView txtDescription = (TextView) v.findViewById(R.id.extension_item_desc_value);
                if(txtDescription.getText().toString().isEmpty()) {
                    v.findViewById(R.id.extension_item_desc_container).setVisibility(View.GONE);
                }

                // URL
				TextView tvUrl = (TextView) v.findViewById(R.id.extension_item_url_value);
				final String url = tvUrl.getText().toString();
                if(url.length() == 0) {
                    View container = v.findViewById(R.id.extension_item_url_container);
                    if(container != null) {
                        container.setVisibility(View.GONE);
                    }
                }
                else {
                    SpannableStringBuilder ssb = new SpannableStringBuilder();
                    ssb.append(url);
                    ssb.setSpan(new URLSpan("#"), 0, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    tvUrl.setText(ssb, TextView.BufferType.SPANNABLE);

                    tvUrl.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                            startActivity(browserIntent);
                        }
                    });
                }
				return v;
			}
			
		};

        setListAdapter(adapter);
		return inflater.inflate(R.layout.l_fragment_extensions, null);
	}
	
	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		LinearLayout ll = (LinearLayout)v.findViewById(R.id.extension_details);

        if(ll.getVisibility() == View.GONE) {
            ll.setVisibility(View.VISIBLE);
        }
        else {
            ll.setVisibility(View.GONE);
        }
	}
}
