package org.acestream.engine;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;

public class WarningDialogFragment extends DialogFragment
	implements DialogInterface.OnClickListener {
	
	@Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
		setRetainInstance(true);
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle(android.R.string.dialog_alert_title);
		builder.setPositiveButton(android.R.string.ok, this);
		builder.setMessage(R.string.dialog_no_connection);
        return builder.create();
    }
	
	@Override
	public void onDestroyView()	{
	    Dialog dialog = getDialog();
	    if ((dialog != null) && getRetainInstance())
	        dialog.setDismissMessage(null);
	    super.onDestroyView();
	}
	
	public void closeDialog() {
		getDialog().dismiss();
	}

	@Override
	public void onClick(DialogInterface arg0, int arg1) {
		
	}
}
