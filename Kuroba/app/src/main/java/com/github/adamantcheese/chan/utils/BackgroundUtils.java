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

import androidx.core.os.HandlerCompat;

import com.github.adamantcheese.chan.BuildConfig;
import com.github.adamantcheese.chan.Chan;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static com.github.adamantcheese.chan.Chan.instance;

public class BackgroundUtils {
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final AtomicLong mainHandlerTokenCounter = new AtomicLong(0);

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

    public static long runOnMainThreadCancelable(Runnable runnable, long delay) {
        long token = mainHandlerTokenCounter.getAndIncrement();
        HandlerCompat.postDelayed(mainHandler, runnable, token, delay);

        return token;
    }

    public static void cancelRunOnMainThreadRequest(long token) {
        mainHandler.removeCallbacksAndMessages(token);
    }

    private static boolean isMainThread() {
        return Thread.currentThread() == Looper.getMainLooper().getThread();
    }

    public static void ensureMainThread() {
        if (!isMainThread()) {
            if (BuildConfig.DEV_BUILD) {
                throw new IllegalStateException("Cannot be executed on a background thread!");
            } else {
                Logger.e(
                        "BackgroundUtils",
                        "ensureMainThread() expected main thread but got " + Thread.currentThread().getName()
                );
            }
        }
    }

    public static void ensureBackgroundThread() {
        if (isMainThread()) {
            if (BuildConfig.DEV_BUILD) {
                throw new IllegalStateException("Cannot be executed on the main thread!");
            } else {
                Logger.e("BackgroundUtils", "ensureBackgroundThread() expected background thread but got main");
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

    public static Cancelable runWithExecutor(Executor executor, final Runnable background) {
        final AtomicBoolean canceled = new AtomicBoolean(false);
        Cancelable cancelable = () -> canceled.set(true);

        executor.execute(() -> {
            if (!canceled.get()) {
                try {
                    background.run();
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
