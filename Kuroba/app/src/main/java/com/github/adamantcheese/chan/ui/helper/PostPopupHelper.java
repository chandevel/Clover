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
package com.github.adamantcheese.chan.ui.helper;

import android.content.Context;

import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.PostLinkable;
import com.github.adamantcheese.chan.core.presenter.ThreadPresenter;
import com.github.adamantcheese.chan.ui.controller.PostRepliesController;
import com.github.adamantcheese.chan.ui.view.ThumbnailView;
import com.github.adamantcheese.chan.utils.RecyclerUtils.RecyclerViewPosition;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;

import static com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast;

public class PostPopupHelper {
    private final Context context;
    private final ThreadPresenter presenter;
    private final PostPopupHelperCallback callback;

    private final Stack<RepliesData> postDataStack = new Stack<>();
    private PostRepliesController presentingController;

    public PostPopupHelper(Context context, ThreadPresenter presenter, PostPopupHelperCallback callback) {
        this.context = context;
        this.presenter = presenter;
        this.callback = callback;
    }

    public void showPosts(Post forPost, List<Post> posts) {
        if (posts.isEmpty()) {
            showToast(context, "No posts to display! Posts may have been removed.");
            return;
        }

        postDataStack.push(new RepliesData(forPost, posts));

        if (postDataStack.size() == 1) {
            if (presentingController == null) {
                presentingController = new PostRepliesController(context, this, presenter);
                callback.presentController(presentingController);
            }
        }

        if (presenter.getLoadable() == null) {
            throw new IllegalStateException("Thread loadable cannot be null");
        }

        updateLinkableMarkedNos(postDataStack.peek(), true);

        presentingController.displayData(presenter.getLoadable(), postDataStack.peek());
    }

    public void pop() {
        if (!postDataStack.isEmpty()) {
            updateLinkableMarkedNos(postDataStack.pop(), !postDataStack.isEmpty());
        }

        if (!postDataStack.isEmpty()) {
            if (presenter.getLoadable() == null) {
                throw new IllegalStateException("Thread loadable cannot be null");
            }

            presentingController.displayData(presenter.getLoadable(), postDataStack.peek());
        } else {
            dismiss();
        }
    }

    public void popAll() {
        while (!postDataStack.empty()) {
            updateLinkableMarkedNos(postDataStack.pop(), false);
        }
        dismiss();
    }

    private void updateLinkableMarkedNos(RepliesData data, boolean bind) {
        for (Post p : data.posts) {
            // we only care about quote links here
            List<PostLinkable> linkables = p.getLinkables();
            for (Iterator<PostLinkable> iterator = linkables.iterator(); iterator.hasNext(); ) {
                PostLinkable linkable = iterator.next();
                if (linkable.type != PostLinkable.Type.QUOTE) {
                    iterator.remove();
                }
            }
            // only set a marked no if there's more than one quote link, for visual clarity
            for (PostLinkable linkable : linkables) {
                linkable.setMarkedNo(bind && linkables.size() > 1 ? data.forPostNo : -1);
            }
        }
    }

    public boolean isOpen() {
        return presentingController != null && presentingController.alive;
    }

    public List<Post> getDisplayingPosts() {
        return !postDataStack.isEmpty() ? postDataStack.peek().posts : Collections.emptyList();
    }

    public void scrollTo(int displayPosition) {
        presentingController.scrollTo(displayPosition);
    }

    public ThumbnailView getThumbnail(PostImage postImage) {
        return presentingController.getThumbnail(postImage);
    }

    public void postClicked(Post p) {
        popAll();
        presenter.highlightPostNo(p.no);
        presenter.scrollToPost(p, true);
    }

    private void dismiss() {
        if (presentingController != null) {
            presentingController.stopPresenting();
            presentingController = null;
        }
    }

    public static class RepliesData {
        public List<Post> posts;
        public int forPostNo;
        public RecyclerViewPosition position;

        public RepliesData(Post forPost, List<Post> posts) {
            this.forPostNo = forPost == null ? -1 : forPost.no;
            this.posts = posts;
            position = new RecyclerViewPosition(0, 0);
        }
    }

    public interface PostPopupHelperCallback {
        void presentController(Controller controller);
    }
}
