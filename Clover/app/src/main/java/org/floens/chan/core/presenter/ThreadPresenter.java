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
import org.floens.chan.core.database.DatabaseManager;
import org.floens.chan.core.exception.ChanLoaderException;
import org.floens.chan.core.manager.WatchManager;
import org.floens.chan.core.model.ChanThread;
import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.PostImage;
import org.floens.chan.core.model.PostLinkable;
import org.floens.chan.core.model.orm.Board;
import org.floens.chan.core.model.orm.History;
import org.floens.chan.core.model.orm.Loadable;
import org.floens.chan.core.model.orm.Pin;
import org.floens.chan.core.model.orm.SavedReply;
import org.floens.chan.core.pool.ChanLoaderFactory;
import org.floens.chan.core.manager.PageRequestManager;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.core.site.Page;
import org.floens.chan.core.site.Site;
import org.floens.chan.core.site.SiteActions;
import org.floens.chan.core.site.http.DeleteRequest;
import org.floens.chan.core.site.http.DeleteResponse;
import org.floens.chan.core.site.http.HttpCall;
import org.floens.chan.core.site.loader.ChanThreadLoader;
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
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import static org.floens.chan.utils.AndroidUtils.getString;

public class ThreadPresenter implements ChanThreadLoader.ChanLoaderCallback, PostAdapter.PostAdapterCallback, PostCellInterface.PostCellCallback, ThreadStatusCell.Callback, ThreadListLayout.ThreadListLayoutPresenterCallback {
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
    private static final int POST_OPTION_EXTRA = 15;

    private ThreadPresenterCallback threadPresenterCallback;
    private WatchManager watchManager;
    private DatabaseManager databaseManager;
    private ChanLoaderFactory chanLoaderFactory;
    private PageRequestManager pageRequestManager;

    private Loadable loadable;
    private ChanThreadLoader chanLoader;
    private boolean searchOpen = false;
    private String searchQuery;
    private PostsFilter.Order order = PostsFilter.Order.BUMP;
    private boolean historyAdded = false;

    @Inject
    public ThreadPresenter(WatchManager watchManager,
                           DatabaseManager databaseManager,
                           ChanLoaderFactory chanLoaderFactory,
                           PageRequestManager pageRequestManager) {
        this.watchManager = watchManager;
        this.databaseManager = databaseManager;
        this.chanLoaderFactory = chanLoaderFactory;
        this.pageRequestManager = pageRequestManager;
    }

    public void create(ThreadPresenterCallback threadPresenterCallback) {
        this.threadPresenterCallback = threadPresenterCallback;
    }

    public void showNoContent() {
        threadPresenterCallback.showEmpty();
    }

    public void bindLoadable(Loadable loadable) {
        if (!loadable.equals(this.loadable)) {
            if (chanLoader != null) {
                unbindLoadable();
            }

            Pin pin = watchManager.findPinByLoadable(loadable);
            // TODO this isn't true anymore, because all loadables come from one location.
            if (pin != null) {
                // Use the loadable from the pin.
                // This way we can store the list position in the pin loadable,
                // and not in a separate loadable instance.
                loadable = pin.loadable;
            }
            this.loadable = loadable;

            chanLoader = chanLoaderFactory.obtain(loadable, this);

            threadPresenterCallback.showLoading();
        }
    }

    public void unbindLoadable() {
        if (chanLoader != null) {
            chanLoader.clearTimer();
            chanLoaderFactory.release(chanLoader, this);
            chanLoader = null;
            loadable = null;
            historyAdded = false;

            threadPresenterCallback.showNewPostsNotification(false, -1);
            threadPresenterCallback.showLoading();
        }
    }

