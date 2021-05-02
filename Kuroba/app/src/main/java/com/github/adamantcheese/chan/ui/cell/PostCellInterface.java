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
package com.github.adamantcheese.chan.ui.cell;

import android.view.View;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.PostLinkable;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.ui.theme.Theme;
import com.github.adamantcheese.chan.ui.view.FloatingMenuItem;
import com.github.adamantcheese.chan.ui.view.ThumbnailView;

import java.util.List;

public interface PostCellInterface {
    void setPost(
            Loadable loadable,
            Post post,
            PostCellCallback callback,
            boolean inPopup,
            boolean highlighted,
            boolean compact,
            Theme theme
    );

    void unsetPost();

    Post getPost();

    ThumbnailView getThumbnailView(PostImage postImage);

    interface PostCellCallback {
        Loadable getLoadable();

        String getSearchQuery();

        void onPostClicked(Post post);

        void onPostDoubleClicked(Post post);

        void onThumbnailClicked(PostImage image, ThumbnailView thumbnail);

        void onShowPostReplies(Post post);

        enum PostOptions {
            POST_OPTION_QUOTE,
            POST_OPTION_QUOTE_TEXT,
            POST_OPTION_INFO,
            POST_OPTION_COPY,
            POST_OPTION_REPORT,
            POST_OPTION_HIGHLIGHT_ID,
            POST_OPTION_DELETE,
            POST_OPTION_SAVE,
            POST_OPTION_UNSAVE,
            POST_OPTION_PIN,
            POST_OPTION_SHARE,
            POST_OPTION_HIGHLIGHT_TRIPCODE,
            POST_OPTION_HIDE,
            POST_OPTION_OPEN_BROWSER,
            POST_OPTION_FILTER,
            POST_OPTION_FILTER_TRIPCODE,
            POST_OPTION_FILTER_IMAGE_HASH,
            POST_OPTION_FILTER_SUBJECT,
            POST_OPTION_FILTER_COMMENT,
            POST_OPTION_FILTER_NAME,
            POST_OPTION_FILTER_ID,
            POST_OPTION_FILTER_FILENAME,
            POST_OPTION_FILTER_FLAG_CODE,
            POST_OPTION_EXTRA,
            POST_OPTION_REMOVE,
            POST_OPTION_COPY_POST_LINK,
            POST_OPTION_COPY_CROSS_BOARD_LINK,
            POST_OPTION_COPY_POST_TEXT,
            POST_OPTION_COPY_IMG_URL,
            POST_OPTION_COPY_POST_URL;

            public static PostOptions valueOf(int i) {
                return PostOptions.values()[i];
            }
        }

        // These floating menu items have type Integer because it is expected that post option IDs are integers
        Object onPopulatePostOptions(
                Post post, List<FloatingMenuItem<PostOptions>> menu, List<FloatingMenuItem<PostOptions>> extraMenu
        );

        void onPostOptionClicked(View anchor, Post post, PostOptions id, boolean inPopup);

        void onPostLinkableClicked(Post post, PostLinkable linkable);

        void onPostNoClicked(Post post);

        void onPostSelectionQuoted(Post post, CharSequence quoted);
    }
}
