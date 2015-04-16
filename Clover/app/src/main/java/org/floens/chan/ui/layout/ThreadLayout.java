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

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.util.AttributeSet;
import android.widget.Toast;

import com.android.volley.VolleyError;

import org.floens.chan.R;
import org.floens.chan.controller.Controller;
import org.floens.chan.core.model.ChanThread;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.PostImage;
import org.floens.chan.core.model.PostLinkable;
import org.floens.chan.core.presenter.ThreadPresenter;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.helper.PostPopupHelper;
import org.floens.chan.ui.view.LoadView;
import org.floens.chan.ui.view.ThumbnailView;
import org.floens.chan.utils.AndroidUtils;

import java.util.List;

/**
 * Wrapper around ThreadListLayout, so that it cleanly manages between loadbar and listview.
 */
public class ThreadLayout extends LoadView implements ThreadPresenter.ThreadPresenterCallback, PostPopupHelper.PostPopupHelperCallback {
    private ThreadLayoutCallback callback;
    private ThreadPresenter presenter;

    private ThreadListLayout threadListLayout;
    private PostPopupHelper postPopupHelper;
    private boolean visible;

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

    private void init() {
        presenter = new ThreadPresenter(this);

        threadListLayout = new ThreadListLayout(getContext());
        threadListLayout.setCallbacks(presenter, presenter);

        postPopupHelper = new PostPopupHelper(getContext(), presenter, this);

        switchVisible(false);
    }

    public void setCallback(ThreadLayoutCallback callback) {
        this.callback = callback;
    }

    public ThreadPresenter getPresenter() {
        return presenter;
    }

    @Override
    public void showPosts(ChanThread thread) {
        threadListLayout.showPosts(thread, !visible);
        switchVisible(true);
        callback.onShowPosts();
    }

    @Override
    public void showError(VolleyError error) {
        switchVisible(true);
        threadListLayout.showError(error);
    }

    @Override
    public void showLoading() {
        switchVisible(false);
    }

    public void showPostInfo(String info) {
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.post_info)
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
        ClipboardManager clipboard = (ClipboardManager) AndroidUtils.getAppRes().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Post text", post.comment.toString());
        clipboard.setPrimaryClip(clip);
        Toast.makeText(getContext(), R.string.post_text_copied_to_clipboard, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void openLink(final String link) {
        if (ChanSettings.getOpenLinkConfirmation()) {
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
    public void showThread(Loadable threadLoadable) {
        callback.showThread(threadLoadable);
    }

    public void showPostsPopup(Post forPost, List<Post> posts) {
        postPopupHelper.showPosts(forPost, posts);
    }

    @Override
    public void showImages(List<PostImage> images, int index, Loadable loadable, ThumbnailView thumbnail) {
        callback.showImages(images, index, loadable, thumbnail);
    }

    @Override
    public void scrollTo(int position) {
        threadListLayout.scrollTo(position);
    }

    public ThumbnailView getThumbnail(PostImage postImage) {
        if (postPopupHelper.isOpen()) {
            return postPopupHelper.getThumbnail(postImage);
        } else {
            return threadListLayout.getThumbnail(postImage);
        }
    }

    private void switchVisible(boolean visible) {
        if (this.visible != visible) {
            this.visible = visible;
            setView(visible ? threadListLayout : null);
        }
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
    }
}
