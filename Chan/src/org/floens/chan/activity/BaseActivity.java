package org.floens.chan.activity;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.adapter.PinnedAdapter;
import org.floens.chan.animation.SwipeDismissListViewTouchListener;
import org.floens.chan.animation.SwipeDismissListViewTouchListener.DismissCallbacks;
import org.floens.chan.model.Pin;
import org.floens.chan.model.Post;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnShowListener;
import android.content.Intent;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SlidingPaneLayout;
import android.support.v4.widget.SlidingPaneLayout.PanelSlideListener;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ShareActionProvider;

/**
 * Activities that use ThreadFragment need to extend BaseActivity.
 * BaseActivity provides callbacks for when the user clicks a post,
 * or clicks an item in the drawer.
 */
public abstract class BaseActivity extends Activity implements PanelSlideListener {
    private final static int ACTION_OPEN_URL = 1;
    
    protected DrawerLayout drawer;
    protected ListView drawerList;
    protected ActionBarDrawerToggle drawerListener;
    protected SlidingPaneLayout pane;
    protected ShareActionProvider shareActionProvider;
    
    /**
	 * Called when a post has been clicked in the pinned drawer
	 * @param post
	 */
	abstract public void onDrawerClicked(Pin post);
	
	/**
	 * Called when a post has been clicked in the listview
	 * @param post
	 */
	abstract public void onOPClicked(Post post);

	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.activity_base);
        
        drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        setupDrawer(drawer);
        
        pane = (SlidingPaneLayout) findViewById(R.id.pane_container);
        pane.setPanelSlideListener(this);
        pane.setParallaxDistance(200);
        pane.setShadowResource(R.drawable.panel_shadow);
        pane.setSliderFadeColor(0xcce5e5e5);
        pane.openPane();
    }
	
    protected void setupDrawer(DrawerLayout drawer) {
        if (drawerListener == null) {
            return;
        }
        
        drawer.setDrawerListener(drawerListener);
        drawer.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);
        
        drawerList = (ListView)findViewById(R.id.left_drawer);
        
        final PinnedAdapter adapter = ChanApplication.getPinnedManager().getAdapter();
        drawerList.setAdapter(adapter);
        
        drawerList.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Pin post = adapter.getItem(position);
                if (post == null || post.type == Pin.Type.HEADER) return;
                onDrawerClicked(post);
            }
        });
        
        drawerList.setOnItemLongClickListener(new OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
				Pin post = adapter.getItem(position);
                if (post == null || post.type == Pin.Type.HEADER) return false;
                
                changePinTitle(post);
				
				return true;
			}
		});
        
        SwipeDismissListViewTouchListener touchListener = new SwipeDismissListViewTouchListener(drawerList,
            new DismissCallbacks() {
                @Override
                public void onDismiss(ListView listView, int[] reverseSortedPositions) {
                    for (int position : reverseSortedPositions) {
                        ChanApplication.getPinnedManager().remove(adapter.getItem(position));
                    }
                }

                @Override
                public boolean canDismiss(int position) {
                    return adapter.getItem(position).type != Pin.Type.HEADER;
                }
            });
        
        drawerList.setOnTouchListener(touchListener);
        drawerList.setOnScrollListener(touchListener.makeScrollListener());
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
        case R.id.action_settings:
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.base, menu);
        return true;
    }
    
    @Override
	public void onPanelClosed(View view) {
	}

	@Override
	public void onPanelOpened(View view) {
	}

	@Override
	public void onPanelSlide(View view, float offset) {
	}
    
    /**
     * Set the url that Android Beam and the share action will send.
     * @param url
     */
    public void setShareUrl(String url) {
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(this);

        if (adapter != null) {
            NdefRecord record = null;
            try {
                record = NdefRecord.createUri(url);
            } catch(IllegalArgumentException e) {
                e.printStackTrace();
                return;
            }
            
            NdefMessage message = new NdefMessage(new NdefRecord[] {record});
            adapter.setNdefPushMessage(message, this);
        }
        
        if (shareActionProvider != null) {
        	Intent share = new Intent(android.content.Intent.ACTION_SEND);
    		share.putExtra(android.content.Intent.EXTRA_TEXT, url);
    		share.setType("text/plain");
        	shareActionProvider.setShareIntent(share);
        }
    }
    
    /**
     * Let the user choose between all activities that can open the url.
     * This is done to prevent "open in browser" opening the url in our own app.
     * @param url
     */
    public void openUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        
        Intent pickIntent = new Intent(Intent.ACTION_PICK_ACTIVITY);
        pickIntent.putExtra(Intent.EXTRA_INTENT, intent);
        
        startActivityForResult(pickIntent, ACTION_OPEN_URL);
    }
    
    /**
     * Used for openUrl
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == ACTION_OPEN_URL && resultCode == RESULT_OK && data != null) {
            data.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(data);
        }
    }

	private void changePinTitle(final Pin pin) {
		final EditText text = new EditText(this);
        text.setSingleLine();
        text.setText(pin.loadable.title);
        text.setSelectAllOnFocus(true);
        
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setPositiveButton(R.string.change, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                    String value = text.getText().toString();
                    
                    if (!TextUtils.isEmpty(value)) {
                        pin.loadable.title = value;
                        ChanApplication.getPinnedManager().update(pin);
                    }
                }
            })
            .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int which) {
                }
            })
            .setTitle(R.string.drawer_pinned_change_title)
            .setView(text)
            .create();
        
        text.requestFocus();
        
        dialog.setOnShowListener(new OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(text, 0);
            }
        });
        
        dialog.show();
	}
}





