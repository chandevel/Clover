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
package com.github.adamantcheese.chan.core.manager;

import java.util.ArrayList;
import java.util.List;

public enum FilterType {
    TRIPCODE(0x1, false),
    NAME(0x2, false),
    COMMENT(0x4, true),
    ID(0x8, false),
    SUBJECT(0x10, true),
    FILENAME(0x20, true);

    public final int flag;
    public final boolean isRegex;

    FilterType(int flag, boolean isRegex) {
        this.flag = flag;
        this.isRegex = isRegex;
    }

    public static List<FilterType> forFlags(int flag) {
        List<FilterType> enabledTypes = new ArrayList<>();
        for (FilterType filterType : values()) {
            if ((filterType.flag & flag) != 0) {
                enabledTypes.add(filterType);
            }
        }
        return enabledTypes;
    }
}
