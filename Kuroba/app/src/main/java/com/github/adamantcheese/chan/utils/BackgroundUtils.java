/*
 * Kuroba - *chan browser https://github.com/Adamantcheese/Kuroba/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.github.adamantcheese.chan.utils;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;

import android.os.Handler;
import android.os.Looper;

import com.github.adamantcheese.chan.BuildConfig;
import com.github.adamantcheese.chan.Chan;
import com.github.adamantcheese.chan.core.settings.ChanSettings;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("unused")
public class BackgroundUtils {
    private static final String TAG = "BackgroundUtils";
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Based on fitting the points {16,28}, {64, 19}, {128,6}, {256, 4}, from some performance checks
    // Equation of -9.27447 log(0.0028267 x) = 1 was solved for and rounded down to get a thread count
    // Note that this may not be the best on a phone, but is probably the best for emulator
    // This calculation also probably sucks immensely
    public static final ExecutorService backgroundService =
            new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors() - 1,
                    300,
                    60L,
                    TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>()
            );

    public static final ScheduledExecutorService backgroundScheduledService = Executors.newScheduledThreadPool(1);

    public static boolean isInForeground() {
        return ((Chan) getAppContext()).getActivityInForeground();
    }

    /**
     * Causes the runnable to be added to the message queue. The runnable will
     * be run on the ui thread.
     */
    public static void runOnMainThread(Runnable runnable) {
        mainHandler.post(runnable);
    }

    public static void runOnMainThread(Runnable runnable, long delay) {
        mainHandler.postDelayed(runnable, delay);
    }

    public static void cleanup() {
        mainHandler.removeCallbacksAndMessages(null);
    }

    public static void runOnBackgroundThread(Runnable runnable) {
        if (BuildConfig.DEBUG && backgroundService.isTerminated()) {
            throw new AssertionError("Executor pool is terminated, this should never occur.");
        }
        runWithExecutor(backgroundService, Executors.callable(runnable), new EmptyResult());
    }

    public static void runOnBackgroundThread(Runnable runnable, long delay) {
        if (BuildConfig.DEBUG && backgroundService.isTerminated()) {
            throw new AssertionError("Executor pool is terminated, this should never occur.");
        }
        runOnMainThread(() -> runOnBackgroundThread(runnable), delay);
    }

    private static boolean isMainThread() {
        return Thread.currentThread() == Looper.getMainLooper().getThread();
    }

    public static void ensureMainThread() {
        if (!isMainThread()) {
            if (BuildConfig.DEV_BUILD && ChanSettings.crashOnWrongThread.get()) {
                throw new IllegalStateException("Cannot be executed on a background thread!");
            } else {
                Logger.e(TAG, "expected main thread but got " + Thread.currentThread().getName());
            }
        }
    }

    public static void ensureBackgroundThread() {
        if (isMainThread()) {
            if (BuildConfig.DEV_BUILD && ChanSettings.crashOnWrongThread.get()) {
                throw new IllegalStateException("Cannot be executed on the main thread!");
            } else {
                Logger.e(TAG, "ensureBackgroundThread() expected background thread but got main");
            }
        }
    }

    public static <T> Cancelable runWithExecutor(
            Executor executor, final Callable<T> background, final BackgroundResult<T> result
    ) {
        final AtomicBoolean canceled = new AtomicBoolean(false);
        Cancelable cancelable = () -> canceled.set(true);

        executor.execute(() -> {
            if (!canceled.get()) {
                try {
                    final T res = background.call();
                    runOnMainThread(() -> {
                        if (!canceled.get()) {
                            result.onResult(res);
                        }
                    });
                } catch (final Exception e) {
                    runOnMainThread(() -> {
                        throw new RuntimeException(e);
                    });
                }
            }
        });

        return cancelable;
    }

    public interface BackgroundResult<T> {
        void onResult(T result);
    }

    public static class EmptyResult
            implements BackgroundResult<Object> {
        @Override
        public void onResult(Object result) {}
    }

    public interface Cancelable {
        void cancel();
    }
}
