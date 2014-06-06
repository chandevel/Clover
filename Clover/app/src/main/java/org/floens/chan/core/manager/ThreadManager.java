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
package org.floens.chan.core.manager;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.text.TextUtils;
import android.widget.CheckBox;
import android.widget.Toast;

import com.android.volley.VolleyError;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.core.ChanPreferences;
import org.floens.chan.core.loader.Loader;
import org.floens.chan.core.loader.LoaderPool;
import org.floens.chan.core.manager.ReplyManager.DeleteListener;
import org.floens.chan.core.manager.ReplyManager.DeleteResponse;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Pin;
import org.floens.chan.core.model.Post;
import org.floens.chan.core.model.PostLinkable;
import org.floens.chan.core.model.SavedReply;
import org.floens.chan.ui.activity.ReplyActivity;
import org.floens.chan.ui.fragment.PostRepliesFragment;
import org.floens.chan.ui.fragment.ReplyFragment;
import org.floens.chan.utils.Logger;
import org.floens.chan.utils.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * All PostView's need to have this referenced. This manages some things like
 * pages, starting and stopping of loading, handling linkables, replies popups
 * etc. onDestroy, onStart and onStop must be called from the activity/fragment
 */
public class ThreadManager implements Loader.LoaderListener {
    private static final String TAG = "ThreadManager";

    private final Activity activity;
    private final ThreadManager.ThreadManagerListener threadManagerListener;
    private final List<List<Post>> popupQueue = new ArrayList<>();
    private PostRepliesFragment currentPopupFragment;
    private int highlightedPost = -1;
    private int lastSeenPost = -1;
    private int lastPost = -1;

    private Loader loader;

    public ThreadManager(Activity activity, final ThreadManagerListener listener) {
        this.activity = activity;
        threadManagerListener = listener;
    }

    public void onDestroy() {
        unbindLoader();
    }

    public void onStart() {
        if (loader != null) {
            if (shouldWatch()) {
                loader.setAutoLoadMore(true);
                loader.requestMoreDataAndResetTimer();
            }
        }
    }

    public void onStop() {
        if (loader != null) {
            loader.setAutoLoadMore(false);
        }
    }

    public void bindLoader(Loadable loadable) {
        if (loader != null) {
            unbindLoader();
        }

        loader = LoaderPool.getInstance().obtain(loadable, this);
        if (shouldWatch()) {
            loader.setAutoLoadMore(true);
        }
    }

    public void unbindLoader() {
        if (loader != null) {
            loader.setAutoLoadMore(false);
            LoaderPool.getInstance().release(loader, this);
            loader = null;
        } else {
            Logger.e(TAG, "Loader already unbinded");
        }

        highlightedPost = -1;
        lastSeenPost = -1;
        lastPost = -1;
    }

    public void bottomPostViewed() {
        if (loader != null && loader.getLoadable().isThreadMode()) {
            Pin pin = ChanApplication.getPinnedManager().findPinByLoadable(loader.getLoadable());
            if (pin != null) {
                ChanApplication.getPinnedManager().onPinViewed(pin);
            }

            updateLastSeen();
        }
    }

    public boolean shouldWatch() {
        if (!ChanPreferences.getThreadAutoRefresh()) {
            return false;
        } else if (loader.getCachedPosts().size() > 0 && loader.getCachedPosts().get(0).closed) {
            return false;
        } else {
            return loader.getLoadable().isThreadMode();
        }
    }

    public void requestData() {
        if (loader != null) {
            loader.requestData();
        } else {
            Logger.e(TAG, "Loader null in requestData");
        }
    }

    /**
     * Called by postadapter and threadwatchcounterview.onclick
     */
    public void requestNextData() {
        if (loader != null) {
            loader.requestMoreData();
        } else {
            Logger.e(TAG, "Loader null in requestData");
        }
    }

    @Override
    public void onError(VolleyError error) {
        threadManagerListener.onThreadLoadError(error);
    }

    @Override
    public void onData(List<Post> result, boolean append) {
        if (!shouldWatch()) {
            loader.setAutoLoadMore(false);
        }

        if (result.size() > 0) {
            lastPost = result.get(result.size() - 1).no;
        }
        updateLastSeen();

        threadManagerListener.onThreadLoaded(result, append);
    }

    public boolean hasLoader() {
        return loader != null;
    }

    public Post findPostById(int id) {
        if (loader == null)
            return null;
        return loader.findPostById(id);
    }

    public Loadable getLoadable() {
        if (loader == null)
            return null;
        return loader.getLoadable();
    }

    public Loader getLoader() {
        return loader;
    }

    public void onThumbnailClicked(Post post) {
        threadManagerListener.onThumbnailClicked(post);
    }

    public void onPostClicked(Post post) {
        if (loader != null && loader.getLoadable().isBoardMode()) {
            threadManagerListener.onOPClicked(post);
        }
    }

