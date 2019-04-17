package org.acestream.engine.testing;

import androidx.fragment.app.FragmentManager;

import org.acestream.engine.EngineSession;
import org.acestream.engine.EngineStatus;
import org.acestream.engine.PlaybackManager;
import org.acestream.engine.R;
import org.acestream.engine.controller.api.response.MediaFilesResponse;
import org.junit.Rule;
import org.junit.Test;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.PlaybackService;
import org.videolan.vlc.gui.MainActivity;
import org.videolan.vlc.gui.network.MRLPanelFragment;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.filters.LargeTest;
import androidx.test.rule.ActivityTestRule;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.clearText;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static com.google.common.truth.Truth.assertThat;

@LargeTest
public class PlayerDetectionTest extends BaseTest {
    private PlaybackService mPlaybackService = null;

    @Rule
    public ActivityTestRule<MainActivity> mMainActivityRule = new ActivityTestRule<>(MainActivity.class);

    @Test
    public void run() throws InterruptedException, UnsupportedEncodingException, NoSuchAlgorithmException {
        MainActivity activity = mMainActivityRule.getActivity();
        FragmentManager manager = activity.getSupportFragmentManager();
        MRLPanelFragment fragment = new MRLPanelFragment();
        fragment.setTestMode(true);
        fragment.setTestCallback(new MRLPanelFragment.TestCallback() {
            @Override
            public void onEmptyInput() {
                fireEvent("onEmptyInput");
            }

            @Override
            public void onGotMediaWrapper(MediaWrapper mw) {
                fireEvent("onGotMediaWrapper", mw);
            }

            @Override
            public void onMediaFilesSuccess(MediaFilesResponse response, List<MediaWrapper> playlist) {
                fireEvent("onMediaFilesSuccess", response, playlist);
            }

            @Override
            public void onMediaFilesError(String error) {
                fireEvent("onMediaFilesError", error);
            }

            @Override
            public void onDialogClosed() {
                fireEvent("onDialogClosed");
            }
        });
        fragment.show(manager, "fragment_mrl");

        // start sintel
        clearEvents();
        onView(ViewMatchers.withId(R.id.mrl_edit_text)).perform(clearText()).perform(typeText("acestream://94c2fd8fb9bc8f2fc71a2cbe9d4b866f227a0209"));
        onView(withId(R.id.send)).perform(click());
        waitEvent("onGotMediaWrapper", 30000, new EventCallback() {
            @Override
            public void gotEvent(Object[] params) {
                assertThat(params).isNotNull();
                assertThat(params.length).isEqualTo(1);
                assertThat(params[0]).isInstanceOf(MediaWrapper.class);
                MediaWrapper mw = (MediaWrapper)params[0];
                assertThat(mw.isP2PItem()).isTrue();
            }
        });
        waitEvent("onDialogClosed", 1000);

        // connect to PM
        clearEvents();
        PlaybackService.Client client = new PlaybackService.Client(activity, new PlaybackService.Client.Callback() {
            @Override
            public void onConnected(PlaybackService service) {
                mPlaybackService = service;
                fireEvent("psConnected");
            }

            @Override
            public void onDisconnected() {
                mPlaybackService = null;
            }
        });
        client.connect();
        waitEvent("psConnected", 5000);
        assertThat(mPlaybackService).isNotNull();

        final PlaybackManager pm = mPlaybackService.getPlaybackManager();
        assertThat(pm).isNotNull();

        // wait for session start
        waitCondition(new Condition() {
            @Override
            public boolean isTrue() {
                return pm.getEngineSession() != null;
            }
        }, 10000);

        final EngineSession session = pm.getEngineSession();
        assertThat(session).isNotNull();

        // Wait for media wrapper initializtion (playbak URI and user agent should be set after
        // session start.
        waitCondition(new Condition() {
            @Override
            public boolean isTrue() {
                MediaWrapper mw = mPlaybackService.getCurrentMediaWrapper();
                if(mw == null) return false;
                if(mw.getPlaybackUri() == null) return false;
                if(mw.getUserAgent() == null) return false;
                return true;
            }
        }, 10000);

        final MediaWrapper mw = mPlaybackService.getCurrentMediaWrapper();
        assertThat(mw).isNotNull();

        assertThat(mw.isP2PItem()).isTrue();
        assertThat(mw.getPlaybackUri()).isNotNull();
        assertThat(mw.getUserAgent()).isNotNull();
        assertThat(mw.getPlaybackUri().toString()).isEqualTo(session.playbackUrl);

        Matcher matcher = Pattern.compile(" SessionId/([0-9A-F]+)").matcher(mw.getUserAgent());
        assertThat(matcher.find()).isTrue();
        String key = matcher.group(1);
        String keyHash = org.acestream.util.Util.sha1Hash(key);

        // wait until engine detects our player
        waitCondition(new Condition() {
            @Override
            public boolean isTrue() {
                EngineStatus status = pm.getLastEngineStatus();
                if(status == null) {
                    return false;
                }

                return status.isOurPlayer == 1;
            }
        }, 120000);

        final EngineStatus status = pm.getLastEngineStatus();
        assertThat(status).isNotNull();
        assertThat(status.isOurPlayer).isEqualTo(1);
        assertThat(status.contentKey.toLowerCase()).isEqualTo(keyHash.toLowerCase());
    }
}
