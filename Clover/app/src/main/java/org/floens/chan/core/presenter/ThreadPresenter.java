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

import org.floens.chan.Chan;
import org.floens.chan.R;
import org.floens.chan.chan.ChanLoader;
import org.floens.chan.chan.ChanUrls;
import org.floens.chan.core.database.DatabaseManager;
import org.floens.chan.core.exception.ChanLoaderException;
import org.floens.chan.core.http.DeleteHttpCall;
import org.floens.chan.core.http.ReplyManager;
import org.floens.chan.core.manager.BoardManager;
import org.floens.chan.core.manager.WatchManager;
import org.floens.chan.core.model.ChanThread;
import org.floens.chan.core.model.History;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Pin;
import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.PostImage;
import org.floens.chan.core.model.PostLinkable;
import org.floens.chan.core.model.SavedReply;
import org.floens.chan.core.pool.LoaderPool;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.adapter.PostAdapter;
import org.floens.chan.ui.adapter.PostsFilter;
import org.floens.chan.ui.cell.PostCellInterface;
import org.floens.chan.ui.cell.ThreadStatusCell;
import org.floens.chan.ui.helper.PostHelper;
import org.floens.chan.ui.layout.ThreadListLayout;
import org.floens.chan.ui.view.FloatingMenuItem;
import org.floens.chan.ui.view.ThumbnailView;
import org.floens.chan.utils.AndroidUtils;

import java.util.ArrayList;
import java.util.List;

import static org.floens.chan.utils.AndroidUtils.getString;

public class ThreadPresenter implements ChanLoader.ChanLoaderCallback, PostAdapter.PostAdapterCallback, PostCellInterface.PostCellCallback, ThreadStatusCell.Callback, ThreadListLayout.ThreadListLayoutPresenterCallback {
    private static final int POST_OPTION_QUOTE = 0;
    private static final int POST_OPTION_QUOTE_TEXT = 1;
    private static final int POST_OPTION_INFO = 2;
    private static final int POST_OPTION_LINKS = 3;
    private static final int POST_OPTION_COPY_TEXT = 4;
    private static final int POST_OPTION_REPORT = 5;
    private static final int POST_OPTION_HIGHLIGHT_ID = 6;
    private static final int POST_OPTION_DELETE = 7;
    private static final int POST_OPTION_SAVE = 8;
    private static final int POST_OPTION_PIN = 9;
    private static final int POST_OPTION_SHARE = 10;
    private static final int POST_OPTION_HIGHLIGHT_TRIPCODE = 11;
    private static final int POST_OPTION_HIDE = 12;
    private static final int POST_OPTION_OPEN_BROWSER = 13;
    private static final int POST_OPTION_FILTER_TRIPCODE = 14;

    private WatchManager watchManager;
    private DatabaseManager databaseManager;
    private ReplyManager replyManager;
    private BoardManager boardManager;

    private ThreadPresenterCallback threadPresenterCallback;

    private Loadable loadable;
    private ChanLoader chanLoader;
    private boolean searchOpen = false;
    private String searchQuery;
    private PostsFilter.Order order = PostsFilter.Order.BUMP;
    private boolean historyAdded = false;

    public ThreadPresenter(ThreadPresenterCallback threadPresenterCallback) {
        this.threadPresenterCallback = threadPresenterCallback;

        watchManager = Chan.getWatchManager();
        databaseManager = Chan.getDatabaseManager();
        replyManager = Chan.getReplyManager();
        boardManager = Chan.getBoardManager();
    }

    public void bindLoadable(Loadable loadable) {
        if (!loadable.equals(this.loadable)) {
            if (chanLoader != null) {
                unbindLoadable();
            }

            Pin pin = watchManager.findPinByLoadable(loadable);
            if (pin != null) {
                // Use the loadable from the pin.
                // This way we can store the list position in the pin loadable,
                // and not in a separate loadable instance.
                loadable = pin.loadable;
            }
            this.loadable = loadable;

            chanLoader = LoaderPool.getInstance().obtain(loadable, this);
        }
    }

    public void unbindLoadable() {
        if (chanLoader != null) {
            chanLoader.clearTimer();
            LoaderPool.getInstance().release(chanLoader, this);
            chanLoader = null;
            loadable = null;
            historyAdded = false;

            threadPresenterCallback.showNewPostsNotification(false, -1);
            threadPresenterCallback.showLoading();
        }
    }

