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
package com.github.adamantcheese.chan.core.presenter;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.ui.cell.PostCellInterface.PostCellCallback.PostOptions.*;
import static com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast;
import static com.github.adamantcheese.chan.ui.widget.DefaultAlertDialog.getDefaultAlertBuilder;
import static com.github.adamantcheese.chan.utils.AndroidUtils.*;

import android.content.Context;
import android.text.TextUtils;
import android.util.MalformedJsonException;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.StartActivity;
import com.github.adamantcheese.chan.core.database.*;
import com.github.adamantcheese.chan.core.manager.*;
import com.github.adamantcheese.chan.core.model.*;
import com.github.adamantcheese.chan.core.model.orm.*;
import com.github.adamantcheese.chan.core.net.*;
import com.github.adamantcheese.chan.core.repository.PageRepository;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.settings.ChanSettings.PostViewMode;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.archives.ExternalSiteArchive;
import com.github.adamantcheese.chan.core.site.http.DeleteRequest;
import com.github.adamantcheese.chan.core.site.http.DeleteResponse;
import com.github.adamantcheese.chan.core.site.loader.ChanThreadLoader;
import com.github.adamantcheese.chan.core.site.parser.comment_action.linkdata.*;
import com.github.adamantcheese.chan.features.embedding.EmbeddingEngine;
import com.github.adamantcheese.chan.features.embedding.embedders.Embedder;
import com.github.adamantcheese.chan.ui.adapter.PostAdapter;
import com.github.adamantcheese.chan.ui.adapter.PostsFilter;
import com.github.adamantcheese.chan.ui.cell.PostCellInterface;
import com.github.adamantcheese.chan.ui.cell.ThreadStatusCell;
import com.github.adamantcheese.chan.ui.helper.PostHelper;
import com.github.adamantcheese.chan.ui.layout.ArchivesLayout;
import com.github.adamantcheese.chan.ui.layout.ThreadListLayout;
import com.github.adamantcheese.chan.ui.text.post_linkables.*;
import com.github.adamantcheese.chan.ui.view.FloatingMenu;
import com.github.adamantcheese.chan.ui.view.FloatingMenuItem;
import com.github.adamantcheese.chan.utils.*;
import com.github.adamantcheese.chan.utils.RecyclerUtils.RecyclerViewPosition;

import java.util.*;

import javax.inject.Inject;
import javax.net.ssl.SSLException;

import okhttp3.HttpUrl;

