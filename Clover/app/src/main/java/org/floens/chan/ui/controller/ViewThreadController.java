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

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;

import org.floens.chan.Chan;
import org.floens.chan.R;
import org.floens.chan.chan.ChanUrls;
import org.floens.chan.controller.Controller;
import org.floens.chan.controller.NavigationController;
import org.floens.chan.core.manager.WatchManager;
import org.floens.chan.core.model.Loadable;
import org.floens.chan.core.model.Pin;
import org.floens.chan.core.presenter.ThreadPresenter;
import org.floens.chan.core.settings.ChanSettings;
import org.floens.chan.ui.helper.HintPopup;
import org.floens.chan.ui.layout.ThreadLayout;
import org.floens.chan.ui.toolbar.ToolbarMenu;
import org.floens.chan.ui.toolbar.ToolbarMenuItem;
import org.floens.chan.ui.view.FloatingMenuItem;
import org.floens.chan.utils.AndroidUtils;

import java.util.ArrayList;
import java.util.List;

import static org.floens.chan.utils.AndroidUtils.dp;
import static org.floens.chan.utils.AndroidUtils.getAttrColor;

public class ViewThreadController extends ThreadController implements ThreadLayout.ThreadLayoutCallback, ToolbarMenuItem.ToolbarMenuItemCallback {
    private static final int ALBUM_ID = 1;
    private static final int PIN_ID = 2;
    private static final int REPLY_ID = 101;
    private static final int REFRESH_ID = 103;
    private static final int SEARCH_ID = 104;
    private static final int SHARE_ID = 105;
    private static final int UP_ID = 106;
    private static final int DOWN_ID = 107;
    private static final int OPEN_BROWSER_ID = 108;

    private ToolbarMenuItem pinItem;
    private ToolbarMenuItem overflowItem;
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

        threadLayout.setPostViewMode(ChanSettings.PostViewMode.LIST);

        view.setBackgroundColor(getAttrColor(context, R.attr.backcolor));

        navigationItem.hasDrawer = true;
        navigationItem.menu = new ToolbarMenu(context);

        navigationItem.menu.addItem(new ToolbarMenuItem(context, this, ALBUM_ID, R.drawable.ic_image_white_24dp));
        pinItem = navigationItem.menu.addItem(new ToolbarMenuItem(context, this, PIN_ID, R.drawable.ic_bookmark_outline_white_24dp));
        List<FloatingMenuItem> items = new ArrayList<>();
        if (!ChanSettings.enableReplyFab.get()) {
            items.add(new FloatingMenuItem(REPLY_ID, context.getString(R.string.action_reply)));
        }
        items.add(new FloatingMenuItem(REFRESH_ID, R.string.action_reload));
        items.add(new FloatingMenuItem(SEARCH_ID, R.string.action_search));
        items.add(new FloatingMenuItem(OPEN_BROWSER_ID, R.string.action_open_browser));
        items.add(new FloatingMenuItem(SHARE_ID, R.string.action_share));
        items.add(new FloatingMenuItem(UP_ID, R.string.action_up));
        items.add(new FloatingMenuItem(DOWN_ID, R.string.action_down));
        overflowItem = navigationItem.createOverflow(context, this, items);

        loadThread(loadable);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        updateDrawerHighlighting(null);
        updateLeftPaneHighlighting(null);
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
        // Update title
        if (message.pin.loadable == loadable) {
            threadLayout.getPresenter().refreshUI();
        }
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
        ThreadPresenter presenter = threadLayout.getPresenter();
        if (!loadable.equals(presenter.getLoadable())) {
            presenter.bindLoadable(loadable);
            this.loadable = presenter.getLoadable();
            navigationItem.title = loadable.title;
            navigationItem.updateTitle();
            setPinIconState(presenter.isPinned());
            updateDrawerHighlighting(loadable);
            updateLeftPaneHighlighting(loadable);
            presenter.requestInitialData();

            int counter = ChanSettings.threadOpenCounter.increase();
            if (counter == 2) {
                HintPopup.show(context, overflowItem.getView(), context.getString(R.string.thread_up_down_hint), -dp(1), 0);
            } else if (counter == 3) {
                HintPopup.show(context, pinItem.getView(), context.getString(R.string.thread_pin_hint), -dp(1), 0);
            }
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
            case ALBUM_ID:
                threadLayout.getPresenter().showAlbum();
                break;
            case PIN_ID:
                setPinIconState(threadLayout.getPresenter().pin());
                updateDrawerHighlighting(loadable);
                break;
        }
    }

    @Override
    public void onSubMenuItemClicked(ToolbarMenuItem parent, FloatingMenuItem item) {
        Integer id = (Integer) item.getId();

        switch (id) {
            case REPLY_ID:
                threadLayout.openReply(true);
                break;
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
                    AndroidUtils.openLinkInBrowser((Activity) context, link);
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
        } else if (splitNavigationController != null) {
            if (splitNavigationController.parentController instanceof DrawerController) {
                ((DrawerController) splitNavigationController.parentController).setPinHighlighted(pin);
            }
        }
    }

    private void updateLeftPaneHighlighting(Loadable loadable) {
        if (splitNavigationController != null) {
            if (splitNavigationController.leftController instanceof NavigationController) {
                NavigationController leftNavigationController = (NavigationController) splitNavigationController.leftController;
                ThreadController threadController = null;
                for (Controller controller : leftNavigationController.childControllers) {
                    if (controller instanceof ThreadController) {
                        threadController = (ThreadController) controller;
                        break;
                    }
                }
                if (threadController != null) {
                    threadController.selectPost(loadable != null ? loadable.no : -1);
                }
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
