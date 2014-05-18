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
package org.floens.chan.core.model;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

@DatabaseTable
public class Board {
    public Board() {
    }

    public Board(String key, String value, boolean saved, boolean workSafe) {
        this.key = key;
        this.value = value;
        this.saved = saved;
        this.workSafe = workSafe;
    }

    @DatabaseField(generatedId = true)
    public int id;

    /**
     * Name of the board, e.g. Literature
     */
    @DatabaseField
    public String key;
    /**
     * Name of the url, e.g. lit
     */
    @DatabaseField
    public String value;

    @DatabaseField
    public boolean workSafe = false;

    @DatabaseField
    public boolean saved = false;

    @DatabaseField
    public int order;

    public boolean finish() {
        if (key == null || value == null)
            return false;

        return true;
    }

    @Override
    public String toString() {
        return key;
    }

    public boolean valueEquals(Board other) {
        return value.equals(other.value);
    }
}
