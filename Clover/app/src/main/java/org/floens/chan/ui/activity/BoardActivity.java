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

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ActionBarDrawerToggle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.chan.ChanUrls;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Pin;
import org.floens.chan.core.model.Post;
import org.floens.chan.service.WatchService;
import org.floens.chan.ui.fragment.ThreadFragment;
import org.floens.chan.utils.Utils;

import java.util.List;

public class BoardActivity extends BaseActivity implements ActionBar.OnNavigationListener {
    private Loadable boardLoadable;
    private Loadable threadLoadable;
    private ThreadFragment boardFragment;
    private ThreadFragment threadFragment;

    private boolean actionBarSetToListNavigation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        actionBarSetToListNavigation = false;
        boardLoadable = new Loadable();
        threadLoadable = new Loadable();

        boardFragment = ThreadFragment.newInstance(this);
        threadFragment = ThreadFragment.newInstance(this);

        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.replace(R.id.left_pane, boardFragment);
        ft.replace(R.id.right_pane, threadFragment);
        ft.commitAllowingStateLoss();

        final ActionBar actionBar = getActionBar();
        actionBar.setListNavigationCallbacks(
                new ArrayAdapter<String>(actionBar.getThemedContext(), R.layout.board_select_spinner,
                        android.R.id.text1, ChanApplication.getBoardManager().getSavedKeys()), this
        );

        updatePaneState();
        updateActionBarState();

        Intent startIntent = getIntent();
        Uri startUri = startIntent.getData();

