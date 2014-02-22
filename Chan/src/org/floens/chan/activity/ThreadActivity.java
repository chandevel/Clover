package org.floens.chan.activity;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.entity.Loadable;
import org.floens.chan.entity.Pin;
import org.floens.chan.entity.Post;
import org.floens.chan.fragment.ReplyFragment;
import org.floens.chan.fragment.ThreadFragment;
import org.floens.chan.net.ChanUrls;

import android.app.FragmentTransaction;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;

public class ThreadActivity extends BaseActivity {
	private Loadable loadable = new Loadable();
	
    private ThreadFragment threadFragment;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        loadable.mode = Loadable.Mode.THREAD;
        
        threadFragment = ThreadFragment.newInstance(this);
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.replace(R.id.right_pane, threadFragment);
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
        
        startLoading(loadable);
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        
        loadable.writeToBundle(this, outState);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
//        getMenuInflater().inflate(R.menu.thread, menu);
        return super.onCreateOptionsMenu(menu);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
        case R.id.action_reload:
            threadFragment.reload();
            
            return true;
        case R.id.action_reply:
            ReplyFragment reply = ReplyFragment.newInstance(loadable);
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

    private void startLoading(Loadable loadable) {
    	Pin pin = ChanApplication.getPinnedManager().findPinByLoadable(loadable);
        if (pin != null) {
            // Use the loadable from the pin.
            // This way we can store the listview position in the pin loadable, 
            // and not in a separate loadable instance.
            loadable = pin.loadable; 
        }
        
        threadFragment.startLoading(loadable);
        
        setShareUrl(ChanUrls.getThreadUrlDesktop(loadable.board, loadable.no));
        
        if (TextUtils.isEmpty(loadable.title)) {
            loadable.title = "/" + loadable.board + "/" + loadable.no;
        }
        
        getActionBar().setTitle(loadable.title);
    }

	@Override
	public void onOPClicked(Post post) {
		threadFragment.getThreadManager().showPostLinkables(post);
	}
}
