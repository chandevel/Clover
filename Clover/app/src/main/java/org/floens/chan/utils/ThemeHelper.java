package org.floens.chan.utils;

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;

import org.floens.chan.R;
import org.floens.chan.core.ChanPreferences;

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
    private int quoteColor;
    private int linkColor;
    private int spoilerColor;

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
        String themeName = ChanPreferences.getTheme();

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

    public void reloadPostViewColors(Context context) {
        TypedArray ta = context.obtainStyledAttributes(null, R.styleable.PostView, R.attr.post_style, 0);
        quoteColor = ta.getColor(R.styleable.PostView_quote_color, 0);
        linkColor = ta.getColor(R.styleable.PostView_link_color, 0);
        spoilerColor = ta.getColor(R.styleable.PostView_spoiler_color, 0);
        ta.recycle();
    }

    public int getQuoteColor() {
        return quoteColor;
    }

    public int getLinkColor() {
        return linkColor;
    }

    public int getSpoilerColor() {
        return spoilerColor;
    }
}
