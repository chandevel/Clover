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
package org.floens.chan.ui.cell;

import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.PostLinkable;
import org.floens.chan.ui.theme.Theme;
import org.floens.chan.ui.view.FloatingMenuItem;
import org.floens.chan.ui.view.ThumbnailView;

import java.util.List;

public interface PostCellInterface {
    void setPost(Theme theme, Post post, PostCellCallback callback, boolean highlighted, int markedN);

    Post getPost();

    ThumbnailView getThumbnailView();

    enum PostViewMode {
        LIST("list"),
        CARD("grid");

        public String name;

        PostViewMode(String name) {
            this.name = name;
        }

        public static PostViewMode find(String name) {
            for (PostViewMode mode : PostViewMode.values()) {
                if (mode.name.equals(name)) {
                    return mode;
                }
            }
            return null;
        }
    }

    interface PostCellCallback {
        Loadable getLoadable();

        void onPostClicked(Post post);

        void onThumbnailClicked(Post post, ThumbnailView thumbnail);

        void onShowPostReplies(Post post);

        void onPopulatePostOptions(Post post, List<FloatingMenuItem> menu);

        void onPostOptionClicked(Post post, Object id);

        void onPostLinkableClicked(PostLinkable linkable);

        void onPostNoClicked(Post post);
    }
}
