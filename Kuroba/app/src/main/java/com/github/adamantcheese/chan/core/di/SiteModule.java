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

import com.github.adamantcheese.chan.core.database.DatabaseManager;
import com.github.adamantcheese.chan.core.repository.SiteRepository;
import com.github.adamantcheese.chan.core.site.SiteResolver;
import com.github.adamantcheese.chan.core.site.SiteService;
import com.github.adamantcheese.chan.utils.Logger;

import org.codejargon.feather.Provides;

import javax.inject.Singleton;

public class SiteModule {

    @Provides
    @Singleton
    public SiteResolver provideSiteResolver(
            SiteRepository siteRepository,
            DatabaseManager databaseManager
    ) {
        Logger.d(AppModule.DI_TAG, "Site resolver");
        return new SiteResolver(siteRepository, databaseManager);
    }

    @Provides
    @Singleton
    public SiteService provideSiteService(
            SiteRepository siteRepository,
            SiteResolver siteResolver
    ) {
        Logger.d(AppModule.DI_TAG, "Site service");
        return new SiteService(siteRepository, siteResolver);
    }
}