        if (savedInstanceState != null) {
            threadLoadable.readFromBundle(this, "thread", savedInstanceState);
            startLoadingThread(threadLoadable);

            // Reset page etc.
            Loadable tmp = new Loadable();
            tmp.readFromBundle(this, "board", savedInstanceState);
            loadBoard(tmp.board);
        } else {
            if (startUri != null) {
                handleIntentURI(startUri);
            }

            if (boardLoadable.mode == Loadable.Mode.INVALID) {
                List<String> savedValues = ChanApplication.getBoardManager().getSavedValues();
                if (savedValues.size() > 0) {
                    loadBoard(savedValues.get(0));
                }
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        boardLoadable.writeToBundle(this, "board", outState);
        threadLoadable.writeToBundle(this, "thread", outState);
    }

    @Override
    protected void onStart() {
        super.onStart();

        WatchService.onActivityStart();
    }

    @Override
    protected void onStop() {
        super.onStop();

        WatchService.onActivityStop();
    }

    @Override
    protected void onPause() {
        super.onPause();

        ChanApplication.getPinnedManager().updateAll();
    }

    @Override
    protected void initDrawer() {
        pinDrawerListener = new ActionBarDrawerToggle(this, pinDrawer, R.drawable.ic_drawer, R.string.drawer_open,
                R.string.drawer_close) {
        };

        super.initDrawer();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        return true;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        pinDrawerListener.onConfigurationChanged(newConfig);
        updateActionBarState();

        updatePaneState();
    }

    private void updatePaneState() {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int width = metrics.widthPixels;

        FrameLayout left = (FrameLayout) findViewById(R.id.left_pane);
        FrameLayout right = (FrameLayout) findViewById(R.id.right_pane);

        LayoutParams leftParams = left.getLayoutParams();
        LayoutParams rightParams = right.getLayoutParams();

        // Content view dp's:
        // Nexus 4 is 384 x 640 dp
        // Nexus 7 is 600 x 960 dp
        // Nexus 10 is 800 x 1280 dp

        if (width < Utils.dp(800)) {
            if (width < Utils.dp(400)) {
                leftParams.width = width - Utils.dp(30);
            } else {
                leftParams.width = width - Utils.dp(150);
            }
            rightParams.width = width;
        } else {
            leftParams.width = Utils.dp(300);
            rightParams.width = width - Utils.dp(300);
        }

        left.setLayoutParams(leftParams);
        right.setLayoutParams(rightParams);

        threadPane.requestLayout();
        left.requestLayout();
        right.requestLayout();
    }

    @Override
    public void openPin(Pin pin) {
        startLoadingThread(pin.loadable);

        pinDrawer.closeDrawer(pinDrawerView);
    }

    @Override
    public void onOPClicked(Post post) {
        startLoadingThread(new Loadable(post.board, post.no, post.subject));
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        pinDrawerListener.syncState();
    }

    @Override
    public void onBackPressed() {
        if (threadPane.isOpen()) {
            super.onBackPressed();
        } else {
            threadPane.openPane();
        }
    }

    @Override
    public void updatePin(Pin pin) {
        super.updatePin(pin);
        updateActionBarState();
    }

    private void updateActionBarState() {
        // Force the actionbar state after the ThreadPane layout,
        // otherwise the ThreadPane incorrectly reports that it's not slidable.
        threadPane.post(new Runnable() {
            @Override
            public void run() {
                updateActionBarStateCallback();
            }
        });
    }

    private void updateActionBarStateCallback() {
        final ActionBar actionBar = getActionBar();

        if (threadPane.isSlideable()) {
            if (threadPane.isOpen()) {
                int index = getBoardIndexNavigator(boardLoadable.board);

                if (index >= 0) {
                    //                    actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
                    setActionBarListMode();
                    actionBar.setTitle("");
                } else {
                    actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
                    String niceTitle = ChanApplication.getBoardManager().getBoardKey(boardLoadable.board);
                    if (niceTitle == null) {
                        actionBar.setTitle("/" + boardLoadable.board + "/");
                    } else {
                        actionBar.setTitle(niceTitle);
                    }
                }

                actionBar.setHomeButtonEnabled(true);
                pinDrawerListener.setDrawerIndicatorEnabled(true);

                if (boardLoadable.isBoardMode())
                    setShareUrl(ChanUrls.getBoardUrlDesktop(boardLoadable.board));
            } else {
                actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
                actionBar.setTitle(threadLoadable.title);
                pinDrawerListener.setDrawerIndicatorEnabled(false);

                if (threadLoadable.isThreadMode())
                    setShareUrl(ChanUrls.getThreadUrlDesktop(threadLoadable.board, threadLoadable.no));
            }

            actionBar.setDisplayHomeAsUpEnabled(true);
        } else {
            setActionBarListMode();
            //            actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
            pinDrawerListener.setDrawerIndicatorEnabled(true);
            actionBar.setTitle(threadLoadable.title);

            actionBar.setDisplayHomeAsUpEnabled(true);

            if (threadLoadable.isThreadMode())
                setShareUrl(ChanUrls.getThreadUrlDesktop(threadLoadable.board, threadLoadable.no));
        }

        actionBar.setDisplayShowTitleEnabled(true);

        invalidateOptionsMenu();
    }

    private void setActionBarListMode() {
        ActionBar actionBar = getActionBar();
        if (actionBar.getNavigationMode() != ActionBar.NAVIGATION_MODE_LIST)
            actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean open = threadPane.isOpen();
        boolean slidable = threadPane.isSlideable();

        setMenuItemEnabled(menu.findItem(R.id.action_reload_board), slidable && open);
        setMenuItemEnabled(menu.findItem(R.id.action_reload_thread), slidable && !open);
        setMenuItemEnabled(menu.findItem(R.id.action_reload_tablet), !slidable);

        setMenuItemEnabled(menu.findItem(R.id.action_pin), !slidable || !open);

        setMenuItemEnabled(menu.findItem(R.id.action_reply), slidable);
        setMenuItemEnabled(menu.findItem(R.id.action_reply_tablet), !slidable);

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
        if (pinDrawerListener.onOptionsItemSelected(item)) {
            return true;
        }

        switch (item.getItemId()) {
            case R.id.action_reload_board:
            case R.id.action_reload_tablet_board:
                boardFragment.reload();
                return true;
            case R.id.action_reload_thread:
            case R.id.action_reload_tablet_thread:
                threadFragment.reload();
                return true;
            case R.id.action_reply:
                if (threadPane.isOpen()) {
                    boardFragment.openReply();
                } else {
                    threadFragment.openReply();
                }
                return true;
            case R.id.action_reply_board:
                boardFragment.openReply();

                return true;
            case R.id.action_reply_thread:
                threadFragment.openReply();

                return true;
            case R.id.action_pin:
                if (threadFragment.hasLoader()) {
                    Pin pin = new Pin();
                    pin.loadable = threadLoadable;

                    addPin(pin);

                    pinDrawer.openDrawer(pinDrawerView);
                }

                return true;
            case R.id.action_open_browser:
                if (threadPane.isOpen()) {
                    showUrlOpenPicker(ChanUrls.getBoardUrlDesktop(boardLoadable.board));
                } else {
                    if (threadFragment.hasLoader()) {
                        showUrlOpenPicker(ChanUrls.getThreadUrlDesktop(threadLoadable.board, threadLoadable.no));
                    }
                }

                return true;
            case android.R.id.home:
                threadPane.openPane();

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

    /**
     * Sets the navigator to appropriately and calls startLoadingBoard
     *
     * @param board
     */
    private void loadBoard(String board) {
        boardLoadable = new Loadable(board);

        int index = getBoardIndexNavigator(boardLoadable.board);
        if (index >= 0) {
            ActionBar actionBar = getActionBar();
            setActionBarListMode();

            if (actionBar.getSelectedNavigationIndex() != index) {
                actionBar.setSelectedNavigationItem(index);
            } else {
                startLoadingBoard(boardLoadable);
            }
        } else {
            startLoadingBoard(boardLoadable);
        }
    }

    @Override
    public boolean onNavigationItemSelected(int position, long id) {
        if (!actionBarSetToListNavigation) {
            actionBarSetToListNavigation = true;
        } else {
            List<String> savedValues = ChanApplication.getBoardManager().getSavedValues();
            if (position >= 0 && position < savedValues.size()) {
                boardLoadable = new Loadable(savedValues.get(position));
                startLoadingBoard(boardLoadable);
            }
        }

        return true;
    }

    private void startLoadingBoard(Loadable loadable) {
        if (loadable.mode == Loadable.Mode.INVALID)
            return;

        boardLoadable = loadable;

        boardFragment.bindLoadable(loadable);
        boardFragment.requestData();

        updateActionBarState();
    }

    private void startLoadingThread(Loadable loadable) {
        if (loadable.mode == Loadable.Mode.INVALID)
            return;

        Pin pin = ChanApplication.getPinnedManager().findPinByLoadable(loadable);
        if (pin != null) {
            // Use the loadable from the pin.
            // This way we can store the listview position in the pin loadable,
            // and not in a separate loadable instance.
            loadable = pin.loadable;
        }

        threadLoadable = loadable;

        threadFragment.bindLoadable(loadable);
        threadFragment.requestData();

        if (TextUtils.isEmpty(loadable.title)) {
            loadable.title = "/" + loadable.board + "/" + loadable.no;
        }

        threadPane.closePane();

        updateActionBarState();
    }

    /**
     * Handle opening from an external url.
     *
     * @param startUri
     */
    private void handleIntentURI(Uri startUri) {
        List<String> parts = startUri.getPathSegments();

        if (parts.size() == 1) {
            // Board mode
            String rawBoard = parts.get(0);
            if (ChanApplication.getBoardManager().getBoardExists(rawBoard)) {
                // To clear the flag
                loadBoard(rawBoard);
                loadBoard(rawBoard);
            } else {
                handleIntentURIFallback(startUri.toString());
            }
        } else if (parts.size() == 3) {
            // Thread mode
            String rawBoard = parts.get(0);
            int no = -1;

            try {
                no = Integer.parseInt(parts.get(2));
            } catch (NumberFormatException e) {
            }

            if (no >= 0 && ChanApplication.getBoardManager().getBoardExists(rawBoard)) {
                startLoadingThread(new Loadable(rawBoard, no));
            } else {
                handleIntentURIFallback(startUri.toString());
                return;
            }
        } else {
            showUrlOpenPicker(startUri.toString());
        }
    }

    private void handleIntentURIFallback(final String url) {
        new AlertDialog.Builder(this).setTitle(R.string.open_unknown_title).setMessage(R.string.open_unknown)
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                }).setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                showUrlOpenPicker(url);
            }
        }).setCancelable(false).create().show();
    }

    private int getBoardIndexNavigator(String boardValue) {
        List<String> list = ChanApplication.getBoardManager().getSavedValues();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).equals(boardValue)) {
                return i;
            }
        }

        return -1;
    }
}
