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

import com.android.volley.VolleyError;

import org.floens.chan.Chan;
import org.floens.chan.R;
import org.floens.chan.chan.ChanLoader;
import org.floens.chan.chan.ChanUrls;
import org.floens.chan.core.http.DeleteHttpCall;
import org.floens.chan.core.http.ReplyManager;
import org.floens.chan.core.manager.WatchManager;
import org.floens.chan.core.model.ChanThread;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Pin;
import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.PostImage;
import org.floens.chan.core.model.PostLinkable;
import org.floens.chan.core.model.SavedReply;
import org.floens.chan.core.net.LoaderPool;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.database.DatabaseManager;
import org.floens.chan.ui.adapter.PostAdapter;
import org.floens.chan.ui.cell.PostCellInterface;
import org.floens.chan.ui.cell.ThreadStatusCell;
import org.floens.chan.ui.helper.PostHelper;
import org.floens.chan.ui.layout.ThreadListLayout;
import org.floens.chan.ui.view.FloatingMenuItem;
import org.floens.chan.ui.view.ThumbnailView;
import org.floens.chan.utils.AndroidUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.floens.chan.utils.AndroidUtils.getString;

public class ThreadPresenter implements ChanLoader.ChanLoaderCallback, PostAdapter.PostAdapterCallback, PostCellInterface.PostCellCallback, ThreadStatusCell.Callback, ThreadListLayout.ThreadListLayoutCallback {
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

    private WatchManager watchManager;
    private DatabaseManager databaseManager;
    private ReplyManager replyManager;

    private ThreadPresenterCallback threadPresenterCallback;

    private Loadable loadable;
    private ChanLoader chanLoader;
    private boolean searchOpen = false;

    public ThreadPresenter(ThreadPresenterCallback threadPresenterCallback) {
        this.threadPresenterCallback = threadPresenterCallback;

        watchManager = Chan.getWatchManager();
        databaseManager = Chan.getDatabaseManager();
        replyManager = Chan.getReplyManager();
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
            Pin pin = watchManager.findPinByLoadable(loadable);
            if (pin == null) {
                Post op = chanLoader.getThread().op;
                watchManager.addPin(loadable, op);
            } else {
                watchManager.removePin(pin);
            }
        }
        return isPinned();
    }

    public boolean isPinned() {
        return watchManager.findPinByLoadable(loadable) != null;
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
        Pin pin = watchManager.findPinByLoadable(loadable);
        if (pin != null) {
            if (pin.archived != result.archived) {
                pin.archived = result.archived;
                watchManager.updatePin(pin);
            }
        }

        chanLoader.setAutoLoadMore(isWatching());
        threadPresenterCallback.showPosts(result);

        if (loadable.markedNo >= 0) {
            Post markedPost = findPostById(loadable.markedNo);
            if (markedPost != null) {
                highlightPost(markedPost);
                scrollToPost(markedPost, false);
            }
            loadable.markedNo = -1;
        }

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

        Pin pin = watchManager.findPinByLoadable(loadable);
        if (pin != null) {
            pin.onBottomPostViewed();
            watchManager.updatePin(pin);
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
            if (position >= 0) {
                scrollTo(position, smooth);
            }
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
        if (position >= 0) {
            scrollTo(position, smooth);
        }
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
            threadLoadable.title = PostHelper.getTitle(post, loadable);
            threadPresenterCallback.showThread(threadLoadable);
        } else {
            if (searchOpen) {
                threadPresenterCallback.filterList(null, null, true, false, true);
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
        for (int i = 0; i < chanLoader.getThread().posts.size(); i++) {
            Post item = chanLoader.getThread().posts.get(i);
            if (item.hasImage) {
                images.add(new PostImage(String.valueOf(item.tim), item.thumbnailUrl, item.imageUrl, item.filename, item.ext, item.imageWidth, item.imageHeight, item.spoiler));
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
        menu.add(new FloatingMenuItem(POST_OPTION_SHARE, R.string.post_share));
        menu.add(new FloatingMenuItem(POST_OPTION_COPY_TEXT, R.string.post_copy_text));
        menu.add(new FloatingMenuItem(POST_OPTION_REPORT, R.string.post_report));

        if (!TextUtils.isEmpty(post.id)) {
            menu.add(new FloatingMenuItem(POST_OPTION_HIGHLIGHT_ID, R.string.post_highlight_id));
        }

        if (databaseManager.isSavedReply(post.board, post.no)) {
            menu.add(new FloatingMenuItem(POST_OPTION_DELETE, R.string.delete));
        }

        if (ChanSettings.developer.get()) {
            menu.add(new FloatingMenuItem(POST_OPTION_SAVE, "Save"));
        }
    }

    public void onPostOptionClicked(Post post, Object id) {
        switch ((Integer) id) {
            case POST_OPTION_QUOTE:
                threadPresenterCallback.quote(post, false);
                break;
            case POST_OPTION_QUOTE_TEXT:
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
                threadPresenterCallback.openWebView("Report /" + post.board + "/" + post.no, ChanUrls.getReportUrl(post.board, post.no));
                break;
            case POST_OPTION_HIGHLIGHT_ID:
                threadPresenterCallback.highlightPostId(post.id);
                break;
            case POST_OPTION_DELETE:
                requestDeletePost(post);
                break;
            case POST_OPTION_SAVE:
                databaseManager.saveReply(new SavedReply(post.board, post.no, "foo"));
                break;
            case POST_OPTION_PIN:
                watchManager.addPin(post);
                break;
            case POST_OPTION_SHARE:
                AndroidUtils.shareLink(
                        post.isOP ?
                                ChanUrls.getThreadUrlDesktop(post.board, post.no) :
                                ChanUrls.getThreadUrlDesktop(post.board, loadable.no, post.no)
                );
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

    /*
     * ThreadStatusCell callbacks
     */
    @Override
    public long getTimeUntilLoadMore() {
        return chanLoader.getTimeUntilLoadMore();
    }

    @Override
    public boolean isWatching() {
        return loadable.isThreadMode() && ChanSettings.autoRefreshThread.get() && chanLoader.getThread() != null &&
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
        if (loadable.isThreadMode()) {
            chanLoader.requestMoreDataAndResetTimer();
        }
    }

    public void deletePostConfirmed(Post post, boolean onlyImageDelete) {
        threadPresenterCallback.showDeleting();

        SavedReply reply = databaseManager.getSavedReply(post.board, post.no);
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
        SavedReply reply = databaseManager.getSavedReply(post.board, post.no);
        if (reply != null) {
            threadPresenterCallback.confirmPostDelete(post);
        }
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

        void postClicked(Post post);

        void showError(VolleyError error);

        void showLoading();

        void showPostInfo(String info);

        void showPostLinkables(List<PostLinkable> linkables);

        void clipboardPost(Post post);

        void showThread(Loadable threadLoadable);

        void openLink(String link);

        void openWebView(String title, String link);

        void showPostsPopup(Post forPost, List<Post> posts);

        void showImages(List<PostImage> images, int index, Loadable loadable, ThumbnailView thumbnail);

        void scrollTo(int position, boolean smooth);

        void highlightPost(Post post);

        void highlightPostId(String id);

        void showSearch(boolean show);

        void filterList(String query, List<Post> filter, boolean clearFilter, boolean setEmptyText, boolean hideKeyboard);

        void quote(Post post, boolean withText);

        void confirmPostDelete(Post post);

        void showDeleting();

        void hideDeleting(String message);
    }
}
