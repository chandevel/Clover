package com.github.adamantcheese.chan.core.site.parser.style;

import android.graphics.Typeface;
import android.text.SpannedString;
import android.text.TextUtils;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.UnderlineSpan;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.PostLinkable;
import com.github.adamantcheese.chan.ui.text.CodeBackgroundSpan;
import com.github.adamantcheese.chan.ui.text.ForegroundColorSpanHashed;
import com.github.adamantcheese.chan.utils.StringUtils;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.StringUtils.span;

public class CommonStyleActions {
    public static final StyleAction NULLIFY = (element, text, theme, post, callback) -> new SpannedString("");
    public static final StyleAction BLOCK_LINE_BREAK =
            (element, text, theme, post, callback) -> new SpannedString(TextUtils.concat(text,
                    element.nextSibling() != null ? "\n" : ""
            ));
    public static final StyleAction NEWLINE = (element, text, theme, post, callback) -> new SpannedString("\n");
    public static final StyleAction SRC_ATTR =
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
}
