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
package com.github.adamantcheese.chan.ui.settings;

import android.view.Gravity;
import android.view.View;

import com.github.adamantcheese.chan.core.settings.primitives.Setting;
import com.github.adamantcheese.chan.ui.controller.settings.SettingsController;
import com.github.adamantcheese.chan.ui.view.FloatingMenu;
import com.github.adamantcheese.chan.ui.view.FloatingMenuItem;

import java.util.ArrayList;
import java.util.List;

import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;

public class ListSettingView<T>
        extends SettingView
        implements View.OnClickListener {
    public final List<Item<T>> items;
    public Item<T> selected;

    private final Setting<T> setting;

    public ListSettingView(
            SettingsController settingsController, Setting<T> setting, int name, String[] itemNames, T[] keys
    ) {
        super(settingsController, getString(name));

        this.setting = setting;

        items = new ArrayList<>(itemNames.length);
        for (int i = 0; i < itemNames.length; i++) {
            items.add(i, new Item<>(itemNames[i], keys[i]));
        }

        updateSelection();
    }

    public ListSettingView(SettingsController settingsController, Setting<T> setting, int name, List<Item<T>> items) {
        this(settingsController, setting, getString(name), items);
    }

    public ListSettingView(
            SettingsController settingsController, Setting<T> setting, String name, List<Item<T>> items
    ) {
        super(settingsController, name);
        this.setting = setting;
        this.items = items;

        updateSelection();
    }

    public String getBottomDescription() {
        return selected.name;
    }

    public Setting<T> getSetting() {
        return setting;
    }

    @Override
    public void setView(View view) {
        super.setView(view);
        if (view == null) return;
        view.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        List<FloatingMenuItem<T>> menuItems = new ArrayList<>(items.size());
        for (Item<T> item : items) {
            if (item.enabled) {
                menuItems.add(new FloatingMenuItem<>(item.key, item.name));
            }
        }

        FloatingMenu<T> menu = new FloatingMenu<>(v.getContext(), v, menuItems);
        menu.setAnchorGravity(Gravity.LEFT, dp(5), dp(5));
        menu.setCallback(new FloatingMenu.ClickCallback<T>() {
            @Override
            public void onFloatingMenuItemClicked(FloatingMenu<T> menu, FloatingMenuItem<T> item) {
                setting.set(item.getId());
                updateSelection();
                settingsController.onPreferenceChange(ListSettingView.this);
            }
        });
        menu.show();
    }

    public void updateSelection() {
        T selectedKey = setting.get();
        for (Item<T> i : items) {
            if (i.key.equals(selectedKey)) {
                selected = i;
                break;
            }
        }
    }

    public static class Item<T> {
        public final String name;
        public final T key;
        public boolean enabled = true;

        public Item(String name, T key) {
            this.name = name;
            this.key = key;
        }
    }
}
