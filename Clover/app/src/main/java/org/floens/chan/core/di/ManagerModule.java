package org.floens.chan.core.di;

import android.content.Context;

import org.codejargon.feather.Provides;
import org.floens.chan.core.database.DatabaseManager;
import org.floens.chan.core.manager.BoardManager;
import org.floens.chan.core.manager.FilterEngine;
import org.floens.chan.core.manager.ReplyManager;
import org.floens.chan.core.manager.WatchManager;
import org.floens.chan.core.pool.ChanLoaderFactory;
import org.floens.chan.core.repository.BoardRepository;

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
            ChanLoaderFactory chanLoaderFactory
    ) {
        return new WatchManager(databaseManager, chanLoaderFactory);
    }
}
