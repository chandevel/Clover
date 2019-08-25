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

import android.graphics.drawable.Drawable;
import android.view.View;

import com.github.adamantcheese.chan.R;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;

/**
 * The navigation properties for a Controller. Controls common properties that parent controllers
 * need to know, such as the title of the controller.
 * <p>
 * This is also used to set up the toolbar menu, see {@link #buildMenu()}.
 */
public class NavigationItem {
    public String title = "";
    public String subtitle = "";

    public boolean hasBack = true;
    public boolean hasDrawer;
    public boolean handlesToolbarInset;
    public boolean swipeable = true;

    public String searchText;
    public boolean search;

    protected ToolbarMenu menu;
    protected ToolbarMiddleMenu middleMenu;
    protected View rightView;

    public boolean hasArrow() {
        return hasBack || search;
    }

    public void setTitle(int resId) {
        title = getString(resId);
    }

    public MenuBuilder buildMenu() {
        return new MenuBuilder(this);
    }

    public void setMiddleMenu(ToolbarMiddleMenu middleMenu) {
        this.middleMenu = middleMenu;
    }

    public void setRightView(View view) {
        rightView = view;
    }

    public ToolbarMenuItem findItem(int id) {
        return menu == null ? null : menu.findItem(id);
    }

    public ToolbarMenuSubItem findSubItem(int id) {
        return menu == null ? null : menu.findSubItem(id);
    }

    public static class MenuBuilder {
        private final NavigationItem navigationItem;
        private final ToolbarMenu menu;

        public MenuBuilder(NavigationItem navigationItem) {
            this.navigationItem = navigationItem;
            menu = new ToolbarMenu();
        }

        public MenuBuilder withItem(int drawable, ToolbarMenuItem.ClickCallback clicked) {
            return withItem(-1, drawable, clicked);
        }

        public MenuBuilder withItem(int id, int drawable, ToolbarMenuItem.ClickCallback clicked) {
            return withItem(new ToolbarMenuItem(id, drawable, clicked));
        }

        public MenuBuilder withItem(int id, Drawable drawable, ToolbarMenuItem.ClickCallback clicked) {
            return withItem(new ToolbarMenuItem(id, drawable, clicked));
        }

        public MenuBuilder withItem(ToolbarMenuItem menuItem) {
            menu.addItem(menuItem);
            return this;
        }

        public MenuOverflowBuilder withOverflow() {
            return new MenuOverflowBuilder(
                    this,
                    new ToolbarMenuItem(
                            ToolbarMenu.OVERFLOW_ID,
                            R.drawable.ic_more_vert_white_24dp,
                            ToolbarMenuItem::showSubmenu));
        }

        public MenuOverflowBuilder withOverflow(ToolbarMenuItem.ToobarThreedotMenuCallback threedotMenuCallback) {
            return new MenuOverflowBuilder(
                    this,
                    new ToolbarMenuItem(
                            ToolbarMenu.OVERFLOW_ID,
                            R.drawable.ic_more_vert_white_24dp,
                            ToolbarMenuItem::showSubmenu,
                            threedotMenuCallback));
        }

        public ToolbarMenu build() {
            navigationItem.menu = menu;
            return menu;
        }
    }

    public static class MenuOverflowBuilder {
        private final MenuBuilder menuBuilder;
        private final ToolbarMenuItem menuItem;

        public MenuOverflowBuilder(MenuBuilder menuBuilder, ToolbarMenuItem menuItem) {
            this.menuBuilder = menuBuilder;
            this.menuItem = menuItem;
        }

        public MenuOverflowBuilder withSubItem(int text, ToolbarMenuSubItem.ClickCallback clicked) {
            return withSubItem(-1, getString(text), true, clicked);
        }

        public MenuOverflowBuilder withSubItem(String text, ToolbarMenuSubItem.ClickCallback clicked) {
            return withSubItem(-1, text, true, clicked);
        }

        public MenuOverflowBuilder withSubItem(int id, int text, ToolbarMenuSubItem.ClickCallback clicked) {
            return withSubItem(id, getString(text), true, clicked);
        }

        public MenuOverflowBuilder withSubItem(int id, int text, boolean enabled, ToolbarMenuSubItem.ClickCallback clicked) {
            return withSubItem(id, getString(text), enabled, clicked);
        }

        public MenuOverflowBuilder withSubItem(int id, String text, boolean enabled, ToolbarMenuSubItem.ClickCallback clicked) {
            menuItem.addSubItem(new ToolbarMenuSubItem(id, text, enabled, clicked));

            return this;
        }

        public MenuBuilder build() {
            return menuBuilder.withItem(menuItem);
        }
    }
}
