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

import org.floens.chan.core.model.orm.Pin;

import java.util.List;
import java.util.concurrent.Callable;

public class DatabasePinManager {
    private static final String TAG = "DatabasePinManager";

    private DatabaseManager databaseManager;
    private DatabaseHelper helper;
    private DatabaseLoadableManager databaseLoadableManager;

    public DatabasePinManager(DatabaseManager databaseManager, DatabaseHelper helper, DatabaseLoadableManager databaseLoadableManager) {
        this.databaseManager = databaseManager;
        this.helper = helper;
        this.databaseLoadableManager = databaseLoadableManager;
    }

    public Callable<Pin> createPin(final Pin pin) {
        if (pin.loadable.id == 0) {
            throw new IllegalArgumentException("Pin loadable is not yet in the db");
        }

		return () -> {
			helper.pinDao.create(pin);
			return pin;
		};
    }

    public Callable<Void> deletePin(final Pin pin) {
		return () -> {
			helper.pinDao.delete(pin);

			return null;
		};
    }

    public Callable<Pin> updatePin(final Pin pin) {
		return () -> {
			helper.pinDao.update(pin);
			return pin;
		};
    }

    public Callable<List<Pin>> updatePins(final List<Pin> pins) {
		return () -> {
			for (int i = 0; i < pins.size(); i++) {
				Pin pin = pins.get(i);
				helper.pinDao.update(pin);
			}

			return null;
		};
    }

    public Callable<List<Pin>> getPins() {
		return () -> {
			List<Pin> list = helper.pinDao.queryForAll();
			for (int i = 0; i < list.size(); i++) {
				Pin p = list.get(i);
				p.loadable = databaseLoadableManager.refreshForeign(p.loadable);
			}
			return list;
		};
    }
}
