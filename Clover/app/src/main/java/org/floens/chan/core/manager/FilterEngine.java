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
package org.floens.chan.core.manager;

import org.floens.chan.core.model.Filter;

import java.util.ArrayList;
import java.util.List;

public class FilterEngine {
    public enum FilterType {
        TRIPCODE(0),
        NAME(1),
        COMMENT(2),
        ID(3),
        SUBJECT(4),
        FILENAME(5);

        public final int id;

        FilterType(int id) {
            this.id = id;
        }

        public static FilterType forId(int id) {
            for (FilterType type : values()) {
                if (type.id == id) {
                    return type;
                }
            }
            return null;
        }
    }

    private static final FilterEngine instance = new FilterEngine();

    public static FilterEngine getInstance() {
        return instance;
    }

    private List<Filter> filters = new ArrayList<>();

    public FilterEngine() {

    }

    public void add(Filter filter) {
    }
}
