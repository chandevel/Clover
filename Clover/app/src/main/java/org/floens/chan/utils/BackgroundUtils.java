/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
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
package org.floens.chan.utils;

import android.os.Looper;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

public class BackgroundUtils {

    public static boolean isMainThread() {
        return Thread.currentThread() == Looper.getMainLooper().getThread();
    }

    public static <T> Cancelable runWithExecutor(Executor executor, final Callable<T> background,
                                                 final BackgroundResult<T> result) {
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        Cancelable cancelable = () -> cancelled.set(true);

        executor.execute(() -> {
            if (!cancelled.get()) {
                try {
                    final T res = background.call();
                    AndroidUtils.runOnUiThread(() -> {
                        if (!cancelled.get()) {
                            result.onResult(res);
                        }
                    });
                } catch (final Exception e) {
                    AndroidUtils.runOnUiThread(() -> {
                        throw new RuntimeException(e);
                    });
                }
            }
        });

        return cancelable;
    }

    public static Cancelable runWithExecutor(Executor executor, final Runnable background) {
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        Cancelable cancelable = () -> cancelled.set(true);

        executor.execute(() -> {
            if (!cancelled.get()) {
                try {
                    background.run();
                } catch (final Exception e) {
                    AndroidUtils.runOnUiThread(() -> {
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
