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

import com.github.adamantcheese.chan.R;

import java.util.ArrayList;
import java.util.List;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;

public enum FilterType {
    TRIPCODE(0x1),
    NAME(0x2),
    COMMENT(0x4),
    ID(0x8),
    SUBJECT(0x10),
    FILENAME(0x20),
    FLAG_CODE(0x40),
    IMAGE(0x80);

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

    public static String filterTypeName(FilterType type) {
        switch (type) {
            case TRIPCODE:
                return getString(R.string.filter_tripcode);
            case NAME:
                return getString(R.string.filter_name);
            case COMMENT:
                return getString(R.string.filter_comment);
            case ID:
                return getString(R.string.filter_id);
            case SUBJECT:
                return getString(R.string.filter_subject);
            case FILENAME:
                return getString(R.string.filter_filename);
            case FLAG_CODE:
                return getString(R.string.filter_flag_code);
            case IMAGE:
                return getString(R.string.filter_image_hash);
        }
        return null;
    }
}
