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
import android.os.Bundle;

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
import com.github.adamantcheese.chan.core.site.SiteService;
import com.github.adamantcheese.chan.utils.AndroidUtils;
import com.github.adamantcheese.chan.utils.Logger;

import org.codejargon.feather.Feather;
import org.greenrobot.eventbus.EventBus;

import java.io.IOException;

import javax.inject.Inject;

import io.reactivex.exceptions.UndeliverableException;
import io.reactivex.plugins.RxJavaPlugins;

public class Chan extends Application implements Application.ActivityLifecycleCallbacks {
    private int activityForegroundCounter = 0;

    @Inject
    DatabaseManager databaseManager;

    @Inject
    SiteService siteService;

    @Inject
    BoardManager boardManager;

    private static Feather feather;

    public static Feather injector() {
        return feather;
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

        siteService.initialize();
        boardManager.initialize();
        databaseManager.initializeAndTrim();

        //create these classes here even if they aren't explicitly used, so they do their background startup tasks
        //and so that they're available for feather later on for archives/filter watch waking
        feather.instance(ArchivesManager.class);
        feather.instance(FilterWatchManager.class);

        // Start watching for slow disk reads and writes after the heavy initializing is done
//        if (BuildConfig.DEBUG) {
//            StrictMode.setThreadPolicy(
//                    new StrictMode.ThreadPolicy.Builder()
//                            .detectCustomSlowCalls()
//                            .detectNetwork()
//                            .detectDiskReads()
//                            .detectDiskWrites()
//                            .penaltyLog()
//                            .build());
//            StrictMode.setVmPolicy(
//                    new StrictMode.VmPolicy.Builder()
//                            .detectAll()
//                            .penaltyLog()
//                            .build());
//        }

        RxJavaPlugins.setErrorHandler(e -> {
            if (e instanceof UndeliverableException) {
                e = e.getCause();
            }
            if (e instanceof IOException) {
                // fine, irrelevant network problem or API that throws on cancellation
                return;
            }
            if (e instanceof InterruptedException) {
                // fine, some blocking code was interrupted by a dispose call
                return;
            }
            if ((e instanceof NullPointerException) || (e instanceof IllegalArgumentException)) {
                // that's likely a bug in the application
                Thread.currentThread().getUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), e);
                return;
            }
            if (e instanceof IllegalStateException) {
                // that's a bug in RxJava or in a custom operator
                Thread.currentThread().getUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), e);
                return;
            }

            Logger.e("APP", "RxJava undeliverable exception", e);
        });
    }

    private void activityEnteredForeground() {
        boolean lastForeground = getApplicationInForeground();

        activityForegroundCounter++;

        if (getApplicationInForeground() != lastForeground) {
            EventBus.getDefault().post(new ForegroundChangedMessage(getApplicationInForeground()));
        }
    }

    private void activityEnteredBackground() {
        boolean lastForeground = getApplicationInForeground();

        activityForegroundCounter--;
        if (activityForegroundCounter < 0) {
            Logger.wtf("ChanApplication", "activityForegroundCounter below 0");
        }

        if (getApplicationInForeground() != lastForeground) {
            EventBus.getDefault().post(new ForegroundChangedMessage(getApplicationInForeground()));
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