    public void requestInitialData() {
        if (chanLoader.getThread() == null) {
            requestData();
        } else {
            chanLoader.quickLoad();
        }
    }

    public void requestData() {
        threadPresenterCallback.showLoading();
        chanLoader.requestData();
    }

    public void onForegroundChanged(boolean foreground) {
        if (chanLoader != null) {
            if (foreground && isWatching()) {
                chanLoader.requestMoreDataAndResetTimer();
                if (chanLoader.getThread() != null) {
                    // Show loading indicator in the status cell
                    showPosts();
                }
            } else {
                chanLoader.clearTimer();
            }
        }
    }

    public boolean pin() {
        Pin pin = watchManager.findPinByLoadable(loadable);
        if (pin == null) {
            if (chanLoader.getThread() != null) {
                Post op = chanLoader.getThread().op;
                watchManager.createPin(loadable, op);
            }
        } else {
            watchManager.deletePin(pin);
        }
        return isPinned();
    }

    public boolean isPinned() {
        return watchManager.findPinByLoadable(loadable) != null;
    }

    public void onSearchVisibilityChanged(boolean visible) {
        searchOpen = visible;
        threadPresenterCallback.showSearch(visible);
        if (!visible) {
            searchQuery = null;
        }

        if (chanLoader.getThread() != null) {
            showPosts();
        }
    }

    public void onSearchEntered(String entered) {
        if (chanLoader.getThread() != null) {
            searchQuery = entered;
            showPosts();
            if (TextUtils.isEmpty(entered)) {
                threadPresenterCallback.setSearchStatus(null, true, false);
            } else {
                threadPresenterCallback.setSearchStatus(entered, false, false);
            }
        }
    }

    public void setOrder(PostsFilter.Order order) {
        if (this.order != order) {
            this.order = order;
            if (chanLoader != null) {
                ChanThread thread = chanLoader.getThread();
                if (thread != null) {
                    scrollTo(0, false);
                    showPosts();
                }
            }
        }
    }

    public void refreshUI() {
        if (chanLoader.getThread() != null) {
            showPosts();
        }
    }

    public void showAlbum() {
        List<Post> posts = threadPresenterCallback.getDisplayingPosts();
        int[] pos = threadPresenterCallback.getCurrentPosition();
        int displayPosition = pos[0];

        List<PostImage> images = new ArrayList<>();
        int index = 0;
        for (int i = 0; i < posts.size(); i++) {
            Post item = posts.get(i);
            if (item.hasImage) {
                images.add(item.image);
            }
            if (i == displayPosition) {
                index = images.size();
            }
        }

        threadPresenterCallback.showAlbum(images, index);
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
        if (isWatching()) {
            chanLoader.setTimer();
        }

        showPosts();

        if (loadable.isThreadMode()) {
            boolean newPosts = false;
            int lastLoaded = loadable.lastLoaded;

            List<Post> posts = result.posts;
            List<PostImage> images = new ArrayList<>();

            int more = 0;
            if (lastLoaded > 0) {
                for (int i = 0; i < posts.size(); i++) {
                    Post post = posts.get(i);

                    if(newPosts) {
                        if(post.hasImage) {
                            images.add(post.image);
                        }
                    }

                    if (post.no == lastLoaded) {
                        more = posts.size() - i - 1;
                        newPosts = true;
                    }
                }
            }
            loadable.setLastLoaded(posts.get(posts.size() - 1).no);

            if (more > 0) {
                threadPresenterCallback.showNewPostsNotification(true, more);
                if(images.size() > 0) {
                    threadPresenterCallback.addImages(images, loadable);
                }
            }
        }

        if (loadable.markedNo >= 0) {
            Post markedPost = findPostById(loadable.markedNo);
            if (markedPost != null) {
                highlightPost(markedPost);
                scrollToPost(markedPost, false);
            }
            loadable.markedNo = -1;
        }

        addHistory();
    }

    @Override
    public void onChanLoaderError(ChanLoaderException error) {
        threadPresenterCallback.showError(error);
    }

    /*
     * PostAdapter callbacks
     */
    @Override
    public void onListScrolledToBottom() {
        if (loadable.isThreadMode()) {
            List<Post> posts = chanLoader.getThread().posts;
            loadable.setLastViewed(posts.get(posts.size() - 1).no);
        }

        Pin pin = watchManager.findPinByLoadable(loadable);
        if (pin != null) {
            watchManager.onBottomPostViewed(pin);
        }

        threadPresenterCallback.showNewPostsNotification(false, -1);

        // Update the last seen indicator
        showPosts();
    }

