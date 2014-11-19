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
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ActionBarDrawerToggle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.Spinner;
import android.widget.TextView;

import org.floens.chan.ChanApplication;
import org.floens.chan.R;
import org.floens.chan.chan.ChanUrls;
import org.floens.chan.core.ChanPreferences;
import org.floens.chan.core.loader.Loader;
import org.floens.chan.core.manager.BoardManager;
import org.floens.chan.core.manager.ThreadManager;
import org.floens.chan.core.model.Board;
import org.floens.chan.core.model.ChanThread;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Pin;
import org.floens.chan.core.model.Post;
import org.floens.chan.ui.fragment.ThreadFragment;
import org.floens.chan.utils.Logger;
import org.floens.chan.utils.Utils;

import java.util.List;

public class ChanActivity extends BaseActivity implements AdapterView.OnItemSelectedListener, BoardManager.BoardChangeListener {
    private static final String TAG = "ChanActivity";

    private Loadable boardLoadable;
    private Loadable threadLoadable;
    private ThreadFragment boardFragment;
    private ThreadFragment threadFragment;

    private boolean ignoreNextOnItemSelected = false;
    private Spinner boardSpinner;
    private BoardSpinnerAdapter spinnerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ChanApplication.getBoardManager().addListener(this);

        boardLoadable = new Loadable();
        threadLoadable = new Loadable();

        boardFragment = ThreadFragment.newInstance(this);
        setBoardFragmentViewMode();

        threadFragment = ThreadFragment.newInstance(this);

        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.replace(R.id.left_pane, boardFragment);
        ft.replace(R.id.right_pane, threadFragment);
        ft.commitAllowingStateLoss();

        final ActionBar actionBar = getActionBar();

        boardSpinner = new Spinner(actionBar.getThemedContext());
        spinnerAdapter = new BoardSpinnerAdapter(this, boardSpinner);
        boardSpinner.setAdapter(spinnerAdapter);
        boardSpinner.setOnItemSelectedListener(this);

        actionBar.setCustomView(boardSpinner);
        actionBar.setDisplayShowCustomEnabled(true);

        updatePaneState();

        Intent startIntent = getIntent();
        Uri startUri = startIntent.getData();

        if (savedInstanceState != null) {
            threadLoadable.readFromBundle(this, "thread", savedInstanceState);
            startLoadingThread(threadLoadable);

            // Reset page etc.
            Loadable tmp = new Loadable();
            tmp.readFromBundle(this, "board", savedInstanceState);
            startLoadingBoard(new Loadable(tmp.board));
        } else {
            if (startUri != null) {
                handleIntentURI(startUri);
            }

            if (boardLoadable.mode == Loadable.Mode.INVALID) {
                List<Board> savedValues = ChanApplication.getBoardManager().getSavedBoards();
                if (savedValues.size() > 0) {
                    startLoadingBoard(new Loadable(savedValues.get(0).value));
                }
            }
        }

        if (startIntent.getExtras() != null) {
            handleExtraBundle(startIntent.getExtras());
        }

