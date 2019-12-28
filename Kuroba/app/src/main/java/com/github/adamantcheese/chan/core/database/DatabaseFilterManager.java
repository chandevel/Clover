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

import com.github.adamantcheese.chan.core.model.orm.Filter;
import com.j256.ormlite.stmt.DeleteBuilder;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.inject;

public class DatabaseFilterManager {
    private static final String TAG = "DatabaseFilterManager";

    @Inject
    DatabaseHelper helper;

    public DatabaseFilterManager() {
        inject(this);
    }

    public Callable<Filter> createFilter(final Filter filter) {
        return () -> {
            helper.filterDao.create(filter);
            return filter;
        };
    }

    public Callable<List<Filter>> updateFilters(final List<Filter> filters) {
        return () -> {
            for (Filter filter : filters) {
                helper.filterDao.update(filter);
            }
            return filters;
        };
    }

    public Callable<Void> deleteFilter(final Filter filter) {
        return () -> {
            helper.filterDao.delete(filter);
            return null;
        };
    }

    public Callable<Filter> updateFilter(final Filter filter) {
        return () -> {
            helper.filterDao.update(filter);
            return filter;
        };
    }

    public Callable<List<Filter>> getFilters() {
        return () -> {
            List<Filter> filters = helper.filterDao.queryForAll();
            Collections.sort(filters, (lhs, rhs) -> lhs.order - rhs.order);
            updateFilters(filters);
            return filters;
        };
    }

    public Callable<Long> getCount() {
        return () -> helper.filterDao.countOf();
    }

    public Callable<Void> deleteFilters(List<Filter> filtersToDelete) {
        return () -> {
            Set<Integer> filterIdSet = new HashSet<>();

            for (Filter filter : filtersToDelete) {
                filterIdSet.add(filter.id);
            }

            DeleteBuilder<Filter, Integer> builder = helper.filterDao.deleteBuilder();
            builder.where().in("id", filterIdSet);

            int deletedCount = builder.delete();

            if (deletedCount != filterIdSet.size()) {
                throw new IllegalStateException(
                        "Deleted count didn't equal filterIdList.size(). (deletedCount = " + deletedCount + "), "
                                + "(filterIdSet = " + filterIdSet.size() + ")");
            }

            return null;
        };
    }
}
