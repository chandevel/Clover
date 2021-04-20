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

import com.github.adamantcheese.chan.core.database.DatabaseBoardManager;
import com.github.adamantcheese.chan.core.database.DatabaseHelper;
import com.github.adamantcheese.chan.core.database.DatabaseSiteManager;
import com.github.adamantcheese.chan.core.repository.BoardRepository;
import com.github.adamantcheese.chan.core.repository.ImportExportRepository;
import com.github.adamantcheese.chan.core.repository.SiteRepository;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.k1rakishou.fsaf.FileManager;

import org.codejargon.feather.Provides;

import javax.inject.Singleton;

public class RepositoryModule {

    @Provides
    @Singleton
    public ImportExportRepository provideImportExportRepository(
            DatabaseHelper databaseHelper, FileManager fileManager
    ) {
        Logger.d(AppModule.DI_TAG, "Import export repository");
        return new ImportExportRepository(databaseHelper, fileManager);
    }

    @Provides
    @Singleton
    public SiteRepository provideSiteRepository(DatabaseSiteManager databaseSiteManager) {
        Logger.d(AppModule.DI_TAG, "Site repository");
        return new SiteRepository(databaseSiteManager);
    }

    @Provides
    @Singleton
    public BoardRepository provideBoardRepository(
            DatabaseBoardManager databaseBoardManager, SiteRepository siteRepository
    ) {
        Logger.d(AppModule.DI_TAG, "Board repository");
        return new BoardRepository(databaseBoardManager, siteRepository);
    }
}
