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

import androidx.recyclerview.widget.RecyclerView;

import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.PostLinkable;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.features.embedding.EmbeddingEngine;
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
            int markedNo,
            boolean showDivider,
            ChanSettings.PostViewMode postViewMode,
            boolean compact,
            Theme theme,
            RecyclerView attachedTo
    );

    Post getPost();

    ThumbnailView getThumbnailView(PostImage postImage);

    interface PostCellCallback {
        Loadable getLoadable();

        EmbeddingEngine getEmbeddingEngine();

        void onPostClicked(Post post);

        void onPostDoubleClicked(Post post);

        void onThumbnailClicked(PostImage image, ThumbnailView thumbnail);

        void onShowPostReplies(Post post);

        // These floating menu items have type Integer because it is expected that post option IDs are integers
        Object onPopulatePostOptions(
                Post post, List<FloatingMenuItem<Integer>> menu, List<FloatingMenuItem<Integer>> extraMenu
        );

        void onPostOptionClicked(View anchor, Post post, Object id, boolean inPopup);

        void onPostLinkableClicked(Post post, PostLinkable linkable);

        void onPostNoClicked(Post post);

        void onPostSelectionQuoted(Post post, CharSequence quoted);
    }
}
