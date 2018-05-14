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
package org.floens.chan.ui.toolbar;

import android.content.Context;
import android.view.View;

import org.floens.chan.ui.view.FloatingMenu;
import org.floens.chan.ui.view.FloatingMenuItem;

import java.util.List;

import static org.floens.chan.utils.AndroidUtils.getString;

public class NavigationItem {
    public String title = "";
    public String subtitle = "";

    public boolean hasBack = true;
    public boolean hasDrawer = false;
    public boolean handlesToolbarInset = false;
    public boolean swipeable = true;

    boolean search = false;
    String searchText;

    public ToolbarMenu menu;
    public ToolbarMiddleMenu middleMenu;
    public View rightView;

    public ToolbarMenuItem createOverflow(
            Context context, ToolbarMenuItem.ToolbarMenuItemCallback callback,
            List<FloatingMenuItem> items) {
        ToolbarMenuItem overflow = menu.createOverflow(callback);
        FloatingMenu overflowMenu = new FloatingMenu(context, overflow.getView(), items);
        overflow.setSubMenu(overflowMenu);
        return overflow;
    }

    public void setTitle(int resId) {
        title = getString(resId);
    }

    public NavigationItem copy() {
        NavigationItem c = new NavigationItem();
        c.title = title;
        c.subtitle = subtitle;

        c.hasBack = hasBack;
        c.hasDrawer = hasDrawer;
        c.handlesToolbarInset = handlesToolbarInset;
        c.swipeable = swipeable;

        c.search = search;
        c.searchText = searchText;

//        c.menu = menu;
        c.middleMenu = middleMenu;
//        c.rightView = rightView;
        return c;
    }
}
