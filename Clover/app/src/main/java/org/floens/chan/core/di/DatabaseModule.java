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

import android.content.Context;

import org.codejargon.feather.Provides;
import org.floens.chan.core.database.DatabaseHelper;
import org.floens.chan.core.database.DatabaseManager;
import org.floens.chan.core.database.LoadableProvider;

import javax.inject.Singleton;

public class DatabaseModule {

    @Provides
    @Singleton
    public DatabaseHelper provideDatabaseHelper(Context applicationContext) {
        return new DatabaseHelper(applicationContext);
    }

    @Provides
    @Singleton
    public DatabaseManager provideDatabaseManager(
            Context applicationContext,
            DatabaseHelper databaseHelper
    ) {
        return new DatabaseManager(applicationContext, databaseHelper);
    }

    @Provides
    @Singleton
    public LoadableProvider provideLoadableProvider(
            DatabaseManager databaseManager
    ) {
        return new LoadableProvider(databaseManager);
    }
}
