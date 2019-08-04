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

import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostLinkable;
import com.github.adamantcheese.chan.ui.text.AbsoluteSizeSpanHashed;
import com.github.adamantcheese.chan.ui.text.BackgroundColorSpanHashed;
import com.github.adamantcheese.chan.ui.text.ForegroundColorSpanHashed;
import com.github.adamantcheese.chan.ui.theme.Theme;

import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StyleRule {
    public enum ForegroundColor {
        INLINE_QUOTE,
        QUOTE
    }

    public enum BackgroundColor {
        CODE
    }

    private final List<String> blockElements = Arrays.asList("p", "div");

    public static StyleRule tagRule(String tag) {
        return new StyleRule().tag(tag);
    }

    private String tag;
    private List<String> classes;

    private List<Action> actions = new ArrayList<>();

    private ForegroundColor foregroundColor = null;
    private BackgroundColor backgroundColor = null;
    private boolean strikeThrough;
    private boolean bold;
    private boolean italic;
    private boolean monospace;
    private int size = 0;

    private PostLinkable.Type link = null;

    private boolean nullify;
    private boolean linkify;

    private String justText = null;

    private boolean blockElement;

    public StyleRule tag(String tag) {
        this.tag = tag;

        if (blockElements.contains(tag)) {
            blockElement = true;
        }

        return this;
    }

    public String tag() {
        return tag;
    }

    public StyleRule cssClass(String cssClass) {
        if (classes == null) {
            classes = new ArrayList<>(4);
        }
        classes.add(cssClass);

        return this;
    }

    public StyleRule action(Action action) {
        actions.add(action);
        return this;
    }

    public StyleRule foregroundColor(ForegroundColor foregroundColor) {
        this.foregroundColor = foregroundColor;
        return this;
    }

    public StyleRule backgroundColor(BackgroundColor backgroundColor) {
        this.backgroundColor = backgroundColor;
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

    public StyleRule size(int size) {
        this.size = size;
        return this;
    }

    public StyleRule nullify() {
        nullify = true;
        return this;
    }

    public StyleRule linkify() {
        linkify = true;
        return this;
    }

    public StyleRule just(String justText) {
        this.justText = justText;
        return this;
    }

    public boolean highPriority() {
        return classes != null && !classes.isEmpty();
    }

    public boolean applies(Element element) {
        if (classes == null || classes.isEmpty()) {
            return true;
        }

        for (String c : classes) {
            if (element.hasClass(c)) {
                return true;
            }
        }

        return false;
    }

    public CharSequence apply(Theme theme,
                              PostParser.Callback callback,
                              Post.Builder post,
                              CharSequence text,
                              Element element) {
        if (nullify) {
            return null;
        }

        if (justText != null) {
            return justText;
        }

        CharSequence result = text;
        for (Action action : actions) {
            result = action.execute(theme, callback, post, text, element);
        }

        List<Object> spansToApply = new ArrayList<>(2);

        if (foregroundColor != null) {
            spansToApply.add(new ForegroundColorSpanHashed(getForegroundColor(theme, foregroundColor)));
        }

        if (backgroundColor != null) {
            spansToApply.add(new BackgroundColorSpanHashed(getBackgroundColor(theme, backgroundColor)));
        }

        if (strikeThrough) {
            spansToApply.add(new StrikethroughSpan());
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

        if (size != 0) {
            spansToApply.add(new AbsoluteSizeSpanHashed(size));
        }

        if (link != null) {
            PostLinkable pl = new PostLinkable(theme, result, result, link);
            post.addLinkable(pl);
            spansToApply.add(pl);
        }

        if (!spansToApply.isEmpty()) {
            result = applySpan(result, spansToApply);
        }

        // Apply break if not the last element.
        if (blockElement && element.nextSibling() != null) {
            result = TextUtils.concat(result, "\n");
        }

        if (linkify) {
            CommentParserHelper.detectLinks(theme, post, result.toString(), new SpannableString(result));
        }

        return result;
    }

    private int getForegroundColor(Theme theme, ForegroundColor foregroundColor) {
        switch (foregroundColor) {
            case INLINE_QUOTE:
                return theme.inlineQuoteColor;
            case QUOTE:
                return theme.quoteColor;
            default:
                return 0;
        }
    }

    private int getBackgroundColor(Theme theme, BackgroundColor backgroundColor) {
        if (backgroundColor == BackgroundColor.CODE) {
            return theme.backColorSecondary;
        }
        return 0;
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
        CharSequence execute(Theme theme,
                             PostParser.Callback callback,
                             Post.Builder post,
                             CharSequence text,
                             Element element);
    }
}
