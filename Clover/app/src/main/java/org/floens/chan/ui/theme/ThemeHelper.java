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
import android.content.Context;
import android.content.res.TypedArray;
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

    private List<Context> contexts = new ArrayList<>();

    private List<Theme> themes = new ArrayList<>();

    private Theme theme;

    private int quoteColor;
    private int highlightQuoteColor;
    private int linkColor;
    private int spoilerColor;
    private int inlineQuoteColor;

    public ThemeHelper() {
        themes.add(new Theme("light", R.style.Chan_Theme, true, PrimaryColor.GREEN));
        themes.add(new Theme("dark", R.style.Chan_Theme_Dark, false, PrimaryColor.DARK));
        themes.add(new Theme("black", R.style.Chan_Theme_Black, false, PrimaryColor.BLACK));
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

    public Context getThemedContext() {
        return contexts.size() > 0 ? contexts.get(contexts.size() - 1) : null;
    }

    public void addContext(Activity context) {
        if (contexts.contains(context)) {
            Logger.e(TAG, "addContext: context already added");
        } else {
            contexts.add(context);
        }

        updateCurrentTheme();
        context.setTheme(theme.resValue);

        TypedArray ta = context.obtainStyledAttributes(new int[]{
                R.attr.post_quote_color,
                R.attr.post_highlight_quote_color,
                R.attr.post_link_color,
                R.attr.post_spoiler_color,
                R.attr.post_inline_quote_color
        });

        quoteColor = ta.getColor(0, 0);
        highlightQuoteColor = ta.getColor(1, 0);
        linkColor = ta.getColor(2, 0);
        spoilerColor = ta.getColor(3, 0);
        inlineQuoteColor = ta.getColor(4, 0);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            context.getWindow().setStatusBarColor(theme.primaryColor.dark);
            context.getWindow().setNavigationBarColor(0xff000000);
            context.setTaskDescription(new ActivityManager.TaskDescription(null, null, theme.primaryColor.color));
        }

        ta.recycle();
    }

    public void removeContext(Activity context) {
        if (!contexts.remove(context)) {
            Logger.e(TAG, "removeContext: context not found");
        }
    }

    public int getQuoteColor() {
        return quoteColor;
    }

    public int getHighlightQuoteColor() {
        return highlightQuoteColor;
    }

    public int getLinkColor() {
        return linkColor;
    }

    public int getSpoilerColor() {
        return spoilerColor;
    }

    public int getInlineQuoteColor() {
        return inlineQuoteColor;
    }

    public enum PrimaryColor {
        RED("red", 0xFF44336, 0xFFD32F2F),
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
            this.dark= dark;
        }
    }
}
