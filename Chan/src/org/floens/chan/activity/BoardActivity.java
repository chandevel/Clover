package org.floens.chan.activity;

import java.util.ArrayList;
import java.util.List;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.entity.Loadable;
import org.floens.chan.entity.Loadable.Mode;
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
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;

public class BoardActivity extends BaseActivity implements ActionBar.OnNavigationListener {
    private ThreadFragment threadFragment;
    private boolean boardSetByIntent = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        loadable.mode = Loadable.Mode.BOARD;
        
        threadFragment = ThreadFragment.newInstance(this);
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.replace(R.id.container, threadFragment);
        ft.commitAllowingStateLoss();
        
        final ActionBar actionBar = getActionBar();
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
        actionBar.setHomeButtonEnabled(true);
        
        actionBar.setListNavigationCallbacks(
            new ArrayAdapter<String>(
                actionBar.getThemedContext(), 
                android.R.layout.simple_list_item_1,
                android.R.id.text1,
                ChanApplication.getBoardManager().getMyBoardsKeys()
            ), this);
        
        Intent startIntent = getIntent();
        Uri startUri = startIntent.getData();
        
        if (savedInstanceState != null) {
            loadable.readFromBundle(this, savedInstanceState);
            loadable.no = 0;
            loadable.listViewIndex = 0;
            loadable.listViewTop = 0;
            setNavigationFromBoardValue(loadable.board);
        } else if (startUri != null) {
            handleStartURI(startUri);
        } else {
            actionBar.setSelectedNavigationItem(0);
        }
    }
    
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        
        loadable.writeToBundle(this, outState);
    }
    
    @Override
    protected void setupDrawer(DrawerLayout drawer) {
        drawerListener = new ActionBarDrawerToggle(this, drawer, R.drawable.ic_drawer, R.string.drawer_open, R.string.drawer_close) {};
        
        super.setupDrawer(drawer);
    }

    @Override
    public boolean onNavigationItemSelected(int position, long id) {
        if (!boardSetByIntent) {
            loadable = new Loadable(ChanApplication.getBoardManager().getMyBoardsValues().get(position));
            startLoading(loadable);
        }
        
        boardSetByIntent = false;
        
        return true;
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            if (((ActionBarDrawerToggle) drawerListener).onOptionsItemSelected(item)) {
                return true;
            }
            
            switch(item.getItemId()) {
            case R.id.action_catalog:
                Loadable l = new Loadable();
                l.mode = Mode.CATALOG;
                l.board = loadable.board;
                startCatalogActivity(l);
                
                return true;
            case R.id.action_reload:
                threadFragment.reload();
                return true;
            case R.id.action_reply:
                ReplyFragment reply = ReplyFragment.newInstance(threadFragment);
                reply.show(getFragmentManager(), "replyDialog");
                
                return true;
            case R.id.action_open_browser:
                openUrl(ChanUrls.getBoardUrlDesktop(loadable.board));
                
                return true;
            }
            
            return super.onOptionsItemSelected(item);
        }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        ((ActionBarDrawerToggle) drawerListener).onConfigurationChanged(newConfig);
    }

    @Override
    public void onDrawerClicked(Pin pin) {
        startThreadActivity(pin.loadable);
        
        drawer.closeDrawer(drawerList);
    }

    @Override
    public void onPostClicked(Post post) {
        startThreadActivity(new Loadable(post.board, post.no, post.subject));
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        ((ActionBarDrawerToggle) drawerListener).syncState();
    }

    private void startLoading(Loadable loadable) {
        threadFragment.startLoading(loadable);
        setNfcPushUrl(ChanUrls.getBoardUrlDesktop(loadable.board));
    }
    
    private void startThreadActivity(Loadable loadable) {
        Intent intent = new Intent(this, ThreadActivity.class);
        
        Bundle bundle = new Bundle();
        loadable.writeToBundle(this, bundle);
        
        intent.putExtras(bundle);
        
        startActivity(intent);
    }
    
    private void startCatalogActivity(Loadable loadable) {
        Intent intent = new Intent(this, CatalogActivity.class);
        
        Bundle bundle = new Bundle();
        loadable.writeToBundle(this, bundle);
        
        intent.putExtras(bundle);
        
        startActivity(intent);
    }
    
    /**
     * Handle opening from an external url.
     * @param startUri
     */
    private void handleStartURI(Uri startUri) {
        List<String> parts = startUri.getPathSegments();
        
        if (parts.size() == 1) {
            // Board mode
            String rawBoard = parts.get(0); 
            
            if (ChanApplication.getBoardManager().getBoardExists(rawBoard)) {
                loadable.board = rawBoard;
                boardSetByIntent = true;
                startLoading(loadable);
                
                ActionBar actionBar = getActionBar();
                if (!setNavigationFromBoardValue(rawBoard)) {
                    actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
                    actionBar.setDisplayShowTitleEnabled(true);
                    String value = ChanApplication.getBoardManager().getBoardKey(rawBoard);
                    actionBar.setTitle(value == null ? ("/" + rawBoard + "/") : value);
                }
                
            } else {
                fallbackOpenUrl(startUri.toString());
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
                loadable.board = rawBoard;
                boardSetByIntent = true;
                startLoading(loadable);
                
                startThreadActivity(new Loadable(rawBoard, no));
                finish();
            } else {
                fallbackOpenUrl(startUri.toString());
                return;
            }
        } else {
            openUrl(startUri.toString());
        }
    }
    
    private void fallbackOpenUrl(final String url) {
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



