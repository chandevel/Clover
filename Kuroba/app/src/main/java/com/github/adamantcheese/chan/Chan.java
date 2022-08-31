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
package com.github.adamantcheese.chan;

import static com.github.adamantcheese.chan.utils.AndroidUtils.postToEventBus;
import static java.lang.Thread.currentThread;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.*;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.core.di.*;
import com.github.adamantcheese.chan.core.manager.BoardManager;
import com.github.adamantcheese.chan.core.manager.SettingNotificationManager;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.github.adamantcheese.chan.core.repository.SiteRepository;
import com.github.adamantcheese.chan.ui.service.*;
import com.github.adamantcheese.chan.ui.widget.CancellableToast;
import com.github.adamantcheese.chan.utils.*;

import org.codejargon.feather.Feather;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.EventBusException;

import java.io.*;

import javax.inject.Inject;

import io.reactivex.exceptions.UndeliverableException;
import io.reactivex.plugins.RxJavaPlugins;

public class Chan
        extends Application
        implements DefaultActivityLifecycleCallbacks {
    private int activityForegroundCounter = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Inject
    SiteRepository siteRepository;

    @Inject
    BoardManager boardManger;

    private static Feather feather;

    public static <T> T instance(Class<T> tClass) {
        return feather.instance(tClass);
    }

    public static <T> void inject(T instance) {
        feather.injectFields(instance);
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        // remove this if you need to debug some sort of event bus issue
        try {
            EventBus.builder().logNoSubscriberMessages(false).installDefaultEventBus();
        } catch (EventBusException e) {
            if (e.getMessage() != null && !e.getMessage().contains("already exists")) {
                throw e;
            } else if (e.getMessage() == null) {
                throw e;
            }
        }
    }

    private void postEventBusOneSecondMessage() {
        EventBus.getDefault().post("TICK");
        handler.postDelayed(this::postEventBusOneSecondMessage, 1000);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(this);

        AndroidUtils.init(this);
        BitmapRepository.initialize(this);

        WatchNotification.setupChannel();
        SavingNotification.setupChannel();
        LastPageNotification.setupChannel();

        feather = Feather.with(new AppModule(), new RepositoryModule(), new ManagerModule());
        feather.injectFields(this);

        siteRepository.initialize();
        boardManger.initialize();

        RxJavaPlugins.setErrorHandler(e -> {
            if (e instanceof UndeliverableException) {
                e = e.getCause();
            }

            if (e == null) {
                return;
            }

            if (e instanceof IOException) {
                // fine, irrelevant network problem or API that throws on cancellation
                return;
            }
            if (e instanceof InterruptedException) {
                // fine, some blocking code was interrupted by a dispose call
                return;
            }
            if (e instanceof RuntimeException && e.getCause() instanceof InterruptedException) {
                // fine, DB synchronous call (via runTask) was interrupted when a reactive stream
                // was disposed of.
                return;
            }
            if ((e instanceof NullPointerException) || (e instanceof IllegalArgumentException)) {
                // that's likely a bug in the application
                currentThread().getUncaughtExceptionHandler().uncaughtException(currentThread(), e);
                return;
            }
            if (e instanceof IllegalStateException) {
                // that's a bug in RxJava or in a custom operator
                currentThread().getUncaughtExceptionHandler().uncaughtException(currentThread(), e);
                return;
            }

            Logger.e("APP", "RxJava undeliverable exception", e);

            // Do not exit the app here! Most of the time an exception that comes here is not a
            // fatal one. We only want to log and report them to analyze later. The app should be
            // able to continue running after that.
        });

        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            //if there's any uncaught crash stuff, just dump them to the log and exit immediately
            String errorText = exceptionToString(false, e);

            Logger.e("UNCAUGHT", errorText);
            Logger.e("UNCAUGHT", "------------------------------");
            Logger.e("UNCAUGHT", "END OF CURRENT RUNTIME MESSAGES");
            Logger.e("UNCAUGHT", "------------------------------");
            Logger.e("UNCAUGHT", "Android API Level: " + Build.VERSION.SDK_INT);
            Logger.e("UNCAUGHT", "App Version: " + BuildConfigUtils.VERSION);
            Logger.e("UNCAUGHT", "Phone Model: " + Build.MANUFACTURER + " " + Build.MODEL);

            if (e instanceof OutOfMemoryError) {
                Logger.e("UNCAUGHT", "Out of memory! Memory stats:");
                Runtime runtime = Runtime.getRuntime();
                long usedMemInMB = (runtime.totalMemory() - runtime.freeMemory()) / 1048576L;
                long maxHeapSizeInMB = runtime.maxMemory() / 1048576L;
                long availHeapSizeInMB = maxHeapSizeInMB - usedMemInMB;
                Logger.e("UNCAUGHT", "Used memory (MB): " + usedMemInMB);
                Logger.e("UNCAUGHT", "Max memory (MB): " + maxHeapSizeInMB);
                Logger.e("UNCAUGHT", "Available memory (MB): " + availHeapSizeInMB);
            }

            System.exit(999);
        });

        SettingNotificationManager.postNotification(null);
    }

    private String exceptionToString(boolean isCalledFromRxJavaHandler, Throwable e) {
        try (StringWriter sw = new StringWriter()) {
            try (PrintWriter pw = new PrintWriter(sw)) {
                e.printStackTrace(pw);
                String stackTrace = sw.toString();

                if (isCalledFromRxJavaHandler) {
                    return "Called from RxJava onError handler.\n" + stackTrace;
                }

                return "Called from unhandled exception handler.\n" + stackTrace;
            }
        } catch (IOException ex) {
            throw new RuntimeException("Error while trying to convert exception to string!", ex);
        }
    }

    public boolean getApplicationInForeground() {
        return activityForegroundCounter > 0;
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        boolean lastForeground = getApplicationInForeground();

        activityForegroundCounter++;

        if (getApplicationInForeground() != lastForeground) {
            postToEventBus(new ForegroundChangedMessage(getApplicationInForeground()));
            postEventBusOneSecondMessage();
        }
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        boolean lastForeground = getApplicationInForeground();

        activityForegroundCounter--;
        if (activityForegroundCounter < 0) {
            Logger.wtf("ChanApplication", "activityForegroundCounter below 0");
        }

        if (getApplicationInForeground() != lastForeground) {
            postToEventBus(new ForegroundChangedMessage(getApplicationInForeground()));
            handler.removeCallbacks(this::postEventBusOneSecondMessage);
        }
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        BackgroundUtils.cleanup();
        CancellableToast.cleanup();
        handler.removeCallbacks(this::postEventBusOneSecondMessage);
    }

    public static class ForegroundChangedMessage {
        public boolean inForeground;

        public ForegroundChangedMessage(boolean inForeground) {
            this.inForeground = inForeground;
        }
    }
}
