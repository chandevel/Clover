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

import com.github.adamantcheese.chan.utils.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * This class doesn't use BitSet as it is saved in the database and using ordinals for database saving is a bad idea.
 * If the order of these ever changes, everything will be messed up.
 */
public enum FilterType {
    TRIPCODE(0x1),
    NAME(0x2),
    COMMENT(0x4),
    ID(0x8),
    SUBJECT(0x10),
    FILENAME(0x20),
    FLAG_CODE(0x40),
    IMAGE_HASH(0x80);

    public final int flag;

    FilterType(int flag) {
        this.flag = flag;
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

    @Override
    public String toString() {
        return StringUtils.caseAndSpace(this.name(), "_", true);
    }
}
