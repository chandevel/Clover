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

import com.google.gson.Gson;

import org.codejargon.feather.Provides;
import com.github.adamantcheese.chan.core.database.DatabaseHelper;
import com.github.adamantcheese.chan.core.database.DatabaseManager;
import com.github.adamantcheese.chan.core.repository.BoardRepository;
import com.github.adamantcheese.chan.core.repository.ImportExportRepository;
import com.github.adamantcheese.chan.core.repository.LastReplyRepository;
import com.github.adamantcheese.chan.core.repository.SiteRepository;

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
            DatabaseManager databaseManager
    ) {
        return new SiteRepository(databaseManager);
    }

    @Provides
    @Singleton
    public BoardRepository provideBoardRepository(
            DatabaseManager databaseManager,
            SiteRepository siteRepository
    ) {
        return new BoardRepository(databaseManager, siteRepository);
    }

    @Provides
    @Singleton
    public LastReplyRepository provideLastReplyRepository() {
        return new LastReplyRepository();
    }
}
