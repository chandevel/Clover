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

import android.content.Context;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.BuildConfig;
import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.site.parser.PostParser;
import com.github.adamantcheese.chan.utils.AndroidUtils;
import com.github.adamantcheese.chan.utils.StringUtils;

import java.util.regex.Pattern;

/**
 * A Theme object, a wrapper around a Android theme<br>
 * Used for setting the toolbar color, and passed around {@link PostParser} to give spans their correct colors.<br>
 * Technically the parser should not do UI, but it is important that spans do not get created on a UI thread for performance.
 * <br><br>
 * Add additional styleable elements for this theme to the Theme Context styleables region
 * See {@link ThemeHelper#createTheme(Context, Theme)} to add those elements to the theme context used for the entire application.
 * If you do add in elements here, be sure to also modify {@link #reset()} and {@link #toString()}, as these are used for
 * saving the theme to settings and also resetting the theme when a user backs out of the theme setup controller.
 */
public class Theme {
    /**
     * The display name of the theme
     */
    public final String name;
    /**
     * The style resource id associated with the theme
     */
    public final int resValue;

    /**
     * This is the main color for the theme, use primaryColorStyleId from it to retrieve R.attr.colorPrimary
     */
    private MaterialColorStyle primaryColor;
    /**
     * This is the color for any accented items (FABs, etc.); use the actual color from accentColorInt instead
     */
    private MaterialColorStyle accentColor;

    // Defaults for the above colors
    private final MaterialColorStyle defaultPrimary;
    private final MaterialColorStyle defaultAccent;

    public int colorPrimaryColorInt;
    public int colorPrimaryDarkColorInt;
    public int accentColorInt;
    public int subjectColorInt;
    public int nameColorInt;
    public int backColorInt;
    public int textColorInt;
    public int textColorPrimaryColorInt;

    public Theme(String displayName, int resValue, MaterialColorStyle primaryColor, MaterialColorStyle accentColor) {
        this.name = displayName;
        this.resValue = resValue;
        this.primaryColor = primaryColor;
        defaultPrimary = primaryColor;
        this.accentColor = accentColor;
        defaultAccent = accentColor;
        buildAttributes();
    }

    public void reset() {
        primaryColor = defaultPrimary;
        accentColor = defaultAccent;
        buildAttributes();
    }

    public MaterialColorStyle getPrimaryColor() {
        return primaryColor;
    }

    public void setPrimaryColor(MaterialColorStyle primaryColor) {
        this.primaryColor = primaryColor;
        buildAttributes();
    }

    public MaterialColorStyle getAccentColor() {
        return accentColor;
    }

    public void setAccentColor(MaterialColorStyle accentColor) {
        this.accentColor = accentColor;
        buildAttributes();
    }

    public void buildAttributes() {
        colorPrimaryColorInt = AndroidUtils.getAttrColor(primaryColor.primaryColorStyleId, R.attr.colorPrimary);
        colorPrimaryDarkColorInt = AndroidUtils.getAttrColor(primaryColor.primaryColorStyleId, R.attr.colorPrimaryDark);
        accentColorInt = AndroidUtils.getAttrColor(accentColor.accentStyleId, R.attr.colorAccent);
        subjectColorInt = AndroidUtils.getAttrColor(resValue, R.attr.post_subject_color);
        nameColorInt = AndroidUtils.getAttrColor(resValue, R.attr.post_name_color);
        backColorInt = AndroidUtils.getAttrColor(resValue, R.attr.backcolor);
        textColorInt = AndroidUtils.getAttrColor(resValue, android.R.attr.textColor);
        textColorPrimaryColorInt = AndroidUtils.getAttrColor(resValue, android.R.attr.textColorPrimary);
    }

    @NonNull
    @Override
    public String toString() {
        return name + "," + primaryColor.name() + "," + accentColor.name();
    }

    public enum MaterialColorStyle {
        // Use
        //
        // UPPER_CASE_SNAKE_CASE
        //
        // for any new items added to this enum; settings saving and display names depend on it.
        // This is statically checked in debug builds.
        //
        // NAME_HERE(R.style.PRIMARY_COLOR, R.style.ACCENT_COLOR)

        RED(R.style.PrimaryRed, R.style.AccentRed),
        PINK(R.style.PrimaryPink, R.style.AccentPink),
        PURPLE(R.style.PrimaryPurple, R.style.AccentPurple),
        DEEP_PURPLE(R.style.PrimaryDeepPurple, R.style.AccentDeepPurple),
        INDIGO(R.style.PrimaryIndigo, R.style.AccentIndigo),
        BLUE(R.style.PrimaryBlue, R.style.AccentBlue),
        LIGHT_BLUE(R.style.PrimaryLightBlue, R.style.AccentLightBlue),
        CYAN(R.style.PrimaryCyan, R.style.AccentCyan),
        TEAL(R.style.PrimaryTeal, R.style.AccentTeal),
        DARK_TEAL(R.style.PrimaryDarkTeal, R.style.AccentDarkTeal),
        GREEN(R.style.PrimaryGreen, R.style.AccentGreen),
        LIGHT_GREEN(R.style.PrimaryLightGreen, R.style.AccentLightGreen),
        LIME(R.style.PrimaryLime, R.style.AccentLime),
        YELLOW(R.style.PrimaryYellow, R.style.AccentYellow),
        AMBER(R.style.PrimaryAmber, R.style.AccentAmber),
        ORANGE(R.style.PrimaryOrange, R.style.AccentOrange),
        DEEP_ORANGE(R.style.PrimaryDeepOrange, R.style.AccentDeepOrange),
        BROWN(R.style.PrimaryBrown, R.style.AccentBrown),
        GREY(R.style.PrimaryGrey, R.style.AccentGrey),
        BLUE_GREY(R.style.PrimaryBlueGrey, R.style.AccentBlueGrey),
        LIGHT_BLUE_GREY(R.style.PrimaryLightBlueGrey, R.style.AccentLightBlueGrey),

        TAN(R.style.PrimaryTan, R.style.AccentTan),
        DARK(R.style.PrimaryDark, R.style.AccentDark),
        BLACK(R.style.PrimaryBlack, R.style.AccentBlack);

        /**
         * This style contains colorPrimary and colorPrimaryDark
         */
        public final int primaryColorStyleId;
        /**
         * This style contains colorAccent
         */
        public final int accentStyleId;

        MaterialColorStyle(int primaryColorStyleId, int accentStyleId) {
            this.primaryColorStyleId = primaryColorStyleId;
            this.accentStyleId = accentStyleId;
        }

        public String prettyName() {
            return StringUtils.caseAndSpace(name(), "_", false);
        }
    }

    static {
        if (BuildConfig.DEBUG) {
            Pattern colorStyleAssertion = Pattern.compile("[A-Z_]+");
            for (MaterialColorStyle style : MaterialColorStyle.values()) {
                if (!colorStyleAssertion.matcher(style.name()).matches()) {
                    throw new RuntimeException("Style \"" + style.name() + "\" is not in UPPER_CASE_SNAKE_CASE.");
                }
            }
        }
    }
}
