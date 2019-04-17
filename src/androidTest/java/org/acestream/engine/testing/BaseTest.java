package org.acestream.engine.testing;

import org.acestream.engine.aliases.App;
import org.junit.Before;

import java.util.HashMap;
import java.util.Map;

import static com.google.common.truth.Truth.assertThat;

class BaseTest {
    private final Map<String, Object[]> mEvents = new HashMap<>();

    @Before
    public void init() {
        App.setTestMode(true);
    }

    interface EventCallback {
        void gotEvent(Object[] params);
    }

    interface Condition {
        boolean isTrue();
    }

    void fireEvent(String name, Object... params) {
        synchronized (mEvents) {
            mEvents.put(name, params);
            mEvents.notify();
        }
    }

    @SuppressWarnings("SameParameterValue")
    void waitEvent(String name, long timeoutMillis) throws InterruptedException {
        waitEvent(name, timeoutMillis, null);
    }

    void waitEvent(String name, long timeoutMillis, EventCallback callback) throws InterruptedException {
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

    void clearEvents() {
        synchronized(mEvents) {
            mEvents.clear();
        }
    }

    void waitCondition(Condition condition, long timeoutMillis) {
        final Object obj = new Object();
        long startedAt = System.currentTimeMillis();

        while(!condition.isTrue()) {
            try {
                synchronized (obj) {
                    obj.wait(1000);
                }
            }
            catch(InterruptedException e) {
                continue;
            }
            long age = System.currentTimeMillis() - startedAt;
            if(age > timeoutMillis) {
                break;
            }
        }

        assertThat(condition.isTrue()).isTrue();
    }
}
