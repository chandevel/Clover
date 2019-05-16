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

import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;

/**
 * An item for a submenu of a ToolbarMenuItem. Most common as subitem for the overflow button.
 * Add with NavigationItem MenuBuilder.
 */
public class ToolbarMenuSubItem {
    public int id;
    public String text;
    public boolean enabled;
    public ClickCallback clicked;

    public ToolbarMenuSubItem(int id, int text, ClickCallback clicked) {
        this(id, getString(text), clicked);
    }

    public ToolbarMenuSubItem(int id, int text, boolean enabled) {
        this(id, getString(text), enabled, null);
    }

    public ToolbarMenuSubItem(int id, String text, ClickCallback clicked) {
        this(id, text, true, clicked);
    }

    public ToolbarMenuSubItem(String text, ClickCallback clicked) {
        this(-1, text, true, clicked);
    }

    public ToolbarMenuSubItem(int id, String text, boolean enabled, ClickCallback clicked) {
        this.id = id;
        this.text = text;
        this.enabled = enabled;
        this.clicked = clicked;
    }

    public void performClick() {
        if (clicked != null) {
            clicked.clicked(this);
        }
    }

    public interface ClickCallback {
        void clicked(ToolbarMenuSubItem subItem);
    }
}
