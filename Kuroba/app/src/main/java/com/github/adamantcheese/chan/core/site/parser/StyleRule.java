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

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostLinkable;
import com.github.adamantcheese.chan.ui.text.AbsoluteSizeSpanHashed;
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

public class StyleRule {
    public static StyleRule tagRule(String tag) {
        return new StyleRule().tag(tag);
    }

    private String tag;
    private final List<String> classes = new ArrayList<>();
    private final List<Action> actions = new ArrayList<>();

    private int foregroundColor = 0;
    private boolean foregroundColorRes = false;
    private boolean strikeThrough;
    private boolean underline;
    private boolean bold;
    private boolean italic;
    private boolean monospace;
    private boolean code;
    private boolean trimEndWhitespace;
    private int size = 0;
    private boolean applyFontRules;

    private PostLinkable.Type link = null;

    private boolean nullify;

    private String justText = null;

    private boolean blockElement;

    public StyleRule tag(String tag) {
        this.tag = tag;

        if (StringUtils.startsWithAny(tag, "p", "div")) {
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

    public StyleRule foregroundColor(int foregroundColor, boolean foregroundColorRes) {
        this.foregroundColor = foregroundColor;
        this.foregroundColorRes = foregroundColorRes;
        return this;
    }

    public StyleRule link(PostLinkable.Type link) {
        this.link = link;
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

        List<Object> spansToApply = new ArrayList<>(2);

        if (foregroundColor != 0) {
            int color = foregroundColor;
            if (foregroundColorRes) {
                color = getAttrColor(theme.resValue, foregroundColor);
            }
            spansToApply.add(new ForegroundColorSpanHashed(color));
        }

        if (strikeThrough) {
            spansToApply.add(new StrikethroughSpan());
        }

        if (underline) {
            spansToApply.add(new UnderlineSpan());
        }

        if (bold && italic) {
            spansToApply.add(new StyleSpan(Typeface.BOLD_ITALIC));
        } else if (bold) {
            spansToApply.add(new StyleSpan(Typeface.BOLD));
        } else if (italic) {
            spansToApply.add(new StyleSpan(Typeface.ITALIC));
        }

        if (monospace) {
            spansToApply.add(new TypefaceSpan("monospace"));
        }

        if (code) {
            spansToApply.add(new CodeBackgroundSpan(theme));
        }

        if (size != 0) {
            spansToApply.add(new AbsoluteSizeSpanHashed(size));
        }

        if (link != null) {
            spansToApply.add(new PostLinkable(theme, link.name(), result, link));
        }

        String style = element.attr("style");
        if (!style.isEmpty()) {
            style = style.replace(" ", "");
            String[] styles = style.split(";");
            for (String s : styles) {
                String[] rule = s.split(":");
                if (rule.length != 2) continue;
                switch (rule[0]) {
                    case "color":
                        spansToApply.add(new ForegroundColorSpanHashed(Color.parseColor(rule[1])));
                        break;
                    case "font-weight":
                        spansToApply.add(new StyleSpan(Typeface.BOLD)); // whatever the weight, just make it bold
                        break;
                    case "font-size":
                        String size = rule[1];
                        if (size.contains("%")) {
                            float scale = Float.parseFloat(size.substring(0, size.indexOf("%"))) / 100f;
                            spansToApply.add(new RelativeSizeSpanHashed(scale));
                        } else if (size.contains("px")) {
                            int sizeDP = dp(Float.parseFloat(size.substring(0, size.indexOf("px"))));
                            spansToApply.add(new AbsoluteSizeSpanHashed(sizeDP));
                        } else if (s.contains("pt")) {
                            // 1pt = 1.33px
                            int sizeDP = dp((Float.parseFloat(size.substring(0, size.indexOf("pt"))) * 4f) / 3f);
                            spansToApply.add(new AbsoluteSizeSpanHashed(sizeDP));
                        } else {
                            float scale = 1f;
                            float scalarUnit = 1f / 4f; // 25% increase in size
                            switch (rule[1]) {
                                case "xx-small":
                                    scale -= 3 * scalarUnit;
                                    break;
                                case "x-small":
                                    scale -= 2 * scalarUnit;
                                    break;
                                case "small":
                                case "smaller":
                                    scale -= scalarUnit;
                                    break;
                                case "medium":
                                    scale = 1f;
                                    break;
                                case "large":
                                case "larger":
                                    scale += scalarUnit;
                                    break;
                                case "x-large":
                                    scale += 2 * scalarUnit;
                                    break;
                                case "xx-large":
                                    scale += 3 * scalarUnit;
                                    break;
                                default:
                                    break;
                            }
                            spansToApply.add(new RelativeSizeSpanHashed(scale));
                        }
                        break;
                    default:
                        break; // ignore anything else
                }
            }
        }

        // special builtins for <font>
        if (applyFontRules) {
            String color = element.attr("color");
            if (!color.isEmpty()) {
                spansToApply.add(new ForegroundColorSpanHashed(Color.parseColor(color)));
            }
            String size = element.attr("size");
            if (!size.isEmpty()) {
                boolean relative = StringUtils.containsAny(size, "+", "-");
                int s = (relative ? 3 : 0) + Integer.parseInt(size);
                spansToApply.add(new RelativeSizeSpanHashed(s / 3f));
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

    private SpannableString applySpan(CharSequence text, List<Object> spans) {
        SpannableString result = new SpannableString(text);
        for (Object span : spans) {
            if (span != null) {
                //priority is 0 by default which is maximum above all else; higher priority is like higher layers, i.e. 2 is above 1, 3 is above 2, etc.
                //we use 1000 here for to go above everything else
                result.setSpan(span, 0, result.length(), (1000 << Spanned.SPAN_PRIORITY_SHIFT) & Spanned.SPAN_PRIORITY);
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
