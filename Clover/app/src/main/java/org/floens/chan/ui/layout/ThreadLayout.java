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
package org.floens.chan.ui.layout;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.NetworkError;
import com.android.volley.NoConnectionError;
import com.android.volley.ServerError;
import com.android.volley.VolleyError;

import org.floens.chan.Chan;
import org.floens.chan.R;
import org.floens.chan.controller.Controller;
import org.floens.chan.core.database.DatabaseManager;
import org.floens.chan.core.model.ChanThread;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.PostImage;
import org.floens.chan.core.model.PostLinkable;
import org.floens.chan.core.model.ThreadHide;
import org.floens.chan.core.presenter.ThreadPresenter;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.adapter.PostsFilter;
import org.floens.chan.ui.cell.PostCellInterface;
import org.floens.chan.ui.helper.PostPopupHelper;
import org.floens.chan.ui.toolbar.Toolbar;
import org.floens.chan.ui.view.LoadView;
import org.floens.chan.ui.view.ThumbnailView;
import org.floens.chan.utils.AndroidUtils;

import java.util.List;

import javax.net.ssl.SSLException;

import static org.floens.chan.utils.AndroidUtils.fixSnackbarText;
import static org.floens.chan.utils.AndroidUtils.getString;

/**
 * Wrapper around ThreadListLayout, so that it cleanly manages between loadbar and listview.
 */
public class ThreadLayout extends CoordinatorLayout implements ThreadPresenter.ThreadPresenterCallback, PostPopupHelper.PostPopupHelperCallback, View.OnClickListener, ThreadListLayout.ThreadListLayoutCallback {
    private enum Visible {
        LOADING,
        THREAD,
        ERROR;
    }

    private DatabaseManager databaseManager;

    private ThreadLayoutCallback callback;

    private ThreadPresenter presenter;

    private LoadView loadView;
    private FloatingActionButton replyButton;
    private ThreadListLayout threadListLayout;
    private LinearLayout errorLayout;

    private TextView errorText;
    private Button errorRetryButton;
    private PostPopupHelper postPopupHelper;
    private Visible visible;
    private ProgressDialog deletingDialog;
    private boolean refreshedFromSwipe;
    private boolean showingReplyButton = false;
    private Snackbar newPostsNotification;

    public ThreadLayout(Context context) {
        super(context);
        init();
    }

    public ThreadLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ThreadLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public void setCallback(ThreadLayoutCallback callback) {
        this.callback = callback;

        presenter = new ThreadPresenter(this);

        loadView = (LoadView) findViewById(R.id.loadview);
        replyButton = (FloatingActionButton) findViewById(R.id.reply_button);
        replyButton.setOnClickListener(this);

        threadListLayout = (ThreadListLayout) LayoutInflater.from(getContext()).inflate(R.layout.layout_thread_list, this, false);
        threadListLayout.setCallbacks(presenter, presenter, presenter, presenter, this);

        postPopupHelper = new PostPopupHelper(getContext(), presenter, this);

        errorLayout = (LinearLayout) LayoutInflater.from(getContext()).inflate(R.layout.layout_thread_error, this, false);
        errorText = (TextView) errorLayout.findViewById(R.id.text);
        errorText.setTypeface(AndroidUtils.ROBOTO_MEDIUM);

        errorRetryButton = (Button) errorLayout.findViewById(R.id.button);
        errorRetryButton.setOnClickListener(this);

        switchVisible(Visible.LOADING);
    }

    @Override
    public void onClick(View v) {
        if (v == errorRetryButton) {
            presenter.requestData();
        } else if (v == replyButton) {
            threadListLayout.openReply(true);
        }
    }

    public boolean canChildScrollUp() {
        if (visible == Visible.THREAD) {
            return threadListLayout.canChildScrollUp();
        } else {
            return true;
        }
    }

    public boolean onBack() {
        return threadListLayout.onBack();
    }

