package org.floens.chan.activity;

import java.util.ArrayList;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.entity.Loadable;
import org.floens.chan.entity.Pin;
import org.floens.chan.entity.Post;
import org.floens.chan.entity.PostLinkable;
import org.floens.chan.fragment.PostPopupFragment;
import org.floens.chan.fragment.ReplyFragment;
import org.floens.chan.fragment.ThreadFragment;
import org.floens.chan.net.ChanUrls;

import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;

public class ThreadActivity extends BaseActivity {
    private ThreadFragment threadFragment;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        loadable.mode = Loadable.Mode.THREAD;
        
        threadFragment = ThreadFragment.newInstance(this);
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.replace(R.id.container, threadFragment);
        ft.commitAllowingStateLoss();
        
        Bundle bundle = getIntent().getExtras();
        
        // Favor savedInstanceState bundle over intent bundle:
        // the intent bundle may be old, for example when the user
        // switches the loadable through the drawer.
        if (savedInstanceState != null) {
            loadable.readFromBundle(this, savedInstanceState);
        } else if (bundle != null) {
            loadable.readFromBundle(this, bundle);
        } else {
            finish();
        }
        
        Pin pin = ChanApplication.getPinnedManager().findPinByLoadable(loadable);
        if (pin != null) {
            // Use the loadable from the pin.
            // This way we can store the listview position in the pin loadable, 
            // and not in a separate loadable instance.
            loadable = pin.loadable; 
        }
        
        startLoading(loadable);
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        
        loadable.writeToBundle(this, outState);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.thread, menu);
        return super.onCreateOptionsMenu(menu);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
        case R.id.action_reload:
            threadFragment.reload();
            
            return true;
        case R.id.action_reply:
            ReplyFragment reply = ReplyFragment.newInstance(threadFragment);
            reply.show(getFragmentManager(), "replyDialog");
            
            return true;
        case R.id.action_pin:
            Pin pin = new Pin();
            pin.loadable = loadable;
            
            ChanApplication.getPinnedManager().add(pin);
            
            drawer.openDrawer(drawerList);
            
            return true;
        case R.id.action_open_browser:
            openUrl(ChanUrls.getThreadUrlDesktop(loadable.board, loadable.no));
            
            return true;
        case android.R.id.home:
            finish();
            
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onDrawerClicked(Pin pin) {
        drawer.closeDrawer(drawerList);
        
        loadable = pin.loadable;
        
        startLoading(loadable);
    }

    /**
     * When the user clicks a post:
     * a. when there's one linkable, open the linkable.
     * b. when there's more than one linkable, show the user multiple options to select from.
     * @param post The post that was clicked.
     */
    @Override
    public void onPostClicked(Post post) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
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

    private void startLoading(Loadable loadable) {
        threadFragment.startLoading(loadable);
        
        setNfcPushUrl(ChanUrls.getThreadUrlDesktop(loadable.board, loadable.no));
        
        if (TextUtils.isEmpty(loadable.title)) {
            loadable.title = "/" + loadable.board + "/" + loadable.no;
        }
        
        getActionBar().setTitle(loadable.title);
    }
    
    /**
     * Handle when a linkable has been clicked.
     * @param linkable the selected linkable.
     */
    private void handleLinkableSelected(final PostLinkable linkable) {
        if (linkable.type == PostLinkable.Type.QUOTE) {
            showPostPopup(linkable);
        } else if (linkable.type == PostLinkable.Type.LINK) {
            if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("preference_open_link_confirmation", true)) {
                AlertDialog dialog = new AlertDialog.Builder(this)
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
    private void showPostPopup(PostLinkable linkable) {
        String value = linkable.value;
        
        Post post = null;
        
        try {
            // Get post id
            String[] splitted = value.split("#p");
            if (splitted.length == 2) {
                int id = Integer.parseInt(splitted[1]);
                
                post = threadFragment.getThreadManager().getPostById(id);
                
                if (post != null) {
                    PostPopupFragment popup = PostPopupFragment.newInstance(post, threadFragment.getThreadManager());
                    
                    FragmentTransaction ft = getFragmentManager().beginTransaction();
                    ft.add(popup, "postPopup");
                    ft.commitAllowingStateLoss();
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
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(linkable.value)));
    }
}
