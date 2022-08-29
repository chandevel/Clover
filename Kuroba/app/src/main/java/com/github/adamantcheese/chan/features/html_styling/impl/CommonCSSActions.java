package com.github.adamantcheese.chan.features.html_styling.impl;

import static com.github.adamantcheese.chan.features.html_styling.impl.CommonStyleActions.BOLD;
import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.sp;
import static com.github.adamantcheese.chan.utils.StringUtils.span;

import android.graphics.Color;
import android.text.TextUtils;

import androidx.core.graphics.ColorUtils;

import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.features.html_styling.base.ChainStyleAction;
import com.github.adamantcheese.chan.features.html_styling.base.StyleAction;
import com.github.adamantcheese.chan.ui.text.AbsoluteSizeSpanHashed;
import com.github.adamantcheese.chan.ui.text.BackgroundColorSpanHashed;
import com.github.adamantcheese.chan.ui.text.ForegroundColorSpanHashed;
import com.github.adamantcheese.chan.ui.text.RelativeSizeSpanHashed;
import com.github.adamantcheese.chan.utils.Logger;
import com.github.adamantcheese.chan.utils.StringUtils;

import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

/**
 * A bunch of common CSS style actions, taking into account the "style" attribute.
 * Also the "font" tag uses the same styling actions.
 */
public class CommonCSSActions {
    private static final StyleAction CSS_SIZE_ATTR = (element, text) -> {
        String size = element.attr("size");
        boolean relative = StringUtils.containsAny(size, "+", "-");
        int sz = (relative ? 3 : 0) + Integer.parseInt(size);
        return span(text, new RelativeSizeSpanHashed(sz / 3f));
    };

    private static final StyleAction CSS_FONT_SIZE_ATTR = (element, text) -> {
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
                default:
                    //100%
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
            }
            return span(text, new RelativeSizeSpanHashed(scale));
        }
    };

    private static final StyleAction CSS_COLOR_ATTR_FG = (element, text) -> {
        int colorInt = getColorFromAttr(element);
        return span(text, colorInt == 0 ? null : new ForegroundColorSpanHashed(colorInt));
    };

    private static final StyleAction CSS_COLOR_ATTR_BG = (element, text) -> {
        int colorInt = getColorFromAttr(element);
        return span(text, colorInt == 0 ? null : new BackgroundColorSpanHashed(colorInt));
    };

    private static int getColorFromAttr(Node element) {
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
        return colorInt;
    }

    public static final StyleAction FONT = new ChainStyleAction(CSS_SIZE_ATTR).chain(CSS_COLOR_ATTR_FG);

    public static final StyleAction INLINE_CSS = (element, text) -> {
        String style = element.attr("style");
        style = style.replace(" ", "");
        if (TextUtils.isEmpty(style)) return text == null ? "" : text;
        String[] styles = style.split(";");
        Element temp = new Element(element.nodeName());
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
                        text = CSS_COLOR_ATTR_FG.style(temp, text);
                        break;
                    case "font-weight":
                        text = BOLD.style(temp, text);
                        break;
                    case "font-size":
                        text = CSS_FONT_SIZE_ATTR.style(temp, text);
                        break;
                    default:
                        break; // ignore anything else
                }
            } catch (Exception e) {
                Logger.d("INLINE_CSS", "Failed to apply style " + a);
            }
        }
        return text == null ? "" : text;
    };
}
