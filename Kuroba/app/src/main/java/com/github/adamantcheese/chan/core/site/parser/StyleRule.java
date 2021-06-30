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
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.UnderlineSpan;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostLinkable;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.ui.text.AbsoluteSizeSpanHashed;
import com.github.adamantcheese.chan.ui.text.BackgroundColorSpanHashed;
import com.github.adamantcheese.chan.ui.text.CodeBackgroundSpan;
import com.github.adamantcheese.chan.ui.text.ForegroundColorSpanHashed;
import com.github.adamantcheese.chan.ui.text.RelativeSizeSpanHashed;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.utils.StringUtils;

import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;

import static com.github.adamantcheese.chan.utils.AndroidUtils.dp;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.sp;

public class StyleRule {
    public static StyleRule tagRule(String tag) {
        return new StyleRule().tag(tag);
    }

    private String tag;
    private final List<String> classes = new ArrayList<>();
    private final List<Action> actions = new ArrayList<>();

    private int backgroundColor = 0;
    private boolean backgroundColorRes = false;
    private int foregroundColor = 0;
    private boolean foregroundColorRes = false;
    private boolean cssStyleInFront = false;
    private boolean strikeThrough;
    private boolean underline;
    private boolean bold;
    private boolean italic;
    private boolean monospace;
    private boolean code;
    private boolean trimEndWhitespace;
    private int size = 0;
    private boolean applyFontRules;

    private boolean spoiler = false;

    private boolean nullify;

    private String justText = null;

    private boolean blockElement;

    public StyleRule tag(String tag) {
        this.tag = tag;

        if (StringUtils.isAnyIgnoreCase(tag, "p", "div")) {
            blockElement = true;
        }

        return this;
    }

    public String tag() {
        return tag;
    }

    public StyleRule cssClass(String cssClass) {
        classes.add(cssClass);
        return this;
    }

    public StyleRule action(Action action) {
        actions.add(action);
        return this;
    }

    public StyleRule backgroundColor(int backgroundColor, boolean backgroundColorRes) {
        this.backgroundColor = backgroundColor;
        this.backgroundColorRes = backgroundColorRes;
        return this;
    }

    public StyleRule foregroundColor(int foregroundColor, boolean foregroundColorRes) {
        this.foregroundColor = foregroundColor;
        this.foregroundColorRes = foregroundColorRes;
        return this;
    }

    public StyleRule cssStyleInFront() {
        this.cssStyleInFront = true;
        return this;
    }

    public StyleRule spoiler() {
        this.spoiler = true;
        return this;
    }

    public StyleRule strikeThrough() {
        strikeThrough = true;
        return this;
    }

    public StyleRule underline() {
        this.underline = true;
        return this;
    }

    public StyleRule bold() {
        bold = true;
        return this;
    }

    public StyleRule italic() {
        italic = true;
        return this;
    }

    public StyleRule monospace() {
        monospace = true;
        return this;
    }

    public StyleRule code() {
        code = true;
        return this;
    }

    public StyleRule trimEndWhitespace() {
        trimEndWhitespace = true;
        return this;
    }

    public StyleRule size(int size) {
        this.size = size;
        return this;
    }

    public StyleRule applyFontRules() {
        this.applyFontRules = true;
        return this;
    }

    public StyleRule nullify() {
        nullify = true;
        return this;
    }

    public StyleRule just(String justText) {
        this.justText = justText;
        return this;
    }

    public boolean highPriority() {
        return !classes.isEmpty();
    }

    public boolean applies(Element element) {
        if (classes.isEmpty()) {
            return true;
        }

        for (String c : classes) {
            if (element.hasClass(c)) {
                return true;
            }
        }

        return false;
    }

