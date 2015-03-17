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
package org.floens.chan.core.presenter;

import android.text.TextUtils;
import android.view.Menu;
import android.widget.ImageView;

import com.android.volley.VolleyError;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.chan.ChanUrls;
import org.floens.chan.core.loader.ChanLoader;
import org.floens.chan.core.loader.LoaderPool;
import org.floens.chan.core.model.ChanThread;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.PostImage;
import org.floens.chan.core.model.PostLinkable;
import org.floens.chan.core.model.SavedReply;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.adapter.PostAdapter;
import org.floens.chan.ui.view.PostView;
import org.floens.chan.utils.AndroidUtils;

import java.util.ArrayList;
import java.util.List;

public class ThreadPresenter implements ChanLoader.ChanLoaderCallback, PostAdapter.PostAdapterCallback, PostView.PostViewCallback {
    private ThreadPresenterCallback threadPresenterCallback;

    private Loadable loadable;
    private ChanLoader chanLoader;

    public ThreadPresenter(ThreadPresenterCallback threadPresenterCallback) {
        this.threadPresenterCallback = threadPresenterCallback;
    }

    public void bindLoadable(Loadable loadable) {
        if (chanLoader == null) {
            if (!loadable.equals(this.loadable)) {
                if (this.loadable != null) {
                    unbindLoadable();
                }

                this.loadable = loadable;

                chanLoader = LoaderPool.getInstance().obtain(loadable, this);
            }
        }
    }

    public void unbindLoadable() {
        if (chanLoader != null) {
            LoaderPool.getInstance().release(chanLoader, this);
            chanLoader = null;
            loadable = null;

            threadPresenterCallback.showLoading();
        }
    }

    public void requestData() {
        threadPresenterCallback.showLoading();
        chanLoader.requestData();
    }

    @Override
    public Loadable getLoadable() {
        return loadable;
    }

    /*
     * ChanLoader callbacks
     */
    @Override
    public void onChanLoaderData(ChanThread result) {
        threadPresenterCallback.showPosts(result);
    }

    @Override
    public void onChanLoaderError(VolleyError error) {
        threadPresenterCallback.showError(error);
    }

    /*
     * PostAdapter callbacks
     */
    @Override
    public void onFilteredResults(String filter, int count, boolean all) {

    }

    @Override
    public void onListScrolledToBottom() {

    }

    @Override
    public void onListStatusClicked() {

    }

    @Override
    public void scrollTo(int position) {

    }

    /*
     * PostView callbacks
     */
    @Override
    public void onPostClicked(Post post) {
        if (loadable.mode == Loadable.Mode.CATALOG) {
            Loadable threadLoadable = new Loadable(post.board, post.no);
            threadLoadable.generateTitle(post);
            threadPresenterCallback.showThread(threadLoadable);
        }
    }

    @Override
    public void onThumbnailClicked(Post post, ImageView thumbnail) {
        List<PostImage> images = new ArrayList<>();
        int index = -1;
        for (int i = 0; i < chanLoader.getThread().posts.size(); i++) {
            Post item = chanLoader.getThread().posts.get(i);
            if (item.hasImage) {
                images.add(new PostImage(item.thumbnailUrl, item.imageUrl, item.filename, item.ext, item.imageWidth, item.imageHeight));
                if (item.no == post.no) {
                    index = i;
                }
            }
        }

        threadPresenterCallback.showImages(images, index, thumbnail);
    }

    @Override
    public void onPopulatePostOptions(Post post, Menu menu) {
        if (chanLoader.getLoadable().isBoardMode() || chanLoader.getLoadable().isCatalogMode()) {
            menu.add(Menu.NONE, 9, Menu.NONE, AndroidUtils.getRes().getString(R.string.action_pin));
        }

        if (chanLoader.getLoadable().isThreadMode()) {
            menu.add(Menu.NONE, 10, Menu.NONE, AndroidUtils.getRes().getString(R.string.post_quick_reply));
        }

        String[] baseOptions = AndroidUtils.getRes().getStringArray(R.array.post_options);
        for (int i = 0; i < baseOptions.length; i++) {
            menu.add(Menu.NONE, i, Menu.NONE, baseOptions[i]);
        }

        if (!TextUtils.isEmpty(post.id)) {
            menu.add(Menu.NONE, 6, Menu.NONE, AndroidUtils.getRes().getString(R.string.post_highlight_id));
        }

        // Only add the delete option when the post is a saved reply
        if (ChanApplication.getDatabaseManager().isSavedReply(post.board, post.no)) {
            menu.add(Menu.NONE, 7, Menu.NONE, AndroidUtils.getRes().getString(R.string.delete));
        }

        if (ChanSettings.getDeveloper()) {
            menu.add(Menu.NONE, 8, Menu.NONE, "Make this a saved reply");
        }
    }

