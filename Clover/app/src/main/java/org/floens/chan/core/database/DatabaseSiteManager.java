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


import com.j256.ormlite.stmt.DeleteBuilder;
import com.j256.ormlite.stmt.QueryBuilder;

import org.floens.chan.core.model.orm.SiteModel;
import org.floens.chan.core.site.Site;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public class DatabaseSiteManager {
    private DatabaseManager databaseManager;
    private DatabaseHelper helper;

    public DatabaseSiteManager(DatabaseManager databaseManager, DatabaseHelper helper) {
        this.databaseManager = databaseManager;
        this.helper = helper;
    }

    public Callable<SiteModel> byId(int id) {
        return () -> helper.siteDao.queryForId(id);
    }

    public Callable<List<SiteModel>> getAll() {
        return () -> helper.siteDao.queryForAll();
    }

    public Callable<Long> getCount() {
        return () -> helper.siteDao.countOf();
    }

    public Callable<SiteModel> add(final SiteModel site) {
        return () -> {
            helper.siteDao.create(site);
            return site;
        };
    }

    public Callable<SiteModel> update(final SiteModel site) {
        return () -> {
            helper.siteDao.update(site);
            return site;
        };
    }

    public Callable<SiteModel> updateId(final SiteModel site, final int newId) {
        return () -> {
            helper.siteDao.updateId(site, newId);
            return site;
        };
    }

    public Callable<Map<Integer, Integer>> getOrdering() {
        return () -> {
            QueryBuilder<SiteModel, Integer> q = helper.siteDao.queryBuilder();
            q.selectColumns("id", "order");
            List<SiteModel> modelsWithOrder = q.query();
            Map<Integer, Integer> ordering = new HashMap<>();
            for (SiteModel siteModel : modelsWithOrder) {
                ordering.put(siteModel.id, siteModel.order);
            }
            return ordering;
        };
    }

    public Callable<Void> updateOrdering(final List<Integer> siteIdsWithCorrectOrder) {
        return () -> {
            for (int i = 0; i < siteIdsWithCorrectOrder.size(); i++) {
                Integer id = siteIdsWithCorrectOrder.get(i);
                SiteModel m = helper.siteDao.queryForId(id);
                m.order = i;
                helper.siteDao.update(m);
            }
            return null;
        };
    }

    public Callable<Void> deleteSite(Site site) {
        return () -> {
            DeleteBuilder<SiteModel, Integer> builder = helper.siteDao.deleteBuilder();
            builder.where().eq("id", site.id());
            builder.delete();

            return null;
        };
    }
}
