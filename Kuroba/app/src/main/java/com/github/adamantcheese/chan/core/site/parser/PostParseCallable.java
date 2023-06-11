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
import com.github.adamantcheese.chan.core.model.orm.PostHide;
import com.github.adamantcheese.chan.core.site.parser.PostParser.PostParserCallback;
import com.github.adamantcheese.chan.features.theme.Theme;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

// Called concurrently to parse the post html and the filters on it belong to ChanReaderRequest
class PostParseCallable
        implements Callable<Post> {
    private final Post.Builder postBuilder;
    private final PostParser parser;
    private final Theme theme;
    private final PostParserCallback postParserCallback;

    public PostParseCallable(
            DatabaseSavedReplyManager savedReplyManager,
            Post.Builder builder,
            PostParser parser,
            List<PostHide> removedPosts,
            Set<Integer> internalNos,
            @NonNull Theme theme
    ) {
        this.postBuilder = builder;
        this.parser = parser;
        this.theme = theme;
        postParserCallback = new PostParserCallback() {
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
        };
    }

    @Override
    public Post call() {
        return parser.parse(postBuilder, theme, postParserCallback);
    }
}
