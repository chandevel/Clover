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
package com.github.adamantcheese.chan.ui.layout;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.github.adamantcheese.chan.R;
import com.github.adamantcheese.chan.controller.Controller;
import com.github.adamantcheese.chan.core.database.DatabaseHideManager;
import com.github.adamantcheese.chan.core.database.DatabaseUtils;
import com.github.adamantcheese.chan.core.manager.FilterType;
import com.github.adamantcheese.chan.core.model.ChanThread;
import com.github.adamantcheese.chan.core.model.Post;
import com.github.adamantcheese.chan.core.model.PostHttpIcon;
import com.github.adamantcheese.chan.core.model.PostImage;
import com.github.adamantcheese.chan.core.model.PostLinkable;
import com.github.adamantcheese.chan.core.model.orm.Loadable;
import com.github.adamantcheese.chan.core.model.orm.PostHide;
import com.github.adamantcheese.chan.core.presenter.ReplyPresenter.Page;
import com.github.adamantcheese.chan.core.presenter.ThreadPresenter;
import com.github.adamantcheese.chan.core.settings.ChanSettings;
import com.github.adamantcheese.chan.core.settings.ChanSettings.PostViewMode;
import com.github.adamantcheese.chan.core.site.Site;
import com.github.adamantcheese.chan.core.site.archives.ExternalSiteArchive;
import com.github.adamantcheese.chan.core.site.loader.ChanThreadLoader;
import com.github.adamantcheese.chan.ui.adapter.PostsFilter;
import com.github.adamantcheese.chan.ui.controller.ImageOptionsController;
import com.github.adamantcheese.chan.ui.helper.PostPopupHelper;
import com.github.adamantcheese.chan.ui.helper.RemovedPostsHelper;
import com.github.adamantcheese.chan.ui.theme.ThemeHelper;
import com.github.adamantcheese.chan.ui.toolbar.Toolbar;
import com.github.adamantcheese.chan.ui.view.HidingFloatingActionButton;
import com.github.adamantcheese.chan.ui.view.LoadView;
import com.github.adamantcheese.chan.ui.view.ThumbnailView;
import com.github.adamantcheese.chan.ui.widget.CancellableSnackbar;
import com.github.adamantcheese.chan.utils.AndroidUtils;
import com.github.adamantcheese.chan.utils.BackgroundUtils;
import com.google.android.material.snackbar.Snackbar;
import com.github.adamantcheese.chan.utils.RecyclerUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import static com.github.adamantcheese.chan.Chan.inject;
import static com.github.adamantcheese.chan.ui.widget.CancellableToast.showToast;
import static com.github.adamantcheese.chan.ui.widget.DefaultAlertDialog.getDefaultAlertBuilder;
import static com.github.adamantcheese.chan.utils.AndroidUtils.clearAnySelectionsAndKeyboards;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getQuantityString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.getString;
import static com.github.adamantcheese.chan.utils.AndroidUtils.openLinkInBrowser;
import static com.github.adamantcheese.chan.utils.AndroidUtils.removeFromParentView;

/**
 * Wrapper around ThreadListLayout, so that it cleanly manages between a loading state
 * and the recycler view.
 */
