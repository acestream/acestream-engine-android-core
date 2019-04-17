package org.acestream.engine.testing;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.LargeTest;

import static com.google.common.truth.Truth.assertThat;

import org.acestream.engine.PlaybackManager;
import org.acestream.engine.aliases.App;
import org.acestream.engine.controller.EngineApi;
import org.junit.Test;
import org.videolan.vlc.PlaybackService;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@LargeTest
public class EngineServiceTest {
    private PlaybackService mService;
    private PlaybackManager mPlaybackManager;
    private EngineApi mEngineApi;

    @Test
    public void startPlaybackService() throws InterruptedException {
        App.vvv("start ps...");

        final CountDownLatch sync = new CountDownLatch(1);

        final PlaybackService.Client.Callback callback = new PlaybackService.Client.Callback() {
            @Override
            public void onConnected(PlaybackService service) {
                mService = service;
                sync.countDown();
            }

            @Override
            public void onDisconnected() {
                mService = null;
            }
        };

        Context ctx = ApplicationProvider.getApplicationContext();
        PlaybackService.Client client = new PlaybackService.Client(ctx, callback);
        client.connect();

        assertThat(sync.await(30, TimeUnit.SECONDS)).isTrue();
        assertThat(mService).isNotNull();
        App.vvv("start ps done");
    }

    @Test
    public void startPlaybackManager() throws InterruptedException {
        App.vvv("start pm...");

        final CountDownLatch sync = new CountDownLatch(1);

        final PlaybackManager.Client.Callback callback = new PlaybackManager.Client.Callback() {
            @Override
            public void onConnected(PlaybackManager service) {
                mPlaybackManager = service;
                sync.countDown();
            }

            @Override
            public void onDisconnected() {
                mPlaybackManager = null;
            }
        };

        Context ctx = ApplicationProvider.getApplicationContext();
        PlaybackManager.Client client = new PlaybackManager.Client(ctx, callback);
        client.connect();

        assertThat(sync.await(30, TimeUnit.SECONDS)).isTrue();
        assertThat(mPlaybackManager).isNotNull();
        App.vvv("start pm done");

        final CountDownLatch sync2 = new CountDownLatch(1);
        mPlaybackManager.addCallback(new PlaybackManager.Callback() {
            @Override
            public void onEngineConnected(EngineApi service) {
                mEngineApi = service;
                sync2.countDown();
            }

            @Override
            public void onEngineFailed() {
            }

            @Override
            public void onEngineUnpacking() {
            }

            @Override
            public void onEngineStarting() {
            }

            @Override
            public void onEngineStopped() {
            }
        });

        mPlaybackManager.startEngine();
        assertThat(sync2.await(120, TimeUnit.SECONDS)).isTrue();
        assertThat(mEngineApi).isNotNull();
        App.vvv("start engine done");

    }
}