    public void onPostLongClicked(final Post post) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);

        List<String> options = new ArrayList<>(Arrays.asList(activity.getResources().getStringArray(R.array.post_options)));
        // Only add the delete option when the post is a saved reply
        boolean delete = false, saved = false;
        if (ChanApplication.getDatabaseManager().isSavedReply(post.board, post.no)) {
            options.add(activity.getString(R.string.delete));
            delete = true;
        }

        if (ChanPreferences.getDeveloper()) {
            options.add("Make this a saved reply");
            saved = true;
        }

        final boolean finalDelete = delete;
        final boolean finalSaved = saved;
        builder.setItems(options.toArray(new String[options.size()]), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0: // Quick reply
                        openReply(false);
                        // Pass through
                    case 1: // Quote
                        ChanApplication.getReplyManager().quote(post.no);
                        break;
                    case 2: // Info
                        showPostInfo(post);
                        break;
                    case 3: // Show clickables
                        showPostLinkables(post);
                        break;
                    case 4: // Copy text
                        copyToClipboard(post.comment.toString());
                        break;
                    case 5: // Delete
                        if (finalDelete && !finalSaved) {
                            deletePost(post);
                        } else if (finalSaved && !finalDelete) {
                            ChanApplication.getDatabaseManager().saveReply(new SavedReply(post.board, post.no, "foo"));
                        }
                        break;
                    case 6:
                        ChanApplication.getDatabaseManager().saveReply(new SavedReply(post.board, post.no, "foo"));

                        break;
                }
            }
        });

        builder.create().show();
    }

    public void openReply(boolean startInActivity) {
        if (loader == null)
            return;

        if (startInActivity) {
            ReplyActivity.setLoadable(loader.getLoadable());
            Intent i = new Intent(activity, ReplyActivity.class);
            activity.startActivity(i);
        } else {
            ReplyFragment reply = ReplyFragment.newInstance(loader.getLoadable(), true);
            reply.show(activity.getFragmentManager(), "replyDialog");
        }
    }

    public void onPostLinkableClicked(PostLinkable linkable) {
        handleLinkableSelected(linkable);
    }

    public void scrollToPost(Post post) {
        threadManagerListener.onScrollTo(post);
    }

    public void highlightPost(Post post) {
        highlightedPost = post.no;
    }

    public boolean isPostHightlighted(Post post) {
        return highlightedPost >= 0 && post.no == highlightedPost;
    }

    public boolean isPostLastSeen(Post post) {
        return post.no == lastSeenPost && post.no != lastPost;
    }

    private void copyToClipboard(String comment) {
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Post text", comment);
        clipboard.setPrimaryClip(clip);
    }

    private void showPostInfo(Post post) {
        String text = "";

        if (post.hasImage) {
            text += "File: " + post.filename + "." + post.ext + " \nDimensions: " + post.imageWidth + "x"
                    + post.imageHeight + "\nSize: " + Utils.getReadableFileSize(post.fileSize, false) + "\n\n";
        }

        text += "Time: " + post.date;

        if (!TextUtils.isEmpty(post.id)) {
            text += "\nId: " + post.id;
        }

        if (!TextUtils.isEmpty(post.email)) {
            text += "\nEmail: " + post.email;
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

        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(R.string.post_info).setMessage(text)
                .setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                }).create();

        dialog.show();
    }

    /**
     * When the user clicks a post: a. when there's one linkable, open the
     * linkable. b. when there's more than one linkable, show the user multiple
     * options to select from.
     *
     * @param post The post that was clicked.
     */
    public void showPostLinkables(Post post) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        final ArrayList<PostLinkable> linkables = post.linkables;

        if (linkables.size() > 0) {
            if (linkables.size() == 1) {
                handleLinkableSelected(linkables.get(0));
            } else {
                String[] keys = new String[linkables.size()];
                for (int i = 0; i < linkables.size(); i++) {
                    keys[i] = linkables.get(i).key;
                }

                builder.setItems(keys, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        handleLinkableSelected(linkables.get(which));
                    }
                });

                AlertDialog dialog = builder.create();
                dialog.show();
            }
        }
    }

    public void showPostReplies(Post post) {
        List<Post> p = new ArrayList<>();
        for (int no : post.repliesFrom) {
            Post r = findPostById(no);
            if (r != null) {
                p.add(r);
            }
        }

        if (p.size() > 0) {
            showPostsRepliesFragment(p);
        }
    }

    /**
     * Handle when a linkable has been clicked.
     *
     * @param linkable the selected linkable.
     */
    private void handleLinkableSelected(final PostLinkable linkable) {
        if (linkable.type == PostLinkable.Type.QUOTE) {
            showPostReply(linkable);
        } else if (linkable.type == PostLinkable.Type.LINK) {
            if (ChanPreferences.getOpenLinkConfirmation()) {
                AlertDialog dialog = new AlertDialog.Builder(activity)
                        .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        }).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                openLink(linkable);
                            }
                        }).setTitle(R.string.open_link_confirmation).setMessage(linkable.value).create();

                dialog.show();
            } else {
                openLink(linkable);
            }
        } else if (linkable.type == PostLinkable.Type.SPOILER) {
            new AlertDialog.Builder(activity).setMessage(linkable.value).show();
        }
    }

    /**
     * When a linkable to a post has been clicked, show a dialog with the
     * referenced post in it.
     *
     * @param linkable the clicked linkable.
     */
    private void showPostReply(PostLinkable linkable) {
        String value = linkable.value;

        Post post;

        try {
            // Get post id
            String[] splitted = value.split("#p");
            if (splitted.length == 2) {
                int id = Integer.parseInt(splitted[1]);

                post = findPostById(id);

                if (post != null) {
                    List<Post> l = new ArrayList<>();
                    l.add(post);
                    showPostsRepliesFragment(l);
                }
            }
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
    }

    /**
     * Open an url.
     *
     * @param linkable Linkable with an url.
     */
    private void openLink(PostLinkable linkable) {
        Utils.openLink(activity, linkable.value);
    }

    private void showPostsRepliesFragment(List<Post> list) {
        // Post popups are now queued up, more than 32 popups on top of each
        // other makes the system crash!
        popupQueue.add(list);

        if (currentPopupFragment != null) {
            currentPopupFragment.dismissNoCallback();
        }

        PostRepliesFragment popup = PostRepliesFragment.newInstance(list, this);

        FragmentTransaction ft = activity.getFragmentManager().beginTransaction();
        ft.add(popup, "postPopup");
        ft.commitAllowingStateLoss();

        currentPopupFragment = popup;
    }

    public void onPostRepliesPop() {
        if (popupQueue.size() == 0)
            return;

        popupQueue.remove(popupQueue.size() - 1);

        if (popupQueue.size() > 0) {
            PostRepliesFragment popup = PostRepliesFragment.newInstance(popupQueue.get(popupQueue.size() - 1), this);

            FragmentTransaction ft = activity.getFragmentManager().beginTransaction();
            ft.add(popup, "postPopup");
            ft.commit();

            currentPopupFragment = popup;
        } else {
            currentPopupFragment = null;
        }
    }

    public void closeAllPostFragments() {
        popupQueue.clear();
        currentPopupFragment = null;
    }

    private void deletePost(final Post post) {
        final CheckBox view = new CheckBox(activity);
        view.setText(R.string.delete_image_only);
        int padding = activity.getResources().getDimensionPixelSize(R.dimen.general_padding);
        view.setPadding(padding, padding, padding, padding);

        new AlertDialog.Builder(activity).setTitle(R.string.delete_confirm).setView(view)
                .setPositiveButton(R.string.delete, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        doDeletePost(post, view.isChecked());
                    }
                }).setNegativeButton(R.string.cancel, new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        }).show();
    }

    private void doDeletePost(Post post, boolean onlyImageDelete) {
        SavedReply reply = ChanApplication.getDatabaseManager().getSavedReply(post.board, post.no);
        if (reply == null) {
            /*
             * reply = new SavedReply(); reply.board = "g"; reply.no = 1234;
             * reply.password = "boom";
             */
            return;
        }

        final ProgressDialog dialog = ProgressDialog.show(activity, null, activity.getString(R.string.delete_wait));

        ChanApplication.getReplyManager().sendDelete(reply, onlyImageDelete, new DeleteListener() {
            @Override
            public void onResponse(DeleteResponse response) {
                dialog.dismiss();

                if (response.isNetworkError || response.isUserError) {
                    int resId;

                    if (response.isTooSoonError) {
                        resId = R.string.delete_too_soon;
                    } else if (response.isInvalidPassword) {
                        resId = R.string.delete_password_incorrect;
                    } else if (response.isTooOldError) {
                        resId = R.string.delete_too_old;
                    } else {
                        resId = R.string.delete_fail;
                    }

                    Toast.makeText(activity, resId, Toast.LENGTH_LONG).show();
                } else if (response.isSuccessful) {
                    Toast.makeText(activity, R.string.delete_success, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(activity, R.string.delete_fail, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void updateLastSeen() {
        Pin pin = ChanApplication.getPinnedManager().findPinByLoadable(loader.getLoadable());
        if (pin != null) {
            Post last = pin.getLastSeenPost();
            if (last != null) {
                lastSeenPost = last.no;
            } else {
                lastSeenPost = -1;
            }
        }
    }

    public interface ThreadManagerListener {
        public void onThreadLoaded(List<Post> result, boolean append);

        public void onThreadLoadError(VolleyError error);

        public void onOPClicked(Post post);

        public void onThumbnailClicked(Post post);

        public void onScrollTo(Post post);
    }
}
