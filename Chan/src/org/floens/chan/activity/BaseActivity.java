package org.floens.chan.activity;

import org.floens.chan.R;
import org.floens.chan.adapter.PinnedAdapter;
import org.floens.chan.animation.SwipeDismissListViewTouchListener;
import org.floens.chan.animation.SwipeDismissListViewTouchListener.DismissCallbacks;
import org.floens.chan.manager.PinnedManager;
import org.floens.chan.model.Pin;
import org.floens.chan.model.Post;
import org.floens.chan.utils.Logger;

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

public abstract class BaseActivity extends Activity implements PanelSlideListener, PinnedManager.PinListener {
    private final static int ACTION_OPEN_URL = 1;

    protected PinnedAdapter pinnedAdapter;
    protected DrawerLayout pinDrawer;
    protected ListView pinDrawerView;
    protected ActionBarDrawerToggle pinDrawerListener;

    protected SlidingPaneLayout threadPane;

    private ShareActionProvider shareActionProvider;

    /**
     * Called when a post has been clicked in the pinned drawer
     * @param post
     */
    abstract public void openPin(Pin post);

    /**
     * Called when a post has been clicked in the listview
     * @param post
     */
    abstract public void onOPClicked(Post post);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_base);

        pinDrawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        initDrawer();

        threadPane = (SlidingPaneLayout) findViewById(R.id.pane_container);
        initPane();

        PinnedManager.getInstance().addPinListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        PinnedManager.getInstance().removePinListener(this);
    }

    private void initPane() {
        threadPane.setPanelSlideListener(this);
        threadPane.setParallaxDistance(200);
        threadPane.setShadowResource(R.drawable.panel_shadow);
        threadPane.setSliderFadeColor(0xcce5e5e5);
        threadPane.openPane();
    }

    protected void initDrawer() {
        if (pinDrawerListener == null) {
            return;
        }

        pinDrawer.setDrawerListener(pinDrawerListener);
        pinDrawer.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);

        pinDrawerView = (ListView)findViewById(R.id.left_drawer);

        pinnedAdapter = new PinnedAdapter(getActionBar().getThemedContext(), 0); // Get the dark theme, not the light one
        pinnedAdapter.reload();
        pinDrawerView.setAdapter(pinnedAdapter);

        pinDrawerView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Pin pin = pinnedAdapter.getItem(position);
                if (pin == null || pin.type == Pin.Type.HEADER) return;
                openPin(pin);
            }
        });

        pinDrawerView.setOnItemLongClickListener(new OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                Pin post = pinnedAdapter.getItem(position);
                if (post == null || post.type == Pin.Type.HEADER) return false;

                changePinTitle(post);

                return true;
            }
        });

        SwipeDismissListViewTouchListener touchListener = new SwipeDismissListViewTouchListener(pinDrawerView,
            new DismissCallbacks() {
                @Override
                public void onDismiss(ListView listView, int[] reverseSortedPositions) {
                    for (int position : reverseSortedPositions) {
                        removePin(pinnedAdapter.getItem(position));
                    }
                }

                @Override
                public boolean canDismiss(int position) {
                    return pinnedAdapter.getItem(position).type != Pin.Type.HEADER;
                }
            });

        pinDrawerView.setOnTouchListener(touchListener);
        pinDrawerView.setOnScrollListener(touchListener.makeScrollListener());
    }

    @Override
    public void onPinsChanged() {
        pinnedAdapter.reload();
        pinDrawerView.invalidate();
        Logger.test("onPinsChanged");
    }

    public void addPin(Pin pin) {
        PinnedManager.getInstance().add(pin);
    }

    public void removePin(Pin pin) {
        PinnedManager.getInstance().remove(pin);
    }

    public void updatePin(Pin pin) {
        PinnedManager.getInstance().update(pin);
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
                        updatePin(pin);
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
        shareActionProvider = (ShareActionProvider) menu.findItem(R.id.action_share).getActionProvider();

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
    public void showUrlOpenPicker(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));

        Intent pickIntent = new Intent(Intent.ACTION_PICK_ACTIVITY);
        pickIntent.putExtra(Intent.EXTRA_INTENT, intent);

        startActivityForResult(pickIntent, ACTION_OPEN_URL);
    }

    /**
     * Used for showUrlOpenPicker
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == ACTION_OPEN_URL && resultCode == RESULT_OK && data != null) {
            data.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(data);
        }
    }
}





