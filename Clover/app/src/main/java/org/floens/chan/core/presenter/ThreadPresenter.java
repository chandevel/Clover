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

import com.android.volley.VolleyError;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.chan.ChanUrls;
import org.floens.chan.core.loader.ChanLoader;
import org.floens.chan.core.loader.LoaderPool;
import org.floens.chan.core.manager.WatchManager;
import org.floens.chan.core.model.ChanThread;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Pin;
import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.PostImage;
import org.floens.chan.core.model.PostLinkable;
import org.floens.chan.core.model.SavedReply;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.adapter.PostAdapter;
import org.floens.chan.ui.cell.ThreadStatusCell;
import org.floens.chan.ui.view.PostView;
import org.floens.chan.ui.view.ThumbnailView;
import org.floens.chan.utils.AndroidUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ThreadPresenter implements ChanLoader.ChanLoaderCallback, PostAdapter.PostAdapterCallback, PostView.PostViewCallback, ThreadStatusCell.Callback {
    private ThreadPresenterCallback threadPresenterCallback;

    private Loadable loadable;
    private ChanLoader chanLoader;
    private boolean searchOpen = false;

    public ThreadPresenter(ThreadPresenterCallback threadPresenterCallback) {
        this.threadPresenterCallback = threadPresenterCallback;
    }

    public void bindLoadable(Loadable loadable) {
        if (!loadable.equals(this.loadable)) {
            if (chanLoader != null) {
                unbindLoadable();
            }

            this.loadable = loadable;
            chanLoader = LoaderPool.getInstance().obtain(loadable, this);
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

    public void onForegroundChanged(boolean foreground) {
        if (chanLoader != null) {
            if (foreground) {
                if (isWatching()) {
                    chanLoader.setAutoLoadMore(true);
                    chanLoader.requestMoreDataAndResetTimer();
                }
            } else {
                chanLoader.setAutoLoadMore(false);
            }
        }
    }

    public boolean pin() {
        if (chanLoader.getThread() != null) {
            WatchManager wm = ChanApplication.getWatchManager();
            Pin pin = wm.findPinByLoadable(loadable);
            if (pin == null) {
                Post op = chanLoader.getThread().op;
                wm.addPin(loadable, op);
            } else {
                wm.removePin(pin);
            }
        }
        return isPinned();
    }

    public boolean isPinned() {
        return ChanApplication.getWatchManager().findPinByLoadable(loadable) != null;
    }

    public void onSearchVisibilityChanged(boolean visible) {
        searchOpen = visible;
        threadPresenterCallback.showSearch(visible);
    }

    public void onSearchEntered(String entered) {
        if (chanLoader.getThread() != null) {
            if (TextUtils.isEmpty(entered)) {
                threadPresenterCallback.filterList(null, null, true, true, false);
            } else {
                processSearch(chanLoader.getThread().posts, entered);
            }
        }
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
        chanLoader.setAutoLoadMore(isWatching());
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
    public void onListScrolledToBottom() {
        if (loadable.isThreadMode()) {
            List<Post> posts = chanLoader.getThread().posts;
            loadable.lastViewed = posts.get(posts.size() - 1).no;
        }

        Pin pin = ChanApplication.getWatchManager().findPinByLoadable(loadable);
        if (pin != null) {
            pin.onBottomPostViewed();
            ChanApplication.getWatchManager().updatePin(pin);
        }
    }

    public void scrollTo(int position, boolean smooth) {
        threadPresenterCallback.scrollTo(position, smooth);
    }

    public void scrollToImage(PostImage postImage, boolean smooth) {
        if (!searchOpen) {
            int position = -1;
            for (int i = 0; i < chanLoader.getThread().posts.size(); i++) {
                Post post = chanLoader.getThread().posts.get(i);
                if (post.hasImage && post.imageUrl.equals(postImage.imageUrl)) {
                    position = i;
                    break;
                }
            }
            scrollTo(position, smooth);
        }
    }

    public void scrollToPost(Post needle, boolean smooth) {
        int position = -1;
        for (int i = 0; i < chanLoader.getThread().posts.size(); i++) {
            Post post = chanLoader.getThread().posts.get(i);
            if (post.no == needle.no) {
                position = i;
                break;
            }
        }
        scrollTo(position, smooth);
    }

    public void highlightPost(Post post) {
        threadPresenterCallback.highlightPost(post);
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
        } else {
            if (searchOpen) {
                threadPresenterCallback.filterList(null, null, true, false, true);
                highlightPost(post);
                scrollToPost(post, false);
            }
        }
    }

    @Override
    public void onThumbnailClicked(Post post, ThumbnailView thumbnail) {
        List<PostImage> images = new ArrayList<>();
        int index = -1;
        for (int i = 0; i < chanLoader.getThread().posts.size(); i++) {
            Post item = chanLoader.getThread().posts.get(i);
            if (item.hasImage) {
                images.add(new PostImage(String.valueOf(item.tim), item.thumbnailUrl, item.imageUrl, item.filename, item.ext, item.imageWidth, item.imageHeight));
                if (item.no == post.no) {
                    index = images.size() - 1;
                }
            }
        }

        threadPresenterCallback.showImages(images, index, chanLoader.getLoadable(), thumbnail);
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
                threadPresenterCallback.highlightPostId(post.id);
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
    public boolean isPostLastSeen(Post post) {
        return false;
    }

    /*
     * ThreadStatusCell callbacks
     */
    @Override
    public long getTimeUntilLoadMore() {
        return chanLoader.getTimeUntilLoadMore();
    }

    @Override
    public boolean isWatching() {
        return loadable.isThreadMode() && ChanSettings.autoRefreshThread.get() && !chanLoader.getThread().closed && !chanLoader.getThread().archived;
    }

    @Override
    public ChanThread getChanThread() {
        return chanLoader.getThread();
    }

    @Override
    public void onListStatusClicked() {
        chanLoader.requestMoreDataAndResetTimer();
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

    private void processSearch(List<Post> all, String originalQuery) {
        List<Post> filtered = new ArrayList<>();

        String query = originalQuery.toLowerCase(Locale.ENGLISH);

        boolean add;
        for (Post item : all) {
            add = false;
            if (item.comment.toString().toLowerCase(Locale.ENGLISH).contains(query)) {
                add = true;
            } else if (item.subject.toLowerCase(Locale.ENGLISH).contains(query)) {
                add = true;
            } else if (item.name.toLowerCase(Locale.ENGLISH).contains(query)) {
                add = true;
            } else if (item.filename != null && item.filename.toLowerCase(Locale.ENGLISH).contains(query)) {
                add = true;
            }
            if (add) {
                filtered.add(item);
            }
        }

        threadPresenterCallback.filterList(originalQuery, filtered, false, false, false);
    }

    public interface ThreadPresenterCallback {
        void showPosts(ChanThread thread);

        void showError(VolleyError error);

        void showLoading();

        void showPostInfo(String info);

        void showPostLinkables(List<PostLinkable> linkables);

        void clipboardPost(Post post);

        void showThread(Loadable threadLoadable);

        void openLink(String link);

        void showPostsPopup(Post forPost, List<Post> posts);

        void showImages(List<PostImage> images, int index, Loadable loadable, ThumbnailView thumbnail);

        void scrollTo(int position, boolean smooth);

        void highlightPost(Post post);

        void highlightPostId(String id);

        void showSearch(boolean show);

        void filterList(String query, List<Post> filter, boolean clearFilter, boolean setEmptyText, boolean hideKeyboard);
    }
}
