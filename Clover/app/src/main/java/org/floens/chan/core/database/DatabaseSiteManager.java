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
package org.floens.chan.core.database;


import org.floens.chan.core.model.orm.SiteModel;

import java.util.List;
import java.util.concurrent.Callable;

public class DatabaseSiteManager {
    private DatabaseManager databaseManager;
    private DatabaseHelper helper;

    public DatabaseSiteManager(DatabaseManager databaseManager, DatabaseHelper helper) {
        this.databaseManager = databaseManager;
        this.helper = helper;
    }

    public Callable<List<SiteModel>> getAll() {
        return new Callable<List<SiteModel>>() {
            @Override
            public List<SiteModel> call() throws Exception {
                return helper.siteDao.queryForAll();
            }
        };
    }

    public Callable<Long> getCount() {
        return () -> helper.siteDao.countOf();
    }

    public Callable<SiteModel> add(final SiteModel site) {
        return new Callable<SiteModel>() {
            @Override
            public SiteModel call() throws Exception {
                helper.siteDao.create(site);

                return site;
            }
        };
    }

    public Callable<SiteModel> update(final SiteModel site) {
        return new Callable<SiteModel>() {
            @Override
            public SiteModel call() throws Exception {
                helper.siteDao.update(site);

                return site;
            }
        };
    }

    public Callable<SiteModel> updateId(final SiteModel site, final int newId) {
        return new Callable<SiteModel>() {
            @Override
            public SiteModel call() throws Exception {
                helper.siteDao.updateId(site, newId);

                return site;
            }
        };
    }
}
