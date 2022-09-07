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
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs;
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs.Filters;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

public class DatabaseFilterManager {
    DatabaseHelper helper;

    public DatabaseFilterManager(DatabaseHelper helper) {
        this.helper = helper;
    }

    public Callable<Filter> createFilter(final Filter filter) {
        return () -> {
            helper.getFilterDao().create(filter);
            return filter;
        };
    }

    public Callable<Filters> updateFilters(final Filters filters) {
        return () -> {
            for (Filter filter : filters) {
                helper.getFilterDao().update(filter);
            }
            return filters;
        };
    }

    public Callable<Void> deleteFilter(final Filter filter) {
        return () -> {
            helper.getFilterDao().delete(filter);
            return null;
        };
    }

    public Callable<Filter> updateFilter(final Filter filter) {
        return () -> {
            helper.getFilterDao().update(filter);
            return filter;
        };
    }

    public Callable<Filters> getFilters() {
        return () -> {
            Filters filters = new Filters(helper.getFilterDao().queryForAll());
            Collections.sort(filters, (lhs, rhs) -> lhs.order - rhs.order);
            updateFilters(filters);
            return filters;
        };
    }

    public Callable<Integer> getCount() {
        return () -> (int) helper.getFilterDao().countOf();
    }

    public Callable<Void> deleteFilters(Filters filtersToDelete) {
        return () -> {
            helper.getFilterDao().delete(filtersToDelete);
            return null;
        };
    }
}