public class ThreadLayout
        extends CoordinatorLayout
        implements ThreadPresenter.ThreadPresenterCallback, PostPopupHelper.PostPopupHelperCallback,
                   RemovedPostsHelper.RemovedPostsCallbacks, View.OnClickListener,
                   ThreadListLayout.ThreadListLayoutCallback, ImageOptionsController.ImageOptionsControllerCallback {
    private enum Visible {
        EMPTY,
        LOADING,
        THREAD,
        ERROR
    }

    @Inject
    DatabaseHideManager databaseHideManager;

    ThreadPresenter presenter;

    private ThreadLayoutCallback callback;

    private View progressLayout;

    private LoadView loadView;
    private HidingFloatingActionButton replyButton;
    private ThreadListLayout threadListLayout;
    private LinearLayout errorLayout;
    private boolean archiveButton;

    private TextView errorText;
    private Button errorRetryButton;
    private PostPopupHelper postPopupHelper;
    private RemovedPostsHelper removedPostsHelper;
    private Visible visible = Visible.EMPTY;
    private ProgressDialog deletingDialog;
    private boolean replyButtonEnabled;
    private boolean showingReplyButton = false;

    public ThreadLayout(Context context) {
        this(context, null);
    }

    public ThreadLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ThreadLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        if (isInEditMode()) return;
        inject(this);
    }

    public void create(ThreadLayoutCallback callback) {
        this.callback = callback;

        // View binding
        loadView = findViewById(R.id.loadview);
        replyButton = findViewById(R.id.reply_button);

        // Inflate ThreadListLayout
        threadListLayout =
                (ThreadListLayout) LayoutInflater.from(getContext()).inflate(R.layout.layout_thread_list, this, false);

        // Inflate error layout
        errorLayout =
                (LinearLayout) LayoutInflater.from(getContext()).inflate(R.layout.layout_thread_error, this, false);
        errorText = errorLayout.findViewById(R.id.text);
        errorRetryButton = errorLayout.findViewById(R.id.button);

        // Inflate thread loading layout
        progressLayout = LayoutInflater.from(getContext()).inflate(R.layout.layout_thread_progress, this, false);

        // View setup
        presenter = new ThreadPresenter(getContext(), this);
        threadListLayout.setCallbacks(presenter, presenter, presenter, presenter, this);
        postPopupHelper = new PostPopupHelper(getContext(), presenter, this);
        removedPostsHelper = new RemovedPostsHelper(getContext(), presenter, this);
        errorText.setTypeface(ThemeHelper.getTheme().mainFont);
        errorRetryButton.setOnClickListener(this);

        // Setup
        replyButtonEnabled = ChanSettings.enableReplyFab.get();
        if (!replyButtonEnabled) {
            removeFromParentView(replyButton);
        } else {
            replyButton.setOnClickListener(this);
            replyButton.setToolbar(getToolbar());
        }
    }

    public void destroy() {
        presenter.unbindLoadable();
    }

    @Override
    public void onClick(View v) {
        if (v == errorRetryButton) {
            if (!archiveButton) {
                presenter.requestData();
            } else {
                presenter.showArchives(presenter.getLoadable(), presenter.getLoadable().no);
            }
        } else if (v == replyButton) {
            threadListLayout.openReply(true);
        }
    }

    public boolean onBack() {
        return threadListLayout.onBack();
    }

    public boolean sendKeyEvent(KeyEvent event) {
        return threadListLayout.sendKeyEvent(event);
    }

    public ThreadPresenter getPresenter() {
        return presenter;
    }

    public void gainedFocus() {
        if (visible == Visible.THREAD) {
            threadListLayout.gainedFocus();
        }
    }

    public void setPostViewMode(PostViewMode postViewMode) {
        threadListLayout.setPostViewMode(postViewMode);
    }

    @Override
    public void replyLayoutOpen(boolean open) {
        showReplyButton(!open);
    }

    @Override
    public Toolbar getToolbar() {
        return callback.getToolbar();
    }

    @Override
    public boolean threadBackPressed() {
        return callback.threadBackPressed();
    }

    @Override
    public boolean isViewingCatalog() {
        return callback.isViewingCatalog();
    }

    @Override
    public void showPosts(ChanThread thread, PostsFilter filter) {
        if (replyButton.getVisibility() != VISIBLE && !(thread.getLoadable().site instanceof ExternalSiteArchive)) {
            replyButton.show();
        }

        threadListLayout.showPosts(thread, filter, visible != Visible.THREAD);

        switchVisible(Visible.THREAD);
        callback.onShowPosts(thread.getLoadable());
    }

    @Override
    public void postClicked(Post post) {
        if (postPopupHelper.isOpen()) {
            postPopupHelper.postClicked(post);
        }
    }

    @Override
    public void showError(ChanThreadLoader.ChanLoaderException error) {
        String errorMessage = getString(error.getErrorMessage());

        if (visible == Visible.THREAD) {
            threadListLayout.showError(errorMessage);
        } else {
            switchVisible(Visible.ERROR);
            errorText.setText(errorMessage);
            archiveButton = false;
            if (error.getErrorMessage() == R.string.thread_load_failed_not_found) {
                errorRetryButton.setText(R.string.thread_view_external_archive);
                archiveButton = true;

                presenter.markAllPostsAsSeen();
            }
        }
    }

    @Override
    public void showLoading() {
        switchVisible(Visible.LOADING);
    }

    @Override
    public void showEmpty() {
        switchVisible(Visible.EMPTY);
    }

    @Override
    public void refreshUI() {
        threadListLayout.refreshUI();
    }

    @Override
    public void openLink(PostLinkable linkable, final String link) {
        if (ChanSettings.openLinkConfirmation.get()) {
            getDefaultAlertBuilder(getContext()).setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.ok, (dialog, which) -> openLinkConfirmed(linkable, link))
                    .setTitle(R.string.open_link_confirmation)
                    .setMessage(link)
                    .show();
        } else {
            openLinkConfirmed(linkable, link);
        }
    }

    public void openLinkConfirmed(final PostLinkable linkable, final String link) {
        if (linkable.type == PostLinkable.Type.JAVASCRIPT) {
            callback.openWebViewController(link, (String) linkable.value);
        } else {
            if (ChanSettings.openLinkBrowser.get()) {
                AndroidUtils.openLink(link);
            } else {
                openLinkInBrowser(getContext(), link);
            }
        }
    }

    @Override
    public void openReportView(Post post) {
        callback.openReportController(post);
    }

    @Override
    public void showThread(Loadable threadLoadable) {
        callback.showThread(threadLoadable);
    }

    @Override
    public void showBoard(Loadable catalogLoadable) {
        callback.showBoard(catalogLoadable);
    }

    @Override
    public void showBoardAndSearch(Loadable catalogLoadable, String searchQuery) {
        callback.showBoardAndSearch(catalogLoadable, searchQuery);
    }

    public void showPostsPopup(Post forPost, List<Post> posts) {
        clearAnySelectionsAndKeyboards(getContext());
        postPopupHelper.showPosts(forPost, posts);
    }

    @Override
    public void hidePostsPopup() {
        postPopupHelper.popAll();
    }

    @Override
    public List<Post> getDisplayingPosts() {
        if (postPopupHelper.isOpen()) {
            return postPopupHelper.getDisplayingPosts();
        } else {
            return threadListLayout.getDisplayingPosts();
        }
    }

    public PostViewMode getPostViewMode() {
        return threadListLayout.getPostViewMode();
    }

    @Override
    public RecyclerUtils.RecyclerViewPosition getCurrentPosition() {
        return threadListLayout.getIndexAndTop();
    }

    @Override
    public void showImages(List<PostImage> images, int index, Loadable loadable, ThumbnailView thumbnail) {
        clearAnySelectionsAndKeyboards(getContext());
        callback.showImages(images, index, loadable, thumbnail);
    }

    @Override
    public void showAlbum(List<PostImage> images, int index) {
        callback.showAlbum(images, index);
    }

    @Override
    public void scrollTo(int displayPosition, boolean smooth) {
        if (postPopupHelper.isOpen()) {
            postPopupHelper.scrollTo(displayPosition);
        } else if (visible == Visible.THREAD) {
            threadListLayout.scrollTo(displayPosition, smooth);
        }
    }

    @Override
    public void smoothScrollNewPosts(int displayPosition) {
        threadListLayout.smoothScrollNewPosts(displayPosition);
    }

    @Override
    public void highlightPostNo(int postNo) {
        threadListLayout.highlightPostNo(postNo);
    }

    @Override
    public void highlightPostId(String id) {
        threadListLayout.highlightPostId(id);
    }

    @Override
    public void highlightPostTripcode(String tripcode) {
        threadListLayout.highlightPostTripcode(tripcode);
    }

    @Override
    public void filterPostSubject(String subject) {
        callback.openFilterForType(FilterType.SUBJECT, subject);
    }

    @Override
    public void filterPostName(String name) {
        callback.openFilterForType(FilterType.NAME, name);
    }

    @Override
    public void filterPostID(String id) {
        callback.openFilterForType(FilterType.ID, id);
    }

    @Override
    public void filterPostComment(CharSequence comment) {
        callback.openFilterForType(FilterType.COMMENT, comment.toString());
    }

    @Override
    public void filterPostFlagCode(Post post) {
        StringBuilder flagCodes = new StringBuilder();
        if (post.httpIcons != null && !post.httpIcons.isEmpty()) {
            for (PostHttpIcon icon : post.httpIcons) {
                flagCodes.append(icon.code).append("|");
            }
        }
        callback.openFilterForType(FilterType.FLAG_CODE, flagCodes.toString().replaceAll("\\|$", ""));
    }

    @Override
    public void filterPostFilename(Post post) {
        if (post.images.isEmpty()) return;
        callback.openFilterForType(FilterType.FILENAME, post.image().filename);
    }

    @Override
    public void filterPostTripcode(String tripcode) {
        callback.openFilterForType(FilterType.TRIPCODE, tripcode);
    }

    @Override
    public void filterPostImageHash(Post post) {
        if (post.images.isEmpty()) return;
        if (post.images.size() == 1) {
            callback.openFilterForType(FilterType.IMAGE, post.image().fileHash);
        } else {
            ListView hashList = new ListView(getContext());
            AlertDialog dialog = getDefaultAlertBuilder(getContext()).setTitle("Select an image to filter.")
                    .setView(hashList)
                    .create();
            dialog.setCanceledOnTouchOutside(true);
            List<String> hashes = new ArrayList<>();
            for (PostImage image : post.images) {
                if (!image.isInlined && image.fileHash != null) hashes.add(image.fileHash);
            }
            hashList.setAdapter(new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1, hashes));
            hashList.setOnItemClickListener((parent, view, position, id) -> {
                callback.openFilterForType(FilterType.IMAGE, hashes.get(position));
                dialog.dismiss();
            });

            dialog.show();
        }
    }

    @Override
    public void showSearch(boolean show) {
        threadListLayout.openSearch(show);
    }

    public void setSearchStatus(String query, boolean setEmptyText, boolean hideKeyboard) {
        threadListLayout.setSearchStatus(query, setEmptyText, hideKeyboard);
    }

    @Override
    public void quote(Post post, boolean withText) {
        threadListLayout.openReply(true);
        threadListLayout.getReplyPresenter().quote(post, withText);
    }

    @Override
    public void quote(Post post, CharSequence text) {
        threadListLayout.openReply(true);
        threadListLayout.getReplyPresenter().quote(post, text);
    }

    @Override
    public void showDeleting() {
        if (deletingDialog == null) {
            deletingDialog = ProgressDialog.show(getContext(), null, getString(R.string.delete_wait));
        }
    }

    @Override
    public void hideDeleting(String message) {
        if (deletingDialog != null) {
            deletingDialog.dismiss();
            deletingDialog = null;

            getDefaultAlertBuilder(getContext()).setMessage(message).setPositiveButton(R.string.ok, null).show();
        }
    }

    @Override
    public void hideThread(Post post, int threadNo, boolean hide) {
        // hideRepliesToThisPost is false here because we don't have posts in the catalog mode so there
        // is no point in hiding replies to a thread
        final PostHide postHide = PostHide.hidePost(post, true, hide, false);

        DatabaseUtils.runTask(databaseHideManager.addThreadHide(postHide));

        presenter.refreshUI();

        CancellableSnackbar.showSnackbar(this,
                hide ? R.string.thread_hidden : R.string.thread_removed,
                R.string.undo,
                v -> {
                    DatabaseUtils.runTask(databaseHideManager.removePostHide(postHide));
                    presenter.refreshUI();
                }
        );
    }

    @Override
    public void hideOrRemovePosts(boolean hide, boolean wholeChain, Set<Post> posts, int threadNo) {
        final List<PostHide> hideList = new ArrayList<>();

        for (Post post : posts) {
            // Do not add the OP post to the hideList since we don't want to hide an OP post
            // while being in a thread (it just doesn't make any sense)
            if (!post.isOP) {
                hideList.add(PostHide.hidePost(post, false, hide, wholeChain));
            }
        }

        DatabaseUtils.runTask(databaseHideManager.addPostsHide(hideList));

        presenter.refreshUI();

        String formattedString;
        if (hide) {
            formattedString = getQuantityString(R.plurals.post_hidden, posts.size(), posts.size());
        } else {
            formattedString = getQuantityString(R.plurals.post_removed, posts.size(), posts.size());
        }

        CancellableSnackbar.showSnackbar(this, formattedString, R.string.undo, v -> {
            DatabaseUtils.runTask(databaseHideManager.removePostsHide(hideList));
            presenter.refreshUI();
        });
    }

    @Override
    public void unhideOrUnremovePost(Post post) {
        DatabaseUtils.runTask(databaseHideManager.removePostHide(PostHide.unhidePost(post)));

        presenter.refreshUI();
    }

    @Override
    public void viewRemovedPostsForTheThread(List<Post> threadPosts, int threadNo) {
        removedPostsHelper.showPosts(threadPosts, threadNo);
    }

    @Override
    public void onRestoreRemovedPostsClicked(Loadable threadLoadable, List<Integer> selectedPosts) {

        List<PostHide> postsToRestore = new ArrayList<>();

        for (Integer postNo : selectedPosts) {
            postsToRestore.add(PostHide.unhidePost(threadLoadable.site.id(), threadLoadable.boardCode, postNo));
        }

        DatabaseUtils.runTask(databaseHideManager.removePostsHide(postsToRestore));

        presenter.refreshUI();

        CancellableSnackbar.showSnackbar(
                this,
                getString(R.string.restored_n_posts, postsToRestore.size())
        );
    }

    @Override
    public void showNewPostsSnackbar(final Loadable loadable, int more) {
        if (more <= 0 || !BackgroundUtils.isInForeground() || (threadListLayout.isReplyLayoutOpen()
                && threadListLayout.getReplyPresenter().getPage() != Page.LOADING
                && ChanSettings.moveInputToBottom.get())) {
            CancellableSnackbar.cleanup();
            return;
        }

        if (threadListLayout.getReplyPresenter().getPage() != Page.AUTHENTICATION) {
            CancellableSnackbar.showSnackbar(this,
                    getQuantityString(R.plurals.thread_new_posts, more, more),
                    R.string.thread_new_posts_goto,
                    v -> {
                        if (loadable == presenter.getLoadable()) {
                            presenter.onNewPostsViewClicked();
                        }
                    }
            );
        }
    }

    @Override
    public void showImageReencodingWindow() {
        clearAnySelectionsAndKeyboards(getContext());
        try {
            presentController(new ImageOptionsController(getContext(), presenter.getLoadable(), this));
        } catch (Exception e) {
            showToast(getContext(), R.string.file_cannot_be_reencoded, Toast.LENGTH_LONG);
        }
    }

    public ThumbnailView getThumbnail(PostImage postImage) {
        if (postPopupHelper.isOpen()) {
            return postPopupHelper.getThumbnail(postImage);
        } else {
            return threadListLayout.getThumbnail(postImage);
        }
    }

    public void openReply(boolean open) {
        threadListLayout.openReply(open);
    }

    private void showReplyButton(final boolean show) {
        if (show != showingReplyButton && replyButtonEnabled) {
            showingReplyButton = show;
            replyButton.animate().cancel();
            replyButton.animate()
                    .setInterpolator(new DecelerateInterpolator(2f))
                    .setStartDelay(show ? 100 : 0)
                    .alpha(show ? 1f : 0f)
                    .scaleX(show ? 1f : 0f)
                    .scaleY(show ? 1f : 0f)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationCancel(Animator animation) {
                            replyButton.setAlpha(show ? 1f : 0f);
                            replyButton.setScaleX(show ? 1f : 0f);
                            replyButton.setScaleY(show ? 1f : 0f);
                            replyButton.setClickable(show);
                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            replyButton.setClickable(show);
                        }
                    })
                    .start();
        }
    }

    private void switchVisible(Visible visible) {
        if (this.visible != visible) {
            if (this.visible == Visible.THREAD) {
                threadListLayout.cleanup();
                postPopupHelper.popAll();
                CancellableSnackbar.cleanup();
            }

            this.visible = visible;
            showReplyButton(false);
            threadListLayout.hideSwipeRefreshLayout();
            switch (visible) {
                case EMPTY:
                    loadView.setView(inflateEmptyView());
                    break;
                case LOADING:
                    loadView.setView(progressLayout);
                    break;
                case THREAD:
                    loadView.setView(threadListLayout);
                    if (presenter.isBound() && presenter.getLoadable().site.siteFeature(Site.SiteFeature.POSTING)) {
                        showReplyButton(true);
                    }
                    break;
                case ERROR:
                    loadView.setView(errorLayout);
                    threadListLayout.gainedFocus();
                    break;
            }
        }
    }

    private View inflateEmptyView() {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.layout_empty_setup, null);
        TextView tv = view.findViewById(R.id.feature);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // This unicode symbol crashes app on APIs below 23
            //😴 sleeping face emoji
            tv.setText("\uD83D\uDE34");
        }

        return view;
    }

    @Override
    public void presentController(Controller controller) {
        callback.presentController(controller);
    }

    @Override
    public void onImageOptionsApplied() {
        threadListLayout.onImageOptionsApplied();
    }

    @Override
    public void onImageOptionsComplete() {
        threadListLayout.onImageOptionsComplete();
    }

    @Override
    public void showHideOrRemoveWholeChainDialog(boolean hide, Post post, int threadNo) {
        String positiveButtonText = hide
                ? getString(R.string.thread_layout_hide_whole_chain)
                : getString(R.string.thread_layout_remove_whole_chain);
        String negativeButtonText =
                hide ? getString(R.string.thread_layout_hide_post) : getString(R.string.thread_layout_remove_post);
        String message = hide
                ? getString(R.string.thread_layout_hide_whole_chain_as_well)
                : getString(R.string.thread_layout_remove_whole_chain_as_well);

        AlertDialog alertDialog = getDefaultAlertBuilder(getContext()).setMessage(message)
                .setPositiveButton(positiveButtonText,
                        (dialog, which) -> presenter.hideOrRemovePosts(hide, true, post, threadNo)
                )
                .setNegativeButton(negativeButtonText,
                        (dialog, which) -> presenter.hideOrRemovePosts(hide, false, post, threadNo)
                )
                .create();

        alertDialog.show();
    }

    public interface ThreadLayoutCallback {
        void showThread(Loadable threadLoadable);

        void showBoard(Loadable catalogLoadable);

        void showBoardAndSearch(Loadable catalogLoadable, String searchQuery);

        void showImages(List<PostImage> images, int index, Loadable loadable, ThumbnailView thumbnail);

        void showAlbum(List<PostImage> images, int index);

        void onShowPosts(Loadable loadable);

        void presentController(Controller controller);

        void openReportController(Post post);

        void openWebViewController(String baseUrl, String javascript);

        Toolbar getToolbar();

        void openFilterForType(FilterType type, String filterText);

        boolean threadBackPressed();

        boolean isViewingCatalog();
    }
}
