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

import org.codejargon.feather.Provides;
import org.floens.chan.core.database.DatabaseManager;
import org.floens.chan.core.repository.SiteRepository;
import org.floens.chan.core.site.SiteResolver;
import org.floens.chan.core.site.SiteService;

import javax.inject.Singleton;

public class SiteModule {

    @Provides
    @Singleton
    public SiteResolver provideSiteResolver(
            SiteRepository siteRepository,
            DatabaseManager databaseManager
    ) {
        return new SiteResolver(siteRepository, databaseManager);
    }

    @Provides
    @Singleton
    public SiteService provideSiteService(
            SiteRepository siteRepository,
            SiteResolver siteResolver
    ) {
        return new SiteService(siteRepository, siteResolver);
    }
}
