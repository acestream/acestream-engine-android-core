package org.acestream.engine;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

import org.acestream.sdk.AceStream;
import org.json.JSONArray;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPOutputStream;

public class ReportProblemActivity
    extends BaseActivity
    implements OnClickListener, AdapterView.OnItemSelectedListener
{

	private final static String TAG = "AS/ReportProblem";
	private final static String DELIMITER = ">>>>>>\n";
	private final static int READ_BUFFER_LENGTH = 4096;

	private EditText mTextDescription;
	private Spinner mSpinner;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// Theme must be applied before super.onCreate

		super.onCreate(savedInstanceState);

		boolean tv = AceStreamEngineBaseApplication.showTvUi();

		setContentView(tv ? R.layout.l_activity_report_problem_tv : R.layout.l_activity_report_problem);
		
		Button btn = (Button)findViewById(R.id.button_report);
		btn.setOnClickListener(this);

		// Setting this click listener on Android 8 make navigation from D-PAD unavailable
		// (at least on Android TV).
		if(!tv) {
			findViewById(R.id.main_layout).setOnClickListener(this);
		}

		mTextDescription = (EditText)findViewById((R.id.description));

		mSpinner = (Spinner) findViewById(R.id.category);
		mSpinner.setOnItemSelectedListener(this);
		mSpinner.setVisibility(View.GONE);

		// get categories from server and show spinner when done
		new updateCategoriesTask().execute();
	}

	private void setCategories(final String[] categories) {
		final Activity activity = this;
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				ArrayAdapter<String> adapter = new ArrayAdapter<String>(activity,
						android.R.layout.simple_list_item_1, categories);
				// Specify the layout to use when the list of choices appears
				adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
				//adapter.setDropDownViewResource(R.layout.spinner_multiline_item);
				// Apply the adapter to the spinner
				mSpinner.setAdapter(adapter);
				mSpinner.setVisibility(View.VISIBLE);
			}
		});
	}

	@Override
    public void onClick(View v) {
		int i = v.getId();
		if (i == R.id.button_report) {
			String category = "other";
			if(mSpinner.getSelectedItem() != null) {
				category = mSpinner.getSelectedItem().toString();
			}
			new SendRequestTask(this, true).execute(
					category,
					mTextDescription.getText().toString());
			finish();

		} else if (i == R.id.main_layout) {
			hideKeyboard();

		}
    }

	private void hideKeyboard() {
		try {
			InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
			imm.hideSoftInputFromWindow(mTextDescription.getWindowToken(), 0);
		}
		catch(Throwable e) {
			Log.e(TAG, "failed to hide keyboard", e);
		}
	}

	@Override
	public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
		String name = parent.getItemAtPosition(position).toString();
		Log.d(TAG, "item selected: pos=" + position + " id=" + id + " name=" + name);
	}

	@Override
	public void onNothingSelected(AdapterView<?> parent) {

	}

	private class updateCategoriesTask extends AsyncTask<Void, Void, Void> {

		private void sendRequest() {
			try {
				String logsDir = AceStream.externalFilesDir();
				if(logsDir == null) {
					Log.e(TAG, "sendRequest: no external files dir");
					return;
				}

				BufferedReader reader;
				StringBuilder builder;

				String locale = Locale.getDefault().getLanguage();

				// we support only "ru" and "en"
				if(!locale.equals("ru")) {
					locale = "en";
				}

				URL url = new URL("https", "android.acestream.net", 443, "/content/report-problem/category." + locale + ".json");
				HttpURLConnection connection = (HttpURLConnection) url.openConnection();
				connection.setRequestMethod("GET");
				connection.setConnectTimeout(5000);
				connection.setDoInput(true);

				// read response
				reader = new BufferedReader(new InputStreamReader( connection.getInputStream() ));

				builder = new StringBuilder();
				String buffer;
				while((buffer = reader.readLine()) != null) {
					builder.append(buffer);
				}

				reader.close();
				String response = builder.toString();
				Log.d(TAG, "response: " + response);

				List<String> categories = new ArrayList<>();
				categories.add(getResources().getString(R.string.select_category));

				JSONArray json = new JSONArray(response);
				for(int i = 0; i < json.length(); i++) {
					categories.add(json.getString(i));
				}

				setCategories(categories.toArray(new String[categories.size()]));
			}
			catch(Throwable e) {
				Log.e(TAG, "request failed", e);
			}
		}

		@Override
		protected Void doInBackground(Void... params) {
			sendRequest();
			return null;
		}

	}

	public static void sendReport(String category, String description) {
		new SendRequestTask(AceStream.context(), false).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, category, description);
	}

	private static class SendRequestTask extends AsyncTask<String, Void, Void> {

		private final Context mContext;
		private final boolean mShowToast;

		public SendRequestTask(Context context, boolean showToast) {
			mContext = context;
			mShowToast = showToast;
		}

		private void addString(GZIPOutputStream out, String data) throws IOException {
			out.write(data.getBytes());
		}

		private void addFile(GZIPOutputStream out, String path) throws IOException {
			int count;
			byte buffer[] = new byte[READ_BUFFER_LENGTH];

			addString(out, DELIMITER);

			try {
				File file = new File(path);
				if (file.exists()) {
					FileInputStream fi = new FileInputStream(file);
					BufferedInputStream in = new BufferedInputStream(fi, READ_BUFFER_LENGTH);
					while ((count = in.read(buffer, 0, READ_BUFFER_LENGTH)) != -1) {
						out.write(buffer, 0, count);
					}
					in.close();
				}
			}
			catch(Throwable e) {
				Log.e(TAG, "failed to add file", e);
			}
		}

		private void addLogcat(GZIPOutputStream out) throws IOException {
			addString(out, DELIMITER);

			Process process;
			try {
				ArrayList<String> commandLine = new ArrayList<>();
				commandLine.add("/system/bin/logcat");
				commandLine.add("-d");
				commandLine.add("-v");
				commandLine.add("time");
				commandLine.add("*:V");
				process = Runtime.getRuntime().exec(commandLine.toArray(new String[0]));
			}
			catch (IOException e) {
				Log.w(TAG, "Cannot execute logcat", e);
				return;
			}

			BufferedReader reader;
			try {

				reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

				String line;
				byte[] separatorBytes = "\n".getBytes();

				while((line = reader.readLine()) != null) {
					// filter logcat (acestream, csdk and errors)
					if(!line.contains("AceStream")
						&& !line.contains(com.connectsdk.core.Util.T)
						&& !line.contains(" E/")) {
						continue;
					}
					byte[] lineBytes = line.getBytes();
					out.write(lineBytes);
					out.write(separatorBytes);
				}
			}
			catch (Exception e) {
				Log.e(TAG, e.toString());
			}
		}

		private void sendRequest(String category, String description) {
			File tempFile = null;
			try {
				String logsDir = AceStream.externalFilesDir();
				if(logsDir == null) {
					Log.e(TAG, "sendRequest: no external files dir");
					return;
				}

				BufferedReader reader;
				StringBuilder builder;

				URL url = new URL("https", "android.acestream.net", 443, "/report");
				HttpURLConnection connection = (HttpURLConnection) url.openConnection();
				connection.setRequestMethod("POST");
				connection.setConnectTimeout(30000);
				connection.setDoInput(true);

				// open temp file for post data
				File cacheDir = AceStreamEngineBaseApplication.context().getCacheDir();
				tempFile = File.createTempFile("report", null, cacheDir);
				FileOutputStream output = new FileOutputStream(tempFile);
				GZIPOutputStream out = new GZIPOutputStream(output);

				builder = new StringBuilder();
				builder.append(category);
				builder.append("\n---\n");
				builder.append(description);
				builder.append("\n---\n");

				ActivityManager activityManager = null;
				try {
					activityManager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
				}
				catch(Throwable e) {
					Log.e(TAG, "sendRequest: failed to get activity manager", e);
				}

				builder.append("arch=" + AceStreamEngineBaseApplication.getStringAppMetadata("arch") + "\n");
				builder.append("version_name=" + AceStream.getApplicationVersionName() + "\n");
				builder.append("version_code=" + AceStream.getApplicationVersionCode() + "\n");
				builder.append("app_id=" + AceStream.getApplicationId()+ "\n");
				builder.append("api_level=" + Build.VERSION.SDK_INT + "\n");
				builder.append("os_version=" + Build.VERSION.CODENAME+ "\n");
				builder.append("device=" + Build.DEVICE + "\n");

				if(activityManager == null) {
					builder.append("memory_class=?\n");
				}
				else {
					builder.append("memory_class=" + activityManager.getMemoryClass() + "\n");
				}

				try {
					builder.append("externalFilesDir=" + AceStream.externalFilesDir() + "\n");

					File f;

					f = Environment.getExternalStorageDirectory();
					if (f == null) {
						builder.append("ExternalStorageDirectory: null\n");
					} else {
						builder.append("ExternalStorageDirectory: path=" + f.getAbsolutePath() + " writable=" + f.canWrite() + "\n");
					}

					f = AceStreamEngineBaseApplication.context().getExternalFilesDir(null);
					if (f == null) {
						builder.append("ExternalFilesDir: null\n");
					} else {
						builder.append("ExternalFilesDir: path=" + f.getAbsolutePath() + " writable=" + f.canWrite() + "\n");
					}
				}
				catch(Throwable e) {
					builder.append("failed to get dirs info: " + e.getMessage() + "\n");
				}

				addString(out, builder.toString());
				addLogcat(out);
				addFile(out, logsDir + "/acestream_std.log");
				addFile(out, logsDir + "/acestream.log");
				addFile(out, logsDir + "/acestream.log.1");
				addFile(out, logsDir + "/logcat.log");
				addFile(out, logsDir + "/logcat.log.1");

				//TODO: add segmenter.log

				// close temp file
				out.close();

				// write post data
				connection.setDoOutput(true);
				connection.setRequestProperty("Content-Length", Long.toString(tempFile.length()));
				connection.setRequestProperty("Content-Type", "application/octet-stream");

				// write post data from temp file
				int count;
				byte[] buf = new byte[READ_BUFFER_LENGTH];
				OutputStream os = connection.getOutputStream();
				FileInputStream fi = new FileInputStream(tempFile);
				BufferedInputStream in = new BufferedInputStream(fi, READ_BUFFER_LENGTH);
				while ((count = in.read(buf, 0, READ_BUFFER_LENGTH)) != -1) {
					os.write(buf, 0, count);
				}
				in.close();
				os.flush();
				os.close();

				// read response
				reader = new BufferedReader(new InputStreamReader( connection.getInputStream() ));

				builder = new StringBuilder();
				String buffer;
				while((buffer = reader.readLine()) != null) {
					builder.append(buffer);
				}

				reader.close();
				String response = builder.toString();
				Log.d(TAG, "response: " + response);

				if(mShowToast) {
					toast(R.string.report_has_been_sent);
				}
			}
			catch(Throwable e) {
				Log.e(TAG, "request failed", e);
				toast(R.string.report_failed);
			}
			finally {
				// remove temp file
				if(tempFile != null) {
					tempFile.delete();
				}
			}
		}

		@Override
		protected Void doInBackground(String... params) {
			sendRequest(params[0], params[1]);
			return null;
		}

		private void toast(final int resId) {
			AceStreamEngineBaseApplication.runOnMainThread(new Runnable() {
				@Override
				public void run() {
					AceStreamEngineBaseApplication.toast(mContext.getString(resId));
				}
			});
		}
	}


}
