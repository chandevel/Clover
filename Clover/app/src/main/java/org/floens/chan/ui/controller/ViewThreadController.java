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
package org.floens.chan.ui.controller;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

import org.floens.chan.Chan;
import org.floens.chan.R;
import org.floens.chan.chan.ChanUrls;
import org.floens.chan.core.manager.WatchManager;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Pin;
import org.floens.chan.ui.cell.PostCellInterface;
import org.floens.chan.ui.layout.ThreadLayout;
import org.floens.chan.ui.toolbar.ToolbarMenu;
import org.floens.chan.ui.toolbar.ToolbarMenuItem;
import org.floens.chan.ui.view.FloatingMenuItem;
import org.floens.chan.utils.AndroidUtils;

import java.util.Arrays;

import static org.floens.chan.utils.AndroidUtils.getAttrColor;

public class ViewThreadController extends ThreadController implements ThreadLayout.ThreadLayoutCallback, ToolbarMenuItem.ToolbarMenuItemCallback {
    private static final int PIN_ID = 2;
    private static final int REFRESH_ID = 101;
    private static final int SEARCH_ID = 102;
    private static final int SHARE_ID = 103;
    private static final int UP_ID = 104;
    private static final int DOWN_ID = 105;
    private static final int OPEN_BROWSER_ID = 106;

    private ToolbarMenuItem pinItem;
    private Loadable loadable;

    public ViewThreadController(Context context) {
        super(context);
    }

    public void setLoadable(Loadable loadable) {
        this.loadable = loadable;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        threadLayout.setPostViewMode(PostCellInterface.PostViewMode.LIST);

        view.setBackgroundColor(getAttrColor(context, R.attr.backcolor));

        navigationItem.hasDrawer = true;
        navigationItem.menu = new ToolbarMenu(context);

        pinItem = navigationItem.menu.addItem(new ToolbarMenuItem(context, this, PIN_ID, R.drawable.ic_bookmark_outline_white_24dp));
        navigationItem.createOverflow(context, this, Arrays.asList(
                new FloatingMenuItem(REFRESH_ID, context.getString(R.string.action_reload)),
                new FloatingMenuItem(SEARCH_ID, context.getString(R.string.action_search)),
                new FloatingMenuItem(OPEN_BROWSER_ID, context.getString(R.string.action_open_browser)),
                new FloatingMenuItem(SHARE_ID, context.getString(R.string.action_share)),
                new FloatingMenuItem(UP_ID, context.getString(R.string.action_up)),
                new FloatingMenuItem(DOWN_ID, context.getString(R.string.action_down))
        ));

        loadThread(loadable);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        updateDrawerHighlighting(null);
    }

    @Override
    public void openPin(Pin pin) {
        loadThread(pin.loadable);
    }

    public void onEvent(WatchManager.PinAddedMessage message) {
        setPinIconState();
    }

    public void onEvent(WatchManager.PinRemovedMessage message) {
        setPinIconState();
    }

    public void onEvent(WatchManager.PinChangedMessage message) {
        setPinIconState();
    }

    @Override
    public void showThread(final Loadable threadLoadable) {
        new AlertDialog.Builder(context)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, final int which) {
                        loadThread(threadLoadable);
                    }
                })
                .setTitle(R.string.open_thread_confirmation)
                .setMessage("/" + threadLoadable.board + "/" + threadLoadable.no)
                .show();
    }

    public void loadThread(Loadable loadable) {
        if (!loadable.equals(threadLayout.getPresenter().getLoadable())) {
            threadLayout.getPresenter().bindLoadable(loadable);
            this.loadable = threadLayout.getPresenter().getLoadable();
            threadLayout.getPresenter().requestData();
            navigationItem.title = loadable.title;
            navigationItem.updateTitle();
            setPinIconState(threadLayout.getPresenter().isPinned());

            updateDrawerHighlighting(loadable);
        }
    }

    @Override
    public void onShowPosts() {
        super.onShowPosts();

        navigationItem.title = loadable.title;
        navigationItem.updateTitle();
    }

    @Override
    public void onMenuItemClicked(ToolbarMenuItem item) {
        switch ((Integer) item.getId()) {
            case PIN_ID:
                setPinIconState(threadLayout.getPresenter().pin());
                break;
        }
    }

    @Override
    public void onSubMenuItemClicked(ToolbarMenuItem parent, FloatingMenuItem item) {
        Integer id = (Integer) item.getId();

        switch (id) {
            case REFRESH_ID:
                threadLayout.getPresenter().requestData();
                break;
            case SEARCH_ID:
                ((ToolbarNavigationController) navigationController).showSearch();
                break;
            case SHARE_ID:
            case OPEN_BROWSER_ID:
                Loadable loadable = threadLayout.getPresenter().getLoadable();
                String link = ChanUrls.getThreadUrlDesktop(loadable.board, loadable.no);

                if (id == SHARE_ID) {
                    AndroidUtils.shareLink(link);
                } else {
                    AndroidUtils.openLink(link);
                }

                break;
            case UP_ID:
            case DOWN_ID:
                boolean up = id == UP_ID;
                threadLayout.getPresenter().scrollTo(up ? 0 : -1, false);
                break;
        }
    }

    private void updateDrawerHighlighting(Loadable loadable) {
        WatchManager wm = Chan.getWatchManager();
        Pin pin = loadable == null ? null : wm.findPinByLoadable(loadable);

        if (navigationController.parentController instanceof DrawerController) {
            ((DrawerController) navigationController.parentController).setPinHighlighted(pin);
        } else if (navigationController.navigationController instanceof SplitNavigationController) {
            if (((SplitNavigationController) navigationController.navigationController).parentController instanceof DrawerController) {
                ((DrawerController) ((SplitNavigationController) navigationController.navigationController).parentController).setPinHighlighted(pin);
            }
        }
    }

    private void setPinIconState() {
        WatchManager wm = Chan.getWatchManager();
        setPinIconState(wm.findPinByLoadable(loadable) != null);
    }

    private void setPinIconState(boolean pinned) {
        pinItem.setImage(pinned ? R.drawable.ic_bookmark_white_24dp : R.drawable.ic_bookmark_outline_white_24dp);
    }
}