    public boolean isBound() {
        return chanLoader != null;
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
            if (!item.images.isEmpty()) {
                images.addAll(item.images);
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
     * ChanThreadLoader callbacks
     */
    @Override
    public void onChanLoaderData(ChanThread result) {
        if (isWatching()) {
            chanLoader.setTimer();
        }

        showPosts();

        if (loadable.isThreadMode()) {
            int lastLoaded = loadable.lastLoaded;
            List<Post> posts = result.posts;
            int more = 0;
            if (lastLoaded > 0) {
                for (int i = 0; i < posts.size(); i++) {
                    Post post = posts.get(i);
                    if (post.no == lastLoaded) {
                        more = posts.size() - i - 1;
                        break;
                    }
                }
            }
            loadable.setLastLoaded(posts.get(posts.size() - 1).no);

            if (more > 0) {
                threadPresenterCallback.showNewPostsNotification(true, more);
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

    public void onNewPostsViewClicked() {
        Post post = findPostById(loadable.lastViewed);
        if (post != null) {
            scrollToPost(post, true);
        } else {
            scrollTo(-1, true);
        }
    }

    public void scrollTo(int displayPosition, boolean smooth) {
        threadPresenterCallback.scrollTo(displayPosition, smooth);
    }

    public void scrollToImage(PostImage postImage, boolean smooth) {
        if (!searchOpen) {
            int position = -1;
            List<Post> posts = threadPresenterCallback.getDisplayingPosts();

            out:
            for (int i = 0; i < posts.size(); i++) {
                Post post = posts.get(i);
                if (!post.images.isEmpty()) {
                    for (int j = 0; j < post.images.size(); j++) {
                        if (post.images.get(j) == postImage) {
                            position = i;
                            break out;
                        }
                    }
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

            if (!post.images.isEmpty()) {
                for (int j = 0; j < post.images.size(); j++) {
                    if (post.images.get(j) == postImage) {
                        scrollToPost(post, false);
                        highlightPost(post);
                        return;
                    }
                }
            }
        }
    }

    /*
     * PostView callbacks
     */
    @Override
    public void onPostClicked(Post post) {
        if (loadable.isCatalogMode()) {
            Loadable threadLoadable = databaseManager.getDatabaseLoadableManager().get(Loadable.forThread(loadable.site, post.board, post.no));
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
    public void onThumbnailClicked(Post post, PostImage postImage, ThumbnailView thumbnail) {
        List<PostImage> images = new ArrayList<>();
        int index = -1;
        List<Post> posts = threadPresenterCallback.getDisplayingPosts();
        for (int i = 0; i < posts.size(); i++) {
            Post item = posts.get(i);

            if (!item.images.isEmpty()) {
                for (int j = 0; j < item.images.size(); j++) {
                    PostImage image = item.images.get(j);
                    images.add(image);
                    if (image.equalUrl(postImage)) {
                        index = images.size() - 1;
                    }
                }
            }
        }

        threadPresenterCallback.showImages(images, index, chanLoader.getLoadable(), thumbnail);
    }

    @Override
    public Object onPopulatePostOptions(Post post, List<FloatingMenuItem> menu,
                                        List<FloatingMenuItem> extraMenu) {
        if (!loadable.isThreadMode()) {
            menu.add(new FloatingMenuItem(POST_OPTION_PIN, R.string.action_pin));
        } else {
            menu.add(new FloatingMenuItem(POST_OPTION_QUOTE, R.string.post_quote));
            menu.add(new FloatingMenuItem(POST_OPTION_QUOTE_TEXT, R.string.post_quote_text));
        }

        if (!loadable.isThreadMode()) {
            menu.add(new FloatingMenuItem(POST_OPTION_HIDE, R.string.post_hide));
        }

        if (loadable.getSite().feature(Site.Feature.POST_REPORT)) {
            menu.add(new FloatingMenuItem(POST_OPTION_REPORT, R.string.post_report));
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

        if (loadable.site.feature(Site.Feature.POST_DELETE) &&
                databaseManager.getDatabaseSavedReplyManager().isSaved(post.board, post.no)) {
            menu.add(new FloatingMenuItem(POST_OPTION_DELETE, R.string.post_delete));
        }

        menu.add(new FloatingMenuItem(POST_OPTION_EXTRA, R.string.post_more));

        extraMenu.add(new FloatingMenuItem(POST_OPTION_INFO, R.string.post_info));
        extraMenu.add(new FloatingMenuItem(POST_OPTION_LINKS, R.string.post_show_links));
        extraMenu.add(new FloatingMenuItem(POST_OPTION_OPEN_BROWSER, R.string.action_open_browser));
        extraMenu.add(new FloatingMenuItem(POST_OPTION_SHARE, R.string.post_share));
        extraMenu.add(new FloatingMenuItem(POST_OPTION_COPY_TEXT, R.string.post_copy_text));

        if (ChanSettings.developer.get()) {
            extraMenu.add(new FloatingMenuItem(POST_OPTION_SAVE, "Save"));
        }

        return POST_OPTION_EXTRA;
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
                    threadPresenterCallback.showPostLinkables(post);
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
                SavedReply savedReply = SavedReply.fromSiteBoardNoPassword(
                        post.board.site, post.board, post.no, "");
                databaseManager.runTaskAsync(databaseManager.getDatabaseSavedReplyManager().saveReply(savedReply));
                break;
            case POST_OPTION_PIN:
                Loadable pinLoadable = databaseManager.getDatabaseLoadableManager().get(Loadable.forThread(loadable.site, post.board, post.no));
                watchManager.createPin(pinLoadable, post);
                break;
            case POST_OPTION_OPEN_BROWSER: {
                String url = loadable.site.resolvable().desktopUrl(loadable, post.isOP ? null : post);
                AndroidUtils.openLink(url);
                break;
            }
            case POST_OPTION_SHARE: {
                String url = loadable.site.resolvable().desktopUrl(loadable, post.isOP ? null : post);
                AndroidUtils.shareLink(url);
                break;
            }
            case POST_OPTION_HIDE:
                threadPresenterCallback.hideThread(post);
        }
    }

    @Override
    public void onPostLinkableClicked(Post post, PostLinkable linkable) {
        if (linkable.type == PostLinkable.Type.QUOTE) {
            Post linked = findPostById((int) linkable.value);
            if (linked != null) {
                threadPresenterCallback.showPostsPopup(post, Collections.singletonList(linked));
            }
        } else if (linkable.type == PostLinkable.Type.LINK) {
            threadPresenterCallback.openLink((String) linkable.value);
        } else if (linkable.type == PostLinkable.Type.THREAD) {
            PostLinkable.ThreadLink link = (PostLinkable.ThreadLink) linkable.value;

            Board board = loadable.site.board(link.board);
            if (board != null) {
                Loadable thread = databaseManager.getDatabaseLoadableManager().get(Loadable.forThread(board.site, board, link.threadId));
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
    public void onPostSelectionQuoted(Post post, CharSequence quoted) {
        threadPresenterCallback.quote(post, quoted);
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
        return chanLoader == null ? null : chanLoader.getThread();
    }

    public Page getPage(Post op) {
        return pageRequestManager.getPage(op);
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

        SavedReply reply = databaseManager.runTask(
                databaseManager.getDatabaseSavedReplyManager().findSavedReply(post.board, post.no)
        );
        if (reply != null) {
            Site site = loadable.getSite();
            site.actions().delete(new DeleteRequest(post, reply, onlyImageDelete), new SiteActions.DeleteListener() {
                @Override
                public void onDeleteComplete(HttpCall httpPost, DeleteResponse deleteResponse) {
                    String message;
                    if (deleteResponse.deleted) {
                        message = getString(R.string.delete_success);
                    } else if (!TextUtils.isEmpty(deleteResponse.errorMessage)) {
                        message = deleteResponse.errorMessage;
                    } else {
                        message = getString(R.string.delete_error);
                    }
                    threadPresenterCallback.hideDeleting(message);
                }

                @Override
                public void onDeleteError(HttpCall httpCall) {
                    threadPresenterCallback.hideDeleting(getString(R.string.delete_error));
                }
            });
        }
    }

    private void requestDeletePost(Post post) {
        SavedReply reply = databaseManager.runTask(
                databaseManager.getDatabaseSavedReplyManager().findSavedReply(post.board, post.no)
        );
        if (reply != null) {
            threadPresenterCallback.confirmPostDelete(post);
        }
    }

    private void showPostInfo(Post post) {
        StringBuilder text = new StringBuilder();

        for (PostImage image : post.images) {
            text.append("Filename: ")
                    .append(image.filename).append(".").append(image.extension)
                    .append(" \nDimensions: ")
                    .append(image.imageWidth).append("x").append(image.imageHeight)
                    .append("\nSize: ")
                    .append(AndroidUtils.getReadableFileSize(image.size, false));

            if (image.spoiler) {
                text.append("\nSpoilered");
            }

            text.append("\n");
        }

        // TODO(multi-site) get this from the timestamp
//        text += "Date: " + post.date;

        if (!TextUtils.isEmpty(post.id)) {
            text.append("\nId: ").append(post.id);
        }

        if (!TextUtils.isEmpty(post.tripcode)) {
            text.append("\nTripcode: ").append(post.tripcode);
        }

        /*if (!TextUtils.isEmpty(post.countryName)) {
            text += "\nCountry: " + post.country + ", " + post.countryName;
        }*/

        if (!TextUtils.isEmpty(post.capcode)) {
            text.append("\nCapcode: ").append(post.capcode);
        }

        threadPresenterCallback.showPostInfo(text.toString());
    }

    private Post findPostById(int id) {
        ChanThread thread = chanLoader.getThread();
        if (thread != null) {
            for (Post post : thread.posts) {
                if (post.no == id) {
                    return post;
                }
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
            PostImage image = chanLoader.getThread().op.image();
            history.thumbnailUrl = image == null ? "" : image.getThumbnailUrl().toString();
            databaseManager.runTaskAsync(databaseManager.getDatabaseHistoryManager().addHistory(history));
        }
    }

    public interface ThreadPresenterCallback {
        void showPosts(ChanThread thread, PostsFilter filter);

        void postClicked(Post post);

        void showError(ChanLoaderException error);

        void showLoading();

        void showEmpty();

        void showPostInfo(String info);

        void showPostLinkables(Post post);

        void clipboardPost(Post post);

        void showThread(Loadable threadLoadable);

        void openLink(String link);

        void openReportView(Post post);

        void showPostsPopup(Post forPost, List<Post> posts);

        void hidePostsPopup();

        List<Post> getDisplayingPosts();

        int[] getCurrentPosition();

        void showImages(List<PostImage> images, int index, Loadable loadable, ThumbnailView thumbnail);

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

        void quote(Post post, CharSequence text);

        void confirmPostDelete(Post post);

        void showDeleting();

        void hideDeleting(String message);

        void hideThread(Post post);

        void showNewPostsNotification(boolean show, int more);
    }
}
