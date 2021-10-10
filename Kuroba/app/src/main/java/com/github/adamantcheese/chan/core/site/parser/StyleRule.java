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
package com.github.adamantcheese.chan.core.site.parser;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.TextUtils;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.UnderlineSpan;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostLinkable;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.site.parser.PostParser.Callback;
import com.github.adamantcheese.chan.ui.text.AbsoluteSizeSpanHashed;
import com.github.adamantcheese.chan.ui.text.CodeBackgroundSpan;
import com.github.adamantcheese.chan.ui.text.ForegroundColorSpanHashed;
import com.github.adamantcheese.chan.ui.text.RelativeSizeSpanHashed;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.utils.JavaUtils;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.chan.utils.StringUtils;

import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Element;

import java.util.List;

import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.sp;
import static com.github.adamantcheese.chan.utils.StringUtils.span;

/**
 * A class that applies a chain of actions to a given set of text.
 * The order in which you put the actions is the order they will execute.
 */
public class StyleRule {
    public final List<StyleAction> actions = new JavaUtils.NoDeleteArrayList<>();

    public Spanned apply(
            Element element, Spanned text, @NonNull Theme theme, Post.Builder post, Callback callback
    ) {
        Spanned result = text;
        // inline CSS is always applied, but if it is already added then someone specified the order for it
        if (!actions.contains(INLINE_CSS)) actions.add(INLINE_CSS);
        for (StyleAction styleAction : actions) {
            try {
                result = styleAction.style(element, result, theme, post, callback);
            } catch (Exception e) {
                Logger.v(this, "Failed style action", e);
            }
        }
        return result;
    }

    /**
     * Allows the ability to change the text or style a given element.
     */
    public interface StyleAction {
        /**
         * @param element  The element that is currently being worked on.
         * @param text     The text to be modified, with all child nodes processed.
         * @param theme    Current theme for where this will be displayed.
         * @param post     The post that the final text will be in.
         * @param callback For getting info about this post in relation to the thread it is in.
         * @return stylized or replacement text for the text passed in
         */
        @Nullable
        SpannedString style(
                Element element, Spanned text, @NonNull Theme theme, Post.Builder post, Callback callback
        );
    }

    public static final StyleAction NULL = (element, text, theme, post, callback) -> null;
    public static final StyleAction BLOCK_LINE_BREAK =
            (element, text, theme, post, callback) -> new SpannedString(TextUtils.concat(text,
                    element.nextSibling() != null ? "\n" : ""
            ));
    public static final StyleAction NEWLINE = (element, text, theme, post, callback) -> new SpannedString("\n");
    public static final StyleAction SRC =
            (element, text, theme, post, callback) -> new SpannedString(element.attr("src"));
    public static final StyleAction CHOMP =
            (element, text, theme, post, callback) -> new SpannedString(StringUtils.chomp(text));
    public static final StyleAction UNDERLINE =
            (element, text, theme, post, callback) -> span(text, new UnderlineSpan());
    public static final StyleAction ITALICIZE =
            (element, text, theme, post, callback) -> span(text, new StyleSpan(Typeface.ITALIC));
    public static final StyleAction BOLD =
            (element, text, theme, post, callback) -> span(text, new StyleSpan(Typeface.BOLD));
    public static final StyleAction STRIKETHROUGH =
            (element, text, theme, post, callback) -> span(text, new StrikethroughSpan());
    public static final StyleAction MONOSPACE =
            (element, text, theme, post, callback) -> span(text, new TypefaceSpan("monospace"));
    public static final StyleAction CODE =
            (element, text, theme, post, callback) -> span(text, new CodeBackgroundSpan(theme));
    public static final StyleAction SPOILER = (element, text, theme, post, callback) -> span(text,
            new PostLinkable(theme, text, PostLinkable.Type.SPOILER)
    );
    public static final StyleAction INLINE_QUOTE_COLOR = (element, text, theme, post, callback) -> span(text,
            new ForegroundColorSpanHashed(getAttrColor(theme.resValue, R.attr.post_inline_quote_color))
    );
    public static final StyleAction COLOR = (element, text, theme, post, callback) -> {
        String color = element.attr("color");
        int colorInt;
        if (StringUtils.startsWithAny(color, "rgb", "rgba")) {
            color = color.substring(color.indexOf('(') + 1);
            color = color.substring(0, color.length() - 1);
            String[] components = color.split(",");
            colorInt = Color.rgb(Integer.parseInt(components[0]),
                    Integer.parseInt(components[1]),
                    Integer.parseInt(components[2])
            );
        } else if (StringUtils.startsWithAny(color, "hsl", "hsla")) {
            color = color.substring(color.indexOf('(') + 1);
            color = color.substring(0, color.length() - 1);
            String[] components = color.split(",");
            float[] hsl = new float[3];
            hsl[0] = Integer.parseInt(components[0]);
            hsl[1] = Integer.parseInt(components[1].substring(0, components[1].length() - 1));
            hsl[2] = Integer.parseInt(components[2].substring(0, components[2].length() - 1));
            colorInt = ColorUtils.HSLToColor(hsl);
        } else {
            colorInt = Color.parseColor(color);
        }
        return span(text, color.isEmpty() ? null : new ForegroundColorSpanHashed(colorInt));
    };
    public static final StyleAction SIZE = (element, text, theme, post, callback) -> {
        String size = element.attr("size");
        boolean relative = StringUtils.containsAny(size, "+", "-");
        int sz = (relative ? 3 : 0) + Integer.parseInt(size);
        return span(text, new RelativeSizeSpanHashed(sz / 3f));
    };

