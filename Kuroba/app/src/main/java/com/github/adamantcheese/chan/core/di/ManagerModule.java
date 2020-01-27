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
import com.github.adamantcheese.chan.core.manager.FilterEngine;
import com.github.adamantcheese.chan.core.manager.FilterWatchManager;
import com.github.adamantcheese.chan.core.manager.PageRequestManager;
import com.github.adamantcheese.chan.core.manager.ReplyManager;
import com.github.adamantcheese.chan.core.manager.SavedThreadLoaderManager;
import com.github.adamantcheese.chan.core.manager.ThreadSaveManager;
import com.github.adamantcheese.chan.core.manager.WakeManager;
import com.github.adamantcheese.chan.core.manager.WatchManager;
import com.github.adamantcheese.chan.core.model.json.site.SiteConfig;
import com.github.adamantcheese.chan.core.pool.ChanLoaderFactory;
import com.github.adamantcheese.chan.core.repository.BoardRepository;
import com.github.adamantcheese.chan.core.repository.SavedThreadLoaderRepository;
import com.github.adamantcheese.chan.core.settings.json.JsonSettings;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.sites.chan4.Chan4;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.k1rakishou.fsaf.FileManager;

import org.codejargon.feather.Provides;

import javax.inject.Named;
import javax.inject.Singleton;

import okhttp3.OkHttpClient;

import static com.github.adamantcheese.chan.core.di.NetModule.THREAD_SAVE_MANAGER_OKHTTP_CLIENT_NAME;

public class ManagerModule {

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
    public ChanLoaderFactory provideChanLoaderFactory() {
        Logger.d(AppModule.DI_TAG, "Chan loader factory");
        return new ChanLoaderFactory();
    }

    @Provides
    @Singleton
    public WatchManager provideWatchManager(
            DatabaseManager databaseManager,
            ChanLoaderFactory chanLoaderFactory,
            WakeManager wakeManager,
            PageRequestManager pageRequestManager,
            ThreadSaveManager threadSaveManager,
            FileManager fileManager
    ) {
        Logger.d(AppModule.DI_TAG, "Watch manager");
        return new WatchManager(databaseManager,
                chanLoaderFactory,
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
            ChanLoaderFactory chanLoaderFactory,
            BoardRepository boardRepository,
            DatabaseManager databaseManager
    ) {
        Logger.d(AppModule.DI_TAG, "Filter watch manager");
        return new FilterWatchManager(wakeManager,
                filterEngine,
                watchManager,
                chanLoaderFactory,
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
    public ArchivesManager provideArchivesManager()
            throws Exception {
        Logger.d(AppModule.DI_TAG, "Archives manager (4chan only)");
        //archives are only for 4chan, make a dummy site instance for this method
        Site chan4 = Chan4.class.newInstance();
        chan4.initialize(9999, new SiteConfig(), new JsonSettings());
        chan4.postInitialize();
        return new ArchivesManager(chan4);
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
}
