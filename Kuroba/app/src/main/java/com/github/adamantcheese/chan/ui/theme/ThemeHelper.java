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

import android.app.Activity;
import android.app.ActivityManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;

import androidx.annotation.AnyThread;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.utils.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.github.adamantcheese.chan.Chan.instance;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAppContext;

public class ThemeHelper {
    private static final String TAG = "ThemeHelper";

    private List<Theme> themes = new ArrayList<>();

    private Theme theme;
    private static final Typeface ROBOTO_MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private static final Typeface ROBOTO_CONDENSED = Typeface.create("sans-serif-condensed", Typeface.NORMAL);
    private static final Typeface TALLEYRAND =
            Typeface.createFromAsset(getAppContext().getAssets(), "font/Talleyrand.ttf");
    private static final Typeface OPTI_CUBA_LIBRE_TWO =
            Typeface.createFromAsset(getAppContext().getAssets(), "font/OPTICubaLibreTwo.otf");

    public ThemeHelper() {
        themes.add(new Theme("Light",
                "light",
                R.style.Chan_Theme,
                PrimaryColor.GREEN,
                ROBOTO_MEDIUM,
                ROBOTO_CONDENSED
        ));
        themes.add(new DarkTheme("Dark",
                "dark",
                R.style.Chan_Theme_Dark,
                PrimaryColor.DARK,
                ROBOTO_MEDIUM,
                ROBOTO_CONDENSED
        ));
        themes.add(new DarkTheme("Black",
                "black",
                R.style.Chan_Theme_Black,
                PrimaryColor.BLACK,
                ROBOTO_MEDIUM,
                ROBOTO_CONDENSED
        ));
        themes.add(new DarkTheme("Tomorrow",
                "tomorrow",
                R.style.Chan_Theme_Tomorrow,
                PrimaryColor.DARK,
                ROBOTO_MEDIUM,
                ROBOTO_CONDENSED
        ));
        themes.add(new DarkTheme("Tomorrow Black",
                "tomorrow_black",
                R.style.Chan_Theme_TomorrowBlack,
                PrimaryColor.BLACK,
                ROBOTO_MEDIUM,
                ROBOTO_CONDENSED
        ));
        themes.add(new Theme("Yotsuba",
                "yotsuba",
                R.style.Chan_Theme_Yotsuba,
                PrimaryColor.RED,
                ROBOTO_MEDIUM,
                ROBOTO_CONDENSED
        ));
        themes.add(new Theme("Yotsuba B",
                "yotsuba_b",
                R.style.Chan_Theme_YotsubaB,
                PrimaryColor.RED,
                ROBOTO_MEDIUM,
                ROBOTO_CONDENSED
        ));
        themes.add(new Theme("Photon",
                "photon",
                R.style.Chan_Theme_Photon,
                PrimaryColor.ORANGE,
                ROBOTO_MEDIUM,
                ROBOTO_CONDENSED
        ));
        themes.add(new DarkTheme("Insomnia",
                "insomnia",
                R.style.Chan_Theme_Insomnia,
                PrimaryColor.DARK,
                ROBOTO_MEDIUM,
                ROBOTO_CONDENSED
        ));
        themes.add(new DarkTheme("Gruvbox",
                "gruvbox",
                R.style.Chan_Theme_Gruvbox,
                PrimaryColor.DARK,
                ROBOTO_MEDIUM,
                ROBOTO_CONDENSED
        ));
        themes.add(new DarkTheme("Neon",
                "neon",
                R.style.Chan_Theme_Neon,
                PrimaryColor.DARK,
                ROBOTO_MEDIUM,
                ROBOTO_CONDENSED
        ));
        themes.add(new DarkTheme("Solarized Dark",
                "solarized_dark",
                R.style.Chan_Theme_SolarizedDark,
                PrimaryColor.ORANGE,
                ROBOTO_MEDIUM,
                ROBOTO_CONDENSED
        ));
        Theme holo = new DarkTheme("Holo",
                "holo",
                R.style.Chan_Theme_Holo,
                PrimaryColor.BROWN,
                TALLEYRAND,
                OPTI_CUBA_LIBRE_TWO
        );
        holo.altFontIsMain = true;
        themes.add(holo);

        ChanSettings.ThemeColor settingTheme = ChanSettings.getThemeAndColor();
        for (Theme theme : themes) {
            if (theme.name.equals(settingTheme.theme)) {
                patchTheme(theme, settingTheme);
                break;
            }
        }

        updateCurrentTheme();
    }

