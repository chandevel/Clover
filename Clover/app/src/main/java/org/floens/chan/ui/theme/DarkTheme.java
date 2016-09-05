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
package org.floens.chan.ui.theme;

import org.floens.chan.R;

public class DarkTheme extends Theme {
    public DarkTheme(String displayName, String name, int resValue, ThemeHelper.PrimaryColor primaryColor) {
        super(displayName, name, resValue, primaryColor);
        isLightTheme = false;
    }

    public void resolveDrawables() {
        super.resolveDrawables();
        settingsDrawable = new ThemeDrawable(R.drawable.ic_settings_white_24dp, 1f);
        imageDrawable = new ThemeDrawable(R.drawable.ic_image_white_24dp, 1f);
        sendDrawable = new ThemeDrawable(R.drawable.ic_send_white_24dp, 1f);
        clearDrawable = new ThemeDrawable(R.drawable.ic_clear_white_24dp, 1f);
        backDrawable = new ThemeDrawable(R.drawable.ic_arrow_back_white_24dp, 1f);
        doneDrawable = new ThemeDrawable(R.drawable.ic_clear_white_24dp, 1f);
        historyDrawable = new ThemeDrawable(R.drawable.ic_history_white_24dp, 1f);
        listAddDrawable = new ThemeDrawable(R.drawable.ic_playlist_add_white_24dp, 1f);
        helpDrawable = new ThemeDrawable(R.drawable.ic_help_outline_white_24dp, 1f);
        refreshDrawable = new ThemeDrawable(R.drawable.ic_refresh_white_24dp, 1f);
    }
}
