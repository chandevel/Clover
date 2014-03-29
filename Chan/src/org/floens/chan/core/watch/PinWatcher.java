package org.floens.chan.core.watch;

import java.util.List;

import org.floens.chan.core.loader.Loader;
import org.floens.chan.core.loader.LoaderPool;
import org.floens.chan.core.model.Pin;
import org.floens.chan.core.model.Post;
import org.floens.chan.service.WatchService;
import org.floens.chan.utils.Logger;

import com.android.volley.VolleyError;

public class PinWatcher implements Loader.LoaderListener {
    private static final String TAG = "PinWatcher";

    private final Pin pin;
    private Loader loader;
    private boolean isError = false;

    public PinWatcher(Pin pin) {
        this.pin = pin;

        loader = LoaderPool.getInstance().obtain(pin.loadable, this);
    }

    public void destroy() {
        if (loader != null) {
            LoaderPool.getInstance().release(loader, this);
            loader = null;
        }
    }

    public void update() {
        if (!isError) {
            loader.loadMoreIfTime();
        }
    }

    public int getNewPostCount() {
        if (pin.watchLastCount <= 0) {
            return 0;
        } else {
            return Math.max(0, pin.watchNewCount - pin.watchLastCount);
        }
    }

    public boolean isError() {
        return isError;
    }

    @Override
    public void onError(VolleyError error) {
        Logger.e(TAG, "PinWatcher onError: ", error);
        isError = true;
        pin.watchLastCount = 0;
        pin.watchNewCount = 0;

        WatchService.callOnPinsChanged();
    }

    @Override
    public void onData(List<Post> result, boolean append) {
        isError = false;

        int count = result.size();

        Logger.test("PinWatcher onData, Post size: " + count);

        if (pin.watchLastCount <= 0) {
            pin.watchLastCount = pin.watchNewCount;
        }

        pin.watchNewCount = count;

        WatchService.callOnPinsChanged();
    }
}
