package org.floens.chan.activity;

import java.util.ArrayList;
import java.util.List;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.entity.Loadable;
import org.floens.chan.entity.Pin;
import org.floens.chan.entity.Post;
import org.floens.chan.fragment.ReplyFragment;
import org.floens.chan.fragment.ThreadFragment;
import org.floens.chan.net.ChanUrls;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.widget.DrawerLayout;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ShareActionProvider;

public class BoardActivity extends BaseActivity implements ActionBar.OnNavigationListener {
	private Loadable boardLoadable = new Loadable();
	private Loadable threadLoadable = new Loadable();
    private ThreadFragment boardFragment;
    private ThreadFragment threadFragment;
    private boolean boardSetByIntent = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        boardLoadable.mode = Loadable.Mode.BOARD;
        threadLoadable.mode =  Loadable.Mode.THREAD;
        
        boardFragment = ThreadFragment.newInstance(this);
        threadFragment = ThreadFragment.newInstance(this);
        
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.replace(R.id.left_pane, boardFragment);
        ft.replace(R.id.right_pane, threadFragment);
        ft.commitAllowingStateLoss();
        
        updateActionBarState();
        
        final ActionBar actionBar = getActionBar();
        actionBar.setListNavigationCallbacks(
                new ArrayAdapter<String>(
                    actionBar.getThemedContext(), 
                    R.layout.board_select_spinner,
                    android.R.id.text1,
                    ChanApplication.getBoardManager().getMyBoardsKeys()
                ), this);
        
        Intent startIntent = getIntent();
        Uri startUri = startIntent.getData();
        
