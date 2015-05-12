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
package org.floens.chan.utils;

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;

import org.floens.chan.R;
import org.floens.chan.core.settings.ChanSettings;

public class ThemeHelper {
    public enum Theme {
        LIGHT("light", R.style.AppTheme, true),
        DARK("dark", R.style.AppTheme_Dark, false),
        BLACK("black", R.style.AppTheme_Dark_Black, false);

        public String name;
        public int resValue;
        public boolean isLightTheme;

        private Theme(String name, int resValue, boolean isLightTheme) {
            this.name = name;
            this.resValue = resValue;
            this.isLightTheme = isLightTheme;
        }
    }

    private static ThemeHelper instance;
    private Context context;
    private int quoteColor;
    private int highlightQuoteColor;
    private int linkColor;
    private int spoilerColor;
    private int inlineQuoteColor;

    public static ThemeHelper getInstance() {
        if (instance == null) {
            instance = new ThemeHelper();
        }

        return instance;
    }

    public static void setTheme(Activity activity) {
        activity.setTheme(ThemeHelper.getInstance().getTheme().resValue);
    }

    public ThemeHelper() {
    }

    public Theme getTheme() {
        String themeName = ChanSettings.getTheme();

        Theme theme = null;
        switch (themeName) {
            case "light":
                theme = Theme.LIGHT;
                break;
            case "dark":
                theme = Theme.DARK;
                break;
            case "black":
                theme = Theme.BLACK;
                break;
        }

        return theme;
    }

    public Context getThemedContext() {
        return context;
    }

    public void reloadPostViewColors(Context context) {
        this.context = context;

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

        ta.recycle();
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
}
