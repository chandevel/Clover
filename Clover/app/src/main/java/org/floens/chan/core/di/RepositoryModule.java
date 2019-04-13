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
