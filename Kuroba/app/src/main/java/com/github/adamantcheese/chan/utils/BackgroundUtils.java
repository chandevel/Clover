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

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.github.adamantcheese.chan.BuildConfig;
import com.github.adamantcheese.chan.Chan;
import com.github.adamantcheese.chan.core.settings.ChanSettings;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.github.adamantcheese.chan.Chan.instance;

public class BackgroundUtils {
    private static final String TAG = "BackgroundUtils";
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static boolean isInForeground() {
        return ((Chan) instance(Context.class)).getApplicationInForeground();
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

    public static void runOnBackgroundThread(Runnable runnable) {
        runOnMainThread(() -> {
            try {
                instance(ExecutorService.class).submit(runnable).get();
            } catch (Exception ignored) {
            }
        });
    }

    public static void runOnBackgroundThread(Runnable runnable, long delay) {
        runOnMainThread(() -> {
            try {
                instance(ExecutorService.class).submit(runnable).get();
            } catch (Exception ignored) {
            }
        }, delay);
    }

    private static boolean isMainThread() {
        return Thread.currentThread() == Looper.getMainLooper().getThread();
    }

    public static void ensureMainThread() {
        if (!isMainThread()) {
            if (BuildConfig.DEV_BUILD && ChanSettings.crashOnSafeThrow.get()) {
                throw new IllegalStateException("Cannot be executed on a background thread!");
            } else {
                Logger.e(TAG, "expected main thread but got " + Thread.currentThread().getName());
            }
        }
    }

    public static void ensureBackgroundThread() {
        if (isMainThread()) {
            if (BuildConfig.DEV_BUILD && ChanSettings.crashOnSafeThrow.get()) {
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

    public interface Cancelable {
        void cancel();
    }
}
