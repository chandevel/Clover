package com.github.adamantcheese.chan.features.html_styling.impl;

import static com.github.adamantcheese.chan.utils.StringUtils.RenderOrder.RENDER_ABOVE_ELSE;
import static com.github.adamantcheese.chan.utils.StringUtils.span;
import static com.github.adamantcheese.chan.utils.StringUtils.spanWithPriority;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.manager.ArchivesManager;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.site.archives.ExternalSiteArchive;
import com.github.adamantcheese.chan.core.site.parser.comment_action.linkdata.ThreadLink;
import com.github.adamantcheese.chan.features.html_styling.base.StyleActionTextAdjuster;
import com.github.adamantcheese.chan.features.html_styling.base.ThemedStyleAction;
import com.github.adamantcheese.chan.ui.text.spans.CodeBackgroundSpan;
import com.github.adamantcheese.chan.ui.text.spans.ForegroundColorSpanHashed;
import com.github.adamantcheese.chan.ui.text.spans.post_linkables.*;
import com.github.adamantcheese.chan.features.theme.Theme;
import com.github.adamantcheese.chan.utils.AndroidUtils;
import com.github.adamantcheese.chan.utils.StringUtils;

import org.jsoup.nodes.Node;
import org.nibor.autolink.*;

import java.util.EnumSet;
import java.util.Iterator;

import okhttp3.HttpUrl;

public class CommonThemedStyleActions {
    public static final ThemedStyleAction LINK = new ThemedStyleAction() {
        private final LinkExtractor LINK_EXTRACTOR =
                LinkExtractor.builder().linkTypes(EnumSet.of(LinkType.URL)).build();

        @NonNull
        @Override
        protected CharSequence style(
                @NonNull Node node, @Nullable CharSequence text, @NonNull Theme theme
        ) {
            Iterator<LinkSpan> links = LINK_EXTRACTOR.extractLinks(text).iterator();
            return StringUtils.replaceAll(text, () -> {
                while (links.hasNext()) {
                    LinkSpan link = links.next();
                    String linkText = TextUtils.substring(text, link.getBeginIndex(), link.getEndIndex());
                    String scheme = linkText.substring(0, linkText.indexOf(':'));
                    if ("http".equals(scheme) || "https".equals(scheme)) return linkText;
                }
                return null;
            }, source -> {
                String linkText = (String) source;
                StyleActionTextAdjuster pl = new ParserLinkLinkable(theme, linkText);
                // double check however and set up "archive" links here in place of regular links
                // this allows the person to pick any archive they want, regardless of if it actually is the link in question
                try {
                    String domain = HttpUrl.get(linkText).topPrivateDomain();
                    if (domain == null) throw new IllegalArgumentException("No domain?");
                    ExternalSiteArchive a = ArchivesManager.getInstance().archiveForDomain(domain);
                    if (a != null) {
                        Loadable resolved = a.resolvable().resolveLoadable(a, HttpUrl.get(linkText));
                        if (resolved != null) {
                            Object value = new ThreadLink(resolved.boardCode, resolved.no, resolved.markedNo);
                            pl = new ArchiveLinkable(theme, value);
                        }
                    }
                } catch (Exception ignored) {}

                return span(pl.adjust(linkText), pl);
            }, false);
        }
    };

    public static ThemedStyleAction INLINE_QUOTE_COLOR = new ThemedStyleAction() {
        @NonNull
        @Override
        protected CharSequence style(@NonNull Node node, @Nullable CharSequence text, @NonNull Theme theme) {
            return span(text,
                    new ForegroundColorSpanHashed(AndroidUtils.getThemeAttrColor(theme, R.attr.post_inline_quote_color))
            );
        }
    };

    public static ThemedStyleAction CODE = new ThemedStyleAction() {
        @NonNull
        @Override
        protected CharSequence style(
                @NonNull Node node, @Nullable CharSequence text, @NonNull Theme theme
        ) {
            return span(text,
                    new CodeBackgroundSpan(AndroidUtils.getThemeAttrColor(theme, R.attr.backcolor_secondary))
            );
        }
    };

    public static ThemedStyleAction SPOILER = new ThemedStyleAction() {
        @NonNull
        @Override
        protected CharSequence style(
                @NonNull Node node, @Nullable CharSequence text, @NonNull Theme theme
        ) {
            // we want to render this last, if possible
            return spanWithPriority(text, RENDER_ABOVE_ELSE, new SpoilerLinkable(theme, text));
        }
    };

    public static ThemedStyleAction QUOTE_COLOR = new ThemedStyleAction() {
        @NonNull
        @Override
        protected CharSequence style(
                @NonNull Node node, @Nullable CharSequence text, @NonNull Theme theme
        ) {
            return span(text,
                    new ForegroundColorSpanHashed(AndroidUtils.getThemeAttrColor(theme, R.attr.post_quote_color))
            );
        }
    };
}
