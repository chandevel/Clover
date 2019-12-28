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
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.site.parser.PostParser;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getRes;

/**
 * A Theme<br>
 * Used for setting the toolbar color, and passed around {@link PostParser} to give the spans the correct color.<br>
 * Technically should the parser not do UI, but it is important that the spans do not get created on an UI thread for performance.
 */
public class Theme {
    public final String displayName;
    public final String name;
    public final int resValue;
    public boolean isLightTheme = true;
    public boolean altFontIsMain = false;
    public ThemeHelper.PrimaryColor primaryColor;
    public ThemeHelper.PrimaryColor accentColor;
    public Typeface mainFont;
    public Typeface altFont;

    public int textPrimary;
    public int textSecondary;
    public int textHint;
    public int quoteColor;
    public int highlightQuoteColor;
    public int linkColor;
    public int spoilerColor;
    public int inlineQuoteColor;
    public int subjectColor;
    public int nameColor;
    public int idBackgroundLight;
    public int idBackgroundDark;
    public int capcodeColor;
    public int detailsColor;
    public int highlightedColor;
    public int savedReplyColor;
    public int selectedColor;
    public int textColorRevealSpoiler;
    public int backColor;
    public int backColorSecondary;

    public ThemeDrawable settingsDrawable = new ThemeDrawable(R.drawable.ic_settings_white_24dp, 0.54f);
    public ThemeDrawable imageDrawable = new ThemeDrawable(R.drawable.ic_image_white_24dp, 0.54f);
    public ThemeDrawable sendDrawable = new ThemeDrawable(R.drawable.ic_send_white_24dp, 0.54f);
    public ThemeDrawable clearDrawable = new ThemeDrawable(R.drawable.ic_clear_white_24dp, 0.54f);
    public ThemeDrawable backDrawable = new ThemeDrawable(R.drawable.ic_arrow_back_white_24dp, 0.54f);
    public ThemeDrawable doneDrawable = new ThemeDrawable(R.drawable.ic_done_white_24dp, 0.54f);
    public ThemeDrawable historyDrawable = new ThemeDrawable(R.drawable.ic_history_white_24dp, 0.54f);
    public ThemeDrawable helpDrawable = new ThemeDrawable(R.drawable.ic_help_outline_white_24dp, 0.54f);
    public ThemeDrawable refreshDrawable = new ThemeDrawable(R.drawable.ic_refresh_white_24dp, 0.54f);

    public Theme(
            String displayName,
            String name,
            int resValue,
            ThemeHelper.PrimaryColor primaryColor,
            Typeface mainFont,
            Typeface altFont
    ) {
        this.displayName = displayName;
        this.name = name;
        this.resValue = resValue;
        this.primaryColor = primaryColor;
        this.mainFont = mainFont;
        this.altFont = altFont;
        accentColor = ThemeHelper.PrimaryColor.TEAL;

        resolveSpanColors();
        resolveDrawables();
    }

    public void resolveDrawables() {
        settingsDrawable.tint = Color.BLACK;
        imageDrawable.tint = Color.BLACK;
        sendDrawable.tint = Color.BLACK;
        clearDrawable.tint = Color.BLACK;
        backDrawable.tint = Color.BLACK;
        doneDrawable.tint = Color.BLACK;
        historyDrawable.tint = Color.BLACK;
        helpDrawable.tint = Color.BLACK;
        refreshDrawable.tint = Color.BLACK;
    }

    public void applyFabColor(FloatingActionButton fab) {
        fab.setBackgroundTintList(ColorStateList.valueOf(accentColor.color));
        fab.getDrawable().setTint(Color.WHITE);
    }

    @SuppressWarnings("ResourceType")
    private void resolveSpanColors() {
        Resources.Theme theme = getRes().newTheme();
        theme.applyStyle(R.style.Chan_Theme, true);
        theme.applyStyle(resValue, true);

        //@formatter:off
        TypedArray ta = theme.obtainStyledAttributes(new int[]{
            R.attr.post_quote_color,
            R.attr.post_highlight_quote_color,
            R.attr.post_link_color,
            R.attr.post_spoiler_color,
            R.attr.post_inline_quote_color,
            R.attr.post_subject_color,
            R.attr.post_name_color,
            R.attr.post_id_background_light,
            R.attr.post_id_background_dark,
            R.attr.post_capcode_color,
            R.attr.post_details_color,
            R.attr.post_highlighted_color,
            R.attr.post_saved_reply_color,
            R.attr.post_selected_color,
            R.attr.text_color_primary,
            R.attr.text_color_secondary,
            R.attr.text_color_hint,
            R.attr.text_color_reveal_spoiler,
            R.attr.backcolor,
            R.attr.backcolor_secondary
        });
        //@formatter:on
        quoteColor = ta.getColor(0, 0);
        highlightQuoteColor = ta.getColor(1, 0);
        linkColor = ta.getColor(2, 0);
        spoilerColor = ta.getColor(3, 0);
        inlineQuoteColor = ta.getColor(4, 0);
        subjectColor = ta.getColor(5, 0);
        nameColor = ta.getColor(6, 0);
        idBackgroundLight = ta.getColor(7, 0);
        idBackgroundDark = ta.getColor(8, 0);
        capcodeColor = ta.getColor(9, 0);
        detailsColor = ta.getColor(10, 0);
        highlightedColor = ta.getColor(11, 0);
        savedReplyColor = ta.getColor(12, 0);
        selectedColor = ta.getColor(13, 0);
        textPrimary = ta.getColor(14, 0);
        textSecondary = ta.getColor(15, 0);
        textHint = ta.getColor(16, 0);
        textColorRevealSpoiler = ta.getColor(17, 0);
        backColor = ta.getColor(18, 0);
        backColorSecondary = ta.getColor(19, 0);

        ta.recycle();
    }

    public static class ThemeDrawable {
        public int drawable;
        public int intAlpha;
        public int tint = -1;

        public ThemeDrawable(int drawable, float alpha) {
            this.drawable = drawable;
            intAlpha = Math.round(alpha * 0xff);
        }

        public void setAlpha(float alpha) {
            intAlpha = Math.round(alpha * 0xff);
        }

        public void apply(ImageView imageView) {
            imageView.setImageResource(drawable);
            // Use the int one!
            imageView.setImageAlpha(intAlpha);
            if (tint != -1) {
                imageView.getDrawable().setTint(tint);
            }
        }

        public Drawable makeDrawable(Context context) {
            Drawable d = context.getDrawable(drawable).mutate();
            d.setAlpha(intAlpha);
            if (tint != -1) {
                d.setTint(tint);
            }
            return d;
        }
    }
}
