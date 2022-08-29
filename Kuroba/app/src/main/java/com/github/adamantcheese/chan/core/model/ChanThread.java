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
package com.github.adamantcheese.chan.core.model;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.repository.PageRepository;
import com.github.adamantcheese.chan.core.site.archives.ExternalSiteArchive;
import com.github.adamantcheese.chan.core.site.common.CommonDataStructs;
import com.github.adamantcheese.chan.ui.text.ForegroundColorSpanHashed;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.github.adamantcheese.chan.utils.AndroidUtils.getAttrColor;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.StringUtils.span;

public class ChanThread {
    public final Loadable loadable;
    // Unmodifiable list of posts. We need it to make this class "thread-safe" (it's actually
    // still not fully thread-safe because Loadable and the Post classes are not thread-safe but
    // there is no easy way to fix them right now) and to avoid copying the whole list of posts
    // every time it is needed somewhere.
    private List<Post> posts;

    public ChanThread(Loadable loadable, List<Post> posts) {
        this.loadable = loadable;
        this.posts = Collections.unmodifiableList(new ArrayList<>(posts));
    }

    public synchronized int getImagesCount() {
        int total = 0;
        for (Post p : posts) {
            total += p.images.size();
        }
        return total;
    }

    public synchronized boolean isClosed() {
        return !posts.isEmpty() && getOp().closed;
    }

    public synchronized boolean isArchived() {
        return !posts.isEmpty() && getOp().archived;
    }

    public synchronized List<Post> getPosts() {
        return posts;
    }

    public synchronized void setNewPosts(List<Post> newPosts) {
        this.posts = Collections.unmodifiableList(new ArrayList<>(newPosts));
    }

    /**
     * Not safe! Only use for read-only operations!
     */
    public synchronized Post getOp() {
        return posts.isEmpty() ? null : posts.get(0);
    }

    public CharSequence summarize(boolean extraStyling) {
        Post op = getOp();
        SpannableStringBuilder builder = new SpannableStringBuilder();
        boolean hasReplies = op.replies >= 0 || posts.size() - 1 > 0;
        boolean hasImages = op.imagesCount >= 0 || getImagesCount() > 0;
        boolean hasUniqueIps = op.uniqueIps >= 0;
        String separator = " / ";
        Object styleSpan = new StyleSpan(extraStyling ? Typeface.BOLD_ITALIC : Typeface.ITALIC);
        int themeTextColor = getAttrColor(ThemeHelper.getTheme().resValue, android.R.attr.textColor);
        Object[] extraSpans = {new ForegroundColorSpanHashed(themeTextColor), new UnderlineSpan()};

        if (hasReplies) {
            boolean hasBumpLimit = loadable.board.bumpLimit > 0;
            CharSequence replies = (op.replies >= 0 ? op.replies : posts.size() - 1) + "R";
            if (hasBumpLimit && op.replies >= loadable.board.bumpLimit) {
                replies = span(replies, styleSpan, extraStyling ? extraSpans : null);
            }
            builder.append(replies);
        }

        if (hasImages) {
            boolean hasImageLimit = loadable.board.imageLimit > 0;
            CharSequence images = (op.imagesCount >= 0 ? op.imagesCount : getImagesCount()) + "I";
            if (hasImageLimit && op.imagesCount >= loadable.board.imageLimit) {
                images = span(images, styleSpan, extraStyling ? extraSpans : null);
            }
            builder.append(hasReplies ? separator : "").append(images);
        }

        if (hasUniqueIps) {
            String ips = op.uniqueIps + "P";
            builder.append(hasReplies || hasImages ? separator : "").append(ips);
        }

        CommonDataStructs.ChanPage p = PageRepository.getPage(op);
        if (p != null && !(loadable.site instanceof ExternalSiteArchive)) {
            CharSequence page = String.valueOf(p.page);
            if (p.page >= loadable.board.pages) {
                page = span(page, styleSpan, extraStyling ? extraSpans : null);
            }
            builder.append(hasReplies || hasImages || hasUniqueIps ? separator : "")
                    .append(getString(R.string.thread_page_no))
                    .append(' ')
                    .append(page);
        }
        return builder;
    }
}