    public CharSequence apply(
            @NonNull Theme theme, PostParser.Callback callback, Post.Builder post, CharSequence text, Element element
    ) {
        if (nullify) {
            return null;
        }

        if (justText != null) {
            return justText;
        }

        CharSequence result = text;
        for (Action action : actions) {
            result = action.execute(theme, callback, post, result, element);
        }

        int DEFAULT_RENDER_WEIGHT = 1000;
        List<Pair<Object, Integer>> spansToApply = new ArrayList<>(2);

        if (backgroundColor != 0) {
            int color = backgroundColor;
            if (backgroundColorRes) {
                color = getAttrColor(theme.resValue, backgroundColor);
            }
            spansToApply.add(new Pair<>(new BackgroundColorSpanHashed(color), DEFAULT_RENDER_WEIGHT));
        }

        if (foregroundColor != 0) {
            int color = foregroundColor;
            if (foregroundColorRes) {
                color = getAttrColor(theme.resValue, foregroundColor);
            }
            spansToApply.add(new Pair<>(new ForegroundColorSpanHashed(color), DEFAULT_RENDER_WEIGHT));
        }

        if (strikeThrough) {
            spansToApply.add(new Pair<>(new StrikethroughSpan(), DEFAULT_RENDER_WEIGHT));
        }

        if (underline) {
            spansToApply.add(new Pair<>(new UnderlineSpan(), DEFAULT_RENDER_WEIGHT));
        }

        if (bold && italic) {
            spansToApply.add(new Pair<>(new StyleSpan(Typeface.BOLD_ITALIC), DEFAULT_RENDER_WEIGHT));
        } else if (bold) {
            spansToApply.add(new Pair<>(new StyleSpan(Typeface.BOLD), DEFAULT_RENDER_WEIGHT));
        } else if (italic) {
            spansToApply.add(new Pair<>(new StyleSpan(Typeface.ITALIC), DEFAULT_RENDER_WEIGHT));
        }

        if (monospace) {
            spansToApply.add(new Pair<>(new TypefaceSpan("monospace"), DEFAULT_RENDER_WEIGHT));
        }

        if (code) {
            spansToApply.add(new Pair<>(new CodeBackgroundSpan(theme), DEFAULT_RENDER_WEIGHT));
        }

        if (size != 0) {
            spansToApply.add(new Pair<>(new AbsoluteSizeSpanHashed(size), DEFAULT_RENDER_WEIGHT));
        }

        if (spoiler) {
            spansToApply.add(new Pair<>(new PostLinkable(theme, result, result, PostLinkable.Type.SPOILER), DEFAULT_RENDER_WEIGHT));
        }

        String style = element.attr("style");
        if (!style.isEmpty()) {
            try {
                style = style.replace(" ", "");
                applyCssStyles(style, spansToApply);
            } catch (Exception ignored) {
                // for any style-related error, just ignore applying styling
            }
        }

        // special builtins for <font>
        if (applyFontRules) {
            String color = element.attr("color");
            if (!color.isEmpty()) {
                spansToApply.add(new Pair<>(new ForegroundColorSpanHashed(Color.parseColor(color)), DEFAULT_RENDER_WEIGHT));
            }
            String size = element.attr("size");
            if (!size.isEmpty()) {
                boolean relative = StringUtils.containsAny(size, "+", "-");
                int s = (relative ? 3 : 0) + Integer.parseInt(size);
                spansToApply.add(new Pair<>(new RelativeSizeSpanHashed(s / 3f), DEFAULT_RENDER_WEIGHT));
            }
        }

        if (!spansToApply.isEmpty()) {
            result = applySpan(result, spansToApply);
        }

        // Apply break if not the last element.
        if (blockElement && element.nextSibling() != null) {
            result = TextUtils.concat(result, "\n");
        }

        if (trimEndWhitespace) {
            result = StringUtils.chomp(new SpannableStringBuilder(result));
        }

        return result;
    }

    private void applyCssStyles(String cssString, List<Pair<Object, Integer>> spansToApply) {
        int RENDER_WEIGHT = cssStyleInFront ? 600 : 1000;
        String[] styles = cssString.split(";");
        for (String s : styles) {
            String[] rule = s.split(":");
            if (rule.length != 2) continue;
            switch (rule[0]) {
                case "color":
                    spansToApply.add(new Pair<>(new ForegroundColorSpanHashed(Color.parseColor(rule[1])), RENDER_WEIGHT));
                    break;
                case "font-weight":
                    spansToApply.add(new Pair<>(new StyleSpan(Typeface.BOLD), RENDER_WEIGHT)); // whatever the weight, just make it bold
                    break;
                case "font-size":
                    // for all rules, cap to range 25% - 175%
                    String size = rule[1];
                    if (size.contains("%")) {
                        float scale = Math.max(
                                Math.min(Float.parseFloat(size.substring(0, size.indexOf("%"))) / 100f, 1.75f),
                                0.25f
                        );
                        spansToApply.add(new Pair<>(new RelativeSizeSpanHashed(scale), RENDER_WEIGHT));
                    } else if (size.contains("px")) {
                        int sizePx = (int) Math.max(Math.min(dp(Float.parseFloat(s.substring(0, size.indexOf("px")))),
                                sp(ChanSettings.fontSize.get()) * 1.75f
                        ), sp(ChanSettings.fontSize.get()) * 0.25f);
                        spansToApply.add(new Pair<>(new AbsoluteSizeSpanHashed(sizePx), RENDER_WEIGHT));
                    } else if (s.contains("pt")) {
                        // 1pt = 1.33px
                        int sizeDP = (int) Math.max(Math.min(dp(
                                (Float.parseFloat(size.substring(0, size.indexOf("pt"))) * 4f) / 3f),
                                sp(ChanSettings.fontSize.get()) * 1.75f
                        ), sp(ChanSettings.fontSize.get()) * 0.25f);
                        spansToApply.add(new Pair<>(new AbsoluteSizeSpanHashed(sizeDP), RENDER_WEIGHT));
                    } else {
                        float scale = 1f;
                        float scalarUnit = 1f / 4f; // 25% increase in size
                        switch (rule[1]) {
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
                        spansToApply.add(new Pair<>(new RelativeSizeSpanHashed(scale), RENDER_WEIGHT));
                    }
                    break;
                default:
                    break; // ignore anything else
            }
        }
    }

    private SpannableString applySpan(CharSequence text, List<Pair<Object, Integer>> spans) {
        SpannableString result = new SpannableString(text);
        for (Pair<Object, Integer> span : spans) {
            if (span != null) {
                //priority is 0 by default which is maximum above all else; higher priority is like higher layers, i.e. 2 is above 1, 3 is above 2, etc.
                //we use 1000 here for to go above everything else
                result.setSpan(span.first, 0, result.length(), (span.second << Spanned.SPAN_PRIORITY_SHIFT) & Spanned.SPAN_PRIORITY);
            }
        }
        return result;
    }

    public interface Action {
        CharSequence execute(
                @NonNull Theme theme,
                PostParser.Callback callback,
                Post.Builder post,
                CharSequence text,
                Element element
        );
    }
}