        if (savedInstanceState != null) {
            boardLoadable.readFromBundle(this, savedInstanceState);
            boardLoadable.no = 0;
            boardLoadable.listViewIndex = 0;
            boardLoadable.listViewTop = 0;
            setNavigationFromBoardValue(boardLoadable.board);
        } else if (startUri != null) {
            handleIntentURI(startUri);
        } else {
            getActionBar().setSelectedNavigationItem(0);
        }
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        
        boardLoadable.writeToBundle(this, outState);
    }
    
    @Override
    protected void setupDrawer(DrawerLayout drawer) {
        drawerListener = new ActionBarDrawerToggle(this, drawer, R.drawable.ic_drawer, R.string.drawer_open, R.string.drawer_close) {};
        
        super.setupDrawer(drawer);
    }

    @Override
    public boolean onNavigationItemSelected(int position, long id) {
        if (!boardSetByIntent) {
            boardLoadable = new Loadable(ChanApplication.getBoardManager().getMyBoardsValues().get(position));
            startLoadingBoard(boardLoadable);
        }
        
        boardSetByIntent = false;
        
        return true;
    }
    
    @Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    super.onCreateOptionsMenu(menu);
	    
	    shareActionProvider = (ShareActionProvider) menu.findItem(R.id.action_share).getActionProvider();
	    
	    return true;
	}

	@Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        drawerListener.onConfigurationChanged(newConfig);
    }

    @Override
    public void onDrawerClicked(Pin pin) {
    	startLoadingThread(pin.loadable);
        
        drawer.closeDrawer(drawerList);
    }

    @Override
    public void onOPClicked(Post post) {
    	startLoadingThread(new Loadable(post.board, post.no, post.subject));
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        drawerListener.syncState();
    }
    
    @Override
    public void onBackPressed() {
    	if (pane.isOpen()) {
    		super.onBackPressed();
    	} else {
    		pane.openPane();
    	}
    }
    
    private void updateActionBarState() {
    	final ActionBar actionBar = getActionBar();
    	
    	if (pane.isSlideable()) {
	    	if (pane.isOpen()) {
	            actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
	            actionBar.setHomeButtonEnabled(true);
	            actionBar.setTitle("");
	            drawerListener.setDrawerIndicatorEnabled(true);
	    	} else {
	    		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
	    		actionBar.setTitle(threadLoadable.title);
	    		drawerListener.setDrawerIndicatorEnabled(false);
	    	}
	    	
	    	actionBar.setDisplayHomeAsUpEnabled(true);
    	} else {
            actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
            drawerListener.setDrawerIndicatorEnabled(true);
    		actionBar.setTitle(threadLoadable.title);
    		
    		actionBar.setDisplayHomeAsUpEnabled(false);
    	}
    	
    	actionBar.setDisplayShowTitleEnabled(true);
        
        invalidateOptionsMenu();
    }
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
    	boolean open = pane.isOpen();
    	
    	setMenuItemEnabled(menu.findItem(R.id.action_pin), !open);
    	
    	return super.onPrepareOptionsMenu(menu);
    }
    
    private void setMenuItemEnabled(MenuItem item, boolean enabled) {
    	if (item != null) {
    		item.setVisible(enabled);
    		item.setEnabled(enabled);
    	}
    }
    
    @Override
	public boolean onOptionsItemSelected(MenuItem item) {
	    if (drawerListener.onOptionsItemSelected(item)) {
	        return true;
	    }
	    
	    switch(item.getItemId()) {
	    case R.id.action_reload:
	    	if (pane.isOpen()) {
	    		boardFragment.reload();
	    	} else {
	    		if (threadFragment.getThreadManager().hasThread()) {
	    			threadFragment.reload();
	    		}
	    	}
	        return true;
	    case R.id.action_reply:
	    	if (pane.isOpen()) {
		        startReply(pane.isSlideable(), boardFragment);
	    	} else {
	    		if (threadFragment.getThreadManager().hasThread()) {
			        startReply(pane.isSlideable(), threadFragment);
	    		}
	    	}
	        
	        return true;
	    case R.id.action_pin:
	    	if (threadFragment.getThreadManager().hasThread()) {
	    		Pin pin = new Pin();
	            pin.loadable = threadLoadable;
	            
	            ChanApplication.getPinnedManager().add(pin);
	            
	            drawer.openDrawer(drawerList);
	    	}
            
            return true;
	    case R.id.action_open_browser:
	    	if (pane.isOpen()) {
	    		openUrl(ChanUrls.getBoardUrlDesktop(boardLoadable.board));
	    	} else {
	    		if (threadFragment.getThreadManager().hasThread()) {
	    			openUrl(ChanUrls.getThreadUrlDesktop(threadLoadable.board, threadLoadable.no));
	    		}
	    	}
	        
	        return true;
	    case android.R.id.home:
	    	pane.openPane();
	    	
	    	return true;
	    }
	    
	    return super.onOptionsItemSelected(item);
	}

	@Override
	public void onPanelClosed(View view) {
		updateActionBarState();
	}

	@Override
	public void onPanelOpened(View view) {
		updateActionBarState();
	}
	
	private void startReply(boolean inActivity, ThreadFragment fragment) {
		if (inActivity) {
			ReplyActivity.setThreadFragment(fragment);
			Intent i = new Intent(this, ReplyActivity.class);
			startActivity(i);
		} else {
			ReplyFragment reply = ReplyFragment.newInstance(fragment);
	        reply.show(getFragmentManager(), "replyDialog");			
		}
	}

	private void startLoadingBoard(Loadable loadable) {
    	this.boardLoadable = loadable;
    	
        boardFragment.startLoading(loadable);
        setShareUrl(ChanUrls.getBoardUrlDesktop(loadable.board));
        
        updateActionBarState();
    }
    
    private void startLoadingThread(Loadable loadable) {
    	Pin pin = ChanApplication.getPinnedManager().findPinByLoadable(loadable);
        if (pin != null) {
            // Use the loadable from the pin.
            // This way we can store the listview position in the pin loadable, 
            // and not in a separate loadable instance.
            loadable = pin.loadable; 
        }
        
        threadLoadable = loadable;
        
        threadFragment.startLoading(loadable);
        
        setShareUrl(ChanUrls.getThreadUrlDesktop(loadable.board, loadable.no));
        
        if (TextUtils.isEmpty(loadable.title)) {
            loadable.title = "/" + loadable.board + "/" + loadable.no;
        }
        
        pane.closePane();
        
        updateActionBarState();
    }
    
    /**
     * Handle opening from an external url.
     * @param startUri
     */
    private void handleIntentURI(Uri startUri) {
        List<String> parts = startUri.getPathSegments();
        
        if (parts.size() == 1) {
            // Board mode
            String rawBoard = parts.get(0); 
            
            if (ChanApplication.getBoardManager().getBoardExists(rawBoard)) {
                boardLoadable.board = rawBoard;
                boardSetByIntent = true;
                startLoadingBoard(boardLoadable);
                
                ActionBar actionBar = getActionBar();
                if (!setNavigationFromBoardValue(rawBoard)) {
                    actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
                    actionBar.setDisplayShowTitleEnabled(true);
                    String value = ChanApplication.getBoardManager().getBoardKey(rawBoard);
                    actionBar.setTitle(value == null ? ("/" + rawBoard + "/") : value);
                }
                
            } else {
                handleIntentURIFallback(startUri.toString());
            }
        } else if (parts.size() == 3) {
            // Thread mode
            // First load a board and then start another activity opening the thread
            String rawBoard = parts.get(0);
            int no = -1;
            
            try {
                no = Integer.parseInt(parts.get(2));
            } catch (NumberFormatException e) {}
            
            if (no >= 0 && ChanApplication.getBoardManager().getBoardExists(rawBoard)) {
                boardLoadable.board = rawBoard;
                boardSetByIntent = true;
                startLoadingBoard(boardLoadable);
                
                startLoadingThread(new Loadable(rawBoard, no));
                finish();
            } else {
                handleIntentURIFallback(startUri.toString());
                return;
            }
        } else {
            openUrl(startUri.toString());
        }
    }
    
    private void handleIntentURIFallback(final String url) {
        new AlertDialog.Builder(this)
            .setTitle(R.string.open_unknown_title)
            .setMessage(R.string.open_unknown)
            .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // Cancel button
                    finish();
                }
            })
            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    // Ok button
                    openUrl(url);
                }
            })
            .setCancelable(false)
            .create()
            .show();
    }
    
    /**
     * Set the visual selector to the board. If the user has not set the board as a favorite,
     * return false.
     * @param boardValue
     * @return true if spinner was set, false otherwise
     */
    private boolean setNavigationFromBoardValue(String boardValue) {
        ArrayList<String> list = ChanApplication.getBoardManager().getMyBoardsValues();
        int foundIndex = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).equals(boardValue)) {
                foundIndex = i;
                break;
            }
        }
        
        if (foundIndex >= 0) {
            getActionBar().setSelectedNavigationItem(foundIndex);
            return true;
        } else {
            return false;
        }
    }
}



