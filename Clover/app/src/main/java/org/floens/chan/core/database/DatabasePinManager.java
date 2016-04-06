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

import org.floens.chan.core.model.Pin;

import java.util.List;
import java.util.concurrent.Callable;

public class DatabasePinManager {
    private static final String TAG = "DatabasePinManager";

    private DatabaseManager databaseManager;
    private DatabaseHelper helper;

    public DatabasePinManager(DatabaseManager databaseManager, DatabaseHelper helper) {
        this.databaseManager = databaseManager;
        this.helper = helper;
    }

    public Callable<Pin> createPin(final Pin pin) {
        return new Callable<Pin>() {
            @Override
            public Pin call() throws Exception {
                helper.loadableDao.create(pin.loadable);
                helper.pinDao.create(pin);
                return pin;
            }
        };
    }

    public Callable<Void> deletePin(final Pin pin) {
        return new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                helper.pinDao.delete(pin);
                helper.loadableDao.delete(pin.loadable);

                return null;
            }
        };
    }

    public Callable<Pin> updatePin(final Pin pin) {
        return new Callable<Pin>() {
            @Override
            public Pin call() throws Exception {
                helper.pinDao.update(pin);
                helper.loadableDao.update(pin.loadable);
                return pin;
            }
        };
    }

    public Callable<List<Pin>> updatePins(final List<Pin> pins) {
        return new Callable<List<Pin>>() {
            @Override
            public List<Pin> call() throws Exception {
                for (int i = 0; i < pins.size(); i++) {
                    Pin pin = pins.get(i);
                    helper.pinDao.update(pin);
                }

                for (int i = 0; i < pins.size(); i++) {
                    Pin pin = pins.get(i);
                    helper.loadableDao.update(pin.loadable);
                }

                return null;
            }
        };
    }

    public Callable<List<Pin>> getPins() {
        return new Callable<List<Pin>>() {
            @Override
            public List<Pin> call() throws Exception {
                List<Pin> list = helper.pinDao.queryForAll();
                for (int i = 0; i < list.size(); i++) {
                    Pin p = list.get(i);
                    helper.loadableDao.refresh(p.loadable);
                }
                return list;
            }
        };
    }
}
