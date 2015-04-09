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
package org.floens.chan.ui.activity;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.TypedArray;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SlidingPaneLayout;
import android.support.v4.widget.SlidingPaneLayout.PanelSlideListener;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.ShareActionProvider;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.EditText;
import android.widget.ListView;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.core.model.ChanThread;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Pin;
import org.floens.chan.core.model.Post;
import org.floens.chan.ui.ThemeActivity;
import org.floens.chan.ui.adapter.PinnedAdapter;
import org.floens.chan.ui.animation.SwipeDismissListViewTouchListener;
import org.floens.chan.ui.animation.SwipeDismissListViewTouchListener.DismissCallbacks;
import org.floens.chan.utils.AndroidUtils;
import org.floens.chan.utils.ThemeHelper;

import static org.floens.chan.utils.AndroidUtils.dp;

public abstract class BaseActivity extends ThemeActivity implements PanelSlideListener {
    public static boolean doRestartOnResume = false;

    private final static int ACTION_OPEN_URL = 1;

    protected PinnedAdapter pinnedAdapter;
    protected DrawerLayout pinDrawer;
    protected ListView pinDrawerView;
    protected ActionBarDrawerToggle pinDrawerListener;

    protected SlidingPaneLayout threadPane;

    private String shareUrl;
    private ShareActionProvider shareActionProvider;
    private Intent pendingShareActionProviderIntent;

    /**
     * Called when a post has been clicked in the pinned drawer
     *
     * @param post
     */
    abstract public void openPin(Pin post);

    /**
     * Called when a post has been clicked in the listview
     *
     * @param post
     */
    abstract public void onOPClicked(Post post);

    abstract public void onOpenThread(Loadable thread);

    abstract public void onThreadLoaded(ChanThread thread);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ThemeHelper.getInstance().reloadPostViewColors(this);

        setContentView(R.layout.activity_base);

        setTheme();
        setToolbar();

        pinDrawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        initDrawer();

        threadPane = (SlidingPaneLayout) findViewById(R.id.pane_container);
        initPane();

        updateIcon();
    }

    @Override
    public void onBackPressed() {
        if (pinDrawer.isDrawerOpen(pinDrawerView)) {
            pinDrawer.closeDrawer(pinDrawerView);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (doRestartOnResume) {
            doRestartOnResume = false;
            recreate();
        }
    }

    private void initPane() {
        threadPane.setPanelSlideListener(this);
        threadPane.setParallaxDistance(dp(100));
        threadPane.setShadowResource(R.drawable.panel_shadow);

        TypedArray ta = obtainStyledAttributes(null, R.styleable.BoardPane, R.attr.board_pane_style, 0);
        int color = ta.getColor(R.styleable.BoardPane_fade_color, 0);
        ta.recycle();

        threadPane.setSliderFadeColor(color);
        threadPane.openPane();
    }

    protected void initDrawer() {
        if (pinDrawerListener == null) {
            return;
        }

        pinDrawer.setDrawerListener(pinDrawerListener);
        pinDrawer.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);

        pinDrawerView = (ListView) findViewById(R.id.left_drawer);

        pinnedAdapter = new PinnedAdapter(getSupportActionBar().getThemedContext(), pinDrawerView); // Get the dark theme, not the light one
        pinnedAdapter.reload();
        pinDrawerView.setAdapter(pinnedAdapter);

        pinDrawerView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Pin pin = pinnedAdapter.getItem(position);
                if (pin == null)
                    return;
                openPin(pin);
            }
        });

        pinDrawerView.setOnItemLongClickListener(new OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                Pin post = pinnedAdapter.getItem(position);
                if (post == null)
                    return false;

                onPinLongPress(post);

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
                        return pinnedAdapter.getItem(position) != null;
                    }
                }
        );

        pinDrawerView.setOnTouchListener(touchListener);
        pinDrawerView.setOnScrollListener(touchListener.makeScrollListener());
        pinDrawerView.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
    }

    private void updateIcon() {
        /*List<Pin> list = ChanApplication.getWatchManager().getWatchingPins();
        if (list.size() > 0) {
            int count = 0;
            boolean color = false;
            for (Pin p : list) {
                count += p.getNewPostCount();
                if (p.getNewQuoteCount() > 0) {
                    color = true;
                }
            }

            if (count > 0) {
                Drawable icon = BadgeDrawable.get(getResources(), R.drawable.ic_launcher, count, color);
                getSupportActionBar().setIcon(icon);
            } else {
                getSupportActionBar().setIcon(R.drawable.ic_launcher);
            }
        } else {
            getSupportActionBar().setIcon(R.drawable.ic_launcher);
        }*/
    }

    public void removePin(Pin pin) {
        ChanApplication.getWatchManager().removePin(pin);
    }

    public void updatePin(Pin pin) {
        ChanApplication.getWatchManager().updatePin(pin);
    }

    private void onPinLongPress(final Pin pin) {
        new AlertDialog.Builder(this)
                .setNegativeButton(R.string.drawer_pinned_delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Delete pin
                        removePin(pin);
                    }
                }).setPositiveButton(R.string.drawer_pinned_change_title, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Change pin title
                final EditText text = new EditText(BaseActivity.this);
                text.setSingleLine();
                text.setText(pin.loadable.title);
                text.setSelectAllOnFocus(true);

                AlertDialog titleDialog = new AlertDialog.Builder(BaseActivity.this)
                        .setPositiveButton(R.string.change, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                                String value = text.getText().toString();

                                if (!TextUtils.isEmpty(value)) {
                                    pin.loadable.title = value;
                                    updatePin(pin);
                                }
                            }
                        }).setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface d, int which) {
                            }
                        }).setTitle(R.string.drawer_pinned_change_title).setView(text).create();

                AndroidUtils.requestKeyboardFocus(titleDialog, text);

                titleDialog.show();
            }
        }).show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.base, menu);
        /*shareActionProvider = (ShareActionProvider) MenuItemCompat.getActionProvider(menu.findItem(R.id.action_share));
        if (pendingShareActionProviderIntent != null) {
            shareActionProvider.setShareIntent(pendingShareActionProviderIntent);
            pendingShareActionProviderIntent = null;
        }*/

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
     *
     * @param url
     */
    public void setShareUrl(String url) {
        shareUrl = url;

        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(this);

        if (adapter != null) {
            NdefRecord record = null;
            try {
                record = NdefRecord.createUri(url);
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
                return;
            }

            NdefMessage message = new NdefMessage(new NdefRecord[]{record});
            try {
                adapter.setNdefPushMessage(message, this);
            } catch (Exception e) {
            }
        }

        Intent share = new Intent(android.content.Intent.ACTION_SEND);
        share.putExtra(android.content.Intent.EXTRA_TEXT, url);
        share.setType("text/plain");

        if (shareActionProvider != null) {
            shareActionProvider.setShareIntent(share);
        } else {
            pendingShareActionProviderIntent = share;
        }
    }

    public void openInBrowser() {
        if (shareUrl != null) {
            showUrlOpenPicker(shareUrl);
        }
    }

    /**
     * Let the user choose between all activities that can open the url. This is
     * done to prevent "open in browser" opening the url in our own app.
     *
     * @param url
     */
    public void showUrlOpenPicker(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        startActivity(intent);
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
