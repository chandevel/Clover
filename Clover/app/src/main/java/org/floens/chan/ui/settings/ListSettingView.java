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
package org.floens.chan.ui.settings;

import android.view.Gravity;
import android.view.View;

import org.floens.chan.R;
import org.floens.chan.core.settings.Setting;
import org.floens.chan.ui.view.FloatingMenu;
import org.floens.chan.ui.view.FloatingMenuItem;

import java.util.ArrayList;
import java.util.List;

import static org.floens.chan.utils.AndroidUtils.dp;

public class ListSettingView extends SettingView implements FloatingMenu.FloatingMenuCallback, View.OnClickListener {
    public final Item[] items;

    private Setting<String> setting;

    private int selected;

    public ListSettingView(SettingsController settingsController, Setting<String> setting, int name, String[] itemNames, String[] keys) {
        this(settingsController, setting, getString(name), itemNames, keys);
    }

    public ListSettingView(SettingsController settingsController, Setting<String> setting, String name, String[] itemNames, String[] keys) {
        super(settingsController, name);

        this.setting = setting;

        items = new Item[itemNames.length];
        for (int i = 0; i < itemNames.length; i++) {
            items[i] = new Item(itemNames[i], keys[i]);
        }

        selectItem();
    }

    public ListSettingView(SettingsController settingsController, Setting<String> setting, int name, Item[] items) {
        this(settingsController, setting, getString(name), items);
    }

    public ListSettingView(SettingsController settingsController, Setting<String> setting, String name, Item[] items) {
        super(settingsController, name);
        this.setting = setting;
        this.items = items;

        selectItem();
    }

    public String getBottomDescription() {
        return items[selected].name;
    }

    public Setting<String> getSetting() {
        return setting;
    }

    @Override
    public void setView(View view) {
        super.setView(view);
        view.setOnClickListener(this);
    }

    @Override
    public void setEnabled(boolean enabled) {
        view.setEnabled(enabled);
        view.findViewById(R.id.top).setEnabled(enabled);
        View bottom = view.findViewById(R.id.bottom);
        if (bottom != null) {
            bottom.setEnabled(enabled);
        }
    }

    @Override
    public void onClick(View v) {
        List<FloatingMenuItem> menuItems = new ArrayList<>(2);

        for (Item item : items) {
            menuItems.add(new FloatingMenuItem(item.key, item.name));
        }

        FloatingMenu menu = new FloatingMenu(v.getContext());
        menu.setAnchor(v, Gravity.LEFT, dp(5), dp(5));
        menu.setPopupWidth(FloatingMenu.POPUP_WIDTH_ANCHOR);
        menu.setCallback(this);
        menu.setItems(menuItems);
        menu.show();
    }

    @Override
    public void onFloatingMenuItemClicked(FloatingMenu menu, FloatingMenuItem item) {
        String selectedKey = (String) item.getId();
        setting.set(selectedKey);
        selectItem();
        settingsController.onPreferenceChange(this);
    }

    @Override
    public void onFloatingMenuDismissed(FloatingMenu menu) {
    }

    private void selectItem() {
        String selectedKey = setting.get();
        for (int i = 0; i < items.length; i++) {
            if (items[i].key.equals(selectedKey)) {
                selected = i;
                break;
            }
        }
    }

    public static class Item {
        public final String name;
        public final String key;

        public Item(String name, String key) {
            this.name = name;
            this.key = key;
        }
    }
}
