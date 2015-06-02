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
import java.util.Arrays;
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

        ChanSettings.ThemeColor settingTheme = ChanSettings.getThemeAndColor();
        if (settingTheme.color != null) {
            for (Theme theme : themes) {
                if (theme.name.equals(settingTheme.theme)) {
                    theme.primaryColor = getColor(settingTheme.color);
                    break;
                }
            }
        }

        updateCurrentTheme();
    }

    public void changeTheme(Theme theme, PrimaryColor primaryColor) {
        ChanSettings.setThemeAndColor(new ChanSettings.ThemeColor(theme.name, primaryColor.name));
        theme.primaryColor = primaryColor;
        updateCurrentTheme();
    }

    public void updateCurrentTheme() {
        ChanSettings.ThemeColor settingTheme = ChanSettings.getThemeAndColor();
        for (Theme theme : themes) {
            if (theme.name.equals(settingTheme.theme)) {
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

    public List<Theme> getThemes() {
        return themes;
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

    public PrimaryColor getColor(String name) {
        for (PrimaryColor primaryColor : PrimaryColor.values()) {
            if (primaryColor.name.equals(name)) {
                return primaryColor;
            }
        }

        Logger.e(TAG, "No color found for setting " + name + ", using a default color");
        return PrimaryColor.BLACK;
    }

    public List<PrimaryColor> getColors() {
        return Arrays.asList(PrimaryColor.values());
    }

    public enum PrimaryColor {
        RED("Red", "red", 0xFFF44336, 0xFFD32F2F),
        PINK("Pink", "pink", 0xFFE91E63, 0xFFC2185B),
        PURPLE("Purple", "purple", 0xFF9C27B0, 0xFF7B1FA2),
        DEEP_PURPLE("Deep purple", "deep_purple", 0xFF673AB7, 0xFF512DA8),
        INDIGO("Indigo", "indigo", 0xFF3F51B5, 0xFF303F9F),
        BLUE("Blue", "blue", 0xFF2196F3, 0xFF1976D2),
        LIGHT_BLUE("Light blue", "light_blue", 0xFF03A9F4, 0xFF0288D1),
        CYAN("Cyan", "cyan", 0xFF00BCD4, 0xFF0097A7),
        TEAL("Teal", "teal", 0xFF009688, 0xFF00796B),
        GREEN("Green", "green", 0xFF4CAF50, 0xFF388E3C),
        LIGHT_GREEN("Light green", "light_green", 0xFF8BC34A, 0xFF689F38),
        LIME("Lime", "lime", 0xFFCDDC39, 0xFFAFB42B),
        YELLOW("Yellow", "yellow", 0xFFFFEB3B, 0xFFFBC02D),
        AMBER("Amber", "amber", 0xFFFFC107, 0xFFFFA000),
        ORANGE("Orange", "orange", 0xFFFF9800, 0xFFF57C00),
        DEEP_ORANGE("Deep orange", "deep_orange", 0xFFFF5722, 0xFFE64A19),
        BROWN("Brown", "brown", 0xFF795548, 0xFF5D4037),
        GREY("Grey", "grey", 0xFF9E9E9E, 0xFF616161),
        BLUE_GREY("Blue grey", "blue_grey", 0xFF607D8B, 0xFF455A64),

        DARK("Dark", "dark", 0xff212121, 0xff000000),
        BLACK("Black", "black", 0xff000000, 0xff000000);

        public final String displayName;
        public final String name;
        public final int color;
        public final int dark;

        PrimaryColor(String displayName, String name, int color, int dark) {
            this.displayName = displayName;
            this.name = name;
            this.color = color;
            this.dark = dark;
        }
    }
}
