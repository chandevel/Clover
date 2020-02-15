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
package com.github.adamantcheese.chan.core.di;

import android.content.Context;

import com.github.adamantcheese.chan.core.database.DatabaseManager;
import com.github.adamantcheese.chan.core.manager.ArchivesManager;
import com.github.adamantcheese.chan.core.manager.BoardManager;
import com.github.adamantcheese.chan.core.manager.ChanLoaderManager;
import com.github.adamantcheese.chan.core.manager.FilterEngine;
import com.github.adamantcheese.chan.core.manager.FilterWatchManager;
import com.github.adamantcheese.chan.core.manager.PageRequestManager;
import com.github.adamantcheese.chan.core.manager.ReplyManager;
import com.github.adamantcheese.chan.core.manager.ReportManager;
import com.github.adamantcheese.chan.core.manager.SavedThreadLoaderManager;
import com.github.adamantcheese.chan.core.manager.SettingsNotificationManager;
import com.github.adamantcheese.chan.core.manager.ThreadSaveManager;
import com.github.adamantcheese.chan.core.manager.WakeManager;
import com.github.adamantcheese.chan.core.manager.WatchManager;
import com.github.adamantcheese.chan.core.repository.BoardRepository;
import com.github.adamantcheese.chan.core.repository.SavedThreadLoaderRepository;
import com.github.adamantcheese.chan.core.site.parser.MockReplyManager;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.k1rakishou.fsaf.FileManager;
import com.google.gson.Gson;

import org.codejargon.feather.Provides;

import java.io.File;

import javax.inject.Named;
import javax.inject.Singleton;

import okhttp3.OkHttpClient;

import static com.github.adamantcheese.chan.core.di.AppModule.getCacheDir;
import static com.github.adamantcheese.chan.core.di.NetModule.THREAD_SAVE_MANAGER_OKHTTP_CLIENT_NAME;

public class ManagerModule {
    private static final String CRASH_LOGS_DIR_NAME = "crashlogs";

    @Provides
    @Singleton
    public BoardManager provideBoardManager(BoardRepository boardRepository) {
        Logger.d(AppModule.DI_TAG, "Board manager");
        return new BoardManager(boardRepository);
    }

    @Provides
    @Singleton
    public FilterEngine provideFilterEngine(DatabaseManager databaseManager) {
        Logger.d(AppModule.DI_TAG, "Filter engine");
        return new FilterEngine(databaseManager);
    }

    @Provides
    @Singleton
    public ReplyManager provideReplyManager(Context applicationContext) {
        Logger.d(AppModule.DI_TAG, "Reply manager");
        return new ReplyManager(applicationContext);
    }

    @Provides
    @Singleton
    public ChanLoaderManager provideChanLoaderFactory() {
        Logger.d(AppModule.DI_TAG, "Chan loader factory");
        return new ChanLoaderManager();
    }

    @Provides
    @Singleton
    public WatchManager provideWatchManager(
            DatabaseManager databaseManager,
            ChanLoaderManager chanLoaderManager,
            WakeManager wakeManager,
            PageRequestManager pageRequestManager,
            ThreadSaveManager threadSaveManager,
            FileManager fileManager
    ) {
        Logger.d(AppModule.DI_TAG, "Watch manager");
        return new WatchManager(databaseManager,
                chanLoaderManager,
                wakeManager,
                pageRequestManager,
                threadSaveManager,
                fileManager
        );
    }

    @Provides
    @Singleton
    public WakeManager provideWakeManager() {
        Logger.d(AppModule.DI_TAG, "Wake manager");
        return new WakeManager();
    }

    @Provides
    @Singleton
    public FilterWatchManager provideFilterWatchManager(
            WakeManager wakeManager,
            FilterEngine filterEngine,
            WatchManager watchManager,
            ChanLoaderManager chanLoaderManager,
            BoardRepository boardRepository,
            DatabaseManager databaseManager
    ) {
        Logger.d(AppModule.DI_TAG, "Filter watch manager");
        return new FilterWatchManager(wakeManager,
                filterEngine,
                watchManager,
                chanLoaderManager,
                boardRepository,
                databaseManager
        );
    }

    @Provides
    @Singleton
    public PageRequestManager providePageRequestManager() {
        Logger.d(AppModule.DI_TAG, "Page request manager");
        return new PageRequestManager();
    }

    @Provides
    @Singleton
    public ArchivesManager provideArchivesManager() {
        Logger.d(AppModule.DI_TAG, "Archives manager (4chan only)");
        return new ArchivesManager();
    }

    @Provides
    @Singleton
    public ThreadSaveManager provideSaveThreadManager(
            DatabaseManager databaseManager,
            @Named(THREAD_SAVE_MANAGER_OKHTTP_CLIENT_NAME) OkHttpClient okHttpClient,
            SavedThreadLoaderRepository savedThreadLoaderRepository,
            FileManager fileManager
    ) {
        Logger.d(AppModule.DI_TAG, "Thread save manager");
        return new ThreadSaveManager(databaseManager, okHttpClient, savedThreadLoaderRepository, fileManager);
    }

    @Provides
    @Singleton
    public SavedThreadLoaderManager provideSavedThreadLoaderManager(
            SavedThreadLoaderRepository savedThreadLoaderRepository, FileManager fileManager
    ) {
        Logger.d(AppModule.DI_TAG, "Saved thread loader manager");
        return new SavedThreadLoaderManager(savedThreadLoaderRepository, fileManager);
    }

    @Provides
    @Singleton
    public MockReplyManager provideMockReplyManager() {
        Logger.d(AppModule.DI_TAG, "Mock reply manager");
        return new MockReplyManager();
    }

    @Provides
    @Singleton
    public ReportManager provideReportManager(
            NetModule.ProxiedOkHttpClient okHttpClient,
            Gson gson,
            ThreadSaveManager threadSaveManager
    ) {
        Logger.d(AppModule.DI_TAG, "Report manager");
        File cacheDir = getCacheDir();

        return new ReportManager(
                okHttpClient.getProxiedClient(),
                threadSaveManager,
                gson,
                new File(cacheDir, CRASH_LOGS_DIR_NAME)
        );
    }

    @Provides
    @Singleton
    public SettingsNotificationManager provideSettingsNotificationManager() {
        return new SettingsNotificationManager();
    }
}
