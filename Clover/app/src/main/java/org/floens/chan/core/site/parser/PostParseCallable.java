/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
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
package org.floens.chan.core.site.parser;

import org.floens.chan.core.database.DatabaseSavedReplyManager;
import org.floens.chan.core.manager.FilterEngine;
import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.orm.Filter;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

// Called concurrently to parse the post html and the filters on it
// belong to ChanReaderRequest
class PostParseCallable implements Callable<Post> {
    private static final String TAG = "PostParseCallable";

    private FilterEngine filterEngine;
    private List<Filter> filters;
    private DatabaseSavedReplyManager savedReplyManager;
    private Post.Builder post;
    private ChanReader reader;
    private final Set<Integer> internalIds;

    public PostParseCallable(FilterEngine filterEngine,
                             List<Filter> filters,
                             DatabaseSavedReplyManager savedReplyManager,
                             Post.Builder post,
                             ChanReader reader, Set<Integer> internalIds) {
        this.filterEngine = filterEngine;
        this.filters = filters;
        this.savedReplyManager = savedReplyManager;
        this.post = post;
        this.reader = reader;
        this.internalIds = internalIds;
    }

    @Override
    public Post call() throws Exception {
        // Process the filters before finish, because parsing the html is dependent on filter matches
        processPostFilter(post);

        post.isSavedReply(savedReplyManager.isSaved(post.board, post.id));

        return reader.getParser().parse(null, post, new PostParser.Callback() {
            @Override
            public boolean isSaved(int postNo) {
                return savedReplyManager.isSaved(post.board, postNo);
            }

            @Override
            public boolean isInternal(int postNo) {
                return internalIds.contains(postNo);
            }
        });
    }

    private void processPostFilter(Post.Builder post) {
        int filterSize = filters.size();
        for (int i = 0; i < filterSize; i++) {
            Filter filter = filters.get(i);
            if (filterEngine.matches(filter, post)) {
                FilterEngine.FilterAction action = FilterEngine.FilterAction.forId(filter.action);
                switch (action) {
                    case COLOR:
                        post.filter(filter.color, false, false, false);
                        break;
                    case HIDE:
                        post.filter(0, true, false, false);
                        break;
                    case REMOVE:
                        post.filter(0, false, true, false);
                        break;
                    case PIN:
                        post.filter(0, false, false, true);
                }
            }
        }
    }
}