    public void scrollTo(int displayPosition, boolean smooth) {
        threadPresenterCallback.scrollTo(displayPosition, smooth);
    }

    public void scrollToImage(PostImage postImage, boolean smooth) {
        if (!searchOpen) {
            int position = -1;
            List<Post> posts = threadPresenterCallback.getDisplayingPosts();
            for (int i = 0; i < posts.size(); i++) {
                Post post = posts.get(i);
                if (post.hasImage && post.imageUrl.equals(postImage.imageUrl)) {
                    position = i;
                    break;
                }
            }
            if (position >= 0) {
                scrollTo(position, smooth);
            }
        }
    }

    public void scrollToPost(Post needle, boolean smooth) {
        int position = -1;
        List<Post> posts = threadPresenterCallback.getDisplayingPosts();
        for (int i = 0; i < posts.size(); i++) {
            Post post = posts.get(i);
            if (post.no == needle.no) {
                position = i;
                break;
            }
        }
        if (position >= 0) {
            scrollTo(position, smooth);
        }
    }

    public void highlightPost(Post post) {
        threadPresenterCallback.highlightPost(post);
    }

    public void selectPost(int post) {
        threadPresenterCallback.selectPost(post);
    }

    public void selectPostImage(PostImage postImage) {
        List<Post> posts = threadPresenterCallback.getDisplayingPosts();
        for (int i = 0; i < posts.size(); i++) {
            Post post = posts.get(i);
            if (post.image == postImage) {
                scrollToPost(post, false);
                highlightPost(post);
                break;
            }
        }
    }

    /*
     * PostView callbacks
     */
    @Override
    public void onPostClicked(Post post) {
        if (loadable.isCatalogMode()) {
            Loadable threadLoadable = databaseManager.getDatabaseLoadableManager().get(Loadable.forThread(post.board, post.no));
            threadLoadable.title = PostHelper.getTitle(post, loadable);
            threadPresenterCallback.showThread(threadLoadable);
        } else {
            if (searchOpen) {
                searchQuery = null;
                showPosts();
                threadPresenterCallback.setSearchStatus(null, false, true);
                highlightPost(post);
                scrollToPost(post, false);
            } else {
                threadPresenterCallback.postClicked(post);
            }
        }
    }

    @Override
    public void onThumbnailClicked(Post post, ThumbnailView thumbnail) {
        List<PostImage> images = new ArrayList<>();
        int index = -1;
        List<Post> posts = threadPresenterCallback.getDisplayingPosts();
        for (int i = 0; i < posts.size(); i++) {
            Post item = posts.get(i);
            if (item.hasImage) {
                images.add(item.image);
                if (item.no == post.no) {
                    index = images.size() - 1;
                }
            }
        }

        threadPresenterCallback.showImages(images, index, chanLoader.getLoadable(), thumbnail);
    }

    @Override
    public void onPopulatePostOptions(Post post, List<FloatingMenuItem> menu) {
        if (!loadable.isThreadMode()) {
            menu.add(new FloatingMenuItem(POST_OPTION_PIN, R.string.action_pin));
        } else {
            menu.add(new FloatingMenuItem(POST_OPTION_QUOTE, R.string.post_quote));
            menu.add(new FloatingMenuItem(POST_OPTION_QUOTE_TEXT, R.string.post_quote_text));
        }

        menu.add(new FloatingMenuItem(POST_OPTION_INFO, R.string.post_info));
        menu.add(new FloatingMenuItem(POST_OPTION_LINKS, R.string.post_show_links));
        menu.add(new FloatingMenuItem(POST_OPTION_OPEN_BROWSER, R.string.action_open_browser));
        menu.add(new FloatingMenuItem(POST_OPTION_SHARE, R.string.post_share));
        menu.add(new FloatingMenuItem(POST_OPTION_COPY_TEXT, R.string.post_copy_text));
        menu.add(new FloatingMenuItem(POST_OPTION_REPORT, R.string.post_report));

        if (!loadable.isThreadMode()) {
            menu.add(new FloatingMenuItem(POST_OPTION_HIDE, R.string.post_hide));
        }

        if (loadable.isThreadMode()) {
            if (!TextUtils.isEmpty(post.id)) {
                menu.add(new FloatingMenuItem(POST_OPTION_HIGHLIGHT_ID, R.string.post_highlight_id));
            }

            if (!TextUtils.isEmpty(post.tripcode)) {
                menu.add(new FloatingMenuItem(POST_OPTION_HIGHLIGHT_TRIPCODE, R.string.post_highlight_tripcode));
                menu.add(new FloatingMenuItem(POST_OPTION_FILTER_TRIPCODE, R.string.post_filter_tripcode));
            }
        }

        if (databaseManager.getDatabaseSavedReplyManager().isSaved(post.board, post.no)) {
            menu.add(new FloatingMenuItem(POST_OPTION_DELETE, R.string.delete));
        }

        if (ChanSettings.developer.get()) {
            menu.add(new FloatingMenuItem(POST_OPTION_SAVE, "Save"));
        }
    }

