package org.floens.chan.core.di;

import com.google.gson.Gson;

import org.codejargon.feather.Provides;
import org.floens.chan.core.database.DatabaseHelper;
import org.floens.chan.core.database.DatabaseManager;
import org.floens.chan.core.repository.BoardRepository;
import org.floens.chan.core.repository.ImportExportRepository;
import org.floens.chan.core.repository.SiteRepository;

import javax.inject.Singleton;

public class RepositoryModule {

    @Provides
    @Singleton
    public ImportExportRepository provideImportExportRepository(
            DatabaseManager databaseManager,
            DatabaseHelper databaseHelper,
            Gson gson
    ) {
        return new ImportExportRepository(databaseManager, databaseHelper, gson);
    }

    @Provides
    @Singleton
    public SiteRepository provideSiteRepository(
            DatabaseManager databaseManager,
            Gson gson
    ) {
        return new SiteRepository(databaseManager, gson);
    }

    @Provides
    @Singleton
    public BoardRepository provideBoardRepository(
            DatabaseManager databaseManager,
            SiteRepository siteRepository
    ) {
        return new BoardRepository(databaseManager, siteRepository);
    }

}
