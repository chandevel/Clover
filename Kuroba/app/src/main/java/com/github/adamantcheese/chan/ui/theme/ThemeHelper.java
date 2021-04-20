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

import android.app.ActivityManager;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.github.adamantcheese.chan.BuildConfig;
import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.utils.Logger;

import java.util.ArrayList;
import java.util.List;

import static com.github.adamantcheese.chan.ui.theme.Theme.MaterialColorStyle.BLACK;
import static com.github.adamantcheese.chan.ui.theme.Theme.MaterialColorStyle.BLUE_GREY;
import static com.github.adamantcheese.chan.ui.theme.Theme.MaterialColorStyle.BROWN;
import static com.github.adamantcheese.chan.ui.theme.Theme.MaterialColorStyle.DARK;
import static com.github.adamantcheese.chan.ui.theme.Theme.MaterialColorStyle.DARK_TEAL;
import static com.github.adamantcheese.chan.ui.theme.Theme.MaterialColorStyle.GREEN;
import static com.github.adamantcheese.chan.ui.theme.Theme.MaterialColorStyle.GREY;
import static com.github.adamantcheese.chan.ui.theme.Theme.MaterialColorStyle.INDIGO;
import static com.github.adamantcheese.chan.ui.theme.Theme.MaterialColorStyle.LIGHT_BLUE;
import static com.github.adamantcheese.chan.ui.theme.Theme.MaterialColorStyle.LIGHT_BLUE_GREY;
import static com.github.adamantcheese.chan.ui.theme.Theme.MaterialColorStyle.ORANGE;
import static com.github.adamantcheese.chan.ui.theme.Theme.MaterialColorStyle.RED;
import static com.github.adamantcheese.chan.ui.theme.Theme.MaterialColorStyle.TAN;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;
import static com.github.adamantcheese.chan.utils.AndroidUtils.isAndroid10;

public class ThemeHelper {
    private static boolean initialized = false;
    /*
        Theme guide, continued from styles.xml
        4) Add in a new line
        themes.add(new Theme("NAME", R.style.NAMEHERE, PRIMARY_COLOR, ACCENT_COLOR));
        If you want to change the default fonts for your themes, there's a constructor and example for that.

        5) If you've added a PrimaryColor or AccentColor, you'll need to add them to the MaterialColorStyle enum in Theme.java
           Be sure to follow the instructions there.

        6) Additional optional steps for selection in the theme picker UI; see comments in appropriate files for details:
            - Add in extra resources to Theme.java and styles.xml; modify the constructor and add in defaults for all themes in ThemeHelper
            - Add in the code to setup your new element in createTheme down below
            - Add in code to ThemeSettingsController to control your styleable element (toolbar icon in a theme, etc.)

        7) That's it! Everything else is taken care of for you automatically.
     */
    public static List<Theme> themes = new ArrayList<>();

    public static final Theme defaultDayTheme = new Theme("Yotsuba B", R.style.Chan_Theme_YotsubaB, RED, RED);
    public static final Theme defaultNightTheme = new Theme("Dark", R.style.Chan_Theme_Dark, DARK, DARK_TEAL);

    public static Theme themeDay = defaultDayTheme;
    public static Theme themeNight = defaultNightTheme;
    public static boolean isNightTheme = false;

    private static final Typeface TALLEYRAND =
            Typeface.createFromAsset(getAppContext().getAssets(), "font/Talleyrand.ttf");
    private static final Typeface OPTI_CUBA_LIBRE_TWO =
            Typeface.createFromAsset(getAppContext().getAssets(), "font/OPTICubaLibreTwo.otf");

