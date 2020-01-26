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
package com.github.adamantcheese.chan.core.site;

import com.github.adamantcheese.chan.core.settings.OptionsSetting;
import com.github.adamantcheese.chan.core.settings.Setting;

import java.util.List;

import static com.github.adamantcheese.chan.core.site.SiteSetting.Type.OPTIONS;

/**
 * Hacky stuff to give the site settings a good UI.
 */
public class SiteSetting {
    public enum Type {
        OPTIONS
    }

    public final String name;
    public final Type type;
    public final Setting<?> setting;

    public List<String> optionNames;

    private SiteSetting(String name, Setting<?> setting) {
        this.name = name;
        this.type = OPTIONS;
        this.setting = setting;
    }

    public static SiteSetting forOption(OptionsSetting<?> options, String name, List<String> optionNames) {
        SiteSetting setting = new SiteSetting(name, options);
        setting.optionNames = optionNames;
        return setting;
    }
}
