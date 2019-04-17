package org.acestream.engine.ads;

import android.os.Handler;
import androidx.annotation.NonNull;
import android.text.TextUtils;
import android.util.Pair;

import org.acestream.engine.AceStreamEngineBaseApplication;
import org.acestream.engine.aliases.App;
import org.acestream.sdk.controller.api.response.AdConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class AdsWaterfall {
    private final static String TAG = "AS/WF";

    public interface InventoryHolder {
        @SuppressWarnings("UnusedReturnValue")
        boolean loadInventory(String inventory);
        boolean showInventory(String placement, String inventory);
        boolean allowCustomAds();
    }

    public static class FrequencyCapError extends Exception {}

    enum InventoryStatus {
        IDLE,
        LOADING,
        LOADED,
        FAILED,
    }

    private class InventoryState {
        private InventoryStatus mStatus;
        private long mUpdatedAt;

        InventoryState(InventoryStatus status) {
            mStatus = status;
            mUpdatedAt = System.currentTimeMillis();
        }

        public InventoryStatus getStatus() {
            return mStatus;
        }

        long getUpdatedAt() {
            return mUpdatedAt;
        }

        @Override
        public String toString() {
            return String.format(
                    Locale.getDefault(),
                    "<InventoryState: status=%s>",
                    mStatus
            );
        }
    }

    public class Placement {
        public final static String PREROLL = "preroll";
        public final static String PAUSE = "pause";
        public final static String UNPAUSE = "unpause";
        public final static String CLOSE = "close";
        // Used for Appodeal placement identification only
        public final static String MAIN_SCREEN = "main_screen";
    }

    public class AdType {
        public final static String VAST = "vast";
        public final static String INTERSTITIAL = "interstitial";
        public final static String REWARDED_VIDEO = "rewarded_video";
    }

    public class Inventory {
        public final static String VAST = "vast";
        public final static String ADMOB_INTERSTITIAL_PREROLL = "admob_interstitial_preroll";
        public final static String ADMOB_INTERSTITIAL_PAUSE = "admob_interstitial_pause";
        public final static String ADMOB_INTERSTITIAL_CLOSE = "admob_interstitial_close";
        public final static String ADMOB_REWARDED_VIDEO = "admob_rv";
        public final static String ADMOB_BANNER = "admob_banner";
        public final static String CUSTOM = "custom";
        public final static String APPODEAL_BANNER = "appodeal_banner";
        public final static String APPODEAL_REWARDED_VIDEO = "appodeal_rv";
        public final static String APPODEAL_INTERSTITIAL = "appodeal_interstitial";
    }

    private final Map<String, InventoryState> mInventoryStatus = new HashMap<>(20);
    private final Map<String, Long> mInventoryWait = new HashMap<>(20);
    private Map<String,Integer> mLoadTimeout;
    private Map<String,Integer> mMinImpressionInterval;
    private Map<String,List<List<String>>> mPriorities;
    private List<String> mCustomAdsRvProviders;
    private String mPlacement;
    private int mIndex;
    private boolean mDone;
    private Handler mHandler;
    private InventoryHolder mInventoryHolder;

    private class LoadTimeoutTask implements Runnable {
        private String mInventory;

        LoadTimeoutTask(@NonNull String inventory) {
            mInventory = inventory;
        }

        @Override
        public void run() {
            InventoryState state = mInventoryStatus.get(mInventory);
            if(state != null && state.getStatus() == InventoryStatus.LOADING) {
                long now = System.currentTimeMillis();
                long age = now - state.getUpdatedAt();
                App.v(TAG, "timeout: fail item: age=" + age + " item=" + mInventory);
                onFailed(mInventory);
            }
        }
    }

    public AdsWaterfall(AdConfig config, Handler handler, InventoryHolder inventoryHolder) {
        mPlacement = null;
        mIndex = -1;
        mDone = false;
        mHandler = handler;
        mInventoryHolder = inventoryHolder;

        mLoadTimeout = config.load_timeout;
        mMinImpressionInterval = config.min_impression_interval;
        mPriorities = config.priorities;
        mCustomAdsRvProviders = config.custom_ads_rv_providers;
        if(mCustomAdsRvProviders == null) {
            mCustomAdsRvProviders = new ArrayList<>();
        }
    }

    public String getPlacement() {
        return mPlacement;
    }

    public void setPlacement(String placement) {
        setPlacement(placement, false);
    }

    public void setPlacement(String placement, boolean force) {
        App.v(TAG, "setPlacement: placement=" + placement + " force=" + force);
        if(TextUtils.equals(placement, mPlacement) && !force) {
            // Placement didn't change
            return;
        }
        mPlacement = placement;
        mIndex = -1;
        mDone = false;
    }

    public void resetInventoryStatus(String inventory) {
        App.v(TAG, "resetInventoryStatus: inventory=" + inventory);
        mInventoryStatus.put(inventory, new InventoryState(InventoryStatus.IDLE));
    }

    public void done() {
        mDone = true;
        App.v(TAG, "done: placement=" + mPlacement);
    }

    private List<List<String>> getList(String placement) {
        if(mPriorities == null) {
            App.v(TAG, "getList: missing priorities");
            return null;
        }
        else if(mPriorities.containsKey(placement)) {
            return mPriorities.get(placement);
        }
        else {
            App.v(TAG, "getList: unknown placement: " + placement);
            return null;
        }
    }

    public boolean showNext() throws FrequencyCapError {
        return showNext(false);
    }

    public boolean showNext(boolean skipFrequencyCapping) throws FrequencyCapError {
        List<String> inventoryList = next(skipFrequencyCapping);
        if(inventoryList == null) {
            App.v(TAG, "showNext: no next inventory");
            return false;
        }

        if(!mInventoryHolder.allowCustomAds()) {
            if(inventoryList.remove(Inventory.CUSTOM)) {
                App.v(TAG, "showNext: filtered custom");
            }
        }

        boolean wait = false;
        boolean show = false;

        for(String inventory: inventoryList) {
            Pair<Boolean, Boolean> result = show(inventory);
            show = result.first;
            wait = result.second;
            if(show) {
                break;
            }
        }

        if(show) {
            // we have displayed ad
            App.v(TAG, "showNext: return true");
            mInventoryWait.clear();
            return true;
        }

        if(!wait) {
            // no ads or all failed
            return showNext();
        }

        return false;
    }

    public boolean showCustomRewardedVideo() {
        boolean show = false;
        App.v(TAG, "showCustomRewardedVideo: providers=" + TextUtils.join(",", mCustomAdsRvProviders));
        for(String inventory: mCustomAdsRvProviders) {
            Pair<Boolean, Boolean> result = show(inventory);
            show = result.first;
            if(show) {
                break;
            }
        }

        return show;
    }

    @SuppressWarnings("StatementWithEmptyBody")
    private Pair<Boolean,Boolean> show(String inventory) {
        boolean wait = false;
        boolean show = false;

        if(TextUtils.equals(inventory, Inventory.CUSTOM)) {
            for(String rv: mCustomAdsRvProviders) {
                InventoryState state = mInventoryStatus.get(rv);
                if (state == null) {
                    state = new InventoryState(InventoryStatus.IDLE);
                }
                InventoryStatus status = state.getStatus();

                if (status == InventoryStatus.IDLE) {
                    mInventoryHolder.loadInventory(rv);
                    wait = true;
                } else if (status == InventoryStatus.LOADED) {
                    show = true;
                    break;
                } else if (status == InventoryStatus.LOADING) {
                    wait = true;
                } else if (status == InventoryStatus.FAILED) {
                    // do nothing
                }
            }

            if(show) {
                // One loaded
                App.v(TAG, "show:custom: show");
                showInventory(mPlacement, Inventory.CUSTOM);
            }
            else if(wait) {
                // At least one is loading
                wait(Inventory.CUSTOM);
                App.v(TAG, "show:custom: wait");
            }
            else {
                // All failed
                App.v(TAG, "show:custom: all failed");
            }
        }
        else {
            InventoryState state = mInventoryStatus.get(inventory);
            App.v(TAG, "show: inventory=" + inventory + " state=" + state);
            if (state == null) {
                state = new InventoryState(InventoryStatus.IDLE);
            }
            InventoryStatus status = state.getStatus();

            if (status == InventoryStatus.IDLE) {
                wait(inventory);
                mInventoryHolder.loadInventory(inventory);
                wait = true;
            } else if (status == InventoryStatus.LOADED) {
                showInventory(mPlacement, inventory);
                show = true;
            } else if (status == InventoryStatus.LOADING) {
                wait(inventory);
                wait = true;
            } else if (status == InventoryStatus.FAILED) {
                // do nothing
            }
        }

        return new Pair<>(show, wait);
    }

    private void wait(String inventory) {
        App.v(TAG, "wait: inventory=" + inventory);
        mInventoryWait.put(inventory, System.currentTimeMillis());
    }

    public List<String> next(boolean skipFrequencyCapping) throws FrequencyCapError {
        ++mIndex;
        List<List<String>> list = getList(mPlacement);

        if(list == null) {
            App.v(TAG, "next: empty list: placement=" + mPlacement);
            return null;
        }

        if(mDone) {
            App.v(TAG, "next: already done: placement=" + mPlacement);
            return null;
        }

        if(!skipFrequencyCapping) {
            checkFrequency(mPlacement);
        }

        if(mIndex < 0 || mIndex >= list.size()) {
            App.v(TAG, "next: no more items: placement=" + mPlacement);
            return null;
        }

        List<String> nextInventory = list.get(mIndex);
        App.v(TAG, "next: placement=" + mPlacement + " next=" + TextUtils.join(",", nextInventory));

        return nextInventory;
    }

    public boolean has(String placement, @NonNull String[] types) {
        for(String type: types) {
            if(has(placement, type)) {
                return true;
            }
        }
        return false;
    }

    public boolean has(String placement, String inventory) {
        List<List<String>> list = getList(placement);

        if(list == null) {
            return false;
        }

        for(List<String> items: list) {
            for(String item: items) {
                if (TextUtils.equals(inventory, item)) {
                    return true;
                }
            }
        }

        return false;
    }

    public void onLoading(String inventory) {
        App.v(TAG, "onLoading: inventory=" + inventory);
        mInventoryStatus.put(inventory, new InventoryState(InventoryStatus.LOADING));

        mHandler.postDelayed(new LoadTimeoutTask(inventory), getLoadTimeout(inventory));
    }

    public void onLoaded(String inventory) {
        App.v(TAG, "onLoaded: inventory=" + inventory);
        mInventoryStatus.put(inventory, new InventoryState(InventoryStatus.LOADED));

        if(!mInventoryWait.containsKey(inventory)
                && mInventoryWait.containsKey(Inventory.CUSTOM)
                && mCustomAdsRvProviders.contains(inventory)) {
            // We're waiting for "custom" and one of RV is loaded
            App.v(TAG, "onLoaded: replace: " + inventory + "->custom");
            inventory = Inventory.CUSTOM;
        }

        if(mInventoryWait.containsKey(inventory)) {
            long age = System.currentTimeMillis() - mInventoryWait.get(inventory);
            App.v(TAG, "onLoaded: show waiting item: inventory=" + inventory + " age=" + age);
            showInventory(mPlacement, inventory);

            // Clear all waiting items
            mInventoryWait.clear();
        }
    }

    public void onFailed(String inventory) {
        App.v(TAG, "onFailed: inventory=" + inventory);
        mInventoryStatus.put(inventory, new InventoryState(InventoryStatus.FAILED));

        if(!mInventoryWait.containsKey(inventory)
                && mInventoryWait.containsKey(Inventory.CUSTOM)
                && mCustomAdsRvProviders.contains(inventory)) {
            // We're waiting for "custom" and one of RV failed
            // Need to check if all RV failed
            boolean allFailed = true;
            for(String rv: mCustomAdsRvProviders) {
                InventoryState state = mInventoryStatus.get(rv);
                if(state == null || state.getStatus() != InventoryStatus.FAILED) {
                    allFailed = false;
                    break;
                }
            }

            if(allFailed) {
                // All RV failed - this means that "custom" failed too
                App.v(TAG, "onFailed: replace: " + inventory + "->custom");
                inventory = Inventory.CUSTOM;
            }
        }

        if(mInventoryWait.containsKey(inventory)) {
            mInventoryWait.remove(inventory);
            App.v(TAG, "onFailed: remove waiting item: inventory=" + inventory + " done=" + mDone + " waiting=" + mInventoryWait.size());
            if(!mDone && mInventoryWait.size() == 0) {
                // All waiting items failed.
                try {
                    showNext();
                }
                catch(FrequencyCapError e) {
                    // ignore
                }
            }
        }
    }

    private int getLoadTimeout(String inventory) {
        int timeout = 10000;

        if(mLoadTimeout != null && mLoadTimeout.containsKey(inventory)) {
            timeout = mLoadTimeout.get(inventory);
        }

        return timeout;
    }

    private int getMinImpressionInterval(String placement) {
        int interval = 0;

        if(mMinImpressionInterval != null && mMinImpressionInterval.containsKey(placement)) {
            interval = mMinImpressionInterval.get(placement);
        }

        return interval;
    }

    @SuppressWarnings("UnusedReturnValue")
    private boolean showInventory(String placement, String inventory) {
        int impressions = saveImpression(placement);
        App.v(TAG, "showInventory: placement=" + placement + " impressions=" + impressions);
        return mInventoryHolder.showInventory(placement, inventory);
    }

    private String getAdFrequencyKey(String placement, String type) {
        return "ads.freq." + placement + "." + type;
    }

    private AtomicInteger getAdFrequencyCounter(String placement) {
        long now = System.currentTimeMillis();
        String key = getAdFrequencyKey(placement, "counter");
        AtomicInteger counter;
        if(AceStreamEngineBaseApplication.hasValue(key)) {
            counter = (AtomicInteger)AceStreamEngineBaseApplication.getValue(key);
        }
        else {
            counter = new AtomicInteger();
            AceStreamEngineBaseApplication.setValue(key, counter);
            AceStreamEngineBaseApplication.setValue(getAdFrequencyKey(placement, "created_at"), now);
        }

        return counter;
    }

    private int saveImpression(String placement) {
        long now = System.currentTimeMillis();
        AtomicInteger counter = getAdFrequencyCounter(placement);
        AceStreamEngineBaseApplication.setValue(getAdFrequencyKey(placement, "last_at"), now);

        return counter.addAndGet(1);
    }

    private void checkFrequency(String placement) throws FrequencyCapError {
        int minInterval = getMinImpressionInterval(placement);
        if(minInterval == 0) {
            return;
        }

        long now = System.currentTimeMillis();
        long lastImpressionAt = AceStreamEngineBaseApplication.getLongValue(getAdFrequencyKey(placement, "last_at"), 0);

        long age = now - lastImpressionAt;

        if(age < minInterval) {
            App.v(TAG, "freq:skip: placement=" + placement + " age=" + age + "/" + minInterval);
            throw new FrequencyCapError();
        }

        App.v(TAG, "freq:allow: placement=" + placement + " age=" + age + "/" + minInterval);
    }
}
