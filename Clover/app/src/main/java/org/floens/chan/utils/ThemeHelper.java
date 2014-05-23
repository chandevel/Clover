package org.floens.chan.utils;

import android.app.Activity;

import org.floens.chan.R;
import org.floens.chan.core.ChanPreferences;

public class ThemeHelper {
    public enum Theme {
        LIGHT("light", R.style.AppTheme),
        DARK("dark", R.style.AppTheme_Dark),
        BLACK("black", R.style.AppTheme_Dark);

        public String name;
        public int resValue;

        private Theme(String name, int resValue) {
            this.name = name;
            this.resValue = resValue;
        }
    }

    public static void setTheme(Activity activity) {
        activity.setTheme(getTheme().resValue);
    }

    public static Theme getTheme() {
        String themeName = ChanPreferences.getTheme();

        Theme theme = null;
        if (themeName.equals("light")) {
            theme = Theme.LIGHT;
        } else if (themeName.equals("dark")) {
            theme = Theme.DARK;
        } else if (themeName.equals("black")) {
            theme = Theme.BLACK;
        }

        return theme;
    }
}
