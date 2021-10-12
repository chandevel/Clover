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

import android.os.Build;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.SpannedString;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.manager.FilterEngine;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostLinkable;
import com.github.adamantcheese.chan.core.model.PostLinkable.Type;
import com.github.adamantcheese.chan.core.model.orm.Filter;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.settings.PersistableChanState;
import com.github.adamantcheese.chan.core.site.parser.style.HtmlDocumentAction;
import com.github.adamantcheese.chan.core.site.parser.style.HtmlElementAction;
import com.github.adamantcheese.chan.ui.text.AbsoluteSizeSpanHashed;
import com.github.adamantcheese.chan.ui.text.BackgroundColorSpanHashed;
import com.github.adamantcheese.chan.ui.text.ForegroundColorSpanHashed;
import com.github.adamantcheese.chan.ui.text.RoundedBackgroundSpan;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.utils.StringUtils;

import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;

import java.util.List;
import java.util.regex.Pattern;

import javax.inject.Inject;

import static android.text.Spanned.SPAN_INCLUSIVE_EXCLUSIVE;
import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getContrastColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.sp;
import static com.github.adamantcheese.chan.utils.StringUtils.span;

public class PostParser {
    @Inject
    private FilterEngine filterEngine;

    // All of these have one matching group associated with the text they need to work
    // This negative lookbehind and negative lookahead are just so it doesn't match too much stuff, experimentally determined
    // not preceded by /, ", l, &, : (optional space too to avoid CSS with spaces) and not followed by ;
    // otherwise match @num and #num
    private final Pattern extraQuotePattern = Pattern.compile("(?<!(?:: ?))(?<![/\\\"l&])[@#](\\d+)(?!;)");
    private final Pattern extraSpoilerPattern = Pattern.compile("\\[spoiler\\](.*?)\\[/spoiler\\]");
    private final Pattern boldPattern = Pattern.compile("\\*\\*(.+)\\*\\*");
    private final Pattern italicPattern = Pattern.compile("\\*(.+)\\*");
    private final Pattern codePattern = Pattern.compile("`(.+)`");
    private final Pattern strikePattern = Pattern.compile("~~(.+)~~");

    /**
     * Construct a new post parser, with the given action for styling parsed comments.
     *
     */
    public PostParser() {
        inject(this);
    }

    public Post parse(
            Post.Builder builder, @NonNull Theme theme, HtmlElementAction elementAction, List<Filter> filters, Callback callback
    ) {
        if (!TextUtils.isEmpty(builder.name)) {
            builder.name = Parser.unescapeEntities(builder.name, false);
        }

        if (!TextUtils.isEmpty(builder.subject)) {
            builder.subject = Parser.unescapeEntities(builder.subject, false);
        }

        parseInfoSpans(theme, builder);
        builder.comment = new SpannableString(parseComment(theme, builder, callback, elementAction));

        // process any removed posts, and remove any linkables/spans attached
        for (PostLinkable l : builder.getLinkables()) {
            if (l.type == Type.QUOTE) {
                if (callback.isRemoved((int) l.value)) {
                    builder.repliesToNos.remove((int) l.value);
                    builder.comment.setSpan(new PostLinkable(theme, new Object(), Type.OTHER) {
                        @Override
                        public void onClick(@NonNull View widget) {
                            showToast(widget.getContext(), "This post has been removed.");
                        }

                        @Override
                        public void updateDrawState(@NonNull TextPaint textPaint) {
                            super.updateDrawState(textPaint);
                            textPaint.setStrikeThruText(true);
                        }
                    }, builder.comment.getSpanStart(l), builder.comment.getSpanEnd(l), SPAN_INCLUSIVE_EXCLUSIVE);
                    builder.comment.removeSpan(l);
                }
            }
        }

        processPostFilter(filters, builder);

        return builder.build();
    }

