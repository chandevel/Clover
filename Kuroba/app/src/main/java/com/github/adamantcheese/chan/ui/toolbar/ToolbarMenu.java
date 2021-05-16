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
package com.github.adamantcheese.chan.ui.toolbar;

import java.util.ArrayList;
import java.util.List;

public class ToolbarMenu {
    public static final int OVERFLOW_ID = Integer.MAX_VALUE;

    public final List<ToolbarMenuItem> items = new ArrayList<>();

    public void addItem(ToolbarMenuItem item) {
        items.add(item);
    }

    public ToolbarMenuItem findItem(int id) {
        for (ToolbarMenuItem item : items) {
            if (item.id.equals(id)) {
                return item;
            }
        }
        return null;
    }

    public ToolbarMenuItem findOverflow() {
        return findItem(OVERFLOW_ID);
    }

    public ToolbarMenuSubItem findSubItem(int id) {
        ToolbarMenuItem overflow = findOverflow();
        if (overflow != null) {
            for (ToolbarMenuSubItem subItem : overflow.subItems) {
                if (subItem.id == id) {
                    return subItem;
                }
            }
        }

        return null;
    }
}
