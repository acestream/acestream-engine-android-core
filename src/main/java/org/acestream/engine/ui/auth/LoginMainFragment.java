package org.acestream.engine.ui.auth;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import org.acestream.engine.AceStreamEngineBaseApplication;
import org.acestream.engine.R;

public class LoginMainFragment extends Fragment implements View.OnClickListener {
    private final static String TAG = "AceStream/LMF";

    private LoginActivity mParentActivity;
    private FrameLayout mButtonGoogle;

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mParentActivity = (LoginActivity)getActivity();
        updateUI();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.auth_login_main_fragment, container, false);

        view.findViewById(R.id.btn_engine_sign_in).setOnClickListener(this);

        mButtonGoogle = view.findViewById(R.id.btn_google_sign_in);
        mButtonGoogle.setOnClickListener(this);

        int pb = AceStreamEngineBaseApplication.showTvUi() ? 36 : 10;
        view.findViewById(R.id.bottom_container).setPadding(0, 10, 0, pb);

        return view;
    }

    private void updateUI() {
        mButtonGoogle.setVisibility(mParentActivity.isGoogleSignInAvailable() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onClick(View view) {
        int i = view.getId();
        if (i == R.id.btn_google_sign_in) {
            mParentActivity.googleSignIn();

        } else if (i == R.id.btn_engine_sign_in) {
            mParentActivity.showEngineLoginFragment();

        }
    }
}
