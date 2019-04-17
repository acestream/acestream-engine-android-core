package org.acestream.engine.testing;

import androidx.fragment.app.FragmentManager;

import org.acestream.engine.R;
import org.acestream.engine.aliases.App;
import org.acestream.engine.controller.api.response.MediaFilesResponse;
import org.junit.Rule;
import org.junit.Test;
import org.videolan.medialibrary.media.MediaWrapper;
import org.videolan.vlc.gui.MainActivity;
import org.videolan.vlc.gui.network.MRLPanelFragment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
public class MRLDialogTest {
    private final Map<String, Object[]> mEvents = new HashMap<>();

    @Rule
    public ActivityTestRule<MainActivity> mMainActivityRule = new ActivityTestRule<>(MainActivity.class);

    private interface EventCallback {
        void gotEvent(Object[] params);
    }

    @Test
    public void run() throws InterruptedException {
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

        clearEvents();
        onView(ViewMatchers.withId(R.id.mrl_edit_text)).perform(clearText()).perform(typeText("test"));
        onView(withId(R.id.send)).perform(click());
        waitEvent("onGotMediaWrapper", 30000, new EventCallback() {
            @Override
            public void gotEvent(Object[] params) {
                assertThat(params).isNotNull();
                assertThat(params.length).isEqualTo(1);
                assertThat(params[0]).isInstanceOf(MediaWrapper.class);
                MediaWrapper mw = (MediaWrapper)params[0];
                App.vvv("got mw: p2p=" + mw.isP2PItem() + " uri=" + mw.getUri());
                assertThat(mw.isP2PItem()).isFalse();
            }
        });
        waitEvent("onDialogClosed", 1000);

        clearEvents();
        onView(withId(R.id.mrl_edit_text)).perform(clearText());
        onView(withId(R.id.send)).perform(click());
        waitEvent("onEmptyInput", 30000);

        clearEvents();
        onView(withId(R.id.mrl_edit_text)).perform(clearText()).perform(typeText("acestream://94c2fd8fb9bc8f2fc71a2cbe9d4b866f227a0209"));
        onView(withId(R.id.send)).perform(click());
        waitEvent("onGotMediaWrapper", 30000, new EventCallback() {
            @Override
            public void gotEvent(Object[] params) {
                assertThat(params).isNotNull();
                assertThat(params.length).isEqualTo(1);
                assertThat(params[0]).isInstanceOf(MediaWrapper.class);
                MediaWrapper mw = (MediaWrapper)params[0];
                App.vvv("got mw: p2p=" + mw.isP2PItem() + " uri=" + mw.getUri());
                assertThat(mw.isP2PItem()).isTrue();
            }
        });
        waitEvent("onDialogClosed", 1000);
    }

    private void clearEvents() {
        synchronized(mEvents) {
            mEvents.clear();
        }
    }

    private void fireEvent(String name, Object... params) {
        synchronized (mEvents) {
            mEvents.put(name, params);
            mEvents.notify();
        }
    }

    private void waitEvent(String name, long timeoutMillis) throws InterruptedException {
        waitEvent(name, timeoutMillis, null);
    }

    private void waitEvent(String name, long timeoutMillis, EventCallback callback) throws InterruptedException {
        long startedAt = System.currentTimeMillis();
        synchronized (mEvents) {
            while (!mEvents.containsKey(name)) {
                mEvents.wait(timeoutMillis);
                long age = System.currentTimeMillis() - startedAt;
                if (age > timeoutMillis) {
                    break;
                }
            }

            assertThat(mEvents.containsKey(name)).isTrue();
            if(callback != null) {
                callback.gotEvent(mEvents.get(name));
            }
        }
    }
}
