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

import android.text.SpannableString;
import android.text.Spanned;

import androidx.annotation.AnyThread;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostLinkable;
import com.github.adamantcheese.chan.ui.theme.Theme;

import org.nibor.autolink.LinkExtractor;
import org.nibor.autolink.LinkSpan;
import org.nibor.autolink.LinkType;

import java.util.EnumSet;

@AnyThread
public class CommentParserHelper {
    private static final LinkExtractor LINK_EXTRACTOR = LinkExtractor.builder()
            .linkTypes(EnumSet.of(LinkType.URL))
            .build();

    /**
     * Detect links in the given spannable, and create PostLinkables with Type.LINK for the
     * links found onto the spannable.
     * <p>
     * The links are detected with the autolink-java library.
     *
     * @param theme     The theme to style the links with
     * @param post      The post where the linkables get added to.
     * @param text      Text to find links in
     * @param spannable Spannable to set the spans on.
     */
    public static void detectLinks(Theme theme, Post.Builder post, String text, SpannableString spannable) {
        final Iterable<LinkSpan> links = LINK_EXTRACTOR.extractLinks(text);
        for (final LinkSpan link : links) {
            final String linkText = text.substring(link.getBeginIndex(), link.getEndIndex());
            final PostLinkable pl = new PostLinkable(theme, linkText, linkText, PostLinkable.Type.LINK);
            //priority is 0 by default which is maximum above all else; higher priority is like higher layers, i.e. 2 is above 1, 3 is above 2, etc.
            //we use 500 here for to go below post linkables, but above everything else basically
            spannable.setSpan(pl, link.getBeginIndex(), link.getEndIndex(), (500 << Spanned.SPAN_PRIORITY_SHIFT) & Spanned.SPAN_PRIORITY);
            post.addLinkable(pl);
        }
    }
}