    public void onPostOptionClicked(Post post, Object id) {
        switch ((Integer) id) {
            case POST_OPTION_QUOTE:
                threadPresenterCallback.hidePostsPopup();
                threadPresenterCallback.quote(post, false);
                break;
            case POST_OPTION_QUOTE_TEXT:
                threadPresenterCallback.hidePostsPopup();
                threadPresenterCallback.quote(post, true);
                break;
            case POST_OPTION_INFO:
                showPostInfo(post);
                break;
            case POST_OPTION_LINKS:
                if (post.linkables.size() > 0) {
                    threadPresenterCallback.showPostLinkables(post.linkables);
                }
                break;
            case POST_OPTION_COPY_TEXT:
                threadPresenterCallback.clipboardPost(post);
                break;
            case POST_OPTION_REPORT:
                threadPresenterCallback.openReportView(post);
                break;
            case POST_OPTION_HIGHLIGHT_ID:
                threadPresenterCallback.highlightPostId(post.id);
                break;
            case POST_OPTION_HIGHLIGHT_TRIPCODE:
                threadPresenterCallback.highlightPostTripcode(post.tripcode);
                break;
            case POST_OPTION_FILTER_TRIPCODE:
                threadPresenterCallback.filterPostTripcode(post.tripcode);
                break;
            case POST_OPTION_DELETE:
                requestDeletePost(post);
                break;
            case POST_OPTION_SAVE:
                SavedReply savedReply = new SavedReply(post.board, post.no, "");
                databaseManager.runTask(databaseManager.getDatabaseSavedReplyManager().saveReply(savedReply));
                break;
            case POST_OPTION_PIN:
                Loadable pinLoadable = databaseManager.getDatabaseLoadableManager().get(Loadable.forThread(post.board, post.no));
                watchManager.createPin(pinLoadable, post);
                break;
            case POST_OPTION_OPEN_BROWSER:
                AndroidUtils.openLink(
                        post.isOP ?
                                ChanUrls.getThreadUrlDesktop(post.board, post.no) :
                                ChanUrls.getThreadUrlDesktop(post.board, loadable.no, post.no)
                );
                break;
            case POST_OPTION_SHARE:
                AndroidUtils.shareLink(
                        post.isOP ?
                                ChanUrls.getThreadUrlDesktop(post.board, post.no) :
                                ChanUrls.getThreadUrlDesktop(post.board, loadable.no, post.no)
                );
                break;
            case POST_OPTION_HIDE:
                threadPresenterCallback.hideThread(post);
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

            if (boardManager.getBoardExists(link.board)) {
                Loadable thread = databaseManager.getDatabaseLoadableManager().get(Loadable.forThread(link.board, link.threadId));
                thread.markedNo = link.postId;

                threadPresenterCallback.showThread(thread);
            }
        }
    }

    @Override
    public void onPostNoClicked(Post post) {
        threadPresenterCallback.quote(post, false);
    }

    @Override
    public void onShowPostReplies(Post post) {
        List<Post> posts = new ArrayList<>();
        synchronized (post.repliesFrom) {
            for (int no : post.repliesFrom) {
                Post replyPost = findPostById(no);
                if (replyPost != null) {
                    posts.add(replyPost);
                }
            }
        }
        if (posts.size() > 0) {
            threadPresenterCallback.showPostsPopup(post, posts);
        }
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
        return loadable.isThreadMode() && ChanSettings.autoRefreshThread.get() &&
                Chan.getInstance().getApplicationInForeground() && chanLoader.getThread() != null &&
                !chanLoader.getThread().closed && !chanLoader.getThread().archived;
    }

    @Override
    public ChanThread getChanThread() {
        return chanLoader.getThread();
    }

    @Override
    public void onListStatusClicked() {
        chanLoader.requestMoreDataAndResetTimer();
    }

