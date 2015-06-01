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

import android.app.Activity;
import android.app.ActivityManager;
import android.os.Build;

import org.floens.chan.R;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.utils.Logger;

import java.util.ArrayList;
import java.util.List;

public class ThemeHelper {
    private static final String TAG = "ThemeHelper";

    public static ThemeHelper instance = new ThemeHelper();

    public static ThemeHelper getInstance() {
        return instance;
    }

    public static Theme theme() {
        return getInstance().getTheme();
    }

    private List<Theme> themes = new ArrayList<>();

    private Theme theme;

    public ThemeHelper() {
        themes.add(new Theme("Light", "light", R.style.Chan_Theme, PrimaryColor.GREEN));
        themes.add(new DarkTheme("Dark", "dark", R.style.Chan_Theme_Dark, PrimaryColor.DARK));
        themes.add(new DarkTheme("Black", "black", R.style.Chan_Theme_Black, PrimaryColor.BLACK));
        themes.add(new Theme("Yotsuba", "yotsuba", R.style.Chan_Theme_Yotsuba, PrimaryColor.RED));
        themes.add(new Theme("Yotsuba B", "yotsuba_b", R.style.Chan_Theme_YotsubaB, PrimaryColor.RED));
        themes.add(new Theme("Photon", "photon", R.style.Chan_Theme_Photon, PrimaryColor.ORANGE));
        themes.add(new DarkTheme("Tomorrow", "tomorrow", R.style.Chan_Theme_Tomorrow, PrimaryColor.DARK));
        updateCurrentTheme();
    }

    public List<Theme> getThemes() {
        return themes;
    }

    public void updateCurrentTheme() {
        String settingTheme = ChanSettings.theme.get();
        for (Theme theme : themes) {
            if (theme.name.equals(settingTheme)) {
                this.theme = theme;
                return;
            }
        }

        Logger.e(TAG, "No theme found for setting " + settingTheme + ", using the first one");
        theme = themes.get(0);
    }

    public Theme getTheme() {
        return theme;
    }

    public void setupContext(Activity context) {
        updateCurrentTheme();
        context.setTheme(theme.resValue);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            context.getWindow().setStatusBarColor(theme.primaryColor.dark);
            context.getWindow().setNavigationBarColor(0xff000000);
            context.setTaskDescription(new ActivityManager.TaskDescription(null, null, theme.primaryColor.color));
        }
    }

    public enum PrimaryColor {
        RED("red", 0xFFF44336, 0xFFD32F2F),
        PINK("pink", 0xFFE91E63, 0xFFC2185B),
        PURPLE("purple", 0xFF9C27B0, 0xFF7B1FA2),
        DEEP_PURPLE("deep purple", 0xFF673AB7, 0xFF512DA8),
        INDIGO("indigo", 0xFF3F51B5, 0xFF303F9F),
        BLUE("blue", 0xFF2196F3, 0xFF1976D2),
        LIGHT_BLUE("light blue", 0xFF03A9F4, 0xFF0288D1),
        CYAN("cyan", 0xFF00BCD4, 0xFF0097A7),
        TEAL("teal", 0xFF009688, 0xFF00796B),
        GREEN("green", 0xFF4CAF50, 0xFF388E3C),
        LIGHT_GREEN("light green", 0xFF8BC34A, 0xFF689F38),
        LIME("lime", 0xFFCDDC39, 0xFFAFB42B),
        YELLOW("yellow", 0xFFFFEB3B, 0xFFFBC02D),
        AMBER("amber", 0xFFFFC107, 0xFFFFA000),
        ORANGE("orange", 0xFFFF9800, 0xFFF57C00),
        DEEP_ORANGE("deep orange", 0xFFFF5722, 0xFFE64A19),
        BROWN("brown", 0xFF795548, 0xFF5D4037),
        GREY("grey", 0xFF9E9E9E, 0xFF616161),
        BLUE_GREY("blue grey", 0xFF607D8B, 0xFF455A64),

        DARK("dark", 0xff212121, 0xff000000),
        BLACK("black", 0xff000000, 0xff000000);

        public final String name;
        public final int color;
        public final int dark;

        PrimaryColor(String name, int color, int dark) {
            this.name = name;
            this.color = color;
            this.dark = dark;
        }
    }
}
