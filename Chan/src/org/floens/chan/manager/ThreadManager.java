package org.floens.chan.manager;

import java.util.ArrayList;

import org.floens.chan.R;
import org.floens.chan.entity.Loadable;
import org.floens.chan.entity.Post;
import org.floens.chan.net.ThreadLoader;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.Gravity;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;

import com.android.volley.NetworkError;
import com.android.volley.NoConnectionError;
import com.android.volley.ServerError;
import com.android.volley.VolleyError;

/**
 * All PostView's need to have this referenced. 
 * This manages some things like pages, starting and stopping of loading etc.
 */
public class ThreadManager {
    private final Context context;
    private final ThreadLoader threadLoader;
    private final ThreadManager.ThreadListener threadListener;
    private Loadable loadable;
    private boolean endOfLine = false;
    private final SparseArray<Post> postsById = new SparseArray<Post>();
    
    public ThreadManager(Context context, final ThreadListener listener) {
        this.context = context;
        threadListener = listener;
        
        threadLoader = new ThreadLoader(new ThreadLoader.ThreadLoaderListener() {
            @Override
            public void onError(VolleyError error) {
                listener.onThreadLoadError(error);
            }
            
            @Override
            public void onData(ArrayList<Post> result) {
                for (Post post : result) {
                    postsById.append(post.no, post);
                }
                
                listener.onThreadLoaded(result);
            }
        });
    }
    
    public Post getPostById(int id) {
        return postsById.get(id);
    }
    
    public Loadable getLoadable() {
        return loadable;
    }
    
    public void startLoading(Loadable loadable) {
        this.loadable = loadable;
               
        threadLoader.start(loadable);
    }
    
    public void stop() {
        threadLoader.stop();
        endOfLine = false;
        postsById.clear();
    }
    
    public void reload() {
        if (loadable == null) {
            Log.e("Chan", "ThreadManager: loadable null");
        } else {
            stop();
            
            if (loadable.isBoardMode()) {
                loadable.no = 0;
                loadable.listViewIndex = 0;
                loadable.listViewTop = 0;
            }
            
            startLoading(loadable);
        }
    }
    
    public void loadMore() {
        if (loadable == null) {
            Log.e("Chan", "ThreadManager: loadable null");
        } else {
            if (!endOfLine) {
                threadLoader.loadMore();
            }
        }
    }
    
    public void onThumbnailClicked(Post post) {
        threadListener.onThumbnailClicked(post);
    }
    
    public void onPostClicked(Post post) {
        threadListener.onPostClicked(post);
    }
    
    public void onPostLongClicked(final Post post) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        
        builder.setItems(R.array.post_options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch(which) {
                case 0: // Info
                    showPostInfo(post);
                case 1: // Quote
                    ReplyManager.getInstance().quote(post.no);
                    break;
                case 2: // Copy text
                    copyText(post.comment.toString());
                    break;
                }
            }
        });
        
        builder.create().show();
    }
    
    /**
     * Returns an TextView containing the appropiate error message
     * @param error
     * @return
     */
    public TextView getTextViewError(VolleyError error) {
        String errorMessage = "";
        
        if ((error instanceof NoConnectionError) || (error instanceof NetworkError)) {
            errorMessage = context.getString(R.string.thread_load_failed_network);
        } else if (error instanceof ServerError) {
            errorMessage = context.getString(R.string.thread_load_failed_server);
        } else {
            errorMessage = context.getString(R.string.thread_load_failed_parsing);
        }
        
        TextView view = new TextView(context);
        view.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        view.setText(errorMessage);
        view.setTextSize(24f);
        view.setGravity(Gravity.CENTER);
        
        return view;
    }
    
    private void copyText(String comment) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("post text", comment);
        clipboard.setPrimaryClip(clip);
    }
    
    private void showPostInfo(Post post) {
        String text = "Time: " + post.date;
        
        if (post.hasImage) {
            text += "\nFile: " + post.filename + " \nSize: " + post.imageWidth + "x" + post.imageHeight;
        }
        
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
        
        AlertDialog dialog = new AlertDialog.Builder(context)
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
    
    public interface ThreadListener {
        public void onThreadLoaded(ArrayList<Post> result);
        public void onThreadLoadError(VolleyError error);
        public void onPostClicked(Post post);
        public void onThumbnailClicked(Post post);
    }
}





