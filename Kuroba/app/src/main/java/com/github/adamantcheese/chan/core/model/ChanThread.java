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
import android.text.SpannableString;
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

public class ChanThread {
    private final Loadable loadable;
    // Unmodifiable list of posts. We need it to make this class "thread-safe" (it's actually
    // still not fully thread-safe because Loadable and the Post classes are not thread-safe but
    // there is no easy way to fix them right now) and to avoid copying the whole list of posts
    // every time it is needed somewhere.
    private List<Post> posts;
    private boolean closed = false;
    private boolean archived = false;

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
        return closed;
    }

    public synchronized boolean isArchived() {
        return archived;
    }

    public synchronized void setClosed(boolean closed) {
        this.closed = closed;
    }

    public synchronized void setArchived(boolean archived) {
        this.archived = archived;
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
        return posts.get(0);
    }

    /**
     * For now it is like this because there are a lot of places that will have to be changed to make
     * this safe
     */
    public Loadable getLoadable() {
        return loadable;
    }

    public SpannableStringBuilder summarize(boolean extraStyling) {
        Post op;
        try {
            op = getOp();
        } catch (Exception e) {
            return null;
        }
        SpannableStringBuilder builder = new SpannableStringBuilder();
        boolean hasReplies = op.getReplies() >= 0 || posts.size() - 1 > 0;
        boolean hasImages = op.getImagesCount() >= 0 || getImagesCount() > 0;
        boolean hasUniqueIps = op.getUniqueIps() >= 0;
        String separator = " / ";
        int style = extraStyling ? Typeface.BOLD_ITALIC : Typeface.ITALIC;

        if (hasReplies) {
            boolean hasBumpLimit = loadable.board.bumpLimit > 0;
            SpannableString replies =
                    new SpannableString((op.getReplies() >= 0 ? op.getReplies() : posts.size() - 1) + "R");
            if (hasBumpLimit && op.getReplies() >= loadable.board.bumpLimit) {
                replies.setSpan(new StyleSpan(style), 0, replies.length(), 0);
                if (extraStyling) {
                    replies.setSpan(new ForegroundColorSpanHashed(getAttrColor(ThemeHelper.getTheme().resValue,
                            android.R.attr.textColor
                    )), 0, replies.length(), 0);
                    replies.setSpan(new UnderlineSpan(), 0, replies.length(), 0);
                }
            }
            builder.append(replies);
        }

        if (hasImages) {
            boolean hasImageLimit = loadable.board.imageLimit > 0;
            SpannableString images =
                    new SpannableString((op.getImagesCount() >= 0 ? op.getImagesCount() : getImagesCount()) + "I");
            if (hasImageLimit && op.getImagesCount() >= loadable.board.imageLimit) {
                images.setSpan(new StyleSpan(style), 0, images.length(), 0);
                if (extraStyling) {
                    images.setSpan(new ForegroundColorSpanHashed(getAttrColor(ThemeHelper.getTheme().resValue,
                            android.R.attr.textColor
                    )), 0, images.length(), 0);
                    images.setSpan(new UnderlineSpan(), 0, images.length(), 0);
                }
            }
            builder.append(hasReplies ? separator : "").append(images);
        }

        if (hasUniqueIps) {
            String ips = op.getUniqueIps() + "P";
            builder.append(hasReplies || hasImages ? separator : "").append(ips);
        }

        CommonDataStructs.ChanPage p = PageRepository.getPage(op);
        if (p != null && !(loadable.site instanceof ExternalSiteArchive)) {
            SpannableString page = new SpannableString(String.valueOf(p.page));
            if (p.page >= loadable.board.pages) {
                page.setSpan(new StyleSpan(style), 0, page.length(), 0);
                if (extraStyling) {
                    page.setSpan(new ForegroundColorSpanHashed(getAttrColor(ThemeHelper.getTheme().resValue,
                            android.R.attr.textColor
                    )), 0, page.length(), 0);
                    page.setSpan(new UnderlineSpan(), 0, page.length(), 0);
                }
            }
            builder.append(hasReplies || hasImages || hasUniqueIps ? separator : "")
                    .append(getString(R.string.thread_page_no))
                    .append(' ')
                    .append(page);
        }
        return builder;
    }
}