    public ThreadPresenter getPresenter() {
        return presenter;
    }

    public void refreshFromSwipe() {
        refreshedFromSwipe = true;
        presenter.requestData();
    }

    public void setPostViewMode(PostCellInterface.PostViewMode postViewMode) {
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
    public boolean collapseToolbar() {
        return callback.collapseToolbar();
    }

    @Override
    public void showPosts(ChanThread thread, PostsFilter filter) {
        threadListLayout.showPosts(thread, filter, visible != Visible.THREAD);
        switchVisible(Visible.THREAD);
        callback.onShowPosts();
    }

    @Override
    public void postClicked(Post post) {
        if (postPopupHelper.isOpen()) {
            postPopupHelper.postClicked(post);
        }
    }

    @Override
    public void showError(VolleyError error) {
        String errorMessage;
        if (error.getCause() instanceof SSLException) {
            errorMessage = getContext().getString(R.string.thread_load_failed_ssl);
        } else if ((error instanceof NoConnectionError) || (error instanceof NetworkError)) {
            errorMessage = getContext().getString(R.string.thread_load_failed_network);
        } else if (error instanceof ServerError) {
            errorMessage = getContext().getString(R.string.thread_load_failed_server);
        } else {
            errorMessage = getContext().getString(R.string.thread_load_failed_parsing);
        }

        if (visible == Visible.THREAD) {
            threadListLayout.showError(errorMessage);
        } else {
            switchVisible(Visible.ERROR);
            errorText.setText(errorMessage);
        }
    }

    @Override
    public void showLoading() {
        switchVisible(Visible.LOADING);
    }

    public void showPostInfo(String info) {
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.post_info_title)
                .setMessage(info)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    public void showPostLinkables(final List<PostLinkable> linkables) {
        String[] keys = new String[linkables.size()];
        for (int i = 0; i < linkables.size(); i++) {
            keys[i] = linkables.get(i).key;
        }

        new AlertDialog.Builder(getContext())
                .setItems(keys, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        presenter.onPostLinkableClicked(linkables.get(which));
                    }
                })
                .show();
    }

    public void clipboardPost(Post post) {
        ClipboardManager clipboard = (ClipboardManager) AndroidUtils.getAppContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Post text", post.comment.toString());
        clipboard.setPrimaryClip(clip);
        Toast.makeText(getContext(), R.string.post_text_copied, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void openLink(final String link) {
        if (ChanSettings.openLinkConfirmation.get()) {
            new AlertDialog.Builder(getContext())
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            AndroidUtils.openLink(link);
                        }
                    })
                    .setTitle(R.string.open_link_confirmation)
                    .setMessage(link)
                    .show();
        } else {
            AndroidUtils.openLink(link);
        }
    }

    @Override
    public void openWebView(String title, String link) {
        AndroidUtils.openWebView((Activity) getContext(), title, link);
    }

    @Override
    public void showThread(Loadable threadLoadable) {
        callback.showThread(threadLoadable);
    }

    public void showPostsPopup(Post forPost, List<Post> posts) {
        postPopupHelper.showPosts(forPost, posts);
    }

    @Override
    public List<Post> getDisplayingPosts() {
        return threadListLayout.getDisplayingPosts();
    }

    @Override
    public void showImages(List<PostImage> images, int index, Loadable loadable, ThumbnailView thumbnail) {
        callback.showImages(images, index, loadable, thumbnail);
    }

    @Override
    public void scrollTo(int position, boolean smooth) {
        threadListLayout.scrollTo(position, smooth);
    }

    @Override
    public void highlightPost(Post post) {
        threadListLayout.highlightPost(post);
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
    public void showSearch(boolean show) {
        threadListLayout.showSearch(show);
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
    public void confirmPostDelete(final Post post) {
        @SuppressLint("InflateParams")
        final View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_post_delete, null);
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.delete_confirm)
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        CheckBox checkBox = (CheckBox) view.findViewById(R.id.image_only);
                        presenter.deletePostConfirmed(post, checkBox.isChecked());
                    }
                })
                .show();
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

            new AlertDialog.Builder(getContext())
                    .setMessage(message)
                    .setPositiveButton(R.string.ok, null)
                    .show();
        }
    }

    @Override
    public void hideThread(Post post) {
        final ThreadHide threadHide = new ThreadHide(post.board, post.no);
        databaseManager.addThreadHide(threadHide);
        presenter.refreshUI();

        Snackbar snackbar = Snackbar.make(this, R.string.post_hidden, Snackbar.LENGTH_LONG);
        snackbar.setAction(R.string.undo, new OnClickListener() {
            @Override
            public void onClick(View v) {
                databaseManager.removeThreadHide(threadHide);
                presenter.refreshUI();
            }
        }).show();
        fixSnackbarText(getContext(), snackbar);
    }

    @Override
    public void showNewPostsNotification(boolean show, int more) {
        if (show) {
            if (!threadListLayout.scrolledToBottom()) {
                String text = getContext().getString(R.string.thread_new_posts,
                        more, getContext().getResources().getQuantityString(R.plurals.posts, more, more));

                newPostsNotification = Snackbar.make(this, text, Snackbar.LENGTH_LONG);
                newPostsNotification.setAction(R.string.thread_new_posts_goto, new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        presenter.scrollTo(-1, false);
                    }
                }).show();
                fixSnackbarText(getContext(), newPostsNotification);
            }
        } else {
            if (newPostsNotification != null) {
                newPostsNotification.dismiss();
                newPostsNotification = null;
            }
        }
    }

    public ThumbnailView getThumbnail(PostImage postImage) {
        if (postPopupHelper.isOpen()) {
            return postPopupHelper.getThumbnail(postImage);
        } else {
            return threadListLayout.getThumbnail(postImage);
        }
    }

    public boolean postRepliesOpen() {
        return postPopupHelper.isOpen();
    }

    private void showReplyButton(boolean show) {
        if (show != showingReplyButton) {
            showingReplyButton = show;

            replyButton.animate()
                    .setInterpolator(new DecelerateInterpolator(2f))
                    .setStartDelay(show ? 100 : 0)
                    .setDuration(200)
                    .alpha(show ? 1f : 0f)
                    .scaleX(show ? 1f : 0f)
                    .scaleY(show ? 1f : 0f)
                    .start();
        }
    }

    private void switchVisible(Visible visible) {
        if (this.visible != visible) {
            if (this.visible != null) {
                switch (this.visible) {
                    case THREAD:
                        threadListLayout.cleanup();
                        postPopupHelper.popAll();
                        showSearch(false);
                        showReplyButton(false);
                        newPostsNotification = null;
                        break;
                }
            }

            this.visible = visible;
            switch (visible) {
                case LOADING:
                    View view = loadView.setView(null);
                    // TODO: cleanup
                    if (refreshedFromSwipe) {
                        refreshedFromSwipe = false;
                        view.setVisibility(View.GONE);
                    }
                    break;
                case THREAD:
                    callback.hideSwipeRefreshLayout();
                    loadView.setView(threadListLayout);
                    showReplyButton(true);
                    break;
                case ERROR:
                    callback.hideSwipeRefreshLayout();
                    loadView.setView(errorLayout);
                    break;
            }
        }
    }

    private void init() {
        databaseManager = Chan.getDatabaseManager();
    }

    @Override
    public void presentRepliesController(Controller controller) {
        callback.presentRepliesController(controller);
    }

    public interface ThreadLayoutCallback {
        void showThread(Loadable threadLoadable);

        void showImages(List<PostImage> images, int index, Loadable loadable, ThumbnailView thumbnail);

        void onShowPosts();

        void presentRepliesController(Controller controller);

        void hideSwipeRefreshLayout();

        Toolbar getToolbar();

        boolean collapseToolbar();
    }
}