    public void onPostOptionClicked(Post post, int id) {
        switch (id) {
            case 10: // Quick reply
//                openReply(false); TODO
                // Pass through
            case 0: // Quote
                ChanApplication.getReplyManager().quote(post.no);
                break;
            case 1: // Quote inline
                ChanApplication.getReplyManager().quoteInline(post.no, post.comment.toString());
                break;
            case 2: // Info
                showPostInfo(post);
                break;
            case 3: // Show clickables
                if (post.linkables.size() > 0) {
                    threadPresenterCallback.showPostLinkables(post.linkables);
                }
                break;
            case 4: // Copy text
                threadPresenterCallback.clipboardPost(post);
                break;
            case 5: // Report
                AndroidUtils.openLink(ChanUrls.getReportUrl(post.board, post.no));
                break;
            case 6: // Id
                //TODO
//                highlightedId = post.id;
//                threadManagerListener.onRefreshView();
                break;
            case 7: // Delete
//                deletePost(post); TODO
                break;
            case 8: // Save reply (debug)
                ChanApplication.getDatabaseManager().saveReply(new SavedReply(post.board, post.no, "foo"));
                break;
            case 9: // Pin
                ChanApplication.getWatchManager().addPin(post);
                break;
        }
    }

    @Override
    public void onPostLinkableClicked(PostLinkable linkable) {
        if (linkable.type == PostLinkable.Type.QUOTE) {
            Post post = findPostById((Integer) linkable.value);

            List<Post> list = new ArrayList<>(1);
            list.add(post);
            threadPresenterCallback.showPostsPopup(linkable.post, list);
        } else if (linkable.type == PostLinkable.Type.LINK) {
            threadPresenterCallback.openLink((String) linkable.value);
        } else if (linkable.type == PostLinkable.Type.THREAD) {
            PostLinkable.ThreadLink link = (PostLinkable.ThreadLink) linkable.value;
            Loadable thread = new Loadable(link.board, link.threadId);

            threadPresenterCallback.showThread(thread);
        }
    }

    @Override
    public void onShowPostReplies(Post post) {
        List<Post> posts = new ArrayList<>();
        for (int no : post.repliesFrom) {
            Post replyPost = findPostById(no);
            if (replyPost != null) {
                posts.add(replyPost);
            }
        }
        if (posts.size() > 0) {
            threadPresenterCallback.showPostsPopup(post, posts);
        }
    }

    @Override
    public boolean isPostHightlighted(Post post) {
        return false;
    }

    public void highlightPost(int no) {
    }

    public void scrollToPost(int no) {
    }

    @Override
    public boolean isPostLastSeen(Post post) {
        return false;
    }

    private void showPostInfo(Post post) {
        String text = "";

        if (post.hasImage) {
            text += "File: " + post.filename + "." + post.ext + " \nDimensions: " + post.imageWidth + "x"
                    + post.imageHeight + "\nSize: " + AndroidUtils.getReadableFileSize(post.fileSize, false) + "\n\n";
        }

        text += "Time: " + post.date;

        if (!TextUtils.isEmpty(post.id)) {
            text += "\nId: " + post.id;
        }

        if (!TextUtils.isEmpty(post.tripcode)) {
            text += "\nTripcode: " + post.tripcode;
        }

        if (!TextUtils.isEmpty(post.countryName)) {
            text += "\nCountry: " + post.countryName;
        }

        if (!TextUtils.isEmpty(post.capcode)) {
            text += "\nCapcode: " + post.capcode;
        }

        threadPresenterCallback.showPostInfo(text);
    }

    private Post findPostById(int id) {
        for (Post post : chanLoader.getThread().posts) {
            if (post.no == id) {
                return post;
            }
        }
        return null;
    }

    public interface ThreadPresenterCallback {
        public void showPosts(ChanThread thread);

        public void showError(VolleyError error);

        public void showLoading();

        public void showPostInfo(String info);

        public void showPostLinkables(List<PostLinkable> linkables);

        public void clipboardPost(Post post);

        public void showThread(Loadable threadLoadable);

        public void openLink(String link);

        public void showPostsPopup(Post forPost, List<Post> posts);

        public void showImages(List<PostImage> images, int index, ImageView thumbnail);
    }
}
