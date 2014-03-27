package org.floens.chan.core.watch;

import java.util.List;

import org.floens.chan.core.loader.Loader;
import org.floens.chan.core.loader.LoaderPool;
import org.floens.chan.core.model.Pin;
import org.floens.chan.core.model.Post;
import org.floens.chan.service.PinnedService;
import org.floens.chan.utils.Logger;

import com.android.volley.VolleyError;

public class PinWatcher implements Loader.LoaderListener {
    private static final String TAG = "PinWatcher";

    private final Pin pin;
    private final Loader loader;

    private long startTime;
    private boolean isError = false;

    public PinWatcher(Pin pin) {
        this.pin = pin;

        loader = LoaderPool.getInstance().obtain(pin.loadable, this);
    }

    public void destroy() {
        LoaderPool.getInstance().release(loader, this);
    }

    public void update() {
//        Logger.test("PinWatcher update");

        if (!isError) {
            loader.tryLoadMoreIfTime();
        }
    }

    public int getNewPostCount() {
        if (pin.watchLastCount <= 0) {
            return 0;
        } else {
            return Math.max(0, pin.watchNewCount - pin.watchLastCount);
        }
    }

    @Override
    public void onError(VolleyError error) {
        Logger.test("PinWatcher onError: ", error);
        isError = true;
    }

    @Override
    public void onData(List<Post> result, boolean append) {
        int count = result.size();

        Logger.test("PinWatcher onData, Post size: " + count);

        if (pin.watchLastCount <= 0) {
            pin.watchLastCount = pin.watchNewCount;
        }

        pin.watchNewCount = count;

        PinnedService.callOnPinsChanged();
    }
}
