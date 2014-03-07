package org.floens.chan.manager;

import java.util.ArrayList;
import java.util.List;

import org.floens.chan.R;
import org.floens.chan.activity.ReplyActivity;
import org.floens.chan.database.DatabaseManager;
import org.floens.chan.fragment.PostRepliesFragment;
import org.floens.chan.fragment.ReplyFragment;
import org.floens.chan.manager.ReplyManager.DeleteListener;
import org.floens.chan.manager.ReplyManager.DeleteResponse;
import org.floens.chan.model.Loadable;
import org.floens.chan.model.Pin;
import org.floens.chan.model.Post;
import org.floens.chan.model.PostLinkable;
import org.floens.chan.model.SavedReply;
import org.floens.chan.net.ThreadLoader;
import org.floens.chan.utils.ChanPreferences;
import org.floens.chan.utils.Logger;
import org.floens.chan.watch.WatchLogic;
import org.floens.chan.watch.WatchLogic.WatchListener;

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
import android.net.Uri;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.CheckBox;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.NetworkError;
import com.android.volley.NoConnectionError;
import com.android.volley.ServerError;
import com.android.volley.VolleyError;

/**
 * All PostView's need to have this referenced. 
 * This manages some things like pages, starting and stopping of loading, 
 * handling linkables, replies popups etc.
 * onDestroy, onPause and onResume must be called from the activity/fragment
 */
public class ThreadManager implements ThreadLoader.ThreadLoaderListener, WatchListener {
    private static final String TAG = "ThreadManager";
    
    private final Activity activity;
    private final ThreadLoader threadLoader;
    private final ThreadManager.ThreadListener threadListener;
    private Loadable loadable;
    private boolean endOfLine = false;
    private WatchLogic watchLogic;
    
    private final List<List<Post>> popupQueue = new ArrayList<List<Post>>();
    private PostRepliesFragment currentPopupFragment;
    
    public ThreadManager(Activity context, final ThreadListener listener) {
        this.activity = context;
        threadListener = listener;
        
        threadLoader = new ThreadLoader(this);
    }
    
    public void onDestroy() {
        if (watchLogic != null) {
            watchLogic.destroy();
        }
    }
    
    public void onPause() {
        if (watchLogic != null) {
            watchLogic.stopTimer();
        }
    }
    
    public void onResume() {
        if (watchLogic != null) {
            watchLogic.startTimer();
        }
    }
    
    @Override
    public void onWatchReloadRequested() {
        Logger.d(TAG, "Reload requested");
        
        if (!threadLoader.isLoading()) {
            threadLoader.start(loadable);
        }
    }
    
    public WatchLogic getWatchLogic() {
        return watchLogic;
    }

    @Override
    public void onError(VolleyError error) {
        threadListener.onThreadLoadError(error);
        
        if (watchLogic != null) {
            watchLogic.stopTimer();
        }
    }
    
    @Override
    public void onData(List<Post> result) {
        if (watchLogic != null) {
            watchLogic.onLoaded(result.size());
        }
        
        threadListener.onThreadLoaded(result);
    }
    
    public boolean hasLoadable() {
        return loadable != null;
    }
    
    public Post findPostById(int id) {
        return threadLoader.getPostById(id);
    }
    
    public Loadable getLoadable() {
        return loadable;
    }
    
    public void startLoading(Loadable loadable) {
        this.loadable = loadable;
        
        stop();
        
        threadLoader.start(loadable);
        
        Pin pin = PinnedManager.getInstance().findPinByLoadable(loadable);
        if (pin != null) {
            PinnedManager.getInstance().onPinViewed(pin);
        }
        
        if (watchLogic != null) {
            watchLogic.destroy();
            watchLogic = null;
        }
        
        if (loadable.isThreadMode()) {
            watchLogic = new WatchLogic(this);
            watchLogic.startTimer();
        }
    }
    
    public void stop() {
        threadLoader.stop();
        endOfLine = false;
        
        if (watchLogic != null) {
            watchLogic.destroy();
            watchLogic = null;
        }
    }
    
    public void reload() {
        if (loadable == null) {
            Logger.e(TAG, "ThreadManager: loadable null");
        } else {
            if (loadable.isBoardMode()) {
                loadable.no = 0;
                loadable.listViewIndex = 0;
                loadable.listViewTop = 0;
            }
            
            startLoading(loadable);
        }
    }
    
    public void loadMore() {
        if (threadLoader.isLoading()) return;
        
        if (loadable == null) {
            Logger.e(TAG, "ThreadManager: loadable null");
        } else {
            if (loadable.isBoardMode()) {
                if (!endOfLine) {
                    threadLoader.loadMore();
                }
            } else if (loadable.isThreadMode()) {
                if (watchLogic != null) {
                    watchLogic.loadNow();
                }
            }
        }
    }
    
    public void onThumbnailClicked(Post post) {
        threadListener.onThumbnailClicked(post);
    }
    
    public void onPostClicked(Post post) {
        if (loadable.isBoardMode()) {
            threadListener.onPostClicked(post);
        }
    }
    
    public void onPostLongClicked(final Post post) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        
        String[] items = null;
        
        String[] temp = activity.getResources().getStringArray(R.array.post_options);
        // Only add the delete option when the post is a saved reply
        if (DatabaseManager.getInstance().isSavedReply(post.board, post.no)) {
            items = new String[temp.length + 1];
            System.arraycopy(temp, 0, items, 0, temp.length);
            items[items.length - 1] = activity.getString(R.string.delete);
        } else {
            items = temp;
        }
        
