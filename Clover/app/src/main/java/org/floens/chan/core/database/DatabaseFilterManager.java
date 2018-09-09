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

import org.floens.chan.core.model.orm.Filter;

import java.util.List;
import java.util.concurrent.Callable;

public class DatabaseFilterManager {
    private static final String TAG = "DatabaseFilterManager";

    private DatabaseManager databaseManager;
    private DatabaseHelper helper;

    public DatabaseFilterManager(DatabaseManager databaseManager, DatabaseHelper helper) {
        this.databaseManager = databaseManager;
        this.helper = helper;
    }

    public Callable<Filter> createFilter(final Filter filter) {
		return () -> {
			helper.filterDao.create(filter);
			return filter;
		};
    }

    public Callable<Void> deleteFilter(final Filter filter) {
        return new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                helper.filterDao.delete(filter);
                return null;
            }
        };
    }

    public Callable<Filter> updateFilter(final Filter filter) {
        return new Callable<Filter>() {
            @Override
            public Filter call() throws Exception {
                helper.filterDao.update(filter);
                return filter;
            }
        };
    }

    public Callable<List<Filter>> getFilters() {
        return new Callable<List<Filter>>() {
            @Override
            public List<Filter> call() throws Exception {
                return helper.filterDao.queryForAll();
            }
        };
    }

    public Callable<Long> getCount() {
        return () -> helper.filterDao.countOf();
    }
}
