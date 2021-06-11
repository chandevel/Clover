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

import com.github.adamantcheese.chan.utils.StringUtils;

import java.util.ArrayList;
import java.util.List;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;

public class SettingsGroup {
    public final String name;
    public final List<SettingView> settingViews = new ArrayList<>();
    public final List<SettingView> displayList = new ArrayList<>();

    public SettingsGroup(int name) {
        this(getString(name));
    }

    public SettingsGroup(String name) {
        this.name = name;
    }

    public <T extends SettingView> T add(T settingView) {
        settingViews.add(settingView);
        return settingView;
    }

    public void filter(String text) {
        displayList.clear();
        for (SettingView settingView : settingViews) {
            if (StringUtils.containsIgnoreCase(settingView.getTopDescription(), text) || StringUtils.containsIgnoreCase(
                    settingView.getBottomDescription(),
                    text
            )) {
                displayList.add(settingView);
            }
        }
    }
}
