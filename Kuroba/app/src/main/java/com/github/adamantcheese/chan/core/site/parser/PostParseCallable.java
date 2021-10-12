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

import androidx.annotation.NonNull;

import com.github.adamantcheese.chan.core.database.DatabaseSavedReplyManager;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.orm.Filter;
import com.github.adamantcheese.chan.core.model.orm.PostHide;
import com.github.adamantcheese.chan.core.site.parser.PostParser.Callback;
import com.github.adamantcheese.chan.ui.theme.Theme;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

// Called concurrently to parse the post html and the filters on it belong to ChanReaderRequest
class PostParseCallable
        implements Callable<Post> {
    private final List<Filter> filters;
    private final DatabaseSavedReplyManager savedReplyManager;
    private final Post.Builder postBuilder;
    private final ChanReader reader;
    private final List<PostHide> removedPosts;
    private final Set<Integer> internalNos;
    private final Theme theme;

    public PostParseCallable(
            List<Filter> filters,
            DatabaseSavedReplyManager savedReplyManager,
            Post.Builder builder,
            ChanReader reader,
            List<PostHide> removedPosts,
            Set<Integer> internalNos,
            @NonNull Theme theme
    ) {
        this.filters = filters;
        this.savedReplyManager = savedReplyManager;
        this.postBuilder = builder;
        this.reader = reader;
        this.removedPosts = removedPosts;
        this.internalNos = internalNos;
        this.theme = theme;
    }

    @Override
    public Post call() {
        // needed for "Apply to own posts" to work correctly
        postBuilder.isSavedReply(savedReplyManager.isSaved(postBuilder.board, postBuilder.no));

        return reader.getParser().parse(postBuilder, theme, reader.getElementAction(), filters, new Callback() {
            @Override
            public boolean isSaved(int postNo) {
                return savedReplyManager.isSaved(postBuilder.board, postNo);
            }

            @Override
            public boolean isInternal(int postNo) {
                return internalNos.contains(postNo);
            }

            public boolean isRemoved(int postNo) {
                return removedPosts.contains(new PostHide(postBuilder.board.siteId, postBuilder.board.code, postNo));
            }
        });
    }
}