    private static final StyleAction CSS_FONT_SIZE = (element, text, theme, post, callback) -> {
        // for all rules, cap to range 25% - 175%
        String size = element.attr("font-size");
        if (size.contains("%")) {
            float scale =
                    Math.max(Math.min(Float.parseFloat(size.substring(0, size.indexOf("%"))) / 100f, 1.75f), 0.25f);
            return span(text, new RelativeSizeSpanHashed(scale));
        } else if (size.contains("px")) {
            int sizePx = (int) Math.max(Math.min(dp(Float.parseFloat(size.substring(0, size.indexOf("px")))),
                    sp(ChanSettings.fontSize.get()) * 1.75f
            ), sp(ChanSettings.fontSize.get()) * 0.25f);
            return span(text, new AbsoluteSizeSpanHashed(sizePx));
        } else if (size.contains("pt")) {
            // 1pt = 1.33px
            int sizeDP =
                    (int) Math.max(Math.min(dp((Float.parseFloat(size.substring(0, size.indexOf("pt"))) * 4f) / 3f),
                            sp(ChanSettings.fontSize.get()) * 1.75f
                    ), sp(ChanSettings.fontSize.get()) * 0.25f);
            return span(text, new AbsoluteSizeSpanHashed(sizeDP));
        } else {
            float scale = 1f;
            float scalarUnit = 1f / 4f; // 25% increase in size
            switch (size) {
                case "xx-small":
                    scale -= 3 * scalarUnit; // 25%
                    break;
                case "x-small":
                    scale -= 2 * scalarUnit;
                    break;
                case "small":
                case "smaller":
                    scale -= scalarUnit;
                    break;
                case "medium":
                    scale = 1f; //100%
                    break;
                case "large":
                case "larger":
                    scale += scalarUnit;
                    break;
                case "x-large":
                    scale += 2 * scalarUnit;
                    break;
                case "xx-large":
                    scale += 3 * scalarUnit; // 175%
                    break;
                default:
                    break;
            }
            return span(text, new RelativeSizeSpanHashed(scale));
        }
    };

    public static final StyleAction INLINE_CSS = (element, text, theme, post, callback) -> {
        String style = element.attr("style");
        style = style.replace(" ", "");
        String[] styles = style.split(";");
        Element temp = new Element(element.tagName());
        for (String s : styles) {
            String[] rule = s.split(":");
            if (rule.length != 2) {
                Logger.d("INLINE_CSS", "Failed to parse style " + s);
                continue;
            }
            temp.attr(rule[0], rule[1]);
        }

        for (Attribute a : temp.attributes()) {
            try {
                switch (a.getKey()) {
                    case "color":
                        text = COLOR.style(temp, text, theme, post, callback);
                        break;
                    case "font-weight":
                        text = BOLD.style(temp, text, theme, post, callback);
                        break;
                    case "font-size":
                        text = CSS_FONT_SIZE.style(temp, text, theme, post, callback);
                        break;
                    default:
                        break; // ignore anything else
                }
            } catch (Exception e) {
                Logger.d("INLINE_CSS", "Failed to apply style " + a);
            }
        }
        return new SpannedString(text);
    };
}
