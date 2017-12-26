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
package org.floens.chan;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.StrictMode;

import org.floens.chan.core.database.DatabaseManager;
import org.floens.chan.core.di.AppModule;
import org.floens.chan.core.di.NetModule;
import org.floens.chan.core.di.UserAgentProvider;
import org.floens.chan.core.site.SiteManager;
import org.floens.chan.utils.AndroidUtils;
import org.floens.chan.utils.Logger;
import org.floens.chan.utils.Time;

import java.util.Locale;

import javax.inject.Inject;

import dagger.ObjectGraph;
import de.greenrobot.event.EventBus;

public class Chan extends Application implements UserAgentProvider {
    private static final String TAG = "ChanApplication";

    @SuppressLint("StaticFieldLeak")
    private static Chan instance;

    private String userAgent;
    private int activityForegroundCounter = 0;

    protected ObjectGraph graph;

    @Inject
    SiteManager siteManager;

    @Inject
    DatabaseManager databaseManager;

    public Chan() {
        instance = this;
    }

    public static Chan getInstance() {
        return instance;
    }

    public static ObjectGraph getGraph() {
        return instance.graph;
    }

    public static <T> T inject(T instance) {
        return Chan.instance.graph.inject(instance);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        final long startTime = Time.startTiming();

        AndroidUtils.init();

        userAgent = createUserAgent();

        graph = ObjectGraph.create(new AppModule(this, this), new NetModule());

        graph.inject(this);

        siteManager.initialize();

        Time.endTiming("Initializing application", startTime);

        // Start watching for slow disk reads and writes after the heavy initializing is done
        if (ChanBuild.DEVELOPER_MODE) {
            StrictMode.setThreadPolicy(
                    new StrictMode.ThreadPolicy.Builder()
                            .detectCustomSlowCalls()
                            .detectNetwork()
                            .detectDiskReads()
                            .detectDiskWrites()
                            .penaltyLog()
                            .build());
            StrictMode.setVmPolicy(
                    new StrictMode.VmPolicy.Builder()
                            .detectAll()
                            .penaltyLog()
                            .build());

            //noinspection PointlessBooleanExpression
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
//                WebView.setWebContentsDebuggingEnabled(true);
            }
        }
    }

    @Override
    public String getUserAgent() {
        return userAgent;
    }

    public void activityEnteredForeground() {
        boolean lastForeground = getApplicationInForeground();

        activityForegroundCounter++;

        if (getApplicationInForeground() != lastForeground) {
            EventBus.getDefault().post(new ForegroundChangedMessage(getApplicationInForeground()));
        }
    }

    public void activityEnteredBackground() {
        boolean lastForeground = getApplicationInForeground();

        activityForegroundCounter--;
        if (activityForegroundCounter < 0) {
            Logger.wtf(TAG, "activityForegroundCounter below 0");
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

    private String createUserAgent() {
        // User agent is <appname>/<version>
        String version = "Unknown";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Logger.e(TAG, "Error getting app version", e);
        }
        version = version.toLowerCase(Locale.ENGLISH).replace(" ", "_");
        return getString(R.string.app_name) + "/" + version;
    }
}
