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
package com.github.adamantcheese.chan.core.database;

import com.github.adamantcheese.chan.core.model.orm.SiteModel;
import com.github.adamantcheese.chan.core.site.Site;
import com.j256.ormlite.stmt.DeleteBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public class DatabaseSiteManager {
    DatabaseHelper helper;

    public DatabaseSiteManager(DatabaseHelper helper) {
        this.helper = helper;
    }

    public Callable<SiteModel> get(int id) {
        return () -> helper.getSiteModelDao().queryForId(id);
    }

    public Callable<List<SiteModel>> getAll() {
        return () -> helper.getSiteModelDao().queryForAll();
    }

    public Callable<Integer> getCount() {
        return () -> (int) helper.getSiteModelDao().countOf();
    }

    public Callable<SiteModel> add(final SiteModel site) {
        return () -> {
            helper.getSiteModelDao().create(site);
            return site;
        };
    }

    public Callable<SiteModel> update(final SiteModel site) {
        return () -> {
            helper.getSiteModelDao().update(site);
            return site;
        };
    }

    public Callable<Map<Integer, Integer>> getOrdering() {
        return () -> {
            List<SiteModel> modelsWithOrder =
                    helper.getSiteModelDao().queryBuilder().selectColumns("id", "order").query();
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
                SiteModel m = get(id).call();
                m.order = i;
                update(m).call();
            }
            return null;
        };
    }

    public Callable<Void> deleteSite(Site site) {
        return () -> {
            DeleteBuilder<SiteModel, Integer> builder = helper.getSiteModelDao().deleteBuilder();
            builder.where().eq("id", site.id());
            builder.delete();

            return null;
        };
    }
}
