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
package com.github.adamantcheese.chan.ui.theme;

import android.graphics.Typeface;

public class DarkTheme extends Theme {
    public DarkTheme(String displayName, String name, int resValue, ThemeHelper.PrimaryColor primaryColor, Typeface mainFont, Typeface altFont) {
        super(displayName, name, resValue, primaryColor, mainFont, altFont);
        isLightTheme = false;
    }

    public void resolveDrawables() {
        settingsDrawable.setAlpha(1f);
        imageDrawable.setAlpha(1f);
        sendDrawable.setAlpha(1f);
        clearDrawable.setAlpha(1f);
        backDrawable.setAlpha(1f);
        doneDrawable.setAlpha(1f);
        historyDrawable.setAlpha(1f);
        helpDrawable.setAlpha(1f);
        refreshDrawable.setAlpha(1f);
    }
}
