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

import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.model.orm.Pin;
import com.j256.ormlite.stmt.DeleteBuilder;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.inject;

public class DatabasePinManager {
    private static final String TAG = "DatabasePinManager";

    @Inject
    DatabaseHelper helper;
    private DatabaseLoadableManager databaseLoadableManager;

    public DatabasePinManager(DatabaseLoadableManager databaseLoadableManager) {
        inject(this);
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

    public Callable<Void> deletePins(final List<Pin> pins) {
        return () -> {
            for (Pin pin : pins) {
                helper.pinDao.delete(pin);
            }

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
            for (Pin pin : pins) {
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

    public Callable<Void> deletePinsFromLoadables(List<Loadable> siteLoadables) {
        return () -> {
            Set<Integer> loadableIdSet = new HashSet<>();

            for (Loadable loadable : siteLoadables) {
                loadableIdSet.add(loadable.id);
            }

            DeleteBuilder<Pin, Integer> builder = helper.pinDao.deleteBuilder();
            builder.where().in("loadable_id", loadableIdSet);
            builder.delete();

            return null;
        };
    }

    public Callable<Pin> getPinByLoadableId(int loadableId) {
        return () -> helper.pinDao.queryBuilder().where().eq("loadable_id", loadableId).queryForFirst();
    }
}
