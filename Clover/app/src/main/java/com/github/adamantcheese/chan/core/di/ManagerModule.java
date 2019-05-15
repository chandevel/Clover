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
package com.github.adamantcheese.chan.core.di;

import android.content.Context;

import org.codejargon.feather.Provides;
import com.github.adamantcheese.chan.core.database.DatabaseManager;
import com.github.adamantcheese.chan.core.manager.BoardManager;
import com.github.adamantcheese.chan.core.manager.FilterEngine;
import com.github.adamantcheese.chan.core.manager.FilterWatchManager;
import com.github.adamantcheese.chan.core.manager.PageRequestManager;
import com.github.adamantcheese.chan.core.manager.ReplyManager;
import com.github.adamantcheese.chan.core.manager.WakeManager;
import com.github.adamantcheese.chan.core.manager.WatchManager;
import com.github.adamantcheese.chan.core.pool.ChanLoaderFactory;
import com.github.adamantcheese.chan.core.repository.BoardRepository;

import javax.inject.Singleton;

public class ManagerModule {

    @Provides
    @Singleton
    public BoardManager provideBoardManager(
            BoardRepository boardRepository
    ) {
        return new BoardManager(boardRepository);
    }

    @Provides
    @Singleton
    public FilterEngine provideFilterEngine(
            DatabaseManager databaseManager,
            BoardManager boardManager
    ) {
        return new FilterEngine(databaseManager, boardManager);
    }

    @Provides
    @Singleton
    public ReplyManager provideReplyManager(Context applicationContext) {
        return new ReplyManager(applicationContext);
    }

    @Provides
    @Singleton
    public ChanLoaderFactory provideChanLoaderFactory() {
        return new ChanLoaderFactory();
    }

    @Provides
    @Singleton
    public WatchManager provideWatchManager(
            DatabaseManager databaseManager,
            ChanLoaderFactory chanLoaderFactory,
            WakeManager wakeManager,
            PageRequestManager pageRequestManager
    ) {
        return new WatchManager(databaseManager, chanLoaderFactory, wakeManager, pageRequestManager);
    }

    @Provides
    @Singleton
    public WakeManager provideWakeManager() {
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
        return new FilterWatchManager(wakeManager, filterEngine, watchManager, chanLoaderFactory, boardRepository, databaseManager);
    }

    @Provides
    @Singleton
    public PageRequestManager providePageRequestManager() {
        return new PageRequestManager();
    }
}