    public static void init() {
        if (initialized) return;
        initialized = true;
        themes.add(new Theme("Light", R.style.Chan_Theme_Light, GREEN, GREEN));
        themes.add(defaultNightTheme);
        themes.add(new Theme("Black", R.style.Chan_Theme_Black, BLACK, INDIGO));
        themes.add(new Theme("Tomorrow", R.style.Chan_Theme_Tomorrow, DARK, LIGHT_BLUE_GREY));
        themes.add(new Theme("Tomorrow Black", R.style.Chan_Theme_TomorrowBlack, BLACK, LIGHT_BLUE_GREY));
        themes.add(new Theme("Yotsuba", R.style.Chan_Theme_Yotsuba, RED, RED));
        themes.add(defaultDayTheme);
        themes.add(new Theme("Photon", R.style.Chan_Theme_Photon, ORANGE, ORANGE));
        themes.add(new Theme("Insomnia", R.style.Chan_Theme_Insomnia, DARK, BLUE_GREY));
        themes.add(new Theme("Gruvbox Light", R.style.Chan_Theme_GruvboxLight, TAN, TAN));
        themes.add(new Theme("Gruvbox Dark", R.style.Chan_Theme_GruvboxDark, DARK, TAN));
        themes.add(new Theme("Gruvbox Black", R.style.Chan_Theme_GruvboxBlack, BLACK, TAN));
        themes.add(new Theme("Neon", R.style.Chan_Theme_Neon, DARK, LIGHT_BLUE));
        themes.add(new Theme("Solarized Dark", R.style.Chan_Theme_SolarizedDark, ORANGE, ORANGE));
        themes.add(new Theme("Colorblind", R.style.Chan_Theme_Colorblind, DARK, GREY));
        Theme holo = new Theme("Holo", R.style.Chan_Theme_Holo, BROWN, RED, TALLEYRAND, OPTI_CUBA_LIBRE_TWO);
        holo.altFontIsMain = true;
        themes.add(holo);

        String[] split = ChanSettings.themeDay.get().split(",");
        boolean ok = false;
        for (Theme theme : themes) {
            if (theme.name.equals(split[0])) {
                try {
                    themeDay = new Theme(theme.name, theme.resValue, theme.primaryColor, theme.accentColor);
                    themeDay.primaryColor = Theme.MaterialColorStyle.valueOf(split[1]);
                    themeDay.accentColor = Theme.MaterialColorStyle.valueOf(split[2]);
                    ok = true;
                } catch (Exception ignored) {
                    // theme name matches, but something else is wrong with the setting
                }
                break;
            }
        }

        if (!ok) {
            Logger.e("ThemeHelper", "No theme found for setting, using default theme for day");
            ChanSettings.themeDay.set(defaultDayTheme.toString());
        }

        split = ChanSettings.themeNight.get().split(",");
        ok = false;
        for (Theme theme : themes) {
            if (theme.name.equals(split[0])) {
                try {
                    themeNight = new Theme(theme.name, theme.resValue, theme.primaryColor, theme.accentColor);
                    themeNight.primaryColor = Theme.MaterialColorStyle.valueOf(split[1]);
                    themeNight.accentColor = Theme.MaterialColorStyle.valueOf(split[2]);
                    ok = true;
                } catch (Exception ignored) {
                    // theme name matches, but something else is wrong with the setting
                }
                break;
            }
        }

        if (!ok) {
            Logger.e("ThemeHelper", "No theme found for setting, using default theme for day");
            ChanSettings.themeNight.set(defaultNightTheme.toString());
        }
    }

    @NonNull
    public static Theme getTheme() {
        return isNightTheme ? themeNight : themeDay;
    }

    public static void resetThemes() {
        for (Theme theme : themes) {
            theme.reset();
        }
    }

    public static boolean areDayAndNightThemesDifferent() {
        return themeDay != themeNight;
    }

    public static void setupContext(AppCompatActivity context) {
        Configuration currentConfig = context.getResources().getConfiguration();
        int nightModeBits = currentConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        switch (nightModeBits) {
            case Configuration.UI_MODE_NIGHT_YES:
                isNightTheme = true;
                break;
            default:
            case Configuration.UI_MODE_NIGHT_NO:
            case Configuration.UI_MODE_NIGHT_UNDEFINED:
                isNightTheme = false;
                break;
        }
        if (!isAndroid10()) isNightTheme = false;
        //set the theme to the newly made theme and setup some small extras
        context.getTheme().setTo(createTheme(context, getTheme()));
        Bitmap taskDescriptionBitmap = BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher);
        context.setTaskDescription(new ActivityManager.TaskDescription(BuildConfig.APP_LABEL, taskDescriptionBitmap));
    }

    public static Resources.Theme createTheme(Context context, Theme theme) {
        //create the proper Android theme instance from the local theme, and the selected colors
        Resources.Theme userTheme = context.getResources().newTheme();
        userTheme.applyStyle(theme.resValue, true); // main styling theme first
        userTheme.applyStyle(theme.primaryColor.primaryColorStyleId, true); // toolbar color, status bar color
        userTheme.applyStyle(theme.accentColor.accentStyleId, true); // FAB, ui element color
        // add in your custom stuff here, the first argument must be something that resolves with R.style.<element>, with force always to true
        return userTheme;
    }
}