public class ThreadPresenter
        implements NetUtilsClasses.ResponseResult<ChanThread>, PostAdapter.PostAdapterCallback,
                   PostCellInterface.PostCellCallback, ThreadStatusCell.Callback,
                   ThreadListLayout.ThreadListLayoutPresenterCallback, ArchivesLayout.Callback,
                   ProgressResponseBody.ProgressListener {
    @Inject
    private WatchManager watchManager;

    @Inject
    private DatabaseLoadableManager databaseLoadableManager;

    @Inject
    private DatabaseSavedReplyManager databaseSavedReplyManager;

    @Inject
    private BoardManager boardManager;

    private final ThreadPresenterCallback threadPresenterCallback;
    private Loadable loadable;
    private ChanThreadLoader chanLoader;
    private boolean searchOpen;
    private String searchQuery;
    private PostsFilter.PostsOrder postsOrder = PostsFilter.PostsOrder.BUMP_ORDER;
    private final Context context;

    public ThreadPresenter(Context context, ThreadPresenterCallback callback) {
        this.context = context;
        threadPresenterCallback = callback;
        inject(this);
    }

    public void showNoContent() {
        threadPresenterCallback.showEmpty();
    }

    public synchronized void bindLoadable(Loadable loadable) {
        if (loadable != this.loadable) {
            unbindLoadable();

            this.loadable = loadable;

            loadable.lastLoadDate =
                    ChanSettings.showHistory.get() ? GregorianCalendar.getInstance().getTime() : loadable.lastLoadDate;
            DatabaseUtils.runTaskAsync(databaseLoadableManager.updateLoadable(loadable, false));

            chanLoader = ChanLoaderManager.obtain(loadable, this);
            chanLoader.addProgressListener(this);
            threadPresenterCallback.showLoading();

            if (chanLoader.getThread() == null) {
                chanLoader.requestFreshData();
            } else {
                refreshUI();
            }
        }
    }

    public synchronized void unbindLoadable() {
        if (isBound()) {
            chanLoader.clearTimer();
            chanLoader.removeProgressListener(this);
            ChanLoaderManager.release(chanLoader, this);
            chanLoader = null;
            loadable = null;

            threadPresenterCallback.showLoading();
        }
    }

    @Override
    public void onDownloadProgress(HttpUrl source, long bytesRead, long contentLength, boolean start, boolean done) {
        threadPresenterCallback.onDownloadProgress(source, bytesRead, contentLength, start, done);
    }

    public void updateDatabaseLoadable() {
        DatabaseUtils.runTaskAsync(databaseLoadableManager.updateLoadable(loadable, false));
    }

    public boolean isBound() {
        return chanLoader != null;
    }

    public void requestData() {
        BackgroundUtils.ensureMainThread();

        if (isBound()) {
            threadPresenterCallback.refreshUI();
            threadPresenterCallback.showLoading();
            chanLoader.requestFreshData();
        }
    }

    public void refreshUI() {
        onSuccess(chanLoader.getThread());
        chanLoader.requestAdditionalData();
    }

    public void onForegroundChanged(boolean foreground) {
        if (isBound()) {
            if (foreground && isWatching()) {
                chanLoader.requestAdditionalData();
                if (chanLoader.getThread() != null) {
                    // Show loading indicator in the status cell
                    showPosts();
                }
            } else {
                chanLoader.clearTimer();
            }
        }
    }

    public void onSearchVisibilityChanged(boolean visible) {
        searchOpen = visible;
        threadPresenterCallback.showSearch(visible);
        if (!visible) {
            searchQuery = null;
        }

        if (isBound() && chanLoader.getThread() != null && !visible) {
            showPosts();
        }
    }

    public void onSearchEntered(String entered) {
        searchQuery = entered;
        if (isBound() && chanLoader.getThread() != null) {
            showPosts();
            if (TextUtils.isEmpty(entered)) {
                threadPresenterCallback.setSearchStatus(null, true, false);
            } else {
                threadPresenterCallback.setSearchStatus(entered, false, false);
            }
        }
    }

    public void setOrder(PostsFilter.PostsOrder postsOrder) {
        if (this.postsOrder != postsOrder) {
            this.postsOrder = postsOrder;
            if (isBound() && chanLoader.getThread() != null) {
                scrollTo(0, false);
                showPosts();
            }
        }
    }

    public synchronized void showAlbum() {
        List<Post> posts = threadPresenterCallback.getDisplayingPosts();
        RecyclerViewPosition pos = threadPresenterCallback.getCurrentPosition();
        int displayPosition = pos.index;

        List<PostImage> images = new ArrayList<>();
        int index = 0;
        for (int i = 0; i < posts.size(); i++) {
            if (i == displayPosition) {
                index = images.size();
            }
            Post item = posts.get(i);
            for (PostImage image : item.images) {
                if (image.type != PostImage.Type.IFRAME) {
                    images.add(image);
                }
            }
        }

        threadPresenterCallback.showAlbum(images, index);
    }

    @Override
    public Loadable getLoadable() {
        return loadable;
    }

    @Override
    public String getSearchQuery() {
        return searchQuery;
    }

    /*
     * ChanThreadLoader callbacks
     */
    @Override
    public void onSuccess(ChanThread result) {
        BackgroundUtils.ensureMainThread();

        if (isBound()) {
            if (isWatching()) {
                chanLoader.setTimer();
            } else {
                chanLoader.clearTimer();
            }
        } else {
            return;
        }

        //allow for search refreshes inside the catalog
        if (result.loadable.isCatalogMode() && !TextUtils.isEmpty(searchQuery)) {
            onSearchEntered(searchQuery);
        } else {
            showPosts();
        }

        if (loadable.isThreadMode()) {
            List<Post> posts = result.getPosts();
            int postsCount = posts.size();

            // calculate how many new posts since last load
            int more = 0;
            for (int i = 0; i < postsCount; i++) {
                Post p = posts.get(i);
                if (p.no == loadable.lastLoaded) {
                    // end index minus last loaded index
                    more = (postsCount - 1) - i;
                    break;
                }
            }

            // update last loaded post
            loadable.lastLoaded = posts.get(postsCount - 1).no;

            // this loadable is fresh, for new post reasons set it to the last loaded
            if (loadable.lastViewed == -1) {
                loadable.lastViewed = loadable.lastLoaded;
            }

            if (loadable.no == result.loadable.no) {
                threadPresenterCallback.showNewPostsSnackbar(loadable, more);
            }
        }

        if (loadable.markedNo >= 0) {
            Post markedPost = PostUtils.findPostById(loadable.markedNo, chanLoader.getThread());
            if (markedPost != null) {
                highlightPostNo(markedPost.no);
                if (BackgroundUtils.isInForeground()) {
                    scrollToPost(markedPost, false);
                }
                if (StartActivity.loadedFromURL) {
                    BackgroundUtils.runOnMainThread(() -> scrollToPost(markedPost, false), 1000);
                    StartActivity.loadedFromURL = false;
                }
            }
            loadable.markedNo = -1;
        }

        updateDatabaseLoadable();

        threadPresenterCallback.updateSubtitle(result.summarize(false));
    }

    @Override
    public void onFailure(Exception error) {
        Logger.d(this, "onChanLoaderError()");

        //by default, a network error has occurred if the exception field is not null
        int errorMessageResId;
        if (error instanceof SSLException) {
            errorMessageResId = R.string.thread_load_failed_ssl;
        } else if (error instanceof NetUtilsClasses.HttpCodeException) {
            if (((NetUtilsClasses.HttpCodeException) error).isServerErrorNotFound()) {
                errorMessageResId = R.string.thread_load_failed_not_found;
            } else {
                errorMessageResId = R.string.thread_load_failed_server;
            }
        } else if (error instanceof MalformedJsonException) {
            errorMessageResId = R.string.thread_load_failed_parsing;
        } else {
            errorMessageResId = R.string.thread_load_failed_network;
        }

        threadPresenterCallback.showError(errorMessageResId);
    }

    /*
     * PostAdapter callbacks
     */
    @Override
    public void onListScrolledToBottom() {
        if (!isBound() || loadable.isCatalogMode()) return;
        if (chanLoader.getThread() != null && !chanLoader.getThread().getPosts().isEmpty()) {
            List<Post> posts = chanLoader.getThread().getPosts();
            loadable.lastViewed = posts.get(posts.size() - 1).no;
        }

        Pin pin = watchManager.findPinByLoadableId(loadable.id);
        if (pin != null) {
            watchManager.onBottomPostViewed(pin);
        }

        threadPresenterCallback.showNewPostsSnackbar(loadable, -1);

        // Update the last seen indicator
        showPosts();
    }

    public void onNewPostsViewClicked() {
        if (!isBound()) return;
        Post post = PostUtils.findPostById(loadable.lastViewed, chanLoader.getThread());
        int position = -1;
        if (post != null) {
            List<Post> posts = threadPresenterCallback.getDisplayingPosts();
            for (int i = 0; i < posts.size(); i++) {
                Post needle = posts.get(i);
                if (post.no == needle.no) {
                    position = i;
                    break;
                }
            }
        }
        // scroll to post after last viewed
        threadPresenterCallback.smoothScrollNewPosts(position + 1);
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
                for (PostImage image : post.images) {
                    if (image == postImage) {
                        position = i;
                        break out;
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

    public void highlightPostNo(int postNo) {
        threadPresenterCallback.highlightPostNo(postNo);
    }

    public void selectPostImage(PostImage postImage) {
        List<Post> posts = threadPresenterCallback.getDisplayingPosts();
        for (Post post : posts) {
            for (PostImage image : post.images) {
                if (image == postImage) {
                    scrollToPost(post, false);
                    highlightPostNo(post.no);
                    return;
                }
            }
        }
    }

    public Post getPostFromPostImage(PostImage postImage) {
        List<Post> posts = threadPresenterCallback.getDisplayingPosts();
        for (Post post : posts) {
            for (PostImage image : post.images) {
                if (image == postImage) {
                    return post;
                }
            }
        }
        return null;
    }

    /*
     * PostView callbacks
     */
    @Override
    public void onPostClicked(Post post) {
        if (!isBound()) return;
        if (loadable.isCatalogMode()) {
            threadPresenterCallback.showThread(Loadable.forThread(post.board,
                    post.no,
                    PostHelper.getTitle(post, loadable)
            ));
        }
    }

    @Override
    public void onPostDoubleClicked(Post post) {
        if (isBound() && loadable.isThreadMode()) {
            if (searchOpen) {
                searchQuery = null;
                showPosts();
                threadPresenterCallback.setSearchStatus(null, false, true);
                threadPresenterCallback.showSearch(false);
                highlightPostNo(post.no);
                scrollToPost(post, false);
            } else {
                threadPresenterCallback.postClicked(post);
            }
        }
    }

    @Override
    public void onThumbnailClicked(PostImage postImage, ImageView thumbnail) {
        if (!isBound()) return;
        List<PostImage> images = new ArrayList<>();
        int index = -1;
        List<Post> posts = threadPresenterCallback.getDisplayingPosts();
        PostViewMode viewMode = threadPresenterCallback.getPostViewMode();
        for (Post post : posts) {
            // for card mode, only add the displayed image
            // otherwise add all images
            if (viewMode != PostViewMode.LIST) {
                if (post.image() == null) continue;
                if (!post.deleted || post.image().isInlined || NetUtils.isCached(post.image().imageUrl)) {
                    //deleted posts always have 404'd images, but let it through if the file exists in cache
                    images.add(post.image());
                    if (post.image().equals(postImage)) {
                        index = images.size() - 1;
                    }
                }
            } else {
                for (PostImage image : post.images) {
                    if (!post.deleted || image.isInlined || NetUtils.isCached(image.imageUrl)) {
                        //deleted posts always have 404'd images, but let it through if the file exists in cache
                        images.add(image);
                        if (image.equals(postImage)) {
                            index = images.size() - 1;
                        }
                    }
                }
            }
        }

        if (!images.isEmpty()) {
            threadPresenterCallback.showImages(images, index, loadable, thumbnail);
        }
    }

    @Override
    public Object onPopulatePostOptions(
            Post post, List<FloatingMenuItem<PostOptions>> menu, List<FloatingMenuItem<PostOptions>> extraMenu
    ) {
        if (!isBound()) return null;

        boolean isSaved = databaseSavedReplyManager.isSaved(post.board, post.no);

        if (loadable.isCatalogMode()
                && watchManager.getPinByLoadable(Loadable.forThread(post.board,
                post.no,
                PostHelper.getTitle(post, loadable),
                false
        )) == null
                && !(loadable.site instanceof ExternalSiteArchive)) {
            menu.add(new FloatingMenuItem<>(POST_OPTION_PIN, R.string.action_pin));
        }

        if (loadable.site.siteFeature(Site.SiteFeature.POSTING) && !loadable.isCatalogMode()) {
            menu.add(new FloatingMenuItem<>(POST_OPTION_QUOTE, R.string.post_quote));
            menu.add(new FloatingMenuItem<>(POST_OPTION_QUOTE_TEXT, R.string.post_quote_text));
        }

        if (loadable.site.siteFeature(Site.SiteFeature.POST_REPORT)) {
            menu.add(new FloatingMenuItem<>(POST_OPTION_REPORT, R.string.post_report));
        }

        if (!(loadable.site instanceof ExternalSiteArchive)) {
            if ((loadable.isCatalogMode() || (loadable.isThreadMode() && !post.isOP))) {
                if (!post.filterStub) {
                    menu.add(new FloatingMenuItem<>(POST_OPTION_HIDE, R.string.post_hide));
                }
                menu.add(new FloatingMenuItem<>(POST_OPTION_REMOVE, R.string.post_remove));
            }
        }

        if (loadable.isThreadMode()) {
            if (!TextUtils.isEmpty(post.id)) {
                menu.add(new FloatingMenuItem<>(POST_OPTION_HIGHLIGHT_ID, R.string.post_highlight_id));
            }

            if (!TextUtils.isEmpty(post.tripcode)) {
                menu.add(new FloatingMenuItem<>(POST_OPTION_HIGHLIGHT_TRIPCODE, R.string.post_highlight_tripcode));
            }
        }

        menu.add(new FloatingMenuItem<>(POST_OPTION_COPY, R.string.post_copy_menu));

        if (loadable.site.siteFeature(Site.SiteFeature.POST_DELETE) && isSaved) {
            menu.add(new FloatingMenuItem<>(POST_OPTION_DELETE, R.string.post_delete));
        }

        if (ChanSettings.accessibleInfo.get()) {
            menu.add(new FloatingMenuItem<>(POST_OPTION_INFO, R.string.post_info));
        } else {
            extraMenu.add(new FloatingMenuItem<>(POST_OPTION_INFO, R.string.post_info));
        }

        menu.add(new FloatingMenuItem<>(POST_OPTION_EXTRA, R.string.post_more));

        extraMenu.add(new FloatingMenuItem<>(POST_OPTION_OPEN_BROWSER, R.string.action_open_browser));
        extraMenu.add(new FloatingMenuItem<>(POST_OPTION_SHARE, R.string.post_share));

        //if the filter menu only has a single option we place just that option in the root menu
        //in some cases a post will have nothing in it to filter (for example a post with no text and an image
        //that is removed by a filter), in such cases there is no filter menu option.
        List<FloatingMenuItem<PostOptions>> filterMenu = populateFilterMenuOptions(post);
        if (filterMenu.size() > 1) {
            extraMenu.add(new FloatingMenuItem<>(POST_OPTION_FILTER, R.string.post_filter));
        } else if (filterMenu.size() == 1) {
            FloatingMenuItem<PostOptions> menuItem = filterMenu.remove(0);
            extraMenu.add(new FloatingMenuItem<>(menuItem.getId(), "Filter " + menuItem.getText().toLowerCase()));
        }

        if (!(loadable.site instanceof ExternalSiteArchive)) {
            extraMenu.add(new FloatingMenuItem<>(isSaved ? POST_OPTION_UNSAVE : POST_OPTION_SAVE,
                    isSaved ? R.string.unmark_as_my_post : R.string.mark_as_my_post
            ));
        }

        return POST_OPTION_EXTRA;
    }

    public void onPostOptionClicked(View anchor, Post post, PostOptions id, boolean inPopup) {
        switch (id) {
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
            case POST_OPTION_COPY:
                showSubMenuOptions(anchor, post, inPopup, populateCopyMenuOptions(post));
                break;
            case POST_OPTION_COPY_POST_LINK:
                setClipboardContent("Post link", String.format(Locale.ENGLISH, ">>%d", post.no));
                showToast(context, R.string.post_link_copied);
                break;
            case POST_OPTION_COPY_CROSS_BOARD_LINK:
                setClipboardContent("Cross-board link",
                        String.format(Locale.ENGLISH, ">>>/%s/%d", post.boardCode, post.no)
                );
                showToast(context, R.string.post_cross_board_link_copied);
                break;
            case POST_OPTION_COPY_POST_URL:
                setClipboardContent("Post URL", loadable.desktopUrl(post));
                showToast(context, R.string.post_url_copied);
                break;
            case POST_OPTION_COPY_IMG_URL:
                setClipboardContent("Image URL", post.image().imageUrl.toString());
                showToast(context, R.string.image_url_copied_to_clipboard);
                break;
            case POST_OPTION_COPY_POST_TEXT:
                setClipboardContent("Post text", post.comment.toString());
                showToast(context, R.string.post_text_copied);
                break;
            case POST_OPTION_REPORT:
                if (inPopup) {
                    threadPresenterCallback.hidePostsPopup();
                }
                threadPresenterCallback.openReportView(post);
                break;
            case POST_OPTION_HIGHLIGHT_ID:
                threadPresenterCallback.highlightPostId(post.id);
                break;
            case POST_OPTION_HIGHLIGHT_TRIPCODE:
                threadPresenterCallback.highlightPostTripcode(post.tripcode);
                break;
            case POST_OPTION_FILTER:
                showSubMenuOptions(anchor, post, inPopup, populateFilterMenuOptions(post));
                break;
            case POST_OPTION_FILTER_SUBJECT:
                threadPresenterCallback.filterPostSubject(post.subject);
                break;
            case POST_OPTION_FILTER_COMMENT:
                threadPresenterCallback.filterPostComment(post.comment);
                break;
            case POST_OPTION_FILTER_NAME:
                threadPresenterCallback.filterPostName(post.name);
                break;
            case POST_OPTION_FILTER_FILENAME:
                threadPresenterCallback.filterPostFilename(post);
                break;
            case POST_OPTION_FILTER_FLAG_CODE:
                threadPresenterCallback.filterPostFlagCode(post);
                break;
            case POST_OPTION_FILTER_ID:
                threadPresenterCallback.filterPostID(post.id);
                break;
            case POST_OPTION_FILTER_TRIPCODE:
                threadPresenterCallback.filterPostTripcode(post.tripcode);
                break;
            case POST_OPTION_FILTER_IMAGE_HASH:
                threadPresenterCallback.filterPostImageHash(post);
                break;
            case POST_OPTION_DELETE:
                requestDeletePost(post);
                break;
            case POST_OPTION_SAVE:
                if (!isBound()) break;
                for (Post p : getMatchingIds(post)) {
                    SavedReply saved = SavedReply.fromBoardNoPassword(p.board, p.no, "");
                    DatabaseUtils.runTask(databaseSavedReplyManager.saveReply(saved));
                    Pin watchedPin = watchManager.getPinByLoadable(loadable);
                    if (watchedPin != null) {
                        synchronized (this) {
                            watchedPin.quoteLastCount += p.repliesFrom.size();
                        }
                    }
                }
                //force reload for reply highlighting
                requestData();
                break;
            case POST_OPTION_UNSAVE:
                if (!isBound()) break;
                for (Post p : getMatchingIds(post)) {
                    SavedReply saved = databaseSavedReplyManager.getSavedReply(p.board, p.no);
                    if (saved != null) {
                        //unsave
                        DatabaseUtils.runTask(databaseSavedReplyManager.unsaveReply(saved));
                        Pin watchedPin = watchManager.getPinByLoadable(loadable);
                        if (watchedPin != null) {
                            synchronized (this) {
                                watchedPin.quoteLastCount -= p.repliesFrom.size();
                            }
                        }
                    } else {
                        Logger.w(this, "SavedReply null, can't unsave");
                    }
                }
                //force reload for reply highlighting
                requestData();
                break;
            case POST_OPTION_PIN:
                Loadable threadPin = Loadable.forThread(post.board, post.no, PostHelper.getTitle(post, loadable));
                threadPin.thumbnailUrl = post.image() == null ? null : post.image().getThumbnailUrl();
                watchManager.createPin(threadPin);
                break;
            case POST_OPTION_OPEN_BROWSER:
                if (isBound()) {
                    openLink(loadable.desktopUrl(post));
                }
                break;
            case POST_OPTION_SHARE:
                if (isBound()) {
                    shareLink(loadable.desktopUrl(post));
                }
                break;
            case POST_OPTION_REMOVE:
            case POST_OPTION_HIDE:
                if (chanLoader == null || chanLoader.getThread() == null) {
                    break;
                }

                boolean hide = id == POST_OPTION_HIDE;

                if (chanLoader.getLoadable().mode == Loadable.Mode.CATALOG) {
                    threadPresenterCallback.hideThread(post, hide);
                } else {
                    if (post.repliesFrom.isEmpty()) {
                        // no replies to this post so no point in showing the dialog
                        hideOrRemovePosts(hide, false, post);
                    } else {
                        // show a dialog to the user with options to hide/remove the whole chain of posts
                        threadPresenterCallback.showHideOrRemoveWholeChainDialog(hide,
                                post,
                                chanLoader.getThread().getOp().no
                        );
                    }
                }
                break;
        }
    }

    private Set<Post> getMatchingIds(Post post) {
        Set<Post> matching = new HashSet<>(1);
        if (TextUtils.isEmpty(post.id)) {
            //just this post
            matching.add(post);
        } else {
            //match all post IDs
            for (Post p : getChanThread().getPosts()) {
                if (!TextUtils.isEmpty(p.id) && p.id.equals(post.id)) {
                    matching.add(p);
                }
            }
        }
        return matching;
    }

    private void showSubMenuOptions(
            View anchor, Post post, Boolean inPopup, List<FloatingMenuItem<PostOptions>> options
    ) {
        FloatingMenu<PostOptions> menu = new FloatingMenu<>(context, anchor, options);
        menu.setCallback(new FloatingMenu.ClickCallback<PostOptions>() {
            @Override
            public void onFloatingMenuItemClicked(FloatingMenu<PostOptions> menu, FloatingMenuItem<PostOptions> item) {
                onPostOptionClicked(anchor, post, item.getId(), inPopup);
            }
        });
        menu.show();
    }

    @Override
    public void onPostLinkableClicked(Post post, PostLinkable<?> linkable) {
        if (!isBound()) return;
        if (linkable instanceof QuoteLinkable) {
            Post linked = PostUtils.findPostById((int) linkable.value, chanLoader.getThread());
            if (linked != null) {
                threadPresenterCallback.showPostsPopup(post, Collections.singletonList(linked));
            }
        } else if (linkable instanceof ParserLinkLinkable) {
            ParserLinkLinkable l = (ParserLinkLinkable) linkable;
            if (l.isJavascript()) {
                threadPresenterCallback.openLink(linkable, loadable.desktopUrl(post));
            } else {
                threadPresenterCallback.openLink(linkable, (String) linkable.value);
            }
        } else if (linkable instanceof EmbedderLinkLinkable) {
            threadPresenterCallback.openLink(linkable, (String) linkable.value);
        } else if (linkable instanceof ThreadLinkable) {
            ThreadLink link = (ThreadLink) linkable.value;

            Board board = loadable.site.board(link.boardCode);
            if (board != null) {
                Loadable thread =
                        Loadable.forThread(board, link.threadId, "", !(board.site instanceof ExternalSiteArchive));
                thread.markedNo = link.postId;

                threadPresenterCallback.showThread(thread);
            }
        } else if (linkable instanceof BoardLinkable) {
            Board b = boardManager.getBoard(loadable.site, (String) linkable.value);
            if (b == null) {
                showToast(context, R.string.site_uses_dynamic_boards);
            } else {
                threadPresenterCallback.showBoard(Loadable.forCatalog(b));
            }
        } else if (linkable instanceof SearchLinkable) {
            SearchLink search = (SearchLink) linkable.value;
            Board bd = boardManager.getBoard(loadable.site, search.board);
            if (bd == null) {
                showToast(context, R.string.site_uses_dynamic_boards);
            } else {
                threadPresenterCallback.showBoardAndSearch(Loadable.forCatalog(bd), search.search);
            }
        } else if (linkable instanceof ArchiveLinkable) {
            if (linkable.value instanceof ThreadLink) {
                ThreadLink opPostPair = (ThreadLink) linkable.value;
                Loadable constructed = Loadable.forThread(Board.fromSiteNameCode(loadable.site,
                                opPostPair.boardCode,
                                opPostPair.boardCode
                        ),
                        opPostPair.threadId,
                        "",
                        false
                );
                showArchives(constructed, opPostPair.postId);
            } else if (linkable.value instanceof ResolveLink) {
                ResolveLink toResolve = (ResolveLink) linkable.value;
                showToast(context, "Calling archive API, just a moment!");
                toResolve.resolve(toResolve, (threadLink) -> {
                    if (threadLink != null) {
                        Loadable constructed = Loadable.forThread(Board.fromSiteNameCode(toResolve.site,
                                        threadLink.boardCode,
                                        threadLink.boardCode
                                ),
                                threadLink.threadId,
                                "",
                                false
                        );
                        showArchives(constructed, threadLink.postId);
                    } else {
                        showToast(context, "Failed to resolve thread external post link!");
                    }
                });
            }
        }
    }

    @Override
    public void onPostNoClicked(Post post) {
        threadPresenterCallback.hidePostsPopup();
        threadPresenterCallback.quote(post, false);
    }

    @Override
    public void onPostSelectionQuoted(Post post, CharSequence quoted) {
        threadPresenterCallback.hidePostsPopup();
        threadPresenterCallback.quote(post, quoted);
    }

    @Override
    public void onShowPostReplies(Post post) {
        if (!isBound()) return;
        List<Post> posts = new ArrayList<>();
        for (int no : post.repliesFrom) {
            Post replyPost = PostUtils.findPostById(no, chanLoader.getThread());
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
        return isBound() ? chanLoader.getTimeUntilLoadMore() : 0L;
    }

    @Override
    public boolean isWatching() {
        //@formatter:off
        return ChanSettings.autoRefreshThread.get()
            && BackgroundUtils.isInForeground()
            && isBound()
            && loadable.isThreadMode()
            && !(loadable.site instanceof ExternalSiteArchive)
            && chanLoader.getThread() != null
            && !chanLoader.getThread().isClosed()
            && !chanLoader.getThread().isArchived();
        //@formatter:on
    }

    @Nullable
    @Override
    public ChanThread getChanThread() {
        return isBound() ? chanLoader.getThread() : null;
    }

    @Override
    public void onListStatusClicked() {
        if (!isBound() || getChanThread() == null) return;
        if (!getChanThread().isArchived()) {
            refreshUI();
        } else {
            showArchives(loadable, loadable.no);
        }
    }

    public void showArchives(Loadable op, int postNo) {
        final ArchivesLayout dialogView =
                (ArchivesLayout) LayoutInflater.from(context).inflate(R.layout.layout_archives, null);
        boolean hasContents = dialogView.setLoadable(op);
        dialogView.setPostNo(postNo);
        dialogView.setCallback(this);

        if (loadable.site instanceof ExternalSiteArchive) {
            // skip the archive picker, re-use the same archive we're already in
            openArchive((ExternalSiteArchive) loadable.site, op, postNo);
        } else if (hasContents) {
            AlertDialog dialog = getDefaultAlertBuilder(context)
                    .setView(dialogView)
                    .setTitle(R.string.thread_view_external_archive)
                    .create();
            dialog.setCanceledOnTouchOutside(true);
            dialogView.attachToDialog(dialog);
            dialog.show();
        } else {
            showToast(context, "No archives for this board or site.");
        }
    }

    @Override
    public void showThread(Loadable loadable) {
        threadPresenterCallback.showThread(loadable);
    }

    @Override
    public void requestNewPostLoad() {
        if (isBound() && loadable.isThreadMode()) {
            refreshUI();
            PageRepository.forceUpdateForBoard(chanLoader.getLoadable().board);
        }
    }

    @Override
    public void onUnhidePostClick(Post post) {
        threadPresenterCallback.unhideOrUnremovePost(post);
    }

    private void requestDeletePost(Post post) {
        SavedReply reply = databaseSavedReplyManager.getSavedReply(post.board, post.no);
        if (reply != null) {
            final View view = LayoutInflater.from(context).inflate(R.layout.dialog_post_delete, null);
            CheckBox checkBox = view.findViewById(R.id.image_only);
            getDefaultAlertBuilder(context)
                    .setTitle(R.string.delete_confirm)
                    .setView(view)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.delete,
                            (dialog, which) -> deletePostConfirmed(post, checkBox.isChecked())
                    )
                    .show();
        }
    }

    private void deletePostConfirmed(Post post, boolean onlyImageDelete) {
        threadPresenterCallback.showDeleting();

        SavedReply reply = databaseSavedReplyManager.getSavedReply(post.board, post.no);
        if (reply != null) {
            post.board.site.actions().delete(new DeleteRequest(post, reply, onlyImageDelete),
                    new NetUtilsClasses.ResponseResult<DeleteResponse>() {
                        @Override
                        public void onSuccess(DeleteResponse deleteResponse) {
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
                        public void onFailure(Exception e) {
                            threadPresenterCallback.hideDeleting(getString(R.string.delete_error));
                        }
                    }
            );
        }
    }

    private void showPostInfo(Post post) {
        View fullView = LayoutInflater.from(context).inflate(R.layout.dialog_post_info, null);
        AlertDialog dialog =
                getDefaultAlertBuilder(context).setView(fullView).setPositiveButton(R.string.ok, null).create();
        dialog.setCanceledOnTouchOutside(true);
        TextView infoText = fullView.findViewById(R.id.post_info);
        LinearLayout linkableGroup = fullView.findViewById(R.id.linkable_group);
        ListView linkableList = fullView.findViewById(R.id.post_linkable_list);

        StringBuilder text = new StringBuilder();
        if (post.isOP && !TextUtils.isEmpty(post.subject)) {
            text.append("Subject: ").append(post.subject).append("\n");
        }

        if (!TextUtils.isEmpty(post.name) && !TextUtils.equals(post.name, "Anonymous")) {
            text.append("Name: ").append(post.name).append("\n");
        }

        if (!TextUtils.isEmpty(post.tripcode)) {
            text.append("Tripcode: ").append(post.tripcode).append("\n");
        }

        if (!TextUtils.isEmpty(post.capcode)) {
            text.append("Capcode: ").append(post.capcode).append("\n");
        }

        if (!TextUtils.isEmpty(post.id) && isBound() && chanLoader.getThread() != null) {
            text.append("Id: ").append(post.id).append("\n");
            int count = 0;
            try {
                for (Post p : chanLoader.getThread().getPosts()) {
                    if (p.id.equals(post.id)) count++;
                }
            } catch (Exception ignored) {
            }
            text.append("Post count: ").append(Integer.toString(count)).append("\n");
        }

        if (post.httpIcons != null && !post.httpIcons.isEmpty()) {
            for (PostHttpIcon icon : post.httpIcons) {
                text.append("Icon ").append(icon.code).append(" description: ").append(icon.description).append("\n");
            }
        }

        text.append("Posted: ").append(PostHelper.getLocalDate(post));

        for (PostImage image : post.images) {
            text.append("\n\n");
            PostUtils.generatePostImageSummaryAndSetTextViewWithUpdates(image, text, dialog, infoText);
        }
        infoText.setText(text);

        Set<String> added = new HashSet<>();
        List<CharSequence> keys = new ArrayList<>();
        List<PostLinkable<?>> linkables = Arrays.asList(post.getLinkables());
        for (PostLinkable<?> linkable : linkables) {
            //skip these linkables, they aren't useful to display
            if (linkable instanceof SpoilerLinkable || linkable instanceof FilterDebugLinkable) continue;
            CharSequence key =
                    post.comment.subSequence(post.comment.getSpanStart(linkable), post.comment.getSpanEnd(linkable));
            String value = linkable.value.toString();
            //need to trim off starting spaces for certain media links if embedded
            CharSequence trimmedUrl =
                    ((key.charAt(0) == ' ' && key.charAt(1) == ' ') ? key.subSequence(2, key.length()) : key);
            boolean speciallyProcessed = false;
            for (Embedder e : EmbeddingEngine.getDefaultEmbedders()) {
                if (e.shouldEmbed(value)) {
                    if (added.contains(trimmedUrl.toString())) continue;
                    keys.add(PostHelper.prependIcon(context, trimmedUrl, e.getIconBitmap(), (int) sp(16)));
                    added.add(trimmedUrl.toString());
                    speciallyProcessed = true;
                    break;
                }
            }
            if (!speciallyProcessed) {
                keys.add(key);
            }
        }

        linkableList.setAdapter(new ArrayAdapter<>(context, R.layout.simple_list_item_thin, keys));
        linkableList.setOnItemClickListener((parent, view, position, id1) -> {
            onPostLinkableClicked(post, linkables.get(position));
            dialog.dismiss();
        });
        linkableList.setOnItemLongClickListener((parent, view, position, id1) -> {
            setClipboardContent("Linkable URL", linkables.get(position).value.toString());
            showToast(context, R.string.linkable_copied_to_clipboard);
            return true;
        });
        if (linkables.size() <= 0) {
            linkableGroup.setVisibility(View.GONE);
        }
        dialog.show();
    }

    private void showPosts() {
        if (chanLoader != null && chanLoader.getThread() != null) {
            threadPresenterCallback.showPosts(chanLoader.getThread(), new PostsFilter(postsOrder, searchQuery));
        }
    }

    public void hideOrRemovePosts(boolean hide, boolean wholeChain, Post post) {
        Set<Post> posts = new HashSet<>();

        if (isBound()) {
            if (wholeChain) {
                ChanThread thread = chanLoader.getThread();
                if (thread != null) {
                    posts.addAll(PostUtils.findPostWithReplies(post.no, thread.getPosts()));
                }
            } else {
                posts.add(PostUtils.findPostById(post.no, chanLoader.getThread()));
            }
        }

        threadPresenterCallback.hideOrRemovePosts(hide, wholeChain, posts);
    }

    public void showRemovedPostsDialog() {
        if (!isBound() || chanLoader.getThread() == null || loadable.isCatalogMode()) {
            return;
        }

        threadPresenterCallback.viewRemovedPostsForTheThread(chanLoader.getThread().getPosts(), loadable.no);
    }

    public void onRestoreRemovedPostsClicked(List<Integer> selectedPosts) {
        if (!isBound()) return;

        threadPresenterCallback.onRestoreRemovedPostsClicked(loadable, selectedPosts);
    }

    @Override
    public void openArchive(ExternalSiteArchive externalSiteArchive, Loadable op, int postNo) {
        if (isBound()) {
            showThread(externalSiteArchive.getArchiveLoadable(op, postNo));
        }
    }

    public void markAllPostsAsSeen() {
        if (!isBound()) return;
        Pin pin = watchManager.findPinByLoadableId(loadable.id);
        if (pin != null) {
            watchManager.onBottomPostViewed(pin);
        }
    }

    private List<FloatingMenuItem<PostOptions>> populateFilterMenuOptions(Post post) {
        List<FloatingMenuItem<PostOptions>> filterMenu = new ArrayList<>();
        if (post.isOP && !TextUtils.isEmpty(post.subject)) {
            filterMenu.add(new FloatingMenuItem<>(POST_OPTION_FILTER_SUBJECT, FilterType.SUBJECT.toString()));
        }
        if (!TextUtils.isEmpty(post.comment)) {
            filterMenu.add(new FloatingMenuItem<>(POST_OPTION_FILTER_COMMENT, FilterType.COMMENT.toString()));
        }
        if (!TextUtils.isEmpty(post.name) && !TextUtils.equals(post.name, "Anonymous")) {
            filterMenu.add(new FloatingMenuItem<>(POST_OPTION_FILTER_NAME, FilterType.NAME.toString()));
        }
        if (!TextUtils.isEmpty(post.id)) {
            filterMenu.add(new FloatingMenuItem<>(POST_OPTION_FILTER_ID, FilterType.ID.toString()));
        }
        if (!TextUtils.isEmpty(post.tripcode)) {
            filterMenu.add(new FloatingMenuItem<>(POST_OPTION_FILTER_TRIPCODE, FilterType.TRIPCODE.toString()));
        }
        if (loadable.board.countryFlags || !loadable.board.boardFlags.isEmpty()) {
            filterMenu.add(new FloatingMenuItem<>(POST_OPTION_FILTER_FLAG_CODE, FilterType.FLAG_CODE.toString()));
        }
        if (!post.images.isEmpty()) {
            filterMenu.add(new FloatingMenuItem<>(POST_OPTION_FILTER_FILENAME, FilterType.FILENAME.toString()));
            if (loadable.site.siteFeature(Site.SiteFeature.IMAGE_FILE_HASH)) {
                filterMenu.add(new FloatingMenuItem<>(POST_OPTION_FILTER_IMAGE_HASH, FilterType.IMAGE_HASH.toString()));
            }
        }
        return filterMenu;
    }

    private List<FloatingMenuItem<PostOptions>> populateCopyMenuOptions(Post post) {
        List<FloatingMenuItem<PostOptions>> copyMenu = new ArrayList<>();
        if (!TextUtils.isEmpty(post.comment)) {
            copyMenu.add(new FloatingMenuItem<>(POST_OPTION_COPY_POST_TEXT, R.string.post_copy_text));
        }
        copyMenu.add(new FloatingMenuItem<>(POST_OPTION_COPY_POST_LINK, R.string.post_copy_link));
        copyMenu.add(new FloatingMenuItem<>(POST_OPTION_COPY_CROSS_BOARD_LINK, R.string.post_copy_cross_board_link));
        copyMenu.add(new FloatingMenuItem<>(POST_OPTION_COPY_POST_URL, R.string.post_copy_post_url));
        if (!post.images.isEmpty()) {
            copyMenu.add(new FloatingMenuItem<>(POST_OPTION_COPY_IMG_URL, R.string.post_copy_image_url));
        }
        copyMenu.add(new FloatingMenuItem<>(POST_OPTION_INFO, R.string.post_info));
        return copyMenu;
    }

    public interface ThreadPresenterCallback
            extends ProgressResponseBody.ProgressListener {
        void showPosts(ChanThread thread, PostsFilter filter);

        void postClicked(Post post);

        void showError(int errResId);

        void showLoading();

        void showEmpty();

        void refreshUI();

        void showThread(Loadable threadLoadable);

        void showBoard(Loadable catalogLoadable);

        void showBoardAndSearch(Loadable catalogLoadable, String searchQuery);

        void openLink(PostLinkable linkable, String link);

        void openReportView(Post post);

        void showPostsPopup(Post forPost, List<Post> posts);

        void hidePostsPopup();

        List<Post> getDisplayingPosts();

        PostViewMode getPostViewMode();

        RecyclerViewPosition getCurrentPosition();

        void showImages(List<PostImage> images, int index, Loadable loadable, ImageView thumbnail);

        void showAlbum(List<PostImage> images, int index);

        void scrollTo(int displayPosition, boolean smooth);

        void smoothScrollNewPosts(int displayPosition);

        void highlightPostNo(int postNo);

        void highlightPostId(String id);

        void highlightPostTripcode(String tripcode);

        void filterPostSubject(CharSequence subject);

        void filterPostName(String name);

        void filterPostComment(CharSequence comment);

        void filterPostID(String ID);

        void filterPostFlagCode(Post post);

        void filterPostFilename(Post post);

        void filterPostTripcode(String tripcode);

        void filterPostImageHash(Post post);

        void showSearch(boolean show);

        void setSearchStatus(String query, boolean setEmptyText, boolean hideKeyboard);

        void quote(Post post, boolean withText);

        void quote(Post post, CharSequence text);

        void showDeleting();

        void hideDeleting(String message);

        void hideThread(Post post, boolean hide);

        void showNewPostsSnackbar(final Loadable loadable, int more);

        void showHideOrRemoveWholeChainDialog(boolean hide, Post post, int threadNo);

        void hideOrRemovePosts(boolean hide, boolean wholeChain, Set<Post> posts);

        void unhideOrUnremovePost(Post post);

        void viewRemovedPostsForTheThread(List<Post> threadPosts, int threadNo);

        void onRestoreRemovedPostsClicked(Loadable threadLoadable, List<Integer> selectedPosts);

        void updateSubtitle(CharSequence summary);
    }
}