        ignoreNextOnItemSelected = true;
    }

    @Override
    protected void onNewIntent(final Intent intent) {
        super.onNewIntent(intent);

        if (intent.getExtras() != null) {
            handleExtraBundle(intent.getExtras());
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

        ChanApplication.getInstance().activityEnteredForeground();
    }

    @Override
    protected void onStop() {
        super.onStop();

        ChanApplication.getInstance().activityEnteredBackground();
    }

    @Override
    protected void onPause() {
        super.onPause();

        ChanApplication.getWatchManager().updateDatabase();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        ChanApplication.getBoardManager().removeListener(this);
    }

    @Override
    protected void initDrawer() {
        pinDrawerListener = new ActionBarDrawerToggle(this, pinDrawer, R.drawable.ic_drawer, R.string.drawer_open,
                R.string.drawer_close) {
        };

        super.initDrawer();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        pinDrawerListener.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        pinDrawerListener.onConfigurationChanged(newConfig);

        updatePaneState();
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
    public void openPin(Pin pin) {
        startLoadingThread(pin.loadable);

        pinDrawer.closeDrawer(pinDrawerView);
    }

    @Override
    public void onOPClicked(Post post) {
        Loadable l = new Loadable(post.board, post.no);
        l.generateTitle(post);
        startLoadingThread(l);
    }

    @Override
    public void onOpenThread(Loadable thread) {
        startLoadingThread(thread);
    }

    @Override
    public void onThreadLoaded(ChanThread thread) {
        updateActionBarState();
        pinnedAdapter.notifyDataSetChanged();
    }

    @Override
    public void updatePin(Pin pin) {
        super.updatePin(pin);
        updateActionBarState();
    }

    @Override
    public void onNothingSelected(final AdapterView<?> parent) {
    }

    @Override
    public void onPanelClosed(View view) {
        updateActionBarState();
    }

    @Override
    public void onPanelOpened(View view) {
        updateActionBarState();
    }

    @Override
    public void onBoardsChanged() {
        spinnerAdapter.setBoards();
        spinnerAdapter.notifyDataSetChanged();
    }

    private void handleExtraBundle(Bundle extras) {
        int pinId = extras.getInt("pin_id", -2);
        if (pinId != -2) {
            if (pinId == -1) {
                pinDrawer.openDrawer(pinDrawerView);
            } else {
                Pin pin = ChanApplication.getWatchManager().findPinById(pinId);
                if (pin != null) {
                    startLoadingThread(pin.loadable);
                }
            }
        }
    }

    private void updatePaneState() {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int width = metrics.widthPixels;

        FrameLayout left = (FrameLayout) findViewById(R.id.left_pane);
        FrameLayout right = (FrameLayout) findViewById(R.id.right_pane);

        LayoutParams leftParams = left.getLayoutParams();
        LayoutParams rightParams = right.getLayoutParams();

        boolean wasSlidable = threadPane.isSlideable();
        boolean isSlidable;

        // Content view dp's:
        // Nexus 4 is 384 x 640 dp
        // Nexus 7 is 600 x 960 dp
        // Nexus 10 is 800 x 1280 dp

        if (ChanPreferences.getForcePhoneLayout()) {
            leftParams.width = width - Utils.dp(30);
            rightParams.width = width;
            isSlidable = true;
        } else {
            if (width < Utils.dp(400)) {
                leftParams.width = width - Utils.dp(30);
                rightParams.width = width;
                isSlidable = true;
            } else if (width < Utils.dp(800)) {
                leftParams.width = width - Utils.dp(60);
                rightParams.width = width;
                isSlidable = true;
            } else if (width < Utils.dp(1000)) {
                leftParams.width = Utils.dp(300);
                rightParams.width = width - Utils.dp(300);
                isSlidable = false;
            } else {
                leftParams.width = Utils.dp(400);
                rightParams.width = width - Utils.dp(400);
                isSlidable = false;
            }
        }

        left.setLayoutParams(leftParams);
        right.setLayoutParams(rightParams);

        threadPane.requestLayout();
        left.requestLayout();
        right.requestLayout();

        LayoutParams drawerParams = pinDrawerView.getLayoutParams();

        if (width < Utils.dp(340)) {
            drawerParams.width = Utils.dp(280);
        } else {
            drawerParams.width = Utils.dp(320);
        }

        pinDrawerView.setLayoutParams(drawerParams);

        updateActionBarState();

        if (isSlidable != wasSlidable) {
            // Terrible hack to sync state for some devices when it changes slidable mode
            threadPane.postDelayed(new Runnable() {
                @Override
                public void run() {
                    updateActionBarState();
                }
            }, 1000);
        }
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
                actionBar.setDisplayShowCustomEnabled(true);
                spinnerAdapter.setBoard(boardLoadable.board);
                actionBar.setTitle("");
                pinDrawerListener.setDrawerIndicatorEnabled(true);

                if (boardLoadable.isBoardMode()) {
                    setShareUrl(ChanUrls.getBoardUrlDesktop(boardLoadable.board));
                } else if (boardLoadable.isCatalogMode()) {
                    setShareUrl(ChanUrls.getCatalogUrlDesktop(boardLoadable.board));
                }
            } else {
                actionBar.setDisplayShowCustomEnabled(false);
                actionBar.setTitle(threadLoadable.title);
                pinDrawerListener.setDrawerIndicatorEnabled(false);

                if (threadLoadable.isThreadMode())
                    setShareUrl(ChanUrls.getThreadUrlDesktop(threadLoadable.board, threadLoadable.no));
            }
        } else {
            actionBar.setDisplayShowCustomEnabled(true);
            pinDrawerListener.setDrawerIndicatorEnabled(true);
            actionBar.setTitle(threadLoadable.title);

            if (threadLoadable.isThreadMode()) {
                setShareUrl(ChanUrls.getThreadUrlDesktop(threadLoadable.board, threadLoadable.no));
            }
        }

        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowTitleEnabled(true);

        invalidateOptionsMenu();
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

        setMenuItemEnabled(menu.findItem(R.id.action_board_view_mode), !slidable || open);

        if (ChanPreferences.getBoardViewMode().equals("list")) {
            menu.findItem(R.id.action_board_view_mode_list).setChecked(true);
        } else if (ChanPreferences.getBoardViewMode().equals("grid")) {
            menu.findItem(R.id.action_board_view_mode_grid).setChecked(true);
        }

        setMenuItemEnabled(menu.findItem(R.id.action_search), slidable);
        setMenuItemEnabled(menu.findItem(R.id.action_search_tablet), !slidable);

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
                    Loader loader = threadFragment.getLoader();
                    if (loader != null && loader.getLoadable().isThreadMode() && loader.getThread() != null) {
                        ChanApplication.getWatchManager().addPin(loader.getLoadable(), loader.getThread().op);
                        pinDrawer.openDrawer(pinDrawerView);
                    }
                }

                return true;
            case R.id.action_open_browser:
                openInBrowser();

                return true;
            case R.id.action_board_view_mode_grid:
                if (!ChanPreferences.getBoardViewMode().equals("grid")) {
                    ChanPreferences.setBoardViewMode("grid");
                    setBoardFragmentViewMode();
                    startLoadingBoard(boardLoadable);
                }
                return true;
            case R.id.action_board_view_mode_list:
                if (!ChanPreferences.getBoardViewMode().equals("list")) {
                    ChanPreferences.setBoardViewMode("list");
                    setBoardFragmentViewMode();
                    startLoadingBoard(boardLoadable);
                }
                return true;
            case R.id.action_search:
                if (threadPane.isOpen()) {
                    boardFragment.startFiltering();
                } else {
                    threadFragment.startFiltering();
                }
                return true;
            case R.id.action_search_board:
                boardFragment.startFiltering();
                return true;
            case R.id.action_search_thread:
                threadFragment.startFiltering();
                return true;
            case android.R.id.home:
                threadPane.openPane();

                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onItemSelected(final AdapterView<?> parent, final View view, final int position, final long id) {
        if (ignoreNextOnItemSelected) {
            Logger.d(TAG, "Ignoring onItemSelected");
            ignoreNextOnItemSelected = false;
            return;
        }

        spinnerAdapter.onItemSelected(position);
    }

    private void startLoadingBoard(Loadable loadable) {
        if (loadable.mode == Loadable.Mode.INVALID)
            return;

        boardLoadable = loadable;

        if (ChanPreferences.getBoardMode().equals("catalog")) {
            boardLoadable.mode = Loadable.Mode.CATALOG;
        } else if (ChanPreferences.getBoardMode().equals("pages")) {
            boardLoadable.mode = Loadable.Mode.BOARD;
        }

        // Force catalog mode when using grid
        if (boardFragment.getViewMode() == ThreadManager.ViewMode.GRID) {
            boardLoadable.mode = Loadable.Mode.CATALOG;
        }

        boardFragment.bindLoadable(boardLoadable);
        boardFragment.requestData();

        updateActionBarState();
    }

    private void startLoadingThread(Loadable loadable) {
        if (loadable.mode == Loadable.Mode.INVALID)
            return;

        Pin pin = ChanApplication.getWatchManager().findPinByLoadable(loadable);
        if (pin != null) {
            // Use the loadable from the pin.
            // This way we can store the listview position in the pin loadable,
            // and not in a separate loadable instance.
            loadable = pin.loadable;
        }

        if (threadLoadable.equals(loadable)) {
            threadFragment.requestNextData();
        } else {
            threadLoadable = loadable;
            threadFragment.bindLoadable(loadable);
            threadFragment.requestData();
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
        Logger.d(TAG, "Opening " + startUri.getPath());

        List<String> parts = startUri.getPathSegments();

        if (parts.size() == 1) {
            // Board mode
            String rawBoard = parts.get(0);
            if (ChanApplication.getBoardManager().getBoardExists(rawBoard)) {
                startLoadingBoard(new Loadable(rawBoard));
            } else {
                handleIntentURIFallback(startUri.toString());
            }
        } else if (parts.size() >= 3) {
            // Thread mode
            String rawBoard = parts.get(0);
            int no = -1;

            try {
                no = Integer.parseInt(parts.get(2));
            } catch (NumberFormatException e) {
            }

            int post = -1;
            String fragment = startUri.getFragment();
            if (fragment != null) {
                int index = fragment.indexOf("p");
                if (index >= 0) {
                    try {
                        post = Integer.parseInt(fragment.substring(index + 1));
                    } catch (NumberFormatException e) {
                    }
                }
            }

            if (no >= 0 && ChanApplication.getBoardManager().getBoardExists(rawBoard)) {
                startLoadingThread(new Loadable(rawBoard, no));
                if (post >= 0) {
                    threadFragment.highlightPost(post);
                }
            } else {
                handleIntentURIFallback(startUri.toString());
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

    private void setBoardFragmentViewMode() {
        if (ChanPreferences.getBoardViewMode().equals("list")) {
            boardFragment.setViewMode(ThreadManager.ViewMode.LIST);
        } else if (ChanPreferences.getBoardViewMode().equals("grid")) {
            boardFragment.setViewMode(ThreadManager.ViewMode.GRID);
        }
    }

    private class BoardSpinnerAdapter extends BaseAdapter {
        private Context context;
        private Spinner spinner;
        private List<Board> boards;
        private int lastSelectedPosition = 0;

        public BoardSpinnerAdapter(Context context, Spinner spinner) {
            this.context = context;
            this.spinner = spinner;
            setBoards();
        }

        public void setBoards() {
            boards = ChanApplication.getBoardManager().getSavedBoards();
        }

        public void setBoard(String boardValue) {
            for (int i = 0; i < boards.size(); i++) {
                if (boards.get(i).value.equals(boardValue)) {
                    spinner.setSelection(i);
                    return;
                }
            }
        }

        public void onItemSelected(int position) {
            if (position >= 0 && position < boards.size()) {
                Loadable board = new Loadable(boards.get(position).value);

                // onItemSelected is called after the view initializes,
                // ignore if it's the same board
                if (boardLoadable.equals(board))
                    return;

                startLoadingBoard(board);

                lastSelectedPosition = position;
            } else {
                startActivity(new Intent(context, BoardEditor.class));
                spinner.setSelection(lastSelectedPosition);
            }
        }

        @Override
        public int getCount() {
            return boards.size() + 1;
        }

        @Override
        public long getItemId(final int position) {
            return position;
        }

        @Override
        public String getItem(final int position) {
            if (position == getCount() - 1) {
                return context.getString(R.string.board_select_add);
            } else {
                return boards.get(position).key;
            }
        }

        @Override
        public View getView(final int position, View convertView, final ViewGroup parent) {
            if (position == getCount() - 1) {
                TextView textView = (TextView) LayoutInflater.from(context).inflate(R.layout.board_select_add, null);
                textView.setText(getItem(position));
                return textView;
            } else {
                TextView textView = (TextView) LayoutInflater.from(context).inflate(R.layout.board_select_spinner, null);
                textView.setText(getItem(position));
                return textView;
            }
        }
    }
}