    @Override
    public void showThread(Loadable loadable) {
        threadPresenterCallback.showThread(loadable);
    }

    @Override
    public void requestNewPostLoad() {
        if (loadable != null && loadable.isThreadMode()) {
            chanLoader.requestMoreDataAndResetTimer();
        }
    }

    public void deletePostConfirmed(Post post, boolean onlyImageDelete) {
        threadPresenterCallback.showDeleting();

        SavedReply reply = databaseManager.runTaskSync(
                databaseManager.getDatabaseSavedReplyManager().findSavedReply(post.board, post.no)
        );
        if (reply != null) {
            replyManager.makeHttpCall(new DeleteHttpCall(reply, onlyImageDelete), new ReplyManager.HttpCallback<DeleteHttpCall>() {
                @Override
                public void onHttpSuccess(DeleteHttpCall httpPost) {
                    String message;
                    if (httpPost.deleted) {
                        message = getString(R.string.delete_success);
                    } else if (!TextUtils.isEmpty(httpPost.errorMessage)) {
                        message = httpPost.errorMessage;
                    } else {
                        message = getString(R.string.delete_error);
                    }
                    threadPresenterCallback.hideDeleting(message);
                }

                @Override
                public void onHttpFail(DeleteHttpCall httpPost) {
                    threadPresenterCallback.hideDeleting(getString(R.string.delete_error));
                }
            });
        }
    }

    private void requestDeletePost(Post post) {
        SavedReply reply = databaseManager.runTaskSync(
                databaseManager.getDatabaseSavedReplyManager().findSavedReply(post.board, post.no)
        );
        if (reply != null) {
            threadPresenterCallback.confirmPostDelete(post);
        }
    }

    private void showPostInfo(Post post) {
        String text = "";

        if (post.hasImage) {
            text += "Filename: " + post.filename + "." + post.ext + " \nDimensions: " + post.imageWidth + "x"
                    + post.imageHeight + "\nSize: " + AndroidUtils.getReadableFileSize(post.fileSize, false);

            if (post.spoiler) {
                text += "\nSpoilered";
            }

            text += "\n\n";
        }

        text += "Date: " + post.date;

        if (!TextUtils.isEmpty(post.id)) {
            text += "\nId: " + post.id;
        }

        if (!TextUtils.isEmpty(post.tripcode)) {
            text += "\nTripcode: " + post.tripcode;
        }

        if (!TextUtils.isEmpty(post.countryName)) {
            text += "\nCountry: " + post.country + ", " + post.countryName;
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

    private void showPosts() {
        threadPresenterCallback.showPosts(chanLoader.getThread(), new PostsFilter(order, searchQuery));
    }

    private void addHistory() {
        if (!historyAdded && ChanSettings.historyEnabled.get() && loadable.isThreadMode()) {
            historyAdded = true;
            History history = new History();
            history.loadable = loadable;
            history.thumbnailUrl = chanLoader.getThread().op.thumbnailUrl;
            databaseManager.getDatabaseHistoryManager().add(history);
        }
    }

    public interface ThreadPresenterCallback {
        void showPosts(ChanThread thread, PostsFilter filter);

        void postClicked(Post post);

        void showError(ChanLoaderException error);

        void showLoading();

        void showPostInfo(String info);

        void showPostLinkables(List<PostLinkable> linkables);

        void clipboardPost(Post post);

        void showThread(Loadable threadLoadable);

        void openLink(String link);

        void openReportView(Post post);

        void showPostsPopup(Post forPost, List<Post> posts);

        void hidePostsPopup();

        List<Post> getDisplayingPosts();

        int[] getCurrentPosition();

        void showImages(List<PostImage> images, int index, Loadable loadable, ThumbnailView thumbnail);

        void addImages(List<PostImage> images, Loadable loadable);

        void showAlbum(List<PostImage> images, int index);

        void scrollTo(int displayPosition, boolean smooth);

        void highlightPost(Post post);

        void highlightPostId(String id);

        void highlightPostTripcode(String tripcode);

        void filterPostTripcode(String tripcode);

        void selectPost(int post);

        void showSearch(boolean show);

        void setSearchStatus(String query, boolean setEmptyText, boolean hideKeyboard);

        void quote(Post post, boolean withText);

        void confirmPostDelete(Post post);

        void showDeleting();

        void hideDeleting(String message);

        void hideThread(Post post);

        void showNewPostsNotification(boolean show, int more);
    }
}