        builder.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch(which) {
                case 0: // Reply
                    openReply(true); // todo if tablet
                    // Pass through
                case 1: // Quote
                    ReplyManager.getInstance().quote(post.no);
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
                    deletePost(post);
                    break;
                }
            }
        });
        
        builder.create().show();
    }
    
    public void openReply(boolean startInActivity) {
        if (startInActivity) {
            ReplyActivity.setLoadable(loadable);
            Intent i = new Intent(activity, ReplyActivity.class);
            activity.startActivity(i);
        } else {
            ReplyFragment reply = ReplyFragment.newInstance(loadable);
            reply.show(activity.getFragmentManager(), "replyDialog");            
        }
    }
    
    public void onPostLinkableClicked(PostLinkable linkable) {
        handleLinkableSelected(linkable);
    }
    
    /**
     * Returns an TextView containing the appropriate error message
     * @param error
     * @return
     */
    public TextView getLoadErrorTextView(VolleyError error) {
        String errorMessage = "";
        
        if ((error instanceof NoConnectionError) || (error instanceof NetworkError)) {
            errorMessage = activity.getString(R.string.thread_load_failed_network);
        } else if (error instanceof ServerError) {
            errorMessage = activity.getString(R.string.thread_load_failed_server);
        } else {
            errorMessage = activity.getString(R.string.thread_load_failed_parsing);
        }
        
        TextView view = new TextView(activity);
        view.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        view.setText(errorMessage);
        view.setTextSize(24f);
        view.setGravity(Gravity.CENTER);
        
        return view;
    }
    
    private void copyToClipboard(String comment) {
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Post text", comment);
        clipboard.setPrimaryClip(clip);
    }
    
    private void showPostInfo(Post post) {
        String text = "";
        
        if (post.hasImage) {
            text += "File: " + post.filename + " \nSize: " + post.imageWidth + "x" + post.imageHeight + "\n\n";
        }
        
        text += "Time: " + post.date ;
        
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
        
        AlertDialog dialog = new AlertDialog.Builder(activity)
            .setTitle(R.string.post_info)
            .setMessage(text)
            .setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                }
            })
            .create();
        
        dialog.show();
    }
    
    /**
     * When the user clicks a post:
     * a. when there's one linkable, open the linkable.
     * b. when there's more than one linkable, show the user multiple options to select from.
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
        List<Post> p = new ArrayList<Post>();
        for (int no : post.repliesFrom) {
            Post r = findPostById(no);
            if (r != null) {
                p.add(r);
            }
        }
        
        if (p.size() > 0) {
            showPostsReplies(p);
        }
    }
    
    /**
     * Handle when a linkable has been clicked.
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
                    })
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            openLink(linkable);
                        }
                    })
                    .setTitle(R.string.open_link_confirmation)
                    .setMessage(linkable.value)
                    .create();
                
                dialog.show();
            } else {
                openLink(linkable);
            }
        }
    }
    
    /**
     * When a linkable to a post has been clicked, 
     * show a dialog with the referenced post in it.
     * @param linkable the clicked linkable.
     */
    private void showPostReply(PostLinkable linkable) {
        String value = linkable.value;
        
        Post post = null;
        
        try {
            // Get post id
            String[] splitted = value.split("#p");
            if (splitted.length == 2) {
                int id = Integer.parseInt(splitted[1]);
                
                post = findPostById(id);
                
                if (post != null) {
                    List<Post> l = new ArrayList<Post>();
                    l.add(post);
                    showPostsReplies(l);
                }
            }
        } catch(NumberFormatException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Open an url.
     * @param linkable Linkable with an url.
     */
    private void openLink(PostLinkable linkable) {
        activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(linkable.value)));
    }
    
    private void showPostsReplies(List<Post> list) {
        // Post popups are now queued up, more than 32 popups on top of each other makes the system crash! 
        popupQueue.add(list);
        
        if (currentPopupFragment != null) {
            currentPopupFragment.dismissNoCallback();
        }
        
        PostRepliesFragment popup = PostRepliesFragment.newInstance(list, this);
        
        FragmentTransaction ft = activity.getFragmentManager().beginTransaction();
        ft.add(popup, "postPopup");
        ft.commit();
        
        currentPopupFragment = popup;
    }
    
    public void onPostRepliesPop() {
        if (popupQueue.size() == 0) return;
        
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
        
        new AlertDialog.Builder(activity)
            .setTitle(R.string.delete_confirm)
            .setView(view)
            .setPositiveButton(R.string.delete, new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    doDeletePost(post, view.isChecked());
                }
            })
            .setNegativeButton(R.string.cancel, new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                }
            })
            .show();
    }
    
    private void doDeletePost(Post post, boolean onlyImageDelete) {
        SavedReply reply = DatabaseManager.getInstance().getSavedReply(post.board, post.no);
        if (reply == null) {
            /*reply = new SavedReply();
            reply.board = "g";
            reply.no = 1234;
            reply.password = "boom";*/
            return;
        }
        
        final ProgressDialog dialog = ProgressDialog.show(activity, null, activity.getString(R.string.delete_wait));
        
        ReplyManager.getInstance().sendDelete(reply, onlyImageDelete, new DeleteListener() {
            @Override
            public void onResponse(DeleteResponse response) {
                dialog.dismiss();
                
                if (response.isNetworkError || response.isUserError) {
                    int resId = 0;
                    
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
    
    public interface ThreadListener {
        public void onThreadLoaded(List<Post> result);
        public void onThreadLoadError(VolleyError error);
        public void onPostClicked(Post post);
        public void onThumbnailClicked(Post post);
    }
}