    public void changeTheme(Theme theme, PrimaryColor primaryColor, PrimaryColor accentColor) {
        ChanSettings.ThemeColor setting = new ChanSettings.ThemeColor(theme.name, primaryColor.name, accentColor.name);
        ChanSettings.setThemeAndColor(setting);
        patchTheme(theme, setting);
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

    private void patchTheme(Theme theme, ChanSettings.ThemeColor setting) {
        // Patch the theme primary and accent color when set in the settings
        if (setting.color != null) {
            theme.primaryColor = getColor(setting.color, PrimaryColor.BLACK);
        }
        if (setting.accentColor != null) {
            theme.accentColor = getColor(setting.accentColor, PrimaryColor.TEAL);
        }
    }

    @AnyThread
    public static Theme getTheme() {
        return instance(ThemeHelper.class).theme;
    }

    public List<Theme> getThemes() {
        return themes;
    }

    public void setupContext(Activity context) {
        updateCurrentTheme();
        context.setTheme(theme.resValue);

        context.getWindow().setStatusBarColor(theme.primaryColor.dark);
        context.getWindow().setNavigationBarColor(0xff000000);

        Bitmap taskDescriptionBitmap =
                BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_task_description);
        context.setTaskDescription(new ActivityManager.TaskDescription(null,
                taskDescriptionBitmap,
                theme.primaryColor.color
        ));
    }

    public PrimaryColor getColor(String name, PrimaryColor defaultColor) {
        for (PrimaryColor primaryColor : PrimaryColor.values()) {
            if (primaryColor.name.equals(name)) {
                return primaryColor;
            }
        }

        Logger.e(TAG, "No color found for setting " + name);
        return defaultColor;
    }

    public List<PrimaryColor> getColors() {
        return Arrays.asList(PrimaryColor.values());
    }

