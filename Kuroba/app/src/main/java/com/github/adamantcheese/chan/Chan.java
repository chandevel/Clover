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
import android.os.Bundle;

import com.github.adamantcheese.chan.core.cache.downloader.FileCacheException;
import com.github.adamantcheese.chan.core.database.DatabaseManager;
import com.github.adamantcheese.chan.core.di.AppModule;
import com.github.adamantcheese.chan.core.di.DatabaseModule;
import com.github.adamantcheese.chan.core.di.GsonModule;
import com.github.adamantcheese.chan.core.di.ManagerModule;
import com.github.adamantcheese.chan.core.di.NetModule;
import com.github.adamantcheese.chan.core.di.RepositoryModule;
import com.github.adamantcheese.chan.core.di.SiteModule;
import com.github.adamantcheese.chan.core.manager.ArchivesManager;
import com.github.adamantcheese.chan.core.manager.BoardManager;
import com.github.adamantcheese.chan.core.manager.FilterWatchManager;
import com.github.adamantcheese.chan.core.manager.ReportManager;
import com.github.adamantcheese.chan.core.manager.SettingsNotificationManager;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.SiteService;
import com.github.adamantcheese.chan.ui.service.LastPageNotification;
import com.github.adamantcheese.chan.ui.service.SavingNotification;
import com.github.adamantcheese.chan.ui.service.WatchNotification;
import com.github.adamantcheese.chan.ui.settings.SettingNotificationType;
import com.github.adamantcheese.chan.utils.AndroidUtils;
import com.github.adamantcheese.chan.utils.Logger;

import org.codejargon.feather.Feather;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import javax.inject.Inject;

import io.reactivex.exceptions.UndeliverableException;
import io.reactivex.plugins.RxJavaPlugins;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getIsOfficial;
import static com.github.adamantcheese.chan.utils.AndroidUtils.postToEventBus;
import static java.lang.Thread.currentThread;

public class Chan
        extends Application
        implements Application.ActivityLifecycleCallbacks {
    private int activityForegroundCounter = 0;

    @Inject
    DatabaseManager databaseManager;

    @Inject
    SiteService siteService;

    @Inject
    BoardManager boardManager;

    @Inject
    ReportManager reportManager;

    @Inject
    SettingsNotificationManager settingsNotificationManager;

    private static Feather feather;

    public static <T> T instance(Class<T> tClass) {
        return feather.instance(tClass);
    }

    public static <T> T inject(T instance) {
        feather.injectFields(instance);
        return instance;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        AndroidUtils.init(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(this);

        feather = Feather.with(
                new AppModule(this),
                new DatabaseModule(),
                new NetModule(),
                new GsonModule(),
                new RepositoryModule(),
                new SiteModule(),
                new ManagerModule()
        );
        feather.injectFields(this);

        //Needs to happen before any sites are processed, in case they request archives
        feather.instance(ArchivesManager.class);

        siteService.initialize();
        boardManager.initialize();
        databaseManager.initializeAndTrim();

        //create these classes here even if they aren't explicitly used, so they do their background startup tasks
        //and so that they're available for feather later on for archives/filter watch waking
        feather.instance(FilterWatchManager.class);

        WatchNotification.setupChannel();
        SavingNotification.setupChannel();
        LastPageNotification.setupChannel();

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
            if (e instanceof FileCacheException.CancellationException
                    || e instanceof FileCacheException.FileNotFoundOnTheServerException) {
                // fine, sometimes they get through all the checks but it doesn't really matter
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
            Logger.e("UNCAUGHT", "Development Build: " + (getIsOfficial() ? "No" : "Yes"));
            Logger.e("UNCAUGHT", "Phone Model: " + Build.MANUFACTURER + " " + Build.MODEL);

            /*
            Runtime runtime = Runtime.getRuntime();
            long usedMemInMB = (runtime.totalMemory() - runtime.freeMemory()) / 1048576L;
            long maxHeapSizeInMB = runtime.maxMemory() / 1048576L;
            long availHeapSizeInMB = maxHeapSizeInMB - usedMemInMB;
            Logger.e("UNCAUGHT", "Used memory (MB): " + usedMemInMB);
            Logger.e("UNCAUGHT", "Max memory (MB): " + maxHeapSizeInMB);
            Logger.e("UNCAUGHT", "Available memory (MB): " + availHeapSizeInMB);
             */

            onUnhandledException(e, errorText);

            System.exit(999);
        });

        if (ChanSettings.collectCrashLogs.get()) {
            if (reportManager.hasCrashLogs()) {
                settingsNotificationManager.notify(SettingNotificationType.CrashLogs);
            }
        }
    }

    private boolean isEmulator() {
        return Build.MODEL.contains("google_sdk") || Build.MODEL.contains("Emulator") || Build.MODEL.contains(
                "Android SDK");
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

    private void activityEnteredForeground() {
        boolean lastForeground = getApplicationInForeground();

        activityForegroundCounter++;

        if (getApplicationInForeground() != lastForeground) {
            postToEventBus(new ForegroundChangedMessage(getApplicationInForeground()));
        }
    }

    private void activityEnteredBackground() {
        boolean lastForeground = getApplicationInForeground();

        activityForegroundCounter--;
        if (activityForegroundCounter < 0) {
            Logger.wtf("ChanApplication", "activityForegroundCounter below 0");
        }

        if (getApplicationInForeground() != lastForeground) {
            postToEventBus(new ForegroundChangedMessage(getApplicationInForeground()));
        }
    }

    public boolean getApplicationInForeground() {
        return activityForegroundCounter > 0;
    }

    public static class ForegroundChangedMessage {
        public boolean inForeground;

        public ForegroundChangedMessage(boolean inForeground) {
            this.inForeground = inForeground;
        }
    }

    //region Empty Methods
    @SuppressWarnings("EmptyMethod")
    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityStarted(Activity activity) {
        activityEnteredForeground();
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void onActivityResumed(Activity activity) {
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void onActivityPaused(Activity activity) {
    }

    @Override
    public void onActivityStopped(Activity activity) {
        activityEnteredBackground();
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void onActivityDestroyed(Activity activity) {
    }
    //endregion Empty Methods
}
