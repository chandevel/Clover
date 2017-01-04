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
package org.floens.chan.core.site.sites.chan4;

import org.floens.chan.chan.ChanParser;
import org.floens.chan.core.database.DatabaseSavedReplyManager;
import org.floens.chan.core.manager.FilterEngine;
import org.floens.chan.core.model.Filter;
import org.floens.chan.core.model.Post;

import java.util.List;
import java.util.concurrent.Callable;

// Called concurrently to parse the post html and the filters on it
class PostParseCallable implements Callable<Post> {
    private static final String TAG = "PostParseCallable";

    private FilterEngine filterEngine;
    private List<Filter> filters;
    private DatabaseSavedReplyManager savedReplyManager;
    private Post.Builder post;
    private ChanParser parser;

    public PostParseCallable(FilterEngine filterEngine,
                             List<Filter> filters,
                             DatabaseSavedReplyManager savedReplyManager,
                             Post.Builder post,
                             ChanParser parser) {
        this.filterEngine = filterEngine;
        this.filters = filters;
        this.savedReplyManager = savedReplyManager;
        this.post = post;
        this.parser = parser;
    }

    @Override
    public Post call() throws Exception {
        // Process the filters before finish, because parsing the html is dependent on filter matches
        processPostFilter(post);

        post.isSavedReply(savedReplyManager.isSaved(post.board.code, post.id));

//        if (!post.parse(parser)) {
//            Logger.e(TAG, "Incorrect data about post received for post " + post.no);
//            return null;
//        }
        return parser.parse(post);
    }

    private void processPostFilter(Post.Builder post) {
        int filterSize = filters.size();
        for (int i = 0; i < filterSize; i++) {
            Filter filter = filters.get(i);
            if (filterEngine.matches(filter, post)) {
                FilterEngine.FilterAction action = FilterEngine.FilterAction.forId(filter.action);
                switch (action) {
                    case COLOR:
                        post.filter(filter.color, false, false);
                        break;
                    case HIDE:
                        post.filter(0, true, false);
                        break;
                    case REMOVE:
                        post.filter(0, false, true);
                        break;
                }
            }
        }
    }
}