    public enum PrimaryColor {
        RED("Red",
                "red",
                0xFFFFEBEE,
                0xFFFFCDD2,
                0xFFEF9A9A,
                0xFFE57373,
                0xFFEF5350,
                0xFFF44336,
                0xFFE53935,
                0xFFD32F2F,
                0xFFD32F2F,
                0xFFB71C1C
        ),
        PINK("Pink",
                "pink",
                0xFFFCE4EC,
                0xFFF8BBD0,
                0xFFF48FB1,
                0xFFF06292,
                0xFFEC407A,
                0xFFE91E63,
                0xFFD81B60,
                0xFFC2185B,
                0xFFC2185B,
                0xFF880E4F
        ),
        PURPLE("Purple",
                "purple",
                0xFFF3E5F5,
                0xFFE1BEE7,
                0xFFCE93D8,
                0xFFBA68C8,
                0xFFAB47BC,
                0xFF9C27B0,
                0xFF8E24AA,
                0xFF7B1FA2,
                0xFF7B1FA2,
                0xFF4A148C
        ),
        DEEP_PURPLE("Deep Purple",
                "deep_purple",
                0xFFEDE7F6,
                0xFFD1C4E9,
                0xFFB39DDB,
                0xFF9575CD,
                0xFF7E57C2,
                0xFF673AB7,
                0xFF5E35B1,
                0xFF512DA8,
                0xFF512DA8,
                0xFF311B92
        ),
        INDIGO("Indigo",
                "indigo",
                0xFFE8EAF6,
                0xFFC5CAE9,
                0xFF9FA8DA,
                0xFF7986CB,
                0xFF5C6BC0,
                0xFF3F51B5,
                0xFF3949AB,
                0xFF303F9F,
                0xFF303F9F,
                0xFF1A237E
        ),
        BLUE("Blue",
                "blue",
                0xFFE3F2FD,
                0xFFBBDEFB,
                0xFF90CAF9,
                0xFF64B5F6,
                0xFF42A5F5,
                0xFF2196F3,
                0xFF1E88E5,
                0xFF1976D2,
                0xFF1976D2,
                0xFF0D47A1
        ),
        LIGHT_BLUE("Light Blue",
                "light_blue",
                0xFFE1F5FE,
                0xFFB3E5FC,
                0xFF81D4FA,
                0xFF4FC3F7,
                0xFF29B6F6,
                0xFF03A9F4,
                0xFF039BE5,
                0xFF0288D1,
                0xFF0288D1,
                0xFF01579B
        ),
        CYAN("Cyan",
                "cyan",
                0xFFE0F7FA,
                0xFFB2EBF2,
                0xFF80DEEA,
                0xFF4DD0E1,
                0xFF26C6DA,
                0xFF00BCD4,
                0xFF00ACC1,
                0xFF0097A7,
                0xFF0097A7,
                0xFF006064
        ),
        TEAL("Teal",
                "teal",
                0xFFE0F2F1,
                0xFFB2DFDB,
                0xFF80CBC4,
                0xFF4DB6AC,
                0xFF26A69A,
                0xFF009688,
                0xFF00897B,
                0xFF00796B,
                0xFF00796B,
                0xFF004D40
        ),
        GREEN("Green",
                "green",
                0xFFE8F5E9,
                0xFFC8E6C9,
                0xFFA5D6A7,
                0xFF81C784,
                0xFF66BB6A,
                0xFF4CAF50,
                0xFF43A047,
                0xFF388E3C,
                0xFF388E3C,
                0xFF1B5E20
        ),
        LIGHT_GREEN("Light Green",
                "light_green",
                0xFFF1F8E9,
                0xFFDCEDC8,
                0xFFC5E1A5,
                0xFFAED581,
                0xFF9CCC65,
                0xFF8BC34A,
                0xFF7CB342,
                0xFF689F38,
                0xFF689F38,
                0xFF33691E
        ),
        LIME("Lime",
                "lime",
                0xFFF9FBE7,
                0xFFF0F4C3,
                0xFFE6EE9C,
                0xFFDCE775,
                0xFFD4E157,
                0xFFCDDC39,
                0xFFC0CA33,
                0xFFAFB42B,
                0xFFAFB42B,
                0xFF827717
        ),
        YELLOW("Yellow",
                "yellow",
                0xFFFFFDE7,
                0xFFFFF9C4,
                0xFFFFF59D,
                0xFFFFF176,
                0xFFFFEE58,
                0xFFFFEB3B,
                0xFFFDD835,
                0xFFFBC02D,
                0xFFFBC02D,
                0xFFF57F17
        ),
        AMBER("Amber",
                "amber",
                0xFFFFF8E1,
                0xFFFFECB3,
                0xFFFFE082,
                0xFFFFD54F,
                0xFFFFCA28,
                0xFFFFC107,
                0xFFFFB300,
                0xFFFFA000,
                0xFFFFA000,
                0xFFFF6F00
        ),
        ORANGE("Orange",
                "orange",
                0xFFFFF3E0,
                0xFFFFE0B2,
                0xFFFFCC80,
                0xFFFFB74D,
                0xFFFFA726,
                0xFFFF9800,
                0xFFFB8C00,
                0xFFF57C00,
                0xFFF57C00,
                0xFFE65100
        ),
        DEEP_ORANGE("Deep Orange",
                "deep_orange",
                0xFFFBE9E7,
                0xFFFFCCBC,
                0xFFFFAB91,
                0xFFFF8A65,
                0xFFFF7043,
                0xFFFF5722,
                0xFFF4511E,
                0xFFE64A19,
                0xFFE64A19,
                0xFFBF360C
        ),
        BROWN("Brown",
                "brown",
                0xFFEFEBE9,
                0xFFD7CCC8,
                0xFFBCAAA4,
                0xFFA1887F,
                0xFF8D6E63,
                0xFF795548,
                0xFF6D4C41,
                0xFF5D4037,
                0xFF5D4037,
                0xFF3E2723
        ),
        GREY("Grey",
                "grey",
                0xFFFAFAFA,
                0xFFF5F5F5,
                0xFFEEEEEE,
                0xFFE0E0E0,
                0xFFBDBDBD,
                0xFF9E9E9E,
                0xFF757575,
                0xFF616161,
                0xFF616161,
                0xFF212121
        ),
        BLUE_GREY("Blue Grey",
                "blue_grey",
                0xFFECEFF1,
                0xFFCFD8DC,
                0xFFB0BEC5,
                0xFF90A4AE,
                0xFF78909C,
                0xFF607D8B,
                0xFF546E7A,
                0xFF455A64,
                0xFF455A64,
                0xFF263238
        ),

        DARK("Dark",
                "dark",
                0xff212121,
                0xff212121,
                0xff212121,
                0xff212121,
                0xff212121,
                0xff212121,
                0xff000000,
                0xff000000,
                0xff000000,
                0xff000000
        ),
        BLACK("Black",
                "black",
                0xff000000,
                0xff000000,
                0xff000000,
                0xff000000,
                0xff000000,
                0xff000000,
                0xff000000,
                0xff000000,
                0xff000000,
                0xff000000
        );

        public final String displayName;
        public final String name;
        public final int color;
        public final int dark;
        public final int color50;
        public final int color100;
        public final int color200;
        public final int color300;
        public final int color400;
        public final int color500;
        public final int color600;
        public final int color700;
        public final int color800;
        public final int color900;

        PrimaryColor(
                String displayName,
                String name,
                int color50,
                int color100,
                int color200,
                int color300,
                int color400,
                int color500,
                int color600,
                int color700,
                int color800,
                int color900
        ) {
            this.displayName = displayName;
            this.name = name;
            this.color = color500;
            this.dark = color700;
            this.color50 = color50;
            this.color100 = color100;
            this.color200 = color200;
            this.color300 = color300;
            this.color400 = color400;
            this.color500 = color500;
            this.color600 = color600;
            this.color700 = color700;
            this.color800 = color800;
            this.color900 = color900;
        }
    }
}
