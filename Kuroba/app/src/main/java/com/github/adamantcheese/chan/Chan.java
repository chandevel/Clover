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

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.core.di.AppModule;
import com.github.adamantcheese.chan.core.di.ManagerModule;
import com.github.adamantcheese.chan.core.di.RepositoryModule;
import com.github.adamantcheese.chan.core.manager.BoardManager;
import com.github.adamantcheese.chan.core.manager.ReportManager;
import com.github.adamantcheese.chan.core.manager.SettingsNotificationManager;
import com.github.adamantcheese.chan.core.manager.SettingsNotificationManager.SettingNotification;
import com.github.adamantcheese.chan.core.net.NetUtils;
import com.github.adamantcheese.chan.core.repository.BitmapRepository;
import com.github.adamantcheese.chan.core.repository.DrawableRepository;
import com.github.adamantcheese.chan.core.repository.SiteRepository;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.service.LastPageNotification;
import com.github.adamantcheese.chan.ui.service.SavingNotification;
import com.github.adamantcheese.chan.ui.service.WatchNotification;
import com.github.adamantcheese.chan.ui.widget.CancellableSnackbar;
import com.github.adamantcheese.chan.ui.widget.CancellableToast;
import com.github.adamantcheese.chan.utils.AndroidUtils;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.github.adamantcheese.chan.utils.Logger;

import org.codejargon.feather.Feather;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.EventBusException;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import javax.inject.Inject;

import io.reactivex.exceptions.UndeliverableException;
import io.reactivex.plugins.RxJavaPlugins;

import static com.github.adamantcheese.chan.utils.AndroidUtils.isEmulator;
import static com.github.adamantcheese.chan.utils.AndroidUtils.postToEventBus;
import static java.lang.Thread.currentThread;

public class Chan
        extends Application
        implements DefaultActivityLifecycleCallbacks {
    private int activityForegroundCounter = 0;

    @Inject
    SiteRepository siteRepository;

    @Inject
    BoardManager boardManger;

    @Inject
    ReportManager reportManager;

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

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(this);

        AndroidUtils.init(this);
        BitmapRepository.initialize(this);
        DrawableRepository.initialize(this);

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

            onUnhandledException(e, exceptionToString(true, e));
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
            Logger.e("UNCAUGHT", "App Version: " + BuildConfig.VERSION_NAME);
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

            onUnhandledException(e, errorText);

            System.exit(999);
        });

        SettingsNotificationManager.postNotification(SettingNotification.Default);
        if (ChanSettings.collectCrashLogs.get()) {
            if (reportManager.countCrashLogs() > 0) {
                SettingsNotificationManager.postNotification(SettingNotification.CrashLog);
            }
        }
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

    private void onUnhandledException(Throwable exception, String error) {
        //don't upload debug crashes

        if ("Debug crash".equals(exception.getMessage())) {
            return;
        }

        if (isEmulator()) {
            return;
        }

        if (ChanSettings.collectCrashLogs.get()) {
            reportManager.storeCrashLog(exception.getMessage(), error);
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
        }
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        BackgroundUtils.cleanup();
        NetUtils.cleanup();
        CancellableToast.cleanup();
        CancellableSnackbar.cleanup();
    }

    public static class ForegroundChangedMessage {
        public boolean inForeground;

        public ForegroundChangedMessage(boolean inForeground) {
            this.inForeground = inForeground;
        }
    }
}