    /**
     * Parse the comment, subject, tripcodes, names etc. as spannables.<br>
     * This is done on a background thread for performance, even when it is UI code.<br>
     * The results will be placed on the Post.*Span members.
     *
     * @param theme   Theme to use for parsing
     * @param builder Post builder to get data from
     */
    private void parseInfoSpans(Theme theme, Post.Builder builder) {
        float detailsSizePx = sp(ChanSettings.fontSize.get() - 4);
        SpannableStringBuilder nameTripcodeIdCapcodeSpan = new SpannableStringBuilder();

        final String defaultName = "Anonymous";
        if (ChanSettings.anonymize.get()) {
            builder.name(defaultName);
            builder.tripcode("");
        }

        if (!TextUtils.isEmpty(builder.name) && (!builder.name.equals(defaultName)
                || ChanSettings.showAnonymousName.get())) {
            nameTripcodeIdCapcodeSpan.append(span(builder.name, new ForegroundColorSpanHashed(theme.nameColor)))
                    .append("  ");
        }

        if (!TextUtils.isEmpty(builder.tripcode)) {
            nameTripcodeIdCapcodeSpan.append(span(builder.tripcode,
                    new ForegroundColorSpanHashed(theme.nameColor),
                    new AbsoluteSizeSpanHashed((int) detailsSizePx)
            )).append("  ");
        }

        if (ChanSettings.anonymizeIds.get()) {
            builder.posterId("");
        }

        if (!TextUtils.isEmpty(builder.posterId)) {
            Object idBackgroundSpan;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && PersistableChanState.experimentalRoundedIDSpans.get()) {
                idBackgroundSpan = new RoundedBackgroundSpan(builder.idColor);
            } else {
                idBackgroundSpan = new BackgroundColorSpanHashed(builder.idColor);
            }
            nameTripcodeIdCapcodeSpan.append(span("  " + builder.posterId + "  ",
                    new ForegroundColorSpanHashed(getContrastColor(builder.idColor)),
                    idBackgroundSpan,
                    new AbsoluteSizeSpanHashed((int) detailsSizePx)
            )).append("  ");
        }

        if (!TextUtils.isEmpty(builder.moderatorCapcode)) {
            nameTripcodeIdCapcodeSpan.append(span(StringUtils.caseAndSpace(builder.moderatorCapcode, null),
                    new ForegroundColorSpanHashed(getAttrColor(theme.accentColor.accentStyleId, R.attr.colorAccent)),
                    new AbsoluteSizeSpanHashed((int) detailsSizePx)
            )).append("  ");
        }

        if (!TextUtils.isEmpty(builder.subject)) {
            // Do not set another color when the post is in stub mode, it sets text_color_secondary
            Object foregroundSpan = builder.filterStub ? null : new ForegroundColorSpanHashed(theme.subjectColor);
            builder.spans(span(builder.subject, foregroundSpan), nameTripcodeIdCapcodeSpan);
        } else {
            builder.spans(null, nameTripcodeIdCapcodeSpan);
        }
    }

    private SpannedString parseComment(@NonNull Theme theme, Post.Builder post, Callback callback, HtmlElementAction elementAction) {
        String comment = post.comment.toString().replace("<wbr>", "");
        // modifiers for HTML
        if (ChanSettings.parseExtraQuotes.get()) {
            comment = extraQuotePattern.matcher(comment).replaceAll(createQuoteElementString(post));
        }
        if (ChanSettings.parseExtraSpoilers.get()) {
            comment = extraSpoilerPattern.matcher(comment).replaceAll("<s>$1</s>");
        }
        if (ChanSettings.mildMarkdown.get()) {
            comment = boldPattern.matcher(comment).replaceAll("<b>$1</b>");
            comment = italicPattern.matcher(comment).replaceAll("<i>$1</i>");
            comment = codePattern.matcher(comment).replaceAll("<pre class=\"prettyprint\">$1</pre>");
            comment = strikePattern.matcher(comment).replaceAll("<strike>$1</strike>");
        }

        return new HtmlDocumentAction(elementAction).style(Jsoup.parseBodyFragment(comment),
                null,
                theme,
                post,
                callback
        );
    }

    private void processPostFilter(List<Filter> filters, Post.Builder post) {
        for (Filter f : filters) {
            FilterEngine.FilterAction action = FilterEngine.FilterAction.forId(f.action);
            if (filterEngine.matches(f, post)) {
                switch (action) {
                    case COLOR:
                        post.filter(f.color, false, false, false, f.applyToReplies, f.onlyOnOP, f.applyToSaved);
                        break;
                    case HIDE:
                        post.filter(0, true, false, false, f.applyToReplies, f.onlyOnOP, false);
                        break;
                    case REMOVE:
                        post.filter(0, false, true, false, f.applyToReplies, f.onlyOnOP, false);
                        break;
                    case WATCH:
                        post.filter(0, false, false, true, false, true, false);
                        break;
                }
            }
        }
    }

    public String createQuoteElementString(Post.Builder post) {
        return "<a href=\"/" + post.board.code + "/thread/" + post.opId + "#p$1\">&gt;&gt;$1</a>";
    }

    public interface Callback {
        /**
         * Is the post no something the user has posted, or marked as posted.
         *
         * @param postNo the post no
         * @return {@code true} if referring to a saved post, {@code false} otherwise.
         */
        boolean isSaved(int postNo);

        /**
         * Is the post no from this thread.
         *
         * @param postNo the post no
         * @return {@code true} if referring to a post in the thread, {@code false} otherwise.
         */
        boolean isInternal(int postNo);

        /**
         * Is the post no something the user has removed.
         *
         * @param postNo the post no
         * @return {@code true} if referring to a removed post, {@code false} otherwise.
         */
        boolean isRemoved(int postNo);
    }
}
