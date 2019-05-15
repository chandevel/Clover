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
package com.github.adamantcheese.chan.ui.view;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;

public class FloatingMenuItem {
    private Object id;
    private String text;
    private boolean enabled;

    public FloatingMenuItem(Object id, int text) {
        this(id, getString(text));
    }

    public FloatingMenuItem(Object id, int text, boolean enabled) {
        this(id, getString(text), enabled);
    }

    public FloatingMenuItem(Object id, String text) {
        this(id, text, true);
    }

    public FloatingMenuItem(Object id, String text, boolean enabled) {
        this.id = id;
        this.text = text;
        this.enabled = enabled;
    }

    public Object getId() {
        return id;
    }

    public void setId(Object id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
